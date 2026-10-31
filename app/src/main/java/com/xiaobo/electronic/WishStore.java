package com.xiaobo.electronic;

import java.util.ArrayList;
import java.util.List;

/**
 * 星空许愿：把想说的话挂到星星上。
 *
 * <p>这个 App 里最贵的不是特效，是"你们之间的东西"。
 * 许下的愿望留下坐标，下次打开还在原来的位置闪——
 * 像一片只属于两个人的星空。
 *
 * <p>坐标存的是 0~1 的比例而不是像素：横竖屏、不同分辨率下位置都不会跑偏。
 */
public final class WishStore {

    public static final int MAX = 60;

    public static final class Wish {
        public final String text;
        public final long ts;
        public final float x;
        public final float y;

        Wish(String text, long ts, float x, float y) {
            this.text = text;
            this.ts = ts;
            this.x = x;
            this.y = y;
        }
    }

    private static final String KEY = "wish_list";

    private WishStore() {
    }

    public static List<Wish> load() {
        List<Wish> out = new ArrayList<>();
        try {
            org.json.JSONArray arr = new org.json.JSONArray(
                    com.tencent.mmkv.MMKV.defaultMMKV().decodeString(KEY, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                org.json.JSONObject o = arr.getJSONObject(i);
                out.add(new Wish(o.optString("text", ""),
                        o.optLong("ts", 0L),
                        (float) o.optDouble("x", 0.5),
                        (float) o.optDouble("y", 0.5)));
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private static void save(List<Wish> list) {
        try {
            org.json.JSONArray arr = new org.json.JSONArray();
            int n = Math.min(list.size(), MAX);
            for (int i = 0; i < n; i++) {
                Wish w = list.get(i);
                org.json.JSONObject o = new org.json.JSONObject();
                o.put("text", w.text);
                o.put("ts", w.ts);
                o.put("x", w.x);
                o.put("y", w.y);
                arr.put(o);
            }
            com.tencent.mmkv.MMKV.defaultMMKV().encode(KEY, arr.toString());
        } catch (Exception ignored) {
        }
    }

    public static boolean add(String text, float x, float y) {
        if (text == null || text.trim().isEmpty()) return false;
        List<Wish> list = load();
        if (list.size() >= MAX) return false;
        // 位置太挤就自动让开一点，免得两颗星叠在一起点不中
        for (Wish w : list) {
            float dx = w.x - x, dy = w.y - y;
            if (dx * dx + dy * dy < 0.0016f) {
                x = Math.min(0.95f, x + 0.06f);
                y = Math.min(0.92f, y + 0.05f);
            }
        }
        list.add(new Wish(text.trim(), System.currentTimeMillis(), x, y));
        save(list);
        return true;
    }

    public static void remove(long ts) {
        List<Wish> list = load();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).ts == ts) {
                list.remove(i);
                save(list);
                return;
            }
        }
    }

    public static void clear() {
        com.tencent.mmkv.MMKV.defaultMMKV().encode(KEY, "[]");
    }

    /** 相对时间文案：刚刚 / 3 天前 / 2 个月前 */
    public static String ago(long ts) {
        long d = System.currentTimeMillis() - ts;
        if (d < 60_000L) return "刚刚";
        if (d < 3_600_000L) return (d / 60_000L) + " 分钟前";
        if (d < 86_400_000L) return (d / 3_600_000L) + " 小时前";
        if (d < 2_592_000_000L) return (d / 86_400_000L) + " 天前";
        return (d / 2_592_000_000L) + " 个月前";
    }
}
