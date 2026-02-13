data "aws_caller_identity" "current" {}

locals {
  name_prefix = "${var.project_name}-${var.environment}"
  common_tags = merge(
    {
      Project     = var.project_name
      Environment = var.environment
      ManagedBy   = "terraform"
      Stack       = "bootstrap-state"
    },
    var.tags
  )

  resolved_state_bucket_name = var.state_bucket_name != "" ? var.state_bucket_name : "${local.name_prefix}-tf-state-${data.aws_caller_identity.current.account_id}"
  resolved_lock_table_name   = var.lock_table_name != "" ? var.lock_table_name : "${local.name_prefix}-tf-locks"
}

resource "aws_s3_bucket" "terraform_state" {
  bucket        = local.resolved_state_bucket_name
  force_destroy = var.force_destroy_state_bucket

  tags = merge(local.common_tags, {
    Name = local.resolved_state_bucket_name
  })
}

resource "aws_s3_bucket_versioning" "terraform_state" {
  bucket = aws_s3_bucket.terraform_state.id

  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "terraform_state" {
  bucket = aws_s3_bucket.terraform_state.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_public_access_block" "terraform_state" {
  bucket = aws_s3_bucket.terraform_state.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_dynamodb_table" "terraform_lock" {
  name         = local.resolved_lock_table_name
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "LockID"

  attribute {
    name = "LockID"
    type = "S"
  }

  tags = merge(local.common_tags, {
    Name = local.resolved_lock_table_name
  })
}
