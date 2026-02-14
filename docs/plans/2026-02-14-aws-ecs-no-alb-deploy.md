# AWS ECS Deploy Without ALB (Restricted Account) Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Make `infra/terraform/aws` deployable in an AWS account that cannot create Load Balancers, and still run `auth-service + order-service + inventory-service` on ECS (reachable via task public IP with CIDR allow-list), including demo dependencies (RabbitMQ/Redis/Postgres) in ECS.

**Architecture:** Add a Terraform toggle to disable ALB resources, introduce Cloud Map service discovery for internal DNS, provision demo dependencies as ECS services, and tighten security group ingress with an explicit CIDR allow-list.

**Tech Stack:** Terraform (AWS provider), ECS/Fargate, Cloud Map, Security Groups, CloudWatch Logs, GitHub Actions

---

### Task 1: Stop Tracking Terraform Plan Artifacts

**Files:**
- Modify: `.gitignore`
- Delete: `infra/terraform/aws/tfplan`
- Delete: `infra/terraform/bootstrap-state/tfplan`

**Step 1: Update `.gitignore`**

Add:

```gitignore
infra/terraform/**/tfplan
```

**Step 2: Remove tracked files**

Run:

```bash
git rm infra/terraform/aws/tfplan infra/terraform/bootstrap-state/tfplan
```

**Step 3: Commit**

```bash
git add .gitignore
git commit -m "chore(terraform): stop tracking tfplan artifacts"
```

---

### Task 2: Add “No ALB” + “Demo Dependencies” Variables

**Files:**
- Modify: `infra/terraform/aws/variables.tf`
- Modify: `infra/terraform/aws/terraform.tfvars.example`

**Step 1: Add variables**

Append to `infra/terraform/aws/variables.tf`:

```hcl
variable "enable_public_alb" {
  description = "When true, provision the public ALB and attach public services to it."
  type        = bool
  default     = true
}

variable "public_task_ingress_cidr_blocks" {
  description = "When enable_public_alb=false, allow inbound access to public service ports from these CIDRs (use <your-ip>/32). Empty list keeps services private."
  type        = list(string)
  default     = []
}

variable "enable_demo_dependencies" {
  description = "When true, provision demo RabbitMQ/Redis/Postgres services in ECS and wire microservices to them (demo-only, no persistence)."
  type        = bool
  default     = false
}
```

**Step 2: Document in `terraform.tfvars.example`**

Add a commented section:

```hcl
# enable_public_alb = false
# public_task_ingress_cidr_blocks = ["1.2.3.4/32"]
# enable_demo_dependencies = true
```

**Step 3: Commit**

```bash
git add infra/terraform/aws/variables.tf infra/terraform/aws/terraform.tfvars.example
git commit -m "feat(terraform): add no-alb and demo-deps toggles"
```

---

### Task 3: Add Cloud Map Namespace + Service Registrations

**Files:**
- Create: `infra/terraform/aws/service-discovery.tf`
- Modify: `infra/terraform/aws/ecs-phase2.tf`

**Step 1: Create namespace**

Create `infra/terraform/aws/service-discovery.tf`:

```hcl
resource "aws_service_discovery_private_dns_namespace" "main" {
  name        = "${local.name_prefix}.local"
  description = "Private service discovery for ${local.name_prefix}"
  vpc         = aws_vpc.main.id

  tags = merge(local.common_tags, {
    Name = "${local.name_prefix}-sd"
  })
}
```

**Step 2: Create Cloud Map services for microservices**

Add:

```hcl
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
    failure_threshold = 1
  }

  tags = merge(local.common_tags, {
    Name = "${local.name_prefix}-${each.key}-sd"
  })
}
```

**Step 3: Register ECS services**

In `aws_ecs_service.service` (in `infra/terraform/aws/ecs-phase2.tf`), add:

```hcl
service_registries {
  registry_arn   = aws_service_discovery_service.microservice[each.key].arn
  container_name = each.key
  container_port = each.value.port
}
```

**Step 4: Commit**

```bash
git add infra/terraform/aws/service-discovery.tf infra/terraform/aws/ecs-phase2.tf
git commit -m "feat(terraform): add cloud map service discovery for ecs services"
```

---

### Task 4: Gate ALB Resources Behind `enable_public_alb`

**Files:**
- Modify: `infra/terraform/aws/ecs-phase2.tf`
- Modify: `infra/terraform/aws/outputs.tf`

