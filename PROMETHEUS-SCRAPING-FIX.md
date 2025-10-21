# Prometheus Scraping Troubleshooting Guide

## Error Observed
```
Failed to scrape Prometheus endpoint {"instance":"localhost:8080", "job":"spring-boot-metrics"}
```

## Root Cause Analysis

This error indicates that the ADOT (AWS Distro for OpenTelemetry) collector sidecar is trying to scrape metrics from your Spring Boot application but failing. This can happen for several reasons:

### 1. **Application Not Fully Started**
- ADOT sidecar starts at the same time as your app
- If ADOT tries to scrape before the app is healthy, it will fail
- This is common with Spring Boot apps that take time to initialize

### 2. **Actuator Endpoint Not Accessible**
- The `/actuator/prometheus` endpoint might not be exposed
- Micrometer Prometheus registry might not be configured correctly
- Management endpoints might be on a different port

### 3. **Container Networking Issue**
- In ECS with `awsvpc` mode, containers in the same task share the network namespace
- Both containers should be able to communicate via `localhost`
- However, if one container crashes/restarts, connectivity breaks

## Quick Diagnostic Commands

### 1. Check Service Health
```bash
# Check if services are running
aws ecs describe-services \
  --cluster email-processor-cluster \
  --services email-processor-microservice-1 email-processor-microservice-2 \
  --region us-west-1 \
  --query 'services[*].[serviceName,runningCount,desiredCount]' \
  --output table
```

### 2. Check Container Logs
```bash
# Microservice-1 application logs
aws logs tail /ecs/email-processor-microservice-1 \
  --since 10m \
  --filter-pattern "ecs" \
  --region us-west-1 \
  --follow

# Microservice-1 ADOT logs (look for scrape errors)
aws logs tail /ecs/email-processor-microservice-1 \
  --since 10m \
  --filter-pattern "adot" \
  --region us-west-1 \
  --follow
```

### 3. Run Comprehensive Diagnostic
```bash
./troubleshoot-prometheus.sh
```

## Solutions

### Solution 1: Verify Application Health

The Spring Boot app must be running and healthy before metrics can be scraped.

**Check if the app started successfully:**
```bash
aws logs filter-log-events \
  --log-group-name /ecs/email-processor-microservice-1 \
  --filter-pattern "Started EmailProcessorApiApplication" \
  --region us-west-1 \
  --max-items 5
```

**Look for these indicators:**
- ✅ "Started EmailProcessorApiApplication in X seconds"
- ✅ "Tomcat started on port 8080"
- ✅ "Exposing 4 endpoint(s) beneath base path '/actuator'"

**If app is not starting:**
- Check for missing environment variables (SQS_QUEUE_URL, SSM_PARAMETER_NAME, etc.)
- Review application error logs
- Verify IAM permissions for SSM Parameter Store access

### Solution 2: Verify Actuator Endpoint

**Test the endpoint from within the container:**

1. Get a running task ID:
```bash
TASK_ARN=$(aws ecs list-tasks \
  --cluster email-processor-cluster \
  --service-name email-processor-microservice-1 \
  --desired-status RUNNING \
  --region us-west-1 \
  --query 'taskArns[0]' \
  --output text)

echo ${TASK_ARN##*/}
```

2. Enable ECS Exec (if not already enabled) - requires task definition update:
```bash
aws ecs update-service \
  --cluster email-processor-cluster \
  --service email-processor-microservice-1 \
  --enable-execute-command \
  --region us-west-1
```

3. Exec into the container:
```bash
aws ecs execute-command \
  --cluster email-processor-cluster \
  --task <task-id> \
  --container microservice-1 \
  --command "/bin/sh" \
  --interactive \
  --region us-west-1
```

4. Test the endpoint:
```bash
# Inside the container
apk add curl  # If curl not available
curl http://localhost:8080/actuator/prometheus
```

**Expected output:** Prometheus-formatted metrics like:
```
# HELP jvm_memory_used_bytes The amount of used memory
# TYPE jvm_memory_used_bytes gauge
jvm_memory_used_bytes{area="heap",id="PS Eden Space",} 1.23456789E8
...
```

### Solution 3: Add Health Check Delay to ADOT

The ADOT sidecar might be trying to scrape before the app is ready. Add a startup delay:

**Update the ADOT container configuration in `terraform/modules/ecs-service/main.tf`:**

```hcl
# Around line 319, in the ADOT container definition
{
  name  = "adot-collector"
  image = "public.ecr.aws/aws-observability/aws-otel-collector:v0.40.0"
  
  # Add this health check
  healthCheck = {
    command     = ["CMD-SHELL", "wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1"]
    interval    = 30
    timeout     = 5
    retries     = 3
    startPeriod = 60  # Give app 60 seconds to start before checking
  }
  
  # Add dependency on app container
  dependsOn = [
    {
      containerName = var.service_name
      condition     = "HEALTHY"
    }
  ]
  
  # ... rest of config
}
```

