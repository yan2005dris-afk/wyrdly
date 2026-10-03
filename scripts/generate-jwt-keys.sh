#!/usr/bin/env bash
set -euo pipefail

# Output directory for JWT keys (gitignored)
JWT_DIR="${1:-wyrdly-backend/src/main/resources/jwt}"

mkdir -p "$JWT_DIR"

PRIVATE_KEY="$JWT_DIR/privateKey.pem"
PUBLIC_KEY="$JWT_DIR/publicKey.pem"

if [ -f "$PRIVATE_KEY" ] && [ -f "$PUBLIC_KEY" ]; then
  echo "JWT keys already exist in $JWT_DIR. Skipping generation."
  exit 0
fi

echo "Generating 2048-bit RSA key pair for SmallRye JWT..."
openssl genpkey -algorithm RSA -out "$PRIVATE_KEY" -pkeyopt rsa_keygen_bits:2048
openssl rsa -pubout -in "$PRIVATE_KEY" -out "$PUBLIC_KEY"

chmod 644 "$PRIVATE_KEY"
chmod 644 "$PUBLIC_KEY"

echo "Keys generated successfully in $JWT_DIR:"
echo " - Private Key (PKCS#8): $PRIVATE_KEY"
echo " - Public Key (X.509):   $PUBLIC_KEY"