**Step 1: Make ALB SG, ALB, listener, rules conditional**

Convert ALB resources to `count`/conditional `for_each`:

- `aws_security_group.alb` -> `count = var.enable_public_alb ? 1 : 0`
- `aws_lb.public` -> `count = var.enable_public_alb ? 1 : 0`
- `aws_lb_target_group.service` -> `for_each = var.enable_public_alb ? local.public_service_definitions : {}`
- `aws_lb_listener.http` -> `count = var.enable_public_alb ? 1 : 0`
- `aws_lb_listener_rule.service` -> `for_each = var.enable_public_alb ? local.public_service_definitions : {}`
- `aws_security_group_rule.ecs_from_alb` -> `for_each = var.enable_public_alb ? local.public_service_definitions : {}`

Update references accordingly:

- `aws_security_group.alb[0].id`
- `aws_lb.public[0].arn`
- `aws_lb_listener.http[0].arn`

**Step 2: Make ECS service LB attachment conditional**

Update dynamic block:

```hcl
dynamic "load_balancer" {
  for_each = (var.enable_public_alb && each.value.expose_public) ? [1] : []

  content {
    target_group_arn = aws_lb_target_group.service[each.key].arn
    container_name   = each.key
    container_port   = each.value.port
  }
}
```

Update `depends_on`:

```hcl
depends_on = var.enable_public_alb ? [aws_lb_listener_rule.service] : []
```

**Step 3: Fix JWKS URI without ALB**

Add a local for service discovery DNS and use it when ALB is disabled:

```hcl
locals {
  service_discovery_namespace = aws_service_discovery_private_dns_namespace.main.name
  auth_internal_base_url      = "http://auth-service.${local.service_discovery_namespace}:8084"

  auth_jwks_uri = var.enable_public_alb
    ? "http://${aws_lb.public[0].dns_name}/.well-known/jwks.json"
    : "${local.auth_internal_base_url}/.well-known/jwks.json"
}
```

**Step 4: Make outputs conditional**

In `infra/terraform/aws/outputs.tf`:

```hcl
output "alb_dns_name" {
  description = "Public ALB DNS name (null when ALB disabled)."
  value       = var.enable_public_alb ? aws_lb.public[0].dns_name : null
}

output "public_service_target_groups" {
  description = "ALB target group ARNs for public services (empty when ALB disabled)."
  value       = var.enable_public_alb ? { for service, tg in aws_lb_target_group.service : service => tg.arn } : {}
}
```

**Step 5: Commit**

```bash
git add infra/terraform/aws/ecs-phase2.tf infra/terraform/aws/outputs.tf
git commit -m "feat(terraform): allow ecs deploy with public alb disabled"
```

---

### Task 5: Add “No ALB” Ingress Rules (CIDR Allow-list) + ECS Self-Ingress

**Files:**
- Modify: `infra/terraform/aws/ecs-phase2.tf`

**Step 1: Allow intra-SG traffic**

Add:

```hcl
resource "aws_security_group_rule" "ecs_self_tcp" {
  type                     = "ingress"
  security_group_id        = aws_security_group.ecs_tasks.id
  from_port                = 0
  to_port                  = 65535
  protocol                 = "tcp"
  source_security_group_id = aws_security_group.ecs_tasks.id
  description              = "Allow ECS tasks to talk to each other (demo dependencies + internal calls)."
}
```

**Step 2: Allow laptop CIDR to hit public services when ALB disabled**

Add:

```hcl
resource "aws_security_group_rule" "ecs_public_ingress" {
  for_each = (!var.enable_public_alb && length(var.public_task_ingress_cidr_blocks) > 0)
    ? local.public_service_definitions
    : {}

  type              = "ingress"
  security_group_id = aws_security_group.ecs_tasks.id
  from_port         = each.value.port
  to_port           = each.value.port
  protocol          = "tcp"
  cidr_blocks       = var.public_task_ingress_cidr_blocks
  description       = "Direct access to ${each.key} when ALB is disabled."
}
```

**Step 3: Commit**

```bash
git add infra/terraform/aws/ecs-phase2.tf
git commit -m "feat(terraform): add ecs self-ingress + optional cidr ingress for no-alb"
```

---

### Task 6: Provision Demo Dependencies (RabbitMQ/Redis/Postgres) in ECS

