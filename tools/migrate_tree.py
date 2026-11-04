# -*- coding: utf-8 -*-
"""一次性迁移：把 subjects_legacy/ 里的旧「章 → 节 → 知识点」三层树，
转成 tools/subjects/ 下的递归节点树（新格式）。

新格式（源文件只写内容，编号/日期/星级全部由 build_authored.py 生成）：

    NAME = "语文"

    TREE = [
        {"name": "第一板块 现代文阅读", "children": [
            {"name": "1.1 论述类文本阅读", "children": [
                {"name": "论证思路与结构分析", "body": "…"},
                {"name": "论点、论据与论证方法", "body": "…",
                 "ex": [{"t": "题干", "body": "解析"}]},
            ]},
        ]},
    ]

规则：有 children 就是分组节点（可无限嵌套）；否则就是叶子知识点。
叶子可选字段：body（正文）、ex（例题）、img（配图名）、lvl（人工指定星级）。
源文件里不写 id / 日期 / star —— 这些是构建产物，写进来只会越改越乱。

本脚本只在迁移时手动跑一次，build_authored.py 不调用它。
确认新格式生效且稳定后，subjects_legacy/ 整个目录即可删除。
"""
import os
import importlib.util

TOOLS = os.path.dirname(os.path.abspath(__file__))
LEGACY = os.path.join(TOOLS, "subjects_legacy")
OUT = os.path.join(TOOLS, "subjects")

KEYS = ["chinese", "math", "english", "physics", "chem",
        "bio", "politics", "history", "geography"]


def load_legacy(key):
    path = os.path.join(LEGACY, key + ".py")
    spec = importlib.util.spec_from_file_location("legacy_" + key, path)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod.NAME, mod.TREE


def convert(old_tree):
    """{章: {节: [知识点]}} → [Node]（递归）。字段名统一，其他原样保留。"""
    roots = []
    for block_name, sections in old_tree.items():
        block = {"name": block_name, "children": []}
        for sec_name, items in sections.items():
            sec = {"name": sec_name, "children": []}
            for it in items:
                leaf = {"name": it["t"]}
                if "body" in it:
                    leaf["body"] = it["body"]
                if "ex" in it:
                    leaf["ex"] = it["ex"]
                if "img" in it:
                    leaf["img"] = it["img"]
                if "lvl" in it:
                    leaf["lvl"] = it["lvl"]
                sec["children"].append(leaf)
            block["children"].append(sec)
        roots.append(block)
    return roots


def q(s):
    """Python 字符串字面量（中文原样保留，不转义成 \\u）。"""
    return repr(s)


def dump_leaf(n, pad, out):
    """叶子：name 一行、body 一行、可选字段接在后面，每条一个字典，看着就能改。"""
    fields = ["\"name\": " + q(n["name"])]
    fields.append("\"body\": " + q(n.get("body", "")))
    if n.get("ex"):
        parts = []
        for e in n["ex"]:
            parts.append("{\"t\": %s, \"body\": %s}"
                         % (q(e.get("t", "")), q(e.get("body", ""))))
        fields.append("\"ex\": [" + ", ".join(parts) + "]")
    if n.get("img"):
        fields.append("\"img\": " + q(n["img"]))
    if "lvl" in n:
        fields.append("\"lvl\": %d" % n["lvl"])
    out.append(pad + "{" + fields[0] + ",")
    for f in fields[1:-1]:
        out.append(pad + " " + f + ",")
    out.append(pad + " " + fields[-1] + "},")


def dump_nodes(nodes, level, out):
    pad = "    " * level
    for n in nodes:
        if "children" in n:
            out.append(pad + "{\"name\": " + q(n["name"]) + ", \"children\": [")
            dump_nodes(n["children"], level + 1, out)
            out.append(pad + "]},")
        else:
            dump_leaf(n, pad, out)


def main():
    for key in KEYS:
        name, old = load_legacy(key)
        nodes = convert(old)
        out = [
            "# -*- coding: utf-8 -*-",
            '"""%s · 递归节点树。分组节点 = name + children，叶子 = name + body。' % name,
            "",
            "只写内容：编号、日期、星级由 tools/build_authored.py 按层级自动生成，",
            "源文件里不要写 id / d / star。加一层就加一层 children，UI 无需改动。",
            "",
            "由 tools/migrate_tree.py 从 subjects_legacy/ 一次性转换而来。",
            '"""',
            "NAME = " + q(name),
            "",
            "TREE = [",
        ]
        dump_nodes(nodes, 1, out)
        out.append("]")
        out.append("")
        path = os.path.join(OUT, key + ".py")
        with open(path, "w", encoding="utf-8") as f:
            f.write("\n".join(out))
        # 统计：分组数 / 叶子数
        groups = leaves = 0
        stack = list(nodes)
        while stack:
            x = stack.pop()
            if "children" in x:
                groups += 1
                stack.extend(x["children"])
            else:
                leaves += 1
        print("%-12s 分组 %3d  叶子 %4d  -> %s"
              % (key, groups, leaves, os.path.basename(path)))


if __name__ == "__main__":
    main()
