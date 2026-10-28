#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""一键发布：构建 APK → 创建 GitHub Release → 上传 APK。

用法（在 Git Bash 中）：
    GITHUB_TOKEN=你的PAT KS_PASS=签名密码 python release.py "本次更新的说明"
"""

import json
import os
import re
import ssl
import subprocess
import sys
import urllib.error
import urllib.request

REPO = "hnxy114514/WSHZKnowledge"
API = "https://api.github.com/repos/" + REPO
APK = "build/汇总知识.apk"
MANIFEST = "app/src/main/AndroidManifest.xml"


def http(url, method="GET", data=None, headers=None):
    """统一走 curl：本机代理会破坏 urllib 带 Authorization 的 POST（报 Bad credentials）。"""
    cmd = ["curl", "-sk", "-X", method,
           "-H", "Authorization: token " + os.environ["GITHUB_TOKEN"],
           "-H", "Accept: application/vnd.github+json",
           "-w", "\n%{http_code}"]
    for k, v in (headers or {}).items():
        cmd += ["-H", "%s: %s" % (k, v)]
    if data is not None:
        cmd += ["--data-binary", "@-"]
    cmd.append(url)
    p = subprocess.run(cmd, input=data, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    out = p.stdout
    idx = out.rfind(b"\n")
    if idx < 0:
        return 0, out
    try:
        code = int(out[idx + 1:].strip() or 0)
    except ValueError:
        code = 0
    return code, out[:idx]


def main():
    # 本脚本位于 scripts/ 下，先定位到项目根（与调用时的 cwd 无关）
    ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    os.chdir(ROOT)
    token = os.environ.get("GITHUB_TOKEN")
    ks_pass = os.environ.get("KS_PASS")
    if not token:
        print("请先设置环境变量 GITHUB_TOKEN")
        sys.exit(1)
    if not ks_pass:
        print("请先设置环境变量 KS_PASS")
        sys.exit(1)
    note = sys.argv[1] if len(sys.argv) > 1 else ""

    text = open(MANIFEST, encoding="utf-8").read()
    m = re.search(r'android:versionName="([^"]+)"', text)
    if not m:
        print("未在 AndroidManifest.xml 找到 versionName")
        sys.exit(1)
    ver = m.group(1)
    tag = "v" + ver

    print("== 1. 构建 %s ==" % tag)
    subprocess.check_call(["bash", "scripts/build.sh"], env={**os.environ, "KS_PASS": ks_pass})

    print("== 2. 创建 Release %s ==" % tag)
    payload = json.dumps({
        "tag_name": tag, "target_commitish": "main", "name": tag,
        "body": "%s - %s" % (tag, note) if note else tag,
        "draft": False, "prerelease": False,
    }).encode("utf-8")
    st, data = http(API + "/releases", "POST", payload, {"Content-Type": "application/json"})
    rel = json.loads(data)
    if st >= 400:
        print("创建 Release 失败:", rel.get("message"))
        sys.exit(1)
    upload_url = rel["upload_url"].split("{")[0]

    print("== 3. 上传 APK ==")
    apk_name = "HZKnowledge-%s.apk" % tag
    with open(APK, "rb") as f:
        apk_data = f.read()
    st, data = http(upload_url + "?name=" + apk_name, "POST", apk_data,
                    {"Content-Type": "application/vnd.android.package-archive"})
    up = json.loads(data)
    if st >= 400:
        print("上传 APK 失败:", up.get("message"))
        sys.exit(1)

    print("== 4. 刷新感谢名单静态备份 ==")
    refresh_thanks()

    print("== 5. 写入版本标记 ==")
    write_version_file(ver, tag)

    print("== 6. 提交版本标记 ==")
    push_version(token, tag)

    print("== 完成 ==")
    print("下载直链:", up.get("browser_download_url"))
    print("Release 页: https://github.com/%s/releases/tag/%s" % (REPO, tag))


def refresh_thanks():
    """重新生成 cloud/thanks.json（感谢名单的镜像兜底数据）。失败不影响发版。"""
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    script = os.path.join(root, "tools", "gen_thanks.py")
    rc = subprocess.call([sys.executable, script])
    if rc != 0:
        print("感谢名单备份未更新（保留上一版），不影响发版")


def write_version_file(ver, tag):
    """写 cloud/version.json（App 的兜底版本号，也用来给 API 结果做交叉校验）。

    必须自动生成：这个文件要是停在旧版本，App 会拿它跟 API 比对取新的，
    旧数据会把更新判断带偏。放在 Release 建好、APK 传完之后写，内容才和线上一致。
    """
    path = os.path.join("cloud", "version.json")
    with open(path, "w", encoding="utf-8") as f:
        json.dump({"version": ver, "tag": tag}, f, ensure_ascii=False)
    print("cloud/version.json ->", ver)


def push_version(token, tag):
    """把 cloud/version.json（App 的兜底版本号）提交并推送到 main。"""
    url = "https://%s@github.com/%s.git" % (token, REPO)
    subprocess.call(["git", "add", "-A"])
    rc = subprocess.call(["git", "commit", "-m", "发布 %s" % tag])
    if rc != 0:
        print("无改动可提交，跳过 commit")
    rc = subprocess.call(["git", "push", url, "main"])
    if rc != 0:
        print("推送失败（不影响已发布的 APK），可稍后手动 push")


if __name__ == "__main__":
    main()
