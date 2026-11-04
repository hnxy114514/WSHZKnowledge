# -*- coding: utf-8 -*-
"""能量流动与营养级 配图生成脚本。
单独运行：python b_ecosys.py  （只生成这一张，写入 app/src/main/assets/img/b_ecosys.png）
共享绘制能力见 common.py。
"""
import os
import sys
import math

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from common import C, W, H, RED, BLUE, GREEN, GREY, dark, save, coords


def build():
    c = C("能量流动与营养级", "能量逐级递减，传递效率约 10%～20%")
    levels = [(120, 330, 400), (160, 268, 320), (200, 210, 240), (240, 156, 160)]
    cs = [(38, 132, 88), (110, 170, 90), (200, 160, 60), (198, 58, 58)]
    for i, (x1, y1, w) in enumerate(levels):
        c.rect(x1, y1, x1 + w, y1 + 52, cs[i], None, 1, 4)
        c.t(x1 + w / 2, y1 + 26, ["生产者", "初级消费者", "次级消费者", "三级消费者"][i], 16, True, "white", "mm")
    c.arrow(96, 380, 96, 150, 2, (90, 90, 90), 10)
    c.t(84, 388, "能量递减", 15, False, (90, 90, 90), "ra")
    c.t(W // 2, 128, "能量传递效率 10%~20%", 17, True, RED, "ma")
    c.rich(W // 2, H - 40, "第一营养级能量最多；能量不能循环流动", 15)
    return c




if __name__ == "__main__":
    save(build(), "生物_8.2_能量流动")
