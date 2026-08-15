#!/bin/bash
# Generate key.properties (local release signing) and print GitHub Secrets values.
# Usage: bash scripts/gen-release-keys.sh
set -euo pipefail

KEYSTORE="release.keystore"
KEYSTORE_SRC="/home/jarm/Nextcloud/android-dev/release.keystore"

if [ ! -f "$KEYSTORE_SRC" ]; then
    echo "ERROR: keystore not found at $KEYSTORE_SRC" >&2
    exit 1
fi

# Make sure the symlink exists
if [ ! -L "$KEYSTORE" ]; then
    ln -sf "$KEYSTORE_SRC" "$KEYSTORE"
    echo "Linked $KEYSTORE -> $KEYSTORE_SRC"
fi

# Prompt for passwords (hidden input)
read -rsp "Enter keystore password: " KEYSTORE_PASSWORD
echo
read -rsp "Enter key password (or press Enter to use keystore password): " KEY_PASSWORD
echo

# Resolve key alias automatically. keytool prints the alias as
# "Alias name: <alias>" (English locale) or "별칭 이름: <alias>" (Korean locale).
KEY_ALIAS=$(keytool -list -keystore "$KEYSTORE" -storepass "$KEYSTORE_PASSWORD" 2>/dev/null \
    | grep -E "Alias name:|별칭 이름:" | head -1 | sed -E 's/^[^:]*:[[:space:]]*//' \
    || true)
if [ -z "$KEY_ALIAS" ]; then
    echo "ERROR: could not find key alias. Listing entries:" >&2
    keytool -list -keystore "$KEYSTORE" -storepass "$KEYSTORE_PASSWORD" 2>&1 | grep -E "PrivateKeyEntry|trustedCertEntry" || true
    echo "Enter the alias manually:"
    read -r KEY_ALIAS
fi
echo "Key alias: $KEY_ALIAS"

if [ -z "$KEY_PASSWORD" ]; then
    KEY_PASSWORD="$KEYSTORE_PASSWORD"
fi

# Write key.properties
cat > key.properties << EOF
storeFile=$KEYSTORE
storePassword=$KEYSTORE_PASSWORD
keyAlias=$KEY_ALIAS
keyPassword=$KEY_PASSWORD
EOF
chmod 600 key.properties
echo "Wrote key.properties"

# Print GitHub Secrets (value = base64, passwords = literal)
echo
echo "=== GitHub Secrets ==="
echo "RELEASE_KEYSTORE_BASE64:"
base64 -w0 "$KEYSTORE_SRC"
echo
echo "RELEASE_KEYSTORE_PASSWORD: $KEYSTORE_PASSWORD"
echo "RELEASE_KEY_ALIAS: $KEY_ALIAS"
echo "RELEASE_KEY_PASSWORD: $KEY_PASSWORD"
