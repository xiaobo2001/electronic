package com.xiaobo.electronic;

import java.util.ArrayList;
import java.util.List;

/**
 * 剧本库：把调好的一套台词存成"剧本"，下次一键重演，不必重打一遍。
 *
 * <p>存什么：名字、台词（\n 分句）、保存时间。不存主题/模板——
 * 那些是随时会变的心情，剧本只锁住"说什么"。
 *
 * <p>为什么不用数据库：到现在为止这个 App 的所有持久化都是 MMKV（纪念日、样式、偏好），
 * 剧本数量上限 30 条，一个 JSON 数组足够，多引一类存储反而多一份出错面。
 */
public final class ScriptStore {

    private static final String KEY = "script_library";
    public static final int MAX = 30;

    public static final class Entry {
        public final String id;
        public final String name;
        public final String lines;
        public final long ts;

        Entry(String id, String name, String lines, long ts) {
            this.id = id;
            this.name = name;
            this.lines = lines;
            this.ts = ts;
        }

        /** 列表里显示的副标题：第一句台词，太长就截断。 */
        public String preview() {
            if (lines == null || lines.isEmpty()) return "（空）";
            String first = lines.split("\n")[0].trim();
            return first.length() > 18 ? first.substring(0, 18) + "…" : first;
        }
    }

    private ScriptStore() {
    }

    public static List<Entry> load() {
        List<Entry> out = new ArrayList<>();
        try {
            org.json.JSONArray arr = new org.json.JSONArray(
                    com.tencent.mmkv.MMKV.defaultMMKV().decodeString(KEY, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                org.json.JSONObject o = arr.getJSONObject(i);
                out.add(new Entry(o.optString("id"),
                        o.optString("name", "未命名"),
                        o.optString("lines", ""),
                        o.optLong("ts", 0L)));
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private static void save(List<Entry> list) {
        try {
            org.json.JSONArray arr = new org.json.JSONArray();
            int n = Math.min(list.size(), MAX);
            for (int i = 0; i < n; i++) {
                Entry e = list.get(i);
                org.json.JSONObject o = new org.json.JSONObject();
                o.put("id", e.id);
                o.put("name", e.name);
                o.put("lines", e.lines);
                o.put("ts", e.ts);
                arr.put(o);
            }
            com.tencent.mmkv.MMKV.defaultMMKV().encode(KEY, arr.toString());
        } catch (Exception ignored) {
        }
    }

    public static boolean add(String name, String lines) {
        if (lines == null || lines.trim().isEmpty()) return false;
        List<Entry> list = load();
        if (list.size() >= MAX) return false;
        // 同名覆盖：用户改了几句话再存一次，应该是更新而不是多出一条重复项
        String key = (name == null || name.trim().isEmpty()) ? defaultName() : name.trim();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).name.equals(key)) {
                list.set(i, new Entry(list.get(i).id, key, lines, System.currentTimeMillis()));
                save(list);
                return true;
            }
        }
        list.add(0, new Entry(String.valueOf(System.currentTimeMillis()), key, lines,
                System.currentTimeMillis()));
        save(list);
        return true;
    }

    public static void rename(String id, String name) {
        if (name == null || name.trim().isEmpty()) return;
        List<Entry> list = load();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id.equals(id)) {
                list.set(i, new Entry(id, name.trim(), list.get(i).lines, list.get(i).ts));
                save(list);
                return;
            }
        }
    }

    public static void remove(String id) {
        List<Entry> list = load();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id.equals(id)) {
                list.remove(i);
                save(list);
                return;
            }
        }
    }

    /** 默认剧名：按已有条数递增，避免一堆"未命名"分不清。 */
    public static String defaultName() {
        return "我的剧本 " + (load().size() + 1);
    }

    public static String defaultName(String firstLine) {
        String base = (firstLine == null || firstLine.trim().isEmpty())
                ? "我的剧本" : firstLine.trim();
        if (base.length() > 10) base = base.substring(0, 10) + "…";
        return base;
    }
}
