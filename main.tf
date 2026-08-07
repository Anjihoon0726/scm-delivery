terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
    helm = {
      source  = "hashicorp/helm"
      version = "~> 2.0"
    }
    kubernetes = {
      source  = "hashicorp/kubernetes"
      version = "~> 2.0"
    }
  }
}

provider "aws" {
  region = "ap-northeast-2"
}

# ==========================================
# 0. Data Sources (100% 동적 조회)
# ==========================================

# 1) mgmt-vpc 동적 조회
data "aws_vpcs" "msk_vpcs" {
  filter {
    name   = "cidr-block-association.cidr-block"
    values = ["10.100.0.0/16"]
  }

  filter {
    name   = "tag:Name"
    values = ["mgmt-vpc"]
  }
}

data "aws_vpc" "msk_vpc" {
  id = tolist(data.aws_vpcs.msk_vpcs.ids)[0]
}

# 2) MSK VPC 프라이빗 라우팅 테이블 동적 조회
data "aws_route_tables" "msk_route_tables" {
  vpc_id = data.aws_vpc.msk_vpc.id
}

# ==========================================
# 1. Delivery VPC 및 서브넷 네트워크 구축 (10.50.0.0/16)
# ==========================================

resource "aws_vpc" "dlv_vpc" {
  cidr_block           = "10.50.0.0/16"
  enable_dns_hostnames = true
  enable_dns_support   = true

  tags = {
    Name = "dlv-vpc"
  }
}

resource "aws_internet_gateway" "dlv_igw" {
  vpc_id = aws_vpc.dlv_vpc.id

  tags = {
    Name = "dlv-igw"
  }
}

# NAT Gateway (외부 택배사 REST API 연동용)
resource "aws_eip" "nat_eip" {
  domain = "vpc"

  tags = {
    Name = "dlv-nat-eip"
  }
}

resource "aws_nat_gateway" "dlv_nat" {
  allocation_id = aws_eip.nat_eip.id
  subnet_id     = aws_subnet.dlv_public_subnet_a.id

  tags = {
    Name = "dlv-nat"
  }

  depends_on = [aws_internet_gateway.dlv_igw]
}

# 서브넷 설정
resource "aws_subnet" "dlv_public_subnet_a" {
  vpc_id                  = aws_vpc.dlv_vpc.id
  cidr_block              = "10.50.10.0/24"
  availability_zone       = "ap-northeast-2a"
  map_public_ip_on_launch = true

  tags = {
    Name                                     = "dlv-public-subnet-2a"
    "kubernetes.io/role/elb"                 = "1"
    "kubernetes.io/cluster/dlv-eks"          = "shared"
  }
}

resource "aws_subnet" "dlv_public_subnet_c" {
  vpc_id                  = aws_vpc.dlv_vpc.id
  cidr_block              = "10.50.11.0/24"
  availability_zone       = "ap-northeast-2c"
  map_public_ip_on_launch = true

  tags = {
    Name                                     = "dlv-public-subnet-2c"
    "kubernetes.io/role/elb"                 = "1"
    "kubernetes.io/cluster/dlv-eks"          = "shared"
  }
}

resource "aws_subnet" "dlv_private_subnet_a" {
  vpc_id            = aws_vpc.dlv_vpc.id
  cidr_block        = "10.50.20.0/24"
  availability_zone = "ap-northeast-2a"

  tags = {
    Name                                     = "dlv-private-subnet-2a"
    "kubernetes.io/role/internal-elb"        = "1"
    "kubernetes.io/cluster/dlv-eks"          = "shared"
  }
}

resource "aws_subnet" "dlv_private_subnet_c" {
  vpc_id            = aws_vpc.dlv_vpc.id
  cidr_block        = "10.50.21.0/24"
  availability_zone = "ap-northeast-2c"

  tags = {
    Name                                     = "dlv-private-subnet-2c"
    "kubernetes.io/role/internal-elb"        = "1"
    "kubernetes.io/cluster/dlv-eks"          = "shared"
  }
}

