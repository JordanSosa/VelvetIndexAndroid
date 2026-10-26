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
