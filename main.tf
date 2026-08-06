terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
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

resource "aws_vpc" "scm_vpc" {
  cidr_block           = "10.50.0.0/16"
  enable_dns_hostnames = true
  enable_dns_support   = true

  tags = {
    Name = "scm-delivery-vpc"
  }
}

resource "aws_internet_gateway" "scm_igw" {
  vpc_id = aws_vpc.scm_vpc.id

  tags = {
    Name = "scm-delivery-igw"
  }
}

# NAT Gateway (외부 택배사 REST API 연동용)
resource "aws_eip" "nat_eip" {
  domain = "vpc"

  tags = {
    Name = "scm-nat-eip"
  }
}

resource "aws_nat_gateway" "scm_nat" {
  allocation_id = aws_eip.nat_eip.id
  subnet_id     = aws_subnet.scm_public_subnet_a.id

  tags = {
    Name = "scm-delivery-nat"
  }

  depends_on = [aws_internet_gateway.scm_igw]
}

# 서브넷 설정
resource "aws_subnet" "scm_public_subnet_a" {
  vpc_id                  = aws_vpc.scm_vpc.id
  cidr_block              = "10.50.10.0/24"
  availability_zone       = "ap-northeast-2a"
  map_public_ip_on_launch = true

  tags = {
    Name                                     = "scm-public-subnet-2a"
    "kubernetes.io/role/elb"                = "1"
    "kubernetes.io/cluster/scm-delivery-eks" = "shared"
  }
}

resource "aws_subnet" "scm_public_subnet_c" {
  vpc_id                  = aws_vpc.scm_vpc.id
  cidr_block              = "10.50.11.0/24"
  availability_zone       = "ap-northeast-2c"
  map_public_ip_on_launch = true

  tags = {
    Name                                     = "scm-public-subnet-2c"
    "kubernetes.io/role/elb"                = "1"
    "kubernetes.io/cluster/scm-delivery-eks" = "shared"
  }
}

resource "aws_subnet" "scm_private_subnet_a" {
  vpc_id            = aws_vpc.scm_vpc.id
  cidr_block        = "10.50.20.0/24"
  availability_zone = "ap-northeast-2a"

  tags = {
    Name                                     = "scm-private-subnet-2a"
    "kubernetes.io/role/internal-elb"       = "1"
    "kubernetes.io/cluster/scm-delivery-eks" = "shared"
  }
}

resource "aws_subnet" "scm_private_subnet_c" {
  vpc_id            = aws_vpc.scm_vpc.id
  cidr_block        = "10.50.21.0/24"
  availability_zone = "ap-northeast-2c"

  tags = {
    Name                                     = "scm-private-subnet-2c"
    "kubernetes.io/role/internal-elb"       = "1"
    "kubernetes.io/cluster/scm-delivery-eks" = "shared"
  }
}

# 퍼블릭 라우팅 테이블
resource "aws_route_table" "scm_public_rt" {
  vpc_id = aws_vpc.scm_vpc.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.scm_igw.id
  }

  route {
    cidr_block                = "10.100.0.0/16"
    vpc_peering_connection_id = aws_vpc_peering_connection.msk_peering.id
  }

  tags = {
    Name = "scm-public-rt"
  }
}

resource "aws_route_table_association" "scm_rta_pub_a" {
  subnet_id      = aws_subnet.scm_public_subnet_a.id
  route_table_id = aws_route_table.scm_public_rt.id
}

resource "aws_route_table_association" "scm_rta_pub_c" {
  subnet_id      = aws_subnet.scm_public_subnet_c.id
  route_table_id = aws_route_table.scm_public_rt.id
}

# 프라이빗 라우팅 테이블 (NAT GW 통한 아웃바운드 인터넷 제공)
resource "aws_route_table" "scm_private_rt" {
  vpc_id = aws_vpc.scm_vpc.id

  route {
    cidr_block     = "0.0.0.0/0"
    nat_gateway_id = aws_nat_gateway.scm_nat.id
  }

  route {
    cidr_block                = "10.100.0.0/16"
    vpc_peering_connection_id = aws_vpc_peering_connection.msk_peering.id
  }

  tags = {
    Name = "scm-private-rt"
  }
}

resource "aws_route_table_association" "scm_rta_pri_a" {
  subnet_id      = aws_subnet.scm_private_subnet_a.id
  route_table_id = aws_route_table.scm_private_rt.id
}

resource "aws_route_table_association" "scm_rta_pri_c" {
  subnet_id      = aws_subnet.scm_private_subnet_c.id
  route_table_id = aws_route_table.scm_private_rt.id
}

