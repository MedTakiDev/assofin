# Assofin

Association financial management: a Spring Boot 4 / Java 21 API (`assofin/`) and an Angular 22 front end (`assofin-frontend/`).

## Run the back end locally

**Prerequisites:** Java 21 and Docker.

```bash
# 1. Create your local settings
cp .env.example .env

# 2. Generate a JWT key and paste it into JWT_SECRET_KEY in .env
openssl rand -base64 32

# 3. Start PostgreSQL and MailDev
docker compose up -d

# 4. Start the application
cd assofin
./mvnw spring-boot:run
```

The API runs on `http://localhost:8080/api/v1`, and Swagger UI on `http://localhost:8080/api/v1/swagger-ui.html`.

The first start creates the administrator from `ADMIN_EMAIL` and `ADMIN_PASSWORD`.

**In IntelliJ:** run `AssofinApplication`. Nothing else is needed, because the `dev` profile reads `.env` itself.

## How configuration works

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

## Tests

```bash
cd assofin
./mvnw verify
```

Tests need Docker (Testcontainers) but not `.env`.
