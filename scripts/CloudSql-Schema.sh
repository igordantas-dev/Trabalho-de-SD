#!/usr/bin/env bash
set -euo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../db"

MODE="${1:-validate}"
case "$MODE" in
  validate) INPUT="Validate.psql" ;;
  apply) INPUT="Apply.psql" ;;
  *) echo "Uso: bash scripts/CloudSql-Schema.sh [validate|apply]" >&2; exit 2 ;;
esac

# Secret remains only in this process environment, never in the repository/output.
export PGPASSWORD
PGPASSWORD="$(gcloud secrets versions access 1 --secret=gymflow-db-password --project=sd2026)"
if [[ -z "$PGPASSWORD" ]]; then
  echo "Secret vazio; conexao cancelada." >&2
  exit 1
fi
export PGCONNECT_TIMEOUT=30
trap 'unset PGPASSWORD' EXIT

gcloud sql connect gymflow-db \
  --project=sd2026 \
  --database=academia \
  --user=gymflow_app \
  --quiet < "$INPUT"
