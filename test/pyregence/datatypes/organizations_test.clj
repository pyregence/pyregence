(ns pyregence.datatypes.organizations-test
  (:require [clojure.test                      :refer [deftest is]]
            [pyregence.datatypes.organization  :as organization]
            [pyregence.datatypes.organizations :as organizations]))

(def ^:private nv-energy
  (organization/->organization "nvenergy" "NV Energy" "nvenergy-credential"))

(def ^:private pge
  (organization/->organization "pge" "PG&E" nil))

(def ^:private mine (organizations/->organizations [nv-energy pge]))

(deftest belonging-to-nothing-is-belonging-to-nothing
  (is (false? (organizations/any? organizations/none))))

(deftest the-organizations-with-psps-data-are-the-ones-psps-data-was-found-for
  ;; forecast_tabs asks this to decide whether to offer the PSPS tab, and the
  ;; capabilities build asks it to decide which utilities to list.
  (is (= (organizations/->organizations [nv-energy])
         (organizations/backed-by mine #{"nvenergy" "sce"}))))

(deftest an-organization-somebody-else-owns-psps-data-for-is-not-mine
  (is (= organizations/none
         (organizations/backed-by mine #{"sce"}))))

(deftest a-tab-an-organization-of-mine-is-allowed-is-a-tab-i-am-allowed
  (is (true?  (organizations/allowed? mine #{"pge" "sce"})))
  (is (false? (organizations/allowed? mine #{"sce"}))))

(deftest a-shared-layer-is-fetched-with-whichever-credential-is-to-hand
  ;; The Euro and NFDRS weather layers are not utility-specific, so any
  ;; organization's credential opens them.
  (is (= "nvenergy-credential" (organizations/a-credential mine))))

(deftest belonging-to-organizations-that-lent-nothing-is-holding-no-credential
  (is (nil? (organizations/a-credential (organizations/->organizations [pge])))))

(deftest a-layer-is-fetched-with-the-credential-of-the-organization-that-owns-it
  (is (= "nvenergy-credential" (organizations/credential-for mine "nvenergy"))))

(deftest an-organization-holding-no-credential-lends-none
  (is (nil? (organizations/credential-for mine "pge"))))

(deftest an-organization-that-is-not-mine-lends-me-nothing
  ;; The reason the lookup is asked of my organizations rather than of all of
  ;; them: a credential I may not use is one PyreCast should never hand over.
  (is (nil? (organizations/credential-for mine "sce"))))
