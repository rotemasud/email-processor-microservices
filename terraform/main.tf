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
module "storage" {
  source = "./modules/storage"

  project_name        = var.project_name
  bucket_name_prefix  = "${var.project_name}-email-storage"
  bucket_purpose      = "email-storage"
  enable_versioning   = true
  block_public_access = true
}

# Messaging Module
module "messaging" {
  source = "./modules/messaging"

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
      value = module.messaging.queue_url
    },
    {
      name  = "SSM_PARAMETER_NAME"
      value = aws_ssm_parameter.api_token.name
    }
  ]

  # IAM Permissions
  sqs_queue_arns = [
    module.messaging.queue_arn,
    module.messaging.dlq_arn
  ]
  ssm_parameter_arns = [aws_ssm_parameter.api_token.arn]
  s3_bucket_arn      = ""

  # Logging
  log_retention_days = 7
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
      value = module.messaging.queue_url
    },
    {
      name  = "S3_BUCKET_NAME"
      value = module.storage.bucket_name
    }
  ]

  # IAM Permissions
  sqs_queue_arns = [
    module.messaging.queue_arn,
    module.messaging.dlq_arn
  ]
  ssm_parameter_arns = []
  s3_bucket_arn      = module.storage.bucket_arn

  # Logging
  log_retention_days = 7
}
