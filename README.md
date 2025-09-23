# Flux Demo - Distributed Tracing with OpenTelemetry

This repository demonstrates a cloud-native microservices architecture with distributed tracing using Flux GitOps, OpenTelemetry auto-instrumentation, and observability best practices.

## 🏗️ Real-World GitOps Repository Architecture

In a production environment, you would typically have multiple repositories following separation of concerns:

```mermaid
graph TB
    subgraph "Developer Repositories"
        A1[java-service repo]
        A2[node-service repo]
        A3[other-service repos]
    end

    subgraph "GitOps Repository"
        B[flux-config repo]
        B --> B1[clusters/]
        B --> B2[infrastructure/]
        B --> B3[apps/]
    end

    subgraph "Infrastructure Repository"
        C[terraform-infra repo]
        C --> C1[modules/]
        C --> C2[environments/]
        C --> C3[flux-bootstrap/]
    end

    subgraph "Kubernetes Clusters"
        D1[Development]
        D2[Staging]
        D3[Production]
    end

    A1 -->|CI/CD builds images| E[Container Registry]
    A2 -->|CI/CD builds images| E
    A3 -->|CI/CD builds images| E

    C -->|1. Provisions clusters| D1
    C -->|1. Provisions clusters| D2
    C -->|1. Provisions clusters| D3

    C -->|2. Installs Flux| D1
    C -->|2. Installs Flux| D2
    C -->|2. Installs Flux| D3

    B -->|3. Flux syncs configs| D1
    B -->|3. Flux syncs configs| D2
    B -->|3. Flux syncs configs| D3

    E -->|4. Flux pulls images| D1
    E -->|4. Flux pulls images| D2
    E -->|4. Flux pulls images| D3
```

### Repository Separation Strategy

#### 1. **Application Repositories** (`java-service`, `node-service`, etc.)
- Contains application source code only
- CI/CD pipeline builds and pushes container images
- Developers work here daily
- No Kubernetes manifests (separation of concerns)

```yaml
# Example: java-service/.gitlab-ci.yml
stages:
  - build
  - test
  - publish

publish:
  stage: publish
  script:
    - docker build -t $REGISTRY/java-service:$VERSION .
    - docker push $REGISTRY/java-service:$VERSION
```

#### 2. **Flux Configuration Repository** (`flux-config`)
- Contains all Kubernetes manifests and Flux configurations
- No application code
- Managed by platform/DevOps team
- Single source of truth for cluster state

```
flux-config/
├── clusters/
│   ├── production/
│   │   ├── flux-system/
│   │   ├── infrastructure.yaml
│   │   └── apps.yaml
│   ├── staging/
│   └── development/
├── infrastructure/
│   ├── common/
│   ├── monitoring/
│   ├── ingress/
│   └── sources/
└── apps/
    ├── base/
    └── overlays/
        ├── production/
        ├── staging/
        └── development/
```

#### 3. **Terraform Infrastructure Repository** (`terraform-infra`)
- Provisions cloud infrastructure (EKS/GKE/AKS clusters)
- Installs and configures Flux via Terraform
- Manages cloud resources (VPC, IAM, DNS, etc.)
- See [Complete Terraform Flux Module Example](docs/terraform-flux-example.md)

```hcl
# Example: terraform-infra/modules/flux-bootstrap/main.tf
module "flux_bootstrap" {
  source = "github.com/fluxcd/terraform-provider-flux//modules/bootstrap"

  github_owner         = var.github_owner
  github_repository    = var.flux_config_repo
  github_token         = var.github_token
  target_path          = "clusters/${var.environment}"
  components_extra     = ["image-reflector-controller", "image-automation-controller"]
}
```

### Real-World Workflow

1. **Infrastructure Provisioning** (Day 0)
   ```bash
   # From terraform-infra repo
   terraform apply -var="environment=production"
   # This creates:
   # - Kubernetes cluster
   # - Networking, IAM, storage
   # - Installs Flux pointing to flux-config repo
   ```

2. **Developer Workflow** (Day 2)
   ```bash
   # Developer makes changes in java-service repo
   git commit -m "feat: add new endpoint"
   git push
   # CI/CD automatically:
   # - Runs tests
   # - Builds container: java-service:1.2.3
   # - Pushes to registry
   ```

3. **GitOps Deployment**
   ```yaml
   # Platform team updates flux-config repo
   # apps/overlays/production/java-service-patch.yaml
   - name: java-service
     newTag: 1.2.3  # Or use Flux image automation
   ```

