# -*- coding: utf-8 -*-
"""生成各科配图（纯代码绘制，不产生任何费用）。"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from img_lib import C, W, H, OUT

RED = (198, 58, 58)
BLUE = (44, 96, 178)
GREEN = (38, 132, 88)
GREY = (120, 120, 120)
dark = (35, 35, 35)


def coords(c, xmin, xmax, ymin, ymax, box=(78, 118, 596, 392), labx="x", laby="y"):
    """建数学坐标→像素映射，并画轴。返回 (px, py)。"""
    x0, y0, x1, y1 = box
    X0 = x0 + (0 - xmin) / (xmax - xmin) * (x1 - x0)
    Y0 = y1 - (0 - ymin) / (ymax - ymin) * (y1 - y0)

    def px(v):
        return x0 + (v - xmin) / (xmax - xmin) * (x1 - x0)

    def py(v):
        return y1 - (v - ymin) / (ymax - ymin) * (y1 - y0)

    c.arrow(px(xmin), py(0), px(xmax), py(0), 2, (70, 70, 70), 11)
    c.arrow(px(0), py(ymin), px(0), py(ymax), 2, (70, 70, 70), 11)
    c.t(px(xmax) - 4, py(0) + 8, labx, 17, False, GREY, "rt")
    c.t(px(0) - 12, py(ymax) + 6, laby, 17, False, GREY, "rb")
    c.t(px(0) - 10, py(0) + 8, "O", 18, False, GREY, "rt")
    return px, py


def save(c, name, acc):
    p, size = c.save(name)
    acc.append((name, size))
    print("  %-14s %6.1f KB" % (name, size / 1024.0))


acc = []

# ===================== 数学 =====================
def m_quad():
    c = C("二次函数图象", "开口方向、对称轴与顶点决定一切")
    px, py = coords(c, -3.4, 3.6, -3.2, 4.6)
    f = lambda v: 0.55 * (v - 1) ** 2 - 2
    c.dash(px(1), py(-3.2), px(1), py(4.6), 2, (150, 150, 150))
    c.fn_curve(f, -3.2, 3.2, px, py, color=RED)
    c.circle(px(1), py(-2), 6, RED, RED, 2)
    c.t(px(1) + 14, py(-2) + 22, "顶点(1,−2)", 19, True, RED, "la")
    c.t(px(1) + 10, py(4.2), "x=1 对称轴", 18, False, GREY, "la")
    c.t(px(-3.2), py(3.6), "a>0 开口向上", 19, True, BLUE, "la")
    c.rich(W // 2, H - 42, "y = ax^{2} + bx + c ，对称轴 x = \\frac{-b}{2a}，顶点 (\\frac{-b}{2a} , \\frac{4ac−b^{2}}{4a})", 18)
    save(c, "m_quad", acc)


def m_trig():
    c = C("正弦函数图象", "周期 2π、振幅 1、五点作图")
    px, py = coords(c, -7.2, 7.4, -1.9, 1.9, (78, 130, 596, 360))
    c.fn_curve(math.sin, -7.0, 7.0, px, py, 400, BLUE)
    for k in (-1, 0, 1):
        c.dash(px(k * 2 * math.pi), py(-1.7), px(k * 2 * math.pi), py(1.7), 1, (185, 185, 185))
        c.t(px(k * 2 * math.pi), py(-1.7) + 16, ("−2π" if k < 0 else ("2π" if k > 0 else "0")), 17, False, GREY, "mt")
    c.dash(px(0), py(1), px(7.0), py(1), 1, (210, 210, 210))
    c.t(px(1.57) - 4, py(1) - 6, "最大值 1", 18, False, RED, "mb")
    c.t(px(4.71), py(-1) + 22, "最小值 −1", 18, False, RED, "mt")
    c.rich(W // 2, H - 42, "y = sin x ：周期 T = 2π，值域 [−1, 1]，x = π/2 + 2kπ 取最大值", 18)
    save(c, "m_trig", acc)


def m_exp():
    c = C("指数函数与对数函数", "互为反函数，图象关于 y = x 对称")
    px, py = coords(c, -3.2, 3.6, -3.2, 4.4)
    c.fn_curve(lambda v: 2 ** v, -3.2, 2.1, px, py, color=RED)
    c.fn_curve(lambda v: 0.5 ** v, -2.0, 3.4, px, py, color=BLUE)
    c.dash(px(-3.2), py(-3.2), px(3.6), py(3.6), 2, (160, 160, 160))
    c.circle(px(1), py(1), 5, GREEN, GREEN, 2)
    c.t(px(1) + 10, py(1) - 6, "(0,1)", 17, False, GREEN, "lb")
    c.t(px(2.0) + 16, py(2 ** 2.0) - 44, "y = 2^{x}", 18, True, RED, "ma")
    c.t(px(2.6), py(0.5 ** 2.6) + 34, "y = (\\frac{1}{2})^{x}", 18, True, BLUE, "ma")
    c.t(px(3.2), py(2.2), "y = x", 17, False, GREY, "lb")
    c.rich(W // 2, H - 42, "a>1 时 2^{x} 递增；(\\frac{1}{2})^{x} 递减；都过 (0,1)，值域恒正", 18)
    save(c, "m_exp", acc)


def m_log():
    c = C("对数函数图象", "定义域 x>0，过 (1,0)，a>1 递增")
    px, py = coords(c, -1.0, 5.4, -2.6, 3.2)
    c.fn_curve(lambda v: math.log(v, 2) if v > 0.02 else None, 0.02, 5.2, px, py, color=RED)
    c.fn_curve(lambda v: math.log(v, 0.5) if v > 0.02 else None, 0.02, 5.2, px, py, color=BLUE)
    c.dash(px(0.02), py(-2.6), px(0.02), py(3.2), 2, (170, 170, 170))
    c.circle(px(1), py(0), 5, GREEN, GREEN, 2)
    c.t(px(1) + 8, py(0) - 10, "(1,0)", 17, False, GREEN, "lb")
    c.t(px(3.4), py(0.65), "y = log_{2}x", 18, True, RED, "la")
    c.t(px(0.55), py(2.6), "y = log_{0.5}x", 18, True, BLUE, "lt")
    c.rich(W // 2, H - 42, "真数必须大于 0：log_{a}N 中 N>0；a>1 增，0<a<1 减", 18)
    save(c, "m_log", acc)


def m_deriv():
    c = C("导数的几何意义", "f′(x_{0}) 就是该点切线的斜率 k = tanα")
    px, py = coords(c, -1.0, 4.2, -1.2, 4.6)
    f = lambda v: 0.42 * v ** 3 - 1.2 * v + 0.6
    d = lambda v: 1.26 * v ** 2 - 1.2
    c.fn_curve(f, -0.6, 3.9, px, py, color=BLUE)
    x0 = 2.0
    k = d(x0)
    y0 = f(x0)
    c.line(px(0.8), py(y0 + k * (0.8 - x0)), px(3.2), py(y0 + k * (3.2 - x0)), 3, RED)
    c.circle(px(x0), py(y0), 6, RED, RED, 2)
    c.t(px(x0) + 10, py(y0) - 12, "切点 A(x_{0}, f(x_{0}))", 17, True, RED, "lb")
    c.t(px(1.0), py(3.9), "k = f′(x_{0}) = tanα", 19, True, (30, 30, 30), "la")
    c.rich(W // 2, H - 42, "切线方程：y − f(x_{0}) = f′(x_{0})(x − x_{0})", 18)
    save(c, "m_deriv", acc)


def m_unit():
    c = C("单位圆与三角函数线", "终边与单位圆交点的坐标就是 (cosα, sinα)")
    ox, oy, R = 250, 258, 128
    c.circle(ox, oy, R, None, (70, 70, 70), 2)
    c.arrow(ox - R - 30, oy, ox + R + 40, oy, 2, (70, 70, 70), 11)
    c.arrow(ox, oy + R + 30, ox, oy - R - 30, 2, (70, 70, 70), 11)
    a = math.radians(38)
    ex, ey = ox + R * math.cos(a), oy - R * math.sin(a)
    c.line(ox, oy, ex, ey, 3, RED)
    c.circle(ex, ey, 6, RED, RED, 2)
    c.t(ex + 10, ey - 10, "P(cosα, sinα)", 17, True, RED, "lb")
    c.line(ex, ey, ex, oy, 2, BLUE)
    c.line(ex, ey, ox, ey, 2, GREEN)
    c.t(ex + 12, (ey + oy) / 2, "sinα", 17, True, BLUE, "lm")
    c.t((ex + ox) / 2, ey - 14, "cosα", 17, True, GREEN, "mb")
    c.t(ox + 62, oy - 26, "α", 19, True, (30, 30, 30), "lm")
    c.t(ox + 6, oy + R + 20, "1", 16, False, GREY, "la")
    c.rich(W // 2, H - 42, "sin^{2}α + cos^{2}α = 1 ，tanα = sinα / cosα", 19)
    save(c, "m_unit", acc)


def m_ellipse():
    c = C("椭圆", "到两焦点距离之和等于 2a（常数）")
    ox, oy, A, B = 318, 250, 210, 120
    c.d.ellipse([ox - A, oy - B, ox + A, oy + B], outline=BLUE, width=4)
    c.arrow(ox - A - 30, oy, ox + A + 34, oy, 2, (80, 80, 80), 11)
    c.arrow(ox, oy - B - 26, ox, oy + B + 26, 2, (80, 80, 80), 11)
    cc = math.sqrt(A * A - B * B)
    for sx in (-1, 1):
        c.circle(ox + sx * cc, oy, 6, RED, RED, 2)
        c.t(ox + sx * cc, oy + 20, "F" + ("1" if sx < 0 else "2"), 17, True, RED, "mt")
    c.line(ox - cc, oy, ox - A, oy - 42, 2, GREEN)
    c.line(ox + cc, oy, ox - A, oy - 42, 2, GREEN)
    c.circle(ox - A, oy - 42, 5, GREEN, GREEN, 2)
    c.t(ox - 20, oy - B - 12, "b", 17, False, GREY, "rm")
    c.t(ox + A + 4, oy + 20, "a", 17, False, GREY, "mt")
    c.t(ox + 60, oy - 108, "|PF_{1}| + |PF_{2}| = 2a", 18, True, GREEN, "la")
    c.rich(W // 2, H - 42, "a^{2} = b^{2} + c^{2} ，离心率 e = \\frac{c}{a} ∈ (0,1)", 18)
    save(c, "m_ellipse", acc)


def m_hyper():
    c = C("双曲线", "到两焦点距离之差的绝对值为 2a")
    px, py = coords(c, -4.4, 4.4, -3.2, 3.2)
    a, b = 1.5, 1.15
    c.fn_curve(lambda v: b * math.sqrt(v * v / (a * a) - 1) if abs(v) >= a else None, -4.2, 4.2, px, py, color=BLUE)
    c.fn_curve(lambda v: -b * math.sqrt(v * v / (a * a) - 1) if abs(v) >= a else None, -4.2, 4.2, px, py, color=BLUE)
    c.dash(px(-4.2), py(-b * 4.2 / a), px(4.2), py(b * 4.2 / a), 1, (170, 170, 170))
    c.dash(px(-4.2), py(b * 4.2 / a), px(4.2), py(-b * 4.2 / a), 1, (170, 170, 170))
    cc = math.sqrt(a * a + b * b)
    for sx in (-1, 1):
        c.circle(px(sx * cc), py(0), 6, RED, RED, 2)
    c.t(px(2.9), py(-2.9), "渐近线 y = ±\\frac{b}{a}x", 17, False, GREY, "ma")
    c.t(px(-1.1), py(2.7), "两支关于 y 轴对称", 17, False, BLUE, "ma")
    c.rich(W // 2, H - 42, "\\frac{x^{2}}{a^{2}} − \\frac{y^{2}}{b^{2}} = 1 ，c^{2} = a^{2} + b^{2} ，e = \\frac{c}{a} > 1", 18)
    save(c, "m_hyper", acc)


def m_parabola():
    c = C("抛物线", "到焦点与准线距离相等")
    px, py = coords(c, -2.6, 4.4, -3.0, 3.0)
    p = 1.2
    c.fn_curve(lambda v: math.sqrt(2 * p * v) if v >= 0 else None, 0, 4.2, px, py, color=BLUE)
    c.fn_curve(lambda v: -math.sqrt(2 * p * v) if v >= 0 else None, 0, 4.2, px, py, color=BLUE)
    c.dash(px(-p / 2), py(-3.0), px(-p / 2), py(3.0), 2, (170, 170, 170))
    c.circle(px(p / 2), py(0), 6, RED, RED, 2)
    c.t(px(p / 2) + 8, py(0) - 10, "F(\\frac{p}{2}, 0)", 17, True, RED, "lb")
    c.t(px(-p / 2) - 8, py(2.6), "准线 x = -\\frac{p}{2}", 16, False, GREY, "rt")
    c.rich(W // 2, H - 42, "y^{2} = 2px ：焦点 (\\frac{p}{2}, 0)，准线 x = -\\frac{p}{2}", 18)
    save(c, "m_parabola", acc)


def m_solid():
    c = C("棱柱与棱锥", "体积 V = Sh，棱锥为 \\frac{1}{3}Sh")
    # 棱柱
    ox, oy = 170, 250
    d = 34
    top = [(ox, oy - 100), (ox + 120, oy - 100), (ox + 120, oy + 20), (ox, oy + 20)]
    c.dash(ox, oy - 100, ox - d, oy - 100 - d, 2, (150, 150, 150))
    c.dash(ox, oy + 20, ox - d, oy + 20 - d, 2, (150, 150, 150))
    c.dash(ox - d, oy - 100 - d, ox - d, oy + 20 - d, 2, (150, 150, 150))
    c.poly([(x - d, y - d) for x, y in top], None, BLUE, 3)
    c.poly(top, None, BLUE, 3)
    for x, y in top:
        c.line(x, y, x - d, y - d, 3, BLUE)
    c.t(ox + 40, oy + 46, "棱柱 V = Sh", 18, True, BLUE, "ma")
    # 棱锥
    bx, by = 430, 270
    base = [(bx, by), (bx + 120, by), (bx + 146, by - 40), (bx + 26, by - 40)]
    ap = (bx + 70, by - 118)
    c.poly(base, None, GREEN, 3)
    for p in base:
        c.line(ap[0], ap[1], p[0], p[1], 3, GREEN)
    c.t(bx + 60, by + 40, "棱锥 V = \\frac{1}{3}Sh", 18, True, GREEN, "ma")
    c.dash(ap[0], ap[1], ap[0], by - 20, 1, (150, 150, 150))
    c.rich(W // 2, H - 40, "柱体、锥体、台体体积：Sh 、\\frac{1}{3}Sh 、\\frac{(S+S′+\\sqrt{SS′})h}{3}", 17)
    save(c, "m_solid", acc)


def m_normal():
    c = C("正态分布密度曲线", "关于 x=μ 对称，σ 越小越瘦高")
    px, py = coords(c, -4.2, 4.2, 0, 0.95, (78, 130, 596, 356))
    for s, col, lab, tx, ty in ((1.0, RED, "σ=1", 2.5, 0.40), (0.5, BLUE, "σ=0.5", 1.6, 0.60)):
        k = 1 / (s * math.sqrt(2 * math.pi))
        c.fn_curve(lambda v, s=s, k=k: k * math.exp(-v * v / (2 * s * s)), -4.0, 4.0, px, py, 320, col, 3)
        c.t(px(tx), py(ty), lab, 17, True, col, "ma")
    c.dash(px(0), py(0), px(0), py(0.9), 2, (160, 160, 160))
    c.t(px(0) - 8, py(0.82), "x=μ", 17, False, GREY, "rm")
    c.rich(W // 2, H - 42, "P(μ−σ, μ+σ) ≈ 68.3% ，P(μ−2σ, μ+2σ) ≈ 95.4%", 17)
    save(c, "m_normal", acc)


# ===================== 物理 =====================
def p_vt():
    c = C("匀变速直线运动 v-t 图象", "斜率是加速度，图象面积是位移")
    px, py = coords(c, -0.4, 6.4, -0.6, 7.2, (78, 120, 596, 372), "t", "v")
    c.fn_curve(lambda v: 1.2 + 0.9 * v, 0, 6.0, px, py, color=RED)
    c.poly([(px(0), py(0)), (px(4.0), py(0)), (px(4.0), py(1.2 + 3.6))], (222, 232, 244), (44, 96, 178), 2)
    c.dash(px(4.0), py(0), px(4.0), py(4.8), 1, (150, 150, 150))
    c.t(px(2.3), py(0.9), "面积 = 位移", 17, True, BLUE, "ma")
    c.t(px(4.9), py(4.4), "斜率 = 加速度 a", 17, True, RED, "lm")
    c.t(px(1.6), py(6.6), "v = v_{0} + at", 18, True, dark, "ma")
    c.rich(W // 2, H - 42, "v = v_{0} + at ，x = v_{0}t + \\frac{at^{2}}{2} ，v^{2} − v_{0}^{2} = 2ax", 17)
    save(c, "p_vt", acc)


def p_slope():
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
    save(c, "p_slope", acc)


def p_projectile():
    c = C("平抛运动", "水平匀速 + 竖直自由落体，轨迹是抛物线")
    ox, oy = 110, 150
    c.arrow(ox - 24, oy, ox + 470, oy, 2, (80, 80, 80), 11)
    c.arrow(ox, oy - 26, ox, oy + 236, 2, (80, 80, 80), 11)
    c.t(ox + 466, oy - 8, "x", 17, False, GREY, "mb")
    c.t(ox - 8, oy + 232, "y", 17, False, GREY, "rt")
    gx, gy = 1.6, 0.42
    c.fn_curve(lambda v: -gy * v * v, 0, 15.6, lambda v: ox + v * 28, lambda y: oy - y * 60, 200, RED, 4)
    vx, vy = ox + 14 * 28, oy - (-gy * 14 * 14) * 60
    c.circle(vx, vy, 6, RED, RED, 2)
    c.arrow(vx, vy, vx + 62, vy, 3, BLUE, 12)
    c.t(vx + 66, vy, "v_{x} = v_{0}", 17, True, BLUE, "lm")
    c.arrow(vx, vy, vx, vy + 52, 3, GREEN, 12)
    c.t(vx + 8, vy + 46, "v_{y} = gt", 17, True, GREEN, "la")
    c.arrow(vx, vy, vx + 58, vy + 44, 3, (120, 60, 160), 12)
    c.t(vx + 26, vy + 70, "v", 18, True, (120, 60, 160), "la")
    c.t(ox + 6, oy - 14, "O", 16, False, GREY, "lb")
    c.rich(W // 2, H - 42, "x = v_{0}t ，y = \\frac{gt^{2}}{2} ，落地时间只由高度决定", 17)
    save(c, "p_projectile", acc)


def p_circular():
    c = C("圆周运动与向心力", "向心力指向圆心，由合外力提供")
    ox, oy, R = 320, 250, 128
    c.dash(ox - R - 20, oy, ox + R + 20, oy, 1, (170, 170, 170))
    c.circle(ox, oy, R, None, (70, 70, 70), 2)
    a = math.radians(-40)
    mx, my = ox + R * math.cos(a), oy + R * math.sin(a)
    c.circle(mx, my, 13, (250, 236, 226), (150, 90, 40), 3)
    c.arrow(mx, my, ox + 8, oy, 4, RED, 13)
    c.t(ox - 96, oy + 96, "F = \\frac{mv^{2}}{r}", 18, True, RED, "ma")
    c.arrow(ox - 60, oy + 78, ox - 10, oy + 26, 2, RED, 10)
    c.arrow(mx, my, mx + 58 * math.cos(a + 1.57), my + 58 * math.sin(a + 1.57), 3, BLUE, 12)
    c.t(mx + 34, my - 42, "v", 18, True, BLUE, "la")
    c.circle(ox, oy, 5, dark, dark, 1)
    c.t(ox + 14, oy + 12, "O", 17, False, GREY, "la")
    c.rich(W // 2, H - 42, "F = \\frac{mv^{2}}{r} = mω^{2}r ，ω = \\frac{2π}{T} ，v = \\frac{2πr}{T}", 17)
    save(c, "p_circular", acc)


def p_efield():
    c = C("匀强电场与电场线", "电场线越密场强越大，方向由正电荷指向负电荷")
    x1, y1, x2, y2 = 140, 160, 500, 340
    c.rect(x1, y1, x2, y1 + 18, (222, 232, 244), (44, 96, 178), 2, 3)
    c.rect(x1, y2 - 18, x2, y2, (248, 226, 226), (198, 58, 58), 2, 3)
    c.t(x2 + 12, y1 + 9, "+", 24, True, BLUE, "lm")
    c.t(x2 + 12, y2 - 9, "−", 24, True, RED, "lm")
    for i in range(6):
        x = x1 + 34 + i * 62
        c.arrow(x, y1 + 26, x, y2 - 26, 2, (110, 110, 110), 10)
    c.t((x1 + x2) / 2, y1 - 16, "极板间为匀强电场", 18, True, dark, "mb")
    c.rich(W // 2, H - 42, "E = \\frac{U}{d} ，F = qE ，电场线从正极板指向负极板", 17)
    save(c, "p_efield", acc)


def p_circuit():
    c = C("串联与并联电路", "串联分压、并联分流")
    # 串联
    def battery(x, y):
        c.line(x, y - 26, x, y + 26, 5, dark)
        c.line(x + 12, y - 15, x + 12, y + 15, 8, dark)
    c.line(120, 160, 120, 330, 3, dark)
    c.line(120, 330, 320, 330, 3, dark)
    c.line(320, 330, 320, 292, 3, dark)
    battery(320, 300)
    c.line(320, 268, 320, 160, 3, dark)
    c.line(320, 160, 120, 160, 3, dark)
    for i, x in enumerate((230, 285)):
        c.rect(x - 22, 148, x + 22, 174, "white", dark, 3, 3)
        c.t(x, 160, "R" + str(i + 1), 15, True, dark, "mm")
        c.line(120, 160, x - 22, 160, 3, dark)
        c.line(x + 22, 160, 320, 160, 3, dark)
    c.t(220, 352, "串联：I 相同，U = U_{1}+U_{2}", 17, True, BLUE, "ma")
    # 并联
    c.line(420, 160, 420, 330, 3, dark)
    c.line(420, 330, 560, 330, 3, dark)
    c.line(560, 330, 560, 300, 3, dark)
    battery(560, 305)
    c.line(560, 280, 560, 160, 3, dark)
    c.line(560, 160, 420, 160, 3, dark)
    for i, y in enumerate((206, 268)):
        c.line(420, y, 476, y, 3, dark)
        c.rect(476, y - 13, 508, y + 13, "white", dark, 3, 3)
        c.line(508, y, 560, y, 3, dark)
        c.t(492, y, "R" + str(i + 1), 15, True, dark, "mm")
    c.t(490, 352, "并联：U 相同，I = I_{1}+I_{2}", 17, True, GREEN, "ma")
    c.rich(W // 2, H - 40, "R_{串} = R_{1}+R_{2} ，\\frac{1}{R_{并}} = \\frac{1}{R_{1}} + \\frac{1}{R_{2}}", 17)
    save(c, "p_circuit", acc)


def p_lorentz():
    c = C("洛伦兹力与圆周运动", "v ⊥ B 时粒子做匀速圆周运动")
    x1, y1, x2, y2 = 90, 130, 560, 372
    c.rect(x1, y1, x2, y2, (244, 248, 252), (150, 160, 175), 2, 3)
    for i in range(6):
        for j in range(4):
            x, y = x1 + 40 + i * 85, y1 + 32 + j * 62
            c.t(x, y, "×", 20, True, (120, 130, 145), "mm")
    c.t(x1 + 14, y1 + 14, "B 垂直纸面向里", 16, True, GREY, "la")
    cx, cy, R = 350, 258, 84
    c.circle(cx, cy, R, None, (70, 70, 70), 2)
    mx, my = cx - R, cy
    c.circle(mx, my, 9, RED, RED, 1)
    c.arrow(mx, my, mx, my - 66, 3, BLUE, 12)
    c.t(mx - 4, my - 70, "v", 18, True, BLUE, "mb")
    c.arrow(mx, my, cx - 8, my, 3, GREEN, 12)
    c.t(cx - 46, my - 20, "F", 18, True, GREEN, "ma")
    c.rich(W // 2, H - 40, "r = mv/(qB) ，T = 2πm/(qB)：周期与速度无关", 17)
    save(c, "p_lorentz", acc)


def p_refract():
    c = C("光的折射与全反射", "空气→玻璃：折射角小于入射角；光密→光疏可全反射")
    yb = 270
    # ---- 左半：折射（空气在上、玻璃在下）----
    O = (280, yb)
    th1 = math.radians(43)
    th2 = math.asin(math.sin(th1) / 1.5)
    c.line(60, yb, 390, yb, 3, (60, 60, 60))
    c.dash(O[0], yb - 150, O[0], yb + 140, 2, (170, 170, 170))
    L = 165
    src = (O[0] - math.sin(th1) * L, O[1] - math.cos(th1) * L)       # 入射光源（左上）
    refr = (O[0] + math.sin(th2) * 140, O[1] + math.cos(th2) * 140)  # 折射（右下，更靠法线）
    refl = (O[0] + math.sin(th1) * L, O[1] - math.cos(th1) * L)      # 反射（右上，与入射对称）
    c.arrow(src[0], src[1], O[0], O[1], 3, RED, 12)
    c.arrow(O[0], O[1], refr[0], refr[1], 3, RED, 12)
    c.arrow(O[0], O[1], refl[0], refl[1], 2, (200, 160, 160), 10)
    c.t(src[0] - 14, src[1] + 22, "入射光", 16, True, RED, "rb")
    c.t(refr[0] + 4, refr[1] - 6, "折射光", 16, True, RED, "lb")
    c.t(refl[0] + 4, refl[1] - 6, "反射光", 15, True, (200, 150, 150), "lb")
    # 入射角弧（法线向上 270° 到入射光 227°）
    r1 = 46
    c.d.arc([O[0]-r1, O[1]-r1, O[0]+r1, O[1]+r1], 227, 270, fill=dark, width=3)
    c.rich(O[0]-44, O[1]-22, "θ_{1}", 16, False, dark, "ma")
    # 折射角弧（法线向下 90° 到折射光 63°）
    r2 = 38
    c.d.arc([O[0]-r2, O[1]-r2, O[0]+r2, O[1]+r2], 63, 90, fill=dark, width=3)
    c.rich(O[0]+24, O[1]+18, "θ_{2}", 16, False, dark, "ma")
    c.rich(O[0], yb - 150, "θ_{1} > θ_{2}（空气→玻璃）", 16, True, RED, "ma")
    c.rich(168, 104, "n = \\frac{sinθ_{1}}{sinθ_{2}}", 18, True, dark, "ma")
    # ---- 右半：全反射（玻璃在下、空气在上）----
    O2 = (500, yb)
    ic = math.radians(50)
    Lc = 110
    c.line(410, yb, 580, yb, 3, (60, 60, 60))
    c.dash(O2[0], yb - 150, O2[0], yb + 140, 2, (170, 170, 170))
    src2 = (O2[0] - math.sin(ic) * Lc, O2[1] + math.cos(ic) * Lc)   # 入射（左下，玻璃中）
    refl2 = (O2[0] + math.sin(ic) * Lc, O2[1] + math.cos(ic) * Lc)  # 反射（右下，玻璃中）
    c.arrow(src2[0], src2[1], O2[0], O2[1], 3, BLUE, 12)
    c.arrow(O2[0], O2[1], refl2[0], refl2[1], 3, BLUE, 12)
    c.t(src2[0] - 6, src2[1] + 16, "入射光", 16, True, BLUE, "rb")
    c.t(refl2[0] + 4, refl2[1] + 8, "反射光", 16, True, BLUE, "la")
    r3 = 52
    c.d.arc([O2[0]-r3, O2[1]-r3, O2[0]+r3, O2[1]+r3], 90, 140, fill=(44, 96, 178), width=3)
    c.d.arc([O2[0]-r3, O2[1]-r3, O2[0]+r3, O2[1]+r3], 40, 90, fill=(44, 96, 178), width=3)
    c.t(O2[0], O2[1]+66, "i = i′", 16, True, BLUE, "mm")
    c.rich(O2[0], yb - 150, "全反射：i ≥ C", 17, True, BLUE, "ma")
    c.rich(W // 2, H - 40, "全反射临界角 sinC = \\frac{1}{n}（约 42°），仅光密→光疏时发生", 17)
    save(c, "p_refract", acc)


def p_wave():
    c = C("机械波的图象", "相邻波峰间距是一个波长 λ")
    px, py = coords(c, -0.3, 7.4, -1.8, 1.9, (78, 130, 596, 360))
    c.fn_curve(lambda v: math.sin(v), 0, 7.2, px, py, 320, BLUE)
    for k in (0, 2 * math.pi):
        c.dash(px(k), py(-1.7), px(k), py(1.7), 1, (185, 185, 185))
    c.arrow(px(0), py(-1.45), px(2 * math.pi), py(-1.45), 2, RED, 11)
    c.t(px(math.pi), py(-1.45) - 10, "λ", 20, True, RED, "mb")
    c.t(px(1.57), py(1) - 14, "波峰", 17, False, GREY, "mb")
    c.t(px(4.71), py(-1) + 30, "波谷", 17, False, GREY, "mt")
    c.rich(W // 2, H - 40, "v = λ/T = λf ，波的频率由波源决定", 17)
    save(c, "p_wave", acc)


def p_transformer():
    c = C("理想变压器", "电压比等于匝数比，功率不变")
    c.rect(210, 140, 440, 340, (238, 242, 246), (60, 70, 85), 3, 6)
    c.t(325, 236, "铁芯", 18, True, (60, 70, 85), "mm")
    # 原线圈
    for i in range(5):
        y = 168 + i * 34
        c.circle(196, y, 17, None, RED, 3)
    c.t(120, 150, "原线圈 n_{1}", 17, True, RED, "la")
    c.t(120, 178, "U_{1}", 17, True, RED, "la")
    # 副线圈
    for i in range(8):
        y = 152 + i * 24
        c.circle(454, y, 16, None, BLUE, 3)
    c.t(492, 150, "副线圈 n_{2}", 17, True, BLUE, "la")
    c.t(492, 178, "U_{2}", 17, True, BLUE, "la")
    c.rich(W // 2, H - 40, "\\frac{U_{1}}{U_{2}} = \\frac{n_{1}}{n_{2}} ，\\frac{I_{1}}{I_{2}} = \\frac{n_{2}}{n_{1}} ，P_{1} = P_{2}", 16)
    save(c, "p_transformer", acc)


def p_gas():
    c = C("气体等温变化 p-V 图", "温度不变时 pV = 常量（双曲线）")
    px, py = coords(c, -0.4, 7.0, -0.4, 7.0, (78, 120, 596, 372))
    for k, col, lab in ((6.0, RED, "T_{1}"), (12.0, BLUE, "T_{2} 高温")):
        c.fn_curve(lambda v, k=k: k / v if v > 0.4 else None, 0.8, 6.8, px, py, 260, col, 3)
    c.t(px(1.1), py(6 / 1.1) - 16, "T_{1}", 17, True, RED, "ma")
    c.t(px(6.1), py(12 / 6.1) + 18, "T_{2} 高温", 17, True, BLUE, "ma")
    c.poly([(px(2), py(0)), (px(2), py(6 / 2)), (px(4), py(6 / 4)), (px(4), py(0))], (226, 234, 246), BLUE, 2)
    c.t(px(3.0), py(1.3), "面积 = pV", 16, True, BLUE, "ma")
    c.rich(W // 2, H - 40, "玻意耳定律 p_{1}V_{1} = p_{2}V_{2}（T 不变）", 17)
    save(c, "p_gas", acc)


# ===================== 化学 =====================
def c_atom():
    c = C("原子结构示意图", "以钠为例：核电荷数 11，三层电子 2、8、1")
    ox, oy = 320, 250
    c.circle(ox, oy, 26, (250, 236, 226), (150, 90, 40), 3)
    c.t(ox, oy, "+11", 19, True, (150, 90, 40), "mm")
    for r, n in ((56, 2), (86, 8), (116, 1)):
        c.circle(ox, oy, r, None, (70, 70, 70), 2)
        for i in range(n):
            a = -math.pi / 2 + i * 2 * math.pi / n
            c.circle(ox + r * math.cos(a), oy + r * math.sin(a), 5, BLUE, BLUE, 1)
    c.t(ox + 130, oy - 116, "最外层 1 个电子", 17, True, RED, "la")
    c.arrow(ox + 96, oy - 96, ox + 60, oy - 56, 2, RED, 10)
    c.rich(W // 2, H - 40, "核外电子排布 2、8、1 ，最外层电子数决定化学性质", 17)
    save(c, "c_atom", acc)


def c_table():
    c = C("元素周期表结构", "7 个周期、16 个族，金属在左、非金属在右")
    x0, y0, cw, ch = 120, 152, 30, 24
    for r in range(7):
        for col in range(18):
            x = x0 + col * cw
            y = y0 + r * ch
            c.rect(x, y, x + cw - 2, y + ch - 2, (238, 242, 248), (170, 178, 190), 1, 2)
    # 过渡金属 d 区（第 3~12 族，第 4~7 周期）
    c.rect(x0 + 2 * cw, y0 + 3 * ch, x0 + 12 * cw - 2, y0 + 7 * ch - 2, (244, 240, 230), (160, 130, 60), 2, 2)
    c.t(x0 + 7 * cw, y0 + 3 * ch + 8, "过渡金属（d 区）", 14, True, (140, 100, 40), "mt")
    # 标注区
    c.rect(x0, y0, x0 + 2 * cw - 2, y0 + 7 * ch - 2, None, (198, 58, 58), 2, 2)
    c.rect(x0 + 12 * cw, y0, x0 + 18 * cw - 2, y0 + 7 * ch - 2, None, (44, 96, 178), 2, 2)
    c.t(x0 + cw, y0 + 7 * ch + 22, "金属区", 16, True, RED, "ma")
    c.t(x0 + 15 * cw, y0 + 7 * ch + 22, "非金属区", 16, True, BLUE, "ma")
    c.t(x0 - 10, y0 + 6, "ⅠA", 15, True, (120, 120, 120), "rm")
    c.rich(W // 2, H - 40, "同周期从左到右非金属性增强；镧系、锕系另列两行（57–71、89–103）", 15)
    save(c, "c_table", acc)


def c_bond():
    c = C("离子键与共价键", "电子式：离子键用方括号与电荷，共价键用共用电子对")
    c.t(320, 132, "NaCl（离子键）", 20, True, RED, "mm")
    c.rich(320, 196, "Na^{+}  [ ∶Cl∶ ]^{−}", 34, True, dark, "ma")
    c.t(320, 232, "钠失 1 个电子，氯得 1 个电子", 15, False, (90, 90, 90), "ma")
    c.t(320, 300, "HCl（共价键）", 20, True, BLUE, "mm")
    c.rich(320, 360, "H ∶ Cl", 34, True, dark, "ma")
    c.t(320, 396, "氢氯各提供 1 个电子共用", 15, False, (90, 90, 90), "ma")
    c.rich(W // 2, H - 24, "离子键无方向性；共价键有方向性和饱和性", 14)
    save(c, "c_bond", acc)


def c_cell():
    c = C("锌铜原电池", "活泼金属作负极失电子，电子经导线流向正极")
    c.rect(120, 160, 540, 340, (232, 242, 250), (120, 150, 180), 2, 4)
    c.line(320, 160, 320, 340, 3, (120, 150, 180))
    c.rect(160, 176, 200, 340, (226, 226, 232), (110, 110, 120), 3, 3)
    c.rect(440, 176, 480, 340, (222, 210, 196), (150, 110, 60), 3, 3)
    c.t(180, 190, "Zn", 18, True, dark, "mt")
    c.t(460, 190, "Cu", 18, True, dark, "mt")
    c.line(180, 176, 180, 112, 3, dark)
    c.line(460, 176, 460, 112, 3, dark)
    c.line(180, 112, 460, 112, 3, dark)
    c.t(320, 130, "电流表 / 导线", 15, False, (90, 90, 90), "mb")
    c.arrow(288, 100, 232, 100, 2, RED, 10)
    c.t(224, 84, "e^{−}", 15, True, RED, "rm")
    c.t(250, 300, "负极（氧化）", 16, True, RED, "ma")
    c.t(410, 300, "正极（还原）", 16, True, BLUE, "ma")
    c.rich(W // 2, H - 40, "负极 Zn−2e^{−}=Zn^{2+} ，正极 2H^{+}+2e^{−}=H_{2}↑", 16)
    save(c, "c_cell", acc)


def c_electro():
    c = C("电解饱和食盐水", "阳极氯离子放电，阴极氢离子放电")
    c.rect(150, 170, 490, 340, (232, 242, 250), (120, 150, 180), 2, 4)
    c.line(230, 340, 230, 150, 4, RED)
    c.line(410, 340, 410, 150, 4, BLUE)
    c.t(230, 140, "阳极（石墨）", 16, True, RED, "mb")
    c.t(410, 140, "阴极（铁）", 16, True, BLUE, "mb")
    for i in range(5):
        c.circle(250 + i * 32, 220 + (i % 2) * 26, 5, None, (150, 170, 190), 1)
    c.t(196, 268, "Cl_{2}↑", 17, True, RED, "lm")
    c.t(424, 268, "H_{2}↑", 17, True, BLUE, "lm")
    c.t(320, 330, "NaOH 溶液", 16, False, (60, 90, 120), "mb")
    c.rich(W // 2, H - 40, "2NaCl + 2H_{2}O = 2NaOH + H_{2}↑ + Cl_{2}↑（电解）", 16)
    save(c, "c_electro", acc)


def c_ethanol():
    c = C("乙醇与乙酸的结构", "官能团决定性质：羟基 −OH、羧基 −COOH")
    c.rect(90, 120, 550, 240, "white", (44, 96, 178), 2, 8)
    c.rich(320, 164, "乙醇 C_{2}H_{5}OH", 20, True, BLUE, "ma")
    c.rich(320, 200, "CH_{3} — CH_{2} — OH", 30, True, dark, "ma")
    c.t(430, 226, "羟基 −OH：与钠反应、被氧化成醛", 14, False, (90, 90, 90), "rm")
    c.rect(90, 272, 550, 392, "white", (198, 58, 58), 2, 8)
    c.rich(320, 316, "乙酸 CH_{3}COOH", 20, True, RED, "ma")
    c.rich(320, 352, "CH_{3} — COOH", 30, True, dark, "ma")
    c.t(430, 378, "羧基 −COOH：酸性、酯化反应", 14, False, (90, 90, 90), "rm")
    c.rich(W // 2, H - 22, "乙醇与乙酸在浓硫酸催化下生成乙酸乙酯", 14)
    save(c, "c_ethanol", acc)


def c_equil():
    c = C("化学平衡的移动", "增大反应物浓度：v正 瞬间升高，v逆 随后增大，建立新平衡")
    px, py = coords(c, -0.4, 7.2, -0.4, 7.2, (78, 120, 596, 372), "t", "v")
    t1, v0, v1, vnew = 2.4, 2.0, 5.2, 3.3
    # 原平衡（一段水平线，正逆速率相等）
    c.line(px(0), py(v0), px(t1), py(v0), 3, (90, 90, 90))
    c.t(px(1.0), py(v0) - 16, "原平衡：v正 = v逆", 15, True, (90, 90, 90), "mb")
    # t1 时刻虚线
    c.dash(px(t1), py(0), px(t1), py(v1 + 0.3), 1, (160, 160, 160))
    c.rich(px(t1) - 2, py(0) + 20, "t_{1} 增大反应物浓度", 15, False, (90, 90, 90), "ma")
    # v正：t1 突增后缓慢下降
    c.dash(px(t1), py(v0), px(t1), py(v1), 1, (198, 58, 58))
    c.fn_curve(lambda t: vnew + (v1 - vnew) * math.exp(-1.1 * (t - t1)), t1, 6.8, px, py, 300, RED, 4)
    # v逆：t1 后缓慢上升
    c.fn_curve(lambda t: vnew - (vnew - v0) * math.exp(-1.1 * (t - t1)), t1, 6.8, px, py, 300, BLUE, 4)
    # 新平衡水平虚线
    c.dash(px(t1), py(vnew), px(7.0), py(vnew), 1, (160, 160, 160))
    c.t(px(6.6), py(vnew) - 10, "新平衡 v正′ = v逆′", 15, True, (120, 120, 120), "mb")
    # 标注
    c.t(px(3.0), py(4.0), "v正（突增后下降）", 16, True, RED, "ma")
    c.t(px(4.6), py(2.5), "v逆（逐渐增大）", 16, True, BLUE, "ma")
    c.t(px(0.3), py(v0) + 22, "v正 = v逆", 14, False, (90, 90, 90), "lb")
    c.rich(W // 2, H - 40, "增大反应物浓度，平衡向正反应方向移动；新平衡时 v正 = v逆 但都大于原平衡", 14)
    save(c, "c_equil", acc)


def c_titration():
    c = C("酸碱中和滴定曲线", "滴定突跃与指示剂选择")
    px, py = coords(c, -0.4, 14.0, -0.6, 14.6, (78, 120, 596, 372))
    def f(v):
        if v < 9.9:
            return 1.2 + 0.12 * v
        if v <= 10.1:
            return 1.2 + 0.12 * 9.9 + (v - 9.9) / 0.2 * 11.2
        return 12.2 + 0.12 * (v - 10.1)
    c.fn_curve(f, 0, 13.8, px, py, 400, RED)
    c.dash(px(0), py(7), px(13.8), py(7), 1, (170, 170, 170))
    c.t(px(1.2), py(7) + 18, "pH=7 中和点", 16, False, GREY, "la")
    c.dash(px(10), py(0), px(10), py(13.8), 1, (150, 150, 150))
    c.t(px(10) + 8, py(13.6), "V=10 mL", 16, False, GREY, "la")
    c.t(px(11.2), py(13.6), "突跃范围", 16, True, BLUE, "ma")
    c.rich(W // 2, H - 40, "强酸强碱滴定可用酚酞或甲基橙，误差 ≤ 0.1%", 16)
    save(c, "c_titration", acc)


def c_mole():
    c = C("物质的量核心换算", "以 n 为中心，四个方向对应四个公式")
    ox, oy = 320, 240
    c.circle(ox, oy, 52, (226, 236, 250), BLUE, 3)
    c.t(ox, oy, "n (mol)", 18, True, BLUE, "mm")
    nodes = [(320, 132, "m 质量", "n = \\frac{m}{M}"), (320, 348, "V 气体体积", "n = \\frac{V}{V_{m}}"),
             (166, 240, "N 微粒数", "n = \\frac{N}{N_{A}}"), (474, 240, "c 物质的量浓度", "n = c V")]
    for x, y, lab, formula in nodes:
        c.rect(x - 74, y - 30, x + 74, y + 30, "white", (60, 60, 60), 2, 6)
        c.t(x, y - 8, lab, 14, True, dark, "mm")
        c.t(x, y + 14, formula, 14, False, (90, 90, 90), "mm")
        c.arrow(ox, oy, x, y, 2, GREY, 10)
    c.rich(W // 2, H - 22, "N_{A} ≈ 6.02×10^{23} mol^{−1}，V_{m} = 22.4 L/mol（标准状况）", 14)
    save(c, "c_mole", acc)


def b_cell():
    c = C("细胞结构示意", "各细胞器分工协作，线粒体供能、叶绿体光合")
    c.rect(90, 120, 550, 366, (240, 246, 252), (60, 110, 160), 4, 20)
    def num(x, y, n):
        c.circle(x, y, 13, "white", (40, 40, 40), 2)
        c.t(x, y, str(n), 14, True, (40, 40, 40), "mm")
    # 细胞核
    c.circle(300, 232, 56, (230, 238, 250), (44, 96, 178), 3)
    c.circle(300, 232, 20, (196, 210, 232), (44, 96, 178), 2)
    num(300, 232, 1)
    # 线粒体
    c.d.ellipse([382, 168, 470, 212], fill=(248, 228, 228), outline=RED, width=3)
    for i in range(3):
        c.line(392 + i * 26, 173, 392 + i * 26, 207, 2, RED)
    num(426, 190, 2)
    # 叶绿体
    c.d.ellipse([382, 274, 470, 318], fill=(226, 244, 232), outline=GREEN, width=3)
    for i in range(3):
        c.line(392 + i * 26, 279, 392 + i * 26, 313, 2, GREEN)
    num(426, 296, 3)
    # 内质网
    for i in range(3):
        c.line(128, 168 + i * 16, 208, 168 + i * 16, 3, (150, 120, 60))
    num(168, 220, 4)
    # 高尔基体
    for i in range(3):
        c.d.arc([128, 268 + i * 24, 196, 300 + i * 24, ], 200, 340, fill=(110, 90, 150), width=3)
    num(162, 252, 5)
    # 液泡
    c.d.ellipse([250, 300, 350, 356], fill=(236, 244, 250), outline=(90, 140, 170), width=2)
    num(300, 328, 6)
    c.rich(W // 2, 388, "① 细胞核  ② 线粒体  ③ 叶绿体", 14, True, (60, 60, 60), "ma")
    c.rich(W // 2, 414, "④ 内质网  ⑤ 高尔基体  ⑥ 液泡", 14, True, (60, 60, 60), "ma")
    c.rich(W // 2, H - 22, "植物细胞特有细胞壁、叶绿体、液泡；动物细胞有中心体", 13)
    save(c, "b_cell", acc)


def b_photo():
    c = C("光合作用过程", "光反应在类囊体薄膜，暗反应在叶绿体基质")
    c.rect(60, 140, 296, 356, (240, 248, 240), (38, 132, 88), 3, 8)
    c.rect(348, 140, 584, 356, (250, 246, 236), (170, 130, 40), 3, 8)
    c.t(178, 158, "光反应（类囊体）", 16, True, GREEN, "mm")
    c.t(466, 158, "暗反应（基质）", 16, True, (170, 130, 40), "mm")
    lines = ["H_{2}O 光解 → O_{2}", "ADP + Pi → ATP", "NADP^{+} → NADPH"]
    for i, t in enumerate(lines):
        c.rich(178, 208 + i * 38, t, 15, False, dark, "ma")
    lines2 = ["CO_{2} + C_{5} → 2C_{3}", "C_{3} 被还原 → (CH_{2}O)"]
    for i, t in enumerate(lines2):
        c.rich(466, 214 + i * 40, t, 14, False, dark, "ma")
    c.arrow(300, 244, 344, 244, 3, RED, 12)
    c.arrow(300, 264, 344, 264, 2, (200, 120, 60), 10)
    c.t(322, 232, "ATP", 13, True, RED, "mb")
    c.t(322, 276, "NADPH", 13, True, (200, 120, 60), "mt")
    c.rich(W // 2, H - 22, "光能 → ATP 和 NADPH 中的活跃化学能 → 有机物中稳定化学能", 13)
    save(c, "b_photo", acc)


def b_resp():
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
    save(c, "b_resp", acc)


def b_dna():
    c = C("DNA 双螺旋与中心法则", "碱基互补配对 A—T、G—C")
    x0 = 150
    for i in range(10):
        y = 140 + i * 22
        c.line(x0, y, x0 + 40, y + 10, 3, BLUE)
        c.line(x0 + 70, y, x0 + 110, y + 10, 3, (200, 120, 60))
        c.line(x0 + 40, y + 10, x0 + 70, y, 2, (150, 150, 150))
    c.t(x0 + 20, 128, "一条链", 15, True, BLUE, "mb")
    c.t(x0 + 90, 128, "另一条链", 15, True, (200, 120, 60), "mb")
    c.t(x0 + 55, 366, "氢键", 15, False, GREY, "ma")
    # 中心法则
    c.rect(300, 176, 396, 214, "white", (60, 60, 60), 2, 6)
    c.t(348, 195, "DNA", 17, True, dark, "mm")
    c.rect(300, 268, 396, 306, "white", (60, 60, 60), 2, 6)
    c.t(348, 287, "RNA", 17, True, dark, "mm")
    c.rect(454, 268, 556, 306, "white", (60, 60, 60), 2, 6)
    c.t(505, 287, "蛋白质", 16, True, dark, "mm")
    c.arrow(348, 214, 348, 264, 2, RED, 10)
    c.t(356, 240, "转录", 14, True, RED, "lm")
    c.arrow(396, 287, 450, 287, 2, GREEN, 10)
    c.t(422, 272, "翻译", 14, True, GREEN, "mb")
    c.arrow(348, 176, 348, 132, 2, BLUE, 10)
    c.t(356, 150, "复制", 14, True, BLUE, "lm")
    c.rich(W // 2, H - 40, "DNA → RNA → 蛋白质；逆转录、RNA 复制见于某些病毒", 15)
    save(c, "b_dna", acc)


def b_meiosis():
    c = C("基因的分离定律", "Aa × Aa 后代 3 显 : 1 隐，基因型 1:2:1")
    x0, y0, cw = 180, 176, 110
    heads = ["", "A", "a"]
    for i, h in enumerate(heads):
        if h:
            c.t(x0 + (i + 0.5) * cw, y0 - 34, h, 19, True, BLUE, "mm")
    for j, h in enumerate(heads):
        if not h:
            continue
        c.t(x0 - 40, y0 + (j - 0.5) * 92, h, 19, True, BLUE, "lm")
    grid = [["AA", "Aa"], ["Aa", "aa"]]
    for j in range(2):
        for i in range(2):
            x = x0 + i * cw
            y = y0 + j * 88
            fill = (226, 240, 232) if grid[j][i] != "aa" else (248, 230, 230)
            c.rect(x, y, x + cw, y + 88, fill, (90, 90, 90), 2, 6)
            c.t(x + cw / 2, y + 44, grid[j][i], 22, True, dark, "mm")
    c.t(W // 2, 376, "表现型：显性 3/4 ，隐性 1/4", 17, True, RED, "ma")
    c.rich(W // 2, H - 40, "测交 Aa × aa → 1:1，可验证基因型", 15)
    save(c, "b_meiosis", acc)


def b_reflex():
    c = C("反射弧的五个环节", "感受器 → 传入神经 → 神经中枢 → 传出神经 → 效应器")
    y = 216
    labels = ["感受器", "传入神经", "神经中枢", "传出神经", "效应器"]
    xs = [96, 208, 320, 432, 544]
    for i, (x, lab) in enumerate(zip(xs, labels)):
        if i in (1, 3):
            c.d.ellipse([x - 26, y - 64, x + 26, y + 64], fill=(250, 246, 236), outline=(170, 130, 40), width=3)
            c.t(x, y - 80, lab, 15, True, (170, 130, 40), "mb")
        else:
            c.circle(x, y, 34, (232, 242, 250), (44, 96, 178), 3)
            c.t(x, y, lab, 15, True, (44, 96, 178), "mm")
    for i in range(4):
        c.arrow(xs[i] + 36, y, xs[i + 1] - 36, y, 3, (90, 90, 90), 12)
    c.t(W // 2, 320, "兴奋在突触处单向传递", 16, False, (90, 90, 90), "ma")
    c.rich(W // 2, H - 40, "神经调节基本方式：反射；结构基础：反射弧", 15)
    save(c, "b_reflex", acc)


def b_ecosys():
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
    save(c, "b_ecosys", acc)


# ===================== 政治 / 历史 / 地理 =====================
def g_curve():
    c = C("需求与供给曲线", "价格上升需求减少、供给增加，交点即均衡价格")
    px, py = coords(c, -0.4, 8.0, -0.4, 8.0, (78, 120, 596, 372), "Q 数量", "P 价格")
    c.fn_curve(lambda v: 8.2 - 0.95 * v, 0.4, 7.6, px, py, 50, RED)
    c.fn_curve(lambda v: 0.6 + 0.95 * v, 0.4, 7.6, px, py, 50, BLUE)
    c.t(px(6.6), py(8.2 - 0.95 * 6.6) - 20, "需求 D", 17, True, RED, "ma")
    c.t(px(6.6), py(0.6 + 0.95 * 6.6) + 26, "供给 S", 17, True, BLUE, "ma")
    c.dash(px(0), py(4.4), px(4.0), py(4.4), 1, (150, 150, 150))
    c.dash(px(4.0), py(0), px(4.0), py(4.4), 1, (150, 150, 150))
    c.circle(px(4.0), py(4.4), 6, GREEN, GREEN, 2)
    c.t(px(4.0) + 12, py(4.4) - 12, "E 均衡点", 16, True, GREEN, "lb")
    c.rich(W // 2, H - 40, "价格由价值决定并受供求影响；供不应求价格上涨", 15)
    save(c, "g_curve", acc)


def h_timeline():
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
    save(c, "h_timeline", acc)


def h_modern():
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
    save(c, "h_modern", acc)


def h_war():
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
    save(c, "h_war", acc)


def e_contour():
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
    save(c, "e_contour", acc)


def e_globe():
    c = C("经纬网与地球仪", "经线指示南北，纬线指示东西")
    ox, oy, R = 300, 250, 130
    c.circle(ox, oy, R, (238, 244, 250), (60, 90, 130), 3)
    for i in range(4):
        y = oy - R + 52 + i * 52
        hr = math.sqrt(max(1, R * R - (y - oy) ** 2))
        c.d.arc([ox - hr, y - 12, ox + hr, y + 12], 0, 180, fill=(60, 90, 130), width=2)
    for k in (-0.7, -0.35, 0, 0.35, 0.7):
        c.d.ellipse([ox - R * abs(k) if k else ox - 6, oy - R, ox + R * abs(k) if k else ox + 6, oy + R], outline=(60, 90, 130), width=2)
    c.d.line([ox, oy - R, ox, oy + R], fill=(198, 58, 58), width=3)
    c.t(ox, oy - R - 14, "N 北极", 15, True, (60, 90, 130), "mb")
    c.t(ox, oy + R + 16, "S 南极", 15, True, (60, 90, 130), "mt")
    c.t(ox + 10, oy + 8, "本初子午线", 14, True, RED, "la")
    c.arrow(460, 150, ox + 60, oy - 60, 2, GREY, 10)
    c.t(468, 146, "经线", 15, False, GREY, "lm")
    c.rich(W // 2, H - 40, "经度 180° 跨度、纬度 90°；1° 纬度约 111 km", 15)
    save(c, "e_globe", acc)


def e_pressure():
    c = C("气压带与风带（侧视展开）", "自北向南高压低压交替，风带斜向吹")
    bands = [(90, "极地高气压带", BLUE), (60, "副极地低气压带", RED),
             (30, "副热带高气压带", BLUE), (0, "赤道低气压带", RED),
             (-30, "副热带高气压带", BLUE), (-60, "副极地低气压带", RED),
             (-90, "极地高气压带", BLUE)]
    y0, bh, gap = 100, 26, 41
    for i, (lat, name, col) in enumerate(bands):
        y = y0 + i * gap
        c.rect(150, y, 350, y + bh, (238, 242, 250), col, 2, 4)
        c.t(145, y + bh / 2, name, 14, True, col, "rm")
    # 风带箭头画在两条气压带之间的空隙里（斜向表示风向）
    winds = [
        (0, "极地东风", "SW", (90, 130, 90)),
        (1, "西风带", "NE", (198, 58, 58)),
        (2, "东北信风", "SW", (44, 96, 178)),
        (3, "东南信风", "NW", (44, 96, 178)),
        (4, "西风带", "SE", (198, 58, 58)),
        (5, "极地东风", "NW", (90, 130, 90)),
    ]
    for i, label, d, col in winds:
        gy = y0 + i * gap + bh + (gap - bh) / 2
        if d == "SW":
            x1, y1, x2, y2 = 330, gy - 5, 268, gy + 5
        elif d == "NE":
            x1, y1, x2, y2 = 268, gy + 5, 330, gy - 5
        elif d == "NW":
            x1, y1, x2, y2 = 330, gy + 5, 268, gy - 5
        else:  # SE
            x1, y1, x2, y2 = 268, gy - 5, 330, gy + 5
        c.arrow(x1, y1, x2, y2, 3, col, 10)
        c.t(370, gy, label, 13, False, col, "lm")
    c.rich(W // 2, H - 40, "气压带、风带随太阳直射点季节移动；南、北半球风向相反", 14)
    save(c, "e_pressure", acc)


def e_water():
    c = C("水循环", "蒸发、水汽输送、降水、径流构成完整循环")
    c.rect(60, 300, 580, 372, (226, 240, 250), (120, 150, 180), 2, 4)
    c.t(320, 336, "海洋", 16, True, (60, 100, 140), "mm")
    c.poly([(60, 300), (200, 230), (330, 292), (470, 208), (580, 292), (580, 300)], (236, 232, 220), (150, 130, 100), 3)
    c.t(470, 240, "陆地", 16, True, (120, 100, 70), "mb")
    c.arrow(180, 292, 180, 208, 3, RED, 12)
    c.t(188, 250, "蒸发", 15, True, RED, "lm")
    c.arrow(180, 200, 420, 172, 3, BLUE, 12)
    c.t(300, 168, "水汽输送", 15, True, BLUE, "mb")
    for x in (360, 420, 480):
        c.line(x, 186, x - 8, 250, 2, BLUE)
    c.t(512, 178, "降水", 15, True, BLUE, "lb")
    c.arrow(470, 286, 300, 300, 3, GREEN, 12)
    c.t(380, 296, "地表径流", 14, True, GREEN, "mb")
    c.rich(W // 2, H - 40, "人类影响最大的是地表径流；水循环使水资源不断更新", 15)
    save(c, "e_water", acc)


def e_plate():
    c = C("板块碰撞与褶皱山脉", "板块挤压使岩层弯曲隆起，形成高大山脉")
    base = 340
    c.rect(60, base, 580, base + 30, (232, 226, 214), (150, 130, 100), 2, 3)
    pts = []
    for i in range(0, 521, 4):
        x = 60 + i
        u = (x - 320) / 240.0
        y = base - 96 * math.exp(-u * u * 2.6)
        pts.append((x, y))
    c.d.line(pts, fill=(150, 110, 60), width=5)
    for k in (1, 2):
        pts2 = [(x, y + k * 22) for x, y in pts]
        c.d.line(pts2, fill=(176, 140, 90), width=2)
    c.arrow(110, base - 20, 250, base - 20, 3, RED, 12)
    c.arrow(530, base - 20, 390, base - 20, 3, BLUE, 12)
    c.t(180, base + 16, "板块挤压", 15, True, RED, "ma")
    c.t(460, base + 16, "板块挤压", 15, True, BLUE, "ma")
    c.t(320, base - 118, "褶皱山", 16, True, (150, 110, 60), "mb")
    c.rich(W // 2, H - 40, "消亡边界形成山脉、海沟；生长边界形成裂谷、海岭", 15)
    save(c, "e_plate", acc)


def e_monsoon():
    c = C("季风环流", "海陆热力性质差异导致冬夏风向相反")
    c.rect(60, 150, 300, 360, (226, 240, 250), (120, 150, 180), 2, 4)
    c.t(180, 340, "海洋（夏季升温慢）", 15, True, (60, 100, 140), "mm")
    c.rect(340, 150, 580, 360, (238, 232, 218), (150, 130, 100), 2, 4)
    c.t(460, 340, "陆地（夏季升温快）", 15, True, (120, 100, 70), "mm")
    c.arrow(200, 210, 460, 210, 4, RED, 13)
    c.t(330, 196, "夏季风：海洋 → 陆地", 16, True, RED, "mb")
    c.arrow(460, 296, 200, 296, 4, BLUE, 13)
    c.t(330, 316, "冬季风：陆地 → 海洋", 16, True, BLUE, "mt")
    c.t(180, 152, "夏季：高压（凉）", 15, True, (60, 100, 140), "mm")
    c.t(460, 152, "夏季：低压（热）", 15, True, (120, 100, 70), "mm")
    c.rich(W // 2, H - 40, "夏季陆热低压、海洋高压；冬季相反，故风向随季节转换", 15)
    save(c, "e_monsoon", acc)


def c_argue():
    c = C("议论文结构", "引—议—联—结，层层递进")
    boxes = [(96, 190, 448, 238, "引：提出中心论点（开门见山）", BLUE),
             (96, 258, 448, 306, "议：分论点并列/递进 + 事例 + 分析", GREEN),
             (96, 326, 448, 374, "联：联系现实，正反对比", (170, 130, 40)),
             (200, 394, 344, 436, "结：回扣论点，升华", RED)]
    for x1, y1, x2, y2, lab, col in boxes:
        c.rect(x1, y1, x2, y2, "white", col, 2, 6)
        c.t((x1 + x2) / 2, (y1 + y2) / 2, lab, 15, True, col, "mm")
    for i in range(3):
        c.arrow(272, 238 + i * 68, 272, 256 + i * 68, 2, GREY, 9)
    c.rich(W // 2, 132, "分论点要并列或递进，不能交叉重复", 16, False, (90, 90, 90), "ma")
    save(c, "c_argue", acc)


def e_clause():
    c = C("主从复合句结构", "从句必须有引导词，且用陈述语序")
    def node(x, y, w, lab, col, bold=True):
        c.rect(x - w / 2, y - 24, x + w / 2, y + 24, "white", col, 2, 6)
        c.t(x, y, lab, 15, bold, col, "mm")
    node(320, 140, 300, "主句：He said", BLUE)
    node(180, 240, 220, "宾语从句", GREEN)
    node(470, 240, 220, "定语从句", (170, 130, 40))
    node(180, 330, 280, "that he would come", dark, False)
    node(470, 330, 280, "which I told you", dark, False)
    c.line(320, 164, 200, 216, 2, GREY)
    c.line(320, 164, 450, 216, 2, GREY)
    c.arrow(180, 264, 180, 306, 2, GREY, 9)
    c.arrow(470, 264, 470, 306, 2, GREY, 9)
    c.rich(W // 2, H - 40, "宾语从句用 that/if/whether；定语从句关系词看先行词", 15)
    save(c, "e_clause", acc)


def p_equilibrium():
    c = C("共点力平衡", "三力平衡：任两力的合力与第三个力等大反向")
    ox, oy = 210, 210
    c.circle(ox, oy, 7, dark, dark, 1)
    c.t(ox + 12, oy + 14, "O", 18, True, dark, "la")
    # 三个力
    c.arrow(ox, oy, ox - 96, oy - 74, 4, RED, 14)     # 左上
    c.arrow(ox, oy, ox + 104, oy - 68, 4, BLUE, 14)   # 右上
    c.arrow(ox, oy, ox, oy + 108, 4, GREEN, 14)       # 向下
    c.t(ox - 100, oy - 84, "F_{1}", 20, True, RED, "rb")
    c.t(ox + 112, oy - 74, "F_{2}", 20, True, BLUE, "lb")
    c.t(ox + 10, oy + 104, "F_{3}", 20, True, GREEN, "la")
    c.t(ox, oy - 6, "合力 = 0", 16, True, GREY, "mb")
    # 右侧矢量三角形：首尾相接闭合
    tx, ty = 430, 250
    p1 = (tx - 90, ty + 60)
    p2 = (tx + 100, ty + 44)
    p3 = (tx + 6, ty - 96)
    c.arrow(p1[0], p1[1], p2[0], p2[1], 4, RED, 14)
    c.arrow(p2[0], p2[1], p3[0], p3[1], 4, BLUE, 14)
    c.arrow(p3[0], p3[1], p1[0], p1[1], 4, GREEN, 14)
    c.t(tx - 2, ty + 50, "F_{1}", 18, True, RED, "ma")
    c.t(tx + 74, ty - 20, "F_{2}", 18, True, BLUE, "lm")
    c.t(tx - 46, ty - 18, "F_{3}", 18, True, GREEN, "rm")
    c.t(tx, ty + 112, "首尾相接 = 闭合三角形", 15, True, GREY, "ma")
    c.rich(W // 2, H - 42, "平衡条件：∑F = 0；正交分解后 ∑F_{x}=0 、∑F_{y}=0", 17)
    save(c, "p_equilibrium", acc)


def p_momentum():
    c = C("一维碰撞与动量守恒", "碰撞前后系统总动量不变")
    y = 230
    c.line(70, y, 570, y, 3, GREY)
    c.t(84, y - 26, "碰前", 17, True, GREY, "la")
    # 碰撞前
    c.circle(150, y - 40, 34, (250, 236, 226), RED, 3)
    c.t(150, y - 40, "m_{1}", 15, True, RED, "mm")
    c.arrow(190, y - 40, 270, y - 40, 3, RED, 13)
    c.t(230, y - 58, "v_{1}", 17, True, RED, "mb")
    c.circle(450, y - 40, 30, (226, 240, 250), BLUE, 3)
    c.t(450, y - 40, "m_{2}", 15, True, BLUE, "mm")
    c.arrow(414, y - 40, 352, y - 40, 3, BLUE, 13)
    c.t(384, y - 58, "v_{2}", 17, True, BLUE, "mb")
    c.dash(320, y - 70, 320, y + 10, 2, (150, 150, 150))
    # 碰撞后
    c.t(84, y + 80, "碰后", 17, True, GREY, "la")
    c.arrow(150, y + 92, 235, y + 92, 3, RED, 13)
    c.t(195, y + 110, "v_{1}′", 17, True, RED, "mt")
    c.arrow(450, y + 92, 330, y + 92, 3, BLUE, 13)
    c.t(390, y + 110, "v_{2}′", 17, True, BLUE, "mt")
    c.rich(W // 2, H - 42, "m_{1}v_{1} + m_{2}v_{2} = m_{1}v_{1}′ + m_{2}v_{2}′", 18)
    save(c, "p_momentum", acc)


def p_interference():
    c = C("双缝干涉", "两列相干光在屏上叠加形成明暗相间条纹")
    c.rect(70, 150, 110, 230, "white", (40, 40, 40), 2)
    c.line(90, 178, 90, 202, 2, dark)   # 单缝
    c.t(90, 168, "S_{0}", 16, True, dark, "mb")
    c.rect(140, 150, 170, 230, "white", (40, 40, 40), 2)
    c.line(148, 176, 148, 194, 2, dark)
    c.line(162, 176, 162, 194, 2, dark)
    c.t(155, 166, "S_{1} S_{2}", 15, True, dark, "mb")
    c.line(520, 150, 520, 230, 4, (200, 200, 205))
    c.t(520, 138, "屏", 16, True, dark, "mb")
    # 两条光路
    c.line(155, 185, 520, 176, 2, RED)
    c.line(155, 185, 520, 208, 2, BLUE)
    # 屏上条纹
    for i, yy in enumerate((156, 176, 208)):
        col = (240, 240, 245) if i % 2 else (230, 120, 120)
        c.rect(520, yy - 10, 560, yy + 10, col, (120, 120, 120), 1)
    c.t(540, 244, "明暗相间", 14, True, GREY, "ma")
    c.rich(W // 2, H - 42, "相邻亮纹间距 Δx = Lλ/d（L 为缝到屏距离，d 为双缝间距）", 17)
    save(c, "p_interference", acc)


def p_em():
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
    save(c, "p_em", acc)


def p_ac():
    c = C("正弦式交流电", "e = E_{m} sin ωt，周期 T，频率 f = \\frac{1}{T}")
    px, py = coords(c, -0.6, 10.4, -1.3, 1.3, (80, 122, 596, 360), "t", "e")
    c.fn_curve(lambda t: math.sin(t * 0.628), 0, 10.0, px, py, 300, RED, 4)
    c.dash(px(0), py(1.0), px(10.0), py(1.0), 1, GREY)
    c.t(px(2.5), py(1.16), "峰值 E_{m}", 16, True, RED, "mb")
    c.dash(px(0), py(-1.0), px(10.0), py(-1.0), 1, GREY)
    c.t(px(2.5), py(-1.18), "−E_{m}", 16, True, RED, "la")
    c.dash(px(10.0), py(0), px(10.0), py(1.02), 1, (150, 150, 150))
    c.t(px(10.0), py(0) + 22, "T", 16, True, BLUE, "ma")
    c.rich(W // 2, H - 42, "有效值 E = \\frac{E_{m}}{\\sqrt{2}} ≈ 0.707 E_{m}；ω = \\frac{2π}{T} = 2πf", 17)
    save(c, "p_ac", acc)


def p_capacitor():
    c = C("平行板电容器", "板间是匀强电场，电容 C = \\frac{εS}{4πkd}")
    y1, y2 = 150, 250
    c.rect(120, y1 - 8, 500, y1 + 8, "white", (40, 40, 40), 3)
    c.rect(120, y2 - 8, 500, y2 + 8, "white", (40, 40, 40), 3)
    for i in range(5):
        x = 150 + i * 80
        c.arrow(x, y1 + 8, x, y2 - 8, 2, (90, 90, 90), 10)
    for i in range(6):
        x = 130 + i * 76
        c.t(x, y1 - 18, "+", 18, True, RED, "mm")
        c.t(x, y2 + 16, "−", 20, True, BLUE, "mm")
    c.t(560, y1, "A 板", 17, True, RED, "la")
    c.t(560, y2, "B 板", 17, True, BLUE, "la")
    c.t(310, (y1 + y2) // 2, "E 匀强", 17, True, (90, 90, 90), "mm")
    c.rich(W // 2, H - 42, "C = \\frac{εS}{4πkd}；U = Ed；带电后储存电能 W = \\frac{CU^{2}}{2}", 17)
    save(c, "p_capacitor", acc)


def m_seq():
    c = C("等差数列与等比数列", "等差看「差」，等比看「比」")
    c.t(70, 120, "等差数列：", 18, True, BLUE, "la")
    row1 = [(110, 150), (210, 150), (310, 150), (410, 150), (510, 150)]
    for i, (x, y) in enumerate(row1):
        c.circle(x, y, 18, (226, 236, 250), BLUE, 2)
        c.t(x, y, str(2 + i * 3), 15, True, dark, "mm")
    for i in range(4):
        c.arrow(row1[i][0] + 18, 150, row1[i + 1][0] - 18, 150, 2, BLUE, 9)
        c.t((row1[i][0] + row1[i + 1][0]) // 2, 138, "+d", 14, True, BLUE, "mb")
    c.t(70, 220, "等比数列：", 18, True, RED, "la")
    row2 = [(110, 270), (210, 270), (310, 270), (410, 270), (510, 270)]
    vals = ["2", "4", "8", "16", "32"]
    for i, (x, y) in enumerate(row2):
        c.circle(x, y, 18, (250, 232, 226), RED, 2)
        c.t(x, y, vals[i], 15, True, dark, "mm")
    for i in range(4):
        c.arrow(row2[i][0] + 18, 270, row2[i + 1][0] - 18, 270, 2, RED, 9)
        c.t((row2[i][0] + row2[i + 1][0]) // 2, 288, "×q", 14, True, RED, "mt")
    c.rich(W // 2, H - 42, "等差通项 a_{n}=a_{1}+(n−1)d；等比通项 a_{n}=a_{1}q^{n−1}", 17)
    save(c, "m_seq", acc)


def m_prob():
    c = C("古典概型", "等可能基本事件：P(A) = \\frac{有利事件数}{总事件数}")
    # 骰子
    c.rect(130, 130, 230, 230, "white", (40, 40, 40), 3, 10)
    for i, (dx, dy) in enumerate(((-32, -32), (32, -32), (-32, 32), (32, 32), (0, 0), (0, -64))):
        x, y = 180 + dx, 180 + dy
        if i < 5:
            c.circle(x, y, 8, dark, dark, 1)
    c.t(180, 252, "掷骰子", 15, True, dark, "mm")
    c.t(400, 130, "P(奇数)", 20, True, RED, "la")
    c.rich(400, 178, "= \\frac{3}{6} = \\frac{1}{2}", 26, True, RED, "ma")
    c.t(400, 210, "样本空间 {1,2,3,4,5,6}", 14, False, GREY, "la")
    c.t(400, 236, "事件 A = {1,3,5}", 14, False, GREY, "la")
    c.rich(W // 2, H - 42, "基本事件等可能且有限时，P(A) = m/n（m 为 A 的基本事件数）", 16)
    save(c, "m_prob", acc)


def m_vector():
    c = C("向量的加法", "三角形法则与平行四边形法则")
    a = (110, -95)
    b = (75, 55)
    # 三角形法则
    ox, oy = 90, 290
    ax, ay = ox + a[0], oy + a[1]
    bx, by = ax + b[0], ay + b[1]
    c.arrow(ox, oy, ax, ay, 4, RED, 14)
    c.arrow(ax, ay, bx, by, 4, BLUE, 14)
    c.arrow(ox, oy, bx, by, 4, GREEN, 14)
    c.t(ox + 50, oy - 44, "a", 19, True, RED, "mm")
    c.t(ax + 44, ay + 26, "b", 19, True, BLUE, "lm")
    c.t(ox + 96, oy - 34, "a + b", 18, True, GREEN, "ma")
    c.t(ox + 92, oy + 40, "三角形法则", 14, True, GREY, "ma")
    # 平行四边形法则
    ox2, oy2 = 430, 290
    ax2, ay2 = ox2 + a[0], oy2 + a[1]
    bx2, by2 = ox2 + b[0], oy2 + b[1]
    c.dash(ax2, ay2, ax2 + b[0], ay2 + b[1], 2, (150, 150, 150))
    c.dash(bx2, by2, bx2 + a[0], by2 + a[1], 2, (150, 150, 150))
    c.arrow(ox2, oy2, ax2, ay2, 4, RED, 14)
    c.arrow(ox2, oy2, bx2, by2, 4, BLUE, 14)
    c.arrow(ox2, oy2, ax2 + b[0], ay2 + b[1], 4, GREEN, 14)
    c.t(ox2 + 52, oy2 - 44, "a", 18, True, RED, "mm")
    c.t(ox2 + 46, oy2 + 28, "b", 18, True, BLUE, "lm")
    c.t(ox2 + 100, oy2 - 34, "a+b", 18, True, GREEN, "ma")
    c.t(ox2 + 92, oy2 + 40, "平行四边形法则", 14, True, GREY, "ma")
    c.rich(W // 2, H - 42, "a + b 由平行四边形对角线或三角形首尾相接得到", 16)
    save(c, "m_vector", acc)


def m_deriv2():
    c = C("导数的几何意义", "f′(x_{0}) 是曲线在 x_{0} 处切线的斜率")
    px, py = coords(c, -2.2, 4.4, -3.4, 5.2, (80, 122, 596, 360), "x", "y")
    def f(x):
        return 0.22 * x ** 2 + 0.4 * x - 0.5
    c.fn_curve(f, -2.0, 4.0, px, py, 300, (44, 96, 178), 4)
    x0 = 1.8
    y0 = f(x0)
    k = 0.44 * x0 + 0.4
    # 切线
    def tan(x):
        return k * (x - x0) + y0
    c.fn_curve(tan, -1.2, 4.2, px, py, color=RED, w=3)
    c.circle(px(x0), py(y0), 6, RED, RED, 2)
    c.t(px(x0) + 12, py(y0) + 26, "切点 P(x_{0}, f(x_{0}))", 16, True, RED, "la")
    c.t(px(2.7), py(tan(2.7)) - 18, "切线斜率 = f′(x_{0})", 17, True, RED, "ma")
    c.t(px(-1.6), py(f(-1.6)) + 6, "y = f(x)", 17, True, BLUE, "lb")
    c.rich(W // 2, H - 42, "割线斜率 Δy/Δx，取极限得切线斜率 f′(x_{0}) = lim Δy/Δx", 16)
    save(c, "m_deriv2", acc)


# ===================== 执行 =====================
JOBS = [m_quad, m_trig, m_exp, m_log, m_deriv, m_unit, m_hyper, m_parabola, m_solid, m_normal,
        m_seq, m_prob, m_vector, m_deriv2,
        p_vt, p_slope, p_projectile, p_circular, p_efield, p_circuit, p_lorentz, p_refract, p_wave, p_transformer, p_gas,
        p_equilibrium, p_momentum, p_interference, p_em, p_ac, p_capacitor,
        c_table, c_bond, c_cell, c_electro, c_ethanol, c_titration, c_mole,
        b_cell, b_photo, b_resp, b_dna, b_meiosis, b_reflex, b_ecosys,
        g_curve, h_timeline, h_modern, h_war,
        e_contour, e_globe, e_pressure, e_water, e_plate, e_monsoon,
        c_argue, e_clause]

if __name__ == "__main__":
    print("生成配图 →", OUT)
    for j in JOBS:
        try:
            j()
        except Exception as e:
            print("!! 失败:", j.__name__, e)
    total = sum(s for _, s in acc)
    print("共 %d 张，合计 %.1f KB" % (len(acc), total / 1024.0))
