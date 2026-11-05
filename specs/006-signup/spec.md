# Feature Specification: Demo Signup and HTTP Basic Access

**Feature Branch**: `006-signup`

**Created**: 2026-11-05

**Status**: Draft

**Input**: User description: "Demo signup and HTTP Basic access (F6). A Create account screen and endpoint, enabled only by a setting that is on for the Render demo and off by default; new accounts get the Admin role. Also accept an HTTP Basic Authorization header on /api for curl or Postman, with the same 5-failure pause, audit and JSON 401 as the form sign-in. No schema change."

**Agreement**: none. Signup is not in the product agreement (single Admin, sign-in). **Decisions**: decision log row of 2026-11-05 (amends the one-Admin rule for the demo only). Builds on the F0 sign-in (FR-001 to FR-009, FR-044, FR-045 of `specs/001-foundation`). No financial rule is involved.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Create an account on the demo (Priority: P1)

A reviewer opens the hosted demo, which has no readable first-run password file. On the sign-in page they choose "Create account", enter a name, a username, a password and the same password again, and submit. They are signed in and land on the home page as an Admin.

**Why this priority**: Without it nobody but the developer can enter the demo.

**Independent Test**: With signup turned on, create an account from the sign-in page and check that the home page opens and the account can sign out and sign in again.

**Acceptance Scenarios**:

1. **Given** signup is on, **When** the visitor opens the sign-in page, **Then** a "Create account" link is shown.
2. **Given** valid details, **When** the visitor submits, **Then** the account is created with the Admin role, the visitor is signed in, and the sign-up is recorded in the audit log.
3. **Given** a username that already exists, **When** the visitor submits, **Then** nothing is created and the form says the username is taken.
4. **Given** a password shorter than 8 characters or a confirmation that does not match, **When** the visitor submits, **Then** nothing is created and the form shows what to fix.
5. **Given** signup is off (the default), **When** the visitor opens the sign-in page, **Then** no "Create account" link is shown, the sign-up page redirects to sign-in, and a direct sign-up request is answered as not found.

---

### User Story 2 - Call the API with HTTP Basic (Priority: P2)

A reviewer using curl or Postman sends a request to any `/api` address with an `Authorization: Basic` header holding the username and password of an existing account, with no browser session and no CSRF token.

**Why this priority**: Lets reviewers test the API without the browser, but the demo is usable without it.

**Independent Test**: Request the "who am I" address with a valid Basic header (success), with a wrong password (401), and after 5 wrong attempts (paused).

**Acceptance Scenarios**:

1. **Given** a valid username and password in a Basic header, **When** the request is sent, **Then** it is answered as that user, including write requests, without a CSRF token.
2. **Given** a wrong password or an unknown user, **When** the request is sent, **Then** the answer is a JSON 401 that does not reveal which part was wrong.
3. **Given** 5 failed attempts in a row for a username, by Basic or by the form, **When** another attempt is made either way, **Then** it is refused as paused for 5 minutes (same rule as F0 FR-044).
4. **Given** a request with no Basic header and no session, **When** it is sent, **Then** it is answered 401 as today, and a browser shows no native password prompt.
5. **Given** a request with a session cookie but no Basic header, **When** it is a write without a CSRF token, **Then** it is still refused as today.

---

### Edge Cases

- Two people submit the same username at the same moment: one is created, the other is told the username is taken.
- Leading or trailing spaces in the username are removed before the check, as sign-in does.
- Signup turned on in a school install by mistake: anyone who reaches the address can create an Admin. The setting is off by default and documented as demo only.
- An account created by signup is a normal user: it can change its password and is never deleted.
- A header with another scheme or broken encoding is answered 401, never a server error.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST offer account creation only when the setting `CAMPUSFLOW_SIGNUP_ENABLED` is true. The setting MUST be false when not set, and the Render blueprint MUST set it to true.
- **FR-002**: When signup is off, a sign-up request MUST be answered as not found, and the sign-in page MUST NOT show the "Create account" link.
- **FR-003**: The sign-in page MUST learn whether signup is on from a public "config" answer that holds only that flag.
- **FR-004**: A sign-up MUST take name, username, password and password confirmation. Name and username are required; the username is trimmed.
- **FR-005**: The password MUST be at least 8 characters and MUST equal the confirmation.
- **FR-006**: The username MUST be unique (compared exactly after trimming, as sign-in compares it). A duplicate MUST be refused with a message that the username is taken, and nothing is created.
- **FR-007**: A new account MUST get the Admin role, which stays the only role. No schema or migration change is made.
- **FR-008**: After a successful sign-up the user MUST be signed in with a session, as after a normal sign-in, and the "change the default password" popup MUST NOT be shown (the user chose the password).
- **FR-009**: A sign-up MUST write an audit entry (area Security, who, when, the username and name) in the same transaction as the account. The entry MUST NOT hold the password or its hash. If the entry cannot be saved, the account MUST NOT be created.
- **FR-010**: The password MUST be stored only as a hash and never logged.
- **FR-011**: System MUST accept an `Authorization: Basic` header on `/api` requests and authenticate the request as that user for that request only, with no session needed.
- **FR-012**: A request with a valid Basic header MUST NOT need a CSRF token. A request without a Basic header MUST keep today's CSRF check.
- **FR-013**: Failed Basic attempts MUST count toward the same 5-failures-then-5-minute pause as form sign-in, per username, and a paused username MUST be refused both ways. A successful Basic request MUST reset the count.
- **FR-014**: A refused Basic request MUST answer JSON 401 (or the paused answer) with no `WWW-Authenticate` challenge, so browsers show no native prompt, and MUST NOT reveal whether the username or password was wrong. Inactive users MUST be refused.
- **FR-015**: A failed Basic attempt MUST be audited as a failed sign-in with the username attempted, as the form sign-in does. A successful Basic request MUST update the last login time like form sign-in does.
- **FR-016**: The decision log MUST record that signup amends the one-Admin rule (F0 FR-003) for the demo only.

### Key Entities

- **User (existing)**: name, username, hashed password, role (Admin only), active flag. Signup adds rows; nothing about the shape changes.
- **Audit entry (existing)**: records the sign-up event with the new username and name only.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A new reviewer can go from the demo address to the signed-in home page in under 1 minute without help from the developer.
- **SC-002**: With signup off, 100% of sign-up attempts fail and no account is created.
- **SC-003**: A reviewer can read data from the API with one curl command using only a username and password.
- **SC-004**: No stored or logged record (database, audit log, application log) contains a password or hash from signup or Basic use.
- **SC-005**: After 5 wrong attempts, the 6th attempt by either way is refused for 5 minutes in 100% of test runs.

## Assumptions

- This is for the Render demo, which holds only made-up data (Constitution VI). The school install leaves signup off and keeps its single Admin.
- Anyone who can sign up gets full Admin access to the demo; there is no approval step, email check, captcha or rate limit beyond the existing sign-in pause. Acceptable for a demo; revisit before exposing real data.
- The idle sign-out (10 minutes) and audit logging apply to new accounts like to the first one.
- HTTPS is provided by the host (Render), so Basic credentials are not sent in clear text.
- Username comparison stays exact (case-sensitive) like sign-in today; a case-insensitive rule would need a schema change and is out of scope.
- Out of scope: other roles, a user-management screen, email or password recovery, a signup toggle inside the app.
