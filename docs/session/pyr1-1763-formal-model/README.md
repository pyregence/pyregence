# PYR1-1763 session-fencing models

This ticket-scoped directory preserves the formal design and review work for
[PYR1-1763](https://sig-gis.atlassian.net/browse/PYR1-1763). It is historical
engineering evidence for that change rather than a general TLA+ subsystem.

These deliberately small TLA+ models cover the concurrency arguments in the
stale-device fencing change without producing an unreadable state graph.

- `IdleRevocation` checks that newer sibling-tab activity fences an older idle
  decision and that a rejected heartbeat cannot resurrect a revoked session.
  “Activity” here means a heartbeat the server accepted before revocation; the
  model does not cover network retry policy, absolute expiry, or liveness.
- `TransferCleanup` checks the transfer compare-and-swap, stale-page logout,
  and generation-qualified browser cleanup against a competing successor. It
  models cleanup under the enforced browser-profile Web Lock, which spans
  command creation, request/response handling, and browser cleanup. It does not
  claim delayed cleanup is safe without that lock.
- `EpochABA` isolates the defensive ABA argument for `session_epoch`. The
  production UUID generations are minted once and should never be reused, so
  the epoch is defense in depth under that assumption rather than the primary
  identity fence. Its `G1 -> Gx -> G1` change is a collapsed abstraction of
  hypothetical reuse, not an executable production transition.

Run all three models and regenerate the compact DOT, SVG, and PDF graphs plus
typeset PDFs of each specification:

```sh
./check-and-render.sh
```

Demonstrate that each safety property catches removal of its intended fence:

```sh
./check-mutations.sh
```

The script expects `tla2sany`, `tlc`, `tla2tex`, `pdflatex`, and Graphviz
`dot` on `PATH`. The generated graphs are review aids. TLC's invariant results
are the verification evidence.

The models verify their stated protocol abstractions. They are not a proof
that the Clojure, ClojureScript, and SQL implementations refine those models.
