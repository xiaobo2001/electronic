package com.xiaobo.electronic.speech;

import com.tencent.mmkv.MMKV;

/**
 * 离线朗读音色清单。
 *
 * <p><b>为什么是"几个独立音色包"而不是一个大而全的模型。</b>
 * 中文多说话人模型（fanchen-C / theresa 等）动辄 116MB 且只有 fp32，
 * 而 piper 单音色 int8 只要 21MB 且音质明显更自然。表白场景下
 * "一个好听的声音"比"一百个一般的声音"有用，所以默认只下一个。
 *
 * <p><b>多说话人包（AISHELL3）。</b>39.9MB 的 int8 里塞了上百个说话人，
 * 想换声音不必重新下载模型，改 sid 就行。代价是它来自朗读语料库、
 * 音质偏机械，外加 13.7MB 中文分词词典（jieba，piper 不需要）。
 * 词典本仓库不带，从 melo 仓库借同一套。
 *
 * <p><b>变声档</b>（{@link #PITCH_FACTORS}）不下载任何东西：
 * 合成后重采样再播放，只改音调不改时长——见 LocalVitsTts 里的实现说明。
 */
public final class TtsVoice {

    private static final String KEY_VOICE = "tts_voice_id";
    private static final String KEY_SPEAKER = "tts_speaker_id";
    private static final String KEY_PITCH = "tts_pitch_idx";

    private static final String MIRROR = "https://hf-mirror.com/";
    private static final String ORIGIN = "https://huggingface.co/";
    private static final String REPO_XIAOYA = "csukuangfj2/vits-piper-zh_CN-xiao_ya-medium-int8/resolve/main/";
    private static final String REPO_CHAOWEN = "csukuangfj2/vits-piper-zh_CN-chaowen-medium-int8/resolve/main/";
    private static final String REPO_AISHELL3 = "csukuangfj/vits-zh-aishell3/resolve/main/";
    /** 中文分词词典的出处：melo 仓库带同一套 jieba 词典，aishell3 仓库不带 */
    private static final String REPO_DICT = "csukuangfj/vits-melo-tts-zh_en/resolve/main/";

    /** 变声档名称：菜单里直接显示 */
    public static final String[] PITCH_NAMES = {"原声", "低沉男声", "甜美女声", "轻快娃娃音"};

    /**
     * 变声系数：播放采样率与重采样比例。
     * 1.0 为原声；<1 音调变低（男声），>1 音调变高（女声/娃娃音）。
     * 幅度控制在 ±20% 以内——再夸张就听不清字了，表白台词含糊不得。
     */
    public static final float[] PITCH_FACTORS = {1.00f, 0.84f, 1.10f, 1.20f};

    /**
     * 整包直链（国内网盘，如蓝奏云）：上传后把直链填进这里。
     *
     * <p>留空不影响功能，只是走官方源逐文件下载（慢一些、容易断）。
     * 填了就优先走整包：一次连接拿一个压缩包，解压即用；
     * 哪天链接失效了会自动回退逐文件，不会让用户卡在"下载失败"。
     */
    private static final String[] ZIP_XIAOYA = {
            ModelRepo.url("xiaoya.zip"),
    };
    private static final String[] ZIP_CHAOWEN = {
            ModelRepo.url("chaowen.zip"),
    };
    private static final String[] ZIP_AISHELL3 = {
            ModelRepo.url("aishell3.zip"),
    };

    /** zip 包体积（本地打包实测值），用于进度条分母 */
    private static final long ZIP_BYTES_XIAOYA = 13_769_076L;
    private static final long ZIP_BYTES_CHAOWEN = 13_788_275L;
    private static final long ZIP_BYTES_AISHELL3 = 33_434_658L;

    // ==================== 小雅 ====================
    public static final TtsVoice XIAOYA = new TtsVoice(
            "xiaoya", "小雅", "温柔女声 · 音质最好", 1,
            "zh_CN-xiao_ya-medium.onnx", "lexicon.txt", "tokens.txt", "",
            new String[]{"date.fst", "number.fst", "phone.fst"},
            new String[]{
                    "zh_CN-xiao_ya-medium.onnx", "lexicon.txt", "tokens.txt",
                    "date.fst", "number.fst", "phone.fst",
            },
            new long[]{18_652_795L, 2_064_505L, 485L, 59_154L, 64_482L, 88_630L},
            new String[]{MIRROR + REPO_XIAOYA, ORIGIN + REPO_XIAOYA},
            null,
            ZIP_XIAOYA, ZIP_BYTES_XIAOYA,
            1.08f);

