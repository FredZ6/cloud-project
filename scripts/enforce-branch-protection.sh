#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE'
Usage:
  GITHUB_TOKEN=... ./scripts/enforce-branch-protection.sh <owner> <repo> [branch]

Example:
  GITHUB_TOKEN=ghp_xxx ./scripts/enforce-branch-protection.sh fredz cloud-order-platform main

Notes:
  - The target branch must already exist on GitHub.
  - The token needs repository administration permission.
  - Required checks are set to: quick-check, integration-tests, openapi-check.
USAGE
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

if [[ $# -lt 2 || $# -gt 3 ]]; then
  usage
  exit 1
fi

if [[ -z "${GITHUB_TOKEN:-}" ]]; then
  echo "ERROR: GITHUB_TOKEN is required." >&2
  exit 1
fi

if ! command -v curl >/dev/null 2>&1; then
  echo "ERROR: curl is required." >&2
  exit 1
fi

owner="$1"
repo="$2"
branch="${3:-main}"

api_url="https://api.github.com/repos/${owner}/${repo}/branches/${branch}/protection"
tmp_file="$(mktemp)"
trap 'rm -f "${tmp_file}"' EXIT

read -r -d '' payload <<'JSON' || true
{
  "required_status_checks": {
    "strict": true,
    "checks": [
      { "context": "quick-check" },
      { "context": "integration-tests" },
      { "context": "openapi-check" }
    ]
  },
  "enforce_admins": true,
  "required_pull_request_reviews": {
    "dismiss_stale_reviews": true,
    "require_code_owner_reviews": false,
    "required_approving_review_count": 1,
    "require_last_push_approval": false
  },
  "required_conversation_resolution": true,
  "restrictions": null,
  "allow_force_pushes": false,
  "allow_deletions": false,
  "block_creations": false,
  "required_linear_history": true,
  "lock_branch": false,
  "allow_fork_syncing": true
}
JSON

echo "Applying branch protection to ${owner}/${repo}:${branch} ..."
http_code="$(
  curl -sS -o "${tmp_file}" -w "%{http_code}" \
    -X PUT "${api_url}" \
    -H "Accept: application/vnd.github+json" \
    -H "Authorization: Bearer ${GITHUB_TOKEN}" \
    -H "X-GitHub-Api-Version: 2022-11-28" \
    -d "${payload}"
)"

if [[ "${http_code}" != "200" ]]; then
  echo "ERROR: GitHub API returned HTTP ${http_code}" >&2
  cat "${tmp_file}" >&2
  exit 1
fi

echo "Branch protection applied successfully."
echo "Required checks: quick-check, integration-tests, openapi-check"
