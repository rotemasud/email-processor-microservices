# Grafana Migration from AWS Managed to Self-Hosted on ECS

## Overview

The infrastructure has been updated to deploy Grafana as a self-hosted service on ECS Fargate instead of using AWS Managed Grafana. This change was made because AWS Managed Grafana is not available in the `us-west-1` region.

## What Changed

### Before (AWS Managed Grafana)
- AWS Managed Grafana workspace
- Authentication via AWS SSO
- Managed service with automatic updates
- Per-user pricing model
- Limited to specific AWS regions

### After (Grafana on ECS)
- Self-hosted Grafana running on ECS Fargate
- Admin password authentication (stored in SSM)
- Full control over Grafana version and configuration
- Predictable Fargate pricing (~$20-30/month)
- Works in any AWS region

## Benefits

1. **Regional Flexibility**: Works in `us-west-1` and any other AWS region
2. **Cost Predictability**: Fixed Fargate costs instead of per-user pricing
3. **Full Control**: Complete access to all Grafana features and plugins
4. **Persistent Storage**: Data stored on encrypted EFS
5. **Integration**: Seamless integration with AWS Managed Prometheus via SigV4

## Architecture Components

### Grafana ECS Service
- **Compute**: ECS Fargate (512 CPU, 1GB Memory)
- **Storage**: Encrypted Amazon EFS for persistence
- **Networking**: Private subnet with ALB for access
- **Authentication**: Admin user with SSM-stored password
- **IAM**: Task role with Prometheus query permissions

### Infrastructure Code
New Terraform module created: `terraform/modules/grafana-ecs/`
- EFS file system and mount targets
- Security groups for EFS, tasks, and ALB
- Application Load Balancer
- ECS task definition and service
- IAM roles and policies
- CloudWatch log group

## Deployment Steps

### 1. Update Terraform Configuration

The configuration has already been updated in the following files:
- `terraform/modules/monitoring/` - Removed AWS Managed Grafana
- `terraform/modules/grafana-ecs/` - New Grafana ECS module
- `terraform/main.tf` - Added Grafana module
- `terraform/variables.tf` - Added `grafana_admin_password` variable
- `terraform/terraform.tfvars` - Added Grafana password (change the default!)

### 2. Update Grafana Admin Password

**IMPORTANT**: Before deploying, update the Grafana admin password in `terraform.tfvars`:

```hcl
grafana_admin_password = "YourSecurePassword123!"
```

### 3. Deploy Infrastructure

If you have existing infrastructure deployed:

```bash
cd terraform

# Initialize Terraform (in case of new modules)
terraform init

# Review the changes
terraform plan

# Apply the changes
terraform apply
```

This will:
- Remove the AWS Managed Grafana workspace
- Create the EFS file system
- Deploy Grafana as an ECS service
- Create the Application Load Balancer

### 4. Access Grafana

After deployment completes:

```bash
# Get Grafana URL
terraform output grafana_url

# Get admin password
aws ssm get-parameter --name /email-processor/grafana/admin-password --with-decryption --query 'Parameter.Value' --output text
```

Visit the Grafana URL and log in with:
- Username: `admin`
- Password: (from SSM above)

### 5. Configure Prometheus Data Source

Option A - Manual Configuration:
1. Go to Configuration → Data Sources
2. Add Prometheus data source
3. Use the endpoint from: `terraform output prometheus_endpoint`
4. Enable SigV4 authentication
5. Set region to `us-west-1`

Option B - Automated via API:
See `GRAFANA-SETUP.md` for the automated configuration script.

### 6. Import Dashboards

Import the pre-built dashboards from `grafana-dashboards/`:
- `jvm-dashboard.json`
- `http-metrics-dashboard.json`
- `business-metrics-dashboard.json`

See `GRAFANA-SETUP.md` for detailed import instructions.

## Migration Checklist

If migrating from existing AWS Managed Grafana:

- [ ] Export existing custom dashboards from AWS Managed Grafana
- [ ] Note any custom alert rules
- [ ] Document custom data source configurations
- [ ] Update `terraform.tfvars` with secure Grafana password
- [ ] Run `terraform apply` to deploy new infrastructure
- [ ] Access new Grafana instance
- [ ] Configure Prometheus data source
- [ ] Import dashboards (pre-built + custom)
- [ ] Recreate alert rules
- [ ] Update team documentation with new Grafana URL
- [ ] Update any scripts/tools that referenced old Grafana endpoint

## Rollback Plan

If you need to rollback to AWS Managed Grafana (in a supported region):

1. Change region in `terraform.tfvars` to a supported region (e.g., `us-west-2`)
2. Restore the old monitoring module configuration
3. Run `terraform apply`

Note: This is only possible if you move to a region that supports AWS Managed Grafana.

## Cost Comparison

### AWS Managed Grafana (estimated)
- Base: ~$9/user/month
- For 5 users: ~$45/month

### Grafana on ECS (estimated)
- Fargate (512 CPU, 1GB): ~$25/month
- EFS storage (<1GB): ~$0.30/month
- ALB: ~$16/month
- **Total**: ~$41/month (unlimited users)

For small teams (1-3 users), the costs are comparable. For larger teams or higher usage, self-hosted is more cost-effective.

## Troubleshooting

### Grafana Won't Start
```bash
# Check service status
aws ecs describe-services --cluster email-processor-cluster --services email-processor-grafana

# Check logs
aws logs tail /ecs/email-processor-grafana --follow
```

### Can't Access Grafana
- Verify ALB security group allows inbound port 80
- Check that Grafana tasks are running in private subnets
- Verify EFS mount targets are in correct subnets

### Prometheus Data Source Not Working
- Ensure SigV4 auth is enabled
- Verify task role has `aps:QueryMetrics` permission
- Check Prometheus endpoint URL is correct

## Support and Documentation

- **Setup Guide**: [GRAFANA-SETUP.md](GRAFANA-SETUP.md)
- **Monitoring Guide**: [MONITORING.md](MONITORING.md)
- **Main README**: [README.md](README.md)

## Next Steps

After deployment:
1. Change the default admin password
2. Create additional Grafana users (optional)
3. Set up HTTPS with ACM certificate (optional)
4. Configure SMTP for email alerts (optional)
5. Explore Grafana plugins and customize dashboards

