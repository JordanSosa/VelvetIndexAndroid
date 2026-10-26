package com.velvetindex.privateapp;

import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;

/**
 * Tiny loopback HTTP proxy. The WebView loads http://127.0.0.1:PORT/m?u=<url>&r=<referer>
 * so that previews and video streams carry the Referer/User-Agent the origin expects.
 * Binds to loopback only; never reachable from outside the device.
 */
public class MediaServer {

    public interface Log { void line(String s); }

    private static final String UA =
        "Mozilla/5.0 (Linux; Android 14; Pixel) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36";
    private static final int MAX_IMAGE_CACHE = 8 * 1024 * 1024;

    private final ServerSocket server;
    private final ExecutorService pool = Executors.newFixedThreadPool(6);
    private final File cacheDir;
    private final Log log;
    public final int port;
    private volatile boolean running = true;

    public MediaServer(File base, Log log) throws IOException {
        this.log = log;
        this.cacheDir = new File(base, "media");
        if (!cacheDir.isDirectory() && !cacheDir.mkdirs()) throw new IOException("cache dir");
        this.server = new ServerSocket(0, 32, InetAddress.getByName("127.0.0.1"));
        this.port = server.getLocalPort();
        Thread t = new Thread(this::acceptLoop, "velvet-media");
        t.setDaemon(true);
        t.start();
    }

    public void stop() {
        running = false;
        try { server.close(); } catch (IOException ignored) {}
        pool.shutdownNow();
    }

    public void clearCache() {
        File[] fs = cacheDir.listFiles();
        if (fs == null) return;
        for (File f : fs) f.delete();
    }

    private void acceptLoop() {
        while (running) {
            try { pool.execute(new Conn(server.accept())); }
            catch (IOException e) { if (running) log.line("accept: " + e.getMessage()); }
        }
    }

    private class Conn implements Runnable {
        private final Socket sock;
        Conn(Socket s) { this.sock = s; }

        @Override public void run() {
            try (Socket s = sock) {
                s.setSoTimeout(60000);
                BufferedInputStream in = new BufferedInputStream(s.getInputStream());
                BufferedOutputStream out = new BufferedOutputStream(s.getOutputStream(), 32768);

                String reqLine = readLine(in);
                if (reqLine == null || reqLine.isEmpty()) return;
                String[] parts = reqLine.split(" ");
                if (parts.length < 2) return;
                String method = parts[0], target = parts[1];

                String range = null, line;
                while ((line = readLine(in)) != null && !line.isEmpty()) {
                    int c = line.indexOf(':');
                    if (c > 0 && line.substring(0, c).trim().equalsIgnoreCase("range"))
                        range = line.substring(c + 1).trim();
                }

                if (!"GET".equals(method) && !"HEAD".equals(method)) { fail(out, 405, "method"); return; }

                if (target.startsWith("/ping"))  { text(out, 200, "ok"); return; }
                if (target.startsWith("/clear")) { clearCache(); text(out, 200, "cleared"); return; }
                if (!target.startsWith("/m"))    { fail(out, 404, "not found"); return; }

                serve(out, target, range, "HEAD".equals(method));
            } catch (Exception e) { log.line("conn: " + e.getMessage()); }
        }
    }

    private void serve(OutputStream out, String target, String range, boolean isHead) throws IOException {
        int q = target.indexOf('?');
        if (q < 0) { fail(out, 400, "missing query"); return; }

        Map<String, String> p = query(target.substring(q + 1));
        String url = p.get("u");
        if (url == null || url.isEmpty()) { fail(out, 400, "missing u"); return; }
        if (url.startsWith("//")) url = "https:" + url;

        String ref = p.get("r");
        if (ref != null && ref.isEmpty()) ref = null;

        String key = sha1(url + "\n" + (ref == null ? "" : ref));
        File cf = new File(cacheDir, key);
        File tf = new File(cacheDir, key + ".type");

        if (range == null && cf.isFile() && cf.length() > 0 && tf.isFile()) {
            head(out, 200, readText(tf), String.valueOf(cf.length()), null, "bytes");
            if (!isHead) pipe(new FileInputStream(cf), out);
            out.flush();
            return;
        }

        HttpURLConnection c = null;
        try {
            c = open(url, ref, range);
            int status = c.getResponseCode();

            // retry ladder for hotlink protection
            if ((status == 401 || status == 403) && range == null) {
                c.disconnect();
                c = open(url, ref == null ? origin(url) : null, range);
                status = c.getResponseCode();
            }

            String ct = String.valueOf(c.getContentType());
            long len = c.getContentLengthLong();
            String cr = c.getHeaderField("Content-Range");
            String ar = c.getHeaderField("Accept-Ranges");

            head(out, status, ct, len >= 0 ? String.valueOf(len) : null, cr, ar);
            if (isHead) { out.flush(); return; }

            InputStream is = status >= 400 ? c.getErrorStream() : c.getInputStream();
            if (is == null) { out.flush(); return; }

            boolean cacheable = status == 200 && range == null && ct.startsWith("image/")
                    && len > 0 && len <= MAX_IMAGE_CACHE;

            OutputStream cos = null;
            if (cacheable) { try { cos = new FileOutputStream(cf); } catch (Exception e) { cos = null; } }

            byte[] buf = new byte[32768];
            int n;
            while ((n = is.read(buf)) > 0) {
                out.write(buf, 0, n);
                if (cos != null) cos.write(buf, 0, n);
            }
            out.flush();
            if (cos != null) { cos.close(); writeText(tf, ct); trimCache(); }

        } catch (Exception e) {
            try { fail(out, 502, "upstream: " + e.getMessage()); } catch (IOException ignored) {}
        } finally { if (c != null) c.disconnect(); }
    }

