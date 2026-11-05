# 腕上知识汇总（WSHZKnowledge）

面向手表（腕上穿戴设备）的高中九科知识点汇总 App。内置语文、数学、英语、物理、化学、生物、政治、历史、地理九门科目的知识清单与配图，支持行内数学公式排版（分数、根号、上下角标、向量可嵌套）与人工标注的重点高亮，并具备基于 GitHub Release 的 APK 覆盖更新。

---

## 目录结构

```
WSHZKnowledge/
├── app/                              Android 工程
│   ├── src/main/
│   │   ├── java/com/lizard/hsknowledge/
│   │   │   ├── MainActivity.java        UI + 数学排版引擎 + 更新 + 反馈（单文件，约 8300 行）
│   │   │   ├── OverScrollScrollView.java 圆屏内容过滚滚动容器（列表与正文共用，方屏过滚为 0）
│   │   │   └── ApkFileProvider.java     APK 覆盖安装用的 ContentProvider
│   │   ├── assets/
│   │   │   ├── data/                    九科知识库 JSON（构建时自动生成，勿手改）
│   │   │   └── img/                     配图 PNG（tools/legacy/img_gen 生成，勿手改）
│   │   ├── res/                         资源（图标、样式、字符串）
│   │   └── AndroidManifest.xml          版本号、权限、Provider 声明
│
├── tools/                             日常在用的脚本与数据源（这里的东西每次构建/都可能用到）
│   ├── subjects/                       九科源文件（唯一需要手改内容的地方）
│   │   ├── chinese.py / math.py / english.py /
│   │   ├── physics.py / chem.py / bio.py /
│   │   ├── politics.py / history.py / geography.py
│   │   └── ext_bipei60.py / ext_zuowen.py   语文扩展内容（必背 60 篇、作文素材），由 chinese.py 加载
│   ├── build_authored.py               构建数据：递归 TREE → assets/data/nodes（含去重、分数化、定稿、编号 v3）
│   ├── check_data.py                   数据体检：编号/标题/正文/加粗/LaTeX/附加块（只读，构建后跑一次）
│   ├── apply_review.py                 抽查改写落盘：按「科目+标题」替换 body，并把该条编号日期改成当天
│   ├── drop_nodes.py                   按标题删除源文件里的条目（人工判定重复后清理用）
│   ├── gen_thanks.py                   生成感谢名单的静态备份 cloud/thanks.json（发版时自动跑）
│   ├── img_map.py                      配图绑定表：科目 + 分组名关键词 + 标题关键词 → 图片文件名
│   ├── ids.json                        节点编号台账 v3（构建时读写，必须随仓库保留）
│   └── legacy/                         老工具归档：现在流程不跑、特殊需求才翻出来（见下方说明）
│       ├── README.md                   每个归档脚本是什么、什么时候会用、怎么跑
│       ├── img_gen/                    配图生成（common.py + 每张图一个脚本 + gen_all.py）
│       ├── make_icon.py                生成应用图标（表盘 + Emoji 书本，输出 5 个 dpi）
│       ├── emoji_src/open_book.png     图标用的书本 Emoji 源图（Fluent Emoji, MIT）
│       └── hl_kit.py                   批量标重点辅助（export 缺标条目 / apply 回写）
│
├── cloud/
│   ├── version.json                    更新兜底用的版本号（App 拉不到 GitHub API 时读它）
│   └── thanks.json                     感谢名单的静态备份（API 全不通时 App 读它）
├── local/                             本地私密文件（凭据、keystore、备忘），已被 .gitignore 排除
├── scripts/build.sh                   无 Gradle 构建脚本（aapt2 + javac + d8 + apksigner）
├── scripts/release.py                 一键发布：构建 + 创建 Release + 上传 APK + 写 cloud/version.json + 刷新 cloud/thanks.json
├── 编号规则.md                         递归节点编号规则（日期段在最前 + 日期继承流程）
├── .gitignore                          排除签名密钥、产物、缓存、归档
└── README.md                           本文件
```

未入库的本地目录（已 gitignore，按需清理）：

- `build/`：**纯构建产物**（obj / dex / 中间 apk / 日志），每次构建重建，可以随时整个删掉。
  脚本一律不放这里——历史上 `build/check_data.py`、`build/patch_*.py` 二十多个一次性补丁脚本混在这里，
  清一次 build 就全丢，且多数早已失效，v2.9.3 已把仍在用的搬到 `tools/`、一次性工具归档到 `tools/legacy/`。
- `local/`：发布凭据、签名 keystore、本地备忘（含 `local/backup/` 的笔记旧备份），不进仓库。

---

## 数据流（一条链路）

```
tools/subjects/*.py  （九科递归节点树，人工维护，只写内容不写编号）
  └ chinese.py 末尾用 _load_ext() 把 ext_bipei60.py（必背 60 篇全文）
    与 ext_zuowen.py（作文素材：人物 60 / 语段 60 / 每日一句入口）拼进对应板块
        │  build_authored.py（唯一格式；源文件里不写 id / 日期 / star）
        ▼
app/src/main/assets/data/*.json  （每科一份 nodes 递归树，构建时生成）
        │  打包进 APK
        ▼
App 启动时在子线程用 parseSubject 递归解析，主线程先显示开屏；主界面用「导航栈 + 通用列表页」渲染
```

改完 `subjects/*.py` 或 `build_authored.py`，跑 `python tools/check_data.py` 做一次体检：
查编号重复 / 标题重复 / 空正文 / 加粗与花括号配对 / 未被排版引擎支持的 LaTeX 命令 /
`\frac` 参数完整性 / 上下标写法，并核对编号里的科目码与日期。
`build_authored.py` 分数化（`fracify`）时分子分母的边界扫描对 `()`、`[]`、`{}` 三种括号都要做
深度保护，否则带下标的分式会被拆坏（历史坑，见《工作技巧》踩坑 23）。

配图走另一条独立的链：

