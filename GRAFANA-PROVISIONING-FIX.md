# Grafana Datasource and Dashboard Provisioning Fix

## Problem
Grafana was starting without any datasources or dashboards configured. The datasource configuration was stored in SSM but never provisioned to Grafana, and dashboards weren't being loaded.

## Solution Implemented

### 1. **S3 Bucket for Dashboards**
- Created an S3 bucket to store Grafana dashboard JSON files
- Automatically uploads all dashboard files from `grafana-dashboards/*.json` to S3
- Enabled versioning for dashboard history

### 2. **Automatic Provisioning on Container Start**
Added a startup script that runs when the Grafana container starts:
- Installs AWS CLI in the container
- Creates provisioning directories
- Generates datasource configuration with your Prometheus endpoint
- Generates dashboard provisioning configuration
- Downloads all dashboards from S3
- Starts Grafana with everything configured

### 3. **IAM Permissions**
- Added S3 read permissions for the Grafana task role
- Allows the container to download dashboards from S3

### 4. **Dashboard Auto-Loading**
Configured Grafana to:
- Load dashboards from `/var/lib/grafana/dashboards`
- Auto-refresh every 10 seconds
- Allow UI updates to dashboards

## What Gets Provisioned

### Datasource
- **Name:** AWS Managed Prometheus
- **Type:** Prometheus
- **Authentication:** AWS SigV4 (automatic)
- **Default:** Yes

### Dashboards
All JSON files from `grafana-dashboards/` directory:
- `business-metrics-dashboard.json` - Business metrics and email processing stats
- `http-metrics-dashboard.json` - HTTP request metrics
- `jvm-dashboard.json` - JVM and application metrics

## How to Apply

1. **Navigate to Terraform directory:**
   ```bash
   cd terraform
   ```

2. **Review the changes:**
   ```bash
   terraform plan
   ```

3. **Apply the configuration:**
   ```bash
   terraform apply
   ```

4. **Wait for deployment:**
   - Terraform will create the S3 bucket
   - Upload dashboard files to S3
   - Update the ECS task definition
   - ECS will automatically deploy a new task with the updated configuration

5. **Verify Grafana:**
   - Get the Grafana URL:
     ```bash
     terraform output grafana_url
     ```
   - Login with:
     - **Username:** `admin`
     - **Password:** `ChangeThisPassword123!` (from terraform.tfvars)
   - Check "Configuration → Data Sources" - should see "AWS Managed Prometheus"
   - Check "Dashboards → Browse" - should see all 3 dashboards

## What Changed

### Files Modified:
- `terraform/modules/grafana-ecs/main.tf`
  - Added S3 bucket for dashboards
  - Added S3 upload resources
  - Added S3 IAM permissions
  - Updated container with provisioning script
  - Added environment variables for Prometheus and S3

- `terraform/modules/grafana-ecs/outputs.tf`
  - Added dashboard bucket outputs

- `terraform/outputs.tf`
  - Exposed dashboard bucket name

## Troubleshooting

### If datasource doesn't appear:
1. Check CloudWatch logs:
   ```bash
   aws logs tail /ecs/email-processor-grafana --follow
   ```
2. Look for errors during provisioning
3. Verify Prometheus endpoint is accessible

### If dashboards don't load:
1. Check S3 bucket contains the JSON files:
   ```bash
   aws s3 ls s3://email-processor-grafana-dashboards/
   ```
2. Check container logs for S3 download errors
3. Verify IAM permissions allow S3 read access

### To update dashboards:
1. Modify the JSON files in `grafana-dashboards/` directory
2. Run `terraform apply` to upload updated files to S3
3. Restart the Grafana ECS task to download latest versions:
   ```bash
   aws ecs update-service \
     --cluster email-processor-cluster \
     --service email-processor-grafana \
     --force-new-deployment
   ```

## Next Steps

After applying, you should:
1. ✅ Login to Grafana
2. ✅ Verify datasource is connected (green check mark)
3. ✅ Open each dashboard and verify metrics are appearing
4. ✅ Customize dashboards as needed (changes persist in EFS)
5. ✅ Consider changing the admin password to something more secure

## Notes

- Dashboards are loaded from S3 on every container start
- Manual changes to dashboards in Grafana UI are saved to EFS (persist across restarts)
- To prevent manual changes, set `allowUiUpdates: false` in the provisioning config
- The startup script adds ~30 seconds to container startup time (due to AWS CLI install and dashboard download)

