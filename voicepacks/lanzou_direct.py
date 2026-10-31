# -*- coding: utf-8 -*-
"""蓝奏云分享页 -> 真实直链（备份源用）。

链路：分享页(带 acw_sc__v2 cookie) -> /fn iframe -> 取 fid/wp_sign
      -> POST ajaxfile.php -> {dom, url} -> dom + /file/ + url
"""
import re, json, urllib.request, urllib.parse
import lanzou_resolve as z

API_HOSTS = [
    "https://apifile.woozooo.com/ajaxfile.php?file={fid}",
    "https://apifile.lanzouw.com/ajaxfile.php?file={fid}",
]


def post(url, data, ua, cookie=None, referer=None):
    body = urllib.parse.urlencode(data).encode()
    req = urllib.request.Request(url, data=body, headers={
        "User-Agent": ua,
        "Content-Type": "application/x-www-form-urlencoded",
        "Accept": "application/json, text/javascript, */*",
        "X-Requested-With": "XMLHttpRequest",
    })
    if cookie:
        req.add_header("Cookie", cookie)
    if referer:
        req.add_header("Referer", referer)
    with urllib.request.urlopen(req, timeout=25, context=z.ctx) as r:
        raw = r.read()
        enc = (r.headers.get("Content-Encoding") or "").lower()
        if "gzip" in enc:
            import gzip
            raw = gzip.decompress(raw)
        return raw.decode("utf-8", "ignore")


def resolve(page_url):
    base = urllib.parse.urlparse(page_url)
    host = f"{base.scheme}://{base.netloc}"
    ck = z.solve_cookie(page_url, z.UA_PC)
    page = z.fetch_real_page(page_url, z.UA_PC)
    if not page:
        return None, "分享页取不到"

    name = None
    m = re.search(r"<title>([^<]+?)\s*-\s*蓝奏云", page)
    if m:
        name = m.group(1).strip()

    fid = None
    mf = re.search(r"var\s+fid\s*=\s*(\d+)", page)
    if mf:
        fid = mf.group(1)

    iframe = re.search(r"<iframe[^>]+src=[\"']([^\"']+)[\"']", page)
    if not iframe:
        return name, "无 iframe"

    fn_url = urllib.parse.urljoin(host, iframe.group(1))
    c2 = z.solve_cookie(page_url, z.UA_PC)
    body, _, _ = z.get(fn_url, z.UA_PC, cookie=c2)
    if body is None:
        return name, "iframe 取不到"
    fn = body.decode("utf-8", "ignore")

    if not fid:
        mf = re.search(r"file=(\d+)", fn)
        fid = mf.group(1) if mf else None
    ajaxdata = re.search(r"var\s+ajaxdata\s*=\s*'([^']*)'", fn)
    wp_sign = re.search(r"var\s+wp_sign\s*=\s*'([^']*)'", fn)
    if not (fid and ajaxdata and wp_sign):
        return name, f"参数不全 fid={fid} ajaxdata={bool(ajaxdata)} wp_sign={bool(wp_sign)}"

    data = {
        "action": "downprocess",
        "websignkey": ajaxdata.group(1),
        "signs": ajaxdata.group(1),
        "sign": wp_sign.group(1),
        "websign": "",
        "kd": "1",
        "ves": "1",
    }
    for tpl in API_HOSTS:
        try:
            r = post(tpl.format(fid=fid), data, z.UA_PC, cookie=c2, referer=fn_url)
            d = json.loads(r)
        except Exception as e:
            continue
        if str(d.get("zt")) == "1" and d.get("dom") and d.get("url"):
            dom = d["dom"].rstrip("/")
            return name, f"{dom}/file/{d['url']}"
        # zt 不为 1 时继续试下一个 API 域名
    return name, "ajaxfile 未返回有效直链"


if __name__ == "__main__":
    for u in [
        "https://xiaobo9966.lanzoum.com/iQePo4e5ttbi",   # xiaoya
        "https://xiaobo9966.lanzoum.com/ib2tg4e5tsri",   # chaowen
        "https://xiaobo9966.lanzoum.com/iQQ5P4e5trkf",   # aishell3
        "https://xiaobo9966.lanzoum.com/ikztJ4e5tscd",   # asr
    ]:
        name, link = resolve(u)
        print(f"{name or '?'}\n  {link}\n")
