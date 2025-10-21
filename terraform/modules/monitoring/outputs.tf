output "prometheus_workspace_id" {
  description = "ID of the Prometheus workspace"
  value       = aws_prometheus_workspace.main.id
}

output "prometheus_workspace_arn" {
  description = "ARN of the Prometheus workspace"
  value       = aws_prometheus_workspace.main.arn
}

output "prometheus_endpoint" {
  description = "Prometheus workspace endpoint"
  value       = aws_prometheus_workspace.main.prometheus_endpoint
}

output "prometheus_remote_write_url" {
  description = "Prometheus remote write URL"
  value       = "${aws_prometheus_workspace.main.prometheus_endpoint}api/v1/remote_write"
}

output "grafana_workspace_id" {
  description = "ID of the Grafana workspace"
  value       = aws_grafana_workspace.main.id
}

output "grafana_workspace_endpoint" {
  description = "Grafana workspace endpoint URL"
  value       = aws_grafana_workspace.main.endpoint
}

output "grafana_role_arn" {
  description = "ARN of the Grafana IAM role"
  value       = aws_iam_role.grafana.arn
}

output "prometheus_remote_write_policy_arn" {
  description = "ARN of the Prometheus remote write policy"
  value       = aws_iam_policy.prometheus_remote_write.arn
}

output "adot_config_parameter_name" {
  description = "SSM Parameter name for ADOT configuration"
  value       = aws_ssm_parameter.adot_config.name
}

output "adot_config_parameter_arn" {
  description = "ARN of the ADOT configuration SSM parameter"
  value       = aws_ssm_parameter.adot_config.arn
}

