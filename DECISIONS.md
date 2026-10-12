# Decisions
Append-only, newest first.

## 2026-10-05 — Legacy plain-text prefs are migrated on every start, then deleted
**Context:** Old versions kept Ecowitt keys and cache in plain text in `wind_widget_prefs`, the same
file EncryptedSharedPreferences uses. The keystore fallback wrote plain `widget_<id>_*` keys there too.
The one-shot migration (flag `migration_completed_v1`) only moved them to the first widget that
started, and it never deleted the plain text.
**Decision:** Each fetcher start reads the plain view of the file and keeps only our own key names.
Unscoped credentials go to every Ecowitt widget that has none. Plain `widget_<id>_*` keys keep their
name. Once the encrypted copy is committed, the plain keys are removed. There is no done flag: with
nothing left, it is a no-op. If the widget list can't be read, the legacy keys stay for the next run.
The keystore fallback now uses its own file, `<name>_unencrypted`.
**Consequences:** A device whose keystore fails keeps its keys in plain text, but in a separate file,
never mixed into the encrypted one. Settings saved earlier under that fallback are moved over too.

## 2026-10-05 — Wind + tide widget reads Windguru and tabuasdemare directly
**Context:** The Echo Show dashboard (echo-show-vento) gets wind from Windguru and tide from
tabuasdemare.com.br through a Python server. Windguru answers 401 unless the station page is the Referer,
which a browser can't set but an app can.
**Decision:** The app calls both sites itself, with no proxy and no keys. Wind is cached for 5 min per
station. On failure the last real reading is shown as STALE, and a reading older than 60 min is STALE
even when the request works. The tide table is fetched once per day and kept only for that day: a
table for another day is never used, even when the site still serves it. The height uses the same
cosine interpolation as server.py. Places (name, station ID, tide slug) live in plain prefs because
they hold no secrets. Each widget keeps its own copy of its place, so deleting a place from the list
doesn't break a widget. The last extreme is shown as a clock time ("às 04:25"), not "há 1h13",
because the widget repaints only every 30 min.
**Consequences:** If either site changes its format, the widget breaks until the parser is fixed (the
Echo would break too). Tide works only for places on tabuasdemare.com.br, and its times are read in
America/Maceio.

