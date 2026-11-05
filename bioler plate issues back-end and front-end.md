# Assofin Boilerplate: Roadmap, Back End and Front End

> **Scope:** the admin-provisioned account approach (no public registration). Spring Boot 4.1 / Java 21 back end,
> Angular 22 / PrimeNG front end.
> **Status of this file:** a plan only. No code was changed. It follows on from
> `issues and resolve prompts-correction.md`.
> **Baseline verified on 2026-11-05** at commit `3417d4b`: `./mvnw test` passes (57 tests), `ng build` **fails**.

## How to read this file

1. Read [section 1](#1-where-the-project-stands) to see the starting point.
2. Read [section 2](#2-architecture-decisions) for the decisions everything else depends on.
3. Read [section 3](#3-refresh-tokens-and-redis) for the refresh-token design.
4. Work through [section 7](#7-the-issues-in-order) in order. Each milestone unlocks the next one.

**Each issue lists:** what it replaces, what it depends on, why it exists, every class or component it touches, its
tests, and how to know it's done.

**Actions used in the tables:** `NEW` create · `CHANGE` modify · `MOVE` relocate without behavior change · `DELETE`
remove.

**Size:** S = one small commit · M = a few commits · L = a dedicated branch.

---

## 1. Where the project stands

| Area                   | State                                                                          |
|------------------------|--------------------------------------------------------------------------------|
| ✅ Closed registration | Register, activation and Redis removed                                         |
| ✅ Bootstrap           | Permissions → `ADMIN` / `USER` roles → default admin, tested                   |
| ✅ Error handling      | `GlobalExceptionHandler`, catalog-driven statuses, no leaked internals, tested |
| ✅ Auth cookie         | `AuthCookieProperties`, `AuthCookieService`, secure by default, tested         |
| ✅ Test foundation     | Testcontainers Postgres, 57 back-end tests                                     |
| 🔴 Front end           | **Does not compile** (issue BP-01)                                             |
| 🔴 Create user         | **No endpoint exists**, so only the bootstrap admin can exist (issue BP-03)    |
| ⬜ Everything else     | Listed below                                                                   |

---

## 2. Architecture decisions

Record each one as a short ADR in `docs/adr/` when you implement it (issue BP-53).

| #      | Decision                                                                                                             | Why                                                                                                                  | Rejected alternative                                                          |
|--------|----------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------|
| ADR-01 | **Accounts are provisioned by an administrator.** No public registration.                                            | The application manages association finances, so membership is controlled.                                           | Self-registration: it's kept on branch `self-registration-approach-snapshot`. |
| ADR-02 | **Package by feature**, with `api` / `application` / `domain` / `infrastructure` inside each feature, plus `shared`. | A feature can be understood and changed in one place.                                                                | Package by layer: every change touches every folder.                          |
| ADR-03 | **JWT in an HttpOnly cookie**, never in `localStorage`.                                                              | JavaScript can't read it, so an XSS can't steal it.                                                                  | Bearer token in `localStorage`.                                               |
| ADR-04 | **Short access token + rotated refresh token.**                                                                      | A stolen access token dies in minutes, and sessions can be revoked.                                                  | One long-lived JWT, which is today's state.                                   |
| ADR-05 | **Redis stores refresh tokens**, behind a `RefreshTokenStore` port.                                                  | See [section 3.3](#33-is-redis-the-right-choice).                                                                    | A PostgreSQL table, which is a valid second choice.                           |
| ADR-06 | **Authorities are loaded from the database on every request**, not read from JWT claims.                             | Disabling a user or changing a role applies immediately.                                                             | Trusting claims: faster, but changes wait for token expiry.                   |
| ADR-07 | **Flyway owns the schema. Application runners own the seed.**                                                        | Permissions live in code enums and the admin comes from env vars, and that seeding is already idempotent and tested. | SQL seed files, which would duplicate the enums.                              |
| ADR-08 | **RFC 9457 `ProblemDetail`** for all errors, with stable string codes.                                               | A standard format that any client understands.                                                                       | Keeping the custom `ExceptionResponse`.                                       |
| ADR-09 | **Permission format `domain:action`** (`user:read`).                                                                 | One format in the database, the back end and the front end. Today there are two.                                     | Keeping `role::read`.                                                         |
| ADR-10 | **Plural REST resources**: `/users`, `/roles`. Actions under `/auth`.                                                | The conventional REST style.                                                                                         | Mixed `/role` and `/users`.                                                   |
| ADR-11 | **Same origin in every environment**: dev proxy locally, reverse proxy in production.                                | It simplifies CORS, cookies and CSRF.                                                                                | Cross-origin calls with CORS.                                                 |
| ADR-12 | **Users are deactivated, never hard-deleted.**                                                                       | Financial records must keep pointing at the person who created them.                                                 | Hard delete.                                                                  |
| ADR-13 | **Front end: signals for state, RxJS for async flows, standalone components, zoneless.**                             | This matches Angular 22 and your existing code.                                                                      | NgRx: too heavy for this size.                                                |
| ADR-14 | **Runtime i18n with JSON files** (`en`, `fr`, `ar`), with RTL for Arabic.                                            | One build serves every language, and `public/i18n/` already exists.                                                  | Angular compile-time i18n: one build per language.                            |

---

## 3. Refresh tokens and Redis

### 3.1 Your understanding, checked

You're mostly right. Here is each point:

| Your understanding                                                     | Verdict                     | Detail                                                                                           |
|------------------------------------------------------------------------|-----------------------------|--------------------------------------------------------------------------------------------------|
| A short-life token in a cookie is used on each request.                | ✅ Correct                  | This is the access token, today's `access_token` cookie.                                         |
| A long-life token also lives in a cookie.                              | ✅ Correct                  | This is the refresh token. It's in a separate cookie.                                            |
| When the short token expires, the long token is used to get a new one. | ✅ Correct                  | That's the purpose of a refresh token.                                                           |
| If there's no valid long token, the user must log in.                  | ✅ Correct                  |                                                                                                  |
| Only a new short token is generated.                                   | ⚠️ Incomplete               | **Both** tokens are replaced. This is called **rotation**, and it's what makes theft detectable. |
| The check is "is the long token expired?"                              | ⚠️ Incomplete               | It must also exist in Redis. Otherwise logout and revocation can't work.                         |
| **The JWT filter does the refresh, then continues the request.**       | ❌ Not recommended          | Use a dedicated `POST /auth/refresh` endpoint, called by Angular. Reasons below.                 |
| Use Redis because enterprises use it.                                  | ⚠️ Right tool, wrong reason | Choose it for what it does. See [section 3.3](#33-is-redis-the-right-choice).                    |

### 3.2 Why the refresh does not belong in the filter

A filter-based refresh does work, and some systems use it. These are the reasons to avoid it:

1. **Parallel requests collide.** Angular often sends 3 or 4 requests at once. When the access token expires, all of
   them would try to rotate the same refresh token. The first succeeds, and the others look like a stolen token being
   reused, so the user gets logged out for no reason.
2. **The refresh cookie would travel on every request.** It would have to use `Path=/`. The most valuable secret in the
   system would then be exposed far more often than needed. With an endpoint, it's sent only to `/api/v1/auth`.
3. **The filter would depend on Redis.** Today a valid access token needs no Redis at all. With the logic in the filter,
   a Redis outage could take down every request.
4. **Any request could change the session.** Even a simple `GET` would write new cookies, which makes behavior hard to
   reason about and test.

### The recommended flow

```
Normal request
  Angular ── GET /users ──────────────────────► JwtAuthenticationFilter: valid token ──► 200

The access token has expired
  Angular ── GET /users ──────────────────────► no valid token ──► 401
     │  authRefreshInterceptor sees the 401
     │  and pauses any other request that also gets a 401 (one shared refresh)
     ├── POST /auth/refresh  (refresh cookie) ─► RefreshTokenService
     │                                             1. hash the token
     │                                             2. look it up in Redis, atomically
     │                                             3. mark the old one as used
     │                                             4. create a new refresh token
     │                                             5. create a new access token
     │   ◄───────────────── 204 + 2 Set-Cookie ──
     └── GET /users  (retried once) ──────────► 200       the user notices nothing

The refresh token is missing, expired or revoked
  POST /auth/refresh ────────────────────────► 401 ──► clear the session ──► /auth

A stolen token is reused
  POST /auth/refresh (an already-used token) ► the whole session family is revoked
                                               audit event + 401, both parties must log in
```

**Why rotation detects theft:** a refresh token works once. If a thief and the real user both hold the same token,
whoever comes second presents a token that's already used. The server can't tell which is which, so it revokes the whole
family. The thief is out, and the real user logs in again.

**Handling two browser tabs:** tab A rotates, and tab B arrives a moment later with the old token. A short grace window
(10 seconds) covers this. Within it, the server answers `204` with no new cookies. Cookies are shared between tabs, so
tab B already has the new ones and its retry works. After the window, it counts as reuse.

### 3.3 Is Redis the right choice?

Yes, for this project. But you should be able to defend it with reasons, not with "enterprises use it".

| Need                        | Redis                    | PostgreSQL table             |
|-----------------------------|--------------------------|------------------------------|
| Automatic expiry            | ✅ Built-in TTL          | ⚠️ Needs a cleanup job       |
| Atomic rotation             | ✅ Lua script            | ✅ Transaction with row lock |
| Rate-limit counters (BP-26) | ✅ `INCR` + TTL          | ⚠️ Heavy write load          |
| No extra infrastructure     | ❌ One more service      | ✅ Already there             |
| Survives a restart          | ⚠️ Only with AOF enabled | ✅ Yes                       |

**Decision:** Redis, because refresh tokens and rate-limit counters are both short-lived, frequently written data. Two
conditions apply:

- **Enable AOF persistence** (`appendonly yes`). Otherwise a Redis restart logs everyone out.
- **Keep it behind a port.** `RefreshTokenStore` is an interface, and Redis is one adapter. Your earlier
  `ActivationTokenStore` used this same good pattern. Don't restore the old `RedisConfig` (ARC-03): use Boot's
  auto-configuration.

**If Redis is down:** requests with a valid access token keep working, and `/auth/refresh` returns `503`. Users are
affected only once their 15-minute token expires. It must never fail open.

### 3.4 Token and cookie design

|                      | Access token                        | Refresh token                                |
|----------------------|-------------------------------------|----------------------------------------------|
| Format               | Signed JWT                          | 256-bit random string, **not** a JWT         |
| Lifetime             | 15 minutes                          | 8 hours idle, 7 days absolute (configurable) |
| Stored on the server | Nowhere                             | Only its SHA-256 hash, in Redis              |
| Cookie (dev)         | `access_token`                      | `refresh_token`                              |
| Cookie (prod)        | `__Host-access_token`               | `__Secure-refresh_token`                     |
| Cookie path          | `/`                                 | `/api/v1/auth`                               |
| Flags                | `HttpOnly; Secure; SameSite=Strict` | `HttpOnly; Secure; SameSite=Strict`          |
| Validated by         | `JwtAuthenticationFilter`           | `RefreshTokenService`                        |

Three details matter:

- **The refresh path is `/api/v1/auth`, not `/api/v1/auth/refresh`.** Logout also needs the cookie, to revoke the
  session.
- **The refresh cookie can't use `__Host-`**, because that prefix requires `Path=/`. It uses `__Secure-` instead.
- **Why a random string and not a JWT?** It needs a server lookup anyway. A JWT would add a signature and readable
  claims for no benefit.

**Redis keys:**

```
rt:token:{sha256}    -> {userId, familyId, createdAt, rotatedAt}   TTL: idle lifetime
rt:family:{familyId} -> {userId, createdAt, revoked}               TTL: absolute lifetime
rt:user:{userId}     -> SET of familyIds                           used for "log out everywhere"
```

### 3.5 When sessions are revoked

| Event                        | Effect                                                                                 |
|------------------------------|----------------------------------------------------------------------------------------|
| Logout                       | That session's family                                                                  |
| Password changed by the user | All the user's families                                                                |
| Password reset by an admin   | All the user's families                                                                |
| User deactivated             | All the user's families; the per-request DB check also blocks the access token at once |
| Role or permissions changed  | Nothing to revoke, because authorities are read per request (ADR-06)                   |
| Refresh token reuse detected | That family, plus an audit event                                                       |

---

## 4. Target structure

### 4.1 Back end

```
com.association.assofin
├─ AssofinApplication
├─ shared/
│  ├─ config/         ClockConfig, OpenApiConfig
│  ├─ error/          ErrorCode, BusinessException + subclasses, GlobalExceptionHandler, ProblemDetailFactory
│  ├─ logging/        RequestCorrelationFilter, LogSanitizer, audit/AuditLogger
│  ├─ persistence/    AuditableEntity, JpaAuditingConfig
│  ├─ security/       SecurityConfig, SecurityBeansConfig, CorsProperties, ApiDocsProperties
│  │  ├─ cookie/      AuthCookieProperties, AuthCookieService
│  │  ├─ handlers/    RestAccessDeniedHandler, RestAuthenticationEntryPoint
│  │  ├─ jwt/         JwtAuthenticationFilter, JwtProperties, JwtService
│  │  └─ principal/   AuthenticatedUser, CurrentUser
│  └─ web/            PageResponse
├─ auth/
│  ├─ api/            AuthenticationController, dto/
│  ├─ application/    AuthenticationService, RefreshTokenService, LoginAttemptService
│  ├─ domain/         RefreshTokenStore (port), RefreshTokenRecord
│  └─ infrastructure/ RedisRefreshTokenStore
├─ user/
│  ├─ api/            UserController, ProfileController, dto/, UserMapper
│  ├─ application/    UserService, UserCredentialService, UserDetailsServiceImpl, PasswordPolicy
│  └─ domain/         User, UserRepository, UserPermissions
├─ role/
│  ├─ api/            RoleController, dto/, RoleMapper
│  ├─ application/    RoleService
│  └─ domain/         Role, RolePermission, RoleRepository, RolePermissions, SystemRole
├─ permission/
│  ├─ api/            PermissionController
│  ├─ application/    PermissionService, PermissionRegistry
│  └─ domain/         Permission, PermissionRepository, PermissionDefinition, PermissionProvider
├─ bootstrap/         BootstrapOrder, PermissionInitializer, RoleInitializer, AdminUserInitializer, BootstrapAdminProperties
└─ mail/              MailService, MailProperties, EmailTemplateName
```

### 4.2 Front end

```
src/app
├─ app.ts, app.config.ts, app.routes.ts
├─ core/                  singletons, loaded once
│  ├─ api/                api.service.ts, api-error.ts, generated/
│  ├─ auth/               auth.repository.ts, auth.service.ts, dto/
│  ├─ config/             app-config.ts
│  ├─ constants/          app-paths.ts
│  ├─ guards/             auth.guard.ts, guest.guard.ts, permission.guard.ts, password-change.guard.ts
│  ├─ i18n/               language.service.ts, transloco-loader.ts
│  ├─ interceptors/       request-id, credentials, error, auth-refresh, timeout
│  ├─ logging/            logger.service.ts, log-sink.ts, console-log.sink.ts, global-error-handler.ts
│  ├─ navigation/         nav-items.ts
│  └─ notification/       notification.service.ts
├─ shared/                reusable, no business logic
│  ├─ components/         page-header, confirm-action, empty-state, field-error, form-error-summary
│  ├─ directives/         has-permission.directive.ts
│  ├─ utils/              apply-server-errors.ts
│  └─ validators/         password-match.validator.ts
├─ layouts/               auth-layout, main-layout (+ topbar, sidebar)
└─ features/              lazy-loaded
   ├─ auth/               login-page, change-password-page
   ├─ dashboard/          dashboard-page
   ├─ profile/            profile-page
   ├─ users/              repository, list / detail / form components, routes
   ├─ roles/              repository, list / form components, routes
   └─ errors/             forbidden-page, not-found-page
```

**Rule:** `features` may import `core` and `shared`. `shared` may not import `features`. One feature may not import
another. BP-09 enforces this with lint rules.

---

## 5. API, permissions and data model

### 5.1 Endpoints

| Method | Path                         | Permission            | Purpose                             | Issue |
|--------|------------------------------|-----------------------|-------------------------------------|-------|
| POST   | `/auth/login`                | public                | Log in, sets both cookies           | BP-24 |
| POST   | `/auth/refresh`              | refresh cookie        | Rotates tokens                      | BP-24 |
| POST   | `/auth/logout`               | public                | Revokes the session, clears cookies | BP-24 |
| POST   | `/auth/logout-all`           | authenticated         | Revokes every session               | BP-25 |
| POST   | `/auth/change-password`      | authenticated         | User changes their own password     | BP-31 |
| GET    | `/users/me`                  | authenticated         | Current profile + permissions       | BP-28 |
| PATCH  | `/users/me`                  | authenticated         | Update own name                     | BP-28 |
| GET    | `/users`                     | `user:read`           | Paged search                        | BP-29 |
| GET    | `/users/{id}`                | `user:read`           | Detail                              | BP-29 |
| POST   | `/users`                     | `user:create`         | Create an account                   | BP-03 |
| PATCH  | `/users/{id}`                | `user:update`         | Update name / role                  | BP-30 |
| POST   | `/users/{id}/deactivate`     | `user:update`         | Deactivate                          | BP-30 |
| POST   | `/users/{id}/activate`       | `user:update`         | Reactivate                          | BP-30 |
| POST   | `/users/{id}/unlock`         | `user:update`         | Clear a lock                        | BP-30 |
| POST   | `/users/{id}/reset-password` | `user:reset-password` | Admin reset, forces a change        | BP-31 |
| GET    | `/roles`                     | `role:read`           | Paged list                          | BP-20 |
| GET    | `/roles/{id}`                | `role:read`           | Detail                              | BP-20 |
| POST   | `/roles`                     | `role:create`         | Create                              | BP-20 |
| PATCH  | `/roles/{id}`                | `role:update`         | Update                              | BP-20 |
| DELETE | `/roles/{id}`                | `role:delete`         | Delete if unused                    | BP-20 |
| GET    | `/permissions`               | `role:read`           | List for the role form              | BP-19 |

### 5.2 Business rules the back end must enforce

| #  | Rule                                                                                             | Why                                                       |
|----|--------------------------------------------------------------------------------------------------|-----------------------------------------------------------|
| R1 | The last active `ADMIN` can't be deactivated or demoted.                                         | Prevents locking everyone out.                            |
| R2 | Users can't deactivate themselves or change their own role.                                      | Prevents accidents and self-escalation.                   |
| R3 | A user can't grant a role holding permissions they don't have.                                   | Prevents privilege escalation.                            |
| R4 | System roles can't be renamed or deleted. `ADMIN` can't lose permissions.                        | A renamed `ADMIN` stops the application at startup today. |
| R5 | A role with assigned users can't be deleted (409).                                               | Referential integrity.                                    |
| R6 | Emails are trimmed and lower-cased before any lookup.                                            | `John@x.com` and `john@x.com` are one person.             |
| R7 | A user with `mustChangePassword` can only change their password, read their profile, or log out. | Enforced on the server, not only in the UI.               |
| R8 | The login response is identical for every failure reason.                                        | Prevents account enumeration.                             |

### 5.3 Data model (target)

```
users                                   role
  id            bigint PK                 id          bigint PK
  email         varchar(255)              name        varchar(50) UNIQUE
  password_hash varchar(255)              system      boolean
  firstname     varchar(100)              version     bigint
  lastname      varchar(100)              audit columns
  role_id       bigint FK -> role
  enabled       boolean                 permission
  locked_until  timestamptz NULL          id          bigint PK
  must_change_password boolean            name        varchar(100) UNIQUE
  password_changed_at  timestamptz
  last_login_at timestamptz NULL        role_permission
  deactivated_at timestamptz NULL         id            bigint PK
  version       bigint                    role_id       bigint FK
  audit columns                           permission_id bigint FK
  UNIQUE INDEX on lower(email)            UNIQUE (role_id, permission_id)

audit columns: created_at, updated_at (timestamptz), created_by, updated_by (varchar)
```

---

## 6. Milestones

```
M0 Repair and finish Phase 1
 └─► M1 Engineering foundation
      └─► M2 Persistence foundation
           └─► M3 Error contract
                └─► M4 Authorization model
                     └─► M5 Authentication hardening
                          └─► M6 User management API
                               └─► M7 Front-end foundation
                                    └─► M8 Front-end features
                                         └─► M9 Observability
                                              └─► M10 Delivery
```

M6 and M7 can run side by side if two people work on them. BP-36 (generated API contract) and the M8 features need M6's
endpoints to exist.

| Milestone                        | Issues         | Outcome                                           |
|----------------------------------|----------------|---------------------------------------------------|
| **M0** Repair and finish Phase 1 | BP-01 to BP-04 | Everything compiles, Phase 1 merged               |
| **M1** Engineering foundation    | BP-05 to BP-11 | Reproducible builds, CI catches mistakes          |
| **M2** Persistence foundation    | BP-12 to BP-16 | A versioned, trustworthy schema                   |
| **M3** Error contract            | BP-17, BP-18   | One error format everywhere                       |
| **M4** Authorization model       | BP-19, BP-20   | Extensible permissions, a complete Role API       |
| **M5** Authentication hardening  | BP-21 to BP-27 | Short tokens, refresh, revocation, throttling     |
| **M6** User management API       | BP-28 to BP-32 | Full account lifecycle                            |
| **M7** Front-end foundation      | BP-33 to BP-41 | A solid, tested application core                  |
| **M8** Front-end features        | BP-42 to BP-47 | The complete administration UI                    |
| **M9** Observability             | BP-48 to BP-50 | Logs, audit trail, health, metrics                |
| **M10** Delivery                 | BP-51 to BP-54 | Containers, end-to-end tests, a reusable template |

**Why this order:**

- **M0 first:** nothing can be verified on a broken build.
- **M1 before features:** CI and strict mode would have caught the broken build the day it happened.
- **M1's package move before more code:** moving 50 classes is cheap. Moving 150 isn't.
- **M2 before more entities:** the first migration should describe a clean model, not freeze a flawed one.
- **M3 before M4 to M6:** every new endpoint then uses the final error format, so nothing is written twice.
- **M5 before M6:** user management must call session revocation, so that has to exist first.
- **M9 late on purpose:** audit events attach to endpoints, so those must exist. You chose this for LOG-01.

---

## 7. The issues in order

---

### M0: Repair and finish Phase 1

#### BP-01 Repair the front-end build · S · 🔴

**Replaces:** FE-09 · **Depends on:** nothing **Why:** `ng build` fails with 6 errors, so no front-end work can be
verified.

| File                                                | Action | What                                                                                  |
|-----------------------------------------------------|--------|---------------------------------------------------------------------------------------|
| `features/auth/pages/login-page/login-page.ts`      | CHANGE | Add `LoginForm` to `imports` (removed by `70fc7f8`)                                   |
| `features/auth/pages/login-page/login-page.html`    | CHANGE | Replace the outer `<form>` with `<div>`: nested forms are invalid                     |
| `features/auth/components/login-form/login-form.ts` | CHANGE | Restore `IconField`, `InputIcon`, `InputPassword`, `InputText` (removed by `97ed947`) |

**Tests:** run `npm test` and record what fails. **Done when:** `npm run build` passes and the login page renders in a
browser.

#### BP-02 Reduce the public API surface · S · 🟠

**Replaces:** SEC-05 · **Depends on:** nothing **Why:** Swagger is public in production, and `/ldap/**` would expose any
future LDAP controller.

| Class                  | Action | What                                                                                                                                                                                     |
|------------------------|--------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `SecurityConfig`       | CHANGE | Remove `/ldap/**`, `/v2/api-docs`, `/swagger-resources*`, `/configuration/*`. Keep `/auth/login` and `/auth/logout`. Use `dispatcherTypeMatchers(ERROR).permitAll()` instead of `/error` |
| `ApiDocsProperties`    | NEW    | `application.security.expose-api-docs`, default `false`                                                                                                                                  |
| `application-dev.yml`  | CHANGE | `expose-api-docs: true`                                                                                                                                                                  |
| `application-prod.yml` | CHANGE | `springdoc.api-docs.enabled: false`, `springdoc.swagger-ui.enabled: false`                                                                                                               |

**Tests:** docs return 401 when the flag is off and 200 when on; `/ldap/x` returns 401. **Done when:** production can't
expose the docs, even by mistake.

#### BP-03 Create-user endpoint, first slice · M · 🔴

**Replaces:** Phase 1 step 1.6 · **Depends on:** nothing **Why:** the application has no way to create a user. This
slice stays simple and uses today's exceptions; M3 and M6 refine it.

| Class                  | Action | What                                                                                                                                    |
|------------------------|--------|-----------------------------------------------------------------------------------------------------------------------------------------|
| `CreateUserRequestDTO` | CHANGE | Password 12 to 72 characters, remove duplicated `@NotNull`, add size limits, override `toString()`                                      |
| `UserResponseDTO`      | NEW    | `id, email, firstname, lastname, roleName, enabled, createdAt`                                                                          |
| `UserMapper`           | NEW    | Entity → DTO, the same style as `RoleMapper`                                                                                            |
| `UserService`          | CHANGE | `createUser()`: `@Transactional`, normalize email, duplicate → 409, remove manual `createdDate`, return the DTO. Delete `saveUser(...)` |
| `UserController`       | CHANGE | `POST /users`, guarded by `user::create`, returns 201 + `Location`                                                                      |

**Tests:** 201, 400, 401, 403, 409 (also with a different case), 404 unknown role; password encoded; password absent
from the response; the new user can log in. **Done when:** the admin can create a user, and that user can log in.

#### BP-04 Merge Phase 1 · S

**Replaces:** step 1.7 · **Depends on:** BP-01 to BP-03

**Done when:** `./mvnw verify` and `npm run build` pass, the PR is reviewed and merged, and the correction file is
updated.

---

### M1: Engineering foundation

#### BP-05 Clean the Maven build · S

**Replaces:** CFG-05 · **Depends on:** BP-04

| File      | Action | What                                                                                                                                                                               |
|-----------|--------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `pom.xml` | CHANGE | Remove `springdoc-openapi-starter-webflux-ui`. Replace `jjwt` with `jjwt-api` + runtime `jjwt-impl`, `jjwt-jackson`. Versions in `<properties>`. Fill or delete the empty metadata |
| `pom.xml` | CHANGE | Add `maven-enforcer-plugin` (Java 21, dependency convergence)                                                                                                                      |
| `pom.xml` | CHANGE | Add `spotless-maven-plugin` and `jacoco-maven-plugin`                                                                                                                              |

**Done when:** `./mvnw verify` passes and WebFlux is gone from `dependency:tree`.

#### BP-06 Configuration and environment · M · 🟠

**Replaces:** CFG-01, CFG-02, part of CFG-04 · **Depends on:** BP-05 **Why:** production can silently start as `dev`,
and the profiles have drifted apart.

| File                   | Action | What                                                                                                                                                      |
|------------------------|--------|-----------------------------------------------------------------------------------------------------------------------------------------------------------|
| `application.yml`      | CHANGE | All shared settings. **Remove the `:dev` fallback** from `spring.profiles.active`. `open-in-view: false`. Remove the trailing slash from the context path |
| `application-dev.yml`  | CHANGE | Only dev overrides. Import `.env` with `spring.config.import: optional:file:../.env[.properties]`                                                         |
| `application-prod.yml` | CHANGE | Correct header. `${DB_HOST}` instead of `postgres-db`. No defaults for secrets                                                                            |
| `.env.example`         | CHANGE | One name per setting, no inline comments, correct units. Add `CORS_ALLOWED_ORIGINS`                                                                       |
| `CorsProperties`       | CHANGE | Record with `@Validated`. Origins from env, no placeholder domain                                                                                         |
| `JwtProperties`        | CHANGE | Record with `@Validated`. `expiration` becomes a `Duration`                                                                                               |
| `AuthCookieService`    | CHANGE | Follows the `Duration`                                                                                                                                    |
| `.gitignore`           | CHANGE | Correct `assofin/.env` and `assofin-frontend/.env` paths                                                                                                  |

**Tests:** the context starts for `test`, and a missing profile stops startup. **Done when:** the app can't start in
production by accident as `dev`.

#### BP-07 Docker Compose for development · S

**Replaces:** CFG-03, step 3 of SEC-08 · **Depends on:** BP-06

| File           | Action | What                                                                                                           |
|----------------|--------|----------------------------------------------------------------------------------------------------------------|
| `compose.yaml` | CHANGE | Pin `postgres:17.x-alpine` and `maildev/maildev:x.y`. Bind ports to `127.0.0.1`. Remove the commented services |
| `pom.xml`      | CHANGE | Decide on `spring-boot-docker-compose`: use it or remove it, and document which                                |

**Done when:** a new developer can run the stack from the README commands alone.

#### BP-08 Back-end code hygiene · S

**Replaces:** HYG-01 · **Depends on:** BP-05

| Class                      | Action | What                                                                                                                                   |
|----------------------------|--------|----------------------------------------------------------------------------------------------------------------------------------------|
| `AuthenticationController` | CHANGE | Delete the commented LDAP block and old implementation. Remove `@ResponseStatus`. Login returns 200 (update the tests that expect 202) |
| `AuthenticationService`    | CHANGE | Replace `assert user != null`. Drop the `fullname` claim                                                                               |
| `UserController`           | CHANGE | Delete the commented code                                                                                                              |
| `UserRepository`           | CHANGE | Delete `findByUsernameIgnoreCase`                                                                                                      |
| `.editorconfig`            | NEW    | At the repository root                                                                                                                 |
| All Java                   | CHANGE | Apply Spotless once, in a separate commit                                                                                              |

**Done when:** `./mvnw spotless:check` passes and no commented-out code remains.

#### BP-09 Front-end strictness and tooling · M · 🟠

**Replaces:** FE-02, FE-08, step 5 of FE-06 · **Depends on:** BP-01

| File                       | Action | What                                                                                                                                 |
|----------------------------|--------|--------------------------------------------------------------------------------------------------------------------------------------|
| `tsconfig.json`            | CHANGE | `strict`, `strictTemplates`, `strictStandalone`. Add `@features/*`, `@layouts/*`, `@shared/*` aliases                                |
| `eslint.config.js`         | NEW    | `angular-eslint`, template accessibility rules, `no-explicit-any`, `no-console`, import boundaries from [section 4.2](#42-front-end) |
| `package.json`             | CHANGE | Scripts `lint`, `format`, `format:check`, `test:ci`. Set a real version                                                              |
| `proxy.conf.json`          | NEW    | `/api` → `http://localhost:8080`, so dev is same-origin (ADR-11)                                                                     |
| `angular.json`             | CHANGE | Use the proxy in `serve`                                                                                                             |
| `.env.example` (front end) | CHANGE | Document `NG_APP_API_BASE_URL` as a relative base                                                                                    |
| `.husky/`, `lint-staged`   | NEW    | Pre-commit checks                                                                                                                    |
| `README.md` (front end)    | CHANGE | Real project documentation                                                                                                           |
| All services               | CHANGE | One convention: `@Injectable({ providedIn: 'root' })` or `@Service()`, not both                                                      |

**Tests:** every existing spec passes with correct providers (`provideHttpClient()`, `provideHttpClientTesting()`,
`provideRouter([])`). **Done when:** `npm run lint`, `npm run build` and `npm test` all pass.

#### BP-10 First CI pipeline · S · 🟠

**Replaces:** step 5 of OPS-01 · **Depends on:** BP-05, BP-09 **Why:** it comes early on purpose. A CI build would have
caught BP-01 immediately.

| File                       | Action | What                                                              |
|----------------------------|--------|-------------------------------------------------------------------|
| `.github/workflows/ci.yml` | NEW    | Back end: `./mvnw verify`. Front end: `npm ci`, lint, test, build |
| `.github/dependabot.yml`   | NEW    | Maven, npm, GitHub Actions                                        |
| Repository settings        | CHANGE | Protect `main`: PR and green checks required                      |

**Done when:** a pull request can't merge with a failing build.

#### BP-11 Package structure and architecture rules · M

**Replaces:** ARC-02 · **Depends on:** BP-08, BP-10 **Why:** moving classes now is cheap. This is a refactor only, with
no behavior change.

| Current                                        | Target                           | Action |
|------------------------------------------------|----------------------------------|--------|
| `handler/*`                                    | `shared/error/`                  | MOVE   |
| `security/*`                                   | `shared/security/`               | MOVE   |
| `infrastructure/persistence/*`                 | `shared/persistence/`            | MOVE   |
| `infrastructure/bootstrap/*`                   | `bootstrap/`                     | MOVE   |
| `infrastructure/permission/PermissionRegistry` | `permission/application/`        | MOVE   |
| `auth/controller`, `auth/dto`                  | `auth/api/`                      | MOVE   |
| `auth/service`                                 | `auth/application/`              | MOVE   |
| `user/*`, `role/*`, `permission/*`             | `api` / `application` / `domain` | MOVE   |
| `ArchitectureTest`                             | test sources                     | NEW    |

**ArchUnit rules:** no package cycles between features; controllers depend on services, never repositories; DTOs don't
depend on entities; only Spring's `@Transactional` is used; only `..infrastructure..` uses the Redis client. **Done
when:** all tests pass unchanged and `ArchitectureTest` is green.

---

### M2: Persistence foundation

#### BP-12 Harden the entities · M

**Replaces:** PER-02 · **Depends on:** BP-11 **Why:** do this **before** Flyway, so the first migration describes the
clean model.

| Class                                          | Action | What                                                                                                                                |
|------------------------------------------------|--------|-------------------------------------------------------------------------------------------------------------------------------------|
| `User`, `Role`, `Permission`, `RolePermission` | CHANGE | One ID strategy: `SEQUENCE`, `allocationSize = 50`. `Role` currently differs                                                        |
| `User`, `Role`, `Permission`, `RolePermission` | CHANGE | Protected no-args constructor. Remove the class-level `@Setter` and `@Builder`, use factory methods. Proxy-safe `equals`/`hashCode` |
| `Permission`                                   | CHANGE | Remove `rolePermissions`                                                                                                            |
| `Role`, `User`                                 | CHANGE | `@Version Long version`                                                                                                             |
| `RolePermission`                               | CHANGE | Unique constraint on `(role_id, permission_id)`                                                                                     |

**Tests:** `Role.replacePermissions` with new and persisted permissions; `equals` before and after persist. **Done
when:** entities can only be built in a valid state.

#### BP-13 Flyway baseline · M · 🟠

**Replaces:** PER-01 · **Depends on:** BP-12

| File                               | Action | What                                                                           |
|------------------------------------|--------|--------------------------------------------------------------------------------|
| `pom.xml`                          | CHANGE | `spring-boot-starter-flyway` + `flyway-database-postgresql`                    |
| `db/migration/V1__init_schema.sql` | NEW    | Tables, FKs, indexes, `UNIQUE INDEX ON users (lower(email))`                   |
| `application.yml`                  | CHANGE | `ddl-auto: validate` in **all** profiles, including `test`. `show-sql` removed |
| `application-dev.yml`              | CHANGE | `logging.level.org.hibernate.SQL: DEBUG` for dev only                          |

**Note:** there's no production data yet, so reset your local database once (`docker compose down -v`) rather than
writing a baseline migration for it. **Tests:** the existing Testcontainers tests now run the real migrations. **Done
when:** Hibernate validates against the migrated schema in every profile.

#### BP-14 Auditing foundation · S

**Replaces:** the rest of BUG-01 · **Depends on:** BP-13

| Class                                          | Action | What                                                                                |
|------------------------------------------------|--------|-------------------------------------------------------------------------------------|
| `ClockConfig`                                  | NEW    | `Clock` bean in UTC                                                                 |
| `AuditableEntity`                              | NEW    | `@MappedSuperclass`: `createdAt`, `updatedAt` (`Instant`), `createdBy`, `updatedBy` |
| `JpaAuditingConfig`                            | CHANGE | `dateTimeProviderRef` from the `Clock`, plus an `AuditorAware<String>`              |
| `SecurityAuditorAware`                         | NEW    | Reads the current user, or `system` during bootstrap                                |
| `User`, `Role`, `RolePermission`, `Permission` | CHANGE | Extend `AuditableEntity`, remove their own fields                                   |
| `V2__audit_columns.sql`                        | NEW    | Migration                                                                           |

**Tests:** `createdAt` and `updatedAt` set on insert and update, `createdBy` is the acting user. **Done when:** no audit
field is set by hand.

#### BP-15 User aggregate · M

**Replaces:** ARC-04 · **Depends on:** BP-14

| Class                      | Action | What                                                                                                                                                                                                                                                 |
|----------------------------|--------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `User`                     | CHANGE | Remove `username`, `domainName`, `birthDate`. Add `lockedUntil`, `mustChangePassword`, `passwordChangedAt`, `lastLoginAt`, `deactivatedAt`. Rename `password` → `passwordHash`. Domain methods `deactivate()`, `lock(until)`, `changePassword(hash)` |
| `EmailNormalizer`          | NEW    | The one place that trims and lower-cases                                                                                                                                                                                                             |
| `PasswordPolicy`           | NEW    | 12 to 72 bytes, checks the UTF-8 byte length                                                                                                                                                                                                         |
| `AuthenticationRequestDTO` | CHANGE | Remove `@Size` from the login password                                                                                                                                                                                                               |
| `SecurityBeansConfig`      | CHANGE | `DelegatingPasswordEncoder`, so the algorithm can evolve                                                                                                                                                                                             |
| `V3__user_lifecycle.sql`   | NEW    | Migration                                                                                                                                                                                                                                            |

**Tests:** domain method tests, `PasswordPolicy` boundaries with multi-byte characters. **Done when:** the `User` state
can only change through domain methods.

#### BP-16 Transaction boundaries · S

**Replaces:** PER-04 · **Depends on:** BP-15

| Class                    | Action | What                                                                                        |
|--------------------------|--------|---------------------------------------------------------------------------------------------|
| All application services | CHANGE | `@Transactional` on writes, `@Transactional(readOnly = true)` on reads, none in controllers |
| `PermissionInitializer`  | CHANGE | One query for existing names, then one batch save                                           |

**Done when:** the ArchUnit rule from BP-11 passes.

---

### M3: Error contract

#### BP-17 Domain exceptions · M

**Replaces:** BUG-11, part of ERR-01 · **Depends on:** BP-16

| Class                                                                     | Action | What                                                                                                    |
|---------------------------------------------------------------------------|--------|---------------------------------------------------------------------------------------------------------|
| `ErrorCode`                                                               | NEW    | Replaces `BusinessErrorCodes`. Stable string code + `HttpStatus` + message key, e.g. `USER_EMAIL_TAKEN` |
| `BusinessException`                                                       | NEW    | Abstract, holds `ErrorCode` and arguments                                                               |
| `ResourceNotFoundException`, `ConflictException`, `BusinessRuleException` | NEW    | Subclasses                                                                                              |
| `UserService`, `RoleService`                                              | CHANGE | Replace JPA and `IllegalArgumentException` uses                                                         |
| `GlobalExceptionHandler`                                                  | CHANGE | One `@ExceptionHandler(BusinessException.class)`                                                        |

**Note:** the startup checks in `AuthCookieProperties` keep `IllegalArgumentException`. That usage is correct. **Done
when:** no `IllegalArgumentException` is used for a business rule.

#### BP-18 RFC 9457 error format · M

**Replaces:** the rest of ERR-01 · **Depends on:** BP-17

| Class                                                     | Action | What                                                                                                                 |
|-----------------------------------------------------------|--------|----------------------------------------------------------------------------------------------------------------------|
| `ProblemDetailFactory`                                    | NEW    | Builds every error: `type`, `title`, `status`, `detail`, `instance`, plus `code`, `timestamp`, `errorId`, `errors[]` |
| `GlobalExceptionHandler`                                  | CHANGE | Returns `ProblemDetail`. Remove the conversion in `handleExceptionInternal`                                          |
| `RestAccessDeniedHandler`, `RestAuthenticationEntryPoint` | CHANGE | Use the factory                                                                                                      |
| `ExceptionResponse`                                       | DELETE |                                                                                                                      |
| `messages_en/fr/ar.properties`                            | NEW    | Message keys, chosen by `Accept-Language`                                                                            |
| `GlobalExceptionHandlerTest`                              | CHANGE | Assert the new format                                                                                                |

**Done when:** 400, 401, 403, 404, 409 and 500 all share one documented format.

---

### M4: Authorization model

#### BP-19 Self-registering permissions · M · 🟠

**Replaces:** ARC-01 · **Depends on:** BP-18

| Class                                | Action | What                                                           |
|--------------------------------------|--------|----------------------------------------------------------------|
| `PermissionProvider`                 | NEW    | Interface each feature implements                              |
| `RolePermissions`, `UserPermissions` | CHANGE | `domain:action` format, plus `USER_RESET_PASSWORD`             |
| `PermissionRegistry`                 | CHANGE | Injects `List<PermissionProvider>`, no domain imports          |
| `PermissionExpressions`              | DELETE | Replaced by the annotation below                               |
| `@RequiresPermission`                | NEW    | Meta-annotation for `@PreAuthorize`, or compile-time constants |
| `ProductPermissions`                 | DELETE | Test code in `main`                                            |
| `PermissionController`               | NEW    | `GET /permissions`                                             |
| `V4__permission_format.sql`          | NEW    | Migrates `::` to `:`                                           |

**Done when:** adding a domain touches only that domain's package.

#### BP-20 Complete Role API · L · 🟠

**Replaces:** BUG-03 (rest), BUG-09, BUG-10, PER-03 · **Depends on:** BP-19

| Class                                       | Action | What                                                           |
|---------------------------------------------|--------|----------------------------------------------------------------|
| `RoleController`                            | CHANGE | Path `/roles`. Add `GET /{id}`, `DELETE /{id}`. Use `Pageable` |
| `CreateRoleRequestDTO`                      | CHANGE | `@NotBlank @Size @Pattern`, reject `ROLE_`                     |
| `UpdateRoleRequestDTO`                      | CHANGE | Real PATCH: every field optional                               |
| `RolePageRequestDTO`, `RolePageResponseDTO` | DELETE | Replaced by `PageResponse<T>`                                  |
| `PageResponse<T>`                           | NEW    | Generic, in `shared/web`                                       |
| `Role`                                      | CHANGE | `system` flag. Rename and removal rules (R4)                   |
| `RoleService`                               | CHANGE | R4, R5, and a two-step paged fetch to remove the N+1           |
| `RoleRepository`                            | CHANGE | Id page + `@EntityGraph` fetch                                 |
| `RoleInitializer`                           | CHANGE | Marks `ADMIN` and `USER` as system roles                       |
| `V5__role_system_flag.sql`                  | NEW    | Migration                                                      |

**Tests:** query count is constant for `GET /roles`; R4 and R5; no parameters → 200; `size=500` → capped. **Done when:**
the `ADMIN` role can't be broken through the API.

---

### M5: Authentication hardening

#### BP-21 Separate the principal from the entity · M · 🟠

**Replaces:** BUG-04 (rest) · **Depends on:** BP-20

| Class                     | Action | What                                                                                                             |
|---------------------------|--------|------------------------------------------------------------------------------------------------------------------|
| `AuthenticatedUser`       | NEW    | Immutable record implementing `UserDetails`: id, email, hash, enabled, locked, `mustChangePassword`, authorities |
| `User`                    | CHANGE | Stops implementing `UserDetails`, `getAuthorities()` moves out                                                   |
| `UserDetailsServiceImpl`  | CHANGE | Maps entity → `AuthenticatedUser`, `@Transactional(readOnly = true)`                                             |
| `AuthenticationService`   | CHANGE | Works with `AuthenticatedUser`                                                                                   |
| `JwtAuthenticationFilter` | CHANGE | Catch only `JwtException` / `IllegalArgumentException`. Add `shouldNotFilter()` for public paths                 |
| `CurrentUser`             | NEW    | Helper or argument resolver for the current principal                                                            |

**Tests:** database failure during the filter → 5xx, not 401; malformed token → 401. **Done when:** no JPA entity
reaches the `SecurityContext`.

#### BP-22 Harden the JWT · M

**Replaces:** SEC-09 steps 1, 2, 3, 5 · **Depends on:** BP-21

| Class           | Action | What                                                                                                                                                                                              |
|-----------------|--------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `JwtProperties` | CHANGE | `issuer`, `audience`, `@NotBlank`, `@Positive`                                                                                                                                                    |
| `JwtService`    | CHANGE | Build the key once and fail at startup under 256 bits. Add `iss`, `aud`, `jti`. Require them on parsing. Allow 30 s skew. Use `Clock` and `Instant`. Drop the `authorities` and `fullname` claims |

**Note:** the lifetime stays long for now. It drops to 15 minutes in BP-38, once Angular can refresh silently. Doing it
earlier would log you out every 15 minutes during development. **Tests:** valid, expired, wrong signature, wrong issuer,
wrong audience, short key at startup. **Done when:** the token carries only what it needs.

#### BP-23 Bring Redis back · S

**Replaces:** the Redis part of SEC-08, follows ARC-03's lesson · **Depends on:** BP-22

| File                          | Action | What                                                                                                  |
|-------------------------------|--------|-------------------------------------------------------------------------------------------------------|
| `pom.xml`                     | CHANGE | `spring-boot-starter-data-redis`; test: `spring-boot-starter-data-redis-test`, `testcontainers-redis` |
| `compose.yaml`                | CHANGE | Pinned `redis`, `requirepass`, `appendonly yes`, `127.0.0.1` port, healthcheck with `REDISCLI_AUTH`   |
| `application.yml`             | CHANGE | `spring.data.redis.*`, including timeouts                                                             |
| `.env.example`                | CHANGE | `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`                                                          |
| `TestcontainersConfiguration` | CHANGE | Add the Redis container with `@ServiceConnection`                                                     |

**Note:** no `RedisConfig` class. Use the auto-configured `StringRedisTemplate`. **Done when:** the context starts with
Redis from Testcontainers.

#### BP-24 Refresh tokens with rotation · L · 🟠

**Replaces:** AUTH-01, step 4 of SEC-09 · **Depends on:** BP-23 **Design:** [section 3](#3-refresh-tokens-and-redis).

| Class                          | Action | What                                                                                |
|--------------------------------|--------|-------------------------------------------------------------------------------------|
| `RefreshTokenProperties`       | NEW    | Idle TTL, absolute TTL, grace window, validated                                     |
| `RefreshTokenRecord`           | NEW    | `userId`, `familyId`, `createdAt`, `rotatedAt`                                      |
| `RefreshTokenStore`            | NEW    | Port: `save`, `rotate`, `revokeFamily`, `revokeAllForUser`                          |
| `RedisRefreshTokenStore`       | NEW    | Adapter. Rotation uses a Lua script, so it's atomic                                 |
| `TokenHasher`                  | NEW    | SHA-256                                                                             |
| `RefreshTokenService`          | NEW    | `issue`, `rotate`, `revoke`. Uses `SecureRandom`, 256 bits. Applies the reuse rules |
| `AuthCookieProperties`         | CHANGE | Add a nested `refresh` section (name, path)                                         |
| `AuthCookieService`            | CHANGE | `createRefreshTokenCookie`, `createClearingRefreshCookie`, `extractRefreshToken`    |
| `AuthenticationController`     | CHANGE | Login sets both cookies. New `POST /auth/refresh`. Logout revokes and clears both   |
| `AuthenticationService`        | CHANGE | Returns a token pair                                                                |
| `AuthTokens`                   | NEW    | Internal record (access + refresh). Never serialized                                |
| `SecurityConfig`               | CHANGE | Permit `/auth/refresh`                                                              |
| `RestAuthenticationEntryPoint` | CHANGE | Clears only the access cookie                                                       |

**Tests:** new pair on rotation; old token rejected; reuse after the grace window revokes the family; reuse within it
returns 204; no raw token in Redis; concurrent calls give one winner; Redis down → valid access token works, refresh
returns 503; idle and absolute expiry. **Done when:** a stolen refresh token is detected, and logout really ends the
session.

#### BP-25 Session revocation · S

**Replaces:** the rest of step 4 of SEC-09 · **Depends on:** BP-24

| Class                       | Action | What                                                                 |
|-----------------------------|--------|----------------------------------------------------------------------|
| `RefreshTokenService`       | CHANGE | `revokeAllForUser()`                                                 |
| `AuthenticationController`  | CHANGE | `POST /auth/logout-all`                                              |
| `UserSessionEvents`         | NEW    | Domain events `UserDeactivated`, `UserPasswordChanged`               |
| `SessionRevocationListener` | NEW    | `@TransactionalEventListener(AFTER_COMMIT)`: revokes on those events |

**Why events:** `UserService` doesn't depend on `auth`, so the features stay decoupled, and revocation runs only after
the commit. **Done when:** every row of [section 3.5](#35-when-sessions-are-revoked) has a test.

#### BP-26 Login throttling · M · 🟠

**Replaces:** SEC-06 · **Depends on:** BP-25

| Class                     | Action | What                                                                                              |
|---------------------------|--------|---------------------------------------------------------------------------------------------------|
| `SecurityBeansConfig`     | CHANGE | **`ProviderManager.setAuthenticationEventPublisher(...)`**. Without this, login events never fire |
| `LoginThrottleProperties` | NEW    | Thresholds and windows                                                                            |
| `LoginAttemptService`     | NEW    | Redis counters per email and per IP, with TTL                                                     |
| `LoginAttemptListener`    | NEW    | Failure and success events                                                                        |
| `RateLimitFilter`         | NEW    | Limits `/auth/login` and `/auth/refresh` per IP, returns 429                                      |
| `User`                    | CHANGE | Temporary lock through `lockedUntil`                                                              |

**Tests:** threshold reached → blocked; success resets; the response never reveals whether the email exists. **Done
when:** password guessing is impractical.

#### BP-27 CSRF and security headers · M

**Replaces:** SEC-07 · **Depends on:** BP-26, and the dev proxy from BP-09

| Class            | Action | What                                                                                                         |
|------------------|--------|--------------------------------------------------------------------------------------------------------------|
| `SecurityConfig` | CHANGE | `CookieCsrfTokenRepository.withHttpOnlyFalse()` with the SPA request handler. Exempt `/auth/login` only      |
| `SecurityConfig` | CHANGE | Headers: `Strict-Transport-Security`, `Content-Security-Policy`, `Referrer-Policy`, `X-Content-Type-Options` |
| `CorsProperties` | CHANGE | Allow `X-XSRF-TOKEN`                                                                                         |
| `docs/adr/`      | NEW    | The CSRF decision                                                                                            |

**Tests:** POST without the token → 403, with it → passes. **Done when:** cookie auth doesn't rely on `SameSite` alone.

---

### M6: User management API

#### BP-28 Profile endpoints · S · 🔴

**Replaces:** BUG-05 · **Depends on:** BP-21

| Class                     | Action | What                                                                               |
|---------------------------|--------|------------------------------------------------------------------------------------|
| `UserProfileResponseDTO`  | CHANGE | `id, email, firstname, lastname, mustChangePassword, role { name, permissions[] }` |
| `RoleSummaryDTO`          | NEW    | Replaces the `Role` entity in the DTO                                              |
| `ProfileController`       | NEW    | `GET /users/me`, `PATCH /users/me`                                                 |
| `UpdateProfileRequestDTO` | NEW    | Name only                                                                          |
| `UserMapper`              | CHANGE | One mapping path                                                                   |
| `UserService`             | CHANGE | Remove the duplicated mapping                                                      |

**Tests:** exact JSON shape; ArchUnit: no DTO depends on an entity. **Done when:** `/users/me` matches what the front
end needs.

#### BP-29 List and search users · M

**Depends on:** BP-28

| Class                | Action | What                                                  |
|----------------------|--------|-------------------------------------------------------|
| `UserController`     | CHANGE | `GET /users`, `GET /users/{id}`, `user:read`          |
| `UserSearchCriteria` | NEW    | `search`, `roleId`, `enabled`                         |
| `UserSpecifications` | NEW    | JPA `Specification`s                                  |
| `UserRepository`     | CHANGE | `JpaSpecificationExecutor`, entity graph for the role |
| `UserListItemDTO`    | NEW    | Light row for the table                               |
| `UserDetailDTO`      | NEW    | Full detail                                           |

**Tests:** filters, whitelisted sort, page size cap, query count constant. **Done when:** an admin can find any user.

#### BP-30 Update, deactivate, unlock · M · 🟠

**Depends on:** BP-29, BP-25

| Class                  | Action | What                                                  |
|------------------------|--------|-------------------------------------------------------|
| `UserController`       | CHANGE | `PATCH /{id}`, `/deactivate`, `/activate`, `/unlock`  |
| `UpdateUserRequestDTO` | NEW    | Name, `roleId`                                        |
| `UserService`          | CHANGE | Applies R1, R2, R3. Publishes `UserDeactivated`       |
| `UserGuards`           | NEW    | Isolates the R1 to R3 checks, so they're easy to test |
| `UserRepository`       | CHANGE | `countByRole_NameAndEnabledTrue` for R1               |

**Tests:** one test per rule, including concurrent demotion of two admins (R1). **Done when:** the last admin can't be
removed, and nobody can self-escalate.

#### BP-31 Credential lifecycle · M · 🟠

**Depends on:** BP-30

| Class                          | Action | What                                                                               |
|--------------------------------|--------|------------------------------------------------------------------------------------|
| `UserCredentialService`        | NEW    | `changeOwnPassword`, `adminResetPassword`                                          |
| `ChangePasswordRequestDTO`     | NEW    | Current + new. Redacted `toString()`                                               |
| `AuthenticationController`     | CHANGE | `POST /auth/change-password`                                                       |
| `UserController`               | CHANGE | `POST /users/{id}/reset-password`, returns a generated temporary password **once** |
| `TemporaryPasswordGenerator`   | NEW    | `SecureRandom`                                                                     |
| `PasswordChangeRequiredFilter` | NEW    | Applies R7, returns 403 `PASSWORD_CHANGE_REQUIRED`                                 |
| `UserService`                  | CHANGE | `createUser` sets `mustChangePassword = true`                                      |
| `AdminUserInitializer`         | CHANGE | The bootstrap admin must also change their password                                |

**Tests:** wrong current password; forced change blocks other endpoints; sessions revoked. **Done when:** no
administrator knows a user's lasting password.

#### BP-32 Invitation email (optional) · M

**Replaces:** BUG-06, the rest of CFG-04 · **Depends on:** BP-31

| Class                                                           | Action | What                                                        |
|-----------------------------------------------------------------|--------|-------------------------------------------------------------|
| `MailService`                                                   | CHANGE | Generic `send`, from `MailProperties`, no hard-coded sender |
| `MailProperties`                                                | NEW    | `from`, `appName`                                           |
| `AsyncConfig`                                                   | NEW    | `@EnableAsync`, bounded `mailExecutor`                      |
| `UserInvitationListener`                                        | NEW    | `AFTER_COMMIT` + `@Async`                                   |
| `templates/account_invitation.html`                             | NEW    | Plus a plain-text part                                      |
| `activate_account.html`, `EmailTemplateName.ACTIVATION_ACCOUNT` | DELETE | Leftovers                                                   |

**Tests:** GreenMail; SMTP failure doesn't roll back the user. **Done when:** a new user gets an email only if the
account was committed.

---

### M7: Front-end foundation

#### BP-33 Application core and HTTP layer · M

**Replaces:** FE-06 · **Depends on:** BP-09

| File                                           | Action        | What                                                                                   |
|------------------------------------------------|---------------|----------------------------------------------------------------------------------------|
| `core/config/app-config.ts`                    | CHANGE        | Validate env values at startup, single API base                                        |
| `core/api/api.service.ts`                      | MOVE + CHANGE | Fix the regex, add `patch`, one path helper. Remove `formatErrors` and `console.error` |
| `core/interceptors/credentials.interceptor.ts` | NEW           | `withCredentials` for API URLs only                                                    |
| `core/interceptors/timeout.interceptor.ts`     | NEW           | Applies `timeout`                                                                      |
| `core/interceptors/request-id.interceptor.ts`  | NEW           | Sends `X-Request-Id`                                                                   |
| `app.config.ts`                                | CHANGE        | Interceptor order below                                                                |

**Interceptor order (outermost first) and why:**

```
requestId → credentials → error → authRefresh → timeout
```

`error` sits outside `authRefresh`, so a 401 that gets refreshed is never shown as an error. `timeout` is innermost, so
each attempt has its own timeout.

**Tests:** URL building, each interceptor. **Done when:** the front end calls the API through a same-origin path.

#### BP-34 Front-end logging · S

**Replaces:** the front-end part of LOG-01 · **Depends on:** BP-33

| File                                           | Action        | What                                              |
|------------------------------------------------|---------------|---------------------------------------------------|
| `core/logging/logger.service.ts`               | MOVE + CHANGE | Levels, sanitizes sensitive keys, writes to sinks |
| `core/logging/log-sink.ts`                     | NEW           | `LogSink` interface and `LOG_SINKS` token         |
| `core/logging/console-log.sink.ts`             | NEW           | The only file allowed to use `console.*`          |
| `core/logging/global-error-handler.ts`         | NEW           | Custom `ErrorHandler`                             |
| `features/auth/pages/login-page/login-page.ts` | CHANGE        | Remove `console.log`                              |

**Done when:** `no-console` is on, and only the sink and `main.ts` are exempt.

#### BP-35 Typed API errors · M · 🟠

**Replaces:** FE-03 · **Depends on:** BP-18, BP-34

| File                                        | Action | What                                                                                |
|---------------------------------------------|--------|-------------------------------------------------------------------------------------|
| `core/api/api-error.ts`                     | NEW    | `ApiError`, matching `ProblemDetail`                                                |
| `core/interceptors/error.interceptor.ts`    | CHANGE | Converts `HttpErrorResponse` → `ApiError` in one place. Toasts only for 0, 403, 5xx |
| `core/interceptors/http-context.ts`         | NEW    | `SKIP_GLOBAL_ERROR` token                                                           |
| `core/notification/notification.service.ts` | MOVE   | Remove the misleading comment                                                       |
| `shared/components/field-error/`            | NEW    | Shows a field's error                                                               |
| `shared/components/form-error-summary/`     | NEW    | Form-level errors                                                                   |
| `shared/utils/apply-server-errors.ts`       | NEW    | Maps `errors[]` onto form controls                                                  |

**Tests:** per status, plus the skip token. **Done when:** every error has one type, and is shown once.

#### BP-36 API contract · M · 🟠

**Replaces:** FE-05 · **Depends on:** BP-35, and the API from M6

| File                           | Action           | What                                                                                     |
|--------------------------------|------------------|------------------------------------------------------------------------------------------|
| `package.json`                 | CHANGE           | `generate:api` with `openapi-typescript`                                                 |
| `core/api/generated/`          | NEW              | Generated models                                                                         |
| `core/auth/dto/*`              | CHANGE or DELETE | `id: number`, `AppPermission` matching the back end. Remove `invoice:*`, `token`, `user` |
| Back-end `OpenApiContractTest` | NEW              | Snapshot of `/v3/api-docs`, so changes show in PRs                                       |

**Done when:** a back-end contract change breaks the front-end build, not production.

#### BP-37 Authentication state · M · 🔴

**Replaces:** FE-01 · **Depends on:** BP-36

| File                                        | Action | What                                                                                                                                     |
|---------------------------------------------|--------|------------------------------------------------------------------------------------------------------------------------------------------|
| `core/auth/services/auth.service.ts`        | CHANGE | Clear state machine (below). `login()` → `getSession()` → set state. Shared in-flight `session$`. `logout()` always clears in `finalize` |
| `core/auth/repositories/auth.repository.ts` | CHANGE | Add `refresh()`, `changePassword()`. Remove `TempTokenResponse`                                                                          |
| `app.config.ts`                             | CHANGE | `provideAppInitializer` resolves the session at boot                                                                                     |

**States:** `unknown` → `authenticated` / `anonymous`. **Tests:** 401 → login → enters the app (the login-loop bug);
parallel guards make one call; logout clears state even on HTTP failure. **Done when:** the login loop is gone and
covered by a test.

#### BP-38 Silent token refresh · M · 🟠

**Depends on:** BP-37, BP-24

| File                                            | Action | What                                                                       |
|-------------------------------------------------|--------|----------------------------------------------------------------------------|
| `core/interceptors/auth-refresh.interceptor.ts` | NEW    | See below                                                                  |
| `.env.example` (root)                           | CHANGE | Access token lifetime set to 15 minutes, now that refresh works end to end |

**Behavior:**

1. A 401 on any request except `/auth/login`, `/auth/refresh`, `/auth/logout`.
2. If no refresh is running, start one. If one is running, wait for it. Only one call is ever made.
3. On success, retry the original request **once**.
4. On failure, clear the session and let the guard redirect.

**Tests:** 3 parallel 401s → exactly one refresh, 3 retries; a retry that fails again doesn't loop; refresh failure
clears the session. **Done when:** an expired token is invisible to the user.

#### BP-39 Guards and routes · M · 🟠

**Replaces:** FE-04 · **Depends on:** BP-37

| File                                            | Action | What                                            |
|-------------------------------------------------|--------|-------------------------------------------------|
| `core/constants/app-paths.ts`                   | NEW    | Route paths, one place                          |
| `core/guards/auth.guard.ts`                     | CHANGE | Uses `returnUrl`, no `catchError`               |
| `core/guards/guest.guard.ts`                    | NEW    | Replaces the inverted `reverse-auth-guard.ts`   |
| `core/guards/reverse-auth-guard.ts`             | DELETE |                                                 |
| `core/guards/permission.guard.ts`               | CHANGE | Waits for the session, reads `data.permissions` |
| `core/guards/password-change.guard.ts`          | NEW    | Forces the change page (R7)                     |
| `shared/directives/has-permission.directive.ts` | NEW    | Shows or hides UI by permission                 |
| `app.routes.ts`                                 | CHANGE | Apply the guards. `**` → 404 page               |

**Note:** front-end guards are for user experience only. The server is the real enforcement. **Tests:** every guard,
every branch. **Done when:** deep links work after a page reload.

#### BP-40 Application shell · M

**Replaces:** the layout part of FE-07 · **Depends on:** BP-39

| Component                           | Action | What                                       |
|-------------------------------------|--------|--------------------------------------------|
| `layouts/main-layout/`              | CHANGE | Replace the placeholder with a real shell  |
| `layouts/main-layout/topbar/`       | NEW    | User name, language switch, logout         |
| `layouts/main-layout/sidebar/`      | NEW    | Navigation filtered by permission          |
| `core/navigation/nav-items.ts`      | NEW    | Declarative menu with required permissions |
| `shared/components/page-header/`    | NEW    | Title and actions                          |
| `shared/components/confirm-action/` | NEW    | Confirms destructive actions               |
| `shared/components/empty-state/`    | NEW    | Empty tables                               |

**Done when:** a user sees only the menu entries they can use, on desktop and mobile.

#### BP-41 Internationalization and RTL · M

**Replaces:** the i18n part of FE-07 · **Depends on:** BP-40

| File                            | Action | What                                                               |
|---------------------------------|--------|--------------------------------------------------------------------|
| `package.json`                  | CHANGE | `@jsverse/transloco`. Confirm Angular 22 support before installing |
| `core/i18n/transloco-loader.ts` | NEW    | Loads `public/i18n/*.json`                                         |
| `core/i18n/language.service.ts` | NEW    | Current language, persisted. Sets `lang` and `dir` on `<html>`     |
| `public/i18n/{en,fr,ar}.json`   | CHANGE | Empty today, fill with keys                                        |
| `core/interceptors/`            | CHANGE | Send `Accept-Language`                                             |
| All templates                   | CHANGE | No hard-coded strings                                              |

**Done when:** the app switches language live, and Arabic renders right-to-left.

---

### M8: Front-end features

#### BP-42 Login page · S

**Depends on:** BP-35, BP-37, BP-41

| Component                              | Action | What                                                           |
|----------------------------------------|--------|----------------------------------------------------------------|
| `features/auth/pages/login-page/`      | CHANGE | Generic error, `SKIP_GLOBAL_ERROR`, loading state, `returnUrl` |
| `features/auth/components/login-form/` | CHANGE | Labels, `aria-live`, required DTO fields                       |
| `core/auth/dto/login-request.dto.ts`   | CHANGE | Required fields                                                |

**Tests:** validation, emits on valid submit, shows the generic error on 401, keeps no password in state. **Done when:**
login works with keyboard and screen reader.

#### BP-43 Change password · S

**Depends on:** BP-31, BP-39

| Component                                        | Action | What                       |
|--------------------------------------------------|--------|----------------------------|
| `features/auth/pages/change-password-page/`      | NEW    | Forced and voluntary modes |
| `features/auth/components/change-password-form/` | NEW    | Current, new, confirm      |
| `shared/validators/password-match.validator.ts`  | NEW    | Cross-field validator      |
| `features/auth/auth.routes.ts`                   | CHANGE | Add the route              |

**Done when:** a new user is forced through this page at first login.

#### BP-44 Profile page · S

**Depends on:** BP-28, BP-40

| Component                              | Action | What                                                 |
|----------------------------------------|--------|------------------------------------------------------|
| `features/profile/pages/profile-page/` | NEW    | View and edit name, role and permissions (read-only) |
| `features/profile/profile.routes.ts`   | NEW    | Lazy route                                           |

#### BP-45 User administration · L · 🟠

**Depends on:** BP-29 to BP-31, BP-40

| Component                                              | Action | What                                                                           |
|--------------------------------------------------------|--------|--------------------------------------------------------------------------------|
| `features/users/data/users.repository.ts`              | NEW    | All `/users` calls                                                             |
| `features/users/pages/users-list-page/`                | NEW    | PrimeNG lazy table: search, role and status filters, server paging and sorting |
| `features/users/pages/user-detail-page/`               | NEW    | Detail and actions                                                             |
| `features/users/components/user-form/`                 | NEW    | Create and edit, role selector                                                 |
| `features/users/components/user-status-tag/`           | NEW    | Active, deactivated, locked                                                    |
| `features/users/components/temporary-password-dialog/` | NEW    | Shown once after a reset, never stored                                         |
| `features/users/users.routes.ts`                       | NEW    | Lazy, guarded by `user:read`                                                   |
| `app.routes.ts`                                        | CHANGE | Add the route (the commented block is there now)                               |

**Tests:** table parameters map to the query; actions hidden without permission; server rule errors shown (R1 to R3);
search is debounced. **Done when:** an admin can manage the complete account lifecycle.

#### BP-46 Role administration · M

**Depends on:** BP-20, BP-40

| Component                                 | Action | What                                 |
|-------------------------------------------|--------|--------------------------------------|
| `features/roles/data/roles.repository.ts` | NEW    | `/roles`, `/permissions`             |
| `features/roles/pages/roles-list-page/`   | NEW    | Table, system roles marked           |
| `features/roles/components/role-form/`    | NEW    | Name + permissions grouped by domain |
| `features/roles/roles.routes.ts`          | NEW    | Guarded by `role:read`               |

**Done when:** system roles can't be edited in the UI, and the server also refuses.

#### BP-47 Dashboard and error pages · S

**Depends on:** BP-40

| Component                                  | Action | What                                     |
|--------------------------------------------|--------|------------------------------------------|
| `features/dashboard/pages/dashboard-page/` | NEW    | Landing page                             |
| `features/errors/pages/forbidden-page/`    | NEW    | 403                                      |
| `features/errors/pages/not-found-page/`    | NEW    | 404                                      |
| `index.html`, title strategy               | CHANGE | Real title and favicon, per-route titles |

---

### M9: Observability

#### BP-48 Back-end logging · M · 🟠

**Replaces:** the back-end part of LOG-01, finishes SEC-02 · **Depends on:** M6

| Class                                            | Action | What                                                          |
|--------------------------------------------------|--------|---------------------------------------------------------------|
| `lombok.config`                                  | NEW    | `lombok.log.fieldName = log`                                  |
| `RequestCorrelationFilter`                       | NEW    | `X-Request-Id`, MDC, cleared in `finally`                     |
| `LogSanitizer`                                   | NEW    | `maskEmail`, strips CR/LF                                     |
| `JwtAuthenticationFilter`                        | CHANGE | MDC `userId`                                                  |
| `GlobalExceptionHandler`                         | CHANGE | `errorId` → `requestId` from the MDC                          |
| `AuthenticationRequestDTO` and other secret DTOs | CHANGE | Redacted `toString()`                                         |
| `CorsProperties`                                 | CHANGE | Expose `X-Request-Id`, remove `Authorization`                 |
| `application-prod.yml`                           | CHANGE | `logging.structured.format.console: ecs`                      |
| `config/checkstyle/checkstyle.xml`               | NEW    | Forbids `System.out` and `printStackTrace`, comments included |
| `ArchitectureTest`                               | CHANGE | No standard streams, no `java.util.logging`                   |

**Tests:** captured logs never contain a password, JWT or full email; one ERROR line for a failure. **Done when:**
`System.out` fails the build.

#### BP-49 Audit trail · M · 🟠

**Replaces:** the audit part of LOG-01 · **Depends on:** BP-48

| Class                                    | Action | What                                           |
|------------------------------------------|--------|------------------------------------------------|
| `AuditEvent`                             | NEW    | Enum of event types                            |
| `AuditLogger`                            | NEW    | The `AUDIT` logger, with key/value fields      |
| `AuthenticationAuditListener`            | NEW    | Login success and failure from security events |
| `UserAuditListener`, `RoleAuditListener` | NEW    | Listens to domain events                       |

**Events:** `LOGIN_SUCCESS`, `LOGIN_FAILURE`, `LOGOUT`, `ACCESS_DENIED`, `REFRESH_TOKEN_REUSE_DETECTED`,
`BOOTSTRAP_ADMIN_CREATED`, `USER_CREATED`, `USER_UPDATED`, `USER_DEACTIVATED`, `USER_PASSWORD_RESET`, `ROLE_CREATED`,
`ROLE_UPDATED`, `ROLE_DELETED`.

**Decision to make later:** logs may not be enough for financial compliance. A persisted `audit_event` table may be
required. Keep `AuditLogger` as the only entry point so that can be added without changing callers. **Done when:** every
security event produces one structured line.

#### BP-50 Health and metrics · S

**Replaces:** step 2 of OPS-01 · **Depends on:** BP-48

| File              | Action | What                                                                                                       |
|-------------------|--------|------------------------------------------------------------------------------------------------------------|
| `pom.xml`         | CHANGE | `spring-boot-starter-actuator`                                                                             |
| `application.yml` | CHANGE | Expose `health`, `info`, `metrics`, `prometheus`. Liveness and readiness. Separate management port in prod |
| `application.yml` | CHANGE | `server.shutdown: graceful`, Hikari settings                                                               |
| `SecurityConfig`  | CHANGE | Only health is public                                                                                      |

**Done when:** readiness reports Postgres and Redis.

---

### M10: Delivery

#### BP-51 Container images · M

**Replaces:** step 1 of OPS-01 · **Depends on:** BP-50

| File                          | Action | What                                               |
|-------------------------------|--------|----------------------------------------------------|
| `assofin/Dockerfile`          | NEW    | Multi-stage, layered jar, non-root, healthcheck    |
| `assofin-frontend/Dockerfile` | NEW    | Node build → nginx                                 |
| `assofin-frontend/nginx.conf` | NEW    | SPA fallback, `/api` proxy, gzip, security headers |
| `.dockerignore`               | NEW    | Both projects                                      |

#### BP-52 Production composition · S

**Depends on:** BP-51

| File                | Action | What                                                                     |
|---------------------|--------|--------------------------------------------------------------------------|
| `compose.prod.yaml` | NEW    | Back end + front end, secrets from the environment, `depends_on` healthy |
| `ci.yml`            | CHANGE | Docker build, gitleaks, dependency scan                                  |

**Done when:** the full stack starts from one command, over HTTPS behind a proxy, and login works with `__Host-`
cookies. That's the first real check of the `Secure` behavior.

#### BP-53 Test hardening and documentation · M

**Replaces:** TST-01, steps 6 and 7 of OPS-01 · **Depends on:** BP-52

| File                       | Action | What                                                                                     |
|----------------------------|--------|------------------------------------------------------------------------------------------|
| `e2e/` (Playwright)        | NEW    | Login, forced password change, create user, deactivate, permission denied, token refresh |
| `pom.xml`                  | CHANGE | JaCoCo minimum (70% on application and domain)                                           |
| `OpenApiConfig`            | NEW    | Title, version, cookie security scheme, `ProblemDetail` schema                           |
| `README.md` (root)         | NEW    | Architecture, setup, env reference, testing, deployment                                  |
| `docs/adr/`                | NEW    | ADR-01 to ADR-14                                                                         |
| `docs/adding-a-feature.md` | NEW    | Step-by-step guide for a new domain                                                      |

#### BP-54 Make it a real template · S

**Depends on:** BP-53

| Item           | Action | What                                                                           |
|----------------|--------|--------------------------------------------------------------------------------|
| Project naming | CHANGE | Document how to rename `assofin`                                               |
| Example domain | NEW    | A small sample feature in the docs, showing permissions, migration, API and UI |
| Release        | NEW    | Tag `v1.0.0`, changelog                                                        |

**Done when:** a developer can start a new project from the template by following the docs alone.

---

## 8. Definition of done (every issue)

**Back end**

- [ ] `./mvnw verify` passes.
- [ ] New behavior has tests, including the failure cases.
- [ ] You watched the important tests fail once, by breaking the code on purpose.
- [ ] No entity in any DTO, no secret in any log.
- [ ] Schema changes go through a Flyway migration.
- [ ] New endpoints are guarded and documented in OpenAPI.

**Front end**

- [ ] `npm run lint`, `npm test` and `npm run build` pass.
- [ ] No `any`, no hard-coded user-facing text.
- [ ] Forms have labels, and keyboard use works.
- [ ] Permissions are checked in the UI **and** on the server.

**Both**

- [ ] One issue per branch, reviewed through a PR.
- [ ] Conventional commit message that references the issue.
- [ ] The correction file or this roadmap is updated.

---

## 9. Traceability: old issues to new issues

| Old issue      | New issue           | | Old issue      | New issue             |
|----------------|---------------------|-|----------------|-----------------------|
| Step 1.6       | BP-03               | | PER-03         | BP-20                 |
| Step 1.7       | BP-04               | | PER-04         | BP-16                 |
| SEC-02 (rest)  | BP-34, BP-48        | | ARC-01         | BP-19                 |
| SEC-05         | BP-02               | | ARC-02         | BP-11                 |
| SEC-06         | BP-26               | | ARC-04         | BP-15                 |
| SEC-07         | BP-27               | | CFG-01, CFG-02 | BP-06                 |
| SEC-08         | BP-07, BP-23        | | CFG-03         | BP-07                 |
| SEC-09         | BP-22, BP-24, BP-25 | | CFG-04         | BP-06, BP-32          |
| AUTH-01        | BP-24, BP-38        | | CFG-05         | BP-05                 |
| BUG-01         | BP-14               | | OPS-01         | BP-10, BP-50 to BP-53 |
| BUG-03         | BP-20               | | LOG-01         | BP-34, BP-48, BP-49   |
| BUG-04         | BP-06, BP-21        | | TST-01         | BP-09, BP-53          |
| BUG-05         | BP-28               | | HYG-01         | BP-08                 |
| BUG-06         | BP-32               | | FE-01          | BP-37                 |
| BUG-09, BUG-10 | BP-20               | | FE-02, FE-08   | BP-09                 |
| BUG-11         | BP-17               | | FE-03          | BP-35                 |
| ERR-01         | BP-17, BP-18        | | FE-04          | BP-39                 |
| PER-01         | BP-13               | | FE-05          | BP-36                 |
| PER-02         | BP-12               | | FE-06          | BP-09, BP-33          |
| FE-09          | BP-01               | | FE-07          | BP-34, BP-40, BP-41   |

**Already closed, so not in this roadmap:** SEC-01, SEC-03, SEC-04, BUG-02, BUG-08. **Obsolete:** BUG-07, ARC-03.