# 퍼블릭 라우팅 테이블
resource "aws_route_table" "dlv_public_rt" {
  vpc_id = aws_vpc.dlv_vpc.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.dlv_igw.id
  }

  route {
    cidr_block                = "10.100.0.0/16"
    vpc_peering_connection_id = aws_vpc_peering_connection.dlv_msk_peering.id
  }

  tags = {
    Name = "dlv-public-rt"
  }
}

resource "aws_route_table_association" "dlv_rta_pub_a" {
  subnet_id      = aws_subnet.dlv_public_subnet_a.id
  route_table_id = aws_route_table.dlv_public_rt.id
}

resource "aws_route_table_association" "dlv_rta_pub_c" {
  subnet_id      = aws_subnet.dlv_public_subnet_c.id
  route_table_id = aws_route_table.dlv_public_rt.id
}

# 프라이빗 라우팅 테이블 (NAT GW 통한 아웃바운드 인터넷 제공)
resource "aws_route_table" "dlv_private_rt" {
  vpc_id = aws_vpc.dlv_vpc.id

  route {
    cidr_block     = "0.0.0.0/0"
    nat_gateway_id = aws_nat_gateway.dlv_nat.id
  }

  route {
    cidr_block                = "10.100.0.0/16"
    vpc_peering_connection_id = aws_vpc_peering_connection.dlv_msk_peering.id
  }

  tags = {
    Name = "dlv-private-rt"
  }
}

resource "aws_route_table_association" "dlv_rta_pri_a" {
  subnet_id      = aws_subnet.dlv_private_subnet_a.id
  route_table_id = aws_route_table.dlv_private_rt.id
}

resource "aws_route_table_association" "dlv_rta_pri_c" {
  subnet_id      = aws_subnet.dlv_private_subnet_c.id
  route_table_id = aws_route_table.dlv_private_rt.id
}

resource "aws_db_subnet_group" "dlv_rds_subnet_group" {
  name       = "dlv-rds-subnet-group"
  subnet_ids = [aws_subnet.dlv_private_subnet_a.id, aws_subnet.dlv_private_subnet_c.id]

  tags = {
    Name = "dlv-rds-subnet-group"
  }
}

resource "aws_elasticache_subnet_group" "dlv_redis_subnet_group" {
  name       = "dlv-redis-subnet-group"
  subnet_ids = [aws_subnet.dlv_private_subnet_a.id, aws_subnet.dlv_private_subnet_c.id]
}

# ==========================================
# 2. VPC Peering 연동 및 양방향 라우팅
# ==========================================

resource "aws_vpc_peering_connection" "dlv_msk_peering" {
  vpc_id      = aws_vpc.dlv_vpc.id
  peer_vpc_id = data.aws_vpc.msk_vpc.id
  auto_accept = true

  tags = {
    Name = "dlv-to-msk-peering"
  }
}

resource "aws_vpc_peering_connection_options" "dlv_msk_peering_options" {
  vpc_peering_connection_id = aws_vpc_peering_connection.dlv_msk_peering.id

  accepter {
    allow_remote_vpc_dns_resolution = true
  }

  requester {
    allow_remote_vpc_dns_resolution = true
  }
}

resource "aws_route" "dlv_msk_return_route" {
  for_each                  = toset(data.aws_route_tables.msk_route_tables.ids)
  route_table_id            = each.value
  destination_cidr_block    = "10.50.0.0/16"
  vpc_peering_connection_id = aws_vpc_peering_connection.dlv_msk_peering.id
}

# ==========================================
# 3. 보안 그룹(Security Group) 설정
# ==========================================

