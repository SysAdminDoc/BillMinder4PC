# Roadmap

Single task tracker for BillMinder for PC. Items are ordered by priority within each block.

## P1: Finish the core surfaces

- [ ] **Add and edit bill.** A basic monthly add form landed in v0.2.0. Editing and advanced fields remain: recurrence, variable amounts, tags, currency, payment URL, and split payees.
- [ ] **Bill detail.** Payment history, lifetime spend, next occurrences, and the mark-paid dialog with custom amount and confirmation number.
- [ ] **Calendar page.** The month grid and selected-day payment strip landed in v0.2.0. A year view remains.
- [ ] **Insights page.** Category spending, payment status, and a six-month outlook landed in v0.2.0. Extend this to twelve months and port `CashFlowProjection` with `CurrencyConverter`.
- [ ] **Settings page.** Reminder time, due-day and overdue controls, tray behaviour, data folder, theme, and layout density landed in v0.2.0. Startup registration and the per-bill reminders overview remain. Status 2026-09-05: startup registration shipped; the per-bill reminders overview is what is left.
- [ ] **Search, sort, and category filter** across the ledger.

## P2: Earn the desktop

Things a phone app structurally cannot do. This is where the app stops being a port.

- [ ] **Keyboard-first entry.** A quick-add bar that parses `Netflix 22.99 monthly 18th`, payee autocomplete, and full tab-through navigation.
- [ ] **Multi-select and bulk edit.** Shift a due date, change a category, or mark paid across a selection.
- [ ] **Master-detail layout** with resizable panes and persisted column widths.
- [ ] **Print and PDF.** A month's bill schedule, a payee statement, and a year-end summary.
- [ ] **Import and export.** CSV first, then OFX and QFX, which Money Manager Ex notably lacks. Column mapping with learning, ported from the Android app. ofx4j 1.38 parses both SGML OFX 1.x and Quicken's QFX quirks.
- [ ] **Import from BillMinder for Android.** Read the phone's JSON export directly. Needs a round-trip test fixture built from a real export so schema drift between the two apps is caught by the build rather than by a user. The export's version field is a hardcoded 5 that nothing validates, payees and settings are absent from it, and payments must be keyed by cycleKey rather than dueDate.
- [ ] **ICS feed.** A subscribable calendar of due dates that lands in Outlook or Google Calendar. Nobody in this space does it properly. ical4j 4.x, or hand-rolled RFC 5545, since feed generation is line-folded text.
- [ ] **Multi-window.** Pop a bill's history out beside the ledger.

## P3: Sync and distribution

- [ ] **Phone to desktop sync over the local network.** QR pairing, no account and no cloud. Append-only change log with per-device IDs and last-writer-wins per field. This is the headline differentiator: Actual Budget needs a Docker sync server and Wallos needs a VPS. Discovery per the LocalSend protocol (UDP multicast announce, pinned self-signed TLS, certificate-fingerprint pairing). Skip cr-sqlite: the bundled SQLite driver cannot load extensions.
- [ ] **App lock.** Windows Hello through the same sidecar as notifications, with a PIN fallback. Note the Android app's PIN hashing is flagged as too weak upstream, so do not port that part as-is.
- [ ] **Encrypted receipt attachments.** The AES/GCM plumbing ports directly; only key provisioning changes, from AndroidKeyStore to DPAPI or a PKCS12 store.
- [ ] **Receipt OCR.** `ReceiptOcrParser` is pure and ports as-is. The MLKit half is replaced by Tesseract or `Windows.Media.Ocr`.
- [ ] **Installer polish.** Signed MSI, an icon, and an update check. GitHub Releases only, built locally. No winget manifest, ever. Signing options live in the vault note "Windows App Signing & Distribution 2026": Azure Artifact Signing is GA for US and Canada individuals, SignPath is free for OSS but shows SignPath Foundation as publisher, and the update check must verify a signed manifest with a monotonic version floor rather than trusting the download host.
- [ ] **Portable mode.** A marker file beside the executable that redirects the data directory, so the app can live on a USB stick.

