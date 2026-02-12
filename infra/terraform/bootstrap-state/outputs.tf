output "state_bucket_name" {
  description = "S3 bucket name for Terraform remote state."
  value       = aws_s3_bucket.terraform_state.bucket
}

output "state_bucket_arn" {
  description = "S3 bucket ARN for Terraform remote state."
  value       = aws_s3_bucket.terraform_state.arn
}

output "lock_table_name" {
  description = "DynamoDB table name for Terraform state locking."
  value       = aws_dynamodb_table.terraform_lock.name
}

output "lock_table_arn" {
  description = "DynamoDB table ARN for Terraform state locking."
  value       = aws_dynamodb_table.terraform_lock.arn
}

output "backend_config_example" {
  description = "Backend configuration snippet for infra/terraform/aws backend.hcl."
  value       = <<-EOT
bucket         = "${aws_s3_bucket.terraform_state.bucket}"
key            = "${var.state_key}"
region         = "${var.aws_region}"
dynamodb_table = "${aws_dynamodb_table.terraform_lock.name}"
encrypt        = true
EOT
}
