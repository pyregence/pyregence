# TODO

## Unused / unwired admin endpoints (PYR1-1512 IDOR review)

These endpoints were hardened with per-resource authorization but have **no
in-app (cljs) caller**. They remain registered routes with `:token` auth, so an
external/machine client could still call them — so they are not provably dead.
Decide per endpoint whether to wire into the UI, keep as an external API, or
remove to reduce attack surface.

- `add-org-user` (`/clj/add-org-user`) — dead duplicate of `add-org-users`
  (plural); the settings UI only uses the plural form. Strongest removal
  candidate.
- `remove-org-user` (`/clj/remove-org-user`)
- `update-org-user-role` (`/clj/update-org-user-role`)
- `update-user-org-membership-status` (`/clj/update-user-org-membership-status`)
- `update-user-name` (`/clj/update-user-name`) — per-user rename; the UI updates
  users in bulk by email via `update-users-roles` / `update-users-status`.

If any are removed, also drop: their route entries, the `can_admin_user` SQL
predicate once it has no remaining caller, and the corresponding pre-existing SQL
functions (`remove_org_user`, `update_org_user_role`, `update_org_membership_status`,
`add_org_user`).