## Research-Driven Additions

Added 2026-08-31 from the ecosystem research pass (details and sources in RESEARCH.md). Ordered by priority; these slot in alongside the blocks above rather than replacing them.

- [ ] P1: **Automatic rolling backups with restore.**
  Why: the category's recurring catastrophe is stranded bill history (Prism, return7's BillMinder, Mint); the single-file design is only a selling point when it is snapshotted and restorable.
  Evidence: Apple Community thread 255168571 ("I have lost all of my bill history now 3 times"); the Android roadmap plans the same.
  Touches: `data` module (`VACUUM INTO` through the bundled driver), settings page, `AppPaths`.
  Acceptance: a daily snapshot rotation (keep N) that verifies each snapshot by reopening it read-only, plus a settings restore path that backs up the live file before replacing it.
  Complexity: M

- [ ] P1: **Since-last-run catch-up dialog.**
  Why: a desktop app is closed or asleep most of the time; everything that came due while away needs one non-modal triage surface. GnuCash's Since Last Run assistant is the proven model and Money Manager Ex's modal-per-item cascade is the proven anti-pattern.
  Evidence: GnuCash SLR manual; moneymanagerex #7703.
  Touches: desktop UI, `AppState`, the reminder scheduler.
  Acceptance: launching after several missed occurrences shows one dialog listing them with pay, skip, and snooze per row; dismissing it leaves the ledger fully usable.
  Complexity: M

- [ ] P1: **Business-day shift and configurable reminder time.**
  Why: fixed 09:00 and weekend-blind reminders are the top configurability complaints in this space, and the Android app already ships the holiday calendar.
  Evidence: firefly-iii #4893 (offsets "not configurable"); Wallos #905; `C:\repos\BillMinder\...\data\HolidayCalendar.kt` (pure JVM, tested).
  Touches: `core` (port `HolidayCalendar` with its test), scheduler, settings.
  Acceptance: a reminder for a Saturday due date fires on Friday at the user-chosen time.
  Complexity: S

- [ ] P1: **Privacy mode: hide amounts in the UI and mask toast content.**
  Why: amounts on a shared or streamed desktop screen and in Action Center leak; the Android app ships both masks already.
  Evidence: Android `SecurityPrefs.maskExternalContent` and `LocalHideAmounts`.
  Touches: desktop theme/state, the future toast layer.
  Acceptance: one toggle blanks amounts across the UI; a second replaces toast name and amount with neutral text.
  Complexity: S

- [ ] P1: **Autopay-aware reminder softness.**
  Why: autopay bills need a "verify it went through" notice, not a nag with escalation; manual bills need the hard path. The flag already exists on the model.
  Evidence: SubTrackr's autopay flag; `Bill.isAutoPay`; Android vacation mode only suppresses autopay bills.
  Touches: scheduler/toast layer, `BillsScreen` badges.
  Acceptance: an autopay bill fires a single FYI notice on the due day with no cascade; a manual bill keeps the full escalation.
  Complexity: S

- [ ] P2: **Port the quick-add templates and merchant normalizer.**
  Why: 28 templates and a 400-alias normalizer make entry, autocomplete, and import cleanup free, and they feed the planned keyboard-first bar.
  Evidence: `C:\repos\BillMinder\...\data\BillTemplates.kt` and `MerchantNormalizer.kt`, both pure JVM with tests.
  Touches: `core`, add/edit form, quick-add bar.
  Acceptance: typing "netf" offers Netflix with category and color; imported names normalize the way the Android import does.
  Complexity: S

- [ ] P2: **Port the interchange and year-end exports, with formula-safe CSV.**
  Why: Bluecoins, YNAB, and Actual export already exists on Android, the desktop is where people want files, and the Android CSV writer never neutralized formula prefixes, a defect not to inherit.
  Evidence: `InterchangeExport.kt`, `BackupManager.exportYearEndCsv`; RESEARCH.md security section.
  Touches: `core`/`data` export code, an export surface in settings.
  Acceptance: exports import cleanly into the three targets, and a payee named "=SUM(A1)" lands inert in Excel.
  Complexity: S