4. **Flux Synchronization**
   - Flux detects changes in `flux-config` repo
   - Applies manifests to cluster
   - Pulls new images from registry
   - Updates running workloads

### Image Tagging and Promotion Strategy

#### Tagging Convention

```mermaid
graph LR
    subgraph "CI/CD Pipeline"
        A[Code Push] --> B{Branch?}
        B -->|main| C[Build: 1.2.3]
        B -->|feature| D[Build: 1.2.3-feat-xyz]
        B -->|release| E[Build: 1.2.3-rc1]
    end

    subgraph "Registry Strategy"
        C --> F[dev-registry/app:1.2.3]
        E --> G[dev-registry/app:1.2.3-rc1]
        F --> H[Tag: 1.2.3-preprod]
        H --> I[staging-registry/app:1.2.3]
        I --> J[Tag: 1.2.3-prod]
        J --> K[prod-registry/app:1.2.3]
    end

    subgraph "Flux Automation"
        F -->|Auto Deploy| L[DEV Environment]
        H -->|Manual Approval| M[PREPROD Environment]
        K -->|Manual Approval| N[PROD Environment]
    end
```

#### Environment-Specific Image Policies

```yaml
# flux-config/clusters/development/image-policies.yaml
apiVersion: image.toolkit.fluxcd.io/v1beta2
kind: ImageRepository
metadata:
  name: demo-service
  namespace: flux-system
spec:
  image: ghcr.io/yourorg/demo-service
  interval: 1m
---
apiVersion: image.toolkit.fluxcd.io/v1beta2
kind: ImagePolicy
metadata:
  name: demo-service-dev
  namespace: flux-system
spec:
  imageRepositoryRef:
    name: demo-service
  policy:
    semver:
      range: ">=1.0.0"  # Any semver version auto-deploys to dev
---
# flux-config/clusters/staging/image-policies.yaml
apiVersion: image.toolkit.fluxcd.io/v1beta2
kind: ImagePolicy
metadata:
  name: demo-service-staging
  namespace: flux-system
spec:
  imageRepositoryRef:
    name: demo-service
  filterTags:
    pattern: '^.*-preprod$'  # Only tags ending with -preprod
  policy:
    semver:
      range: ">=1.0.0-preprod"
---
# flux-config/clusters/production/image-policies.yaml
apiVersion: image.toolkit.fluxcd.io/v1beta2
kind: ImageRepository
metadata:
  name: demo-service-prod
  namespace: flux-system
spec:
  image: prod-registry.company.com/demo-service  # Different registry
  interval: 5m
  secretRef:
    name: prod-registry-auth  # Separate credentials
---
apiVersion: image.toolkit.fluxcd.io/v1beta2
kind: ImagePolicy
metadata:
  name: demo-service-prod
  namespace: flux-system
spec:
  imageRepositoryRef:
    name: demo-service-prod
  filterTags:
    pattern: '^.*-prod$'  # Only tags ending with -prod
  policy:
    semver:
      range: ">=1.0.0-prod"
```

#### CI/CD Pipeline Example

```yaml
# .gitlab-ci.yml or .github/workflows/release.yml
stages:
  - build
  - promote
  - release

build:
  script:
    # Build and push with semver tag
    - docker build -t ${REGISTRY}/app:${VERSION} .
    - docker push ${REGISTRY}/app:${VERSION}
    # Auto-deploys to DEV via Flux

promote-to-staging:
  when: manual  # Requires manual approval
  script:
    # Tag for staging
    - docker pull ${REGISTRY}/app:${VERSION}
    - docker tag ${REGISTRY}/app:${VERSION} ${REGISTRY}/app:${VERSION}-preprod
    - docker push ${REGISTRY}/app:${VERSION}-preprod
    # Flux picks this up for PREPROD

promote-to-production:
  when: manual  # Requires manual approval
  script:
    # Copy to production registry with different auth
    - docker pull ${STAGING_REGISTRY}/app:${VERSION}
    - docker tag ${STAGING_REGISTRY}/app:${VERSION} ${PROD_REGISTRY}/app:${VERSION}-prod
    - docker login ${PROD_REGISTRY} -u ${PROD_USER} -p ${PROD_TOKEN}
    - docker push ${PROD_REGISTRY}/app:${VERSION}-prod
    # Flux picks this up for PROD
```


#### Advanced Promotion Strategies

##### Blue-Green Deployments with Image Tags

