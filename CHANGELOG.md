# Changelog

All notable changes to this project are recorded here.

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
