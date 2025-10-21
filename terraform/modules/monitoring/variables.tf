variable "project_name" {
  description = "Project name for resource naming"
  type        = string
}

variable "workspace_alias" {
  description = "Alias for the Prometheus workspace"
  type        = string
  default     = "email-processor-metrics"
}

