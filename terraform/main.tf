terraform {
  required_version = ">= 1.0"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

provider "aws" {
  region = var.aws_region
}

# Data sources
data "aws_caller_identity" "current" {}

# VPC
resource "aws_vpc" "main" {
  cidr_block           = "10.0.0.0/16"
  enable_dns_hostnames = true
  enable_dns_support   = true

  tags = {
    Name = "${var.project_name}-vpc"
  }
}

# Internet Gateway
resource "aws_internet_gateway" "main" {
  vpc_id = aws_vpc.main.id

  tags = {
    Name = "${var.project_name}-igw"
  }
}

# Public Subnets
resource "aws_subnet" "public" {
  count = 2

  vpc_id                  = aws_vpc.main.id
  cidr_block              = "10.0.${count.index + 1}.0/24"
  availability_zone       = var.availability_zones[count.index]
  map_public_ip_on_launch = true

  tags = {
    Name = "${var.project_name}-public-subnet-${count.index + 1}"
    Type = "Public"
  }
}

# Private Subnets
resource "aws_subnet" "private" {
  count = 2

  vpc_id            = aws_vpc.main.id
  cidr_block        = "10.0.${count.index + 10}.0/24"
  availability_zone = var.availability_zones[count.index]

  tags = {
    Name = "${var.project_name}-private-subnet-${count.index + 1}"
    Type = "Private"
  }
}

# NAT Gateway
resource "aws_eip" "nat" {
  count = 1

  domain = "vpc"
  depends_on = [aws_internet_gateway.main]

  tags = {
    Name = "${var.project_name}-nat-eip"
  }
}

resource "aws_nat_gateway" "main" {
  count = 1

  allocation_id = aws_eip.nat[0].id
  subnet_id     = aws_subnet.public[0].id

  tags = {
    Name = "${var.project_name}-nat-gateway"
  }

  depends_on = [aws_internet_gateway.main]
}

# Route Tables
resource "aws_route_table" "public" {
  vpc_id = aws_vpc.main.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.main.id
  }

  tags = {
    Name = "${var.project_name}-public-rt"
  }
}

resource "aws_route_table" "private" {
  count = 2

  vpc_id = aws_vpc.main.id

  route {
    cidr_block     = "0.0.0.0/0"
    nat_gateway_id = aws_nat_gateway.main[0].id
  }

  tags = {
    Name = "${var.project_name}-private-rt-${count.index + 1}"
  }
}

# Route Table Associations
resource "aws_route_table_association" "public" {
  count = 2

  subnet_id      = aws_subnet.public[count.index].id
  route_table_id = aws_route_table.public.id
}

resource "aws_route_table_association" "private" {
  count = 2

  subnet_id      = aws_subnet.private[count.index].id
  route_table_id = aws_route_table.private[count.index].id
}

# SQS Queue
resource "aws_sqs_queue" "email_queue" {
  name                       = "${var.project_name}-email-queue"
  visibility_timeout_seconds = 300
  message_retention_seconds  = 1209600
  receive_wait_time_seconds  = 20

  tags = {
    Name = "${var.project_name}-email-queue"
  }
}

# SQS Dead Letter Queue
resource "aws_sqs_queue" "email_dlq" {
  name                      = "${var.project_name}-email-dlq"
  message_retention_seconds = 1209600

  tags = {
    Name = "${var.project_name}-email-dlq"
  }
}

# SQS Redrive Policy
resource "aws_sqs_queue_redrive_policy" "email_queue" {
  queue_url = aws_sqs_queue.email_queue.id
  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.email_dlq.arn
    maxReceiveCount     = 3
  })
}

# S3 Bucket
resource "aws_s3_bucket" "email_storage" {
  bucket = "${var.project_name}-email-storage-${random_string.bucket_suffix.result}"

  tags = {
    Name = "${var.project_name}-email-storage"
  }
}

resource "random_string" "bucket_suffix" {
  length  = 8
  special = false
  upper   = false
}

resource "aws_s3_bucket_versioning" "email_storage" {
  bucket = aws_s3_bucket.email_storage.id
  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "email_storage" {
  bucket = aws_s3_bucket.email_storage.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_public_access_block" "email_storage" {
  bucket = aws_s3_bucket.email_storage.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# SSM Parameter for API Token
resource "aws_ssm_parameter" "api_token" {
  name  = "/email-processor/api-token"
  type  = "SecureString"
  value = var.api_token

  tags = {
    Name = "${var.project_name}-api-token"
  }
}

# ECR Repositories
resource "aws_ecr_repository" "microservice_1" {
  name                 = "${var.project_name}-microservice-1"
  image_tag_mutability = "MUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }

  tags = {
    Name = "${var.project_name}-microservice-1"
  }
}

resource "aws_ecr_repository" "microservice_2" {
  name                 = "${var.project_name}-microservice-2"
  image_tag_mutability = "MUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }

  tags = {
    Name = "${var.project_name}-microservice-2"
  }
}

# CloudWatch Log Groups
resource "aws_cloudwatch_log_group" "microservice_1" {
  name              = "/ecs/${var.project_name}-microservice-1"
  retention_in_days = 7

  tags = {
    Name = "${var.project_name}-microservice-1-logs"
  }
}

resource "aws_cloudwatch_log_group" "microservice_2" {
  name              = "/ecs/${var.project_name}-microservice-2"
  retention_in_days = 7

  tags = {
    Name = "${var.project_name}-microservice-2-logs"
  }
}
