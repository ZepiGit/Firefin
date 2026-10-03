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

- **TLS trust.** Jellyfin REST, media and playback-snapshot clients and the
  public TMDB artwork client use the platform trust store plus the bundled
  ISRG Root X1 anchor (Let's Encrypt), added through one scoped
  `HandshakeCertificates` instance. Android ships ISRG Root X1 only from 7.1.1
  on. Chain and hostname validation stay with the platform; there is no
  trust-all `TrustManager`, `HostnameVerifier` bypass, pinning or global
  accept-all switch. JVM tests cover an added anchor, a wrong hostname and an
  unknown self-signed certificate. Conscrypt 2.5.2 is installed for API-22 TLS
  compatibility.
- **Fire OS 5 caveat.** Fire OS 5 ships a frozen certificate store. Public
  chains other than Let's Encrypt and private or self-signed Jellyfin
  certificates may still be rejected. Firefin does not work around this by
  disabling validation; use a server with a publicly trusted chain, or a trusted
  local path. Real AFTT trust behavior has not been tested.
- **Server address.** A bare host defaults to `https://`; if that fails with a
  TLS error, the sign-in message suggests `http://`, but the app never switches
  scheme by itself. Addresses with embedded credentials, a query string, a
  fragment or a non-HTTP(S) scheme are rejected. An explicit `http://` address
  requires confirming an unencrypted-connection warning on every sign-in. The
  manifest permits cleartext (`usesCleartextTraffic`), so this works the same on
  Android 9 and newer, where the platform default for this target SDK would
  block it. Use HTTP only on a trusted local network.
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
  out after 25 s. Media calls have no total limit, but a read that receives no
  data for 30 s fails. Request cancellation cancels the underlying OkHttp call.
- **Storage.** The session (server URL, user, access token, device ID) is kept
  in app-private SharedPreferences, unencrypted, with `allowBackup="false"`.
  Sign-out asks the server to revoke the token (`POST /Sessions/Logout`, best
  effort) and clears the token and user ID locally; the server address and user
  name are kept to prefill the next sign-in.
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
`firefin-release` environment, and were used to publish `0.2.3-firefin`,
`0.2.4-firefin` and `0.2.5-firefin`; the
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
network or VPN. CI builds of the legacy app are validation-only; the earlier
community releases `v1.1.0-firetv32-r20` and `v1.1.0-firetv32-r21` remain on the
releases page as history and carry this limitation.

## Open security items

- Certificate behavior on physical Fire OS 5 hardware (AFTT).
- Repository-level signing secrets: move them into the `firefin-release`
  environment.
- Dependency lock/verification metadata (versions are pinned, hashes are not).
- Wiring token/query stripping for server-supplied media URLs, if required.

## Reporting

Use GitHub's private vulnerability reporting for this repository when it is
enabled. Until then, open an issue that only asks for a private contact and
contains no exploit details. Include affected version, reproduction steps,
impact and a minimal sanitized log in the private report.
