# -*- coding: utf-8 -*-
"""等高线地形图 配图生成脚本。
单独运行：python e_contour.py  （只生成这一张，写入 app/src/main/assets/img/e_contour.png）
共享绘制能力见 common.py。
"""
import os
import sys
import math

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from common import C, W, H, RED, BLUE, GREEN, GREY, dark, save, coords


def build():
    c = C("等高线地形图", "同线等高、等高距相同；疏密看坡度，弯曲看山脊山谷")
    ox, oy = 296, 250
    dark = (120, 100, 70)
    steps = 240

    def pt(theta, base):
        d_up = abs((theta + math.pi / 2 + math.pi) % (2 * math.pi) - math.pi)
        d_dn = abs((theta - math.pi / 2 + math.pi) % (2 * math.pi) - math.pi)
        k = 1 + 0.20 * math.exp(-(d_up ** 2) / 0.16) - 0.34 * math.exp(-(d_dn ** 2) / 0.10)
        return (ox + base * 1.48 * math.cos(theta) * k,
                oy + base * 0.76 * math.sin(theta) * k)

    # 等高线：内圈密、外圈疏，明显体现「疏密→坡度陡缓」
    for base in (172, 136, 104, 78, 58, 44):
        pts = [pt(i * 2 * math.pi / steps, base) for i in range(steps + 1)]
        c.d.line(pts, fill=dark, width=2, joint="curve")

    # 河流：沿山谷向下流出
    c.d.line([(ox - 2, oy + 26), (ox + 6, oy + 80), (ox - 6, oy + 128), (ox + 2, oy + 168)],
             fill=(60, 120, 200), width=3, joint="curve")
    c.t(ox + 14, oy + 152, "河流", 12, False, (60, 120, 200), "lm")

    # 山顶
    c.rect(ox - 40, oy - 34, ox + 40, oy - 8, fill="white", outline="white", w=0)
    c.t(ox, oy - 30, "▲ 山顶", 15, True, dark, "ma")

    # 山脊（上方外凸）/ 山谷（下方内凹）
    c.t(ox, 82, "山脊：等高线凸向低处", 14, True, (170, 70, 60), "ma")
    c.t(556, 376, "山谷：等高线凸向高处", 14, True, (60, 120, 200), "rm")

    c.t(556, 132, "等高距相同", 13, False, GREY, "rm")
    # 疏密对比：内圈密、外圈疏，直接对应坡度陡缓
    c.t(ox + 126, oy - 44, "密集 → 坡陡", 13, True, RED, "lm")
    c.arrow(ox + 114, oy - 38, ox + 82, oy - 10, 2, RED, 9)
    c.t(40, oy + 10, "稀疏 → 坡缓", 13, True, GREEN, "lm")
    c.arrow(132, oy + 6, 94, oy - 10, 2, GREEN, 9)
    c.rich(W // 2, H - 30, "等高线越密坡度越陡，越疏坡度越缓；重合处为陡崖，内大外小为山峰", 14)
    return c




if __name__ == "__main__":
    save(build(), "e_contour")
