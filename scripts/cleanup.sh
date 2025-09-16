#!/bin/bash

set -e

echo "🧹 Flux Demo Cleanup Script"
echo "============================"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Get the script directory and project root
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
PROJECT_ROOT="$( cd "$SCRIPT_DIR/.." && pwd )"

echo -e "\n${YELLOW}⚠️  This will delete all Flux Demo resources!${NC}"
read -p "Are you sure you want to continue? (y/N) " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo -e "${RED}Cleanup cancelled${NC}"
    exit 1
fi

# Delete applications
echo -e "\n🗑️  Deleting applications..."
kubectl delete -f "$PROJECT_ROOT/flux/clusters/minikube/apps.yaml" --ignore-not-found=true

# Delete infrastructure
echo -e "\n🗑️  Deleting infrastructure..."
kubectl delete -f "$PROJECT_ROOT/flux/clusters/minikube/infrastructure.yaml" --ignore-not-found=true

# Delete Flux system
echo -e "\n🗑️  Deleting Flux system..."
flux uninstall --silent

# Delete namespaces
echo -e "\n🗑️  Deleting namespaces..."
kubectl delete namespace demo-service --ignore-not-found=true
kubectl delete namespace opentelemetry --ignore-not-found=true

# Clean up local files
echo -e "\n🗑️  Cleaning up local files..."
if [ -f "$PROJECT_ROOT/age.key" ]; then
    echo -e "${YELLOW}Found age.key file${NC}"
    read -p "Delete age.key? (y/N) " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        rm -f "$PROJECT_ROOT/age.key"
        echo -e "${GREEN}✅ age.key deleted${NC}"
    fi
fi

echo -e "\n${GREEN}✅ Cleanup complete!${NC}"

# Ask about Minikube
echo -e "\n${YELLOW}Minikube is still running${NC}"
read -p "Stop Minikube? (y/N) " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    minikube stop
    echo -e "${GREEN}✅ Minikube stopped${NC}"
fi