## 2026-10-03 — Every widget gets its corners from a 16dp layout outline
**Context:** After the Modern fix, Clean, Bar and Compact still baked height-scaled corners into the
bitmap (Compact's was 20% of its size), so they also looked rounder than other home-screen widgets.
**Decision:** All five layouts clip to a 16dp outline on Android 12+ (`widget_outline`, or the Chart and
Bar backgrounds, now also 16dp). Each renderer has `clipCorners`, which draws 16-unit corners only
before Android 12. The picker previews and tests render with `clipCorners = true`.
**Consequences:** All widgets have the same corner radius at any size. New widget styles must follow
the same pattern.

## 2026-10-03 — Modern widget corners come from the layout outline too
**Context:** Modern baked a 28-unit corner into its bitmap, scaled by widget height and then stretched
by fitXY, so on a Galaxy S25 it came out ~30dp, much rounder than other home-screen widgets (~16dp).
**Decision:** Same as the Chart widget: on Android 12+ `widget_wind_modern.xml` clips to a 16dp
outline and draws the 1dp border as a foreground; the bitmap is a plain rectangle. Before 12 the
bitmap draws its own 16-unit corners (`clipCorners`).
**Consequences:** Corners stay 16dp at any size. Clean, Bar and Compact still bake height-scaled
corners into the bitmap; move them over the same way if they look too round.

## 2026-10-03 — Status shows only the time; offline is amber
**Context:** "Updated 19:14" / "Offline 19:14" / "Demo data" took room from the title on narrow widgets.
**Decision:** The status is just the reading time for LIVE, CACHED and STALE, and "Demo" for demo
data. STALE is told apart only by bold amber text; DEMO keeps the amber pill since it is not a reading.
**Consequences:** Offline relies on colour plus bold weight, which is weaker for colour-blind users. If
that becomes a problem, add an icon or the pill back for STALE (`StatusBadge.hasPill`).

## 2026-10-03 — Ecowitt keys go in the query string
**Context:** 899c204 moved `application_key`/`api_key` into `X-Application-Key`/`X-API-Key` headers to
keep them out of URLs. Ecowitt v3 ignores those headers (40010 "Invalid application Key", checked
against the live API), so every fetch failed and widgets showed DEMO.
**Decision:** Keys are query parameters again, built with `HttpUrl` (encoded). URLs are never logged.
A test asserts the keys are in the query and not in headers.
**Consequences:** Keys are in request URLs (HTTPS, so not on the wire in clear). Don't add an OkHttp
logging interceptor at URL level without redacting them.

## 2026-10-03 — Chart widget corners come from clipToOutline, not the bitmap
**Context:** Review of PR #2: the bitmap is rendered at max width x max height and stretched (fitXY)
into the real cell, whose aspect differs between portrait and landscape. A radius baked into the
bitmap can't match the 16dp background in both, so opaque pixels showed in the corners.
**Decision:** `widget_wind.xml` sets `clipToOutline` on the root, clipping to `widget_background` at
its on-screen size (Android 12+). `WindChartRenderer` only clears its own corners below Android 12.
**Consequences:** Exact corners on 12+; an approximation on 8–11. Rendering at the real cell size
per orientation would fix the stretch itself (text too); not done yet.

## 2026-10-03 — Widget bitmaps use ARGB_8888
**Context:** RGB_565 has no alpha, so the area outside each rounded card came out opaque black and the
translucent card colours were drawn solid.
**Decision:** All 5 renderers create ARGB_8888 bitmaps. Chart fills its full rect, so it clears the
corners outside a 16dp round rect (matching `widget_background`).
**Consequences:** Twice the bitmap memory per widget. Rendering at 1x density (backlog) would offset it
if RemoteViews size limits are ever hit.

## 2026-10-03 — Stale and demo data get an amber status badge on every widget
**Context:** "Offline 15:16" was drawn in the same small grey text as "Updated 15:16", so an old
reading looked live at a glance.
**Decision:** `StatusBadge.draw` renders the status line for all 5 styles; STALE and DEMO are bold amber
inside an amber pill. LIVE and CACHED keep the plain style (CACHED is a recent real reading).
**Consequences:** One place to change status styling. The numbers themselves are not dimmed; if that
turns out not to be enough on the phone, dim them next.

## 2026-10-03 — Modern chart auto-fits its Y axis; picker previews rendered from the real renderers
**Context:** Modern used a fixed 0–20 kt axis, so a typical 11–14 kt window filled ~15% of the height and
looked like a flat line next to Clean (which fits min..max of the 3h window). Modern and Clean pointed
`previewLayout` at the live layout, which shows blank/"loading" in the Android 12+ widget picker.
**Decision:** Modern uses Clean's scaling (min..max, range ≥ 1 kt, line at 85% of height), keeping its blue
colours. Modern and Clean get `previewImage` PNGs rendered by `WidgetPreviewGenerator` (Robolectric native
graphics, pt-BR, fixed sample data); `previewLayout` was dropped for them.
**Consequences:** The Modern axis no longer shows absolute scale (a 2 kt swing looks as tall as a 15 kt one;
the current value is still printed). Previews must be regenerated by hand after renderer changes (AGENTS.md).

## 2026-10-03 — All widget updates run through WorkManager
**Context:** Providers and `BootReceiver` launched coroutines in a global scope and returned at once.
The worker reported success before any fetch had finished, and the system could kill the process mid-fetch.
**Decision:** Each provider exposes a `suspend fun updateWidget(..., forceRefresh)`.
`onUpdate`, resize, tap, boot and configure all call `WindUpdateScheduler.enqueueUpdate(ids, forceRefresh)`.
That enqueues a unique `OneTimeWork`; `WindUpdateWorker` sends each widget to its provider and waits for it.
One-time work has no network constraint, so offline resizes still re-render from the stale cache.
**Consequences:** A tap can take a moment to show (WorkManager scheduling). Expedited work was
rejected: on API < 31 it needs a foreground notification (`getForegroundInfo`). Repeated taps on the same
widget while its work is still pending or running are ignored (`ExistingWorkPolicy.KEEP`).

## 2026-10-03 — Demo data is never cached and never replaces a real reading
**Context:** On an API or network failure, `fetchFromEcowitt` returned demo data, and `fetch()` cached it
as `LIVE`. That overwrote the last real reading, so fake wind showed as real.
**Decision:** Missing credentials → demo data, not cached. A failed history request throws, and `fetch()`
returns the last real reading marked `STALE`. Demo data only appears if no fetch has ever succeeded.
Saving credentials clears that widget's cache.
**Consequences:** A widget whose station goes offline keeps showing an aging real reading labelled
"Offline HH:MM". A fresh widget with bad keys shows "Demo data" (still no error overlay; see bug 7).
The `OkHttpClient` is now one shared instance that can be swapped in via the constructor, for tests.
