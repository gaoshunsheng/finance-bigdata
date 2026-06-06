#!/usr/bin/env bash
# ============================================================================
# deploy.sh — Deploy Finance Bigdata Platform to Kubernetes
# ============================================================================
# Usage:
#   ./deploy.sh                                    # Default: latest, no registry
#   REGISTRY=harbor.example.com/finance ./deploy.sh
#   REGISTRY=harbor.example.com/finance TAG=v1.0.0 ./deploy.sh
#   ./deploy.sh --dry-run                          # Print manifests without applying
# ============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REGISTRY="${REGISTRY:-}"
TAG="${TAG:-latest}"
NAMESPACE="finance-platform"
DRY_RUN=false

# Parse args
for arg in "$@"; do
  case "$arg" in
    --dry-run) DRY_RUN=true ;;
    -n|--namespace) shift; NAMESPACE="${1:-$NAMESPACE}" ;;
    -h|--help)
      echo "Usage: $0 [OPTIONS]"
      echo ""
      echo "Options:"
      echo "  --dry-run          Print rendered manifests without applying"
      echo "  -n, --namespace    Target namespace (default: finance-platform)"
      echo ""
      echo "Environment variables:"
      echo "  REGISTRY           Container registry prefix (e.g., harbor.example.com/finance)"
      echo "  TAG                Image tag (default: latest)"
      exit 0
      ;;
  esac
done

echo "============================================"
echo " Finance Bigdata Platform — K8s Deploy"
echo "============================================"
echo " Registry : ${REGISTRY:-<none, local build>}"
echo " Tag      : ${TAG}"
echo " Namespace: ${NAMESPACE}"
echo " Dry run  : ${DRY_RUN}"
echo "============================================"

# Build kustomize command
BUILD_CMD="kustomize build ${SCRIPT_DIR}"

# Apply registry overrides if set
if [ -n "$REGISTRY" ]; then
  BUILD_CMD="kustomize build ${SCRIPT_DIR} | \
    sed 's|finance-bigdata/|${REGISTRY}/|g'"
fi

# Apply tag override
BUILD_CMD="${BUILD_CMD} | sed 's|:latest|:${TAG}|g'"

if [ "$DRY_RUN" = true ]; then
  echo ""
  echo "--- Dry run: rendering manifests ---"
  eval "$BUILD_CMD"
  exit 0
fi

# Ensure namespace exists
kubectl get namespace "$NAMESPACE" >/dev/null 2>&1 || \
  kubectl apply -f "${SCRIPT_DIR}/namespace.yaml"

# Apply manifests
echo ""
echo "Applying manifests..."
eval "$BUILD_CMD" | kubectl apply -f -

echo ""
echo "Waiting for rollouts..."

# Wait for application deployments
for deploy in decision-server decision-admin model-platform data-service; do
  echo "  → $deploy"
  kubectl rollout status deployment/"$deploy" -n "$NAMESPACE" --timeout=120s 2>/dev/null || \
    echo "  ⚠ $deploy rollout not complete (may still be starting)"
done

echo ""
echo "✅ Deploy complete!"
echo ""
echo "Verify:"
echo "  kubectl get pods -n $NAMESPACE"
echo "  kubectl get ingress -n $NAMESPACE"