    // ==================== 超文 ====================
    public static final TtsVoice CHAOWEN = new TtsVoice(
            "chaowen", "超文", "清亮成熟 · CC0 可商用", 1,
            "zh_CN-chaowen-medium.onnx", "lexicon.txt", "tokens.txt", "",
            new String[]{"date.fst", "number.fst", "phone.fst"},
            new String[]{
                    "zh_CN-chaowen-medium.onnx", "lexicon.txt", "tokens.txt",
                    "date.fst", "number.fst", "phone.fst",
            },
            new long[]{18_652_796L, 2_064_505L, 485L, 59_154L, 64_482L, 88_630L},
            new String[]{MIRROR + REPO_CHAOWEN, ORIGIN + REPO_CHAOWEN},
            null,
            ZIP_CHAOWEN, ZIP_BYTES_CHAOWEN,
            1.08f);

    // ==================== AISHELL3 多说话人 ====================
    /** 仓库标称 175 个说话人；实际以模型读出来的 numSpeakers() 为准 */
    public static final TtsVoice AISHELL3 = new TtsVoice(
            "aishell3", "百变声音包", "上百种声音 · 音质偏机械", 175,
            "vits-aishell3.int8.onnx", "lexicon.txt", "tokens.txt", "dict",
            new String[]{"phone.fst", "date.fst", "number.fst", "new_heteronym.fst"},
            new String[]{
                    "vits-aishell3.int8.onnx", "lexicon.txt", "tokens.txt",
                    "date.fst", "number.fst", "phone.fst", "new_heteronym.fst",
                    "dict/jieba.dict.utf8", "dict/hmm_model.utf8", "dict/idf.utf8",
                    "dict/user.dict.utf8", "dict/stop_words.utf8",
                    "dict/pos_dict/char_state_tab.utf8", "dict/pos_dict/prob_emit.utf8",
                    "dict/pos_dict/prob_start.utf8", "dict/pos_dict/prob_trans.utf8",
            },
            new long[]{
                    39_870_124L, 2_042_943L, 1_671L, 59_154L, 64_482L, 88_630L, 21_974L,
                    5_071_204L, 519_739L, 5_998_871L, 49L, 8_974L,
                    327_139L, 1_687_686L, 4_347L, 124_159L,
            },
            // 前两个源给模型本体，后两个给从别家借来的词典
            new String[]{
                    MIRROR + REPO_AISHELL3, ORIGIN + REPO_AISHELL3,
                    MIRROR + REPO_DICT, ORIGIN + REPO_DICT,
            },
            // 前 7 个文件用 aishell3 仓库，后 9 个词典文件用 melo 仓库
            new int[]{0, 0, 0, 0, 0, 0, 0, 2, 2, 2, 2, 2, 2, 2, 2, 2},
            ZIP_AISHELL3, ZIP_BYTES_AISHELL3,
            1.05f);

    public static final TtsVoice[] ALL = {XIAOYA, CHAOWEN, AISHELL3};

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
    final String[] bases;
    final int[] baseIdx;
    final String[] zipUrls;
    final long zipBytes;
    /** 语速：>1 更慢。告白比播报慢一点更动人 */
    final float lengthScale;

    private TtsVoice(String id, String name, String desc, int speakers,
                     String modelFile, String lexiconFile, String tokensFile, String dictDir,
                     String[] ruleFsts, String[] files, long[] sizes,
                     String[] bases, int[] baseIdx, String[] zipUrls, long zipBytes,
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
        this.bases = bases;
        this.baseIdx = baseIdx;
        this.zipUrls = zipUrls;
        this.zipBytes = zipBytes;
        this.lengthScale = lengthScale;
    }

    public ModelDownloader.Pack pack() {
        return new ModelDownloader.Pack("tts_" + id, files, sizes, bases, baseIdx,
                zipUrls, zipBytes);
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