```yaml
# Production can use blue-green strategy
apiVersion: image.toolkit.fluxcd.io/v1beta2
kind: ImagePolicy
metadata:
  name: app-blue
  namespace: flux-system
spec:
  imageRepositoryRef:
    name: app-prod-repo
  filterTags:
    pattern: '^[0-9]+\.[0-9]+\.[0-9]+-blue$'
  policy:
    semver:
      range: ">=1.0.0"

---
apiVersion: image.toolkit.fluxcd.io/v1beta2
kind: ImagePolicy
metadata:
  name: app-green
  namespace: flux-system
spec:
  imageRepositoryRef:
    name: app-prod-repo
  filterTags:
    pattern: '^[0-9]+\.[0-9]+\.[0-9]+-green$'
  policy:
    semver:
      range: ">=1.0.0"
```

##### Canary Releases with Progressive Delivery

```yaml
# Using Flagger for canary deployments
apiVersion: flagger.app/v1beta1
kind: Canary
metadata:
  name: app-canary
  namespace: production
spec:
  targetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: app
  progressDeadlineSeconds: 600
  service:
    port: 8080
  analysis:
    interval: 1m
    threshold: 5
    maxWeight: 50
    stepWeight: 10
    metrics:
    - name: request-success-rate
      thresholdRange:
        min: 99
      interval: 1m
  # Automatic promotion based on image tags
  provider: flux
  imageRepository: app-prod-repo
  imageTag: "1.2.3-canary"
```

##### Emergency Rollback Procedures

```bash
# Quick rollback using Flux
flux suspend kustomization apps --namespace=flux-system
flux resume kustomization apps --namespace=flux-system

# Manual image rollback
kubectl set image deployment/app app=prod-registry.company.com/app:1.2.2-prod \
  -n production

# GitOps rollback
git revert HEAD  # Revert last commit with image update
git push origin main
# Flux will sync and rollback automatically
```

### Registry Separation Strategy

In case SOC2 or ISO27001 compliance needs to be in place, a repository separation with different set of rules needs to be put in place.
If not required, you can ignore this chapter. There could be also variations to this strategy,
when only production is segregated.

```yaml
# Different registries for different environments
Development:
  Registry: ghcr.io/yourorg/*
  Access: All developers can push
  Retention: 30 days

Staging:
  Registry: staging-registry.company.com/*
  Access: CI/CD + Release team
  Retention: 90 days
  Scanning: Required

Production:
  Registry: prod-registry.company.com/*
  Access: CI/CD with approval only
  Retention: 1 year
  Scanning: Required + signed images
  Compliance: SOC2, ISO27001 compliant
```

#### Multi-Environment Image Promotion Workflow

```mermaid
sequenceDiagram
    participant Dev as Developer
    participant CI as CI/CD
    participant DR as Dev Registry
    participant SR as Staging Registry
    participant PR as Prod Registry
    participant FD as Flux (Dev)
    participant FS as Flux (Staging)
    participant FP as Flux (Prod)

    Dev->>CI: Push to main branch
    CI->>CI: Build & Test
    CI->>DR: Push image:1.2.3
    FD->>DR: Detect new image
    FD->>FD: Deploy to Dev

    Note over CI,SR: Manual Approval Required
    CI->>DR: Pull image:1.2.3
    CI->>SR: Push image:1.2.3-preprod
    FS->>SR: Detect -preprod tag
    FS->>FS: Deploy to Staging

    Note over CI,PR: Manual Approval + Security Scan
    CI->>SR: Pull image:1.2.3-preprod
    CI->>PR: Push image:1.2.3-prod (signed)
    FP->>PR: Detect -prod tag
    FP->>FP: Deploy to Production
```

##### Example: Complete Promotion Pipeline

