# -*- coding: utf-8 -*-
"""两次世界大战时间轴 配图生成脚本。
单独运行：python h_war.py  （只生成这一张，写入 app/src/main/assets/img/h_war.png）
共享绘制能力见 common.py。
"""
import os
import sys
import math

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from common import C, W, H, RED, BLUE, GREEN, GREY, dark, save, coords


def build():
    c = C("两次世界大战时间轴", "1914—1918 与 1939—1945")
    y1, y2 = 200, 330
    for y, lab, col in ((y1, "第一次世界大战 1914—1918", RED), (y2, "第二次世界大战 1939—1945", BLUE)):
        c.line(70, y, 570, y, 4, (120, 120, 120))
        c.t(70, y - 22, lab, 17, True, col, "lb")
    ev1 = [("萨拉热窝", "1914"), ("凡尔登", "1916"), ("凡尔赛", "1919")]
    ev2 = [("闪击波兰", "1939"), ("斯大林格勒", "1942"), ("诺曼底", "1944"), ("联合国", "1945")]
    for i, (n, yr) in enumerate(ev1):
        x = 130 + i * 158
        c.circle(x, y1, 8, (248, 230, 230), RED, 2)
        c.t(x, y1 + 18, n + " " + yr, 14, False, dark, "mt")
    for i, (n, yr) in enumerate(ev2):
        x = 116 + i * 128
        c.circle(x, y2, 8, (226, 236, 250), BLUE, 2)
        c.t(x, y2 + 18, n + " " + yr, 14, False, dark, "mt")
    c.rich(W // 2, H - 40, "战后建立凡尔赛—华盛顿体系、雅尔塔体系", 15)
    return c




if __name__ == "__main__":
    save(build(), "历史_8.1_格局")
