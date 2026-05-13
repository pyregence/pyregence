# Plan: Allow Users to Select Fuels Layer for Match Drop Runs

## Context

Match Drop fire simulations currently hardcode `fuel-source: "landfire"` and `fuel-version: "2.4.0"`. Users need a dropdown to select which LANDFIRE version to use. If the ignition point is outside the selected fuels layer's geographic boundary, an error should be shown. The requirement says "Make work on Pyretechnics at a minimum" — the Kubernetes/sig3 path handles Pyretechnics.

The pipeline is: **Frontend** → **Backend (pyregence)** → **sig3** → **DPS (fuel_wx_ign.py)** → models (ELMFIRE/Pyretechnics/GridFire) → GeoSync. Only the frontend and pyregence backend need code changes — sig3's `match-drop.edn` already passes through `fuel-source`/`fuel-version` via its request-mapping, and the DPS spec already accepts all LANDFIRE versions we'll expose.

## Available LANDFIRE Versions

**DPS valid list** (`dps.clj:185`): `1.0.5, 1.3.0, 1.4.0, 2.0.0_2019, 2.0.0_2020, 2.1.0, 2.2.0, 2.3.0, 2.4.0, 2.4.0_2.1.0_nbflip`

**Exposed in dropdown** (intersection of DPS-supported + GeoServer-available, excluding ambiguous mappings):
`2.4.0` (default), `2.3.0`, `2.2.0`, `2.1.0`, `1.4.0`, `1.3.0`, `1.0.5`

Excluded: `2.5.0` / `2.5.0-2.4.0` (not in DPS), `2.0.0` (ambiguous: DPS has `2.0.0_2019` and `2.0.0_2020`), `2.4.0_2.1.0_nbflip` (no GeoServer layer).

---

## Step 1: Add Match Drop fuel config — `config.cljs`

**File:** `src/cljs/pyregence/config.cljs`

Add a new `match-drop-fuel-versions` ordered map (near top of file, after geographic constants). Each key is the DPS fuel-version string, with a human-readable label and GeoServer workspace filter for extent lookup:

```clojure
(def match-drop-fuel-versions
  (array-map
   "2.4.0" {:opt-label "LANDFIRE 2.4.0"  :geoserver-filter "landfire-2.4.0"}
   "2.3.0" {:opt-label "LANDFIRE 2.3.0"  :geoserver-filter "landfire-2.3.0"}
   "2.2.0" {:opt-label "LANDFIRE 2.2.0"  :geoserver-filter "landfire-2.2.0"}
   "2.1.0" {:opt-label "LANDFIRE 2.1.0"  :geoserver-filter "landfire-2.1.0"}
   "1.4.0" {:opt-label "LANDFIRE 1.4.0"  :geoserver-filter "landfire-1.4.0"}
   "1.3.0" {:opt-label "LANDFIRE 1.3.0"  :geoserver-filter "landfire-1.3.0"}
   "1.0.5" {:opt-label "LANDFIRE 1.0.5"  :geoserver-filter "landfire-1.0.5"}))
```

## Step 2: Add fuel dropdown to Match Drop UI — `match_drop_tool.cljs`

**File:** `src/cljs/pyregence/components/map_controls/match_drop_tool.cljs`

1. Add `fuel-version` atom (default `"2.4.0"`) in `match-drop-tool`'s `r/with-let` block
2. Add a `fuel-version-select` dropdown component using `<select>` + `c/match-drop-fuel-versions`
3. Place it after "Ignition Location" and before `<hr>` / weather section
4. Update `initiate-match-drop!` signature to accept `fuel-version`
5. Include `:fuel-version fuel-version` in the params map sent to `"initiate-md"`
6. Thread `fuel-version` through `md-buttons` to the submit handler

## Step 3: Backend validation & passthrough — `match_drop.clj`

**File:** `src/clj/pyregence/match_drop.clj`

1. Add `layers` to the `:refer` from `pyregence.capabilities`
2. Define `valid-md-fuel-versions` set and `default-fuel-version` constant
3. Add `get-fuel-layer-extent` — looks up extent from `(:shasta @layers)` by matching workspace `"fuels_landfire-{version}"`
4. Add `point-within-extent?` — parses `[minx miny maxx maxy]` strings to doubles, checks containment
5. In `initiate-md!`, add two new `cond` branches before the existing checks:
   - Validate fuel-version is in `valid-md-fuel-versions`
   - Validate ignition point is within the fuel layer extent (if extent is available)
6. In `match-drop-args->body` (Kubernetes path, line ~408): replace hardcoded `"2.4.0"` with `(or fuel-version default-fuel-version)`

## Step 4: No changes needed in external repos

- **sig3 `match-drop.edn`**: Already maps `pyrc_fuel_version` → `:fuel-version` in request-mapping
- **pyrecast-scripts `dps.clj`**: Spec already accepts all exposed versions
- **`fuel_wx_ign.py`**: Already validates and accepts all exposed versions

---

## Verification

1. Start dev server, open Match Drop tool, confirm dropdown shows 7 LANDFIRE versions with 2.4.0 selected by default
2. Submit a Match Drop with a non-default version (e.g., 2.3.0), check the `dps_request` JSONB in `match_jobs` table contains `fuel-version: "2.3.0"`
3. Test boundary validation: pick a point outside CONUS and verify error message references the fuel layer
4. Test backward compatibility: call `initiate-md` API without `fuel-version` param, verify it defaults to 2.4.0
5. Verify the Kubernetes/sig3 path sends the correct `pyrc_fuel_version` value (this is the Pyretechnics path)
