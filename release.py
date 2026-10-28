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
APK = "汇总知识.apk"
MANIFEST = "app/src/main/AndroidManifest.xml"


def http(url, method="GET", data=None, headers=None):
    ctx = ssl.create_default_context()
    ctx.check_hostname = False
    ctx.verify_mode = ssl.CERT_NONE
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Authorization", "token " + os.environ["GITHUB_TOKEN"])
    req.add_header("Accept", "application/vnd.github+json")
    for k, v in (headers or {}).items():
        req.add_header(k, v)
    try:
        with urllib.request.urlopen(req, context=ctx) as r:
            return r.status, r.read()
    except urllib.error.HTTPError as e:
        return e.code, e.read()


def main():
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
    subprocess.check_call(["bash", "build.sh"], env={**os.environ, "KS_PASS": ks_pass})

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

    print("== 4. 提交版本标记 ==")
    push_version(token, tag)

    print("== 完成 ==")
    print("下载直链:", up.get("browser_download_url"))
    print("Release 页: https://github.com/%s/releases/tag/%s" % (REPO, tag))


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
