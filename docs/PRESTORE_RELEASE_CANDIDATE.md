# OAK Healthy Pre-Store Release Candidate

P11-CLOSE defines the final candidate that can be built and tested without paid Apple/Google developer accounts.

Candidate requirements:
- Warm Editorial/health-wellness design with corrected dark-theme contrast and the Stage A complete-product hierarchy.
- Stage B final UI RC gate passes: compact/large-text fallbacks, finite/reduced launch motion, dead-style cleanup and synthetic screenshot/demo readiness.
- Accessibility, responsive layout, failure UX, History, Coach, Sync diagnostics, post-redesign performance and synthetic presentation contracts pass.
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
