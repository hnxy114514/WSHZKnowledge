# 腕上知识汇总（WSHZKnowledge）

面向手表（腕上穿戴设备）的高中九科知识点汇总 App。内置语文、数学、英语、物理、化学、生物、政治、历史、地理九门科目的知识清单与配图，支持行内数学公式排版（分数、根号、上下角标、向量可嵌套）与人工标注的重点高亮，并具备基于 GitHub Release 的 APK 覆盖更新。

---

## 目录结构

```
WSHZKnowledge/
├── app/                              Android 工程
│   ├── src/main/
│   │   ├── java/com/lizard/hsknowledge/
│   │   │   ├── MainActivity.java        UI + 数学排版引擎 + 云端更新（核心，约 2000 行）
│   │   │   └── ApkFileProvider.java     APK 覆盖安装用的 ContentProvider
│   │   ├── assets/
│   │   │   ├── data/                    九科知识库 JSON（构建时自动生成，勿手改）
│   │   │   └── img/                     配图 PNG（gen_images.py 生成，勿手改）
│   │   ├── res/                         资源（图标、样式、字符串）
│   │   └── AndroidManifest.xml          版本号、权限、Provider 声明
│
├── tools/                             数据与配图的「源」和生成脚本
│   ├── subjects/                       九科源文件（唯一需要手改内容的地方）
│   │   ├── chinese.py / math.py / english.py /
│   │   ├── physics.py / chem.py / bio.py /
│   │   └── politics.py / history.py / geography.py
│   ├── highlights.py                   人工标注的重点片段清单（key = 科目+标题）
│   ├── build_authored.py               构建数据：subjects → assets/data + cloud/update.json
│   ├── img_lib.py                      配图公式排版库（LaTeX 解析 + 分式/根号绘制）
│   ├── gen_images.py                   生成 60 张配图 → assets/img
│   ├── gen_icon.py                     生成应用图标
│   └── _archive/                       历史旧源归档（只读，不再参与构建）
│
├── cloud/update.json                   完整数据包备份（构建副产品，App 已不读取，仅存档）
├── build.sh                           无 Gradle 构建脚本（aapt2 + javac + d8 + apksigner）
├── release.py                         一键发布：构建 + 创建 Release + 上传 APK
├── .gitignore                          排除签名密钥、产物、缓存、归档
└── README.md                           本文件
```

未入库的本地目录（已 gitignore，按需清理）：

- `build/`：构建中间产物（每次构建重建）。
- `tmp/`：临时审计/测试文件。
- `tools/gen_out/`：早期实验导出。
- `配图核对/`：配图人工核对工作区（01 已核验 / 02 生成中 / 03 待核对）。

---

## 数据流（一条链路）

```
tools/subjects/*.py  （九科知识点源，人工维护）
        │  build_authored.py
        ▼
app/src/main/assets/data/*.json  （九科 JSON，构建时生成）
        │  打包进 APK
        ▼
App 启动时读取 assets/data，MainActivity 排版引擎渲染
```

配图走另一条独立的链：

```
tools/img_lib.py + tools/gen_images.py
        │  直接运行
        ▼
app/src/main/assets/img/*.png  （60 张）
        │  打包进 APK
        ▼
知识点详情页展示，双击可全屏缩放
```

重点高亮链：

```
tools/highlights.py  （HL 字典：{科目, 标题} → 高亮片段）
        │  build_authored.py 应用
        ▼
JSON 正文里的片段被包成加粗+高亮色
```

---

## 开发工作流

### 1. 修改知识点内容

只改 `tools/subjects/*.py`（每个文件是 `TREE = {章: {节: [知识点]}}` 的嵌套结构，按缩进即层级）。改完重新构建。

### 2. 修改 / 新增配图

改 `tools/gen_images.py`（和 `img_lib.py`），然后：

```bash
cd tools && python gen_images.py
```

把新生成的 PNG 放入 `配图核对/` 走一遍人工核对，确认无误后再随 APK 发布。

### 3. 修改 App 代码（UI / 排版引擎 / 更新逻辑）

改 `app/src/main/java/.../MainActivity.java`，然后重新构建。

### 4. 修改重点高亮

改 `tools/highlights.py` 的 `HL` 字典，key 用「科目 key + 知识点标题」，value 是正文里要标亮的**原文字片段**。构建时会校验，匹配不到会打印「未匹配」提示。

