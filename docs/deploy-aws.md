# AWS deployment (Milestone 3 phase 1 + phase 2 + phase 3 + phase 4)

Terraform roots:

- Main infrastructure: `infra/terraform/aws`
- Remote-state bootstrap: `infra/terraform/bootstrap-state`

This now provisions:

- VPC + public subnets + internet route
- ECS cluster
- ECR repositories (all services)
- Public ALB + path-based routing
- ECS task definition + ECS service (Fargate) for each microservice
- IAM execution/task roles + CloudWatch log group
- Optional secret injection hooks (Secrets Manager / SSM Parameter Store)
- Remote Terraform state backend (S3 + DynamoDB lock table)
- CD gate in GitHub Actions (`terraform init/validate/plan`, optional `apply`)

## Prerequisites

- Terraform `>= 1.6`
- AWS CLI configured (`aws configure` or OIDC/session credentials)
- Docker

## 1) Bootstrap remote Terraform state (one-time)

```bash
cd /Users/fredz/Documents/New\ project/cloud-order-platform/infra/terraform/bootstrap-state
cp terraform.tfvars.example terraform.tfvars
terraform init
terraform plan
terraform apply
```

Collect backend outputs:

```bash
terraform output state_bucket_name
terraform output lock_table_name
terraform output backend_config_example
```

## 2) Configure backend for main infrastructure

```bash
cd /Users/fredz/Documents/New\ project/cloud-order-platform/infra/terraform/aws
cp backend.hcl.example backend.hcl
```

Update `backend.hcl` with values from bootstrap outputs, then initialize:

```bash
cd /Users/fredz/Documents/New\ project/cloud-order-platform/infra/terraform/aws
terraform init -reconfigure -backend-config=backend.hcl
```

## 3) Configure main infrastructure inputs and plan/apply

```bash
cd /Users/fredz/Documents/New\ project/cloud-order-platform/infra/terraform/aws
cp terraform.tfvars.example terraform.tfvars
```

Edit `terraform.tfvars`:

- `postgres_host`, `rabbitmq_host`, `redis_host` must point to reachable endpoints.
- Keep `ecs_desired_count_by_service` at `0` for the first apply (safe bootstrap).
- If your AWS account cannot create load balancers, set:
  - `enable_public_alb = false`
  - `public_task_ingress_cidr_blocks = ["<your-ip>/32"]` (optional; enables direct access via task public IP)
- Optional demo mode (no persistence): `enable_demo_dependencies = true` to provision RabbitMQ/Redis/Postgres inside ECS.

Run:

```bash
cd /Users/fredz/Documents/New\ project/cloud-order-platform/infra/terraform/aws
terraform plan
terraform apply
```

Useful outputs:

```bash
terraform output ecs_cluster_name
terraform output alb_dns_name
terraform output -json ecr_repository_urls
```

When `enable_public_alb=false`, `alb_dns_name` will be `null`. Use ECS task public IPs to access services.

## 4) Build and push images

Build local images:

```bash
cd /Users/fredz/Documents/New\ project/cloud-order-platform
./scripts/build-all-images.sh
```

Login to ECR:

```bash
AWS_ACCOUNT_ID=<your-account-id>
AWS_REGION=us-east-1

aws ecr get-login-password --region "${AWS_REGION}" | \
  docker login --username AWS --password-stdin \
  "${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"
```

Tag/push one example (`order-service`):

```bash
docker tag cloud-order-platform/order-service:local \
  "${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/cloud-order-platform-dev/order-service:latest"

docker push \
  "${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/cloud-order-platform-dev/order-service:latest"
```

Repeat for:

- `auth-service`
- `catalog-service`
- `order-service`
- `inventory-service`
- `payment-service`
- `notification-service`

## 5) Scale services from 0 to 1

After all images are pushed, set desired counts to `1` in `terraform.tfvars`:

```hcl
ecs_desired_count_by_service = {
  auth-service         = 1
  catalog-service      = 1
  order-service        = 1
  inventory-service    = 1
  payment-service      = 1
  notification-service = 1
}
```

Then apply:

```bash
cd /Users/fredz/Documents/New\ project/cloud-order-platform/infra/terraform/aws
terraform apply
```

## 6) Optional: Secret injection for ECS tasks

1) Create secrets/parameters:

- Example Secrets Manager secret for order DB password:
  - `arn:aws:secretsmanager:us-east-1:123456789012:secret:cloud/dev/order-db-AbCdEf`
- Example SSM secure parameter for inventory DB password:
  - `arn:aws:ssm:us-east-1:123456789012:parameter/cloud/dev/inventory-db-password`

2) Add to `terraform.tfvars`:

```hcl
secret_environment_by_service = {
  order-service = {
    ORDER_DB_PASSWORD = "arn:aws:secretsmanager:us-east-1:123456789012:secret:cloud/dev/order-db-AbCdEf:password::"
  }
  inventory-service = {
    INVENTORY_DB_PASSWORD = "arn:aws:ssm:us-east-1:123456789012:parameter/cloud/dev/inventory-db-password"
  }
}

task_execution_secret_arns = [
  "arn:aws:secretsmanager:us-east-1:123456789012:secret:cloud/dev/order-db-AbCdEf"
]

task_execution_ssm_parameter_arns = [
  "arn:aws:ssm:us-east-1:123456789012:parameter/cloud/dev/inventory-db-password"
]
```

3) Apply Terraform:

```bash
cd /Users/fredz/Documents/New\ project/cloud-order-platform/infra/terraform/aws
terraform apply
```

## 7) CD to ECS with Terraform gate

Workflow:

- `.github/workflows/cd-ecs.yml`

What it does:

- Initializes Terraform with remote backend config from repository variables
- Runs `terraform validate` + `terraform plan -detailed-exitcode` as a deploy gate
- Optionally runs `terraform apply` before image rollout
- Builds/pushes all 6 service images to ECR
- Triggers ECS rolling redeploy (`--force-new-deployment`) for each service

Required GitHub settings:

- `Secrets`:
  - `AWS_ROLE_TO_ASSUME` (OIDC assumable deployment role ARN)
- `Variables`:
  - `AWS_REGION` (example `us-east-1`)
  - `PROJECT_NAME` (default `cloud-order-platform`)
  - `DEPLOY_ENV` (default `dev`)
  - `ENABLE_PUBLIC_ALB` (`true|false`, optional; default `true`)
  - `ECS_CLUSTER_NAME` (optional override; otherwise `<project>-<env>-cluster`)
  - `TF_STATE_BUCKET` (remote state S3 bucket name)
  - `TF_STATE_LOCK_TABLE` (optional; default `<project>-<env>-tf-locks`)
  - `TF_STATE_KEY_PREFIX` (optional; when set, prepends state key path)
  - `TF_AUTO_APPLY` (`true|false`, optional; default `false`)

Manual run options:

- `deploy_env`: target environment
- `image_tag`: ECR tag
- `terraform_apply`: when `true`, applies plan changes before container deployment

Gate behavior:

- If Terraform plan has changes and apply is not enabled, workflow fails before deploy.
- Enable apply with either:
  - workflow input `terraform_apply=true`, or
  - repository variable `TF_AUTO_APPLY=true`.
