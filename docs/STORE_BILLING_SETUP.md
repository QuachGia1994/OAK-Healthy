# OAK Healthy Store Billing Setup

P3.2/P3.3 connect the shared Free/Pro/Coach entitlement model to StoreKit 2 and Google Play Billing. Store metadata and localized prices remain store-authoritative.

## Stable product identifiers

Create these exact auto-renewable subscription product IDs on both stores:

- `oak_pro_monthly`
- `oak_pro_annual`
- `oak_coach_monthly`
- `oak_coach_annual`

Do not rename these IDs after release.

## App Store Connect

1. Use bundle ID `com.phongqk.oakhealthy`.
2. Create one subscription group for OAK Healthy plans.
3. Add the four auto-renewable subscriptions above.
4. Configure duration to match each product ID: monthly or annual.
5. Add localized display names, descriptions and prices in App Store Connect.
6. Configure subscription levels so Coach ranks above Pro when App Store upgrade/downgrade behavior is used.
7. Complete Paid Applications agreements, banking and tax information before sale.
8. Test with StoreKit Testing in Xcode or App Store sandbox/TestFlight before production rollout.

The app loads products with `Product.products(for:)`, derives access only from verified `Transaction.currentEntitlements`, listens to `Transaction.updates`, finishes verified transactions, and uses `AppStore.sync()` for Restore Purchases.

## Google Play Console

1. Use application ID `com.phongqk.oakhealthy`.
2. Create the four subscription products above.
3. For each product, create and activate an auto-renewing base plan matching monthly or annual duration.
4. Configure localized names, descriptions, prices and availability in Play Console.
5. Complete merchant/payments profile requirements before sale.
6. Copy the app's Base64-encoded Play licensing public key from Play Console and place it only in local build configuration:

```properties
PLAY_BILLING_PUBLIC_KEY=<base64-public-key>
```

Do not commit the local properties file. The public key is used for purchase-signature verification; a missing key makes paid entitlement fail closed.

The Android app uses Google Play Billing Library 9.1.0, queries `SUBS` products and currently owned subscriptions, never grants access for `PENDING` purchases, validates package/product/signature before entitlement, and acknowledges completed purchases.

## Production security follow-up

Client-side Play signature verification is an immediate protection boundary, not a substitute for a secure commerce backend. Before broad production scale, move purchase-token verification and acknowledgement to a backend using the Google Play Developer API and add Real-time Developer Notifications. Keep the current `EntitlementManager` and product catalog; replace only the verifier/entitlement source.

StoreKit 2 transactions are cryptographically verified by StoreKit before OAK Healthy grants iOS access.

## Test matrix before launch

- Fresh user with no purchase remains Free.
- Pro monthly and annual unlock Pro only.
- Coach monthly and annual unlock Coach.
- If both Pro and Coach are active, Coach wins.
- Cancelled purchase does not unlock access.
- Pending Play purchase does not unlock access.
- Invalid/unverified transaction does not unlock access.
- Restore Purchases recovers active access.
- Cancellation with remaining paid period keeps access until the store entitlement expires.
- Refund/revocation removes access after store state refresh.
- Product prices shown in-app match the current storefront and are never hard-coded.
