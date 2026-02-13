output "vpc_id" {
  description = "Created VPC ID."
  value       = aws_vpc.main.id
}

output "public_subnet_ids" {
  description = "Public subnet IDs used by ECS tasks/ALB in follow-up phases."
  value       = [for subnet in aws_subnet.public : subnet.id]
}

output "ecs_cluster_name" {
  description = "ECS cluster name."
  value       = aws_ecs_cluster.main.name
}

output "ecs_cluster_arn" {
  description = "ECS cluster ARN."
  value       = aws_ecs_cluster.main.arn
}

output "ecr_repository_urls" {
  description = "ECR repository URLs per service."
  value = {
    for service, repo in aws_ecr_repository.service :
    service => repo.repository_url
  }
}

output "alb_dns_name" {
  description = "Public ALB DNS name."
  value       = aws_lb.public.dns_name
}

output "public_service_target_groups" {
  description = "ALB target group ARNs for public services."
  value = {
    for service, tg in aws_lb_target_group.service :
    service => tg.arn
  }
}

output "ecs_service_names" {
  description = "ECS service names by microservice."
  value = {
    for service, ecs_service in aws_ecs_service.service :
    service => ecs_service.name
  }
}

output "ecs_task_execution_role_arn" {
  description = "IAM role ARN used by ECS task execution."
  value       = aws_iam_role.ecs_task_execution.arn
}

output "ecs_task_role_arn" {
  description = "IAM task role ARN used by service containers."
  value       = aws_iam_role.ecs_task.arn
}

output "ecs_execution_secret_policy_name" {
  description = "Execution-role inline policy for runtime secret reads (if configured)."
  value       = local.has_secret_access_requirements ? aws_iam_role_policy.ecs_task_execution_secrets[0].name : null
}
