--------------------------- MODULE TransferCleanup --------------------------
EXTENDS Naturals

(***************************************************************************
This model keeps only the states needed to visualize three delayed operations:
a transfer confirmation, an old G1 page logout, and old-cookie cleanup.  G3A
represents any successor installed while one of those operations is delayed.

RunOldPageCleanup treats inspection and mutation as one action.  This is a
protocol model under the implementation's enforced browser-profile Web Lock,
which spans command creation, both HTTP responses, and response-side cleanup.
It does not claim that the cleanup would be safe without that serialization.
***************************************************************************)

Sessions == {"G1A", "G2B", "G3A"}
Cookies == {"G1", "G2", "G3", "none"}
ChallengeStates == {"none", "pending", "used"}

VARIABLES
    currentSession,
    epoch,
    cookieA,
    cookieB,
    challengeState,
    successorInstalled,
    revoked,
    oldLogoutHandled,
    oldCleanupHandled

vars == << currentSession, epoch, cookieA, cookieB, challengeState,
           successorInstalled, revoked, oldLogoutHandled,
           oldCleanupHandled >>

Init ==
    /\ currentSession = "G1A"
    /\ epoch = 1
    /\ cookieA = "G1"
    /\ cookieB = "none"
    /\ challengeState = "none"
    /\ successorInstalled = FALSE
    /\ revoked = FALSE
    /\ oldLogoutHandled = FALSE
    /\ oldCleanupHandled = FALSE

StageTransfer ==
    /\ challengeState = "none"
    /\ challengeState' = "pending"
    /\ UNCHANGED << currentSession, epoch, cookieA, cookieB,
                    successorInstalled, revoked, oldLogoutHandled,
                    oldCleanupHandled >>

ConfirmTransfer ==
    /\ challengeState = "pending"
    /\ challengeState' = "used"
    /\ IF currentSession = "G1A" /\ epoch = 1 /\ ~revoked
          THEN /\ currentSession' = "G2B"
               /\ epoch' = 2
               /\ cookieB' = "G2"
               /\ UNCHANGED << cookieA, revoked >>
          ELSE /\ UNCHANGED << currentSession, epoch, cookieA, cookieB,
                               revoked >>
    /\ UNCHANGED << successorInstalled, oldLogoutHandled,
                    oldCleanupHandled >>

InstallCompetingSuccessor ==
    /\ challengeState # "none"
    /\ ~revoked
    /\ currentSession # "G3A"
    /\ currentSession' = "G3A"
    /\ epoch' = epoch + 1
    /\ cookieA' = "G3"
    /\ successorInstalled' = TRUE
    /\ UNCHANGED << cookieB, challengeState, revoked,
                    oldLogoutHandled, oldCleanupHandled >>

RunOldPageLogout ==
    /\ currentSession # "G1A"
    /\ ~oldLogoutHandled
    /\ oldLogoutHandled' = TRUE
    /\ revoked' = IF currentSession = "G1A" THEN TRUE ELSE revoked
    /\ UNCHANGED << currentSession, epoch, cookieA, cookieB,
                    challengeState, successorInstalled,
                    oldCleanupHandled >>

RunOldPageCleanup ==
    /\ currentSession # "G1A"
    /\ ~oldCleanupHandled
    /\ oldCleanupHandled' = TRUE
    /\ cookieA' = IF cookieA = "G1" THEN "none" ELSE cookieA
    /\ UNCHANGED << currentSession, epoch, cookieB, challengeState,
                    successorInstalled, revoked, oldLogoutHandled >>

Next ==
    \/ StageTransfer
    \/ ConfirmTransfer
    \/ InstallCompetingSuccessor
    \/ RunOldPageLogout
    \/ RunOldPageCleanup

Spec == Init /\ [][Next]_vars

TypeOK ==
    /\ currentSession \in Sessions
    /\ epoch \in 1..3
    /\ cookieA \in Cookies
    /\ cookieB \in Cookies
    /\ challengeState \in ChallengeStates
    /\ successorInstalled \in BOOLEAN
    /\ revoked \in BOOLEAN
    /\ oldLogoutHandled \in BOOLEAN
    /\ oldCleanupHandled \in BOOLEAN

LiveSessionKeepsItsCookie ==
    /\ (currentSession = "G1A" /\ ~revoked) => cookieA = "G1"
    /\ currentSession = "G2B" => cookieB = "G2"
    /\ currentSession = "G3A" => cookieA = "G3"

OldPageCannotRevokeSuccessor ==
    currentSession # "G1A" => ~revoked

InstalledSuccessorCannotBeReplaced ==
    successorInstalled => currentSession = "G3A"

=============================================================================
