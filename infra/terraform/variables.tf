variable "region" {
  description = "AWS region for everything except CloudFront (global)."
  type        = string
  default     = "us-east-2"
}

variable "project" {
  description = "Name prefix for resources and SSM parameters."
  type        = string
  default     = "calendar-copilot"
}

variable "alert_email" {
  description = "Email address for CloudWatch alarms and budget alerts (you must confirm the SNS subscription email)."
  type        = string
}

variable "monthly_budget_usd" {
  description = "AWS Budgets alert threshold in USD."
  type        = number
  default     = 20
}

variable "instance_type" {
  description = "EC2 instance type for the Spring Boot API (ARM/Graviton)."
  type        = string
  default     = "t4g.small"
}

variable "db_instance_class" {
  description = "RDS instance class."
  type        = string
  default     = "db.t4g.micro"
}

variable "gemini_model" {
  description = "Gemini model id used by the AI planner."
  type        = string
  default     = "gemini-flash-latest"
}
