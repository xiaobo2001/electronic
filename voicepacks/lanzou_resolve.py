# -*- coding: utf-8 -*-
"""蓝奏云分享页 -> 真实直链。处理 acw_sc__v2 反爬 cookie。"""
import re, sys, json, urllib.request, urllib.parse, ssl

UA = "Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.0 Mobile/15E148 Safari/604.1"
UA_PC = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

# acw_sc__v2 的固定置换表与异或常量（公开资料）
M = [0xf,0x23,0x1d,0x18,0x21,0x10,0x1,0x26,0xa,0x9,0x13,0x1f,0x28,0x1b,0x16,0x17,0x19,0xd,0x6,0xb,0x27,0x12,0x14,0x8,0xe,0x15,0x20,0x1a,0x2,0x1e,0x7,0x4,0x11,0x5,0x3,0x1c,0x22,0x25,0xc,0x24]
P = "3000176000856006061501533003690027800375"

def acw(arg1):
    q = ['']*40
    for x, ch in enumerate(arg1):
        for z in range(40):
            if M[z] == x + 1:
                q[z] = ch
    u = ''.join(q)
    return ''.join('%02x' % (int(u[i:i+2], 16) ^ int(P[i:i+2], 16)) for i in range(0, 40, 2))

ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode = ssl.CERT_NONE

def get(url, ua=UA_PC, cookie=None, follow=True):
    req = urllib.request.Request(url, headers={
        "User-Agent": ua,
        "Accept-Encoding": "gzip, deflate",
    })
    if cookie:
        req.add_header("Cookie", cookie)
    try:
        with urllib.request.urlopen(req, timeout=25, context=ctx) as r:
            data = r.read()
            enc = (r.headers.get("Content-Encoding") or "").lower()
            if "gzip" in enc:
                import gzip
                data = gzip.decompress(data)
            elif "deflate" in enc:
                import zlib
                try:
                    data = zlib.decompress(data)
                except Exception:
                    data = zlib.decompress(data, -zlib.MAX_WBITS)
            return data, r.getcode(), dict(r.headers)
    except Exception as e:
        return None, None, str(e)

def solve_cookie(url, ua):
    body, code, hdr = get(url, ua)
    if body is None:
        return None
    s = body.decode('utf-8', 'ignore')
    m = re.search(r"arg1='([0-9a-fA-F]{40})'", s)
    if not m:
        return None
    return "acw_sc__v2=" + acw(m.group(1))

def fetch_real_page(url, ua):
    # 先取反爬页，算 cookie，再带 cookie 取真实页
    ck = solve_cookie(url, ua)
    body, code, hdr = get(url, ua, cookie=ck)
    if body is None:
        return None
    s = body.decode('utf-8', 'ignore')
    if "arg1=" in s and len(s) < 6000:
        # 又遇上反爬，再解一次
        ck = solve_cookie(url, ua)
        body, code, hdr = get(url, ua, cookie=ck)
        if body is None:
            return None
        s = body.decode('utf-8', 'ignore')
    return s

def direct_link(page_url):
    host = urllib.parse.urlparse(page_url)
    base = f"{host.scheme}://{host.netloc}"
    s = fetch_real_page(page_url, UA_PC)
    if not s:
        return None, "页面取不到"
    # 文件名（确认对应哪个包）
    name = None
    for nm in ["xiaoya.zip", "chaowen.zip", "aishell3.zip", "asr.zip"]:
        if nm in s:
            name = nm
            break
    # PC 页: 'url/dom' via ajaxm.php?file=<num>; 或页面里直接有下载地址
    m = re.search(r"ajaxm\.php\?file=(\d+)", s)
    if not m:
        # 也许页面给了下载页跳转
        m2 = re.findall(r"href=['\"]([^'\"]*(?:file|download)[^'\"]*)['\"]", s)
        return name, "未找到 ajaxm id; 可疑链接: " + str(m2[:3])
    fid = m.group(1)
    api = f"{base}/ajaxm.php?file={fid}"
    ck = solve_cookie(page_url, UA)
    body, code, hdr = get(api, UA, cookie=ck)
    if body is None:
        return name, "ajaxm 失败"
    try:
        d = json.loads(body.decode('utf-8', 'ignore'))
    except Exception:
        return name, "ajaxm 返回非 JSON: " + body[:200].decode('utf-8','ignore')
    dom = d.get('dom') or d.get('url') and ''
    u = d.get('url')
    if dom and u:
        return name, dom.rstrip('/') + '/file/' + u
    return name, "ajaxm JSON: " + str(d)[:200]

def main():
    links = [
        "https://xiaobo9966.lanzoum.com/iQQ5P4e5trkf",
        "https://xiaobo9966.lanzoum.com/ikztJ4e5tscd",
        "https://xiaobo9966.lanzoum.com/ib2tg4e5tsri",
        "https://xiaobo9966.lanzoum.com/iQePo4e5ttbi",
    ]
    for u in links:
        name, result = direct_link(u)
        print("分享页:", u)
        print("  文件:", name)
        print("  直链/结果:", result)
        print()

if __name__ == '__main__':
    main()
