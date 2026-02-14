resource "aws_service_discovery_private_dns_namespace" "main" {
  name        = "${local.name_prefix}.local"
  description = "Private service discovery namespace for ${local.name_prefix}."
  vpc         = aws_vpc.main.id

  tags = merge(local.common_tags, {
    Name = "${local.name_prefix}-sd"
  })
}

resource "aws_service_discovery_service" "microservice" {
  for_each = local.service_definitions

  name = each.key

  dns_config {
    namespace_id = aws_service_discovery_private_dns_namespace.main.id

    dns_records {
      ttl  = 10
      type = "A"
    }
  }

  health_check_custom_config {
    # ECS manages task health. For a demo setup, a simple custom check is sufficient.
    failure_threshold = 1
  }

  tags = merge(local.common_tags, {
    Name = "${local.name_prefix}-${each.key}-sd"
  })
}

