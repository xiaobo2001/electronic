package com.xiaobo.electronic.speech;

import com.tencent.mmkv.MMKV;

/**
 * 离线朗读音色清单。
 *
 * <p><b>只保留一套音色：小雅。</b>piper 单音色 int8 约 21MB，音质自然，
 * 是三套候选里公认好听的那套。表白场景下"一个好听的声音"比
 * "一百个一般的声音"有用，超文、百变声音包（音质偏机械、还多带
 * 一份 jieba 词典）都已从内置资源中移除，APK 因此瘦了约 46MB。
 *
 * <p><b>变声档</b>（{@link #PITCH_FACTORS}）不占用额外空间：
 * 合成后重采样再播放，只改音调不改时长——一份模型能调出男声/娃娃音，
 * 见 LocalVitsTts 里的实现说明。想换听感用变声档即可，不必换模型。
 *
 * <p>说话人相关字段（{@link #multiSpeaker()} / {@link #currentSpeaker()}）
 * 保留是为了兼容旧版存档与既有调用链；当前唯一音色是单说话人，
 * 这些路径不会触发。
 */
public final class TtsVoice {

    private static final String KEY_VOICE = "tts_voice_id";
    private static final String KEY_SPEAKER = "tts_speaker_id";
    private static final String KEY_PITCH = "tts_pitch_idx";

    /** 变声档名称：菜单里直接显示 */
    public static final String[] PITCH_NAMES = {"原声", "低沉男声", "甜美女声", "轻快娃娃音"};

    /**
     * 变声系数：播放采样率与重采样比例。
     * 1.0 为原声；<1 音调变低（男声），>1 音调变高（女声/娃娃音）。
     * 幅度控制在 ±20% 以内——再夸张就听不清字了，表白台词含糊不得。
     */
    public static final float[] PITCH_FACTORS = {1.00f, 0.84f, 1.10f, 1.20f};

    // ==================== 小雅（唯一内置音色） ====================
    public static final TtsVoice XIAOYA = new TtsVoice(
            "xiaoya", "小雅", "温柔女声 · 音质最好", 1,
            "zh_CN-xiao_ya-medium.onnx", "lexicon.txt", "tokens.txt", "",
            new String[]{"date.fst", "number.fst", "phone.fst"},
            new String[]{
                    "zh_CN-xiao_ya-medium.onnx", "lexicon.txt", "tokens.txt",
                    "date.fst", "number.fst", "phone.fst",
            },
            new long[]{18_652_795L, 2_064_505L, 485L, 59_154L, 64_482L, 88_630L},
            1.08f);

    public static final TtsVoice[] ALL = {XIAOYA};

    public final String id;
    public final String name;
    /** 菜单里跟在名字后面的一句人话（音质/许可/体积暗示都在这儿说） */
    public final String desc;
    /** 说话人数量：1 表示单一音色，>1 表示可切换 sid */
    public final int speakers;
    final String modelFile;
    final String lexiconFile;
    final String tokensFile;
    /** 分词词典目录（相对音色目录），空串表示不需要 */
    public final String dictDir;
    final String[] ruleFsts;
    public final String[] files;
    public final long[] sizes;
    /** 语速：>1 更慢。告白比播报慢一点更动人 */
    final float lengthScale;

    private TtsVoice(String id, String name, String desc, int speakers,
                     String modelFile, String lexiconFile, String tokensFile, String dictDir,
                     String[] ruleFsts, String[] files, long[] sizes,
                     float lengthScale) {
        this.id = id;
        this.name = name;
        this.desc = desc;
        this.speakers = speakers;
        this.modelFile = modelFile;
        this.lexiconFile = lexiconFile;
        this.tokensFile = tokensFile;
        this.dictDir = dictDir;
        this.ruleFsts = ruleFsts;
        this.files = files;
        this.sizes = sizes;
        this.lengthScale = lengthScale;
    }

    public ModelInstaller.Pack pack() {
        return new ModelInstaller.Pack("tts_" + id, files, sizes);
    }

    public long totalBytes() {
        long t = 0;
        for (long s : sizes) t += s;
        return t;
    }

    public boolean multiSpeaker() {
        return speakers > 1;
    }

    // ==================== 当前选择（MMKV） ====================

    /** 当前音色；id 不存在时回落到小雅（比如旧版本存的 id 被删掉了） */
    public static TtsVoice current() {
        String id = null;
        try {
            id = MMKV.defaultMMKV().decodeString(KEY_VOICE, XIAOYA.id);
        } catch (Throwable ignored) {
        }
        TtsVoice v = byId(id);
        return v != null ? v : XIAOYA;
    }

    public static void setCurrent(TtsVoice v) {
        try {
            MMKV.defaultMMKV().encode(KEY_VOICE, v == null ? XIAOYA.id : v.id);
        } catch (Throwable ignored) {
        }
    }

    public static TtsVoice byId(String id) {
        if (id == null) return null;
        for (TtsVoice v : ALL) {
            if (v.id.equals(id)) return v;
        }
        return null;
    }

    /** 当前说话人编号（只对多说话人包有意义） */
    public static int currentSpeaker() {
        try {
            int s = MMKV.defaultMMKV().decodeInt(KEY_SPEAKER, 0);
            return s < 0 ? 0 : s;
        } catch (Throwable t) {
            return 0;
        }
    }

    public static void setSpeaker(int sid) {
        try {
            MMKV.defaultMMKV().encode(KEY_SPEAKER, sid < 0 ? 0 : sid);
        } catch (Throwable ignored) {
        }
    }

    /** 当前变声档（下标对应 {@link #PITCH_NAMES}） */
    public static int currentPitch() {
        try {
            int i = MMKV.defaultMMKV().decodeInt(KEY_PITCH, 0);
            return (i < 0 || i >= PITCH_FACTORS.length) ? 0 : i;
        } catch (Throwable t) {
            return 0;
        }
    }

    public static void setPitch(int idx) {
        try {
            MMKV.defaultMMKV().encode(KEY_PITCH,
                    (idx < 0 || idx >= PITCH_FACTORS.length) ? 0 : idx);
        } catch (Throwable ignored) {
        }
    }

    public static float pitchFactor() {
        return PITCH_FACTORS[currentPitch()];
    }

    public static String pitchName() {
        return PITCH_NAMES[currentPitch()];
    }
}
