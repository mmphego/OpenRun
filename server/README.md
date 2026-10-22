# Optional OpenRun account and integration service

Nothing is deployed or preconfigured. Core Android training remains offline. You operate the service, its backups and its HTTPS endpoint.

## Run

```sh
cd server
python -m venv .venv
. .venv/bin/activate
pip install -r requirements.txt
RWUN_DB=rwun.sqlite3 python app.py
# Production: docker compose up --build -d
```

The container binds localhost:8000. Put an HTTPS reverse proxy in front; `Caddyfile.example` shows the route. Replace the example hostname with your own DNS name and configure TLS. The Android app accepts HTTPS endpoints only; it never disables certificate verification or follows redirects with credentials.

In Android: Profile → Account & encrypted cloud backup. Enter the endpoint, create/sign into an account, and choose a separate backup encryption passphrase (12+ characters). Keep it somewhere safe: the server cannot recover it. On another device, sign in with the same account and use the same encryption passphrase before restoring.

## Implemented behavior

- Account passwords: salted PBKDF2-HMAC-SHA256, 600,000 iterations. Login/registration limited to ten attempts/minute/IP per process.
- Thirty-day opaque sessions; server stores token hashes, not raw bearer tokens. Sign-out revokes a session. Account deletion removes the account, backup and stored integration tokens.
- Android encrypts the ZIP with PBKDF2-HMAC-SHA256 (600,000 iterations, independent 16-byte salt) and AES-256-GCM (random 12-byte nonce). The server stores only the encrypted blob. Secrets/passphrase on Android use an Android Keystore key, outside the portable training backup.
- Conditional upload with `If-Match` prevents silent concurrent overwrite. A changed server revision returns 409. Export local data before resolving a conflict by restoring the remote backup. This is snapshot backup/recovery, not automatic record merging.
- Optional periodic encrypted uploads. Android may delay jobs; the app exposes last success/error. No timing guarantee is made.
- Maximum encrypted upload: 50 MiB. Store backups of the SQLite volume operationally; losing it loses the account database and encrypted backups.

## Strava setup

Register an application at Strava. Set `STRAVA_CLIENT_ID`, `STRAVA_CLIENT_SECRET`, `PUBLIC_BASE_URL=https://your.domain` and `RWUN_TOKEN_KEY` to a Fernet key. Generate that key with:

```sh
python -c 'from cryptography.fernet import Fernet; print(Fernet.generate_key().decode())'
```

Keep the key and client secret in your deployment secret store. Configure Strava's callback domain for your host; callback route is `/strava/callback`. Authorize from the app's Strava screen. One-time OAuth state expires after ten minutes. Provider tokens are encrypted at rest. Access tokens refresh server-side. Read access imports the last 30 days of running activities; an explicit per-run action uploads GPX. Strava processes uploads asynchronously: use Check latest upload to distinguish queued, completed and failed states. Rate limits and allowed athlete counts depend on your registered application.

Strava live tests require real credentials and user consent. Server tests use simulated provider responses only for callback state handling; they do not establish a real Strava connection. Delete/revoke the connection in Strava itself if you delete the server or lose its keys.

## Garmin blocker

No unofficial account access or guessed partner endpoints are implemented. Automatic workout delivery/activity retrieval requires official Garmin Activity/Training API partner approval, documentation and credentials. The service reports that blocker explicitly. FIT/GPX/TCX file import and Bluetooth HR do not depend on account sync.

## Operational limits

This is a single-process self-hosted service, not a managed SaaS. Supply HTTPS, email/account recovery, monitoring, disk quotas, volume backups and an operator security review before exposing it broadly. There is no managed hosting, password-reset email, live coach or moderation team. The in-process abuse limiter is not a distributed rate limiter.

Run tests: `python -m unittest discover -s server -p 'test_*.py' -v` from the project root.

Primary API references:
- https://developers.strava.com/docs/authentication/
- https://developers.strava.com/docs/reference/
- https://developers.strava.com/docs/uploads/
- https://developer.garmin.com/gc-developer-program/overview/

## Race catalogue and optional Gemini

See [race data and AI contracts](../docs/RACE-DATA-AND-AI.md). The server now serves `/v1/races` (public filtered catalogue) and `/v1/coach/explain` (authenticated, explicit consent, bounded summaries, rate-limited). Configure an authorised `RWUN_RACE_FEED_URL` and optional `GEMINI_API_KEY`/`GEMINI_MODEL` in the server environment. Missing Gemini credentials return 503. No deployment or credentials are supplied. Never put provider keys in the APK.
