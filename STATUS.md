# Status
Updated: 2026-10-05

## State
PRs #1–#4 are merged into master: review fixes, design P1, Ecowitt auth + status, 16dp corners, and
the "Vento + Maré" 4x2 widget (Windguru wind + tabuasdemare tide, selectable places, Ponta Verde by
default). Wind + tide was checked on the S25 with live data and no crashes.
Branch `fix/review-bugs-5-8` fixes review bugs 5–8:
- Legacy plain-text credentials go to every Ecowitt widget and are then deleted (no one-shot flag).
- The `SecurePrefs` keystore fallback writes to a separate `*_unencrypted` file.
- The loading spinner uses `partiallyUpdateAppWidget`; the tap handler is set before the fetch, so it
  survives errors (all 6 providers).
- Ecowitt responses are closed via `use {}`.
43 unit tests pass.

## Blockers
- None.

## Next
1. On the S25: check the spinner and tap-to-refresh on an Ecowitt widget, then review + merge the PR.
2. Design P2: one shared wind colour scale for speed and gust.
3. P3: one UI language via strings.xml; Chart cleanup.
