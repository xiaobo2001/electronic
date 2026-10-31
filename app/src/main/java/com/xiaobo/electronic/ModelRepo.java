package com.xiaobo.electronic;

/**
 * 模型整包的下载地址。
 *
 * <p><b>为什么地址要单独收在一处。</b>服务器 IP、目录路径说变就变，
 * 散落在各处常量里改一次就得全局搜索一遍；而且真正"当前正确"的地址
 * 在 {@link PackSources} 那份在线清单里，这里只是清单拉不到时的兜底。
 *
 * <p><b>为什么是 http 不是 https。</b>这台服务器目前只开了 80 端口，443 不通。
 * Android 9（targetSdk 28）起默认禁止明文流量，已在
 * {@code res/xml/network_security_config.xml} 里单独放行了这个地址，
 * 不影响其它任何域名的安全策略。等服务器配上 HTTPS，把这里改掉即可。
 */
public final class ModelRepo {

    /** 整包所在目录，必须以 / 结尾 */
    public static final String BASE = "http://8.130.169.254/tts/";

    /** 源清单（1KB JSON）：地址变了改它就行，用户不用更新 App */
    public static final String MANIFEST = BASE + "voice-sources.json";

    private ModelRepo() {
    }

    /** 某个整包的下载地址 */
    public static String url(String file) {
        return BASE + file;
    }
}
