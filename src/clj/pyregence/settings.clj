(ns pyregence.settings
  "Where this process's configuration comes from, under PyreCast's own name for
   it.

   `triangulum.config` resolves `config.edn` against the process working
   directory and offers nothing that overrides it -- no flag, no variable, no
   argument to any `-main`. A supervisor, a container, a scheduled job and an
   acceptance run each read whatever `config.edn` is in front of them.

   Nor can triangulum take an option for it, which is why the name has to be
   ours: every entry point parses its arguments as
   `(get-cli-options args cli-options cli-actions alias (get-config :server))`.
   The configuration is an argument to the parser, so it is read before the
   arguments are, and a `--config` among them would arrive after the file had
   been settled.

   Second time around -- `pyregence.routing-handler` is the first -- so it is
   also the shape: a name we own in front of triangulum, adopted back in one
   edit here once the change lands upstream.

   Install, don't build. Nothing here decides which settings this process runs
   on; a composition root settles one before anything reads a key. The second
   realization is deliberately absent: settings held in memory, for a process
   with no file to read, wants `get-config` to come through here too and is a
   larger change than this namespace was cut for."
  (:require [triangulum.config :as config]))

(defprotocol ISettings
  (in-force! [settings]
    "Put these settings in force for this process. Called by a composition root
     before any namespace that reads a key is loaded."))

(defrecord ConfigFile [path]
  ISettings
  (in-force! [_] (config/load-config path)))

(defrecord WorkingDirectory []
  ISettings
  (in-force! [_] nil))

(defn from-file
  "Settings read from the file at PATH, wherever this process was started."
  [path]
  (->ConfigFile path))

(defn from-working-directory
  "What triangulum does unaided: `config.edn`, resolved against the working
   directory. Named rather than implied, so a launch that means to depend on its
   working directory says so."
  []
  (->WorkingDirectory))

(defn from-args
  "The settings NAMED by a `--config PATH` pair in ARGS, or the working
   directory's if there is none. Answers the settings and the arguments with the
   pair removed, since what is left belongs to a dispatch that would refuse an
   option it does not know.

   A long spelling only: `-c` is triangulum's `--cider-nrepl`."
  [args]
  (let [args (vec args)
        at   (.indexOf args "--config")]
    (if (neg? at)
      [(from-working-directory) args]
      [(from-file (get args (inc at)))
       (into (subvec args 0 at) (subvec args (min (count args) (+ at 2))))])))

(defn in-force-from-args!
  "Put the settings NAMED in ARGS in force, and answer what is left of ARGS.

   The two in one call, because they are one decision made in one order and the
   order is the whole of it: the dispatch reads configuration in order to parse
   its own arguments, so settings settled after it are settled too late. A
   composition root cannot reach the arguments the dispatch wants without having
   gone through the call that settles them first."
  [args]
  (let [[settings args] (from-args args)]
    (in-force! settings)
    args))

^:rct/test
(comment
  ;; The pair is consumed, and what surrounds it is handed on untouched.
  (let [[settings rest-args] (from-args ["server" "start" "--config" "/etc/pyrecast.edn"
                                         "--http-port" "13371"])]
    [(:path settings) rest-args])
  ;=> ["/etc/pyrecast.edn" ["server" "start" "--http-port" "13371"]]

  ;; No pair, no change: an unconfigured launch behaves as it always did.
  (let [[settings rest-args] (from-args ["server" "start"])]
    [(:path settings) rest-args])
  ;=> [nil ["server" "start"]]

  ;; A trailing --config names nothing, and the complaint belongs to whoever
  ;; reads the file rather than to a second spelling of "missing" here.
  (:path (first (from-args ["server" "start" "--config"])))
  ;=> nil
  )
