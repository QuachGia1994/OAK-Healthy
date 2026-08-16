# OAK Healthy Privacy Policy

Last updated: 2026-08-16

OAK Healthy is a wellness and supplement-routine tracking application. It is not a medical device and does not diagnose, treat, cure, or prevent disease. Information shown by the app is not a substitute for advice from a qualified healthcare professional.

## Data you enter

OAK Healthy can store client/profile names, supplement names, doses, schedules, cycle settings, reminder times, and intake history. This information is used to provide the tracking, reminder, history, cycle, export, and synchronization features requested by the user.

By default, this data is stored locally in the app sandbox on the device. OAK Healthy does not sell this information and does not use supplement or intake data for advertising, marketing, or behavioral profiling.

## Cloud synchronization

Encrypted cloud synchronization is an optional paid feature. When the user explicitly enables and configures it, OAK Healthy sends the selected profile's synchronization payload to Firebase services used by OAK Healthy. The app uses anonymous Firebase authentication for this synchronization boundary; OAK Healthy does not require a named Firebase account from the user.

Cloud synchronization link identifiers and encryption keys are operational secrets. They are never included in analytics events or custom crash-report metadata. Users should keep synchronization keys private.

Factory Reset clears local app data but intentionally does not promise deletion of remote backups. Users who want to remove a hosted synchronization relationship should revoke/unlink it before resetting the app where that option is available.

## Anonymous diagnostics

Anonymous diagnostics are OFF by default. If the user enables **Share anonymous diagnostics** in Settings, OAK Healthy may use Firebase Analytics and Firebase Crashlytics to receive coarse app-usage events and crash/stability diagnostics.

Custom diagnostic events are allowlisted. OAK Healthy does not intentionally include client IDs, client names, supplement names, doses, intake times/history, cloud link IDs, encryption keys, file paths, or raw server responses in Analytics event parameters or Crashlytics custom metadata.

The current custom analytics allowlist is limited to plan/paywall and purchase-flow entry events such as opening Plan & Access, starting a purchase, and starting purchase restoration. No advertising identifier is intentionally linked by the iOS Analytics integration.

If the user disables anonymous diagnostics, Analytics and Crashlytics collection are disabled by the app. Crashlytics unsent reports are deleted where the SDK provides that operation.

## Notifications

If notification permission is granted, OAK Healthy schedules local reminders based on the user's supplement routine. Notification content can contain information selected by the user for reminders. Notification permission can be disabled in system settings.

## Purchases

Subscription purchases are processed by Apple App Store or Google Play. OAK Healthy receives store product and entitlement information needed to determine Free, Pro, or Coach access. Payment-card information is not handled by OAK Healthy.

## Sharing and export

When the user explicitly chooses Share/Export, OAK Healthy creates the requested output and invokes the platform share UI. The user decides where that exported content is sent. OAK Healthy does not automatically publish supplement data to social networks or other third parties.

## Data retention and deletion

Local OAK Healthy data can be removed with Factory Reset or by deleting individual profiles/items in the app. App uninstall also removes app-sandboxed local data subject to platform behavior. Remote sync data is separate from local Factory Reset; revoke/unlink remote synchronization before reset when remote deletion is desired.

Anonymous diagnostic data, when enabled, is retained according to the configured Firebase/Google Analytics and Crashlytics retention controls for the OAK Healthy Firebase project.

## Third-party services

OAK Healthy currently integrates with Firebase for authentication, realtime database synchronization, App Check, and—only after user opt-in—Analytics and Crashlytics. Apple App Store and Google Play provide subscription purchase processing on their respective platforms.

## Children

OAK Healthy is not designed as a child-directed service. Do not enter another person's sensitive health information without appropriate authorization.

## Changes

If OAK Healthy materially changes how it collects, uses, or shares data, this policy and the relevant App Store / Google Play privacy declarations must be updated before releasing the changed behavior.

## Publication requirement

Before public store submission, publish this policy at a stable public HTTPS URL that is accessible without login, geofencing, or a PDF viewer. Enter that URL in App Store Connect and Google Play Console and expose the same policy or an equivalent accessible privacy view inside the app.
