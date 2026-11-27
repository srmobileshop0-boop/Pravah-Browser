package com.pravah.browser;

import android.content.ContentValues;
import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.webkit.CookieManager;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Downloads an HLS (.m3u8) video: picks the best quality, fetches every segment (with the page's
 * cookies / referer), decrypts AES-128 streams and saves a playable MP4 in Downloads/Pravah.
 */
final class HlsDownloader {

    interface Listener {
        void progress(long done, long total);

        void done(String name, String uri, long size);

        void fail(String why);
    }

    private static final class Seg {
        String url;
        byte[] key;
        byte[] iv;
    }

    private static final class Out {
        Uri uri;
        File file;
    }

    private final Context ctx;
    private final String ua;
    private final String referer;

    HlsDownloader(Context c, String userAgent, String referer) {
        this.ctx = c.getApplicationContext();
        this.ua = userAgent == null || userAgent.isEmpty() ? "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36" : userAgent;
        this.referer = referer;
    }

    // ------------------------------------------------------------------ HTTP

    private HttpURLConnection open(String u) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
        c.setConnectTimeout(20000);
        c.setReadTimeout(30000);
        c.setInstanceFollowRedirects(false);
        c.setRequestProperty("User-Agent", ua);
        c.setRequestProperty("Accept", "*/*");
        if (referer != null && referer.startsWith("http")) {
            c.setRequestProperty("Referer", referer);
            try {
                Uri r = Uri.parse(referer);
                if (r.getScheme() != null && r.getHost() != null) c.setRequestProperty("Origin", r.getScheme() + "://" + r.getHost());
            } catch (Exception ignored) {
            }
        }
        try {
            String ck = CookieManager.getInstance().getCookie(u);
            if (ck != null) c.setRequestProperty("Cookie", ck);
        } catch (Exception ignored) {
        }
        return c;
    }

    private static String resolve(String base, String rel) throws IOException {
        try {
            return new URL(new URL(base), rel).toString();
        } catch (Exception e) {
            throw new IOException("bad url");
        }
    }

    private byte[] get(String u) throws IOException {
        IOException last = null;
        for (int attempt = 0; attempt < 5; attempt++) {
            String cur = u;
            HttpURLConnection c = null;
            try {
                for (int hop = 0; hop < 6; hop++) {
                    c = open(cur);
                    int code = c.getResponseCode();
                    if (code >= 300 && code < 400) {
                        String loc = c.getHeaderField("Location");
                        c.disconnect();
                        c = null;
                        if (loc == null) throw new IOException("redirect");
                        cur = resolve(cur, loc);
                        continue;
                    }
                    if (code >= 400) throw new IOException("HTTP " + code);
                    InputStream in = c.getInputStream();
                    ByteArrayOutputStream bo = new ByteArrayOutputStream(1 << 16);
                    byte[] buf = new byte[16384];
                    int n;
                    while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
                    in.close();
                    return bo.toByteArray();
                }
                throw new IOException("too many redirects");
            } catch (IOException e) {
                last = e;
                try {
                    Thread.sleep(400L * (attempt + 1));
                } catch (InterruptedException ignored) {
                }
            } finally {
                if (c != null) c.disconnect();
            }
        }
        throw last == null ? new IOException("failed") : last;
    }

    private String text(String u) throws IOException {
        return new String(get(u), "UTF-8");
    }

    private byte[] fetchSeg(Seg g) throws IOException {
        byte[] d = get(g.url);
        if (g.key != null) {
            try {
                Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding");
                c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(g.key, "AES"), new IvParameterSpec(g.iv));
                d = c.doFinal(d);
            } catch (Exception e) {
                throw new IOException("decrypt failed");
            }
        }
        return d;
    }

    /** Qualities of a master playlist as {label, url, bandwidth}, best first. Empty when it is not a master playlist. */
    ArrayList<String[]> variants(String url) {
        ArrayList<String[]> out = new ArrayList<>();
        try {
            String body = text(url);
            if (!body.contains("#EXT-X-STREAM-INF")) return out;
            String[] ls = body.split("\\r?\\n");
            for (int i = 0; i < ls.length; i++) {
                String s = ls[i].trim();
                if (!s.startsWith("#EXT-X-STREAM-INF")) continue;
                String res = attr(s, "RESOLUTION");
                long bw = attrLong(s, "BANDWIDTH");
                for (int j = i + 1; j < ls.length; j++) {
                    String u = ls[j].trim();
                    if (u.isEmpty() || u.startsWith("#")) continue;
                    String label = "Video";
                    if (res != null && res.indexOf('x') > 0) label = res.substring(res.indexOf('x') + 1).trim() + "p";
                    else if (bw > 0) label = (bw / 1000) + " kbps";
                    boolean dup = false;
                    for (String[] o : out) if (o[0].equals(label)) dup = true;
                    if (!dup) out.add(new String[]{label, resolve(url, u), String.valueOf(Math.max(bw, 0))});
                    break;
                }
            }
        } catch (Exception ignored) {
        }
        java.util.Collections.sort(out, new java.util.Comparator<String[]>() {
            @Override
            public int compare(String[] a, String[] b) {
                return Long.compare(Long.parseLong(b[2]), Long.parseLong(a[2]));
            }
        });
        return out;
    }

    // ---------------------------------------------------------- helpers for the caller

    /** True when this playlist lists other playlists (a "master" playlist). */
    boolean isMaster(String url) {
        try {
            return text(url).contains("#EXT-X-STREAM-INF");
        } catch (Exception e) {
            return false;
        }
    }

    /** Size of a file in bytes, or -1 when the server does not say. */
    long contentLength(String url) {
        HttpURLConnection c = null;
        try {
            String cur = url;
            for (int hop = 0; hop < 5; hop++) {
                c = open(cur);
                c.setRequestMethod("HEAD");
                int code = c.getResponseCode();
                if (code >= 300 && code < 400) {
                    String loc = c.getHeaderField("Location");
                    c.disconnect();
                    c = null;
                    if (loc == null) return -1;
                    cur = resolve(cur, loc);
                    continue;
                }
                if (code >= 400) return -1;
                return c.getContentLengthLong();
            }
        } catch (Exception ignored) {
        } finally {
            if (c != null) c.disconnect();
        }
        return -1;
    }

    // ------------------------------------------------------------------ playlist parsing

    private static String attr(String line, String name) {
        Matcher m = Pattern.compile("[:,]" + name + "=(\"([^\"]*)\"|[^,]*)").matcher(line);
        if (!m.find()) return null;
        return m.group(2) != null ? m.group(2) : m.group(1);
    }

    private static long attrLong(String line, String name) {
        try {
            String v = attr(line, name);
            return v == null ? -1 : Long.parseLong(v.trim());
        } catch (Exception e) {
            return -1;
        }
    }

    private static byte[] parseIv(String hex) {
        if (hex == null) return null;
        String h = hex.trim();
        if (h.startsWith("0x") || h.startsWith("0X")) h = h.substring(2);
        while (h.length() < 32) h = "0" + h;
        if (h.length() > 32) h = h.substring(h.length() - 32);
        byte[] b = new byte[16];
        for (int i = 0; i < 16; i++) b[i] = (byte) Integer.parseInt(h.substring(i * 2, i * 2 + 2), 16);
        return b;
    }

    private static byte[] seqIv(long seq) {
        byte[] b = new byte[16];
        for (int i = 15; i >= 8; i--) {
            b[i] = (byte) (seq & 0xff);
            seq >>= 8;
        }
        return b;
    }

    // ------------------------------------------------------------------ main job

    void run(String playlistUrl, String nameBase, Listener l) {
        try {
            String base = playlistUrl;
            String body = text(base);
            for (int depth = 0; depth < 3 && body.contains("#EXT-X-STREAM-INF"); depth++) {
                String best = null;
                long bw = -1;
                String[] ls = body.split("\\r?\\n");
                for (int i = 0; i < ls.length; i++) {
                    String s = ls[i].trim();
                    if (!s.startsWith("#EXT-X-STREAM-INF")) continue;
                    long b = attrLong(s, "BANDWIDTH");
                    for (int j = i + 1; j < ls.length; j++) {
                        String u = ls[j].trim();
                        if (u.isEmpty() || u.startsWith("#")) continue;
                        if (best == null || b > bw) {
                            bw = b;
                            best = resolve(base, u);
                        }
                        break;
                    }
                }
                if (best == null) break;
                base = best;
                body = text(base);
            }

            List<Seg> segs = new ArrayList<>();
            String mapUrl = null;
            boolean aes = false;
            String keyUrl = null;
            byte[] ivAttr = null;
            long seq = 0;
            HashMap<String, byte[]> keys = new HashMap<>();
            for (String raw : body.split("\\r?\\n")) {
                String s = raw.trim();
                if (s.isEmpty()) continue;
                if (s.startsWith("#EXT-X-MEDIA-SEQUENCE:")) {
                    try {
                        seq = Long.parseLong(s.substring(22).trim());
                    } catch (Exception ignored) {
                    }
                } else if (s.startsWith("#EXT-X-MAP:")) {
                    String u = attr(s, "URI");
                    if (u != null) mapUrl = resolve(base, u);
                } else if (s.startsWith("#EXT-X-KEY:")) {
                    String m = attr(s, "METHOD");
                    if ("AES-128".equalsIgnoreCase(m)) {
                        String ku = attr(s, "URI");
                        if (ku == null) {
                            l.fail("encrypted stream");
                            return;
                        }
                        aes = true;
                        keyUrl = resolve(base, ku);
                        ivAttr = parseIv(attr(s, "IV"));
                    } else if (m != null && !"NONE".equalsIgnoreCase(m)) {
                        l.fail("this stream is DRM protected");
                        return;
                    } else {
                        aes = false;
                        keyUrl = null;
                        ivAttr = null;
                    }
                } else if (!s.startsWith("#")) {
                    Seg g = new Seg();
                    g.url = resolve(base, s);
                    if (aes && keyUrl != null) {
                        byte[] k = keys.get(keyUrl);
                        if (k == null) {
                            k = get(keyUrl);
                            keys.put(keyUrl, k);
                        }
                        g.key = k;
                        g.iv = ivAttr != null ? ivAttr : seqIv(seq);
                    }
                    segs.add(g);
                    seq++;
                    if (segs.size() > 8000) break;
                }
            }
            if (segs.isEmpty()) {
                l.fail("no video found");
                return;
            }

            File tmp = new File(ctx.getCacheDir(), "hls_" + System.nanoTime() + ".bin");
            long written = 0;
            OutputStream os = new BufferedOutputStream(new FileOutputStream(tmp), 1 << 16);
            java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(4);
            try {
                if (mapUrl != null) {
                    byte[] init = get(mapUrl);
                    os.write(init);
                    written += init.length;
                }
                java.util.ArrayDeque<java.util.concurrent.Future<byte[]>> q = new java.util.ArrayDeque<>();
                int next = 0;
                for (int i = 0; i < segs.size(); i++) {
                    while (next < segs.size() && q.size() < 8) {
                        final Seg g = segs.get(next++);
                        q.add(pool.submit(new java.util.concurrent.Callable<byte[]>() {
                            @Override
                            public byte[] call() throws Exception {
                                return fetchSeg(g);
                            }
                        }));
                    }
                    byte[] d = q.poll().get();
                    os.write(d);
                    written += d.length;
                    l.progress(written, written * segs.size() / (i + 1));
                }
            } catch (java.util.concurrent.ExecutionException e) {
                try {
                    os.close();
                } catch (IOException ignored) {
                }
                tmp.delete();
                Throwable c = e.getCause();
                throw (c instanceof IOException) ? (IOException) c : new IOException("segment failed");
            } catch (InterruptedException e) {
                try {
                    os.close();
                } catch (IOException ignored) {
                }
                tmp.delete();
                throw new IOException("cancelled");
            } catch (IOException e) {
                try {
                    os.close();
                } catch (IOException ignored) {
                }
                tmp.delete();
                throw e;
            } finally {
                pool.shutdownNow();
            }
            os.close();

            String stamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new java.util.Date());
            String name = (nameBase == null || nameBase.trim().isEmpty()) ? "Pravah_" + stamp : nameBase.trim();
            // always re-wrap into a clean MP4 so seeking works; fall back to the raw file only if that fails
            if (remux(tmp, name + ".mp4", l)) {
                tmp.delete();
                return;
            }
            if (mapUrl != null) {
                finishRaw(tmp, name + ".mp4", "video/mp4", l);
                return;
            }
            finishRaw(tmp, name + ".ts", "video/mp2t", l);
        } catch (IOException e) {
            String m = e.getMessage();
            l.fail(m == null ? "network error" : m);
        } catch (Throwable t) {
            l.fail("unexpected error");
        }
    }

    // ------------------------------------------------------------------ saving

    private Out newOut(String name, String mime) throws IOException {
        Out o = new Out();
        if (Build.VERSION.SDK_INT >= 29) {
            ContentValues cv = new ContentValues();
            cv.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
            cv.put(MediaStore.MediaColumns.MIME_TYPE, mime);
            cv.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Pravah");
            cv.put(MediaStore.MediaColumns.IS_PENDING, 1);
            o.uri = ctx.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
            if (o.uri == null) throw new IOException("cannot create file");
        } else {
            File dir = ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            if (dir == null) dir = ctx.getFilesDir();
            o.file = new File(dir, name);
        }
        return o;
    }

    private void publish(Out o) {
        if (o.uri == null) return;
        try {
            ContentValues cv = new ContentValues();
            cv.put(MediaStore.MediaColumns.IS_PENDING, 0);
            ctx.getContentResolver().update(o.uri, cv, null, null);
        } catch (Exception ignored) {
        }
    }

    private void discard(Out o) {
        try {
            if (o.uri != null) ctx.getContentResolver().delete(o.uri, null, null);
            else if (o.file != null) o.file.delete();
        } catch (Exception ignored) {
        }
    }

    private static String uriString(Out o) {
        return o.uri != null ? o.uri.toString() : Uri.fromFile(o.file).toString();
    }

    private void finishRaw(File tmp, String name, String mime, Listener l) {
        Out o = null;
        try {
            o = newOut(name, mime);
            OutputStream os = o.uri != null ? ctx.getContentResolver().openOutputStream(o.uri) : new FileOutputStream(o.file);
            if (os == null) throw new IOException("cannot open file");
            InputStream in = new FileInputStream(tmp);
            byte[] b = new byte[1 << 16];
            int n;
            long total = 0;
            while ((n = in.read(b)) > 0) {
                os.write(b, 0, n);
                total += n;
            }
            in.close();
            os.close();
            publish(o);
            tmp.delete();
            l.done(name, uriString(o), total);
        } catch (Exception e) {
            if (o != null) discard(o);
            tmp.delete();
            l.fail("could not save the file");
        }
    }

    /** True when this video sample starts with an IDR frame (so the player can seek to it). */
    private static boolean looksLikeIdr(ByteBuffer buf, int sz, String mime) {
        int lim = Math.min(sz, 512);
        boolean hevc = mime != null && mime.contains("hevc");
        for (int i = 0; i + 4 < lim; i++) {
            if (buf.get(i) == 0 && buf.get(i + 1) == 0 && (buf.get(i + 2) == 1 || (buf.get(i + 2) == 0 && buf.get(i + 3) == 1))) {
                int p = buf.get(i + 2) == 1 ? i + 3 : i + 4;
                if (p >= lim) break;
                int b0 = buf.get(p) & 0xff;
                if (hevc) {
                    int type = (b0 >> 1) & 0x3f;
                    if (type >= 16 && type <= 21) return true;
                } else if ((b0 & 0x1f) == 5) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Re-wraps an MPEG-TS file into a normal MP4 (no re-encoding). Returns false when that is not possible. */
    private boolean remux(File src, String name, Listener l) {
        Out o = null;
        ParcelFileDescriptor pfd = null;
        MediaExtractor ex = new MediaExtractor();
        MediaMuxer mux = null;
        boolean started = false;
        boolean ok = false;
        try {
            ex.setDataSource(src.getAbsolutePath());
            int n = ex.getTrackCount();
            if (n == 0) return false;
            o = newOut(name, "video/mp4");
            if (o.uri != null) {
                pfd = ctx.getContentResolver().openFileDescriptor(o.uri, "rw");
                if (pfd == null) throw new IOException("cannot open file");
                mux = new MediaMuxer(pfd.getFileDescriptor(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            } else {
                mux = new MediaMuxer(o.file.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            }
            int[] map = new int[n];
            boolean[] isVideo = new boolean[n];
            String[] mimes = new String[n];
            int added = 0;
            boolean hasVideo = false;
            for (int i = 0; i < n; i++) {
                map[i] = -1;
                MediaFormat f = ex.getTrackFormat(i);
                String mime = f.getString(MediaFormat.KEY_MIME);
                mimes[i] = mime;
                if (mime != null && (mime.startsWith("video/") || mime.startsWith("audio/"))) {
                    try {
                        map[i] = mux.addTrack(f);
                        ex.selectTrack(i);
                        added++;
                        if (mime.startsWith("video/")) {
                            hasVideo = true;
                            isVideo[i] = true;
                        }
                    } catch (Exception e) {
                        map[i] = -1;
                    }
                }
            }
            if (added == 0 || !hasVideo) throw new IOException("no tracks");
            mux.start();
            started = true;
            ByteBuffer buf = ByteBuffer.allocate(4 * 1024 * 1024);
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            long[] lastT = new long[n];
            long[] offT = new long[n];
            long baseT = Long.MIN_VALUE;
            while (true) {
                int sz = ex.readSampleData(buf, 0);
                if (sz < 0) break;
                int ti = ex.getSampleTrackIndex();
                if (ti >= 0 && ti < n && map[ti] >= 0) {
                    long t = ex.getSampleTime();
                    if (baseT == Long.MIN_VALUE) baseT = t;
                    long adj = t - baseT + offT[ti];
                    // a stream that jumps back in time (joined segments) is made continuous again
                    if (adj < lastT[ti] - 1000000L) {
                        offT[ti] += lastT[ti] - adj + 33333L;
                        adj = t - baseT + offT[ti];
                    }
                    if (adj > lastT[ti]) lastT[ti] = adj;
                    info.offset = 0;
                    info.size = sz;
                    info.presentationTimeUs = Math.max(0L, adj);
                    boolean key = (ex.getSampleFlags() & MediaExtractor.SAMPLE_FLAG_SYNC) != 0 || (isVideo[ti] && looksLikeIdr(buf, sz, mimes[ti]));
                    info.flags = key ? MediaCodec.BUFFER_FLAG_KEY_FRAME : 0;
                    mux.writeSampleData(map[ti], buf, info);
                }
                ex.advance();
            }
            mux.stop();
            started = false;
            mux.release();
            mux = null;
            long size = pfd != null ? pfd.getStatSize() : o.file.length();
            if (pfd != null) {
                pfd.close();
                pfd = null;
            }
            publish(o);
            ok = true;
            l.done(name, uriString(o), size);
            return true;
        } catch (Throwable t) {
            return false;
        } finally {
            try {
                ex.release();
            } catch (Throwable ignored) {
            }
            if (mux != null) {
                try {
                    if (started) mux.stop();
                } catch (Throwable ignored) {
                }
                try {
                    mux.release();
                } catch (Throwable ignored) {
                }
            }
            if (pfd != null) {
                try {
                    pfd.close();
                } catch (Throwable ignored) {
                }
            }
            if (!ok && o != null) discard(o);
        }
    }
}
