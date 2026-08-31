# Changelog

All notable changes to this project are recorded here.

## Unreleased

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
