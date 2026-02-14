# AWS ECS Deploy Without ALB + Demo Dependencies (Design)

**Date:** 2026-02-14

## Background

Our current Terraform root (`infra/terraform/aws`) provisions a public **Application Load Balancer (ALB)** and routes traffic to ECS/Fargate services.

In AWS account **149536499313**, creating load balancers fails with:

- `OperationNotPermitted: This AWS account currently does not support creating load balancers`

This blocks `terraform apply` at `aws_lb.public`.

## Goals

- Keep the default architecture (ALB + path routing) for accounts that support ALB.
- Add a safe toggle to **disable ALB** so Terraform can still provision ECS services.
- Enable a resume-ready demo in this restricted account:
  - Run `auth-service + order-service + inventory-service` on ECS (`desired_count=1`).
  - Reach them from a developer laptop via **task public IP** (no ALB), restricted by CIDR allow-list.
- Make services work end-to-end by provisioning **demo dependencies** in ECS:
  - RabbitMQ
  - Redis
  - Postgres (one instance per service DB)

## Non-goals

- Production-grade networking (private subnets, NAT gateways, ALB/NLB alternatives).
- Data persistence for demo dependencies.
- TLS/custom domains.

## Design

### 1) Terraform toggles

Add variables (safe defaults):

- `enable_public_alb` (bool, default `true`)
  - When `false`, skip ALB security group, ALB, listener, listener rules, and target groups.
- `public_task_ingress_cidr_blocks` (list(string), default `[]`)
  - When `enable_public_alb=false` and this list is non-empty, allow inbound to public service ports
    from these CIDRs (use `x.x.x.x/32` for your laptop).
- `enable_demo_dependencies` (bool, default `false`)
  - When `true`, create ECS services for demo RabbitMQ/Redis/Postgres and wire microservices to them.

### 2) Service discovery (Cloud Map)

Create a private DNS namespace in the VPC, for example:

- `${project_name}-${environment}.local`

Register every ECS service in the namespace (microservices + demo dependencies).

This enables internal DNS names like:

- `auth-service.<namespace>`
- `rabbitmq.<namespace>`
- `postgres-order.<namespace>`

### 3) Removing ALB hard dependencies

Current config hard-references `aws_lb.public.dns_name` for:

- Terraform output `alb_dns_name`
- `AUTH_JWKS_URI` passed into order-service

Change behavior:

- When ALB is enabled:
  - `AUTH_JWKS_URI = http://<alb-dns>/.well-known/jwks.json` (current behavior)
- When ALB is disabled:
  - `AUTH_JWKS_URI = http://auth-service.<namespace>:8084/.well-known/jwks.json`

Outputs must become conditional:

- `alb_dns_name = null` when ALB is disabled
- `public_service_target_groups = {}` when ALB is disabled (or omitted)

### 4) Networking + security groups

Without ALB, the current ECS tasks security group has no ingress and becomes unreachable.

We will:

- Allow **intra-SG** traffic (self-referencing ingress) so services and demo dependencies can connect.
- Add conditional **CIDR ingress** for “public services” when ALB is disabled, controlled by
  `public_task_ingress_cidr_blocks`.

Security posture:

- Default: no public ingress (`public_task_ingress_cidr_blocks = []`).
- For demo: set to your own IP only.

### 5) Demo dependencies in ECS

When `enable_demo_dependencies=true`, create ECS services (Fargate) for:

- `rabbitmq` (port 5672)
- `redis` (port 6379)
- `postgres-order` (port 5432, `POSTGRES_DB=order_db`)
- `postgres-inventory` (port 5432, `POSTGRES_DB=inventory_db`)
- `postgres-payment` (port 5432, `POSTGRES_DB=payment_db`)

All dependencies:

- Use Cloud Map registration for DNS-based discovery.
- Share the same ECS tasks security group; reachable only via self-ingress.
- Use minimal CPU/memory defaults for cost control.

Microservice wiring (when demo deps enabled):

- `RABBITMQ_HOST = rabbitmq.<namespace>`
- `REDIS_HOST = redis.<namespace>`
- Per-service Postgres hosts:
  - order-service: `postgres-order.<namespace>`
  - inventory-service: `postgres-inventory.<namespace>`
  - payment-service: `postgres-payment.<namespace>`

### 6) Repo hygiene: stop tracking Terraform plan artifacts

`infra/terraform/**/tfplan` is currently tracked in git, but it is a generated binary file that may embed
state/values and should not be committed.

We will:

- Remove tracked `tfplan` files from git
- Add `infra/terraform/**/tfplan` to `.gitignore`

## Rollout / How To Run (Restricted Account)

1. Apply “base infra” first (no workloads):
   - `enable_public_alb=false`
   - `enable_demo_dependencies=true`
   - `ecs_desired_count_by_service` stays `0`
2. Build and push images to ECR (at minimum: auth/order/inventory).
3. Enable public ingress for your laptop IP and scale:
   - `public_task_ingress_cidr_blocks = ["<your-ip>/32"]`
   - set desired counts for `auth-service/order-service/inventory-service` to `1`
4. Get task public IPs via AWS CLI and verify:
   - Auth token: `POST http://<auth-task-ip>:8084/api/auth/token`
   - Order API: `http://<order-task-ip>:8081/api/orders...`
   - Inventory dashboard: `http://<inventory-task-ip>:8082/release-dashboard.html`

## Verification

- `terraform validate` succeeds
- `terraform plan` shows no ALB resources when disabled
- `terraform apply` succeeds in this account
- ECS services are stable for the three demo services

