package com.xiaobo.electronic;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * 纪念日数据层。
 *
 * <p>原先纪念日的 JSON 读写、天数计算都写在 MainActivity 里，UI 和数据搅在一起——
 * 想给"距今多少天"写个单测都无从下手。这里把存取与计算收口，
 * Activity 只负责展示，计算逻辑可以独立测试。
 */
public final class AnniversaryStore {

    private AnniversaryStore() {
    }

    public static final class Entry {
        public final String date;
        public final String label;
        public final String lines;

        Entry(String date, String label, String lines) {
            this.date = date;
            this.label = label;
            this.lines = lines;
        }
    }

    private static final String KEY = "anniversary_list";

    public static List<Entry> load() {
        List<Entry> out = new ArrayList<>();
        try {
            org.json.JSONArray arr = new org.json.JSONArray(
                    com.tencent.mmkv.MMKV.defaultMMKV().decodeString(KEY, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                org.json.JSONObject o = arr.getJSONObject(i);
                out.add(new Entry(o.optString("date"),
                        o.optString("label"),
                        o.optString("lines", "遇见你真好")));
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    public static void add(String label, String lines) {
        try {
            org.json.JSONArray arr = new org.json.JSONArray(
                    com.tencent.mmkv.MMKV.defaultMMKV().decodeString(KEY, "[]"));
            org.json.JSONObject o = new org.json.JSONObject();
            o.put("date", new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
                    .format(new java.util.Date()));
            o.put("label", label);
            o.put("lines", lines);
            arr.put(o);
            com.tencent.mmkv.MMKV.defaultMMKV().encode(KEY, arr.toString());
        } catch (Exception ignored) {
        }
    }

    public static void clearAll() {
        com.tencent.mmkv.MMKV.defaultMMKV().encode(KEY, "[]");
    }

    /**
     * 距今天数文案，如"距今 328 天（第 2 年）"。
     * 按自然日计算（两端都归零到当天 0 点），避免"今天 23 点 vs 那天 1 点"算出 0.9 天被截断成 0 天。
     */
    public static String daysLabel(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) return "";
        try {
            java.text.SimpleDateFormat sdf =
                    new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.CHINA);
            java.util.Date d = sdf.parse(dateStr);
            if (d == null) return "";
            Calendar today = Calendar.getInstance();
            Calendar that = Calendar.getInstance();
            that.setTime(d);
            trimToDay(today);
            trimToDay(that);
            long days = (today.getTimeInMillis() - that.getTimeInMillis()) / 86400000L;
            if (days == 0) return "就是今天";
            if (days < 0) return "还有 " + (-days) + " 天";
            long years = days / 365;
            return "距今 " + days + " 天" + (years >= 1 ? "（第 " + (years + 1) + " 年）" : "");
        } catch (Exception e) {
            return "";
        }
    }

    private static void trimToDay(Calendar c) {
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
    }
}