resource "aws_db_subnet_group" "scm_rds_subnet_group" {
  name       = "scm-rds-subnet-group"
  subnet_ids = [aws_subnet.scm_private_subnet_a.id, aws_subnet.scm_private_subnet_c.id]

  tags = {
    Name = "scm-rds-subnet-group"
  }
}

resource "aws_elasticache_subnet_group" "scm_redis_subnet_group" {
  name       = "scm-redis-subnet-group"
  subnet_ids = [aws_subnet.scm_private_subnet_a.id, aws_subnet.scm_private_subnet_c.id]
}

# ==========================================
# 2. VPC Peering 연동 및 양방향 라우팅
# ==========================================

resource "aws_vpc_peering_connection" "msk_peering" {
  vpc_id      = aws_vpc.scm_vpc.id
  peer_vpc_id = data.aws_vpc.msk_vpc.id
  auto_accept = true

  tags = {
    Name = "scm-to-msk-peering"
  }
}

resource "aws_vpc_peering_connection_options" "msk_peering_options" {
  vpc_peering_connection_id = aws_vpc_peering_connection.msk_peering.id

  accepter {
    allow_remote_vpc_dns_resolution = true
  }

  requester {
    allow_remote_vpc_dns_resolution = true
  }
}

resource "aws_route" "msk_vpc_return_route" {
  for_each                  = toset(data.aws_route_tables.msk_route_tables.ids)
  route_table_id            = each.value
  destination_cidr_block    = "10.50.0.0/16"
  vpc_peering_connection_id = aws_vpc_peering_connection.msk_peering.id
}

# ==========================================
# 3. 보안 그룹(Security Group) 설정
# ==========================================

