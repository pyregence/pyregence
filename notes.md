# Plan: Add LANDFIRE 2.5.0 to match-drop fuel versions

## Context

Chris confirmed the DPS server now supports LANDFIRE versions `["1.0.5", "1.3.0", "1.4.0", "2.0.0_2019", "2.0.0_2020", "2.1.0", "2.2.0", "2.3.0", "2.4.0", "2.5.0"]`. The client-side validation list and UI need to be updated to add `2.5.0` and remove the stale `2.4.0_2.1.0_nbflip` entry. Non-LANDFIRE fuel sources (CA Fuelscapes, CFO, Fire Factor, CECS) are **not** supported by the DPS and should not be exposed for match-drop.

## Changes

### 1. Dockerfile sed patch (container build)
**File:** `/home/danielhabib/sig/sig3/containers/sig3/corespeq/Dockerfile` ~line 42

Add a new `RUN sed` line after the existing one (line 42) to replace the `valid_fuel_versions` list in the cloned `fuel_wx_ign.py`:

```dockerfile
RUN sed -i 's/valid_fuel_versions = .*/valid_fuel_versions = ["1.0.5", "1.3.0", "1.4.0", "2.0.0_2019", "2.0.0_2020", "2.1.0", "2.2.0", "2.3.0", "2.4.0", "2.5.0"]/' /elmfire/cloudfire/fuel_wx_ign.py
```

This patches the cloned elmfire repo at Docker build time so the client validation matches the server.

### 2. Pyregence UI — config (frontend dropdown)
**File:** `/home/danielhabib/sig/pyregence/src/cljs/pyregence/config.cljs` lines 27-35

Add `"2.5.0"` as a new entry after 2.4.0. Keep 2.4.0 first (it stays the default):

```clojure
(def match-drop-fuel-versions
  (array-map
   "2.4.0" {:opt-label "LANDFIRE 2.4.0"}
   "2.5.0" {:opt-label "LANDFIRE 2.5.0"}
   "2.3.0" {:opt-label "LANDFIRE 2.3.0"}
   "2.2.0" {:opt-label "LANDFIRE 2.2.0"}
   "2.1.0" {:opt-label "LANDFIRE 2.1.0"}
   "1.4.0" {:opt-label "LANDFIRE 1.4.0"}
   "1.3.0" {:opt-label "LANDFIRE 1.3.0"}
   "1.0.5" {:opt-label "LANDFIRE 1.0.5"}))
```

### 3. Pyregence backend — validation set
**File:** `/home/danielhabib/sig/pyregence/src/clj/pyregence/match_drop.clj` line 31-34

Add `"2.5.0"` to the valid set. Keep `default-fuel-version` as `"2.4.0"`:

```clojure
(def valid-md-fuel-versions
  #{"2.5.0" "2.4.0" "2.3.0" "2.2.0" "2.1.0" "1.4.0" "1.3.0" "1.0.5"})

(def default-fuel-version "2.4.0")
```

### 4. Pyregence frontend — default atom
**File:** `/home/danielhabib/sig/pyregence/src/cljs/pyregence/components/map_controls/match_drop_tool.cljs` line 351

No change needed — the atom already defaults to `"2.4.0"`, which we're keeping. **Skip this file.**

## Verification

1. Build the Docker image — confirm the `sed` patch applies cleanly (no error in build log).
2. In pyregence on branch `PYR1-1474-allow-users-to-select-fuels-layer-for-match-drop-runs`, verify the match-drop tool dropdown shows LANDFIRE 2.5.0 through 1.0.5 and defaults to 2.5.0.
3. Confirm a match-drop request with `fuel-version=2.5.0` reaches the backend without being rejected by `valid-md-fuel-versions`.
