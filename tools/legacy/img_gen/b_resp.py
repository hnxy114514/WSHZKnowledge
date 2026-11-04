# -*- coding: utf-8 -*-
"""有氧呼吸三阶段 配图生成脚本。
单独运行：python b_resp.py  （只生成这一张，写入 app/src/main/assets/img/b_resp.png）
共享绘制能力见 common.py。
"""
import os
import sys
import math

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from common import C, W, H, RED, BLUE, GREEN, GREY, dark, save, coords


def build():
    c = C("有氧呼吸三阶段", "第一阶段在细胞质基质，后两阶段在线粒体")
    ys = 186
    boxes = [(96, "葡萄糖"), (256, "丙酮酸 + [H]"), (416, "CO_{2} + [H]")]
    for x, lab in boxes:
        c.rect(x, ys - 28, x + 124, ys + 28, "white", (60, 60, 60), 2, 6)
        c.rich(x + 62, ys, lab, 14, True, dark, "ma")
    c.arrow(220, ys, 252, ys, 2, GREY, 10)
    c.arrow(380, ys, 412, ys, 2, GREY, 10)
    c.t(158, ys - 58, "① 细胞质基质", 14, True, GREEN, "ma")
    c.t(318, ys - 58, "② 线粒体基质", 14, True, BLUE, "ma")
    c.t(158, ys + 48, "释放 2 ATP", 14, True, RED, "ma")
    c.t(318, ys + 48, "释放 2 ATP", 14, True, RED, "ma")
    c.rect(180, 286, 470, 344, "white", (60, 60, 60), 2, 6)
    c.rich(325, 302, "[H] + O_{2} → H_{2}O", 15, True, dark, "ma")
    c.rich(325, 326, "③ 线粒体内膜  释放 34 ATP", 14, True, RED, "ma")
    c.rich(W // 2, H - 22, "1 mol 葡萄糖彻底氧化约释放 2870 kJ，其中约 1161 kJ 储存在 ATP", 13)
    return c




if __name__ == "__main__":
    save(build(), "生物_3.3_有氧呼吸")
