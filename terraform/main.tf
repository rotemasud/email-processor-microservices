terraform {
  required_version = ">= 1.0"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.0"
    }
  }
}

provider "aws" {
  region = var.aws_region
}

# Data sources
data "aws_caller_identity" "current" {}

# Networking Module
module "networking" {
  source = "./modules/networking"

  project_name         = var.project_name
  availability_zones   = var.availability_zones
  vpc_cidr             = "10.0.0.0/16"
  public_subnet_cidrs  = ["10.0.1.0/24", "10.0.2.0/24"]
  private_subnet_cidrs = ["10.0.10.0/24", "10.0.11.0/24"]
  enable_nat_gateway   = true
}

# Storage Module
module "s3" {
  source = "./modules/s3"

  project_name        = var.project_name
  bucket_name_prefix  = "${var.project_name}-email-storage"
  bucket_purpose      = "email-storage"
  enable_versioning   = true
  block_public_access = true
}

# Messaging Module
module "sqs" {
  source = "./modules/sqs"

  project_name               = var.project_name
  queue_name                 = "email-queue"
  visibility_timeout_seconds = 300
  message_retention_seconds  = 1209600
  receive_wait_time_seconds  = 20
  max_receive_count          = 3
}

# SSM Parameter for API Token (shared resource)
resource "aws_ssm_parameter" "api_token" {
  name  = "/email-processor/api-token"
  type  = "SecureString"
  value = var.api_token

  tags = {
    Name = "${var.project_name}-api-token"
  }
}

# ECS Cluster Module
module "ecs_cluster" {
  source = "./modules/ecs-cluster"

  project_name              = var.project_name
  enable_container_insights = true
}

# Monitoring Module (Prometheus)
module "monitoring" {
  source = "./modules/monitoring"

  project_name    = var.project_name
  workspace_alias = "${var.project_name}-metrics"
}

# Grafana on ECS
module "grafana" {
  source = "./modules/grafana-ecs"

  project_name                 = var.project_name
  vpc_id                       = module.networking.vpc_id
  public_subnet_ids            = module.networking.public_subnet_ids
  private_subnet_ids           = module.networking.private_subnet_ids
  cluster_id                   = module.ecs_cluster.cluster_id
  cluster_name                 = module.ecs_cluster.cluster_name
  prometheus_endpoint          = module.monitoring.prometheus_endpoint
  prometheus_query_policy_arn  = module.monitoring.grafana_prometheus_query_policy_arn
  aws_region                   = var.aws_region
  grafana_admin_password       = var.grafana_admin_password

  # Resource configuration
  cpu           = 512
  memory        = 1024
  desired_count = 1
}

# Microservice 1 (API Service with ALB)
module "microservice_1" {
  source = "./modules/ecs-service"

  project_name       = var.project_name
  service_name       = "microservice-1"
  aws_region         = var.aws_region
  vpc_id             = module.networking.vpc_id
  public_subnet_ids  = module.networking.public_subnet_ids
  private_subnet_ids = module.networking.private_subnet_ids
  cluster_id         = module.ecs_cluster.cluster_id
  cluster_name       = module.ecs_cluster.cluster_name

  # Container Configuration
  container_port = 8080
  cpu            = var.microservice_1_cpu
  memory         = var.microservice_1_memory

  # Scaling Configuration
  desired_count = var.desired_count
  min_capacity  = var.min_capacity
  max_capacity  = var.max_capacity

  # Load Balancer Configuration
  enable_load_balancer = true
  health_check_path    = "/api/health"

  # Environment Variables
  environment_variables = [
    {
      name  = "AWS_DEFAULT_REGION"
      value = var.aws_region
    },
    {
      name  = "SQS_QUEUE_URL"
      value = module.sqs.queue_url
    },
    {
      name  = "SSM_PARAMETER_NAME"
      value = aws_ssm_parameter.api_token.name
    }
  ]

  # IAM Permissions
  sqs_queue_arns = [
    module.sqs.queue_arn,
    module.sqs.dlq_arn
  ]
  ssm_parameter_arns = [aws_ssm_parameter.api_token.arn]
  s3_bucket_arn      = ""

  # Logging
  log_retention_days = 7

  # Monitoring (Prometheus & Grafana)
  enable_prometheus                  = true
  prometheus_remote_write_url        = module.monitoring.prometheus_remote_write_url
  prometheus_remote_write_policy_arn = module.monitoring.prometheus_remote_write_policy_arn
}

# Microservice 2 (Consumer Service without ALB)
module "microservice_2" {
  source = "./modules/ecs-service"

  project_name       = var.project_name
  service_name       = "microservice-2"
  aws_region         = var.aws_region
  vpc_id             = module.networking.vpc_id
  public_subnet_ids  = module.networking.public_subnet_ids
  private_subnet_ids = module.networking.private_subnet_ids
  cluster_id         = module.ecs_cluster.cluster_id
  cluster_name       = module.ecs_cluster.cluster_name

  # Container Configuration
  container_port = 8080
  cpu            = var.microservice_2_cpu
  memory         = var.microservice_2_memory

  # Scaling Configuration
  desired_count = var.desired_count
  min_capacity  = var.min_capacity
  max_capacity  = var.max_capacity

  # No Load Balancer for this service
  enable_load_balancer = false

  # Environment Variables
  environment_variables = [
    {
      name  = "AWS_DEFAULT_REGION"
      value = var.aws_region
    },
    {
      name  = "SQS_QUEUE_URL"
      value = module.sqs.queue_url
    },
    {
      name  = "S3_BUCKET_NAME"
      value = module.s3.bucket_name
    }
  ]

  # IAM Permissions
  sqs_queue_arns = [
    module.sqs.queue_arn,
    module.sqs.dlq_arn
  ]
  ssm_parameter_arns = []
  s3_bucket_arn      = module.s3.bucket_arn

  # Logging
  log_retention_days = 7

  # Monitoring (Prometheus & Grafana)
  enable_prometheus                  = true
  prometheus_remote_write_url        = module.monitoring.prometheus_remote_write_url
  prometheus_remote_write_policy_arn = module.monitoring.prometheus_remote_write_policy_arn
}
