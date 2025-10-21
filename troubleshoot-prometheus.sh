#!/bin/bash

# Script to troubleshoot Prometheus scraping issues in ECS
# Usage: ./troubleshoot-prometheus.sh

set -e

CLUSTER="email-processor-cluster"
REGION="us-west-1"

echo "=== Prometheus Scraping Troubleshooting ==="
echo ""

# Function to check service status
check_service() {
    local service_name=$1
    echo "Checking service: $service_name"
    echo "----------------------------------------"
    
    # Get running tasks
    TASK_ARN=$(aws ecs list-tasks \
        --cluster $CLUSTER \
        --service-name $service_name \
        --desired-status RUNNING \
        --region $REGION \
        --query 'taskArns[0]' \
        --output text)
    
    if [ "$TASK_ARN" == "None" ] || [ -z "$TASK_ARN" ]; then
        echo "❌ No running tasks found for $service_name"
        echo ""
        return 1
    fi
    
    echo "✅ Found running task: ${TASK_ARN##*/}"
    
    # Get task details
    TASK_DETAILS=$(aws ecs describe-tasks \
        --cluster $CLUSTER \
        --tasks $TASK_ARN \
        --region $REGION \
        --query 'tasks[0]' \
        --output json)
    
    # Check health status
    HEALTH_STATUS=$(echo $TASK_DETAILS | jq -r '.healthStatus // "UNKNOWN"')
    echo "Health Status: $HEALTH_STATUS"
    
    # Check container statuses
    echo ""
    echo "Container Statuses:"
    echo $TASK_DETAILS | jq -r '.containers[] | "  \(.name): \(.lastStatus) (health: \(.healthStatus // "N/A"))"'
    
    echo ""
    echo "Recent Application Logs (last 20 lines):"
    echo "----------------------------------------"
    aws logs tail "/ecs/email-processor-$service_name" \
        --since 5m \
        --filter-pattern "ecs" \
        --region $REGION \
        --format short 2>/dev/null | tail -20 || echo "No logs found"
    
    echo ""
    echo "Recent ADOT Logs (last 20 lines):"
    echo "----------------------------------------"
    aws logs tail "/ecs/email-processor-$service_name" \
        --since 5m \
        --filter-pattern "adot" \
        --region $REGION \
        --format short 2>/dev/null | tail -20 || echo "No ADOT logs found"
    
    echo ""
    echo "Checking Prometheus endpoint accessibility:"
    echo "----------------------------------------"
    
    # Get network interface
    ENI_ID=$(echo $TASK_DETAILS | jq -r '.attachments[0].details[] | select(.name=="networkInterfaceId") | .value')
    PRIVATE_IP=$(aws ec2 describe-network-interfaces \
        --network-interface-ids $ENI_ID \
        --region $REGION \
        --query 'NetworkInterfaces[0].PrivateIpAddress' \
        --output text)
    
    echo "Task Private IP: $PRIVATE_IP"
    echo "Prometheus endpoint should be: http://$PRIVATE_IP:8080/actuator/prometheus"
    echo ""
}

# Check microservice-1
check_service "microservice-1"
echo "========================================"
echo ""

# Check microservice-2
check_service "microservice-2"
echo "========================================"
echo ""

# Check Prometheus workspace
echo "Checking Prometheus Workspace:"
echo "----------------------------------------"
WORKSPACE_ID=$(aws amp list-workspaces \
    --region $REGION \
    --query "workspaces[?alias=='email-processor-prometheus'].workspaceId" \
    --output text)

if [ -z "$WORKSPACE_ID" ]; then
    echo "❌ Prometheus workspace not found"
else
    echo "✅ Workspace ID: $WORKSPACE_ID"
    
    # Try to query for metrics
    echo ""
    echo "Checking for scraped metrics (last 5 minutes):"
    ENDPOINT=$(aws amp describe-workspace \
        --workspace-id $WORKSPACE_ID \
        --region $REGION \
        --query 'workspace.prometheusEndpoint' \
        --output text)
    echo "Prometheus Endpoint: ${ENDPOINT}"
    
    # Note: Querying AMP requires proper authentication, showing command for reference
    echo ""
    echo "To query metrics manually, use:"
    echo "aws amp query-workspace --workspace-id $WORKSPACE_ID --region $REGION ..."
fi

echo ""
echo "========================================"
echo "Common Issues and Solutions:"
echo "========================================"
echo ""
echo "1. If app container is unhealthy:"
echo "   - Check application logs for startup errors"
echo "   - Verify environment variables are set correctly"
echo "   - Check if SQS_QUEUE_URL, S3_BUCKET_NAME, SSM_PARAMETER_NAME are set"
echo ""
echo "2. If ADOT shows connection errors:"
echo "   - Ensure app is listening on port 8080"
echo "   - Verify /actuator/prometheus endpoint exists"
echo "   - Check if micrometer-registry-prometheus dependency is in pom.xml"
echo ""
echo "3. If metrics not appearing in Prometheus:"
echo "   - Check ADOT logs for 'remote write' errors"
echo "   - Verify IAM role has aps:RemoteWrite permission"
echo "   - Check Prometheus workspace is active"
echo ""
echo "4. To test the actuator endpoint locally:"
echo "   - Exec into the container:"
echo "   - aws ecs execute-command --cluster $CLUSTER --task <task-id> --container microservice-1 --command '/bin/sh' --interactive"
echo "   - Then run: wget -O- http://localhost:8080/actuator/prometheus"
echo ""

