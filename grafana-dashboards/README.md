# Grafana Dashboards

This directory contains pre-built Grafana dashboard JSON files for monitoring the Email Processor microservices.

## Available Dashboards

### 1. JVM Dashboard (`jvm-dashboard.json`)

Monitors JVM health and performance metrics:

- **Memory Usage**: Heap and non-heap memory usage and limits
- **Garbage Collection**: GC pause times and frequency
- **Threads**: Live and daemon thread counts
- **CPU Usage**: System and process CPU utilization

**Best for**: Identifying memory leaks, GC pressure, and resource utilization issues.

### 2. HTTP Metrics Dashboard (`http-metrics-dashboard.json`)

Tracks HTTP request/response metrics:

- **Request Rate**: Requests per second by endpoint
- **Latency**: p50, p95, and p99 request duration percentiles
- **Status Codes**: Distribution of 2xx, 4xx, 5xx responses
- **Error Rate**: 4xx and 5xx error frequency

**Best for**: Monitoring API performance, identifying slow endpoints, tracking errors.

### 3. Business Metrics Dashboard (`business-metrics-dashboard.json`)

Custom business and operational metrics:

**Microservice-1 (API Service):**
- SQS message send rate and failures
- Validation success/failure rates (by type: token, email data)
- SQS publish duration

**Microservice-2 (Consumer Service):**
- SQS message receive and processing rates
- S3 upload success/failure rates
- Message processing duration
- S3 upload duration
- File size distribution

**Best for**: Understanding system throughput, identifying bottlenecks, tracking business KPIs.

