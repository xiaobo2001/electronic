package com.xiaobo.electronic.speech;

import android.content.Context;

import com.tencent.mmkv.MMKV;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import com.xiaobo.electronic.App;

/**
 * 模型整包的「在线源清单」。
 *
 * <p><b>解决什么问题。</b>网盘直链是逆向接口，域名一年换好几次
 * （lanzous → lanzoui → lanzoux → lanzouy）。把直链写死在 App 里，
 * 链接一失效，所有用户都下不了模型，而修只能靠发版——用户不更新就一直坏着。
 *
 * <p>加了这层之后：App 启动时后台拉一个 1KB 的 JSON，拿到当前最新的直链存本地；
 * 下载时优先用清单里的链接，拉不到清单就用内置的。将来链接失效，
 * 改 JSON 就行，用户不用更新 App，甚至不会察觉。
 *
 * <p><b>清单放哪儿。</b>任何能随时改、能公开访问的地方都行：
 * Gitee / GitHub 仓库的 raw 文件、jsDelivr CDN、自己的服务器。
 * 地址填在 {@link #MANIFEST_URLS}，可以填多个，按顺序试。
 *
 * <p>清单格式：
 * <pre>{@code
 * {
 *   "v": 1,
 *   "packs": {
 *     "tts_xiaoya":  { "zip": ["https://.../xiaoya.zip"],  "bytes": 13769076 },
 *     "tts_chaowen": { "zip": ["https://.../chaowen.zip"], "bytes": 13788275 },
 *     "tts_aishell3":{ "zip": ["https://.../aishell3.zip"],"bytes": 33434658 },
 *     "asr":         { "zip": ["https://.../asr.zip"],     "bytes": 20945582 }
 *   }
 * }
 * }</pre>
 */
public final class PackSources {

    /**
     * 清单地址。留空 = 只用内置直链（功能不受影响，只是失去"链接热更新"能力）。
     * 建议填两个以上，一个挂了还有另一个。
     */
    private static final String[] MANIFEST_URLS = {
            ModelRepo.MANIFEST,
    };

    private static final String KEY_JSON = "model_sources_json";
    private static final String KEY_TIME = "model_sources_time";
    private static final long MIN_INTERVAL = 6 * 60 * 60 * 1000L;   // 6 小时最多刷一次

    private PackSources() {
    }

    /** 启动时调一次：后台拉清单，成功就缓存。失败无所谓，下次再说 */
    public static void refreshAsync(Context ctx) {
        if (MANIFEST_URLS.length == 0) return;
        long last = 0L;
        try {
            last = MMKV.defaultMMKV().decodeLong(KEY_TIME, 0L);
        } catch (Throwable ignored) {
        }
        if (System.currentTimeMillis() - last < MIN_INTERVAL) return;
        new Thread(() -> {
            for (String u : MANIFEST_URLS) {
                String json = fetch(u);
                if (json == null || json.isEmpty()) continue;
                try {
                    MMKV.defaultMMKV().encode(KEY_JSON, json);
                    MMKV.defaultMMKV().encode(KEY_TIME, System.currentTimeMillis());
                } catch (Throwable ignored) {
                }
                return;
            }
        }, "pack-sources").start();
    }

    /**
     * 把清单里的直链并到内置直链前面。
     *
     * <p>内置的仍然保留在后面：清单里的链接万一坏了，还有内置的那条能试，
     * 再不行还有官方逐文件下载兜底。三层保险，任何一层活着都能下到模型。
     */
    public static ModelDownloader.Pack apply(ModelDownloader.Pack pack) {
        String[] remote = zipUrls(pack.dir);
        String[] merged = merge(remote, pack.zipUrls);
        long bytes = zipBytes(pack.dir, pack.zipBytes);
        if (merged == null) return pack;
        return new ModelDownloader.Pack(pack.dir, pack.files, pack.sizes,
                pack.bases, pack.baseIdx, merged, bytes);
    }

    /**
     * 合并两组链接：远程在前、内置在后，去掉空串与重复。
     * 远程优先是因为它随时能改，是"当前正确"的那份。
     */
    public static String[] merge(String[] remote, String[] builtin) {
        Set<String> out = new LinkedHashSet<>();
        if (remote != null) {
            for (String s : remote) {
                if (s != null && !s.trim().isEmpty()) out.add(s.trim());
            }
        }
        if (builtin != null) {
            for (String s : builtin) {
                if (s != null && !s.trim().isEmpty()) out.add(s.trim());
            }
        }
        if (out.isEmpty()) return null;
        return out.toArray(new String[0]);
    }

    /** 清单里该包的直链；没清单、没这项就返回 null */
    static String[] zipUrls(String key) {
        org.json.JSONObject packs = packs();
        if (packs == null) return null;
        try {
            org.json.JSONObject p = packs.optJSONObject(key);
            if (p == null) return null;
            org.json.JSONArray arr = p.optJSONArray("zip");
            if (arr == null || arr.length() == 0) return null;
            List<String> list = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                String s = arr.optString(i, "");
                if (!s.isEmpty()) list.add(s);
            }
            return list.isEmpty() ? null : list.toArray(new String[0]);
        } catch (Throwable t) {
            return null;
        }
    }

    static long zipBytes(String key, long fallback) {
        org.json.JSONObject packs = packs();
        if (packs == null) return fallback;
        try {
            org.json.JSONObject p = packs.optJSONObject(key);
            if (p == null) return fallback;
            long v = p.optLong("bytes", 0L);
            return v > 0 ? v : fallback;
        } catch (Throwable t) {
            return fallback;
        }
    }

    private static org.json.JSONObject packs() {
        String json = null;
        try {
            json = MMKV.defaultMMKV().decodeString(KEY_JSON, null);
        } catch (Throwable ignored) {
        }
        if (json == null || json.isEmpty()) return null;
        try {
            return new org.json.JSONObject(json).optJSONObject("packs");
        } catch (Throwable t) {
            return null;
        }
    }

    /** 拉清单文本；5 秒超时，失败返回 null（这是锦上添花，不该拖慢启动） */
    private static String fetch(String url) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setInstanceFollowRedirects(true);
            // 部分托管站会拦非浏览器 UA，返回一个反爬页面而不是 JSON
            conn.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                            + "(KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36");
            conn.setRequestProperty("Accept", "application/json, text/plain, */*");
            int code = conn.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) return null;
            InputStream in = conn.getInputStream();
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            in.close();
            return bos.toString("UTF-8");
        } catch (Throwable t) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

}