- [ ] P2: **Category budgets.**
  Why: budget progress per category answers "is this getting out of hand", is a paid-tier feature in Chronicle, and the Android math is pure and stored outside the schema, so no interchange impact.
  Evidence: `CategoryBudget.kt` (`BudgetMath`, tested) on Android.
  Touches: `core` port, insights page, settings.
  Acceptance: setting a monthly budget for a category shows spent-against-budget progress computed from payment history.
  Complexity: M

- [ ] P2: **Subscription lifecycle: end date, cancellation deadline, trial expiry.** (Paired schema change with the Android app.)
  Why: the strongest open demand across trackers, and a reminder at the cancellation deadline beats one at the renewal date. Requires new Bill fields, so it must land in both repos per the interchange contract.
  Evidence: Wallos #986 (open), SubTrackr README, Chronicle Pro trial tracking; the Android roadmap plans the same lifecycle fields.
  Touches: `Bill` schema in both repos, `CycleEngine`, add/edit form, scheduler.
  Acceptance: a bill with a notice period reminds N days before the cancellation deadline, and "3 payments remaining" renders on the card; the JSON round-trip fixture covers the new fields.
  Complexity: M

- [ ] P2: **Persist window state.**
  Why: a desktop app that forgets its size and monitor reads as a port; Compose 1.12's Window API v2 exposes exactly this.
  Evidence: Compose Multiplatform 1.12.0 release notes (Window/Dialog API v2).
  Touches: `Main.kt`, settings storage.
  Acceptance: relaunch restores size, position, and monitor, clamped to a screen that still exists.
  Complexity: S

- [ ] P2: **Clear the name before the first public release.**
  Why: "BillMinder" was return7, Inc.'s registered iOS trademark in this exact category; the company is gone but the mark may not be.
  Evidence: Apple Community thread 255168571; return7 held the registration.
  Touches: nothing code-side unless a rename follows.
  Acceptance: a TESS search result recorded in the local project notes with a go or no-go decision before v1.0 publicity.
  Complexity: S

- [ ] P2: **Keyboard and screen-reader accessibility pass.**
  Why: nothing on the roadmap covers accessibility; Compose Desktop needs explicit focus order and Windows narration checks, and the ledger currently carries only icon descriptions.
  Evidence: `BillsScreen.kt` (contentDescription on the check button only); Compose Desktop accessibility docs.
  Touches: desktop UI.
  Acceptance: the full ledger is operable by keyboard alone, and mark-paid announces its state change to Narrator.
  Complexity: M

- [ ] P3: **Flexible recurrence: every N units, last day of month, multiple days per month.** (Paired engine change with the Android app.)
  Why: the strongest recurrence demand across Actual and Wallos; the engine is deliberately identical on both platforms, so this is one shared change with one shared test vector.
  Evidence: Actual schedules docs; Wallos #1143 (open); the CycleEngine port contract in the local project notes.
  Touches: `CycleEngine` in both repos plus both test suites, add/edit form.
  Acceptance: a last-day-of-month bill occurs correctly across February and leap years on both apps from a single shared test vector.
  Complexity: L

- [ ] P3: **Partial payments that do not advance the cycle.** (Paired schema change with the Android app.)
  Why: rent shares and split payments need a cycle to stay outstanding with a remaining balance; today any payment settles the cycle outright.
  Evidence: Chronicle manual (partial payments); the unique `(billId, cycleKey)` REPLACE semantics in `BillDao.kt`.
  Touches: payment schema and settle logic in both repos, detail dialog.
  Acceptance: paying half leaves the cycle outstanding showing the remainder; paying the rest settles it; the round-trip fixture covers it.
  Complexity: L

### Added 2026-09-05 (cross-platform parity pass)

This pass compared this repo against the Android sibling file by file. Details, evidence, and the full drift list are in RESEARCH.md.

