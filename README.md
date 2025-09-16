# Flux Demo - Spring Boot Service with Kubernetes and OpenTelemetry

This repository demonstrates a production-ready setup for deploying a Java Spring Boot service to Kubernetes using Flux CD, with complete observability through OpenTelemetry.

## 📁 Repository Structure

```
flux-demo/
├── java-service/           # Java Spring Boot application
│   ├── src/               # Source code
│   ├── pom.xml           # Maven configuration
│   └── Dockerfile        # Container image definition
│
├── flux/                  # All Kubernetes and Flux configurations
│   ├── apps/             # Application deployments
│   │   └── demo-service/ # Demo service manifests
│   │       ├── base/     # Base Kustomization
│   │       └── overlays/ # Environment-specific configs
│   │
│   ├── infrastructure/   # Infrastructure components
│   │   ├── opentelemetry/ # OpenTelemetry operator and collector
│   │   └── sources/      # Helm repositories and sources
│   │
│   └── clusters/         # Cluster-specific configurations
│       └── minikube/     # Minikube cluster config
│
└── scripts/              # Setup and utility scripts
```

## 🚀 Features

- **GitOps with Flux CD**: Automated deployments and configuration management
- **Image Automation**:
  - Automatic image scanning with Image Reflector Controller
  - Policy-based image selection (semver, regex, alphabetical)
  - Automated Git commits for image updates
  - Support for multiple container registries
- **Secret Management with SOPS**: Encrypted secrets in Git
- **OpenTelemetry Integration**:
  - Auto-instrumentation for Java applications
  - Distributed tracing
  - Metrics collection with Prometheus
  - Centralized collector for telemetry data
- **ConfigMaps**: Environment-specific configuration
- **Multi-environment Support**: Dev/Prod overlays with Kustomize
- **Health Checks**: Liveness and readiness probes
- **Security**: Non-root containers, security contexts

## 🛠 Prerequisites

- Docker
- Minikube
- kubectl
- Flux CLI
- age (for SOPS encryption)
- Gradle (for local development)
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

### 2. Build the Java Application

```bash
cd java-service
./gradlew build
docker build -t demo-service:latest .
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

## 📊 Observability

### OpenTelemetry

The setup includes:
- **OpenTelemetry Operator**: Manages instrumentation and collectors
- **Auto-instrumentation**: Java agent automatically injected
- **Collector**: Centralized telemetry processing

### Accessing Metrics

```bash
# Port-forward to Prometheus endpoint
kubectl port-forward -n demo-service svc/demo-service 8080:8080

# Access metrics
curl http://localhost:8080/actuator/prometheus
```

### Accessing Traces

```bash
# Port-forward to collector
kubectl port-forward -n opentelemetry deployment/otel-collector 55679:55679

# View zpages
open http://localhost:55679/debug/tracez
```

## 🧪 Testing the Application

### Local Testing

```bash
# Port-forward the service
kubectl port-forward -n demo-service svc/demo-service 8080:8080

# Test endpoints
curl http://localhost:8080/api/health
curl http://localhost:8080/api/info
curl http://localhost:8080/api/greeting/World
```

### Monitoring Flux

```bash
# Check Flux status
flux get all

# Check kustomizations
flux get kustomizations

# Check sources
flux get sources git

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
kubectl get pods -n demo-service
kubectl describe pod -n demo-service <pod-name>
kubectl logs -n demo-service <pod-name>
```

### Check Flux Events
```bash
kubectl get events -n flux-system --sort-by='.lastTimestamp'
```

### Verify OpenTelemetry
```bash
kubectl get instrumentation -n demo-service
kubectl logs -n opentelemetry deployment/opentelemetry-operator
```

## 📚 Additional Resources

- [Flux Documentation](https://fluxcd.io/docs/)
- [OpenTelemetry Documentation](https://opentelemetry.io/docs/)
- [SOPS Documentation](https://github.com/mozilla/sops)
- [Spring Boot Documentation](https://spring.io/projects/spring-boot)

## 📄 License

MIT License - See LICENSE file for details