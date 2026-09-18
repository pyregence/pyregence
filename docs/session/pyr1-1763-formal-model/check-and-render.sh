#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
generated_dir="$script_dir/generated"

mkdir -p "$generated_dir"

for model in IdleRevocation TransferCleanup EpochABA; do
  tla2sany "$script_dir/$model.tla"
  tlc -workers 1 -fp 0 \
      -config "$script_dir/$model.cfg" \
      -dump dot,actionlabels "$generated_dir/$model.dot" \
      "$script_dir/$model.tla"
  dot -Tsvg "$generated_dir/$model.dot" -o "$generated_dir/$model.svg"
  dot -Tpdf "$generated_dir/$model.dot" -o "$generated_dir/$model.pdf"
  (
    cd "$generated_dir"
    tla2tex -latexCommand pdflatex -nops \
      -out "$model-spec" "../$model.tla"
  )
done
