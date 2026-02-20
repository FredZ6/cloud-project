#!/usr/bin/env bash

set -euo pipefail

usage() {
  cat <<'EOF'
Usage:
  ./scripts/deploy-aws.sh [--yes] [--skip-init] [--plan-only]

Options:
  -y, --yes      Skip confirmation prompt before apply.
  --skip-init    Skip terraform init.
  --plan-only    Run init/validate/plan only, do not apply.
  -h, --help     Show this help.

Notes:
  - Targets infra/terraform/aws only.
  - Uses backend.hcl when present.
  - Requires terraform.tfvars in infra/terraform/aws.
EOF
}

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
AWS_TF_DIR="${PROJECT_ROOT}/infra/terraform/aws"

AUTO_APPROVE=false
SKIP_INIT=false
PLAN_ONLY=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    -y|--yes)
      AUTO_APPROVE=true
      shift
      ;;
    --skip-init)
      SKIP_INIT=true
      shift
      ;;
    --plan-only)
      PLAN_ONLY=true
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      usage
      exit 1
      ;;
  esac
done

if ! command -v terraform >/dev/null 2>&1; then
  echo "terraform is not installed or not in PATH." >&2
  exit 1
fi

if [[ ! -f "${AWS_TF_DIR}/terraform.tfvars" ]]; then
  echo "Missing ${AWS_TF_DIR}/terraform.tfvars" >&2
  echo "Create it from terraform.tfvars.example before deploy." >&2
  exit 1
fi

PLAN_FILE_NAME="deploy-$(date +%s).tfplan"

cleanup() {
  rm -f "${AWS_TF_DIR}/${PLAN_FILE_NAME}"
}
trap cleanup EXIT

if [[ "${SKIP_INIT}" == "false" ]]; then
  echo "==> terraform init (aws)"
  if [[ -f "${AWS_TF_DIR}/backend.hcl" ]]; then
    terraform -chdir="${AWS_TF_DIR}" init -reconfigure -backend-config=backend.hcl
  else
    terraform -chdir="${AWS_TF_DIR}" init
  fi
fi

echo "==> terraform validate (aws)"
terraform -chdir="${AWS_TF_DIR}" validate

echo "==> terraform plan (aws)"
terraform -chdir="${AWS_TF_DIR}" plan -out="${PLAN_FILE_NAME}"

if [[ "${PLAN_ONLY}" == "true" ]]; then
  echo "Plan-only mode: skipping apply."
  exit 0
fi

if [[ "${AUTO_APPROVE}" == "false" ]]; then
  read -r -p "Apply this plan to AWS? Type 'apply' to continue: " confirmation
  if [[ "${confirmation}" != "apply" ]]; then
    echo "Apply cancelled."
    exit 1
  fi
fi

echo "==> terraform apply (aws)"
terraform -chdir="${AWS_TF_DIR}" apply "${PLAN_FILE_NAME}"

echo "AWS deploy completed."
