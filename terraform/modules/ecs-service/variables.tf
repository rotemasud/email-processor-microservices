variable "project_name" {
  description = "Name of the project"
  type        = string
}

variable "service_name" {
  description = "Name of the service"
  type        = string
}

variable "aws_region" {
  description = "AWS region"
  type        = string
}

variable "vpc_id" {
  description = "ID of the VPC"
  type        = string
}

variable "public_subnet_ids" {
  description = "List of public subnet IDs for ALB"
  type        = list(string)
}

variable "private_subnet_ids" {
  description = "List of private subnet IDs for ECS tasks"
  type        = list(string)
}

variable "cluster_id" {
  description = "ID of the ECS cluster"
  type        = string
}

variable "cluster_name" {
  description = "Name of the ECS cluster"
  type        = string
}

variable "container_image" {
  description = "Docker image to use (defaults to ECR repository latest)"
  type        = string
  default     = ""
}

variable "container_port" {
  description = "Port the container listens on"
  type        = number
  default     = 8080
}

variable "cpu" {
  description = "CPU units for the task (256, 512, 1024, 2048, 4096)"
  type        = number
  default     = 512
}

variable "memory" {
  description = "Memory for the task in MB"
  type        = number
  default     = 1024
}

variable "desired_count" {
  description = "Desired number of tasks"
  type        = number
  default     = 1
}

variable "min_capacity" {
  description = "Minimum number of tasks for auto scaling"
  type        = number
  default     = 1
}

variable "max_capacity" {
  description = "Maximum number of tasks for auto scaling"
  type        = number
  default     = 10
}

variable "enable_load_balancer" {
  description = "Enable Application Load Balancer"
  type        = bool
  default     = true
}

variable "health_check_path" {
  description = "Health check path for ALB target group"
  type        = string
  default     = "/api/health"
}

variable "health_check_healthy_threshold" {
  description = "Number of consecutive health check successes required"
  type        = number
  default     = 2
}

variable "health_check_unhealthy_threshold" {
  description = "Number of consecutive health check failures required"
  type        = number
  default     = 2
}

variable "health_check_interval" {
  description = "Health check interval in seconds"
  type        = number
  default     = 30
}

variable "health_check_timeout" {
  description = "Health check timeout in seconds"
  type        = number
  default     = 5
}

variable "health_check_matcher" {
  description = "HTTP status codes to consider healthy"
  type        = string
  default     = "200"
}

variable "log_retention_days" {
  description = "CloudWatch log retention in days"
  type        = number
  default     = 7
}

variable "environment_variables" {
  description = "Environment variables for the container"
  type = list(object({
    name  = string
    value = string
  }))
  default = []
}

variable "sqs_queue_arns" {
  description = "List of SQS queue ARNs to grant access to"
  type        = list(string)
  default     = []
}

variable "s3_bucket_arn" {
  description = "S3 bucket ARN to grant access to"
  type        = string
  default     = ""
}

variable "ssm_parameter_arns" {
  description = "List of SSM parameter ARNs to grant access to"
  type        = list(string)
  default     = []
}

variable "autoscaling_metric_type" {
  description = "Metric type for autoscaling"
  type        = string
  default     = "ECSServiceAverageCPUUtilization"
}

variable "autoscaling_target_value" {
  description = "Target value for autoscaling metric"
  type        = number
  default     = 70.0
}

variable "prometheus_remote_write_url" {
  description = "AWS Managed Prometheus remote write endpoint URL"
  type        = string
  default     = ""
}

variable "prometheus_remote_write_policy_arn" {
  description = "ARN of the Prometheus remote write IAM policy"
  type        = string
  default     = ""
}

variable "enable_prometheus" {
  description = "Enable Prometheus metrics collection with ADOT sidecar"
  type        = bool
  default     = true
}

