SHELL := /usr/bin/env bash

.PHONY: help deploy deploy-plan destroy destroy-all

help:
	@echo "Targets:"
	@echo "  make deploy       # Deploy AWS infra (auto-confirm)"
	@echo "  make deploy-plan  # Plan only, no apply"
	@echo "  make destroy      # Destroy AWS infra (auto-confirm, keeps bootstrap state)"
	@echo "  make destroy-all  # Destroy AWS infra + bootstrap state (auto-confirm)"

deploy:
	./scripts/deploy-aws.sh --yes

deploy-plan:
	./scripts/deploy-aws.sh --plan-only

destroy:
	./scripts/destroy-aws.sh --yes

destroy-all:
	./scripts/destroy-aws.sh --yes --include-bootstrap-state
