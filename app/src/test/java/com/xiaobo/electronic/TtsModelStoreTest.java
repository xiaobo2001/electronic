package com.xiaobo.electronic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;
import com.xiaobo.electronic.speech.TtsVoice;

/**
 * 音色清单自洽性。
 *
 * <p>这里只能测不依赖 Context 的部分（下载路径、真机加载靠手测），
 * 但"文件清单和体积表对不上"是这类代码最容易犯也最难发现的错——
 * 少算一个文件，进度条就永远走不到 100%；体积写小了，断点续跑会误判。
 */
public class TtsModelStoreTest {

    /** 三套音色的实际字节数（与 HuggingFace 仓库逐一核对过） */
    private static final long XIAOYA_TOTAL = 20_930_051L;
    private static final long CHAOWEN_TOTAL = 20_930_052L;
    private static final long AISHELL3_TOTAL = 55_891_146L;

    @Test
    public void voicePacks_manifestMatchesSizes() {
        assertEquals(XIAOYA_TOTAL, TtsVoice.XIAOYA.totalBytes());
        assertEquals(CHAOWEN_TOTAL, TtsVoice.CHAOWEN.totalBytes());
        assertEquals(AISHELL3_TOTAL, TtsVoice.AISHELL3.totalBytes());
    }

    /** 每套音色：文件数与体积数必须一一对应 */
    @Test
    public void voicePacks_filesAndSizesAligned() {
        for (TtsVoice v : TtsVoice.ALL) {
            assertEquals("音色 " + v.id + " 的文件/体积表长度不一致",
                    v.files.length, v.sizes.length);
        }
    }

    @Test
    public void voiceIds_uniqueAndLookup() {
        Set<String> ids = new HashSet<>();
        for (TtsVoice v : TtsVoice.ALL) {
            assertTrue("音色 id 重复：" + v.id, ids.add(v.id));
        }
        assertEquals(TtsVoice.XIAOYA, TtsVoice.byId("xiaoya"));
        assertEquals(TtsVoice.AISHELL3, TtsVoice.byId("aishell3"));
        assertNull(TtsVoice.byId("no-such-voice"));
        assertNotNull(TtsVoice.byId(null) == null ? TtsVoice.XIAOYA : null);
    }

    /** 体积区间：太大劝退用户，太小说明体积表抄错了 */
    @Test
    public void sizes_reasonableForOnDemandDownload() {
        for (TtsVoice v : TtsVoice.ALL) {
            long mb = v.totalBytes() / (1024 * 1024);
            assertTrue(v.id + " 应大于 10MB（否则不如内置）: " + mb, mb > 10);
            assertTrue(v.id + " 不应超过 80MB（移动网络下太劝退）: " + mb, mb < 80);
        }
    }

    /** 多说话人包必须带词典目录（jieba），piper 两个不需要 */
    @Test
    public void dictDir_onlyForMultiSpeaker() {
        assertEquals("", TtsVoice.XIAOYA.dictDir);
        assertEquals("", TtsVoice.CHAOWEN.dictDir);
        assertEquals("dict", TtsVoice.AISHELL3.dictDir);
    }

    /** 变声档：数量、范围、首档必须是原声（1.0） */
    @Test
    public void pitchPresets_sane() {
        assertEquals(TtsVoice.PITCH_NAMES.length, TtsVoice.PITCH_FACTORS.length);
        assertEquals(1.0f, TtsVoice.PITCH_FACTORS[0], 0.0001f);
        for (float f : TtsVoice.PITCH_FACTORS) {
            assertTrue("变声系数超出安全区间: " + f, f >= 0.8f && f <= 1.25f);
        }
    }

    /** 播放采样率偏移后的合法范围（8kHz 模型 × 最低档也不能低于 AudioTrack 下限 4kHz） */
    @Test
    public void pitch_factorsKeepSampleRateLegal() {
        int lowestModelRate = 8000;
        for (float f : TtsVoice.PITCH_FACTORS) {
            int out = Math.round(lowestModelRate * f);
            assertTrue("变声后采样率非法: " + out, out >= 4000 && out <= 192_000);
        }
    }
}
