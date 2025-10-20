variable "project_name" {
  description = "Name of the project"
  type        = string
}

variable "enable_container_insights" {
  description = "Enable Container Insights for the ECS cluster"
  type        = bool
  default     = true
}

