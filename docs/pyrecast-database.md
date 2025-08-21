# Pyrecast Database

The Pyrecast database is a PostgreSQL database that leverages [Triangulum](https://github.com/sig-gis/triangulum), a library written by SIG to help you easily interact with Postgres from a Clojure back-end.
Going into Triangulum is beyond the scope of this document—please see the [Triangulum README](https://github.com/sig-gis/triangulum#readme) and docs for more information.
As noted in the [Pyrecast Architecture document](./pyrecast-architecture.md), the Postgres DB is not used to store any information about specific layers—that is instead the job of GeoServer.

The Postgres DB used by Pyrecast is fairly light.
It's used primarily to keep track of users and organizations, among other things.
This document aims to give a brief description of each of the SQL tables that are used in Pyrecast:

| Table Name | Description |
| :--- | :--- |
| `match_jobs` | This table is used to save information about Pyrecast's Match Drop feature. More specifically, it's used to keep track information pertaining to Match Drop requests that users create through the Pyrecast web UI such as the time it was created, the name of the request, the status of the request, etc. |
| `organization_layers` | This table is used to display custom layers to Pyrecast on an organization-by-organization basis. Logging into an organization that has entries in this table will display those additional layers on the front-end. This is done by providing both a `layer_path` and a `layer_config`. The `layer_path` gives us a path telling us where inside of the `state.cljs/capabilities` atom a new key-value pair should be placed. The last key provided in the `layer_path` will be added to `capabilities` where the value associated with that key is the map from the `layer_config` column. Recall that before signing in, the `capabilities` atom is initially populated with either just `config.cljs/near-term-forecast-options` or `config.cljs/long-term-forecast-options`. Its easiest to think of the `layer_path` and `layer_config` columns as a way to manually add new key-value pair(s) to one of these massive `-options` maps once logging in to an account associated with an organization. |
| `organizations` | A table to store all of the organizations that exist on Pyrecast. Currently, there is no way to create an organization using the web UI. You must instead create one by adding it to the `organizations` table via a SQL change file. This table contains information about an organization such as its ID, name, and the date it was created. The `email_domains` and `auto_add` columns work together to specify which accounts—when newly created using the Pyrecast web UI—should be automatically added to the organization. Note that a user with any email can be added to any organization, the above two columns just provide a convenient way to automatically add users to organizations. If you wish to add a user maually, you can use the `/admin` page on the Pyrecast web UI (assuming you are an admin of that organization). |
| `users` | A table to store all of Pyrecast's users. Contains the information that you would expect a users table to have: each user has an associated ID, email, name, role, organization (optionally), email verification status etc. |
| `user_totp` | A table to store Time-based One-Time Password (TOTP) secrets for users using 2FA. |
| `user_backup_codes` | A table used to store backup codes for users' 2FA. |
