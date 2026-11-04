# -*- coding: utf-8 -*-
"""按标题删除源文件里的一条知识点（dict 元素）。

只做结构删除，不生成内容。用于人工判断「两条其实是同一个知识点」之后清理重复项。
删之前请先确认保留哪一条、内容是否已并入保留项。

用法（仓库根）：
    python tools/drop_nodes.py tools/subjects/math.py "异面直线与角" "异面直线所成的角与距离"
    python tools/drop_nodes.py tools/subjects/math.py "异面直线与角" --write
"""
import io
import os
import re
import sys


def find_dict_span(src, title):
    pat = re.compile(r'([\'"])name\1\s*:\s*([\'"])' + re.escape(title) + r'\2')
    m = pat.search(src)
    if not m:
        return None
    # 向前找最近的 {，即这个条目的起点
    start = src.rfind('{', 0, m.start())
    if start < 0:
        return None
    # 从 start 做括号配对，字符串内的括号要跳过
    depth = 0
    i = start
    quote = None
    while i < len(src):
        c = src[i]
        if quote:
            if c == '\\':
                i += 2
                continue
            if src.startswith(quote, i):
                i += len(quote)
                quote = None
                continue
            i += 1
            continue
        if src.startswith('"""', i) or src.startswith("'''", i):
            quote = src[i:i + 3]
            i += 3
            continue
        if c in '"\'':
            quote = c
            i += 1
            continue
        if c == '{':
            depth += 1
        elif c == '}':
            depth -= 1
            if depth == 0:
                end = i + 1
                # 顺带吃掉后面的逗号和它所在行的剩余空白
                j = end
                while j < len(src) and src[j] in ' \t':
                    j += 1
                if j < len(src) and src[j] == ',':
                    end = j + 1
                while end < len(src) and src[end] in ' \t':
                    end += 1
                if end < len(src) and src[end] == '\n':
                    end += 1
                return start, end
        i += 1
    return None


if __name__ == '__main__':
    args = [a for a in sys.argv[1:]]
    write = '--write' in args
    args = [a for a in args if a != '--write']
    if len(args) < 2:
        print(__doc__)
        sys.exit(1)
    path, titles = args[0], args[1:]
    src = io.open(path, encoding='utf-8').read()
    total = 0
    for t in titles:
        span = find_dict_span(src, t)
        if not span:
            print('找不到：%s' % t)
            continue
        s, e = span
        print('删除 %-28s 占 %d 字节' % (t[:28], e - s))
        src = src[:s] + src[e:]
        total += 1
    if write and total:
        io.open(path, 'w', encoding='utf-8').write(src)
        print('\n已写入 %s，删除 %d 条' % (path, total))
    else:
        print('\n预演：将删除 %d 条（未写入）' % total)
