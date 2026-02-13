variable "aws_region" {
  description = "AWS region for all resources."
  type        = string
  default     = "us-east-1"
}

variable "project_name" {
  description = "Project prefix used in resource naming."
  type        = string
  default     = "cloud-order-platform"
}

variable "environment" {
  description = "Environment name (dev/staging/prod)."
  type        = string
  default     = "dev"
}

variable "vpc_cidr" {
  description = "VPC CIDR block."
  type        = string
  default     = "10.40.0.0/16"
}

variable "availability_zones" {
  description = "Availability zones used for public subnets."
  type        = list(string)
  default     = ["us-east-1a", "us-east-1b"]
}

variable "public_subnet_cidrs" {
  description = "Public subnet CIDR blocks."
  type        = list(string)
  default     = ["10.40.1.0/24", "10.40.2.0/24"]
}

variable "service_names" {
  description = "Microservice names mapped to ECR repositories."
  type        = list(string)
  default = [
    "auth-service",
    "catalog-service",
    "order-service",
    "inventory-service",
    "payment-service",
    "notification-service"
  ]
}

variable "tags" {
  description = "Additional resource tags."
  type        = map(string)
  default     = {}
}

variable "service_image_tag" {
  description = "Default image tag used when no per-service override is provided."
  type        = string
  default     = "latest"
}

variable "service_image_overrides" {
  description = "Per-service full image URI overrides (service -> image URI)."
  type        = map(string)
  default     = {}
}

variable "alb_ingress_cidr_blocks" {
  description = "CIDR blocks allowed to access the public ALB."
  type        = list(string)
  default     = ["0.0.0.0/0"]
}

variable "ecs_desired_count_by_service" {
  description = "Desired task count per service."
  type        = map(number)
  default = {
    auth-service         = 0
    catalog-service      = 0
    order-service        = 0
    inventory-service    = 0
    payment-service      = 0
    notification-service = 0
  }
}

variable "ecs_cpu_by_service" {
  description = "Fargate task CPU units per service."
  type        = map(number)
  default = {
    auth-service         = 512
    catalog-service      = 512
    order-service        = 512
    inventory-service    = 512
    payment-service      = 512
    notification-service = 512
  }
}

variable "ecs_memory_by_service" {
  description = "Fargate task memory (MiB) per service."
  type        = map(number)
  default = {
    auth-service         = 1024
    catalog-service      = 1024
    order-service        = 1024
    inventory-service    = 1024
    payment-service      = 1024
    notification-service = 1024
  }
}

variable "postgres_host" {
  description = "PostgreSQL host used by order/inventory/payment services."
  type        = string
  default     = "localhost"
}

variable "postgres_port" {
  description = "PostgreSQL port."
  type        = number
  default     = 5432
}

variable "postgres_database_names" {
  description = "Database name mapping for services with Postgres dependency."
  type        = map(string)
  default = {
    order-service     = "order_db"
    inventory-service = "inventory_db"
    payment-service   = "payment_db"
  }
}

variable "postgres_username" {
  description = "PostgreSQL username."
  type        = string
  default     = "cloud"
}

variable "postgres_password" {
  description = "PostgreSQL password."
  type        = string
  default     = "cloud"
  sensitive   = true
}

variable "rabbitmq_host" {
  description = "RabbitMQ host."
  type        = string
  default     = "localhost"
}

variable "rabbitmq_port" {
  description = "RabbitMQ port."
  type        = number
  default     = 5672
}

variable "rabbitmq_username" {
  description = "RabbitMQ username."
  type        = string
  default     = "cloud"
}

variable "rabbitmq_password" {
  description = "RabbitMQ password."
  type        = string
  default     = "cloud"
  sensitive   = true
}

variable "redis_host" {
  description = "Redis host used by inventory-service cache."
  type        = string
  default     = "localhost"
}

variable "redis_port" {
  description = "Redis port."
  type        = number
  default     = 6379
}

variable "otel_exporter_otlp_endpoint" {
  description = "OTLP HTTP endpoint used by services with tracing. Empty string keeps app defaults."
  type        = string
  default     = ""
}

variable "management_tracing_sampling_probability" {
  description = "Spring management tracing sampling probability."
  type        = string
  default     = "1.0"
}

variable "auth_expected_issuer" {
  description = "Token issuer expected by order-service and used by auth-service token issuance."
  type        = string
  default     = "auth-service"
}

variable "auth_required_order_role" {
  description = "RBAC role required by order-service for order creation."
  type        = string
  default     = "buyer"
}

variable "environment_variables_by_service" {
  description = "Additional environment variables per service (service -> map(name -> value))."
  type        = map(map(string))
  default     = {}
}

variable "cloudwatch_log_retention_days" {
  description = "CloudWatch log retention for ECS service logs."
  type        = number
  default     = 14
}

variable "secret_environment_by_service" {
  description = "Secret-backed environment variables per service (service -> map(name -> valueFrom ARN/reference))."
  type        = map(map(string))
  default     = {}
}

variable "task_execution_secret_arns" {
  description = "Secrets Manager secret ARNs that ECS execution role can read."
  type        = list(string)
  default     = []
}

variable "task_execution_ssm_parameter_arns" {
  description = "SSM parameter ARNs that ECS execution role can read."
  type        = list(string)
  default     = []
}