Notes on existing items, so they are not re-filed as new ones:

- **"Import from BillMinder for Android"** is pointed at the wrong format. The phone app no longer writes a JSON export; `BackupManager.kt` there has `exportBundle` (`.bmbak`) plus an import-only `importLegacyJson`. Re-point this item at `.bmbak`: an encrypted AES-256-GCM container over a ZIP holding `manifest.json` with per-entry SHA-256, `data.json` with bills, cycle-keyed payments, payees and seven preferences, and `receipts/<uuid>.bin`. The spec is `docs/BACKUP_FORMAT.md` in that repo. Reading a legacy JSON file stays worth keeping as a secondary path for old exports, but it is no longer the interchange story.
- **`BillCycles` was never fully ported.** The Android copy owns `rangeSnapshot()` and `currentCycles()`, the single path every surface there uses for totals, and this copy has neither. The arrears and month totals were fixed on 2026-09-05 by counting occurrences in `AppState` directly, which was the smaller change. Porting `rangeSnapshot` with its `CycleRangeSnapshot` type is still the parity move, and it carries the currency-conversion hook the twelve-month insights item needs.
- **"Subscription lifecycle", "Flexible recurrence", and "Partial payments"** are all paired changes, and none of them can start until this database can migrate. See the migration item below.
- **"Port the quick-add templates and merchant normalizer"**: hold the templates half. All 28 entries in the Android `BillTemplates.kt` are dead code there; its editor hardcodes a separate six-item grid. Porting them now copies dead code. `MerchantNormalizer` is live and worth porting immediately.

#### P1

- [ ] P1: **Mirror the parity contract and enforce it at build time.**
  Why: `core/.../model/Bill.kt` calls the schema an interchange contract in a comment, `CLAUDE.md` calls the ported tests a drift detector, and neither is checked by anything. `BillCycles` diverged from the Android copy without anyone noticing, and this repo has also accumulated inherited dead API that copying source moves along silently.
  Evidence: 2026-09-05 diff. `ResolvedBillCycle`, `CycleRangeSnapshot`, `currentCycles()` and `rangeSnapshot()` exist only on Android; `paidKeys()` and `unpaidOccurrences()` only here. Inherited but unreferenced here: `SortMode`, `BillCategory.fromLabel`, `PayeeDraft`/`PayeeMath`, `CycleEngine.parseCycleKey`, `CycleEngine.cycleKeyForInstant`, `Format.monthLabel`, `AppPaths.attachmentsDir`, `BillDao.observePayees`/`allPayees`/`payeesFor`, `BillRepository.observePayees`/`updateBill`/`deleteBill`, and a `kotlinx-serialization-json` dependency with no `@Serializable` in the module.
  Touches: a tracked `PARITY.md` listing every mirrored file with an owning repo and a normalized SHA-256; a `parityCheck` Gradle task that strips the package declaration and trailing whitespace before hashing; the same file and task in the Android repo.
  Acceptance: `gradlew parityCheck` passes on a clean tree and fails by name when one line of `core/.../cycle/CycleEngine.kt` changes without the manifest; the manifest records an owning repo per file so this repo's own originals (the day-change signal, `AppLogger`, the light theme) are not reported as drift.
  Complexity: M

- [ ] P1: **Add Room migration infrastructure before the first paired schema change.**
  Why: the database is `version = 1` with zero `Migration` classes, no `addMigrations(...)`, and no destructive fallback. The first schema bump hard-fails into the recovery screen, which offers no repair or restore action. Every paired feature on this roadmap is a schema bump.
  Evidence: `data/.../BillDatabase.kt`; the exported `data/schemas/...BillDatabase/1.json`; `StartupRecoveryScreen.kt` offers only Close and Open data folder. The Android repo tests migrations from every shipped schema version and cites a competitor's "data reset after update" report as the reason (https://github.com/isaacsa51/Minus/issues/153).
  Touches: an `ALL_MIGRATIONS` list wired into `DatabaseFactory.open`, a migration test class modelled on the Android `BillDatabaseMigrationTest`, a v1 fixture builder, and a snapshot-before-migrate step so a failed upgrade is recoverable.
  Acceptance: a deliberate v1-to-v2 migration applies on an existing database with rows intact and is covered by a test that starts from a hand-built v1 fixture including indices; opening a database written by a newer version reports the situation and leaves the file untouched; a mid-migration failure leaves the pre-migration snapshot in `backups/`.
  Complexity: M

