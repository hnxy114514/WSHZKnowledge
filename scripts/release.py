#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""一键发布：构建 APK → 创建 GitHub Release → 上传 APK。

用法（在 Git Bash 中）：
    GITHUB_TOKEN=你的PAT KS_PASS=签名密码 python release.py "本次更新的说明"          正式版
    GITHUB_TOKEN=你的PAT KS_PASS=签名密码 python release.py --pre "预发布说明"        预发布（只给自己测）
    GITHUB_TOKEN=你的PAT python release.py --promote                               把当前版本的预发布转正

预发布与正式版的区别（App 端只看 /releases/latest，这个接口服务端就排除预发布）：
    --pre      建 Release 时标 prerelease=True，且不写 cloud/version.json、不提交推送。
               普通用户永远收不到；只有把设置里「接收测试版」打开的机器才查得到。
    --promote  不重新构建、不重新上传，只把那条 Release 的 prerelease 改成 False，
               再写 version.json 并提交推送。用户拿到的就是你测过的那个包。
"""

import json
import os
import re
import ssl
import subprocess
import sys
import time
import urllib.error
import urllib.request

T0 = time.time()


def elapsed():
    return "%.1fs" % (time.time() - T0)

REPO = "hnxy114514/WSHZKnowledge"
API = "https://api.github.com/repos/" + REPO
APK = "build/汇总知识.apk"
MANIFEST = "app/src/main/AndroidManifest.xml"


def http(url, method="GET", data=None, headers=None):
    """统一走 curl：本机 hosts 把 github 域名劫持到 127.0.0.1、代理又坏，
    用 --resolve 钉死真实 IP 直连绕过劫持（IP 用公共 DNS 现查，变了就更新这里）。"""
    cmd = ["curl", "-sk", "--noproxy", "*",
           "--resolve", "api.github.com:443:20.205.243.168",
           "--resolve", "uploads.github.com:443:20.205.243.161",
           "-X", method,
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


def bump_pre(ver):
    """推到下一个测试版号：3.12.5 → 3.12.5pre1，3.12.5pre1 → 3.12.5pre2。

    每轮测试号都要往上走：compareSemver 在 App 端会先比三段数字、再比 pre 序号，
    同号不会提示更新，不递增的话第二轮的包根本收不到。
    """
    m = re.match(r'^(.*?)pre(\d+)$', ver, re.I)
    if m:
        return "%spre%d" % (m.group(1), int(m.group(2)) + 1)
    if "pre" in ver.lower():
        return "%spre1" % re.sub(r'[.\-_ ]?pre\d*$', '', ver, flags=re.I)
    return "%spre1" % ver


def main():
    # 本脚本位于 scripts/ 下，先定位到项目根（与调用时的 cwd 无关）
    ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    os.chdir(ROOT)
    token = os.environ.get("GITHUB_TOKEN")
    ks_pass = os.environ.get("KS_PASS")
    if not token:
        print("请先设置环境变量 GITHUB_TOKEN")
        sys.exit(1)
    # 只把非 -- 开头的参数当更新说明，否则 --pre 会被当成说明写进 Release
    argv = [a for a in sys.argv[1:] if not a.startswith("--")]
    flags = [a for a in sys.argv[1:] if a.startswith("--")]
    pre = "--pre" in flags
    promote = "--promote" in flags
    note = argv[0] if argv else ""

    text = open(MANIFEST, encoding="utf-8").read()
    m = re.search(r'android:versionName="([^"]+)"', text)
    if not m:
        print("未在 AndroidManifest.xml 找到 versionName")
        sys.exit(1)
    ver = m.group(1)
    tag = "v" + ver

    if pre and promote:
        print("--pre 和 --promote 只能选一个")
        sys.exit(1)
    # 转正不需要构建，也就不需要签名密码：它只改那条已上传的 Release
    if promote:
        print("== 转正 %s ==" % tag)
        promote_release(token, ver, tag)
        return
    if not ks_pass:
        print("请先设置环境变量 KS_PASS")
        sys.exit(1)

    if pre:
        # 每轮测试版号自动 +1：manifest 写 3.12.5 → 这轮发 3.12.5pre1，再发就是 pre2
        new_ver = bump_pre(ver)
        t2 = re.sub(r'android:versionName="[^"]*"', 'android:versionName="%s"' % new_ver, text)
        mc = re.search(r'android:versionCode="(\d+)"', t2)
        new_code = int(mc.group(1)) + 1
        t2 = re.sub(r'android:versionCode="\d+"', 'android:versionCode="%d"' % new_code, t2)
        with open(MANIFEST, "w", encoding="utf-8") as f:
            f.write(t2)
        ver, tag = new_ver, "v" + new_ver
        print("测试版版本号：%s（versionCode %d）" % (ver, new_code))

    print("== 1. 构建 %s ==" % tag)
    subprocess.check_call(["bash", "scripts/build.sh"], env={**os.environ, "KS_PASS": ks_pass})
    print("   构建完成，累计 %s" % elapsed())

    print("== 2. 创建 Release %s%s ==" % (tag, "（预发布）" if pre else ""))
    payload = json.dumps({
        "tag_name": tag, "target_commitish": "main", "name": tag,
        "body": "%s - %s" % (tag, note) if note else tag,
        "draft": False, "prerelease": pre,
    }).encode("utf-8")
    st, data = http(API + "/releases", "POST", payload, {"Content-Type": "application/json"})
    rel = json.loads(data)
    if st >= 400:
        print("创建 Release 失败:", rel.get("message"))
        sys.exit(1)
    upload_url = rel["upload_url"].split("{")[0]
    print("   建 Release 完成，累计 %s" % elapsed())

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
    print("   上传 %d KB 完成，累计 %s" % (len(apk_data) // 1024, elapsed()))

    if pre:
        print("== 预发布：写 cloud/beta.json（只提交这一个文件）==")
        write_beta_file(token, ver, tag)
        print("   普通用户查 /releases/latest 看不到这一版；只有开了「接收测试版」的机器能收到。")
        print("   测好之后跑：python scripts/release.py --promote")
    else:
        # 正式版不该再指向预发布：删掉标记，后面 push_version 会把它一起提交
        beta_path = os.path.join("cloud", "beta.json")
        if os.path.isfile(beta_path):
            os.remove(beta_path)
            print("已删除 cloud/beta.json（正式版不再指向预发布）")

        print("== 4. 刷新感谢名单静态备份 ==")
        refresh_thanks()
        print("   感谢名单完成，累计 %s" % elapsed())

        print("== 5. 写入版本标记 ==")
        write_version_file(ver, tag)

        print("== 6. 提交版本标记 ==")
        push_version(token, tag)
        print("   提交推送完成，累计 %s" % elapsed())

    print("== 完成（总耗时 %s）==" % elapsed())
    print("下载直链:", up.get("browser_download_url"))
    print("Release 页: https://github.com/%s/releases/tag/%s" % (REPO, tag))


def delete_release(token, tag):
    """删掉一条 Release（GitHub 的 tag 会保留，不影响历史）。"""
    st, data = http(API + "/releases/tags/" + tag)
    if st >= 400:
        print("预发布 %s 不存在，跳过删除" % tag)
        return
    rid = json.loads(data).get("id")
    st, data = http(API + "/releases/" + str(rid), "DELETE")
    if st >= 300:
        try:
            msg = json.loads(data).get("message")
        except Exception:
            msg = data[:120]
        print("删除预发布失败：", msg)
    else:
        print("已删除预发布 %s（tag 保留）" % tag)


def promote_release(token, ver, tag):
    """把预发布转正。

    版本号带 pre 的（如 3.12.5pre）：先去掉后缀、删掉带 pre 的那条 Release，
    再按正常流程重新构建并发正式版——线上版本号不能带 pre，用户看着别扭。
    版本号不带后缀的：只把 prerelease 改成 False，不重打包，测过的包直接给用户。
    """
    if "pre" in ver.lower():
        clean = re.sub(r'[-. _]?pre\d*$', '', ver, flags=re.I)
        if clean == ver:
            print("版本号带 pre 但去不掉后缀：%s，请先手动改 AndroidManifest.xml" % ver)
            sys.exit(1)
        print("预发布 %s → 正式版 %s" % (ver, clean))
        text = open(MANIFEST, encoding="utf-8").read()
        text = text.replace('android:versionName="%s"' % ver,
                            'android:versionName="%s"' % clean)
        with open(MANIFEST, "w", encoding="utf-8") as f:
            f.write(text)
        delete_release(token, tag)
        # 去掉 --promote 后回到正常发版流程：构建 → 建正式 Release → 上传 → 写版本号 → 提交推送
        sys.argv = [a for a in sys.argv if a != "--promote"]
        main()
        return

    st, data = http(API + "/releases/tags/" + tag)
    if st >= 400:
        print("找不到 Release %s：%s" % (tag, json.loads(data).get("message")))
        sys.exit(1)
    rel = json.loads(data)
    rid = rel.get("id")
    if not rid:
        print("Response 里没有 release id，转正中止")
        sys.exit(1)
    print("Release id = %s，当前 prerelease = %s" % (rid, rel.get("prerelease")))

    st, data = http(API + "/releases/" + str(rid), "PATCH",
                    json.dumps({"prerelease": False}).encode("utf-8"),
                    {"Content-Type": "application/json"})
    if st >= 400:
        print("转正失败：", json.loads(data).get("message"))
        sys.exit(1)
    print("已转正：%s" % tag)

    print("== 刷新感谢名单静态备份 ==")
    refresh_thanks()

    print("== 写入版本标记 ==")
    write_version_file(ver, tag)

    print("== 提交版本标记 ==")
    push_version(token, tag)

    print("== 完成 ==")
    print("Release 页: https://github.com/%s/releases/tag/%s" % (REPO, tag))


def write_beta_file(token, ver, tag):
    """预发布专用：把 tag 写进 cloud/beta.json，并且只提交这一个文件。

    App 的测试通道读的就是它（raw 通道、几十字节），比查 GitHub 的 Release 列表稳得多
    ——列表 20KB、未认证每小时 60 次额度，手表上经常超时或撞限流。
    预发布期间源码改动仍然不提交，等正式发版时一起提交；这个文件在正式发版时会被删掉。
    """
    path = os.path.join("cloud", "beta.json")
    with open(path, "w", encoding="utf-8") as f:
        json.dump({"version": ver, "tag": tag}, f, ensure_ascii=False)
    print("   cloud/beta.json ->", tag)
    url = "https://%s@github.com/%s.git" % (token, REPO)
    subprocess.call(["git", "add", "cloud/beta.json"])
    rc = subprocess.call(["git", "commit", "-m", "预发布 %s" % tag])
    if rc != 0:
        print("   beta.json 内容没变，跳过提交")
        return
    rc = subprocess.call(["git", "push", url, "main"])
    if rc != 0:
        print("   beta.json 推送失败（测试通道会退回 API 列表），可稍后手动 push")


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
