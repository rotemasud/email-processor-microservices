# SQS Queue
resource "aws_sqs_queue" "main" {
  name                       = "${var.project_name}-${var.queue_name}"
  visibility_timeout_seconds = var.visibility_timeout_seconds
  message_retention_seconds  = var.message_retention_seconds
  receive_wait_time_seconds  = var.receive_wait_time_seconds

  tags = {
    Name = "${var.project_name}-${var.queue_name}"
  }
}

# SQS Dead Letter Queue
resource "aws_sqs_queue" "dlq" {
  name                      = "${var.project_name}-${var.queue_name}-dlq"
  message_retention_seconds = var.message_retention_seconds

  tags = {
    Name = "${var.project_name}-${var.queue_name}-dlq"
  }
}

# SQS Redrive Policy
resource "aws_sqs_queue_redrive_policy" "main" {
  queue_url = aws_sqs_queue.main.id

  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.dlq.arn
    maxReceiveCount     = var.max_receive_count
  })
}

