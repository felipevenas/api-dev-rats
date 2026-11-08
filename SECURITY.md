# Security Policy 🔒

The **DevRats API** reads commit metadata from GitHub to validate consistency in private challenges. Security and privacy are product pillars: no source code, private commit message, GitHub token, or third-party repository data may be exposed.

If you believe you have found a vulnerability, we appreciate responsible disclosure.

---

## Supported Versions

Only the latest version on `main` receives active security updates:

| Version | Supported |
| --- | --- |
| 0.1.x | :white_check_mark: |
| < 0.1.0 | :x: |

---

## Reporting a Vulnerability

**Please do not report security vulnerabilities publicly in open issues.**

1. Open a confidential report through **[Private Vulnerability Reporting](https://github.com/felipevenas/api-dev-rats/security/advisories/new)**.
2. If that feature is unavailable, contact the maintainer directly through [@felipevenas](https://github.com/felipevenas), without opening a public issue.
3. Describe the details, reproduction steps, potential impact, and any proof of concept (PoC).
4. We will respond within **48 business hours** with an assessment and mitigation plan.

---

## Backend Security Scope

The following are considered high-priority vulnerabilities:

- **Token or secret exposure**: GitHub token, `JWT_SECRET`, `TOKEN_ENCRYPTION_KEY`, GitHub App private key, or `GITHUB_WEBHOOK_SECRET` in logs, responses, traces, build artifacts, or the repository.
- **Authentication or session failure**: forged JWT, incorrect `iss`/`aud`/expiry validation, reusable refresh token, missing rotation, or refresh cookie without `HttpOnly`/`SameSite`.
- **Authorization failure**: access to another participant's group, invite, challenge, progress, or data (IDOR), including by changing IDs or invite tokens.
- **Webhook bypass**: accepting a payload without validating `X-Hub-Signature-256` or processing a repeated delivery non-idempotently.
- **Progress or result manipulation**: counting an ineligible commit, duplicating a valid day, changing a winner outside the atomic flow, or tampering with the ranking.
- **Injection**: SQL/JPQL, template, header, or log injection from input parameters.
- **SSRF or unauthorized access**: using a user-supplied URL for outbound calls to the GitHub API.
- **Data leakage**: exposure of source code, private commit messages, e-mails, private repository metadata, or personal data.
- **Insecure configuration**: permissive CORS, `local` profile active in production, unauthenticated Actuator, Swagger enabled in production, or disabled rate limiting.
- **Vulnerable dependencies** with an impact on the build or runtime.

---

## Resolution Process

1. We confirm the report and classify its impact.
2. We prepare the fix in a controlled environment with regression tests.
3. We publish the fix on `main` and record it in [`CHANGELOG.md`](CHANGELOG.md).
4. We credit the researcher, if desired, after the fix.

Frontend vulnerabilities belong to [`webapp-dev-rats`](https://github.com/felipevenas/webapp-dev-rats); report them through the same channel and identify the affected component.

Thank you for helping keep DevRats secure.
