# Velvet Index Android
Private local-first media catalogue for Android 8+.

Features: public Rule34 tag search; X media discovery in browser; private SQLite bookmarks and collections; file-picker indexing of local images and videos.

## Build an APK
The GitHub Actions workflow in `.github/workflows/build-apk.yml` builds a debug APK automatically on push or via the Actions > Build private Android APK > Run workflow menu. Find the downloadable ZIP under the successful run's Artifacts section, extract `app-debug.apk`, then install it on Android. Debug APKs are suitable for private testing only. Later builds may be signed with different debug keys.

The app has not been tested on an Android phone. It does not download external media or play local content internally yet. Only index lawful, consensual content that you are permitted to access.

## Version 0.3 discovery and organisation
- Combined API search across Rule34 and e621, with a choice to search one provider alone.
- Adult-video discovery shortcuts for Pornhub, XVideos, xHamster, Japanese catalogue metadata (JAVLibrary), IAFD and independent creators (ManyVids). These open the provider or a site-scoped web search; they are **not** integrated scraping, automated indexing or downloadable search results.
- Library filtering by collection, source and keywords; URL normalisation for search-result deduplication and duplicate-link inspection.
- Media preview and optional Android Download Manager saving, when authorised.

Still planned: native video search adapters where explicitly permitted, thumbnail generation for imported local files, binary/file-hash duplicate detection, and optional on-device AI tagging. No age-gate bypass, login bypass, access to private media, or indexing of nonconsensual content.

## Experimental real-person video search
A first apiJAV catalogue adapter searches publicly documented video metadata (title, thumbnail, studio, release code, duration) and adds cards to the unified results feed. The API and thumbnail hosts may be unreliable; neither media hosting permission nor playback rights have been verified, so the app links to provider metadata and does not proxy or download video streams from this adapter. This is not Pornhub integration. Future adapters should require permission-compatible access and stable metadata/thumbnail rights.

## Experimental Lustpress-compatible video sources (v0.6)
Under About > Video source settings, specify an HTTPS endpoint of a Lustpress-compatible instance you operate or are authorised to access. Search has provider-specific options for Pornhub, XVideos, xHamster, XNXX, RedTube, YouPorn, Eporner and TXXX. API response parsing accepts common `data[]`, `data.results[]`, `data.videos[]` etc. Thumbnail/title/source URLs are displayed when returned; playback remains with the publisher. Providers may reject requests or return unexpected response shapes. No service is bundled and no access restrictions are bypassed by Velvet Index.

**Important:** The Lustpress maintainers say they discontinued their hosted public API on 11 March 2026, so the app requires a separately deployed or authorised HTTPS instance. Do not enter access tokens in the URL. The Android code was not compiled or integration-tested in this workspace; it requires a successful GitHub Actions build and real provider testing.
