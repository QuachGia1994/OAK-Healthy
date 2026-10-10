# Privacy Policy

Last updated: 2026-10-10

OAK Healthy is a supplement and routine tracker. It is not a medical device and does not provide medical advice.

## Data on your device

Profile names, supplement names, doses, schedules, cycles, inventory counts and intake history are stored in your browser (IndexedDB) on your device. By default nothing is sent to any server.

## Encrypted sync (optional)

If you turn on sync, your data is encrypted on your device with AES-256-GCM using a key only you hold. OAK Healthy's server (Cloudflare Workers + D1) stores the encrypted blob, a revision number, a random vault identifier and a hash of an access token. We cannot read your data and cannot recover it if you lose your key.

## Share links (optional)

When you share a stack, the selected routines (name, dose, time, cycle, category, note) are encrypted with a one-time key that is placed in the link's `#` fragment. The server stores only ciphertext; anyone with the full link can read the shared stack. Shares expire after 365 days.

## What we do not do

No accounts, no analytics, no advertising identifiers, no selling or sharing of data. Cloudflare may process standard request metadata (IP address, user agent) to operate and protect the service.

## Deletion

Clear the site data in your browser to delete local data. Turning off sync stops uploads; contact the maintainer to delete a vault, or delete it with the API using your sync key.