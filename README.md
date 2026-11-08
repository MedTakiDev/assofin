# Assofin

Association financial management: a Spring Boot 4 / Java 21 API (`assofin/`) and an Angular 22 front end (`assofin-frontend/`).

## Run the back end locally

**Prerequisites:** Java 21 and Docker.

```bash
# 1. Create your local settings
cp .env.example .env

# 2. Generate a JWT key and paste it into JWT_SECRET_KEY in .env
openssl rand -base64 32

# 3. Start PostgreSQL and MailDev (they keep running until "docker compose stop")
docker compose up -d
```

**4. Start the application in IntelliJ:** in the `AssofinApplication` run configuration, set the repository's `.env` as the env file. It provides `SPRING_PROFILES_ACTIVE=dev`, and the `dev` profile reads the rest.

From a terminal instead: `cd assofin && SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run`

The API runs on `http://localhost:8080/api/v1`, and Swagger UI on `http://localhost:8080/api/v1/swagger-ui.html`.

The first start creates the administrator from `ADMIN_EMAIL` and `ADMIN_PASSWORD`.

## Profiles

| Where | Profile | Set by |
|---|---|---|
| Your machine | `dev` | `.env` through IntelliJ, or the terminal command above |
| Every server (staging, production) | `prod` | The deployment platform |
| Tests and CI | `test` | `@ActiveProfiles` in the tests, no variable needed |

There is no fallback. **If startup stops with `Profile '${SPRING_PROFILES_ACTIVE}' must contain a letter…`, the variable isn't set.** Locally, check that the IntelliJ run configuration uses the repository `.env` as its env file. On a server, set `SPRING_PROFILES_ACTIVE=prod`.

`application.yml` holds the production values. Only `application-dev.yml` relaxes them for local HTTP: a plain `access_token` cookie without `Secure`, Swagger UI and SQL logging. So an empty or misspelled profile still runs with production-safe settings. **Every server must use HTTPS:** the production cookie (`__Host-access_token`, `Secure`) is never sent over plain HTTP.

## How configuration works

`application.yml` holds every shared setting, each read from one environment variable. The profile files only hold what differs.

`.env` is for local development only. Spring Boot reads it in the `dev` profile, and Docker Compose reads it automatically. It holds real secrets and is ignored by git. Never commit it.

**Order of precedence, strongest first:**

1. Real environment variables
2. `.env` (`dev` profile only)
3. `application-dev.yml` or `application-prod.yml`
4. `application.yml`

Production has no `.env`. Its secrets are injected as real environment variables by the deployment platform.

**`.env` format rules.** Several tools read this file, and they parse it differently. Every setting must look exactly like this, with no spaces around `=` and no quotes:

```bash
KEY=value
```

Comments go on their own line. A comment after a value becomes part of the value: `POSTGRES_PASSWORD=secret #old` makes the password `secret #old`.

## Production environment variables

Production has no `.env`. The deployment platform injects these as real environment variables. A missing required variable stops the application at startup.

| Variable | Required | Example |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | Yes | `prod` (exactly) |
| `POSTGRES_HOST` | Yes | `db.internal` |
| `POSTGRES_PORT` | No, default `5432` | `5432` |
| `POSTGRES_DB` | Yes | `assofin` |
| `POSTGRES_USER` | Yes | `assofin` |
| `POSTGRES_PASSWORD` | Yes | (secret) |
| `JWT_SECRET_KEY` | Yes | (secret, `openssl rand -base64 32`) |
| `JWT_EXPIRATION` | Yes | `24h` |
| `CORS_ALLOWED_ORIGINS` | Yes | `https://app.example.org` |
| `MAIL_HOST` | Yes | `smtp.example.org` |
| `MAIL_PORT` | Yes | `587` |
| `MAIL_FROM` | Yes | `no-reply@assofin.org` (a domain the SMTP server may send for) |
| `MAIL_USERNAME` | No | `assofin` |
| `MAIL_PASSWORD` | No | (secret) |
| `MAIL_SMTP_AUTH` | No, default `true` | `true` |
| `MAIL_SMTP_STARTTLS` | No, default `true` | `true` |
| `ADMIN_EMAIL` | First start only | `admin@example.org` |
| `ADMIN_PASSWORD` | First start only | (secret, 12+ characters) |
| `ADMIN_FIRSTNAME`, `ADMIN_LASTNAME` | No | `System`, `Administrator` |

`ProductionProfileStartupTest` starts the application with exactly the required variables, so this table and the code can't drift apart.

## Tests

```bash
cd assofin
./mvnw verify
```

Tests need Docker (Testcontainers) but not `.env`.

## Continuous integration

GitHub Actions runs `.github/workflows/backend-ci.yml` on every push and on pull requests to `main`:

| Job | What it does | When it fails, locally run |
|---|---|---|
| Build and test | `./mvnw verify`: compile, build rules (Maven Enforcer) and all tests | `cd assofin && ./mvnw -B -ntp verify` |
| Dependency scan | OSV-Scanner on the SBOM of the build | See the vulnerability id in the job log |
| Secret scan | gitleaks over the whole git history | `docker run --rm -v "$PWD:/repo:ro" ghcr.io/gitleaks/gitleaks:v8.30.1 git /repo --config /repo/.gitleaks.toml --redact` |

A failed build keeps its test reports as the `test-reports` artifact on the run's page.

**A reported vulnerability:** update the dependency. Only if no fixed version exists, add it to `assofin/osv-scanner.toml` with a reason and an expiry date.

**A reported secret:** treat it as compromised and change it wherever it is used. Deleting it from git is not enough, because it stays in the history.
