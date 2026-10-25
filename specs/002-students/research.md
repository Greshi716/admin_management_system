# Research: Students and Class Records (F1)

F0 decisions (sessions, CSRF, audit, table framework, locking, dates `dd-MM-yyyy`, tests) apply unchanged.
Only new choices are recorded here.

## R1. Photos stored in the database

- **Decision**: table `student_photos` (one row per student: `bytea`, content type, size, updated time). `students.photo_version` (bigint, null = no photo) is set from the update time. The list and profile read `photo_version`, never the bytes. `GET /api/students/{id}/photo?v={photo_version}` serves the bytes with `Cache-Control: private, max-age=31536000, immutable`; the changing `v` busts the cache on replace.
- **Rationale**: one backup holds everything (owner decision); the separate table keeps photo bytes out of every entity load and list query; about 30 KB per photo (400 px JPEG) means 10,000 students is about 300 MB.
- **Mapping and size to verify**: map `content` as a plain `byte[]` on a `bytea` column (not a large object) and check that `ddl-auto=validate` accepts it; the 30 KB figure is an estimate to be measured with sample phone photos, and it adds to the daily backup size.
- **Alternatives**: lazy `@Basic` column on `students` (Hibernate lazy basic needs bytecode enhancement; a separate table is simpler); files on disk (rejected by owner: second thing to back up).

## R2. Resizing with the JDK, upload limits

- **Decision**: `javax.imageio` decodes JPEG or PNG, the longest side is scaled to at most 400 px, written as JPEG quality about 0.85 (PNG transparency drawn on white). Accept `image/jpeg` and `image/png`, checked by decoding, not by file name. Limit the upload to 5 MB (`spring.servlet.multipart.max-file-size=5MB`) and refuse images over an assumed 25 megapixels, read from the image header before decoding.
- **Rationale**: no dependency; the pixel limit stops a small file that decodes to a huge bitmap from using the 4 GB PC's memory. The 25-megapixel figure is a technical safeguard I chose; it is not in the spec (a phone camera photo is 12 to 16 MP). Change if wanted.
- **Phone photos**: the JDK reader does not apply the rotation tag of a phone JPEG, so a portrait photo may come out sideways (I believe; to be checked in the first tasks). If so, read the tag and rotate before resizing.
- **Alternatives**: Thumbnailator or imgscalr (rejected: new dependency for one call); resize in the browser (rejected: the server must not trust the client).

## R3. GR No. uniqueness, never reused

- **Decision**: the stored GR No. is trimmed. `student_gr_numbers` has one row for every GR No. ever given to a student, key = `lower(trim(gr_no))` as primary key. Registering inserts a row; correcting a GR No. inserts a new row and leaves the old row, so the old number stays reserved (clarified 2026-10-25). `students` has a unique index on `lower(gr_no)`. A duplicate key becomes a field error on GR No. (422).
- **Rationale**: the database enforces "unique" and "never reused" even with two requests at once. `sr_no` comes from a sequence (`student_sr_no_seq`) and a rolled-back save may leave a gap; numbers are never handed out twice.
- **Alternatives**: unique index on `students` only (rejected: a corrected number would be free again).

## R4. Sensitive fields (Aadhaar, religion/caste)

- **Decision** (all changes are small):
  1. `StudentController` declares the two columns with `asSensitive()`. Existing framework behaviour: masked in the page and in the export, not searched.
  2. **Change**: `TableQueryService` throws a 422 `ValidationException` for a filter or a sort on a sensitive column (today it accepts both). The screen shows no filter box or sort arrow for them.
  3. **Change**: add `religionCaste` to `SensitiveMasker.MASKED` so the audit log stores it as last 4 characters; `aadhaar` is already there. Student audit entries use the field names `aadhaar` and `religionCaste`.
  4. Two read endpoints: `GET /api/students/{id}` returns the **masked** values (profile); `GET /api/students/{id}/edit` returns the **full** values (edit form only).  The edit endpoint sends `Cache-Control: no-store`. The list and export never return full values.
  5. Aadhaar: 12 digits, optional, spaces ignored; stored as 12 digits without spaces. Religion/caste: free text, optional.
  6. No logging of request or response bodies for these endpoints; entity `toString()` excludes the two fields; validation messages never repeat the value.
- **Rationale**: matches spec FR-010 and constitution VI with the smallest change to F0.
- **Decided**: stored as plain text in the database, no field-level encryption (decision log, 25-10-2026).
- **Alternatives**: a view that returns masked columns only (rejected: logic in SQL, harder to test); encrypting at rest (not asked).

## R5. List columns from the class record, master data only through the public API

- **Decision**: `students` holds plain `Long` ids for standard, division and category. `masterdata.api.MasterDataLookup` gives: the current year (id, name) or none; a check that a division belongs to a year and standard and that standard, division and category exist and are active (inactive is refused for new choices, kept on existing records); and name and order maps for standards, divisions, categories. The list columns Std / Div and Category show the current year's class record, or the student's most recent one when there is none for the current year (FR-008): a correlated subquery on `student_enrollments` picks the row of the current year id, else the row with the greatest `year_start_date` (a column copied from the academic year when the row is created; it is used only to order a student's records, and years cannot overlap, so later edits of a year's dates cannot change the order). A further internal column `recordYearId` carries that row's academic year; the screen compares it with the current year and, when different, shows the year name (from the lookup) in small text. Filters and sorts apply to the row shown. Filters use the ids (dropdowns). Sort by Std / Div and by Category uses a `CASE` over the lookup's maps (standard display order then division name; category name), a few dozen values.
- **First check**: a spike on 10,000 rows is the first task (plan "First tasks"); fallback in the plan.
- **Rationale**: honours "never their tables" and still sorts across the whole result in SQL (FR-032 of F0). Values are rebuilt per request, so a renamed division appears at once.
- **Alternatives**: JPA `@ManyToOne` to master data entities (rejected: couples to their internals); a read view owned by masterdata (possible later; adds a migration); copying names into the enrollment (rejected: goes stale).

