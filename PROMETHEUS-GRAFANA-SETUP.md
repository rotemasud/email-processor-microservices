# AWS Prometheus & Grafana Integration - Implementation Summary

This document summarizes the implementation of AWS Managed Prometheus and Grafana monitoring for the Email Processor microservices.

## Overview

The monitoring solution provides comprehensive observability for both microservices using:
- **AWS Managed Prometheus (AMP)**: Centralized metrics storage
- **AWS Managed Grafana (AMG)**: Visualization and dashboarding
- **AWS Distro for OpenTelemetry (ADOT)**: Metrics collection via sidecars
- **Spring Boot Actuator + Micrometer**: Prometheus metrics exposition

## Changes Made

### 1. Microservice Code Changes

#### Both Microservices (microservice-1 & microservice-2)

**Dependencies Added (`pom.xml`):**
```xml
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

**Configuration Updates (`application.yml`):**
- Exposed `/actuator/prometheus` endpoint
- Enabled Prometheus metrics export
- Added service tags for metric identification
- Configured histogram percentiles for HTTP requests

#### Microservice-1 (API Service)

**New Files:**
- `src/main/java/com/emailprocessor/api/config/MetricsConfig.java`
  - Configured custom metrics beans (Counters and Timers)
  - Metrics for SQS publishing, validation, and API requests

**Modified Files:**
- `src/main/java/com/emailprocessor/api/service/SqsPublisherService.java`
  - Added metrics tracking for message publishing
  - Tracks success/failure counts and duration
  
- `src/main/java/com/emailprocessor/api/service/ValidationService.java`
  - Added metrics for token and email data validation
  - Separate counters for different validation failure types

**Custom Metrics Exposed:**
- `sqs_messages_sent_total`: Total SQS messages sent
- `sqs_messages_sent_failure_total`: Failed SQS sends
- `sqs_publish_duration_seconds`: SQS publish latency
- `validation_success_total`: Successful validations
- `validation_failure_total{type}`: Validation failures by type

#### Microservice-2 (Consumer Service)

**New Files:**
- `src/main/java/com/emailprocessor/processor/config/MetricsConfig.java`
  - Configured custom metrics beans
  - Metrics for SQS consumption, S3 uploads, and processing

**Modified Files:**
- `src/main/java/com/emailprocessor/processor/service/SqsPollerService.java`
  - Added metrics for messages received
  
- `src/main/java/com/emailprocessor/processor/service/MessageProcessor.java`
  - Added metrics for processing success/failure
  - Tracks processing duration
  
- `src/main/java/com/emailprocessor/processor/service/S3UploaderService.java`
  - Added metrics for S3 upload success/failure
  - Tracks upload duration and file sizes

**Custom Metrics Exposed:**
- `sqs_messages_received_total`: Total SQS messages received
- `sqs_messages_processed_total{status}`: Processing outcomes
- `sqs_message_processing_duration_seconds`: Processing latency
- `s3_uploads_total{status}`: S3 upload outcomes
- `s3_upload_duration_seconds`: Upload latency
- `s3_upload_file_size_bytes`: File size distribution

### 2. Terraform Infrastructure Changes

#### New Module: `modules/monitoring`

**Files Created:**
- `modules/monitoring/main.tf`
  - AWS Managed Prometheus workspace
  - AWS Managed Grafana workspace
  - IAM roles and policies for Grafana and ADOT
  - SSM parameter for ADOT configuration

- `modules/monitoring/variables.tf`
  - Input variables for workspace names

- `modules/monitoring/outputs.tf`
  - Prometheus and Grafana endpoint URLs
  - IAM policy ARNs for integration

**Resources Created:**
- `aws_prometheus_workspace.main`: Prometheus workspace
- `aws_grafana_workspace.main`: Grafana workspace with AWS SSO auth
- `aws_iam_role.grafana`: Grafana service role
- `aws_iam_policy.grafana_prometheus_query`: Query permissions
- `aws_iam_policy.prometheus_remote_write`: ADOT write permissions
- `aws_ssm_parameter.adot_config`: ADOT collector configuration

#### Updated Module: `modules/ecs-service`

**Modified Files:**
- `modules/ecs-service/variables.tf`
  - Added `prometheus_remote_write_url` variable
  - Added `prometheus_remote_write_policy_arn` variable
  - Added `enable_prometheus` boolean flag

- `modules/ecs-service/main.tf`
  - Added ADOT collector sidecar container to task definitions
  - Configured ADOT to scrape `localhost:8080/actuator/prometheus`
  - Set up remote write to AWS Managed Prometheus
  - Added IAM policy attachment for Prometheus write permissions
  - Added CloudWatch log policy for ADOT logs

**ADOT Sidecar Configuration:**
- Image: `public.ecr.aws/aws-observability/aws-otel-collector:v0.40.0`
- Scrapes metrics every 30 seconds
- Uses SigV4 authentication for AMP
- Includes batch processing and resource attributes

#### Updated: `terraform/main.tf`

**Changes:**
- Added `monitoring` module instantiation
- Passed Prometheus configuration to both microservice modules
- Wired up IAM permissions for metrics collection

#### Updated: `terraform/outputs.tf`

**New Outputs:**
- `prometheus_workspace_id`: AMP workspace ID
- `prometheus_endpoint`: Query endpoint URL
- `grafana_workspace_endpoint`: Grafana URL
- `grafana_workspace_id`: AMG workspace ID

#### New File: `terraform/adot-config.yaml`

ADOT collector configuration template:
- Prometheus receiver configuration
- Batch processor settings
- Remote write exporter with SigV4 auth
- Resource attribution

### 3. Grafana Dashboards

**Directory Created:** `grafana-dashboards/`

**Dashboard Files:**

1. **`jvm-dashboard.json`**
   - 6 panels covering JVM metrics
   - Memory usage (heap/non-heap)
   - Garbage collection metrics
   - Thread counts
   - CPU usage
   - GC pause times

2. **`http-metrics-dashboard.json`**
   - 5 panels for HTTP monitoring
   - Request rate by endpoint
   - Latency percentiles (p50, p95)
   - Status code distribution
   - Error rate tracking (4xx/5xx)

3. **`business-metrics-dashboard.json`**
   - 9 panels for business KPIs
   - SQS message flow (sent, received, processed)
   - Validation metrics
   - S3 upload metrics
   - Processing and upload durations
   - File size distribution

**Dashboard README:**
- `grafana-dashboards/README.md`: Import instructions and customization guide

### 4. Documentation

**New Files:**

1. **`MONITORING.md`**
   - Complete monitoring guide
   - Architecture diagram
   - Grafana access instructions
   - Metric reference documentation
   - Example PromQL queries
   - Troubleshooting guide
   - Cost optimization tips

2. **`grafana-dashboards/README.md`**
   - Dashboard descriptions
   - Import instructions
   - Customization guide
   - Query examples
   - Troubleshooting tips

3. **`PROMETHEUS-GRAFANA-SETUP.md`** (this file)
   - Implementation summary
   - Complete change log

**Updated Files:**

1. **`README.md`**
   - Added monitoring infrastructure to component list
   - Added "Monitoring & Observability" section
   - Added links to MONITORING.md
   - Listed available metrics

## Deployment Steps

### 1. Apply Terraform Changes

```bash
cd terraform
terraform init -upgrade
terraform plan
terraform apply
```

This will create:
- AWS Managed Prometheus workspace
- AWS Managed Grafana workspace
- IAM roles and policies
- Update ECS task definitions with ADOT sidecars

### 2. Rebuild and Deploy Microservices

```bash
# Build microservice-1
cd microservice-1
mvn clean package
docker build -t microservice-1:latest .

