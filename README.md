# Email Processor Microservices

A cloud-native microservices architecture for processing email data using AWS services, built with Spring Boot and deployed on ECS Fargate.

## Architecture Overview

```
┌─────────────────┐
│     Client      │
│   (External)    │
└────────┬────────┘
         │
         │ HTTP POST /api/email
         │
         ▼
┌─────────────────┐
│  Application    │
│  Load Balancer  │
│   (Public)      │
└────────┬────────┘
         │
         │ Route to Target Group
         │
         ▼
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│  Microservice 1 │───▶│   SQS Queue     │───▶│  Microservice 2 │
│   (REST API)    │    │  (Message Bus)  │    │ (SQS Consumer)  │
│  ECS Fargate    │    │                 │    │  ECS Fargate    │
│ Private Subnet  │    └─────────────────┘    │ Private Subnet  │
└─────────────────┘                            └────────┬────────┘
         │                                              │
         │ Validate Token                               │
         ▼                                              │ Store Email
┌─────────────────┐                            ┌───────-▼────────┐
│  SSM Parameter  │                            │   S3 Bucket     │
│  Store (Token)  │                            │ (Email Storage) │
└─────────────────┘                            └─────────────────┘
```

## Components

### Microservice 1 - REST API
- **Purpose**: Receives email processing requests via REST API
- **Technology**: Spring Boot 3.x, Java 17
- **Features**:
  - Token validation against AWS SSM Parameter Store
  - Email data validation (4 required fields)
  - SQS message publishing
  - Health checks and monitoring

### Microservice 2 - SQS Consumer
- **Purpose**: Processes email messages from SQS and stores them in S3
- **Technology**: Spring Boot 3.x, Java 17
- **Features**:
  - Scheduled SQS polling (every 30 seconds)
  - S3 upload with organized folder structure
  - Dead letter queue handling
  - Health checks and monitoring

### Infrastructure (Terraform)
- **VPC**: Multi-AZ setup with public/private subnets
- **ECS Fargate**: Serverless container hosting
- **Application Load Balancer**: HTTP traffic routing
- **SQS**: Message queue with dead letter queue
- **S3**: Email data storage with versioning and encryption
- **SSM Parameter Store**: Secure token storage
- **ECR**: Container image repositories
- **CloudWatch**: Logging and monitoring

**✨ Modular Structure**: The Terraform configuration uses reusable modules for easy scalability and maintenance. See [terraform/MODULES-README.md](terraform/MODULES-README.md) for details.

## Prerequisites

- AWS CLI configured with appropriate permissions
- Terraform >= 1.0
- Docker
- Java 17
- Maven 3.9+
- GitHub repository with Actions enabled

## 🔒 Security Notice for Forkers

**If you're forking this repository:**
- ✅ **Your AWS credentials are safe** - This repo does NOT contain any AWS credentials
- ⚠️ **You MUST set up your own AWS account and credentials** to use this project
- 🔐 GitHub Actions uses encrypted secrets that are NOT accessible to forks
- 💰 **Running this infrastructure will incur AWS charges** (~$80-100/month if running 24/7)
- 📝 The workflow is configured to only deploy from the original repository owner's account

**To use this in your own AWS account:**
1. **Set up AWS OIDC and IAM:**
   - Create OIDC provider for GitHub Actions in your AWS account
   - Create an IAM role with permissions for ECR, ECS, and IAM PassRole
   - Configure the role's trust policy to allow your forked repository
2. **Update the workflows:**
   - Edit both `.github/workflows/ci.yml` and `.github/workflows/cd.yml`
   - Change `role-to-assume` ARN to your IAM role ARN
   - Update `github.repository_owner` in `ci.yml` to your GitHub username
3. **Configure GitHub Secrets:**
   - Add `AWS_ACCOUNT_ID` secret (just your account ID, no access keys needed!)
4. **Deploy infrastructure:**
   - Update `terraform/terraform.tfvars` with your desired configuration
   - Run `terraform apply`

## Setup Instructions

### 1. Infrastructure Deployment

The Terraform configuration uses a modular structure for better maintainability and scalability. For detailed information about the modules, see [terraform/MODULES-README.md](terraform/MODULES-README.md).

```bash
# Navigate to terraform directory
cd terraform

# Initialize Terraform (downloads modules and providers)
terraform init

# Plan the deployment
terraform plan

# Apply the infrastructure
terraform apply
```

**Adding a new microservice?** Simply add a new module block in `terraform/main.tf` - see the modules documentation for examples.

### 2. GitHub Secrets Configuration

**⚠️ IMPORTANT**: This project uses OIDC (OpenID Connect) for secure authentication with AWS.

**Setting up OIDC in AWS (Required for Forkers):**

If you forked this repo, you need to set up OIDC in your AWS account:

```bash
# 1. Create OIDC Provider
aws iam create-open-id-connect-provider \
  --url https://token.actions.githubusercontent.com \
  --client-id-list sts.amazonaws.com \
  --thumbprint-list 6938fd4d98bab03faadb97b34396831e3780aea1

# 2. Create IAM Role (save as trust-policy.json first)
# Replace YOUR_ACCOUNT_ID and YOUR_USERNAME
{
  "Version": "2012-10-17",
  "Statement": [{
    "Effect": "Allow",
    "Principal": {
      "Federated": "arn:aws:iam::YOUR_ACCOUNT_ID:oidc-provider/token.actions.githubusercontent.com"
    },
    "Action": "sts:AssumeRoleWithWebIdentity",
    "Condition": {
      "StringEquals": {"token.actions.githubusercontent.com:aud": "sts.amazonaws.com"},
      "StringLike": {"token.actions.githubusercontent.com:sub": "repo:YOUR_USERNAME/email-processor-microservices:*"}
    }
  }]
}

# Create the role
aws iam create-role --role-name GitHubActionsRole --assume-role-policy-document file://trust-policy.json

# 3. Attach permissions (ECR, ECS, IAM PassRole)
# Create appropriate policies for your role
```

