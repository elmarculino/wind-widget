# Status
Updated: 2026-10-05

## State
PRs #1–#3 (review fixes, design P1, Ecowitt auth + status) are merged into master; all widgets clip
to a 16dp outline. Branch `feature/wind-tide-widget`: new "Vento + Maré" 4x2 widget, the right half
of the Echo Show dashboard. Wind comes from a Windguru station and tide from tabuasdemare.com.br,
both fetched directly with no proxy or keys. Places can be chosen per widget and new ones added,
checked against both sites (Ponta Verde is the default). The widget is reconfigurable. 38 unit tests
pass, including tide parity with echo-show-vento's server.py.

## Blockers
- The wind+tide widget hasn't run on the phone yet (adb device not connected on 2026-10-05).

## Next
1. Install on the S25: add the widget, add a second place, tap to refresh, airplane mode (amber time,
   tide from the day's cache).
2. Review + merge the wind-tide PR.
3. Fix bugs 5–8 (bug 6, plain-text keys, first); design P2 shared colour scale; P3 (UI language).
