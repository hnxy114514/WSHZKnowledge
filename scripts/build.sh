#!/bin/bash
# 腕上知识汇总 构建脚本（无 Gradle：aapt2 + javac + d8 + zipalign + apksigner）
# 工程与产出全部在 D 盘；SDK 工具链复用本机既有环境（C 盘，只读引用）
set -e

# 签名密码通过环境变量传入（避免明文入库）：
#   KS_PASS=你的密码 ./build.sh        （KEY_PASS 未设置时自动复用 KS_PASS）
KS_PASS="${KS_PASS:?请先 export KS_PASS=签名密码}"
KEY_PASS="${KEY_PASS:-$KS_PASS}"

SDK="C:/Users/zzy33/WorkBuddy/Claw/sdk-setup/android-sdk"
BT="$SDK/build-tools/34.0.0"
R8="C:/Users/zzy33/WorkBuddy/Claw/sdk-setup/r8.jar"
JAVA="/c/Program Files/Java/jdk-21/bin/java"
JAVAC="/c/Program Files/Java/jdk-21/bin/javac"
ANDROID_JAR="$SDK/platforms/android-34/android.jar"

PROJ="D:/HZKnowledge"
KEY="$PROJ/local/release.keystore"
BUILD="$PROJ/build"
SRC="$PROJ/app/src/main"
RES="$SRC/res"
MAN="$SRC/AndroidManifest.xml"
OUT="$PROJ/build/汇总知识.apk"

