# OAK Healthy Pre-Store Release Candidate

P11-CLOSE defines the final candidate that can be built and tested without paid Apple/Google developer accounts.

Candidate requirements:
- Warm Editorial design with corrected dark-theme contrast.
- Accessibility, responsive layout, failure UX, reduced-motion, History, Coach, Sync diagnostics, post-redesign performance and synthetic presentation contracts pass.
- P8–P11 repository regression and release preflight pass.
- Android APK/build workflow passes and publishes its CI artifact.
- iOS unsigned archive/IPA workflow passes and publishes its CI artifact.
- Quality Gates enforce Android and iOS coverage thresholds.
- Diagnostics remain privacy-safe and paid entitlements remain fail-closed.

Explicitly deferred to P12:
- Apple Developer enrollment and signing/distribution.
- Google Play Console enrollment and store upload.
- TestFlight / Play Internal testing.
- Live StoreKit / Play subscription activation.
- Production rollout.
