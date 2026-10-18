# AWS deployment

```
Browser ──HTTPS──▶ CloudFront ──/*──────▶ S3 (Angular build, private, OAC)
                       │
                       └──/api/*──▶ EC2 t4g.small (Spring Boot :8080, public subnet)
                                        │            └──▶ Gemini API
                                        └──▶ RDS PostgreSQL (private subnets)
Secrets: SSM Parameter Store · Logs/alarms: CloudWatch · Deploys: S3 + SSM Run Command
```

## Cost (approximate, us-east-2, on-demand)

| Resource | ~USD / month |
|---|---|
| EC2 t4g.small + 20 GB gp3 | 14 |
| RDS db.t4g.micro + 20 GB gp3 | 15 |
| Public IPv4 (Elastic IP) | 3.60 |
| S3, CloudFront, CloudWatch, SSM (demo traffic) | < 1 |
| **Total** | **≈ 33–35** (less with free-tier credits) |

`terraform destroy` removes everything and stops all charges. A $20/month budget alert is created.

## One-time setup

1. Install tools: `brew install awscli terraform`
2. Sign in with an IAM Identity Center (SSO) user or an IAM user with admin rights. Do **not** use the root account.
   ```bash
   aws configure sso        # or: aws configure
   aws sts get-caller-identity
   ```
3. Create your variables file:
   ```bash
   cp infra/terraform/example.tfvars infra/terraform/terraform.tfvars   # set alert_email
   ```

## Provision (≈ 15–20 min, mostly RDS and CloudFront)

```bash
terraform -chdir=infra/terraform init
terraform -chdir=infra/terraform plan
terraform -chdir=infra/terraform apply
```

Then click the confirmation link AWS emails to `alert_email`, or alarms won't reach you.

## Gemini API key (AI milestone)

Created by hand so it never touches Terraform state or git. Run this yourself:

```bash
aws ssm put-parameter --region us-east-2 --name /calendar-copilot/gemini-api-key \
  --type SecureString --value "PASTE_KEY_HERE"
```

The backend reads it at startup, so redeploy (or restart) after setting it.

## Deploy

```bash
infra/deploy-backend.sh     # build jar → S3 → restart on EC2 → wait for health
infra/deploy-frontend.sh    # ng build → S3 → CloudFront invalidation
terraform -chdir=infra/terraform output app_url
```

## Operate

```bash
# Live application logs
aws logs tail /calendar-copilot/app --region us-east-2 --follow

# Shell on the instance (no SSH keys, no open port 22)
aws ssm start-session --region us-east-2 --target "$(terraform -chdir=infra/terraform output -raw instance_id)"
```

Session Manager needs the [Session Manager plugin](https://docs.aws.amazon.com/systems-manager/latest/userguide/session-manager-working-with-install-plugin.html) (`brew install --cask session-manager-plugin`).

## Security notes

- Port 8080 accepts traffic only from CloudFront's managed prefix list, and the app rejects
  `/api` requests without CloudFront's secret `X-Origin-Verify` header.
- RDS sits in private subnets with no internet route and only accepts the API instance.
- No SSH; IMDSv2 required; EBS, RDS and S3 encrypted at rest.
- The instance role can read only `/calendar-copilot/*` parameters and the backend jar prefix.
- Terraform state (local, gitignored) contains the generated DB password and origin secret.

## Teardown

```bash
terraform -chdir=infra/terraform destroy
aws ssm delete-parameter --region us-east-2 --name /calendar-copilot/gemini-api-key
```

## Deliberately out of scope (next steps)

Private EC2 behind CloudFront VPC origins, Multi-AZ RDS, ALB + Auto Scaling, WAF rate-based
rules, custom domain with ACM, GitHub Actions deploys with OIDC, async AI jobs (SQS → Lambda).
