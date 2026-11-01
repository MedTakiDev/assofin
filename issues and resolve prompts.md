# Assofin Boilerplate: Engineering Review, Issues & Fix Prompts

> Scope: the whole repo on branch `setup-role-domain-backend` (Spring Boot 4.1 / Java 21 backend in `assofin/`, Angular 22 + PrimeNG frontend in `assofin-frontend/`, Docker Compose infra at the root).
> Reviewed read-only. No code was changed and nothing was committed.

---

## 1. Overall rating

| Area | Score /10 | Comment |
|---|---|---|
| Project structure / modularity | **6.5** | Package-by-feature (`auth`, `role`, `user`, `permission`), a port/adapter for the token store, and clean Angular `core / features / layouts` folders. Let down by catch-all packages (`handler`, `infrastructure`) and by infrastructure code that hard-codes domain enums. |
| Security | **3** | Good ideas (HttpOnly cookie, hashed activation tokens, method security). Several critical defects, though: every new user becomes ADMIN, passwords go to stdout, error messages leak internals, and `secure(false)` is hard-coded. |
| Correctness | **3.5** | Several runtime bugs on the main paths: JPA auditing isn't enabled, roles aren't seeded, the authority string is double-prefixed, lazy loading runs outside a transaction, and the frontend login loops back to the login page. |
| Error handling & API contract | **4** | A central handler and an error-code catalog exist, but HTTP statuses don't match the catalog, the generic handler catches `AccessDenied` and returns 500, there's no domain exception hierarchy, and the API returns entities. |
| Persistence | **3.5** | `ddl-auto: update` in prod, no migrations, mixed ID strategies, fragile `equals/hashCode`, N+1 queries. |
| Configuration / DevOps | **3.5** | Env-driven config is a good start. But profiles disagree, env var names drift, prod has no Dockerfile, there's no CI, no health checks, and compose files are duplicated. |
| Testing | **1** | One `contextLoads` test that needs live Postgres and Redis. The frontend specs are the CLI-generated "should create" stubs. |
| Code hygiene | **4.5** | Lots of commented-out code, `System.out`/`console.log`, `printStackTrace`, unused imports and classes, typos, leftover text from another project ("Avalon Odyssey"). |
| Frontend architecture | **5.5** | Modern stack (standalone components, signals, functional guards and interceptors, lazy routes, path aliases, a repository layer). But TypeScript `strict` is off, the DTOs don't match the backend, guards are broken or inverted, and the i18n files are empty and never wired up. |

### **Overall: about 4 / 10 against an enterprise-grade standard. It's a promising draft, not yet a reusable boilerplate.**

**Verdict.** The *architecture intent* is senior-level: feature packages, a permission contract interface (`PermissionDefinition`), a Redis token store behind a port, cookie-based JWT, a signal-based auth store, a repository → service → component split on the frontend. The *execution* isn't production-safe yet. A boilerplate gets copied into every future project, so its defects multiply. Fix the 🔴 Critical and 🟠 High items before reusing it anywhere.

### What is already good (keep it)
- Feature-oriented backend packages, and DTOs written as Java `record`s with Bean Validation.
- `ActivationTokenStore` interface plus `RedisActivationTokenStore` adapter. Tokens come from `SecureRandom`, only the **SHA-256 hash** is stored, and they have a TTL. That's a good design.
- `PermissionDefinition` plus per-domain permission enums, synced to the DB at startup.
- `Role.replacePermissions()` updates permissions as a diff instead of delete-all and re-insert.
- JWT in an `HttpOnly`, `SameSite=Strict` cookie, a stateless session, and custom JSON `AuthenticationEntryPoint` / `AccessDeniedHandler`.
- `@ConfigurationProperties` for CORS, JWT and the frontend URL.
- Angular: standalone components, signals, `inject()`, functional `CanMatchFn` guards and `HttpInterceptorFn`, lazy `loadComponent`/`loadChildren`, `@core/*` and `@auth/*` path aliases, env vars through `@ngx-env`.

---

## 2. Issue index

Severity: 🔴 Critical (security hole or broken main flow) · 🟠 High · 🟡 Medium · 🔵 Low / hygiene

