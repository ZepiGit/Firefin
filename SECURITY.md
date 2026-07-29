# Security and release signing

## Supported release

Security reports should target the latest published FireTV32 community build.
Do not include server passwords, access tokens, private URLs, full database
files or unsanitized logs in public issues.

## Signing key handling

The APK update identity depends on the release certificate. Keep the keystore
and its passwords outside the repository, encrypted in backup and accessible
only to release maintainers.

Never publish:

- `*.keystore` or `*.jks`
- `android/keystore.properties`
- signing passwords or exported private keys
- source archives containing historical signing material

If a private signing key or password is exposed, stop using it, generate a new
key and rebuild the release. Android will treat the result as a different
signing identity, so users must uninstall the old package before installing
the replacement.

## Legacy TLS compatibility

Fire OS 5 ships a frozen certificate store that rejects some currently valid
public chains and many privately hosted Jellyfin certificates. This build
therefore uses a compatibility transport for media-server and artwork
requests that accepts certificates the platform cannot validate.

This weakens protection against an active network attacker. Use the build only
on a trusted local network or through a trusted VPN, prefer a server URL with a
valid modern certificate, and do not expose an unprotected Jellyfin endpoint
directly to hostile networks. This behavior is a documented compatibility
trade-off for Android 5.1 and should be reconsidered if the minimum Android
version is raised.

## Reporting

Open a private security report in the repository hosting platform where
available. Include affected version, reproduction steps, impact and a minimal
sanitized log.
