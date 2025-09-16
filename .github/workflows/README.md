# GitHub Actions CI/CD Configuration

## Overview

This repository uses GitHub Actions to automatically build and push Docker images to GitHub Container Registry (ghcr.io).

## Workflow: Build and Push Java Application

**File**: `.github/workflows/build-java.yml`

### Triggers

- **Push to main branch** - when changes are made to `java-service/**`
- **Pull requests to main** - builds but doesn't push images
- **Manual trigger** - via workflow_dispatch

### What it does

1. **Builds the Java application** with Gradle
2. **Runs tests** to ensure quality
3. **Builds Docker image** and pushes to `ghcr.io/petrmac/flux-demo/demo-service`
4. **Tags images** with:
   - `latest` (for main branch)
   - Version timestamp (e.g., `20240116.143022-abc12345`)
   - Git SHA
   - Branch name

### Required Secrets

No additional secrets needed! The workflow uses the built-in `GITHUB_TOKEN` which has permissions to push to GitHub Container Registry.

### Container Registry

Images are published to:
```
ghcr.io/petrmac/flux-demo/demo-service
```

### Setting Up Package Visibility

1. After the first image is pushed, go to the package settings:
   - Navigate to: https://github.com/petrmac?tab=packages
   - Click on the `demo-service` package
   - Click "Package settings"
   - Change visibility to "Public" if desired
   - Link the repository to the package

### Flux Integration

The Flux ImageRepository is configured to scan:
```yaml
spec:
  image: ghcr.io/petrmac/flux-demo/demo-service
  interval: 5m
```

Flux will automatically detect new images and can update deployments based on configured policies.

### Testing the Workflow

1. Make a change to any file in `java-service/`
2. Commit and push to main branch
3. Check Actions tab for build progress
4. Once complete, the new image will be available at ghcr.io

### Manual Trigger

You can manually trigger the workflow:
1. Go to Actions tab
2. Select "Build and Push Java Application"
3. Click "Run workflow"
4. Select branch and click "Run workflow"

## Troubleshooting

### Build Failures

Check the Actions tab for detailed logs. Common issues:
- Gradle build failures - check Java version compatibility
- Docker build failures - check Dockerfile syntax
- Permission issues - ensure GITHUB_TOKEN has package write permissions

### Image Not Updating in Cluster

1. Check Flux image repository status:
   ```bash
   flux get image repository demo-service
   ```

2. Force a scan:
   ```bash
   flux reconcile image repository demo-service
   ```

3. Check image policies:
   ```bash
   flux get image policy
   ```