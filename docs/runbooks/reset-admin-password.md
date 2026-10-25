# Reset the Admin password (developer procedure)

Use this when the admin has forgotten the password. There is no recovery code and no email reset (FR-045).
Only the developer does this, on the school computer.

## Steps

1. Stop the CampusFlow service (or the `spring-boot:run` process).
2. Start it **once** with the setting `campusflow.reset-admin-password=true`. For example:
   - from source: `cd management` then `.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--campusflow.reset-admin-password=true"`
   - installed: add `--campusflow.reset-admin-password=true` to the start command, or set the environment variable `CAMPUSFLOW_RESET_ADMIN_PASSWORD=true`.
3. Wait for the log line "The Admin password was reset. The new one-time password is in ...". The log shows the file path only, never the password.
4. Open `credentials-first-run.txt` in the application data directory (`campusflow.data-dir`, `./data` by default) and read the `Password:` line.
5. Stop the service and start it again **without** the setting. Leaving it on would reset the password at every start.
6. Give the admin the new password. At the next sign-in the popup asks the admin to change it ("Change now" or "Later").

## What the reset does

- Replaces the Admin's password with a new random one (old password stops working at once).
- Marks the password as not yet changed, so the change-password popup shows again.
- Clears failed sign-in attempts and any 5-minute sign-in pause.
- Writes a Security entry in the audit log (event `reset`). The entry never contains the password.

## The credentials file

- The file is deleted automatically when the admin changes the password.
- If the file is still there after a reset that was not used (the admin remembered the old password after all), delete it by hand.
- Never copy the file into the repository, a chat or an email.

## Check

Sign in with the new password. The audit log (Security area) shows one reset entry.
