# S3 bucket for Grafana dashboards
resource "aws_s3_bucket" "dashboards" {
  bucket = "${var.project_name}-grafana-dashboards"

  tags = {
    Name    = "${var.project_name}-grafana-dashboards"
    Project = var.project_name
  }
}

resource "aws_s3_bucket_versioning" "dashboards" {
  bucket = aws_s3_bucket.dashboards.id
  versioning_configuration {
    status = "Enabled"
  }
}

# Upload dashboard files to S3
resource "aws_s3_object" "dashboards" {
  for_each = fileset("${path.module}/../../grafana-dashboards", "*.json")
  
  bucket = aws_s3_bucket.dashboards.id
  key    = each.value
  source = "${path.module}/../../grafana-dashboards/${each.value}"
  etag   = filemd5("${path.module}/../../grafana-dashboards/${each.value}")

  tags = {
    Name    = each.value
    Project = var.project_name
  }
}

# EFS for Grafana persistent storage
resource "aws_efs_file_system" "grafana" {
  creation_token = "${var.project_name}-grafana-efs"
  encrypted      = true

  lifecycle_policy {
    transition_to_ia = "AFTER_30_DAYS"
  }

  tags = {
    Name    = "${var.project_name}-grafana-efs"
    Project = var.project_name
  }
}

# EFS Mount Targets (one per AZ)
resource "aws_efs_mount_target" "grafana" {
  count           = length(var.private_subnet_ids)
  file_system_id  = aws_efs_file_system.grafana.id
  subnet_id       = var.private_subnet_ids[count.index]
  security_groups = [aws_security_group.efs.id]
}

# EFS Access Point for Grafana (sets proper UID/GID)
resource "aws_efs_access_point" "grafana" {
  file_system_id = aws_efs_file_system.grafana.id

  root_directory {
    path = "/grafana"
    creation_info {
      owner_gid   = 472  # Grafana group ID
      owner_uid   = 472  # Grafana user ID
      permissions = "755"
    }
  }

  # Removed posix_user to allow root to manage files initially
  # The container runs as root for setup, then switches to user 472 for Grafana process

  tags = {
    Name    = "${var.project_name}-grafana-access-point"
    Project = var.project_name
  }
}

# Security Group for EFS
resource "aws_security_group" "efs" {
  name        = "${var.project_name}-grafana-efs-sg"
  description = "Security group for Grafana EFS"
  vpc_id      = var.vpc_id

  ingress {
    description     = "NFS from ECS tasks"
    from_port       = 2049
    to_port         = 2049
    protocol        = "tcp"
    security_groups = [aws_security_group.grafana_task.id]
  }

  egress {
    description = "Allow all outbound"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name    = "${var.project_name}-grafana-efs-sg"
    Project = var.project_name
  }
}

# Security Group for Grafana ECS Tasks
resource "aws_security_group" "grafana_task" {
  name        = "${var.project_name}-grafana-task-sg"
  description = "Security group for Grafana ECS tasks"
  vpc_id      = var.vpc_id

  ingress {
    description     = "HTTP from ALB"
    from_port       = 3000
    to_port         = 3000
    protocol        = "tcp"
    security_groups = [aws_security_group.alb.id]
  }

  egress {
    description = "Allow all outbound"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name    = "${var.project_name}-grafana-task-sg"
    Project = var.project_name
  }
}

# Security Group for ALB
resource "aws_security_group" "alb" {
  name        = "${var.project_name}-grafana-alb-sg"
  description = "Security group for Grafana ALB"
  vpc_id      = var.vpc_id

  ingress {
    description = "HTTP from anywhere"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    description = "Allow all outbound"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name    = "${var.project_name}-grafana-alb-sg"
    Project = var.project_name
  }
}

# Application Load Balancer
resource "aws_lb" "grafana" {
  name               = "${var.project_name}-grafana-alb"
  internal           = false
  load_balancer_type = "application"
  security_groups    = [aws_security_group.alb.id]
  subnets            = var.public_subnet_ids

  tags = {
    Name    = "${var.project_name}-grafana-alb"
    Project = var.project_name
  }
}

# Target Group
resource "aws_lb_target_group" "grafana" {
  name        = "${var.project_name}-grafana-tg"
  port        = 3000
  protocol    = "HTTP"
  vpc_id      = var.vpc_id
  target_type = "ip"

  health_check {
    enabled             = true
    healthy_threshold   = 2
    interval            = 30
    matcher             = "200,302"
    path                = "/api/health"
    port                = "traffic-port"
    protocol            = "HTTP"
    timeout             = 5
    unhealthy_threshold = 3
  }

  deregistration_delay = 30

  tags = {
    Name    = "${var.project_name}-grafana-tg"
    Project = var.project_name
  }
}

# ALB Listener
resource "aws_lb_listener" "grafana" {
  load_balancer_arn = aws_lb.grafana.arn
  port              = "80"
  protocol          = "HTTP"

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.grafana.arn
  }
}

# CloudWatch Log Group
resource "aws_cloudwatch_log_group" "grafana" {
  name              = "/ecs/${var.project_name}-grafana"
  retention_in_days = 7

  tags = {
    Name    = "${var.project_name}-grafana-logs"
    Project = var.project_name
  }
}