### 3. Running Tests

Both microservices include comprehensive unit tests (39+ tests total).

```bash
# Run tests for Microservice 1
cd microservice-1
mvn test

# Run tests for Microservice 2
cd microservice-2
mvn test

# Run all tests from root
mvn test -f microservice-1/pom.xml && mvn test -f microservice-2/pom.xml
```

See [TESTING.md](TESTING.md) for detailed test documentation.

### 4. Local Development

#### Microservice 1 (REST API)
```bash
cd microservice-1
mvn spring-boot:run
```

#### Microservice 2 (SQS Consumer)
```bash
cd microservice-2
mvn spring-boot:run
```

### 5. Testing the API

```bash
# Test the health endpoint
curl http://localhost:8080/api/health

# Test email processing
curl -X POST http://localhost:8080/api/email \
  -H "Content-Type: application/json" \
  -d '{
    "data": {
      "email_subject": "Happy new year!",
      "email_sender": "John doe",
      "email_timestream": "1693561101",
      "email_content": "Just want to say... Happy new year!!!"
    },
    "token": "$DJISA<$#45ex3RtYr"
  }'
```

## API Documentation

### POST /api/email

Processes an email message and queues it for storage.

**Request Body:**
```json
{
  "data": {
    "email_subject": "string (required)",
    "email_sender": "string (required)",
    "email_timestream": "string (required, Unix timestamp)",
    "email_content": "string (required)"
  },
  "token": "string (required)"
}
```

**Response:**
```json
{
  "success": true,
  "message": "Email processed successfully and queued for storage",
  "correlationId": "uuid"
}
```

**Error Responses:**
- `400 Bad Request`: Invalid email data
- `401 Unauthorized`: Invalid token
- `500 Internal Server Error`: Server error

## Deployment

### CI/CD Pipeline

The project includes **manual-only** GitHub Actions workflows to give you full control and save costs:

1. **CI Pipeline** (`.github/workflows/ci.yml`) - Manual Trigger Only:
   - Builds and tests both microservices
   - Creates Docker images
   - Optionally pushes images to ECR
   - **How to run**: Go to Actions → CI - Build and Test → Run workflow
   - **Options**: 
     - `skip_ecr_push`: Set to `true` to only build/test locally without pushing to ECR

2. **CD Pipeline** (`.github/workflows/cd.yml`) - Manual Trigger Only:
   - Deploys services to ECS Fargate
   - Supports selective deployment (one or both services)
   - Waits for deployment completion
   - **How to run**: Go to Actions → CD - Deploy to ECS → Run workflow
   - **Options**:
     - `service`: Choose which service to deploy (both, microservice-1, or microservice-2)
     - `image_tag`: Specify which image tag to deploy (default: latest)

### Manual Deployment Steps

```bash
# Step 1: Deploy infrastructure with Terraform
cd terraform
terraform init
terraform apply

# Step 2: Build and push Docker images via GitHub Actions
# Go to: GitHub → Actions → "CI - Build and Test" → Run workflow
# - Leave "skip_ecr_push" unchecked to push to ECR

# Step 3: Deploy to ECS via GitHub Actions
# Go to: GitHub → Actions → "CD - Deploy to ECS" → Run workflow
# - Select "both" to deploy all services
# - Use image tag "latest" or specify a specific SHA

# Alternative: Deploy locally using AWS CLI
# (See CLI-OPERATIONS-GUIDE.md for detailed commands)
```

## Monitoring and Logging

- **CloudWatch Logs**: Container logs are automatically collected
- **Health Checks**: Both services expose `/api/health` endpoints
- **Metrics**: ECS service metrics available in CloudWatch
- **Alarms**: Dead letter queue depth monitoring

## Security Features

- **Network Security**: Private subnets for ECS tasks
- **IAM Roles**: Least privilege access for services
- **Encryption**: S3 server-side encryption, SSM SecureString
- **Token Validation**: Secure token storage in SSM Parameter Store
- **Container Security**: Multi-stage Docker builds, vulnerability scanning

## File Structure

```
email-processor-microservices/
├── microservice-1/                 # REST API service
│   ├── src/main/java/
│   ├── src/main/resources/
│   ├── pom.xml
│   └── Dockerfile
├── microservice-2/                 # SQS consumer service
│   ├── src/main/java/
│   ├── src/main/resources/
│   ├── pom.xml
│   └── Dockerfile
├── terraform/                      # Infrastructure as Code
│   ├── main.tf
│   ├── variables.tf
│   ├── outputs.tf
│   └── ecs.tf
├── .github/workflows/              # CI/CD pipelines
│   ├── ci.yml
│   └── cd.yml
└── README.md
```
### Logs and Debugging

```bash
# View ECS service logs
aws logs tail /ecs/email-processor-microservice-1 --follow

# Check ECS service status
aws ecs describe-services --cluster email-processor-cluster --services email-processor-microservice-1

# View SQS queue attributes
aws sqs get-queue-attributes --queue-url <queue-url> --attribute-names All
```

## License

This project is licensed under the MIT License.