```yaml
# .gitlab-ci.yml
variables:
  DEV_REGISTRY: ghcr.io/yourorg
  STAGING_REGISTRY: staging.company.com
  PROD_REGISTRY: prod.company.com

stages:
  - build
  - test
  - deploy-dev
  - promote-staging
  - promote-production

# Automatic deployment to DEV
deploy-dev:
  stage: deploy-dev
  script:
    - docker build -t ${DEV_REGISTRY}/${APP_NAME}:${VERSION} .
    - docker push ${DEV_REGISTRY}/${APP_NAME}:${VERSION}
    # Flux automatically picks this up for dev environment
  only:
    - main

# Manual promotion to STAGING
promote-staging:
  stage: promote-staging
  when: manual
  script:
    # Security scan before promotion
    - trivy image ${DEV_REGISTRY}/${APP_NAME}:${VERSION}

    # Re-tag for staging
    - docker pull ${DEV_REGISTRY}/${APP_NAME}:${VERSION}
    - docker tag ${DEV_REGISTRY}/${APP_NAME}:${VERSION} \
                 ${STAGING_REGISTRY}/${APP_NAME}:${VERSION}-preprod

    # Push to staging registry
    - docker login ${STAGING_REGISTRY} -u ${STAGING_USER} -p ${STAGING_TOKEN}
    - docker push ${STAGING_REGISTRY}/${APP_NAME}:${VERSION}-preprod

    # Create promotion record
    - |
      echo "Image promoted to staging" >> promotion-log.txt
      echo "Version: ${VERSION}" >> promotion-log.txt
      echo "Promoted by: ${GITLAB_USER_LOGIN}" >> promotion-log.txt
      echo "Date: $(date)" >> promotion-log.txt
  only:
    - main

# Manual promotion to PRODUCTION
promote-production:
  stage: promote-production
  when: manual
  needs: ["promote-staging"]
  script:
    # Enhanced security checks
    - trivy image --severity HIGH,CRITICAL \
                  ${STAGING_REGISTRY}/${APP_NAME}:${VERSION}-preprod

    # Sign image with cosign
    - cosign sign --key ${COSIGN_KEY} \
                  ${STAGING_REGISTRY}/${APP_NAME}:${VERSION}-preprod

    # Copy to production registry
    - docker pull ${STAGING_REGISTRY}/${APP_NAME}:${VERSION}-preprod
    - docker tag ${STAGING_REGISTRY}/${APP_NAME}:${VERSION}-preprod \
                 ${PROD_REGISTRY}/${APP_NAME}:${VERSION}-prod

    # Push to production registry (air-gapped)
    - docker login ${PROD_REGISTRY} -u ${PROD_USER} -p ${PROD_TOKEN}
    - docker push ${PROD_REGISTRY}/${APP_NAME}:${VERSION}-prod

    # Create audit trail
    - |
      cat <<EOF | kubectl apply -f -
      apiVersion: v1
      kind: ConfigMap
      metadata:
        name: promotion-${VERSION}
        namespace: flux-system
      data:
        version: "${VERSION}"
        promoted_by: "${GITLAB_USER_LOGIN}"
        promoted_at: "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
        source_image: "${STAGING_REGISTRY}/${APP_NAME}:${VERSION}-preprod"
        target_image: "${PROD_REGISTRY}/${APP_NAME}:${VERSION}-prod"
      EOF
  only:
    - main
  environment:
    name: production
    url: https://app.company.com
```

##### Flux Configuration for Each Environment

```yaml
# clusters/development/apps-image-automation.yaml
apiVersion: image.toolkit.fluxcd.io/v1beta1
kind: ImageUpdateAutomation
metadata:
  name: apps-automation
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
        email: fluxbot@company.com
        name: Flux Bot
      messageTemplate: |
        [DEV] Automated image update

        Images:
        {{range .Updated.Images}}
        - {{.}}
        {{end}}
    push:
      branch: main
  update:
    path: "./clusters/development"
    strategy: Setters

---
# clusters/staging/apps-image-policy.yaml
apiVersion: image.toolkit.fluxcd.io/v1beta2
kind: ImagePolicy
metadata:
  name: app-staging
  namespace: flux-system
spec:
  imageRepositoryRef:
    name: app-staging-repo
  filterTags:
    pattern: '^[0-9]+\.[0-9]+\.[0-9]+-preprod$'
    extract: '$0'
  policy:
    alphabetical:
      order: asc

---
# clusters/production/apps-image-policy.yaml
apiVersion: image.toolkit.fluxcd.io/v1beta2
kind: ImagePolicy
metadata:
  name: app-production
  namespace: flux-system
spec:
  imageRepositoryRef:
    name: app-prod-repo
  filterTags:
    pattern: '^[0-9]+\.[0-9]+\.[0-9]+-prod$'
    extract: '$0'
  policy:
    alphabetical:
      order: asc
```

### Benefits of This Architecture

✅ **Separation of Concerns**
- Developers focus on code
- Platform team manages infrastructure
- Clear ownership boundaries

✅ **Security**
- Application repos don't need cluster access
- Flux has minimal permissions (pull only)
- Secrets managed separately (SOPS/Sealed Secrets)
- Production images in isolated registry with strict access
- Image signing and verification with cosign
- Vulnerability scanning at each promotion stage

✅ **Scalability**
- Easy to add new clusters
- Consistent across environments
- Reusable Terraform modules
- Multi-region deployments supported

✅ **Auditability**
- All changes tracked in Git
- Clear deployment history
- Easy rollbacks
- Image promotion tracked through tags
- Complete audit trail with ConfigMaps
- Compliance-ready with SOC2/ISO27001

