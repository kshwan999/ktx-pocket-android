package kr.personal.ktxpocket;

import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** HTTPS transport using Android's certificate store and per-client cookies. */
public final class AndroidHttp {
    private final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER);

    public synchronized String exchange(String address, String method, String headersJson, String body) throws Exception {
        URI uri = URI.create(address);
        if (!"https".equals(uri.getScheme())) throw new IllegalArgumentException("HTTPS is required");
        HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
        try {
            connection.setConnectTimeout(20000);
            connection.setReadTimeout(20000);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod(method);
            JSONObject headers = new JSONObject(headersJson);
            Iterator<String> names = headers.keys();
            while (names.hasNext()) {
                String name = names.next();
                connection.setRequestProperty(name, headers.getString(name));
            }
            for (Map.Entry<String, List<String>> entry : cookies.get(uri, Collections.emptyMap()).entrySet()) {
                connection.setRequestProperty(entry.getKey(), String.join("; ", entry.getValue()));
            }
            if ("POST".equals(method)) {
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
                connection.setFixedLengthStreamingMode(bytes.length);
                try (java.io.OutputStream stream = connection.getOutputStream()) { stream.write(bytes); }
            }
            int code = connection.getResponseCode();
            cookies.put(uri, connection.getHeaderFields());
            InputStream raw = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            if (raw != null) {
                try (InputStream stream = raw) {
                    byte[] chunk = new byte[8192];
                    int count;
                    while ((count = stream.read(chunk)) != -1) {
                        if (bytes.size() + count > 8 * 1024 * 1024) throw new java.io.IOException("Response too large");
                        bytes.write(chunk, 0, count);
                    }
                }
            }
            return new JSONObject().put("status_code", code)
                    .put("text", bytes.toString(StandardCharsets.UTF_8.name())).toString();
        } finally { connection.disconnect(); }
    }
}
