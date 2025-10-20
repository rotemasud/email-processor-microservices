# ECR Repository
resource "aws_ecr_repository" "main" {
  name                 = "${var.project_name}-${var.service_name}"
  image_tag_mutability = "MUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }

  tags = {
    Name = "${var.project_name}-${var.service_name}"
  }
}

# CloudWatch Log Group
resource "aws_cloudwatch_log_group" "main" {
  name              = "/ecs/${var.project_name}-${var.service_name}"
  retention_in_days = var.log_retention_days

  tags = {
    Name = "${var.project_name}-${var.service_name}-logs"
  }
}

# Security Group for ALB
resource "aws_security_group" "alb" {
  count = var.enable_load_balancer ? 1 : 0

  name_prefix = "${substr(var.service_name, 0, 6)}-alb-"
  vpc_id      = var.vpc_id

  ingress {
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "${var.project_name}-${var.service_name}-alb-sg"
  }

  lifecycle {
    create_before_destroy = true
  }
}

# Security Group for ECS Tasks
resource "aws_security_group" "ecs_tasks" {
  name_prefix = "${substr(var.service_name, 0, 6)}-ecs-"
  vpc_id      = var.vpc_id

  dynamic "ingress" {
    for_each = var.enable_load_balancer ? [1] : []
    content {
      from_port       = var.container_port
      to_port         = var.container_port
      protocol        = "tcp"
      security_groups = [aws_security_group.alb[0].id]
    }
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "${var.project_name}-${var.service_name}-ecs-tasks-sg"
  }

  lifecycle {
    create_before_destroy = true
  }
}

# Application Load Balancer
resource "aws_lb" "main" {
  count = var.enable_load_balancer ? 1 : 0

  name_prefix        = substr("${var.service_name}-", 0, 6)
  internal           = false
  load_balancer_type = "application"
  security_groups    = [aws_security_group.alb[0].id]
  subnets            = var.public_subnet_ids

  enable_deletion_protection = false

  tags = {
    Name = "${var.project_name}-${var.service_name}-alb"
  }
}

resource "aws_lb_target_group" "main" {
  count = var.enable_load_balancer ? 1 : 0

  name_prefix = substr("${var.service_name}-", 0, 6)
  port        = var.container_port
  protocol    = "HTTP"
  vpc_id      = var.vpc_id
  target_type = "ip"

  health_check {
    enabled             = true
    healthy_threshold   = var.health_check_healthy_threshold
    interval            = var.health_check_interval
    matcher             = var.health_check_matcher
    path                = var.health_check_path
    port                = "traffic-port"
    protocol            = "HTTP"
    timeout             = var.health_check_timeout
    unhealthy_threshold = var.health_check_unhealthy_threshold
  }

  lifecycle {
    create_before_destroy = true
  }

  tags = {
    Name = "${var.project_name}-${var.service_name}-tg"
  }
}

resource "aws_lb_listener" "main" {
  count = var.enable_load_balancer ? 1 : 0

  load_balancer_arn = aws_lb.main[0].arn
  port              = "80"
  protocol          = "HTTP"

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.main[0].arn
  }
}

# IAM Role for ECS Task Execution
resource "aws_iam_role" "ecs_task_execution_role" {
  name = "${var.project_name}-${var.service_name}-exec-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "ecs-tasks.amazonaws.com"
        }
      }
    ]
  })

  tags = {
    Name = "${var.project_name}-${var.service_name}-exec-role"
  }
}

resource "aws_iam_role_policy_attachment" "ecs_task_execution_role_policy" {
  role       = aws_iam_role.ecs_task_execution_role.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

# IAM Role for ECS Task
resource "aws_iam_role" "ecs_task_role" {
  name = "${var.project_name}-${var.service_name}-task-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "ecs-tasks.amazonaws.com"
        }
      }
    ]
  })

  tags = {
    Name = "${var.project_name}-${var.service_name}-task-role"
  }
}

