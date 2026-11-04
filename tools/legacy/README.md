# tools/legacy —— 老工具归档

这里放的是**现在构建流程不会调用、但特殊需求还会翻出来用**的脚本。
`python build_authored.py` / `scripts/build.sh` / `scripts/release.py` 都不依赖这里的任何文件，
所以这个目录整个删掉也不影响构建和发版。

放进来而不是删掉的判断标准：**将来某个具体场景下还会想跑一次**。一次性改完就不会再碰的，直接删。

---

## img_gen/ —— 重新生成配图

App 里 58 张知识配图（`app/src/main/assets/img/*.png`）就是这里画出来的。

- `common.py`：公式排版库（LaTeX 解析、分式/根号绘制）+ 颜色、坐标变换 `coords()`、输出 `save()`。
- `<图片名>.py`：一张图一个脚本，比如 `m_quad.py` 画「数学_3.1_二次函数」。
- `gen_all.py`：一键按 `ACTIVE` 列表重新生成全部在用配图。

用法（在仓库根）：

```bash
cd tools/legacy/img_gen
python m_quad.py        # 只重画这一张
python gen_all.py       # 全部重画
```

注意：

- 依赖 `Pillow`。字体写死 Windows 路径（`C:/Windows/Fonts/msyh.ttc`），换系统要改 `common.py` 顶部的 `FONT_R/FONT_B`。
- 输出目录由 `common.py` 自动向上找仓库根推出（`<root>/app/src/main/assets/img`），目录移动过也不会写错。
- 图是**生成物**，不要手改 png；改图就改脚本再跑。改完记得 `git status` 看一眼是否只有预期的那几张变了。

## make_icon.py —— 重新生成应用图标

输出 5 个 dpi 的 `mipmap-*/ic_launcher.png`（传统方图标）和 `ic_launcher_foreground.png`（自适应图标前景）。

```bash
python tools/legacy/make_icon.py
```

- 源图在 `emoji_src/open_book.png`（Microsoft Fluent Emoji 的书本，MIT License）。
- 只有想换图标时才需要跑；跑完会直接覆盖 `app/src/main/res/mipmap-*` 下的文件。

## hl_kit.py —— 批量标重点辅助

正文里 `**片段**` 就是「加粗 + 琥珀金」的重点标记。这个工具用来批量找「还没标重点」的条目、
把标注结果回写进 `tools/subjects/*.py`。

```bash
python tools/legacy/hl_kit.py export <科目key> [批大小]     # 导出缺标条目，人工标注
python tools/legacy/hl_kit.py apply  <科目key> [--write]    # 把标注结果应用回去（不带 --write 只预览）
```

- 路径按「在仓库根运行」写死（`tools/subjects/<key>.py`），别在别的目录跑。
- 它只做文本搬运，不判断内容好坏；标哪一句仍然要人读一遍再定（见《工作技巧》第 79 条）。

---

## 已经删掉、不用再找的

v2.9.3 清理时删掉的（都在原来的 `build/` 里，且 build 是被 gitignore 的产物目录，清一次就全丢）：

- `build/patch_*.py` 二十多个：历史上成批改源码的一次性补丁，早已应用完，再跑一次会破坏代码。
  改动过程在 git 历史里能查到，不需要留脚本。
- `build/replace_asset.py`：往 GitHub Release 上传替换资产的旧脚本，功能已被 `scripts/release.py` 覆盖。
- `build/_old/`、`build/objchk*/`、`build/*.log`、`build/*.out`、`build/*.apk`：构建中间产物与调试输出，共 139 MB，全部删除。
- `build/_data_backup/`、`build/_subj_backup/`：旧三层格式时代的数据快照，格式已废弃。
- `build/_research/`：当时的临时资料（拉取的 issue、诗词原文、笔记备份）。笔记旧备份已转到 `local/backup/`。