> **Phase 1 impact** (see [section 3](#phase-1)): resolves SEC-01 and BUG-02; pulls in BUG-01 and BUG-03 as prerequisites; makes BUG-07 and ARC-03 obsolete; shrinks BUG-06, SEC-06, SEC-08, FE-05 and FE-07.

| ID | Sev | Title |
|---|---|---|
| [SEC-01](#sec-01) | 🔴 | Every registered user gets `ROLE_ADMIN` |
| [SEC-02](#sec-02) | 🔴 | Plain-text credentials logged (backend `System.out` + frontend `console.log`) |
| [SEC-03](#sec-03) | 🔴 | Generic exception handler leaks internal messages and hides `AccessDenied` as 500 |
| [SEC-04](#sec-04) | 🟠 | Cookie `secure(false)` hard-coded in 3 places; cookie max-age not tied to JWT expiry |
| [SEC-05](#sec-05) | 🟠 | Swagger and a non-existent `/ldap/**` are public in every profile |
| [SEC-06](#sec-06) | 🟠 | No brute-force protection or rate limiting on `/auth/login`, `/auth/register`, activation |
| [SEC-07](#sec-07) | 🟡 | CSRF disabled while using cookie auth (relies only on SameSite) |
| [SEC-08](#sec-08) | 🟡 | Redis password length logged at startup; Redis password passed on the healthcheck command line |
| [SEC-09](#sec-09) | 🟡 | JWT: no issuer/audience, no revocation/refresh strategy, no startup check of key strength |
| [BUG-01](#bug-01) | 🔴 | JPA auditing not enabled: `createdDate NOT NULL` inserts fail for Role/RolePermission |
| [BUG-02](#bug-02) | 🔴 | Roles are never seeded: registration throws on a fresh DB; nobody can get role permissions |
| [BUG-03](#bug-03) | 🔴 | Authority string double-prefixed: `"ROLE_" + "ROLE_ADMIN"` = `ROLE_ROLE_ADMIN` |
| [BUG-04](#bug-04) | 🔴 | `User.getAuthorities()` touches LAZY `role` outside a transaction (login/JWT filter) |
| [BUG-05](#bug-05) | 🔴 | `/users/me` returns the JPA `Role` entity (lazy proxy serialization, data leak, contract mismatch) |
| [BUG-06](#bug-06) | 🟠 | `@Async` without `@EnableAsync`; mail failure after the user is saved leaves orphan accounts |
| [BUG-07](#bug-07) | 🟠 | Activation flow is non-atomic (validate → save → consume), is a GET with side effects, and has no resend |
| [BUG-08](#bug-08) | 🟠 | Wrong `@Param` import (`io.lettuce...`) in `RoleRepository` |
| [BUG-09](#bug-09) | 🟠 | `RolePageRequestDTO` `@Min(20)` on `size`, and `page`/`size` default to 0 → every call without params is a 400 |
| [BUG-10](#bug-10) | 🟠 | `UpdateRoleRequestDTO` has `@NotNull` on all fields while the endpoint is PATCH |
| [BUG-11](#bug-11) | 🟡 | `IllegalArgumentException` used for business errors → 500 instead of 409/400 |
| [ERR-01](#err-01) | 🟠 | Error contract: HTTP status ignores `BusinessErrorCodes.httpStatus`, typo `exceptionDec`, no RFC 9457 ProblemDetail, missing handlers |
| [PER-01](#per-01) | 🟠 | `ddl-auto: update` (and `show-sql`) in prod; no Flyway/Liquibase |
| [PER-02](#per-02) | 🟡 | Mixed ID generation strategies; `Permission.equals/hashCode` on mutable `id`; `LocalDate` audit fields |
| [PER-03](#per-03) | 🟡 | N+1 queries when listing roles and building authorities; JWT filter hits the DB on every request |
| [PER-04](#per-04) | 🟡 | Spring vs Jakarta `@Transactional` mixed; missing transactions at service boundaries |
| [ARC-01](#arc-01) | 🟠 | `PermissionRegistry` hard-codes domain enums (infrastructure depends on domains); test enum `ProductPermissions` ships in `main` |
| [ARC-02](#arc-02) | 🟡 | Package layout inconsistencies (`handler`, `infrastructure`, interface in `entity` package, singular/plural routes) |
| [ARC-03](#arc-03) | 🟡 | Redundant custom Redis config shadows Spring Boot auto-config |
| [ARC-04](#arc-04) | 🟡 | `User` entity design: email is username, `username = "first last"`, unused LDAP fields, `@Builder` on entities |
| [CFG-01](#cfg-01) | 🟠 | Profile configs drift (prod file titled "dev", wrong host, different env names, hard-coded port, SMTP auth always on) |
| [CFG-02](#cfg-02) | 🟠 | `.env` handling: Spring never loads `.env`; inline comments and spaces corrupt values; wrong comment on expiry; stale `.gitignore` paths |
| [CFG-03](#cfg-03) | 🟡 | `compose.example.yaml` duplicates and drifts from `compose.yaml`; `spring-boot-docker-compose` overlaps manual config |
| [CFG-04](#cfg-04) | 🟡 | Hard-coded sender/subject from another project, CORS placeholder origin, context-path trailing slash |
| [CFG-05](#cfg-05) | 🟡 | `pom.xml`: both springdoc **webmvc and webflux** starters; empty metadata placeholders; no quality plugins |
| [OPS-01](#ops-01) | 🟠 | No Dockerfiles, no CI pipeline, no Actuator/health, no structured logging, no root README |
| [LOG-01](#log-01) | 🟠 | No logging strategy: SLF4J + structured logs, correlation id, audit trail, frontend `LoggerService`, `no-console` / ArchUnit / Checkstyle guardrails (remaining SEC-02 scope) |
| [TST-01](#tst-01) | 🟠 | Essentially zero tests; the only test needs live infra |
| [HYG-01](#hyg-01) | 🔵 | Commented-out code, unused classes/imports/fields, `printStackTrace`, TODOs, typos |
| [FE-01](#fe-01) | 🔴 | Login loop: session state cached as "unauthenticated" and never refreshed after login |
| [FE-02](#fe-02) | 🟠 | TypeScript `strict` mode and `strictTemplates` disabled |
| [FE-03](#fe-03) | 🟠 | Error handling chain broken: `ApiService` unwraps errors, components read wrong fields, `notificationService.show(string)` |
| [FE-04](#fe-04) | 🟠 | Guards: `reverseAuthGuard` inverted and unused; redirects to non-existent `/dashboard` and `/login` |
| [FE-05](#fe-05) | 🟠 | Frontend/backend contract mismatch (permissions format, profile shape, register DTO, id type, signup response) |
| [FE-06](#fe-06) | 🟡 | `ApiService` bugs: broken trailing-slash regex, hard-coded `api/v1` duplicated with backend context path, no `patch`, timeout config unused |
| [FE-07](#fe-07) | 🟡 | Incomplete features: empty signup form, no activation page, empty i18n files not wired, empty `LoggerService`, missing image |
| [FE-08](#fe-08) | 🟡 | Tooling: no ESLint, no Prettier script, undeclared dependency `@primeicons/angular`, nested `<form>`, default title/favicon |

---

<a id="phase-1"></a>
## 3. Phase 1: Closed registration, bootstrapped admin, admin-provisioned users

> **Decision.** For now there is **no public self-registration**. On startup the application creates the `ADMIN` role, which holds every permission, and one default admin account. From then on, only users holding the `user::create` permission (the admin, at first) can create accounts, through `POST /users`. The email activation flow and Redis are removed because nothing uses them any more.

**Resolves:** [SEC-01](#sec-01) (nobody can self-assign a role any more) and [BUG-02](#bug-02) (roles and grants are seeded at startup).
**Also requires in the same branch:** [BUG-01](#bug-01). `Role.createdDate` is `NOT NULL` and auditing is off, so seeding the `ADMIN` role fails without it. Applying [BUG-03](#bug-03) here is strongly recommended because the seeded role names decide the authority strings.
**Makes obsolete or shrinks:** [BUG-07](#bug-07) (activation flow is gone), [ARC-03](#arc-03) (Redis config is gone), the Redis half of [SEC-08](#sec-08), the `/auth/register` and activation parts of [SEC-06](#sec-06), [BUG-06](#bug-06) (no mail is sent during registration any more), the register DTO part of [FE-05](#fe-05), and the signup/activation parts of [FE-07](#fe-07).

### Phase 1 at a glance

| Step | What | Output |
|---|---|---|
| [1.1](#phase-1-1) | Open a PR `setup-role-domain-backend` → `main` | PR URL |
| [1.2](#phase-1-2) | Merge the PR, sync local `main` | `main` contains the role domain work |
| [1.3](#phase-1-3) | Create branch `admin-bootstrap-user-provisioning` | Clean branch off `main` |
| [1.4](#phase-1-4) | Remove self-registration, activation and Redis | App starts without Redis; `/auth/register` gone |
| [1.5](#phase-1-5) | Bootstrap permissions → `ADMIN` role (all permissions) → default admin user | Fresh DB starts with a working admin |
| [1.6](#phase-1-6) | `POST /users` (controller → service → repository), guarded by `user::create` | Admin creates the other accounts |
| [1.7](#phase-1-7) | Verify, then PR the branch to `main` | Phase 1 done |

Each step is one commit, or a small group of commits, so the PR can be reviewed step by step.

---

<a id="phase-1-1"></a>
### 1.1 Open a pull request to `main`

The current branch `setup-role-domain-backend` holds the role/permission domain work and the latest review fixes. Merge it first so Phase 1 starts from a clean `main`.

**Before opening it:** decide whether the two review files (`issues and prompts.md`, `issues and resolve prompts.md`) belong in the repo. If they do, move them to `docs/review/` and commit them. If they don't, add them to `.gitignore`. Don't leave them untracked across branch switches.

**Prompt:**
```
In the repo root (branch setup-role-domain-backend):
1. Run `git status` and make sure only intended changes are present. Run the backend build
   (`cd assofin && ./mvnw -q verify`) and the frontend build (`cd assofin-frontend && npm run build`)
   and report failures instead of hiding them.
2. Push the branch: `git push -u origin setup-role-domain-backend`.
3. Open a PR to main with the GitHub CLI:
   gh pr create --base main --head setup-role-domain-backend \
     --title "Role domain backend: roles, permissions, exception handling, frontend audit fixes" \
     --body "<summary of the commits since main: git log --oneline main..HEAD>"
4. Print the PR URL. Do not merge yet.
```

---

<a id="phase-1-2"></a>
### 1.2 Merge the pull request to `main`

**Prompt:**
```
1. Check the PR is mergeable and checks (if any) are green: `gh pr view --json mergeable,statusCheckRollup`.
2. Merge with a merge commit (consistent with previous PRs, e.g. #4):
   gh pr merge <PR_NUMBER> --merge --delete-branch
3. Sync locally:
   git checkout main && git pull --ff-only origin main
4. Confirm `git log --oneline -5` shows the merge commit.
```

---

<a id="phase-1-3"></a>
### 1.3 Create the Phase 1 branch

**Branch name:** `admin-bootstrap-user-provisioning`. It follows the existing kebab-case style (`setup-role-domain-backend`, `implement-cookie-based-authentication`) and names both halves of the change: the admin created at bootstrap and the admin-driven user provisioning.
Alternatives if you adopt prefixes later: `feature/admin-provisioned-accounts`, `feature/closed-registration-admin-bootstrap`.

```
git checkout main && git pull --ff-only
git checkout -b admin-bootstrap-user-provisioning
```

---

<a id="phase-1-4"></a>
### 1.4 Remove self-registration, account activation and Redis

**Where (current code):**
- `auth/controller/AuthenticationController.java`: `register(...)` (`POST /auth/register`) and `activateAccount(...)` (`GET /auth/activate-account`).
- `auth/service/AuthenticationService.java`: `register(...)`, `activateAccount(...)`, `sendActivationEmail(...)` and the dependencies used only by them (`MailService`, `FrontendProperties`, `ActivationTokenService`, `UserRepository`).
- `user/service/UserService.java`: `saveUser(RegistrationRequestDTO)`, the registration-specific "repository-side" write. `UserRepository` has no register-only method. Keep `existsByEmail` and `findByEmail`, because step 1.6 and login still need them.
- `auth/dto/request/RegistrationRequestDTO.java`, `auth/dto/response/RegistrationResponseDTO.java`.
- `auth/activation/` (`ActivationTokenService`, `ActivationTokenStore`).
- `infrastructure/redis/` (`RedisConfig`, `RedisProperties`, `RedisActivationTokenStore`, `components/RedisDebugConfig`).
- `pom.xml`: `spring-boot-starter-data-redis`. `application-dev.yml` / `application-prod.yml`: `spring.data.redis.*`. `application.yml`: `application.frontend.activation-url` (`FRONTEND_ACTIVATION_URL`).
- Root `compose.yaml` **and** `compose.example.yaml` (there is no `docker-compose.yml`): the `redis` service and the `redis-data` volume. Root `.env` / `.env.example`: `REDIS_*` variables.
- `SecurityConfig`: `/auth/**` is `permitAll`, which is broader than needed once register and activate are gone.

**Prompt:**
```
Context: Spring Boot backend in assofin/ (base package com.association.assofin), compose files at
the repo root. Public self-registration and email activation are being removed: accounts will be
created by an administrator (a later step adds POST /users). Delete the code rather than commenting
it out; git history is the archive. Do not touch unrelated code.

A. Registration endpoint and logic
   1. AuthenticationController: delete register(...) (POST /auth/register) and activateAccount(...)
      (GET /auth/activate-account). Remove the now-unused UserService field and the
      `throws MessagingException` / jakarta.mail imports.
   2. AuthenticationService: delete register(...), activateAccount(...), sendActivationEmail(...)
      and the fields only they used (MailService, FrontendProperties, ActivationTokenService,
      UserRepository). Keep authenticate(...) working unchanged.
   3. UserService: delete saveUser(RegistrationRequestDTO) and the RoleEnum.ROLE_ADMIN lookup.
      Keep existsByEmail/findByEmail in UserRepository (still needed).
   4. Delete auth/dto/request/RegistrationRequestDTO and auth/dto/response/RegistrationResponseDTO.

B. Activation token approach
   5. Delete the package auth/activation (ActivationTokenService, ActivationTokenStore).
   6. Delete the package infrastructure/redis entirely (RedisConfig, RedisProperties,
      RedisActivationTokenStore, components/RedisDebugConfig). Remove any
      @EnableConfigurationProperties / @ConfigurationPropertiesScan reference to RedisProperties.
   7. Remove FrontendProperties.activationUrl and the application.frontend.activation-url property
      (delete FrontendProperties if it becomes empty, plus its registration).
   8. pom.xml: remove spring-boot-starter-data-redis (and any lettuce/redis test dependency).
   9. application-dev.yml / application-prod.yml: remove the spring.data.redis block.
      If an auto-config exclusion is needed for tests, add none; Redis must simply be absent.
   10. Mail: leave the mail module (MailService, templates, spring-boot-starter-mail, maildev) in
       place but unused by auth. It will serve future "account created / reset password" emails.
       Delete only templates/activate_account.html and EmailTemplateName.ACTIVATION_ACCOUNT if that
       leaves the code compiling; otherwise leave them and add a TODO referencing this step.

C. Infrastructure
   11. compose.yaml and compose.example.yaml: remove the redis service, its healthcheck and the
       redis-data volume. Remove REDIS_* variables from .env.example (and tell me to remove them
       from my local .env; do not edit .env yourself).

D. Security surface
   12. SecurityConfig: replace the "/auth/**" permitAll with exactly "/auth/login" and
       "/auth/logout" so a future /auth endpoint is not public by accident.

E. Frontend (assofin-frontend/), so the UI does not call a dead endpoint
   13. Remove the signup route from app.routes.ts, the signup-form component, register-request.dto.ts,
       and the register(...) methods in core/auth/repositories/auth.repository.ts and
       core/auth/services/auth.service.ts. Remove any "Create account" link from the login page.

Verification:
   - `grep -rniE "redis|activation|RegistrationRequest|/register" assofin/src assofin-frontend/src compose*.yaml`
     returns nothing (except mail template leftovers explicitly kept in step 10).
   - `cd assofin && ./mvnw -q verify` passes and the app starts with only Postgres running.
   - POST /auth/register now returns 401/404 (not 2xx). `cd assofin-frontend && npm run build` passes.
Commit message: "remove self-registration, email activation and redis".
```

---

<a id="phase-1-5"></a>
### 1.5 Application bootstrap: permissions → `ADMIN` role with all permissions → default admin user

**Design:**
- Three idempotent `ApplicationRunner`s in one package `infrastructure/bootstrap`, run in a fixed order:
  1. `PermissionInitializer` (exists, gets `@Order(1)`): syncs every `PermissionDefinition` from the registry into `permission`.
  2. `RoleInitializer` (`@Order(2)`): ensures the system roles exist. `ADMIN` is granted **every** permission in the registry, add-missing-only, so a permission added in code later reaches the admin at the next startup without wiping manual changes. `USER` is created with no permissions.
  3. `AdminUserInitializer` (`@Order(3)`): if **no user holds the `ADMIN` role**, it creates one from configuration.
- Role names are stored **without** the `ROLE_` prefix (`ADMIN`, `USER`), and `User.getAuthorities()` adds `ROLE_` once. This fixes [BUG-03](#bug-03).
- The admin credentials come from env vars, never from code. The password is never logged.

**Prompt:**
```
Context: Spring Boot backend in assofin/. Self-registration was removed (step 1.4). The application
must now bootstrap itself on an empty database: permissions, an ADMIN role holding all permissions,
and a default admin user. Prerequisite (do it first, in this branch): enable JPA auditing as described
in BUG-01, because Role.createdDate is NOT NULL and Role inserts fail without auditing.

1. System roles
   - Replace role/RoleEnum (which implements GrantedAuthority and stores "ROLE_ADMIN") with
     role/SystemRole { ADMIN, USER }, a plain enum. Persist role names without the "ROLE_" prefix.
   - User.getAuthorities(): emit "ROLE_" + role.getName() exactly once, plus one authority per
     permission name. Remove every remaining reference to RoleEnum.

2. User permissions (needed so the admin can be granted, and step 1.6 can check, user management)
   - Create user/permissions/UserPermissions implements PermissionDefinition with
     CREATE("user::create"), READ("user::read"), UPDATE("user::update"), DELETE("user::delete"),
     matching the "role::create" style of RolePermissions.
   - Register UserPermissions in PermissionRegistry (replace the commented UserPermission line).
   - Add userCreate()/userRead()/userUpdate()/userDelete() to PermissionExpressions ("permissions" bean).

3. Bootstrap properties
   - Create a validated @ConfigurationProperties record "application.bootstrap.admin" with
     email (@Email @NotBlank), password (@NotBlank, min 12), firstname, lastname (defaults "System",
     "Administrator"). Bind from ADMIN_EMAIL, ADMIN_PASSWORD, ADMIN_FIRSTNAME, ADMIN_LASTNAME in
     application.yml. Add these to .env.example with placeholder values and a comment that the
     password must be changed in every real environment. Do not edit .env.

4. Initializers (package infrastructure/bootstrap; move PermissionInitializer there)
   - PermissionInitializer: add @Order(1). Keep its logic.
   - RoleInitializer implements ApplicationRunner, @Order(2), @Transactional:
       * for each SystemRole, create the role if roleRepository.existsByName(name) is false;
       * ADMIN: load all Permission rows for the registry values and add the missing ones only
         (do not remove extra grants; document this choice in Javadoc);
       * USER: no permissions by default.
   - AdminUserInitializer implements ApplicationRunner, @Order(3), @Transactional:
       * if userRepository.existsByRole_Name("ADMIN") → log INFO "Admin account present, skipping" and return;
       * if a user already exists with the configured email but is not ADMIN → throw
         IllegalStateException with a clear message (fail fast, never silently promote);
       * otherwise create the user: email, firstname, lastname, password = passwordEncoder.encode(...),
         role = ADMIN, enabled = true, accountLocked = false;
       * log INFO "Bootstrap admin created: <email>". NEVER log the password.
   - Concurrency: rely on the unique constraints (role.name, permission.name, user.email) and catch
     DataIntegrityViolationException in each initializer, treating it as "another instance did it".
   - If the admin properties are missing while no admin exists, startup must fail with a message
     naming the missing env vars (no hard-coded fallback credentials).

5. Repositories
   - UserRepository: add boolean existsByRole_Name(String roleName).
   - RoleRepository: keep findByName; also fix the wrong @Param import (BUG-08) while here.

6. Tests (Testcontainers Postgres, @SpringBootTest)
   - Empty DB → after startup: every registry permission exists; ADMIN holds all of them; USER holds none;
     exactly one admin user exists, enabled, with a BCrypt password.
   - Restarting (running the runners a second time) creates no duplicates.
   - The bootstrap admin can POST /auth/login and then GET /role (200).
Commit message: "bootstrap admin role with all permissions and default admin user".
```

---

<a id="phase-1-6"></a>
### 1.6 Create-user endpoint: `POST /users`

This replaces "register". The path is `UserController` → `UserService.createUser(...)` → `UserRepository.save(...)`.

**Prompt:**
```
Context: Spring Boot backend in assofin/. Self-registration is removed; an administrator creates
accounts. Permissions "user::create" etc. exist and ADMIN holds them (step 1.5).

1. DTOs (records, in user/dto)
   - request/CreateUserRequestDTO: firstname (@NotBlank, max 100), lastname (@NotBlank, max 100),
     email (@NotBlank @Email, max 255), password (@NotBlank, 12–128 chars), roleName (@NotBlank).
   - response/UserResponseDTO: id, email, firstname, lastname, roleName, enabled, createdAt.
     Never return the User or Role entity (see BUG-05).
   - A small UserMapper (MapStruct if already used for RoleMapper, otherwise a static method).

2. Service: UserService.createUser(CreateUserRequestDTO) @Transactional
   (org.springframework.transaction.annotation.Transactional)
   - Normalize email (trim + lowercase).
   - If userRepository.existsByEmail(email) → throw a business exception mapped to 409
     (BusinessErrorCodes.ENTITY_ALREADY_EXISTS), NOT IllegalArgumentException (see BUG-11).
   - Resolve the role by name via RoleService/RoleRepository → 404 business exception if missing.
   - Build the User: password = passwordEncoder.encode(...), enabled = true (no activation step any more),
     accountLocked = false, username consistent with the existing convention.
   - Persist with userRepository.save(user) and return the mapped UserResponseDTO.
   - Policy: an admin may create another ADMIN. Keep it, but document it in the Javadoc.

3. Controller: UserController
   - @PostMapping, @PreAuthorize("hasAuthority(@permissions.userCreate())"), @Valid body.
   - Return 201 Created with a Location header /users/{id} and the UserResponseDTO body.
   - Add OpenAPI annotations (summary, 201/400/403/409 responses).

4. Tests
   - @WebMvcTest(UserController): 201 with valid body + authority; 400 on invalid body;
     403 without user::create; 401 unauthenticated.
   - Service unit test: duplicate email → 409 exception; unknown role → 404 exception;
     password stored encoded; user enabled.
   - Integration (Testcontainers): bootstrap admin logs in, creates a USER account, the new user
     can log in and gets 403 on POST /users and POST /role.
Commit message: "add admin-only create user endpoint".
```

---

<a id="phase-1-7"></a>
### 1.7 Verify and open the Phase 1 pull request

**Definition of done:**
- [ ] Empty database + only Postgres running → the app starts, logs "Bootstrap admin created", and no Redis is needed.
- [ ] `POST /auth/register` and `GET /auth/activate-account` no longer exist; `/auth/**` is not blanket `permitAll`.
- [ ] Admin login works; `GET /role` → 200; `POST /users` → 201.
- [ ] A user created by the admin can log in and is refused (403) on admin endpoints.
- [ ] No password or token appears in logs.
- [ ] `./mvnw verify` and `npm run build` pass; the new tests are green.
- [ ] Issue statuses updated in this file: [SEC-01](#sec-01) and [BUG-02](#bug-02) resolved; [BUG-07](#bug-07) and [ARC-03](#arc-03) obsolete.

```
git push -u origin admin-bootstrap-user-provisioning
gh pr create --base main --head admin-bootstrap-user-provisioning \
  --title "Closed registration: bootstrapped admin and admin-provisioned users" \
  --body "Resolves SEC-01, BUG-02 (and BUG-01, BUG-03 as prerequisites). Removes self-registration, email activation and Redis."
```

**Follow-ups (not in Phase 1):** a forced password change on first login for admin-created users (and for the bootstrap admin), `GET/PATCH/DELETE /users` behind `user::read/update/delete`, an admin "Users" page in the frontend, and optionally an email invitation flow that reuses the mail module.

---

## 4. Detailed issues and fix prompts

Each prompt is self-contained and can be pasted into an AI coding assistant (or used as a ticket description). Apply them roughly in index order: the critical ones unblock the rest.

---

<a id="sec-01"></a>
### SEC-01 🔴 Every registered user gets `ROLE_ADMIN`

> ✅ **Resolved by [Phase 1](#phase-1).** Self-registration is removed (step [1.4](#phase-1-4)), the admin is bootstrapped (step [1.5](#phase-1-5)) and accounts are created through `POST /users` (step [1.6](#phase-1-6)). The prompt below is kept for reference only; its "register as USER" approach is superseded.
**Where:** `assofin/src/main/java/com/association/assofin/user/service/UserService.java:30` (commit `802a44b` "change default user role to admin role on register").
**Problem:** Self-registration assigns `ROLE_ADMIN`. Anyone on the internet can create an administrator account. That's a privilege escalation by design.
**Impact:** Full compromise of any project built on this boilerplate.

**Prompt:**
```
In the Spring Boot backend at assofin/, self-registration currently assigns ROLE_ADMIN
(UserService.saveUser(RegistrationRequestDTO), line ~30). Fix this securely:

1. Self-registered users must receive the least-privileged default role (ROLE_USER / "USER").
   Make the default role name configurable via @ConfigurationProperties
   (e.g. application.security.default-role=USER) instead of hard-coding it.
2. Provide a safe way to bootstrap the first administrator:
   - Create an ApplicationRunner "AdminBootstrapInitializer" that, only if no user with the
     ADMIN role exists, creates one from env vars (ADMIN_EMAIL, ADMIN_PASSWORD) with the password
     BCrypt-encoded and enabled=true. If the vars are absent, log a WARN and skip.
   - Never log the password.
3. Add an integration test proving that POST /auth/register creates a user whose role is USER,
   and that this user receives 403 on POST /role.
Do not change unrelated code. Explain the change in the commit message.
```

---

<a id="sec-02"></a>
### SEC-02 🔴 Plain-text credentials logged

> 🟡 **Partially resolved on `admin-bootstrap-user-provisioning`.** The credential prints in `AuthenticationController.authenticate` (and the same prints in its commented-out old implementation), the `console.log` calls in `login-form.ts` (`signIn`) and `login-page.ts` (`onLogin`), and the `printStackTrace()` calls in `GlobalExceptionHandler` are removed. **Still open, moved to [LOG-01](#log-01):** introducing SLF4J project-wide, masked login audit events with the client IP, the frontend `LoggerService`, and the ESLint `no-console` / ArchUnit / Checkstyle guardrails. Leftovers LOG-01 also covers: `System.out.println(...getPassword())` in the commented LDAP block (`AuthenticationController:86-87`), `console.log(data.message)` in `login-page.ts:31`, `console.error` in `api.service.ts:59`, and `GlobalExceptionHandler` now logging nothing at all for unexpected errors.

**Where:** `AuthenticationController.java:39-40` (`System.out.println(email/password)`), `login-form.ts:59` (`console.log('Sending payload:', …)`), `login-page.ts:46,50`.
**Problem:** Passwords end up in server stdout and container logs (shipped to log aggregators) and in the browser console.

**Prompt:**
```
Remove every place where credentials or tokens are printed:
- Backend: AuthenticationController.authenticate prints email and password with System.out.println.
  Delete these lines and also the commented-out blocks that contain the same prints.
  Introduce SLF4J logging (Lombok @Slf4j) project-wide and replace all System.out / printStackTrace
  usage with log.debug/info/warn/error. Never log passwords, JWTs, activation tokens, or full
  request bodies of /auth/**. Log login success/failure as an audit event with the email masked
  (e.g. j***@domain.com) and the client IP.
- Frontend: remove console.log in login-form.ts (signIn) and login-page.ts (onLogin).
  Route any remaining diagnostics through LoggerService (src/app/core/services/logger.service.ts)
  that is a no-op in production builds (use isDevMode()).
- Add an ESLint rule "no-console": "error" (allow only inside LoggerService) and a Checkstyle /
  ArchUnit rule forbidding System.out and printStackTrace in the backend so this can't regress.
```

---

<a id="sec-03"></a>
### SEC-03 🔴 Generic exception handler leaks internals and turns `AccessDenied` into 500
**Where:** `handler/GlobalExceptionHandler.java:128-140` (also 87-115, 59-67).
**Problem:**
- `@ExceptionHandler(Exception.class)` returns `exp.getMessage()` to the client: SQL errors, class names, Hibernate messages. It uses `printStackTrace()` instead of a logger.
- With method security, `@PreAuthorize` failures throw `AuthorizationDeniedException` inside the MVC dispatch. The `Exception.class` handler catches it **before** Spring Security's `ExceptionTranslationFilter` does, so a forbidden user gets **500** and `RestAccessDeniedHandler` never runs. Spring's `NoResourceFoundException` (unknown URL), `HttpRequestMethodNotSupportedException`, `HttpMessageNotReadableException` and `MethodArgumentTypeMismatchException` also become 500.
- `MessagingException` handler returns the raw SMTP message.

**Prompt:**
```
Refactor assofin/.../handler/GlobalExceptionHandler.java:

1. Catch-all Exception handler: log the full exception with SLF4J at ERROR (include a generated
   correlation/trace id), and return a generic body "An unexpected error occurred" plus the trace id.
   Never return exp.getMessage() for unexpected exceptions.
2. Add explicit handlers BEFORE the catch-all, or rethrow, so that Spring Security exceptions are not swallowed:
   - org.springframework.security.access.AccessDeniedException / AuthorizationDeniedException → 403
   - org.springframework.security.core.AuthenticationException → 401
3. Add handlers for common framework exceptions with correct statuses:
   NoResourceFoundException → 404, HttpRequestMethodNotSupportedException → 405,
   HttpMessageNotReadableException → 400, MethodArgumentTypeMismatchException → 400,
   MissingServletRequestParameterException → 400, HandlerMethodValidationException /
   ConstraintViolationException → 400 with field errors.
   Simplest route: make the class extend ResponseEntityExceptionHandler and override
   handleExceptionInternal so all of these share the same body format.
4. MessagingException: log it, return 503/500 with a generic message, no SMTP details.
5. Write @WebMvcTest tests: a user without role::read calling GET /role gets 403 (not 500);
   an unknown URL gets 404; a malformed JSON body gets 400; an unexpected RuntimeException returns a
   body that does NOT contain the exception message.
```

---

<a id="sec-04"></a>
### SEC-04 🟠 Cookie flags hard-coded; cookie lifetime not tied to the JWT
**Where:** `AuthenticationController.java:45-51, 115-121`, `RestAuthenticationEntryPoint.java:31-37`.
**Problem:** `secure(false)` and `maxAge(24h)` are copy-pasted three times. Production won't get `Secure` unless someone remembers to edit code. `JWT_EXPIRATION_MS` and the cookie lifetime can diverge. The cookie name `"token"` is a magic string repeated in four places (including `JwtAuthenticationFilter`).

**Prompt:**
```
Centralize auth cookie creation in the backend:
1. Create AuthCookieProperties (@ConfigurationProperties "application.security.cookie") with:
   name (default "access_token"), secure (default true), sameSite (default "Strict"),
   path (default "/"), domain (optional). Set secure=false only in application-dev.yml.
2. Create an AuthCookieService (component) with createAccessTokenCookie(String jwt) and
   createClearingCookie(). maxAge must be derived from JwtProperties.expiration (ms → seconds).
3. Replace the 3 duplicated ResponseCookie builders (AuthenticationController login/logout,
   RestAuthenticationEntryPoint) and the cookie lookup in JwtAuthenticationFilter with this service.
4. Consider the "__Host-" cookie prefix in prod (requires Secure, Path=/, no Domain).
5. Unit-test AuthCookieService for both profiles.
```

---

<a id="sec-05"></a>
### SEC-05 🟠 Swagger and `/ldap/**` public everywhere
**Where:** `SecurityConfig.java:45-57`.
**Problem:** API docs are public in production. `/ldap/**` is permitted although no such endpoint exists, so it would silently expose any future LDAP controller. `/v2/api-docs`, `/swagger-resources` and `/configuration/**` are springfox-era leftovers.

**Prompt:**
```
In SecurityConfig:
1. Move public paths into a constant or properties list. Keep only "/auth/**" public by default.
   List the specific public auth endpoints (login, register, activate-account, logout) instead of a
   wildcard if possible.
2. Remove "/ldap/**", "/v2/api-docs", "/swagger-resources/**", "/configuration/**".
3. Expose "/v3/api-docs/**" and "/swagger-ui/**" only when a property
   application.security.expose-api-docs=true (true in dev, false in prod). Also set
   springdoc.api-docs.enabled / springdoc.swagger-ui.enabled per profile.
4. Add tests: in the prod profile GET /v3/api-docs returns 401/404; in dev it returns 200.
```

---

<a id="sec-06"></a>
### SEC-06 🟠 No brute-force or abuse protection

> ℹ️ **After [Phase 1](#phase-1)** only `/auth/login` needs protection; `/auth/register` and activation are gone.
**Where:** `/auth/login`, `/auth/register`, `/auth/activate-account`.
**Problem:** Unlimited password guessing, mass registration (and mail flooding), token guessing. `accountLocked` exists on `User` but nothing ever sets it.

**Prompt:**
```
Add abuse protection to the auth endpoints using the Redis instance already in the stack:
1. A LoginAttemptService that counts failed logins per email AND per IP in Redis with TTL
   (e.g. 5 failures / 15 min). Hook it via AuthenticationFailureBadCredentialsEvent and
   AuthenticationSuccessEvent listeners. When the threshold is hit, reject with 429 (or lock the
   account temporarily via accountLocked + lockedUntil) and return a generic message.
2. Rate-limit /auth/register and /auth/activate-account per IP (e.g. Bucket4j with a Redis backend,
   or a simple Redis INCR+EXPIRE filter).
3. Make the thresholds configurable via @ConfigurationProperties.
4. Return identical messages and similar timing for "unknown email" vs "wrong password"
   to avoid user enumeration (keep hideUserNotFoundExceptions = true).
5. Add integration tests with Testcontainers Redis.
```

---

<a id="sec-07"></a>
### SEC-07 🟡 CSRF disabled with cookie-based auth
**Where:** `SecurityConfig.java:43`.
**Problem:** With JWT in a cookie, CSRF protection matters. `SameSite=Strict` mitigates most of it, but sub-domain attacks, older browsers, and any future switch to `Lax` (needed for cross-site redirects such as the activation email link landing logged-in) would open the hole.

**Prompt:**
```
Re-enable CSRF protection for the cookie-based JWT setup:
1. Use CookieCsrfTokenRepository.withHttpOnlyFalse() with a CsrfTokenRequestAttributeHandler
   (SPA pattern from the Spring Security reference "Single-Page Applications").
2. Ignore CSRF only for /auth/login and /auth/register (pre-auth endpoints), or accept the token there too.
3. On the Angular side, configure provideHttpClient(withXsrfConfiguration({cookieName:'XSRF-TOKEN',
   headerName:'X-XSRF-TOKEN'})). Note that Angular only attaches the header to relative / same-origin URLs,
   so serve the API via a dev proxy (proxy.conf.json) or through the same origin in production.
4. Add "X-XSRF-TOKEN" to CORS allowed headers.
5. Add tests that a state-changing request without the CSRF header is rejected with 403.
Document the decision in an ADR (docs/adr/0001-csrf-with-cookie-jwt.md).
```

---

<a id="sec-08"></a>
### SEC-08 🟡 Secret-related logging and exposure

> ℹ️ **Redis parts are obsolete after [Phase 1](#phase-1)**, because Redis is removed.
**Where:** `infrastructure/redis/components/RedisDebugConfig.java`, `compose.yaml` Redis healthcheck (`redis-cli -a ${REDIS_PASSWORD}`).
**Problem:** A debug bean logs the password length (it helps an attacker and is noise in prod). The password appears in `docker inspect` and the process list.

**Prompt:**
```
1. Delete RedisDebugConfig entirely (or restrict it to @Profile("dev") and log only host/port,
   never anything derived from the password).
2. In compose.yaml change the Redis healthcheck to use the REDISCLI_AUTH environment variable
   instead of "-a <password>": environment: REDISCLI_AUTH=${REDIS_PASSWORD}; test: ["CMD","redis-cli","ping"].
3. Ensure Postgres/Redis ports are bound to 127.0.0.1 in dev ("127.0.0.1:${POSTGRES_PORT}:5432").
```

---

<a id="sec-09"></a>
### SEC-09 🟡 JWT hardening
**Where:** `security/jwt/JwtService.java`, `JwtProperties.java`.
**Problem:** No `iss`/`aud` claims, so any token signed with the key is accepted. There's no `jti`, revocation, or refresh token, so logout only deletes the browser cookie and a stolen token stays valid until it expires (24h in `.env.example`). The key is Base64-decoded on every call, and a missing or short secret is only discovered at the first login. The `authorities` claim is written but never read, because authorities are reloaded from the DB anyway. That's inconsistent.

**Prompt:**
```
Harden JwtService/JwtProperties:
1. Annotate JwtProperties with @Validated; require secretKey @NotBlank and expiration @Positive.
   Build the SecretKey once (constructor / @PostConstruct) and fail fast at startup if it decodes to
   fewer than 256 bits.
2. Add issuer and audience (configurable) on generation and require them on parsing
   (Jwts.parser().requireIssuer(..).requireAudience(..)). Add a jti (UUID) claim. Allow a small
   clock skew (e.g. 30s).
3. Decide on one authority source: either keep reloading the user from the DB (then drop the
   "authorities" claim) or trust the claims and skip the DB hit. Document the choice.
4. Implement short-lived access tokens (e.g. 15 min) plus a refresh token (opaque, stored hashed in
   Redis like the activation token, rotated on use, in a separate HttpOnly cookie scoped to
   /auth/refresh). Add POST /auth/refresh. On logout, delete the refresh token from Redis and
   optionally denylist the access token jti until its expiry.
5. Replace java.util.Date with Instant / Clock injection so expiry logic is testable; write unit
   tests for valid, expired, wrong-signature, wrong-issuer tokens.
```

---

<a id="bug-01"></a>
### BUG-01 🔴 JPA auditing is not enabled

> ⚠️ **Prerequisite of [Phase 1](#phase-1), step [1.5](#phase-1-5).** Seeding the `ADMIN` role fails until auditing is enabled.
**Where:** `AssofinApplication.java` (no `@EnableJpaAuditing`), `Role.java:37-39`, `RolePermission.java:36-38`, `User.java:49-54`.
**Problem:** `@CreatedDate` and `@LastModifiedDate` are never populated. `createdDate` is `nullable = false`, so `POST /role` (and every `RolePermission` insert) fails with a constraint violation, which then shows up as a misleading 409 "resource already exists". `UserService` works around it by setting `createdDate` manually.

**Prompt:**
```
Fix auditing in the backend:
1. Create a JpaAuditingConfig @Configuration with @EnableJpaAuditing(dateTimeProviderRef="auditingDateTimeProvider")
   and a DateTimeProvider bean backed by an injectable java.time.Clock (UTC).
2. Create an abstract @MappedSuperclass "AuditableEntity" with @EntityListeners(AuditingEntityListener.class)
   and fields createdAt (Instant, @CreatedDate, nullable=false, updatable=false), updatedAt (Instant,
   @LastModifiedDate), and optionally createdBy/updatedBy with an AuditorAware<String> that reads the
   SecurityContext.
3. Make User, Role, RolePermission (and Permission if needed) extend it. Remove their duplicated
   audit fields and the manual .createdDate(LocalDate.now()) in UserService.
4. Add a @DataJpaTest (Testcontainers Postgres) proving createdAt/updatedAt are set on insert/update.
```

---

<a id="bug-02"></a>
### BUG-02 🔴 Roles are never seeded; no permissions are granted to any role

> ✅ **Resolved by [Phase 1](#phase-1), step [1.5](#phase-1-5)** (`RoleInitializer` + `AdminUserInitializer`). The prompt below is kept for reference; its "POST /auth/register succeeds" test no longer applies.
**Where:** `UserService.java:30` → `RoleService.findByName` throws `EntityNotFoundException`; `PermissionInitializer` seeds only permissions.
**Problem:** On a fresh database, registration fails with 404 "Role … not found". Even after a role row is inserted by hand, no role has any permission, so `/role` endpoints are unreachable. That's a bootstrap deadlock.

**Prompt:**
```
Implement deterministic seeding of roles and their permissions:
1. Prefer Flyway (see PER-01): add migrations that insert base roles (ADMIN, USER) and the
   role↔permission grants for ADMIN. If permissions stay code-driven, keep PermissionInitializer
   but add a RoleInitializer (ApplicationRunner, @Order after permissions) that:
   - ensures roles defined in a "SystemRole" enum exist (ADMIN, USER),
   - grants ADMIN every PermissionDefinition from the registry (idempotent, using Role.replacePermissions
     or add-missing-only to avoid overwriting admin customizations, document which),
   - grants USER a configurable minimal set (possibly empty).
2. Make the initializers idempotent and safe to run concurrently on multiple instances
   (unique constraints + catch DataIntegrityViolationException, or Flyway only).
3. Integration test: on an empty DB the app starts, POST /auth/register succeeds, and the bootstrap
   admin can call GET /role.
```

---

<a id="bug-03"></a>
### BUG-03 🔴 Authority double prefix `ROLE_ROLE_ADMIN`

> ⚠️ **Handled in [Phase 1](#phase-1), step [1.5](#phase-1-5)** (`SystemRole` names stored without the `ROLE_` prefix).
**Where:** `RoleEnum.java` (`ROLE_ADMIN`), `User.java:61` (`"ROLE_" + role.getName()`).
**Problem:** The DB role name looks up `RoleEnum.ROLE_ADMIN.name()` = `"ROLE_ADMIN"`, and `getAuthorities()` prepends `ROLE_` again. `hasRole('ADMIN')` will never match. `RoleEnum` implementing `GrantedAuthority` with an unused `displayName` adds to the confusion.

**Prompt:**
```
Normalize role naming:
1. Store role names WITHOUT the "ROLE_" prefix in the DB (ADMIN, USER). Rename RoleEnum to
   SystemRole { ADMIN, USER } and remove its GrantedAuthority implementation and unused displayName.
2. In the authority mapping, add exactly one "ROLE_" prefix (or use a GrantedAuthorityDefaults bean
   and keep it consistent everywhere).
3. Validate role names in CreateRoleRequestDTO: @NotBlank, @Size(max=50), @Pattern("^[A-Z][A-Z0-9_]*$"),
   and reject names starting with "ROLE_".
4. Unit test: a user with role ADMIN gets authority "ROLE_ADMIN" (exactly once) plus its permissions.
```

---

<a id="bug-04"></a>
### BUG-04 🔴 Lazy `role` accessed outside a transaction
**Where:** `User.java:45-46` (`@ManyToOne(fetch = LAZY) Role role`), `User.getAuthorities()`, `UserDetailsServiceImpl`, `JwtAuthenticationFilter:53-59`.
**Problem:** `loadUserByUsername` returns a detached `User` whose `role` (and `role.rolePermissions → permission`) are lazy proxies. `DaoAuthenticationProvider` and the JWT filter then call `getAuthorities()` outside any session, which throws a `LazyInitializationException`. Open-session-in-view doesn't cover servlet filters. In the filter, the exception is swallowed by `catch (Exception)`, so every authenticated request silently becomes anonymous → 401. *(Verify with a test; this is the likely root cause of "authorities" problems.)* Also, the JPA entity is used as the security principal and stored in the `SecurityContext`.

**Prompt:**
```
Decouple the security principal from the JPA entity and load authorities eagerly:
1. Add a repository method that fetches the user with role and permissions in one query:
   @EntityGraph(attributePaths = {"role", "role.rolePermissions", "role.rolePermissions.permission"})
   Optional<User> findWithAuthoritiesByEmailIgnoreCase(String email);
2. Create an immutable "AuthenticatedUser" record implementing UserDetails (id, email, passwordHash,
   enabled, locked, Set<GrantedAuthority>). UserDetailsServiceImpl (@Transactional(readOnly=true))
   maps the entity to it. Remove UserDetails from the User entity.
3. In JwtAuthenticationFilter, catch only io.jsonwebtoken.JwtException / IllegalArgumentException and
   log them at DEBUG. Do not catch every Exception (DB outages must surface as 5xx, not 401).
   Also skip the filter for public paths via shouldNotFilter().
4. Set spring.jpa.open-in-view=false explicitly to make lazy-loading bugs visible.
5. Integration test: register → activate → login → GET /users/me returns 200 with permissions.
```

---

<a id="bug-05"></a>
### BUG-05 🔴 `/users/me` returns a JPA entity
**Where:** `user/dto/response/UserProfileResponseDTO.java` (`Role role`), `UserService.getProfileByUsername`.
**Problem:** Serializing a Hibernate proxy causes `ByteBuddyInterceptor` / lazy serialization errors or infinite recursion. It exposes internal structure (`rolePermissions`, audit fields), and the frontend expects `role.permissions: string[]`, which never exists. Mapping is duplicated in two service methods.

**Prompt:**
```
Fix the user profile API contract:
1. Change UserProfileResponseDTO to: Long id, String email, String firstname, String lastname,
   RoleSummaryDTO role (String name, Set<String> permissions). No entity types in any DTO.
   Add an ArchUnit test: classes in "..dto.." must not depend on classes in "..entity..".
2. Create UserMapper (or use MapStruct across the project, including RoleMapper) and use it in both
   getProfileByUsername and getUserProfileById to remove the duplication.
3. Run the mapping inside a read-only transaction, with the entity-graph query from BUG-04.
4. Update the Angular AuthUserProfileDto to match exactly (id: number). Consider generating TS types
   from the OpenAPI spec (openapi-typescript or ng-openapi-gen) so contracts can't drift again.
5. Add a @WebMvcTest/JSON test asserting the exact JSON shape of GET /users/me.
```

---

<a id="bug-06"></a>
### BUG-06 🟠 `@Async` is a no-op; registration and email are not consistent

> ℹ️ **Reduced by [Phase 1](#phase-1).** Registration no longer sends mail. Apply this prompt only when an email feature (invitation, password reset) is added.
**Where:** `mail/MailService.java:29`, `AuthenticationService.register`.
**Problem:** Without `@EnableAsync`, mail is sent synchronously inside the HTTP request. If SMTP fails, the user is already saved, so they can't re-register ("Email already exists") and can't get a new link. The `RuntimeException(e)` wrap loses meaning, and the `throws MessagingException` on the controller is dead.

**Prompt:**
```
Make email sending reliable and asynchronous:
1. Add @EnableAsync with a dedicated ThreadPoolTaskExecutor bean ("mailExecutor": bounded queue,
   named threads, an AsyncUncaughtExceptionHandler that logs). Use @Async("mailExecutor").
2. Decouple registration from SMTP: in AuthenticationService.register (@Transactional) save the user,
   create the token, and publish a UserRegisteredEvent. Handle it with
   @TransactionalEventListener(phase = AFTER_COMMIT) + @Async to send the email, so mail is only
   sent if the user row committed and SMTP failures don't roll back or block the request.
   (Optional: transactional outbox table for guaranteed delivery with retries.)
3. Add spring-retry (or Resilience4j) retry with backoff around the actual send.
4. Add POST /auth/resend-activation {email} (rate-limited, always returns 202 to avoid enumeration),
   which invalidates the previous token and issues a new one.
5. Make sender address, subject and app name configurable (application.mail.from, application.name),
   remove the hard-coded "contacts@avaodessey.com" and "Avalon Odyssey" strings, and externalize
   subjects to messages.properties for i18n.
6. Rename sendAccountActivationEmail params to match their use, remove the unused CONFIRM_EMAIL
   template reference (that template doesn't exist), and remove unused imports.
```

---

<a id="bug-07"></a>
### BUG-07 🟠 Activation flow: non-atomic, side effects on GET

> 🗑️ **Obsolete after [Phase 1](#phase-1), step [1.4](#phase-1-4).** The activation flow is removed. Re-read this only if email activation comes back.
**Where:** `AuthenticationService.activateAccount`, `ActivationTokenService.validateToken/consumeToken`, `AuthenticationController:105`.
**Problem:** Validate → enable → consume is three Redis/DB steps, so two concurrent clicks can both succeed. `@Transactional` here is `jakarta.transaction.Transactional` and doesn't cover Redis. A GET that mutates state gets triggered by email link scanners and prefetchers, which consume the token before the user clicks. The return type is `AuthenticationResponseDTO` but the body is empty. An already-active user isn't handled. Invalid tokens throw `IllegalArgumentException`, which becomes 500.

**Prompt:**
```
Rework account activation:
1. Make token consumption atomic: in RedisActivationTokenStore add consume(tokenHash) using
   redisTemplate.opsForValue().getAndDelete(key) (Redis GETDEL). Call it first, then enable the user.
   Remove the validate-then-consume sequence and the unused invalidateToken if not needed.
2. Change the endpoint to POST /auth/activate with body {token}. The email link points to the Angular
   page /auth/activate?token=..., which calls the POST, so link scanners can't burn tokens.
3. Throw a dedicated InvalidActivationTokenException → 400 with business code, and handle
   "already enabled" idempotently (return 200/204).
4. Use org.springframework.transaction.annotation.Transactional.
5. Make the TTL configurable (application.security.activation.ttl=15m) instead of a constant.
6. Tests: valid token activates once; second use → 400; expired token → 400; concurrent activation
   (two threads) → exactly one success.
```

---

<a id="bug-08"></a>
### BUG-08 🟠 Wrong `@Param` import
**Where:** `role/repository/RoleRepository.java:4` (`io.lettuce.core.dynamic.annotation.Param`).
**Problem:** It only works by accident, because Boot compiles with `-parameters`. It couples the JPA repository to the Redis client library.

**Prompt:**
```
In RoleRepository replace `import io.lettuce.core.dynamic.annotation.Param;` with
`import org.springframework.data.repository.query.Param;`. Remove unused imports (Page, Pageable).
Search the whole backend for any other io.lettuce imports outside infrastructure/redis and fix them.
Add an ArchUnit rule: only ..infrastructure.redis.. may depend on io.lettuce.. and org.springframework.data.redis..
```

---

<a id="bug-09"></a>
### BUG-09 🟠 Role pagination validation is wrong
**Where:** `role/dto/request/RolePageRequestDTO.java:10-12`, `RoleController.getRoles`.
**Problem:** `@Min(20)` on `size` (meant to be a default, or `@Min(1)`). Primitive `int` defaults to 0, so `GET /role` without params → 400. `@Valid @NotNull` on a query-bound record is unusual. Sorting is fixed.

**Prompt:**
```
Fix role listing pagination:
1. Use Spring Data's Pageable directly with @PageableDefault(size = 20, sort = "id") and set
   spring.data.web.pageable.max-page-size=100. Or keep the record with Integer fields, defaults
   (page=0, size=20) applied in a compact constructor, and constraints @Min(0) page, @Min(1) @Max(100) size.
2. Allow whitelisted sort fields only (id, name, createdAt) to prevent sorting on arbitrary properties.
3. Create a reusable generic PageResponse<T>(List<T> content, int page, int size, long totalElements,
   int totalPages) in a shared package and use it instead of RolePageResponseDTO.
4. Fix the N+1 (see PER-03).
5. MockMvc tests: no params → 200 with size 20; size=500 → capped/400; page=-1 → 400.
```

---

<a id="bug-10"></a>
### BUG-10 🟠 PATCH requires all fields
**Where:** `role/dto/request/UpdateRoleRequestDTO.java`, `RoleService.updateRole`.
**Problem:** `@NotNull` on `name` and `permissions` contradicts the null checks in the service and PATCH semantics. `CreateRoleRequestDTO` allows a blank name, and `name().trim()` can NPE before validation in other callers. There's no DELETE endpoint although `role::delete` exists. System roles (ADMIN) can be renamed or stripped of permissions, which locks everyone out.

**Prompt:**
```
Clean up the Role API:
1. UpdateRoleRequestDTO: remove @NotNull; validate when present (@Size(min=1,max=50) + pattern on
   name). Keep PATCH semantics (null = unchanged). Or switch to PUT with full replacement, but be consistent.
2. CreateRoleRequestDTO: name @NotBlank @Size @Pattern, permissions @NotNull (may be empty), element
   constraints Set<@NotBlank String>.
3. Add DELETE /role/{id} guarded by role::delete. Refuse (409) when users are still assigned to it.
4. Protect system roles: add a `system` boolean column; system roles can't be deleted or renamed, and
   ADMIN can't lose role::* permissions.
5. Use plural, consistent resource paths: /roles, /users (update the frontend accordingly).
6. Return 404 via a ResourceNotFoundException (see ERR-01) and test each branch.
```

---

<a id="bug-11"></a>
### BUG-11 🟡 `IllegalArgumentException` for business rules
**Where:** `UserService.java:27,51`, `ActivationTokenService.java:42,53`.
**Problem:** These fall into the catch-all handler and become 500 with the raw message ("Email already exists"). They should be 409/400/404.

**Prompt:**
```
Replace IllegalArgumentException used for business conditions with the domain exception hierarchy
from ERR-01: EmailAlreadyUsedException (409, or return 202 silently on register to avoid enumeration,
document the choice), ResourceNotFoundException (404), InvalidActivationTokenException (400).
Grep the backend for "new IllegalArgumentException" and "new RuntimeException" and convert each one.
Add tests for each mapped status.
```

---

<a id="err-01"></a>
### ERR-01 🟠 Error contract and exception design
**Where:** `handler/*`.
**Problem:**
- `BusinessErrorCodes.httpStatus` is declared but never used. Handlers hard-code statuses that contradict it (ACCOUNT_LOCKED is FORBIDDEN in the enum but UNAUTHORIZED in the handler).
- Business codes are arbitrary (304/404/409/410/500 look like HTTP codes but aren't).
- The field is named `exceptionDec` (a typo). Validation errors are a `Set<String>` with no field names, while the `errors` map is never filled.
- The security handlers build their own format with `businessCode = 401/403`.
- No timestamp, path, or trace id. No standard (RFC 9457).

**Prompt:**
```
Design a single, standard error contract for the API:
1. Adopt RFC 9457 Problem Details (Spring's ProblemDetail, spring.mvc.problemdetails.enabled=true)
   with extension properties: code (stable string such as "AUTH_BAD_CREDENTIALS", "ROLE_NOT_FOUND"),
   traceId, timestamp, and errors (list of {field, message} for validation).
2. Create an abstract BusinessException(ErrorCode code, Object... args) extends RuntimeException, plus
   subclasses (ResourceNotFoundException, ConflictException, ...). ErrorCode is an enum carrying
   HttpStatus + a message key. A single @ExceptionHandler(BusinessException.class) uses
   code.getHttpStatus(), so statuses can't diverge again.
3. Resolve messages through MessageSource (messages_en/fr/ar.properties) using Accept-Language,
   matching the frontend's i18n languages.
4. Make RestAuthenticationEntryPoint and RestAccessDeniedHandler produce the SAME ProblemDetail body
   (share a ProblemDetailWriter component).
5. Rename package "handler" to "common.error" (or "shared.web.error"). Delete ExceptionResponse.
6. Document the error format in the OpenAPI spec (@ApiResponse with a ProblemDetail schema) and add
   tests asserting the JSON for 400/401/403/404/409/500.
7. Update the Angular error interceptor to read `detail`/`code`/`errors` from this format.
```

---

<a id="per-01"></a>
### PER-01 🟠 Schema managed by Hibernate in production
**Where:** `application-prod.yml` (`ddl-auto: update`, `show-sql: true`, `format_sql`).
**Problem:** `ddl-auto: update` never drops or renames columns, can't do data migrations, can't be reviewed, and can corrupt prod. `show-sql` in prod floods logs and may leak data.

**Prompt:**
```
Introduce Flyway for schema management:
1. Add org.flywaydb:flyway-core and flyway-database-postgresql. Create
   src/main/resources/db/migration/V1__init_schema.sql generated from the current entities
   (users, role, permission, role_permission) with proper PKs, FKs (ON DELETE rules),
   UNIQUE constraints (users.email lower-case unique index, role.name, permission.name,
   (role_id, permission_id) unique), NOT NULL and indexes on FK columns.
2. V2__seed_roles_permissions.sql for base roles/permissions (see BUG-02).
3. Set spring.jpa.hibernate.ddl-auto=validate in all profiles (dev included, to catch drift early)
   and spring.jpa.show-sql=false in prod. Use logging.level.org.hibernate.SQL=DEBUG in dev only.
4. Add a Testcontainers-based test that boots the app against a fresh Postgres and runs migrations.
```

---

<a id="per-02"></a>
### PER-02 🟡 Entity modelling issues
**Where:** `User.java:30` (`@GeneratedValue` AUTO → SEQUENCE), `Role.java:26` (IDENTITY), `Permission.java:20,31-40`, `RolePermission`.
**Problem:**
- Inconsistent ID strategies (one shared `*_seq`, the other IDENTITY, which disables JDBC batching).
- `Permission.equals/hashCode` uses a mutable `id` plus `name`, so the hash changes after persist and breaks `HashSet` membership (it's used in `replacePermissions`). `Role` and `RolePermission` have no equals/hashCode.
- `Permission.rolePermissions` has `cascade = ALL, orphanRemoval = true` from the permission side, so deleting a permission silently deletes grants. The bidirectional collection is unnecessary.
- `LocalDate` for audit fields loses time and timezone.
- There's no unique constraint on `(role_id, permission_id)`, and no `@Version` for optimistic locking.
- `@Builder` + `@AllArgsConstructor` + `@Setter` on entities allows building invalid state. Lombok `@Data` and `@ToString` risks on lazy relations.

**Prompt:**
```
Harden the JPA entity model:
1. Use one ID strategy everywhere: GenerationType.SEQUENCE with a per-table sequence and
   allocationSize=50 (supports batching), or UUIDv7 if IDs are exposed publicly. Reflect this in Flyway.
2. Implement equals/hashCode following the Hibernate-recommended pattern: based on a natural/business
   key that never changes (Permission.name, Role.name), or id-based with getClass/proxy-safe checks and
   a constant hashCode. Apply to Permission, Role, RolePermission, User.
3. Remove Permission.rolePermissions (unidirectional from RolePermission is enough), or at least
   remove cascade/orphanRemoval there.
4. Add @Table(uniqueConstraints = @UniqueConstraint(columnNames={"role_id","permission_id"})) and
   @Version Long version on aggregate roots (Role, User).
5. Replace LocalDate audit fields with Instant (done via AuditableEntity in BUG-01).
6. Restrict entity construction: protected no-args constructor for JPA, static factory methods or
   builders that enforce invariants, no public setters for ids/collections.
7. Unit test Role.replacePermissions with transient and persisted Permission instances.
```

---

<a id="per-03"></a>
### PER-03 🟡 N+1 queries and a per-request DB hit
**Where:** `RoleService.findRolesPage` → `RoleMapper.toDto` (lazy `rolePermissions`, then lazy `permission`), `JwtAuthenticationFilter` loading the user on every request.

**Prompt:**
```
Remove N+1 queries in role listing and auth:
1. For paging roles, do a two-step fetch: page the ids (SELECT r.id FROM Role r ...) and then load
   them with @EntityGraph(attributePaths={"rolePermissions","rolePermissions.permission"}) using
   WHERE r.id IN :ids. Or set hibernate.default_batch_fetch_size=50 as a global safety net.
2. Enable Hibernate statistics in tests and assert the query count for GET /roles stays constant
   (e.g. with datasource-proxy or io.hypersistence QueryCountHolder).
3. For the JWT filter, either trust the token's authorities (short-lived tokens, see SEC-09) or cache
   the AuthenticatedUser per email in Redis/Caffeine with a short TTL, evicted when a user's role or
   that role's permissions change.
```

---

<a id="per-04"></a>
### PER-04 🟡 Transaction boundaries
**Where:** `AuthenticationService` uses `jakarta.transaction.Transactional`; others use Spring's. `UserService.saveUser`, `findById` and `register` have no transaction. `PermissionInitializer` is `@Transactional` on `ApplicationRunner.run`, which works but is mixed with per-row `existsByName` checks (N queries).

**Prompt:**
```
Standardize transactions:
1. Use only org.springframework.transaction.annotation.Transactional across the backend
   (add an ArchUnit rule forbidding jakarta.transaction.Transactional).
2. Put @Transactional on application-service methods that write (register, activateAccount, role
   save/update) and @Transactional(readOnly = true) on read methods. Keep controllers transaction-free.
3. PermissionInitializer: load all existing names in one query (findAllNames()), compute the missing
   set and saveAll in a single batch.
4. Remove AuthenticationService's direct UserRepository dependency: go through UserService so each
   aggregate has a single owner.
```

---

<a id="arc-01"></a>
### ARC-01 🟠 Permission registry violates Open/Closed; test code in `main`
**Where:** `infrastructure/permission/PermissionRegistry.java`, `products/ProductPermissions.java` ("only for test"), `role/permissions/PermissionExpressions.java`.
**Problem:** Every new domain requires editing infrastructure code. A fake `product` domain ships to production and seeds DB permissions. `PermissionExpressions` needs one method per permission (it won't scale). There are three naming formats (`role::read` in the backend, `user:read` in the frontend, `PermissionDefinition` in the `entity` package although it isn't an entity).

**Prompt:**
```
Make permissions self-registering and consistent:
1. Move PermissionDefinition to a shared package (e.g. com.association.assofin.shared.security).
2. Let each domain contribute its permissions as a Spring bean:
   @Bean PermissionProvider rolePermissions() { return () -> List.of(RolePermissions.values()); }
   PermissionRegistry injects List<PermissionProvider> and flattens them. Infrastructure no longer
   imports domain enums.
3. Delete products/ProductPermissions from src/main (move an example to src/test or to docs).
4. Choose ONE permission string format, e.g. "<domain>:<action>" ("role:read"), and apply it in the
   backend enums, the Flyway seed, and the Angular AppPermission type (ideally generate the TS union
   from an endpoint such as GET /permissions or from the OpenAPI spec).
5. Replace the per-permission PermissionExpressions methods with direct constants in @PreAuthorize:
   @PreAuthorize("hasAuthority('" + RolePermissions.Names.READ + "')") using compile-time String
   constants nested in the enum, or a custom meta-annotation @RequiresPermission(RolePermissions.READ)
   backed by an AuthorizationManager.
6. Optionally prune permissions in the DB that no longer exist in code (log them, don't auto-delete grants).
```

---

<a id="arc-02"></a>
### ARC-02 🟡 Package and naming inconsistencies
**Where:** backend root packages.
**Problem:** `handler` (actually error handling), `infrastructure` (mixes Redis, permission bootstrap, app properties), `security` (top-level, but `auth` is a feature). DTO mappers live under `dto/mapper`. Routes are `/role` (singular) vs `/users` (plural). DTO suffix conventions vary (`RegistrationResponseDTO` is unused).

**Prompt:**
```
Propose and apply a consistent package convention (as a refactor-only change, no behavior change):

com.association.assofin
 ├─ shared/            (cross-cutting: error/, config/, security/ (JWT, filters, handlers), persistence/ (AuditableEntity), web/ (PageResponse))
 ├─ auth/              (api/ controllers + DTOs, application/ services, domain/ ports like ActivationTokenStore, infrastructure/ RedisActivationTokenStore)
 ├─ user/              (api/, application/, domain/ entity+repository, mapper/)
 ├─ role/              (same layout)
 └─ permission/        (same layout)

- Rename "handler" → shared/error, move app properties to shared/config, move Redis adapters into the
  feature that owns the port (auth/infrastructure).
- Pluralize REST resources (/roles, /users), keep /auth for actions.
- Delete unused classes (RegistrationResponseDTO, the ldap leftovers).
- Add ArchUnit tests enforcing: controllers → services only; domain doesn't depend on api or
  infrastructure; no cycles between feature packages (slices().matching("..assofin.(*)..").should().beFreeOfCycles()).
Use IDE-safe moves; run the full build and tests after.
```

---

<a id="arc-03"></a>
### ARC-03 🟡 Custom Redis config shadows Boot auto-config

> 🗑️ **Obsolete after [Phase 1](#phase-1), step [1.4](#phase-1-4).** Redis is removed.
**Where:** `infrastructure/redis/RedisConfig.java`, `RedisProperties.java` (a record bound to `spring.data.redis`, which has the same simple name as Boot's own `RedisProperties`).
**Problem:** The manual `LettuceConnectionFactory` ignores `timeout`, `username`, SSL, pool and sentinel/cluster settings that are configured in YAML. The `username: default` and `client-type` lines are dead config. Name clashes are confusing.

**Prompt:**
```
Remove infrastructure/redis/RedisConfig.java and the custom RedisProperties record. Rely on Spring
Boot's Redis auto-configuration (spring.data.redis.* incl. host, port, username, password, timeout,
ssl.enabled, lettuce.pool.*). Use the auto-configured StringRedisTemplate in RedisActivationTokenStore.
Verify with a Testcontainers Redis test that timeout and password settings are honored.
```

---

<a id="arc-04"></a>
### ARC-04 🟡 `User` entity design
**Where:** `user/entity/User.java`.
**Problem:** `getUsername()` returns the email, while a separate `username` field holds `"first last"`, which isn't unique and isn't used for login. `domainName`, `birthDate` and the LDAP comments are unused. `findByUsernameIgnoreCase` is unused. Email isn't normalized (case and whitespace), so `John@x.com` and `john@x.com` can both register. There's no length limit on password (BCrypt truncates at 72 bytes).

**Prompt:**
```
Clean up the User aggregate:
1. Email is the login identifier: normalize it (trim + lower-case) in one place (a value object Email
   or a @PrePersist/@PreUpdate hook plus normalization before lookup), with a unique index on
   lower(email).
2. Rename/clarify fields: displayName (optional) instead of "username"; remove domainName, birthDate
   and LDAP leftovers unless a concrete feature needs them (document the LDAP plan in an ADR instead
   of commented code).
3. Password policy: @Size(min=12, max=72) (or check UTF-8 byte length ≤ 72), optionally a
   strength check (zxcvbn / Passay). Don't validate @Size on the LOGIN DTO, only @NotBlank.
4. Use DelegatingPasswordEncoder (PasswordEncoderFactories.createDelegatingPasswordEncoder()) so the
   hashing algorithm can evolve (e.g. to Argon2) without breaking existing hashes.
5. Remove @NotNull where @NotEmpty/@NotBlank already implies it (DTOs).
```

---

<a id="cfg-01"></a>
### CFG-01 🟠 Profile configurations drift
**Where:** `application-dev.yml`, `application-prod.yml`, `application.yml`.
**Problem:**
- The prod file's header says "DEVELOPMENT".
- Prod datasource host is hard-coded `postgres-db`, which matches no compose service (it's `postgres`).
- Dev hard-codes port `5432`, ignoring `POSTGRES_PORT`.
- Env names differ per profile (`MAIL_HOST`/`MAIL_PORT` vs `MAIL_PROVIDER`/`SMTP_PORT`, `REDIS_TIMEOUT_MS` vs `REDIS_TIMEOUT`).
- SMTP `auth: true` and `starttls: true` are forced in dev although MailDev needs neither (`MAIL_SMTP_AUTH`/`MAIL_SMTP_STARTTLS` exist but are unused).
- `REDIS_TIMEOUT_MS=2000` is bound to a Duration, so it parses as 2000 **ms** only by luck of the default unit.
- Shared settings are duplicated instead of living in `application.yml`.

**Prompt:**
```
Restructure Spring configuration:
1. application.yml holds ALL shared settings with env placeholders and safe defaults, using one
   consistent env var name per setting:
   DB_HOST, DB_PORT, DB_NAME, DB_USER, DB_PASSWORD, REDIS_HOST, REDIS_PORT, REDIS_PASSWORD,
   REDIS_TIMEOUT (Duration, e.g. 2s), MAIL_HOST, MAIL_PORT, MAIL_USERNAME, MAIL_PASSWORD,
   MAIL_SMTP_AUTH, MAIL_SMTP_STARTTLS, MAIL_FROM, JWT_SECRET_KEY, JWT_EXPIRATION (Duration), FRONTEND_BASE_URL.
2. application-dev.yml only overrides dev specifics (show SQL logging, cookie secure=false, api docs
   on, mail auth false). application-prod.yml only overrides prod specifics (no defaults for secrets,
   so startup fails fast if they're missing; cookie secure=true; api docs off).
3. Fix the prod datasource host (use ${DB_HOST}) and correct the file headers.
4. Validate all custom @ConfigurationProperties with @Validated + constraints.
5. Use Duration types for timeouts/expirations (JwtProperties.expiration → Duration).
6. Update .env.example to the new names and remove unused ones.
7. Add a test per profile that the context starts with only the documented env vars set.
```

---

<a id="cfg-02"></a>
### CFG-02 🟠 `.env` handling problems
**Where:** root `.env.example`, `.gitignore`.
**Problem:**
- Spring Boot doesn't read `.env`. It currently depends on the IDE injecting env vars, which isn't reproducible.
- Inline comments (`SPRING_PROFILES_ACTIVE=dev #changed…`, `POSTGRES_PASSWORD=change_me #forget…`) and spaces (`REDIS_CONTAINER_NAME = redis.assofin`) get parsed into values by many loaders.
- The comment "15 minutes = 86400000 ms" is wrong (that's 24 h).
- The header says the root `.env` must never contain app secrets, yet it holds the JWT secret.
- `.gitignore` lists `backend/.env` and `frontend/.env`, but the folders are `assofin/` and `assofin-frontend/` (still covered by the bare `.env` pattern, but misleading). The frontend `.gitignore` doesn't ignore `.env`.

**Prompt:**
```
Make environment configuration reproducible and safe:
1. Load env for local runs explicitly: either `spring.config.import: optional:file:../.env[.properties]`
   in application-dev.yml, or the spring-dotenv library, or a Makefile/`./run-dev.sh` that does
   `set -a; source .env; set +a; ./mvnw spring-boot:run`. Document it in the README.
2. Rewrite .env.example: one KEY=value per line, no inline comments, no spaces around "=", comments
   on their own lines, correct units (JWT_EXPIRATION=15m), placeholders like
   JWT_SECRET_KEY=<generate with: openssl rand -base64 64>.
3. Split concerns, or fix the header to match reality: infra/compose vars vs application secrets.
4. Fix .gitignore paths (assofin/.env, assofin-frontend/.env), add .env to assofin-frontend/.gitignore,
   keep `!.env.example`.
5. Add a startup check (or Maven enforcer / pre-commit hook such as gitleaks) that prevents committing secrets.
```

---

<a id="cfg-03"></a>
### CFG-03 🟡 Compose duplication and overlap
**Where:** `compose.yaml`, `compose.example.yaml`, `pom.xml` (`spring-boot-docker-compose`).
**Problem:** `compose.yaml` has no secrets (it's all `${VAR}`), so the "example" copy isn't needed, and it has already drifted (Redis without ports or healthcheck, maildev commented out, frontend port 4200 vs 80). `spring-boot-docker-compose` auto-starts `compose.yaml` and creates connection details that override the manual YAML, so two sources of truth. `maildev/maildev` has no version tag. Commented-out backend/frontend services reference non-existent `./backend`, `./frontend`.

**Prompt:**
```
Consolidate Docker Compose:
1. Delete compose.example.yaml; keep a single compose.yaml for infra (postgres, redis, maildev) and a
   compose.prod.yaml (or profiles: ["app"]) that adds backend + frontend services built from
   ./assofin and ./assofin-frontend Dockerfiles (see OPS-01).
2. Pin image versions (postgres:17.x-alpine, redis:8.x-alpine, maildev/maildev:2.x).
3. Add healthchecks to all services and depends_on: condition: service_healthy for the backend.
4. Decide on spring-boot-docker-compose: either use it (then remove manual datasource/redis URLs from
   application-dev.yml and set spring.docker.compose.file=../compose.yaml) or remove the dependency.
   Document the choice.
5. Bind dev ports to 127.0.0.1 only.
```

---

<a id="cfg-04"></a>
### CFG-04 🟡 Hard-coded values from another project and placeholders
**Where:** `MailService.java:26` (`contacts@avaodessey.com`), `AuthenticationService.java:86` ("Avalon Odyssey account activation"), `application.yml` CORS `https://your-production-domain.com`, `server.servlet.context-path: /api/v1/` (trailing slash), the email template text "use the following activation code" while it sends a link.

**Prompt:**
```
Remove project-specific and placeholder values:
1. Introduce application.name / application.mail.from / application.mail.subjects.* properties and use
   them in MailService/AuthenticationService and in the Thymeleaf template (th:text="${appName}").
2. CORS allowed-origins must come from env (CORS_ALLOWED_ORIGINS, comma-separated); no placeholder
   domain in the repo; prod has no default.
3. Change context-path to "/api" and version via @RequestMapping("/v1/...") on controllers, or keep
   "/api/v1" without the trailing slash. Make the frontend ApiService match (see FE-06).
4. Fix the activation email copy (it sends a link, not a code), add a plain-text alternative part,
   escape user-controlled values (firstname) and use MULTIPART_MODE_MIXED_RELATED only if inline images exist.
```

---

<a id="cfg-05"></a>
### CFG-05 🟡 `pom.xml` issues
**Where:** `assofin/pom.xml`.
**Problem:** Both `springdoc-openapi-starter-webmvc-ui` **and** `-webflux-ui`, which pulls WebFlux into a servlet app (classpath conflicts, bigger image). The `jjwt` aggregate artifact is used instead of `jjwt-api` + runtime `jjwt-impl`/`jjwt-jackson`. There are empty `<name/>`, `<licenses>`, `<developers>`, `<scm>` placeholders, and no quality or security plugins.

**Prompt:**
```
Clean up assofin/pom.xml:
1. Remove springdoc-openapi-starter-webflux-ui. Keep only the webmvc-ui starter, with its version in a
   <properties> entry.
2. Replace io.jsonwebtoken:jjwt with jjwt-api (compile) + jjwt-impl and jjwt-jackson (runtime), version
   via property.
3. Fill or delete the empty metadata elements.
4. Add build quality gates: spotless-maven-plugin (palantir/google-java-format), maven-enforcer-plugin
   (Java 21, dependency convergence, ban duplicate classes), jacoco with a minimum coverage rule,
   OWASP dependency-check or the CycloneDX SBOM plugin, and optionally error-prone / SpotBugs.
5. Add test deps: testcontainers (postgresql, redis), archunit-junit5, spring-boot-testcontainers.
6. Run ./mvnw verify and make sure it passes.
```

---

<a id="ops-01"></a>
### OPS-01 🟠 No delivery or operations foundation
> ℹ️ Item 3 (logging and correlation id) is covered in detail by [LOG-01](#log-01). Do it there, not here.
**Problem:** No Dockerfiles, no CI, no Actuator (health/readiness/metrics), no structured logging or correlation id, no root README explaining setup, no OpenAPI metadata (title, security scheme for the cookie), no graceful shutdown settings.

**Prompt:**
```
Add the operational baseline expected from an enterprise boilerplate:
1. Backend Dockerfile (multi-stage: maven:3-eclipse-temurin-21 build → eclipse-temurin:21-jre-alpine
   runtime, non-root user, layered jar, HEALTHCHECK on /actuator/health/readiness). Frontend Dockerfile
   (node build → nginx:alpine serving dist with SPA fallback, security headers, gzip, and /api proxied
   to the backend so the app is same-origin).
2. spring-boot-starter-actuator: expose health (liveness/readiness, db, redis, mail), info, metrics,
   prometheus. Restrict actuator to a management port or to authenticated users in prod.
3. Logging: logback-spring.xml with JSON (logstash-logback-encoder or Boot 3.4+ structured logging)
   in prod, human-readable in dev. A servlet filter (or Micrometer Tracing) adds a traceId to MDC and
   to the X-Request-Id response header, and it's included in error bodies (ERR-01).
4. server.shutdown=graceful, sensible Hikari pool and timeouts.
5. GitHub Actions workflow: backend (mvn verify with Testcontainers), frontend (npm ci, lint, test,
   build), docker build, dependency scanning (Dependabot/Renovate config), gitleaks.
6. Root README.md: architecture overview, prerequisites, how to run dev (compose + backend + frontend),
   env var reference, how to add a new domain (permissions, routes), testing, deployment.
7. OpenAPI: an @OpenAPIDefinition with title/version and a cookie-based security scheme.
```

---

<a id="log-01"></a>
### LOG-01 🟠 No logging strategy: backend logging, frontend logging, audit trail and regression guardrails
**Origin:** the remaining scope of [SEC-02](#sec-02). SEC-02 is about *removing* credential prints; LOG-01 is about *replacing* them with a real logging design and making sure they can't come back. It also takes over the logging and correlation-id part of [OPS-01](#ops-01) (item 3).
**Where (current state):**
- Backend: only the three bootstrap initializers use `@Slf4j`. Everything else has no logger.
- `GlobalExceptionHandler`: the `printStackTrace()` calls are gone (SEC-02), but nothing replaced them. Unexpected 500s now **leave no trace at all**, which is worse for operations than before. The `//todo implement logging mechanism needed` comments are still there.
- `AuthenticationController:86-87`: `System.out.println(...getPassword())` still sits inside the commented-out LDAP block. A single uncomment brings the leak back, and neither the compiler nor ArchUnit can see it.
- Java `record` DTOs (`AuthenticationRequestDTO`, `CreateUserRequestDTO`) generate a `toString()` that prints **every** field, password included. One `log.debug("{}", request)` leaks the credential.
- Frontend: `console.error` in `core/services/api.service.ts:59` and `main.ts:6`, `console.log(data.message)` in `login-page.ts:31`. `LoggerService` is an empty class. There is no ESLint at all (see [FE-08](#fe-08)), so `no-console` can't be enforced yet.
- No log format per profile, no correlation id, no audit trail for authentication or administrative actions, no rule that stops `System.out` / `console.log` from coming back.

**Problem:** logging is either missing (no diagnostics for 5xx, no audit trail) or unsafe (raw stdout, browser console, DTOs that print secrets). In an enterprise setting logs are shipped to an aggregator (ELK, Loki, Datadog), kept for months and read by many people, so they have to be **useful, structured, correlated and free of secrets**. Without automated guardrails, the next feature will bring `System.out` back.

**Target design (what "done" looks like):**

| Concern | Backend | Frontend |
|---|---|---|
| API | SLF4J 2 + Logback (Boot default) through Lombok `@Slf4j`. No `System.out`/`err`, no `printStackTrace`, no `java.util.logging`. | A single `LoggerService` with `debug/info/warn/error`. No direct `console.*` outside the console sink. |
| Format | `dev`: human-readable console pattern. `prod`: JSON through Spring Boot native structured logging (`logging.structured.format.console=ecs`), no extra dependency. | `dev`: browser console. `prod`: `debug`/`info` disabled, `warn`/`error` go to pluggable sinks (console by default; a remote sink such as Sentry or a `/client-logs` endpoint can be added later without touching call sites). |
| Correlation | A filter assigns an `X-Request-Id` (it accepts a valid incoming one, otherwise generates a UUID), puts it in MDC as `requestId`, returns it as a response header and includes it in error bodies ([ERR-01](#err-01)). After authentication the MDC also gets `userId` (the id, never the email). The MDC is cleared in `finally`. | An interceptor sends an `X-Request-Id` on every call and logs it with every HTTP error, so a support ticket can be matched to the backend log line. |
| Levels | `ERROR`: unexpected failure, stack trace logged **once**, at the global handler. `WARN`: handled security-relevant events (403, login failure, locked account). `INFO`: lifecycle and business events. `DEBUG`: diagnostics, off in prod. Expected 4xx business exceptions: `DEBUG`/`INFO`, **no** stack trace. Never log-and-rethrow. | `error`: uncaught errors (global `ErrorHandler`) and 5xx/0 HTTP failures. `warn`: handled 4xx. `debug`: dev diagnostics. |
| Secrets | Never log passwords, JWTs, the `token` cookie, `Authorization`/`Cookie` headers, the bootstrap admin password (`ADMIN_PASSWORD`), or request bodies of `/auth/**` and `POST /users`. Sensitive record DTOs override `toString()` to redact secrets. Emails are masked (`j***@domain.com`) through one `LogSanitizer` utility that also strips CR/LF from user-controlled values (log injection, CWE-117). | Never log form values, credentials, auth responses or full `HttpErrorResponse` bodies. Log status, method, URL path (no query string) and `requestId`. |
| Audit | A dedicated `AUDIT` logger (`LoggerFactory.getLogger("AUDIT")`) with SLF4J 2 key/value pairs (`event`, `outcome`, `actorId`, `targetId`, `maskedEmail`, `clientIp`, `requestId`). Login events come from Spring Security `AuthenticationSuccessEvent` / `AbstractAuthenticationFailureEvent` `@EventListener`s, not from code in the controller. | — |
| Guardrails | An ArchUnit test (no standard streams, no `java.util.logging`, no `printStackTrace`) **and** a Checkstyle regex rule that also scans comments, so commented-out prints fail the build too. | ESLint `"no-console": "error"`, turned off only for the console sink file and `main.ts`. |

**Events to audit:**
- `LOGIN_SUCCESS` / `LOGIN_FAILURE` (with the reason category: `BAD_CREDENTIALS`, `LOCKED`, `DISABLED`), `LOGOUT`, `ACCESS_DENIED`.
- Admin-provisioning approach: `BOOTSTRAP_ADMIN_CREATED` (replace the current `Bootstrap admin created: <email>` line with a masked email), `USER_CREATED` (actor id, new user id, role name), and later `USER_UPDATED`, `USER_DELETED`, `ROLE_PERMISSIONS_CHANGED` as those endpoints arrive.


**Prompt:**
```
Context: Spring Boot 4 / Java 21 backend in assofin/ (base package com.association.assofin, Lombok
available), Angular 22 frontend in assofin-frontend/. SEC-02 already removed the credential prints
from AuthenticationController.authenticate, login-form.ts and login-page.ts, and removed
printStackTrace from GlobalExceptionHandler. Now introduce an enterprise-grade logging design and
guardrails. Delete leftovers rather than commenting them out. Do not change business behavior.
Account approach: closed registration. The admin is bootstrapped at startup and accounts are created
through POST /users (Phase 1). There is no self-registration, activation or Redis any more.

BACKEND
1. Logging API and configuration
   - Use SLF4J through Lombok @Slf4j everywhere. Add lombok.config at assofin/ with
     `lombok.log.fieldName = log` and `config.stopBubbling = true`.
   - application.yml: logging.level.root=INFO, logging.level.com.association.assofin=INFO.
     application-dev.yml: com.association.assofin=DEBUG and a readable console pattern that includes
     %X{requestId}. application-prod.yml: logging.structured.format.console=ecs (Spring Boot native
     structured logging; do not add logstash-logback-encoder), and Hibernate SQL / show-sql off.
   - Add a logback-spring.xml only if the yml settings above cannot do the job; say why if you do.

2. Correlation id
   - Create infrastructure/logging/RequestCorrelationFilter (OncePerRequestFilter, ordered before
     the Spring Security filter chain). It reads X-Request-Id; if the value is missing or isn't a
     UUID/[A-Za-z0-9-]{8,64}, it generates a UUID. It puts the value into MDC "requestId", sets the
     X-Request-Id response header, and clears the MDC in finally.
   - After authentication succeeds in the JWT filter, put the user's id (never the email) into MDC
     "userId"; it is cleared in the same finally block.
   - Expose X-Request-Id in the CORS exposedHeaders so the frontend can read it.
   - Add the requestId to every error body produced by GlobalExceptionHandler, so users can quote it.

3. Sanitization (infrastructure/logging/LogSanitizer, final utility class + unit tests)
   - maskEmail("john.doe@domain.com") -> "j***@domain.com"; null/blank/invalid -> "***".
   - sanitize(String) strips \r and \n (log injection, CWE-117) and truncates to 200 chars.
   - Override toString() in every record DTO that carries a secret (AuthenticationRequestDTO, CreateUserRequestDTO, and any later password-change DTO)
     to print "***" instead of the secret. Add a unit test per DTO asserting the raw password never
     appears in toString().

4. Exception logging (GlobalExceptionHandler; add @Slf4j and remove the "todo logging" comments)
   - Exception.class (unexpected): log.error("Unhandled exception [requestId={}]", ..., exp) with
     the stack trace, exactly once. Never log-and-rethrow anywhere else.
   - Business / 4xx exceptions (EntityNotFound, EntityExists, validation, BadCredentials, Locked,
     Disabled): log at DEBUG (INFO for security-relevant ones) with no stack trace. Never log the
     rejected values of validation errors on password fields.
   - AccessDeniedException / the custom AccessDeniedHandler and AuthenticationEntryPoint: WARN
     with method, path, userId (if any). No stack trace.

5. Audit trail (infrastructure/logging/audit)
   - AuditLogger component wrapping LoggerFactory.getLogger("AUDIT"), using the SLF4J 2 fluent API:
     log.atInfo().addKeyValue("event", ...).addKeyValue("outcome", ...)...log("...").
     Fields: event, outcome (SUCCESS/FAILURE), actorId, targetId, maskedEmail, clientIp, requestId,
     reason (failure category only).
   - AuthenticationAuditListener with @EventListener for AuthenticationSuccessEvent and
     AbstractAuthenticationFailureEvent (make sure an AuthenticationEventPublisher bean is
     registered so the events fire). Log LOGIN_SUCCESS (INFO) and LOGIN_FAILURE (WARN) with the
     masked email and the client IP. The failure reason is a category (BAD_CREDENTIALS, LOCKED,
     DISABLED), never the submitted password, and the HTTP response stays generic.
   - Client IP: HttpServletRequest.getRemoteAddr(). Set server.forward-headers-strategy=framework
     (prod) so a trusted proxy's X-Forwarded-For is honored; never parse X-Forwarded-For by hand.
   - Also audit: LOGOUT, ACCESS_DENIED, BOOTSTRAP_ADMIN_CREATED (AdminUserInitializer: replace the
     "Bootstrap admin created: <email>" log with an audit event using the masked email),
     USER_CREATED (UserService.createUser: actorId, new user id, role name; never the request DTO),
     and ROLE create/update/delete in RoleService (actorId, role id, permission names).

6. Cleanup
   - Delete the commented-out LDAP block in AuthenticationController (it still contains
     System.out.println(...getPassword())), and any other commented code containing System.out,
     printStackTrace or credentials.
   - Replace every remaining System.out / System.err / printStackTrace in src/main and src/test
     with the logger (tests may use assertions instead of prints).

7. Guardrails (so this can't regress)
   - Add com.tngtech.archunit:archunit-junit5 (test scope) and an ArchitectureTest with:
       GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS,
       GeneralCodingRules.NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING,
       a custom rule: no classes should call method Throwable.printStackTrace(),
       a custom rule: Logger fields are private static final (what Lombok @Slf4j generates).
   - Add maven-checkstyle-plugin bound to the validate phase (failOnViolation=true,
     includeTestSourceDirectory=true) with config/checkstyle/checkstyle.xml holding ONLY these rules
     for now (no full Google style, to avoid noise):
       RegexpSinglelineJava format "System\.(out|err)\." ignoreComments=false
       RegexpSinglelineJava format "\.printStackTrace\(" ignoreComments=false
     ignoreComments=false is intentional: ArchUnit checks bytecode and can't see commented-out
     prints; Checkstyle can.

8. Backend tests
   - Using @ExtendWith(OutputCaptureExtension.class) + @SpringBootTest (Testcontainers Postgres):
     a failed and a successful POST /auth/login and a POST /users produce LOGIN_FAILURE/LOGIN_SUCCESS
     audit lines, and the captured output never contains the submitted password, the JWT value or
     the full email.
   - The response of any request carries X-Request-Id; a valid incoming X-Request-Id is echoed back;
     an error body contains the same requestId.
   - A forced unexpected exception produces exactly one ERROR line with a stack trace.

FRONTEND
9. Tooling prerequisite (part of FE-08): run `ng add angular-eslint`, add "lint": "ng lint" to
   package.json.
10. LoggerService (src/app/core/services/logger.service.ts, providedIn root)
   - Levels debug < info < warn < error. The minimum level comes from the environment
     (NG_APP_LOG_LEVEL via @ngx-env, default "debug" in dev, "warn" in prod) and isDevMode().
   - Output goes through an InjectionToken<LogSink[]> LOG_SINKS (multi). Ship ConsoleLogSink
     (the ONLY file allowed to use console.*). Keep the token so a remote sink (Sentry, or a future
     POST /client-logs endpoint) can be plugged in later; don't build the remote sink now.
   - Every entry has: level, message, context (string), timestamp, requestId (if known), and an
     optional sanitized data object. A sanitize step drops keys matching
     /password|token|secret|authorization|cookie/i, recursively.
11. Global error handling
   - Provide a custom ErrorHandler (GlobalErrorHandler) that logs uncaught errors through
     LoggerService.error. Keep provideBrowserGlobalErrorListeners().
   - Add a correlation interceptor that sets X-Request-Id (crypto.randomUUID()) on every API call.
     The error interceptor / ApiService logs HTTP errors as: method, URL path without query string,
     status, requestId (from the response header, or the one sent). Never the request body.
     5xx/0 -> error, 4xx -> warn.
12. Replace console usage
   - core/services/api.service.ts:59 -> logger.error(...) with the safe fields above.
   - features/auth/pages/login-page/login-page.ts:31 -> remove (use the notification service for
     user-facing messages, not logs).
   - main.ts:6 runs before Angular DI exists: keep console.error there, with an
     `// eslint-disable-next-line no-console -- bootstrap failure, DI not available` comment.
13. ESLint guardrail (eslint.config.js)
   - "no-console": "error" for src/**/*.ts.
   - An override that turns it off only for src/app/core/logging/console-log.sink.ts (or wherever
     ConsoleLogSink lives) and src/main.ts.
14. Frontend tests (Vitest/Karma, whichever the project uses)
   - LoggerService: debug/info are dropped at level "warn"; sensitive keys are removed from data;
     sinks receive the entry.
   - GlobalErrorHandler forwards to LoggerService.error.
   - Correlation interceptor sets X-Request-Id.

VERIFICATION
   - grep -rnE "System\.(out|err)|printStackTrace" assofin/src -> no results (comments included).
   - grep -rnE "console\.(log|debug|info|warn|error)" assofin-frontend/src -> only the console sink
     and main.ts.
   - cd assofin && ./mvnw -q verify passes, including ArchitectureTest and Checkstyle.
     Temporarily adding System.out.println("x") anywhere fails the build (try it, then revert).
   - cd assofin-frontend && npm run lint && npm test && npm run build pass. Temporarily adding
     console.log("x") to a component fails lint (try it, then revert).
   - Run the app with the prod profile: logs are JSON, each line has requestId, a login produces one
     AUDIT line with a masked email, and no password/JWT appears anywhere.
Split into commits: "add backend logging, correlation id and audit trail",
"forbid System.out and printStackTrace with ArchUnit and Checkstyle",
"add frontend LoggerService, global error handler and no-console lint rule".
```

---

<a id="tst-01"></a>
### TST-01 🟠 No real tests
**Where:** `AssofinApplicationTests` (needs a running Postgres and Redis), Angular `*.spec.ts` (CLI stubs, about 268 lines total, and some likely fail without providers, e.g. `TestBed.configureTestingModule({})` for services that inject `HttpClient`).

**Prompt:**
```
Create a testing foundation for the boilerplate:
Backend:
1. Testcontainers via @ServiceConnection (PostgreSQLContainer, RedisContainer) in a shared
   @TestConfiguration so @SpringBootTest no longer needs local infra. MailHog/GreenMail for mail.
2. Unit tests (JUnit 5 + Mockito + AssertJ): JwtService, ActivationTokenService, Role.replacePermissions,
   RoleService (conflicts, missing permissions), UserService.
3. Slice tests: @WebMvcTest for AuthenticationController, RoleController, UserController with
   spring-security-test (@WithMockUser(authorities = "role:read")), asserting status codes and error
   JSON. @DataJpaTest for repositories/entity graphs.
4. One end-to-end integration test: register → read the token from GreenMail → activate → login
   (cookie) → GET /users/me → logout.
5. ArchUnit rules (see ARC-02) and a JaCoCo threshold (e.g. 70% line coverage on application/domain).
Frontend:
6. Fix the existing specs with the correct providers (provideHttpClient(), provideHttpClientTesting(),
   provideRouter([]), MessageService). Write meaningful tests for AuthService (session caching,
   login refresh, clearSession), authGuard/permissionGuard (redirects), errorInterceptor (per status),
   ApiService (URL building), LoginForm (validation, emits on valid submit).
7. Add Playwright e2e for the login flow against the compose stack.
Make `./mvnw verify` and `npm test -- --watch=false` both pass in CI.
```

---

<a id="hyg-01"></a>
### HYG-01 🔵 Code hygiene
**Examples:** a large commented-out LDAP login block in `AuthenticationController:73-104` (it contains a nested `/*`, which is fragile); unused `userService` in `AuthenticationController`; `@ResponseStatus(ACCEPTED)` together with `ResponseEntity` (redundant), and login returning **202** instead of 200; `ResponseEntity<?>`; unused imports (`Value`, `HashMap`, `Map`, `UTF_8`, `MULTIPART_MODE_MIXED`, `Page`, `Pageable`, `List`, `Set`, `Role`); unused `RegistrationResponseDTO`, `findByUsernameIgnoreCase`, `invalidateToken`, `TempTokenResponse`, `HttpResourceRequest`, `HttpClient`, `Service` import in `auth.service.ts`, `title` signal in `App`; `assert user != null` (disabled at runtime); typos (`exceptionDec`, "bellow", "environement", "avaodessey"); comments that are out of date (`// might use the cors properties class to refactor…` when it already does; "Endpoint 2/1" ordering); inconsistent formatting (tabs vs spaces, `=userDetailsService`, `RoleResponseDTO  save`).

**Prompt:**
```
Perform a hygiene-only pass across backend and frontend (no behavior change):
1. Delete commented-out code blocks (LDAP login, old token-in-body implementation, commented compose
   services, the commented users route). Capture any still-relevant intent as GitHub issues or ADRs.
2. Remove unused imports, fields, methods, classes and DTOs (list each removal in the PR description).
3. Controllers: return ResponseEntity without redundant @ResponseStatus; login returns 200 OK
   (or 204), register 202, logout 204; replace ResponseEntity<?> with concrete types.
4. Replace `assert` with explicit checks.
5. Fix typos and outdated comments; keep comments explaining "why", not "what".
6. Apply automatic formatting: Spotless (Java) and Prettier (frontend, add "format" and "format:check"
   npm scripts); add .editorconfig at the repo root.
7. Ensure ./mvnw verify, npm run lint and npm test still pass.
```

---

<a id="fe-01"></a>
### FE-01 🔴 Login loop after successful login
**Where:** `auth.service.ts:56-90, 98-101`, `auth-guard.ts`, `login-page.ts:51`.
**Problem:** When an unauthenticated user hits `/`, `authGuard` → `checkSession()` → 401 → `clearSession()` sets `isInitialized = true, user = null`. After a successful login, `login()` doesn't refresh state, so `navigateByUrl('/')` → `checkSession()` returns the cached `null` → redirect back to `/auth`. The user can't get in without a full page reload. `logout()` also doesn't navigate or reset in-flight state. `signup()` sets the current user from a response that is empty (202).

**Prompt:**
```
Fix the Angular auth state machine in src/app/core/auth/services/auth.service.ts:
1. login(): after a successful POST /auth/login, call the session endpoint (GET /users/me), set
   currentUser and isInitialized, and only then emit. Use switchMap: login → getSession → tap(set state).
2. checkSession(): cache the in-flight request (shareReplay(1) on a private session$) so parallel
   guards don't fire duplicate calls; map errors to of(null) instead of rethrowing, so guards don't
   need catchError.
3. logout(): call the backend, then clear state in finalize() (even if the call fails) and navigate
   to /auth in the caller or a dedicated effect.
4. signup(): don't set currentUser (the backend returns 202 with no body); return Observable<void>
   and show a "check your email" message.
5. Optionally use provideAppInitializer(() => inject(AuthService).checkSession()) to resolve the
   session once at boot.
6. Unit tests: guard → 401 → login → navigate('/') succeeds; logout clears state even on HTTP error.
```

---

<a id="fe-02"></a>
### FE-02 🟠 TypeScript strictness disabled
**Where:** `assofin-frontend/tsconfig.json`.
**Problem:** The Angular CLI default `"strict": true`, `strictTemplates`, `strictStandalone` and similar were removed, and `any` is used (`formatErrors(error: any)`). For a boilerplate this lowers the bar for every derived project.

**Prompt:**
```
Re-enable strict type checking in assofin-frontend/tsconfig.json:
- compilerOptions: "strict": true, "noUncheckedIndexedAccess": true (optional), keep existing flags,
  and add "baseUrl"-free paths (already fine) plus "@features/*", "@layouts/*", "@shared/*" aliases.
- angularCompilerOptions: "strictTemplates": true, "strictInjectionParameters": true,
  "strictInputAccessModifiers": true, "strictStandalone": true, "extendedDiagnostics" with checks set to error.
Fix all resulting compile errors properly (no `any`, no non-null assertions unless justified); type
API errors with an ApiError interface matching the backend ProblemDetail (ERR-01).
```

---

<a id="fe-03"></a>
### FE-03 🟠 Error handling chain is broken
**Where:** `api.service.ts:58-61`, `login-page.ts:53-57`, `error-interceptor.ts`.
**Problem:** `ApiService.formatErrors` rethrows `error.error` (the body), so components lose `status`. `login-page` then reads `err.error?.message` (undefined) while the backend sends `errorMessage`. `notificationService.show(err.message)` passes a **string** where `ToastMessageOptions` is expected. Errors are logged with `console.error` and toasts are shown twice (interceptor + component). The interceptor ignores 404/409/422 and uses hard-coded English strings although the app has ar/en/fr.

**Prompt:**
```
Rework frontend error handling:
1. Define `interface ApiError { status: number; code?: string; title: string; detail?: string;
   errors?: {field: string; message: string}[]; traceId?: string }` (matching the backend ProblemDetail).
2. Convert HttpErrorResponse → ApiError in ONE place (the errorInterceptor), rethrow the ApiError,
   and remove catchError/formatErrors from ApiService.
3. The interceptor shows global toasts only for 0/401(session expiry)/403/5xx. 400/404/409/422 are left to
   the caller (forms map `errors` to field errors). Add an HttpContextToken SKIP_GLOBAL_ERROR so callers
   (e.g. login, where 401 means wrong credentials) can opt out of the global toast and the session clear.
4. Fix login-page: show `apiError.detail` in errorMessage. Remove the wrong notificationService.show(string) call.
5. All user-facing strings go through i18n keys (see FE-07).
6. Unit-test the interceptor per status code and the SKIP_GLOBAL_ERROR token.
```

---

<a id="fe-04"></a>
### FE-04 🟠 Guards are broken or inverted
**Where:** `reverse-auth-guard.ts`, `permission-guard.ts`, `app.routes.ts`.
**Problem:** `reverseAuthGuard` redirects **unauthenticated** users to `/dashboard` in its `catchError` (inverted). It isn't applied to `/auth`, and `/dashboard` doesn't exist. `permissionGuard` redirects to `/login` (the route is `/auth`), skips `checkSession()` when not initialized (so on a deep link `currentUser` is still null → denied), and hard-codes the `:read` suffix. The wildcard redirect `**` → `''` turns every typo into an auth check.

**Prompt:**
```
Fix and standardize Angular route guards:
1. Centralize route paths in a constant (APP_ROUTES = { auth: '/auth', home: '/', forbidden: '/403' }).
2. guestGuard (rename reverseAuthGuard to a CanMatchFn): authenticated → redirect home; unauthenticated
   or error → allow. Apply it to the 'auth' route.
3. authGuard: uses checkSession() (which now never errors, see FE-01) and redirects to /auth with a
   returnUrl query param. After login, navigate to returnUrl.
4. permissionGuard(...required: AppPermission[]): await checkSession() first, then check
   hasAnyPermission/hasAllPermissions. Unauthenticated → /auth, missing permission → /403. Read
   required permissions from route data (data: { permissions: ['role:read'] }) instead of the
   string-concatenated ":read".
5. Add a simple HomePage (dashboard) child under MainLayout, a 403 page and a 404 page. Change the
   '**' route to the 404 page.
6. Add a *hasPermission structural directive for showing/hiding UI elements.
7. Unit tests for each guard.
```

---

<a id="fe-05"></a>
### FE-05 🟠 Frontend/backend contract mismatch

> ℹ️ **After [Phase 1](#phase-1)** the register DTO mismatch disappears (signup is removed).
**Where:** `auth-user-profile.dto.ts`, `register-request.dto.ts`, `auth-response.dto.ts`, `login-request.dto.ts`.
**Problem:** The permission format differs (`user:read` vs `role::read`), and the frontend permission list (`user:*`, `invoice:*`) doesn't exist on the backend. `role.permissions` isn't in the backend response. `id: string` vs a Java `long`. `RegisterRequestDto` lacks `firstname`/`lastname`, which the backend requires. `AuthResponseDto.user` and `token` are never returned. Login DTO fields are optional although required.

**Prompt:**
```
Align frontend DTOs with the backend API and prevent future drift:
1. Generate TypeScript models from the backend OpenAPI spec (/v3/api-docs) with openapi-typescript
   or ng-openapi-gen into src/app/core/api/generated (git-tracked or generated in CI). Add an npm
   script "generate:api".
2. If not generating, manually fix: AuthUserProfileDto { id: number; email; firstname; lastname;
   role: { name: string; permissions: AppPermission[] } }, RegisterRequestDto { firstname; lastname;
   email; password }, LoginRequestDto with required fields, AuthResponseDto { message: string }.
3. AppPermission must match the backend permission strings exactly (single format, see ARC-01).
   Remove the fictional invoice/user permissions or add them to the backend.
4. Add a contract test (backend) that snapshots the OpenAPI JSON, so API changes are visible in PRs.
```

---

<a id="fe-06"></a>
### FE-06 🟡 `ApiService` defects
**Where:** `core/services/api.service.ts`.
**Problem:** The regex `/\/\$/` matches the literal string `"/$"`, not a trailing slash, so `http://host/` + `/api/v1` gives `//api/v1`. `API_VERSION` is hard-coded and duplicates the backend context path. There's no `patch` method (the backend role update is PATCH). `timeout` config is never applied. `withCredentials: true` is forced on every call (that's fine, but better handled by an interceptor). `APP_CONFIG_VALUE` is referenced directly in `app.config.ts`, which bypasses the DI token.

**Prompt:**
```
Fix src/app/core/services/api.service.ts:
1. Trailing slash regex: replace(/\/+$/, ''). Build URLs with a helper that also strips leading
   slashes from `path`.
2. Move the version prefix into config (NG_APP_API_BASE_URL already includes /api/v1, or a separate
   NG_APP_API_PREFIX). Keep one source of truth.
3. Add patch<T>().
4. Create interceptors: credentialsInterceptor (withCredentials: true for API URLs only) and
   timeoutInterceptor (rxjs timeout(config.timeout), mapping TimeoutError to an ApiError with status 0
   or 408). Register them in app.config.ts in a documented order (credentials → timeout → error).
5. Use a dev proxy (proxy.conf.json, "/api" → http://localhost:8080) so dev is same-origin like prod
   (simplifies CORS and CSRF, see SEC-07).
6. In app.config.ts read config through the APP_CONFIG token where possible; validate env values at
   startup (throw a readable error if NG_APP_API_BASE_URL is empty).
7. Note in docs that NG_APP_PRIMEUI_LICENSE_KEY ends up in the public bundle (it isn't a secret).
8. Unit tests for URL building and each interceptor.
```

---

<a id="fe-07"></a>
### FE-07 🟡 Incomplete or placeholder features

> ℹ️ **After [Phase 1](#phase-1)** the signup form and activation page are out of scope (removed, not completed).
**Where:** `signup-form.ts/html` (empty), no activation page although the backend emails `${FRONTEND_ACTIVATION_URL}` (`/activate`), `public/i18n/{ar,en,fr}.json` (0 bytes, no i18n library installed), `logger.service.ts` (empty), `login-page.html` references `/img/auth-side-image2.png` (it doesn't exist; only `bg-image.jpeg` does), `MainLayout` without a header, nav or logout.

**Prompt:**
```
Complete the auth boilerplate UI:
1. SignupForm: reactive form (firstname, lastname, email, password, confirmPassword with a
   cross-field validator), backend validation errors mapped to fields, success state
   "Check your inbox to activate your account".
2. ActivateAccountPage at /auth/activate: reads ?token, POSTs to /auth/activate (BUG-07), shows
   success/expired states, and offers "resend activation email".
3. i18n: install @jsverse/transloco (or ngx-translate), wire the existing public/i18n/{en,fr,ar}.json
   files, add a language switcher, and support RTL for Arabic (set dir="rtl" on <html> dynamically).
   Move every hard-coded string (interceptor messages, forms) to translation keys.
4. LoggerService: levels (debug/info/warn/error) that are no-ops in production (isDevMode()), with a
   hook to send errors to a remote collector later. Provide a global ErrorHandler using it.
5. MainLayout: header with user name, logout button (calls AuthService.logout then navigates),
   sidebar/nav driven by permissions.
6. Fix the missing image reference, set a proper <title> (with Title strategy per route) and favicon.
```

---

<a id="fe-08"></a>
### FE-08 🟡 Frontend tooling and markup
**Where:** `package.json`, `login-page.html`, `index.html`.
**Problem:** No ESLint (`angular-eslint`). Prettier is installed but has no script or CI check. `@primeicons/angular` is imported but not declared in `package.json` (it's only present transitively, so it can break on any install). `login-page.html` wraps `<app-login-form>` (which has its own form) inside another `<form>`, which is invalid nested-form HTML that can cause double submits. `README.md` is the default CLI text. `version: 0.0.0`.

**Prompt:**
```
Harden frontend tooling:
1. `ng add angular-eslint`; enable recommended + template accessibility rules, plus no-console and
   @typescript-eslint/no-explicit-any as errors. Add npm scripts: lint, format, format:check, test:ci
   (vitest run --coverage).
2. Declare @primeicons/angular explicitly in dependencies with a pinned compatible version; run
   `npm ls` to check for other undeclared imports.
3. Remove the outer <form> in login-page.html (use a <div>/<section>).
4. Add husky + lint-staged pre-commit (prettier + eslint on staged files).
5. Replace the CLI README with project-specific docs (structure: core/features/layouts/shared,
   conventions for adding a feature module, env vars, scripts).
6. Add accessibility basics: labels for inputs, aria-live for form errors, focus management after
   navigation.
```

---

## 5. Suggested order of work

0. **Phase 1, closed registration** ([section 3](#phase-1)): [1.1](#phase-1-1) PR → [1.2](#phase-1-2) merge → [1.3](#phase-1-3) branch → [1.4](#phase-1-4) remove register/activation/Redis → [1.5](#phase-1-5) bootstrap ([BUG-01](#bug-01), [BUG-03](#bug-03) first) → [1.6](#phase-1-6) `POST /users` → [1.7](#phase-1-7) verify + PR. Closes [SEC-01](#sec-01) and [BUG-02](#bug-02).
1. **Make it safe:** [SEC-02](#sec-02), [SEC-03](#sec-03), [SEC-04](#sec-04), [SEC-05](#sec-05)
2. **Make it work end-to-end:** [BUG-04](#bug-04), [BUG-05](#bug-05), [FE-01](#fe-01), [FE-05](#fe-05)
3. **Make it trustworthy:** [TST-01](#tst-01) (Testcontainers + key flows), [PER-01](#per-01) (Flyway; port the Phase 1 seeding to migrations if you adopt it), [ERR-01](#err-01)
4. **Make it reusable:** [ARC-01](#arc-01), [ARC-02](#arc-02), [CFG-01](#cfg-01), [CFG-02](#cfg-02), [CFG-03](#cfg-03), [CFG-04](#cfg-04), [CFG-05](#cfg-05), [FE-02](#fe-02), [FE-03](#fe-03), [FE-04](#fe-04), [FE-06](#fe-06)
5. **Make it operable:** [OPS-01](#ops-01), [SEC-06](#sec-06) (login only), [SEC-07](#sec-07), [SEC-09](#sec-09), [PER-02](#per-02), [PER-03](#per-03), [PER-04](#per-04)
6. **Polish:** [HYG-01](#hyg-01), [FE-07](#fe-07), [FE-08](#fe-08), [ARC-04](#arc-04), [SEC-08](#sec-08)
7. **Only if email features return:** [BUG-06](#bug-06), [BUG-07](#bug-07), [ARC-03](#arc-03)
8. **Logging and audit (after Phase 1 and steps 1 to 6 are done):** [LOG-01](#log-01). It finishes [SEC-02](#sec-02) and replaces item 3 of [OPS-01](#ops-01). It comes last on purpose: by then the user-management endpoints, the error contract ([ERR-01](#err-01)) and ESLint ([FE-08](#fe-08)) exist, so the audit events, the `requestId` in error bodies and the `no-console` rule have stable code to attach to.

Once Phase 1 and steps 1 to 4 are done, this would reasonably score **7.5 to 8 / 10** as an enterprise starter. Step 5 is what moves it to "production-grade platform template".
