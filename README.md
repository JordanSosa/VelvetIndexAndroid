# Velvet Index

Local-first Android catalogue for finding **niche** adult content — the stuff mainstream
tube sites don't tag. Built around booru tag vocabularies, real-world leak archives, and
open browser shortcuts. Nothing is filtered unless you ask.

Not affiliated with any provider. Only index what you're allowed to access.

## What it does

- **Multi-source tag search** across e621, Rule34, Gelbooru, Danbooru, Konachan, yande.re,
  Coomer and Kemono — all at once or one at a time.
- **Local media proxy** (`127.0.0.1`) that sets the right `Referer`/`User-Agent` per host,
  so previews and video streams work on hotlink-protected CDNs.
- **24-category tag taxonomy** for dark / non-con, zoo, leaks, teen, futa, furry, BDSM,
  hyper, NTR and more.
- **Private library** in on-device SQLite: collections, tags, notes, favourites,
  duplicate detection, JSON export/import.
- **22 browser shortcuts** for sites with no API (JAVLibrary, IAFD, X, Reddit, nHentai,
  Hanime, F95Zone, ManyVids).

## Build

Push to `main` and grab `VelvetIndex-debug.apk` from the run's Artifacts. Or locally:

    gradle wrapper          # once, to generate gradlew
    ./gradlew assembleDebug

Install over ADB (`adb install -r app-debug.apk`) or by opening the APK on the phone.

Requires Android 8.0+. `targetSdk 35`, `compileSdk 35`, AGP 8.7.3, JDK 17.

## Finding dark content — the actual workflow

Mainstream tube sites are useless for this. Use the right tool:

| What you want | Where it lives | How |
|---|---|---|
| Non-con / mind-break / forced | Boorus | Tags tab -> Dark / Non-con. Search `rape animated` on e621 |
| Zoo / bestiality | e621, Gelbooru | Tags tab -> Zoo. Add `animated` |
| Real leaked / amateur | Coomer, Kemono, X, Reddit | Search a creator handle, or the browser links |
| "Teen" as a real age | Anywhere | `petite` / `flat_chest` beats `teen` as a tag |
| JAV / Japanese | JAVLibrary + tubes | Look up the code, then search the tube sites |
| Something genuinely obscure | Boorus | Sort Obscure first — sorts by lowest score |

**Tips that matter:**

- Booru tags use underscores: `mind_break`, `flat_chest`, `netorare`.
- Append `animated` to get motion instead of stills.
- Append `rating:e` to kill all safe/questionable results.
- `order:random` and `score:>50` are e621/Danbooru meta-tags.
- Long-press a tag chip to *append* it instead of replacing your query.
- If an API returns nothing, use Open-in-browser sites — 22 shortcuts, nothing filtered.

## Sources

| Source | Group | Key needed |
|---|---|---|
| e621, Rule34 | Booru | no |
| Gelbooru | Booru | optional (removes rate cap) |
| Danbooru, Konachan, yande.re | Booru | no (off by default) |
| Coomer, Kemono | Real / Leaks | no |
| Reddit | Real / Leaks | no (often throttled) |
| Pornhub, XVideos, xHamster +5 | Video API | needs your own instance |

Adding a source is ~10 lines in `app/src/main/assets/sources.js` — no Java rebuild.

## Architecture

    MainActivity  --JS bridge--  index.html / sources.js / tags.js
         |                              |
         |  MediaServer (127.0.0.1) ----+  img/video via /m?u=&r=
         |  SQLite (velvet_private.db)
         +  HttpURLConnection fetch pool

- Network runs on a 6-thread pool; SQLite on its own single thread, so the library
  stays responsive during a search.
- The proxy binds to loopback only. No ports open to the LAN.
- No accounts, no cloud sync, no analytics.

## Limitations (honest list)

- The video-provider adapter needs a Lustpress-compatible API you host — the public
  one shut down in March 2026.
- The proxy sets `Referer` from the item's page origin. Some CDNs want a *different*
  referer; if a preview stays blocked, use Source to open it in a browser.
- Downloads go direct by default (works even if the app is killed). Flip the toggle in
  Settings to route them through the proxy when a CDN demands a referer.
- Reddit's public JSON endpoint throttles aggressively.
- Not tested on a real device yet. Rotate the screen, tap an http link, download
  something, play a video, press back — that's the smoke test.

## Licence

MIT. Taxonomy and source list are opinionated suggestions, not gospel.
