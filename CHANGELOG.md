# Changelog

All notable changes to OAK Healthy are documented here. The project follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and uses semantic
versioning where practical.

## [Unreleased]

## [2.11.0] - 2026-10-11

### Added

- Settings → Backup → Download history (CSV): your dose history for the last 30 days, 90 days or all of it, one row per dose (date, time, person, supplement, dose, Taken / Skipped / Missed, when it was recorded), ready to open in Excel or Google Sheets for a doctor or coach. The file is made on this device; nothing is sent to the server. Missed follows each routine's current schedule.

## [2.10.0] - 2026-10-11

### Changed

- A share link the server refuses to delete now stays in Settings → Share links, marked "Couldn't be deleted" with its expiry date and no Delete button, until 30 days after it expires. The mark is kept in the encrypted vault, so your other devices show it too.
- Delete everything doesn't try again to delete a link already marked as refused; it counts it among the links that couldn't be deleted.

## [2.9.0] - 2026-10-11

### Added

- Settings → Share links lists the share links you created (title, created date, expiry) and deletes one without Delete everything. A link the server refuses to delete leaves the list with its expiry date in the notice. The title stays on your devices and in the encrypted vault; a pretty link's words are public as before.
- A deleted share link is remembered in the encrypted vault for 400 days, so it doesn't come back from another device. A 2.8 device's push drops this record and the titles; a deleted link then shows again until another 2.9 device syncs.

### Changed

- Share link deletes are rate-limited per IP (120 per hour, separate from creating links). Delete everything stops at the limit and Retry continues where it stopped.

## [2.8.0] - 2026-10-11

### Added

- Share links can be deleted: the app makes a secret delete code for each new link, and the server stores only its hash (migration 0007). Delete everything now deletes the share links this account created (also those listed by your other synced devices) before the vault. The final notice says how many links it couldn't delete; those expire 365 days after they were created.
- "Everything deleted" now shows after the reload that ends Delete everything.
- Once a day the server purges vault tombstones older than 365 days and expired share links.

### Changed

- A device that already synced a vault treats a missing vault (404) like a deleted one (410): it turns sync off and says why, instead of uploading it again. A new key still creates its vault.
- Share links made before 2.8 can't be deleted (no delete code); they expire after 365 days. The privacy policy says so.

## [2.7.0] - 2026-10-11

### Added

- Delete everything (Settings and Account): one confirmed action deletes the circles you own, turns off reminders, deletes the encrypted vault and the account, then clears this device. A failed step stops with its reason, and Retry continues where it stopped.
- Deleted vaults leave a server tombstone (migration 0006: hashed ids and the deletion time only). Another device that still syncs gets 410, turns sync off and says why, instead of uploading the vault again.
- The replay floor (highest circle update a member saw) rides in the encrypted vault, so a reinstall that pulls the vault still refuses an older update. Bearer-link circles have no floor.

### Changed

- Delete account is replaced by Delete everything; the privacy policy describes what is deleted, the tombstone, and that share links expire after 365 days.
- One shared fixture checks that the app and the service worker push the same vault circle fields.

## [2.6.0] - 2026-10-11

### Added

- Server-side circle epoch (migration 0005): every owner write carries the epoch it last saw; a stale one gets 409 and the device re-bases on the vault, so two owner devices removing different members offline end with both removals and one invite. A 2.5 app can change a circle only until a 2.6 device has written to it.
- Replay guard: each circle publish carries a counter inside the sealed payload; a member device refuses an update older than one it already showed. 2.5 members still verify 2.6 updates.
- A circle whose new signing key is not synced yet says so in Settings.

### Fixed

- A vault push from the service worker no longer carries a not-yet-committed signing key.
- Old-address handoff: edited or extra profiles count as local data (only the untouched first-run profile is ignored), so such a device keeps them and sees the notice; a malformed key in a device link no longer throws.
- Delete account no longer promises a vault delete in Settings: it says the vault and circles stay on the server and to revoke circles first.

## [2.5.1] - 2026-10-11

### Changed

