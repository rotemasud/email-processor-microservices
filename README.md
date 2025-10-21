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
- **AWS Managed Prometheus**: Metrics collection and storage
- **Grafana on ECS**: Metrics visualization with pre-built dashboards
- **AWS Distro for OpenTelemetry (ADOT)**: Metrics collection from services

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
- 💰 **Running this infrastructure will incur AWS charges** (~$80-100/month if running 24/7)

**For detailed setup instructions**, see [FORKING-GUIDE.md](FORKING-GUIDE.md) which includes:
- AWS OIDC and IAM role configuration
- GitHub Actions workflow updates
- Complete deployment steps

## Setup Instructions

### 1. Infrastructure Deployment

The Terraform configuration uses a modular structure for better maintainability and scalability.

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

### 2. Local Development

**Prerequisites for local development:**
- AWS infrastructure deployed (Step 1 above)
- AWS CLI configured with credentials
- Environment variables set (see below)

#### Microservice 1 (REST API)

```bash
# Set required environment variables
export AWS_DEFAULT_REGION=us-west-1
export SQS_QUEUE_URL=$(aws sqs get-queue-url --queue-name email-processor-queue --query 'QueueUrl' --output text)

# Run the service
cd microservice-1
mvn spring-boot:run
```

#### Microservice 2 (SQS Consumer)

```bash
# Set required environment variables
export AWS_DEFAULT_REGION=us-west-1
export SQS_QUEUE_URL=$(aws sqs get-queue-url --queue-name email-processor-queue --query 'QueueUrl' --output text)
export S3_BUCKET_NAME=email-processor-storage

# Run the service
cd microservice-2
mvn spring-boot:run
```

**Note:** Both services connect to real AWS resources (SQS, S3, SSM Parameter Store), so the infrastructure must be deployed first.

### 3. Testing the API

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

### 4. Running Tests

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

### 5. CI/CD Setup (Optional)

**⚠️ Note**: This project uses GitHub Actions with OIDC for secure AWS authentication.

If you're setting this up in your own AWS account, see [FORKING-GUIDE.md](FORKING-GUIDE.md) for detailed instructions on configuring OIDC, IAM roles, and GitHub Actions workflows.

## Monitoring & Observability

This project includes comprehensive monitoring with AWS Managed Prometheus and Grafana running on ECS.

### Metrics Endpoints

Both microservices expose Prometheus metrics at:
```
http://<service-url>:8080/actuator/prometheus
```

### Available Metrics

**Microservice-1 (API Service):**
- SQS message publishing metrics (count, failures, duration)
- Validation metrics (success/failure rates by type)
- HTTP request metrics (rate, latency, status codes)
- JVM metrics (memory, GC, threads, CPU)

**Microservice-2 (Consumer Service):**
- SQS message consumption metrics (received, processed, failures)
- S3 upload metrics (count, duration, file sizes)
- Message processing duration
- JVM metrics (memory, GC, threads, CPU)

### Accessing Grafana

After deploying infrastructure:

```bash
# Get Grafana URL
cd terraform
terraform output grafana_url
```

Grafana is deployed as a self-hosted service on ECS Fargate with:
- Persistent storage using Amazon EFS
- AWS Managed Prometheus as the data source
- Pre-configured dashboards for immediate insights

Access Grafana at the URL above and log in with:
- **Username**: `admin`
- **Password**: Retrieve from SSM Parameter Store:
  ```bash
  aws ssm get-parameter --name /email-processor/grafana/admin-password --with-decryption --query 'Parameter.Value' --output text
  ```

### Pre-built Dashboards

Three comprehensive dashboards are included in `grafana-dashboards/`:

1. **JVM Dashboard** (`jvm-dashboard.json`): Memory usage, garbage collection, thread metrics, CPU
2. **HTTP Metrics Dashboard** (`http-metrics-dashboard.json`): Request rates, latencies, status codes, errors
3. **Business Metrics Dashboard** (`business-metrics-dashboard.json`): SQS, S3, and validation-specific metrics

Import these dashboards in Grafana via **Dashboards → Import** to start monitoring your services.

### Logs and Debugging

```bash
# View ECS service logs
aws logs tail /ecs/email-processor-microservice-1 --follow

# Check ECS service status
aws ecs describe-services --cluster email-processor-cluster --services email-processor-microservice-1

# View SQS queue attributes
aws sqs get-queue-attributes --queue-url <queue-url> --attribute-names All
```

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
```

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
│   ├── src/test/java/             # Unit tests
│   ├── pom.xml
│   └── Dockerfile
├── microservice-2/                 # SQS consumer service
│   ├── src/main/java/
│   ├── src/main/resources/
│   ├── src/test/java/             # Unit tests
│   ├── pom.xml
│   └── Dockerfile
├── terraform/                      # Infrastructure as Code
│   ├── modules/
│   │   ├── networking/            # VPC, subnets, NAT gateway
│   │   ├── sqs/                   # SQS queue and DLQ
│   │   ├── s3/                    # S3 bucket configuration
│   │   ├── ecs-cluster/           # ECS cluster
│   │   ├── ecs-service/           # Reusable ECS service module
│   │   ├── monitoring/            # AWS Managed Prometheus
│   │   └── grafana-ecs/           # Grafana on ECS
│   ├── main.tf
│   ├── variables.tf
│   ├── outputs.tf
│   └── backend.tf
├── grafana-dashboards/             # Pre-built Grafana dashboards
│   ├── jvm-dashboard.json
│   ├── http-metrics-dashboard.json
│   └── business-metrics-dashboard.json
├── .github/workflows/              # CI/CD pipelines
│   ├── ci.yml
│   └── cd.yml
├── README.md                       # Project documentation
└── FORKING-GUIDE.md               # Setup guide for your own AWS account
```

## License

This project is licensed under the MIT License.
