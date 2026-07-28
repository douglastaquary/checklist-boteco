#!/usr/bin/env bash
# Captura prints das abas iOS no Simulator para o README.
# Uso: ./scripts/capture-readme-screenshots.sh
# Requer app Debug instalado no simulador booted (iPhone 14).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/docs/assets/readme"
mkdir -p "$OUT"

BUNDLE_ID="com.checklistboteco.ios"

capture_tab() {
  local tab="$1"
  local file="$2"
  echo "Capturing $file (tab=$tab)"
  xcrun simctl terminate booted "$BUNDLE_ID" 2>/dev/null || true
  sleep 0.3
  # Persist tab choice before launch (read in MainTabView DEBUG onAppear).
  xcrun simctl spawn booted defaults write "$BUNDLE_ID" BecoScreenshotTab -string "$tab"
  xcrun simctl launch booted "$BUNDLE_ID" >/dev/null
  sleep 2.4
  xcrun simctl io booted screenshot "$OUT/$file"
}

open -a Simulator
xcrun simctl boot "iPhone 14" 2>/dev/null || true

capture_tab dashboard "ios-dashboard.png"
capture_tab aiChat "ios-ai-chat.png"
capture_tab purchases "ios-purchases.png"
capture_tab inventory "ios-inventory.png"
capture_tab more "ios-more.png"

echo "Done:"
ls -la "$OUT"/ios-*.png