# Build microservice-2
cd microservice-2
mvn clean package
docker build -t microservice-2:latest .

# Push to ECR and deploy via your CI/CD pipeline
```

### 3. Configure AWS SSO for Grafana

```bash
# Get Grafana workspace ID
terraform output grafana_workspace_id

# Configure SSO in AWS Console or via CLI
# Follow AWS documentation for SSO setup
```

### 4. Import Grafana Dashboards

1. Get Grafana URL: `terraform output grafana_workspace_endpoint`
2. Log in with AWS SSO
3. Import dashboards from `grafana-dashboards/` directory
4. Select Prometheus data source (auto-configured)

## Verification

### Check Metrics Endpoint

```bash
# For microservice-1 (via ALB)
ALB_DNS=$(terraform output -raw alb_dns_name)
curl http://$ALB_DNS/actuator/prometheus

# Should return Prometheus metrics in text format
```

### Verify ADOT Collector

```bash
# Check ADOT sidecar logs
aws logs tail /ecs/<project-name>-microservice-1 --follow --filter-pattern "adot"
```

### Test Prometheus

```bash
# Get Prometheus endpoint
PROM_ENDPOINT=$(terraform output -raw prometheus_endpoint)

# Query via AWS CLI (requires additional setup)
aws amp query-workspaces --alias email-processor-metrics
```

### Verify Grafana

1. Navigate to Grafana URL
2. Go to Explore
3. Run query: `up{service=~"microservice.*"}`
4. Should see both services with value `1`

## Metrics Available

### Standard JVM Metrics (Both Services)

- `jvm_memory_used_bytes{area}`: Memory usage
- `jvm_memory_max_bytes{area}`: Memory limits
- `jvm_gc_pause_seconds_*`: GC metrics
- `jvm_threads_*`: Thread metrics
- `system_cpu_usage`: CPU metrics
- `process_cpu_usage`: Process CPU

### Standard HTTP Metrics (Both Services)

- `http_server_requests_seconds_count`: Request count
- `http_server_requests_seconds_sum`: Total duration
- `http_server_requests_seconds_bucket`: Latency histogram

### Custom Business Metrics

See sections above for microservice-specific metrics.

## Cost Estimate

**AWS Managed Prometheus:**
- Ingestion: ~$0.30 per 10M samples
- Query: ~$0.01 per 1M samples queried
- Storage: $0.03 per GB-month
- Estimated: ~$20-30/month for 2 microservices

**AWS Managed Grafana:**
- $9 per active user per month
- Estimated: $18-27/month for 2-3 users

**ADOT Collector:**
- No additional cost (runs in ECS tasks)

**Total Estimated Cost:** ~$40-60/month

## Troubleshooting

### Metrics Not Appearing

1. Check ADOT logs: `aws logs tail /ecs/<project>-<service> --filter "adot"`
2. Verify IAM permissions on task role
3. Check `/actuator/prometheus` endpoint accessibility
4. Verify Prometheus workspace is active

### Grafana Can't Query

1. Verify Grafana IAM role has `aps:QueryMetrics`
2. Check data source configuration
3. Test Prometheus endpoint connectivity

### High Cardinality Warning

If metrics have too many unique label combinations:
1. Review custom metrics for high-cardinality labels
2. Consider using metric relabeling in ADOT config
3. Adjust scrape intervals

## Future Enhancements

Consider adding:
- **Alerting**: Configure Grafana alerts for critical metrics
- **Recording Rules**: Pre-compute expensive queries
- **Long-term Storage**: Configure AMP data retention
- **Custom Dashboards**: Service-specific views
- **SLO Tracking**: Define and monitor SLOs
- **Distributed Tracing**: Add X-Ray or AWS Distro for OpenTelemetry tracing

## References

- [AWS Managed Prometheus](https://docs.aws.amazon.com/prometheus/)
- [AWS Managed Grafana](https://docs.aws.amazon.com/grafana/)
- [ADOT Collector](https://aws-otel.github.io/docs/getting-started/collector)
- [Micrometer Prometheus](https://micrometer.io/docs/registry/prometheus)
- [Spring Boot Actuator](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html)

