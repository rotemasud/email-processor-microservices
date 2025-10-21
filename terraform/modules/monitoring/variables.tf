variable "project_name" {
  description = "Project name for resource naming"
  type        = string
}

variable "workspace_alias" {
  description = "Alias for the Prometheus workspace"
  type        = string
  default     = "email-processor-metrics"
}

variable "grafana_workspace_name" {
  description = "Name for the Grafana workspace"
  type        = string
  default     = "email-processor-grafana"
}

