# Research: Foundation (F0)

All items below were unknowns in the Technical Context or technology choices that need a
recorded rationale. Versions were checked against npm and Maven Central on 2026-10-24.

## R1. Angular version and Node prerequisite

- **Decision**: Angular 22.2.x with standalone components, signals and strict TypeScript.
- **Rationale**: Latest stable; PrimeNG 22.1 lists Angular `^22.1` as its peer. Angular 22 requires Node `^22.22.3 || ^24.15.0 || >=26`.
- **Finding**: This machine has Node 22.22.2, which is too old. **Prerequisite**: upgrade Node to 22.22.3 or newer (24 LTS is fine) before creating `frontend/`.
- **Alternatives**: Pin Angular 21 to stay on Node 22.22.2 (rejected: older stack, no benefit once Node is updated).

## R2. PrimeNG theming with a custom preset

- **Decision**: Build one preset, `src/app/theme/campusflow-preset.ts`, with `definePreset(Aura, { semantic: { primary: {...}, colorScheme: { light: {...} } }, components: {...} })` from `@primeuix/themes`, registered through `providePrimeNG({ theme: { preset, options: { darkModeSelector: false } } })`. Light theme only.
- **Rationale**: This is PrimeNG's own design-token mechanism; the accent (`#6D4AE0`), surfaces, text, borders and radius live in one file. Tailwind 4 reads the same values via CSS variables defined in `styles.css` from the preset's tokens, so layout utilities and components agree.
- **Verification step (first implementation task)**: a short spike that confirms the `definePreset` / `providePrimeNG` import paths and the token names against PrimeNG 22.1.4 and `@primeuix/themes` 3.1.0 before screens are built. The package exposes `dist/*`, `tokens` and `types`; exact exports are confirmed during the spike.
- **Tokens**: primary 500 = `#6D4AE0` (scale generated around it); surface 0 = white, page `#F7F5FD`, sidebar `#EDE7FB`; text `#1E1833`, muted `#5A5075`; border `#E6E0F5`; input border `#8E84AD` (3:1 on white); radius 10-16 px; control height 44 px. Red/amber only for error/warn severities.
- **Alternatives**: Plain CSS overrides on a stock theme (rejected: scatters colours, breaks on upgrade); Angular Material (rejected: user chose PrimeNG).

- **Spike result (T008, PrimeNG 22.1.4, `@primeuix/themes` 3.1.0)**: confirmed and working.
  - Imports: `import { definePreset } from '@primeuix/themes'`, `import Aura from '@primeuix/themes/aura'`, `import { providePrimeNG } from 'primeng/config'`.
  - Radius is a primitive token, not a semantic one: set it under `primitive.borderRadius` (`semantic.borderRadius` fails to compile). Form fields and components read it through `{border.radius.*}`.
  - Aura's own semantic tokens use `light-dark(...)`; overriding `semantic.colorScheme.light` still works with `darkModeSelector: false` (light only).
  - Verified by `theme/campusflow-preset.spec.ts`: rendering a `p-button` injects styles containing `#6d4ae0`, `#8e84ad` and `10px`.
  - PrimeNG has no height token. The 44 px minimum comes from padding: `paddingY: 0.75rem` on form fields and buttons gives about 45 px with the 14 px font, 1.5 line height and 1 px border. Confirm visually when the first screens exist.

## R3. Fonts offline

- **Decision**: Self-host via `@fontsource/plus-jakarta-sans` and `@fontsource/noto-sans-gujarati`; font stack `"Plus Jakarta Sans", "Noto Sans Gujarati", system-ui, sans-serif` (FR-042).
- **Rationale**: The school computer may have no internet (agreement section 10); Google Fonts would break rendering.
- **Alternatives**: Google Fonts CDN (rejected: offline).

## R4. Authentication: server session, JSON login

- **Decision**: Spring Security with a server-side session (`JSESSIONID`, HttpOnly, SameSite=Lax), JSON `POST /api/auth/login`, 10-minute idle timeout via `server.servlet.session.timeout=10m` (FR-008). BCrypt for password hashes.
- **Rationale**: Matches the user's instruction; one admin, one app, same origin in production, so no token infrastructure is needed. Idle timeout is enforced by the container; a session listener records the expiry in the audit log.
- **Alternatives**: JWT (rejected: needless for same-origin single user, harder logout/idle handling); Argon2 (BCrypt is sufficient and built in).

## R5. CSRF with an Angular SPA

- **Decision**: Keep CSRF protection on, using `CookieCsrfTokenRepository.withHttpOnlyFalse()` and Spring's SPA-friendly request handler; Angular's `HttpClient` sends `X-XSRF-TOKEN` automatically.
- **Rationale**: Session cookies need CSRF protection; Angular supports the pattern natively.
- **Alternatives**: Disable CSRF (rejected: unsafe with cookie sessions).

## R6. Lock-out and developer reset

- **Decision**: `users.failed_attempts` and `users.locked_until`; after 5 consecutive failures sign-in is refused for 5 minutes (FR-044), counters reset on success. There is no recovery code: a forgotten password is reset by the developer with a startup property that generates a new random password, writes it to the credentials file, marks it unchanged and is audited (FR-045).
- **Rationale**: Directly from the spec and clarifications; stored in the database so the pause survives restarts.

