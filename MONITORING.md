# Email Processor Monitoring Guide

This guide explains how to access and use the AWS Managed Prometheus and Grafana monitoring for the Email Processor microservices.

## Overview

The monitoring stack includes:
- **AWS Managed Prometheus (AMP)**: Stores metrics from both microservices
- **Grafana on ECS**: Self-hosted Grafana service for visualizing metrics with pre-built dashboards
- **AWS Distro for OpenTelemetry (ADOT) Collector**: Scrapes Prometheus metrics from Spring Boot apps

## Architecture

```
┌─────────────────┐         ┌─────────────────┐
│ Microservice-1  │         │ Microservice-2  │
│   (Spring Boot) │         │   (Spring Boot) │
│                 │         │                 │
│ /actuator/      │         │ /actuator/      │
│   prometheus    │         │   prometheus    │
└────────┬────────┘         └────────┬────────┘
         │                           │
         │ scrape                    │ scrape
         │ (localhost:8080)          │ (localhost:8080)
         │                           │
    ┌────▼────────┐            ┌────▼────────┐
    │ ADOT        │            │ ADOT        │
    │ Collector   │            │ Collector   │
    │ (sidecar)   │            │ (sidecar)   │
    └────┬────────┘            └────┬────────┘
         │                           │
         │ remote_write              │ remote_write
         │                           │
         └────────┬──────────────────┘
                  │
                  ▼
         ┌────────────────┐
         │ AWS Managed    │
         │ Prometheus     │
         │ (AMP)          │
         └────────┬───────┘
                  │
                  │ query
                  │
                  ▼
         ┌────────────────┐
         │   Grafana on   │
         │   ECS Fargate  │
         └────────────────┘
```

## Accessing Grafana

After deploying with Terraform, get the Grafana URL:

```bash
cd terraform
terraform output grafana_url
```

### First-Time Setup

1. Navigate to the Grafana URL (ALB DNS name)
2. Log in with the default credentials:
   - **Username**: `admin`
   - **Password**: Retrieve from SSM Parameter Store:
     ```bash
     aws ssm get-parameter --name /email-processor/grafana/admin-password --with-decryption --query 'Parameter.Value' --output text
     ```
3. You'll be redirected to the Grafana home page

**Note**: For detailed Grafana setup, configuration, and datasource setup, see [GRAFANA-SETUP.md](GRAFANA-SETUP.md)

## Importing Dashboards

Pre-built dashboard JSON files are available in the `grafana-dashboards/` directory:

1. **JVM Dashboard** (`jvm-dashboard.json`): JVM memory, GC, threads, CPU
2. **HTTP Metrics Dashboard** (`http-metrics-dashboard.json`): Request rates, latencies, status codes
3. **Business Metrics Dashboard** (`business-metrics-dashboard.json`): SQS, S3, validation metrics

### Import Steps

1. Log into Grafana workspace
2. Click **+** (Create) → **Import**
3. Upload the JSON file or paste the JSON content
4. Select the Prometheus data source (should be auto-configured)
5. Click **Import**

## Available Metrics

### Microservice-1 (API Service)

#### SQS Metrics
- `sqs_messages_sent_total`: Total messages sent to SQS
- `sqs_messages_sent_failure_total`: Failed SQS send attempts
- `sqs_publish_duration_seconds`: Time to publish to SQS (histogram)

#### Validation Metrics
- `validation_success_total`: Successful validations
- `validation_failure_total{type="token"}`: Token validation failures
- `validation_failure_total{type="emaildata"}`: Email data validation failures

#### HTTP Metrics
- `http_server_requests_seconds_count`: Request count
- `http_server_requests_seconds_sum`: Total request duration
- `http_server_requests_seconds_bucket`: Request duration histogram (for percentiles)

### Microservice-2 (Consumer Service)

#### SQS Metrics
- `sqs_messages_received_total`: Messages received from SQS
- `sqs_messages_processed_total{status="success"}`: Successfully processed messages
- `sqs_messages_processed_total{status="failure"}`: Failed message processing
- `sqs_message_processing_duration_seconds`: Message processing time (histogram)

#### S3 Metrics
- `s3_uploads_total{status="success"}`: Successful S3 uploads
- `s3_uploads_total{status="failure"}`: Failed S3 uploads
- `s3_upload_duration_seconds`: S3 upload time (histogram)
- `s3_upload_file_size_bytes`: File size distribution (summary)

### Standard JVM Metrics

Both microservices expose standard Spring Boot Actuator + Micrometer metrics:

