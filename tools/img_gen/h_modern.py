# -*- coding: utf-8 -*-
"""中国近代史时间轴 配图生成脚本。
单独运行：python h_modern.py  （只生成这一张，写入 app/src/main/assets/img/h_modern.png）
共享绘制能力见 common.py。
"""
import os
import sys
import math

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from common import C, W, H, RED, BLUE, GREEN, GREY, dark, save, coords


def build():
    c = C("中国近代史时间轴", "1840—1949：屈辱与探索并行")
    y = 250
    c.line(60, y, 580, y, 4, (90, 90, 90))
    items = [("鸦片战争", "1840"), ("甲午战争", "1894"), ("辛亥", "1911"), ("五四", "1919"), ("抗战", "1937"), ("新中国", "1949")]
    for i, (name, year) in enumerate(items):
        x = 96 + i * 96
        c.circle(x, y, 9, (248, 230, 230), RED, 3)
        c.t(x, y - 22, name, 16, True, RED, "mb")
        c.t(x, y + 20, year, 14, False, (90, 90, 90), "mt")
    c.t(60, y + 74, "由器物（洋务）→ 制度（戊戌、辛亥）→ 思想（新文化）逐层深入", 15, False, dark, "la")
    c.rich(W // 2, H - 40, "两大任务：民族独立、人民解放；国家富强、人民幸福", 15)
    return c




if __name__ == "__main__":
    save(build(), "h_modern")
