# Velvet Index Android
Private local-first media catalogue for Android 8+.

Features: public Rule34 tag search; X media discovery in browser; private SQLite bookmarks and collections; file-picker indexing of local images and videos.

## Build an APK
The GitHub Actions workflow in `.github/workflows/build-apk.yml` builds a debug APK automatically on push or via the Actions > Build private Android APK > Run workflow menu. Find the downloadable ZIP under the successful run's Artifacts section, extract `app-debug.apk`, then install it on Android. Debug APKs are suitable for private testing only. Later builds may be signed with different debug keys.

The app has not been tested on an Android phone. It does not download external media or play local content internally yet. Only index lawful, consensual content that you are permitted to access.
