#!/bin/bash

set -e

echo "🚀 Complete Flux Setup with Authentication"
echo "==========================================="

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
MAGENTA='\033[0;35m'
NC='\033[0m' # No Color

# Configuration
CONTEXT="${FLUX_CONTEXT:-minikube}"
GITHUB_USER="${GITHUB_USER:-}"
GITHUB_REPO="${GITHUB_REPO:-flux-demo}"
GITHUB_BRANCH="${GITHUB_BRANCH:-main}"
PROJECT_ROOT="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

# Function to print section headers
print_section() {
    echo ""
    echo -e "${MAGENTA}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${MAGENTA}  $1${NC}"
    echo -e "${MAGENTA}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
}

# ============================================================================
# STEP 1: Prerequisites Check
# ============================================================================
print_section "📋 Checking Prerequisites"

# Check for required commands
check_command() {
    if ! command -v $1 &> /dev/null; then
        echo -e "${RED}❌ $1 not found. Please install it first.${NC}"
        if [ "$1" == "flux" ]; then
            echo -e "${YELLOW}   Install with: brew install fluxcd/tap/flux${NC}"
        elif [ "$1" == "age" ]; then
            echo -e "${YELLOW}   Install with: brew install age${NC}"
        fi
        exit 1
    else
        echo -e "${GREEN}✅ $1 found${NC}"
    fi
}

check_command kubectl
check_command flux
check_command age
check_command git

# Verify kubectl context
if ! kubectl config get-contexts ${CONTEXT} &> /dev/null; then
    echo -e "${RED}❌ Kubernetes context '${CONTEXT}' not found.${NC}"
    echo "Available contexts:"
    kubectl config get-contexts
    exit 1
fi

echo -e "${GREEN}✅ Using context: ${CONTEXT}${NC}"
kubectl config use-context ${CONTEXT}

# ============================================================================
# STEP 2: GitHub Configuration
# ============================================================================
print_section "🔧 GitHub Configuration"

# Get GitHub username if not set
if [ -z "$GITHUB_USER" ]; then
    read -p "Enter your GitHub username: " GITHUB_USER
    if [ -z "$GITHUB_USER" ]; then
        echo -e "${RED}❌ GitHub username cannot be empty${NC}"
        exit 1
    fi
fi

# Confirm repository details
echo -e "${CYAN}Repository: github.com/${GITHUB_USER}/${GITHUB_REPO}${NC}"
echo -e "${CYAN}Branch: ${GITHUB_BRANCH}${NC}"
read -p "Is this correct? (Y/n) " -n 1 -r
echo
if [[ $REPLY =~ ^[Nn]$ ]]; then
    read -p "Enter repository name: " GITHUB_REPO
    read -p "Enter branch name (default: main): " GITHUB_BRANCH
    GITHUB_BRANCH=${GITHUB_BRANCH:-main}
fi

# Check for GitHub token
if [ -z "$GITHUB_TOKEN" ]; then
    echo ""
    echo -e "${YELLOW}📝 GitHub Personal Access Token required${NC}"
    echo "Required permissions:"
    echo "  • repo (full control)"
    echo "  • write:packages (for GHCR - optional)"
    echo ""
    echo -e "${BLUE}Create at: https://github.com/settings/tokens/new${NC}"
    echo ""
    echo -e "${YELLOW}Enter your GitHub Personal Access Token:${NC}"
    read -s GITHUB_TOKEN
    echo ""

    if [ -z "$GITHUB_TOKEN" ]; then
        echo -e "${RED}❌ GitHub token cannot be empty${NC}"
        exit 1
    fi

    # Export for flux bootstrap
    export GITHUB_TOKEN
fi

# ============================================================================
# STEP 3: Create Namespace and Secrets
# ============================================================================
print_section "🔐 Setting up Namespace and Secrets"

# Create flux-system namespace
if ! kubectl get namespace flux-system &> /dev/null; then
    echo -e "${BLUE}Creating flux-system namespace...${NC}"
    kubectl create namespace flux-system
    echo -e "${GREEN}✅ Namespace created${NC}"
else
    echo -e "${GREEN}✅ Namespace already exists${NC}"
fi

# Generate age key if not exists
if [ ! -f "$PROJECT_ROOT/age.key" ]; then
    echo -e "${BLUE}Generating age encryption key...${NC}"
    age-keygen -o "$PROJECT_ROOT/age.key"
    echo -e "${GREEN}✅ Age key generated${NC}"
else
    echo -e "${GREEN}✅ Age key already exists${NC}"
fi

# Create SOPS secret
echo -e "${BLUE}Creating SOPS secret...${NC}"
cat "$PROJECT_ROOT/age.key" |
kubectl create secret generic sops-age \
    --namespace=flux-system \
    --from-file=age.agekey=/dev/stdin \
    --dry-run=client -o yaml | kubectl apply -f -
echo -e "${GREEN}✅ SOPS secret created${NC}"

# Create Git authentication secret
echo -e "${BLUE}Creating Git authentication secret...${NC}"
kubectl create secret generic flux-system \
    --namespace=flux-system \
    --from-literal=username="$GITHUB_USER" \
    --from-literal=password="$GITHUB_TOKEN" \
    --dry-run=client -o yaml | kubectl apply -f -
echo -e "${GREEN}✅ Git authentication secret created${NC}"

# Create GitHub Container Registry secret in flux-system
echo -e "${BLUE}Creating GHCR secret for image pulling...${NC}"
kubectl create secret docker-registry ghcr-secret \
    --namespace=flux-system \
    --docker-server=ghcr.io \
    --docker-username="$GITHUB_USER" \
    --docker-password="$GITHUB_TOKEN" \
    --docker-email="${GITHUB_USER}@users.noreply.github.com" \
    --dry-run=client -o yaml | kubectl apply -f -
