#!/usr/bin/env bash
# Generates a self-signed TLS certificate for local/testing use only.
# Production should use a real certificate (e.g. Let's Encrypt) placed at the same paths.
#
# Usage: ./nginx/generate-dev-certs.sh [common-name]   (defaults to "localhost")
set -euo pipefail

CN="${1:-localhost}"
CERT_DIR="$(cd "$(dirname "$0")" && pwd)/certs"

mkdir -p "$CERT_DIR"

openssl req -x509 -nodes -newkey rsa:2048 \
  -days 365 \
  -keyout "$CERT_DIR/privkey.pem" \
  -out    "$CERT_DIR/fullchain.pem" \
  -subj   "/CN=$CN" \
  -addext "subjectAltName=DNS:$CN,DNS:localhost,IP:127.0.0.1"

echo "Self-signed cert written to $CERT_DIR (CN=$CN)."
