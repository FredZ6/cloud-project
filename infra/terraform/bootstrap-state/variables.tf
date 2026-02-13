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
