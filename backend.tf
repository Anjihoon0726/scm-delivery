terraform {
  backend "s3" {
    bucket       = "hc-scm-terraform-state-ap-northeast-2"
    key          = "dlv/terraform.tfstate"
    region       = "ap-northeast-2"
    use_lockfile = true
    encrypt      = true
  }
}