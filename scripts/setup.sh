#!/bin/bash

set -e

echo "🚀 Flux Demo Setup Script"
echo "========================="

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Get the script directory and project root
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
PROJECT_ROOT="$( cd "$SCRIPT_DIR/.." && pwd )"

# Check prerequisites
check_command() {
    if ! command -v $1 &> /dev/null; then
        echo -e "${RED}❌ $1 is not installed${NC}"
        exit 1
    else
        echo -e "${GREEN}✅ $1 is installed${NC}"
    fi
}

echo -e "\n📋 Checking prerequisites..."
check_command docker
check_command kubectl
check_command minikube
check_command flux
check_command age

# Start Minikube
echo -e "\n🔧 Starting Minikube..."
if minikube status | grep -q "Running"; then
    echo -e "${GREEN}✅ Minikube is already running${NC}"
else
    minikube start --memory 4096 --cpus 2
    echo -e "${GREEN}✅ Minikube started${NC}"
fi

# Configure Docker environment
echo -e "\n🐳 Configuring Docker environment..."
eval $(minikube docker-env)
echo -e "${GREEN}✅ Docker environment configured${NC}"

# Build Java application
echo -e "\n🔨 Building Java application..."
cd "$PROJECT_ROOT/java-service"
docker build -t demo-service:latest .
echo -e "${GREEN}✅ Java application built${NC}"

# Check Flux prerequisites
echo -e "\n🔍 Checking Flux prerequisites..."
flux check --pre

# Install Flux with image automation
echo -e "\n📦 Installing Flux with image automation..."
flux install \
  --components-extra=image-reflector-controller,image-automation-controller
echo -e "${GREEN}✅ Flux installed with image automation${NC}"

# Generate SOPS key if not exists
echo -e "\n🔐 Setting up SOPS encryption..."
if [ ! -f "$PROJECT_ROOT/age.key" ]; then
    age-keygen -o "$PROJECT_ROOT/age.key"
    echo -e "${GREEN}✅ Age key generated${NC}"
else
    echo -e "${YELLOW}⚠️  Age key already exists${NC}"
fi

# Create SOPS secret in cluster
kubectl create namespace flux-system --dry-run=client -o yaml | kubectl apply -f -
kubectl create secret generic sops-age \
  --namespace=flux-system \
  --from-file=age.agekey="$PROJECT_ROOT/age.key" \
  --dry-run=client -o yaml | kubectl apply -f -
echo -e "${GREEN}✅ SOPS secret created${NC}"

# Apply Flux configurations
echo -e "\n🚀 Applying Flux configurations..."
kubectl apply -k "$PROJECT_ROOT/flux/clusters/minikube/flux-system/"
echo -e "${GREEN}✅ Flux system components applied${NC}"

# Wait for Flux to be ready
echo -e "\n⏳ Waiting for Flux to be ready..."
flux check

# Setup GitHub authentication if needed
echo -e "\n🔐 Setting up GitHub authentication..."
echo -e "${YELLOW}Flux needs GitHub authentication to:${NC}"
echo "  - Read from private repositories"
echo "  - Write image updates back to Git"
echo ""
read -p "Do you want to set up GitHub authentication now? (y/N) " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    "$SCRIPT_DIR/setup-github-auth.sh"
else
    echo -e "${YELLOW}⚠️  Skipping GitHub auth setup${NC}"
    echo -e "${YELLOW}You can run './scripts/setup-github-auth.sh' later${NC}"
fi

# Apply infrastructure
echo -e "\n🏗️  Deploying infrastructure..."
kubectl apply -f "$PROJECT_ROOT/flux/clusters/minikube/infrastructure.yaml"
echo -e "${GREEN}✅ Infrastructure deployed${NC}"

# Apply applications
echo -e "\n📱 Deploying applications..."
kubectl apply -f "$PROJECT_ROOT/flux/clusters/minikube/apps.yaml"
echo -e "${GREEN}✅ Applications deployed${NC}"

# Wait for deployments
echo -e "\n⏳ Waiting for deployments to be ready..."
kubectl wait --for=condition=available --timeout=300s \
  deployment/opentelemetry-operator -n opentelemetry || true
kubectl wait --for=condition=available --timeout=300s \
  deployment/demo-service -n demo-service || true

# Show status
echo -e "\n📊 Deployment Status:"
echo -e "${YELLOW}Flux Status:${NC}"
flux get all

echo -e "\n${YELLOW}Pods:${NC}"
kubectl get pods -A | grep -E "(demo-service|opentelemetry|flux-system)"

echo -e "\n${GREEN}🎉 Setup complete!${NC}"
echo -e "\nYou can access the service with:"
echo -e "${YELLOW}kubectl port-forward -n demo-service svc/demo-service 8080:8080${NC}"
echo -e "\nThen visit:"
echo -e "${YELLOW}http://localhost:8080/api/health${NC}"
echo -e "${YELLOW}http://localhost:8080/api/info${NC}"
echo -e "${YELLOW}http://localhost:8080/api/greeting/World${NC}"