echo -e "${GREEN}✅ GHCR secret created in flux-system namespace${NC}"

# ============================================================================
# STEP 4: Bootstrap Flux
# ============================================================================
print_section "🚀 Bootstrapping Flux"

echo "This will:"
echo "  1. Install Flux components in your cluster"
echo "  2. Create/update the Git repository"
echo "  3. Configure Flux to sync from Git"
echo ""
read -p "Continue with Flux bootstrap? (Y/n) " -n 1 -r
echo
if [[ $REPLY =~ ^[Nn]$ ]]; then
    echo -e "${YELLOW}⚠️  Skipping Flux bootstrap${NC}"
    echo "You can run it manually later with:"
    echo "flux bootstrap github \\"
    echo "  --owner=${GITHUB_USER} \\"
    echo "  --repository=${GITHUB_REPO} \\"
    echo "  --branch=${GITHUB_BRANCH} \\"
    echo "  --path=flux/clusters/${CONTEXT} \\"
    echo "  --components-extra=image-reflector-controller,image-automation-controller \\"
    echo "  --read-write-key \\"
    echo "  --personal"
    exit 0
fi

# Run Flux bootstrap
flux bootstrap github \
    --context="${CONTEXT}" \
    --owner="${GITHUB_USER}" \
    --repository="${GITHUB_REPO}" \
    --branch="${GITHUB_BRANCH}" \
    --components-extra=image-reflector-controller,image-automation-controller \
    --path=flux/clusters/${CONTEXT} \
    --read-write-key \
    --personal

# ============================================================================
# STEP 5: Post-Bootstrap Configuration
# ============================================================================
print_section "🔧 Post-Bootstrap Configuration"

echo -e "${BLUE}Waiting for Flux to create demo-service namespace...${NC}"
for i in {1..30}; do
    if kubectl get namespace demo-service &> /dev/null; then
        echo -e "${GREEN}✅ demo-service namespace found${NC}"
        break
    fi
    echo -n "."
    sleep 2
done

# Replicate GHCR secret to demo-service namespace
if kubectl get namespace demo-service &> /dev/null; then
    echo -e "${BLUE}Replicating GHCR secret to demo-service namespace...${NC}"
    kubectl create secret docker-registry ghcr-secret \
        --namespace=demo-service \
        --docker-server=ghcr.io \
        --docker-username="$GITHUB_USER" \
        --docker-password="$GITHUB_TOKEN" \
        --docker-email="${GITHUB_USER}@users.noreply.github.com" \
        --dry-run=client -o yaml | kubectl apply -f -
    echo -e "${GREEN}✅ GHCR secret replicated to demo-service namespace${NC}"
else
    echo -e "${YELLOW}⚠️  demo-service namespace not found. You may need to manually create the GHCR secret later.${NC}"
    echo -e "${YELLOW}Run: kubectl create secret docker-registry ghcr-secret --namespace=demo-service --docker-server=ghcr.io --docker-username=<USERNAME> --docker-password=<TOKEN>${NC}"
fi

# ============================================================================
# STEP 6: Verify Installation
# ============================================================================
print_section "✅ Verifying Installation"

echo -e "${BLUE}Checking Flux components...${NC}"
flux check

echo -e "\n${BLUE}Git repository status:${NC}"
flux get sources git

echo -e "\n${BLUE}Kustomization status:${NC}"
flux get kustomizations

# ============================================================================
# STEP 7: Post-Setup Instructions
# ============================================================================
print_section "🎉 Setup Complete!"

echo -e "${GREEN}Flux has been successfully installed and configured!${NC}"
echo ""
echo -e "${CYAN}Repository:${NC} github.com/${GITHUB_USER}/${GITHUB_REPO}"
echo -e "${CYAN}Path:${NC} flux/clusters/${CONTEXT}"
echo -e "${CYAN}Components:${NC}"
echo "  • Source Controller"
echo "  • Kustomize Controller"
echo "  • Helm Controller"
echo "  • Notification Controller"
echo "  • Image Reflector Controller ✨"
echo "  • Image Automation Controller ✨"
echo ""
echo -e "${YELLOW}📝 Next Steps:${NC}"
echo ""
echo "1. Your repository has been updated with Flux manifests"
echo "2. Add your application manifests to the repository"
echo "3. Flux will automatically sync and deploy them"
echo ""
echo -e "${CYAN}Useful Commands:${NC}"
echo "  flux get all                    # Show all resources"
echo "  flux logs --follow              # Watch Flux logs"
echo "  flux reconcile source git flux-system  # Force sync"
echo ""
echo -e "${CYAN}For Image Automation:${NC}"
echo "  flux get image repository        # List image repositories"
echo "  flux get image policy           # List image policies"
echo "  flux get image update           # List image updates"
echo ""
echo -e "${BLUE}Documentation:${NC}"
echo "  • Flux: https://fluxcd.io/docs/"
echo "  • Image Automation: https://fluxcd.io/docs/guides/image-update/"

# Save configuration for future reference
echo -e "\n${BLUE}Saving configuration...${NC}"
cat > "$PROJECT_ROOT/.flux-config" << EOF
# Flux Configuration
# Generated: $(date)
GITHUB_USER=${GITHUB_USER}
GITHUB_REPO=${GITHUB_REPO}
GITHUB_BRANCH=${GITHUB_BRANCH}
CONTEXT=${CONTEXT}
PATH=flux/clusters/${CONTEXT}
EOF

echo -e "${GREEN}✅ Configuration saved to .flux-config${NC}"