echo "== 1. 生成知识库（subjects → assets/data）=="
cd "$PROJ/tools"
# 数据源没变就跳过生成：改 Java / 改文档时没必要把九科重新算一遍（原来固定烧 75 秒，
# 其中 73 秒是去重时的两两比对）。指纹取「科目源文件 + build_authored.py + ids.json」的内容，
# 不看 mtime——git 切分支、复制目录都会改 mtime，内容哈希才靠谱。产物不全也强制重跑。
DATA_STAMP="$BUILD/.data_stamp"
DATA_SIG=$(python - "$PROJ" <<'PY'
import hashlib, os, sys
root = sys.argv[1]
files = [os.path.join(root, 'tools', 'build_authored.py'),
         os.path.join(root, 'tools', 'ids.json')]
sd = os.path.join(root, 'tools', 'subjects')
files += [os.path.join(sd, f) for f in sorted(os.listdir(sd)) if f.endswith('.py')]
h = hashlib.md5()
for f in files:
    h.update(os.path.basename(f).encode('utf-8'))
    h.update(open(f, 'rb').read())
print(h.hexdigest())
PY
)
DATA_COUNT=$(ls "$PROJ"/app/src/main/assets/data/*.json 2>/dev/null | wc -l)
if [ -f "$DATA_STAMP" ] && [ "$(cat "$DATA_STAMP")" = "$DATA_SIG" ] && [ "$DATA_COUNT" -ge 10 ]; then
    echo "   数据源没变，跳过生成（约省 75 秒）"
else
    python build_authored.py
    echo -n "$DATA_SIG" > "$DATA_STAMP"
fi

echo "== 1.5 注释体检（译文块的 ①②③ 编号与落点，有问题直接中断）=="
python "$PROJ/tools/check_notes.py" || { echo "注释编号有问题，构建中止"; exit 1; }

# 预编译 App 直接读的二进制缓存：手表上解析 1.47MB JSON 要 2.6 秒，读这个只要几百毫秒。
# 缓存头里带着 versionCode，所以每次构建都要重做（约 0.5 秒），不能跟着数据一起跳过。
echo "== 1.6 生成二进制缓存 =="
python "$PROJ/tools/gen_cache.py"

# 词书清单：dict/books.json 是唯一来源，App 读 assets 里这份（离线也能列出可下载的词书）
cp "$PROJ/dict/books.json" "$PROJ/app/src/main/assets/dict_books.json"

# 助记图索引：词头 → 图片地址，任何词书命中都能用
python "$PROJ/tools/gen_pics.py" || { echo "助记图索引生成失败"; exit 1; }

echo "== 2. 清理 =="
python - "$BUILD" "$OUT" <<'PY'
import os, shutil, sys, time
build, out = sys.argv[1], sys.argv[2]
for d in ("gen", "obj", "dexout"):
    p = os.path.join(build, d)
    if os.path.isdir(p):
        # 旧产物移到 build/_old/<时间戳>/ 而不是直接删：
        # 一是保留一次现场便于排查，二是避免一次批量删除上百个中间文件。
        old_root = os.path.join(build, "_old")
        stamp = time.strftime("%Y%m%d-%H%M%S")
        dst = os.path.join(old_root, stamp + "-" + d)
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        try:
            os.rename(p, dst)
        except OSError:
            shutil.rmtree(p, ignore_errors=True)
# 旧产物归档只留最近 2 次构建（一次构建产生 obj/gen/dexout 一组），更早的自动删掉。
# 以前只提醒不删，结果 build/ 涨到 139 MB；这里用 rmtree 逐目录删，失败也不影响构建。
old_root = os.path.join(build, "_old")
if os.path.isdir(old_root):
    groups = {}
    for n in os.listdir(old_root):
        groups.setdefault(n.split("-", 1)[0], []).append(n)
    for g in sorted(groups)[:-2]:
        for n in groups[g]:
            shutil.rmtree(os.path.join(old_root, n), ignore_errors=True)
for f in ("classes.dex", "classes.jar", "base.apk", "unsigned.apk", "aligned.apk", "res.zip"):
    p = os.path.join(build, f)
    if os.path.isfile(p):
        os.remove(p)
if os.path.isfile(out):
    os.remove(out)
for d in ("gen", "obj"):
    os.makedirs(os.path.join(build, d), exist_ok=True)
print("cleaned")
PY

echo "== 3. aapt2 compile =="
"$BT/aapt2" compile --dir "$RES" -o "$BUILD/gen/res.zip"

echo "== 4. aapt2 link =="
"$BT/aapt2" link "$BUILD/gen/res.zip" --manifest "$MAN" -o "$BUILD/base.apk" \
  -I "$ANDROID_JAR" --no-version-vectors --auto-add-overlay

echo "== 5. javac 编译 =="
"$JAVAC" --release 8 -nowarn -g:none -d "$BUILD/obj" -cp "$ANDROID_JAR" \
  $(find "$SRC/java" -name '*.java')

echo "== 6. 打包 class 为 jar =="
python - "$BUILD/obj" "$BUILD/classes.jar" <<'PY'
import sys, zipfile, os
root, out = sys.argv[1], sys.argv[2]
with zipfile.ZipFile(out, 'w', zipfile.ZIP_DEFLATED) as z:
    for dp, _, fs in os.walk(root):
        for f in fs:
            if f.endswith('.class'):
                full = os.path.join(dp, f)
                z.write(full, os.path.relpath(full, root))
print('jar', os.path.getsize(out), 'bytes')
PY

echo "== 7. d8 生成 classes.dex =="
mkdir -p "$BUILD/dexout"
"$JAVA" -cp "$R8" com.android.tools.r8.D8 \
  --lib "$ANDROID_JAR" --min-api 23 --output "$BUILD/dexout" "$BUILD/classes.jar"
cp "$BUILD/dexout/classes.dex" "$BUILD/classes.dex"

echo "== 8. 组装未签名 APK（dex + 全部 assets）=="
python - "$BUILD/base.apk" "$BUILD/classes.dex" "$SRC/assets" "$BUILD/unsigned.apk" <<'PY'
import sys, zipfile, os
base, dex, assets, out = sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4]
n = 0
with zipfile.ZipFile(base, 'r') as zin, zipfile.ZipFile(out, 'w', zipfile.ZIP_DEFLATED) as zout:
    for item in zin.infolist():
        if item.filename == 'classes.dex':
            continue
        zout.writestr(item, zin.read(item.filename))
    zout.write(dex, 'classes.dex')
    for dp, _, fs in os.walk(assets):
        for f in sorted(fs):
            full = os.path.join(dp, f)
            rel = os.path.relpath(full, assets).replace('\\', '/')
            arc = 'assets/' + rel
            if rel == 'kb_cache.bin':
                # 不压缩：整包 deflate 的话，App 读它要先解压 1.35 MB，手表上要一两秒；
                # 直接 STORED 就是顺序读取，代价是 APK 大一点。
                zi = zipfile.ZipInfo(arc, date_time=(2026, 1, 1, 0, 0, 0))
                zi.compress_type = zipfile.ZIP_STORED
                zi.external_attr = 0o644 << 16
                with open(full, 'rb') as fh:
                    zout.writestr(zi, fh.read())
            else:
                zout.write(full, arc)
            n += 1
print('assembled', os.path.getsize(out), 'bytes, assets files:', n)
PY

echo "== 9. zipalign =="
"$BT/zipalign" -f -p 4 "$BUILD/unsigned.apk" "$BUILD/aligned.apk"

echo "== 10. apksigner 签名 =="
"$JAVA" -jar "$BT/lib/apksigner.jar" sign \
  --ks "$KEY" --ks-key-alias hskey \
  --ks-pass "pass:$KS_PASS" --key-pass "pass:$KEY_PASS" \
  --out "$OUT" "$BUILD/aligned.apk"

echo "== 完成 =="
ls -la "$OUT"
