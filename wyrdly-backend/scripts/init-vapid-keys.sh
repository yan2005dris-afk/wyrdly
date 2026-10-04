#!/usr/bin/env bash
# scripts/init-vapid-keys.sh
# Idempotent bootstrap of Web Push VAPID (RFC 8292) key pair on the filesystem.
#
# Usage:
#   init-vapid-keys.sh [OUTPUT_DIR]
#
# Defaults OUTPUT_DIR to /deployments/vapid (matches the Docker Compose volume mount).
# The script is safe to run multiple times: it skips generation when both files already exist.

set -euo pipefail

OUTPUT_DIR="${1:-${VAPID_OUTPUT_DIR:-/deployments/vapid}}"
PUBLIC_FILE="$OUTPUT_DIR/publicKey.txt"
PRIVATE_FILE="$OUTPUT_DIR/privateKey.txt"
PRIVATE_PEM="$OUTPUT_DIR/privateKey.pem"

mkdir -p "$OUTPUT_DIR"

# --- Idempotency guard ---------------------------------------------------
if [ -s "$PUBLIC_FILE" ] && [ -s "$PRIVATE_FILE" ]; then
  echo "[vapid] Keys already present at $OUTPUT_DIR. Skipping generation."
  echo "        public:  $PUBLIC_FILE ($(wc -c < "$PUBLIC_FILE") bytes)"
  echo "        private: $PRIVATE_FILE ($(wc -c < "$PRIVATE_FILE") bytes)"
  exit 0
fi

echo "[vapid] Generating fresh P-256 VAPID key pair into $OUTPUT_DIR ..."

# --- 1. Generate the EC P-256 private key in PEM (temporary) -------------
openssl ecparam -name prime256v1 -genkey -noout -out "$PRIVATE_PEM"

# --- 2. Export the private key as PKCS#8 DER -> base64url ----------------
openssl pkcs8 -topk8 -nocrypt -in "$PRIVATE_PEM" -outform DER \
  | base64 -w 0 | tr '+/' '-_' | tr -d '=' > "$PRIVATE_FILE"

# --- 3. Export the public key as uncompressed EC point (0x04 || X || Y) -> base64url ----
# The SubjectPublicKeyInfo DER ends with the BIT STRING wrapping the uncompressed point:
#   ... 03 42 00 04 X[32] Y[32]
# We strip everything but the last 65 bytes (the raw 04 || X || Y point per ANSI X9.62).
openssl ec -in "$PRIVATE_PEM" -conv_form uncompressed -pubout -outform DER \
  | tail -c 65 | base64 -w 0 | tr '+/' '-_' | tr -d '=' > "$PUBLIC_FILE"

# --- 4. Cleanup intermediate PEM (private key material must not linger) -
shred -u "$PRIVATE_PEM" 2>/dev/null || rm -f "$PRIVATE_PEM"

chmod 644 "$PUBLIC_FILE"
chmod 644 "$PRIVATE_FILE"

# --- 6. Make the keys readable by the unprivileged runtime user -----------------
# The script runs as root (so it can write to the bind-mounted volume), but
# the JVM is launched via `su-exec quarkus`. Two layered safeguards so the
# runtime can read the files:
#   (a) chmod 644 (world-readable) on both keys.
#   (b) best-effort chown to the configured RUNTIME_USER (uid 10001 in our
#       Docker image) so the JVM process owner matches.
if command -v chown >/dev/null 2>&1; then
  chown "${RUNTIME_USER:-quarkus}:${RUNTIME_GROUP:-quarkus}" \
      "$PUBLIC_FILE" "$PRIVATE_FILE" 2>/dev/null || true
fi

# --- 5. Self-verify: decode and assert expected lengths ------------------
PUB_LEN=$(wc -c < "$PUBLIC_FILE")
PRIV_LEN=$(wc -c < "$PRIVATE_FILE")

if [ "$PUB_LEN" -ne 87 ]; then
  echo "[vapid] ERROR: public key file has $PUB_LEN bytes, expected 87 (base64url of 65-byte uncompressed point)." >&2
  exit 1
fi
if [ "$PRIV_LEN" -lt 160 ] || [ "$PRIV_LEN" -gt 200 ]; then
  echo "[vapid] ERROR: private key file has $PRIV_LEN bytes, expected ~185 (PKCS#8 DER P-256)." >&2
  exit 1
fi

echo "[vapid] Keys generated successfully:"
echo "        public:  $PUBLIC_FILE ($PUB_LEN bytes, base64url)"
echo "        private: $PRIVATE_FILE ($PRIV_LEN bytes, base64url)"