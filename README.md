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
│   │   │   └── img/                     配图 PNG（tools/img_gen 生成，勿手改）
│   │   ├── res/                         资源（图标、样式、字符串）
│   │   └── AndroidManifest.xml          版本号、权限、Provider 声明
│
├── tools/                             数据与配图的「源」和生成脚本
│   ├── subjects/                       九科源文件（唯一需要手改内容的地方）
│   │   ├── chinese.py / math.py / english.py /
│   │   ├── physics.py / chem.py / bio.py /
│   │   └── politics.py / history.py / geography.py
│   ├── build_authored.py               构建数据：subjects → assets/data（含去重、公式分数化、定稿覆盖）
│   ├── img_map.py                      配图绑定表：科目 + 节 + 知识点 → 图片文件名
│   ├── img_gen/                       配图生成（每张图一个脚本 + 共享 common.py）
│   │   ├── common.py                   公式排版库（LaTeX 解析 + 分式/根号绘制）+ 颜色/coords/save
│   │   ├── <图片名>.py                 每张配图一个脚本，单独运行只生成那一张
│   │   └── gen_all.py                  一键重新生成全部在用配图
│   ├── make_icon.py                    生成应用图标（表盘 + Emoji 书本，输出 5 个 dpi）
│   └── emoji_src/open_book.png          图标用的书本 Emoji 源图（Fluent Emoji, MIT）
│
├── cloud/version.json                 更新兜底用的版本号（App 拉不到 GitHub API 时读它）
├── local/                             本地私密文件（凭据、keystore、备忘），已被 .gitignore 排除
├── scripts/build.sh                   无 Gradle 构建脚本（aapt2 + javac + d8 + apksigner）
├── scripts/release.py                 一键发布：构建 + 创建 Release + 上传 APK
├── .gitignore                          排除签名密钥、产物、缓存、归档
└── README.md                           本文件
```

未入库的本地目录（已 gitignore，按需清理）：

- `build/`：构建中间产物与产出 APK（每次构建重建）。
- `local/`：发布凭据、签名 keystore、本地备忘，不进仓库。

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
tools/img_gen/  （common.py + 每张图一个 <图片名>.py + gen_all.py）
        │  直接运行某个脚本 / 或 gen_all.py
        ▼
app/src/main/assets/img/*.png  （56 张）
        │  打包进 APK
        ▼
知识点详情页展示，双击可全屏缩放
```

重点高亮链（已内联，不再有单独文件）：

```
tools/subjects/*.py 正文里的 **片段**
        │  build_authored.py 原样带过
        ▼
App 渲染为琥珀金加粗（applyMarkup 处理 **）
```

编号链（构建时生成并持久化）：

```
tools/subjects/*.py  →  build_authored.py 的 assign_ids()  →  tools/ids.json（编号台账）
                                                            →  写进 assets/data 的「章 / 知识点」
```

规则见仓库根目录的《编号规则.md》。要点：`科目码-加入日期-章序号[-章内序号]`，
编号一经分配永不重排，`tools/ids.json` 必须随仓库保留（删了会全部按新日期重编）。

---

## 开发工作流

### 1. 修改知识点内容

只改 `tools/subjects/*.py`（每个文件是 `TREE = {章: {节: [知识点]}}` 的嵌套结构，按缩进即层级）。改完重新构建。

### 2. 修改 / 新增配图

配图代码在 `tools/img_gen/`：每张图一个 `<图片名>.py`，共享绘制能力在 `common.py`。
要改某张图，只编辑对应的那个脚本，单独运行即可只重新生成它：

```bash
cd tools/img_gen && python m_quad.py        # 只重新生成「数学_3.1_二次函数.png」
python gen_all.py                           # 需要全部重生成时跑这个
```

脚本会把 PNG 直接写到 `app/src/main/assets/img/` 下的对应文件名，跑一次构建就打包进去了。

### 3. 修改 App 代码（UI / 排版引擎 / 更新逻辑）

改 `app/src/main/java/.../MainActivity.java`，然后重新构建。

### 4. 修改重点高亮

重点高亮不再单独维护，直接在 `tools/subjects/*.py` 的正文里用 `**要标亮的原文片段**` 包起来即可，App 会渲染成琥珀金加粗。构建时如果 `**` 不成对会报异常。

标亮范围内的 `rac{}`、`\sqrt{}`、`ec{}`、`^{}`、`_{}` 会一起被标亮。这些是自绘的 `ReplacementSpan`，
不会自动继承 `ForegroundColorSpan` / `StyleSpan`，标亮状态由 `applyMarkup(src, hl)` 显式传进 `InlineSpan`。
改这块时记得：新增任何自绘 span 都要跟着传 `hl`，否则又会出现「旁边文字亮了、公式没亮」。

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
- 第 2 位 `Y`：资料库新增内容（知识点、配图、高亮）；
- 第 3 位 `Z`：功能新增、界面调整、修 bug（小功能也走这一位）。

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
- 屏幕中央有进度卡片，实时显示百分比、已下载/总大小、当前下载源；卡片不拦截点击，可边下边用；卡片底部有「取消」，点后立即中断下载并清理临时文件。
- 同一版本下载过一次即复用本地 `filesDir/update.apk`（`apk_tag` 记录版本），不重复下载。
- 发现新版本先弹「更新说明」弹窗：标题给出版本号，中间是可滚动的更新内容（来自 Release 正文，`trimNotes()` 去掉 Markdown 记号并截到 460 字），下方三个入口：「立即更新」「稍后」「查看历次更新日志」。
- 点「稍后」会把版本号记进 `skip_version`：之后开机自动检查不再打扰同一版本，手动点「检查更新」仍会提示。
- 点「立即更新」时如果还没有「安装未知应用」权限，先去授权；`onResume` 检测到权限已给就自动接着下载（`pendingDownload`），不用再点一次检查更新。