- The old workers.dev address hands over to https://healthy.oakgatekeeper.uk: a synced device pushes, turns off its old reminders and links the new address with its sync key, then opens the page that was asked for (invites, shares and reset links keep working); a device with no data goes straight over; a device with unsynced data shows a notice to turn on sync first. Google and GitHub sign-in accept both addresses.

### Security

- The `next=` target after linking a device is followed only when the URL parser resolves it to this origin.

## [2.5.0] - 2026-10-11

### Added

- Custom domain https://healthy.oakgatekeeper.uk; the workers.dev address keeps working (each address keeps its own on-device data).
- Circle owner state (invite secret, member pins, signing key) rides in the encrypted vault sync, so every owner device can publish and remove members; merges move rotation forward only, deletes stick.
- Signed circle snapshots: invites carry the owner's signing key (`#o=`); members refuse an unsigned or tampered update with a notice. Members who joined before 2.5 pin the first signed snapshot.
- Service worker handles `pushsubscriptionchange`: it re-subscribes when needed and moves the reminder times to the new endpoint (`POST /api/push/move`) without an open tab. If the server already dropped the old row, it subscribes the new endpoint with times worked out on the device.

### Fixed

- A circle publish racing a member removal can no longer write the old invite secret back.
- Dashboard Taken/Skipped and the notification's Taken share one dose rule (stock moves only when Taken flips).
- Two owner devices can no longer mint different signing keys for a pre-2.5 circle: a new key stays pending until the vault push commits it, and the vault's key wins.
- A vault push from the service worker keeps circle state.

## [2.4.0] - 2026-10-11

Everything deployed since 2.1.0; the 2.2 and 2.3 rounds went live without their own version.

### Added

- Circles with member accounts: invite links `/c/<id>#j=<secret>`; people join with their own OAK account and get the snapshot through a per-member ECIES key wrap. Each publish uses a fresh content key; Remove rotates the invite secret and re-publishes so the others keep access. D1 migration `0004_circle_members.sql` (tables `member_keys`, `circle_members`, column `circles.join_hash`). Bearer-link circles are unchanged.
- Reminder notifications: “Taken” marks the listed doses on-device (intake + stock, same rule as the dashboard, skips cycle off-days) and refreshes open tabs; Snooze is now 10 min.
- Live camera barcode scanning (getUserMedia + BarcodeDetector/ponyfill, check-digit-valid GTINs only); falls back to a photo when the camera is unavailable.
- Public `/privacy` page (English + Vietnamese) linked from the sign-in screens; required by the Google sign-in consent screen.
- Readable share URLs `/s/<handle>/<slug>` with link previews (Open Graph + Twitter card). Only the handle, the slug words and the item count are public; the stack stays encrypted with the key in the `#fragment`. Old `/s/<id>` links keep working and get a generic preview.
- Background Sync: offline edits or network-failed pushes register a one-off `oak-sync`; the service worker hands it to an open tab, or syncs the encrypted vault itself (same HKDF/AES-GCM + last-writer-wins) when no tab is open.
- Stack table: sortable columns (name, time, cycle, inventory, status; asc/desc, untracked stock last) plus a sort menu on small screens.
- Haptic feedback (Vibration API) on Taken/Skipped, pause, share and schedule apply; respects reduced motion.
- Inventory: reorder link (http/https only) and shelf life after opening, with “expires in”/“expired” alerts on the dashboard and in the stack table.
- Chrono-timing: “Suggested schedule” in Insights moves doses to slots that satisfy timing rules and split clashing pairs, with per-item Apply, Apply all and Undo.
- Biomarkers: adherence-vs-marker chart (30-day adherence before each lab draw) with Pearson r and a plain-language strength label.
- Device handoff by QR: Settings shows a 60-second QR that opens `/settings/#link=<key>` on the other device; the key stays in the URL fragment and is wiped from the address bar on arrival.
- Account → Sign-in methods: change your password (re-wraps the same sync key; other sessions are signed out), add a password to a Google/GitHub-only account, link or unlink Google/GitHub (never the last method). Linking an identity owned by another account no longer switches accounts.
- Reminders: per-routine toggle (routine drawer and Settings chips) and Snooze 10 min / 1 hour actions on the notification. The server stores only one pending snooze time per push endpoint (migration `0003_push_snooze.sql`).
- Barcode lookup next to label OCR: the barcode is decoded on the device (native BarcodeDetector or lazy ZXing WebAssembly) and only the number is sent, from the browser, to Open Food Facts to prefill name, dose and count. Manual entry with check-digit validation as fallback.

