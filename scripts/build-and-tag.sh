#!/bin/bash

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Get the script directory and project root
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
PROJECT_ROOT="$( cd "$SCRIPT_DIR/.." && pwd )"

# Default values
VERSION=""
REGISTRY="docker.io"
REPOSITORY="library"
IMAGE_NAME="demo-service"
PUSH=false

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        -v|--version)
            VERSION="$2"
            shift 2
            ;;
        -r|--registry)
            REGISTRY="$2"
            shift 2
            ;;
        -o|--repository)
            REPOSITORY="$2"
            shift 2
            ;;
        -p|--push)
            PUSH=true
            shift
            ;;
        -h|--help)
            echo "Usage: $0 [OPTIONS]"
            echo ""
            echo "Options:"
            echo "  -v, --version VERSION     Version tag (e.g., 1.0.0, dev-feature)"
            echo "  -r, --registry REGISTRY   Container registry (default: docker.io)"
            echo "  -o, --repository REPO     Repository/organization (default: library)"
            echo "  -p, --push               Push image to registry"
            echo "  -h, --help               Show this help message"
            echo ""
            echo "Examples:"
            echo "  # Build and tag locally"
            echo "  $0 -v 1.0.0"
            echo ""
            echo "  # Build and push to Docker Hub"
            echo "  $0 -v 1.0.0 -o myusername -p"
            echo ""
            echo "  # Build and push to GitHub Container Registry"
            echo "  $0 -v 1.0.0 -r ghcr.io -o myorg -p"
            exit 0
            ;;
        *)
            echo -e "${RED}Unknown option: $1${NC}"
            exit 1
            ;;
    esac
done

# Validate version
if [ -z "$VERSION" ]; then
    # Generate version from git
    GIT_COMMIT=$(git rev-parse --short HEAD 2>/dev/null || echo "unknown")
    GIT_BRANCH=$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo "unknown")

    if [ "$GIT_BRANCH" = "main" ] || [ "$GIT_BRANCH" = "master" ]; then
        VERSION="latest"
    else
        VERSION="dev-${GIT_BRANCH}-${GIT_COMMIT}"
    fi

    echo -e "${YELLOW}No version specified, using: ${VERSION}${NC}"
fi

# Build full image name
if [ "$REGISTRY" = "docker.io" ] && [ "$REPOSITORY" = "library" ]; then
    FULL_IMAGE="${IMAGE_NAME}"
else
    FULL_IMAGE="${REGISTRY}/${REPOSITORY}/${IMAGE_NAME}"
fi

echo -e "${BLUE}🐳 Building Docker Image${NC}"
echo "================================"
echo -e "Registry:    ${REGISTRY}"
echo -e "Repository:  ${REPOSITORY}"
echo -e "Image:       ${IMAGE_NAME}"
echo -e "Version:     ${VERSION}"
echo -e "Full tag:    ${FULL_IMAGE}:${VERSION}"
echo ""

# Check if using Minikube Docker daemon
if minikube status 2>/dev/null | grep -q "Running"; then
    echo -e "${YELLOW}Detected Minikube - using Minikube Docker daemon${NC}"
    eval $(minikube docker-env)
fi

# Build the application
echo -e "\n${BLUE}📦 Building Java application...${NC}"
cd "$PROJECT_ROOT/java-service"
./gradlew build
echo -e "${GREEN}✅ Java build complete${NC}"

# Build Docker image
echo -e "\n${BLUE}🔨 Building Docker image...${NC}"
docker build -t "${IMAGE_NAME}:${VERSION}" .

# Tag for registry if not local
if [ "$REGISTRY" != "docker.io" ] || [ "$REPOSITORY" != "library" ]; then
    docker tag "${IMAGE_NAME}:${VERSION}" "${FULL_IMAGE}:${VERSION}"
fi

# Also tag as latest if this is a release version (semver format)
if [[ $VERSION =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    echo -e "\n${BLUE}🏷️  Tagging as latest...${NC}"
    docker tag "${IMAGE_NAME}:${VERSION}" "${IMAGE_NAME}:latest"

    if [ "$REGISTRY" != "docker.io" ] || [ "$REPOSITORY" != "library" ]; then
        docker tag "${IMAGE_NAME}:${VERSION}" "${FULL_IMAGE}:latest"
    fi
fi

echo -e "${GREEN}✅ Docker image built and tagged${NC}"

# List the created images
echo -e "\n${BLUE}📋 Created images:${NC}"
docker images | grep "${IMAGE_NAME}" | head -5

# Push if requested
if [ "$PUSH" = true ]; then
    echo -e "\n${BLUE}📤 Pushing image to registry...${NC}"

    # Check if logged in
    if ! docker push "${FULL_IMAGE}:${VERSION}" 2>/dev/null; then
        echo -e "${YELLOW}⚠️  Push failed. Please login to ${REGISTRY} first:${NC}"
        echo -e "${YELLOW}   docker login ${REGISTRY}${NC}"
        exit 1
    fi

    # Push latest tag if it exists
    if [[ $VERSION =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
        docker push "${FULL_IMAGE}:latest"
    fi

    echo -e "${GREEN}✅ Image pushed to registry${NC}"
fi

echo -e "\n${GREEN}🎉 Done!${NC}"

# Show Flux image automation hint
if [[ $VERSION =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    echo -e "\n${BLUE}💡 Flux Image Automation Tip:${NC}"
    echo -e "The new version ${VERSION} will be automatically detected by Flux"
    echo -e "if you have configured ImageRepository and ImagePolicy resources."
    echo -e "\nTo trigger immediate reconciliation:"
    echo -e "${YELLOW}flux reconcile image repository demo-service${NC}"
    echo -e "${YELLOW}flux reconcile image update demo-service${NC}"
fi