- [ ] P1: **Write `.bmbak` as well as read it, with the Android validation intact.**
  Why: the existing import item only moves data one way. `AppState.exportBackup()` writes a ZIP of a raw SQLite snapshot plus `preferences.properties` with no manifest, version stamp, or checksum, so nothing this app produces can be opened on the phone, and a corrupted backup cannot be detected before it is restored.
  Evidence: `AppState.exportBackup()`; `SettingsScreen.kt:273-278` disabled import control; the Android `docs/BACKUP_FORMAT.md` and `data/BackupBundle.kt`, whose validation covers the authenticated header, exact schema, entry-path pattern, zip-slip containment, per-entry byte count and SHA-256, foreign-key integrity, cycle uniqueness, and per-field bounds.
  Touches: a `:core` or `:data` port of the platform-free container, validator, and payload (the Android repo has a paired item to extract exactly that half), a passphrase prompt in Settings, the export and import controls, and the parity manifest.
  Acceptance: a `.bmbak` written here opens on the phone with bills, payments, payees, and settings matching the source graph exactly, and the reverse holds; a truncated, tampered, or wrong-schema file is refused with the same message on both sides and nothing is written; the rolling-backup item's unencrypted snapshot stays a separate format and is documented as such.
  Complexity: L

- [ ] P1: **Persist and bound the reminder dedupe set.**
  Why: `ReminderEventId(billId, cycleDate, kind)` lives in a plain in-memory set that is never written to disk and never pruned. Today that only wastes memory because nothing is delivered. The moment toasts ship, a restart re-arms every identity and re-fires reminders the user already dismissed, and a long-running tray process leaks one entry per event forever.
  Evidence: `desktop/.../ReminderScheduler.kt` `delivered` set; `CLAUDE.md` records the identity rule and says to keep it when adding toast delivery.
  Touches: `ReminderScheduler.kt`, a small delivered-events store beside `preferences.properties` or a table in the database, and a retention rule that drops entries older than the lookback window.
  Acceptance: a reminder delivered before a restart is not delivered again after it; entries older than the retention window are dropped on each reconcile so the set stays bounded across a simulated year; the existing backward-clock-jump test still passes unchanged.
  Note (2026-09-05): reminder delivery shipped, so this is now a live defect rather than a latent one, and it is bigger than the `delivered` set. `ReminderAlertQueue`'s snoozes, dismissals and cascade level are process memory too, and `ReminderScheduler.reconcile` anchors `lastCheckedAt` on its first tick and never emits an instant that precedes app start. Dismiss a reminder at 21:00, sign out, sign back in at 08:00, and both follow-ups and the original are gone. Persisting the delivered set alone will not fix that; the queue's state and a replay window have to land with it.
  Complexity: M

- [ ] P2: **Check the sign-in task's target and enabled state, not just its name.**
  Why: `isRegistered` only asks whether a task of that name exists. A task that is disabled in taskschd.msc, or that still points at a previous install path, reads as registered, so Settings shows a ticked box for something that will not start the app.
  Evidence: `StartupRegistration.isRegistered` runs `schtasks /query /tn <name>` and reads only the exit code; `register` is called only from the checkbox, so `<Command>` is never refreshed after an install path changes. Found by the 2026-09-05 adversarial review of `b2e812a`.
  Touches: `StartupRegistration` (parse `/query /xml` or `/v /fo list` for the action path and the enabled state), `AppState.reconcileStartAtLogin`, tests.
  Acceptance: a task pointing at a stale executable is reported as not registered and is rewritten on the next enable; a task disabled outside the app reads as off; the reconcile also runs when the window regains focus, not only at construction.
  Complexity: S

