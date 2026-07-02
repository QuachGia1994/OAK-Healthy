#!/bin/bash
# Deploy Firebase Realtime Database rules.
# Prerequisites: npm install -g firebase-tools && firebase login
#
# Usage: bash firebase/deploy-rules.sh

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
RULES_FILE="$SCRIPT_DIR/database.rules.json"

if ! command -v firebase &>/dev/null; then
  echo "Error: firebase-tools not installed. Run: npm install -g firebase-tools"
  exit 1
fi

if [ ! -f "$RULES_FILE" ]; then
  echo "Error: Rules file not found at $RULES_FILE"
  exit 1
fi

echo "Deploying Firebase RTDB rules..."
firebase deploy --only database --project oak-healthy
echo "Done."
