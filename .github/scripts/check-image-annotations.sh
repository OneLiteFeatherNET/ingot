#!/usr/bin/env bash
# Checks the OCI annotations of a pushed image index and of its platform manifests.
#
# Usage:
#   check-image-annotations.sh INDEX_FILE MANIFEST_DIR
#
# INDEX_FILE is the raw index, as printed by:
#   docker buildx imagetools inspect --raw <ref>
#
# MANIFEST_DIR holds one raw platform manifest per platform descriptor of the index, named after its digest with
# the colon replaced by a dash (sha256:<hex> becomes sha256-<hex>.json). Each one comes from:
#   docker buildx imagetools inspect --raw <ref>@<digest>
#
# Attestation manifests (platform unknown/unknown, or the attestation-manifest reference type) carry no
# annotations and are skipped, so the caller does not need a file for them.
#
# Exits 0 when the index and every platform manifest carry all seven expected annotations with non-empty
# values. Otherwise exits 1 and names each missing or empty key. Exits 2 on a usage error.
# Requires bash and jq.

set -euo pipefail

REQUIRED_KEYS=(
  "org.opencontainers.image.title"
  "org.opencontainers.image.description"
  "org.opencontainers.image.vendor"
  "org.opencontainers.image.licenses"
  "org.opencontainers.image.version"
  "org.opencontainers.image.base.name"
  "org.opencontainers.image.base.digest"
)

usage() {
  echo "usage: $(basename "$0") INDEX_FILE MANIFEST_DIR" >&2
}

# Prints the problems with one raw document and returns 1 when there are any.
check_document() {
  local file="$1" label="$2" key value failed=0
  if ! jq empty "$file" >/dev/null 2>&1; then
    echo "error: $label is not valid JSON: $file" >&2
    return 1
  fi
  for key in "${REQUIRED_KEYS[@]}"; do
    value="$(jq -r --arg key "$key" '(.annotations // {})[$key] // empty' "$file")"
    if [[ -z "${value//[[:space:]]/}" ]]; then
      echo "error: $label is missing or has an empty annotation $key: $file" >&2
      failed=1
    fi
  done
  return "$failed"
}

if [[ $# -ne 2 || "$1" == -* || "$2" == -* ]]; then
  usage
  exit 2
fi

INDEX_FILE="$1"
MANIFEST_DIR="$2"

if [[ ! -f "$INDEX_FILE" ]]; then
  echo "error: index file does not exist: $INDEX_FILE" >&2
  exit 1
fi

if ! jq empty "$INDEX_FILE" >/dev/null 2>&1; then
  echo "error: index is not valid JSON: $INDEX_FILE" >&2
  exit 1
fi

failed=0
check_document "$INDEX_FILE" "index" || failed=1

platform_count=0
while IFS= read -r digest; do
  platform_count=$((platform_count + 1))
  manifest_file="$MANIFEST_DIR/${digest//:/-}.json"
  if [[ ! -f "$manifest_file" ]]; then
    echo "error: no manifest file for platform manifest $digest in $MANIFEST_DIR" >&2
    failed=1
    continue
  fi
  check_document "$manifest_file" "platform manifest $digest" || failed=1
done < <(jq -r '.manifests[]
  | select((.platform.os // "") != "unknown")
  | select((.annotations["vnd.docker.reference.type"] // "") != "attestation-manifest")
  | .digest' "$INDEX_FILE")

if [[ "$platform_count" -eq 0 ]]; then
  echo "error: no platform manifest in index: $INDEX_FILE" >&2
  exit 1
fi

if [[ "$failed" -ne 0 ]]; then
  exit 1
fi

echo "ok: index and $platform_count platform manifest(s) carry all ${#REQUIRED_KEYS[@]} annotations"