# IAM Role for ECS Task Execution
resource "aws_iam_role" "task_execution" {
  name = "${var.project_name}-grafana-task-execution-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Principal = {
          Service = "ecs-tasks.amazonaws.com"
        }
        Action = "sts:AssumeRole"
      }
    ]
  })

  tags = {
    Name    = "${var.project_name}-grafana-task-execution-role"
    Project = var.project_name
  }
}

# Attach AWS managed policy for ECS task execution
resource "aws_iam_role_policy_attachment" "task_execution" {
  role       = aws_iam_role.task_execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

# IAM Role for ECS Task (runtime permissions)
resource "aws_iam_role" "task_role" {
  name = "${var.project_name}-grafana-task-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Principal = {
          Service = "ecs-tasks.amazonaws.com"
        }
        Action = "sts:AssumeRole"
      }
    ]
  })

  tags = {
    Name    = "${var.project_name}-grafana-task-role"
    Project = var.project_name
  }
}

# Attach Prometheus query policy to task role
resource "aws_iam_role_policy_attachment" "prometheus_query" {
  role       = aws_iam_role.task_role.name
  policy_arn = var.prometheus_query_policy_arn
}

# IAM Policy for EFS access
resource "aws_iam_policy" "efs_access" {
  name        = "${var.project_name}-grafana-efs-access"
  description = "Allow Grafana task to mount EFS volume with root access"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "elasticfilesystem:ClientMount",
          "elasticfilesystem:ClientWrite",
          "elasticfilesystem:ClientRootAccess"
        ]
        Resource = aws_efs_file_system.grafana.arn
      }
    ]
  })

  tags = {
    Name    = "${var.project_name}-grafana-efs-access"
    Project = var.project_name
  }
}

# Attach EFS access policy to task role
resource "aws_iam_role_policy_attachment" "efs_access" {
  role       = aws_iam_role.task_role.name
  policy_arn = aws_iam_policy.efs_access.arn
}

# IAM Policy for S3 dashboard access
resource "aws_iam_policy" "s3_dashboards" {
  name        = "${var.project_name}-grafana-s3-dashboards"
  description = "Allow Grafana task to read dashboards from S3"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "s3:GetObject",
          "s3:ListBucket"
        ]
        Resource = [
          aws_s3_bucket.dashboards.arn,
          "${aws_s3_bucket.dashboards.arn}/*"
        ]
      }
    ]
  })

  tags = {
    Name    = "${var.project_name}-grafana-s3-dashboards"
    Project = var.project_name
  }
}

# Attach S3 dashboard policy to task role
resource "aws_iam_role_policy_attachment" "s3_dashboards" {
  role       = aws_iam_role.task_role.name
  policy_arn = aws_iam_policy.s3_dashboards.arn
}

# SSM Parameter for Grafana admin password
resource "aws_ssm_parameter" "grafana_password" {
  name  = "/${var.project_name}/grafana/admin-password"
  type  = "SecureString"
  value = var.grafana_admin_password

  tags = {
    Name    = "${var.project_name}-grafana-password"
    Project = var.project_name
  }
}

# SSM Parameter for Grafana datasource provisioning
resource "aws_ssm_parameter" "grafana_datasource" {
  name = "/${var.project_name}/grafana/datasource-config"
  type = "String"
  value = templatefile("${path.module}/../../grafana-datasource.yaml", {
    PROMETHEUS_ENDPOINT = var.prometheus_endpoint
    AWS_REGION          = var.aws_region
  })

  tags = {
    Name    = "${var.project_name}-grafana-datasource"
    Project = var.project_name
  }
}

# IAM Policy for reading SSM parameters
resource "aws_iam_policy" "ssm_read" {
  name        = "${var.project_name}-grafana-ssm-read"
  description = "Allow Grafana task to read SSM parameters"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "ssm:GetParameter",
          "ssm:GetParameters"
        ]
        Resource = [
          aws_ssm_parameter.grafana_password.arn,
          aws_ssm_parameter.grafana_datasource.arn
        ]
      }
    ]
  })

  tags = {
    Name    = "${var.project_name}-grafana-ssm-read"
    Project = var.project_name
  }
}

# Attach SSM read policy to task execution role
resource "aws_iam_role_policy_attachment" "ssm_read" {
  role       = aws_iam_role.task_execution.name
  policy_arn = aws_iam_policy.ssm_read.arn
}

