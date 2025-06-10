#!/usr/bin/env bash
# Run to enter a shell that contains all pyregences dependences (e.g java)
# Notes
# + symlink to env because webpack looks for node there.
# + share m2 and gitlabs for clojure deps caching.

guix time-machine \
         --channels=channels.scm \
         -- shell \
         --manifest=manifest.scm \
         --container \
         --network \
         --symlink="/usr/bin/env=bin/env" \
         --share=$HOME/.m2 \
         --share=$HOME/.gitlibs \
         "$@"
