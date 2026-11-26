# -*- coding: utf-8 -*-
"""把 build/dict/all 下的原始词表整理成 dict/ 目录 + books.json。

文件名形如 1521164649209_CET4_1.zip（时间戳_词表代号_册数），这里：
- 去掉时间戳，用「词表代号」当 id 和文件名（App 下载后存的就是它）
- 解析出中文名（四级/六级/考研/雅思…，luan = 乱序版，末尾数字 = 第几册）
- 解压数一遍行数得到词数（词表是 JSON Lines，一行一个词）
- 按「考试类 → 册数」排序，写出 dict/books.json（App 读 assets 里这份）
"""
import json
import os
import re
import shutil
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, 'build', 'dict', 'all')
OUT = os.path.join(ROOT, 'dict')

# 长串优先，否则 PEPGaoZhong 会被 GaoZhong 抢先匹配
NAMES = [
    ('BeiShiGaoZhong', '高中（北师大）'),
    ('PEPXiaoXue', '小学'),
    ('PEPChuZhong', '初中'),
    ('PEPGaoZhong', '高中'),
    ('ChuZhong', '初中'),
    ('GaoZhong', '高中'),
    ('CET4', '四级'),
    ('CET6', '六级'),
    ('KaoYan', '考研'),
    ('IELTS', '雅思'),
    ('TOEFL', '托福'),
    ('GMAT', 'GMAT'),
    ('BEC', 'BEC 商务'),
    ('Level4', '专四'),
    ('Level8', '专八'),
    ('GRE', 'GRE'),
    ('SAT', 'SAT'),
]
ORDER = ['四级', '六级', '考研', '专四', '专八', '雅思', '托福', 'GRE', 'GMAT', 'SAT',
         'BEC 商务', '高中', '高中（北师大）', '初中', '小学']


def parse(code):
    """CET4_1 → ('四级', 1, False)；IELTSluan_2 → ('雅思', 2, True)"""
    luan = 'luan' in code.lower()
    body = code.replace('luan', '').replace('LUAN', '')
    vol = 0
    m = re.search(r'[_-](\d+)$', body)
    if m:
        vol = int(m.group(1))
        body = body[:m.start()]
    name = body
    for key, cn in NAMES:
        if body.upper().startswith(key.upper()):
            name = cn
            break
    return name, vol, luan


def count_words(zp):
    try:
        z = zipfile.ZipFile(zp)
        inner = z.namelist()[0]
        return sum(1 for ln in z.read(inner).decode('utf-8', 'ignore').splitlines() if ln.strip())
    except Exception:
        return 0


def main():
    if not os.path.isdir(SRC):
        print('没有 build/dict/all，先下载')
        return
    os.makedirs(OUT, exist_ok=True)
    books = []
    for fn in sorted(os.listdir(SRC)):
        if not fn.endswith('.zip'):
            continue
        code = re.sub(r'^(\d+_|reciteWord_)', '', fn[:-4])   # 去掉时间戳 / reciteWord_ 前缀
        name, vol, luan = parse(code)
        words = count_words(os.path.join(SRC, fn))
        if words == 0:
            print('跳过（读不出词数）:', fn)
            continue
        title = name + ('（乱序）' if luan else '') + ((' 第%d册' % vol) if vol else '')
        books.append({
            'id': code,
            'name': title,
            'words': words,
            'size': os.path.getsize(os.path.join(SRC, fn)),
            'file': code + '.zip',
            '_src': fn,
            '_order': (ORDER.index(name) if name in ORDER else 99, luan, vol, code),
        })

    books.sort(key=lambda b: b['_order'])
    for b in books:
        b['_src'] = b.get('_src')
        b.pop('_order', None)

    # 清掉旧文件，避免新旧命名混在一起
    for fn in os.listdir(OUT):
        if fn.endswith('.zip'):
            os.remove(os.path.join(OUT, fn))
    for b in books:
        shutil.copy(os.path.join(SRC, b.pop('_src')), os.path.join(OUT, b['file']))

    with open(os.path.join(OUT, 'books.json'), 'w', encoding='utf-8') as f:
        json.dump({'books': books}, f, ensure_ascii=False, indent=1)

    total = sum(b['size'] for b in books)
    words = sum(b['words'] for b in books)
    print('词书 %d 本 · 词条 %d（含重复）· 共 %.1f MB' % (len(books), words, total / 1e6))
    for b in books[:8]:
        print('  %-18s %-16s %5d 词 %5.2f MB' % (b['id'], b['name'], b['words'], b['size'] / 1e6))


if __name__ == '__main__':
    main()