    private HttpURLConnection open(String url, String referer, String range) throws IOException {
        URL u = new URL(url);
        HttpURLConnection c = (HttpURLConnection) u.openConnection();
        c.setInstanceFollowRedirects(true);
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        c.setRequestProperty("User-Agent", UA);
        c.setRequestProperty("Accept-Encoding", "identity");
        c.setRequestProperty("Accept", "image/avif,image/webp,image/*,video/*,*/*;q=0.8");
        if (referer != null) c.setRequestProperty("Referer", referer);
        if (range != null)   c.setRequestProperty("Range", range);
        return c;
    }

    private void head(OutputStream out, int status, String ct, String len, String cr, String ar) throws IOException {
        StringBuilder sb = new StringBuilder(256);
        sb.append("HTTP/1.1 ").append(status).append(' ').append(reason(status)).append("\r\n");
        sb.append("Content-Type: ")
          .append(ct == null || ct.equals("null") ? "application/octet-stream" : ct).append("\r\n");
        if (len != null) sb.append("Content-Length: ").append(len).append("\r\n");
        if (cr != null)  sb.append("Content-Range: ").append(cr).append("\r\n");
        sb.append("Accept-Ranges: ").append(ar == null || ar.equals("null") ? "bytes" : ar).append("\r\n");
        sb.append("Access-Control-Allow-Origin: *\r\n");
        sb.append("Cache-Control: max-age=86400\r\n");
        sb.append("Connection: close\r\n\r\n");
        out.write(sb.toString().getBytes("UTF-8"));
    }

    private void fail(OutputStream out, int code, String msg) throws IOException {
        byte[] b = msg.getBytes("UTF-8");
        head(out, code, "text/plain; charset=utf-8", String.valueOf(b.length), null, null);
        out.write(b); out.flush();
    }

    private void text(OutputStream out, int code, String msg) throws IOException {
        byte[] b = msg.getBytes("UTF-8");
        head(out, code, "text/plain; charset=utf-8", String.valueOf(b.length), null, null);
        out.write(b); out.flush();
    }

    private static void pipe(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[32768];
        int n;
        try (InputStream i = in) { while ((n = i.read(buf)) > 0) out.write(buf, 0, n); }
    }

    private void trimCache() {
        File[] fs = cacheDir.listFiles();
        if (fs == null || fs.length < 500) return;
        Arrays.sort(fs, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
        for (int i = 0; i < fs.length - 400; i++) fs[i].delete();
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream(128);
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') break;
            if (c != '\r') b.write(c);
        }
        if (c == -1 && b.size() == 0) return null;
        return b.toString("UTF-8");
    }

    private static Map<String, String> query(String s) {
        Map<String, String> m = new HashMap<>();
        for (String pair : s.split("&")) {
            int i = pair.indexOf('=');
            if (i < 0) continue;
            try {
                m.put(URLDecoder.decode(pair.substring(0, i), "UTF-8"),
                      URLDecoder.decode(pair.substring(i + 1), "UTF-8"));
            } catch (Exception ignored) {}
        }
        return m;
    }

    private static String origin(String url) {
        try {
            URL u = new URL(url);
            return u.getProtocol() + "://" + u.getHost() + (u.getPort() > 0 ? ":" + u.getPort() : "") + "/";
        } catch (Exception e) { return null; }
    }

    private static String sha1(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] d = md.digest(s.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b & 0xff));
            return sb.toString();
        } catch (Exception e) { return String.valueOf(s.hashCode()); }
    }

    private static String readText(File f) {
        try (InputStream in = new FileInputStream(f)) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[1024];
            int n;
            while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
            return b.toString("UTF-8");
        } catch (Exception e) { return "application/octet-stream"; }
    }

    private static void writeText(File f, String s) {
        try (OutputStream o = new FileOutputStream(f)) { o.write(s.getBytes("UTF-8")); }
        catch (Exception ignored) {}
    }

    private static String reason(int code) {
        switch (code) {
            case 200: return "OK";
            case 206: return "Partial Content";
            case 400: return "Bad Request";
            case 401: return "Unauthorized";
            case 403: return "Forbidden";
            case 404: return "Not Found";
            case 405: return "Method Not Allowed";
            case 416: return "Range Not Satisfiable";
            case 502: return "Bad Gateway";
            default:  return "Status";
        }
    }
}
