locals {
  service_definitions = {
    auth-service = {
      port                  = 8084
      expose_public         = true
      route_patterns        = ["/api/auth/*", "/.well-known/*"]
      listener_priority     = 100
      health_check_path     = "/actuator/health/readiness"
      default_cpu           = 512
      default_memory        = 1024
      default_desired_count = 1
    }
    order-service = {
      port                  = 8081
      expose_public         = true
      route_patterns        = ["/api/orders*"]
      listener_priority     = 200
      health_check_path     = "/actuator/health/readiness"
      default_cpu           = 512
      default_memory        = 1024
      default_desired_count = 1
    }
    inventory-service = {
      port                  = 8082
      expose_public         = true
      route_patterns        = ["/api/stocks*", "/dashboard*", "/release-dashboard.html", "/assets/*"]
      listener_priority     = 300
      health_check_path     = "/actuator/health/readiness"
      default_cpu           = 512
      default_memory        = 1024
      default_desired_count = 1
    }
    catalog-service = {
      port                  = 8085
      expose_public         = true
      route_patterns        = ["/api/catalog/*"]
      listener_priority     = 400
      health_check_path     = "/actuator/health/readiness"
      default_cpu           = 512
      default_memory        = 1024
      default_desired_count = 1
    }
    notification-service = {
      port                  = 8086
      expose_public         = true
      route_patterns        = ["/api/notifications/*"]
      listener_priority     = 500
      health_check_path     = "/actuator/health/readiness"
      default_cpu           = 512
      default_memory        = 1024
      default_desired_count = 1
    }
    payment-service = {
      port                  = 8083
      expose_public         = false
      route_patterns        = []
      listener_priority     = 0
      health_check_path     = "/actuator/health/readiness"
      default_cpu           = 512
      default_memory        = 1024
      default_desired_count = 1
    }
  }

  public_subnet_ids = [for subnet in aws_subnet.public : subnet.id]

  public_service_definitions = {
    for service_name, definition in local.service_definitions :
    service_name => definition if definition.expose_public
  }

  service_image_urls = {
    for service_name, definition in local.service_definitions :
    service_name => lookup(
      var.service_image_overrides,
      service_name,
      "${aws_ecr_repository.service[service_name].repository_url}:${var.service_image_tag}"
    )
  }

  service_discovery_namespace = aws_service_discovery_private_dns_namespace.main.name
  auth_internal_base_url      = "http://auth-service.${local.service_discovery_namespace}:8084"

  auth_jwks_uri = var.enable_public_alb
    ? "http://${aws_lb.public[0].dns_name}/.well-known/jwks.json"
    : "${local.auth_internal_base_url}/.well-known/jwks.json"

  base_environment_by_service = {
    auth-service = merge(
      {
        AUTH_TOKEN_ISSUER                        = var.auth_expected_issuer
        MANAGEMENT_TRACING_SAMPLING_PROBABILITY = var.management_tracing_sampling_probability
      },
      var.otel_exporter_otlp_endpoint == "" ? {} : {
        OTEL_EXPORTER_OTLP_ENDPOINT = var.otel_exporter_otlp_endpoint
      }
    )

    order-service = merge(
      {
        ORDER_DB_URL                             = format("jdbc:postgresql://%s:%d/%s", var.postgres_host, var.postgres_port, lookup(var.postgres_database_names, "order-service", "order_db"))
        ORDER_DB_USER                            = var.postgres_username
        ORDER_DB_PASSWORD                        = var.postgres_password
        RABBITMQ_HOST                            = var.rabbitmq_host
        RABBITMQ_PORT                            = tostring(var.rabbitmq_port)
        RABBITMQ_USER                            = var.rabbitmq_username
        RABBITMQ_PASSWORD                        = var.rabbitmq_password
        MANAGEMENT_TRACING_SAMPLING_PROBABILITY = var.management_tracing_sampling_probability
        AUTH_EXPECTED_ISSUER                     = var.auth_expected_issuer
        AUTH_REQUIRED_ORDER_ROLE                 = var.auth_required_order_role
        AUTH_JWKS_URI                            = local.auth_jwks_uri
      },
      var.otel_exporter_otlp_endpoint == "" ? {} : {
        OTEL_EXPORTER_OTLP_ENDPOINT = var.otel_exporter_otlp_endpoint
      }
    )

    inventory-service = merge(
      {
        INVENTORY_DB_URL                         = format("jdbc:postgresql://%s:%d/%s", var.postgres_host, var.postgres_port, lookup(var.postgres_database_names, "inventory-service", "inventory_db"))
        INVENTORY_DB_USER                        = var.postgres_username
        INVENTORY_DB_PASSWORD                    = var.postgres_password
        RABBITMQ_HOST                            = var.rabbitmq_host
        RABBITMQ_PORT                            = tostring(var.rabbitmq_port)
        RABBITMQ_USER                            = var.rabbitmq_username
        RABBITMQ_PASSWORD                        = var.rabbitmq_password
        REDIS_HOST                               = var.redis_host
        REDIS_PORT                               = tostring(var.redis_port)
        MANAGEMENT_TRACING_SAMPLING_PROBABILITY = var.management_tracing_sampling_probability
      },
      var.otel_exporter_otlp_endpoint == "" ? {} : {
        OTEL_EXPORTER_OTLP_ENDPOINT = var.otel_exporter_otlp_endpoint
      }
    )

    payment-service = merge(
      {
        PAYMENT_DB_URL                           = format("jdbc:postgresql://%s:%d/%s", var.postgres_host, var.postgres_port, lookup(var.postgres_database_names, "payment-service", "payment_db"))
        PAYMENT_DB_USER                          = var.postgres_username
        PAYMENT_DB_PASSWORD                      = var.postgres_password
        RABBITMQ_HOST                            = var.rabbitmq_host
        RABBITMQ_PORT                            = tostring(var.rabbitmq_port)
        RABBITMQ_USER                            = var.rabbitmq_username
        RABBITMQ_PASSWORD                        = var.rabbitmq_password
        MANAGEMENT_TRACING_SAMPLING_PROBABILITY = var.management_tracing_sampling_probability
      },
      var.otel_exporter_otlp_endpoint == "" ? {} : {
        OTEL_EXPORTER_OTLP_ENDPOINT = var.otel_exporter_otlp_endpoint
      }
    )

    notification-service = merge(
      {
        RABBITMQ_HOST                            = var.rabbitmq_host
        RABBITMQ_PORT                            = tostring(var.rabbitmq_port)
        RABBITMQ_USER                            = var.rabbitmq_username
        RABBITMQ_PASSWORD                        = var.rabbitmq_password
        MANAGEMENT_TRACING_SAMPLING_PROBABILITY = var.management_tracing_sampling_probability
      },
      var.otel_exporter_otlp_endpoint == "" ? {} : {
        OTEL_EXPORTER_OTLP_ENDPOINT = var.otel_exporter_otlp_endpoint
      }
    )

    catalog-service = merge(
      {
        MANAGEMENT_TRACING_SAMPLING_PROBABILITY = var.management_tracing_sampling_probability
      },
      var.otel_exporter_otlp_endpoint == "" ? {} : {
        OTEL_EXPORTER_OTLP_ENDPOINT = var.otel_exporter_otlp_endpoint
      }
    )
  }

  service_secret_environment_by_service = {
    for service_name, definition in local.service_definitions :
    service_name => lookup(var.secret_environment_by_service, service_name, {})
  }

  service_environment_by_service = {
    for service_name, definition in local.service_definitions :
    service_name => {
      for env_name, env_value in merge(
        lookup(local.base_environment_by_service, service_name, {}),
        lookup(var.environment_variables_by_service, service_name, {})
      ) :
      env_name => env_value
      if !contains(keys(local.service_secret_environment_by_service[service_name]), env_name)
    }
  }

  has_secret_access_requirements = length(var.task_execution_secret_arns) > 0 || length(var.task_execution_ssm_parameter_arns) > 0
}

