# -*- coding: utf-8 -*-
"""楞次定律 配图生成脚本。
单独运行：python p_em.py  （只生成这一张，写入 app/src/main/assets/img/p_em.png）
共享绘制能力见 common.py。
"""
import os
import sys
import math

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from common import C, W, H, RED, BLUE, GREEN, GREY, dark, save, coords


def build():
    c = C("楞次定律", "感应电流的磁场总要阻碍引起感应电流的磁通量变化")
    cx = 250
    # 磁铁（上方）：上 S 下 N，N 极朝下插入
    c.rect(cx - 45, 92, cx + 45, 160, (240, 240, 245), (40, 40, 40), 2)
    c.t(cx, 112, "S", 20, True, BLUE, "mm")
    c.t(cx, 144, "N", 20, True, RED, "mm")
    c.arrow(cx, 168, cx, 206, 3, (150, 60, 60), 12)
    c.t(cx + 54, 180, "向下插入", 15, True, (150, 60, 60), "la")
    # 线圈
    c.rect(cx - 85, 220, cx + 85, 296, "white", (40, 40, 40), 3)
    c.line(cx - 70, 232, cx - 70, 284, 2, (40, 40, 40))
    c.line(cx + 70, 232, cx + 70, 284, 2, (40, 40, 40))
    # 感应电流：左线上、右线下（俯视逆时针）
    c.arrow(cx - 55, 286, cx - 55, 240, 3, GREEN, 12)
    c.arrow(cx + 55, 240, cx + 55, 286, 3, GREEN, 12)
    c.t(cx, 312, "感应电流 i_{感}", 16, True, GREEN, "mm")
    # 右侧：感应磁场向上，与磁铁 N 极相斥
    c.arrow(cx + 170, 300, cx + 170, 210, 3, (200, 120, 40), 12)
    c.t(cx + 184, 212, "B_{感} 向上", 17, True, (150, 80, 20), "la")
    c.t(cx + 184, 244, "与磁铁 N 极相斥", 14, False, (120, 75, 30), "la")
    c.rich(W // 2, H - 42, "磁通量增加 → 感应磁场方向与之相反，阻碍磁通量增大", 16)
    return c




if __name__ == "__main__":
    save(build(), "p_em")
