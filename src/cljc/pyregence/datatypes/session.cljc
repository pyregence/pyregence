(ns pyregence.datatypes.session
  "Whose session a question is being asked on behalf of.

   A role and not a person: what PyreCast's answers turn on is which of its
   routes answers for this caller, and PyreCast decides that by role. Who they
   are is PyreCast's business and never the page's.

   Deliberately no `ended?`. A page cannot know its own session has ended --
   that is the whole of PYR1-1623 -- so a session value that could say so would
   be modelling the bug's absence. PyreCast is the only thing that knows, and
   the only way to find out is to ask it something and be refused.")

(defrecord Session [role])

(defn ->session
  "The session PyreCast reported by ROLE."
  [role]
  (->Session role))

(def ^:private every-organization-roles
  "The roles for whom every organization is one of theirs.

   A closed set, so a role nobody has classified administers nothing. The same
   two strings appear in nine other places, all of them asking this same
   question of a bare `user-role`; `components.settings.roles` holds the wider
   role ladder and is cljs, so it cannot be reached from here. Reconciling them
   is worth doing and is not this ticket."
  #{"super_admin" "account_manager"})

(defn administers-every-organization?
  "Whether every organization is one of this session's.

   The question the two org routes differ on, asked once and tested once.

   Not to be confused with `roles-who-can-see-admin-btn`
   (`near_term_forecast.cljs:68`), which reads similarly and asks something
   else: who may see an admin button. An organization admin administers one
   organization, so it is a yes there and a no here."
  [a-session]
  (contains? every-organization-roles (:role a-session)))
