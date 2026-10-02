# Security and release signing

## Supported release

Signed Firefin releases are published on the GitHub releases page and tagged
`firefin-v<version>`. Security reports should target the newest release or the
latest commit of the native app on `main`. Do not
include server passwords, access tokens, private URLs, full database files or
unsanitized logs in public issues.

## Native transport rules (as implemented)

These describe the code in `ServerTransport` and `Urls`. They are not a
completed security audit; open items are listed below.

- **Jellyfin TLS trust.** Jellyfin REST/media clients use platform trust and
  hostname validation. The native app has no trust-all `TrustManager`,
  `HostnameVerifier` bypass or global accept-all switch. Only the public TMDB
  artwork client adds the bundled ISRG Root X1 anchor through a scoped
  `HandshakeCertificates` client; this is not pinning or a validation bypass.
  Conscrypt 2.5.2 is installed for API-22 TLS compatibility.
- **Fire OS 5 caveat.** Fire OS 5 ships a frozen certificate store. Some
  currently valid public chains and many private Jellyfin certificates may be
  rejected. Firefin does not work around this by disabling validation; use a
  server with a certificate chain the device trusts, or a trusted local path.
  Real AFTT trust behavior has not been tested.
- **Server address.** A bare host defaults to `https://`. Addresses with
  embedded credentials, a query string, a fragment or a non-HTTP(S) scheme are
  rejected. An explicit `http://` address typed by the user is accepted; there
  is no separate consent prompt or warning yet (open item), and the manifest
  sets no cleartext policy, so platform defaults apply (API 23+ blocks cleartext
  for this target SDK; API 22 allows it). Use HTTP only on a trusted local
  network.
- **Origin binding.** Every request must match the configured scheme, host,
  port and path prefix (complete path segments, not string prefixes) and carry
  no URL userinfo; otherwise it is refused before sending.
- **Redirects.** The REST client never follows redirects, so a login or API
  request cannot forward the password or `Authorization` header to another
  origin (covered by a MockWebServer test). The media client (Media3 and
  artwork) follows at most three GET redirects and only within the configured
  origin and path; anything else fails. The media client's redirect path has dedicated MockWebServer tests for hop
  limits, cross-origin refusal and non-GET refusal.
- **Seerr credentials.** Jellyfin login never forwards the entered password to Seerr or Moonbase. Seerr authentication is a separate, explicit action from the Media Requests screen; credentials are sent only to the configured server transport after the user presses its sign-in button, and are not stored.
- **Tokens.** The access token is sent in the `Authorization` header only.
  Firefin does not append it to image, stream or HLS URLs, so it does not
  appear in image cache keys. Cookies are removed from outgoing requests.
  A helper that strips `api_key`-style parameters exists in `Urls` but is not
  wired into playback; media URLs returned by the server are used as-is if they
  are same-origin.
- **Limits.** REST responses larger than 8 MiB are rejected; REST calls time
  out after 25 s. Request cancellation cancels the underlying OkHttp call.
- **Storage.** The session (server URL, user, access token, device ID) is kept
  in app-private SharedPreferences, unencrypted, with `allowBackup="false"`.
  Logout clears server, user and token.
- **Version header.** The client identifies itself with `BuildConfig.VERSION_NAME`,
  the version from `version.properties`, matching the APK version.

## Signing key handling

The APK update identity depends on the release certificate. Keep the keystore
and its passwords outside the repository, encrypted in backup and accessible
only to release maintainers. The release workflow reads the key from the
`FIREFIN_*` secrets and pins the certificate through the
`FIREFIN_CERTIFICATE_SHA256` variable (see [BUILDING.md](BUILDING.md)); the key
should be exposed only to the protected `firefin-release` job. The secrets and
the variable are currently stored at repository level, not inside the
`firefin-release` environment, and were used to publish `0.2.3-firefin`; the
reviewer and branch rules of the environment gate the release job, but moving
the values into the environment is an open hardening step. No production key or
fingerprint is stored in this repository, and there is no debug-key release
path.

Never publish:

- `*.keystore` or `*.jks`
- `keystore.properties` or `android/keystore.properties`
- signing passwords or exported private keys
- source archives containing historical signing material

If a private signing key or password is exposed, stop using it, generate a new
key and rebuild the release. Android will treat the result as a different
signing identity, so users must uninstall the old package before installing
the replacement.

## Legacy Flutter app (history)

The legacy `org.moonfin.firetv32` app uses a compatibility transport that
**disables certificate validation** (`badCertificateCallback` returns true in
the Dart network code) to cope with the frozen Fire OS 5 certificate store.
That weakens protection against an active network attacker. It is not part of
the native app. If you still run the legacy app, use it only on a trusted local
network or VPN. Legacy builds are validation-only in CI and are not published.

## Open security items

- Explicit user consent flow for cleartext HTTP.
- Dedicated tests for media-client redirects and API 22 certificate behavior.
- Dependency lock/verification metadata (versions are pinned, hashes are not).
- Wiring token/query stripping for server-supplied media URLs, if required.

## Reporting

Open a private security report in the repository hosting platform where
available. Include affected version, reproduction steps, impact and a minimal
sanitized log.
