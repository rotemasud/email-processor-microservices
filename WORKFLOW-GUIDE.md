# GitHub Actions Workflow Guide

This guide explains how to use the manual-only CI/CD workflows.

## 🎯 Overview

Both CI and CD workflows run **ONLY on manual trigger** to give you complete control over builds, deployments, and AWS costs.

---

## 🔨 CI Workflow: Build and Test

**Purpose**: Build Docker images, run tests, and optionally push to ECR.

### How to Run

1. Go to your GitHub repository
2. Click on the **Actions** tab
3. Select **"CI - Build and Test"** from the left sidebar
4. Click **"Run workflow"** button (top right)
5. Configure options:
   - **Branch**: Choose which branch to build from
   - **Skip ECR Push**: 
     - ☐ Unchecked (default): Build, test, AND push to ECR
     - ☑ Checked: Only build and test locally (no AWS charges)

### When to Use

- ✅ Before deploying: Build and push images to ECR
- ✅ Testing changes: Use `skip_ecr_push = true` to test without AWS costs
- ✅ After code changes: Verify builds succeed
- ✅ Security audits: Ensure Docker images pass vulnerability scans

### What It Does

```
1. Checkout code
2. Set up Java 17
3. Cache Maven dependencies
4. Run unit tests (mvn clean test)
5. Build Docker image locally
6. [Optional] Push to Amazon ECR
   - microservice-1 image
   - microservice-2 image
   - Tagged with commit SHA + "latest"
```

### Cost Impact

