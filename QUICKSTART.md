# 🚀 Quick Start Guide

## After Cloning/Forking This Repository

### 1. Update Git Repository URL

Edit `flux/clusters/minikube/flux-system/gotk-sync.yaml` and replace `<your-username>` with your GitHub username:

```yaml
url: https://github.com/YOUR-USERNAME/flux-demo
```

### 2. Run Setup

```bash
# Start Minikube and install everything
./scripts/setup.sh
```

### 3. Verify Flux is Working

```bash
# Check if Flux can access your Git repository
flux get sources git

# Should show:
# NAME         READY   MESSAGE
# flux-system  True    stored artifact for revision 'main@sha1:...'
```

## Troubleshooting

### If Flux Cannot Access Git Repository

**Option A: Use Public Repository**
- Make your GitHub repository public
- No additional configuration needed

**Option B: Use Private Repository**
1. Create a GitHub Personal Access Token with repo permissions
2. Create the secret:
```bash
flux create secret git flux-system \
  --url=https://github.com/YOUR-USERNAME/flux-demo \
  --username=YOUR-USERNAME \
  --password=YOUR-GITHUB-TOKEN
```

**Option C: Use Local Development Mode**
```bash
# This mounts your local directory into Minikube
./scripts/setup-local-git.sh
```

### Check Flux Status

```bash
# See all Flux resources
flux get all

# Watch Flux logs
flux logs --follow

# Force reconciliation
make flux-sync
```

## Common Issues

### "Source artifact not found"
- Flux cannot access the Git repository
- Check the URL in gotk-sync.yaml
- Ensure the repository exists and is accessible

### "Kustomization failed"
```bash
# Check the specific error
kubectl describe kustomization -n flux-system demo-service

# Check events
kubectl get events -n flux-system --sort-by='.lastTimestamp'
```

### Image automation not working
```bash
# Check if image controllers are installed
flux check --components-extra=image-reflector-controller,image-automation-controller

# Reinstall with image automation
flux install --components-extra=image-reflector-controller,image-automation-controller
```