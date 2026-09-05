# Blocked Roadmap Items

Items that cannot be finished in this workspace. Each says what is done, what remains, and exactly
what would unblock it.

## Windows toast notifications with action buttons

Moved here 2026-09-05.

**What already shipped instead.** The item's own sanctioned fallback is built and released: a tray
balloon plus an always-on-top reminder pane carrying Mark Paid, Snooze 1 hour, Snooze until
tomorrow, and Dismiss, with the dismissal cascade behind it (commits `827ba49`, `f1fcfad`).
Reminders reach the user today. What remains is the real Action Center toast, whose buttons can be
answered without a window taking focus.

**Original item text, unchanged:**

> **Windows toast notifications with action buttons.** Mark Paid, Snooze, and Open. A JVM process
> cannot raise a real Action Center toast on its own: it needs an AppUserModelID on a Start menu
> shortcut plus a COM activator for the buttons. Plan is a small .NET sidecar shipped inside the
> jpackage app image, using `ToastNotificationManagerCompat` which registers the activator for
> unpackaged apps, talking to the JVM over a named pipe. Fallback if that stalls: tray balloon plus
> an always-on-top due window, which is a port of the Android full-screen alarm screen. Research
> 2026-08-31: evaluate SnoreToast (KDE's LGPL toast exe, buttons reported over exit code or named
> pipe) and kdroidFilter's ComposeNativeNotification before building the sidecar; both are lighter,
> though the latter's button activation is unverified. jpackage does not stamp the AppUserModelID on
> its shortcut, so stamping it post-install is needed on every route.

**Why it is blocked.** Its acceptance is that the buttons work. Confirming that means raising a real
toast on the signed-in desktop and clicking its buttons. The standing rule for this machine is that
GUI validation runs in an isolated virtual monitor, a separate user session, or headless, and never
against the developer's active display; no isolated Windows session with a live Action Center is
available here. Everything else in this repo is verified offscreen through `ImageComposeScene`,
which cannot render a shell notification. Shipping the native path unverified would mean claiming a
reminder route works when nobody has seen it fire.

**A second, softer blocker:** an unpackaged app only gets toasts once an AppUserModelID is stamped on
a Start menu shortcut, which exists only after an MSI install. A Gradle run cannot produce the
conditions the feature needs, so even a manual check requires installing the packaged build first.

**Route to take when it is unblocked.** Prefer protocol activation over the COM activator: register a
`billminder4pc:` scheme under `HKCU\Software\Classes`, give each toast button
`activationType="protocol"` with a URI such as `billminder4pc:markpaid?bill=<id>&cycle=<iso-date>`,
and let the existing `SingleInstanceGuard` activation socket forward the URI to the running process.
That removes the .NET sidecar and the COM registration from the design entirely; the sidecar is only
needed if protocol activation proves unable to carry the payload. The toast XML itself can be raised
from Windows PowerShell 5.1 through `Windows.UI.Notifications`, which PowerShell 7 cannot do.

**What would unblock it:** an isolated Windows session (second user account, VM, or virtual monitor)
with the MSI installed, where a toast can be raised and its buttons clicked without touching the
developer's display. Alternatively, the owner confirms they will click through the check themselves
on a build handed to them.
