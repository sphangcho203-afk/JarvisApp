# F.R.I.D.A.Y. Android release signing

F.R.I.D.A.Y. must use one permanent release key for every installable update. Never commit the keystore or its passwords to Git.

## One-time key creation

Run this on a trusted computer and keep the resulting file in at least two encrypted backups:

```bash
keytool -genkeypair -v \
  -keystore friday-release.jks \
  -alias friday \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000
```

Losing this keystore means future APKs cannot update an existing installation.

## Required GitHub Actions secrets

Create these repository secrets under **Settings → Secrets and variables → Actions**:

- `FRIDAY_KEYSTORE_BASE64`: base64-encoded contents of `friday-release.jks`
- `FRIDAY_KEYSTORE_PASSWORD`: keystore password
- `FRIDAY_KEY_ALIAS`: key alias, normally `friday`
- `FRIDAY_KEY_PASSWORD`: key password

Encode the keystore without line wrapping:

```bash
base64 -w 0 friday-release.jks
```

On macOS:

```bash
base64 < friday-release.jks | tr -d '\n'
```

## Release guarantees

The workflow blocks publishing from `main` unless all four secrets are present. It then:

1. reconstructs the keystore only inside the temporary runner,
2. builds the non-debug release variant,
3. verifies ZIP alignment and the APK certificate,
4. checks that `application-debuggable` is absent,
5. writes a SHA-256 checksum,
6. publishes only the signed APK and checksum.

Pull requests may build an unsigned release variant for validation, but that file is never uploaded as a user-facing APK.