## R6. Duplicate-name warning

- **Decision**: `GET /api/students/name-matches?surname=&firstName=&middleName=&excludeId=` returns the students (GR No., name, status) whose three names are equal ignoring capitals and spaces at both ends. The form calls it when the name fields lose focus; the warning never blocks saving. An index on `lower(surname), lower(first_name)` supports it.
- **Rationale**: stateless; works for "Save and add next". Case-insensitive comparison is my assumption (spec says "same name").

## R7. Guarding module dependencies

- **Decision**: one JUnit test reads the `import com.admin.management.*` lines of the main sources and fails if the module graph has a cycle or if a module imports another module's package other than `shared`, the `audit` classes `AuditService` and `AuditExportRecorder`, and `masterdata.api`. Allowed today: `audit -> shared`; `masterdata -> audit, shared`; `security -> audit, shared`; `students -> shared, audit, masterdata.api`.
- **Rationale**: the constitution's rule is enforced by the build, with no ArchUnit dependency. (ArchUnit is the alternative if a dependency is acceptable.)

## R8. Frontend shell

- **Decision**: `shell.component` becomes: top bar in the existing deep purple `accent-deep` (`#4B2FA8`, owner decision), white text (`on-accent`), active tab filled with `accent` (`#6D4AE0`), hover with `accent-strong`; no new colour values and not the prototype hex; a Playwright check measures text contrast of at least 4.5:1 on the bar and on the active tab; with logo, tabs, current-year tag, user name, Change password, Logout; tabs shown only for built features (Students, Setup); a second row of pills appears only under Finance (not built, so not rendered); Setup shows a left list (Master data: Academic years, Standards, Divisions, Student categories; System: Audit log) next to the content; content max width 1280 px centred. Routes keep their paths except that master data and the audit log are reached from Setup. The no-current-year block, password popup and idle sign-out stay in the shell. F0 e2e specs keep their assertions; only selectors that name the sidebar change.
- **Alternatives**: keep the sidebar for Setup only (rejected: the decision log says it is replaced).

## R9. Registration form and entry speed

- **Decision**: one reactive form for register and edit, in tab order, native `select` and `date` controls, autofocus on GR No., errors next to the field, two buttons "Save" and "Save and add next". After "Save and add next" the form resets, focus returns to GR No. and a toast confirms the saved GR No. The 20-student keyboard-only run is a Playwright scenario (quickstart). No extra shortcuts are added.
- **Alternatives**: remember the last standard and division between entries (rejected: the spec says the form is cleared).

## R10. Validation rules (server and form)

- From the spec and the request: Mobile 1 required and 10 digits (optional leading +91 or 0 allowed, stored as 10 digits); Mobile 2 optional, same rule; admission date not after today (school date, `Clock`) and not before date of birth; Aadhaar 12 digits; gender Male, Female or Other; photo JPEG or PNG up to 5 MB. These rules are FR-017. A future admission date is refused (owner decision, 25-10-2026). The other rules are assumptions in the spec, not in the agreement.

## R11. Items not decided by the agreement

All decided since the first plan: audit area `Students`, plain-text Aadhaar, future admission dates refused, most recent class record in the list, full mobile numbers, yearly category shown, History block on the profile.


## R12. History block on the profile (FR-019)

- **Decision**: the audit module gets one public read method, `AuditService.history(area, entities, limit)`, returning entries for a list of record ids (`Student#12`, `StudentEnrollment#40`, ...) newest first, with user name, date, action and the stored (already masked) changes. `StudentService` collects the student's id and the ids of the student's class records and calls it; `GET /students/{id}/history` returns the result. The block is not paged (a student has a handful of entries); at most 200 entries are returned (owner agreed), with a flag `truncated` so the screen can say "Older entries are in the Audit log". Migration `V6` adds `CREATE INDEX ... ON audit_log (entity, at)` so reading by record id does not scan the table.
- **Rationale**: no new table and no duplicate of the audit data; uses the audit module only through its public class (constitution VII). Masking is applied when the entry is written (F0 FR-025), so nothing sensitive can leak when it is read.
- **Notes**: the Export entry of a list is not part of a student's history (it names no student). The 200-entry limit is my safeguard, not in the spec.
- **Alternatives**: a `student_id` column on `audit_log` (rejected: changes the F0 table and its append-only trigger for one screen); querying `audit_log` from the students module (rejected: reads another module's table).

## R13. Error codes for field problems

- **Decision**: a duplicate GR No. is a 409 `ConflictException` carrying the field `grNo`, as F0 does for overlapping academic-year dates; empty or invalid fields, a division outside the standard, or a future admission date are 422 with field errors. The form shows both next to the field.
- **Rationale**: one convention across F0 and F1 (plan review M5).