- `jvm_memory_used_bytes{area="heap|nonheap"}`: JVM memory usage
- `jvm_memory_max_bytes`: Maximum JVM memory
- `jvm_gc_pause_seconds_*`: Garbage collection metrics
- `jvm_threads_live_threads`: Number of live threads
- `jvm_threads_daemon_threads`: Number of daemon threads
- `system_cpu_usage`: System CPU usage
- `process_cpu_usage`: Process CPU usage

## Querying Metrics with PromQL

### Example Queries

#### Request Rate (requests per second)
```promql
rate(http_server_requests_seconds_count[5m])
```

#### 95th Percentile Request Latency
```promql
histogram_quantile(0.95, rate(http_server_requests_seconds_bucket[5m]))
```

#### SQS Message Success Rate
```promql
rate(sqs_messages_processed_total{status="success"}[5m]) /
rate(sqs_messages_received_total[5m])
```

#### Average S3 Upload File Size
```promql
s3_upload_file_size_bytes_sum / s3_upload_file_size_bytes_count
```

## Accessing Metrics Endpoint Directly

Each microservice exposes metrics at:
```
http://<service-url>:8080/actuator/prometheus
```

For microservice-1 (with ALB):
```bash
# Get ALB DNS
terraform output alb_dns_name

# Access metrics (note: requires valid API token in header for most endpoints)
curl http://<alb-dns>/actuator/prometheus
```

For microservice-2 (no public access):
```bash
# Access via ECS Exec or CloudWatch Logs
aws ecs execute-command --cluster <cluster-name> \
  --task <task-id> \
  --container microservice-2 \
  --command "curl localhost:8080/actuator/prometheus" \
  --interactive
```

## Alerting (Optional Enhancement)

Grafana supports alerting. To set up alerts:

1. In Grafana, go to **Alerting** → **Alert rules**
2. Create a new alert rule
3. Define the query (e.g., high error rate)
4. Set thresholds and conditions
5. Configure notification channels (SNS, email, Slack, etc.)

### Example Alert Rules

**High Error Rate**
```promql
rate(http_server_requests_seconds_count{status=~"5.."}[5m]) > 0.1
```

**High Memory Usage**
```promql
jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"} > 0.9
```

**SQS Processing Failures**
```promql
rate(sqs_messages_processed_total{status="failure"}[5m]) > 0
```

## Troubleshooting

### Metrics Not Appearing in Prometheus

1. **Check ADOT Collector Logs**:
   ```bash
   aws logs tail /ecs/<project-name>-<service-name> --follow --filter-pattern "adot"
   ```

2. **Verify IAM Permissions**:
   - Task role has `aps:RemoteWrite` permission
   - Check CloudWatch logs for authentication errors

3. **Check Metrics Endpoint**:
   ```bash
   # From within the task
   curl localhost:8080/actuator/prometheus
   ```

### Grafana Can't Query Prometheus

1. **Verify Data Source Configuration**:
   - Go to Configuration → Data Sources
   - Test the Prometheus connection
   - Ensure the workspace URL is correct

2. **Check IAM Permissions**:
   - Grafana ECS task role has `aps:QueryMetrics` permission
   - SigV4 authentication is enabled in the datasource

### Dashboard Shows No Data

1. **Check Time Range**: Ensure the dashboard time range matches when your services have been running
2. **Verify Label Selectors**: Check that queries use correct `service` labels (microservice-1, microservice-2)
3. **Inspect Query**: Use Grafana's Query Inspector to see raw PromQL queries

## Cost Optimization

- **Prometheus**: Charged for ingested samples and query volume (~$0.03 per million samples)
- **Grafana on ECS**: Fargate compute costs (~$20-30/month for single instance)
- **EFS for Grafana**: Storage costs (~$0.30/GB/month, typically <1GB)
- **ADOT Collector**: No additional cost (runs as ECS sidecar)

To reduce costs:
- Adjust scrape intervals (default: 30s)
- Reduce retention period if not using long-term analysis
- Use smaller Fargate task size for Grafana if sufficient
- Consider running Grafana only during business hours for dev environments

## Additional Resources

- [AWS Managed Prometheus Documentation](https://docs.aws.amazon.com/prometheus/)
- [Grafana Documentation](https://grafana.com/docs/)
- [Grafana with AWS SigV4](https://grafana.com/docs/grafana/latest/datasources/prometheus/#sigv4-authentication)
- [GRAFANA-SETUP.md](GRAFANA-SETUP.md) - Detailed Grafana setup guide
- [Spring Boot Actuator Metrics](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html#actuator.metrics)
- [Micrometer Prometheus Registry](https://micrometer.io/docs/registry/prometheus)
- [ADOT Collector Configuration](https://aws-otel.github.io/docs/getting-started/collector)

