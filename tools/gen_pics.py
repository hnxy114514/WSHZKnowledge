# -*- coding: utf-8 -*-
"""把「图片记忆」词书里的 词头 → 图片地址 抽成一张全局表。

这样任何词书里的词，只要这张表里有，就能显示助记图——不必再局限于
「四级真题核心词」那一本（CET4luan_1）。源数据里 1162 个词带图。

输出：app/src/main/assets/word_pics.txt
格式：每行「词头<TAB>相对路径」，词头统一小写，App 端拼上网易图床前缀。
"""
import json
import os
import sys
import zipfile

SRC = os.path.join('dict', 'CET4luan_1.zip')
OUT = os.path.join('app', 'src', 'main', 'assets', 'word_pics.txt')
BASE = 'http://ydschool-online.nos.netease.com/'


def main():
    if not os.path.isfile(SRC):
        print('缺少 %s，跳过' % SRC)
        return 0

    z = zipfile.ZipFile(SRC)
    names = [n for n in z.namelist() if n.lower().endswith('.json')]
    if not names:
        print('压缩包里没有 json，跳过')
        return 0

    seen = {}
    for line in z.read(names[0]).decode('utf-8', 'ignore').splitlines():
        line = line.strip()
        if not line:
            continue
        try:
            o = json.loads(line)
        except Exception:
            continue
        w = (o.get('content') or {}).get('word') or {}
        head = (w.get('wordHead') or '').strip().lower()
        pic = ((w.get('content') or {}).get('picture') or '').strip()
        if not head or not pic.startswith(BASE):
            continue
        if head not in seen:
            seen[head] = pic[len(BASE):]

    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, 'w', encoding='utf-8', newline='\n') as f:
        for k, v in seen.items():
            f.write('%s\t%s\n' % (k, v))

    print('word_pics.txt: %d 条 · %.0f KB' % (len(seen), os.path.getsize(OUT) / 1024.0))
    return 0


if __name__ == '__main__':
    sys.exit(main())