---

## 构建与发布

签名密码不写入仓库，通过环境变量传入。

### 只构建

```bash
KS_PASS=你的签名密码 ./build.sh
```

产物：`汇总知识.apk`。

### 一键发布（构建 + Release + 上传 APK）

```bash
GITHUB_TOKEN=你的PAT KS_PASS=你的签名密码 python release.py "本次更新的说明"
```

脚本会：
1. 从 `AndroidManifest.xml` 读 `versionName`（如 `1.3.1`）；
2. 调用 `build.sh` 重新构建；
3. 在 GitHub 创建 `v1.3.1` 标签的 Release；
4. 上传 APK 并打印直链。

---

## 版本号规范（语义化）

格式 `X.Y.Z`，只改三处之一：

- 第 1 位 `X`：非常大的结构性改动；
- 第 2 位 `Y`：新增内容 / 功能；
- 第 3 位 `Z`：修 bug / 小调整。

改 `AndroidManifest.xml` 的 `versionName`（`versionCode` 同步 +1 即可，仅作整数递增，不参与比较）。App 内更新比较用的是 `versionName` 与 Release 的 tag。

---

## 云端更新机制

- App 启动时后台静默检查，设置页「检查更新」可手动触发。
- 取版本号三级兜底：
  1. 直连 `api.github.com/.../releases/latest`；
  2. 走镜像 `https://gh-proxy.com/` + 同一 API（实测可转发 API）；
  3. 仍失败则读静态文件 `cloud/version.json`（`build_authored.py` 构建时按 `versionName` 生成，`release.py` 会随发布自动提交推送）。
- 用 `versionName` 与最新 Release 的 `tag_name` 做语义化比较。
- 有新版 → 先申请「安装未知应用」权限 → 下载 APK → 触发覆盖安装。
- 下载源顺序（`MainActivity.MIRRORS`，前缀 + 原地址）：直连 → gh-proxy.com → ghproxy.net → ghfast.top → gh.llkk.cc → github.moeyy.xyz。任一源 15 秒内拿不到 150KB 即判定「太慢」自动切换下一个。
- 屏幕中央有进度卡片，实时显示百分比、已下载/总大小、当前下载源；卡片不拦截点击，可边下边用。
- 同一版本下载过一次即复用本地 `filesDir/update.apk`（`apk_tag` 记录版本），不重复下载。

---

## 界面交互要点

- 每页的「‹ 返回」是随内容滚动的第一项，不固定占用顶部空间；系统返回键/手势始终可用。
- **右滑返回**：设置 → 显示 → 「右滑返回」可开关（默认开）。起手点须在屏幕左侧 1/4 内，向右拖动时当前页跟手右移、下方露出上一级页面的实时预览（视差 32%）；松手超过 46dp 即滑出并切页，不足则两页一起回弹。主页不触发；在输入框内起手需从最左 18dp 内且滑 80dp 以上（避免拖动光标误触）。
- **全屏看图时右滑不生效**，只能点左上角「‹ 关闭」（图内支持双指缩放、单指拖动、双击定点缩放）。
- 全局搜索：结果项第一行是知识点标题、第二行是「科目 › 章」小字；右侧 ✕ 清空关键词；从详情返回会保留关键词与结果。

---

## 界面交互要点

- 每页的「‹ 返回」是随内容滚动的第一项，不固定占用顶部空间；系统返回键/手势始终可用。
- **右滑返回**：设置 → 显示 → 「右滑返回」可开关（默认开）。从屏幕向右滑（横向位移 > 48dp 且明显横向）返回上一页；主页不触发；正在输入关键词时需从最左侧起手并滑得更长，避免误触光标拖动。
- **全屏看图时右滑不生效**，只能点左上角「‹ 关闭」（图内支持双指缩放、单指拖动、双击定点缩放）。
- 全局搜索：结果项第一行是知识点标题、第二行是「科目 › 章」小字；右侧 ✕ 清空关键词；从详情返回会保留关键词与结果。

---

## 注意事项

- `app/src/main/assets/data/` 和 `assets/img/` 都是**生成物**，不要手改，改源文件后重新构建。
- 签名密钥 `release.keystore` 和 GitHub Token 都**不能入库、不能外泄**。
- 发布新版本必须同时改 `versionName` 并推代码，否则 App 检测不到更新。
