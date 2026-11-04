# -*- coding: utf-8 -*-
"""高亮标注辅助：export 导出缺标条目 / apply 应用片段。
用法（在仓库根跑）：python tools/legacy/hl_kit.py export <key> [批大小]
                    python tools/legacy/hl_kit.py apply <key> [--write]
"""
import json, glob, importlib.util, sys

def load(key):
    spec = importlib.util.spec_from_file_location(key, 'tools/subjects/%s.py' % key)
    m = importlib.util.module_from_spec(spec); spec.loader.exec_module(m)
    return m

def items_of(m):
    for block, ds in m.TREE.items():
        for sec, its in ds.items():
            for it in its:
                yield sec, it

def export(key, size=40):
    m = load(key)
    miss = [(sec, it) for sec, it in items_of(m) if '**' not in it.get('body', '')]
    print("%s 缺标 %d 条" % (key, len(miss)))
    for i in range(0, len(miss), size):
        part = miss[i:i+size]
        lines = []
        for k, (sec, it) in enumerate(part, 1):
            lines.append("[%d] %s | %s" % (i+k, sec, it['t']))
            lines.append(it['body'])
            lines.append("")
        fn = 'build/hlx_%s_%02d.txt' % (key, i//size)
        open(fn, 'w', encoding='utf-8').write("\n".join(lines))
        print(" ", fn, len(part), "条")

def dump(m, path):
    lines = ['# -*- coding: utf-8 -*-',
             '"""%s · 全部知识点。缩进即层级：章 → 节 → 知识点。"""' % m.NAME,
             'NAME = %s' % json.dumps(m.NAME, ensure_ascii=False), '', 'TREE = {']
    for block, ds in m.TREE.items():
        lines.append('    %s: {' % json.dumps(block, ensure_ascii=False))
        for sec, its in ds.items():
            lines.append('        %s: [' % json.dumps(sec, ensure_ascii=False))
            for x in its:
                lines.append('            %s,' % json.dumps(x, ensure_ascii=False))
            lines.append('        ],')
        lines.append('    },')
    lines.append('}')
    open(path, 'w', encoding='utf-8').write('\n'.join(lines) + '\n')

def apply(key, write=False):
    m = load(key)
    idx = {}
    for _, it in items_of(m):
        idx.setdefault(it['t'], it)
    hl = {}
    for f in sorted(glob.glob('build/hl_%s_*.json' % key)):
        hl.update(json.load(open(f, encoding='utf-8')))
    ok = skip = 0
    fails = []
    for title, frag in hl.items():
        it = idx.get(title)
        if not it:
            fails.append((title, '标题不存在')); continue
        body = it['body']
        if '**' in body:
            skip += 1; continue
        if frag in body:
            it['body'] = body.replace(frag, '**' + frag + '**', 1); ok += 1
        else:
            fails.append((title, '未匹配: ' + frag[:26]))
    print("%s：成功 %d，跳过 %d，失败 %d" % (key, ok, skip, len(fails)))
    for t, why in fails: print("  ✗", t, "|", why)
    if write and ok:
        dump(m, 'tools/subjects/%s.py' % key)
        print("已写回 tools/subjects/%s.py" % key)

if __name__ == '__main__':
    cmd, key = sys.argv[1], sys.argv[2]
    if cmd == 'export':
        export(key, int(sys.argv[3]) if len(sys.argv) > 3 else 40)
    else:
        apply(key, '--write' in sys.argv)
