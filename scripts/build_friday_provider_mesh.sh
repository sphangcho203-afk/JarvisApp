#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

python3 scripts/finalize_sensory_source.py
python3 scripts/run_pending_integrations.py
python3 scripts/apply_provider_integrations.py

(
  cd helix-ui
  npm ci --no-audit --no-fund
  npm run build
)

rm -rf app/src/main/assets/helix
mkdir -p app/src/main/assets/helix
cp -R helix-ui/dist/. app/src/main/assets/helix/

task="${1:-:app:assembleDebug}"
shift || true
./gradlew "$task" "$@" --stacktrace --no-daemon

echo "FRIDAY provider mesh build complete: $task"