**Files:**
- Create: `infra/terraform/aws/demo-dependencies.tf`
- Modify: `infra/terraform/aws/ecs-phase2.tf`

**Step 1: Add Cloud Map services for dependencies**

In `infra/terraform/aws/demo-dependencies.tf`, define locals:

```hcl
locals {
  demo_enabled = var.enable_demo_dependencies

  demo_namespace = aws_service_discovery_private_dns_namespace.main.name
  demo_rabbitmq  = "rabbitmq.${local.demo_namespace}"
  demo_redis     = "redis.${local.demo_namespace}"

  demo_postgres_hosts = {
    order-service     = "postgres-order.${local.demo_namespace}"
    inventory-service = "postgres-inventory.${local.demo_namespace}"
    payment-service   = "postgres-payment.${local.demo_namespace}"
  }
}
```

Create `aws_service_discovery_service` resources for:
- `rabbitmq`
- `redis`
- `postgres-order`
- `postgres-inventory`
- `postgres-payment`

**Step 2: Create ECS task defs + services**

Create `aws_ecs_task_definition` and `aws_ecs_service` resources for each dependency.

Key properties:
- `desired_count = local.demo_enabled ? 1 : 0`
- `assign_public_ip = true` (no NAT; required to pull images)
- security group: `aws_security_group.ecs_tasks.id`
- register in Cloud Map via `service_registries { ... }`

Use images:
- `rabbitmq:3-management`
- `redis:7-alpine`
- `postgres:16-alpine`

Postgres env example:

```hcl
environment = [
  { name = "POSTGRES_DB", value = "order_db" },
  { name = "POSTGRES_USER", value = var.postgres_username },
  { name = "POSTGRES_PASSWORD", value = var.postgres_password }
]
```

**Step 3: Wire microservices to demo deps**

In `infra/terraform/aws/ecs-phase2.tf` locals, replace direct `var.*_host` usage with effective locals:

- RabbitMQ host:
  - `local.rabbitmq_host = var.enable_demo_dependencies ? local.demo_rabbitmq : var.rabbitmq_host`
- Redis host:
  - `local.redis_host = var.enable_demo_dependencies ? local.demo_redis : var.redis_host`
- Postgres host per service:
  - `lookup(local.demo_postgres_hosts, "<service>", var.postgres_host)`

Then update service environment blocks to use those effective values.

**Step 4: Commit**

```bash
git add infra/terraform/aws/demo-dependencies.tf infra/terraform/aws/ecs-phase2.tf
git commit -m "feat(terraform): add demo rabbitmq/redis/postgres as ecs services"
```

---

### Task 7: Update CD Workflow To Pass `enable_public_alb`

**Files:**
- Modify: `.github/workflows/cd-ecs.yml`
- Modify: `docs/deploy-aws.md`

**Step 1: Add GitHub variable hook**

In `.github/workflows/cd-ecs.yml`, add env:

```yaml
  ENABLE_PUBLIC_ALB: ${{ vars.ENABLE_PUBLIC_ALB || 'true' }}
```

And pass it to terraform plan/apply:

```bash
-var="enable_public_alb=${ENABLE_PUBLIC_ALB}"
```

**Step 2: Document workaround**

In `docs/deploy-aws.md`, add a note explaining:
- For accounts that cannot create load balancers, set `enable_public_alb=false`
- Provide `public_task_ingress_cidr_blocks` for direct access

**Step 3: Commit**

```bash
git add .github/workflows/cd-ecs.yml docs/deploy-aws.md
git commit -m "docs(ci): support enable_public_alb toggle in cd terraform gate"
```

---

### Task 8: Local Terraform Verification (Restricted Account)

**Files:**
- Modify (ignored, local only): `infra/terraform/aws/terraform.tfvars`

**Step 1: Validate**

Run:

```bash
terraform -chdir=infra/terraform/aws validate
```

Expected: success.

**Step 2: Plan with ALB disabled**

In `infra/terraform/aws/terraform.tfvars` set:

```hcl
enable_public_alb = false
enable_demo_dependencies = true
public_task_ingress_cidr_blocks = []
```

Run:

```bash
terraform -chdir=infra/terraform/aws plan -no-color
```

Expected:
- No `aws_lb.*` resources planned
- Demo deps resources planned when enabled

**Step 3: Apply**

Run:

```bash
terraform -chdir=infra/terraform/aws apply -auto-approve
```

Expected: success (no ALB creation attempted).

