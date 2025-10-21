variable "project_name" {
  description = "Project name for resource naming"
  type        = string
}

variable "vpc_id" {
  description = "VPC ID where Grafana will be deployed"
  type        = string
}

variable "public_subnet_ids" {
  description = "Public subnet IDs for ALB"
  type        = list(string)
}

variable "private_subnet_ids" {
  description = "Private subnet IDs for ECS tasks"
  type        = list(string)
}

variable "cluster_id" {
  description = "ECS Cluster ID"
  type        = string
}

variable "cluster_name" {
  description = "ECS Cluster name"
  type        = string
}

variable "prometheus_endpoint" {
  description = "AWS Managed Prometheus endpoint"
  type        = string
}

variable "prometheus_query_policy_arn" {
  description = "ARN of IAM policy to query Prometheus"
  type        = string
}

variable "aws_region" {
  description = "AWS region"
  type        = string
}

variable "grafana_admin_password" {
  description = "Admin password for Grafana"
  type        = string
  sensitive   = true
}

variable "cpu" {
  description = "CPU units for Grafana task"
  type        = number
  default     = 512
}

variable "memory" {
  description = "Memory for Grafana task in MB"
  type        = number
  default     = 1024
}

variable "desired_count" {
  description = "Desired number of Grafana tasks"
  type        = number
  default     = 1
}

