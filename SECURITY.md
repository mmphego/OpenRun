# Security policy

## Supported versions

OpenRun is a beta. Security fixes target the current default branch and latest published release only; there is no long-term support or response-time guarantee. No signed release or hosted service is implied by this policy.

## Report privately

Use GitHub's **Security → Advisories → Report a vulnerability** if private vulnerability reporting has been enabled. That setting still needs maintainer verification. Otherwise use a private contact method published by the repository owner on their GitHub profile. If neither exists, open an issue asking for a private reporting channel without disclosing the vulnerability.

Do not post credentials, real routes, health data, working exploits against other people's systems or sensitive logs in public issues. Include the affected commit/version, a minimal synthetic reproduction, impact and any proposed mitigation. Do not test against a service without its operator's permission.

## Boundaries

The app stores fitness data locally and can export it. Exported files and screenshots may reveal locations and health information. Optional services are independently operated and require their own hardening. Provider credentials belong server-side; release signing keys belong in a restricted secret store. Fork PR workflows must never receive those secrets.

A security review, confidential reporting channel and release-signing setup remain required before broader production distribution. See [RELEASING](docs/RELEASING.md) and [ROADMAP](ROADMAP.md).
