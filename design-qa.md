# Design QA

## Comparison target

- Source visual truth:
  - `docs/mockups/v0.2.0/bills.png`
  - `docs/mockups/v0.2.0/calendar.png`
  - `docs/mockups/v0.2.0/insights.png`
  - `docs/mockups/v0.2.0/settings.png`
- Rendered implementation:
  - `desktop/build/screenshots/bills.png`
  - `desktop/build/screenshots/calendar.png`
  - `desktop/build/screenshots/insights.png`
  - `desktop/build/screenshots/settings.png`
- Viewport: 1120 x 760 dp at density 1.0, rendered offscreen through `ImageComposeScene`.
- Source pixels: 1522 or 1523 x 1032 or 1033 depending on the page.
- Implementation pixels: 1120 x 760 for every page.
- Density normalization: each source was cropped to 1508 x 1024, then resampled to 1120 x 760. Each normalized source and implementation capture was joined on one 2240 x 760 canvas.
- State: dark theme, Monday August 31 2026, fixed UTC clock, six seeded bills, one paid bill, and one overdue bill.

## Full-view comparison evidence

- `desktop/build/design-qa/bills-comparison-final.png`
- `desktop/build/design-qa/calendar-comparison-final.png`
- `desktop/build/design-qa/insights-comparison-final.png`
- `desktop/build/design-qa/settings-comparison-final.png`

Each half remains at its full 1120 x 760 pixel size in the combined evidence. Focused crops were not needed because headings, table text, chart labels, borders, icons, and controls stayed readable at original detail. Interaction-only states without a source counterpart were also checked in `desktop/build/screenshots/add-bill.png`, `desktop/build/screenshots/variable-payment.png`, and `desktop/build/screenshots/bills-light.png`.

## Findings

- No actionable P0, P1, or P2 differences remain.
- [P3] The source mockups carry a faint luminous background treatment. The implementation uses flat navy tokens. This follows the design brief's no-gradient direction and keeps native surfaces consistent.
- [P3] Native Compose text rasterization and Material icon stroke weight differ slightly from the rendered mockups. Family, hierarchy, size, weight, and alignment remain equivalent.
- Expected data difference: Calendar includes the seeded paid Car insurance occurrence, which the source mockup omitted. Keeping real ledger data is preferable to hiding it for the capture.
- Expected state difference: Start when I sign in is visibly disabled until the Windows reminder service is installed. The source showed it enabled, but the implementation does not present a non-working control as active.

## Required fidelity surfaces

- Fonts and typography: the implementation uses the platform sans-serif stack with compact desktop sizes, semibold headings, readable small labels, and no clipping. The visual hierarchy follows the source on all four pages.
- Spacing and layout rhythm: the 196 dp sidebar, 28 dp page margins, 10 dp section gaps, table density, card borders, 8 to 10 dp radii, and grouped content match the source structure. No persistent control is clipped at 1120 x 760.
- Colors and tokens: navy surfaces, vivid blue navigation, mint success, amber due state, and coral danger state map cleanly to the source. Light mode uses darker semantic status colors to preserve contrast.
- Image quality and asset fidelity: the source contains no photos, logos, or decorative raster assets. The implementation uses the existing wordmark text and the Material icon family. Data charts are native UI visualizations rather than replacement artwork.
- Copy and content: page titles, summaries, section labels, amounts, due states, settings labels, and local-data language are coherent and consistent with the source. Product constraints are labeled instead of hidden.

## Interaction and accessibility checks

- Fixed bill payment completes from the Bills page in one click.
- Variable bill payment opens the amount form with the saved estimate prefilled.
- Add bill opens the monthly entry form.
- Calendar payment settles the selected occurrence.
- Theme selection updates the saved preference, and a fresh store reload retains the setting.
- Dark and light captures were inspected. Semantic success, warning, and danger text remains readable in both themes.
- Every visible primary control uses a native clickable surface with text or a content description. The native desktop app has no browser console.

## Comparison history

### Pass 1

- [P1] Bills rows lacked the source's due-date and status-column structure. Fixed by adding the month/day block, compact bill metadata, amount column, and square paid control. Post-fix evidence: `desktop/build/design-qa/bills-comparison-final.png`.
- [P1] Calendar day cells showed only bill names. Fixed by adding amount and paid, overdue, due-today, or upcoming state text. The agenda gained the source's date block and square payment control. Post-fix evidence: `desktop/build/design-qa/calendar-comparison-final.png`.
- [P2] The six-month chart consumed too much vertical space and had no scale. Fixed with a bounded chart region, a five-label value axis, a reference guide, and month plus year labels. Post-fix evidence: `desktop/build/design-qa/insights-comparison-final.png`.
- [P2] Direct dark-theme status colors were too light on the optional light theme. Fixed with theme-aware success, warning, and danger tokens. Post-fix evidence: `desktop/build/screenshots/bills-light.png`.

### Pass 2

- Rechecked all four normalized side-by-side captures. No actionable P0, P1, or P2 differences remain.

## Implementation checklist

- [x] Shared visual tokens and desktop shell
- [x] Bills, Calendar, Insights, and Settings parity
- [x] Main page actions and settings persistence
- [x] Same-viewport dark-theme captures
- [x] Light-theme contrast capture
- [x] Post-fix combined comparisons

## Follow-up polish

- A custom font package could narrow the final rasterization difference, but the current native stack is crisp and avoids an unnecessary bundled asset.

final result: passed
