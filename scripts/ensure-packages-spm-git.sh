#!/usr/bin/env bash
# Xcode 14.2 SPM still requires a Git repo at Packages/ when the app
# references ../Packages via XCRemoteSwiftPackageReference (branch ios-packages).
# Sources stay versioned in the monorepo; Packages/.git is gitignored and local-only.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PACKAGES="$ROOT/Packages"
BRANCH="ios-packages"
RESOLVED="$ROOT/iosApp/ChecklistBoteco.xcodeproj/project.xcworkspace/xcshareddata/swiftpm/Package.resolved"

if [[ ! -f "$PACKAGES/Package.swift" ]]; then
  echo "error: missing $PACKAGES/Package.swift" >&2
  exit 1
fi

git_packages() {
  git -C "$PACKAGES" -c user.email="${GIT_AUTHOR_EMAIL:-dev@checklistboteco.local}" \
    -c user.name="${GIT_AUTHOR_NAME:-Checklist Boteco}" "$@"
}

if [[ ! -d "$PACKAGES/.git" ]]; then
  echo "Initializing local Packages/.git for Xcode 14.2 SPM…"
  git -C "$PACKAGES" init
  git -C "$PACKAGES" checkout -b "$BRANCH"
else
  current="$(git -C "$PACKAGES" rev-parse --abbrev-ref HEAD 2>/dev/null || true)"
  if [[ "$current" != "$BRANCH" ]]; then
    git -C "$PACKAGES" checkout -B "$BRANCH" >/dev/null 2>&1 || git -C "$PACKAGES" checkout -b "$BRANCH"
  fi
fi

git_packages add -A
if ! git_packages diff --cached --quiet; then
  git_packages commit -m "Local SPM snapshot for Xcode 14.2"
  echo "Committed local SPM snapshot in Packages/."
else
  echo "Packages/.git already up to date."
fi

REV="$(git -C "$PACKAGES" rev-parse HEAD)"
echo "Packages branch=$BRANCH revision=$REV"

if [[ -f "$RESOLVED" ]]; then
  RESOLVED="$RESOLVED" PACKAGES_PATH="$PACKAGES" REV="$REV" python3 - <<'PY'
import json, os, pathlib

resolved = pathlib.Path(os.environ["RESOLVED"])
packages_path = os.environ["PACKAGES_PATH"]
rev = os.environ["REV"]
data = json.loads(resolved.read_text())
pins = data.setdefault("pins", [])
packages_pin = next((pin for pin in pins if pin.get("identity") == "packages"), None)
if packages_pin is None:
    pins.append(
        {
            "identity": "packages",
            "kind": "localSourceControl",
            "location": packages_path,
            "state": {"branch": "ios-packages", "revision": rev},
        }
    )
else:
    packages_pin["kind"] = "localSourceControl"
    packages_pin["location"] = packages_path
    packages_pin.setdefault("state", {})
    packages_pin["state"]["branch"] = "ios-packages"
    packages_pin["state"]["revision"] = rev
resolved.write_text(json.dumps(data, indent=2) + "\n")
print(f"Updated Package.resolved packages revision → {rev}")
PY
fi