# ECS Task Definition
resource "aws_ecs_task_definition" "grafana" {
  family                   = "${var.project_name}-grafana"
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                      = var.cpu
  memory                   = var.memory
  execution_role_arn       = aws_iam_role.task_execution.arn
  task_role_arn            = aws_iam_role.task_role.arn

  volume {
    name = "grafana-storage"

    efs_volume_configuration {
      file_system_id          = aws_efs_file_system.grafana.id
      transit_encryption      = "ENABLED"
      transit_encryption_port = 2049
      authorization_config {
        access_point_id = aws_efs_access_point.grafana.id
        iam             = "ENABLED"
      }
    }
  }

  container_definitions = jsonencode([
    {
      name  = "grafana"
      image = "grafana/grafana:latest"
      user  = "0:0"  # Run as root (UID 0, GID 0) to allow setup tasks
      
      portMappings = [
        {
          containerPort = 3000
          protocol      = "tcp"
        }
      ]

      environment = [
        {
          name  = "GF_SECURITY_ADMIN_USER"
          value = "admin"
        },
        {
          name  = "GF_SERVER_ROOT_URL"
          value = "http://localhost:3000"
        },
        {
          name  = "GF_AUTH_SIGV4_AUTH_ENABLED"
          value = "true"
        },
        {
          name  = "PROMETHEUS_ENDPOINT"
          value = var.prometheus_endpoint
        },
        {
          name  = "AWS_REGION"
          value = var.aws_region
        },
        {
          name  = "DASHBOARD_BUCKET"
          value = aws_s3_bucket.dashboards.id
        },
        {
          name  = "GF_PATHS_DATA"
          value = "/var/lib/grafana"
        },
        {
          name  = "GF_PATHS_LOGS"
          value = "/var/log/grafana"
        },
        {
          name  = "GF_PATHS_PLUGINS"
          value = "/var/lib/grafana/plugins"
        },
        {
          name  = "GF_PATHS_PROVISIONING"
          value = "/etc/grafana/provisioning"
        }
      ]

      secrets = [
        {
          name      = "GF_SECURITY_ADMIN_PASSWORD"
          valueFrom = aws_ssm_parameter.grafana_password.arn
        }
      ]

      entryPoint = ["/bin/sh", "-c"]
      command = [<<-EOT
        set -e
        
        echo "Installing dependencies..."
        apk add --no-cache python3 py3-pip curl su-exec
        
        echo "Installing AWS CLI via pip..."
        pip3 install --break-system-packages awscli
        
        echo "Creating provisioning directories..."
        mkdir -p /etc/grafana/provisioning/datasources
        mkdir -p /etc/grafana/provisioning/dashboards
        mkdir -p /var/lib/grafana/dashboards
        mkdir -p /var/log/grafana
        
        echo "Creating datasource configuration..."
        cat > /etc/grafana/provisioning/datasources/prometheus.yaml <<'EOF'
apiVersion: 1

datasources:
  - name: AWS Managed Prometheus
    type: prometheus
    access: proxy
    url: $${PROMETHEUS_ENDPOINT}
    isDefault: true
    editable: false
    jsonData:
      httpMethod: POST
      sigV4Auth: true
      sigV4AuthType: default
      sigV4Region: $${AWS_REGION}
EOF
        
        echo "Creating dashboard provisioning configuration..."
        cat > /etc/grafana/provisioning/dashboards/dashboards.yaml <<'EOF'
apiVersion: 1

providers:
  - name: 'default'
    orgId: 1
    folder: ''
    type: file
    disableDeletion: false
    updateIntervalSeconds: 10
    allowUiUpdates: true
    options:
      path: /var/lib/grafana/dashboards
      foldersFromFilesStructure: false
EOF
        
        echo "Downloading dashboards from S3..."
        aws s3 sync s3://$${DASHBOARD_BUCKET}/ /var/lib/grafana/dashboards/ --exclude "*" --include "*.json" --region $${AWS_REGION}
        
        echo "Setting correct ownership on Grafana directories..."
        chown -R 472:0 /var/lib/grafana
        chown -R 472:0 /var/log/grafana
        chown -R 472:0 /etc/grafana/provisioning
        
        echo "Switching to user 472 and starting Grafana..."
        exec su-exec 472:0 /run.sh
      EOT
      ]

      mountPoints = [
        {
          sourceVolume  = "grafana-storage"
          containerPath = "/var/lib/grafana"
          readOnly      = false
        }
      ]

      logConfiguration = {
        logDriver = "awslogs"
        options = {
          "awslogs-group"         = aws_cloudwatch_log_group.grafana.name
          "awslogs-region"        = var.aws_region
          "awslogs-stream-prefix" = "grafana"
        }
      }

      healthCheck = {
        command     = ["CMD-SHELL", "wget --no-verbose --tries=1 --spider http://localhost:3000/api/health || exit 1"]
        interval    = 30
        timeout     = 5
        retries     = 3
        startPeriod = 60
      }
    }
  ])

  tags = {
    Name    = "${var.project_name}-grafana-task"
    Project = var.project_name
  }
}

# ECS Service
resource "aws_ecs_service" "grafana" {
  name            = "${var.project_name}-grafana"
  cluster         = var.cluster_id
  task_definition = aws_ecs_task_definition.grafana.arn
  desired_count   = var.desired_count
  launch_type     = "FARGATE"

  network_configuration {
    subnets          = var.private_subnet_ids
    security_groups  = [aws_security_group.grafana_task.id]
    assign_public_ip = false
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.grafana.arn
    container_name   = "grafana"
    container_port   = 3000
  }

  depends_on = [
    aws_lb_listener.grafana,
    aws_efs_mount_target.grafana
  ]

  tags = {
    Name    = "${var.project_name}-grafana-service"
    Project = var.project_name
  }
}

