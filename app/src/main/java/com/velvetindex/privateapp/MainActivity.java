package com.velvetindex.privateapp;

import android.Manifest;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.webkit.ConsoleMessage;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private static final int REQ_PICK    = 42;
    private static final int REQ_STORAGE = 43;
    private static final int REQ_EXPORT  = 44;
    private static final int REQ_IMPORT  = 45;

    static final String UA =
        "Mozilla/5.0 (Linux; Android 14; Pixel) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36";

    private WebView web;
    private FrameLayout root;
    private DB db;
    private MediaServer server;

    private final ExecutorService io  = Executors.newSingleThreadExecutor();
    private final ExecutorService net = Executors.newFixedThreadPool(6);

    private final Handler ui = new Handler(Looper.getMainLooper());
    private volatile boolean alive = true;

    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;

    private String pendingUrl, pendingRef, pendingName;

    private final Map<String, HttpURLConnection> inflight = new HashMap<>();

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        db = new DB();
        try { server = new MediaServer(getCacheDir(), s -> log("media: " + s)); }
        catch (Exception e) { log("media server unavailable: " + e); }

        root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(16, 14, 22));
        root.setOnApplyWindowInsetsListener(this::applyInsets);
        setContentView(root);

        buildWeb();
    }

    private void buildWeb() {
        WebView v = new WebView(this);
        WebSettings st = v.getSettings();
        st.setJavaScriptEnabled(true);
        st.setDomStorageEnabled(true);
        st.setLoadsImagesAutomatically(true);
        st.setAllowFileAccess(true);
        st.setAllowContentAccess(false);
        st.setAllowFileAccessFromFileURLs(true);
        st.setAllowUniversalAccessFromFileURLs(true);
        st.setMediaPlaybackRequiresUserGesture(false);
        st.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        st.setCacheMode(WebSettings.LOAD_DEFAULT);
        st.setUseWideViewPort(true);
        st.setLoadWithOverviewMode(true);

        v.setBackgroundColor(Color.rgb(16, 14, 22));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) WebView.setWebContentsDebuggingEnabled(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(v, true);
        v.addJavascriptInterface(new Bridge(), "Native");

        v.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView w, WebResourceRequest r) {
                return route(r.getUrl());
            }
            @SuppressWarnings("deprecation")
            @Override public boolean shouldOverrideUrlLoading(WebView w, String url) {
                return route(Uri.parse(url));
            }
            @Override public void onReceivedError(WebView w, WebResourceRequest r, WebResourceError e) {
                if (r.isForMainFrame()) log("page error: " + e.getDescription());
            }
            @Override public boolean onRenderProcessGone(WebView w, RenderProcessGoneDetail d) {
                log("renderer gone, rebuilding");
                root.removeView(w);
                w.destroy();
                web = null;
                buildWeb();
                return true;
            }
        });

        v.setWebChromeClient(new WebChromeClient() {
            @Override public void onShowCustomView(View view, CustomViewCallback cb) {
                if (customView != null) { cb.onCustomViewHidden(); return; }
                customView = view;
                customViewCallback = cb;
                root.addView(view, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                web.setVisibility(View.GONE);
            }
            @Override public void onHideCustomView() {
                if (customView == null) return;
                root.removeView(customView);
                customView = null;
                web.setVisibility(View.VISIBLE);
                customViewCallback.onCustomViewHidden();
            }
            @Override public boolean onConsoleMessage(ConsoleMessage m) {
                log("js: " + m.message() + " @" + m.lineNumber());
                return true;
            }
        });

        web = v;
        root.addView(v, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        v.loadUrl("file:///android_asset/index.html");
        root.requestApplyInsets();
    }

    private WindowInsets applyInsets(View view, WindowInsets insets) {
        if (web == null) return insets;
        int top    = insets.getInsets(WindowInsets.Type.statusBars()).top;
        int nav    = insets.getInsets(WindowInsets.Type.navigationBars()).bottom;
        int ime    = insets.getInsets(WindowInsets.Type.ime()).bottom;
        int bottom = Math.max(nav, ime);
        ViewGroup.LayoutParams lp = web.getLayoutParams();
        if (lp instanceof FrameLayout.LayoutParams) {
            FrameLayout.LayoutParams f = (FrameLayout.LayoutParams) lp;
            if (f.topMargin != top || f.bottomMargin != bottom) {
                f.topMargin = top;
                f.bottomMargin = bottom;
                web.setLayoutParams(f);
            }
        }
        return insets;
    }

    @Override public void onBackPressed() {
        if (web == null) { super.onBackPressed(); return; }
        try {
            web.evaluateJavascript("window.handleBack?window.handleBack():false", value -> {
                if (!"true".equals(value)) exitAfterBack();
            });
        } catch (Exception e) { exitAfterBack(); }
    }

    private void exitAfterBack() { MainActivity.super.onBackPressed(); }

    @Override protected void onPause() { super.onPause(); if (web != null) web.onPause(); }
    @Override protected void onResume() { super.onResume(); if (web != null) web.onResume(); }

    @Override protected void onDestroy() {
        alive = false;
        if (server != null) server.stop();
        io.shutdown();
        net.shutdownNow();
        if (web != null) {
            root.removeView(web);
            web.removeJavascriptInterface("Native");
            web.destroy();
            web = null;
        }
        db.close();
        super.onDestroy();
    }

    private void log(String s) { android.util.Log.d("VelvetIndex", s); }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    private void callJs(String fn, String... args) {
        if (!alive) return;
        StringBuilder sb = new StringBuilder("window.").append(fn)
                .append("&&window.").append(fn).append('(');
        for (int i = 0; i < args.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(JSONObject.quote(args[i] == null ? "" : args[i]));
        }
        sb.append(')');
        final String js = sb.toString();
        ui.post(() -> {
            if (alive && web != null) {
                try { web.evaluateJavascript(js, null); } catch (Exception ignored) {}
            }
        });
    }

    private boolean route(Uri u) {
        if (u == null || u.getScheme() == null) return false;
        String scheme = u.getScheme().toLowerCase(Locale.ROOT);
        if (scheme.equals("file") || scheme.equals("about")
                || scheme.equals("data") || scheme.equals("blob")
                || scheme.equals("javascript")) return false;
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, u);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            toast("Nothing installed can open " + scheme + ": links");
        } catch (Exception e) {
            toast("Could not open link");
        }
        return true;
    }

    private SharedPreferences prefs() { return getSharedPreferences("velvet", MODE_PRIVATE); }

    private void requestDownload(String url, String referer, String name) {
        if (url == null || !url.startsWith("http")) { toast("Not a downloadable URL"); return; }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                   != PackageManager.PERMISSION_GRANTED) {
            pendingUrl = url; pendingRef = referer; pendingName = name;
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_STORAGE);
            return;
        }
        enqueueDownload(url, referer, name);
    }

    private void enqueueDownload(String url, String referer, String name) {
        try {
            boolean viaProxy = prefs().getBoolean("download_via_proxy", false)
                    && server != null && referer != null && !referer.isEmpty();
            String fetchUrl = viaProxy
                    ? "http://127.0.0.1:" + server.port + "/m?u="
                      + Uri.encode(url) + "&r=" + Uri.encode(referer)
                    : url;

            String ext = guessExt(url);
            String base = (name == null || name.trim().isEmpty())
                    ? "velvet_" + System.currentTimeMillis()
                    : sanitize(name.trim());
            if (base.length() > 60) base = base.substring(0, 60);
            String file = base + "." + ext;

            DownloadManager.Request req = new DownloadManager.Request(Uri.parse(fetchUrl));
            req.setTitle(file);
            req.setMimeType(mimeFor(ext));
            req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            if (!viaProxy) {
                req.addRequestHeader("User-Agent", UA);
                if (referer != null && !referer.isEmpty()) req.addRequestHeader("Referer", referer);
            }
            req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, file);
            ((DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE)).enqueue(req);
            toast("Downloading " + file);
        } catch (Exception e) {
            toast("Download failed: " + e.getMessage());
        }
    }

    private static String guessExt(String url) {
        String path = url;
        int q = path.indexOf('?');
        if (q > 0) path = path.substring(0, q);
        int d = path.lastIndexOf('.');
        if (d > 0 && path.length() - d <= 6) {
            String e = path.substring(d + 1).toLowerCase(Locale.ROOT);
            if (e.matches("jpe?g|png|gif|webp|avif|bmp|mp4|webm|mkv|mov|m4v|ts|flv|mp3|m4a")) return e;
        }
        return "jpg";
    }

    private static String mimeFor(String ext) {
        switch (ext) {
            case "mp4": case "m4v": return "video/mp4";
            case "webm": return "video/webm";
            case "mkv":  return "video/x-matroska";
            case "mov":  return "video/quicktime";
            case "png":  return "image/png";
            case "gif":  return "image/gif";
            case "webp": return "image/webp";
            case "avif": return "image/avif";
            default:     return "image/jpeg";
        }
    }

    private static String sanitize(String s) {
        return s.replaceAll("[^A-Za-z0-9 _.-]", "_").replaceAll("\\s+", "_");
    }

    @Override public void onRequestPermissionsResult(int req, String[] perms, int[] res) {
        super.onRequestPermissionsResult(req, perms, res);
        if (req == REQ_STORAGE && res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED) {
            enqueueDownload(pendingUrl, pendingRef, pendingName);
        } else if (req == REQ_STORAGE) {
            toast("Storage permission denied");
        }
    }

    @Override protected void onActivityResult(int req, int result, Intent data) {
        super.onActivityResult(req, result, data);
        if (result != RESULT_OK || data == null) return;

        if (req == REQ_PICK) { indexPickedFiles(data); return; }

        if (req == REQ_EXPORT) {
            try (OutputStream os = getContentResolver().openOutputStream(data.getData())) {
                os.write(db.all().toString(2).getBytes("UTF-8"));
                toast("Library exported");
            } catch (Exception e) { toast("Export failed: " + e.getMessage()); }
            return;
        }

        if (req == REQ_IMPORT) {
            try (InputStream is = getContentResolver().openInputStream(data.getData())) {
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = is.read(buf)) > 0) bo.write(buf, 0, n);
                JSONArray arr = new JSONArray(bo.toString("UTF-8"));
                final int[] added = {0};
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.optJSONObject(i);
                    if (o != null && db.saveObj(o)) added[0]++;
                }
                callJs("onLibrary", db.all().toString());
                callJs("onStatus", "Imported " + added[0] + " items");
            } catch (Exception e) { toast("Import failed: " + e.getMessage()); }
        }
    }

    private void indexPickedFiles(Intent data) {
        final JSONArray found = new JSONArray();
        try {
            if (data.getClipData() != null) {
                for (int i = 0; i < data.getClipData().getItemCount(); i++)
                    describe(found, data.getClipData().getItemAt(i).getUri());
            } else if (data.getData() != null) {
                describe(found, data.getData());
            }
        } catch (Exception e) { toast("Could not read selection"); return; }

        io.execute(() -> {
            for (int i = 0; i < found.length(); i++) {
                try { db.saveObj(found.getJSONObject(i)); } catch (Exception ignored) {}
            }
            callJs("onLibrary", db.all().toString());
            callJs("onStatus", "Indexed " + found.length() + " file(s)");
        });
    }

    private void describe(JSONArray arr, Uri uri) {
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            String name = "Imported media";
            String type = getContentResolver().getType(uri);
            try (Cursor c = getContentResolver().query(uri,
                    new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                if (c != null && c.moveToFirst()) name = c.getString(0);
            }
            JSONObject x = new JSONObject();
            x.put("source", "Local file");
            x.put("page_url", uri.toString());
            x.put("file_url", uri.toString());
            x.put("title", name);
            x.put("collection", "Local imports");
            x.put("media_type", type != null && type.startsWith("video") ? "video" : "image");
            arr.put(x);
        } catch (Exception ignored) {}
    }

    class Bridge {

        @JavascriptInterface public int proxyPort() { return server == null ? 0 : server.port; }

        @JavascriptInterface public void logMsg(String s) { log("js: " + s); }

        @JavascriptInterface public void list() {
            io.execute(() -> callJs("onLibrary", db.all().toString()));
        }

        @JavascriptInterface public void save(String s) {
            io.execute(() -> {
                try {
                    boolean added = db.saveObj(new JSONObject(s));
                    callJs("onLibrary", db.all().toString());
                    callJs("onStatus", added ? "Saved" : "Already in library");
                } catch (Exception e) { callJs("onStatus", "Save failed"); }
            });
        }

        @JavascriptInterface public void update(String s) {
            io.execute(() -> {
                try { db.update(new JSONObject(s)); callJs("onLibrary", db.all().toString()); }
                catch (Exception e) { callJs("onStatus", "Update failed"); }
            });
        }

        @JavascriptInterface public void remove(int id) {
            io.execute(() -> {
                db.getWritableDatabase().delete("items", "id=?", new String[]{String.valueOf(id)});
                callJs("onLibrary", db.all().toString());
                callJs("onStatus", "Deleted");
            });
        }

        @JavascriptInterface public void toggleFav(int id, boolean fav) {
            io.execute(() -> {
                ContentValues v = new ContentValues();
                v.put("fav", fav ? 1 : 0);
                db.getWritableDatabase().update("items", v, "id=?", new String[]{String.valueOf(id)});
                callJs("onLibrary", db.all().toString());
            });
        }

        @JavascriptInterface public void fetch(String reqId, String url, String headersJson) {
            net.execute(() -> {
                HttpURLConnection c = null;
                try {
                    URL u = new URL(url);
                    String proto = u.getProtocol();
                    if (!"https".equals(proto) && !"http".equals(proto)) throw new Exception("bad scheme");
                    c = (HttpURLConnection) u.openConnection();
                    c.setInstanceFollowRedirects(true);
                    c.setConnectTimeout(15000);
                    c.setReadTimeout(25000);
                    c.setRequestProperty("User-Agent", UA);
                    c.setRequestProperty("Accept", "application/json, text/plain, */*");
                    if (headersJson != null && !headersJson.isEmpty() && !headersJson.equals("{}")) {
                        JSONObject h = new JSONObject(headersJson);
                        for (Iterator<String> it = h.keys(); it.hasNext(); ) {
                            String k = it.next();
                            c.setRequestProperty(k, h.optString(k));
                        }
                    }
                    synchronized (inflight) { inflight.put(reqId, c); }

                    int status = c.getResponseCode();
                    InputStream in = status >= 400 ? c.getErrorStream() : c.getInputStream();
                    String body = "";
                    if (in != null) {
                        ByteArrayOutputStream bo = new ByteArrayOutputStream();
                        byte[] buf = new byte[16384];
                        int n, total = 0;
                        while ((n = in.read(buf)) > 0 && total < 8000000) { bo.write(buf, 0, n); total += n; }
                        in.close();
                        body = bo.toString("UTF-8");
                    }

                    JSONObject hs = new JSONObject();
                    for (Map.Entry<String, List<String>> e : c.getHeaderFields().entrySet()) {
                        if (e.getKey() == null) continue;
                        List<String> v = e.getValue();
                        hs.put(e.getKey().toLowerCase(Locale.ROOT), v == null || v.isEmpty() ? "" : v.get(0));
                    }
                    callJs("onFetch", reqId, String.valueOf(status), hs.toString(), body);
                } catch (Exception e) {
                    callJs("onFetchError", reqId, String.valueOf(e.getMessage()));
                } finally {
                    synchronized (inflight) { inflight.remove(reqId); }
                    if (c != null) c.disconnect();
                }
            });
        }

        @JavascriptInterface public void abort(String reqId) {
            HttpURLConnection c;
            synchronized (inflight) { c = inflight.get(reqId); }
            if (c != null) { try { c.disconnect(); } catch (Exception ignored) {} }
        }

        @JavascriptInterface public void probe(String reqId, String url, String referer) {
            net.execute(() -> {
                HttpURLConnection c = null;
                try {
                    c = (HttpURLConnection) new URL(url).openConnection();
                    c.setInstanceFollowRedirects(true);
                    c.setConnectTimeout(12000);
                    c.setReadTimeout(15000);
                    c.setRequestProperty("User-Agent", UA);
                    c.setRequestProperty("Accept-Encoding", "identity");
                    c.setRequestProperty("Range", "bytes=0-0");
                    if (referer != null && !referer.isEmpty()) c.setRequestProperty("Referer", referer);
                    int status = c.getResponseCode();
                    JSONObject o = new JSONObject();
                    o.put("status", status);
                    o.put("type", String.valueOf(c.getContentType()));
                    o.put("length", String.valueOf(c.getHeaderField("Content-Range")));
                    o.put("acceptRanges", String.valueOf(c.getHeaderField("Accept-Ranges")));
                    o.put("playable", status == 200 || status == 206);
                    callJs("onProbe", reqId, o.toString());
                } catch (Exception e) {
                    callJs("onProbe", reqId, "{\"status\":0,\"playable\":false}");
                } finally { if (c != null) c.disconnect(); }
            });
        }

        @JavascriptInterface public void open(String s) { ui.post(() -> route(Uri.parse(s))); }

        @JavascriptInterface public void toast(String s) { ui.post(() -> MainActivity.this.toast(s)); }

        @JavascriptInterface public void download(String url, String referer, String name) {
            ui.post(() -> requestDownload(url, referer, name));
        }

        @JavascriptInterface public void importMedia() {
            ui.post(() -> {
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                i.setType("*/*");
                i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/*", "video/*"});
                i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                startActivityForResult(i, REQ_PICK);
            });
        }

        @JavascriptInterface public void viewLocal(String uri) {
            ui.post(() -> {
                try {
                    Uri u = Uri.parse(uri);
                    if (!"content".equals(u.getScheme())) { toast("Not a local file"); return; }
                    Intent i = new Intent(Intent.ACTION_VIEW);
                    i.setDataAndType(u, getContentResolver().getType(u));
                    i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(i);
                } catch (Exception e) { toast("No compatible viewer installed"); }
            });
        }

        @JavascriptInterface public void exportLibrary() {
            ui.post(() -> {
                Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("application/json");
                i.putExtra(Intent.EXTRA_TITLE, "velvet-library-"
                        + new SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(new Date()) + ".json");
                startActivityForResult(i, REQ_EXPORT);
            });
        }

        @JavascriptInterface public void importLibrary() {
            ui.post(() -> {
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("application/json");
                startActivityForResult(i, REQ_IMPORT);
            });
        }

        @JavascriptInterface public String getPref(String k) { return prefs().getString(k, ""); }

        @JavascriptInterface public void setPref(String k, String v) {
            prefs().edit().putString(k, v).apply();
        }

        @JavascriptInterface public boolean getFlag(String k, boolean def) {
            return prefs().getBoolean(k, def);
        }

        @JavascriptInterface public void setFlag(String k, boolean v) {
            prefs().edit().putBoolean(k, v).apply();
        }

        @JavascriptInterface public void clearCache() {
            if (server != null) server.clearCache();
            if (web != null) web.clearCache(true);
            callJs("onStatus", "Cache cleared");
        }
    }

    class DB extends SQLiteOpenHelper {

        DB() { super(MainActivity.this, "velvet_private.db", null, 2); }

        private static final String CREATE =
            "CREATE TABLE items(" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "source TEXT, page_url TEXT UNIQUE, file_url TEXT, preview_url TEXT," +
            "title TEXT, tags TEXT, collection TEXT, notes TEXT," +
            "media_type TEXT, rating TEXT, score INTEGER DEFAULT 0," +
            "width INTEGER DEFAULT 0, height INTEGER DEFAULT 0, duration INTEGER DEFAULT 0," +
            "fav INTEGER DEFAULT 0, created INTEGER)";

        @Override public void onCreate(SQLiteDatabase d) {
            d.execSQL(CREATE);
            d.execSQL("CREATE INDEX IF NOT EXISTS idx_items_created ON items(created DESC)");
            d.execSQL("CREATE INDEX IF NOT EXISTS idx_items_coll ON items(collection)");
        }

        @Override public void onUpgrade(SQLiteDatabase d, int oldV, int newV) {
            if (oldV < 2) {
                try { d.execSQL("ALTER TABLE items ADD COLUMN media_type TEXT"); } catch (Exception ignored) {}
                try { d.execSQL("ALTER TABLE items ADD COLUMN rating TEXT"); } catch (Exception ignored) {}
                try { d.execSQL("ALTER TABLE items ADD COLUMN score INTEGER DEFAULT 0"); } catch (Exception ignored) {}
                try { d.execSQL("ALTER TABLE items ADD COLUMN width INTEGER DEFAULT 0"); } catch (Exception ignored) {}
                try { d.execSQL("ALTER TABLE items ADD COLUMN height INTEGER DEFAULT 0"); } catch (Exception ignored) {}
                try { d.execSQL("ALTER TABLE items ADD COLUMN duration INTEGER DEFAULT 0"); } catch (Exception ignored) {}
                try { d.execSQL("ALTER TABLE items ADD COLUMN fav INTEGER DEFAULT 0"); } catch (Exception ignored) {}
            }
        }

        private static final String[] COLS = {
            "source", "page_url", "file_url", "preview_url", "title",
            "tags", "collection", "notes", "media_type", "rating"
        };

        synchronized boolean saveObj(JSONObject x) {
            String page = x.optString("page_url", "");
            String file = x.optString("file_url", "");
            if (page.isEmpty() && file.isEmpty()) return false;

            ContentValues v = new ContentValues();
            for (String k : COLS) v.put(k, x.optString(k, ""));
            if (v.getAsString("collection") == null || v.getAsString("collection").isEmpty())
                v.put("collection", "Unsorted");
            v.put("score", x.optInt("score", 0));
            v.put("created", System.currentTimeMillis());
            long row = getWritableDatabase()
                    .insertWithOnConflict("items", null, v, SQLiteDatabase.CONFLICT_IGNORE);
            return row != -1;
        }

        synchronized void update(JSONObject x) {
            ContentValues v = new ContentValues();
            for (String k : new String[]{"title", "tags", "collection", "notes"})
                v.put(k, x.optString(k, ""));
            getWritableDatabase().update("items", v, "id=?", new String[]{x.optString("id")});
        }

        synchronized JSONArray all() {
            JSONArray arr = new JSONArray();
            try (Cursor c = getReadableDatabase().rawQuery(
                    "SELECT id,source,page_url,file_url,preview_url,title,tags,collection,notes," +
                    "media_type,rating,score,fav FROM items ORDER BY created DESC LIMIT 5000", null)) {
                while (c.moveToNext()) {
                    JSONObject j = new JSONObject();
                    try {
                        for (int i = 0; i < c.getColumnCount(); i++) {
                            String col = c.getColumnName(i);
                            if (c.isNull(i)) j.put(col, "");
                            else if ("id".equals(col) || "score".equals(col) || "fav".equals(col))
                                j.put(col, c.getLong(i));
                            else j.put(col, c.getString(i));
                        }
                    } catch (Exception ignored) {}
                    arr.put(j);
                }
            }
            return arr;
        }
    }
}