### Solution 4: Check ADOT Configuration

Verify the ADOT collector is configured correctly:

**Check ADOT logs for configuration issues:**
```bash
aws logs filter-log-events \
  --log-group-name /ecs/email-processor-microservice-1 \
  --filter-pattern "error" \
  --region us-west-1 \
  --max-items 20 | grep -i "adot\|prometheus\|scrape"
```

**Common ADOT errors:**
- "connection refused" → App not started yet or not listening on port 8080
- "404 Not Found" → `/actuator/prometheus` endpoint doesn't exist
- "authentication failed" → IAM permissions issue for Prometheus remote write

### Solution 5: Verify IAM Permissions

Ensure the ECS task role has permission to write to AWS Managed Prometheus:

```bash
# Check which role is attached to the service
aws ecs describe-services \
  --cluster email-processor-cluster \
  --services email-processor-microservice-1 \
  --region us-west-1 \
  --query 'services[0].taskDefinition' \
  --output text

# Get the task definition
TASK_DEF=$(aws ecs describe-task-definition \
  --task-definition <task-def-from-above> \
  --region us-west-1 \
  --query 'taskDefinition.taskRoleArn' \
  --output text)

# Check attached policies
aws iam list-attached-role-policies \
  --role-name $(echo $TASK_DEF | cut -d'/' -f2) \
  --region us-west-1
```

**Required permission:**
The task role should have a policy with `aps:RemoteWrite` permission.

### Solution 6: Verify Prometheus Workspace

```bash
# Check workspace status
aws amp list-workspaces --region us-west-1

# Get workspace details
WORKSPACE_ID=$(aws amp list-workspaces \
  --region us-west-1 \
  --query "workspaces[?alias=='email-processor-prometheus'].workspaceId" \
  --output text)

aws amp describe-workspace \
  --workspace-id $WORKSPACE_ID \
  --region us-west-1
```

**Workspace status should be:** `ACTIVE`

## Expected Behavior When Working

When everything is working correctly, you should see:

### In Application Logs:
```
Started EmailProcessorApiApplication in 12.345 seconds
Tomcat started on port(s): 8080 (http)
Exposing 4 endpoint(s) beneath base path '/actuator'
```

### In ADOT Logs:
```
Metrics sent to Prometheus workspace
Successfully scraped 127.0.0.1:8080
Remote write successful
```

### In Grafana:
- Metrics should appear in datasource
- Dashboards should show data
- Example metrics:
  - `http_server_requests_seconds_count`
  - `jvm_memory_used_bytes`
  - `process_cpu_usage`

## Manual Testing

### Test Actuator Endpoints Locally

If you want to test before deploying:

```bash
# Build and run microservice-1 locally
cd microservice-1
mvn spring-boot:run

# In another terminal
curl http://localhost:8080/actuator/health
curl http://localhost:8080/actuator/prometheus | head -20
```

## Next Steps

1. **Run the diagnostic script:**
   ```bash
   ./troubleshoot-prometheus.sh
   ```

2. **Check the output and identify which step is failing:**
   - Service not running?
   - Containers unhealthy?
   - ADOT showing errors?
   - App not exposing metrics?

3. **Apply the relevant solution above**

4. **If still not working, collect these logs:**
   ```bash
   # Collect all relevant logs
   aws logs tail /ecs/email-processor-microservice-1 --since 30m > microservice1-logs.txt
   aws logs tail /ecs/email-processor-microservice-2 --since 30m > microservice2-logs.txt
   ```

## Common Quick Fixes

### Quick Fix 1: Restart Services
Sometimes a simple restart resolves transient issues:
```bash
aws ecs update-service \
  --cluster email-processor-cluster \
  --service email-processor-microservice-1 \
  --force-new-deployment \
  --region us-west-1

aws ecs update-service \
  --cluster email-processor-cluster \
  --service email-processor-microservice-2 \
  --force-new-deployment \
  --region us-west-1
```

### Quick Fix 2: Check Environment Variables
Ensure all required environment variables are set in the task definition:
- `AWS_DEFAULT_REGION`
- `SQS_QUEUE_URL`
- `S3_BUCKET_NAME` (microservice-2 only)
- `SSM_PARAMETER_NAME` (microservice-1 only)

Missing environment variables can prevent the app from starting, which prevents ADOT from scraping metrics.

## Additional Resources

- [AWS Managed Prometheus Documentation](https://docs.aws.amazon.com/prometheus/)
- [ADOT Collector Documentation](https://aws-otel.github.io/docs/introduction)
- [Spring Boot Actuator Guide](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html)
- [Micrometer Prometheus](https://micrometer.io/docs/registry/prometheus)


