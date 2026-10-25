# CampusFlow UI direction (F0)

Text version of the UI direction agreed from the "CampusFlow F0 Mockups" canvas (sign-in, Academic years with the app shell, audit log). No mockup images are exported, so this file is the design reference.

## Colours
| Token | Value | Use |
|---|---|---|
| page | `#F7F5FD` | page background, table header, disabled fill |
| surface | `#FFFFFF` | cards, top bar, inputs |
| sidebar | `#EDE7FB` | sidebar and sign-in left panel |
| accent | `#6D4AE0` | primary buttons, active navigation, logo mark |
| accent text | `#4B2FA8` | links, current-year tag, outline buttons |
| accent soft | `#ECE6FB` / `#E6DEFB` | tags, avatar, current badge |
| ink | `#1E1833` | main text |
| muted | `#5A5075` | secondary text |
| sidebar text | `#3B3157`, labels `#6A5F8C` | navigation |
| border | `#E6E0F5` | cards, row dividers |
| input border | `#8E84AD` | inputs and buttons (3:1 contrast) |
| error | bg `#FDECEC`, text `#8A1C1C` | wrong sign-in |
| warning | bg `#FFF3DC`, text `#6B4200` | sign-in pause, no-current-year panel |
| area badges | Security `#E8EEFB`/`#2B4A9A`; Master data `#FDF0DC`/`#6B4200` | audit log |
| action badges | Create `#E6DEFB`/`#4B2FA8`; Edit `#E8EEFB`/`#2B4A9A`; Login `#F0EEF5`/`#4F4A63`; Export `#F0E8FB`/`#5B2E9A` | audit log |

Alternative accents offered in the mockup: `#7C3AED`, `#4F46E5`, `#0F766E`. The accent must be set in one place (the `@theme` block in `frontend/src/styles.css`) so it can change for the whole app.

## Type and shape
- Font: Plus Jakarta Sans, Noto Sans Gujarati as fallback. Page title 28px bold, body 14 to 15px, table heading 12px bold uppercase.
- Corners: inputs and buttons 10px, cards 16px, tags fully rounded.
- Controls at least 44px tall (the mockup used 40px for row buttons; use 44px).

## Screens
- **Sign-in**: left panel (lavender) with product name and one line of text; right side has the form (username, password with Show/Hide, Sign in) and a short line "Forgot your password? Contact the developer." States: wrong password (red notice), paused after 5 failures (amber notice, button disabled).
- **App shell**: sidebar (Master data: Academic years, Standards, Divisions, Student categories; System: Audit log), top bar with breadcrumb, current-year tag, user name, Change password and Log out, a modal popup after sign-in while the default password is unchanged ("Change now" primary, "Later" secondary).
- **No current year (blocking)**: when today's date is in no academic year, sign-in lands on Academic years with a prominent amber panel "No academic year covers today. Create one to continue." and the create form open. Other sidebar items are greyed out and not clickable; Change password and Log out stay available. The panel disappears once a year covering today is saved.
- **List page (Academic years)**: title and one line of help, primary "Add academic year", card with search box, row count, Export to Excel, header with sortable columns, a filter row, rows with Edit and Delete (Delete is refused with an "in use" message when the year is referenced) and a "Current" tag on the year that contains today's date; academic years have no Make current action and no inactive status (standards, divisions and categories do have Mark inactive), footer with rows per page and paging.
- **Audit log**: same table; filters for From, To, Today / This month / This year, Area and Action; columns Date and time, User, Area, Action, Record, What changed.