resource "aws_security_group" "dlv_rds_sg" {
  name        = "dlv-rds-sg"
  description = "Security Group for Private PostgreSQL RDS"
  vpc_id      = aws_vpc.dlv_vpc.id

  ingress {
    from_port   = 15432
    to_port     = 15432
    protocol    = "tcp"
    cidr_blocks = ["10.50.0.0/16"]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_security_group" "dlv_redis_sg" {
  name        = "dlv-redis-sg"
  description = "Security Group for Private ElastiCache Redis"
  vpc_id      = aws_vpc.dlv_vpc.id

  ingress {
    from_port   = 16379
    to_port     = 16379
    protocol    = "tcp"
    cidr_blocks = ["10.50.0.0/16"]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

# ==========================================
# 4. AWS Secrets Manager
# ==========================================

resource "aws_secretsmanager_secret" "db_secret" {
  name                    = "dlv/db-credentials"
  recovery_window_in_days = 0

  tags = {
    Environment = "dev"
    Service     = "dlv"
  }
}

resource "aws_secretsmanager_secret_version" "db_secret_val" {
  secret_id = aws_secretsmanager_secret.db_secret.id
  secret_string = jsonencode({
    username = "dlv_admin"
    password = "dlv_1234"
    engine   = "postgres"
    host     = aws_db_instance.dlv_postgres.address
    port     = 15432
    dbname   = "db_delivery"
  })
}

# ==========================================
# 5. Database & Cache 구축 (PostgreSQL & Redis)
# ==========================================

resource "aws_db_instance" "dlv_postgres" {
  identifier             = "dlv-postgres-db"
  allocated_storage      = 20
  max_allocated_storage  = 50
  engine                 = "postgres"
  engine_version         = "15"
  instance_class         = "db.t3.micro"
  port                   = 15432
  db_name                = "db_delivery"
  username               = "dlv_admin"
  password               = "dlv_1234"
  db_subnet_group_name   = aws_db_subnet_group.dlv_rds_subnet_group.name
  vpc_security_group_ids = [aws_security_group.dlv_rds_sg.id]
  publicly_accessible    = false
  skip_final_snapshot    = true

  tags = {
    Name = "dlv-postgres"
  }
}

resource "aws_elasticache_cluster" "dlv_redis" {
  cluster_id           = "dlv-redis-cluster"
  engine               = "redis"
  node_type            = "cache.t3.micro"
  num_cache_nodes      = 1
  parameter_group_name = "default.redis7"
  port                 = 16379
  subnet_group_name    = aws_elasticache_subnet_group.dlv_redis_subnet_group.name
  security_group_ids   = [aws_security_group.dlv_redis_sg.id]

  tags = {
    Name = "dlv-redis"
  }
}

# ==========================================
# 6. EKS IAM 역할 설정
# ==========================================

resource "aws_iam_role" "dlv_eks_cluster_role" {
  name = "dlv-eks-cluster-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "eks.amazonaws.com"
        }
      }
    ]
  })
}

resource "aws_iam_role_policy_attachment" "eks_cluster_policy" {
  policy_arn = "arn:aws:iam::aws:policy/AmazonEKSClusterPolicy"
  role       = aws_iam_role.dlv_eks_cluster_role.name
}

resource "aws_iam_role" "dlv_eks_node_role" {
  name = "dlv-eks-node-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "ec2.amazonaws.com"
        }
      }
    ]
  })
}

resource "aws_iam_role_policy_attachment" "eks_worker_node_policy" {
  policy_arn = "arn:aws:iam::aws:policy/AmazonEKSWorkerNodePolicy"
  role       = aws_iam_role.dlv_eks_node_role.name
}

resource "aws_iam_role_policy_attachment" "eks_cni_policy" {
  policy_arn = "arn:aws:iam::aws:policy/AmazonEKS_CNI_Policy"
  role       = aws_iam_role.dlv_eks_node_role.name
}

resource "aws_iam_role_policy_attachment" "eks_container_registry_policy" {
  policy_arn = "arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryReadOnly"
  role       = aws_iam_role.dlv_eks_node_role.name
}

# AWS Load Balancer Controller 권한 (EKS 노드 역할에 추가)
resource "aws_iam_policy" "alb_controller_policy" {
  name        = "AWSLoadBalancerControllerIAMPolicy"
  description = "IAM policy for AWS Load Balancer Controller"
  policy      = file("${path.module}/alb_controller_iam_policy.json")
}

