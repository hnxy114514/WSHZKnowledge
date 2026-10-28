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
python build_authored.py

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
# 上一轮改名留下的归档只保留最近 3 份，免得 build 目录无限涨
old_root = os.path.join(build, "_old")
if os.path.isdir(old_root):
    runs = sorted(os.listdir(old_root), reverse=True)[3:]
    for r in runs:
        try:
            shutil.rmtree(os.path.join(old_root, r), ignore_errors=True)
        except Exception:
            pass          # 清不掉就留着，不影响构建
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
            zout.write(full, 'assets/' + rel)
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
