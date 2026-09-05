# Changelog

All notable changes to this project are recorded here.

## Unreleased

### Added
- Reminders now reach you. A due or overdue bill raises a tray balloon and an always-on-top reminder pane naming the bill, its amount, and how the due date sits against today.
- The reminder pane settles the bill in one click, snoozes for an hour or until tomorrow, or dismisses. A variable-amount bill opens the amount form instead of recording its estimate.
- A snooze is held against the wall clock, so one taken before the machine sleeps fires when it wakes rather than an hour of running time later.
- Reminders withdraw themselves. Paying a bill anywhere in the app, or deleting it, clears any reminder still waiting for that cycle.
- A dismissed reminder for an unpaid bill comes back once after four hours and once a day after that first dismissal, then stops asking. Both follow-ups are measured from the original dismissal, so pushing the first one away late doesn't push the last one a further day out. An overdue reminder doesn't cascade, and settling the bill ends the sequence immediately.

- Start when I sign in works. It registers a per-user Task Scheduler entry with the battery conditions switched off, which is what otherwise stops a task from ever running on a machine that uses Modern Standby. Turning it off removes the entry, and the checkbox reflects the task that actually exists rather than what was last clicked.

- Reminders count back from the last day a bill can actually be paid. A due date on a Saturday, a Sunday, or a US federal holiday is reminded against the business day before it, so a "one day before" reminder for a Sunday bill arrives on the Friday rather than the Saturday.
- Two privacy switches in Settings. Hide amounts blanks every figure in the window, which is what you want on a shared or screen-shared desktop. Private notifications keeps the bill's name and its amount out of tray alerts, since those sit on the lock screen and stay in the notification history. Both use the same wording as the phone app.
- The app keeps a rolling set of database backups, one a day, seven deep. Each is verified by reopening it and reading from it, and one that won't open is thrown away rather than counted, so the rotation can't quietly fill up with unusable files. Settings lists the recent ones and can put one back, copying aside whatever it replaces first.
- A bill marked as paying automatically gets one notice on the day the money leaves, so you can check it went through. No lead-up reminder, no second reminder, no overdue notice, and dismissing it ends it instead of starting the four-hour cascade. Bills you pay by hand are unchanged.

### Fixed
- The calendar resolved occurrences in the system time zone while the ledger used the app's, so the two could disagree about which day a bill fell on.
- Total due counts every outstanding cycle. A bill three months in arrears owed one month's amount on the header and now owes three. The ledger header also shows what the calendar month bills in total, paid or not.

### Changed
- Reminder events used to be written to the log and nowhere else.
- The Start-when-I-sign-in checkbox used to be permanently disabled. In a development build it still is, because there is no installed executable to point the task at, and the row now says so.

## [0.2.1] - 2026-08-31

### Fixed
- Release packaging now preserves Room's generated database implementation, so the installed app opens instead of stopping during database startup.
- MSI packaging now opens and queries a temporary database through the optimized runtime. This catches missing Room classes and broken native SQLite bindings before an installer is created.

## [0.2.0] - 2026-08-31

### Added
- A complete four-page desktop workspace. Calendar shows real occurrences inside the month grid, Insights summarizes live bill data, and Settings persists appearance and reminder preferences.
- A basic monthly bill form from the Bills page, with category, first due date, amount, automatic-payment state, and the current reminder defaults.
- Dark, light, and system appearance modes, plus compact-layout and launch-page preferences.
- A local ZIP backup action and quick access to the app data folder.
- Offscreen render coverage for every page, the add-bill form, calendar payment, theme selection, and the variable-payment form.
- A 15-second wall-clock reminder scheduler now rebuilds events from current bill and payment data after sleep or clock changes. It emits the configured reminders once per unpaid cycle. Overdue checks use the same path.

### Changed
- The Bills page now follows a compact desktop ledger layout with a date column, payment status controls, a grouped attention row, and a three-part month summary.
- Reminder time and the overdue policy now feed the live scheduler. Closing behavior follows the saved tray preference.

### Fixed
- The open ledger now rolls over at midnight without waiting for a database change, so due groups and relative dates stay current overnight.
- Sample bills are now limited to a genuine first run and cannot return after the ledger is emptied.
- Quick pay now asks for the actual amount on variable bills instead of recording the saved estimate; fixed bills remain one click.
- A second launch now activates the existing window and exits before opening another database connection.
- Startup and write failures now appear in the app and are written to durable log files; corrupt databases open a recovery screen instead of killing the process.
- Closing the main window now keeps BillMinder in the Windows tray. The tray shows the actionable due count, lists today's bills, restores the window, and can settle the next fixed bill.
- Tray quick pay opens the existing exact-amount panel for a variable bill instead of recording its estimate.

## [0.1.0] - 2026-08-31

First scaffold. The app builds, runs, and reads and writes a real database.

### Added
- Three-module Gradle build on Kotlin 2.4.10, Compose Multiplatform 1.12.0, and Room 3.0.2.
- `core` module holding the recurrence engine and the bill, payment, and payee models, ported from BillMinder for Android with its full test suite (27 tests).
- `data` module with the Room schema, DAO, and repository, running on the bundled SQLite driver so the engine version does not depend on the host (8 tests).
- `desktop` module with the midnight ledger theme, a sidebar shell, and a ledger view grouping bills into overdue, upcoming, and paid.
- Mark paid and undo, resolved against the correct billing cycle rather than the calendar date.
- Offscreen Skia render test that doubles as the README screenshot generator, so UI validation never opens a window on the developer's display.
- Sample bills seeded on first run against an empty database only.
- Windows MSI packaging through jpackage, with a Start menu shortcut that a later toast notification layer will need for its AppUserModelID.

### Notes
- The database is version 1 and independent of the Android app's version 7, though the table shapes match field for field so an export maps across cleanly.
- Reminders, the calendar page, and the insights page are not implemented yet.
