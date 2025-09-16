.PHONY: help build setup clean port-forward test

help: ## Show this help message
	@echo 'Usage: make [target]'
	@echo ''
	@echo 'Available targets:'
	@awk 'BEGIN {FS = ":.*##"; printf "\033[36m\033[0m"} /^[a-zA-Z_-]+:.*?##/ { printf "  \033[36m%-15s\033[0m %s\n", $$1, $$2 } /^##@/ { printf "\n\033[1m%s\033[0m\n", substr($$0, 5) } ' $(MAKEFILE_LIST)

##@ Development

build: ## Build the Java application Docker image
	@echo "🔨 Building Java application..."
	@cd java-service && docker build -t demo-service:latest .
	@echo "✅ Build complete"

build-version: ## Build and tag with version (use VERSION=x.x.x)
	@echo "🔨 Building Java application version $(VERSION)..."
	@./scripts/build-and-tag.sh -v $(VERSION)

build-push: ## Build and push to registry (use VERSION=x.x.x REGISTRY=... REPO=...)
	@echo "🔨 Building and pushing version $(VERSION)..."
	@./scripts/build-and-tag.sh -v $(VERSION) -r $(REGISTRY) -o $(REPO) -p

run-local: ## Run the Java application locally
	@echo "🚀 Running Java application locally..."
	@cd java-service && ./gradlew bootRun

test: ## Run Java application tests
	@echo "🧪 Running tests..."
	@cd java-service && ./gradlew test

##@ Kubernetes

setup: ## Set up the entire stack on Minikube
	@echo "🚀 Setting up Flux Demo..."
	@./scripts/setup.sh

clean: ## Clean up all resources
	@echo "🧹 Cleaning up..."
	@./scripts/cleanup.sh

port-forward: ## Start port forwarding for all services
	@echo "🔌 Starting port forwards..."
	@./scripts/port-forward.sh

##@ Flux Operations

flux-check: ## Check Flux status
	@flux check

flux-sync: ## Force Flux reconciliation
	@echo "🔄 Forcing Flux sync..."
	@flux reconcile source git flux-system
	@flux reconcile kustomization infrastructure-sources
	@flux reconcile kustomization infrastructure-opentelemetry
	@flux reconcile kustomization demo-service
	@flux reconcile image repository demo-service || true
	@flux reconcile image update demo-service || true

flux-logs: ## Show Flux logs
	@flux logs --follow

##@ Monitoring

status: ## Show deployment status
	@echo "📊 Cluster Status"
	@echo "=================="
	@echo "\n🔸 Flux Components:"
	@flux get all
	@echo "\n🔸 Image Automation:"
	@flux get image repository || true
	@flux get image policy || true
	@flux get image update || true
	@echo "\n🔸 Pods:"
	@kubectl get pods -A | grep -E "(demo-service|opentelemetry|flux-system)" || true
	@echo "\n🔸 Services:"
	@kubectl get svc -A | grep -E "(demo-service|opentelemetry)" || true

logs-app: ## Show application logs
	@kubectl logs -n demo-service -l app.kubernetes.io/name=demo-service --tail=100 -f

logs-otel: ## Show OpenTelemetry logs
	@kubectl logs -n opentelemetry deployment/otel-collector --tail=100 -f

##@ SOPS

encrypt-secrets: ## Encrypt secrets with SOPS
	@echo "🔐 Encrypting secrets..."
	@sops -e -i flux/apps/demo-service/base/secret.enc.yaml

decrypt-secrets: ## Decrypt secrets with SOPS
	@echo "🔓 Decrypting secrets..."
	@sops -d flux/apps/demo-service/base/secret.enc.yaml

generate-age-key: ## Generate new age key for SOPS
	@echo "🔑 Generating age key..."
	@age-keygen -o age.key
	@echo "✅ Key generated at age.key"