resource "aws_security_group" "rds_sg" {
  name        = "scm-rds-sg"
  description = "Security Group for Private PostgreSQL RDS"
  vpc_id      = aws_vpc.scm_vpc.id

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

resource "aws_security_group" "redis_sg" {
  name        = "scm-redis-sg"
  description = "Security Group for Private ElastiCache Redis"
  vpc_id      = aws_vpc.scm_vpc.id

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
  name                    = "scm/delivery/db-credentials"
  recovery_window_in_days = 0

  tags = {
    Environment = "dev"
    Service     = "scm-delivery"
  }
}

resource "aws_secretsmanager_secret_version" "db_secret_val" {
  secret_id = aws_secretsmanager_secret.db_secret.id
  secret_string = jsonencode({
    username = "dlv_admin"
    password = "dlv_1234"
    engine   = "postgres"
    host     = aws_db_instance.scm_postgres.address
    port     = 15432
    dbname   = "db_delivery"
  })
}

# ==========================================
# 5. Database & Cache 구축 (PostgreSQL & Redis)
# ==========================================

resource "aws_db_instance" "scm_postgres" {
  identifier             = "scm-postgres-db"
  allocated_storage      = 20
  max_allocated_storage  = 50
  engine                 = "postgres"
  engine_version         = "15"
  instance_class         = "db.t3.micro"
  port                   = 15432
  db_name                = "db_delivery"
  username               = "dlv_admin"
  password               = "dlv_1234"
  db_subnet_group_name   = aws_db_subnet_group.scm_rds_subnet_group.name
  vpc_security_group_ids = [aws_security_group.rds_sg.id]
  publicly_accessible    = false
  skip_final_snapshot    = true

  tags = {
    Name = "scm-delivery-postgres"
  }
}

resource "aws_elasticache_cluster" "scm_redis" {
  cluster_id           = "scm-redis-cluster"
  engine               = "redis"
  node_type            = "cache.t3.micro"
  num_cache_nodes      = 1
  parameter_group_name = "default.redis7"
  port                 = 16379
  subnet_group_name    = aws_elasticache_subnet_group.scm_redis_subnet_group.name
  security_group_ids   = [aws_security_group.redis_sg.id]

  tags = {
    Name = "scm-delivery-redis"
  }
}

# ==========================================
# 6. EKS IAM 역할 설정
# ==========================================

resource "aws_iam_role" "scm_eks_cluster_role" {
  name = "scm-eks-cluster-role"

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
  role       = aws_iam_role.scm_eks_cluster_role.name
}

resource "aws_iam_role" "scm_eks_node_role" {
  name = "scm-eks-node-role"

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
  role       = aws_iam_role.scm_eks_node_role.name
}

resource "aws_iam_role_policy_attachment" "eks_cni_policy" {
  policy_arn = "arn:aws:iam::aws:policy/AmazonEKS_CNI_Policy"
  role       = aws_iam_role.scm_eks_node_role.name
}

resource "aws_iam_role_policy_attachment" "eks_container_registry_policy" {
  policy_arn = "arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryReadOnly"
  role       = aws_iam_role.scm_eks_node_role.name
}

# ==========================================
# 7. EKS Control Plane & Node Group 생성
# ==========================================

resource "aws_eks_cluster" "scm_eks" {
  name     = "scm-delivery-eks"
  role_arn = aws_iam_role.scm_eks_cluster_role.arn
  version  = "1.31"

  vpc_config {
    subnet_ids = [
      aws_subnet.scm_private_subnet_a.id,
      aws_subnet.scm_private_subnet_c.id
    ]
    endpoint_private_access = true
    endpoint_public_access  = true
  }

  depends_on = [
    aws_iam_role_policy_attachment.eks_cluster_policy
  ]

  tags = {
    Name = "scm-delivery-eks"
  }
}

resource "aws_eks_node_group" "scm_node_group" {
  cluster_name    = aws_eks_cluster.scm_eks.name
  node_group_name = "scm-delivery-node-group"
  node_role_arn   = aws_iam_role.scm_eks_node_role.arn

  subnet_ids = [
    aws_subnet.scm_private_subnet_a.id,
    aws_subnet.scm_private_subnet_c.id
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
  ]

  tags = {
    Name = "scm-delivery-worker-node"
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
  name            = "db.${data.aws_route53_zone.scm_zone.name}"
  type            = "CNAME"
  ttl             = 300
  allow_overwrite = true

  records = [element(split(":", aws_db_instance.scm_postgres.endpoint), 0)]
}

resource "aws_route53_record" "redis_record" {
  zone_id         = data.aws_route53_zone.scm_zone.zone_id
  name            = "redis.${data.aws_route53_zone.scm_zone.name}"
  type            = "CNAME"
  ttl             = 300
  allow_overwrite = true

  records = [aws_elasticache_cluster.scm_redis.cache_nodes[0].address]
}

# ==========================================
# 9. MSK 연동
# ==========================================
# Route 53 프라이빗 존 연동은 담당자 확인 결과 zone 자체가 삭제되어 있어 조회가 불가능하므로 제거함
# (SCM/MSK는 동일 계정(967996001868)으로 확인됨 - 계정 문제는 아니었음).
# SASL/SCRAM 인증 + TLS 암호화(포트 9096, SASL_SSL) + AWS Secrets Manager 연동 시크릿(사용자명/비밀번호)
# 방식으로 연동한다. 같은 계정이므로 시크릿 리소스 정책 수정 없이 아래 IAM 정책만으로 접근 가능하다.
# 부트스트랩 브로커 주소는 VPC 피어링(aws_vpc_peering_connection.msk_peering)을 통한 사설 IP 통신으로 접근한다.
# (2026-08-06 기준, MSK 콘솔 "클라이언트 정보 보기"에서 확인한 SASL/SCRAM 프라이빗 엔드포인트,
#  브로커 IP가 바뀌면 이 값도 다시 갱신해야 함)

locals {
  msk_bootstrap_brokers_sasl_scram = "b-3.hcscmmsk.un6rv7.c3.kafka.ap-northeast-2.amazonaws.com:9096,b-1.hcscmmsk.un6rv7.c3.kafka.ap-northeast-2.amazonaws.com:9096,b-2.hcscmmsk.un6rv7.c3.kafka.ap-northeast-2.amazonaws.com:9096"
}

variable "msk_scram_secret_arn" {
  description = "MSK 클러스터에 연동된 SASL/SCRAM 인증용 Secrets Manager 시크릿 ARN (사용 시크릿: AmazonMSK_hc-scm-msk_DLV, SCM/MSK 동일 계정 967996001868 소유)"
  type        = string
  default     = "arn:aws:secretsmanager:ap-northeast-2:967996001868:secret:AmazonMSK_hc-scm-msk_DLV-r31ySM"
}

resource "aws_ssm_parameter" "msk_bootstrap_brokers" {
  name        = "/scm/msk/bootstrap-brokers-sasl-scram"
  description = "MSK bootstrap brokers (SASL/SCRAM over TLS, private endpoint, port 9096)"
  type        = "String"
  value       = local.msk_bootstrap_brokers_sasl_scram

  tags = {
    Environment = "dev"
  }
}

# 앱(EKS 노드 역할)이 부트스트랩 주소(SSM)와 SASL/SCRAM 인증 정보(Secrets Manager)를 읽을 수 있도록 권한 부여
resource "aws_iam_role_policy" "eks_node_msk_scram_access" {
  name = "msk-scram-auth-access"
  role = aws_iam_role.scm_eks_node_role.id

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