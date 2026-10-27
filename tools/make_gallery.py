# -*- coding: utf-8 -*-
"""生成「配图核对」图册（三栏流程化）：
  01_已核验可用/  02_生成中待自检/  03_待人工核对/   + index.html
状态由 tools/img_status.py 登记；未登记图片默认归入「已核验可用」。
"""
import os
import json
import shutil
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from img_status import CHECKING, MANUAL

ROOT = os.path.abspath(os.path.join(HERE, ".."))
IMG = os.path.join(ROOT, "app", "src", "main", "assets", "img")
JSON = os.path.join(ROOT, "app", "src", "main", "assets", "knowledge.json")
OUT = os.path.join(ROOT, "配图核对")

LANES = [("01_已核验可用", "已核验可用（可进软件）"),
         ("02_生成中待自检", "生成中 / 待自检"),
         ("03_待人工核对", "待人工核对")]


def load():
    with open(JSON, encoding="utf-8") as f:
        return json.load(f)


def collect(d):
    m = {}
    for s in d["subjects"]:
        for b in s["blocks"]:
            for ch in b["children"]:
                for it in ch["items"]:
                    p = it.get("img", "")
                    if p:
                        m[os.path.basename(p)] = (s["name"], b["name"], ch["name"], it["t"], it.get("star", 2))
    return m


def main():
    d = load()
    bound = collect(d)
    if os.path.isdir(OUT):
        for f in os.listdir(OUT):
            fp = os.path.join(OUT, f)
            if os.path.isfile(fp):
                os.remove(fp)
            else:
                shutil.rmtree(fp, ignore_errors=True)
    else:
        os.makedirs(OUT)

    files = sorted(f for f in os.listdir(IMG) if f.endswith(".png"))
    lanes = {k: [] for k, _ in LANES}
    for f in files:
        if f in MANUAL:
            lanes["03_待人工核对"].append(f)
        elif f in CHECKING:
            lanes["02_生成中待自检"].append(f)
        else:
            lanes["01_已核验可用"].append(f)

    cards, nav = [], []
    for key, label in LANES:
        lst = lanes[key]
        dpath = os.path.join(OUT, key)
        os.makedirs(dpath, exist_ok=True)
        for f in lst:
            shutil.copy2(os.path.join(IMG, f), os.path.join(dpath, f))
        nav.append('<a href="#%s">%s（%d）</a>' % (key, label, len(lst)))
        cards.append('<h2 id="%s">%s <span class="cnt">%d 张</span></h2>' % (key, label, len(lst)))
        if not lst:
            cards.append('<p class="empty">（暂无图片）</p>')
            continue
        cards.append('<div class="grid">')
        for f in lst:
            info = bound.get(f)
            if info:
                _, block, sec, title, star = info
                cap = '<b>%s</b><span class="meta">%s ／ %s</span>' % (title, block, sec)
            else:
                cap = '<b class="warn">（尚未绑定知识点）</b><span class="meta">—</span>'
            cards.append(
                '<figure><a href="%s/%s" target="_blank"><img src="%s/%s" alt="%s"></a>'
                '<figcaption><code>%s</code>%s</figcaption></figure>' % (key, f, key, f, f, f, cap))
        cards.append('</div>')

    html = """<!DOCTYPE html>
<html lang="zh-CN"><head><meta charset="utf-8">
<title>汇总知识 · 配图核对图册</title>
<style>
*{box-sizing:border-box}
body{margin:0;padding:28px 32px 60px;font:15px/1.6 "Microsoft YaHei",system-ui,sans-serif;
     background:#f4f6fa;color:#1d2433}
h1{font-size:24px;margin:0 0 6px}
.sub{color:#6b7688;margin:0 0 18px}
.nav{display:flex;flex-wrap:wrap;gap:8px;margin:0 0 24px}
.nav a{padding:5px 12px;border-radius:20px;background:#fff;border:1px solid #dfe4ee;
       color:#2c4a7c;text-decoration:none;font-size:13px}
.nav a:hover{background:#e8eefb}
h2{font-size:18px;margin:30px 0 12px;padding-left:10px;border-left:5px solid #3a62b0}
.cnt{font-size:13px;color:#8a93a5;font-weight:400;margin-left:8px}
.empty{color:#8a93a5;font-size:13px;padding:4px 10px}
.grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(330px,1fr));gap:16px}
figure{margin:0;background:#fff;border-radius:12px;padding:10px;
       box-shadow:0 1px 3px rgba(28,44,80,.10);border:1px solid #e6eaf2}
figure img{width:100%;display:block;border-radius:8px;border:1px solid #eef1f7}
figcaption{padding:8px 4px 2px;font-size:13px;line-height:1.5}
figcaption b{display:block;font-size:14px;color:#1d2433;margin-bottom:3px}
figcaption code{font-size:12px;color:#8a93a5;background:#f3f5fa;padding:1px 5px;border-radius:4px}
.meta{display:block;color:#6b7688;font-size:12.5px;margin-top:3px}
.warn{color:#b23c3c}
footer{margin-top:40px;color:#8a93a5;font-size:12.5px}
</style></head><body>
<h1>汇总知识 · 配图核对图册</h1>
<p class="sub">共 __N__ 张示意图（程序本地绘制，零成本）。三栏管理：已核验可用 / 生成中待自检 / 待人工核对。点击图片看原始尺寸。</p>
<div class="nav">__NAV__</div>
__CARDS__
<footer>核对要点：几何关系是否正确、过程是否画反、标签有无重叠遮挡、公式下标是否符合规范。</footer>
</body></html>
"""
    html = html.replace("__N__", str(len(files))).replace("__NAV__", "".join(nav)).replace("__CARDS__", "\n".join(cards))
    p = os.path.join(OUT, "index.html")
    with open(p, "w", encoding="utf-8") as f:
        f.write(html)
    print("图册:", p)
    for key, label in LANES:
        print("  %s %d 张" % (label, len(lanes[key])))


if __name__ == "__main__":
    main()
