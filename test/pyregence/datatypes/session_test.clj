(ns pyregence.datatypes.session-test
  "Whose session a question is being asked on behalf of, and what that entitles
   them to be told.

   The two role strings below are written out in nine other places, every one of
   them a bare set literal against a raw `user-role`. Nothing said what the set
   meant, so the only way to tell whether a tenth site was asking this question
   or a neighbouring one was to read all nine. These sentences say what it
   means, once."
  (:require [clojure.test                :refer [deftest is]]
            [pyregence.datatypes.session :as session]))

(deftest an-account-manager-administers-every-organization
  ;; Which is why they are asked a different question: all of them are theirs.
  (is (session/administers-every-organization?
       (session/->session "account_manager"))))

(deftest a-super-admin-administers-every-organization
  (is (session/administers-every-organization?
       (session/->session "super_admin"))))

(deftest an-organization-admin-administers-only-their-own
  ;; The line worth drawing, because the name invites the other answer. An
  ;; organization admin administers members inside one organization; they have
  ;; no standing over the roster of organizations, so they get a member's route.
  ;; `roles-who-can-see-admin-btn` says yes to them and is asking something
  ;; else.
  (is (not (session/administers-every-organization?
            (session/->session "organization_admin")))))

(deftest a-member-administers-nothing
  (is (not (session/administers-every-organization?
            (session/->session "member")))))

(deftest a-session-belonging-to-nobody-administers-nothing
  ;; "none" is a role PyreCast really reports, and it is the one an ended
  ;; session decays into. It must not be mistaken for an administrator.
  (is (not (session/administers-every-organization?
            (session/->session "none")))))

(deftest a-role-pyrecast-has-never-heard-of-administers-nothing
  ;; Fail closed. A role added server-side and not here is not an administrator
  ;; until somebody says it is.
  (is (not (session/administers-every-organization?
            (session/->session "wildfire_marshal")))))
