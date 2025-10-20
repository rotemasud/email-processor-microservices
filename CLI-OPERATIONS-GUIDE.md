# CLI-Only Operations & Troubleshooting Guide

This guide provides complete CLI-based operations for deploying, managing, and troubleshooting the email processor microservices without requiring AWS Console access.

## Table of Contents
- [Prerequisites](#prerequisites)
- [Initial Setup](#initial-setup)
- [Deployment](#deployment)
- [Verification](#verification)
- [Troubleshooting](#troubleshooting)
- [Monitoring](#monitoring)
- [Common Issues](#common-issues)
- [Rollback Procedures](#rollback-procedures)

---

## Prerequisites

### Required Tools
```bash
# Verify AWS CLI installation and version
aws --version  # Should be >= 2.x

# Verify Terraform installation
terraform version  # Should be >= 1.0

# Verify Docker installation
docker --version

# Verify Java and Maven (for local development)
java -version  # Should be 17
mvn --version  # Should be 3.9+
```

### AWS Credentials Configuration
```bash
# Configure AWS credentials (if not already done)
aws configure

# Verify credentials are working
aws sts get-caller-identity

# Export your account ID as a variable for later use
export AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
export AWS_REGION=eu-west-1  # Or your preferred region
echo "Account ID: $AWS_ACCOUNT_ID"
echo "Region: $AWS_REGION"
```

---

## Initial Setup

### 1. Build Docker Images Locally (Optional - for testing)
```bash
# Build microservice-1
cd microservice-1
docker build -t email-processor-microservice-1:latest .
cd ..

# Build microservice-2
cd microservice-2
docker build -t email-processor-microservice-2:latest .
cd ..

# Verify images were created
docker images | grep email-processor
```

### 2. Initialize Terraform
```bash
cd terraform

# Initialize Terraform
terraform init

# Validate configuration
terraform validate

# Format Terraform files
terraform fmt

# Review the execution plan
terraform plan

# Show what will be created (save to file for reference)
terraform plan -out=tfplan
terraform show tfplan > plan-output.txt
```

---

## Deployment

### 1. Deploy Infrastructure
```bash
cd terraform

# Apply infrastructure (will prompt for confirmation)
terraform apply

# Or apply without confirmation (for automation)
terraform apply -auto-approve

# Capture outputs
terraform output -json > outputs.json

# View specific outputs
export SQS_QUEUE_URL=$(terraform output -raw sqs_queue_url)
export S3_BUCKET_NAME=$(terraform output -raw s3_bucket_name)
export ALB_DNS=$(terraform output -raw alb_dns_name)
export ECR_REPO_1=$(terraform output -raw ecr_repository_microservice_1)
export ECR_REPO_2=$(terraform output -raw ecr_repository_microservice_2)

echo "SQS Queue URL: $SQS_QUEUE_URL"
echo "S3 Bucket: $S3_BUCKET_NAME"
echo "ALB DNS: $ALB_DNS"
echo "ECR Repo 1: $ECR_REPO_1"
echo "ECR Repo 2: $ECR_REPO_2"
```

### 2. Build and Push Docker Images to ECR
```bash
# Authenticate Docker to ECR
aws ecr get-login-password --region $AWS_REGION | \
  docker login --username AWS --password-stdin $AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com

# Build and push microservice-1
cd ../microservice-1
docker build -t $ECR_REPO_1:latest .
docker tag $ECR_REPO_1:latest $ECR_REPO_1:latest
docker push $ECR_REPO_1:latest

# Build and push microservice-2
cd ../microservice-2
docker build -t $ECR_REPO_2:latest .
docker tag $ECR_REPO_2:latest $ECR_REPO_2:latest
docker push $ECR_REPO_2:latest

cd ..
```

### 3. Force New ECS Deployment (to use new images)
```bash
# Get cluster name
export CLUSTER_NAME=$(aws ecs list-clusters --query 'clusterArns[0]' --output text | awk -F'/' '{print $NF}')

# Get service names
export SERVICE_1=$(aws ecs list-services --cluster $CLUSTER_NAME --query 'serviceArns[0]' --output text | awk -F'/' '{print $NF}')
export SERVICE_2=$(aws ecs list-services --cluster $CLUSTER_NAME --query 'serviceArns[1]' --output text | awk -F'/' '{print $NF}')

# Force new deployment
aws ecs update-service --cluster $CLUSTER_NAME --service $SERVICE_1 --force-new-deployment
aws ecs update-service --cluster $CLUSTER_NAME --service $SERVICE_2 --force-new-deployment

# Wait for services to stabilize
aws ecs wait services-stable --cluster $CLUSTER_NAME --services $SERVICE_1 $SERVICE_2
```

---

## Verification

### 1. Check Infrastructure Components

#### VPC and Networking
```bash
# List VPCs
aws ec2 describe-vpcs --filters "Name=tag:Name,Values=*email-processor*" \
  --query 'Vpcs[*].[VpcId,CidrBlock,Tags[?Key==`Name`].Value|[0]]' \
  --output table

# List Subnets
aws ec2 describe-subnets --filters "Name=tag:Name,Values=*email-processor*" \
  --query 'Subnets[*].[SubnetId,CidrBlock,AvailabilityZone,Tags[?Key==`Name`].Value|[0]]' \
  --output table

# Check NAT Gateways
aws ec2 describe-nat-gateways --filter "Name=tag:Name,Values=*email-processor*" \
  --query 'NatGateways[*].[NatGatewayId,State,SubnetId]' \
  --output table
```

#### SQS Queues
```bash
# List queues
aws sqs list-queues --queue-name-prefix email-processor

# Get queue attributes
aws sqs get-queue-attributes \
  --queue-url $SQS_QUEUE_URL \
  --attribute-names All \
  --output json | jq

# Check queue depth
aws sqs get-queue-attributes \
  --queue-url $SQS_QUEUE_URL \
  --attribute-names ApproximateNumberOfMessages,ApproximateNumberOfMessagesNotVisible \
  --query 'Attributes'
```

#### S3 Bucket
```bash
# List buckets
aws s3 ls | grep email-processor

# Check bucket configuration
aws s3api get-bucket-versioning --bucket $S3_BUCKET_NAME
aws s3api get-bucket-encryption --bucket $S3_BUCKET_NAME

# List objects in bucket
aws s3 ls s3://$S3_BUCKET_NAME --recursive
```

#### SSM Parameter
```bash
# Check if SSM parameter exists
aws ssm describe-parameters --filters "Key=Name,Values=/email-processor/api-token"

# Get parameter value (be careful, this exposes the token)
aws ssm get-parameter --name /email-processor/api-token --with-decryption --query 'Parameter.Value' --output text
```

#### ECR Repositories
```bash
# List ECR repositories
aws ecr describe-repositories --repository-names \
  email-processor-microservice-1 \
  email-processor-microservice-2 \
  --output table

# List images in repositories
aws ecr list-images --repository-name email-processor-microservice-1 --output table
aws ecr list-images --repository-name email-processor-microservice-2 --output table

# Get image details with tags
aws ecr describe-images --repository-name email-processor-microservice-1 \
  --query 'imageDetails[*].[imageTags[0],imagePushedAt,imageSizeInBytes]' \
  --output table
```

### 2. Check ECS Components

#### ECS Cluster
```bash
# List clusters
aws ecs list-clusters

# Describe cluster
aws ecs describe-clusters --clusters $CLUSTER_NAME

# Check Container Insights status
aws ecs describe-clusters --clusters $CLUSTER_NAME \
  --query 'clusters[0].settings' \
  --output table
```

#### ECS Services
```bash
# List services
aws ecs list-services --cluster $CLUSTER_NAME

# Describe services (detailed)
aws ecs describe-services \
  --cluster $CLUSTER_NAME \
  --services $SERVICE_1 $SERVICE_2 \
  --output json | jq

# Get service status summary
aws ecs describe-services \
  --cluster $CLUSTER_NAME \
  --services $SERVICE_1 $SERVICE_2 \
  --query 'services[*].[serviceName,status,runningCount,desiredCount,pendingCount]' \
  --output table
```

#### ECS Tasks
```bash
# List running tasks
aws ecs list-tasks --cluster $CLUSTER_NAME --desired-status RUNNING

# Get task details
export TASK_ARN=$(aws ecs list-tasks --cluster $CLUSTER_NAME --service-name $SERVICE_1 --query 'taskArns[0]' --output text)

aws ecs describe-tasks --cluster $CLUSTER_NAME --tasks $TASK_ARN

# Check task health
aws ecs describe-tasks \
  --cluster $CLUSTER_NAME \
  --tasks $TASK_ARN \
  --query 'tasks[0].[lastStatus,healthStatus,containers[0].healthStatus]'
```

#### Task Definitions
```bash
# List task definition families
aws ecs list-task-definition-families --family-prefix email-processor

# Get latest task definition
aws ecs describe-task-definition \
  --task-definition email-processor-microservice-1 \
  --query 'taskDefinition.[family,taskRoleArn,executionRoleArn,cpu,memory]'

# View full task definition
aws ecs describe-task-definition \
  --task-definition email-processor-microservice-1 \
  --output json | jq
```

### 3. Check Load Balancer

#### ALB Status
```bash
# List load balancers
aws elbv2 describe-load-balancers \
  --query 'LoadBalancers[?contains(LoadBalancerName, `email-processor`)].[LoadBalancerName,DNSName,State.Code]' \
  --output table

# Get target groups
aws elbv2 describe-target-groups \
  --query 'TargetGroups[?contains(TargetGroupName, `email-processor`)].[TargetGroupName,Protocol,Port,HealthCheckPath]' \
  --output table

# Check target health
export TG_ARN=$(aws elbv2 describe-target-groups \
  --query 'TargetGroups[?contains(TargetGroupName, `microservice-1`)].TargetGroupArn' \
  --output text)

aws elbv2 describe-target-health --target-group-arn $TG_ARN --output table
```

### 4. Test Application Endpoints
```bash
# Get ALB DNS name
export ALB_DNS=$(cd terraform && terraform output -raw alb_dns_name)

# Test health endpoint
curl -v http://$ALB_DNS/api/health

# Test email processing endpoint
curl -X POST http://$ALB_DNS/api/email \
  -H "Content-Type: application/json" \
  -d '{
    "data": {
      "email_subject": "Test Email CLI",
      "email_sender": "cli-test@example.com",
      "email_timestream": "'$(date +%s)'",
      "email_content": "This is a test from CLI"
    },
    "token": "'$(aws ssm get-parameter --name /email-processor/api-token --with-decryption --query Parameter.Value --output text)'"
  }'
```

---

## Troubleshooting

### 1. ECS Service Issues

#### Service Not Starting
```bash
# Check service events (last 10)
aws ecs describe-services \
  --cluster $CLUSTER_NAME \
  --services $SERVICE_1 \
  --query 'services[0].events[0:10].[createdAt,message]' \
  --output table

# Check all service events
aws ecs describe-services \
  --cluster $CLUSTER_NAME \
  --services $SERVICE_1 \
  --output json | jq '.services[0].events[] | {time: .createdAt, message: .message}'
```

#### Task Failures
```bash
# List stopped tasks (with reason)
aws ecs list-tasks --cluster $CLUSTER_NAME --desired-status STOPPED --max-results 5

# Describe stopped task
export STOPPED_TASK=$(aws ecs list-tasks --cluster $CLUSTER_NAME --desired-status STOPPED --max-results 1 --query 'taskArns[0]' --output text)

aws ecs describe-tasks \
  --cluster $CLUSTER_NAME \
  --tasks $STOPPED_TASK \
  --query 'tasks[0].[stoppedReason,stopCode,containers[0].reason]'

# Get detailed stop reason
aws ecs describe-tasks \
  --cluster $CLUSTER_NAME \
  --tasks $STOPPED_TASK \
  --output json | jq '.tasks[0] | {stoppedAt, stoppedReason, stopCode, containers: .containers[] | {name, reason, exitCode}}'
```

#### Container Startup Issues
```bash
# Check task execution role permissions
export EXEC_ROLE_ARN=$(aws ecs describe-task-definition \
  --task-definition email-processor-microservice-1 \
  --query 'taskDefinition.executionRoleArn' \
  --output text)

aws iam get-role --role-name $(echo $EXEC_ROLE_ARN | awk -F'/' '{print $NF}')

# Check task role permissions
export TASK_ROLE_ARN=$(aws ecs describe-task-definition \
  --task-definition email-processor-microservice-1 \
  --query 'taskDefinition.taskRoleArn' \
  --output text)

aws iam list-attached-role-policies --role-name $(echo $TASK_ROLE_ARN | awk -F'/' '{print $NF}')
```

### 2. CloudWatch Logs Analysis

#### View Real-time Logs
```bash
# Tail logs for microservice-1 (requires awslogs CLI or use native AWS CLI)
aws logs tail /ecs/email-processor-microservice-1 --follow --format short

# Tail logs for microservice-2
aws logs tail /ecs/email-processor-microservice-2 --follow --format short

# Filter logs by time (last 1 hour)
aws logs tail /ecs/email-processor-microservice-1 \
  --since 1h \
  --format short

# Filter logs by pattern
aws logs tail /ecs/email-processor-microservice-1 \
  --filter-pattern "ERROR" \
  --follow
```

#### Search Logs
```bash
# Get log streams
aws logs describe-log-streams \
  --log-group-name /ecs/email-processor-microservice-1 \
  --order-by LastEventTime \
  --descending \
  --max-items 5

# Get latest log stream
export LOG_STREAM=$(aws logs describe-log-streams \
  --log-group-name /ecs/email-processor-microservice-1 \
  --order-by LastEventTime \
  --descending \
  --max-items 1 \
  --query 'logStreams[0].logStreamName' \
  --output text)

# Get log events from specific stream
aws logs get-log-events \
  --log-group-name /ecs/email-processor-microservice-1 \
  --log-stream-name "$LOG_STREAM" \
  --limit 50 \
  --query 'events[*].[timestamp,message]' \
  --output text

# Filter logs with Insights query
aws logs start-query \
  --log-group-name /ecs/email-processor-microservice-1 \
  --start-time $(date -u -d '1 hour ago' +%s) \
  --end-time $(date -u +%s) \
  --query-string 'fields @timestamp, @message | filter @message like /ERROR/ | sort @timestamp desc | limit 20'

# Get query results (use query-id from previous command)
# aws logs get-query-results --query-id <query-id-from-above>
```

#### Export Logs for Analysis
```bash
# Export logs to file
aws logs filter-log-events \
  --log-group-name /ecs/email-processor-microservice-1 \
  --start-time $(date -u -d '1 hour ago' +%s)000 \
  --query 'events[*].message' \
  --output text > microservice-1-logs.txt

# Search exported logs
grep -i "error\|exception\|failed" microservice-1-logs.txt
```

### 3. Network Connectivity Issues

#### Security Group Rules
```bash
# Get security group IDs
export ALB_SG=$(aws ec2 describe-security-groups \
  --filters "Name=tag:Name,Values=*email-processor*alb*" \
  --query 'SecurityGroups[0].GroupId' \
  --output text)

export ECS_SG=$(aws ec2 describe-security-groups \
  --filters "Name=tag:Name,Values=*email-processor*ecs-tasks*" \
  --query 'SecurityGroups[0].GroupId' \
  --output text)

# Check ALB security group rules
aws ec2 describe-security-groups --group-ids $ALB_SG --output json | jq '.SecurityGroups[0].IpPermissions'

# Check ECS security group rules
aws ec2 describe-security-groups --group-ids $ECS_SG --output json | jq '.SecurityGroups[0].IpPermissions'
```

#### Subnet Configuration
```bash
# Check if private subnets have route to NAT Gateway
aws ec2 describe-route-tables \
  --filters "Name=tag:Name,Values=*email-processor*private*" \
  --query 'RouteTables[*].Routes' \
  --output table
```

#### Test Connectivity from Task
```bash
# Execute command in running ECS task (ECS Exec must be enabled)
aws ecs execute-command \
  --cluster $CLUSTER_NAME \
  --task $TASK_ARN \
  --container microservice-1 \
  --interactive \
  --command "/bin/sh"

# If ECS Exec is not enabled, you need to update the service first:
# aws ecs update-service --cluster $CLUSTER_NAME --service $SERVICE_1 --enable-execute-command
```

### 4. SQS Issues

#### Check Queue Status
```bash
# Get queue attributes
aws sqs get-queue-attributes \
  --queue-url $SQS_QUEUE_URL \
  --attribute-names All \
  --output json | jq

# Check messages in queue
aws sqs get-queue-attributes \
  --queue-url $SQS_QUEUE_URL \
  --attribute-names ApproximateNumberOfMessages,ApproximateNumberOfMessagesNotVisible,ApproximateNumberOfMessagesDelayed

# Receive messages (for testing)
aws sqs receive-message \
  --queue-url $SQS_QUEUE_URL \
  --max-number-of-messages 1 \
  --visibility-timeout 30 \
  --wait-time-seconds 5

# Check Dead Letter Queue
export DLQ_URL=$(aws sqs list-queues --queue-name-prefix email-processor-email-dlq --query 'QueueUrls[0]' --output text)

aws sqs get-queue-attributes \
  --queue-url $DLQ_URL \
  --attribute-names ApproximateNumberOfMessages

# View messages in DLQ (without deleting)
aws sqs receive-message \
  --queue-url $DLQ_URL \
  --max-number-of-messages 10 \
  --attribute-names All \
  --message-attribute-names All
```

#### Send Test Message
```bash
# Send a test message directly to SQS
aws sqs send-message \
  --queue-url $SQS_QUEUE_URL \
  --message-body '{
    "emailSubject": "Test from CLI",
    "emailSender": "cli@example.com",
    "emailTimestream": "'$(date +%s)'",
    "emailContent": "Test message",
    "correlationId": "test-'$(date +%s)'"
  }'
```

### 5. S3 Issues

#### Check Bucket Configuration
```bash
# Verify bucket exists
aws s3api head-bucket --bucket $S3_BUCKET_NAME

# Check bucket permissions
aws s3api get-bucket-policy --bucket $S3_BUCKET_NAME 2>/dev/null || echo "No bucket policy"

# Check encryption
aws s3api get-bucket-encryption --bucket $S3_BUCKET_NAME

# List recent objects
aws s3 ls s3://$S3_BUCKET_NAME/ --recursive --human-readable --summarize

# Check if objects are being created
aws s3api list-objects-v2 \
  --bucket $S3_BUCKET_NAME \
  --query 'Contents[*].[Key,LastModified,Size]' \
  --output table
```

#### Test S3 Write Permissions
```bash
# Create test file
echo "test" > /tmp/test.txt

# Try to upload (simulating what the application does)
aws s3 cp /tmp/test.txt s3://$S3_BUCKET_NAME/test/test.txt

# Verify upload
aws s3 ls s3://$S3_BUCKET_NAME/test/

# Clean up
aws s3 rm s3://$S3_BUCKET_NAME/test/test.txt
```

### 6. IAM Permission Issues

#### Verify Task Role Permissions
```bash
# Get task role
export TASK_ROLE=$(aws ecs describe-task-definition \
  --task-definition email-processor-microservice-1 \
  --query 'taskDefinition.taskRoleArn' \
  --output text | awk -F'/' '{print $NF}')

# List attached policies
aws iam list-attached-role-policies --role-name $TASK_ROLE

# Get policy document
export POLICY_ARN=$(aws iam list-attached-role-policies \
  --role-name $TASK_ROLE \
  --query 'AttachedPolicies[0].PolicyArn' \
  --output text)

export POLICY_VERSION=$(aws iam get-policy \
  --policy-arn $POLICY_ARN \
  --query 'Policy.DefaultVersionId' \
  --output text)

aws iam get-policy-version \
  --policy-arn $POLICY_ARN \
  --version-id $POLICY_VERSION \
  --query 'PolicyVersion.Document' | jq
```

#### Simulate IAM Policy
```bash
# Test if task role can send to SQS
aws iam simulate-principal-policy \
  --policy-source-arn arn:aws:iam::$AWS_ACCOUNT_ID:role/$TASK_ROLE \
  --action-names sqs:SendMessage \
  --resource-arns $(aws sqs get-queue-attributes --queue-url $SQS_QUEUE_URL --attribute-names QueueArn --query 'Attributes.QueueArn' --output text)

# Test if task role can write to S3
aws iam simulate-principal-policy \
  --policy-source-arn arn:aws:iam::$AWS_ACCOUNT_ID:role/$TASK_ROLE \
  --action-names s3:PutObject \
  --resource-arns arn:aws:s3:::$S3_BUCKET_NAME/*
```

### 7. Application-Level Debugging

#### Check Environment Variables
```bash
# View task definition environment variables
aws ecs describe-task-definition \
  --task-definition email-processor-microservice-1 \
  --query 'taskDefinition.containerDefinitions[0].environment' \
  --output table

# Check if SQS_QUEUE_URL is set correctly
aws ecs describe-task-definition \
  --task-definition email-processor-microservice-1 \
  --query 'taskDefinition.containerDefinitions[0].environment[?name==`SQS_QUEUE_URL`]' \
  --output table
```

#### SSM Parameter Issues
```bash
# Verify SSM parameter exists and is accessible
aws ssm get-parameter \
  --name /email-processor/api-token \
  --with-decryption \
  --query 'Parameter.[Name,Type,Value]' \
  --output table

# Check parameter history
aws ssm get-parameter-history \
  --name /email-processor/api-token \
  --query 'Parameters[*].[LastModifiedDate,Value]' \
  --output table
```

---

## Monitoring

### 1. CloudWatch Metrics

#### ECS Service Metrics
```bash
# Get CPU utilization
aws cloudwatch get-metric-statistics \
  --namespace AWS/ECS \
  --metric-name CPUUtilization \
  --dimensions Name=ServiceName,Value=$SERVICE_1 Name=ClusterName,Value=$CLUSTER_NAME \
  --start-time $(date -u -d '1 hour ago' +%Y-%m-%dT%H:%M:%S) \
  --end-time $(date -u +%Y-%m-%dT%H:%M:%S) \
  --period 300 \
  --statistics Average,Maximum \
  --output table

# Get memory utilization
aws cloudwatch get-metric-statistics \
  --namespace AWS/ECS \
  --metric-name MemoryUtilization \
  --dimensions Name=ServiceName,Value=$SERVICE_1 Name=ClusterName,Value=$CLUSTER_NAME \
  --start-time $(date -u -d '1 hour ago' +%Y-%m-%dT%H:%M:%S) \
  --end-time $(date -u +%Y-%m-%dT%H:%M:%S) \
  --period 300 \
  --statistics Average,Maximum \
  --output table
```

#### ALB Metrics
```bash
# Get target response time
aws cloudwatch get-metric-statistics \
  --namespace AWS/ApplicationELB \
  --metric-name TargetResponseTime \
  --dimensions Name=LoadBalancer,Value=$(echo $ALB_DNS | cut -d'-' -f2-) \
  --start-time $(date -u -d '1 hour ago' +%Y-%m-%dT%H:%M:%S) \
  --end-time $(date -u +%Y-%m-%dT%H:%M:%S) \
  --period 300 \
  --statistics Average,Maximum \
  --output table

# Get request count
aws cloudwatch get-metric-statistics \
  --namespace AWS/ApplicationELB \
  --metric-name RequestCount \
  --dimensions Name=LoadBalancer,Value=$(echo $ALB_DNS | cut -d'-' -f2-) \
  --start-time $(date -u -d '1 hour ago' +%Y-%m-%dT%H:%M:%S) \
  --end-time $(date -u +%Y-%m-%dT%H:%M:%S) \
  --period 300 \
  --statistics Sum \
  --output table

# Get HTTP error codes
aws cloudwatch get-metric-statistics \
  --namespace AWS/ApplicationELB \
  --metric-name HTTPCode_Target_5XX_Count \
  --dimensions Name=LoadBalancer,Value=$(echo $ALB_DNS | cut -d'-' -f2-) \
  --start-time $(date -u -d '1 hour ago' +%Y-%m-%dT%H:%M:%S) \
  --end-time $(date -u +%Y-%m-%dT%H:%M:%S) \
  --period 300 \
  --statistics Sum \
  --output table
```

#### SQS Metrics
```bash
# Get number of messages sent
aws cloudwatch get-metric-statistics \
  --namespace AWS/SQS \
  --metric-name NumberOfMessagesSent \
  --dimensions Name=QueueName,Value=email-processor-email-queue \
  --start-time $(date -u -d '1 hour ago' +%Y-%m-%dT%H:%M:%S) \
  --end-time $(date -u +%Y-%m-%dT%H:%M:%S) \
  --period 300 \
  --statistics Sum \
  --output table

# Get number of messages received
aws cloudwatch get-metric-statistics \
  --namespace AWS/SQS \
  --metric-name NumberOfMessagesReceived \
  --dimensions Name=QueueName,Value=email-processor-email-queue \
  --start-time $(date -u -d '1 hour ago' +%Y-%m-%dT%H:%M:%S) \
  --end-time $(date -u +%Y-%m-%dT%H:%M:%S) \
  --period 300 \
  --statistics Sum \
  --output table

# Get approximate age of oldest message
aws cloudwatch get-metric-statistics \
  --namespace AWS/SQS \
  --metric-name ApproximateAgeOfOldestMessage \
  --dimensions Name=QueueName,Value=email-processor-email-queue \
  --start-time $(date -u -d '1 hour ago' +%Y-%m-%dT%H:%M:%S) \
  --end-time $(date -u +%Y-%m-%dT%H:%M:%S) \
  --period 300 \
  --statistics Maximum \
  --output table
```

### 2. Create Custom Dashboard (CloudWatch)
```bash
# Create a dashboard with all key metrics
aws cloudwatch put-dashboard \
  --dashboard-name EmailProcessorDashboard \
  --dashboard-body file://dashboard-config.json

# To create dashboard-config.json, see example in appendix
```

### 3. Set Up Alarms
```bash
# Create alarm for high error rate
aws cloudwatch put-metric-alarm \
  --alarm-name email-processor-high-errors \
  --alarm-description "Alert when 5XX errors exceed threshold" \
  --metric-name HTTPCode_Target_5XX_Count \
  --namespace AWS/ApplicationELB \
  --statistic Sum \
  --period 300 \
  --threshold 10 \
  --comparison-operator GreaterThanThreshold \
  --evaluation-periods 2 \
  --datapoints-to-alarm 2

# Create alarm for DLQ messages
export DLQ_NAME=$(echo $DLQ_URL | awk -F'/' '{print $NF}')

aws cloudwatch put-metric-alarm \
  --alarm-name email-processor-dlq-messages \
  --alarm-description "Alert when DLQ has messages" \
  --metric-name ApproximateNumberOfMessagesVisible \
  --namespace AWS/SQS \
  --statistic Average \
  --period 300 \
  --threshold 1 \
  --comparison-operator GreaterThanThreshold \
  --evaluation-periods 1 \
  --dimensions Name=QueueName,Value=$DLQ_NAME

# List all alarms
aws cloudwatch describe-alarms \
  --alarm-name-prefix email-processor \
  --query 'MetricAlarms[*].[AlarmName,StateValue,StateReason]' \
  --output table
```

---

## Common Issues

### Issue 1: Service Cannot Pull Image from ECR

**Symptoms:**
- Tasks fail to start
- Error: "CannotPullContainerError"

**Diagnosis:**
```bash
# Check if image exists in ECR
aws ecr describe-images \
  --repository-name email-processor-microservice-1 \
  --image-ids imageTag=latest

# Check task execution role has ECR permissions
aws iam get-role-policy \
  --role-name email-processor-ecs-task-execution-role \
  --policy-name ECRPolicy 2>/dev/null || echo "No ECR policy attached"

# Check if ECR is accessible from private subnet
# Verify VPC has S3 and ECR endpoints or NAT Gateway
```

**Solution:**
```bash
# Ensure task execution role has ECR permissions
# This should already be attached via AmazonECSTaskExecutionRolePolicy

# Verify NAT Gateway is working
aws ec2 describe-nat-gateways \
  --filter "Name=tag:Name,Values=*email-processor*" \
  --query 'NatGateways[*].[NatGatewayId,State]' \
  --output table

# Check if VPC endpoints exist (alternative to NAT)
aws ec2 describe-vpc-endpoints \
  --filters "Name=vpc-id,Values=$(aws ec2 describe-vpcs --filters Name=tag:Name,Values=*email-processor* --query 'Vpcs[0].VpcId' --output text)" \
  --query 'VpcEndpoints[*].[ServiceName,State]' \
  --output table
```

### Issue 2: Tasks Start but Immediately Stop

**Symptoms:**
- Tasks go from PENDING to STOPPED quickly
- No application logs in CloudWatch

**Diagnosis:**
```bash
# Get stopped task details
export STOPPED_TASK=$(aws ecs list-tasks --cluster $CLUSTER_NAME --desired-status STOPPED --max-results 1 --query 'taskArns[0]' --output text)

aws ecs describe-tasks \
  --cluster $CLUSTER_NAME \
  --tasks $STOPPED_TASK \
  --query 'tasks[0].[stoppedReason,containers[0].reason,containers[0].exitCode]' \
  --output json | jq

# Check application logs
aws logs tail /ecs/email-processor-microservice-1 --since 30m
```

**Common Causes:**
1. Application crashes on startup
2. Missing environment variables
3. Cannot connect to AWS services (SQS, S3, SSM)
4. Port configuration mismatch

**Solution:**
```bash
# Verify environment variables are set
aws ecs describe-task-definition \
  --task-definition email-processor-microservice-1 \
  --query 'taskDefinition.containerDefinitions[0].environment'

# Check IAM permissions
aws iam simulate-principal-policy \
  --policy-source-arn arn:aws:iam::$AWS_ACCOUNT_ID:role/$TASK_ROLE \
  --action-names ssm:GetParameter sqs:SendMessage s3:PutObject \
  --resource-arns "*"
```

### Issue 3: Cannot Access Application via ALB

**Symptoms:**
- 503 Service Unavailable
- Connection timeout

**Diagnosis:**
```bash
# Check target health
export TG_ARN=$(aws elbv2 describe-target-groups \
  --query 'TargetGroups[?contains(TargetGroupName, `microservice-1`)].TargetGroupArn' \
  --output text)

aws elbv2 describe-target-health --target-group-arn $TG_ARN

# Check if tasks are registered with target group
aws elbv2 describe-target-health \
  --target-group-arn $TG_ARN \
  --query 'TargetHealthDescriptions[*].[Target.Id,TargetHealth.State,TargetHealth.Reason]' \
  --output table
```

**Solution:**
```bash
# If no targets are registered, check service configuration
aws ecs describe-services \
  --cluster $CLUSTER_NAME \
  --services $SERVICE_1 \
  --query 'services[0].loadBalancers'

# Verify security group allows traffic from ALB to ECS tasks
aws ec2 describe-security-groups --group-ids $ECS_SG \
  --query 'SecurityGroups[0].IpPermissions[?FromPort==`8080`]'

# Check health check configuration
aws elbv2 describe-target-groups \
  --target-group-arns $TG_ARN \
  --query 'TargetGroups[0].HealthCheckPath'
```

### Issue 4: Messages Not Being Processed

**Symptoms:**
- Messages pile up in SQS queue
- No objects created in S3

**Diagnosis:**
```bash
# Check queue depth
aws sqs get-queue-attributes \
  --queue-url $SQS_QUEUE_URL \
  --attribute-names ApproximateNumberOfMessages,ApproximateNumberOfMessagesNotVisible

# Check if microservice-2 is running
aws ecs describe-services \
  --cluster $CLUSTER_NAME \
  --services $SERVICE_2 \
  --query 'services[0].[serviceName,status,runningCount,desiredCount]'

# Check microservice-2 logs
aws logs tail /ecs/email-processor-microservice-2 --follow
```

**Solution:**
```bash
# Scale up microservice-2 if needed
aws ecs update-service \
  --cluster $CLUSTER_NAME \
  --service $SERVICE_2 \
  --desired-count 2

# Check DLQ for failed messages
export DLQ_URL=$(aws sqs list-queues --queue-name-prefix email-processor-email-dlq --query 'QueueUrls[0]' --output text)

aws sqs receive-message \
  --queue-url $DLQ_URL \
  --max-number-of-messages 10 \
  --attribute-names All

# Reprocess DLQ messages (move back to main queue)
# This requires a script to receive from DLQ and send to main queue
```

### Issue 5: High Memory or CPU Usage

**Diagnosis:**
```bash
# Check current metrics
aws cloudwatch get-metric-statistics \
  --namespace AWS/ECS \
  --metric-name MemoryUtilization \
  --dimensions Name=ServiceName,Value=$SERVICE_1 Name=ClusterName,Value=$CLUSTER_NAME \
  --start-time $(date -u -d '1 hour ago' +%Y-%m-%dT%H:%M:%S) \
  --end-time $(date -u +%Y-%m-%dT%H:%M:%S) \
  --period 300 \
  --statistics Average,Maximum \
  --output table

# Check if auto-scaling is working
aws application-autoscaling describe-scaling-activities \
  --service-namespace ecs \
  --resource-id service/$CLUSTER_NAME/$SERVICE_1 \
  --max-results 10
```

**Solution:**
```bash
# Manually scale service
aws ecs update-service \
  --cluster $CLUSTER_NAME \
  --service $SERVICE_1 \
  --desired-count 3

# Increase task resources
# Edit task definition to increase CPU/memory, then update service

# Update auto-scaling thresholds
aws application-autoscaling put-scaling-policy \
  --policy-name $SERVICE_1-scaling \
  --service-namespace ecs \
  --resource-id service/$CLUSTER_NAME/$SERVICE_1 \
  --scalable-dimension ecs:service:DesiredCount \
  --policy-type TargetTrackingScaling \
  --target-tracking-scaling-policy-configuration file://scaling-config.json
```

---

## Rollback Procedures

### Rollback ECS Service to Previous Task Definition
```bash
# List task definition revisions
aws ecs list-task-definitions \
  --family-prefix email-processor-microservice-1 \
  --sort DESC

# Rollback to previous revision
aws ecs update-service \
  --cluster $CLUSTER_NAME \
  --service $SERVICE_1 \
  --task-definition email-processor-microservice-1:1  # Replace with desired revision

# Wait for rollback to complete
aws ecs wait services-stable --cluster $CLUSTER_NAME --services $SERVICE_1
```

### Rollback Infrastructure Changes
```bash
cd terraform

# View state file
terraform show

# Rollback to previous Terraform state
terraform state pull > backup-$(date +%Y%m%d-%H%M%S).tfstate

# Apply previous configuration from git
git log --oneline terraform/
git checkout <previous-commit-hash> -- terraform/
terraform plan
terraform apply
```

### Emergency Cleanup
```bash
# Stop all tasks (for emergency maintenance)
aws ecs update-service \
  --cluster $CLUSTER_NAME \
  --service $SERVICE_1 \
  --desired-count 0

aws ecs update-service \
  --cluster $CLUSTER_NAME \
  --service $SERVICE_2 \
  --desired-count 0

# Destroy entire infrastructure (CAUTION!)
cd terraform
terraform destroy
```

---

## Useful Scripts

### Complete Health Check Script
```bash
#!/bin/bash
# save as: health-check.sh

echo "=== Email Processor Health Check ==="
echo ""

# Load environment variables
export CLUSTER_NAME=$(aws ecs list-clusters --query 'clusterArns[0]' --output text | awk -F'/' '{print $NF}')
export SERVICE_1="email-processor-microservice-1"
export SERVICE_2="email-processor-microservice-2"

# Check ECS Services
echo "1. ECS Services Status:"
aws ecs describe-services \
  --cluster $CLUSTER_NAME \
  --services $SERVICE_1 $SERVICE_2 \
  --query 'services[*].[serviceName,status,runningCount,desiredCount]' \
  --output table

# Check Load Balancer
echo ""
echo "2. Load Balancer Target Health:"
export TG_ARN=$(aws elbv2 describe-target-groups \
  --query 'TargetGroups[?contains(TargetGroupName, `microservice-1`)].TargetGroupArn' \
  --output text)
aws elbv2 describe-target-health --target-group-arn $TG_ARN --output table

# Check SQS Queue
echo ""
echo "3. SQS Queue Status:"
export SQS_QUEUE_URL=$(aws sqs list-queues --queue-name-prefix email-processor-email-queue --query 'QueueUrls[0]' --output text)
aws sqs get-queue-attributes \
  --queue-url $SQS_QUEUE_URL \
  --attribute-names ApproximateNumberOfMessages,ApproximateNumberOfMessagesNotVisible \
  --query 'Attributes' \
  --output table

# Check S3 Bucket
echo ""
echo "4. S3 Bucket Recent Objects:"
export S3_BUCKET_NAME=$(aws s3 ls | grep email-processor-email-storage | awk '{print $3}')
aws s3 ls s3://$S3_BUCKET_NAME/ --recursive --human-readable | tail -5

echo ""
echo "=== Health Check Complete ==="
```

### Log Monitoring Script
```bash
#!/bin/bash
# save as: monitor-logs.sh

# Usage: ./monitor-logs.sh microservice-1
SERVICE=$1

if [ -z "$SERVICE" ]; then
  echo "Usage: $0 <microservice-1|microservice-2>"
  exit 1
fi

echo "Monitoring logs for $SERVICE..."
echo "Press Ctrl+C to stop"
echo ""

aws logs tail /ecs/email-processor-$SERVICE --follow --format short \
  | while read line; do
    echo "$line" | grep --color=auto -E "ERROR|WARN|Exception|Failed|$"
  done
```

Make scripts executable:
```bash
chmod +x health-check.sh monitor-logs.sh
```

---

## Additional Resources

### Quick Reference Commands
```bash
# Export all important variables for your session
export AWS_REGION=eu-west-1
export AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
export CLUSTER_NAME="email-processor-cluster"
export SERVICE_1="email-processor-microservice-1"
export SERVICE_2="email-processor-microservice-2"
export SQS_QUEUE_URL=$(aws sqs list-queues --queue-name-prefix email-processor-email-queue --query 'QueueUrls[0]' --output text)
export S3_BUCKET_NAME=$(aws s3 ls | grep email-processor-email-storage | awk '{print $3}')
export ALB_DNS=$(cd terraform && terraform output -raw alb_dns_name 2>/dev/null || echo "Run terraform output to get ALB DNS")

# Print all variables
echo "AWS_REGION=$AWS_REGION"
echo "AWS_ACCOUNT_ID=$AWS_ACCOUNT_ID"
echo "CLUSTER_NAME=$CLUSTER_NAME"
echo "SQS_QUEUE_URL=$SQS_QUEUE_URL"
echo "S3_BUCKET_NAME=$S3_BUCKET_NAME"
echo "ALB_DNS=$ALB_DNS"
```

### CloudWatch Logs Insights Queries

**Find all errors:**
```
fields @timestamp, @message
| filter @message like /ERROR/
| sort @timestamp desc
| limit 50
```

**Find slow requests:**
```
fields @timestamp, @message
| filter @message like /Processing time/
| parse @message /Processing time: (?<duration>\d+)ms/
| filter duration > 1000
| sort duration desc
```

**Count messages by log level:**
```
fields @timestamp, @message
| stats count() by @logLevel
```

---

## Summary

This guide provides comprehensive CLI-only management for your email processor microservices:

1. **Complete CLI Control**: Every operation can be performed via AWS CLI, Terraform CLI, or Docker CLI
2. **No Console Access Required**: All monitoring, debugging, and management tasks are scriptable
3. **Troubleshooting Tools**: Detailed commands for diagnosing and fixing common issues
4. **Automation Ready**: All commands can be scripted for CI/CD pipelines
5. **Production Ready**: Includes monitoring, alerting, and rollback procedures

For daily operations, use the health-check script. For troubleshooting, refer to the specific sections based on the symptoms you're seeing.