resource "aws_security_group" "alb" {
  count = var.enable_public_alb ? 1 : 0

  name        = "${local.name_prefix}-alb-sg"
  description = "Ingress security group for public ALB."
  vpc_id      = aws_vpc.main.id

  ingress {
    description = "HTTP ingress"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = var.alb_ingress_cidr_blocks
  }

  egress {
    description = "Allow all outbound traffic."
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = merge(local.common_tags, {
    Name = "${local.name_prefix}-alb-sg"
  })
}

resource "aws_security_group" "ecs_tasks" {
  name        = "${local.name_prefix}-ecs-tasks-sg"
  description = "Security group for ECS tasks."
  vpc_id      = aws_vpc.main.id

  # Ingress rules are handled by separate aws_security_group_rule resources for clarity and conditional logic.

  egress {
    description = "Allow all outbound traffic."
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = merge(local.common_tags, {
    Name = "${local.name_prefix}-ecs-tasks-sg"
  })
}

resource "aws_security_group_rule" "ecs_self_tcp" {
  type                     = "ingress"
  security_group_id        = aws_security_group.ecs_tasks.id
  from_port                = 0
  to_port                  = 65535
  protocol                 = "tcp"
  source_security_group_id = aws_security_group.ecs_tasks.id
  description              = "Allow ECS tasks to talk to each other (demo dependencies + internal calls)."
}

resource "aws_security_group_rule" "ecs_public_ingress" {
  for_each = (!var.enable_public_alb && length(var.public_task_ingress_cidr_blocks) > 0) ? local.public_service_definitions : {}

  type              = "ingress"
  security_group_id = aws_security_group.ecs_tasks.id
  from_port         = each.value.port
  to_port           = each.value.port
  protocol          = "tcp"
  cidr_blocks       = var.public_task_ingress_cidr_blocks
  description       = "Direct access to ${each.key} when ALB is disabled."
}

resource "aws_security_group_rule" "ecs_from_alb" {
  for_each = var.enable_public_alb ? local.public_service_definitions : {}

  type                     = "ingress"
  security_group_id        = aws_security_group.ecs_tasks.id
  from_port                = each.value.port
  to_port                  = each.value.port
  protocol                 = "tcp"
  source_security_group_id = aws_security_group.alb[0].id
  description              = "Allow ALB to access ${each.key}."
}

resource "aws_lb" "public" {
  count = var.enable_public_alb ? 1 : 0

  name               = trimsuffix(substr("${local.name_prefix}-alb", 0, 32), "-")
  internal           = false
  load_balancer_type = "application"
  security_groups    = [aws_security_group.alb[0].id]
  subnets            = local.public_subnet_ids

  tags = merge(local.common_tags, {
    Name = "${local.name_prefix}-alb"
  })
}

resource "aws_lb_target_group" "service" {
  for_each = var.enable_public_alb ? local.public_service_definitions : {}

  name        = trimsuffix(substr("${var.environment}-${replace(each.key, "service", "svc")}", 0, 32), "-")
  port        = each.value.port
  protocol    = "HTTP"
  target_type = "ip"
  vpc_id      = aws_vpc.main.id

  health_check {
    enabled             = true
    path                = each.value.health_check_path
    protocol            = "HTTP"
    matcher             = "200-399"
    interval            = 30
    timeout             = 5
    healthy_threshold   = 2
    unhealthy_threshold = 3
  }

  tags = merge(local.common_tags, {
    Name = "${local.name_prefix}-${each.key}-tg"
  })
}

resource "aws_lb_listener" "http" {
  count = var.enable_public_alb ? 1 : 0

  load_balancer_arn = aws_lb.public[0].arn
  port              = 80
  protocol          = "HTTP"

  default_action {
    type = "fixed-response"

    fixed_response {
      content_type = "text/plain"
      message_body = "Not Found"
      status_code  = "404"
    }
  }
}

resource "aws_lb_listener_rule" "service" {
  for_each = var.enable_public_alb ? local.public_service_definitions : {}

  listener_arn = aws_lb_listener.http[0].arn
  priority     = each.value.listener_priority

  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.service[each.key].arn
  }

  condition {
    path_pattern {
      values = each.value.route_patterns
    }
  }
}

data "aws_iam_policy_document" "ecs_task_assume_role" {
  statement {
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["ecs-tasks.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "ecs_task_execution" {
  name               = "${local.name_prefix}-ecs-exec-role"
  assume_role_policy = data.aws_iam_policy_document.ecs_task_assume_role.json

  tags = merge(local.common_tags, {
    Name = "${local.name_prefix}-ecs-exec-role"
  })
}

resource "aws_iam_role_policy_attachment" "ecs_task_execution" {
  role       = aws_iam_role.ecs_task_execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

data "aws_iam_policy_document" "ecs_task_execution_secrets" {
  count = local.has_secret_access_requirements ? 1 : 0

  dynamic "statement" {
    for_each = length(var.task_execution_secret_arns) > 0 ? [1] : []

    content {
      sid = "ReadSecretsManagerValues"
      actions = [
        "secretsmanager:DescribeSecret",
        "secretsmanager:GetSecretValue"
      ]
      resources = var.task_execution_secret_arns
    }
  }

  dynamic "statement" {
    for_each = length(var.task_execution_ssm_parameter_arns) > 0 ? [1] : []

    content {
      sid = "ReadSsmSecureParameters"
      actions = [
        "ssm:GetParameter",
        "ssm:GetParameters",
        "ssm:GetParametersByPath"
      ]
      resources = var.task_execution_ssm_parameter_arns
    }
  }
}

resource "aws_iam_role_policy" "ecs_task_execution_secrets" {
  count = local.has_secret_access_requirements ? 1 : 0

  name   = "${local.name_prefix}-ecs-exec-secrets"
  role   = aws_iam_role.ecs_task_execution.id
  policy = data.aws_iam_policy_document.ecs_task_execution_secrets[0].json
}

resource "aws_iam_role" "ecs_task" {
  name               = "${local.name_prefix}-ecs-task-role"
  assume_role_policy = data.aws_iam_policy_document.ecs_task_assume_role.json

  tags = merge(local.common_tags, {
    Name = "${local.name_prefix}-ecs-task-role"
  })
}

resource "aws_cloudwatch_log_group" "ecs" {
  name              = "/ecs/${local.name_prefix}"
  retention_in_days = var.cloudwatch_log_retention_days

  tags = merge(local.common_tags, {
    Name = "${local.name_prefix}-ecs-logs"
  })
}

resource "aws_ecs_task_definition" "service" {
  for_each = local.service_definitions

  family                   = "${local.name_prefix}-${each.key}"
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                      = tostring(lookup(var.ecs_cpu_by_service, each.key, each.value.default_cpu))
  memory                   = tostring(lookup(var.ecs_memory_by_service, each.key, each.value.default_memory))
  execution_role_arn       = aws_iam_role.ecs_task_execution.arn
  task_role_arn            = aws_iam_role.ecs_task.arn

  container_definitions = jsonencode([
    {
      name      = each.key
      image     = local.service_image_urls[each.key]
      essential = true
      portMappings = [
        {
          containerPort = each.value.port
          hostPort      = each.value.port
          protocol      = "tcp"
        }
      ]
      environment = [
        for env_name, env_value in local.service_environment_by_service[each.key] : {
          name  = env_name
          value = tostring(env_value)
        }
      ]
      secrets = [
        for secret_name, secret_value_from in local.service_secret_environment_by_service[each.key] : {
          name      = secret_name
          valueFrom = secret_value_from
        }
      ]
      logConfiguration = {
        logDriver = "awslogs"
        options = {
          awslogs-group         = aws_cloudwatch_log_group.ecs.name
          awslogs-region        = var.aws_region
          awslogs-stream-prefix = replace(each.key, "-service", "")
        }
      }
    }
  ])

  tags = merge(local.common_tags, {
    Name = "${local.name_prefix}-${each.key}-taskdef"
  })
}

resource "aws_ecs_service" "service" {
  for_each = local.service_definitions

  name            = "${local.name_prefix}-${each.key}"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.service[each.key].arn
  launch_type     = "FARGATE"
  desired_count   = lookup(var.ecs_desired_count_by_service, each.key, each.value.default_desired_count)

  deployment_minimum_healthy_percent = 50
  deployment_maximum_percent         = 200

  network_configuration {
    subnets          = local.public_subnet_ids
    security_groups  = [aws_security_group.ecs_tasks.id]
    assign_public_ip = true
  }

  service_registries {
    registry_arn   = aws_service_discovery_service.microservice[each.key].arn
    container_name = each.key
    container_port = each.value.port
  }

  dynamic "load_balancer" {
    for_each = (var.enable_public_alb && each.value.expose_public) ? [1] : []
    iterator = lb

    content {
      target_group_arn = aws_lb_target_group.service[each.key].arn
      container_name   = each.key
      container_port   = each.value.port
    }
  }

  depends_on = var.enable_public_alb ? [aws_lb_listener_rule.service] : []

  tags = merge(local.common_tags, {
    Name = "${local.name_prefix}-${each.key}-service"
  })
}