resource "aws_iam_role_policy_attachment" "eks_node_alb_controller_policy" {
  policy_arn = aws_iam_policy.alb_controller_policy.arn
  role       = aws_iam_role.dlv_eks_node_role.name
}

# ExternalDNS를 위한 Route 53 IAM 권한 추가
resource "aws_iam_role_policy" "eks_node_route53_access" {
  name = "external-dns-route53-access"
  role = aws_iam_role.dlv_eks_node_role.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect   = "Allow"
        Action   = ["route53:ChangeResourceRecordSets"]
        Resource = "arn:aws:route53:::hostedzone/Z1044783PWW9R75I2WV6"
      },
      {
        Effect = "Allow"
        Action = [
          "route53:ListHostedZones",
          "route53:ListResourceRecordSets",
          "route53:ListTagsForResource"
        ]
        Resource = "*"
      }
    ]
  })
}

# ==========================================
# 7. EKS Control Plane & Node Group 생성
# ==========================================

resource "aws_eks_cluster" "dlv_eks" {
  name     = "dlv-eks"
  role_arn = aws_iam_role.dlv_eks_cluster_role.arn
  version  = "1.31"

  vpc_config {
    subnet_ids = [
      aws_subnet.dlv_private_subnet_a.id,
      aws_subnet.dlv_private_subnet_c.id
    ]
    endpoint_private_access = true
    endpoint_public_access  = true
  }

  depends_on = [
    aws_iam_role_policy_attachment.eks_cluster_policy
  ]

  tags = {
    Name = "dlv-eks"
  }
}

resource "aws_eks_node_group" "dlv_node_group" {
  cluster_name    = aws_eks_cluster.dlv_eks.name
  node_group_name = "dlv-node-group"
  node_role_arn   = aws_iam_role.dlv_eks_node_role.arn

  subnet_ids = [
    aws_subnet.dlv_private_subnet_a.id,
    aws_subnet.dlv_private_subnet_c.id
  ]

  instance_types = ["t3.medium"]

  scaling_config {
    desired_size = 2
    min_size     = 1
    max_size     = 4
  }

  update_config {
    max_unavailable = 1
  }

  depends_on = [
    aws_iam_role_policy_attachment.eks_worker_node_policy,
    aws_iam_role_policy_attachment.eks_cni_policy,
    aws_iam_role_policy_attachment.eks_container_registry_policy,
    aws_iam_role_policy_attachment.eks_node_alb_controller_policy,
    aws_nat_gateway.dlv_nat,
    aws_route_table_association.dlv_rta_pri_a,
    aws_route_table_association.dlv_rta_pri_c,
    aws_route_table_association.dlv_rta_pub_a,
    aws_route_table_association.dlv_rta_pub_c
  ]

  tags = {
    Name = "dlv-worker-node"
  }
}

# ==========================================
# 8. Route 53 공용 레코드 설정 (RDS, Redis)
# ==========================================

data "aws_route53_zone" "scm_zone" {
  zone_id      = "Z1044783PWW9R75I2WV6"
  private_zone = false
}

resource "aws_route53_record" "db_record" {
  zone_id         = data.aws_route53_zone.scm_zone.zone_id
  name            = "dlv-db.${data.aws_route53_zone.scm_zone.name}" # dlv-db로 수정
  type            = "CNAME"
  ttl             = 300
  allow_overwrite = true

  records = [element(split(":", aws_db_instance.dlv_postgres.endpoint), 0)]
}

resource "aws_route53_record" "redis_record" {
  zone_id         = data.aws_route53_zone.scm_zone.zone_id
  name            = "dlv-redis.${data.aws_route53_zone.scm_zone.name}" # dlv-redis로 수정
  type            = "CNAME"
  ttl             = 300
  allow_overwrite = true

  records = [aws_elasticache_cluster.dlv_redis.cache_nodes[0].address]
}

# ==========================================
# 9. MSK 연동
# ==========================================