# IAM Policy for ECS Task
resource "aws_iam_policy" "ecs_task_policy" {
  name        = "${var.project_name}-${var.service_name}-task-policy"
  description = "Policy for ECS tasks to access AWS services"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = concat(
      length(var.sqs_queue_arns) > 0 ? [
        {
          Effect = "Allow"
          Action = [
            "sqs:SendMessage",
            "sqs:ReceiveMessage",
            "sqs:DeleteMessage",
            "sqs:GetQueueAttributes"
          ]
          Resource = var.sqs_queue_arns
        }
      ] : [],
      var.s3_bucket_arn != "" ? [
        {
          Effect = "Allow"
          Action = [
            "s3:PutObject",
            "s3:GetObject",
            "s3:DeleteObject"
          ]
          Resource = "${var.s3_bucket_arn}/*"
        }
      ] : [],
      length(var.ssm_parameter_arns) > 0 ? [
        {
          Effect = "Allow"
          Action = [
            "ssm:GetParameter",
            "ssm:GetParameters"
          ]
          Resource = var.ssm_parameter_arns
        }
      ] : []
    )
  })
}

resource "aws_iam_role_policy_attachment" "ecs_task_role_policy" {
  role       = aws_iam_role.ecs_task_role.name
  policy_arn = aws_iam_policy.ecs_task_policy.arn
}

# ECS Task Definition
resource "aws_ecs_task_definition" "main" {
  family                   = "${var.project_name}-${var.service_name}"
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                      = var.cpu
  memory                   = var.memory
  execution_role_arn       = aws_iam_role.ecs_task_execution_role.arn
  task_role_arn            = aws_iam_role.ecs_task_role.arn

  container_definitions = jsonencode([
    {
      name  = var.service_name
      image = var.container_image != "" ? var.container_image : "${aws_ecr_repository.main.repository_url}:latest"

      portMappings = var.enable_load_balancer ? [
        {
          containerPort = var.container_port
          protocol      = "tcp"
        }
      ] : []

      environment = var.environment_variables

      logConfiguration = {
        logDriver = "awslogs"
        options = {
          awslogs-group         = aws_cloudwatch_log_group.main.name
          awslogs-region        = var.aws_region
          awslogs-stream-prefix = "ecs"
        }
      }

      essential = true
    }
  ])

  tags = {
    Name = "${var.project_name}-${var.service_name}-task"
  }
}

# ECS Service
resource "aws_ecs_service" "main" {
  name            = "${var.project_name}-${var.service_name}"
  cluster         = var.cluster_id
  task_definition = aws_ecs_task_definition.main.arn
  desired_count   = var.desired_count
  launch_type     = "FARGATE"

  network_configuration {
    security_groups  = [aws_security_group.ecs_tasks.id]
    subnets          = var.private_subnet_ids
    assign_public_ip = false
  }

  dynamic "load_balancer" {
    for_each = var.enable_load_balancer ? [1] : []
    content {
      target_group_arn = aws_lb_target_group.main[0].arn
      container_name   = var.service_name
      container_port   = var.container_port
    }
  }

  depends_on = [
    aws_iam_role_policy_attachment.ecs_task_execution_role_policy,
    aws_iam_role_policy_attachment.ecs_task_role_policy
  ]

  tags = {
    Name = "${var.project_name}-${var.service_name}-service"
  }
}

# Auto Scaling Target
resource "aws_appautoscaling_target" "main" {
  max_capacity       = var.max_capacity
  min_capacity       = var.min_capacity
  resource_id        = "service/${var.cluster_name}/${aws_ecs_service.main.name}"
  scalable_dimension = "ecs:service:DesiredCount"
  service_namespace  = "ecs"
}

# Auto Scaling Policy
resource "aws_appautoscaling_policy" "main" {
  name               = "${var.project_name}-${var.service_name}-scaling"
  policy_type        = "TargetTrackingScaling"
  resource_id        = aws_appautoscaling_target.main.resource_id
  scalable_dimension = aws_appautoscaling_target.main.scalable_dimension
  service_namespace  = aws_appautoscaling_target.main.service_namespace

  target_tracking_scaling_policy_configuration {
    predefined_metric_specification {
      predefined_metric_type = var.autoscaling_metric_type
    }
    target_value = var.autoscaling_target_value
  }
}