### Fixed

- Reminders → Send test: re-reads the browser's push subscription, re-registers it with the server and retries once on 403/404/410, instead of failing with “Push service rejected the test”.
- Reminders: Chrome subscriptions on `*.google.com` push hosts (e.g. `jmt17.google.com`) are accepted; they failed with “Reminder sync failed (400)”. The error now includes the server's reason.
- Account deletion also removes the account's member keys and circle memberships.
- Base64 encoding of large vaults is chunked, so snapshots near the 1.5 MB limit no longer overflow the call stack.
- Label scan matches names regardless of spaces and hyphens ("Vitamin D-3", "Omega 3").
- Every knowledge-base entry now cites a source (13 entries had none).
- Vietnamese UI: greeting, toast region label, the default shared-stack title and the Notes prefill (dictionary advice) are translated.
- Label scan reads Vietnamese labels too (`eng+vie` OCR) when the UI is in Vietnamese.

## [2.1.0] - 2026-10-10

### Added

- Accounts: email + password sign-up / sign-in / sign-out, forgot and reset password (Resend email, 30-minute single-use token — active once `RESEND_API_KEY` is set), Google and GitHub sign-in (OAuth code + PKCE + HttpOnly state cookie — active once the client secrets are set), account deletion. Zero-knowledge: PBKDF2 (600k) on device wraps the sync key; the sync key is the recovery code.
- Passkey vault unlock via WebAuthn PRF.
- Web Push reminders (VAPID, `ReminderClock` Durable Object alarm every minute instead of a cron trigger, push-service host allowlist, payload-free pushes composed by the service worker).
- Label scan: on-device OCR (tesseract.js) prefills name, dose, pill count and serving size.
- Biomarkers on Insights: 14 markers with typical ranges, status chips and trend lines; synced in the vault.
- Family & coach circles: live read-only encrypted profile views at `/c/<id>#<key>`, revocable.
- Knowledge base grows to 37 entries with aliases, food timing, sources and new clashes / synergies.
- Vietnamese UI with automatic detection and a language toggle.

### Changed

- Mobile layout for iPhone 16 Plus and Meizu Lucky 08: 44 px touch targets, ≥ 12 px text, 16 px inputs (no iOS zoom), single-column cards, safe-area padding, toasts above the bottom nav, Duplicate available in the drawer.
- Worker split into `auth.ts`, `push.ts`, `util.ts`; CSRF Origin check, D1 rate limits; 500 responses no longer include error details.
- D1 schema extended (expand-only): users, identities, sessions, reset_tokens, oauth_states, rate_limits, wrapped_keys, push_subs, circles.

## [2.0.0] - 2026-10-10

### Changed

- OAK Healthy is now a single cloud web app / PWA (Next.js 15, React 19, Tailwind v4) deployed on Cloudflare Workers at https://oak-healthy.kim-phong619.workers.dev. Native Android and iOS apps, Firebase sync, store billing and mobile CI/QA gates are removed (still available in git history).
- Sync is rebuilt as a zero-knowledge vault: HKDF-derived vault id / auth / AES-256-GCM key, ciphertext stored in D1 with optimistic revisions and last-writer-wins merge with tombstones.

### Added

- Dub.co-style UI: Today bento dashboard (adherence ring, next-dose countdown, 7-day sparkline, streak, slot timeline), Stack manager table (search `/`, create `C`, category/status filters, drawer editor), Insights (12-month heatmap, time-of-day histogram, per-routine adherence, history), Settings.
- `⌘K` command menu, Undo toasts, dark mode, multi-profile switcher.
- Interaction engine (clashes, synergies, chrono-timing warnings) and dictionary autofill.
- Inventory tracking with auto-decrement and low-stock warnings.
- Encrypted shareable stack links (`/s/<id>#<key>`) with copy-to-my-stack.
- Offline support via IndexedDB and a service worker; JSON export/import.
- TypeScript ports of DoseTimingPolicy, CalculateCycleUseCase and the local-day boundary with node:test coverage.