✅ **Reliability**
- Automated rollback on failures
- Progressive delivery support
- Blue-green deployment capabilities
- Canary releases with metrics-based promotion

## 📁 Current Demo Repository Structure

> **Note**: This demo repository combines all components for simplicity. In production, these would be separate repositories as shown above.

```
flux-demo/
├── java-service/           # Java Spring Boot application
│   ├── src/               # Source code
│   ├── build.gradle       # Gradle build configuration
│   └── Dockerfile        # Container image definition
│
├── node-service/          # Node.js NestJS application
│   ├── src/              # TypeScript source code
│   ├── package.json      # NPM dependencies
│   └── Dockerfile        # Container image definition
│
├── flux/                  # All Kubernetes and Flux configurations
│   ├── apps/             # Application deployments
│   │   ├── demo-service/ # Java service manifests
│   │   │   ├── base/     # Base Kustomization
│   │   │   └── minikube/ # Minikube-specific configs
│   │   └── node-service/ # Node service manifests
│   │       ├── base/     # Base Kustomization
│   │       └── minikube/ # Minikube-specific configs
│   │
│   ├── infrastructure/   # Infrastructure components
│   │   ├── opentelemetry/          # OpenTelemetry collector configuration
│   │   ├── opentelemetry-operator/ # OpenTelemetry operator deployment
│   │   ├── jaeger/                 # Jaeger tracing backend
│   │   └── sources/                # Helm repositories and sources
│   │
│   └── clusters/         # Cluster-specific configurations
│       └── minikube/     # Minikube cluster config
│
└── scripts/              # Setup and utility scripts
```

## 🚀 Features

- **Microservices Architecture**:
  - **Java Service** (Spring Boot): Backend REST API service
  - **Node Service** (NestJS): TypeScript backend that calls Java service
  - Service-to-service communication with distributed tracing
- **GitOps with Flux CD**: Automated deployments and configuration management
- **Zero-Code Instrumentation**:
  - OpenTelemetry Operator auto-instrumentation
  - No manual tracing code required
  - Support for Java and Node.js applications
- **Distributed Tracing**:
  - Complete request flow visibility across services
  - Jaeger backend for trace storage and visualization
  - Automatic trace context propagation
  - OTLP protocol support
- **Image Automation**:
  - Automatic image scanning with Image Reflector Controller
  - Policy-based image selection (semver)
  - Automated Git commits for image updates
- **Secret Management with SOPS**: Encrypted secrets in Git
- **Health Checks**: Liveness and readiness probes for all services
- **Security**: Non-root containers, security contexts, RBAC

## 🛠 Prerequisites

- Docker
- Minikube
- kubectl
- Flux CLI
- age (for SOPS encryption)
- Gradle 8.5+ (for local development)
- Java 21+ (LTS)

## 📦 Installation

### ⚠️ IMPORTANT: Update Git Repository URL

Before running setup, edit `flux/clusters/minikube/flux-system/gotk-sync.yaml`:
```yaml
# Replace <your-username> with your GitHub username
url: https://github.com/YOUR-USERNAME/flux-demo
```

### 1. Start Minikube

```bash
minikube start --memory 4096 --cpus 2
eval $(minikube docker-env)  # Use Minikube's Docker daemon
```

### 2. Build the Applications

#### Java Service
```bash
cd java-service
./gradlew clean build
# For Spring Boot 3.x with buildpack support:
./gradlew bootBuildImage --imageName=ghcr.io/petrmac/flux-demo/demo-service:latest
# Load into Minikube
minikube image load ghcr.io/petrmac/flux-demo/demo-service:latest
```

#### Node Service
```bash
cd node-service
npm install
npm run build
docker build -t ghcr.io/petrmac/flux-demo/node-service:latest .
# Load into Minikube
minikube image load ghcr.io/petrmac/flux-demo/node-service:latest
```

### 3. Install Flux with Image Automation

```bash
flux check --pre
flux install \
  --components-extra=image-reflector-controller,image-automation-controller
```

### 4. Set up SOPS encryption

```bash
# Generate age key for SOPS
age-keygen -o age.key

# Create secret in cluster
kubectl create namespace flux-system
kubectl create secret generic sops-age \
  --namespace=flux-system \
  --from-file=age.agekey=age.key
```

### 5. Bootstrap Flux (Option A: Using GitHub)

```bash
export GITHUB_TOKEN=<your-github-token>
export GITHUB_USER=<your-github-username>

flux bootstrap github \
  --owner=$GITHUB_USER \
  --repository=flux-demo \
  --branch=main \
  --path=./flux/clusters/minikube \
  --personal
```

