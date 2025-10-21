variable "aws_region" {
  description = "AWS region"
  type        = string
  default     = "us-west-1"
}

variable "availability_zones" {
  description = "Availability zones to use"
  type        = list(string)
  default     = ["us-west-1a", "us-west-1c"]
}

variable "project_name" {
  description = "Name of the project"
  type        = string
  default     = "email-processor"
}

variable "api_token" {
  description = "API token for authentication (must be provided in terraform.tfvars or as TF_VAR_api_token)"
  type        = string
  sensitive   = true
}

variable "grafana_admin_password" {
  description = "Admin password for Grafana (must be provided in terraform.tfvars or as TF_VAR_grafana_admin_password)"
  type        = string
  sensitive   = true
}

variable "environment" {
  description = "Environment name"
  type        = string
  default     = "dev"
}

variable "microservice_1_cpu" {
  description = "CPU units for microservice 1 (256, 512, 1024, 2048, 4096)"
  type        = number
  default     = 512
}

variable "microservice_1_memory" {
  description = "Memory for microservice 1 in MB"
  type        = number
  default     = 1024
}

variable "microservice_2_cpu" {
  description = "CPU units for microservice 2 (256, 512, 1024, 2048, 4096)"
  type        = number
  default     = 256
}

variable "microservice_2_memory" {
  description = "Memory for microservice 2 in MB"
  type        = number
  default     = 512
}

variable "desired_count" {
  description = "Desired number of tasks"
  type        = number
  default     = 1
}

variable "max_capacity" {
  description = "Maximum number of tasks for auto scaling"
  type        = number
  default     = 10
}

variable "min_capacity" {
  description = "Minimum number of tasks for auto scaling"
  type        = number
  default     = 1
}
