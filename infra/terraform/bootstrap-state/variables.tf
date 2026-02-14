variable "aws_region" {
  description = "AWS region for bootstrap state resources."
  type        = string
  default     = "us-east-1"
}

variable "project_name" {
  description = "Project prefix used in naming."
  type        = string
  default     = "cloud-order-platform"
}

variable "environment" {
  description = "Environment name (dev/staging/prod)."
  type        = string
  default     = "dev"
}

variable "state_bucket_name" {
  description = "Optional explicit S3 bucket name for Terraform remote state."
  type        = string
  default     = ""
}

variable "lock_table_name" {
  description = "Optional explicit DynamoDB table name for Terraform state locking."
  type        = string
  default     = ""
}

variable "state_key" {
  description = "Suggested default key path for the main stack state object."
  type        = string
  default     = "cloud-order-platform/dev/aws.tfstate"
}

variable "force_destroy_state_bucket" {
  description = "Allow destroying the state bucket even when non-empty (not recommended outside disposable environments)."
  type        = bool
  default     = false
}

variable "tags" {
  description = "Additional tags for bootstrap resources."
  type        = map(string)
  default     = {}
}

variable "github_repository" {
  description = "GitHub repository (OWNER/REPO) allowed to assume the deploy role via OIDC."
  type        = string
  default     = "FredZ6/cloud-project"
}

variable "github_ref" {
  description = "GitHub ref allowed to assume the deploy role (example: refs/heads/main)."
  type        = string
  default     = "refs/heads/main"
}

variable "github_actions_role_name" {
  description = "Optional IAM role name assumed by GitHub Actions. Empty string uses <project_name>-github-actions-deploy."
  type        = string
  default     = ""
}

variable "github_actions_policy_arn" {
  description = "IAM policy ARN attached to the GitHub Actions deploy role."
  type        = string
  default     = "arn:aws:iam::aws:policy/AdministratorAccess"
}
