# -*- coding: utf-8 -*-
"""把按册拆开的词表合并成「一类一本」。

原始词库把同一套词拆成 CET4_1/2/3、PEPGaoZhong_1..11 这种分册，
外加 luan（乱序）版本。这里按类别合并、按词头去重，正序与乱序各自成一本。

输出：dict/<code>.zip（内含 <code>.json，JSON Lines，格式与原库一致）+ dict/books.json
"""
import json
import os
import re
import shutil
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DICT = os.path.join(ROOT, 'dict')
TMP = os.path.join(ROOT, 'build', 'dict', 'merged')

# base（去掉册数的词表代号）→ (输出代号, 显示名)
MAP = {
    'CET4': ('CET4', '四级'),
    'CET4luan': ('CET4luan', '四级（乱序）'),
    'CET6': ('CET6', '六级'),
    'CET6luan': ('CET6luan', '六级（乱序）'),
    'KaoYan': ('KaoYan', '考研'),
    'KaoYanluan': ('KaoYanluan', '考研（乱序）'),
    'Level4': ('Level4', '专四'),
    'Level4luan': ('Level4luan', '专四（乱序）'),
    'Level8': ('Level8', '专八'),
    'Level8luan': ('Level8luan', '专八（乱序）'),
    'IELTS': ('IELTS', '雅思'),
    'IELTSluan': ('IELTSluan', '雅思（乱序）'),
    'TOEFL': ('TOEFL', '托福'),
    'GRE': ('GRE', 'GRE'),
    'GMAT': ('GMAT', 'GMAT'),
    'GMATluan': ('GMATluan', 'GMAT（乱序）'),
    'SAT': ('SAT', 'SAT'),
    'BEC': ('BEC', 'BEC 商务'),
    # 人教高中（11 册单元词表）与高中完整词表合并成一本
    'PEPGaoZhong': ('GaoZhong', '高中'),
    'GaoZhong': ('GaoZhong', '高中'),
    'GaoZhongluan': ('GaoZhongluan', '高中（乱序）'),
    'BeiShiGaoZhong': ('BeiShiGaoZhong', '高中（北师大）'),
    'PEPChuZhong': ('ChuZhong', '初中'),
    'ChuZhong': ('ChuZhong', '初中'),
    'ChuZhongluan': ('ChuZhongluan', '初中（乱序）'),
    'PEPXiaoXue': ('XiaoXue', '小学'),
    'WaiYanSheChuZhong': ('WaiYanSheChuZhong', '初中（外研社）'),
}

ORDER = ['四级', '四级（乱序）', '六级', '六级（乱序）', '考研', '考研（乱序）',
         '专四', '专四（乱序）', '专八', '专八（乱序）', '雅思', '雅思（乱序）',
         '托福', 'GRE', 'GMAT', 'GMAT（乱序）', 'SAT', 'BEC 商务',
         '高中', '高中（乱序）', '高中（北师大）',
         '初中', '初中（乱序）', '初中（外研社）', '小学']

W_HEAD = re.compile(r'"wordHead"\s*:\s*"([^"]+)"')


def base_of(bid):
    b = re.sub(r'^(reciteWord_|\d+_)', '', bid)
    b = re.sub(r'_\d+$', '', b)
    b = re.sub(r'^(PEPChuZhong)[789]$', r'\1', b)   # 七八九年级都归初中
    b = re.sub(r'^(PEPXiaoXue)\d$', r'\1', b)       # 三到六年级都归小学
    return b


def main():
    books = json.load(open(os.path.join(DICT, 'books.json'), encoding='utf-8'))['books']
    groups = {}
    for b in books:
        base = base_of(b['id'])
        if base not in MAP:
            print('未归类，跳过:', b['id'], base)
            continue
        code, name = MAP[base]
        g = groups.setdefault(name, {'code': code, 'files': []})
        g['files'].append(b['file'])

    os.makedirs(TMP, exist_ok=True)
    out = []
    for name, g in groups.items():
        code = g['code']
        seen = set()
        lines = []
        for fn in g['files']:
            z = zipfile.ZipFile(os.path.join(DICT, fn))
            raw = z.read(z.namelist()[0]).decode('utf-8', 'ignore')
            for ln in raw.splitlines():
                ln = ln.strip()
                if not ln:
                    continue
                m = W_HEAD.search(ln)
                key = (m.group(1) if m else '').lower()
                if not key or key in seen:
                    continue
                seen.add(key)
                lines.append(ln)
        # 写合并后的 zip（一个 JSON 文件，行序保留「先出现的在前」）
        zp = os.path.join(TMP, code + '.zip')
        with zipfile.ZipFile(zp, 'w', zipfile.ZIP_DEFLATED) as z:
            z.writestr(code + '.json', '\n'.join(lines) + '\n')
        out.append({
            'id': code,
            'name': name,
            'words': len(lines),
            'size': os.path.getsize(zp),
            'file': code + '.zip',
        })
        print('%-16s %-14s %6d 词（并入 %d 册）%5.2f MB'
              % (code, name, len(lines), len(g['files']), os.path.getsize(zp) / 1e6))

    out.sort(key=lambda b: ORDER.index(b['name']) if b['name'] in ORDER else 99)

    # 替换 dict/ 里的旧文件
    for fn in os.listdir(DICT):
        if fn.endswith('.zip'):
            os.remove(os.path.join(DICT, fn))
    for b in out:
        shutil.copy(os.path.join(TMP, b['file']), os.path.join(DICT, b['file']))
    with open(os.path.join(DICT, 'books.json'), 'w', encoding='utf-8') as f:
        json.dump({'books': out}, f, ensure_ascii=False, indent=1)

    print('\n合并后 %d 本 · 共 %d 词 · %.1f MB'
          % (len(out), sum(b['words'] for b in out), sum(b['size'] for b in out) / 1e6))


if __name__ == '__main__':
    main()
