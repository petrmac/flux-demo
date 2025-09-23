# Terraform Flux Bootstrap Module Example

This document shows how to create a complete Terraform module for bootstrapping Flux in a production environment.

## Module Structure

```
terraform-infra/
├── modules/
│   └── flux-bootstrap/
│       ├── main.tf
│       ├── variables.tf
│       ├── outputs.tf
│       └── versions.tf
├── environments/
│   ├── production/
│   │   ├── main.tf
│   │   ├── variables.tf
│   │   └── terraform.tfvars
│   ├── staging/
│   └── development/
└── README.md
```

## Flux Bootstrap Module

### modules/flux-bootstrap/versions.tf
```hcl
terraform {
  required_version = ">= 1.5.0"

  required_providers {
    flux = {
      source  = "fluxcd/flux"
      version = ">= 1.2.0"
    }
    github = {
      source  = "integrations/github"
      version = ">= 5.42.0"
    }
    kubectl = {
      source  = "gavinbunney/kubectl"
      version = ">= 1.14.0"
    }
  }
}
```

### modules/flux-bootstrap/variables.tf
```hcl
variable "github_owner" {
  description = "GitHub owner/organization name"
  type        = string
}

variable "github_repository" {
  description = "GitHub repository name for Flux configs"
  type        = string
}

variable "github_token" {
  description = "GitHub personal access token"
  type        = string
  sensitive   = true
}

variable "target_path" {
  description = "Path in the Git repository for this cluster's configs"
  type        = string
}

variable "environment" {
  description = "Environment name (production, staging, development)"
  type        = string
}

variable "cluster_endpoint" {
  description = "Kubernetes cluster endpoint"
  type        = string
}

variable "cluster_ca_certificate" {
  description = "Kubernetes cluster CA certificate"
  type        = string
  sensitive   = true
}

variable "flux_version" {
  description = "Flux version to install"
  type        = string
  default     = "2.2.0"
}

variable "components_extra" {
  description = "Extra Flux components to install"
  type        = list(string)
  default     = [
    "image-reflector-controller",
    "image-automation-controller"
  ]
}

variable "network_policy" {
  description = "Enable network policies"
  type        = bool
  default     = true
}
```

### modules/flux-bootstrap/main.tf
```hcl
# Configure providers
provider "github" {
  owner = var.github_owner
  token = var.github_token
}

provider "kubectl" {
  host                   = var.cluster_endpoint
  cluster_ca_certificate = base64decode(var.cluster_ca_certificate)
  load_config_file       = false
}

# Create or use existing GitHub repository
resource "github_repository" "flux_config" {
  name        = var.github_repository
  description = "Flux configuration repository for ${var.environment}"
  visibility  = "private"

  auto_init              = true
  allow_merge_commit     = true
  delete_branch_on_merge = true
  has_issues             = true

  lifecycle {
    prevent_destroy = true
  }
}

# Add deploy key for Flux
resource "github_repository_deploy_key" "flux" {
  title      = "flux-${var.environment}"
  repository = github_repository.flux_config.name
  key        = tls_private_key.flux.public_key_openssh
  read_only  = false
}

# Generate SSH key for Flux
resource "tls_private_key" "flux" {
  algorithm   = "ECDSA"
  ecdsa_curve = "P256"
}

# Bootstrap Flux
resource "flux_bootstrap_git" "this" {
  depends_on = [github_repository_deploy_key.flux]

  path                    = var.target_path
  network_policy          = var.network_policy
  version                 = var.flux_version
  components_extra        = var.components_extra

  # Git configuration
  url = "ssh://git@github.com/${var.github_owner}/${var.github_repository}.git"
  ssh = {
    username    = "git"
    private_key = tls_private_key.flux.private_key_pem
  }
}

# Create namespace for SOPS secrets
resource "kubectl_manifest" "sops_namespace" {
  yaml_body = <<YAML
apiVersion: v1
kind: Namespace
metadata:
  name: flux-system
YAML

  depends_on = [flux_bootstrap_git.this]
}

# Add SOPS age key secret (for secrets encryption)
resource "kubectl_manifest" "sops_age_secret" {
  count = var.sops_age_key != "" ? 1 : 0

  yaml_body = <<YAML
apiVersion: v1
kind: Secret
metadata:
  name: sops-age
  namespace: flux-system
type: Opaque
data:
  age.agekey: ${base64encode(var.sops_age_key)}
YAML

  depends_on = [kubectl_manifest.sops_namespace]
  sensitive  = true
}

# Create cluster configuration ConfigMap
resource "kubectl_manifest" "cluster_config" {
  yaml_body = <<YAML
apiVersion: v1
kind: ConfigMap
metadata:
  name: cluster-config
  namespace: flux-system
data:
  environment: ${var.environment}
  region: ${var.aws_region}
  cluster_name: ${var.cluster_name}
YAML

  depends_on = [flux_bootstrap_git.this]
}

# Create image update automation
resource "kubectl_manifest" "image_update_automation" {
  for_each = toset(var.image_automation_namespaces)

  yaml_body = <<YAML
apiVersion: image.toolkit.fluxcd.io/v1beta2
kind: ImageUpdateAutomation
metadata:
  name: ${each.value}
  namespace: flux-system
spec:
  interval: 10m
  sourceRef:
    kind: GitRepository
    name: flux-system
  git:
    checkout:
      ref:
        branch: main
    commit:
      author:
        name: fluxbot
        email: fluxbot@${var.github_owner}.github.io
      messageTemplate: |
        Automated image update

        [ci skip]
    push:
      branch: main
  update:
    path: "${var.target_path}/apps/${each.value}"
    strategy: Setters
YAML

  depends_on = [flux_bootstrap_git.this]
}
```

