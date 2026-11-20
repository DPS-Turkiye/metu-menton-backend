# Configuration

All settings are environment variables, read by `src/main/resources/application.yml`. Locally they come from
`.env` (copy `.env.example`, which documents each one and holds safe local defaults); in production from the
server's environment. A new setting goes into `.env.example` with a safe default.

Never commit `.env`: it holds real keys.

## Core

| Variable | Purpose |
|---|---|
| `APP_EDITION` | `TEKNOKENT` or `ODTU`, required: a missing, blank or unknown value stops the app. |
| `SPRING_PROFILES_ACTIVE` | `dev` locally (permissive CORS for localhost). |
| `SERVER_PORT`, `APP_PORT` | Port inside the container, and on the host for compose. |
| `SECURITY_USERNAME`, `SECURITY_PASSWORD` | The frontend proxy's service credentials (HTTP Basic). |
| `SWAGGER_ENABLED` | `true` serves the API docs at `/api/docs`. Off in production. |
| `APP_PUBLIC_URL` | The frontend's address, used in e-mail links. |
| `APP_VERSION` | Set by the deploy workflow on released images (`--build-arg APP_VERSION=<tag>`); a release refuses placeholder secrets even on the `dev` profile, and any build outside `dev` refuses them too. |

## Data

| Variable | Purpose |
|---|---|
| `DATABASE_HOST`, `DATABASE_PORT`, `DATABASE_NAME`, `DATABASE_USERNAME`, `DATABASE_PASSWORD` | PostgreSQL. |
| `DATABASE_POOL_SIZE` | Connections per instance; keep (instances × this) under the database's limit. |
| `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD` | Redis (no password in compose; set one in production). |
| `AWS_REGION`, `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` | SES (when it is the mail transport), and S3 unless the `AWS_S3_*` ones below are set. |
| `AWS_S3_BUCKET` | Where files are stored; empty leaves storage unconfigured. |
| `AWS_S3_ENDPOINT` | An S3-compatible store (MinIO) instead of AWS S3; path-style, and needs its own keys. `APP_EDITION=ODTU` refuses to start with a bucket and no such endpoint. |
| `AWS_S3_PUBLIC_ENDPOINT` | The address presigned links are signed for, when the browser cannot reach `AWS_S3_ENDPOINT`; empty uses that. |
| `AWS_S3_REGION`, `AWS_S3_ACCESS_KEY_ID`, `AWS_S3_SECRET_ACCESS_KEY` | The store's own region and keys. Empty falls back to `AWS_REGION`, and, only with no endpoint, to the `AWS_*` keys: an S3-compatible store never gets the AWS ones. |

## E-mail

`MailService` sends every e-mail (Office Hours steps, reminders, invitations, password resets) through one
transport, chosen at start-up. A failed send is logged and never fails the request that caused it.

| Variable | Purpose |
|---|---|
| `APP_MAIL_TRANSPORT` | `ses` (the default; empty means `ses` too), `smtp`, `log` or `off`. Anything else stops the start-up. |
| `MAIL_FROM` | The sender address. With `ses`, empty sends no e-mail. With `smtp` it is required. |
| `SMTP_HOST` | The mail server, a host name or IP address alone. Required with `smtp`. |
| `SMTP_PORT` | When empty: `587`, or `465` with `SMTP_TLS=implicit`. |
| `SMTP_USERNAME`, `SMTP_PASSWORD` | The login; set both, or neither for a server that accepts this machine without one. A login needs `SMTP_STARTTLS=required` or `SMTP_TLS=implicit`. The password is never logged. |
| `SMTP_TLS` | `starttls` (the default): a plain connection that upgrades, usually port 587 or 25. `implicit`: TLS from the first byte, usually port 465. |
| `SMTP_STARTTLS` | Only with `SMTP_TLS=starttls`. `required` (the default): encrypted, or not sent. `optional`: encrypted when the server offers it. `off`: plain. |

- **`ses`** is Amazon SES with the `AWS_*` region and keys above, exactly as before the setting existed.
- **`smtp`** is for a deployment with its own mail server (ODTÜ). A missing `SMTP_HOST` or `MAIL_FROM`, half a
  login, or a bad port, `SMTP_TLS` or `SMTP_STARTTLS` value stops the start-up with a message naming the variable.
  So does a login over STARTTLS that is `optional` or `off`, because it could cross the network unencrypted;
  without a login those two only log a warning. Connecting, each answer from the server and each write time out
  after 10 seconds. The server's certificate must match `SMTP_HOST` and be trusted by the JVM. A failure is logged
  with the recipient's domain and the server's reply code, never the address.
- **`log`** sends nothing and logs each mail's subject and the recipient's domain, never the text or the full
  address. For development; it works without `MAIL_FROM`.
- **`off`** sends nothing and says so once at start-up. The app behaves as it does with an empty `MAIL_FROM`.

### What to ask the mail server's owner (ODTÜ)

