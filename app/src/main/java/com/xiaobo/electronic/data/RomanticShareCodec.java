package com.xiaobo.electronic.data;

import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 浪漫模式分享码编解码工具。
 * v1 格式（兼容）：💖FW:Base64(文案)|倒计时秒数
 * v2 格式（表白优化#18）：💖FW2:Base64(Base64(行1)~Base64(行2)~…|秒数|音量)
 * v2 可携带多段台词与 BGM 音量，对方导入即可复刻完整氛围。
 */
public class RomanticShareCodec {
    private static final String PREFIX = "💖FW:";
    private static final String PREFIX_V2 = "💖FW2:";
    private static final String SEPARATOR = "|";
    private static final String LINE_SEP = "~";

    public static class RomanticInfo {
        public String message;
        public int seconds;
        public float volume = -1f; // -1 = 未指定（用默认）

        public RomanticInfo(String message, int seconds) {
            this.message = message;
            this.seconds = seconds;
        }
    }

    /** 【#23】最近一次解码失败的具体原因（导入提示用；解码成功时为空串） */
    public static String lastDecodeError = "";

    /** v2 编码：多段台词 + 秒数 + 音量 */
    public static String encode(List<String> lines, int seconds, float volume) {
        try {
            StringBuilder joined = new StringBuilder();
            for (int i = 0; i < lines.size(); i++) {
                if (i > 0) joined.append(LINE_SEP);
                joined.append(b64(lines.get(i)));
            }
            String payload = joined + SEPARATOR + clampSeconds(seconds) + SEPARATOR
                    + Math.round(volume * 100);
            return PREFIX_V2 + b64(payload);
        } catch (Exception e) {
            return "";
        }
    }

    /** v1 编码（保留兼容） */
    public static String encode(String message, int seconds) {
        try {
            return PREFIX + b64(message) + SEPARATOR + clampSeconds(seconds);
        } catch (Exception e) {
            return "";
        }
    }

    /** 解析（自动识别 v1/v2；失败返回 null，具体原因存 lastDecodeError） */
    public static RomanticInfo decode(String code) {
        if (code == null) {
            lastDecodeError = "分享码是空的";
            return null;
        }
        code = code.trim();
        if (code.startsWith(PREFIX_V2)) {
            try {
                String payload = deb64(code.substring(PREFIX_V2.length()));
                String[] parts = payload.split("\\" + LINE_SEP + "|\\" + SEPARATOR, -1);
                // parts: [b64line..., seconds, volume]
                if (parts.length < 3) {
                    lastDecodeError = "内容不完整（缺少秒数或音量段），请重新完整复制";
                    return null;
                }
                int volumeIdx = parts.length - 1;
                int secondsIdx = parts.length - 2;
                int seconds;
                try {
                    seconds = clampSeconds(Integer.parseInt(parts[secondsIdx]));
                } catch (NumberFormatException e) {
                    lastDecodeError = "秒数段格式不对，请重新复制完整分享码";
                    return null;
                }
                float vol;
                try {
                    vol = Math.max(0f, Math.min(1f, Integer.parseInt(parts[volumeIdx]) / 100f));
                } catch (NumberFormatException e) {
                    vol = -1f; // 音量段坏了降级用默认音量，不打断导入
                }
                StringBuilder msg = new StringBuilder();
                for (int i = 0; i < secondsIdx; i++) {
                    if (msg.length() > 0) msg.append("\n");
                    msg.append(deb64(parts[i]));
                }
                RomanticInfo info = new RomanticInfo(msg.toString(), seconds);
                info.volume = vol;
                lastDecodeError = "";
                return info;
            } catch (Exception e) {
                lastDecodeError = "内容损坏（无法解码），请重新复制完整分享码";
                return null;
            }
        }
        if (code.startsWith(PREFIX)) {
            try {
                String content = code.substring(PREFIX.length());
                int splitIndex = content.lastIndexOf(SEPARATOR);
                if (splitIndex <= 0) {
                    lastDecodeError = "旧版分享码缺少秒数段，请让对方重新生成";
                    return null;
                }
                String message = deb64(content.substring(0, splitIndex));
                int seconds;
                try {
                    seconds = clampSeconds(Integer.parseInt(content.substring(splitIndex + 1)));
                } catch (NumberFormatException e) {
                    lastDecodeError = "旧版分享码秒数段格式不对，请让对方重新生成";
                    return null;
                }
                lastDecodeError = "";
                return new RomanticInfo(message, seconds);
            } catch (Exception e) {
                lastDecodeError = "旧版分享码内容损坏，请让对方重新生成";
                return null;
            }
        }
        lastDecodeError = "这不是浪漫分享码（请确认以 💖FW 开头）";
        return null;
    }

    public static boolean isValidCode(String text) {
        return text != null && (text.trim().startsWith(PREFIX_V2) || text.trim().startsWith(PREFIX));
    }

    /** 多行文本按 v2 编码（超过 10 行截断） */
    public static String encodeLines(String multiline, int seconds, float volume) {
        List<String> lines = splitLines(multiline);
        return encode(lines, seconds, volume);
    }

    /** 台词行数上限：演出节奏按每句 3~5 秒推进，10 句可撑起约一分钟的告白段 */
    public static final int MAX_LINES = 10;

    public static List<String> splitLines(String multiline) {
        List<String> out = new ArrayList<>();
        if (multiline != null) {
            for (String s : multiline.split("\n")) {
                String t = s.trim();
                if (!t.isEmpty()) out.add(t);
                if (out.size() >= MAX_LINES) break;
            }
        }
        if (out.isEmpty()) out.add("遇见你真好");
        return out;
    }

    private static int clampSeconds(int s) {
        return Math.max(1, Math.min(60, s)); // v2 放宽到 60 秒
    }

    private static String b64(String s) {
        return Base64.encodeToString(s.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP | Base64.NO_PADDING);
    }

    private static String deb64(String s) {
        return new String(Base64.decode(s, Base64.NO_WRAP | Base64.NO_PADDING), StandardCharsets.UTF_8);
    }
}
