
## About the XMLs feature

The purpose of what we'll add is to read observations (fill in) from the XML server when they are not available in the rest API.

The API for the Canadian weather stations has two sets of stations:
- non-partner (or "core"), which are, I think, provided by the government
- partner, which are operated by companies and other groups, and shared with the government

Observations for non-partner stations are easy to get, and mostly come from the REST API. Those for partner stations, however, are often missing from the API, and have to be fetched from a server that holds XML files. The naming patterns for directories and files in that server are weird, so coming up with a stable method to find the correct XML for an observation is a challenge. That's the main goal of the XML fill in feature: to find observations in XMLs with the least amount of fetches, and in the most efficient way possible.



## About the refactoring

The weather stations feature is similar to the cameras feature:

- A list of map points (FeatureCollection) is created based on some API response (cameras for one, weather stations for the other).
- The FeatureCollection is served to the client, which renders the points as icons on a map.
- When the user clicks on an icon, the client fetches something (an observation or an image) from the backend and displays it.

So, considering that there's recent work that was done to improve the structure of the cameras feature, we can take the opportunity now to re-implement the weather stations feature using a similar approach.

Relevant PR: https://github.com/pyregence/pyregence/pull/1208

NOTE: the use of "refactoring" here will end up being wrong: as you'll see below, this will be closer to a re-implementation, using existing code as reference.

### Guides for refactoring

The following list is not exhaustive but has some points found during the cameras refactoring:

- Whenever reasonable, decouple the I/O operations (fetching from an API, reading filles) from their processing (extracting properties, formatting map features, etc). Don't do it if that results in too many trivial functions. In other words, try to write pure functions for processing but don't be obsessive about it; this is clojure, not Haskell.
- Use HTTP headers to communicate metadata such as cache status and upstream failures to the client, to make debugging and diagnosing issues easier.
- Use similar patterns for all sources (but, of course, make the specifics different according to their specifics).



## Acceptance criteria

Again, not exhaustive, but something to get started with:

- Reading from each source is an independent process. Failure in one does not affect the other
- There is a cache pot per source, and the application falls back to it if a read fails
- The structures and namespace layout is similar for the two sources; they have a similar shape (without necessarily making them exactly the same).
- The refresh intervals can be configured per source.



## File structure

The following are what I think will be the files involved in the implementation, and the order in which I'd like to tackle them:

### weather_stations.clj
- The aggregator. Has the request handlers, the cache, and leverages the per-source functions to get points and observations. Other than cache configuration, has little source-specific code. It acts as a "glue" to bring together the US and Ca sources, and handle cache.

### weather_stations.ca.clj

- The Canada-specific code for reading from the REST API, and handling the transition to the XML server when needed. It's the "source of truth" for all things Canada from the perspective of weather_stations.clj

- weather_stations.clj is the aggregator. Calls functions from the source specific namespaces to get both the list of stations and individual observations. Merges the station lists and holds the request handler.
- weather_stations.us.clj has the us specific fetching and processing
- weather_stations.ca.clj has the Canada specific fetching and processing for the rest API 
- weather_stations.ca.xml.clj has the crawling, fetching, parsing and processing logic for the XML server.

### weather_stations.ca.xml.clj (optional)

If the XML-related code is substantial, we can break it into its own file. Otherwise, put in the same file.

We could also have a directory weather_stations with files us.clj and ca.clj (optional ca.xml.clj) inside. Perhaps cleaner than having multiple similarly-named files.



## Process

We'll work on a new branch, named PYR1-1712-weather-stations-xmls-and-refactor, on a work tree at ../worktrees/. The branch should start from main (not from the XMLs branch we reviewed).

- Preliminary tests: before starting the implementation, we need to be certain that there's a reasonable way to read observations for both partner and non-partner stations without having to read thousands of xml files from the server. Start by writing code to demonstrate this (through the REPL or a Babashka script). The code may be reused when implementing the XMls for Canada, or just serve as reference. If this fails and we can't prove a way to read observations in an efficient way, we'll stop the whole thing and communicate that to the Project Manager.

If / once we have proof of being able to read observations with the XML fallback, the process I'm thinking is:

- Start with weather_stations. Write cache-related functions, shape the cache atom, the TTLs configuration.
- Then move on to adding the Canadian REST API as a source, both for points and observations (which is equivalent to what currently exists on main). The observations fns should return values that are useful both to provide informative headers in the http response, and to decide when to go fetch a XML. If a nil is enough, that's fine (or can be changed later). At this point, the XML is not read from at all.
- Minimal crawling of the XML server. The goal is just to build the data necessary to later find the XML file needed for a given observation if it's not found on the REST API. Probably read some directory listings and put the results in the cache atom (similar to how the currentImages are processed for cameras, maybe). See open question on what id to use for key.
- Write observation fill-in function. A function that takes a station id and attempts to read the latest observation from XML. Finds the file (leveraging the info from the crawl), reads it, parses the XML and extracts the relevant values. Returns either an observation in the format expected by the handler / client, or a map that can be used to build one such value. I think we should let weather stations handle its caching.
- Change the client code to only fetch from observations (don't handle the xml fallback). The backend should attempt to fetch from XML without the client making a second request.



## Open questions

- I'd like to be able to control TTL per source, and separately for points and for observations. Should we have two cache atoms, one for points and another one for observations?
- What's the best ID to use to key observations cache, and the crawl details needed to fetch XML observations? msc_id? iata?
- The weather stations feature already exists on main. To do a reimplementation we need a new namespace (or a way to separate the new code from the old, to make review easier). I was thinking that we could do the new one at weather_stations_new, and once finished, just remove weather_stations and swap the other one in (namespace changes only).

Start by gathering the context you need, and let's discuss the open questions. Help me address gaps in this plan, and once it's solid, refine the plan and write it to scratchpad/weather-stations-implementation.claude.md. Put any notes that will be useful to you in a new session, so you can pick up from there.