### 5. Bootstrap Flux (Option B: Manual Installation)

```bash
# Apply Flux components
kubectl apply -f flux/clusters/minikube/flux-system/

# Wait for Flux to be ready
flux check

# Apply infrastructure
kubectl apply -f flux/clusters/minikube/infrastructure.yaml

# Apply apps
kubectl apply -f flux/clusters/minikube/apps.yaml
```

## 🔧 Configuration

### Environment Variables

The application uses the following environment variables (configured via ConfigMap):

- `APP_VERSION`: Application version
- `ENVIRONMENT`: Deployment environment (development/production)
- `GREETING_PREFIX`: Prefix for greeting messages
- `GREETING_SUFFIX`: Suffix for greeting messages

### Secrets

Secrets are managed with SOPS and include:
- `DATABASE_URL`: Database connection string
- `API_KEY`: External API key
- `JWT_SECRET`: JWT signing key
- `OAUTH_CLIENT_SECRET`: OAuth client secret

To encrypt secrets:
```bash
sops -e -i flux/apps/demo-service/base/secret.enc.yaml
```

## 📊 Auto-Instrumentation with OpenTelemetry

### Zero-Code Instrumentation

Both services use OpenTelemetry auto-instrumentation via the Operator - **no manual instrumentation code required!**

#### Java Service Configuration
```yaml
annotations:
  instrumentation.opentelemetry.io/inject-java: "opentelemetry/opentelemetry-collector"
  instrumentation.opentelemetry.io/container-names: "demo-service"
```

#### Node Service Configuration
```yaml
annotations:
  instrumentation.opentelemetry.io/inject-nodejs: "opentelemetry/opentelemetry-collector"
  instrumentation.opentelemetry.io/container-names: "node-service"
```

### Components
- **OpenTelemetry Operator**: Manages instrumentation lifecycle
- **Auto-instrumentation**: Automatically injected at pod startup
- **Collector**: Centralized telemetry processing
- **Jaeger**: Distributed trace storage and visualization

### Service Endpoints

#### Node Service (Port 3000)
- `GET /` - Service information
- `GET /health` - Health check
- `GET /ready` - Readiness check
- `GET /api/greeting?name=X` - Calls Java service and returns greeting
- `POST /api/chain` - Creates chain of requests for deeper traces
- `POST /api/simulate` - Simulates various scenarios (slow, error, timeout)

#### Java Service (Port 8080)
- `GET /actuator/health` - Health check
- `GET /actuator/prometheus` - Prometheus metrics
- `GET /api/greeting?name=X` - Returns greeting message
- `POST /api/echo` - Echoes back the message
- `POST /api/simulate` - Simulates various scenarios

### Viewing Traces in Jaeger

1. **Access Jaeger UI**: http://localhost:16686
2. **Select Service**: Choose `node-service` or `demo-service` from the dropdown
3. **Find Traces**: Click "Find Traces" to see all requests
4. **Analyze Trace**: Click on any trace to see the distributed trace details

#### What to Look For in Jaeger:
- **Trace Timeline**: Shows the complete request flow through both services
- **Service Dependencies**: View the system architecture diagram
- **Span Details**: Click spans to see:
  - HTTP headers and parameters
  - Response status codes
  - Processing duration
  - Error details (if any)
- **Critical Path**: Identifies performance bottlenecks
- **Compare Traces**: Compare multiple traces to identify patterns

## 🧪 Testing Distributed Tracing

### Access Services

```bash
# Port-forward Node Service
kubectl port-forward -n node-service svc/node-service 3000:3000
```
```bash
# Port-forward Java Service (optional, for direct testing)
kubectl port-forward -n demo-service svc/demo-service 8080:8080
```
```bash
# Port-forward Jaeger UI
kubectl port-forward -n jaeger svc/jaeger-query 8086:16686
```

### Test Service Communication

#### Basic Request Flow (Node → Java)
```bash
# Simple greeting request that creates a distributed trace
curl http://localhost:3000/api/greeting?name=World

# Response shows the complete chain
{
  "message": "Hello, World!",
  "timestamp": "2025-01-22T10:00:00.000Z",
  "source": "node-service",
  "upstreamSource": "demo-service"
}
```

#### Chain Requests (Deeper Traces)
```bash
# Create a multi-hop trace
curl -X POST http://localhost:3000/api/chain \
  -H "Content-Type: application/json" \
  -d '{"message": "Test Chain", "depth": 0}'
```

