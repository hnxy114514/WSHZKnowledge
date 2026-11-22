# -*- coding: utf-8 -*-
"""把 assets/data/*.json 预编译成 App 直接读的二进制缓存（assets/kb_cache.bin）。

为什么要有这个：手表上解析 1.47 MB JSON 要 2.6 秒（org.json 是主要开销），
而建 1914 个 Node 对象只要几百毫秒。把「词法分析」这一步挪到构建期，
App 启动时直接按固定顺序读二进制、建对象，首次安装也能快。

格式（大端，与 MainActivity 的 writeNodeToCache / readNodeFromCache 严格一一对应）：
    int32 magic = 0x485a4b32 ("HZK2")
    int32 versionCode          # 与 APK 一致；不一致时 App 自动作废走 JSON
    int32 subjectCount
    每个科目: str key, str name, int32 rootCount, node*
    node: str id, str name, byte flag(1=叶子/0=分组)
        叶子: str body, str daily, int32 star, str img, str d, int32 blockCount, block*
              block: str name, str unit, str mark, int32 itemCount, item*
                     item: int32 lineCount, line*
                           line: str tag, str text, str tone
        分组: int32 childCount, node*
    str: int32 字节长度 + UTF-8 字节（不用 writeUTF：它的 modified UTF-8 在 \\0 和增补字符上不一致）

用法：python tools/gen_cache.py   （build.sh 会自动调）
"""
import json
import os
import re
import struct
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DATA = os.path.join(ROOT, 'app', 'src', 'main', 'assets', 'data')
MAN = os.path.join(ROOT, 'app', 'src', 'main', 'AndroidManifest.xml')
OUT = os.path.join(ROOT, 'app', 'src', 'main', 'assets', 'kb_cache.bin')
MAGIC = 0x485a4b34


def w_int(out, n):
    out.write(struct.pack('>i', int(n)))


def w_str(out, s):
    b = (s or '').encode('utf-8')
    out.write(struct.pack('>i', len(b)))
    out.write(b)


def marks_of(n):
    """与 App 的 Node.marks() 一致：按出现顺序去重，用 / 连接。"""
    seen = []
    for b in (n.get('extra', []) or []):
        m = b.get('mark', '')
        if m and m not in seen:
            seen.append(m)
    return '/'.join(seen)


def extras_bytes(n):
    """附加内容序列化成一段字节，格式与 Java 的 readExtrasFrom 对应。"""
    import io as _io
    buf = _io.BytesIO()
    blocks = n.get('extra', []) or []
    w_int(buf, len(blocks))
    for b in blocks:
        w_str(buf, b.get('name', ''))
        w_str(buf, b.get('unit', ''))
        w_str(buf, b.get('mark', ''))
        items = b.get('items', []) or []
        w_int(buf, len(items))
        for it in items:
            lines = it.get('lines', []) or []
            w_int(buf, len(lines))
            for ln in lines:
                w_str(buf, ln.get('tag', ''))
                w_str(buf, ln.get('text', ''))
                w_str(buf, ln.get('tone', 'body'))
    return buf.getvalue()


def w_node(out, n):
    w_str(out, n.get('id', ''))
    w_str(out, n.get('name', ''))
    kids = n.get('children')
    if kids:
        out.write(b'\x00')
        w_int(out, len(kids))
        for c in kids:
            w_node(out, c)
        return
    out.write(b'\x01')
    w_str(out, n.get('body', ''))
    w_str(out, n.get('daily', ''))
    w_int(out, n.get('star', 2))
    w_str(out, n.get('img', ''))
    w_str(out, n.get('d', ''))
    w_str(out, marks_of(n))          # 列表行标记，构建时算好，App 不必为它解析附加内容
    ex = extras_bytes(n)             # 附加内容整段打包：App 启动只存字节，进详情页才解析
    w_int(out, len(ex))
    out.write(ex)


def main():
    man = open(MAN, encoding='utf-8').read()
    m = re.search(r'android:versionCode="(\d+)"', man)
    if not m:
        print('找不到 versionCode，缓存未生成')
        sys.exit(1)
    ver = int(m.group(1))

    order = json.load(open(os.path.join(DATA, 'order.json'), encoding='utf-8'))['order']
    tmp = OUT + '.tmp'
    with open(tmp, 'wb') as out:
        w_int(out, MAGIC)
        w_int(out, ver)
        w_int(out, len(order))
        for o in order:
            sub = json.load(open(os.path.join(DATA, o['s'] + '.json'), encoding='utf-8'))['subject']
            w_str(out, o['s'])
            w_str(out, o.get('name', o['s']))
            roots = sub.get('nodes', []) or []
            w_int(out, len(roots))
            for n in roots:
                w_node(out, n)
    os.replace(tmp, OUT)
    print('kb_cache.bin %d 字节（versionCode %d，%d 科）' % (os.path.getsize(OUT), ver, len(order)))


if __name__ == '__main__':
    main()
