(ns pyregence.pages.terms-of-use)

(defn root-component
  "The root component for the /terms-of-use page."
  [_]
  [:div {:style {:align-items    "center"
                 :display        "flex"
                 :flex-direction "column"
                 :margin-top     "2.5rem"
                 :width          "100%"}}
   [:div {:style {:margin "1rem" :width  "75%"}}
    [:div
     [:h2 "TERMS OF USE"]
     [:p "Last Updated: September 1, 2025"]
     [:p
      "Welcome to Pyregence! We, the Pyregence Consortium, and each and every individual, entity and collaborator therein (“Pyregence”, “we”, “us” or “our”), operate web sites, research facilities, mobile applications, social media pages, and all site-related services (collectively, the “Site”). The Site is provided for your personal and noncommercial use. These Terms of Use, together with the "
      [:a {:href "/privacy-policy"} "Privacy Policy"]
      " and any documents and licenses expressly incorporated (collectively these “Terms”) govern your access to and use of the Site, whether as a guest or registered user. By using the Site, you agree to these Terms. If you do not agree to the Terms, do not use the Site. You will still be bound by the Terms as they existed when you last used the Site. We reserve the right to make changes to these Terms at any time. Please check back from time to time to ensure you are aware of any updates or changes."]
     [:p
      [:strong [:u "THESE TERMS INCLUDE IMPORTANT INFORMATION ABOUT LIMITATIONS OF LIABILITY AND AN AGREEMENT TO SUBMIT ALL DISPUTES TO INDIVIDUAL MANDATORY ARBITRATION – PLEASE READ CAREFULLY"]]]]
    [:div
     [:h3 "I. DISCLAIMER; LIMITATION OF LIABILITY (PLEASE READ CAREFULLY)."]
     [:p [:strong [:u "Disclaimer"] " THE SITE AND ALL OF ITS TEXT, IMAGES, AND SOFTWARE (COLLECTIVELY, “CONTENTS”) ARE PROVIDED ON AN 'AS IS' BASIS WITHOUT ANY WARRANTIES OF ANY KIND, WHETHER EXPRESS, IMPLIED OR STATUTORY. YOU AGREE THAT YOU MUST EVALUATE, AND THAT YOU BEAR ALL RISKS ASSOCIATED WITH THE USE OF THE SITE, INCLUDING WITHOUT LIMITATION ANY RELIANCE ON THE ACCURACY, COMPLETENESS OR USEFULNESS OF ANY CONTENT AVAILABLE THROUGH OR IN CONNECTION WITH THE SITE."]]
     [:p
      [:strong [:u "Limitation of Liability"]]
      " We recognize that some laws provide consumers specific rights and remedies and prohibit waiver of the same. Except with respect to such laws, you waive all damages under any cause of action other than actual damage for out-of-pocket loss limited to the amount you paid to access and use the Site. For example, except with respect to such laws, you waive nominal damages, liquidated damages, statutory damages, consequential damages, presumed damages, as well as the imposition of costs and attorney’s fees."]
     [:h3 "II. Data Protection."]
     [:p
      [:strong [:u "Security"]]
      " We maintain safeguards intended to protect the integrity and security of the Site. However, we cannot guarantee that the Site will be secure, complete, correct or up-to-date, or that access to the Site will remain uninterrupted."]
     [:p
      [:strong [:u "Registration; User Names and Passwords"]]
      " To use certain portions of the Site, you may be required to create an account and password. Your user name and password are for your personal use only and should be kept confidential. You are responsible for any use or misuse of your user name or password. Please promptly notify us of any confidentiality breach or unauthorized use of your user name, password, or your Site account."]
     [:p
      [:strong [:u "Third Party Web Sites; Links"]]
      " The Site links to other web sites and online services. We have no control over third parties or the individual members of the Pyregence Consortium, and their independent web sites, products or services are not governed by these Terms. We are not responsible for the availability, accuracy, or security of such other sites. We do not endorse any third-party products and services. When you navigate to a linked web site, you are departing from our Site and entering an independent or third-party venue subject to the terms and conditions, privacy policy and relevant licenses of that provider. Your use of other web sites and online services is solely at your own risk."]]
    [:div
     [:h3 "III. RULES OF CONDUCT."]
     [:p
      [:strong [:u "Follow the Law"]]
      " While using the Site, you are required to comply with these Terms and all applicable laws, rules and regulations."]
     [:p
      [:strong [:u "Respect Others"]]
      " We expect users of the Site to respect the rights and dignity of others. Do not use the Site to harass, stalk, threaten or otherwise violate the legal rights of others. Do not impersonate anyone. Do not disrupt the operation of the Site. We reserve the right in our sole discretion to restrict use, block guests and/or terminate accounts that do not comport with these Rules of Conduct and to remove any materials that violate these Terms or which we find objectionable."]
     [:p
      [:strong [:u "Use of the Site"]]
      " The Site is maintained on servers located in the United States, and is intended for users age 16 and above who are not restricted or prohibited by law or regulation to access and use the Site in the United States."]
     [:p
      [:strong [:u "Indemnity"]]
      " You agree to defend, indemnify and hold harmless us, and our directors, officers, employees, agents, affiliates, shareholders, licensors, and representatives, from and against all claims, losses, costs and expenses (including without limitation attorneys’ fees) arising out of (a) your use of, or activities in connection with, the Site, (b) any violation of these Terms by you or through your account; and (c) any allegation that any Submission or Creation (defined below) you make available or create through or in connection with the Site infringes or otherwise violates the copyright, trademark, trade secret, privacy or other intellectual property or other rights of any third party."]
     [:p
      [:strong [:u "Termination"]]
      " We may terminate your access to the Site at our sole discretion, at any time, and without prior notice. We may immediately deactivate or delete all related information and files."]]
    [:div
     [:h3 "IV. CONFIDENTIALITY OF COMMUNICATIONS."]
     [:p
      [:strong [:u "Personal Information"]]
      " Any Personal Information you submit on or through the Site is governed by our "
      [:a {:href "/privacy-policy"} "Privacy Policy"]
      ". Please do not submit through the Site any sensitive personal information, as defined by our Privacy Policy."]
     [:p
      [:strong [:u "Additional Communications"]]
      " Any other information you submit on or through the Site will be treated as non-confidential and non-proprietary. This includes all information you submit directly or indirectly (for example, through the use on a third-party social media site using a hashtag we promote)."]
     [:p
      [:strong [:u "Submissions and Creations"]]
      " Submissions and Creations (defined below) will be treated as non-confidential and non-proprietary. You acknowledge that any information in a Submission or Creation is public information."]]
    [:div
     [:h3 "V. INTELLECTUAL PROPERTY."]
     [:p "The Site and its Contents, including all trademarks, service marks, and graphical elements, are our sole property unless otherwise expressly noted and are protected by copyright, trademark, patent, and/or other proprietary rights and laws.  The Site and its Contents may also contain various third-party names, trademarks, and service marks that are the property of their respective owners. Subject to these Terms, you are granted a personal, non-exclusive, non-transferable and revocable license to use the Site solely for your own personal, non-commercial purposes and solely in accordance with these Terms."]
     [:p
      "This license is terminable at any time, and does not grant you any additional rights with respect to the Site or its Contents.  Pyregence reserves all other rights. You may not modify, alter or change any Content, or distribute, publish, transmit, reuse, re-post, reverse engineer, or disassemble the Content or any portion thereof for public or commercial purposes, including, without limitation, the text, images, audio and video.  Certain software on the Pyregence web site is open source, subject to "
      [:a {:href "https://www.eclipse.org/legal/epl-2.0/" :target "_blank"} "Eclipse Public License v2"]
      " (“EPL License”), which is expressly incorporated in these Terms.  Your use of any Content, except as provided in these Terms, including in the EPL License, without our written permission of is strictly prohibited."]]
    [:div
     [:h3 "VI. COMMERCIAL USE"]
     [:p
      [:strong
       [:u "Permitted Uses"]]
      " Use of the PyreCast platform, including all data, forecasts, visualizations, and associated modeling frameworks, is provided free of charge for:"]
     [:ul
      [:li "Non-commercial research conducted by academic institutions, government agencies, and individual researchers"]
      [:li "Educational purposes in accredited educational settings and institutions"]
      [:li "Public safety applications by emergency management, fire service organizations, and government agencies"]]]
    [:p
     [:strong
      [:u "Commercial Use Restrictions"]]
     " Commercial use is strictly prohibited without prior written authorization. Commercial use includes, but is not limited to:"]
    [:ul
     [:li "Integration into products or services offered for sale or licensing"]
     [:li "Use in fee-based consulting, analysis, or advisory services"]
     [:li "Incorporation into proprietary software applications or platforms"]
     [:li "Use to generate revenue directly or indirectly"]
     [:li "Integration into enterprise workflows or business operations"]
     [:li "Redistribution or resale of PyreCast data or derived products"]]
    [:p
     [:strong
      [:u "Data Attribution Requirements"]]
     " All uses of PyreCast data must include appropriate attribution in any publications, presentations, reports, or derivatives as follows: \"Data source: PyreCast Wildfire Forecasting Platform ("
     [:a {:href "http://pyrecast.org" :target "_blank"} "pyrecast.org"] ").\""]
    [:p
     [:strong
      [:u "Commercial Authorization"]]
     " To inquire about commercial licensing, integration partnerships, or enterprise use authorization, contact: "
     [:a {:href   "mailto:info@pyrecast.com"
          :target "_blank"
          :rel    "noreferrer noopener"}
      "info@pyrecast.com"]
     "."]
    [:div
     [:h3 "VII. SUBMISSIONS AND CREATIONS."]
     [:p
      [:strong
       [:u "On-Site Submissions and Creations"]]
      " The Site may include a variety of interactive services.  You may be able to submit information using these services (“On-Site Submissions”). You also may be able to create materials using the services (“On-Site Creations”)."]
     [:p
      [:strong [:u "Off-Site Submissions and Creations"]]
      " The same sort of interactive services may be available on certain third-party web sites and social media platforms, including independent sites of the individual members of the Pyregence Consortium. You may use these services to submit information (\"Off-Site Submissions\"), " [:em "for example, "] "using hashtags we promote, leaving a review, feedback or suggestion, or commenting on social media pages.  You may also use those third-party services to create materials (“Off-Site Creations”)."]
     [:p "On-Site Submissions and Off-Site Submissions shall, collectively, be referred to herein as “Submissions”; On-Site Creations and Off-Site Creations shall, collectively, be referred to herein as “Creations.”"]
     [:p
      [:strong [:u "Grant of Rights for Submissions and Creations"]]
      " You grant us a worldwide, non-exclusive, transferable, royalty-free, perpetual, irrevocable right and license with respect to all Submissions and Creations.  We can use this license with no compensation to you.  The license allows us: (a) to use, reproduce, distribute, adapt (including without limitation edit, modify, translate, and reformat), derive, transmit, display and perform, publicly or otherwise, any Submission and/or Creation (including without limitation your voice, image or likeness as embodied in such Submission or Creation), in any media now known or hereafter developed, for our business purposes, and (b) to sublicense the foregoing rights, through multiple tiers, to the maximum extent permitted by applicable law. The foregoing licenses shall survive termination of these Terms for any reason. To the extent that there may exist a conflict between the foregoing license and the applicable EPL License, the EPL license shall prevail and control."]
     [:p
      [:strong [:u "Representation and Warranty"]] " For each Submission and each Creation, you represent and warrant that you have all rights necessary to grant these licenses (including without limitation rights in any musical compositions and/or sound recordings embodied or embedded in any Submission or Creation), and that such Submission or Creation, and your provision or creation thereof through the Site, complies with all applicable laws, rules and regulations and does not infringe or otherwise violate the copyright, trademark, trade secret, privacy or other intellectual property or other rights of any third party. You further irrevocably waive any “moral rights” or other rights with respect to attribution of authorship or integrity of materials regarding each Submission and Creation that you may have under any applicable law under any legal theory."]
     [:p
      [:strong [:u "No Liability for Disclosure; Publically Facing Submissions and Creations"]]
      " No Submission or Creation will be subject to any obligation, whether of confidentiality, attribution or otherwise, on our part and we will not be liable for any use or disclosure of any Submission or Creation.  Further, to the extent that any Submission or Creation is made in a public space on our Site or other site, it is considered publically facing content that was intended by you to be publically accessible.  Under no circumstances do we agree to delete, modify, censor or remove any such publically facing content from our Site or other sites."]
     [:p
      [:strong [:u "You Have Sole Responsibility for Your Submissions and Creations"]]
      " You acknowledge and agree that you are solely responsible for any Submission or Creation you provide, and for any consequences thereof, including the use of any Submission or Creation by third parties. You understand that your Submissions and Creations may be accessible to other parties, who may be able to share your Submissions and Creations with others and to make them available elsewhere, including on other sites and platforms."]
     [:p
      [:strong
       [:u "We Are Not Responsible For Third Parties"]]
      " We have no control over what third parties may do with your Submission or Creation.  We have no legal liability for such misuse.  We also do not endorse and are not responsible for any opinions, advice, statements, information, or other materials made available in any Submission or Creation."]]
    [:div
     [:h3 "VIII. DISPUTE RESOLUTION TERMS (PLEASE READ CAREFULLY)."]
     [:p
      [:strong [:u "Informal Dispute Resolution."]]
      " To give us an opportunity to resolve informally any disputes between you and us arising out of or relating in any way to our Site, these Terms, or any services or products provided (“Claims”), you agree to communicate your Claim to us by emailing us at by emailing us at "
      [:a {:href   "mailto:contact@pyregence.org"
           :target "_blank"
           :rel    "noreferrer noopener"}
       "contact@pyregence.org"]
      " You agree not to bring any suit or to initiate arbitration proceedings until 60 days after the date on which you communicated your Claim to us have elapsed. If we are not able to resolve your Claim within 60 days, you may seek relief through arbitration or in small claims court, as set forth in this Section VII."]
     [:p
      [:strong [:u "Choice of Arbitrator and Rules"]]
      " Any disputes, claims, and causes of action arising out of or connected with your use of the Site (each, a “Dispute”) must be submitted exclusively to the American Arbitration Association (AAA) to be heard under their "
      [:a {:href   "https://www.adr.org/sites/default/files/Consumer_Rules_Web_0.pdf"
           :target "_blank"
           :rel    "noreferrer noopener"}
       "Consumer Arbitration Rules"]
      " The AAA’s rules and filing instructions are available at www.adr.org or by calling 1-800-778-7879.  If for any reason,  AAA is unable or unwilling to conduct the arbitration consistent with these terms, you and we will pick another arbitrator pursuant to "
      [:a {:href   "https://www.law.cornell.edu/uscode/text/9/5"
           :target "_blank"
           :rel    "noreferrer noopener"}
       "9 U.S. Code § 5"]
      "."]
     [:p
      [:strong [:u "Mandatory Arbitration"]]
      " You agree that any Dispute between us shall be resolved exclusively in individual (non-class action) arbitration.  The parties intend to be bound to the "
      [:a {:href   "https://www.law.cornell.edu/uscode/text/9/chapter-1"
           :target "_blank"
           :rel    "noreferrer noopener"}
       "Federal Arbitration Act"]
      ", 9 U.S.C. § 1 "
      [:em "et seq"]
      ".  An arbitration means there will be no jury and no judge."]
     [:p
      [:strong [:u "Class Action Waiver"]]
      " You agree that any Dispute between us shall be resolved in an individual action.  Under no circumstances will you file, seek, or participate in a class action, mass action, or representative action in connection with any Dispute."]
     [:p
      [:strong [:u "Scope of Arbitration"]]
      " The arbitrator shall exclusively determine all issues as to any Dispute, and must follow and enforce these Terms.  The arbitrator shall also determine any question as to whether any Dispute or issue is subject to arbitration.  The arbitrator shall not have the power to hear any Dispute as a class action, mass action, or representative action.  The arbitrator shall not have any power to issue relief to anyone but you or us."]
     [:p
      [:strong [:u "Exception to Arbitration"]]
      " Disputes that can be fully resolved in small claims court need not be submitted to arbitration."]
     [:p
      [:strong [:u "Choice of Venue"]]
      " You agree that any Dispute shall be heard exclusively in Contra Costa County, California unless otherwise agreed to by the parties or determined by the arbitrator.  You consent to jurisdiction in the State of California for all purposes."]
     [:p
      [:strong [:u "Choice of Law"]]
      " These Terms and your use of the Site are governed by the laws of the State of California, U.S.A., without regard to its choice of law provisions.  However, any determination as to whether a Dispute is subject to arbitration, or as to the conduct of the arbitration, shall be governed exclusively by the "
      [:a {:href   "https://www.law.cornell.edu/uscode/text/9/chapter-1"
           :target "_blank"
           :rel    "noreferrer noopener"}
       "Federal Arbitration Act"]
      ", 9 U.S.C. § 1 "
      [:em "et seq"]
      "."]
     [:p
      [:strong [:u "Remedies Available in Arbitration"]]
      " The arbitrator may grant any remedy, relief, or outcome that the parties could have received in court, including awards of attorney’s fees and costs, in accordance with the law(s) that applies to the case, except injunctive relief."]
     [:p
      [:strong [:u "Injunctive Relief"]]
      " The arbitrator may not issue any injunction.  If either party in a Dispute seeks injunctive relief, the arbitrator will complete arbitration of the Dispute, issue an award of monetary compensation (if any), and then the party seeking injunctive relief may file a new action in state in Contra Costa County, California, or federal court in the Northern District of California solely for injunctive relief.  The findings of fact and conclusions of law of the arbitrator shall not be submitted as evidence or constitute precedent in such subsequent suit."]]
    [:div
     [:h3 "IX. MISCELLANEOUS."]
     [:p
      [:strong [:u "Notices"]]
      " Notices to you may be made via posting to the Site, by email, or by regular mail, in our sole discretion.  Notices to us should be made by emailing us at "
      [:a {:href "mailto:contact@pyregence.org"} "contact@pyregence.org"]
      "."]
     [:p
      [:strong [:u "Evidence"]]
      " You agree that a printed version of these Terms and of any notice given in electronic form, including by posting to the Site, shall be admissible in any judicial or administrative proceedings based upon or relating to these Terms."]
     [:p
      [:strong [:u "Force Majeure"]]
      " We will not be responsible for any failure to fulfill any obligation due to any cause beyond our control."]
     [:p
      [:strong [:u "Severability"]]
      " If any provision of these Terms is determined to be unenforceable or invalid, such provision shall nonetheless be enforced to the fullest extent permitted by applicable law, and such determination shall not affect the validity and enforceability of any other remaining provisions."]
     [:p
      [:strong [:u "Information or Complaints"]]
      " If you have a question or complaint regarding the Site, please send an e-mail to us at "
      [:a {:href "mailto:contact@pyregence.org"} "contact@pyregence.org."]
      " Please note that e-mail communications will not necessarily be secure; accordingly, you should not include credit card information or other sensitive information in your e-mail correspondence with us. California residents may reach the Complaint Assistance Unit of the Division of Consumer Services of the California Department of Consumer Affairs by mail at 1625 North Market Blvd., Sacramento, CA 95834, or by telephone at (916) 445-1254 or (800) 952-5210."]
     [:p
      [:strong [:u "Claims of Copyright Infringement"]]
      " We respect the intellectual property rights of others. If you believe that any content on our Site or other activity taking place on our Site infringes a work protected by copyright, please notice us by e-mail at "
      [:a {:href "mailto:contact@pyregence.org"} "contact@pyregence.org."]
      " Your notice must comply with the Digital Millennium Copyright Act (17 U.S.C. §512) (the “DMCA”).  Upon receipt of a DMCA-compliant notice, we will respond and proceed in accordance with the DMCA."]
     [:p "We have also put in place a Repeat Infringer Policy to address situations in which a particular individual is the subject of multiple DMCA notices.  If we determine that an individual has violated our Repeat Infringer Policy, then we may, in our sole discretion, take any number of steps, such as issuing warnings, suspending or terminating the individual’s account, or any other measures that we deem appropriate."]]
    [:div
     [:h3 "X. CHANGES."]
     [:p
      [:strong [:u "Changes to the Site"]]
      " We may modify or discontinue the Site and its Contents at any time, in our sole discretion without notice. We reserve the right to withdraw or amend this Site, and any service or material we provide on our Site, in our sole discretion without notice. We will not be liable if, for any reason, all or any part of our Site is unavailable at any time or for any period."]
     [:p
      [:strong [:u "Changes to the Terms"]]
      " We may change these Terms at any time. We will provide reasonable notice, including by posting a revised version of these Terms, which are effective as of the date posted through the Site. Your use of our Site is subject to the Terms posted on our Site at the time of your use. Your continued use of our Site following the posting of revised Terms means that you accept to the changes. However, any changes to the Dispute Resolution Terms set forth in Section VII will not apply to any Dispute for which the parties have actual notice on or prior to the date the change is posted on our Site."]]]])
