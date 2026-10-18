resource "random_password" "db" {
  length  = 32
  special = false
}

resource "aws_db_subnet_group" "main" {
  name       = var.project
  subnet_ids = aws_subnet.private[*].id
}

resource "aws_db_instance" "main" {
  identifier     = var.project
  engine         = "postgres"
  engine_version = "16"
  instance_class = var.db_instance_class

  db_name  = "calendar_copilot"
  username = "copilot"
  password = random_password.db.result

  allocated_storage = 20
  storage_type      = "gp3"
  storage_encrypted = true

  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.db.id]
  publicly_accessible    = false
  multi_az               = false
  # Same AZ as the API instance: no cross-AZ hop on every query.
  availability_zone = local.azs[0]

  backup_retention_period    = 1
  auto_minor_version_upgrade = true
  apply_immediately          = true

  # Demo project: allow a clean `terraform destroy`.
  deletion_protection = false
  skip_final_snapshot = true
}
