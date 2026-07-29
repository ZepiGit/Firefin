#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FLUTTER_BIN="${FLUTTER_BIN:-flutter}"
APK_SOURCE="$REPO_ROOT/build/app/outputs/flutter-apk/app-release.apk"
APK_OUTPUT="$REPO_ROOT/Moonfin_FireTV32_Unofficial_1.1.0-r4.apk"

for variable in \
  MOONFIN_KEYSTORE_FILE \
  MOONFIN_KEYSTORE_PASSWORD \
  MOONFIN_KEY_ALIAS \
  MOONFIN_KEY_PASSWORD; do
  if [ -z "${!variable:-}" ]; then
    echo "Missing required private signing variable: $variable" >&2
    exit 1
  fi
done

cd "$REPO_ROOT"

"$FLUTTER_BIN" --version
"$FLUTTER_BIN" clean
"$FLUTTER_BIN" pub get
"$FLUTTER_BIN" build apk \
  --release \
  --target-platform android-arm \
  --build-name 1.1.0 \
  --build-number 3000011

if [ ! -f "$APK_SOURCE" ]; then
  echo "APK not found at $APK_SOURCE" >&2
  exit 1
fi

cp "$APK_SOURCE" "$APK_OUTPUT"
sha256sum "$APK_OUTPUT"
echo "Created $APK_OUTPUT"