```
tools/legacy/img_gen/  （common.py + 每张图一个 <图片名>.py + gen_all.py）
        │  直接运行某个脚本 / 或 gen_all.py
        ▼
app/src/main/assets/img/*.png  （57 张）
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

附加内容链（例题、译文共用一套结构，挂在叶子节点上，不算下级层级）：

```
tools/subjects/*.py 的叶子节点带 "extra" 字段（放在 body 同级）：
  "extra": [{
      "name": "例题", "unit": "道", "mark": "例",     ← 按钮名 / 计数单位 / 列表短标记
      "items": [
          {"lines": [
              {"tag": "题干",       "text": 题干, "tone": "head"},
              {"tag": "答案与解析", "text": 解析, "tone": "body"}]}
      ]}]
        │  build_authored.py 的 to_extra_block 逐行过 latexify，文案一个字不改
        ▼
assets/data/*.json 的叶子带 "extra" 数组
        │  App parseNode 解析进 Node.extras
        ▼
列表行标题后带「（mark）」短标记（例/译，来自数据）；
详情页底部按块数生成按钮（数据给的「name · 组数 unit」）；
点进通用附加内容页：一组一张卡，卡内每行 = 数据给的 tag + 文本，按 tone 决定字号与颜色。
```

**这套结构的关键是「界面不认识内容」**：UI 里没有「例题」「译文」「答案与解析」任何一个字，
它只知道「块 → 若干组 → 每组若干行，每行有 tag / text / tone」。所以：

- 加一类新内容（比如「赏析」「背景」），只写数据，界面一行不用改；
- 改按钮名、改标签文案、改分组单位，只改数据，不用发版逻辑；
- tone 只有三种语义：`head`（主体，大号加粗）、`body`（正文，小一号）、`note`（注释，更小更暗）。
  具体字号颜色在 `toneTextSize()` / `toneTextColor()` / `toneChipColor()` 一处定义。

**译文**就是这么做的：`name` 写「译文」、`unit` 写「句」、`mark` 写「译」，
每组的 lines 是「原文 / 译文 / 注释」三行，原文行里的难词用 `^{①}` 标角标（复用现有的上标渲染）。
原文页的 `body` 里**不放任何角标**，角标只属于译文页那一行原文 —— `check_data.py` 会自动查这一条。

例题内容四条硬规矩：
- 题目必须是**真实高考真题或地区模拟题**，不自己编造；答案与解析由助手生成，但要尽量正确。
- 来源标注格式统一：真题「（某年 高考·某卷）」、模拟题「（某年 模拟·某地区某模拟）」，不写网站。
- 选择题必须带选项、答案、解析三样，缺一不可。
- **需要配图的例题，图片不许自己生成**：用原题的图，或直接截取整张原题的图。

编号链（构建时生成并持久化）：

```
tools/subjects/*.py  →  build_authored.py 的 assign_ids()  →  tools/ids.json（编号台账 v3）
                                                            →  写进 assets/data 的每个节点 id
```

规则见《编号规则.md》。要点：分组 `<科目码>-<各级序号>`；叶子 `<日期>-<父分组编号>-<本级序号>`，
**日期段在最前**。编号只做稳定标识，UI 判断层级只看 `children`、绝不靠编号段数。
序号一经分配永不重排，`tools/ids.json` 必须随仓库保留。
大版本重排时 `assign_ids()` 会从旧 `items` 继承原日期，否则「今日新增」会列出全部条目。

感谢名单链（唯一需要联网的内容）：

```
GitHub issues（带「已修复 / 已处理」标签）
        │  App 拉取 → 按提交人聚合 → 关于页「感谢名单」
        ▲
        └── 兜底：cloud/thanks.json（tools/gen_thanks.py 生成，release.py 每次发版刷新，
                                     推到 main 之后 raw 才读得到，发版必须推代码）
```
取数顺序见「启动时的通道探测」与「感谢名单的取数兜底」两节。

---

## 开发工作流

### 1. 修改知识点内容

只改 `tools/subjects/*.py`，每个文件是 `NAME` + `TREE = [ Node ... ]` 的**递归节点树**：

```python
TREE = [
    {"name": "第一板块 现代文阅读", "children": [
        {"name": "1.1 论述类文本阅读", "children": [
            {"name": "论证思路与结构分析", "body": "…"},
        ]},
    ]},
]
```

- 有 `children` = 分组节点，否则 = 叶子知识点；**层级随便套几层**，UI 自动识别，不用改代码。
- 源文件只写内容：`id` / `d` / `star` / 日期都由 `build_authored.py` 生成，**不要手写编号**。
- 想加一整块新内容：往 `TREE` 里加一个分组节点即可；想再分一层，给它套一层 `children`。
- 改完跑 `python tools/build_authored.py` 重新生成 `assets/data/*.json`，再构建 APK。


### 2. 修改 / 新增配图

配图代码在 `tools/legacy/img_gen/`：每张图一个 `<图片名>.py`，共享绘制能力在 `common.py`。
要改某张图，只编辑对应的那个脚本，单独运行即可只重新生成它：

```bash
cd tools/legacy/img_gen && python m_quad.py  # 只重新生成「数学_3.1_二次函数.png」
python gen_all.py                           # 需要全部重生成时跑这个
```

脚本会把 PNG 直接写到 `app/src/main/assets/img/` 下的对应文件名，跑一次构建就打包进去了。

### 3. 修改 App 代码（UI / 排版引擎 / 更新逻辑）

改 `app/src/main/java/.../MainActivity.java`，然后重新构建。
动手前先看上面的《App 端实现地图》：屏幕表、SvList 分页、导航栈、存储键、每日一句流程都在那里，
照着改就行，不用先通读代码。新增一屏要同步改的四处（常量 / screens[] / screenView / svOf）也在那里列了。

### 4. 修改重点高亮

重点高亮不再单独维护，直接在 `tools/subjects/*.py` 的正文里用 `**要标亮的原文片段**` 包起来即可，App 会渲染成琥珀金加粗。构建时如果 `**` 不成对会报异常。

标亮范围内的 `rac{}`、`\sqrt{}`、`ec{}`、`^{}`、`_{}` 会一起被标亮。这些是自绘的 `ReplacementSpan`，
不会自动继承 `ForegroundColorSpan` / `StyleSpan`，标亮状态由 `applyMarkup(src, hl)` 显式传进 `InlineSpan`。
改这块时记得：新增任何自绘 span 都要跟着传 `hl`，否则又会出现「旁边文字亮了、公式没亮」。

### 5. 给知识点加附加内容（例题 / 译文 / 以后的新类型）

**只动数据，不动 Java。** 在叶子节点上加 `extra`：

```python
{"name": "劝学（荀子）", "lvl": 0, "body": """…原文…""",
 "extra": [{"name": "译文", "unit": "句", "mark": "译", "items": [
     {"lines": [{"tag": "原文", "text": "君子曰：学不可以已^{①}。", "tone": "head"},
                {"tag": "译文", "text": "君子说：学习是不可以停止的。", "tone": "body"},
                {"tag": "注释", "text": "① 已：停止。", "tone": "note"}]},
     …
 ]}]}
```

- `name` 就是按钮文字的前半段（「译文」→ 按钮「译文 · 15 句」）；
- `unit` 是计数单位（「句」「道」「首」），`mark` 是列表行里的短标记（「译」「例」）；
- 一组的每一行有 `tag`（行首小标签，可留空）、`text`（正文）、`tone`（`head` / `body` / `note`）。
- 一个节点可以有多个块（比如同时有例题和译文），详情页会按顺序排出多个按钮。

规则：
1. **原文正文里不放角标**，角标只写在译文页的原文行里（`^{①}` 复用现有上标渲染）。
2. 译文按高考翻译的标准写：直译为主、字字落实，判断句、定语后置、名词作动词都要在译文里体现。
3. 注释只注难懂的字词（通假、活用、特殊句式、古今异义、生僻字读音），不逐字解释。
4. 写完跑 `python tools/build_authored.py` + `python tools/check_data.py`：
   后者会校验结构完整性，并对译文块做「原文能否按顺序对上正文」的比对，漏句错位会直接报出来。

---

### 6. 抽查完善知识点（每批 100 条）

不是全库重写，而是**定期随机抽一批、逐条人工打磨**：

1. 抽样（脚本只做抽样，不碰内容）：从九科源文件分层随机抽 100 条，
   排除每日一句入口、2.4 必背 60 篇原文、优美语段原文（这三类不该改写）。
   抽过的记进 `local/抽取记录.json`（本地、不入库），下次按「科目+标题」排除，**不会重复抽到同一条**。
2. 逐条读原文，判断四类问题并改写：**内容空泛**（只有几句口号）、**有错**（公式/史实/概念）、
   **缺重点**（没有 `**...**` 标注）、**排版糊成一团**（长句不换行，手表上没法读）。
3. 改写结果写在 `build/_review/editN.txt`，格式是：

   ```
   @@@ <科目key> | <条目标题>
   <新正文，多行原样写，不需要转义>
   ```

   科目 key 用源文件名：`chinese` / `math` / `english` / `physics` / `chem` / `bio` / `politics` / `history` / `geography`，
   语文扩展内容（必背 60 篇、作文素材）在 `ext_zuowen`、`ext_bipei60`。
4. 落盘：`python tools/apply_review.py build/_review/editN.txt`（预演）→ 加 `--write` 真正写入。
   它会自动把正文里的 `\` 转义成 `\\`（源文件不是 raw 字符串），
   并把 `tools/ids.json` 里该条的**日期段改成当天**（序号不动），这样「今日新增」能看到这批改动。
5. 重建 + 体检：`python tools/build_authored.py` → `python tools/check_data.py`。
6. 如果构建日志出现「合并 A <= B」，说明两条被判为重复、正文被拼接：
   **要人工读一遍拼接结果**，把重复表述合并掉；确认是同一知识点后，
   用 `python tools/drop_nodes.py <源文件> "多余的标题" --write` 删掉多余那条。

抽查的重点是**内容与排版**，不是字数：一行一个要点、关键结论单独成行、公式独占一行，
手表小屏上「扫一眼就能抓住结构和重点」。

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
- 第 2 位 `Y`：资料库新增内容（知识点、配图、高亮、例题）；
- 第 3 位 `Z`：功能新增、界面调整、修 bug（小功能也走这一位）。
- **进位清零**：Y 加 1 → Z 清零；X 加 1 → Y、Z 都清零（如 2.1.5 加内容 → 2.2.0）。

改 `AndroidManifest.xml` 的 `versionName`（`versionCode` 同步 +1 即可，仅作整数递增，不参与比较）。App 内更新比较用的是 `versionName` 与 Release 的 tag。

---

## 云端更新机制

- App 启动时后台静默检查，设置页「检查更新」可手动触发。
- 取版本号（每一级都优先走启动时测出的通道，见下一节）：
  1. `api.github.com/.../releases/latest`，失败换能转发 API 的镜像；
  2. 同时读静态文件 `cloud/version.json`（`release.py` 发版时按 `versionName` 自动写入并提交推送）；
  3. **两边交叉比对，取更新的那个**。镜像普遍缓存 API 响应，只信 API 会在刚发完版时拿回上一个版本。
- API 请求一律带 `?nocache=<当前毫秒>` 穿透镜像缓存；Release 资源名必须与 tag 对得上才采用其下载地址，否则按 tag 拼 URL。
- 用 `versionName` 与最新 tag 做语义化比较（`compareSemver`，按三段数字比）。
- 有新版 → 先申请「安装未知应用」权限 → 下载 APK → 触发覆盖安装。
- 下载源顺序（`MainActivity.MIRRORS`，前缀 + 原地址）：直连 → gh-proxy.com → ghproxy.net → ghfast.top → gh.llkk.cc → github.moeyy.xyz。任一源 15 秒内拿不到 150KB 即判定「太慢」自动切换下一个。
- 屏幕中央有进度卡片，实时显示百分比、已下载/总大小、当前下载源；卡片不拦截点击，可边下边用；卡片底部有「取消」，点后立即断开当前下载连接并关闭进度卡片，不等读超时。
- 同一版本下载过一次即复用本地 `filesDir/update.apk`（`apk_tag` 记录版本），不重复下载。
- 发现新版本先弹「更新说明」弹窗：标题给出版本号，中间是可滚动的更新内容（来自 Release 正文，`trimNotes()` 去掉 Markdown 记号并截到 900 字；内容区限高 `min(dp(190), 屏高*0.45)`，太小会显示不全），下方三个入口：「立即更新」「稍后」「查看历次更新日志」。
- 点「稍后」会把版本号记进 `skip_version`：之后开机自动检查不再打扰同一版本，手动点「检查更新」仍会提示。
- 点「立即更新」时如果还没有「安装未知应用」权限，先去授权；`onResume` 检测到权限已给就自动接着下载（`pendingDownload`），不用再点一次检查更新。
- **下载前会再确认一次是不是最新**（最多等 6 秒）：弹窗弹出到点按钮之间可能又发了新版，确认到更新的就改下那个，避免下到上一个版本。

### 弹窗的绘制注意（所有弹窗通用）

- 卡片整块**不要开软件层**（`setLayerType(LAYER_TYPE_SOFTWARE)`）。层里的内容跟不上内层 `ScrollView` 的滚动，滑起来会留下上一帧的文字（残影）。
- 滚动时靠 `OnScrollChangedListener` 调 `box.invalidate()` 整块重绘；`ScrollView` 关掉过滚动与淡出边（边缘拖影）。
- 窗口背景透明的同时要显式 `setFormat(PixelFormat.TRANSLUCENT)`，否则合成器按不透明窗口处理，滚动时更容易留残影。
- 这三样统一封装在两个方法里，**新增弹窗一律调它们，别自己再写一遍**：
  - `tuneDialogWindow(dlg)`：设 `TRANSLUCENT` 像素格式 + 透明背景。
  - `tuneDialogScroll(sc, card)`：关过滚动与淡出边 + 滚动时整块 `invalidate`。
- **弹窗里的确认按钮要放在滚动区外面**：编辑反馈弹窗曾把「保存修改」放进 `ScrollView` 内部，
  类型选择占几行、描述框再占几行，按钮被顶到屏幕下方看不见，用户反馈「没有确认按钮」。
  现在编辑弹窗是「竖排容器 = 上面可滚动的内容区 + 底部固定的操作条」，按钮永远可见。
- **滚动区必须有不透明背景色**（`sc.setBackgroundColor(...)`）：背景透明时滚动只重画脏区，
  上一帧的像素留在原地就是残影。
- **窗口格式必须在 `show()` 之前设**（`tuneDialogWindow(dlg)` → `dlg.show()`）：
  `setFormat(PixelFormat.TRANSLUCENT)` 在窗口真正创建时才生效，show 之后再设是无效的，
  窗口已经按不透明格式建好，合成器只重画脏区 → 残影。
  这是「更新弹窗没残影、编辑弹窗有残影」的根本差别：更新弹窗的顺序天生是对的，编辑弹窗写反了。
- **弹窗里有 EditText 时要管输入法**（`tuneDialogInput(dlg)`）：
  `SOFT_INPUT_STATE_ALWAYS_HIDDEN` 别一打开就弹键盘挡住内容；
  `SOFT_INPUT_ADJUST_NOTHING` 让键盘弹出时窗口不变尺寸——否则弹窗整体跳动，
  跳动过程中合成器只重画脏区，于是「拖影 + 闪」一起出现（表屏小，键盘还会挡住弹窗）。
- **滚动区限高用 LayoutParams 固定高度**，别重写 `onMeasure` 传 AT_MOST：
  AT_MOST 下内容高度由子 view 决定，滚动时重绘范围算不准。
- **圆屏上的矩形弹窗要收窄**（`sizeDialog()` 里圆屏用 0.78 屏宽）：矩形的左右角会伸到圆弧外，
  内容被裁、按钮点不到（用户反馈「好像漏出来一点点，但点不到」）。
- **选项按钮按 3 列排**：5 个反馈类型按 2 列排 3 行，最后一行会被屏幕下缘裁掉。
  3 列 2 行正好放得下（类型区 94dp，能装进 0.30 屏高的滚动区），也不需要给整行加圆屏安全边距
  （加了反而把格子压窄，4 个字放不下）。

### 启动时的通道探测（全局共用）

- `onCreate` 里后台起 `startRouteProbe()`，并发试探两条通道：
  - **API 通道**：直连 `api.github.com/zen` + `API_MIRRORS`；
  - **raw/github 通道**：直连 `cloud/version.json` + `MIRRORS` 全量镜像。
- 取「能连通且耗时最短」的那个前缀，存 `prefs` 的 `route_api` / `route_raw`；下次启动先套用上次结果，同时后台再测一次刷新。
- 全不通时存 `none`，`apiBase()` / `rawBase()` 退化为直连，行为与探测前一致。
- **公开请求**走 `apiBase()` / `rawBase()`：更新检查、感谢名单、头像。原来的「逐个镜像重试」逻辑保留在后面当兜底。
- **带 token 的请求一律直连**（`apiAuthedBase()` 恒返回空串）：个人信息 `/user`、我的反馈列表与评论。
  - 原因：镜像（gh-proxy.com 等）**按 URL 缓存响应、会忽略 `Authorization` 头**。实测同一 `/user`：
    直连返回本人账号，走镜像返回的是 `aeroheaven1` / `HHPLow`；带伪造的无效 token 走镜像**仍然**返回 `HHPLow`——
    镜像根本没看 token。`/user` 是「凭 token 返回当前用户」的接口，走镜像必然串成别人的账号。
  - 曾经出过「个人主页显示成别人账号」的 bug：探测打的是公开接口 `api.github.com/zen`，镜像能转发就算通道可用，
    于是带鉴权的 `/user` 也走了镜像。
  - 这条规矩对所有带 token 的接口都适用，别为了「能连上」把 token 交给第三方镜像站。
- 探测结果出来后浮一个底部气泡「已连接服务器 · 直连 / 镜像域名」，淡入停留 1.8 秒自动移除，不拦截操作，也不弹任何界面。
- 首启显示调节引导页时不弹气泡（避免盖住「完成」按钮）；点完成后 `recreate()` 会重跑一次，那时补上。
- 手动重测：设置 → 其他 → 「重新连接服务器」（`startRouteProbe(true)`）。不沿用上次结果，全部候选重测一遍，
  下面那行实时显示「GitHub 接口：直连 / 镜像域名 / 未连通；文件与头像：…」，检测中显示「正在检测各条线路…」，
  成败都弹气泡（失败是「没连上服务器，稍后再试」）。网络切换后用它最省事。
- 登录相关 POST（`device/code`、`oauth/access_token`）与提交反馈仍走直连，不经过镜像，避免把 token 交给第三方。
- 个人主页的账号名以直连返回的为准（本地只当缓存）。老版本若已把别人的账号名存进 `prefs`，
  升级后进入个人页会自动更正，并清掉按旧账号名存的头像缓存文件。

### 感谢名单的取数兜底

- 取数顺序：启动时测出的最优通道（`apiBase()`）→ 直连（5 秒超时）→ `API_MIRRORS` 里能转发 API 的镜像 → 静态备份 `cloud/thanks.json`（走 `MIRRORS` 全量镜像）。整轮 25 秒总时限。
- `cloud/thanks.json` 由 `tools/gen_thanks.py` 生成，`release.py` 每次发版自动刷新；内容是最小 issues 数组（只带 `user.login` 与一条已修复标签），App 端复用同一个 `parseThanks()`，解析逻辑不分叉。
- 整轮重试有 25 秒总时限；全都拿不到就用 `prefs` 里上次成功的结果（`thanks_cache`）兜底，并提示「网络不通，下面是上次加载的名单」，下次进页面会重新试。
- 头像同样加了镜像兜底（只多试前两个，不为一张图干等）。

---

## 更新日志（App 内）

- 数据源：`app/src/main/assets/changelog.json`（随 APK 打包，离线可看），结构 `{"version":1,"log":[{"v":"2.0.0","d":"10-28","items":["…"]}]}`，按版本倒序排列。
- 入口：设置 → 关于 → 「更新日志」；当前版本那一栏用高亮底色并标注「当前」。
- **发版纪律**：每次出包前先在 `changelog.json` 的 `log` 数组**最前面**插入本版条目（版本号与 `versionName` 一致），否则新版本在更新日志里查不到。只写用户能感知的变化，一条一句话，不写内部实现。
- Release 正文（`release.py` 的参数）与 changelog 条目分开维护：前者给「更新说明弹窗」用，后者给「更新日志」用；两边说法要一致。

---

## 反馈功能（App 内提交 Issue）

- 登录入口：设置 → 「个人主页」（显示头像、昵称，可退出登录）；反馈页未登录时点状态行或提交会自动跳过去。
- 反馈入口：设置 → 常用 → 反馈与建议。
- 登录用 GitHub Device Flow：`MainActivity.GH_CLIENT_ID` 硬编码 OAuth App 的 client_id，scope 为 `public_repo`。
- 流程：点登录 → 取 8 位授权码 → 自动复制到剪贴板并弹原生对话框 → 反馈页显示固定网址二维码（`assets/qr_login.png`，内容 `https://github.com/login/device`）+ 大号授权码，手机扫码后输码授权（手表浏览器支持不佳，作为备选按钮）→ 手表每 5 秒轮询 → 成功后 token 存 SharedPreferences（`gh_token` / `gh_login`）。
- 二维码是静态图片，不含授权码：授权码每次不同，静态图只承载固定网址，用户扫完照表上的码输入。
- 提交：`POST /repos/<repo>/issues`，正文自动附带版本号、机型、Android 版本、圆/方屏、最近浏览的知识点路径。
- **反馈类型按「用户看到什么现象」分类**，每类下面有一行说明随选中项实时变化，用户不用猜：
  显示问题（公式错位、显示不全、残影、缩放没生效、按钮找不到）/ 内容有误（答案、史实、公式写错）/
  闪退卡顿 / 功能建议 / 其他问题。
  旧反馈里的类型名（`公式显示` 等）由 `parseIssueType()` 映射到新分类，历史记录照样能编辑。
  登录与提交都走直连，不经镜像（请求里带 token，不给第三方）。
- 「我的反馈记录」：拉取 `issues?creator=<自己>&state=all`（走 `apiBase()`），列表显示状态与日期，点开看正文 + 最新回复。
- 状态约定（靠 label、state 与正文标记）：有 `已修复` 标签 → 已修复；正文含「提交者已撤回」→ 已撤回；
  issue 已关闭 → 已处理；有 `处理中` 标签 → 处理中；否则 待处理。处理一条反馈时：先打 `处理中`，修完补评论 + 打 `已修复` + 关闭。
- **自己发的、还没受理（待处理）的反馈可以改或撤**：打开那条反馈，底部会出现「编辑这条反馈」「撤回这条反馈」。
  - 编辑：弹窗里改类型与描述，保存时整体覆盖 GitHub 上的标题和正文（`updateIssue`）。
    弹窗结构是「标题 → 可滚的类型选择 → 常驻的输入框 → 底部固定按钮条」：
    输入框与按钮都必须在滚动区**外面**，否则屏小的时候它们被内容顶到可视区外，用户会以为没有输入框、没有确认按钮。
  - 撤回：正文末尾追加一行「（提交者已撤回 · 时间）」并把状态置为 CLOSED（`updateIssue` + `state: CLOSED`），列表里显示成「已撤回」，跟「已处理」区分开。
  - 已被受理（处理中 / 已修复 / 已关闭）或不是自己发的，就不显示这两个按钮，避免改到正在处理的内容。
- **项目所有者（@hnxy114514）可以删除任意一条反馈**：打开任意反馈底部有「删除这条反馈（开发者）」，删前弹确认框。
  REST 没有删 issue 的接口，必须走 GraphQL 的 `deleteIssue`，需要仓库写权限；失败时把 GitHub 返回的原因直接提示出来。
- 上面三个操作都走 GraphQL（`POST https://api.github.com/graphql`，直连带 token，认证头用 `Bearer`），
  issue 的 `node_id` 从列表接口带回来直接用。改完会重新拉一次列表（`mineDirty` 标记，回到列表页时触发）。

---

## App 端实现地图（不读代码先看这一节）

Android 端只有 **一个 Activity、三个 Java 文件**：
`MainActivity.java`（约 8300 行，界面、排版引擎、联网、更新、本地存储全在里面）、
`OverScrollScrollView.java`（圆屏内容过滚的滚动容器，列表和正文共用；方屏过滚距离为 0）、
`ApkFileProvider.java`（把下载好的安装包以 content:// 交给系统安装器）。
没有第三方 UI 库，**没有 XML 布局**，除主题与开屏底图外的所有界面都是代码里 new 出来的 View。
所以改界面 = 改这个文件里的 `buildXxxScreen()` / `renderXxx()`，不需要找 layout 文件。

### 启动流程与开屏（v2.9.2 起）

```
系统先画 windowBackground
  └ AppTheme.Splash → res/drawable/splash_bg.xml（黑底 + 居中图标）
     这一步在 Activity 创建之前就完成，所以从点图标开始就不是黑屏
        ▼
MainActivity.onCreate：只做很快的事
  ├ 读 prefs（字号 / 缩放 / 滑动返回 / 震动 / 科目顺序）
  ├ showBootSplash()   自绘开屏：图标 + 转圈 + 「正在载入知识库…」
  └ bootLoad()         起子线程去读数据
        ▼
子线程：cleanupOldApk() → loadData() → loadMarks() → loadOrderKeys()
        ▼
回主线程 bootFinish()：补足最短显示时长 BOOT_MIN_MS = 320ms（数据读太快也让 logo 亮一下）
        ▼
enterApp()：buildUi() 建主界面（setContentView 换掉开屏）→ 定首屏
          → startRouteProbe() 探测网络通道 → checkUpdate() → installCrashHandler()
          → 700ms 后问候语 → 1600ms 后每日一句自动刷新
```

- **主线程不干重活**：九科 JSON（1300+ 节点）全在子线程解析；`loadData` / `loadMarks` / `loadOrderKeys`
  只写内存字段、不碰任何 View，所以可以安全地放在子线程。
- 子线程**不能弹 Toast**（没有 Looper）：加载异常记到 `loadError`，进主界面后再提示。
- 开屏是纯 View（ImageView + ProgressBar + 一行字），创建只要几毫秒，能立刻上屏；
  它是 `setContentView` 的第一份内容，主界面建好后被 `setContentView(root)` 顶掉。
- 窗口底色全程是 `C_BG`（纯黑），换内容不会白闪。

### 界面骨架：18 个「屏幕」叠在一个 FrameLayout 里

`buildUi()` 建一个 `root`（FrameLayout），往里 add 18 个 `newScreen()`（LinearLayout，位置 `fp()` 铺满），
同一时刻只有一个 `VISIBLE`，切换就是「把旧的 GONE、新的 VISIBLE」——不是跳 Activity，所以很快、也没有转场黑屏。

屏幕常量与「第几个」强绑定，**顺序不能乱**：

| 值 | 常量 | 页面 | 怎么建 / 怎么画 |
|---|---|---|---|
| 0 | S_SUBJECT | 主页（科目 + 每日问候 + 入口） | `buildSubjectScreen()` / `renderSubjects()` |
| 1 | S_NODE | 通用节点列表（任意层级） | `renderNodes()` → `bindNodes()` |
| 2 | S_DETAIL | 知识点正文 | `openDetail()` |
| 3 | S_SETTINGS | 设置 | `buildSettingsScreen()` |
| 4 | S_SEARCH | 全局搜索 | `refreshGlobalSearch()` |
| 5 | S_FEEDBACK | 反馈（提 issue） | `buildFeedbackScreen()` |
| 6 | S_MYFEEDBACK | 我的反馈 | `renderMine()` |
| 7 | S_PROFILE | 个人主页（GitHub 账号） | `renderProfile()` |
| 8 | S_CHANGELOG | 更新日志 | `buildChangelogScreen()` |
| 9 | S_ABOUT | 关于 | `buildAboutScreen()` |
| 10 | S_DISPLAY | 调节显示 | `buildDisplayScreen()` |
| 11 | S_TODAY | 今日新增 | `renderToday()` |
| 12 | S_EXTRA | 附加内容（例题 / 译文…） | `openExtra()` |
| 13 | S_FAV | 我的收藏（第一级：按科目） | `renderFav()` |
| 14 | S_HIST | 最近浏览 | `renderHist()` |
| 15 | S_FAV_SUB | 某科目的收藏（第二级） | `renderFavSub()` |
| 16 | S_DAILY | 每日一句 | `renderDaily()` |
| 17 | S_SENT | 每日一句的收藏 / 浏览记录 | `renderSent()` |

`SCREEN_COUNT = 18`。四个地方必须同步改，漏一个就会错位或崩：
① 常量与 `SCREEN_COUNT`；② `showScreen()` 里的 `screens[]` 数组（**下标即常量值**）；
③ `screenView(s)` 的 switch（滑动预览用）；④ `svOf(s)` 的 switch（返回时记滚动位置用）。
另外 `backTarget()` 决定返回键去哪，`showScreen()` 里要加该页的渲染分支。

### 加一屏的完整步骤

1. 在常量后面加 `S_XXX = 18`，`SCREEN_COUNT` 改 19；
2. `buildUi()` 里 `xxxScreen = newScreen(); ... root.addView(xxxScreen, fp());`；
3. `screens[]` 数组末尾加 `xxxScreen`（顺序必须和常量值一致）；
4. `screenView()` 与 `svOf()` 各加一个 `case S_XXX:`；
5. `backTarget()` 里写返回去哪，`showScreen()` 里加 `else if (s == S_XXX) renderXxx();`；
6. 需要从主页进就在 `buildSubjectScreen()` 加一行入口。

### SvList：唯一的列表实现

不用 ListView/RecyclerView，全部是 `SvList`：**外层 `OverScrollScrollView` + 竖排 LinearLayout，一行就是一个普通 View**。

- `addHead(view)` 把返回键、标题、搜索框放进滚动区顶部，**跟着内容一起滚**（不固定占位）。
- `bind(RowSource)` 交给它一个「有多少行 + 第 i 行长什么样」的对象，它负责分页：
  首屏 `first = 30` 行，滚到还剩约一屏续载 `page = 40` 行，每帧只追加 `chunk = 15` 行。
- 搜索结果与今日新增受 `HIT_LIMIT = 400` 限制（一次最多 400 行），超了标题会注明。
- `enter()` 进页面时跳过顶部占位把第一个内容贴顶；`restore()` 恢复上次滚动位置（`savedY`，只有「返回上一级」才恢复）。
- 圆屏才有上下 25% 屏高的透明占位（`makeScrollSpacer()`），方屏不加；内容不满一屏时 `fitSpacers()` 自动收掉。

### 数据模型只有一个 Node

```java
Node {
  String id, name;            // 编号、标题
  String body;                // 叶子正文（单段，多行用 \n）
  List<Node> children;        // 非空 = 分组节点
  List<ExtraBlock> extras;    // 附加内容块：例题、译文…按钮名与标签全由数据给
                              //   ExtraBlock { name, unit, mark, items }
                              //   ExtraItem  { lines }
                              //   ExtraLine  { tag, text, tone(head/body/note) }
  int star; String img, d;    // 星级、配图、加入日期
  String daily;               // 非空 = 每日一句入口（phil / poem）
  Node parent; Subject subj;  // 父链与所属科目（路径、收藏页都要用）
  int savedY;                 // 离开时记下列表位置
  boolean isLeaf() { return children.isEmpty(); }
  String path();              // 面包屑：科目 › … › 自己，由父链现算
}
```

**层级判断只看 `children`，绝不看编号段数**。所以数据里三层、四层、五层混着都行，UI 一行不用改。

### 导航：一个栈 + 一个通用列表页

- `curSubject` 当前科目；`navStack` 从科目根走到当前分组的节点链；`curNode` 当前列表页展示的是谁的 children。
- `openNode(n)`：`n.isLeaf()` → 开详情；否则压栈、`renderNodes()`、留在此页。
- `nodeBack()`：弹栈；栈空且是从收藏/搜索进来（`nodeEntryBack`）就回那一页，否则回主页。
- `openNodeFrom(n, back)`：从搜索 / 收藏 / 历史 / 今日跳进来时，先把祖先链补进 `navStack`，
  保证详情页的面包屑和返回都有得退。
- 面包屑 `currentPath()` + `setPath()` 按栈动态拼，有几级几段，每段带 `pathSpan` 可点跳回那一层；
  最后一段灰字不可点。
- 切页动画在 `showScreen()`：按 `pageDepth()` 差决定「从右进 / 从左退 / 同级上浮」，185ms 减速曲线；
  节点页内部换层（S_NODE→S_NODE）用 `slideNodeList(±1)` 单独做轻动画。

### 渲染：正文与重点高亮

正文一律走 `applyMarkup(text)`：它认两种写法——
`**加粗**` → **加粗 + 琥珀金 `C_HI`**（就是「标重点」）；`$...$` 里的公式 → 自绘 span（分数、根号、上下标、向量）。
新增任何自绘 span 都要把「当前是否在标亮区间」显式传下去，否则会出现「旁边字亮了、公式没亮」。

### 本地存储一览（都在 SharedPreferences `settings` 里）

| 键 | 内容 | 说明 |
|---|---|---|
| `fav_ids` | 收藏的节点编号（逗号分隔） | 稳定编号，跳过 6 位日期段 |
| `hist_ids` | 浏览过的节点编号 | 最多 `HIST_MAX = 50`，`pushHistory()` 维护 |
| `daily_pool` | 本地号 → 句子原文（JSON） | 每日一句专用，不存知识点编号 |
| `daily_fav` | 收藏的本地号列表 | 有序，最新在前 |
| `daily_hist` | 浏览过的本地号 | 最多 `DAILY_HIST_MAX = 50` |
| `daily_day_phil` / `daily_day_poem` | 上次自动刷新的日期（yyMMdd） | 两类各自记 |
| `downloaded_tag` | 已下载的版本标记 | 用来判断安装包是否可以删 |
| `subject_order` | 主页科目顺序 | |
| `font_size` / `list_size` / `title_size` / `sub_size` / `ui_scale` | 显示设置 | 改完必须重建界面 |
| `display_tuned` | 是否已完成首次显示调节 | false 时启动进 S_DISPLAY |

收藏页两级：`S_FAV` 只列「有收藏的科目 + 每日一句」，点科目进 `S_FAV_SUB` 倒序平铺，每行带短路径小字。
所有节点行右侧都有收藏星（每日一句入口行例外，句子在页面里收藏）。

### 每日一句（联网取句 + 本地文本收藏）

- 接口：一言 `https://v1.hitokoto.cn/?{分类}&encode=json&charset=utf-8`；
  哲思美句 `c=k&c=d`，古诗文 `c=i`。免费、免登录。返回取 `hitokoto` / `from` / `from_who`
  （`null` 字符串要过滤掉，否则界面上会显示「—— null」）。
- 每类至少备 `DAILY_WANT = 10` 条：取不够继续取，去重；取不到就提示重试，**绝不编句子**。
  句子只放内存 `dailyMem`（本次运行有效），不写盘、不进 assets。
- 每天第一次打开 `checkDailyForToday()` 各刷一批；页面里「换一批」手动刷。
- 手动刷新是一套「可撤销」流程：`startDailyRefresh()` 把当前这批存进 `dailyBackup`、页面切成加载态
  （固定加载块：转圈 + 取消；此时翻页/收藏/换一批先隐藏，避免误点）；
  拉到新句子 → 换新 + 清备份；点取消或拉取失败 → 还原备份。`dailySeq` 用来丢弃已取消的在途结果，
  避免「取消了结果又回来」。
- 收藏与历史存「本地号 + 原文」：`ensureDailyNo()` 找同文本的号、没有就取当前最小空号；
  取消收藏或历史被挤出后 `dropDailyIfUnused()` 在无人引用时把文本从 `daily_pool` 删掉，号随之空出复用。
- 界面：句子卡片和加载块都是**固定 head**，翻页只改文字，不重建列表 —— 否则页面会跳回顶部。

### 在线更新与安装

1. `checkUpdate()` 拉 GitHub Release，比对 `versionName`，弹窗展示更新说明；
2. 确认后 `downloadApkWithMirrors()` 依次试「直连 + 几个镜像」，下到 `files/update.apk`（先写 `.tmp` 再改名）；
   **下载前先删掉上一次的旧包**，避免占空间、装错版本；
3. 写完记 `downloaded_tag = v<新版>`，再拉起安装器；
4. `installApkWithRetry()`：等界面稳定、**要求窗口已拿到焦点**才 `startActivity` 安装；
   拿不到焦点每 400ms 重试，最多 4 次，仍不行就 `pendingInstall` 等回到前台再装；
5. `ApkFileProvider.query()` 要返回真实的文件名和大小，返回 null 时个别系统会认为 URI 无效、直接不弹安装器；
6. `cleanupOldApk()` 在下次启动时清理：版本号已经等于 `downloaded_tag` 说明装上了，删包；顺带清掉残留 `.apk` 与 `.tmp`。

### 显示设置为什么必须重建界面

字号是 `Metrics` 快照、尺寸走 `dp()`（含 `uiScale`），都是**建界面时就算进控件**的，
光电改变量界面看起来完全没变。所以调完必须重建——但用 `rebuildUi()` **就地重建**（重新 `buildUi()` 再 `showScreen(backTo)`），
**不要用 `recreate()`**：那会把 Activity 整个销毁重建，窗口背景先露出开屏图、还要再走一遍启动流程，
用户看到的就是「调完显示退出来闪一下黑屏」。

---

## 界面交互要点

- **圆屏滚动安全区**：圆屏页面无上下黑边（`newScreen` 上下 padding 归零，只留左右），滚动范围与安全区
  都由滚动容器自己承担；方屏不受影响。
  - **所有列表都是 ScrollView**（`SvList`：外层 `OverScrollScrollView` + 竖排 `LinearLayout`，一行就是一个
    普通 View），不再用 ListView——边界回弹统一走系统 `overScrollBy` / `springBack`，与设置页、详情页手感一致。
  - **顶部标题/搜索框随内容滚动**：返回键、标题、搜索框都作为列表头（`addHead`）放进滚动区，
    详情页、例题页的标题/路径放进滚动区 `inner` 第一项，随内容一起滚。
  - **首尾占位 + 定位**：圆屏列表在上下各放一段透明占位（`makeScrollSpacer()`，高 25% 屏高），
    让首尾内容能滑到屏幕中间；进入页面时跳过顶部占位、第一个内容贴最顶端。
    方屏 `overDist()=0`，不加占位。
  - **内容不足一屏时占位自动收掉**（`fitSpacers()`）：这时本来就滚不动，留着只会让顶部空一大块；
    续载把内容撑过一屏时同步补偿滚动位置，画面不会突然往下跳。
  - **分页渲染**：首屏 30 行，滚到还剩约一屏时续载下一批 40 行，每帧只追加 15 行，滑动不顿；
    「今日新增」这种数据不是一次到位的，由 `fill` 先补数据再渲染。命中再多一次也只渲染 400 行
    （`HIT_LIMIT`），再多会让手表吃不消，标题会注明「先列出前 400 条」。
  - **不能在外层 `newScreen` 上加安全区 padding**：外层 padding 固定、内容进不去，会永久留黑边（踩过坑）。
  - **正文页同样要加占位**：关于、更新日志、反馈、个人主页、显示调节这几页也是滚动容器，
    统一在渲染完调 `padInner()` 补上下占位、进入时 `scrollToTop()` 跳过顶部占位。
    只给列表加、漏掉这几页的话，它们就没有上下留白（用户反馈过「有的列表根本没有空白占位」）。
  - **内容不满一屏时底部占位会自动加厚**（`fitSpacers()`）：不补的话可滚动范围为 0，顶部占位等于白加，
    首尾内容照样划不到屏幕中间。
- 每页的「‹ 返回」是随内容滚动的第一项，不固定占用顶部空间；系统返回键/手势始终可用。
  - **只有返回上一级才回到离开时的位置**（`goBack()` 打标记 + `SvList.restore()`）；
    之后再正向点进同一页一律回到默认位置（顶部），不会停在半中间。
- **右滑返回**：设置 → 显示 → 「右滑返回」可开关（默认开）。起手点须在屏幕左侧 1/4 内，向右拖动时当前页跟手右移、下方露出上一级页面的实时预览（视差 32%）；松手超过 46dp 即滑出并切页，不足则两页一起回弹。主页不触发；在输入框内起手需从最左 18dp 内且滑 80dp 以上（避免拖动光标误触）。
  - **拖动字号/缩放滑块时右滑返回不介入**（`sliderTouching` 标志），避免调滑块调着调着退出页面。
- **全屏看图时右滑不生效**，只能点左上角「‹ 关闭」（图内支持双指缩放、单指拖动、双击定点缩放）。
- 个人主页：设置 → 「其他」里，未登录显示「登录 GitHub」，登录后显示圆形头像 + @昵称 + 真实名字；头像会缓存到本地，离线也能显示。
- 全局搜索：结果项第一行是标题、第二行是短路径小字（「科目 › … › 直接父级」，深层也不丢归属）；右侧 ✕ 清空关键词；从详情返回会保留关键词与结果。
- **通用节点列表页（UI 动态层级）**：主页点科目进 `S_NODE`，靠节点的 `children` 自动判断
  下面还有几级——有下级就压栈继续列，是叶子就开详情页。层级深度完全由数据决定，
  3 级 / 4 级 / 5 级并存都不用改代码；原来的 `S_BLOCK → S_CHILD → S_ITEM` 三个固定屏已合并。
  - 导航栈 `navStack` 记录从科目根走到当前分组的路径，返回时逐层弹栈，栈空回主页。
    面包屑按栈动态拼（几级就几段），每段可点跳回对应层；列表页节内搜索支持按后代内容匹配分组。
  - 从收藏 / 搜索 / 历史 / 今日跳进节点时，`openNodeFrom()` 会先补齐祖先链，
    保证详情页面包屑与返回都有得退；`nodeEntryBack` 记住来源页，退出时回到那一页而不是主页。
- **我的收藏**：统一**一套** `favIds`（分组节点与叶子知识点都能收藏），页面改成两级：
  - 第一级只列「有收藏的科目」（带各科目收藏数），点科目才进 `S_FAV_SUB`；
  - 第二级把该科目的收藏内容**倒序平铺**，每行下面一行小字标出它在哪一层（短路径）。
  - 所有中间分组节点的列表行右侧**都带收藏星**，详情页叶子标题右侧也带星；
    左侧同宽透明占位保证标题居中。`toggleFav()` 统一读写，不再分章/块/知识点三套 prefs。
- **必背 60 篇（全文）**：语文「第二板块 古诗文阅读 › 2.4 必背 60 篇（全文）」，
  按统编教材册次分五组（必修上/必修下/选择性必修上中下），共 60 条：文言文 20（必修 10 + 选必 10）
  + 诗词曲 40。每条正文是教材要求背诵的**节选全文**（不是古籍全篇），生僻字、多音字在正文里
  就地注音，写法是「字（拼音）」，随数据一起打包。
  注意与 2.3 里那五个「推荐背诵名句·…」区分：那些只收重点名句，2.4 收整篇。
  文言文逐篇配译文（`extra` 里名字为「译文」的块）：每组三行——原文（带 ①②③ 角标）、
  译文（高考直译口径）、注释（只注难词）。**原文页不加角标，角标只在译文页的原文行**。
  进度：必修上册 4 篇（劝学 15 组、师说 30 组、赤壁赋 32 组、登泰山记 32 组）已完成，
  其余册次按同样口径继续补。
- **作文素材**：语文「第四板块 写作 › 4.3 作文素材」，三部分：
  - 人物素材 60 条：一人一条，同一人物的多件事迹合并进同一条，标题写成「人物名·他做的事」，
    正文分【事迹】【常用主题】两段，方便直接搬进议论文。
  - 优美语段 60 条：全部是名家原文段落（朱自清《匆匆》《春》《背影》《荷塘月色》、史铁生《我与地坛》
    《秋天的怀念》、老舍《济南的冬天》、郁达夫《故都的秋》、鲁迅《从百草园到三味书屋》、宗璞《紫藤萝瀑布》、
    刘成章《安塞腰鼓》、茅盾《白杨礼赞》），编号命名，正文末尾标出处，不是自撰。
- **每日一句（联网取句 + 本地文本收藏）**：入口在「写作 › 作文素材 › 每日一句」，分「哲思美句」「古诗文」两类。
  - 句子**不在 assets 里**，App 端调一言接口 `https://v1.hitokoto.cn/?c=…&encode=json` 实时取：
    哲思美句用 `c=k&c=d`（哲学/文学），古诗文用 `c=i`（诗词）。接口免费、无需注册登录。
  - 每次取至少 10 条（`DAILY_WANT`），多了不嫌多，少了继续取；取不到就提示重试，**绝不编句子**。
    只在内存里留本次运行这一批（`dailyMem`），不写盘。
  - 每天第一次打开 App 自动各刷一批（`checkDailyForToday`，两类各记 `daily_day_*`）；
    页面里另有「换一批」按钮可随时手动刷新，以及上一句 / 下一句翻看。
  - 收藏与浏览记录**不存知识点编号**，而是「本地号 + 句子原文」：
    `daily_pool`（号 → 文本）、`daily_fav`（收藏的号，有序）、`daily_hist`（浏览的号，最多 50）。
    号在 `ensureDailyNo()` 里分配，优先复用当前空着的最小的号；取消收藏或历史被挤出后，
    `dropDailyIfUnused()` 会在没人引用时把文本从 pool 里**彻底删掉**，号随之空出来给新句子用，
    避免本地越攒越多。
  - 「我的收藏」第一级会多出一行「每日一句 · N 条」，「最近浏览」里也有一行入口；
    句子列表点一下复制整句，收藏模式长按取消收藏。
- **最近浏览**：打开过的叶子自动记进主页「最近浏览」，最新的排最上面，**最多 50 条**
  （`HIST_MAX`，超出自动清掉最旧的），页面顶部有一行文字把这条例明出来。
  - 收藏与历史都存**稳定编号**（跳过编号里的 6 位日期段），日期变了也不丢；
    查不到的编号（条目已删）自动跳过。
- **新增知识点（今日）**：主页「设置」上方，**严格按设备当天日期**匹配节点编号首段的日期
  （`yyMMdd`，见《编号规则.md》），只显示当天那一批，标题「今日新增 · N 条」。
  叶子解析出的 `d` 字段即该日期；整库重排编号时必须从旧台账继承日期，否则会一次列出全部条目。
  当天确实没有新增时标题是「今日暂无新增」，下面给一个「看看最近一批（MM-DD）」的入口，
  点了才去看历史上最近的一批，标题同步改成「最近新增（MM-DD）· N 条」——
  **不能默认拿历史批次顶替当天**，否则日期永远是错的（曾经 29 号还挂着 28 号的内容）。
  `todayFallbackOn` 记着当前是不是在看历史批次，跨天进页面会强制回到「只看今天」。
  列表分页渲染：首屏 30 行，滚到还剩约一屏时续载下一批 40 行，只往已有内容尾部追加、不重建整表。
- **显示调节（设置 → 显示）**：正文字号、列表字号、标题 / 副标题字号、整体缩放四项。
  - **整体缩放滑块拖动过程中就有实时反馈**：`applyLiveScale()` 只把**预览卡片**按比例放大。
    不要去缩放整个根容器——那会让内容超出屏幕被裁、滑块自己变形、比例跟真正重排的结果对不上，看着很怪。
    预览卡片本来就是用来看效果的，界面本体保持不动，松手后复位并精确重建。
  - 其余几项（字号）松手即生效：保存设置后重建整个界面，并回到用户刚才所在的页面（不把人甩回主页）。
  - 整体缩放 `ui_scale`（0.85 ~ 1.40）**必须同时作用在间距和字号两条线上**：
    间距、图标、圆角统一走 `dp()`（内部乘 `uiScale`）；字号统一走 `Metrics` 的派生值
    （`titleSize` / `subtitleSize` / `backSize` / `secTagSize`，构造时已乘）与 `fsSp()` / `lsSp()`。
  - 新增任何文字都要用上面这几个入口，不能直接 `setTextSize(常量)`，否则又会出现「间距变大了、字没变」。
  - **改了缩放必须重建界面**：字号是 `Metrics` 的快照、控件尺寸在创建时量好，
    光改变量不重建，界面看起来完全没变。统一走 `applyDisplayChange(backTo)`：保存 + `recreate()` + 回到指定页。
  - 返回键（「‹ 返回设置」）也要走 `applyDisplayChange`，否则拖完直接返回，设置就白改了。
- **附加内容（例题 / 译文）**：数据里带 `extra` 的知识点，列表标题后跟「（例）」「（译）」这类短标记；
  点进知识点，正文最下面按块生成按钮（如「例题 · 1 道」「译文 · 15 句」，文字全部来自数据）；
  点开进入同一个附加内容页：一组一张卡，卡内按行渲染「数据给的标签 + 文本」，
  页顶副标题显示块名。返回回到详情页。加新类型只写数据，界面不动。
- **路径面包屑**：顶部按导航栈动态拼，**有几级就几段**（不再固定「首页 › 科目 › 章 › 节」）——
  点「首页」直接回主页，点科目回到该科目顶层，点中间任一段就截断栈跳回那一层；
  最后一段是当前所在层级，灰字不可点。**长按任意「‹ 返回」按钮也能一步回主页**（会提示「已回到首页」）。
  用 `ClickableSpan` + 自写触摸判定（按下到抬起几乎没位移才算点击），不用 `LinkMovementMethod`，
  否则手指在标题上起手滑动会被吃掉、滑不动；起手时还要 `requestDisallowInterceptTouchEvent(true)`，
  手指真滑动了再放开，否则滚动容器会把 UP 吃掉、点了没反应。
- **显示调节首次引导**：没存过「已调节」标记的用户（含老版本升级上来的）首次启动会先看到字体 / 缩放调节页，
  必须点「完成，开始使用」才进主页，返回键不放行；确认过之后不再出现。
- **文字一律居中**：按钮、列表项、标题、说明小字都居中；只有详情页正文长段落、更新说明条目、更新日志的「· 」条目
  和输入框（搜索框、反馈描述）保持靠左。
  - **带图标的按钮**必须在右侧补一个同宽的透明占位（`applyIcon()` 里已做）：
    TextView 会把「图标 + 文字」当整体居中，不补占位的话文字会被左侧图标顶偏，看着就不在按钮正中。
  - 新增任何说明文字记得 `setGravity(Gravity.CENTER)`，只有真正的长段落和输入类控件才留 `TOP|START`。
- **连接状态气泡**：通道探测出结果后屏幕底部浮出「已连接服务器 · 直连 / 镜像域名」，1.8 秒后自动淡出，
  不拦操作；失败不弹。设置 → 其他 → 「重新连接服务器」可随时重测。


---

## 注意事项

- `app/src/main/assets/data/` 和 `assets/img/` 都是**生成物**，不要手改，改源文件后重新构建。
- 签名密钥 `release.keystore` 和 GitHub Token 都**不能入库、不能外泄**。
- 发布新版本必须同时改 `versionName` 并推代码，否则 App 检测不到更新。
- **改代码要同步改文档**：动了目录结构、数据流、联网方式、编号规则、界面交互的，同一个改动必须把本文件里对应的章节一起改；
  文档里写死的张数、行数、域名这类事实也要跟着更新（历史上出现过「56 张图」「约 2000 行」这类过时描述）。
  用户能感知的变化还要进 `app/src/main/assets/changelog.json`。
- **素材署名**：应用图标中的书本图形取自 Microsoft Fluent Emoji（MIT License），由 `tools/make_icon.py` 合成圆形表盘后输出。