### Removed

- `Android/`, `iOS/`, `firebase/`, `fixtures/`, `scripts/` (mobile release gates), mobile workflows, store/QA/commercial docs, build logs and IDE caches.

## [1.x mobile] - archived, never released from Unreleased


### Added

- App Shell Closure adds cross-platform scroll-minimized primary navigation: Android collapses the 80dp icon+label bar to a 56dp icon-only bar on downward user scroll and restores it on upward scroll, while iPhone on iOS 26+ uses the native `tabBarMinimizeBehavior(.onScrollDown)` with an iOS 17+ fallback and an automated shell/inset/token gate.
- Health-data integrity hardening adds canonical cross-platform dose timing/completion policies, explicit local-day boundaries/codecs, iOS SwiftData mutation owners for clients/routines/history, stable-ID Safe Mode recovery targeting, and focused DST/idempotence/recovery persistence tests plus a current health-data-flow architecture map.
- Stage B Final UI/UX Release Candidate adds compact/large-text fallbacks for Stack/Coach/Settings/Plan Access, finite reduced-motion launch branding, deterministic screenshot/demo documentation, dead presentation-token cleanup, and a dedicated cross-platform RC gate wired into Quality Gates and the unified regression matrix.
- Stage A Complete Product UI Redesign unifies Stack, Coach, Settings/Profile, Onboarding, Plan Access, Sync/Recovery, notification diagnostics and Safe Mode across Android/iOS, with progressive technical disclosure and a single cross-platform presentation contract.
- UI-R2 History & Trends Scanability adds cross-platform completion-ring/trend hierarchy, visible result/day counts, unified activity-chart styling, compact search/segmented filters, and continuous date-grouped History timelines without per-record cards.
- UI-R1 Health & Wellness redesign adds cross-platform spacing/radius/type-scale tokens, a flat navigation shell, a single progress-led Home insight surface, a continuous Add/Edit Supplement form surface, and an automated presentation contract/QA matrix.
- P12 Store Activation adds Android API 36/Xcode 26 store toolchain readiness, explicit no-upload versus execute-store workflow modes, fail-closed missing-credential jobs, stable catalog/store activation gates, and a non-fabricated external-account runbook.
- P11.2–P11.10 and P11-CLOSE add cross-platform accessibility/touch-target policies, responsive metric layouts, reduced-motion behavior, failure-safe Sync recovery copy, progressive technical diagnostics, denser History/Coach signals, a synthetic-only presentation pack, and a pre-store completion gate/QA matrix.
- P11.1 UX Polish 4.0 adds shared Android/iOS feedback surfaces, explicit History load-error/true-empty/no-match recovery states, Coach locked/empty/error feedback, EN/VI parity, and a repository UX contract/QA matrix.
- P10.2–P10.4 add bounded persistence/background-work budgets, AES-GCM tamper/security/privacy regressions, Firebase/backup/secret-scan contracts, and one `scripts/oak_regression.py` command for the full deterministic P8–P10 repository matrix.
- P10.1 Architecture Decomposition adds typed sync status/log boundaries, injectable notification diagnostics, Coach source providers, iOS bootstrap/import/lifecycle coordinators, a current-state architecture map, and a repository boundary regression gate.
- P9.5 Activation & Retention 2.0 adds cross-platform first-value milestones, actionable empty/recovery states, pressure-free recent-rhythm feedback, existing-user milestone reconciliation, and consent-gated aggregate activation telemetry restricted to milestone/state enums.
- P9.4 Coach Workspace 3.0 adds client drill-down, same-client current-vs-previous 7/30/90-day comparisons, client filters, bounded local routine check-ins, renderer-neutral report documents, Android/iOS workspace tests, and a non-medical/local-only repository gate.
- P9.3 Sync Engine 2.0 adds durable dirty-part queues, sanitized operation journals, deterministic conflict previews, exponential retry/backoff state, and cross-platform local-first regression gates.
- P9.2 Reminder Reliability 3.0 adds automatic Android/iOS schedule reconciliation, lifecycle/time-change recovery, platform diagnostics, and a fail-closed repository gate that forbids intake-history mutation from recovery paths.
- P9.1 Data Recovery 2.0 adds detailed no-write import previews, duplicate/orphan collision gates, pre-restore snapshots with rollback, Android↔iOS fixtures, and complete supported-schema/database migration matrices.
- P7.1–P8 reliability/maturity work: cross-platform reminder health and repair paths, 7/30/90-day Coach reports, typed sync recovery states, semantic backup integrity manifests, adaptive report/demo layouts, synthetic QA scenarios, and a repository product-readiness CI gate.
- New Android/iOS backup fixtures assert the same canonical SHA-256 digest and reject payload changes before import persistence mutation.
- P5.1A store-neutral commerce lifecycle/replay core on Android and iOS with retry-safe authority-unavailable behavior, fail-closed downgrade states, and unit fixtures; it is not a runtime premium unlock path.
- P5.2–P6 product work for deferred-store development: plan-preview fallback, first-value onboarding guidance, entitlement-gated Coach Overview, overdue recovery CTA, reminder-rebuild visibility, EN/VI copy, and debug-only read-only synthetic demo screens.
- Phase 4 commercial validation/growth gates: machine-checkable real-store evidence, production candidate matching, privacy-safe aggregate KPI reporting, and a P4.1–P4.4 operations runbook/templates.
- Commercial funnel diagnostics now cover store product-load, purchase-result, and restore-result outcomes with allowlisted product/plan/period/store metadata only.
- An explicit iOS SwiftData schema v1/migration-plan baseline plus a regression fixture that writes an existing unversioned store and reopens it through the versioned container without losing client, supplement, or intake data.
- Android Room v2→v6 migration regression coverage and shared Android/iOS fixtures for legacy backup identity compatibility.
- Beta/store release preflight, safe beta/internal defaults, explicit production confirmation, Play billing-key release wiring, launch checklist, listing copy, and signed iOS Crashlytics dSYM upload.
- Privacy-first Firebase Analytics/Crashlytics diagnostics on Android and iOS with explicit opt-in, allowlisted commercial funnel events, health-data field scrubbing, in-app disclosure, and store privacy declaration guidance.
- StoreKit 2 and Google Play Billing 9.1 subscription flows with store-localized pricing, purchase/restore handling, verified entitlement resolution, and fail-closed pending/unverified purchase behavior.
- Cross-platform Free/Pro/Coach entitlement policy, stable subscription product catalog, Plan & Access UI, and monetization delivery roadmap.
- Cross-platform AES-GCM interoperability and revision validation tests.
- In-app sync security guidance, unlink controls, and protected key display.
- Durable iOS notification actions for Taken and Skipped dose events.
- Public contribution, governance, security, and roadmap documentation.

