# -*- coding: utf-8 -*-
"""斜面上的受力分析 配图生成脚本。
单独运行：python p_slope.py  （只生成这一张，写入 app/src/main/assets/img/p_slope.png）
共享绘制能力见 common.py。
"""
import os
import sys
import math

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from common import C, W, H, RED, BLUE, GREEN, GREY, dark, save, coords


def build():
    """斜面受力分析：物块贴在 30° 斜面上，重力正交分解 + 支持力 + 摩擦力。
    几何严格按倾角计算：物块底面沿斜边，法向垂直斜边。"""
    c = C("斜面上的受力分析", "重力按「沿斜面 / 垂直斜面」两个方向正交分解")
    th = math.radians(30)
    A = (130, 366)                      # 斜面底端（左下）
    L = 390                             # 水平投影长度
    Cc = (A[0] + L, A[1] - L * math.tan(th))   # 斜面顶端（右上）
    B = (Cc[0], A[1])                   # 直角顶点（右下）
    c.poly([A, B, Cc], (238, 243, 250), (45, 45, 45), 3)

    # 斜边方向单位向量：d 沿斜面向下，n 沿外法线（离开斜面）
    ex, ey = Cc[0] - A[0], Cc[1] - A[1]
    el = math.hypot(ex, ey)
    ux_, uy_ = ex / el, ey / el                 # A->C 向上
    dx, dy = -uy_ * -1, 0                        # 占位，下面重算
    dx, dy = -ux_, -uy_                         # 沿斜面向下（C->A 方向）
    nx, ny = -dy, dx                            # 外法向（指向左上方）
    if ny > 0:                                  # 保证法向朝上（屏幕 y 减小）
        nx, ny = dy, -dx

    P = (A[0] + 0.52 * ex, A[1] + 0.52 * ey)    # 斜边上的接触点
    w, h = 86, 54
    cx = P[0] + nx * h / 2
    cy = P[1] + ny * h / 2

    def box(ox, oy):
        pts = []
        for sx, sy in ((-1, -1), (1, -1), (1, 1), (-1, 1)):
            pts.append((ox + sx * w / 2 * dx + sy * h / 2 * nx,
                        oy + sx * w / 2 * dy + sy * h / 2 * nx))
        return pts
    c.poly(box(cx, cy), (252, 236, 224), (140, 86, 40), 3)

    # 重力（竖直向下）
    c.arrow(cx, cy, cx, cy + 104, 3, RED, 13)
    c.t(cx - 14, cy + 100, "G = mg", 18, True, RED, "rm")
    # 支持力（垂直斜面向外）
    c.arrow(cx, cy, cx + nx * 92, cy + ny * 92, 3, GREEN, 12)
    c.t(cx + nx * 96, cy + ny * 96 - 6, "N", 18, True, GREEN, "mb")
    # 摩擦力（沿斜面向上）
    fx, fy = -dx, -dy
    c.arrow(cx, cy, cx + fx * 84, cy + fy * 84, 3, (150, 90, 30), 12)
    c.t(cx + fx * 92, cy + fy * 92 - 2, "f = μN", 17, True, (150, 90, 30), "lb")
    # 重力分解：mg sinθ 沿斜面向下
    c.arrow(cx, cy, cx + dx * 100, cy + dy * 100, 3, BLUE, 12)
    c.t(cx + dx * 104 - 8, cy + dy * 104 + 14, "mg sinθ", 17, True, BLUE, "ra")
    # 重力分解：mg cosθ 垂直斜面向内
    c.arrow(cx, cy, cx - nx * 84, cy - ny * 84, 3, (200, 120, 40), 12)
    c.t(cx - nx * 88 + 10, cy - ny * 88 + 20, "mg cosθ", 17, True, (200, 120, 40), "la")

    # 倾角 θ（A 点处底边与斜边之间，屏幕坐标顺时针为正）
    r = 74
    c.d.arc([A[0] - r, A[1] - r, A[0] + r, A[1] + r], 330, 360, fill=(40, 40, 40), width=3)
    c.t(A[0] + 96 * math.cos(math.radians(-15)), A[1] + 96 * math.sin(math.radians(-15)),
        "θ", 20, True, (40, 40, 40), "mm")
    c.t(B[0] - 10, B[1] - 12, "θ = 30°", 15, False, (110, 110, 110), "rb")
    c.rich(W // 2, H - 40, "沿斜面：mg sinθ − f = ma　　垂直斜面：N = mg cosθ　　滑动摩擦 f = μN", 16)
    return c




if __name__ == "__main__":
    save(build(), "物理_2.3_受力分析")
