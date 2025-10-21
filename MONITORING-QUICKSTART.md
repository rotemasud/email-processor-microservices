# Monitoring Quick Start Guide

Fast track to get AWS Prometheus and Grafana monitoring up and running.

## Prerequisites

- ✅ Terraform infrastructure already deployed
- ✅ AWS CLI configured
- ✅ AWS SSO set up (or plan to use IAM authentication)

## 5-Minute Setup

### Step 1: Deploy Monitoring Infrastructure

```bash
cd terraform
terraform init -upgrade
terraform apply
```

**What this creates:**
- AWS Managed Prometheus workspace
- AWS Managed Grafana workspace  
- IAM roles and policies
- ADOT sidecars in ECS tasks

⏱️ **Time:** ~5-10 minutes

### Step 2: Get Grafana URL

```bash
terraform output grafana_workspace_endpoint
```

Copy this URL - you'll need it to access Grafana.

### Step 3: Configure AWS SSO (First Time Only)

If you haven't set up AWS SSO:

```bash
# 1. Enable AWS SSO in AWS Console
# Go to: AWS SSO → Enable AWS SSO

# 2. Create a user
# AWS SSO → Users → Add user

# 3. Assign user to Grafana workspace
# Amazon Managed Grafana → Workspaces → Your workspace → Assign user
```

⏱️ **Time:** ~5 minutes (first time only)

### Step 4: Access Grafana

1. Navigate to the Grafana URL from Step 2
2. Sign in with your AWS SSO credentials
3. You're in! 🎉

### Step 5: Import Dashboards

In Grafana:

1. Click **+** (Create) → **Import**
2. Click **Upload JSON file**
3. Import these three dashboards:
   - `grafana-dashboards/jvm-dashboard.json`
   - `grafana-dashboards/http-metrics-dashboard.json`
   - `grafana-dashboards/business-metrics-dashboard.json`
4. Select **Prometheus** as data source
5. Click **Import**

Repeat for each dashboard.

⏱️ **Time:** ~2 minutes

### Step 6: Deploy Updated Microservices

```bash
# The microservices need to be rebuilt with new dependencies

# Option A: Use CI/CD pipeline (recommended)
git add .
git commit -m "Add Prometheus and Grafana monitoring"
git push

# Option B: Manual build and deploy
cd microservice-1
mvn clean package
# Push to ECR and update ECS service

cd ../microservice-2
mvn clean package
# Push to ECR and update ECS service
```

⏱️ **Time:** ~10 minutes (via pipeline)

### Step 7: Verify Metrics

After ~1 minute, check that metrics are flowing:

1. In Grafana, go to **Explore**
2. Run this query:
   ```promql
   up{service=~"microservice.*"}
   ```
3. You should see both services with value `1`

✅ **You're all set!**

## What You Get

### 📊 Dashboards

1. **JVM Dashboard**
   - Memory usage and GC metrics
   - Thread counts
   - CPU utilization

2. **HTTP Metrics Dashboard**
   - Request rates
   - Response times (p50, p95, p99)
   - Error rates

3. **Business Metrics Dashboard**
   - SQS message flow
   - S3 upload metrics  
   - Validation statistics

### 📈 Metrics Available

**Microservice-1:**
- SQS messages sent/failed
- Validation success/failure rates
- API request metrics

**Microservice-2:**
- SQS messages received/processed
- S3 upload success/failure
- File size distribution

### 🎯 Metrics Endpoints

Both services expose metrics at:
```
http://<service>:8080/actuator/prometheus
```

## Quick Troubleshooting

### No data in dashboards?

**Check 1:** Are the services running?
```bash
aws ecs list-tasks --cluster <cluster-name> --service-name <service-name>
```

**Check 2:** Are metrics being exposed?
```bash
# For microservice-1 (has ALB)
curl http://$(terraform output -raw alb_dns_name)/actuator/prometheus
```

**Check 3:** ADOT collector logs
```bash
aws logs tail /ecs/<project-name>-microservice-1 --follow --filter "adot"
```

### Can't access Grafana?

- ✅ Check you're using the correct URL from `terraform output`
- ✅ Verify AWS SSO user is assigned to workspace
- ✅ Try incognito/private browsing mode

### Metrics showing but dashboards empty?

- ✅ Check time range (top right in Grafana)
- ✅ Verify Prometheus data source is configured
- ✅ Check query syntax in panel inspector

## Next Steps

### 1. Set Up Alerts

Create alerts for critical conditions:
- High error rates
- Memory usage > 90%
- SQS processing failures

**How:** Grafana → Alerting → Alert rules

### 2. Customize Dashboards

Add panels for:
- Specific endpoints you care about
- Custom business KPIs
- Team-specific metrics

### 3. Create a Monitoring Playlist

For NOC/status displays:
1. Grafana → Playlists → New playlist
2. Add all dashboards
3. Set rotation interval
4. Start playlist

### 4. Share Access

Add team members:
1. AWS SSO → Users → Add user
2. Assign to Grafana workspace
3. They can log in with their AWS SSO credentials

## Cost Estimate

Expected monthly cost:
- **Prometheus:** ~$20-30
- **Grafana:** $9 per active user
- **Total:** ~$40-60/month for 2-3 users

Reduce costs by:
- Adjusting scrape intervals
- Limiting active users
- Using recording rules for expensive queries

## Resources

- 📖 **Full Documentation:** [MONITORING.md](MONITORING.md)
- 📊 **Dashboard Guide:** [grafana-dashboards/README.md](grafana-dashboards/README.md)
- 🔧 **Implementation Details:** [PROMETHEUS-GRAFANA-SETUP.md](PROMETHEUS-GRAFANA-SETUP.md)
- 🏠 **Main README:** [README.md](README.md)

## Support

Having issues? Check:
1. [MONITORING.md](MONITORING.md) - Troubleshooting section
2. [AWS Managed Prometheus Docs](https://docs.aws.amazon.com/prometheus/)
3. [AWS Managed Grafana Docs](https://docs.aws.amazon.com/grafana/)

---

**Happy Monitoring! 📊✨**

