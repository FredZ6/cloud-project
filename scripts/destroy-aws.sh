#!/usr/bin/env bash

set -euo pipefail

usage() {
  cat <<'EOF'
Usage:
  ./scripts/destroy-aws.sh [--yes] [--skip-init] [--include-bootstrap-state]

Options:
  -y, --yes                   Skip confirmation prompts before destroy.
  --skip-init                 Skip terraform init.
  --include-bootstrap-state   Also destroy infra/terraform/bootstrap-state.
  -h, --help                  Show this help.

Notes:
  - Default only destroys infra/terraform/aws.
  - Keeps remote state bootstrap resources unless --include-bootstrap-state is set.
EOF
}

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
AWS_TF_DIR="${PROJECT_ROOT}/infra/terraform/aws"
BOOTSTRAP_TF_DIR="${PROJECT_ROOT}/infra/terraform/bootstrap-state"

AUTO_APPROVE=false
SKIP_INIT=false
INCLUDE_BOOTSTRAP_STATE=false

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
    --include-bootstrap-state)
      INCLUDE_BOOTSTRAP_STATE=true
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
  echo "Create it from terraform.tfvars.example before destroy." >&2
  exit 1
fi

confirm_or_exit() {
  local prompt="$1"
  local expected="$2"
  local answer
  read -r -p "${prompt}" answer
  if [[ "${answer}" != "${expected}" ]]; then
    echo "Cancelled."
    exit 1
  fi
}

run_destroy_for_root() {
  local root_dir="$1"
  local root_label="$2"
  local use_backend_hcl="$3"
  local plan_file_name="destroy-${root_label}-$(date +%s).tfplan"

  if [[ "${SKIP_INIT}" == "false" ]]; then
    echo "==> terraform init (${root_label})"
    if [[ "${use_backend_hcl}" == "true" && -f "${root_dir}/backend.hcl" ]]; then
      terraform -chdir="${root_dir}" init -reconfigure -backend-config=backend.hcl
    else
      terraform -chdir="${root_dir}" init
    fi
  fi

  echo "==> terraform plan -destroy (${root_label})"
  terraform -chdir="${root_dir}" plan -destroy -out="${plan_file_name}"

  if [[ "${AUTO_APPROVE}" == "false" ]]; then
    confirm_or_exit "Destroy ${root_label}? Type 'destroy' to continue: " "destroy"
  fi

  echo "==> terraform apply (${root_label} destroy plan)"
  terraform -chdir="${root_dir}" apply "${plan_file_name}"
  rm -f "${root_dir}/${plan_file_name}"
}

run_destroy_for_root "${AWS_TF_DIR}" "aws" "true"

if [[ "${INCLUDE_BOOTSTRAP_STATE}" == "true" ]]; then
  if [[ ! -f "${BOOTSTRAP_TF_DIR}/terraform.tfvars" ]]; then
    echo "Missing ${BOOTSTRAP_TF_DIR}/terraform.tfvars; skipping bootstrap-state destroy." >&2
    exit 1
  fi
  run_destroy_for_root "${BOOTSTRAP_TF_DIR}" "bootstrap-state" "false"
fi

echo "AWS destroy completed."
