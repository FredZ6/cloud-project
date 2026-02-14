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

output "github_actions_oidc_provider_arn" {
  description = "IAM OIDC provider ARN for GitHub Actions."
  value       = aws_iam_openid_connect_provider.github_actions.arn
}

output "github_actions_role_arn" {
  description = "IAM role ARN assumed by GitHub Actions (set GitHub secret AWS_ROLE_TO_ASSUME to this value)."
  value       = aws_iam_role.github_actions_deploy.arn
}
