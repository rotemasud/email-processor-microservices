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

## How to Import Dashboards

### Method 1: Upload JSON File

1. Log into your AWS Managed Grafana workspace
2. Click **+** (Create) in the left sidebar
3. Select **Import**
4. Click **Upload JSON file**
5. Select one of the dashboard JSON files from this directory
6. Select the **Prometheus** data source (should be auto-configured)
7. Click **Import**

### Method 2: Copy/Paste JSON

1. Open one of the dashboard JSON files in a text editor
2. Copy the entire contents
3. In Grafana, click **+** → **Import**
4. Paste the JSON into the text area
5. Click **Load**
6. Select the **Prometheus** data source
7. Click **Import**

## Dashboard Configuration

After importing, you may want to customize:

### Time Ranges

- Default refresh: 30 seconds
- Adjust via the top-right time picker
- Recommended ranges: Last 1 hour, Last 6 hours, Last 24 hours

### Variables (Optional)

Add dashboard variables for filtering by service:

1. Click dashboard settings (gear icon)
2. Go to **Variables**
3. Add new variable:
   - **Name**: `service`
   - **Type**: Query
   - **Query**: `label_values(service)`
   - **Multi-value**: Yes

Then update panel queries to use `{service=~"$service"}` filter.

### Alert Rules

Set up alerts for critical metrics:

1. Edit a panel
2. Click **Alert** tab
3. Create alert rule with conditions
4. Configure notification channel

**Example alert thresholds:**
- JVM Heap Usage > 90%
- HTTP Error Rate > 5%
- SQS Processing Failures > 0
- Response Time p95 > 2 seconds

## Recommended Dashboard Setup

### Dashboard Organization

Create folders in Grafana to organize dashboards:

- **Infrastructure**: JVM Dashboard
- **Application**: HTTP Metrics Dashboard
- **Business**: Business Metrics Dashboard

### Dashboard Links

Link related dashboards for easy navigation:

1. Dashboard settings → **Links**
2. Add links to related dashboards
3. Display as dropdown or tags

### Playlist

Create a playlist for NOC displays:

1. Playlists → **New playlist**
2. Add all three dashboards
3. Set interval (e.g., 30 seconds)
4. Start playlist for rotation

## Querying Tips

### Filtering by Service

All metrics include a `service` label:
- `microservice-1`: API service
- `microservice-2`: Consumer service

Filter queries:
```promql
# Only microservice-1
http_server_requests_seconds_count{service="microservice-1"}

# Both services
http_server_requests_seconds_count{service=~"microservice-.*"}
```

### Time Ranges in Queries

Use appropriate time ranges for rate calculations:

```promql
# 5-minute rate (good for recent trends)
rate(http_server_requests_seconds_count[5m])

# 15-minute rate (smoother, less sensitive to spikes)
rate(http_server_requests_seconds_count[15m])
```

### Percentile Calculations

For histogram metrics:

```promql
# 95th percentile
histogram_quantile(0.95, rate(http_server_requests_seconds_bucket[5m]))

# 99th percentile
histogram_quantile(0.99, rate(http_server_requests_seconds_bucket[5m]))
```

## Troubleshooting

### "No data" in panels

**Check:**
1. Time range includes when services were running
2. Data source is configured correctly
3. Services are actually sending metrics to Prometheus
4. Query syntax is correct

**Debug:**
```bash
# Verify metrics are in Prometheus
# In Grafana, go to Explore and try a simple query:
up{service=~"microservice.*"}
```

### Slow dashboard loading

**Solutions:**
1. Reduce time range
2. Increase query interval
3. Simplify queries (fewer aggregations)
4. Use recording rules in Prometheus

### Missing metrics

If some metrics don't appear:
1. Check Spring Boot application is running
2. Verify `/actuator/prometheus` endpoint is accessible
3. Check ADOT collector logs for scrape errors
4. Verify IAM permissions for AMP remote write

## Customization Ideas

### Additional Panels

Add custom panels for:
- Specific endpoint monitoring
- Custom business KPIs
- Correlation analysis between metrics
- SLA/SLO tracking

### Annotations

Add annotations for deployments:
1. Dashboard settings → **Annotations**
2. Create annotation query or manual annotations
3. Mark deployment times for correlation

### Theme Customization

Grafana supports light/dark themes:
- User preferences → **Preferences** → **UI Theme**

## Metric Reference

See [MONITORING.md](../MONITORING.md) for complete metric reference and PromQL query examples.

## Support

For issues with:
- **Dashboards**: Check query syntax and Prometheus data source
- **Missing data**: See MONITORING.md troubleshooting section
- **AWS Managed Grafana**: Check AWS Grafana documentation
- **Prometheus**: Check AWS Managed Prometheus documentation

