locals {
  demo_dependency_definitions = {
    rabbitmq = {
      port   = 5672
      cpu    = 256
      memory = 512
      image  = "rabbitmq:3-management"
      environment = [
        { name = "RABBITMQ_DEFAULT_USER", value = var.rabbitmq_username },
        { name = "RABBITMQ_DEFAULT_PASS", value = var.rabbitmq_password }
      ]
    }
    redis = {
      port        = 6379
      cpu         = 256
      memory      = 512
      image       = "redis:7-alpine"
      environment = []
    }
    postgres-order = {
      port   = 5432
      cpu    = 256
      memory = 512
      image  = "postgres:16-alpine"
      environment = [
        { name = "POSTGRES_DB", value = lookup(var.postgres_database_names, "order-service", "order_db") },
        { name = "POSTGRES_USER", value = var.postgres_username },
        { name = "POSTGRES_PASSWORD", value = var.postgres_password }
      ]
    }
    postgres-inventory = {
      port   = 5432
      cpu    = 256
      memory = 512
      image  = "postgres:16-alpine"
      environment = [
        { name = "POSTGRES_DB", value = lookup(var.postgres_database_names, "inventory-service", "inventory_db") },
        { name = "POSTGRES_USER", value = var.postgres_username },
        { name = "POSTGRES_PASSWORD", value = var.postgres_password }
      ]
    }
    postgres-payment = {
      port   = 5432
      cpu    = 256
      memory = 512
      image  = "postgres:16-alpine"
      environment = [
        { name = "POSTGRES_DB", value = lookup(var.postgres_database_names, "payment-service", "payment_db") },
        { name = "POSTGRES_USER", value = var.postgres_username },
        { name = "POSTGRES_PASSWORD", value = var.postgres_password }
      ]
    }
  }
}

resource "aws_service_discovery_service" "demo_dependency" {
  for_each = var.enable_demo_dependencies ? local.demo_dependency_definitions : {}

  name = each.key

  dns_config {
    namespace_id = aws_service_discovery_private_dns_namespace.main.id

    dns_records {
      ttl  = 10
      type = "A"
    }
  }

  health_check_custom_config {
    failure_threshold = 1
  }

  tags = merge(local.common_tags, {
    Name = "${local.name_prefix}-${each.key}-sd"
  })
}

resource "aws_ecs_task_definition" "demo_dependency" {
  for_each = var.enable_demo_dependencies ? local.demo_dependency_definitions : {}

  family                   = "${local.name_prefix}-${each.key}"
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                      = tostring(each.value.cpu)
  memory                   = tostring(each.value.memory)
  execution_role_arn       = aws_iam_role.ecs_task_execution.arn
  task_role_arn            = aws_iam_role.ecs_task.arn

  container_definitions = jsonencode([
    {
      name      = each.key
      image     = each.value.image
      essential = true
      portMappings = [
        {
          containerPort = each.value.port
          hostPort      = each.value.port
          protocol      = "tcp"
        }
      ]
      environment = each.value.environment
      logConfiguration = {
        logDriver = "awslogs"
        options = {
          awslogs-group         = aws_cloudwatch_log_group.ecs.name
          awslogs-region        = var.aws_region
          awslogs-stream-prefix = "deps"
        }
      }
    }
  ])

  tags = merge(local.common_tags, {
    Name = "${local.name_prefix}-${each.key}-taskdef"
  })
}

resource "aws_ecs_service" "demo_dependency" {
  for_each = var.enable_demo_dependencies ? local.demo_dependency_definitions : {}

  name            = "${local.name_prefix}-${each.key}"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.demo_dependency[each.key].arn
  launch_type     = "FARGATE"
  desired_count   = 1

  deployment_minimum_healthy_percent = 0
  deployment_maximum_percent         = 100

  network_configuration {
    subnets          = local.public_subnet_ids
    security_groups  = [aws_security_group.ecs_tasks.id]
    assign_public_ip = true
  }

  service_registries {
    registry_arn = aws_service_discovery_service.demo_dependency[each.key].arn
  }

  tags = merge(local.common_tags, {
    Name = "${local.name_prefix}-${each.key}-service"
  })
}
