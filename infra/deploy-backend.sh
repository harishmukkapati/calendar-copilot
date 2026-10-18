#!/usr/bin/env bash
# Builds the Spring Boot jar, uploads it to S3, and restarts the service on EC2 via
# SSM Run Command (no SSH). Then waits for /api/health through CloudFront.
#
# Usage: infra/deploy-backend.sh            (runs tests; needs Docker)
#        SKIP_TESTS=1 infra/deploy-backend.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TF="terraform -chdir=$ROOT/infra/terraform"
REGION="$($TF output -raw region)"
BUCKET="$($TF output -raw artifacts_bucket)"
INSTANCE="$($TF output -raw instance_id)"
APP_URL="$($TF output -raw app_url)"
VERSION="$(git -C "$ROOT" rev-parse --short HEAD)"

echo "==> Building backend ($VERSION)"
cd "$ROOT/backend"
if [[ "${SKIP_TESTS:-}" == "1" ]]; then
  ./mvnw -q package -DskipTests
else
  ./mvnw -q package
fi
JAR="$(ls target/*.jar | grep -v -- '-plain' | head -1)"

echo "==> Uploading $JAR"
aws s3 cp "$JAR" "s3://$BUCKET/backend/app-$VERSION.jar" --region "$REGION" --only-show-errors
aws s3 cp "$JAR" "s3://$BUCKET/backend/latest.jar" --region "$REGION" --only-show-errors

echo "==> Restarting service on $INSTANCE"
COMMAND_ID="$(aws ssm send-command \
  --region "$REGION" \
  --instance-ids "$INSTANCE" \
  --document-name AWS-RunShellScript \
  --comment "deploy $VERSION" \
  --parameters "commands=[
    \"aws s3 cp s3://$BUCKET/backend/latest.jar /opt/calendar-copilot/app.jar --region $REGION --only-show-errors\",
    \"chown app:app /opt/calendar-copilot/app.jar\",
    \"systemctl restart calendar-copilot.service\"
  ]" \
  --query Command.CommandId --output text)"
aws ssm wait command-executed --region "$REGION" --command-id "$COMMAND_ID" --instance-id "$INSTANCE" || {
  aws ssm get-command-invocation --region "$REGION" --command-id "$COMMAND_ID" --instance-id "$INSTANCE" \
    --query '{status:Status,stderr:StandardErrorContent}'
  exit 1
}

echo "==> Waiting for $APP_URL/api/health"
for _ in $(seq 1 60); do
  if curl -fsS "$APP_URL/api/health" >/dev/null 2>&1; then
    echo "Backend $VERSION is healthy."
    exit 0
  fi
  sleep 3
done
echo "Health check failed. Check logs: aws logs tail $($TF output -raw log_group) --region $REGION --follow" >&2
exit 1