## R7. First-run credentials delivery

- **Decision**: When `users` is empty at startup, create the Admin with a random password. Write it to a one-time file in the application data directory (`credentials-first-run.txt`) and log only that the file was created. The installer (later phase) shows the file once and deletes it.
- **Rationale**: Secrets must not go into logs (Principle VI, FR-002). A console banner is lost on a service install.
- **Open point for the installer feature**: who shows and deletes the file. Until then, quickstart documents reading and deleting it manually.
- **Alternatives**: Log banner (rejected: persists in logs); fixed default password (rejected by the spec).

## R8. Academic year integrity in the database

- **Decision**: Enforce in PostgreSQL: `CHECK (end_date > start_date)`; an exclusion constraint `EXCLUDE USING gist (daterange(start_date, end_date, '[]') WITH &&)` for overlaps (FR-011). The current academic year is not stored: it is derived from today's date (an injected `java.time.Clock` with zone Asia/Kolkata) and the year dates, and the overlap constraint guarantees at most one match (FR-012, FR-013).
- **Rationale**: Database constraints hold even under two overlapping requests (SC-003). Range types support `&&` in GiST without the `btree_gist` extension.
- **Alternatives**: Application-only checks (rejected: race conditions).

## R9. Concurrent edits

- **Decision**: JPA `@Version` on every editable table; a stale save returns HTTP 409 with the latest record, and the UI shows the latest values (FR-046).
- **Alternatives**: Last-write-wins (rejected by clarification).

## R10. Removing master data

- **Decision**: Delete attempt runs; a foreign-key violation maps to HTTP 409 "in use" (for standards, divisions and categories the message suggests marking inactive instead). Inactive standards, divisions and categories are hidden from pick-lists for new records but stay on existing ones. Academic years have no inactive status (FR-020).
- **Rationale**: Works automatically as later modules add foreign keys; no per-module "in use" registry needed.

## R11. Audit log mechanism

- **Decision**: Explicit `AuditService.record(...)` calls from services, `Propagation.MANDATORY` so it must join the business transaction (FR-023). Auth events use `REQUIRES_NEW`. `audit_log` has a trigger that raises an error on UPDATE or DELETE (FR-024). `SensitiveMasker` masks to the last 4 characters; password, hash and recovery code are never recorded (FR-025).
- **Rationale**: Explicit calls are easy to test and read; listeners/AOP hide which writes are audited and make field masking harder to control.
- **Alternatives**: Hibernate `Interceptor`/Envers (rejected: captures everything, harder to mask and to label by area); database triggers for all tables (rejected: loses the app user and reason).
- **Auth event shape**: `action=Login`, `area=Security`, `entity=Session`, event type in `changes` (login, failed, paused, logout, idle, password, reset).

## R12. Standard table: server query and Excel export

- **Decision**: Request shape mirrors PrimeNG's lazy event (`first`, `rows`, `sortField`, `sortOrder`, `globalFilter`, `filters{field:{value,matchMode}}`). Each list declares `ColumnDef`s once; `TableQueryService` builds JPA Specifications (search = OR across searchable columns, filters = AND, then sort and page in SQL). Export uses `SXSSFWorkbook` (window about 500 rows) fed by paged reads, writes all declared columns including hidden ones, keeps sensitive columns masked, then records an Export audit entry.
- **Rationale**: One implementation, reused by every list (FR-034); memory use stays flat for large exports (SC-007).
- **Match modes supported in F0**: contains, startsWith, equals, date range, boolean; more can be added later without changing the request shape.
- **Alternatives**: Per-list hand-written queries (rejected: violates reuse); client-side export (rejected: must export all pages).

## R13. Serving the Angular build from Spring Boot

- **Decision**: Dev uses `ng serve` with `/api` proxied to port 8080. Production build is copied into `management/src/main/resources/static` (git-ignored) by a script; a controller forwards non-`/api`, non-file paths to `index.html`.
- **Rationale**: One process on the school computer. A Maven profile wrapping the build is deferred to the packaging phase.

## R14. Test strategy

- **Decision**: Backend integration tests run against a separate local database `admin_management_test` (Flyway `clean`+`migrate` allowed only under the `test` profile) until Docker is set up; Testcontainers can replace it later without changing tests. Playwright runs against the dev servers with the test profile. A custom `@Covers("FR-xxx")` annotation and FR IDs in Playwright titles feed a script that fails the build if any FR in `spec.md` has no reference (FR-036, Principle VIII).
- **Alternatives**: H2 (rejected: constraints such as exclusion and partial indexes are PostgreSQL-specific).

## R15. Dates and locale

- **Decision**: API uses ISO-8601; screens show `dd-MM-yyyy` (matches the agreement's examples); English UI with Gujarati text supported by the font stack.

## R16. Items outside this plan that need action

- Upgrade Node (R1).
- (No mockup export needed; screens follow `docs/design/ui-tokens.md`.)
- Update agreement v1.1 and its decision log: `active` flag on master tables, audit `area` and `Delete`, user lock-out columns (recovery code later dropped), masked audit values. Consider a PATCH amendment to constitution Principle VI wording.
