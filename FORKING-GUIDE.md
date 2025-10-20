# Forking Guide: Running This Project on Your Own AWS Account

If you forked this repository and want to run it on your own AWS account, follow this guide.

## ⚠️ Important Notes

- **Cost Warning**: Running this infrastructure will cost approximately $80-100/month if running 24/7
- **Your Responsibility**: You're responsible for all AWS charges incurred
- **Manual Setup Required**: You need to configure your own AWS account and update several files

---

## 📋 Prerequisites

- AWS Account with admin access
- AWS CLI configured locally
- Terraform >= 1.0 installed
- GitHub account (you already have this if you forked!)
- Docker and Java 17 for local development

---

## 🚀 Setup Steps

### Step 1: Set Up AWS OIDC Provider

```bash
# Create the OIDC provider in your AWS account
aws iam create-open-id-connect-provider \
  --url https://token.actions.githubusercontent.com \
  --client-id-list sts.amazonaws.com \
  --thumbprint-list 6938fd4d98bab03faadb97b34396831e3780aea1
```

### Step 2: Create IAM Role with Trust Policy

Create a file called `trust-policy.json`:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": {
        "Federated": "arn:aws:iam::YOUR_ACCOUNT_ID:oidc-provider/token.actions.githubusercontent.com"
      },
      "Action": "sts:AssumeRoleWithWebIdentity",
      "Condition": {
        "StringEquals": {
          "token.actions.githubusercontent.com:aud": "sts.amazonaws.com"
        },
        "StringLike": {
          "token.actions.githubusercontent.com:sub": "repo:YOUR_GITHUB_USERNAME/email-processor-microservices:*"
        }
      }
    }
  ]
}
```

**Replace:**
- `YOUR_ACCOUNT_ID` with your AWS account ID (get it: `aws sts get-caller-identity --query Account --output text`)
- `YOUR_GITHUB_USERNAME` with your GitHub username

Create the role:
```bash
aws iam create-role \
  --role-name GitHubActionsRole \
  --assume-role-policy-document file://trust-policy.json
```

### Step 3: Attach Permissions to IAM Role

Create a file called `permissions-policy.json`:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "ECRAccess",
      "Effect": "Allow",
      "Action": [
        "ecr:GetAuthorizationToken",
        "ecr:BatchCheckLayerAvailability",
        "ecr:PutImage",
        "ecr:InitiateLayerUpload",
        "ecr:UploadLayerPart",
        "ecr:CompleteLayerUpload",
        "ecr:BatchGetImage",
        "ecr:GetDownloadUrlForLayer"
      ],
      "Resource": "*"
    },
    {
      "Sid": "ECSAccess",
      "Effect": "Allow",
      "Action": [
        "ecs:DescribeTaskDefinition",
        "ecs:DescribeServices",
        "ecs:DescribeClusters",
        "ecs:RegisterTaskDefinition",
        "ecs:UpdateService"
      ],
      "Resource": "*"
    },
    {
      "Sid": "IAMPassRole",
      "Effect": "Allow",
      "Action": "iam:PassRole",
      "Resource": "*",
      "Condition": {
        "StringEquals": {
          "iam:PassedToService": "ecs-tasks.amazonaws.com"
        }
      }
    }
  ]
}
```

Attach the policy:
```bash
aws iam put-role-policy \
  --role-name GitHubActionsRole \
  --policy-name GitHubActionsCICDPolicy \
  --policy-document file://permissions-policy.json
```

### Step 4: Update Workflow Files

You need to update two files to point to your AWS account:

#### File 1: `.github/workflows/ci.yml`

Find and replace:

```yaml
# FIND THIS:
if: github.repository_owner == 'rotemasud' && github.event.inputs.skip_ecr_push != 'true'

# CHANGE TO:
if: github.repository_owner == 'YOUR_GITHUB_USERNAME' && github.event.inputs.skip_ecr_push != 'true'

# FIND THIS:
role-to-assume: arn:aws:iam::256344107989:role/GitHubActionsRole

# CHANGE TO:
role-to-assume: arn:aws:iam::YOUR_ACCOUNT_ID:role/GitHubActionsRole
```

**4 places to update** in `ci.yml` (lines 64, 71, 76, 92)

#### File 2: `.github/workflows/cd.yml`

Find and replace:

