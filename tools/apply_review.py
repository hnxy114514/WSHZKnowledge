# -*- coding: utf-8 -*-
"""把人工写好的正文落到 tools/subjects/*.py。

**这个脚本不生成任何内容**：正文全部由人工在 build/_review/editN.txt 里逐条写好，
这里只做两件机械活：按「科目 + 标题」定位条目，把它的 body 字面量替换掉。

格式（多行正文原样写，不需要转义）：
    @@@ <科目key> | <条目标题>
    <新正文第一行>
    <新正文第二行>
    ...

用法（仓库根）：
    python tools/apply_review.py build/_review/edit1.txt          # 预演，只报告
    python tools/apply_review.py build/_review/edit1.txt --write   # 真正写入
"""
import datetime
import io
import json
import os
import re
import sys

TOOLS = os.path.dirname(os.path.abspath(__file__))


def parse(path):
    blocks = []
    cur = None
    for raw in io.open(path, encoding='utf-8').read().split('\n'):
        if raw.startswith('@@@ '):
            if cur:
                blocks.append(cur)
            head = raw[4:].strip()
            key, title = head.split('|', 1)
            cur = {'key': key.strip(), 'title': title.strip(), 'lines': []}
        elif cur is not None:
            cur['lines'].append(raw)
    if cur:
        blocks.append(cur)
    for b in blocks:
        # 去掉首尾空行，但保留正文内部的空行
        while b['lines'] and not b['lines'][0].strip():
            b['lines'].pop(0)
        while b['lines'] and not b['lines'][-1].strip():
            b['lines'].pop()
        b['body'] = '\n'.join(b['lines'])
    return blocks


def find_name(src, title):
    """定位 `"name": "<title>"` 的结束位置（右引号之后）。标题可能用单/双引号。"""
    pat = re.compile(r'([\'"])name\1\s*:\s*([\'"])' + re.escape(title) + r'\2')
    hits = list(pat.finditer(src))
    if not hits:
        return None, 0
    return hits[0].end(), len(hits)


def literal_end(src, start):
    """从 start（body 的值起始处）跳过一个 Python 字符串字面量，返回结束下标。"""
    i = start
    while i < len(src) and src[i] in ' \t':
        i += 1
    if src.startswith('"""', i) or src.startswith("'''", i):
        q = src[i:i + 3]
        j = src.find(q, i + 3)
        if j < 0:
            raise ValueError('三引号未闭合')
        return j + 3
    if src[i] in '"\'':
        q = src[i]
        j = i + 1
        while j < len(src):
            if src[j] == '\\':
                j += 2
                continue
            if src[j] == q:
                return j + 1
            j += 1
        raise ValueError('引号未闭合')
    raise ValueError('body 不是字符串字面量：%r' % src[i:i + 20])


def touch_ids(titles):
    """内容改过的条目，编号里的日期段改成今天（日期=最后修改日期），序号段不动。

    台账 key 形如「科目|一级|二级|…|标题」，按结尾标题匹配；同标题多条就跳过并报警，
    不猜是哪一个。
    """
    today = datetime.date.today().strftime('%y%m%d')
    path = os.path.join(TOOLS, 'ids.json')
    data = json.load(io.open(path, encoding='utf-8'))
    nodes = data.setdefault('nodes', {})
    done = []
    for t in titles:
        cands = [k for k, v in nodes.items() if k.endswith('|' + t) and 'd' in v]
        if len(cands) == 1:
            rec = nodes[cands[0]]
            parts = rec['id'].split('-')
            parts[0] = today                      # 首段是日期，只换这一段
            rec['id'] = '-'.join(parts)
            rec['d'] = today
            done.append((t, rec['id']))
        elif len(cands) > 1:
            print('  ! 台账里同名 %d 条，日期未改：%s' % (len(cands), t))
        else:
            print('  ! 台账里没找到叶子，日期未改：%s' % t)
    io.open(path, 'w', encoding='utf-8').write(json.dumps(data, ensure_ascii=False, indent=0))
    return done


def apply_block(b, write):
    path = os.path.join(TOOLS, 'subjects', b['key'] + '.py')
    if not os.path.isfile(path):
        return '科目文件不存在：%s' % path
    src = io.open(path, encoding='utf-8').read()
    end, hits = find_name(src, b['title'])
    if end is None:
        return '找不到标题：%s' % b['title']
    if hits > 1:
        return '标题重复 %d 处，需人工确认：%s' % (hits, b['title'])
    m = re.compile(r'([\'"])body\1\s*:\s*').search(src, end)
    if not m:
        return '标题后找不到 body：%s' % b['title']
    # body 必须在同一条目的紧邻位置，避免串到下一个条目
    gap = src[end:m.start()]
    if 'name' in gap:
        return 'body 定位异常（中间夹了别的条目）：%s' % b['title']
    try:
        lend = literal_end(src, m.end())
    except ValueError as e:
        return '%s：%s' % (b['title'], e)

    body = b['body']
    if '"""' in body or "'''" in body:
        return '正文里不能出现三引号：%s' % b['title']
    # 源文件是普通三引号字符串（非 raw），正文里的 \frac、\sqrt 必须写成 \\frac，
    # 否则 Python 会把 \f 解释成换页符、\s 报无效转义，数据直接坏掉。
    esc = body.replace('\\', '\\\\')
    new_lit = '"""' + esc + '"""'
    out = src[:m.end()] + new_lit + src[lend:]
    if write:
        io.open(path, 'w', encoding='utf-8').write(out)
    old_len = lend - m.end()
    return 'OK  %-28s 原 %4d 字符 → 新 %4d 字符' % (b['title'][:28], old_len, len(new_lit))


if __name__ == '__main__':
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(1)
    f = sys.argv[1]
    write = '--write' in sys.argv
    blocks = parse(f)
    ok = bad = 0
    changed = []
    for b in blocks:
        r = apply_block(b, write)
        print(r)
        if r.startswith('OK'):
            ok += 1
            changed.append(b['title'])
        else:
            bad += 1
    print('\n共 %d 条：成功 %d，失败 %d %s' % (len(blocks), ok, bad, '(已写入)' if write else '(预演，未写入)'))
    if write and changed:
        done = touch_ids(changed)
        today = datetime.date.today().strftime('%y%m%d')
        print('编号日期已更新为 %s：%d 条（另 %d 条未匹配）' % (today, len(done), len(changed) - len(done)))