locals {
  msk_bootstrap_brokers_sasl_scram = "b-1.hcscmmsk.qhxzwx.c3.kafka.ap-northeast-2.amazonaws.com:9096,b-2.hcscmmsk.qhxzwx.c3.kafka.ap-northeast-2.amazonaws.com:9096,b-3.hcscmmsk.qhxzwx.c3.kafka.ap-northeast-2.amazonaws.com:9096"

  msk_broker_ips = {
    "b-1.hcscmmsk" = "10.100.10.70"
    "b-2.hcscmmsk" = "10.100.12.194"
    "b-3.hcscmmsk" = "10.100.11.127"
  }
}

variable "msk_scram_secret_arn" {
  description = "MSK 클러스터에 연동된 SASL/SCRAM 인증용 Secrets Manager 시크릿 ARN"
  type        = string
  default     = "arn:aws:secretsmanager:ap-northeast-2:967996001868:secret:AmazonMSK_hc-scm-msk_DLV-r31ySM"
}

resource "aws_route53_zone" "dlv_msk_broker_zone" {
  name = "qhxzwx.c3.kafka.ap-northeast-2.amazonaws.com"

  vpc {
    vpc_id = aws_vpc.dlv_vpc.id
  }

  comment = "DLV에서 MSK 브로커 도메인을 풀기 위한 프라이빗 존 (VPC Peering DNS 한계 우회)"
}

resource "aws_route53_record" "dlv_msk_broker_a" {
  for_each = local.msk_broker_ips

  zone_id = aws_route53_zone.dlv_msk_broker_zone.zone_id
  name    = "${each.key}.qhxzwx.c3.kafka.ap-northeast-2.amazonaws.com"
  type    = "A"
  ttl     = 60
  records = [each.value]
}

resource "aws_ssm_parameter" "msk_bootstrap_brokers" {
  name        = "/dlv/msk/bootstrap-brokers-sasl-scram"
  description = "MSK bootstrap brokers (SASL/SCRAM over TLS, private endpoint, port 9096)"
  type        = "String"
  value       = local.msk_bootstrap_brokers_sasl_scram

  tags = {
    Environment = "dev"
  }
}

resource "aws_iam_role_policy" "eks_node_msk_scram_access" {
  name = "msk-scram-auth-access"
  role = aws_iam_role.dlv_eks_node_role.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid      = "ReadBootstrapBrokerParameter"
        Effect   = "Allow"
        Action   = "ssm:GetParameter"
        Resource = aws_ssm_parameter.msk_bootstrap_brokers.arn
      },
      {
        Sid      = "ReadScramAuthSecret"
        Effect   = "Allow"
        Action   = "secretsmanager:GetSecretValue"
        Resource = var.msk_scram_secret_arn
      }
    ]
  })
}

# ==========================================
# 10. Helm Provider & Controller / ExternalDNS 배포
# ==========================================

provider "helm" {
  kubernetes {
    host                   = aws_eks_cluster.dlv_eks.endpoint
    cluster_ca_certificate = base64decode(aws_eks_cluster.dlv_eks.certificate_authority[0].data)

    exec {
      api_version = "client.authentication.k8s.io/v1beta1"
      args        = ["eks", "get-token", "--cluster-name", aws_eks_cluster.dlv_eks.name]
      command     = "aws"
    }
  }
}

resource "helm_release" "aws_load_balancer_controller" {
  name             = "aws-load-balancer-controller"
  repository       = "https://aws.github.io/eks-charts"
  chart            = "aws-load-balancer-controller"
  version          = "1.8.1"
  namespace        = "kube-system"
  timeout          = 600

  set {
    name  = "clusterName"
    value = aws_eks_cluster.dlv_eks.name
  }

  set {
    name  = "serviceAccount.create"
    value = "true"
  }

  set {
    name  = "serviceAccount.name"
    value = "aws-load-balancer-controller"
  }

  set {
    name  = "region"
    value = "ap-northeast-2"
  }

  set {
    name  = "vpcId"
    value = aws_vpc.dlv_vpc.id
  }

  set {
    name  = "hostNetwork"
    value = "true"
  }

  depends_on = [
    aws_eks_node_group.dlv_node_group
  ]
}