### modules/flux-bootstrap/outputs.tf
```hcl
output "flux_namespace" {
  description = "The namespace where Flux is installed"
  value       = "flux-system"
}

output "flux_version" {
  description = "The version of Flux installed"
  value       = var.flux_version
}

output "git_repository_url" {
  description = "The Git repository URL for Flux configs"
  value       = "https://github.com/${var.github_owner}/${var.github_repository}"
}

output "git_repository_ssh_url" {
  description = "The SSH URL for the Git repository"
  value       = "git@github.com:${var.github_owner}/${var.github_repository}.git"
}

output "deploy_key_id" {
  description = "The ID of the deploy key"
  value       = github_repository_deploy_key.flux.id
}
```

## Environment Configuration

### environments/production/main.tf
```hcl
module "eks_cluster" {
  source = "../../modules/eks"

  cluster_name = "prod-cluster"
  environment  = "production"

  # ... other EKS configuration
}

module "flux_bootstrap" {
  source = "../../modules/flux-bootstrap"

  github_owner           = var.github_owner
  github_repository      = "flux-config"
  github_token           = var.github_token
  target_path            = "clusters/production"
  environment            = "production"

  cluster_endpoint       = module.eks_cluster.cluster_endpoint
  cluster_ca_certificate = module.eks_cluster.cluster_ca_certificate

  flux_version           = "2.2.0"
  components_extra       = [
    "image-reflector-controller",
    "image-automation-controller"
  ]

  image_automation_namespaces = [
    "demo-service",
    "node-service"
  ]

  sops_age_key = var.sops_age_key

  depends_on = [module.eks_cluster]
}
```

### environments/production/terraform.tfvars
```hcl
github_owner = "your-org"
aws_region   = "us-west-2"

# Node groups configuration
node_groups = {
  general = {
    instance_types = ["m5.large"]
    min_size       = 2
    max_size       = 10
    desired_size   = 3
  }
}

# These should come from environment variables or secrets manager
# github_token = env.GITHUB_TOKEN
# sops_age_key = env.SOPS_AGE_KEY
```

## Usage

```bash
# Initialize Terraform
cd environments/production
terraform init

# Plan the deployment
terraform plan -out=tfplan

# Apply the configuration
terraform apply tfplan

# The cluster is now running with Flux installed and configured
# Flux will start syncing from the flux-config repository
```

## Post-Bootstrap Configuration

After Terraform completes, your Flux configuration repository will be initialized with:

```
flux-config/
└── clusters/
    └── production/
        └── flux-system/
            ├── gotk-components.yaml
            ├── gotk-sync.yaml
            └── kustomization.yaml
```

You can then add your applications and infrastructure:

```bash
# Add infrastructure components
flux create kustomization infrastructure \
  --source=flux-system \
  --path="./infrastructure/production" \
  --prune=true \
  --interval=10m

# Add applications
flux create kustomization apps \
  --source=flux-system \
  --path="./apps/production" \
  --prune=true \
  --interval=5m \
  --depends-on=infrastructure
```

## Destroying Resources

To safely destroy the infrastructure:

```bash
# Suspend Flux reconciliation
flux suspend kustomization --all

# Remove finalizers from Flux resources
kubectl -n flux-system patch kustomization/apps -p '{"metadata":{"finalizers":null}}' --type=merge

# Destroy with Terraform
terraform destroy
```