1. **Host and port** of the SMTP server this machine may use.
2. **TLS:** STARTTLS (port 587 or 25) or TLS from the first byte (port 465)? Is its certificate issued for that
   host name by a public CA? If not, we need the CA certificate to add to the server's trust store.
3. **Login:** a username and password for Menton, or is this server's IP address allowed without one? If by
   address: which address must we send from?
4. **The allowed sender:** which From address may Menton use (such as `noreply@…metu.edu.tr`), and may it carry a
   display name ("ODTÜ Menton")? Where do replies and bounces to it go?
5. **Sending limits:** messages per minute, hour and day, and recipients per message. Menton sends one recipient
   per message, two at a time.
6. **SPF and DKIM** for the sender's domain: is this mail server already in the domain's SPF record, and does it
   sign with DKIM? Is there a DMARC policy? Without these, mail to addresses outside ODTÜ lands in spam.
7. **Network:** is the mail server reachable from Menton's server (firewall rule for the port)?

## Sign-in

| Variable | Purpose |
|---|---|
| `FIREBASE_PROJECT_ID`, `FIREBASE_CREDENTIALS_JSON` | Firebase Auth and push; the service-account JSON on one line. |
| `REGISTRATION_ALLOWED_EMAIL_DOMAINS` | Comma-separated domains allowed to sign up (subdomains count); empty allows any. |
| `APP_BOOTSTRAP_STAFF` | `ROLE:email:firebaseUid` entries, comma-separated (`ADMIN` or `COORDINATOR`): staff accounts created at startup when neither the uid nor the address has a row yet. Never changes an existing row; a malformed entry stops the startup with nothing created. |
| `INSTITUTION_LOGIN_DEV_STUB` | `true` replaces the ODTÜ login with a persona picker. Local only. |
| `OTG_LOGIN_ENABLED`, `OTG_ISSUER`, `OTG_USERINFO_URL`, `OTG_CLIENT_ID`, `OTG_CLIENT_SECRET`, `OTG_REDIRECT_URI` | "OTG ile giriş yap". The secret is server-side only. |
| `LTI_PLATFORM_ISSUER`, `LTI_CLIENT_ID`, `LTI_DEPLOYMENT_IDS`, `LTI_PLATFORM_AUTH_URL`, `LTI_PLATFORM_JWKS_URL`, `LTI_TOOL_LAUNCH_URL`, `LTI_TOOL_PRIVATE_KEY` | Office hours from Moodle over LTI 1.3; an empty issuer keeps LTI off. Once LTI is configured, an empty `LTI_TOOL_PRIVATE_KEY` stops start-up outside the `dev` profile. |

## AI

| Variable | Purpose |
|---|---|
| `AI_PROVIDER` | `openai` (default) or `openrouter`; anything else stops start-up. |
| `OPENAI_API_KEY`, `OPENAI_BASE_URL`, `OPENAI_MODEL`, `OPENAI_EMBEDDING_MODEL` | OpenAI. |
| `OPENROUTER_API_KEY`, `OPENROUTER_BASE_URL`, `OPENROUTER_MODEL`, `OPENROUTER_REPORT_MODEL`, `OPENROUTER_EMBEDDING_MODEL`, `OPENROUTER_APP_NAME` | OpenRouter. |
| `AI_PROFILE_PARSING_ENABLED`, `AI_SYLLABUS_ENABLED`, `AI_MATCHING_ENABLED`, `AI_MEETING_REPORT_ENABLED`, `AI_TASK_HANDOVER_ENABLED` | Per-feature switches; unset follows the edition: on in TEKNOKENT, locked off in ODTU, where `true` stops start-up (see [AI features](ai.md)). |
| `MATCHING_PGVECTOR_ENABLED` | Use the pgvector shortlist before ranking; unset follows AI matching (locked off in ODTU). |
| `TESSERACT_EXECUTABLE`, `TESSERACT_LANGUAGES`, `TESSERACT_TIMEOUT_SECONDS` | Local OCR for scanned PDFs; the image ships Turkish and English. |

## Meetings, realtime and notifications

| Variable | Purpose |
|---|---|
| `MEETING_PROVIDER_BASE_URL`, `MEETING_PROVIDER_API_KEY` | The meeting bot provider. |
| `MEETING_TRANSCRIPT_BOT_ENABLED`, `MEETING_TRANSCRIPT_BOT_INTERVAL_MS`, `MEETING_TRANSCRIPT_BOT_MAX_CONCURRENT` | The transcript bot job and how many bots may run at once. |
| `REALTIME_TOKEN_SECRET`, `REALTIME_TOKEN_TTL_SECONDS`, `REALTIME_ALLOWED_ORIGINS` | WebSocket tokens and allowed origins. |
| `REALTIME_DOCUMENT_TTL_HOURS`, `REALTIME_SEED_LOCK_SECONDS` | The live meeting note kept in Redis. |
| `DOCUMENTS_ALLOWED_ORIGINS` | Origins allowed to upload straight to the API. |
| `NOTIFICATION_REMINDERS_ENABLED`, `NOTIFICATION_REMINDERS_INTERVAL_MS` | The reminder job. |
