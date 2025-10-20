# Email Processor Microservices

A cloud-native microservices architecture for processing email data using AWS services, built with Spring Boot and deployed on ECS Fargate.

## Architecture Overview

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Client/API    │───▶│  Microservice 1 │───▶│     SQS Queue   │
│   Gateway       │    │   (REST API)    │    │                 │
└─────────────────┘    └─────────────────┘    └─────────────────┘
                                │                        │
                                ▼                        ▼
                       ┌─────────────────┐    ┌─────────────────┐
                       │  Application    │    │  Microservice 2 │
                       │  Load Balancer  │    │ (SQS Consumer)  │
                       └─────────────────┘    └─────────────────┘
                                                        │
                                                        ▼
                                               ┌─────────────────┐
                                               │   S3 Bucket     │
                                               │ (Email Storage) │
                                               └─────────────────┘
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
- **Application Load Balancer**: HTTP/HTTPS traffic routing
- **SQS**: Message queue with dead letter queue
- **S3**: Email data storage with versioning and encryption
- **SSM Parameter Store**: Secure token storage
- **ECR**: Container image repositories
- **CloudWatch**: Logging and monitoring

## Prerequisites

- AWS CLI configured with appropriate permissions
- Terraform >= 1.0
- Docker
- Java 17
- Maven 3.9+
- GitHub repository with Actions enabled

## Setup Instructions

### 1. Infrastructure Deployment

```bash
# Navigate to terraform directory
cd terraform

# Initialize Terraform
terraform init

# Plan the deployment
terraform plan

# Apply the infrastructure
terraform apply
```

### 2. GitHub Secrets Configuration

Configure the following secrets in your GitHub repository:

- `AWS_ACCESS_KEY_ID`: Your AWS access key
- `AWS_SECRET_ACCESS_KEY`: Your AWS secret key
- `AWS_ACCOUNT_ID`: Your AWS account ID

### 3. Local Development

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

### 4. Testing the API

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

The project includes GitHub Actions workflows for:

1. **CI Pipeline** (`.github/workflows/ci.yml`):
   - Builds and tests both microservices
   - Creates Docker images
   - Pushes images to ECR
   - Runs security scans

2. **CD Pipeline** (`.github/workflows/cd.yml`):
   - Deploys services to ECS Fargate
   - Supports manual deployment with service selection
   - Waits for deployment completion

### Manual Deployment

```bash
# Deploy infrastructure
cd terraform
terraform apply

# Deploy services via GitHub Actions
# Go to Actions tab and run "CD - Deploy to ECS" workflow
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

## Troubleshooting

### Common Issues

1. **Token Validation Failures**:
   - Verify SSM parameter exists and has correct value
   - Check IAM permissions for SSM access

2. **SQS Connection Issues**:
   - Verify queue URL in environment variables
   - Check IAM permissions for SQS access

3. **S3 Upload Failures**:
   - Verify bucket name and permissions
   - Check IAM role has S3 write permissions

4. **ECS Deployment Issues**:
   - Check ECR repository exists
   - Verify task definition and service configuration
   - Review CloudWatch logs for container errors

### Logs and Debugging

```bash
# View ECS service logs
aws logs tail /ecs/email-processor-microservice-1 --follow

# Check ECS service status
aws ecs describe-services --cluster email-processor-cluster --services email-processor-microservice-1

# View SQS queue attributes
aws sqs get-queue-attributes --queue-url <queue-url> --attribute-names All
```

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests if applicable
5. Submit a pull request

## License

This project is licensed under the MIT License.
