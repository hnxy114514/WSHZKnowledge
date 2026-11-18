# -*- coding: utf-8 -*-
"""检查「译文 / 例题和译文」块里注释的编号与落点（长期工具，随仓库保留）。

用户的原话是「检查一下注释有没有标错地方，就是那个右上角的小圆圈」。
注释行自带 ①②③，圆圈有没有标对，机器能判的是这四件事：

  A 编号体系：一组之内必须从 ① 起顺排（要么组内重排、要么整篇连排，不许两种混着用，
    否则同一篇里有的组是 ①②③、有的组孤零零一个 ③，看起来就是标错）
  B 词条在不在对应的原文句里（找不到 = 注释挂错了句子）
  C 词条在原文中的先后顺序与编号顺序是否一致（不一致 = 圆圈标错位置）
  D 词条是不是写成了整句（>12 字，提醒「词条写词、释文写句」）

误报处理（都是教辅常规写法，不算错）：
  · 并列词条 `日、月：名词作状语`、`属、徒、伦：类，辈` —— 按「、」拆开逐个找，都在就算对
  · 省略号词条 `载……载……`、`若……然` —— 跳过
  · 单字词条（`而`、`下`）在句中出现多次 —— 首次出现位置不代表注释所指，只在「该词在本句
    只出现一次」时才判顺序，否则跳过（血泪经验：不加这个前置条件，误报从 3 条涨到 5 条全是假的）

用法：
    python tools/check_notes.py          只打摘要
    python tools/check_notes.py -v       连明细一起打
    python tools/check_notes.py B        只看 B 类明细（A / B / C / D 可组合）
退出码：有 A / B / C 类问题时为 1，否则 0（D 只是提醒，不影响退出码）。
"""
import importlib.util
import os
import re
import sys

CIRCLES = '①②③④⑤⑥⑦⑧⑨⑩⑪⑫⑬⑭⑮⑯⑰⑱⑲⑳'
IDX = {c: i + 1 for i, c in enumerate(CIRCLES)}
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SUBJECTS = os.path.join(ROOT, 'tools', 'subjects')


def strip_pinyin(s):
    """去掉`字（pīn）`的注音括号，只留字面用于比对。"""
    return re.sub(r'[（(][a-zA-Zāáǎàēéěèīíǐìōóǒòūúǔùǖǘǚǜüńňǹ\s,，·\-]+[）)]', '', s)


def leaves_of(tree):
    stack = list(tree)
    while stack:
        n = stack.pop()
        if n.get('children'):
            stack.extend(n['children'])
        else:
            yield n


def load_trees():
    """加载所有科目源文件，返回 [(科目名, TREE)]；ext_*.py 是扩展数据、不是科目。"""
    out = []
    for fn in sorted(os.listdir(SUBJECTS)):
        if not fn.endswith('.py') or fn.startswith('ext_') or fn.startswith('_'):
            continue
        path = os.path.join(SUBJECTS, fn)
        spec = importlib.util.spec_from_file_location('sub_' + fn[:-3], path)
        mod = importlib.util.module_from_spec(spec)
        try:
            spec.loader.exec_module(mod)
        except Exception as e:
            print('跳过 %s：%s' % (fn, e))
            continue
        if hasattr(mod, 'TREE'):
            out.append((getattr(mod, 'NAME', fn[:-3]), mod.TREE))
    return out


def parse_notes(text):
    parts = re.split('([' + CIRCLES + '])', text)
    return [(IDX[parts[i]], parts[i + 1].strip()) for i in range(1, len(parts) - 1, 2)]


def head_word(head):
    """注释词条：冒号前那一段。"""
    for sep in ('：', ':'):
        if sep in head:
            return head.split(sep)[0].strip()
    return ''


def check():
    A, B, C, D = [], [], [], []
    groups = 0
    notes = 0
    for subj, tree in load_trees():
        for leaf in leaves_of(tree):
            for blk in leaf.get('extra') or []:
                if '译文' not in (blk.get('name') or ''):
                    continue
                # 一门课一篇可能有多块，按块统计编号体系
                starts = []
                for it in blk.get('items') or []:
                    lines = it.get('lines') or []
                    tags = [l.get('tag') for l in lines]
                    if '原文' not in tags:
                        continue
                    src = strip_pinyin(next(l['text'] for l in lines if l['tag'] == '原文'))
                    entries = parse_notes(' '.join(l['text'] for l in lines if l['tag'] == '注释'))
                    if not entries:
                        continue
                    groups += 1
                    notes += len(entries)
                    starts.append(entries[0][0])
                    seen = []
                    for num, head in entries:
                        w = head_word(head)
                        if not w:
                            D.append((subj, leaf['name'], src, '%d 号没有词条（缺冒号）' % num, head))
                            continue
                        if '…' in w:
                            continue
                        words = [x for x in w.split('、') if x]
                        if len(words) > 1:
                            missing = [x for x in words if strip_pinyin(x) not in src]
                            if missing:
                                B.append((subj, leaf['name'], src,
                                          '%d 号并列词条里的「%s」原文找不到' % (num, '、'.join(missing)), head))
                            continue
                        w = strip_pinyin(w)
                        if len(w) > 12:
                            D.append((subj, leaf['name'], src, '%d 号词条像整句：%s' % (num, w), head))
                            continue
                        cnt = src.count(w)
                        if cnt == 0:
                            B.append((subj, leaf['name'], src, '%d 号「%s」原文里找不到' % (num, w), head))
                            continue
                        if cnt > 1:
                            # 一句话里同一个字出现多次，首次位置代表不了注释所指，跳过顺序判定
                            continue
                        pos = src.find(w)
                        if seen and pos < seen[-1][0]:
                            C.append((subj, leaf['name'], src,
                                      '%d 号「%s」排在 %d 号「%s」前面' % (num, w, seen[-1][1], seen[-1][2]), head))
                        seen.append((pos, num, w))
                # 两种合法体系：每组从 ① 起（starts 全是 1），或整篇连排（starts 正好是 1..组数）
                if starts:
                    ok = all(n == 1 for n in starts) or starts == list(range(1, len(starts) + 1))
                    if not ok:
                        A.append((subj, leaf['name'], '',
                                  '编号既不是组内从①起、也不是整篇连排：%s' % starts, ''))
    return groups, notes, A, B, C, D


def main():
    groups, notes, A, B, C, D = check()
    print('注释组 %d，注释条目 %d' % (groups, notes))
    print('A 编号体系异常 %d ／ B 词条找不到 %d ／ C 顺序颠倒 %d ／ D 词条像整句 %d'
          % (len(A), len(B), len(C), len(D)))
    args = [a for a in sys.argv[1:] if not a.startswith('-')]
    show_all = '-v' in sys.argv
    for tag, arr in (('A', A), ('B', B), ('C', C), ('D', D)):
        if not show_all and (not args or tag not in args):
            continue
        for subj, leaf, src, msg, head in arr:
            print('\n[%s] %s · %s  %s' % (tag, subj, leaf, msg))
            if src:
                print('   原文：%s' % src)
            if head:
                print('   注释：%s' % head)
    sys.exit(1 if (A or B or C) else 0)


if __name__ == '__main__':
    main()