- [ ] P1: **Pass the injected zone into the calendar.**
  Why: `CalendarScreen` resolves occurrences with the system default zone while `AppState` uses its injected one, so under a non-default zone the grid and the ledger disagree about which day a bill falls on. The whole point of the cycle-key design is that both apps agree on the date.
  Evidence: `desktop/.../ui/CalendarScreen.kt:75` and `:354` call `CycleEngine.occurrencesInRange` and `dueInstant` with no zone argument.
  Touches: `CalendarScreen.kt`, the zone plumbed through from `AppState`, and a test that renders the calendar under a non-default zone.
  Acceptance: with the clock and zone both injected to a non-default zone, the calendar cell holding a bill matches the ledger's due date for that bill; a test fails if the zone argument is dropped again.
  Complexity: S

#### P2

- [ ] P2: **Reconcile the shared palette with the phone.**
  Why: the two apps are meant to read as one product and the accent already differs, while the stored default colour matches neither. A bill created here and opened on the phone renders outside that app's palette.
  Evidence: `desktop/.../theme/Color.kt:19` `CatBlue = 0xFF338BFF` against the Android `0xFF62A5FF`; `Bill.color` defaults to `0xFF89B4FA` in both; `CategoryColors[0]` is `CatBlue` in both.
  Touches: `theme/Color.kt`, the `Bill.color` default in `core/.../model/Bill.kt`, the same files in the Android repo, the parity manifest, and regenerated README captures.
  Acceptance: one agreed hex per named token in both repos with the palette file in the parity manifest; the `Bill.color` default equals `CategoryColors[0]`; existing bills keep their stored colour because `storedBillColor` is untouched.
  Complexity: S

- [ ] P2: **Make the Insights page tell the truth.**
  Why: three separate defects on one page, and `design-qa.md` currently records no actionable P0, P1, or P2 differences remaining. The month arrows suggest the summary follows them and it does not, the outlook's axis is a fixed list of strings unrelated to the data, and the status ring is invisible in the wrong theme.
  Evidence: `desktop/.../ui/InsightsScreen.kt:63-65` computes scheduled, paid, and remaining from the whole dashboard and ignores `displayedMonth`; `:288` hardcodes the axis labels `$2.4k`, `$1.8k`, `$1.2k`, `$0.6k`, `$0`; `:296-300` pins the guide line at 31% of height; `:217` hardcodes the ring track to `0xFF2A3C55` instead of a theme token.
  Touches: `InsightsScreen.kt` summary computation, an axis derived from the chart ceiling, a theme-aware track colour, and a light-theme screenshot.
  Acceptance: moving the month arrows changes scheduled, paid, and remaining; the axis labels match the data at three different value scales including an all-zero month; the ring track is legible in both themes and the light capture proves it.
  Complexity: S

- [ ] P2: **Anchor the screenshot suite to semantics and golden images.**
  Why: it is the best test asset in the repo and the easiest to fool. Every interaction targets absolute pixel coordinates, so a layout shift silently clicks the wrong thing or nothing, and the assertions are byte-size floors, so a blank or garbled render above 5 KB passes.
  Evidence: `desktop/.../ScreenshotTest.kt` clicks at (1040,42), (1025,568), (1028,697), (878,188), (1025,438) and asserts only `bytes > 5_000`; `design-qa.md` cites comparison images under `desktop/build/` which is gitignored, so the QA pass cannot be reproduced from a clean checkout.
  Touches: `ScreenshotTest.kt` (semantics-based finders in place of coordinates), a committed golden-image directory with a perceptual diff and a tolerance, and `design-qa.md` pointing at tracked evidence.
  Acceptance: moving a control 40 px does not change which control a test clicks; a deliberately blanked page fails the golden diff; the design-QA evidence referenced in the document exists in a clean checkout.
  Complexity: M