#### Simulate Different Scenarios
```bash
# Slow response (3 second delay)
curl -X POST http://localhost:3000/api/simulate \
  -H "Content-Type: application/json" \
  -d '{"scenario": "slow", "delay": 500}'

# Error scenario
curl -X POST http://localhost:3000/api/simulate \
  -H "Content-Type: application/json" \
  -d '{"scenario": "error"}'

# Normal with custom delay
curl -X POST http://localhost:3000/api/simulate \
  -H "Content-Type: application/json" \
  -d '{"scenario": "normal", "delay": 1000}'
```

#### Batch Testing (Generate Multiple Traces)
```bash
# Send 10 requests in parallel
for i in {1..10}; do
  curl -s http://localhost:3000/api/greeting?name=Test$i &
done
wait
```

### Monitoring Flux

```bash
# Check Flux status
flux get all

# Check kustomizations
flux get kustomizations

# Check sources
flux get sources git
flux get sources helm

# Check Helm releases
flux get helmreleases -A

# Watch logs
flux logs --follow
```

## 🔄 Continuous Deployment

Flux automatically syncs changes from Git:

1. Push changes to the repository
2. Flux detects changes (default: 1-minute interval)
3. Applies changes to the cluster
4. Monitors health checks

### Manual Reconciliation

```bash
# Force reconciliation
flux reconcile source git flux-system
flux reconcile kustomization demo-service
```

## 🖼️ Image Automation

### Building and Tagging Images

Use the provided script to build and tag images:

```bash
# Build with automatic versioning
./scripts/build-and-tag.sh

# Build with specific version
./scripts/build-and-tag.sh -v 1.0.1

# Build and push to Docker Hub
./scripts/build-and-tag.sh -v 1.0.1 -o yourusername -p

# Build and push to GitHub Container Registry
./scripts/build-and-tag.sh -v 1.0.1 -r ghcr.io -o yourorg -p
```

### Image Update Policies

The setup includes several image update policies:

- **Semver Range**: Updates based on semantic versioning
  - `~1.0.0` - Patch updates only (1.0.x)
  - `^1.0.0` - Minor and patch updates (1.x.x)
  - `>=1.0.0` - Any newer version

- **Latest**: Always use the most recent image
- **Dev Branches**: Track development branches with pattern matching

### Monitoring Image Updates

```bash
# List image repositories
flux get image repository

# List image policies
flux get image policy

# Check image update automation status
flux get image update

# Force image scan
flux reconcile image repository demo-service

# Force image update
flux reconcile image update demo-service
```

## 📝 Development Workflow

### With Image Automation (Recommended)

1. **Make code changes** in `java-service/`
2. **Build and tag** new Docker image:
   ```bash
   ./scripts/build-and-tag.sh -v 1.0.1 -p
   ```
3. **Flux automatically**:
   - Scans the registry for new images
   - Updates manifests according to policies
   - Commits changes to Git
   - Deploys to cluster

### Manual Workflow

1. **Make code changes** in `java-service/`
2. **Build and tag** new Docker image
3. **Update image tag** in Kubernetes manifests
4. **Commit and push** to Git
5. **Flux deploys** automatically

## 🔄 Migration from Demo to Production Setup

### Step 1: Split the Repositories

```bash
# 1. Create separate application repositories
git subtree split --prefix=java-service -b java-service-branch
git subtree split --prefix=node-service -b node-service-branch

# 2. Push to new repositories
git push git@github.com:yourorg/java-service.git java-service-branch:main
git push git@github.com:yourorg/node-service.git node-service-branch:main

# 3. Create flux-config repository
git subtree split --prefix=flux -b flux-config-branch
git push git@github.com:yourorg/flux-config.git flux-config-branch:main
```

### Step 2: Create Terraform Infrastructure

```hcl
# terraform-infra/environments/production/main.tf
module "eks_cluster" {
  source = "../../modules/eks"

  cluster_name    = "production-cluster"
  region          = "us-west-2"
  node_groups     = var.node_groups
}

module "flux" {
  source = "../../modules/flux-bootstrap"

  cluster_endpoint = module.eks_cluster.endpoint
  github_owner     = "yourorg"
  github_repo      = "flux-config"
  target_path      = "clusters/production"
}
```

### Step 3: Update CI/CD Pipelines

```yaml
# java-service/.github/workflows/ci.yml
name: Build and Push
on:
  push:
    branches: [main]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Build and push
        run: |
          docker build -t ghcr.io/yourorg/java-service:${{ github.sha }} .
          docker push ghcr.io/yourorg/java-service:${{ github.sha }}

      # No Kubernetes deployment here - Flux handles it
```

