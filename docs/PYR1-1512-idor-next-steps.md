# PYR1-1512 — IDOR follow-up plan

## Where we are

The primary IDOR fix has landed: org/user admin and match-drop endpoints now
enforce **per-resource authorization** scoped to the acting user (from the
session), via the `can_admin_org` / `can_admin_user` SQL predicates and owner
checks on match drops. Per the
[OWASP IDOR Prevention Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Insecure_Direct_Object_Reference_Prevention_Cheat_Sheet.html),
that access-control check is the real control and is now in place.

The session continues to carry the internal sequential ids (`:user-id`,
`:organization-id`) and handlers read them from there — unchanged from `main`.

## Next step (Step 3): unpredictable public identifiers — defense in depth

Authorization stops the exploit; unpredictable ids additionally stop
*enumeration* (guessing/iterating ids to probe or scrape). OWASP treats this as
a supplementary layer, **not** a replacement for the checks above. This step
generalizes the existing `org_unique_id` pattern to the entities that still emit
sequential integer ids to the browser, while keeping the integer PKs internal.

### Sequential ids currently reaching the browser

| Entity | Exposed sequential id | Where |
| --- | --- | --- |
| `organizations` | `org-id` (`org_unique_id` already available) | `get-all-organizations`, `get-current-user-organization` |
| `users` | `user-id` | `get-all-users`, `get-org-member-users` |
| `match_jobs` | `match-job-id` | `match-drop/get-match-drops`, capabilities map key |
| `organization_layers` | `org_layer_id` | `capabilities/get-user-layers` |
| session | `:user-id`, `:organization-id` | serialized into the page by `triangulum.views/render-page` |

External ids (`runway-job-id`, marketplace `procurement-account-id`,
`google-user-identity`) are not sequential PyreCast ids and are out of scope.

### Recipe per entity (keep integer PKs internal)

1. **Migration**: add a `*_unique_id uuid` column, `DEFAULT gen_random_uuid()`,
   `NOT NULL UNIQUE`, and backfill existing rows.
2. **SQL**: have the client-facing functions accept/return the uuid and join on
   it. Internal-only functions keep their integer params.
3. **Handlers**: return the uuid instead of the int, and accept the uuid inbound.
4. **cljs**: swap the `:*-id` keys for `:*-unique-id`.

For the session, mirror the `organizations` approach: expose the org's
`org_unique_id` (and, once added, the user's `user_unique_id`) to the client
instead of the sequential `:organization-id` / `:user-id`, resolving back to the
integer PK server-side.

### Suggested order (each independently shippable)

1. `match_jobs` — clearest self-contained leak (`match-job-id`).
2. `organization_layers` — small, read-only (`get-user-layers`).
3. `organizations` / `users` list responses — stop emitting `org-id` / `user-id`
   in favor of the unique ids.
4. Session `:user-id` / `:organization-id` — lowest priority; these are the
   caller's own ids and only useful to an attacker once combined with a missing
   authz check, which Step 1 closed.

## Loose ends to verify (cheap, worth doing alongside)

- **Unwired endpoints**: `remove-org-user`, `update-org-user-role`, and
  `update-user-org-membership-status` are guarded now but are not called from
  cljs — confirm and either wire them up or remove them.
- Keep authorization as the primary gate; the uuid work above is
  enumeration-hardening only and must not be treated as the access control.
