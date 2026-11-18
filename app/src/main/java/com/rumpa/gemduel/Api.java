package com.rumpa.gemduel;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Map;

/** Minimal HTTP client for the Gem Pop PHP server. Callbacks run on a background thread. */
public class Api {
    public interface CB {
        void ok(JSONObject o);
        void err(String e);
    }

    public static void post(final String base, final String file, final Map<String, String> params, final CB cb) {
        new Thread(() -> {
            try {
                StringBuilder sb = new StringBuilder();
                for (Map.Entry<String, String> e : params.entrySet()) {
                    if (sb.length() > 0) sb.append('&');
                    sb.append(URLEncoder.encode(e.getKey(), "UTF-8")).append('=')
                            .append(URLEncoder.encode(e.getValue(), "UTF-8"));
                }
                HttpURLConnection c = (HttpURLConnection) new URL(base + "/" + file).openConnection();
                c.setRequestMethod("POST");
                c.setConnectTimeout(8000);
                c.setReadTimeout(12000);
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                OutputStream o = c.getOutputStream();
                o.write(sb.toString().getBytes("UTF-8"));
                o.close();
                int rc = c.getResponseCode();
                InputStream is = rc >= 400 ? c.getErrorStream() : c.getInputStream();
                StringBuilder txt = new StringBuilder();
                if (is != null) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
                    String l;
                    while ((l = br.readLine()) != null) txt.append(l);
                    br.close();
                }
                JSONObject j = new JSONObject(txt.toString());
                if (j.optBoolean("ok", false)) cb.ok(j);
                else cb.err(j.optString("error", "Server error " + rc));
            } catch (Exception e) {
                cb.err(e.getMessage() == null ? "Network error" : e.getMessage());
            }
        }).start();
    }

    public static Bitmap bitmap(String url) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(8000);
            InputStream is = c.getInputStream();
            Bitmap b = BitmapFactory.decodeStream(is);
            is.close();
            return b == null ? null : Bitmap.createScaledBitmap(b, 96, 96, true);
        } catch (Exception e) {
            return null;
        }
    }
}
