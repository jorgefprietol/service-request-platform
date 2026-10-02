# Security policy

Supported version: current `main` and the latest tagged release.

For a suspected vulnerability, use GitHub's private vulnerability reporting if available on this repository. Do not include credentials, personal data or exploit details in public issues. If private reporting is unavailable, contact the maintainer through the contact information on the GitHub profile.

The default Compose stack is bound to loopback and uses individual OIDC subjects with verified signatures, issuer, audience and expiry. Authorization uses verified realm roles. Static machine tokens require explicit development-tokens mode. The local identity provider runs in development mode; production adoption needs a production identity provider, TLS, perimeter limits and a verified backup policy. See [operations](docs/operations.md) and [roadmap](docs/roadmap.md).
