# Roadmap

Single task tracker for BillMinder for PC. Items are ordered by priority within each block.

## P0 — The reminder layer

Without this the app is a spreadsheet with a nice theme. This is the product.

- [ ] **Tray residency.** Compose Desktop `Tray` with a due-count badge, a hover tooltip listing today's bills, and a context menu that can mark the next bill paid without opening the window. Closing the window hides to tray rather than exiting.
- [ ] **Reminder scheduler.** Replace AlarmManager with a coroutine timer that recomputes from the cycle engine on wake rather than trusting a fired-at timestamp. Must survive system sleep and resume: Modern Standby will suspend the process, so the scheduler has to reconcile against wall-clock time when it comes back rather than assume its timer stayed accurate. Confirmed 2026-08-31: Windows relative timers exclude S0 sleep time, so a long delay() fires late; use a short tick that compares wall clock and re-anchors on divergence.
- [ ] **Windows toast notifications with action buttons.** Mark Paid, Snooze, and Open. A JVM process cannot raise a real Action Center toast on its own: it needs an AppUserModelID on a Start menu shortcut plus a COM activator for the buttons. Plan is a small .NET sidecar shipped inside the jpackage app image, using `ToastNotificationManagerCompat` which registers the activator for unpackaged apps, talking to the JVM over a named pipe. Fallback if that stalls: tray balloon plus an always-on-top due window, which is a port of the Android full-screen alarm screen. Research 2026-08-31: evaluate SnoreToast (KDE's LGPL toast exe, buttons reported over exit code or named pipe) and kdroidFilter's ComposeNativeNotification before building the sidecar; both are lighter, though the latter's button activation is unverified. jpackage does not stamp the AppUserModelID on its shortcut, so stamping it post-install is needed on every route.
- [ ] **Launch at logon.** Task Scheduler registration so reminders fire whether or not the app was opened. Register with battery flags off and `WakeToRun` on, otherwise Modern Standby silently defers the task with no missed-run catch-up.
- [ ] **Escalation.** Cascading reminders at 4 hours, 24 hours, and overdue, matching the Android behaviour. Note: the Android cascade is dismissal-triggered, and both follow-ups abort if the cycle was paid or changed in the meantime.

## P1 — Finish the core surfaces

- [ ] **Add and edit bill.** Form covering every field the model already carries: category, recurrence, variable amounts, tags, currency, payment URL, split payees.
- [ ] **Bill detail.** Payment history, lifetime spend, next occurrences, and the mark-paid dialog with custom amount and confirmation number.
- [ ] **Calendar page.** Full month grid with bills rendered inside the day cells, not as dots. A side agenda for the selected day. Year view.
- [ ] **Insights page.** Spending by category, twelve-month cash-flow projection, and forecast. Port `CashFlowProjection` from the Android app once `CurrencyConverter` is ported alongside it.
- [ ] **Settings page.** Reminder timing, startup behaviour, data folder, theme. Reminder timing should include the time of day (Wallos #905; Firefly's fixed offsets are a standing complaint there) plus a per-bill reminders overview (Wallos #983).
- [ ] **Search, sort, and category filter** across the ledger.

## P2 — Earn the desktop

Things a phone app structurally cannot do. This is where the app stops being a port.

- [ ] **Keyboard-first entry.** A quick-add bar that parses `Netflix 22.99 monthly 18th`, payee autocomplete, and full tab-through navigation.
- [ ] **Multi-select and bulk edit.** Shift a due date, change a category, or mark paid across a selection.
- [ ] **Master-detail layout** with resizable panes and persisted column widths.
- [ ] **Print and PDF.** A month's bill schedule, a payee statement, and a year-end summary.
- [ ] **Import and export.** CSV first, then OFX and QFX, which Money Manager Ex notably lacks. Column mapping with learning, ported from the Android app. ofx4j 1.38 parses both SGML OFX 1.x and Quicken's QFX quirks.
- [ ] **Import from BillMinder for Android.** Read the phone's JSON export directly. Needs a round-trip test fixture built from a real export so schema drift between the two apps is caught by the build rather than by a user. The export's version field is a hardcoded 5 that nothing validates, payees and settings are absent from it, and payments must be keyed by cycleKey rather than dueDate.
- [ ] **ICS feed.** A subscribable calendar of due dates that lands in Outlook or Google Calendar. Nobody in this space does it properly. ical4j 4.x, or hand-rolled RFC 5545, since feed generation is line-folded text.
- [ ] **Multi-window.** Pop a bill's history out beside the ledger.

## P3 — Sync and distribution

- [ ] **Phone to desktop sync over the local network.** QR pairing, no account and no cloud. Append-only change log with per-device IDs and last-writer-wins per field. This is the headline differentiator: Actual Budget needs a Docker sync server and Wallos needs a VPS. Discovery per the LocalSend protocol (UDP multicast announce, pinned self-signed TLS, certificate-fingerprint pairing). Skip cr-sqlite: the bundled SQLite driver cannot load extensions.
- [ ] **App lock.** Windows Hello through the same sidecar as notifications, with a PIN fallback. Note the Android app's PIN hashing is flagged as too weak upstream, so do not port that part as-is.
- [ ] **Encrypted receipt attachments.** The AES/GCM plumbing ports directly; only key provisioning changes, from AndroidKeyStore to DPAPI or a PKCS12 store.
- [ ] **Receipt OCR.** `ReceiptOcrParser` is pure and ports as-is. The MLKit half is replaced by Tesseract or `Windows.Media.Ocr`.
- [ ] **Installer polish.** Signed MSI, an icon, and an update check. GitHub Releases only, built locally. No winget manifest, ever. Signing options live in the vault note "Windows App Signing & Distribution 2026": Azure Artifact Signing is GA for US and Canada individuals, SignPath is free for OSS but shows SignPath Foundation as publisher, and the update check must verify a signed manifest with a monotonic version floor rather than trusting the download host.
- [ ] **Portable mode.** A marker file beside the executable that redirects the data directory, so the app can live on a USB stick.

## Research-Driven Additions

Added 2026-08-31 from the ecosystem research pass (details and sources in RESEARCH.md). Ordered by priority; these slot in alongside the blocks above rather than replacing them.

- [ ] P0: **Single-instance guard.**
  Why: two launches are two processes on one database file today, and duplicate tray icons plus double toasts the moment tray residency lands. Must precede the tray work.
  Evidence: `Main.kt` takes no lock; standard desktop convention.
  Touches: `Main.kt`, `AppPaths` (lock file or localhost socket).
  Acceptance: a second launch exits after bringing the first instance's window to front, including the known toFront-from-tray workaround (compose-multiplatform #4231).
  Complexity: S

- [ ] P0: **Error surfacing and file logging.** (Covers the "No logging yet" known issue.)
  Why: a corrupt database kills the process with no dialog and no log, and a failed mark-paid vanishes inside `scope.launch`. The global error rule is toast plus log plus crash file, never silent.
  Evidence: `Main.kt` opens the DB unguarded; `AppState.markPaid` swallows exceptions; `AppPaths.logFile` is defined and unused. Android ships a `DatabaseRecoveryScreen` for exactly this.
  Touches: `Main.kt`, `AppState.kt`, `DatabaseFactory.kt`, a small logging util.
  Acceptance: a deliberately corrupted database file at launch produces a recovery dialog and a log entry instead of a dead process; a failing write surfaces in the UI; `sqlite_version()` is logged at startup.
  Complexity: M

- [ ] P0: **Ask the amount when quick-paying a variable bill.**
  Why: the ledger's one-click check writes the estimated amount into payment history for variable bills, which corrupts the exact record the app exists to keep. TimelyBills' broken mark-paid is its top review complaint.
  Evidence: `BillRepository.markPaid` defaults `amount = bill.amount`; trustpilot.com/review/timelybills.app.
  Touches: `BillsScreen.kt`, `AppState.kt`. Complements the P1 bill-detail dialog, which handles the full custom-amount path.
  Acceptance: the quick action on a variable bill opens a prefilled amount prompt; fixed bills stay one-click.
  Complexity: S

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

- [ ] P1: **Month total and true arrears total.**
  Why: "Total due" counts a bill once no matter how many cycles are unpaid, and a month total of all bills is the number Chronicle's own reviews flag as missing.
  Evidence: `AppState.kt` sums `bill.amount` per row; `BillCycles.unpaidOccurrences` exists for this and is unused; Chronicle App Store review.
  Touches: `AppState.kt`, `BillsScreen` summary header, tests.
  Acceptance: a bill three cycles in arrears contributes three amounts to the attention total, and the header shows the calendar month's total billed.
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
  Acceptance: a TESS search result recorded in CLAUDE.md with a go or no-go decision before v1.0 publicity.
  Complexity: S

- [ ] P2: **Keyboard and screen-reader accessibility pass.**
  Why: nothing on the roadmap covers accessibility; Compose Desktop needs explicit focus order and Windows narration checks, and the ledger currently carries only icon descriptions.
  Evidence: `BillsScreen.kt` (contentDescription on the check button only); Compose Desktop accessibility docs.
  Touches: desktop UI.
  Acceptance: the full ledger is operable by keyboard alone, and mark-paid announces its state change to Narrator.
  Complexity: M

- [ ] P3: **Flexible recurrence: every N units, last day of month, multiple days per month.** (Paired engine change with the Android app.)
  Why: the strongest recurrence demand across Actual and Wallos; the engine is deliberately identical on both platforms, so this is one shared change with one shared test vector.
  Evidence: Actual schedules docs; Wallos #1143 (open); the CycleEngine port contract in CLAUDE.md.
  Touches: `CycleEngine` in both repos plus both test suites, add/edit form.
  Acceptance: a last-day-of-month bill occurs correctly across February and leap years on both apps from a single shared test vector.
  Complexity: L

- [ ] P3: **Partial payments that do not advance the cycle.** (Paired schema change with the Android app.)
  Why: rent shares and split payments need a cycle to stay outstanding with a remaining balance; today any payment settles the cycle outright.
  Evidence: Chronicle manual (partial payments); the unique `(billId, cycleKey)` REPLACE semantics in `BillDao.kt`.
  Touches: payment schema and settle logic in both repos, detail dialog.
  Acceptance: paying half leaves the cycle outstanding showing the remainder; paying the rest settles it; the round-trip fixture covers it.
  Complexity: L

## Known issues

- The MSI is 101 MB. That is a bundled JRE plus Skia, so some of it is unavoidable, but
  `packageReleaseMsi` runs ProGuard and the `modules(...)` list in `desktop/build.gradle.kts` was
  written conservatively. Both are worth revisiting before the first release.
- `AppPaths` has no portable-mode override yet, so the data directory is always under `%LOCALAPPDATA%`.
- The ledger has no empty-state action. It tells you there are no bills but offers no way to add one.
- Sidebar sections other than Bills are placeholders.
- No logging yet. `AppPaths.logFile` is defined and unused.
