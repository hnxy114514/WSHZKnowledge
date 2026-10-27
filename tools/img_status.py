# -*- coding: utf-8 -*-
"""配图状态登记表（人工维护，决定图册分栏）：
- READY：    已自检通过，可进入软件（不在下面集合里的图片即默认已核验）
- CHECKING： 正在生成 / 尚待自检
- MANUAL：   需要人工核对（AI 无法自行确定正确性时才放入）

修改后运行 `python tools/make_gallery.py` 重新生成图册三栏。
"""
CHECKING = {
}
MANUAL = {
}
