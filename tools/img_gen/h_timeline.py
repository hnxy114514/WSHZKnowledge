# -*- coding: utf-8 -*-
"""中国古代朝代时间轴 配图生成脚本。
单独运行：python h_timeline.py  （只生成这一张，写入 app/src/main/assets/img/h_timeline.png）
共享绘制能力见 common.py。
"""
import os
import sys
import math

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from common import C, W, H, RED, BLUE, GREEN, GREY, dark, save, coords


def build():
    c = C("中国古代朝代时间轴", "统一与分裂交替，制度不断演进")
    y = 250
    c.line(60, y, 580, y, 4, (90, 90, 90))
    items = [("秦", "前221"), ("汉", "前202"), ("唐", "618"), ("宋", "960"), ("元", "1271"), ("明", "1368"), ("清", "1644")]
    for i, (name, year) in enumerate(items):
        x = 88 + i * 82
        c.circle(x, y, 9, (226, 236, 250), BLUE, 3)
        c.t(x, y - 22, name, 19, True, BLUE, "mb")
        c.t(x, y + 20, year, 14, False, (90, 90, 90), "mt")
    c.t(60, y + 74, "专制主义中央集权不断加强：三省六部 → 二府三司 → 内阁 → 军机处", 15, False, dark, "la")
    c.rich(W // 2, H - 40, "记忆主线：制度沿革 + 经济重心南移 + 思想控制", 15)
    return c




if __name__ == "__main__":
    save(build(), "h_timeline")
