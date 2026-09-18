------------------------------- MODULE EpochABA ------------------------------
EXTENDS Naturals

(***************************************************************************
The UUID generation is expected never to be reused.  This deliberately models
the defensive ABA case anyway: a challenge observes G1, the state leaves G1
and later returns to G1, and the epoch prevents the delayed challenge from
being accepted against the recycled value.
***************************************************************************)

VARIABLES
    currentGeneration,
    epoch,
    observedGeneration,
    observedEpoch,
    challengeStaged,
    challengeUsed,
    recycled,
    outcome

vars == << currentGeneration, epoch, observedGeneration, observedEpoch,
           challengeStaged, challengeUsed, recycled, outcome >>

Init ==
    /\ currentGeneration = "G1"
    /\ epoch = 1
    /\ observedGeneration = "none"
    /\ observedEpoch = 0
    /\ challengeStaged = FALSE
    /\ challengeUsed = FALSE
    /\ recycled = FALSE
    /\ outcome = "ready"

Observe ==
    /\ ~challengeStaged
    /\ challengeStaged' = TRUE
    /\ observedGeneration' = currentGeneration
    /\ observedEpoch' = epoch
    /\ UNCHANGED << currentGeneration, epoch, challengeUsed, recycled,
                    outcome >>

RecycleGeneration ==
    /\ challengeStaged
    /\ ~challengeUsed
    /\ ~recycled
    /\ recycled' = TRUE
    /\ currentGeneration' = "G1"
    /\ epoch' = epoch + 2
    /\ UNCHANGED << observedGeneration, observedEpoch, challengeStaged,
                    challengeUsed, outcome >>

Confirm ==
    /\ challengeStaged
    /\ ~challengeUsed
    /\ challengeUsed' = TRUE
    /\ outcome' = IF observedGeneration = currentGeneration
                      /\ observedEpoch = epoch
                    THEN "accepted"
                    ELSE "rejected"
    /\ UNCHANGED << currentGeneration, epoch, observedGeneration,
                    observedEpoch, challengeStaged, recycled >>

Next == Observe \/ RecycleGeneration \/ Confirm

Spec == Init /\ [][Next]_vars

TypeOK ==
    /\ currentGeneration = "G1"
    /\ epoch \in {1, 3}
    /\ observedGeneration \in {"none", "G1"}
    /\ observedEpoch \in {0, 1}
    /\ challengeStaged \in BOOLEAN
    /\ challengeUsed \in BOOLEAN
    /\ recycled \in BOOLEAN
    /\ outcome \in {"ready", "accepted", "rejected"}

RecycledChallengeIsRejected ==
    (recycled /\ challengeUsed) => outcome = "rejected"

=============================================================================
