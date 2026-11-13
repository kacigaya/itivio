# Security policy

## Supported versions

Security fixes target the latest tagged pre-release and current development.
Older 0.x versions have no separate maintenance branch. Update to the latest
version before checking whether a problem still occurs.

## Report a vulnerability

1. Use GitHub's [private vulnerability report form](https://github.com/kacigaya/itivio/security/advisories/new).
2. Include the app version or commit, Android version, affected feature, and expected impact.
3. Give the smallest steps or test case that reproduce the problem.
4. Remove personal data, real journey details, credentials, and signing keys.

If private reporting is unavailable, open an issue that asks for a private
reporting channel. Do not include the vulnerability details or an exploit in
that public issue. Share details only after a private channel is available.

Do not test against other users or disrupt public transport services. Use local
fixtures or a service you control. Report a problem in Transitous, IGN, or
OpenFreeMap to that service when the app does not cause it.

There is no fixed response or fix deadline. Keep vulnerability details private
while the maintainer reviews the report and prepares a fix.

## Scope

Report unintended disclosure of location or journeys, unsafe handling of
provider data and links, data loss, and defects that let an attacker cross an
app security boundary. Include dependency vulnerabilities when you can show
how they affect the app.

The [privacy policy](PRIVACY.md) describes intended network requests and local
storage. For ordinary bugs and feature requests, use the
[issue tracker](https://github.com/kacigaya/itivio/issues).
