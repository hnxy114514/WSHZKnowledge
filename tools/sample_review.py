# -*- coding: utf-8 -*-
"""随机抽取一批知识点供人工抽查改写（只抽样，不改任何内容）。

规则：
- 从九科源文件 TREE 里收集叶子节点；
- 排除：每日一句入口、2.4 必背 60 篇原文、优美语段原文（这些内容不该改写）；
- 排除 local/抽取记录.json 里已经抽过的（按「科目 + 标题」），保证不重复抽；
- 按各科可抽数量分层，共 100 条；结果追加到记录（status=pending）；
- 同时把原文导出到 build/_review/part<N>-<k>.txt，方便逐批阅读。

用法（仓库根）：
    python tools/sample_review.py            # 抽一批（100 条）
    python tools/sample_review.py --count 50 # 抽 50 条
"""
import collections
import datetime
import importlib.util
import json
import os
import random
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
KEYS = ['chinese', 'math', 'english', 'physics', 'chem', 'bio', 'politics', 'history', 'geography']
SUBJ = dict(zip(KEYS, ['语文', '数学', '英语', '物理', '化学', '生物', '政治', '历史', '地理']))
SKIP_PATH = ('2.4 必背 60 篇', '优美语段')
REC = os.path.join(ROOT, 'local', '抽取记录.json')
OUT_DIR = os.path.join(ROOT, 'build', '_review')


def load(key):
    path = os.path.join(ROOT, 'tools', 'subjects', key + '.py')
    spec = importlib.util.spec_from_file_location(key, path)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


def collect():
    out = []
    for k in KEYS:
        mod = load(k)
        name = getattr(mod, 'NAME', k)

        def walk(nodes, path):
            for n in nodes:
                p = path + [n.get('name', '')]
                if n.get('children'):
                    walk(n['children'], p)
                else:
                    if n.get('daily'):
                        continue
                    if any(s in ' › '.join(p) for s in SKIP_PATH):
                        continue
                    out.append({'subject': k, 'subject_name': SUBJ[k],
                                'path': ' › '.join(p[1:]),
                                'title': n.get('name', ''),
                                'body': n.get('body') or ''})
        walk(mod.TREE, [name])
    return out


def main():
    count = 100
    if '--count' in sys.argv:
        count = int(sys.argv[sys.argv.index('--count') + 1])

    rec = {'version': 1, 'batches': []}
    if os.path.isfile(REC):
        rec = json.load(open(REC, encoding='utf-8'))
    done = set()
    for b in rec.get('batches', []):
        for it in b['items']:
            done.add((it['subject'], it['title']))
    # 按章节抽查时改写过的条目也算「已抽过」，避免刚人工写好又被抽一遍
    for b in rec.get('chapter_batches', []):
        for it in b['items']:
            for t in it.get('fixed_titles', []):
                done.add((it['subject'], t))

    pool = collect()
    avail = collections.defaultdict(list)
    for r in pool:
        if (r['subject'], r['title']) not in done:
            avail[r['subject']].append(r)
    total = sum(len(v) for v in avail.values())
    if total < count:
        print('可抽只剩 %d 条，少于 %d' % (total, count))
        count = total

    alloc = {}
    for k, v in avail.items():
        alloc[k] = max(4, round(count * len(v) / total))
    while sum(alloc.values()) != count:
        diff = count - sum(alloc.values())
        if diff < 0:
            k = max(alloc, key=lambda x: alloc[x])
            if alloc[k] <= 1:
                break
            alloc[k] -= 1
        else:
            k = max((x for x in avail if alloc[x] < len(avail[x])),
                    key=lambda x: len(avail[x]) - alloc[x])
            alloc[k] += 1

    picked = []
    for k in KEYS:
        n = min(alloc.get(k, 0), len(avail[k]))
        picked += random.sample(avail[k], n)
    picked.sort(key=lambda r: (KEYS.index(r['subject']), r['path'], r['title']))

    batch_no = len(rec.get('batches', [])) + 1
    rec['batches'].append({
        'batch': batch_no, 'date': datetime.date.today().isoformat(),
        'count': len(picked),
        'note': '随机抽取，人工逐条改写并标重点；status: pending/done',
        'items': [dict(r, status='pending', changed='') for r in picked],
    })
    json.dump(rec, open(REC, 'w', encoding='utf-8'), ensure_ascii=False, indent=1)

    os.makedirs(OUT_DIR, exist_ok=True)
    for part in range(4):
        chunk = picked[part * 25:(part + 1) * 25]
        if not chunk:
            continue
        lines = []
        for i, it in enumerate(chunk, part * 25 + 1):
            lines.append('===== [%02d] %s ｜ %s' % (i, it['subject_name'], it['title']))
            lines.append('路径: ' + it['path'])
            lines.append(it['body'])
            lines.append('')
        path = os.path.join(OUT_DIR, 'part%d-%d.txt' % (batch_no, part + 1))
        open(path, 'w', encoding='utf-8').write('\n'.join(lines))

    print('第 %d 批：抽 %d 条（可抽池 %d，已抽过 %d）' % (batch_no, len(picked), total, len(done)))
    print('分配：', {k: alloc[k] for k in KEYS})
    for it in picked:
        print('  %s ｜ %s' % (it['subject_name'], it['title']))
    print('\n原文已导出到 build/_review/part%d-1..4.txt' % batch_no)


if __name__ == '__main__':
    main()