### Step 4: Configure Flux for Multi-Environment

```yaml
# flux-config/clusters/production/apps.yaml
apiVersion: kustomize.toolkit.fluxcd.io/v1
kind: Kustomization
metadata:
  name: apps
  namespace: flux-system
spec:
  interval: 10m
  path: ./apps/overlays/production
  prune: true
  sourceRef:
    kind: GitRepository
    name: flux-system
  postBuild:
    substituteFrom:
      - kind: ConfigMap
        name: cluster-config  # Environment-specific values
```

## 🔒 Security Best Practices

- Non-root user in containers
- Read-only root filesystem where possible
- Security contexts with minimal capabilities
- Network policies (can be added)
- RBAC with ServiceAccounts
- Encrypted secrets with SOPS

## 🐛 Troubleshooting

### Check Pod Status
```bash
# Check all service pods
kubectl get pods -n demo-service
kubectl get pods -n node-service
kubectl get pods -n opentelemetry
kubectl get pods -n jaeger

# Check logs for specific service
kubectl logs -n demo-service deployment/demo-service
kubectl logs -n node-service deployment/node-service
```

### Check Flux Events
```bash
kubectl get events -n flux-system --sort-by='.lastTimestamp'
```

### Verify OpenTelemetry Auto-Instrumentation
```bash
# Check OpenTelemetry Operator
kubectl get deployment -n opentelemetry-operator
kubectl logs -n opentelemetry-operator deployment/opentelemetry-operator

# Check OpenTelemetry Collector
kubectl get pods -n opentelemetry
kubectl logs -n opentelemetry daemonset/opentelemetry-collector

# Check Instrumentation CRD
kubectl get instrumentation -A
kubectl describe instrumentation -n opentelemetry opentelemetry-collector

# Verify instrumentation is injected in pods
kubectl describe pod -n node-service -l app.kubernetes.io/name=node-service | grep -A5 "Init Containers"
kubectl describe pod -n demo-service -l app.kubernetes.io/name=demo-service | grep -A5 "Init Containers"

# Check if OTEL environment variables are set
kubectl exec -n node-service deployment/node-service -- env | grep OTEL
kubectl exec -n demo-service deployment/demo-service -- env | grep OTEL

# Check Jaeger
kubectl get deployment -n jaeger
kubectl logs -n jaeger deployment/jaeger
```

### No Traces Appearing?

1. **Verify services are communicating**:
   ```bash
   # Test node service can reach java service
   kubectl exec -n node-service deployment/node-service -- curl http://demo-service.demo-service.svc.cluster.local:8080/api/greeting
   ```

2. **Check OpenTelemetry Collector is receiving data**:
   ```bash
   kubectl logs -n opentelemetry daemonset/opentelemetry-collector | grep -i trace
   ```

3. **Verify Jaeger is receiving traces**:
   ```bash
   kubectl logs -n jaeger deployment/jaeger | grep -i received
   ```

4. **Force pod restart to re-inject instrumentation**:
   ```bash
   kubectl rollout restart deployment/node-service -n node-service
   kubectl rollout restart deployment/demo-service -n demo-service
   ```

## 📚 Additional Resources

- [Flux Documentation](https://fluxcd.io/docs/)
- [OpenTelemetry Documentation](https://opentelemetry.io/docs/)
- [SOPS Documentation](https://github.com/mozilla/sops)
- [Spring Boot Documentation](https://spring.io/projects/spring-boot)

## 🏗️ Infrastructure Components

### Jaeger Tracing

The setup includes Jaeger for distributed tracing:
- **All-in-One Mode**: Single deployment for development/testing
- **In-Memory Storage**: Suitable for non-production environments
- **OTLP Support**: Receives traces via OTLP protocol from OpenTelemetry Collector
- **UI Access**: Web interface for trace analysis and visualization

### OpenTelemetry Collector Configuration

The collector is configured with:
- **Receivers**: OTLP, Jaeger, Zipkin, OpenCensus protocols
- **Processors**: Batch processing, memory limiting, K8s attributes, tail sampling
- **Exporters**: OTLP to Jaeger, logging for debugging
- **Pipelines**: Separate pipelines for traces, metrics, and logs

### Gradle Build Configuration

The Java service uses Gradle 8.5+ with:
- **Spring Boot 3.x**: Latest Spring Boot framework
- **Java 21 LTS**: Long-term support Java version
- **Spring Boot Gradle Plugin**: For building executable JARs and Docker images
- **Buildpack Support**: Cloud-native container image building
- **Dependency Management**: Centralized version management

## 📄 License

MIT License - See LICENSE file for details