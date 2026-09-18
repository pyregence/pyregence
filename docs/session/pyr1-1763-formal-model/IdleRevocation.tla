--------------------------- MODULE IdleRevocation ---------------------------
EXTENDS Naturals

(***************************************************************************
This model isolates the race between an idle decision from one tab and a
heartbeat carrying newer human activity from a sibling tab.  One activity
advance is enough to exercise both serializations without obscuring the graph.
***************************************************************************)

VARIABLES
    live,
    everRevoked,
    lastActive,
    idleExpected,
    idleObserved,
    idleHandled,
    heartbeatPending,
    inputUsed,
    activityAdvancedAfterObservation,
    lastOutcome

vars == << live, everRevoked, lastActive, idleExpected, idleObserved,
           idleHandled, heartbeatPending, inputUsed,
           activityAdvancedAfterObservation, lastOutcome >>

MaxActivity == 1

Init ==
    /\ live = TRUE
    /\ everRevoked = FALSE
    /\ lastActive = 0
    /\ idleExpected = 0
    /\ idleObserved = FALSE
    /\ idleHandled = FALSE
    /\ heartbeatPending = FALSE
    /\ inputUsed = FALSE
    /\ activityAdvancedAfterObservation = FALSE
    /\ lastOutcome = "ready"

ObserveIdle ==
    /\ live
    /\ ~idleObserved
    /\ idleObserved' = TRUE
    /\ idleExpected' = lastActive
    /\ UNCHANGED << live, everRevoked, lastActive, idleHandled,
                    heartbeatPending, inputUsed,
                    activityAdvancedAfterObservation, lastOutcome >>

HumanInput ==
    /\ live
    /\ ~inputUsed
    /\ inputUsed' = TRUE
    /\ heartbeatPending' = TRUE
    /\ UNCHANGED << live, everRevoked, lastActive, idleExpected,
                    idleObserved, idleHandled,
                    activityAdvancedAfterObservation, lastOutcome >>

AcceptHeartbeat ==
    /\ live
    /\ heartbeatPending
    /\ lastActive < MaxActivity
    /\ lastActive' = lastActive + 1
    /\ heartbeatPending' = FALSE
    /\ activityAdvancedAfterObservation' =
          (activityAdvancedAfterObservation \/
           (idleObserved /\ lastActive + 1 > idleExpected))
    /\ lastOutcome' = "heartbeat-accepted"
    /\ UNCHANGED << live, everRevoked, idleExpected, idleObserved,
                    idleHandled, inputUsed >>

RejectHeartbeat ==
    /\ ~live
    /\ heartbeatPending
    /\ heartbeatPending' = FALSE
    /\ lastOutcome' = "heartbeat-rejected"
    /\ UNCHANGED << live, everRevoked, lastActive, idleExpected,
                    idleObserved, idleHandled, inputUsed,
                    activityAdvancedAfterObservation >>

RunIdleRevocation ==
    /\ idleObserved
    /\ ~idleHandled
    /\ idleHandled' = TRUE
    /\ IF live /\ lastActive = idleExpected
          THEN /\ live' = FALSE
               /\ everRevoked' = TRUE
               /\ lastOutcome' = "revoked"
          ELSE /\ UNCHANGED << live, everRevoked >>
               /\ lastOutcome' = "fenced"
    /\ UNCHANGED << lastActive, idleExpected, idleObserved,
                    heartbeatPending, inputUsed,
                    activityAdvancedAfterObservation >>

Next ==
    \/ ObserveIdle
    \/ HumanInput
    \/ AcceptHeartbeat
    \/ RejectHeartbeat
    \/ RunIdleRevocation

Spec == Init /\ [][Next]_vars

TypeOK ==
    /\ live \in BOOLEAN
    /\ everRevoked \in BOOLEAN
    /\ lastActive \in 0..MaxActivity
    /\ idleExpected \in 0..MaxActivity
    /\ idleObserved \in BOOLEAN
    /\ idleHandled \in BOOLEAN
    /\ heartbeatPending \in BOOLEAN
    /\ inputUsed \in BOOLEAN
    /\ activityAdvancedAfterObservation \in BOOLEAN
    /\ lastOutcome \in {"ready", "heartbeat-accepted",
                         "heartbeat-rejected", "revoked", "fenced"}

RevocationIsMonotonic == everRevoked => ~live

NewerActivityFencesOlderIdle == activityAdvancedAfterObservation => live

=============================================================================