- [ ] P2: **Route stored timestamps through the injected clock.**
  Why: `Bill.createdAt` and `Payment.paidAt` default to `System.currentTimeMillis()`, so they cannot be frozen in tests that otherwise inject a `Clock`, and a fixture written under a fixed clock still carries real wall time into any interchange round-trip fixture.
  Evidence: `core/.../model/Bill.kt` defaults on both entities; `AppStateTest` and `ScreenshotTest` inject `Clock.fixed(...)` for everything else.
  Touches: `BillRepository` write paths so the caller supplies the timestamp, `AppState`, `SampleData`, and the paired change in the Android repo since the defaults live in the shared entity.
  Acceptance: a bill added under a fixed clock stores exactly that instant; `SampleData` seeds deterministic timestamps; the change is applied in both repos in the same pass because the entity is mirrored.
  Complexity: S

- [ ] P2: **Delete the orphan toast sidecar binaries.**
  Why: two compiled `.exe` files sit in the working tree with no source, no build wiring, and no code reference. They are untracked because `*.exe` is gitignored, which means they are invisible to review and one careless `git add -f` or packaging change away from shipping.
  Evidence: `desktop/toast-sidecar/bin/Release/net48/BillMinderToast.exe` and `desktop/toast-sidecar/obj/Release/net48/BillMinderToast.exe`; no Gradle task or Kotlin file names them.
  Touches: the `desktop/toast-sidecar/` directory.
  Acceptance: the directory is gone; when the toast route is chosen, the sidecar is re-created with its source tracked and its build wired into Gradle.
  Complexity: S

- [ ] P2: **Correct the ported-test count in the documents.**
  Why: `README.md`, `CHANGELOG.md`, and `CLAUDE.md` all say the ported recurrence engine carries 27 tests, and the figure is used as the evidence that the port is complete. `CycleEngineTest` has 25 methods; the 27 is the whole `:core` module including `PayeeMathTest`.
  Evidence: `core/src/test/.../CycleEngineTest.kt` and `core/src/test/.../model/PayeeMathTest.kt` method counts on 2026-09-05.
  Touches: `README.md`, `CHANGELOG.md` v0.1.0 entry, `CLAUDE.md`.
  Acceptance: every sentence attributes its count to the right scope, and the parity manifest is what the documents point at for completeness rather than a number in prose.
  Complexity: S

#### P3

- [ ] P3: **Give reminder settings a per-bill level, not just a global default.**
  Why: `firstReminderDays` and `dueDayReminder` only affect the add form. Changing them does nothing to bills that already exist and nothing to delivery, so the Reminders card reads as a global setting and behaves as a form default. The phone app is the opposite: per-bill timings with no global default at all. Both apps should offer a global default that per-bill values can override.
  Evidence: `desktop/.../AppPreferences.kt` keys `firstReminderDays` and `dueDayReminder`, consumed only in `BillsScreen.kt` when constructing a new `Bill`; `Bill.reminderTiming` and `Bill.secondReminderTiming` are the per-bill fields both apps already store; the Android scheduler reads only the per-bill fields.
  Touches: the reminders card in `SettingsScreen.kt`, `ReminderPolicy`, the add and edit forms, the existing per-bill reminders overview item, and the matching global-default setting on the phone.
  Acceptance: changing the global default changes when existing bills without an explicit override are reminded; a bill with an explicit timing ignores the global default; both apps agree on which level wins, and the rule is written in the parity contract.
  Complexity: M

## Known issues

- The MSI is 101 MB. That is a bundled JRE plus Skia, so some of it is unavoidable, but
  `packageReleaseMsi` runs ProGuard and the `modules(...)` list in `desktop/build.gradle.kts` was
  written conservatively. Both are worth revisiting before the first release.
- `AppPaths` has no portable-mode override yet, so the data directory is always under `%LOCALAPPDATA%`.
- The add form covers the common monthly case, but edit and advanced bill fields still need their full surface.
- Calendar year view and multi-currency forecast conversion aren't built yet.
