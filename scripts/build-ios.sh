#!/usr/bin/env bash
# Prepara Packages/.git (Xcode 14.2 SPM) e compila o app iOS no simulador.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
"$ROOT/scripts/ensure-packages-spm-git.sh"

DESTINATION="${DESTINATION:-platform=iOS Simulator,name=iPhone 14}"

cd "$ROOT"
xcodebuild \
  -project iosApp/ChecklistBoteco.xcodeproj \
  -scheme ChecklistBoteco \
  -destination "$DESTINATION" \
  "$@" \
  build
