# Security Notes

## Firebase Realtime Database Rules

The app uses anonymous Firebase authentication. Security rules must enforce per-user data isolation. Deploy the following rules to Firebase Console:

```json
{
  "rules": {
    "oakBins": {
      "$binId": {
        ".read": "auth != null",
        ".write": "auth != null"
      }
    }
  }
}
```

**Note:** With anonymous auth, all users share the same `auth.uid` namespace. For production, consider implementing a mapping document that binds each binId to a specific anonymous user, or migrate to email/password authentication.

## Certificate Pinning (iOS)

Run a DEBUG build once to capture real SPKI hashes from the console output:

```
[CertPin] host=firebasedatabase.app spki_sha256=<hash>
[CertPin] host=gist.githubusercontent.com spki_sha256=<hash>
```

Paste the captured hashes into `iOS/Services/CertificatePinning.swift` in the `pinnedHashes` dictionary.

## Room Database Encryption (Android)

SQLCipher dependency is added (`net.zetetic:android-database-sqlcipher:4.5.6`). The helper is at `EncryptedDatabaseHelper.kt`.

**To enable encryption on existing installs:**

1. The passphrase is stored in EncryptedSharedPreferences backed by AndroidKeyStore
2. Existing unencrypted databases need a migration:
   - Export all data from unencrypted Room DB
   - Create new encrypted DB with `SupportFactory(passphrase)`
   - Import data into encrypted DB
   - Delete old unencrypted DB file
3. New installs get encryption from the start

**Current status:** Helper is ready but not wired into `SupplementDatabase.getInstance()`. This change requires careful testing with existing user data.

## Key Rotation

- Firebase API keys were previously committed to git. Rotate them in Firebase Console after the first release with the new gitignored config.
- EncryptedSharedPreferences master key is generated per-device via AndroidKeyStore — no rotation needed.
- iOS Keychain keys use `kSecAttrAccessibleAfterFirstUnlock` — rotation not needed for this protection level.
- Room DB encryption key: stored in EncryptedSharedPreferences, derived from AndroidKeyStore master key.
