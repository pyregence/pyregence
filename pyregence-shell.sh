#!/usr/bin/env bash
# Run to enter a container that contains all pyregences dependences (e.g java)

guix time-machine \
  --channels=channels.scm \
  -- shell \
  --manifest=manifest.scm \
  --container \
  --network \
  -S /usr/bin/env=bin/env \
  -- bash -c '
  export PS1="🔥 \w \$ "
  exec bash
  '
