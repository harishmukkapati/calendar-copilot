output "app_url" {
  description = "Public HTTPS URL of the app."
  value       = "https://${aws_cloudfront_distribution.main.domain_name}"
}

output "region" {
  value = var.region
}

output "cloudfront_distribution_id" {
  value = aws_cloudfront_distribution.main.id
}

output "frontend_bucket" {
  value = aws_s3_bucket.frontend.bucket
}

output "artifacts_bucket" {
  value = aws_s3_bucket.artifacts.bucket
}

output "instance_id" {
  value = aws_instance.app.id
}

output "db_endpoint" {
  value = aws_db_instance.main.address
}

output "log_group" {
  value = aws_cloudwatch_log_group.app.name
}
