# Roadmap

Single task tracker for BillMinder for PC. Items are ordered by priority within each block.

## P0 — The reminder layer

Without this the app is a spreadsheet with a nice theme. This is the product.

- [ ] **Tray residency.** Compose Desktop `Tray` with a due-count badge, a hover tooltip listing today's bills, and a context menu that can mark the next bill paid without opening the window. Closing the window hides to tray rather than exiting.
- [ ] **Reminder scheduler.** Replace AlarmManager with a coroutine timer that recomputes from the cycle engine on wake rather than trusting a fired-at timestamp. Must survive system sleep and resume: Modern Standby will suspend the process, so the scheduler has to reconcile against wall-clock time when it comes back rather than assume its timer stayed accurate.
- [ ] **Windows toast notifications with action buttons.** Mark Paid, Snooze, and Open. A JVM process cannot raise a real Action Center toast on its own: it needs an AppUserModelID on a Start menu shortcut plus a COM activator for the buttons. Plan is a small .NET sidecar shipped inside the jpackage app image, using `ToastNotificationManagerCompat` which registers the activator for unpackaged apps, talking to the JVM over a named pipe. Fallback if that stalls: tray balloon plus an always-on-top due window, which is a port of the Android full-screen alarm screen.
- [ ] **Launch at logon.** Task Scheduler registration so reminders fire whether or not the app was opened. Register with battery flags off and `WakeToRun` on, otherwise Modern Standby silently defers the task with no missed-run catch-up.
- [ ] **Escalation.** Cascading reminders at 4 hours, 24 hours, and overdue, matching the Android behaviour.

## P1 — Finish the core surfaces

- [ ] **Add and edit bill.** Form covering every field the model already carries: category, recurrence, variable amounts, tags, currency, payment URL, split payees.
- [ ] **Bill detail.** Payment history, lifetime spend, next occurrences, and the mark-paid dialog with custom amount and confirmation number.
- [ ] **Calendar page.** Full month grid with bills rendered inside the day cells, not as dots. A side agenda for the selected day. Year view.
- [ ] **Insights page.** Spending by category, twelve-month cash-flow projection, and forecast. Port `CashFlowProjection` from the Android app once `CurrencyConverter` is ported alongside it.
- [ ] **Settings page.** Reminder timing, startup behaviour, data folder, theme.
- [ ] **Search, sort, and category filter** across the ledger.

## P2 — Earn the desktop

Things a phone app structurally cannot do. This is where the app stops being a port.

- [ ] **Keyboard-first entry.** A quick-add bar that parses `Netflix 22.99 monthly 18th`, payee autocomplete, and full tab-through navigation.
- [ ] **Multi-select and bulk edit.** Shift a due date, change a category, or mark paid across a selection.
- [ ] **Master-detail layout** with resizable panes and persisted column widths.
- [ ] **Print and PDF.** A month's bill schedule, a payee statement, and a year-end summary.
- [ ] **Import and export.** CSV first, then OFX and QFX, which Money Manager Ex notably lacks. Column mapping with learning, ported from the Android app.
- [ ] **Import from BillMinder for Android.** Read the phone's JSON export directly. Needs a round-trip test fixture built from a real export so schema drift between the two apps is caught by the build rather than by a user.
- [ ] **ICS feed.** A subscribable calendar of due dates that lands in Outlook or Google Calendar. Nobody in this space does it properly.
- [ ] **Multi-window.** Pop a bill's history out beside the ledger.

## P3 — Sync and distribution

- [ ] **Phone to desktop sync over the local network.** QR pairing, no account and no cloud. Append-only change log with per-device IDs and last-writer-wins per field. This is the headline differentiator: Actual Budget needs a Docker sync server and Wallos needs a VPS.
- [ ] **App lock.** Windows Hello through the same sidecar as notifications, with a PIN fallback. Note the Android app's PIN hashing is flagged as too weak upstream, so do not port that part as-is.
- [ ] **Encrypted receipt attachments.** The AES/GCM plumbing ports directly; only key provisioning changes, from AndroidKeyStore to DPAPI or a PKCS12 store.
- [ ] **Receipt OCR.** `ReceiptOcrParser` is pure and ports as-is. The MLKit half is replaced by Tesseract or `Windows.Media.Ocr`.
- [ ] **Installer polish.** Signed MSI, an icon, and an update check. GitHub Releases only, built locally. No winget manifest, ever.
- [ ] **Portable mode.** A marker file beside the executable that redirects the data directory, so the app can live on a USB stick.

## Known issues

- The MSI is 101 MB. That is a bundled JRE plus Skia, so some of it is unavoidable, but
  `packageReleaseMsi` runs ProGuard and the `modules(...)` list in `desktop/build.gradle.kts` was
  written conservatively. Both are worth revisiting before the first release.
- `AppPaths` has no portable-mode override yet, so the data directory is always under `%LOCALAPPDATA%`.
- The ledger has no empty-state action. It tells you there are no bills but offers no way to add one.
- Sidebar sections other than Bills are placeholders.
- No logging yet. `AppPaths.logFile` is defined and unused.
