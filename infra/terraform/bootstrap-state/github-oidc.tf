locals {
  github_oidc_url         = "https://token.actions.githubusercontent.com"
  github_actions_subject  = "repo:${var.github_repository}:ref:${var.github_ref}"
  github_actions_role_name = var.github_actions_role_name != "" ? var.github_actions_role_name : "${var.project_name}-github-actions-deploy"
}

data "tls_certificate" "github_actions" {
  url = local.github_oidc_url
}

resource "aws_iam_openid_connect_provider" "github_actions" {
  url             = local.github_oidc_url
  client_id_list  = ["sts.amazonaws.com"]
  thumbprint_list = [data.tls_certificate.github_actions.certificates[0].sha1_fingerprint]

  tags = merge(local.common_tags, {
    Name = "${var.project_name}-github-actions-oidc"
  })
}

resource "aws_iam_role" "github_actions_deploy" {
  name = local.github_actions_role_name

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Principal = {
          Federated = aws_iam_openid_connect_provider.github_actions.arn
        }
        Action = "sts:AssumeRoleWithWebIdentity"
        Condition = {
          StringEquals = {
            "token.actions.githubusercontent.com:aud" = "sts.amazonaws.com"
            "token.actions.githubusercontent.com:sub" = local.github_actions_subject
          }
        }
      }
    ]
  })

  tags = merge(local.common_tags, {
    Name = local.github_actions_role_name
  })
}

resource "aws_iam_role_policy_attachment" "github_actions_deploy_policy" {
  role       = aws_iam_role.github_actions_deploy.name
  policy_arn = var.github_actions_policy_arn
}

