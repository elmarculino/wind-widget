# Status
Updated: 2026-10-03

## State
Code review done (findings in PROJECT.md › Definition of done). Bugs 1–4 are fixed on branch
`fix/review-data-integrity` (PR open). The branch also carries Marco's earlier uncommitted
work (saved locations, UI). Modern chart now auto-fits like Clean; Modern/Clean have rendered picker
previews. Design review P1 on `fix/widget-design-p1` (stacked on the review PR): Compact layout,
Modern overlap/legend, amber offline badge, gust format, transparent corners (ARGB_8888),
16-point compass, chart clipToOutline. `fix/ecowitt-auth-params` (stacked on P1): keys back as query
params (headers broke every fetch), Modern status badge on the title row. 20 unit tests pass.
Installed on Marco's S25 via USB: live data confirmed on the Modern widget.

## Blockers
- Not tested on a device yet.

## Next
1. Install the APK on the phone; test tap, resize and airplane mode (should show "Offline HH:MM", not demo).
2. Review + merge the PR (3 commits: earlier UI work, review fixes, Modern line + previews).
3. Fix bugs 5–8 (bug 6, plain-text keys, first); design P2 shared colour scale; P3 (UI language).
