# AWS Managed Prometheus Workspace
resource "aws_prometheus_workspace" "main" {
  alias = var.workspace_alias

  tags = {
    Name    = "${var.project_name}-prometheus"
    Project = var.project_name
  }
}

# AWS Managed Grafana Workspace
resource "aws_grafana_workspace" "main" {
  name                     = var.grafana_workspace_name
  account_access_type      = "CURRENT_ACCOUNT"
  authentication_providers = ["AWS_SSO"]
  permission_type          = "SERVICE_MANAGED"
  data_sources             = ["PROMETHEUS"]
  role_arn                 = aws_iam_role.grafana.arn

  tags = {
    Name    = "${var.project_name}-grafana"
    Project = var.project_name
  }
}

# IAM Role for Grafana
resource "aws_iam_role" "grafana" {
  name = "${var.project_name}-grafana-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Principal = {
          Service = "grafana.amazonaws.com"
        }
        Action = "sts:AssumeRole"
      }
    ]
  })

  tags = {
    Name    = "${var.project_name}-grafana-role"
    Project = var.project_name
  }
}

# IAM Policy for Grafana to Query Prometheus
resource "aws_iam_policy" "grafana_prometheus_query" {
  name        = "${var.project_name}-grafana-prometheus-query"
  description = "Allow Grafana to query AWS Managed Prometheus"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "aps:QueryMetrics",
          "aps:GetSeries",
          "aps:GetLabels",
          "aps:GetMetricMetadata"
        ]
        Resource = aws_prometheus_workspace.main.arn
      }
    ]
  })

  tags = {
    Name    = "${var.project_name}-grafana-prometheus-query"
    Project = var.project_name
  }
}

# Attach Policy to Grafana Role
resource "aws_iam_role_policy_attachment" "grafana_prometheus_query" {
  role       = aws_iam_role.grafana.name
  policy_arn = aws_iam_policy.grafana_prometheus_query.arn
}

# IAM Policy for ADOT to Remote Write to Prometheus
resource "aws_iam_policy" "prometheus_remote_write" {
  name        = "${var.project_name}-prometheus-remote-write"
  description = "Allow ADOT Collector to remote write to AWS Managed Prometheus"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "aps:RemoteWrite",
          "aps:GetSeries",
          "aps:GetLabels",
          "aps:GetMetricMetadata"
        ]
        Resource = aws_prometheus_workspace.main.arn
      }
    ]
  })

  tags = {
    Name    = "${var.project_name}-prometheus-remote-write"
    Project = var.project_name
  }
}

# SSM Parameter for ADOT Configuration
resource "aws_ssm_parameter" "adot_config" {
  name        = "/${var.project_name}/adot-collector-config"
  description = "ADOT Collector configuration for Prometheus metrics"
  type        = "String"
  value = templatefile("${path.module}/../../adot-config.yaml", {
    PROMETHEUS_REMOTE_WRITE_URL = "${aws_prometheus_workspace.main.prometheus_endpoint}api/v1/remote_write"
  })

  tags = {
    Name    = "${var.project_name}-adot-config"
    Project = var.project_name
  }
}

