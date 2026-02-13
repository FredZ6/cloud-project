# Terraform Remote State and CD Guardrails Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add Terraform remote state on AWS S3 + DynamoDB and enforce Terraform plan/apply gates in the ECS CD workflow.

**Architecture:** Split state-bootstrap resources into an isolated Terraform root (`bootstrap-state`) and keep application infrastructure in `infra/terraform/aws` using an S3 backend. In GitHub Actions CD, run terraform init/validate/plan as a deployment gate and optionally apply infrastructure changes before container rollout.

**Tech Stack:** Terraform 1.6+, AWS provider v5, GitHub Actions, AWS OIDC role.

---

### Task 1: Add remote-state bootstrap Terraform root

**Files:**
- Create: `infra/terraform/bootstrap-state/versions.tf`
- Create: `infra/terraform/bootstrap-state/variables.tf`
- Create: `infra/terraform/bootstrap-state/main.tf`
- Create: `infra/terraform/bootstrap-state/outputs.tf`
- Create: `infra/terraform/bootstrap-state/terraform.tfvars.example`

**Step 1: Define provider/version constraints and variables**
- Add Terraform/AWS provider config and bootstrap variables.

**Step 2: Implement S3+DynamoDB resources**
- Provision encrypted/versioned S3 bucket and lock table with safe defaults.

**Step 3: Add outputs and example inputs**
- Output bucket/table names and backend snippet to drive main stack init.

### Task 2: Wire main Terraform root to S3 backend

**Files:**
- Modify: `infra/terraform/aws/versions.tf`
- Create: `infra/terraform/aws/backend.hcl.example`
- Modify: `.gitignore`

**Step 1: Add backend block**
- Configure `terraform.backend "s3" {}` for partial backend config.

**Step 2: Add backend example config**
- Provide sample `backend.hcl` with bucket/key/region/dynamodb settings.

**Step 3: Ignore local backend override file**
- Ignore `infra/terraform/aws/backend.hcl` to prevent environment leakage.

### Task 3: Add Terraform plan/apply gate into CD workflow

**Files:**
- Modify: `.github/workflows/cd-ecs.yml`

**Step 1: Extend workflow inputs/env**
- Add optional `terraform_apply` input and state backend variables.

**Step 2: Add init/validate/plan gate**
- Run terraform init with backend config, validate, and plan using detailed exit codes.

**Step 3: Enforce apply policy and deployment gate**
- If plan has changes, require explicit apply signal (workflow input or repo var).
- Run `terraform apply` when enabled; otherwise fail before container rollout.

### Task 4: Update deployment documentation and milestone tracking

**Files:**
- Modify: `docs/deploy-aws.md`
- Modify: `README.md`

**Step 1: Document bootstrap-state flow**
- Add commands for creating remote state backend resources.

**Step 2: Document backend init and CD guardrail configuration**
- Explain required GitHub variables/secrets for backend state and apply gating.

**Step 3: Mark milestone phase completion**
- Update milestone list with phase 4.

### Task 5: Verify changes before completion

**Files:**
- Verify only

**Step 1: Validate build and OpenAPI checks**
- Run `mvn -B -ntp -Dmaven.test.skip=true verify`.
- Run `OPENAPI_RUNTIME_CHECK=0 ./scripts/verify-openapi.sh`.

**Step 2: Validate Terraform configuration**
- Run `terraform -chdir=infra/terraform/aws init -backend=false`.
- Run `terraform -chdir=infra/terraform/aws validate`.
- Run `terraform -chdir=infra/terraform/aws plan -input=false`.

**Step 3: Report evidence**
- Include command results and any limitations (credentials/network/backend values).
