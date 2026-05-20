● REST API responds (401 - wrong creds likely for dev). But WMS/WFS data requests hang. This strongly suggests geoserver01's WMS/WFS service is stuck or deadlocked — the admin UI and REST API work but the OGC
  services don't.

  Here's the complete diagnosis:

  Root cause: geoserver01 (shasta) OGC services are unresponsive.

  - The Tomcat web admin (/geoserver01/web/) and REST API work fine
  - WMS GetCapabilities and WFS GetFeature requests all time out
  - geoserver02 and geoserver03 work normally — they share the same Tomcat JVM, so this is a geoserver01-specific issue (likely a thread pool deadlock or an overwhelmed workspace)
  - The deployed dev app server works because it loaded :shasta capabilities at startup when geoserver01 was healthy — that data persists in the layers atom
  - Your local server started (or tried to reload) when geoserver01 was already broken, so :shasta never loaded

  This is not a CORS or configuration issue — your local config.edn geoserver URLs are identical to the deployed dev config. The geoserver01 instance needs a restart or investigation on the GCP VM. In the
  meantime, there's no config change that will fix this locally since the geoserver itself is the problem.
