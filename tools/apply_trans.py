# -*- coding: utf-8 -*-
"""把人工写好的译文数据写进源文件（只做机械落盘，不改一个字）。

数据文件里要导出 GROUPS：
    GROUPS = [("师说（韩愈）", SHISHUO), ...]     # 篇名与源码里的 name 完全一致

每组是 (原文, 译文, 注释)，原文里的难词用 ^{①} 标角标；注释为空就不生成注释行。

用法：python tools/apply_trans.py build/_review/trans_bx2.py [--write]
"""
import importlib.util
import io
import os
import re
import sys

SRC = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'subjects', 'ext_bipei60.py')



PINYIN_RE = re.compile(r'（[a-zāáǎàēéěèīíǐìōóǒòūúǔùǖǘǚǜü ]+）')


def strip_pinyin(t):
    """去掉正文里的注音括号，只比字句。"""
    return PINYIN_RE.sub('', t or '')


def check_groups(name, body, groups):
    """每组原文去掉角标、去掉拼音括号后，必须能在正文里按顺序找到。"""
    body = strip_pinyin(body)
    pos = 0
    bad = []
    for i, (src, _t, _n) in enumerate(groups, 1):
        plain = strip_pinyin(re.sub(r'\^\{[^}]*\}', '', src)).strip()
        k = body.find(plain, pos)
        if k < 0:
            bad.append((i, plain[:26]))
        else:
            pos = k + len(plain)
    return bad

def build(groups):
    parts = []
    for s, tr, n in groups:
        lines = ['            {"tag": "原文", "text": "%s", "tone": "head"},' % s,
                 '            {"tag": "译文", "text": "%s", "tone": "body"}' % tr]
        if n.strip():
            lines[-1] += ','
            lines.append('            {"tag": "注释", "text": "%s", "tone": "note"}' % n)
        parts.append('        {"lines": [\n' + '\n'.join(lines) + ']}')
    return ('"extra": [{"name": "译文", "unit": "句", "items": [\n'
            + ',\n'.join(parts) + ']}], ')


def node_body(src, name):
    """从源文件里取出某篇的 body：先定位 name，再找它后面的三引号字符串。"""
    i = src.find('{"name": "%s",' % name)
    if i < 0:
        return ''
    a = src.find('"""', i)
    if a < 0:
        return ''
    b = src.find('"""', a + 3)
    return src[a + 3:b]


def main():
    if len(sys.argv) < 2:
        print('用法：python tools/apply_trans.py <数据文件> [--write]')
        return
    path = sys.argv[1]
    write = '--write' in sys.argv
    spec = importlib.util.spec_from_file_location('trans_data', path)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)

    src = io.open(SRC, encoding='utf-8').read()
    done = skip = 0
    for name, groups in mod.GROUPS:
        anchor = '{"name": "%s",' % name
        if anchor not in src:
            print('  ! 源码里找不到篇目：%s' % name)
            skip += 1
            continue
        after = src.split(anchor, 1)[1][:160]
        if '"extra"' in after:
            print('  - 已有译文，跳过：%s' % name)
            skip += 1
            continue
        # 先校验切句能不能对上原文，对不上就不写，避免把错数据落盘
        bad = check_groups(name, node_body(src, name), groups)
        if bad:
            print('  ! %s 有 %d 句对不上正文：%s' % (name, len(bad), bad[:3]))
            skip += 1
            continue
        src = src.replace(anchor, anchor + ' ' + build(groups), 1)
        print('  + %-24s %d 组（切句全部对上）' % (name, len(groups)))
        done += 1

    if write:
        io.open(SRC, 'w', encoding='utf-8').write(src)
        print('已写入 %d 篇（跳过 %d）' % (done, skip))
    else:
        print('预演：%d 篇可写入，%d 篇跳过（加 --write 真正写入）' % (done, skip))


if __name__ == '__main__':
    main()
