# Wind Widget

## Goal
Android home-screen widgets showing real-time and 3-hour wind (speed, gust, direction in knots)
from Ecowitt weather stations, starting with the MiCasa station in São Miguel dos Milagres.
The reading must be trustworthy: it is used to decide on the day's wind conditions, so stale or
fake data must never look live.

## Scope
- In: 5 widget styles (Chart, Bar, Clean, Compact, Modern), saved locations, per-widget
  credentials, periodic + tap refresh, offline fallback to the last real reading.
- Out: non-Ecowitt stations, forecasts, Play Store publishing (TBD), iOS.

## Definition of done
Review of 2026-10-03, bugs (numbers match the review):
- [x] 1. Demo data no longer cached as live / no longer overwrites the real cache
- [x] 2. Updates run inside a WorkManager worker that waits for fetch + render
- [x] 3. Tap-to-refresh forces a network fetch
- [x] 4. Configuring/changing credentials clears the old cache and forces a fetch
- [ ] 5. Legacy-prefs migration covers every existing widget, not only the first one
- [ ] 6. Plain-text legacy API keys removed after migration; `SecurePrefs` fallback never writes plain text to the encrypted file
- [ ] 7. Loading state uses `partiallyUpdateAppWidget`; error path keeps the tap handler (all 5 providers)
- [ ] 8. HTTP responses always closed (`response.use {}`)
- [ ] 9. Modern chart clamps values above 20 kt; guard `size < 2` before dividing by `size - 1`
- [ ] 10. Rounded corners rendered with ARGB_8888 (transparent corners)

Improvements backlog:
- [ ] Drop `updatePeriodMillis` (WorkManager already does 30 min)
- [ ] Cache keyed by station MAC, not by widget (fewer API calls)
- [ ] Timeouts ~10s, at most 1 retry; also retry on 5xx/429
- [ ] Render at 1× density instead of 2×
- [ ] `onDeleted` removes per-widget credentials and cache
- [ ] Missing gust/direction shown as "--" instead of 0 / N
- [ ] Times stored as epoch millis instead of ISO strings
- [ ] Widgets reference a saved `locationId` instead of copying credentials
- [ ] UI strings in `strings.xml`, consistent language
- [ ] One base provider class instead of 5 copies
- [ ] Replace deprecated `security-crypto` 1.0.0 (`MasterKeys`)
- [x] CI runs `testDebugUnitTest`
- [x] Modern chart line auto-fits like Clean
- [x] Rendered picker previews for Modern and Clean (Chart, Bar, Compact still generic)

Design review of 2026-10-03:
- [x] P1: Compact layout fits 2x2; Modern chart clear of the direction circle; Modern legend matches
  the chart; STALE/DEMO shown as an amber badge on every widget; gust always 1 decimal
- [ ] P2: one shared wind colour scale for speed and gust; 16-point compass (ENE, not E)
- [ ] P3: one UI language via strings.xml; Chart cleanup (round Y ticks, arrows off the line,
  gust bars labelled, lighter bottom row); Bar colour legend; short label per saved location

## Links
- README (setup/usage): [README.md](README.md)
- Design notes: [docs/plans/](docs/plans/)
