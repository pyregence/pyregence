To deploy the latest master branch to a remote server in production
mode, follow these steps:

1. Reconnect to your existing screen session and kill the running server.

  $ screen -r
  Ctrl-c

2. Download the latest code.

  $ git pull

3. Apply any DB changes.

  $ psql -h localhost -U pyregence -d pyregence -f src/sql/changes/SOME_CHANGE_FILE.sql
  $ clojure -A:build-db only-functions

4. Recompile the CLJS code.

  $ clojure -A:compile-cljs

5. Recompile the CLJ code and launch the server in production mode.

  $ sudo clojure -A:run-server:production

6. Detach from the running screen session.

  Ctrl-a d
