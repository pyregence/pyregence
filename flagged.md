# Flagged items — PYR1-1529 (WUI active fires select)

## 1. `:wui` feature flag must be set per deployment

`config.edn` is gitignored, so the `:wui true` flag is **not** in version control.
It was added only to the tracked template `config.default.edn`.

**Action required:** ensure each deployment's actual `config.edn` sets
`:wui true` under `:triangulum.views/client-keys :features`, e.g.:

```edn
:features {:match-drop       true
           ...
           :pyretechnics     true
           :wui              true
           :image-mosaic-gwc true}
```

When the flag is absent/false:
- WUI layers are not ingested by `process-layers!`.
- `get-fire-names` returns an empty `:wui-active-fires` map.
- The "WUI Fire" select never appears.

## 2. Known limitation — isochrones underlay not wired for WUI fires

The "Modeled perimeter" isochrones **underlay** in `:active-fire`
(`src/cljs/pyregence/config.cljs`, the `:isochrones` underlay) is hardcoded to
`:geoserver-key :trinity` and `:dependent-inputs [:fire-name :burn-pct :fuel
:model :model-init]`. For a selected WUI fire this will **not** render
correctly because:
- WUI isochrones live in the `:psps` GeoServer, not `:trinity`.
- The underlay is keyed off `:fire-name`, not `:wui-fire-name`.

The main WUI forecast layers (forecasted fire location, flame length, spread
rate, crown fire) work fully. Wiring WUI isochrones is a **follow-up** if
needed — it would require a WUI-specific isochrones underlay (or making the
existing one switch `:geoserver-key`/dependent input based on which fire select
is active).

## 3. End-to-end testing prerequisites

To exercise the feature end-to-end you need:
- Actual `wui-fire-spread-forecast_<fire>_<ts>:*` layers published in the
  `:psps` (utility) GeoServer.
- To be logged in as a user belonging to a PSPS organization (the select is
  restricted to PSPS-org users, and `:psps` tiles authenticate with that org's
  `:geoserver-credentials`).