resource "helm_release" "external_dns" {
  name             = "external-dns"
  repository       = "https://kubernetes-sigs.github.io/external-dns"
  chart            = "external-dns"
  version          = "1.15.2"
  namespace        = "kube-system"
  create_namespace = false
  timeout          = 600

  set {
    name  = "provider.name"
    value = "aws"
  }

  set {
    name  = "aws.zoneType"
    value = "public"
  }

  set {
    name  = "txtOwnerId"
    value = data.aws_route53_zone.scm_zone.zone_id
  }

  set {
    name  = "policy"
    value = "sync"
  }

  set {
    name  = "env[0].name"
    value = "AWS_REGION"
  }

  set {
    name  = "env[0].value"
    value = "ap-northeast-2"
  }

  depends_on = [
    aws_eks_node_group.dlv_node_group,
    aws_iam_role_policy.eks_node_route53_access
  ]
}

resource "local_file" "external_dns_patch" {
  filename = "${path.module}/patch.json"
  content = jsonencode({
    spec = {
      template = {
        spec = {
          hostNetwork = true
        }
      }
    }
  })
}

resource "null_resource" "patch_external_dns" {
  depends_on = [helm_release.external_dns, local_file.external_dns_patch]

  provisioner "local-exec" {
    interpreter = ["PowerShell", "-Command"]
    command     = "kubectl patch deployment external-dns -n kube-system --type=merge --patch-file '${path.module}/patch.json'"
  }
}

# ==========================================
# 11. Kubernetes Provider & Manifest 자동 배포
# ==========================================

provider "kubernetes" {
  host                   = aws_eks_cluster.dlv_eks.endpoint
  cluster_ca_certificate = base64decode(aws_eks_cluster.dlv_eks.certificate_authority[0].data)

  exec {
    api_version = "client.authentication.k8s.io/v1beta1"
    args        = ["eks", "get-token", "--cluster-name", aws_eks_cluster.dlv_eks.name]
    command     = "aws"
  }
}

resource "kubernetes_manifest" "app_deployment" {
  manifest = yamldecode(file("C:/teamproject/IntelliJ_IDEA/scm-delivery/k8s/deployment.yaml"))

  depends_on = [aws_eks_node_group.dlv_node_group]
}

resource "kubernetes_manifest" "app_service" {
  manifest = yamldecode(file("C:/teamproject/IntelliJ_IDEA/scm-delivery/k8s/service.yaml"))

  depends_on = [kubernetes_manifest.app_deployment]
}

resource "kubernetes_manifest" "app_ingress" {
  manifest = yamldecode(file("C:/teamproject/IntelliJ_IDEA/scm-delivery/k8s/ingress.yaml"))

  depends_on = [
    kubernetes_manifest.app_service,
    helm_release.aws_load_balancer_controller
  ]
}

# ==========================================
# 12. ACM 인증서 발급 및 DNS 검증 추가
# ==========================================

resource "aws_acm_certificate" "cert" {
  domain_name       = "dlv.project2-hc-scm.cloud"
  validation_method = "DNS"

  tags = {
    Name = "dlv-cert"
  }
}

resource "aws_route53_record" "cert_validation" {
  for_each = {
    for dvo in aws_acm_certificate.cert.domain_validation_options : dvo.domain_name => {
      name   = dvo.resource_record_name
      record = dvo.resource_record_value
      type   = dvo.resource_record_type
    }
  }

  allow_overwrite = true
  name            = each.value.name
  records         = [each.value.record]
  ttl             = 60
  type            = each.value.type
  zone_id         = data.aws_route53_zone.scm_zone.zone_id
}

resource "aws_acm_certificate_validation" "cert" {
  certificate_arn         = aws_acm_certificate.cert.arn
  validation_record_fqdns = [for record in aws_route53_record.cert_validation : record.fqdn]
}