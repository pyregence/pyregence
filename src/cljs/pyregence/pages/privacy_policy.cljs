(ns pyregence.pages.privacy-policy)

(defn root-component
  "The root component for the /privacy-policy page."
  [_]
  [:div {:style {:align-items    "center"
                 :display        "flex"
                 :flex-direction "column"
                 :margin-top     "2.5rem"
                 :width          "100%"}}
   [:div {:style {:margin "1rem"
                  :width  "75%"}}
    [:h2 "PRIVACY POLICY"]
    [:p
     [:em "Last Updated"]
     " November 10, 2020"
     [:br]
     [:br]
     "The Pyregence Consortium, and each and every individual, entity and collaborator therein; (“Pyregence”, “we”, “us” or “our”) respects your desire for privacy. This Privacy Policy explains what information we collect about you, whether on our web site, mobile application, or elsewhere (collectively, the “Site”). It also explains why we collect the information, as well as how we collect and use the information."
     [:br]
     [:br]
     "This Privacy Policy is intended for individuals in the United States. If you live outside of the United States and choose to use the Site, you do so at your own risk and understand that your information will be sent to and stored in the United States. By using the Site in the United States or otherwise providing personal information to us, you agree to this Privacy Policy."]
    [:h3 "PERSONAL INFORMATION WE COLLECT."]
    [:p "“Personal Information” generally means any information that identifies you as an individual, and any other information we associate with it. We collect several categories of information, from a few different sources:"]
    [:p
     [:em "Usage Information."]
     " We passively collect information when you use the Site, through our web servers and third-party analytics tools. For instance, our system logs may record certain information about visitors to the Site, including the web request, Internet Protocol (“IP”) address, device and mobile ad identifiers, browser information, interaction with the Site, pages viewed, app usage, and other such information."]
    [:p
     [:em "Information You Might Provide."]
     " We collect information and other content you voluntarily provide us, which may include:"]
    [:ul
     [:li "User ID and password"]
     [:li "Contact information when you register or submit an inquiry, such as your name, street address, date of birth, phone number, and/or email address"]
     [:li "Geographical or location information, which may include city, county, state, zip code, country or other geographical or map input(s) such as repositioning the map view."]
     [:li "Any information you provide in communications with us, such as by e-mail or via customer service."]
     [:li "Any submissions or creations that you generate on the Site."]
     [:li "Any content or contributions you post in a public space on the Site, including comments, videos and photos that you might submit."]]
    [:p
     [:em "Non-Identifiable Information."]
     " In addition to collecting Personal Information, we may collect information that does not identify you and is not associated with your Personal Information. We may also de-identify information, so it no longer identifies you. We may also aggregate and use such information to engage other activities in a manner that does not use Personal Information and is thus outside the scope of this Privacy Policy."]
    [:h3 "COOKIES AND ANALYTICS."]
    [:p
     "We use certain cookies, pixel tags and other technologies to help us understand how you use the Site and enable us to personalize your experience. Cookies are small pieces of text. They are provided by most websites and stored by your web browser on the computer, phone, or other device that you are using. Cookies serve many purposes. They help a website remember your preferences, learn which areas of the website are useful and which areas need improvement, and can provide you with targeted advertisements or personalized content. Sometimes, cookies are enabled when pixels are placed on a website. Pixels are also referred to as web beacons, clear gifs, and tags. They enable websites to read and place cookies.  To learn more about cookies, please see the Cookies and Analytics section below, or visit "
     [:a {:href "http://www.allaboutcookies.org/"} "http://www.allaboutcookies.org/."]]
    [:h3 "HOW WE USE PERSONAL INFORMATION."]
    [:p "We may use Personal Information as permitted by law, for the following business purposes:"]
    [:ul
     [:li "to improve and personalize your experience with our Site."]
     [:li "to perform analytics, quality control, and determine the effectiveness of our Site, and develop new products and services."]
     [:li "to respond to your inquiries and to communicate with you."]
     [:li "to consider your application for employment and qualifications, and to conduct reference checks."]]
    [:p "We may also use your information as we believe to be necessary or appropriate for certain essential purposes, including:"]
    [:ul
     [:li "to comply with applicable law and legal process."]
     [:li "to respond to requests from public and government authorities, including public and government authorities outside your country of residence."]
     [:li "to detect, prevent, or investigate potential security incidents or fraud."]
     [:li "to enforce our terms and conditions."]
     [:li "to protect our operations."]
     [:li "to protect our rights, privacy, safety or property, security and/or that of you or others."]
     [:li "to allow us to pursue available remedies or limit the damages that we may sustain."]]
    [:p "If you submit any information relating to other people in connection with the Site, you represent that you have the authority to do so and to permit us to use the information in accordance with this Privacy Policy."]
    [:h3 "HOW WE SHARE INFORMATION."]
    [:p "To the extent permitted by law, and in connection with its business operations and services, Pyregence may disclose your Personal Information to the following categories of third parties:"]
    [:ul
     [:li "to individual members of the Pyregence Consortium."]
     [:li "to our affiliates for the purposes described in this Privacy Policy."]
     [:li "to our third-party service providers who provide website hosting, data analysis, infrastructure provision, IT services, email delivery services and other services, to enable them to provide services."]
     [:li "to a third party in the event of any reorganization, merger, sale, joint venture, assignment, transfer or other disposition of all or any portion of our business, assets or stock (including in connection with any bankruptcy or similar proceedings)."]
     [:li "our online and email advertisers or other third-party vendors we use who may provide cookies, pixel tags, web beacons, clear GIFs or other similar technologies for use on the Site or other websites to manage and improve our website analytics. "]
     [:li "in connection with the essential purposes described above (e.g., to comply with legal obligations)."]]
    [:h3 "YOUR CHOICES AND RIGHTS."]
    [:p
     [:em "Modifying your Personal Information. "]
     "To update or change Personal Information that you already have submitted in connection with a product registration, warranty claim, or employment application, please contact us at "
     [:a {:href "mailto:contact@pyregence.org"} "contact@pyregence.org"]
     [:br]
     [:br]
     [:em "Cookies. "]
     "You can modify your cookie settings on your browser, but if you delete or choose not to accept our cookies, you may be missing out on certain features of the Site."
     [:br]
     [:br]
     [:em "\"Do Not Track\" Settings. "]
     "Your Internet browser may contain tools to request that web sites not track your online activities. Industry standards for this technology are evolving and we currently do not respond to or take any action with respect to the \"do not track\" settings in your Internet browser."
     [:br]
     [:br]
     [:em "California Privacy Rights. "]
     "California residents have additional rights under the California Consumer Privacy Act."
     [:br]
     [:br]
     [:em "Right to Know. "]
     "California residents may request the following information from Pyregence:"]
    [:ol
     [:li "The categories of Personal Information that we collect, use, disclose, and sell, as applicable;"]
     [:li "The categories of sources from which Personal Information is collected;"]
     [:li "The business or commercial purpose for collecting or selling (if applicable) the Personal Information;"]
     [:li "The categories of third parties with whom we share Personal Information; and"]
     [:li "The specific pieces of Personal Information that we have collected about you."]]
    [:p
     [:em "Right to Delete. "]
     "California residents may request that we delete your Personal Information. Note that deletion requests are subject to certain limitations, for example, we may keep Personal Information as required or permitted by law, or to process transactions and facilitate customer and service requests."
     [:br]
     [:br]
     [:em "Right to Opt-Out. "]
     "We do not sell the Personal Information of California residents."]
    [:p
     [:em "Submitting a Request. "]
     "You have the right not to be discriminated against if you exercise your privacy rights. To make a request to access or delete your Personal Information, please email us at "
     [:a {:href "mailto:contact@pyregence.org"} "contact@pyregence.org"]
     ". Please provide your name, state of residence, home or business address with zip code, the services you utilized, so that we can authenticate and verify your request as we are required to do by law.  You may designate an authorized agent to submit a request on your behalf. In order to designate an authorized agent, please send us your written authorization for that agent. Both you and your agent must sign and date the form."]
    [:h3 "OTHER THINGS TO KNOW."]
    [:p
     [:em "Third Party Content. "]
     "The Site may link to other websites and online services. Such links are provided for your convenience only. We have no control over such third parties and if you decide to access any third-party link, you do so solely at your own risk and subject to the terms and conditions and privacy policy that third-party."]
    [:p
     [:em "Social Media Features and Widgets. "]
     "The Site may include social media features and widgets, such as the LinkedIn button. These features may collect your IP address, which page you are visiting on the Site, and may set a cookie to enable the feature to function properly. Social media features and widgets are either hosted by a third party or hosted directly on our online Services. Your interactions with these features and widgets are governed by the privacy policy of the company providing them."]
    [:p
     [:em "Minors. "]
     "The Site is not directed toward minors under age 18. If we discover that we have inadvertently collected Personal Information from a person under 18, we will delete that information immediately. If you are a parent or guardian of a minor and believe he or she has disclosed personal information to us, please contact us at " [:a {:href "mailto:contact@pyregence.org"} "contact@pyregence.org"] "."]
    [:h3 "CHANGES TO THIS POLICY."]
    [:p "We may update or change this Privacy Policy from time to time. You can see when we last updated the Policy by checking in the “Last Updated” date at the top of this page. All changes to the Policy are effective as of the date it is posted."]
    [:h3 "QUESTIONS."]
    [:p
     "If you have any questions regarding this Privacy Policy, you can contact us at "
     [:a {:href "mailto:contact@pyregence.org"} "contact@pyregence.org"
      "."]]]])
