package com.xiaobo.electronic.data;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 告白回忆册数据层。
 *
 * <p>每次告白演出自动落一条：台词、日期时刻、当晚月相星座、结果（我愿意 / 再想想）。
 * 不需要用户手动存——演完就是一段回忆。按时间倒序存，上限 60 条（约够记两年，
 * 再老的故事也该翻篇了）。
 *
 * <p>与 {@link AnniversaryStore} 的分工：纪念日只记"成功那一天"，用于倒数提醒；
 * 回忆册记**每一次完整演出**（含没成功的），用于回看当时的台词与氛围。两者互不干扰。
 *
 * <p>存 MMKV 的 JSON 数组，纯本地。
 */
public final class MemoryAlbumStore {

    private MemoryAlbumStore() {
    }

    public static final int RESULT_YES = 1;
    public static final int RESULT_THINK = 2;

    public static final class Entry {
        public final long ts;
        public final String lines;
        public final String zodiac;
        public final float moonAge;
        public final String result;      // "我愿意" / "再想想"
        public final String herName;
        public final String myName;

        public Entry(long ts, String lines, String zodiac, float moonAge,
                     String result, String herName, String myName) {
            this.ts = ts;
            this.lines = lines;
            this.zodiac = zodiac;
            this.moonAge = moonAge;
            this.result = result;
            this.herName = herName;
            this.myName = myName;
        }

        /** 列表里的一行标题：日期 + 结果 */
        public String title() {
            String day = new SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.CHINA)
                    .format(new Date(ts));
            return day + "  " + (RESULT_YES == resultCode() ? "💗 我愿意" : "🤔 再想想");
        }

        private int resultCode() {
            return "我愿意".equals(result) ? RESULT_YES : RESULT_THINK;
        }
    }

    private static final String KEY = "memory_album_list";
    private static final int MAX = 60;

    public static List<Entry> load() {
        List<Entry> out = new ArrayList<>();
        try {
            org.json.JSONArray arr = new org.json.JSONArray(
                    com.tencent.mmkv.MMKV.defaultMMKV().decodeString(KEY, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                org.json.JSONObject o = arr.getJSONObject(i);
                out.add(new Entry(
                        o.optLong("ts"),
                        o.optString("lines"),
                        o.optString("zodiac"),
                        (float) o.optDouble("moonAge", 0),
                        o.optString("result", "我愿意"),
                        o.optString("herName"),
                        o.optString("myName")));
            }
        } catch (Exception ignored) {
        }
        // 时间倒序：最新的一段在最上面
        out.sort(Comparator.comparingLong((Entry e) -> e.ts).reversed());
        return out;
    }

    /** 演出结束时调用一次 */
    public static void add(long ts, String lines, String zodiac, float moonAge,
                           int result, String herName, String myName) {
        try {
            org.json.JSONArray arr = new org.json.JSONArray(
                    com.tencent.mmkv.MMKV.defaultMMKV().decodeString(KEY, "[]"));
            org.json.JSONObject o = new org.json.JSONObject();
            o.put("ts", ts);
            o.put("lines", lines == null ? "" : lines);
            o.put("zodiac", zodiac == null ? "" : zodiac);
            o.put("moonAge", moonAge);
            o.put("result", result == RESULT_THINK ? "再想想" : "我愿意");
            o.put("herName", herName == null ? "" : herName);
            o.put("myName", myName == null ? "" : myName);
            arr.put(o);
            // 保留最近 MAX 条：JSON 数组是按写入顺序，倒序删老的
            while (arr.length() > MAX) arr.remove(0);
            com.tencent.mmkv.MMKV.defaultMMKV().encode(KEY, arr.toString());
        } catch (Exception ignored) {
        }
    }

    public static void clearAll() {
        com.tencent.mmkv.MMKV.defaultMMKV().encode(KEY, "[]");
    }

    public static int count() {
        return com.tencent.mmkv.MMKV.defaultMMKV().decodeString(KEY, "[]").equals("[]")
                ? 0 : load().size();
    }
}
