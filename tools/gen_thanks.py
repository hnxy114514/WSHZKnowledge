# -*- coding: utf-8 -*-
"""生成 cloud/thanks.json —— 感谢名单的静态备份。

App 取感谢名单的顺序是：直连 api.github.com → API 镜像 → 本文件（走全量镜像）。
raw.githubusercontent.com 几乎所有镜像都代，所以这个文件是网络最差时的兜底。

生成的是「最小 issues 数组」（只带 user.login 与一条已修复标签），
App 端用同一套 parseThanks() 解析，逻辑不用分叉。

用法：python tools/gen_thanks.py        （需要环境变量 GITHUB_TOKEN，非必需）
      不带 token 也能跑（走未登录接口，有速率限制）。
"""
import json, os, sys, urllib.request

REPO = "hnxy114514/WSHZKnowledge"
OWNER = "hnxy114514"
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "cloud", "thanks.json")
API = "https://api.github.com/repos/%s/issues?state=closed&per_page=100" % REPO


def fetch():
    req = urllib.request.Request(API, headers={
        "Accept": "application/vnd.github+json",
        "User-Agent": "HZKnowledge-thanks",
    })
    tok = os.environ.get("GITHUB_TOKEN")
    if tok:
        req.add_header("Authorization", "token " + tok)
    with urllib.request.urlopen(req, timeout=20) as r:
        return json.loads(r.read().decode("utf-8"))


def main():
    try:
        issues = fetch()
    except Exception as e:
        print("拉取失败，保持原文件不变：%s" % e)
        return 1

    cnt = {}
    for o in issues:
        if "pull_request" in o:
            continue
        names = [l.get("name", "") if isinstance(l, dict) else str(l)
                 for l in o.get("labels", [])]
        if not any("已修复" in n or "已处理" in n for n in names):
            continue
        login = (o.get("user") or {}).get("login", "")
        if not login or login.lower() == OWNER:
            continue
        cnt[login] = cnt.get(login, 0) + 1

    # 置顶约定与 App 端一致（这里是本地约定，文件里只体现顺序）
    order = sorted(cnt.items(), key=lambda kv: (-kv[1], kv[0].lower()))
    lines = []
    for login, n in order:
        for _ in range(n):
            lines.append({"user": {"login": login}, "labels": [{"name": "已修复"}]})

    out = os.path.abspath(OUT)
    with open(out, "w", encoding="utf-8") as f:
        json.dump(lines, f, ensure_ascii=False, indent=0)
    print("已写入 %s：%d 人，%d 条采纳记录" % (out, len(order), len(lines)))
    for login, n in order:
        print("  %-16s %d" % (login, n))
    return 0


if __name__ == "__main__":
    sys.exit(main())