---

## 更新日志（App 内）

- 数据源：`app/src/main/assets/changelog.json`（随 APK 打包，离线可看），结构 `{"version":1,"log":[{"v":"1.11.2","d":"10-28","items":["…"]}]}`，按版本倒序排列。
- 入口：设置 → 关于 → 「更新日志」；当前版本那一栏用高亮底色并标注「当前」。
- **发版纪律**：每次出包前先在 `changelog.json` 的 `log` 数组**最前面**插入本版条目（版本号与 `versionName` 一致），否则新版本在更新日志里查不到。只写用户能感知的变化，一条一句话，不写内部实现。
- Release 正文（`release.py` 的参数）与 changelog 条目分开维护：前者给「更新说明弹窗」用，后者给「更新日志」用；两边说法要一致。

---

## 反馈功能（App 内提交 Issue）

- 登录入口：设置 → 「个人主页」（显示头像、昵称，可退出登录）；反馈页未登录时点状态行或提交会自动跳过去。
- 反馈入口：设置 → 数据管理 → 反馈与建议。
- 登录用 GitHub Device Flow：`MainActivity.GH_CLIENT_ID` 硬编码 OAuth App 的 client_id，scope 为 `public_repo`。
- 流程：点登录 → 取 8 位授权码 → 自动复制到剪贴板并弹原生对话框 → 反馈页显示固定网址二维码（`assets/qr_login.png`，内容 `https://github.com/login/device`）+ 大号授权码，手机扫码后输码授权（手表浏览器支持不佳，作为备选按钮）→ 手表每 5 秒轮询 → 成功后 token 存 SharedPreferences（`gh_token` / `gh_login`）。
- 二维码是静态图片，不含授权码：授权码每次不同，静态图只承载固定网址，用户扫完照表上的码输入。
- 提交：`POST /repos/<repo>/issues`，正文自动附带版本号、机型、Android 版本、圆/方屏、最近浏览的知识点路径。
- 「我的反馈记录」：拉取 `issues?creator=<自己>&state=all`，列表显示状态与日期，点开看正文 + 最新回复。
- 状态约定（靠 label 与 state）：有 `已修复` 标签 → 已修复；issue 已关闭 → 已处理；有 `处理中` 标签 → 处理中；否则 待处理。处理一条反馈时：先打 `处理中`，修完补评论 + 打 `已修复` + 关闭。

---

## 界面交互要点

- 每页的「‹ 返回」是随内容滚动的第一项，不固定占用顶部空间；系统返回键/手势始终可用。
- **右滑返回**：设置 → 显示 → 「右滑返回」可开关（默认开）。起手点须在屏幕左侧 1/4 内，向右拖动时当前页跟手右移、下方露出上一级页面的实时预览（视差 32%）；松手超过 46dp 即滑出并切页，不足则两页一起回弹。主页不触发；在输入框内起手需从最左 18dp 内且滑 80dp 以上（避免拖动光标误触）。
- **全屏看图时右滑不生效**，只能点左上角「‹ 关闭」（图内支持双指缩放、单指拖动、双击定点缩放）。
- 个人主页：设置第一项，未登录显示「登录 GitHub」，登录后显示圆形头像 + @昵称 + 真实名字；头像会缓存到本地，离线也能显示。
- 全局搜索：结果项第一行是知识点标题、第二行是「科目 › 章」小字；右侧 ✕ 清空关键词；从详情返回会保留关键词与结果。
- **新增知识点（今日）**：主页「设置」上方，按知识点编号里的加入日期筛选当天新增；当天没有就退到最近一批，
  标题写成「最近新增（MM-DD）· N 条」。列表分页加载：首屏 40 条，滚到倒数 5 条内续载下一批，只往已有列表尾部追加、不重建。
- **显示调节首次引导**：没存过「已调节」标记的用户（含老版本升级上来的）首次启动会先看到字体 / 缩放调节页，
  必须点「完成，开始使用」才进主页，返回键不放行；确认过之后不再出现。


---

## 注意事项

- `app/src/main/assets/data/` 和 `assets/img/` 都是**生成物**，不要手改，改源文件后重新构建。
- 签名密钥 `release.keystore` 和 GitHub Token 都**不能入库、不能外泄**。
- 发布新版本必须同时改 `versionName` 并推代码，否则 App 检测不到更新。
- **素材署名**：应用图标中的书本图形取自 Microsoft Fluent Emoji（MIT License），由 `tools/make_icon.py` 合成圆形表盘后输出。
