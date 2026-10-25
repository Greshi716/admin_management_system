# Security review: Foundation (F0), T102

Date: 2026-10-25. Scope: `management/src/main/java/com/admin/management/security/`, `shared/web/ApiExceptionHandler`,
`management/src/main/resources/application*.properties`. Done by reading the code (the `security-review` skill
could not run because the repository has no `origin` remote to diff against).

## Checked and fine

| Area | Finding |
|---|---|
| Session cookie | `HttpOnly`, `SameSite=Lax`; session id is changed at sign-in (no fixation); 10 minute idle timeout |
| CSRF | On for every request including sign-in; token in the `XSRF-TOKEN` cookie, checked from the `X-XSRF-TOKEN` header; refused calls answer 403 JSON |
| Access | Every `/api` call except sign-in needs a session; unsigned calls answer 401 JSON, never a redirect; `/api/test-support` exists only under the `test` profile |
| Passwords | Hashed by `PasswordEncoder`; first-run password is 20 random characters from `SecureRandom`; written only to `credentials-first-run.txt` (deleted when the password is changed); the log gets the path, not the password |
| Sign-in | Unknown username costs the same hashing time as a wrong password; five failures pause sign-in for 5 minutes; every attempt is audited; a typed-in password in the username box is masked and cut to 50 characters |
| Error bodies | Validation, conflict and constraint errors map to fixed messages; no SQL text or stack trace reaches the client |
| Test-only values | The fixed test admin password is honoured only under the `test` profile |

## Changed in this task

- `application.properties`: `server.error.include-*` set to never, so no error body can ever carry a message,
  stack trace or binding detail, even if Spring Boot's defaults change.

## Left as is, for the owner to decide

1. **Database password in `application.properties`** (`postgres` / `postgres`). It is the development default for
   localhost. On the school computer, override it with environment variables
   (`SPRING_DATASOURCE_PASSWORD`) set by the Docker compose file, not by editing this file.
2. **Listening address.** The app listens on all network interfaces. If the school computer is the only client,
   `server.address=127.0.0.1` would stop other machines on the network reaching it. Not changed, because the
   plan does not say whether other computers will connect.
3. **Session cookie is not `Secure`.** The app is served over plain HTTP on the school computer, where a `Secure`
   cookie would not be stored by every browser. Revisit if HTTPS is added.
4. **The pause message shows only for real usernames**, so someone who tries five times can tell that a username
   exists (423 versus 401). With a single admin account this gives away little. The pause itself is required by FR-044.
5. **A person who can reach the sign-in page can pause the admin's sign-in for 5 minutes** by failing five times.
   This is the cost of FR-044 and is accepted by the spec.
6. **`credentials-first-run.txt` on Windows** keeps the permissions of the data folder (no POSIX `rw-------`).
   Keep `data/` readable only by the school computer's admin account.
7. **No catch-all handler** for unexpected exceptions; they reach Spring Boot's default error page, which now
   shows no detail. A short `ApiProblem` for status 500 would be consistent with the other errors.
