# Grafana Setup Guide

This guide explains how to access and configure Grafana running on ECS with AWS Managed Prometheus.

## Overview

Grafana is deployed as an ECS service on Fargate with:
- **Persistent Storage**: Amazon EFS for storing dashboards, users, and configurations
- **Load Balancer**: Application Load Balancer for external access
- **Data Source**: AWS Managed Prometheus with SigV4 authentication
- **Authentication**: Admin user with password stored in SSM Parameter Store

## Accessing Grafana

After deploying the infrastructure with Terraform, you can access Grafana via the ALB DNS name:

```bash
# Get the Grafana URL from Terraform outputs
terraform output grafana_url
```

The output will look like: `http://email-processor-grafana-alb-XXXXXXXXXX.us-west-1.elb.amazonaws.com`

### Default Credentials

- **Username**: `admin`
- **Password**: Retrieved from SSM Parameter Store (see terraform.tfvars)

To retrieve the password:

```bash
aws ssm get-parameter --name /email-processor/grafana/admin-password --with-decryption --query 'Parameter.Value' --output text
```

## Configuring Prometheus Data Source

### Option 1: Manual Configuration via UI

1. Log in to Grafana
2. Navigate to **Configuration** → **Data Sources**
3. Click **Add data source**
4. Select **Prometheus**
5. Configure the following settings:
   - **Name**: `AWS Managed Prometheus`
   - **URL**: Get from Terraform output:
     ```bash
     terraform output prometheus_endpoint
     ```
   - Under **Auth**, enable **SigV4 auth**
   - **Default Region**: `us-west-1` (or your configured region)
   - **Auth Provider**: `AWS SDK Default`
6. Click **Save & Test**

### Option 2: Automated Configuration via API

Use the following script to automatically configure the datasource:

```bash
#!/bin/bash

# Get values from Terraform
GRAFANA_URL=$(terraform output -raw grafana_url)
GRAFANA_PASSWORD=$(aws ssm get-parameter --name /email-processor/grafana/admin-password --with-decryption --query 'Parameter.Value' --output text)
PROMETHEUS_ENDPOINT=$(terraform output -raw prometheus_endpoint)
AWS_REGION="us-west-1"  # or your configured region

# Create datasource via API
curl -X POST "${GRAFANA_URL}/api/datasources" \
  -H "Content-Type: application/json" \
  -u "admin:${GRAFANA_PASSWORD}" \
  -d @- <<EOF
{
  "name": "AWS Managed Prometheus",
  "type": "prometheus",
  "url": "${PROMETHEUS_ENDPOINT}",
  "access": "proxy",
  "isDefault": true,
  "jsonData": {
    "httpMethod": "POST",
    "sigV4Auth": true,
    "sigV4AuthType": "default",
    "sigV4Region": "${AWS_REGION}"
  }
}
EOF
```

Save this script as `configure-grafana-datasource.sh`, make it executable, and run it:

```bash
chmod +x configure-grafana-datasource.sh
./configure-grafana-datasource.sh
```

## Importing Dashboards

The project includes pre-configured dashboards in the `grafana-dashboards/` directory:

1. **business-metrics-dashboard.json** - Email processing metrics
2. **http-metrics-dashboard.json** - HTTP request metrics
3. **jvm-dashboard.json** - JVM performance metrics

### Import via UI

1. Navigate to **Dashboards** → **Import**
2. Click **Upload JSON file**
3. Select a dashboard file from `grafana-dashboards/`
4. Select the **AWS Managed Prometheus** data source
5. Click **Import**

### Import via API

```bash
#!/bin/bash

GRAFANA_URL=$(terraform output -raw grafana_url)
GRAFANA_PASSWORD=$(aws ssm get-parameter --name /email-processor/grafana/admin-password --with-decryption --query 'Parameter.Value' --output text)

# Import each dashboard
for dashboard in grafana-dashboards/*.json; do
  echo "Importing $(basename $dashboard)..."
  
  curl -X POST "${GRAFANA_URL}/api/dashboards/db" \
    -H "Content-Type: application/json" \
    -u "admin:${GRAFANA_PASSWORD}" \
    -d @- <<EOF
{
  "dashboard": $(cat $dashboard),
  "overwrite": true
}
EOF
done
```

## Updating Dashboards

The dashboards are stored in EFS, so they persist across container restarts. To update dashboards:

1. Make changes in Grafana UI
2. Export the dashboard JSON
3. Update the corresponding file in `grafana-dashboards/`
4. Commit to version control

## Architecture Details

### Persistent Storage (EFS)

Grafana data is stored on an encrypted EFS file system mounted at `/var/lib/grafana`. This includes:
- Dashboards
- User accounts and permissions
- Data source configurations
- Organization settings
- Plugins

### High Availability

The current configuration runs a single Grafana instance (`desired_count = 1`). For production:

1. Increase `desired_count` in `terraform/main.tf`:
   ```hcl
   module "grafana" {
     # ...
     desired_count = 2  # Run 2 instances
   }
   ```

2. Grafana with EFS supports multiple instances sharing the same storage

### Security

- **Network**: Grafana runs in private subnets, accessible only via ALB
- **Authentication**: AWS IAM with SigV4 for Prometheus access
- **Encryption**: EFS encryption at rest and in transit
- **Secrets**: Admin password stored in SSM Parameter Store (SecureString)

## Monitoring Grafana

Grafana logs are sent to CloudWatch Logs:

```bash
aws logs tail /ecs/email-processor-grafana --follow
```

View Grafana container health:

```bash
aws ecs describe-services \
  --cluster email-processor-cluster \
  --services email-processor-grafana
```

## Troubleshooting

### Grafana Won't Start

Check ECS task logs:
```bash
aws logs tail /ecs/email-processor-grafana --follow
```

Check EFS mount targets are available:
```bash
aws efs describe-mount-targets --file-system-id $(terraform output -raw efs_id)
```

### Can't Connect to Prometheus

Verify IAM permissions:
```bash
aws iam get-role-policy --role-name email-processor-grafana-task-role --policy-name grafana-prometheus-query
```

Test Prometheus endpoint:
```bash
PROMETHEUS_ENDPOINT=$(terraform output -raw prometheus_endpoint)
aws amp query --workspace-id $(terraform output -raw prometheus_workspace_id) --query-string "up" --start-time $(date -u +%s -d '5 minutes ago') --end-time $(date -u +%s)
```

### Dashboard Not Loading Data

1. Verify data source is configured correctly
2. Check that SigV4 auth is enabled
3. Verify IAM role has Prometheus query permissions
4. Check that metrics are being collected by ADOT

## Cost Optimization

Grafana on ECS costs include:
- **Fargate**: ~$20-30/month (512 CPU, 1GB RAM, single instance)
- **EFS**: ~$0.30/GB/month (typically <1GB for Grafana data)
- **ALB**: ~$16/month base + data transfer

To reduce costs:
- Use smaller Fargate task size if sufficient
- Consider running Grafana only during business hours
- Use HTTPS and CloudFront for better caching

## Next Steps

- Enable HTTPS with ACM certificate
- Set up CloudWatch alerts for Grafana service health
- Configure SMTP for Grafana alerting
- Set up SSO/SAML authentication
- Create custom dashboards for your metrics

## References

- [Grafana Documentation](https://grafana.com/docs/)
- [AWS Managed Prometheus](https://docs.aws.amazon.com/prometheus/)
- [Grafana with SigV4](https://grafana.com/docs/grafana/latest/datasources/prometheus/#sigv4-authentication)

