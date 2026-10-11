# Privacy Policy

Last updated: 2026-10-11

OAK Healthy is a supplement and routine tracker. It is not a medical device and does not provide medical advice.

## Data on your device

Profiles, supplements, doses, schedules, cycles, inventory, intake history and biomarker results are stored in your browser (IndexedDB). By default nothing is sent to any server. Label photos are read on your device and never uploaded.

## Account (optional)

If you create an account we store your email address, a random salt, a hash of a value derived from your password, and your sync key encrypted with a key only your password can produce. If you sign in with Google or GitHub we also store the provider's account id. We never receive your password or your unencrypted data.

## Encrypted sync, share links and circles (optional)

Synced data, shared stacks and circle views are encrypted on your device (AES-GCM). The server stores ciphertext plus technical identifiers (vault id, revision, hashed access tokens). We cannot read the content. Anyone with a full share or circle link can view it until it expires (shares: 365 days) or you revoke it (circles).

## Reminders (optional)

If you enable reminders we store your browser's push endpoint and the minutes of the day to notify you. We do not know what you take.

## Email

Password-reset emails are sent through our email provider (Resend) to the address you enter.

## What we do not do

No analytics, no advertising identifiers, no selling or sharing of data. Cloudflare may process standard request metadata (IP address, user agent) to operate and protect the service; IPs are used briefly for rate limiting.

## Deletion

**Settings → Delete everything** (also on the Account page) deletes, in this order: the circles you own (members lose access at once), this device's reminder subscription, your encrypted vault, your account with its sign-in methods, passkeys and circle memberships, then all data on this device. If a step fails, nothing after it is deleted and **Retry** continues where it stopped. Without an account, the same action deletes your circles, reminders, vault and local data.

A deleted vault leaves a tombstone with only its id (derived one-way from your sync key), a hash of its access token and the deletion time, so your other devices turn sync off instead of uploading it again.

Limits: a circle created on a device that never synced is unknown to your other devices, so run Delete everything (or Delete circle) on that device too. Share links have no owner and no delete route: anyone with the link can open one until it expires after 365 days.

You can also delete a circle or turn off reminders on their own in Settings.
