# Store Privacy Declaration Checklist

Last reviewed: 2026-08-16

This checklist is a release aid, not a substitute for the final questions shown by App Store Connect or Google Play Console. Store answers must match the shipping binary and enabled Firebase project settings.

## OAK Healthy positioning

- Wellness / supplement routine and adherence tracker.
- Not a medical device.
- Does not diagnose, treat, cure, or prevent disease.
- No advertising or behavioral targeting based on health data.
- Anonymous diagnostics are opt-in and OFF by default.

## Apple App Store Connect

Before submission:

1. Publish `docs/PRIVACY_POLICY.md` at a stable public HTTPS URL and enter it as the Privacy Policy URL.
2. Complete App Privacy for the app and include third-party SDK behavior.
3. Review Health & Fitness / Other User Content declarations for the exact shipping behavior. User-entered supplement routines and intake history are health-related content even though OAK Healthy does not use HealthKit.
4. If anonymous diagnostics are enabled by a user, disclose Analytics/Crashlytics data categories that Firebase actually collects for the configured SDKs and project.
5. Do not declare tracking/advertising use for OAK Healthy diagnostics unless the shipping SDK configuration changes. The Swift Package target adds `FirebaseAnalytics` but not the separate `FirebaseAnalyticsIdentitySupport` product, and OAK Healthy does not request ATT permission or set advertising/user identifiers in custom diagnostics.
6. Confirm subscription products `oak_pro_monthly`, `oak_pro_annual`, `oak_coach_monthly`, and `oak_coach_annual` are attached to the submitted version as required.
7. Keep the in-app wellness disclaimer visible and avoid medical efficacy claims in screenshots/metadata.

## Google Play Console

Before any closed/open/production test release:

1. Publish the privacy policy at a public, non-geofenced HTTPS URL and enter it in Play Console.
2. Complete **Data safety** based on the shipping SDKs and server behavior, including Firebase services.
3. Complete the **Health apps declaration** for OAK Healthy's health/wellness functionality. This declaration is required even for testing tracks.
4. State that the app is not a medical device and include the in-app disclaimer when the applicable Play health-policy flow asks for it.
5. Do not declare advertising or sale of health data. OAK Healthy has no ad SDK in this release plan.
6. Ensure the Play subscriptions use the same four stable product IDs and have active base plans/offers before testing purchases.
7. Configure the Play licensing public key used by client-side billing verification, and plan backend purchase-token verification before materially scaling commerce.

## Diagnostics privacy contract

The source code must keep these rules true:

- Collection default: OFF.
- User can enable/disable anonymous diagnostics in Settings.
- Allowed custom analytics events are limited to commercial funnel entry points and normalized store outcomes (product load, purchase result, restore result).
- No custom event field may contain client/profile identifiers, names, supplement names, doses, intake history/times, sync IDs/codes/keys, file paths, or raw server responses.
- Cloud sync telemetry exposes only coarse error type/status code.
- No user identifier is set in Firebase Analytics or Crashlytics.

## Release-blocking manual items

The repository cannot complete these account-owner actions automatically:

- Accept Apple/Google developer agreements and banking/tax requirements.
- Create/approve subscription products and pricing in App Store Connect / Play Console.
- Publish the public privacy-policy URL.
- Complete App Privacy / Data safety / Health apps declarations.
- Add tester groups, review contacts, age rating, screenshots, descriptions, support URL, and review notes.
- Submit a build for Apple/Google review.

Do not describe a release as "store launched" until these console-side items are complete and the store reports the version as available to the intended audience.
