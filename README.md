# 腕上知识汇总（WSHZKnowledge）

面向手表（腕上穿戴设备）的高中九科知识点汇总应用，内置数学、物理、化学、生物、政治、历史、地理、语文、英语九门科目的知识清单与配图，支持行内数学公式排版（分数、根号、上下角标可嵌套）与人工标注的重点高亮。

## 目录结构

```
app/                     Android 工程源码
  src/main/java/...      MainActivity.java（UI + 数学排版引擎 + 云端更新）
  src/main/assets/data/  内置知识库 JSON（构建时生成）
  src/main/assets/img/   配图（PNG）
  src/main/res/          资源与图标
tools/
  subjects/*.py          九科源文件（结构化知识点树，含人工高亮标注）
  highlights.py          人工标注的重点片段清单
  build_authored.py      从 subjects 生成 assets/data 与 cloud/update.json
  gen_images.py          公式配图生成
  img_lib.py             数学公式排版库（LaTeX 结构解析 + 连续折线根号）
cloud/
  update.json            云端更新数据包（app 启动时检查更新）
build.sh                 无 Gradle 构建脚本（aapt2 + javac + d8 + apksigner）
```

## 构建

签名密码不写入仓库，构建时通过环境变量传入：

```bash
KS_PASS=你的签名密码 ./build.sh
```

构建产物为 `汇总知识.apk`（同时生成 `cloud/update.json`）。

## 云端更新

- app 每次启动时读取内置 `assets/data`，并在后台检查 `UPDATE_URL`（指向 `cloud/update.json`）。
- 若云端 `v` 版本号大于本地版本，则下载到应用私有目录，下次启动生效。
- 数据更新只覆盖文字内容；配图更新需要发布新版 APK。