### Changed

- Android `Scaffold.bottomBar` now owns primary navigation and consumes the system navigation inset exactly once; iOS palette ownership moved into `OAKDesignTokens.swift`, and `DESIGN.md` now mirrors the current moss/paper token system instead of the retired teal/gradient/glass language.
- History and Coach derived metrics now consume one timing/completion policy per platform; Android history queries use half-open local-day ranges and iOS date-only backup/sync values preserve the literal calendar day instead of converting through UTC.
- iOS Views no longer directly insert/delete/save SwiftData health models; persistence mutations are delegated to explicit mutation-store boundaries and local Coach check-in corruption/save failures are surfaced instead of silently becoming empty data.
- Legacy iOS export upgrade no longer contains a name-based merge path; ID-less routines are converted to deterministic identities before entering the preview/restore pipeline.
- Stage B removes decorative infinite launch animation and obsolete glass/elevation/hero/chart/badge presentation tokens, makes synthetic Demo Preview follow the same continuous-row wellness hierarchy as production UI, and preserves all health/business behavior while closing the redesign program.
- Stage A removes remaining glass/material fragmentation from core iOS flows, consolidates Android onboarding to Client → Reminders → Done while preserving exact-alarm/battery actions, replaces card-per-supplement/client/alarm/log patterns with continuous rows, and reorganizes paywall/sync/recovery hierarchy without changing health/business logic.
- History now prioritizes completion/trend over raw intake totals, uses the same wellness accent language for 7/30-day trends and 7-day activity, replaces glass/material search treatments, and renders log records as continuous divider rows grouped by date.
- Home no longer fragments Due/Missed/Taken/Skipped into four standalone metric cards; both platforms now place semantic metrics and overdue recovery inside one primary daily progress surface. Add/Edit Supplement now uses one divided form surface instead of a separate card for Details, Timing and Rhythm.
- Android/iOS wellness colors now use a quieter neutral background/raised-surface system with stronger dark-mode contrast and a restrained green accent; bottom navigation no longer uses floating glass/material treatment.
- Android compile/target SDK moved to API 36 with AGP 8.10.1 and Gradle 8.11.1; iOS build/quality/store workflows now select the current stable Xcode and enforce an Xcode 26+ floor for 2026 store submissions.
- Release workflow now runs readiness-only by default and requires `execute_store_uploads=true` before any App Store Connect or Google Play upload; requested execution with missing credentials fails instead of reporting a successful skip.
- Dark-theme paper, secondary-text, outline and hairline tokens now use higher-contrast values on Android and iOS; Sync technical metrics are hidden behind progressive disclosure and iOS Sync log surfaces no longer use per-row blur material.
- History now distinguishes loading, load failure, true empty and filtered no-match states with explicit retry/client-management actions; touched iOS History diagnostics expose only aggregate client presence and error type rather than raw client identity/error text.
- iOS Settings status rows now mirror Android long-label protection by giving labels flexible width while keeping trailing status/timestamp values single-line and right aligned.
- History/Coach reads now use bounded date windows, sync dirty checks use existence-sized database queries, Android periodic fallback sync is 30 minutes, and iOS realtime fallback polling never runs faster than 30 seconds during recent activity.
- Sync, notification, Safe Mode recovery, bootstrap and Coach repository access now have explicit service/domain owners instead of keeping raw persistence/platform orchestration inside large views and ViewModels; behavior and data schemas are unchanged.
- Store execution is explicitly deferred until developer accounts are desired; repository commerce/store gates remain intact and do not block continued product development.
- About/onboarding/recovery copy now consistently positions OAK Healthy as a wellness routine tracker for individuals and coaches without diagnosis/treatment claims.
- Production store promotion now requires an explicit beta-tested candidate commit/version/build and release-specific P4 evidence; iOS reuses that TestFlight build and Android promotes that Play versionCode instead of rebuilding production binaries.
- iOS production bootstrap now constructs its persistent `ModelContainer` from the versioned SwiftData schema and central migration plan.
- Firebase payload and revision reads are now atomic snapshots.
- Firebase writes now use compare-and-set transactions with monotonic revisions.
- Android and iOS sync settings now separate pending Link Code input from the
  active link.