```yaml
# FIND THIS (appears twice):
role-to-assume: arn:aws:iam::256344107989:role/GitHubActionsRole

# CHANGE TO:
role-to-assume: arn:aws:iam::YOUR_ACCOUNT_ID:role/GitHubActionsRole
```

**2 places to update** in `cd.yml` (lines 46, 103)

### Step 5: Deploy Infrastructure with Terraform

```bash
# Clone your forked repository
git clone https://github.com/YOUR_GITHUB_USERNAME/email-processor-microservices.git
cd email-processor-microservices/terraform

# Update terraform.tfvars with your preferences
# (Optional: change region, instance sizes, etc.)

# Initialize and apply
terraform init
terraform apply
```

This will create:
- VPC with public and private subnets
- ECR repositories for both microservices
- ECS cluster and services
- Application Load Balancer
- SQS queue and Dead Letter Queue
- S3 bucket for email storage
- SSM parameter for API token
- All necessary IAM roles and security groups

### Step 6: Test Your Setup

#### Option 1: Via GitHub Actions UI

1. Go to **Actions** tab in your forked repository
2. Select **CI - Build and Test** workflow
3. Click **Run workflow**
4. Leave "Skip ECR push" unchecked
5. Click **Run workflow**

#### Option 2: Via GitHub CLI

```bash
# Trigger CI
gh workflow run ci.yml

# Trigger CD
gh workflow run cd.yml -f service=both -f image_tag=latest
```

#### Verify Deployment

```bash
# Check if ECR images were pushed
aws ecr describe-images --repository-name email-processor-microservice-1

# Check ECS services
aws ecs list-services --cluster email-processor-cluster

# Get the load balancer URL
aws elbv2 describe-load-balancers --query 'LoadBalancers[0].DNSName' --output text
```

---

## 🧪 Testing the API

Once deployed, test your API:

```bash
# Get your ALB DNS name
ALB_URL=$(terraform output -raw alb_dns_name)

# Test health endpoint
curl http://$ALB_URL/api/health

# Test email processing
curl -X POST http://$ALB_URL/api/email \
  -H "Content-Type: application/json" \
  -d '{
    "data": {
      "email_subject": "Test Email",
      "email_sender": "test@example.com",
      "email_timestream": "1693561101",
      "email_content": "This is a test email"
    },
    "token": "$DJISA<$#45ex3RtYr"
  }'
```

---

## 🔍 Troubleshooting

### Issue: Workflow fails with "Not authorized to perform sts:AssumeRoleWithWebIdentity"

**Cause**: Trust policy doesn't match your repository.

**Solution**: 
- Verify your trust policy has the correct GitHub username
- Make sure the OIDC provider is created
- Check the IAM role exists: `aws iam get-role --role-name GitHubActionsRole`

### Issue: Workflow fails with "repository_owner check failed"

**Cause**: You didn't update the repository owner in `ci.yml`.

**Solution**: Update all 4 occurrences of `github.repository_owner` in `.github/workflows/ci.yml`

### Issue: "Access Denied" when pushing to ECR

**Cause**: IAM role doesn't have ECR permissions.

**Solution**: 
- Verify IAM role has the permissions policy attached
- Check ECR repositories exist: `aws ecr describe-repositories`

### Issue: Terraform fails to create resources

**Cause**: Multiple possible causes.

**Solution**:
- Ensure your AWS credentials are configured: `aws sts get-caller-identity`
- Check you have sufficient permissions (Admin recommended for initial setup)
- Verify the region in `terraform.tfvars` is valid

---

## 💰 Cost Management

### Estimated Monthly Costs (Running 24/7):

| Service | Estimated Cost |
|---------|----------------|
| ECS Fargate (2 tasks) | $30-40 |
| Application Load Balancer | $20-25 |
| NAT Gateway | $30-35 |
| S3 Storage | $1-5 |
| Other (ECR, SQS, logs) | $5-10 |
| **Total** | **~$80-100/month** |

### Cost Reduction Tips:

1. **Stop services when not in use:**
   ```bash
   aws ecs update-service --cluster email-processor-cluster \
     --service email-processor-microservice-1 --desired-count 0
   ```

2. **Use smaller instance sizes** (update in `terraform/variables.tf`)

3. **Deploy to a cheaper region** (update in `terraform.tfvars`)

4. **Clean up when done:**
   ```bash
   cd terraform
   terraform destroy
   ```

---

