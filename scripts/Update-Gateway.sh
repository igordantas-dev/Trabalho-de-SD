#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

if [[ -n "$(git status --porcelain)" ]]; then
  echo "Commit or discard local changes before publishing a commit-tagged image." >&2
  exit 1
fi

TAG="$(git rev-parse --short HEAD)"
IMAGE="us-central1-docker.pkg.dev/sd2026/gymflow/api-gateway:$TAG"

gcloud auth configure-docker us-central1-docker.pkg.dev --quiet
docker build -f api-gateway/Dockerfile -t "$IMAGE" .
docker push "$IMAGE"

# Preserve service identity, secret references, hosts and other existing settings.
gcloud run services update gymflow-gateway \
  --project=sd2026 \
  --region=us-central1 \
  --image="$IMAGE" \
  --update-env-vars=GRPC_DEADLINE_SECONDS=20

echo "GATEWAY ATUALIZADO — TAG: $TAG — DEADLINE: 20s"