- Three-tab navigation, filters, app icon, and loading identity were redesigned
  consistently across Android and iOS.
- Android card rendering and accessibility semantics were streamlined to reduce
  scroll frame spikes on physical devices.
- iOS simulator Debug builds use ad-hoc signing so Keychain-backed encryption
  tests run under GitHub Actions without changing unsigned Release archives.

### Fixed

- Android bottom navigation no longer risks double-applying the system navigation-bar inset on gesture or 3-button navigation devices.
- Android `removeIntake(supplementId, date)` now deletes only the requested local calendar day instead of ignoring the supplied date and deleting today's history; exact next-midnight records are excluded by half-open query bounds.
- iOS pending-import recovery now uses the stored client UUID when present, rejects ambiguous legacy name-only targets and surfaces rollback persistence failure instead of swallowing it.
- Google Play product refresh now clears stale cached purchase offers before rebuilding the active offer map, preventing a removed/reconfigured store offer from remaining selectable in the client session.
- Android Settings rows now reserve flexible width for long labels and keep trailing values on one line, preventing the latest reminder rebuild timestamp from collapsing into a narrow vertical column.
- Android sync tombstones now compare the remote effective timestamp against both local update and deletion timestamps, so an older remote deletion cannot erase a newer local edit; equal-timestamp conflicts keep local state on both platforms.
- Backup restore no longer silently drops orphan or high-volume history; future schemas and incomplete recurrence payloads now fail closed, and Safe Mode requires a stable preview before applying downloaded data.
- P7/P8 Android backup identity compatibility now stays within the enforced core function-length gate.
- Android consumed one-shot reminders now remove their schedule-registry entry when the broadcast is received, preventing successfully delivered alarms from being reported as stale by reminder-health diagnostics.
- Android Coach Overview no longer imports Compose's internal `weight` symbol, fixing the `defc7bf` compile regression; iOS SwiftData schema version identity is now computed rather than stored as a non-Sendable static value under Swift 6 concurrency checks.
- Android Room v2→v3 now stages intake history before rebuilding the supplement table, preventing foreign-key cascades from deleting legacy history during migration.
- Legacy v1 and ID-less backup payloads now derive the same deterministic supplement identity on Android and iOS, preventing cross-platform duplicate identities after import or sync.
- iOS dose-time and supplement deletion now await notification cleanup before returning, and nil-client autosync requests no longer retain short-lived SwiftData contexts after teardown.
- Android periodic cycle workers now use a WorkManager-compatible constructor.
- Android backup imports now preserve interval/last-taken data and remap cross-profile ID collisions without breaking history links.
- Android background cloud sync now runs without constructing `HomeViewModel`, shares active-client/log persistence services, and retries engine-reported failures instead of silently treating them as success.
- Android startup now uses a dedicated dependency factory and testable theme/splash/notification policies; `MainActivity.onCreate` no longer owns database or ViewModel construction.
- Factory reset now stops sync, clears pending notifications, removes local profile/history data, wipes app preferences and cloud encryption material on both Android and iOS.
- Android reminders fall back safely when exact-alarm access is unavailable and no longer re-enable an in-app notification opt-out.
- Cross-platform sync now preserves newer deletion tombstones, keeps the revision returned by Android conflict retries, and avoids multi-client history truncation on iOS.
- iOS manual and automatic cloud sync now share one serialized engine, including legacy single-bin fallback and stale-error cleanup.
- iOS cloud sync no longer carries the unused JSONBin HTTP transport or secret injection; orchestration now lives in a dedicated coordinator with regression-tested serialization/fallback/conflict handling.
- iOS client-scoped SwiftData reads now scope through the target profile before applying record limits, preventing other profiles from hiding History or sync records and reducing fetch-all work.
- Notification scheduling and dose actions now stay isolated to the active profile on Android and iOS, including profile switches, reboot/time-change rescheduling, and stale notification actions.
- Cloud host/link ownership is now scoped per profile on Android and iOS; legacy global links migrate once to the active profile, profile switches rebind realtime sync, and Android in-flight exports retain their captured profile.
- iOS realtime cloud sync sessions now bind to both profile and manifest identity, reject stale generation completions, and cannot let an old listener clear or mark a newer profile session.
- Android profile create/update/delete flows now commit persistence before changing active-profile or dialog state, preventing duplicate-name or database failures from creating phantom active clients.
- Android alarm receivers now retain their broadcast lifetime while asynchronous reminder validation and rescheduling complete.
- Cloud hosting now cleans up partial uploads, and iOS re-hosting keeps the previous host until the replacement is ready.

### Security

- Android/iOS AES-GCM sync now has explicit ciphertext-tamper regression coverage, and CI verifies Firebase auth/revision/payload rules, OS backup exclusions, platform key-storage contracts, diagnostics allowlists, plaintext-downgrade rejection, and strong committed-secret signatures.
- Cloud/debug telemetry no longer emits client IDs, cloud link IDs, raw server responses, file paths, or raw error text; analytics and crash collection are disabled by default until the user opts in.
- Malformed encryption envelopes now fail closed.
- Firebase identifiers and encryption key IDs are validated before use.
- In-place encryption-key rotation was removed to prevent inaccessible cloud
  data.
- Firebase database rules now validate node shape, payload size, and strictly
  increasing revisions.

[Unreleased]: https://github.com/QuachGia1994/OAK-Healthy/commits/main
