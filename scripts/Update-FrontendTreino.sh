#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

if [[ "$(git branch --show-current)" != "develop" ]]; then
  echo "Execute este script na branch develop." >&2
  exit 1
fi
if [[ -n "$(git status --porcelain)" ]]; then
  echo "Salve as alteracoes em um commit antes de publicar." >&2
  exit 1
fi

PROJECT="sd2026"
REGION="us-central1"
TAG="$(git rev-parse --short HEAD)"
REGISTRY="$REGION-docker.pkg.dev/$PROJECT/gymflow"
TREINO_IMAGE="$REGISTRY/treino-service:$TAG"
FRONTEND_IMAGE="$REGISTRY/frontend:$TAG"

# Confirma os servicos existentes e registra suas imagens anteriores.
PREVIOUS_TREINO="$(gcloud run services describe gymflow-treino --project="$PROJECT" --region="$REGION" --format='value(spec.template.spec.containers[0].image)')"
PREVIOUS_FRONTEND="$(gcloud run services describe gymflow-frontend --project="$PROJECT" --region="$REGION" --format='value(spec.template.spec.containers[0].image)')"
printf 'Imagem anterior de treinos: %s\nImagem anterior do frontend: %s\n' "$PREVIOUS_TREINO" "$PREVIOUS_FRONTEND"

gcloud auth configure-docker "$REGION-docker.pkg.dev" --quiet
# Builds sequenciais para reduzir o consumo de memoria.
docker build -f treino-service/Dockerfile -t "$TREINO_IMAGE" .
docker build -f frontend/Dockerfile -t "$FRONTEND_IMAGE" .
docker push "$TREINO_IMAGE"
docker push "$FRONTEND_IMAGE"

# Atualiza somente as imagens. Preserva configuracao, identidade e secrets.
gcloud run services update gymflow-treino \
  --project="$PROJECT" --region="$REGION" --image="$TREINO_IMAGE"
gcloud run services update gymflow-frontend \
  --project="$PROJECT" --region="$REGION" --image="$FRONTEND_IMAGE"

gcloud run services describe gymflow-treino --project="$PROJECT" --region="$REGION" \
  --format='yaml(status.latestReadyRevisionName,status.conditions,status.traffic)'
gcloud run services describe gymflow-frontend --project="$PROJECT" --region="$REGION" \
  --format='yaml(status.latestReadyRevisionName,status.conditions,status.traffic,status.url)'
echo "FRONTEND E TREINOS ATUALIZADOS - TAG: $TAG"
