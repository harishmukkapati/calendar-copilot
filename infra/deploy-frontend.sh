#!/usr/bin/env bash
# Builds the Angular app and publishes it to S3 behind CloudFront.
# Hashed bundles are cached for a year; index.html is always revalidated so a
# deploy takes effect immediately.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TF="terraform -chdir=$ROOT/infra/terraform"
REGION="$($TF output -raw region)"
BUCKET="$($TF output -raw frontend_bucket)"
DISTRIBUTION="$($TF output -raw cloudfront_distribution_id)"
APP_URL="$($TF output -raw app_url)"

echo "==> Building frontend"
cd "$ROOT/frontend"
if [[ -s "$HOME/.nvm/nvm.sh" ]]; then
  # shellcheck disable=SC1091
  source "$HOME/.nvm/nvm.sh" >/dev/null && nvm use >/dev/null
fi
npx ng build
DIST="$ROOT/frontend/dist/frontend/browser"

echo "==> Uploading to s3://$BUCKET"
aws s3 sync "$DIST" "s3://$BUCKET" --region "$REGION" --delete --only-show-errors \
  --exclude index.html --exclude favicon.svg \
  --cache-control "public,max-age=31536000,immutable"
aws s3 cp "$DIST/index.html" "s3://$BUCKET/index.html" --region "$REGION" --only-show-errors \
  --cache-control "no-cache"
aws s3 cp "$DIST/favicon.svg" "s3://$BUCKET/favicon.svg" --region "$REGION" --only-show-errors \
  --cache-control "public,max-age=86400"

echo "==> Invalidating CloudFront cache for index.html"
aws cloudfront create-invalidation --distribution-id "$DISTRIBUTION" --paths "/index.html" "/" \
  --query Invalidation.Id --output text >/dev/null

echo "Frontend deployed: $APP_URL"
