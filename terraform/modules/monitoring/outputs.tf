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

output "prometheus_query_url" {
  description = "Prometheus query URL for Grafana datasource"
  value       = "${aws_prometheus_workspace.main.prometheus_endpoint}api/v1/query"
}

output "grafana_prometheus_query_policy_arn" {
  description = "ARN of the IAM policy for Grafana to query Prometheus"
  value       = aws_iam_policy.grafana_prometheus_query.arn
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

