# -*- coding: utf-8 -*-
"""把 tools/bei60_more_q.py 里人工/网上找的题追加进 chinese.py 的例题块。

只认一个硬指标：**答案必须逐空出现在该篇正文里**（去标点、去注音后比对）。
对不上的直接报错并跳过——网上抄来的题错字不少，宁可少一条。
"""
import importlib.util
import json
import re
import sys

spec = importlib.util.spec_from_file_location('mq', 'tools/bei60_more_q.py')
mq = importlib.util.module_from_spec(spec)
spec.loader.exec_module(mq)

spec2 = importlib.util.spec_from_file_location('cn', 'tools/subjects/chinese.py')
mod = importlib.util.module_from_spec(spec2)
spec2.loader.exec_module(mod)


def strip_all(s):
    """去掉注音括号、标点、空白，只留字，用于跟正文比对。"""
    s = re.sub(r'[（(][a-zA-Zāáǎàēéěèīíǐìōóǒòūúǔùǖǘǚǜüńňǹ\s,，·\-]+[）)]', '', s)
    return re.sub(r'[^\u4e00-\u9fff]', '', s)


def leaves(t):
    st = list(t)
    while st:
        n = st.pop()
        if n.get('children'):
            st.extend(n['children'])
        else:
            yield n


body_of = {}
for leaf in leaves(mod.TREE):
    body_of[leaf['name']] = strip_all(leaf.get('body') or '')

ok, bad = 0, []
for name, qs in mq.Q.items():
    if name not in body_of:
        bad.append((name, '找不到这一篇'))
        continue
    body = body_of[name]
    for stem, ans, src in qs:
        holes = stem.count('＿＿')
        spans = [x for x in ans.split('；') if x]
        if holes != len(spans):
            bad.append((name, '空数(%d)与答案数(%d)不符：%s' % (holes, len(spans), stem[:30])))
            continue
        miss = [a for a in spans if strip_all(a) not in body]
        if miss:
            bad.append((name, '答案正文里找不到「%s」' % '、'.join(miss)))
            continue
        ok += 1

print('通过 %d 条，问题 %d 条' % (ok, len(bad)))
for name, msg in bad:
    print('  [%s] %s' % (name, msg))
print('覆盖篇目：%d 篇 / 目标 %d 篇' % (len([n for n in mq.Q if n in body_of]), len(mq.Q)))

if '--write' not in sys.argv:
    sys.exit(1 if bad else 0)

# ---------- 写入 ----------
added = 0
for leaf in leaves(mod.TREE):
    name = leaf['name']
    if name not in mq.Q:
        continue
    blocks = leaf.setdefault('extra', [])
    ex = None
    for b in blocks:
        if b['name'] == '例题':
            ex = b
            break
    if ex is None:
        ex = {'name': '例题', 'unit': '道', 'mark': '例', 'items': []}
        blocks.append(ex)
    have = {(it['lines'][0]['text'], it['lines'][1]['text']) for it in ex['items']}
    for stem, ans, src in mq.Q[name]:
        fm = '补写出下列句子中的空缺部分。\n' + stem
        am = '答案：' + ans + '（' + src + '）'
        if (fm, am) in have:
            continue
        ex['items'].append({'lines': [
            {'tag': '题干', 'text': fm, 'tone': 'head'},
            {'tag': '答案与解析', 'text': am, 'tone': 'body'},
        ]})
        added += 1

head = '''# -*- coding: utf-8 -*-
"""语文 · 递归节点树。分组节点 = name + children，叶子 = name + body。
编号/日期/星级由 build_authored.py 自动生成，源文件不写 id/d/star。
"""
NAME = '语文'

TREE = '''
open('tools/subjects/chinese.py', 'w', encoding='utf-8', newline='\n').write(
    head + json.dumps(mod.TREE, ensure_ascii=False, indent=1) + '\n')
print('追加 %d 条' % added)
