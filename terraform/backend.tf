# Uncomment and configure for remote state storage
# terraform {
#   backend "s3" {
#     bucket         = "your-terraform-state-bucket"
#     key            = "email-processor/terraform.tfstate"
#     region         = "us-west-1"
#     encrypt        = true
#     dynamodb_table = "terraform-state-lock"
#   }
# }
