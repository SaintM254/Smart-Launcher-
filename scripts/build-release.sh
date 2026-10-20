#!/usr/bin/env sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$ROOT"
if [ ! -f "$HOME/.config/smart-launcher/signing.properties" ]; then
  echo "Missing external signing key config: ~/.config/smart-launcher/signing.properties" >&2
  exit 1
fi
./gradlew --no-daemon clean assembleRelease
cp app/build/outputs/apk/release/app-release.apk SmartLauncher-v1.0.apk
printf 'Signed release APK: %s/SmartLauncher-v1.0.apk\n' "$ROOT"
