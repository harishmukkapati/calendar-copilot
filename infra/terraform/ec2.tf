data "aws_ssm_parameter" "al2023_arm64" {
  name = "/aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-arm64"
}

data "aws_kms_alias" "ssm" {
  name = "alias/aws/ssm"
}

# ---- IAM: what the instance itself may do ----

resource "aws_iam_role" "app" {
  name = "${var.project}-app"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "ec2.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

# Session Manager shell access and SSM Run Command (used by the deploy script).
resource "aws_iam_role_policy_attachment" "ssm_core" {
  role       = aws_iam_role.app.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

resource "aws_iam_role_policy_attachment" "cloudwatch_agent" {
  role       = aws_iam_role.app.name
  policy_arn = "arn:aws:iam::aws:policy/CloudWatchAgentServerPolicy"
}

resource "aws_iam_role_policy" "app" {
  name = "${var.project}-app"
  role = aws_iam_role.app.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid      = "ReadOwnSecrets"
        Effect   = "Allow"
        Action   = ["ssm:GetParameter", "ssm:GetParameters"]
        Resource = "arn:aws:ssm:${var.region}:${local.account_id}:parameter${local.ssm_prefix}/*"
      },
      {
        Sid      = "DecryptSecureStrings"
        Effect   = "Allow"
        Action   = "kms:Decrypt"
        Resource = data.aws_kms_alias.ssm.target_key_arn
      },
      {
        Sid      = "DownloadBackendJar"
        Effect   = "Allow"
        Action   = "s3:GetObject"
        Resource = "${aws_s3_bucket.artifacts.arn}/backend/*"
      }
    ]
  })
}

resource "aws_iam_instance_profile" "app" {
  name = "${var.project}-app"
  role = aws_iam_role.app.name
}

# ---- Instance ----

resource "aws_instance" "app" {
  ami                    = data.aws_ssm_parameter.al2023_arm64.value
  instance_type          = var.instance_type
  subnet_id              = aws_subnet.public[0].id
  vpc_security_group_ids = [aws_security_group.app.id]
  iam_instance_profile   = aws_iam_instance_profile.app.name

  metadata_options {
    http_tokens   = "required" # IMDSv2 only
    http_endpoint = "enabled"
  }

  root_block_device {
    volume_type = "gp3"
    volume_size = 20
    encrypted   = true
  }

  user_data = templatefile("${path.module}/user_data.sh.tftpl", {
    region        = var.region
    project       = var.project
    ssm_prefix    = local.ssm_prefix
    db_host       = aws_db_instance.main.address
    db_port       = aws_db_instance.main.port
    db_name       = aws_db_instance.main.db_name
    db_username   = aws_db_instance.main.username
    gemini_model  = var.gemini_model
    log_group     = aws_cloudwatch_log_group.app.name
    artifacts_uri = "s3://${aws_s3_bucket.artifacts.bucket}/backend/latest.jar"
  })
  user_data_replace_on_change = true

  tags = { Name = var.project }

  lifecycle {
    # A newer AMI should not silently replace the running server.
    ignore_changes = [ami]
  }
}

# Stable public address so CloudFront's origin hostname survives stop/start.
resource "aws_eip" "app" {
  domain   = "vpc"
  instance = aws_instance.app.id
  tags     = { Name = var.project }
}
