package com.xiaobo.electronic;

import static org.junit.Assert.assertTrue;

import com.xiaobo.electronic.data.LegalText;

import org.junit.Test;

/**
 * 隐私/权限文案自洽性。
 *
 * <p>这类文案最怕"代码改了、声明没改"——比如真加了网络请求却还写着不联网。
 * 这里把关键事实断言住，改实现时若忘了同步文案会立刻红。
 */
public class LegalTextTest {

    @Test
    public void privacy_noNetworkClaim() {
        assertTrue(LegalText.PRIVACY.contains("INTERNET"));
        assertTrue(LegalText.PRIVACY.contains("不上传"));
    }

    @Test
    public void privacy_coversAllLocalData() {
        assertTrue(LegalText.PRIVACY.contains("MMKV"));
        assertTrue(LegalText.PRIVACY.contains("录音"));
        assertTrue(LegalText.PRIVACY.contains("第三方"));
        assertTrue(LegalText.PRIVACY.contains("儿童"));
    }

    @Test
    public void permissions_coversEveryManifestPermission() {
        String[] perms = {
                "RECORD_AUDIO", "READ_MEDIA_IMAGES", "READ_EXTERNAL_STORAGE",
                "SET_WALLPAPER", "POST_NOTIFICATIONS", "RECEIVE_BOOT_COMPLETED",
                "USE_FULL_SCREEN_INTENT", "FOREGROUND_SERVICE", "VIBRATE"
        };
        for (String p : perms) {
            assertTrue("权限说明漏了 " + p, LegalText.PERMISSIONS.contains(p));
        }
    }

    @Test
    public void permissions_noInternetClaim() {
        assertTrue(LegalText.PERMISSIONS.contains("没有 INTERNET 权限"));
    }
}
