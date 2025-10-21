output "grafana_url" {
  description = "Grafana URL"
  value       = "http://${aws_lb.grafana.dns_name}"
}

output "grafana_alb_dns" {
  description = "Grafana ALB DNS name"
  value       = aws_lb.grafana.dns_name
}

output "grafana_security_group_id" {
  description = "Security group ID for Grafana tasks"
  value       = aws_security_group.grafana_task.id
}

output "efs_id" {
  description = "EFS file system ID for Grafana"
  value       = aws_efs_file_system.grafana.id
}

output "grafana_admin_password_ssm_parameter" {
  description = "SSM parameter name for Grafana admin password"
  value       = aws_ssm_parameter.grafana_password.name
}

output "dashboard_bucket_name" {
  description = "S3 bucket name for Grafana dashboards"
  value       = aws_s3_bucket.dashboards.id
}

output "dashboard_bucket_arn" {
  description = "S3 bucket ARN for Grafana dashboards"
  value       = aws_s3_bucket.dashboards.arn
}