- **With ECR Push**: ~$0.10 per GB stored + data transfer
- **Without ECR Push**: $0 (runs on GitHub's free runners)

---

## 🚀 CD Workflow: Deploy to ECS

**Purpose**: Deploy Docker images from ECR to ECS Fargate.

### How to Run

1. Go to your GitHub repository
2. Click on the **Actions** tab
3. Select **"CD - Deploy to ECS"** from the left sidebar
4. Click **"Run workflow"** button (top right)
5. Configure options:
   - **Branch**: Choose which branch to deploy from
   - **Service**: Select which service to deploy
     - `both` - Deploy both microservices (default)
     - `microservice-1` - Only deploy the API service
     - `microservice-2` - Only deploy the consumer service
   - **Image tag**: Specify which image version to deploy
     - `latest` - Use the most recent build (default)
     - `<commit-sha>` - Deploy a specific version

### When to Use

- ✅ Initial deployment: After infrastructure is created
- ✅ Updates: When you have new images in ECR
- ✅ Rollback: Deploy a previous image tag
- ✅ Selective updates: Deploy only one changed service

### What It Does

```
1. Checkout code
2. Configure AWS credentials
3. Get current ECS task definition
4. Update task definition with new image
5. Register new task definition version
6. Update ECS service with new task definition
7. Wait for deployment to complete
8. Verify services are stable
```

### Prerequisites

⚠️ **Before running CD workflow:**
- ✅ Infrastructure deployed via Terraform
- ✅ ECR repositories created
- ✅ Docker images pushed to ECR (via CI workflow)
- ✅ GitHub Secrets configured

### Cost Impact

- Workflow execution: $0 (GitHub free runners)
- ECS tasks: ~$0.04/hour per service (~$30/month if running 24/7)

---

## 📋 Complete Deployment Workflow

### First-Time Setup

```bash
# 1. Deploy infrastructure
cd terraform
terraform init
terraform apply
# Creates: VPC, ECS cluster, ECR repos, S3, SQS, etc.

# 2. Build and push images
GitHub → Actions → "CI - Build and Test" → Run workflow
  ├─ Branch: main
  └─ Skip ECR Push: ☐ (unchecked)

# 3. Deploy to ECS
GitHub → Actions → "CD - Deploy to ECS" → Run workflow
  ├─ Branch: main
  ├─ Service: both
  └─ Image tag: latest
```

### Regular Updates

```bash
# 1. Make code changes and commit to your branch

# 2. Build new images
GitHub → Actions → "CI - Build and Test" → Run workflow
  ├─ Branch: your-branch
  └─ Skip ECR Push: ☐ (unchecked)

# 3. Deploy updated services
GitHub → Actions → "CD - Deploy to ECS" → Run workflow
  ├─ Branch: your-branch
  ├─ Service: both (or specific service)
  └─ Image tag: latest (or specific SHA)
```

### Rollback to Previous Version

```bash
# 1. Find the commit SHA you want to rollback to
git log --oneline

# 2. Deploy that specific version
GitHub → Actions → "CD - Deploy to ECS" → Run workflow
  ├─ Branch: main
  ├─ Service: both
  └─ Image tag: <previous-commit-sha>
```

---

## 🔒 Security Features

### Fork Protection

Both workflows include protection against accidental usage by forks:

```yaml
if: github.repository_owner == 'rotemm'
```

This ensures that:
- ✅ Only YOUR repository can deploy to YOUR AWS account
- ✅ Forks cannot accidentally trigger AWS deployments
- ✅ Your GitHub Secrets remain secure

### Secrets Required

The workflows require these GitHub Secrets:

| Secret | Description | How to Get |
|--------|-------------|------------|
| `AWS_ACCESS_KEY_ID` | IAM user access key | AWS IAM Console |
| `AWS_SECRET_ACCESS_KEY` | IAM user secret key | AWS IAM Console |
| `AWS_ACCOUNT_ID` | 12-digit AWS account ID | `aws sts get-caller-identity` |

**To configure:**
1. Go to: GitHub repo → Settings → Secrets and variables → Actions
2. Click "New repository secret"
3. Add each secret

---

## 💰 Cost Optimization Tips

### During Development

```bash
# Option 1: Test builds without AWS costs
CI Workflow → skip_ecr_push = true

# Option 2: Scale ECS tasks to zero when not using
aws ecs update-service \
  --cluster email-processor-cluster \
  --service email-processor-microservice-1 \
  --desired-count 0

# Option 3: Destroy infrastructure when not needed
cd terraform
terraform destroy
```

### For Production

```bash
# Use specific image tags (not just "latest")
CD Workflow → image_tag = <commit-sha>

# Deploy only changed services
CD Workflow → service = microservice-1  # Only deploy what changed

# Monitor costs
aws ce get-cost-and-usage \
  --time-period Start=2024-01-01,End=2024-01-31 \
  --granularity MONTHLY \
  --metrics BlendedCost
```

---

## 📊 Monitoring Workflow Runs

### View Workflow History

```
GitHub → Actions → Select workflow → View all runs
```

### Check Deployment Status

```bash
# ECS service status
aws ecs describe-services \
  --cluster email-processor-cluster \
  --services email-processor-microservice-1 \
  --region us-west-1

# View logs
aws logs tail /ecs/email-processor-microservice-1 --follow --region us-west-1
```

### Common Issues

| Issue | Solution |
|-------|----------|
| Workflow not visible | Make sure you're on the Actions tab of your repo |
| "Run workflow" disabled | You need write access to the repository |
| Deployment fails | Check that images exist in ECR and infrastructure is deployed |
| AWS credentials error | Verify GitHub Secrets are configured correctly |

---

## 🎓 Best Practices

1. **Always test before deploying**
   - Run CI with `skip_ecr_push = true` first
   - Review test results
   - Then run CI again without skip to push to ECR

2. **Use specific image tags for production**
   - Development: `image_tag = latest`
   - Production: `image_tag = <commit-sha>`

3. **Deploy services incrementally**
   - Deploy one service at a time
   - Verify it's working
   - Then deploy the next

4. **Monitor after deployment**
   - Check CloudWatch logs
   - Verify health endpoints
   - Monitor metrics

5. **Clean up regularly**
   - Old ECR images: Keep only recent versions
   - Destroy infrastructure when not needed
   - Review AWS costs monthly

---

## 📚 Additional Resources

- **Detailed CLI Operations**: See `CLI-OPERATIONS-GUIDE.md`
- **Architecture Overview**: See `README.md`
- **Terraform Configuration**: See `terraform/` directory

---

## 🆘 Need Help?

If workflows fail:
1. Check the workflow run logs in GitHub Actions
2. Verify AWS credentials and permissions
3. Ensure infrastructure is deployed via Terraform
4. Check that ECR images exist (for CD workflow)
5. Review CloudWatch logs for application errors

For local testing without workflows, see the CLI-OPERATIONS-GUIDE.md.

