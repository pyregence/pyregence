#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
scratch_dir="$(mktemp -d)"
trap 'rm -rf "$scratch_dir"' EXIT

python3 - "$script_dir" "$scratch_dir" <<'PY'
from pathlib import Path
import shutil
import sys

source = Path(sys.argv[1])
target = Path(sys.argv[2])

mutations = {
    "IdleRevocation": (
        "live /\\ lastActive = idleExpected",
        "live",
    ),
    "TransferCleanup": (
        'currentSession = "G1A" /\\ epoch = 1 /\\ ~revoked',
        "TRUE",
    ),
    "EpochABA": (
        "observedGeneration = currentGeneration\n"
        "                      /\\ observedEpoch = epoch",
        "observedGeneration = currentGeneration",
    ),
}

for model, (before, after) in mutations.items():
    text = (source / f"{model}.tla").read_text()
    if text.count(before) != 1:
        raise SystemExit(f"mutation anchor for {model} changed")
    (target / f"{model}.tla").write_text(text.replace(before, after))
    shutil.copy2(source / f"{model}.cfg", target / f"{model}.cfg")
PY

declare -A expected_invariant=(
  [IdleRevocation]=NewerActivityFencesOlderIdle
  [TransferCleanup]=InstalledSuccessorCannotBeReplaced
  [EpochABA]=RecycledChallengeIsRejected
)

for model in IdleRevocation TransferCleanup EpochABA; do
  log="$scratch_dir/$model.log"
  if tlc -workers 1 \
         -config "$scratch_dir/$model.cfg" \
         "$scratch_dir/$model.tla" >"$log" 2>&1; then
    echo "$model mutation unexpectedly passed" >&2
    cat "$log" >&2
    exit 1
  fi
  if ! grep -q "Invariant ${expected_invariant[$model]} is violated" "$log"; then
    echo "$model failed for a reason other than its intended invariant" >&2
    cat "$log" >&2
    exit 1
  fi
  echo "$model mutation: intended invariant failed"
done
