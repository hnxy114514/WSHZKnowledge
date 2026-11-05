# -*- coding: utf-8 -*-
"""一次性迁移：把源文件里的旧写法

    "ex": [{"t": 题干, "body": 解析}, ...]

改成统一的新写法

    "extra": [{"name": "例题", "unit": "道", "items": [
        {"lines": [{"tag": "题干", "text": 题干, "tone": "head"},
                   {"tag": "答案与解析", "text": 解析, "tone": "body"}]}, ...]}]

只搬字符串、不重写内容：题干与解析原样取源码里的字面量（含转义），
所以不会改动一个字。用 ast 精确定位，避免正则匹配到引号里的内容。

用法：python tools/migrate_ex_to_extra.py [--write]
"""
import ast
import io
import os
import sys

SUBJ = ['chinese', 'math', 'english', 'physics', 'chem', 'bio',
        'politics', 'history', 'geography']


def pos_of(lines, lineno, col_bytes):
    """(行号, 列字节偏移) → 全文字符偏移。ast 的 col_offset 是 UTF-8 字节数，中文源码必须换算。"""
    line = lines[lineno - 1]
    prefix = line.encode('utf-8')[:col_bytes].decode('utf-8', 'ignore')
    return sum(len(x) for x in lines[:lineno - 1]) + len(prefix)


def literal(src, node):
    """取某个节点的原始字面量文本（保留引号与转义）。"""
    lines = src.splitlines(True)
    a = pos_of(lines, node.lineno, node.col_offset)
    b = pos_of(lines, node.end_lineno, node.end_col_offset)
    return src[a:b]


def find_ex_dicts(tree):
    """遍历所有 dict，找出 key 为 'ex' 的项，返回 [(key_str_node, value_node)]。"""
    out = []
    for node in ast.walk(tree):
        if not isinstance(node, ast.Dict):
            continue
        for k, v in zip(node.keys, node.values):
            if isinstance(k, ast.Constant) and k.value == 'ex' and isinstance(v, ast.List):
                out.append((k, v))
    return out


def build_extra(src, value):
    """把 ex 的 List 节点转成 extra 的源码文本。"""
    groups = []
    for el in value.elts:
        if not isinstance(el, ast.Dict):
            continue
        kv = {}
        for k, v in zip(el.keys, el.values):
            if isinstance(k, ast.Constant) and isinstance(v, ast.Constant):
                kv[k.value] = literal(src, v)
        t = kv.get('t', "''")
        b = kv.get('body', "''")
        groups.append(
            '{"lines": [{"tag": "题干", "text": %s, "tone": "head"},\n'
            '                       {"tag": "答案与解析", "text": %s, "tone": "body"}]}'
            % (t, b))
    body = ',\n                  '.join(groups)
    return ('"extra": [{"name": "例题", "unit": "道",\n'
            '                "items": [%s]}]' % body)


def migrate(path, write):
    src = io.open(path, encoding='utf-8').read()
    tree = ast.parse(src)
    hits = find_ex_dicts(tree)
    if not hits:
        return 0
    lines = src.splitlines(True)
    edits = []
    for k, v in hits:
        a = pos_of(lines, k.lineno, k.col_offset)
        b = pos_of(lines, v.end_lineno, v.end_col_offset)
        edits.append((a, b, build_extra(src, v)))
    # 从后往前替换，避免前面的改动影响后面的偏移
    for a, b, new in sorted(edits, key=lambda x: -x[0]):
        src = src[:a] + new + src[b:]
    if write:
        io.open(path, 'w', encoding='utf-8').write(src)
    return len(edits)


def main():
    write = '--write' in sys.argv
    total = 0
    for k in SUBJ:
        p = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'subjects', k + '.py')
        n = migrate(p, write)
        if n:
            print('%-10s %d 处' % (k, n))
        total += n
    print('共 %d 处 %s' % (total, '(已写入)' if write else '(预演)'))


if __name__ == '__main__':
    main()
