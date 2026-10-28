# -*- coding: utf-8 -*-
"""用程序生成知识库配图（纯本地绘制，零成本）。
统一风格：640x470 白底、深蓝标题、黑线示意图、底部公式说明。
公式用与 App 相同的数学语法：^{上标}、_{下标}、\\frac{分子}{分母}、\\sqrt{内容}，
由 t/rich 绘制成真正的上下标、上下分数与带横线的根式。
输出：app/src/main/assets/img/*.png
"""
import os
import re
import math

from PIL import Image, ImageDraw, ImageFont

W, H = 640, 470
HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))   # <root>/tools/img_gen -> <root>
OUT = os.path.join(ROOT, "app", "src", "main", "assets", "img")
FONT_R = "C:/Windows/Fonts/msyh.ttc"
FONT_B = "C:/Windows/Fonts/msyhbd.ttc"
_fc = {}


def font(size, bold=False):
    k = (int(size), bold)
    if k not in _fc:
        _fc[k] = ImageFont.truetype(FONT_B if bold else FONT_R, int(size))
    return _fc[k]


def tokens(s):
    """把 ^{..} / _{..} 切成 [(文本, 模式)]，模式 0 正常 1 上标 2 下标。"""
    out, i, buf = [], 0, ""
    while i < len(s):
        if s[i] in "^_" and i + 1 < len(s) and s[i + 1] == "{":
            j = s.find("}", i + 2)
            if j > 0:
                if buf:
                    out.append((buf, 0))
                    buf = ""
                out.append((s[i + 2:j], 1 if s[i] == "^" else 2))
                i = j + 1
                continue
        buf += s[i]
        i += 1
    if buf:
        out.append((buf, 0))
    return out



_TEX = {
    "pm": "±", "mp": "∓", "times": "×", "cdot": "·", "div": "÷",
    "le": "≤", "leq": "≤", "ge": "≥", "geq": "≥", "ne": "≠", "neq": "≠",
    "approx": "≈", "equiv": "≡", "propto": "∝", "infty": "∞",
    "circ": "°", "degree": "°", "prime": "′",
    "alpha": "α", "beta": "β", "gamma": "γ", "delta": "δ", "epsilon": "ε",
    "theta": "θ", "lambda": "λ", "mu": "μ", "pi": "π", "rho": "ρ",
    "sigma": "σ", "tau": "τ", "phi": "φ", "omega": "ω", "Delta": "Δ",
    "Omega": "Ω", "Sigma": "Σ", "sum": "∑", "int": "∫",
    "to": "→", "rightarrow": "→", "leftarrow": "←", "leftrightarrow": "↔",
    "Rightarrow": "⇒", "Leftrightarrow": "⇔",
    "in": "∈", "notin": "∉", "subset": "⊂", "cup": "∪", "cap": "∩",
    "angle": "∠", "perp": "⊥", "parallel": "∥", "triangle": "△", "odot": "⊙",
    "because": "∵", "therefore": "∴", "ldots": "…", "cdots": "…",
    "quad": "  ", "qquad": "    ", "text": "", "mathrm": "", "mathbf": "",
}


def _tex_sym(s):
    """把 \\pm 之类的命令换成 Unicode 符号（结构命令 frac/sqrt 另行处理）。"""
    def rep(m):
        k = m.group(1)
        return _TEX.get(k, m.group(0))
    return re.sub(r"\\([A-Za-z]+)", rep, s)


# ================= 结构式排版：\frac{}{} 与 \sqrt{} =================
def _read_arg(s, pos):
    n = len(s)
    if pos >= n:
        return "", pos
    if s[pos] == '{':
        depth, i, sb = 0, pos, []
        while i < n:
            c = s[i]
            if c == '{':
                depth += 1
                if depth > 1:
                    sb.append(c)
            elif c == '}':
                depth -= 1
                if depth == 0:
                    i += 1
                    break
                sb.append(c)
            else:
                sb.append(c)
            i += 1
        return "".join(sb), i
    return s[pos], pos + 1


def parse_struct(s):
    """元素：('t',文字) ('sup',内层) ('sub',内层) ('frac',分子,分母) ('sqrt',内层)"""
    out, buf, i, n = [], "", 0, len(s)
    while i < n:
        if s.startswith("\\dfrac", i) or s.startswith("\\tfrac", i):
            a, p1 = _read_arg(s, i + 6)
            b, p2 = _read_arg(s, p1)
            if buf:
                out.append(("t", buf)); buf = ""
            out.append(("frac", a, b)); i = p2
        elif s.startswith("\\frac", i):
            a, p1 = _read_arg(s, i + 5)
            b, p2 = _read_arg(s, p1)
            if buf:
                out.append(("t", buf)); buf = ""
            out.append(("frac", a, b)); i = p2
        elif s.startswith("\\sqrt", i):
            j = i + 5
            if j < n and s[j] == '[':
                e = s.find(']', j)
                if e > 0:
                    j = e + 1
            a, p1 = _read_arg(s, j)
            if buf:
                out.append(("t", buf)); buf = ""
            out.append(("sqrt", a)); i = p1
        elif s[i] in "^_" and i + 1 < n:
            a, p1 = _read_arg(s, i + 1)
            if buf:
                out.append(("t", buf)); buf = ""
            out.append(("sup" if s[i] == "^" else "sub", a)); i = p1
        else:
            buf += s[i]; i += 1
    if buf:
        out.append(("t", buf))
    return out


def _m_seq(els, size, bold, d):
    w = asc = desc = 0.0
    for el in els:
        ew, ea, ed = _m_el(el, size, bold, d)
        w += ew
        asc = max(asc, ea)
        desc = max(desc, ed)
    return w, asc, desc


def _m_el(el, size, bold, d):
    k = el[0]
    if k == "t":
        f = font(size, bold)
        bb = d.textbbox((0, 0), el[1] or " ", font=f, anchor="ls")
        return d.textlength(el[1], font=f), -bb[1], bb[3]
    if k in ("sup", "sub"):
        s2 = size * 0.70
        w, a1, d1 = _m_seq(parse_struct(el[1]), s2, bold, d)
        if k == "sup":
            sh = size * 0.34
            return w, a1 + sh, max(0.0, d1 - sh)
        sh = size * 0.20
        return w, max(0.0, a1 - sh), d1 + sh
    if k == "frac":
        s2 = size * 0.74
        aw, aa, _ = _m_seq(parse_struct(el[1]), s2, bold, d)
        bw, ba, bd = _m_seq(parse_struct(el[2]), s2, bold, d)
        gap, bar = s2 * 0.14, s2 * 0.30
        return max(aw, bw) + s2 * 0.26, bar + gap + aa, max(ba + bd + gap - bar, s2 * 0.18)
    if k == "sqrt":
        iw, ia, idn = _m_seq(parse_struct(el[1]), size, bold, d)
        g = size * 0.32
        return g + iw + size * 0.12, ia + size * 0.18, max(idn, size * 0.06) + size * 0.07
    return 0.0, 0.0, 0.0


def _d_seq(d, els, x, base, size, bold, color):
    cur = x
    for el in els:
        ew, _, _ = _m_el(el, size, bold, d)
        _d_el(d, el, cur, base, size, bold, color)
        cur += ew
    return cur - x


def _d_seq_center(d, els, cx, base, size, bold, color):
    w, _, _ = _m_seq(els, size, bold, d)
    _d_seq(d, els, cx - w / 2.0, base, size, bold, color)


def _d_el(d, el, x, base, size, bold, color):
    k = el[0]
    if k == "t":
        d.text((x, base), el[1], font=font(size, bold), fill=color, anchor="ls")
    elif k == "sup":
        d.text((x, base - size * 0.34), el[1], font=font(size * 0.70, bold), fill=color, anchor="ls")
    elif k == "sub":
        d.text((x, base + size * 0.20), el[1], font=font(size * 0.70, bold), fill=color, anchor="ls")
    elif k == "frac":
        s2 = size * 0.74
        aw, _, _ = _m_seq(parse_struct(el[1]), s2, bold, d)
        bw, ba, _ = _m_seq(parse_struct(el[2]), s2, bold, d)
        w = max(aw, bw) + s2 * 0.26
        cx = x + w / 2.0
        gap, bar = s2 * 0.14, s2 * 0.30
        bar_y = base - bar
        _d_seq_center(d, parse_struct(el[1]), cx, bar_y - gap, s2, bold, color)
        d.line([(cx - max(aw, bw) / 2 - s2 * 0.10, bar_y),
                (cx + max(aw, bw) / 2 + s2 * 0.10, bar_y)],
               fill=color, width=max(1, int(round(s2 * 0.07))))
        _d_seq_center(d, parse_struct(el[2]), cx, bar_y + gap + ba, s2, bold, color)
    elif k == "sqrt":
        iw, ia, idn = _m_seq(parse_struct(el[1]), size, bold, d)
        g = size * 0.32
        _d_seq(d, parse_struct(el[1]), x + g, base, size, bold, color)
        top = base - ia - size * 0.12
        bot = base + max(idn, size * 0.06) + size * 0.08
        d.line([(x + g * 0.78, bot), (x + g * 0.12, top + size * 0.10),
                (x + g * 0.44, top), (x + g + iw + size * 0.08, top)],
               fill=color, width=max(1, int(round(size * 0.06))))

class C(object):
    def __init__(self, title, sub=""):
        self.im = Image.new("RGB", (W, H), "white")
        self.d = ImageDraw.Draw(self.im)
        self.d.rectangle([1, 1, W - 2, H - 2], outline=(205, 205, 205), width=2)
        self.t(W // 2, 30, title, 31, True, (22, 44, 92), "mm")
        if sub:
            self.t(W // 2, 66, sub, 19, False, (120, 120, 120), "mm")

    # ---- 基础 ----
    def _struct(self, x, y, s, size, bold, color, anchor):
        els = parse_struct(_tex_sym(s))
        w, asc, desc = _m_seq(els, size, bold, self.d)
        h = anchor[0] if anchor else "l"
        v = anchor[1] if len(anchor) > 1 else "a"
        sx = x - w / 2.0 if h == "m" else (x - w if h == "r" else x)
        if v == "t":
            base = y + asc
        elif v == "b":
            base = y - desc
        elif v == "m":
            base = y + (asc - desc) / 2.0
        else:
            base = y
        _d_seq(self.d, els, sx, base, size, bold, color)
        return w

    def t(self, x, y, s, size=20, bold=False, color=(30, 30, 30), anchor="la"):
        if "\\" in s:
            s = _tex_sym(s)
        if "\\frac" in s or "\\sqrt" in s:
            return self._struct(x, y, s, size, bold, color, anchor)
        parts = tokens(s)
        if len(parts) == 1 and parts[0][1] == 0:
            self.d.text((x, y), s, font=font(size, bold), fill=color, anchor=anchor)
            return
        # 含上下标：逐段绘制，水平按 l/m/r 定位，垂直按 t/m/b 定位
        segs = []
        total_w = 0
        top = bottom = None
        for txt, mode in parts:
            sz = size * (0.70 if mode else 1.0)
            f = font(sz, bold)
            w = self.d.textlength(txt, font=f)
            dy = -size * 0.34 if mode == 1 else (size * 0.18 if mode == 2 else 0)
            bb = self.d.textbbox((0, dy), txt, font=f, anchor="ls")
            segs.append((txt, f, w, dy, bb))
            total_w += w
            top = bb[1] if top is None else min(top, bb[1])
            bottom = bb[3] if bottom is None else max(bottom, bb[3])
        h = anchor[0] if anchor and anchor[0] in "lmr" else "l"
        v = anchor[1] if anchor and len(anchor) > 1 else "a"
        if h == "m":
            cx = x - total_w / 2
        elif h == "r":
            cx = x - total_w
        else:
            cx = x
        if v == "t":
            base_y = y - top
        elif v == "b":
            base_y = y - bottom
        elif v == "m":
            base_y = y - (top + bottom) / 2
        else:
            base_y = y
        for txt, f, w, dy, bb in segs:
            self.d.text((cx, base_y + dy), txt, font=f, fill=color, anchor="ls")
            cx += w
        return total_w

    def rich(self, x, y, s, size=20, bold=False, color=(30, 30, 30), anchor="ma"):
        """带上下标/分数/根号的文本，anchor 支持 ma / la。"""
        if "\\" in s:
            s = _tex_sym(s)
        if "\\frac" in s or "\\sqrt" in s:
            return self._struct(x, y, s, size, bold, color, anchor)
        parts = tokens(s)
        widths = []
        for txt, mode in parts:
            sz = size * (0.70 if mode else 1.0)
            widths.append(self.d.textlength(txt, font=font(sz, bold)))
        total = sum(widths)
        cx = x - total / 2 if anchor == "ma" else x
        for (txt, mode), w in zip(parts, widths):
            sz = size * (0.70 if mode else 1.0)
            dy = -size * 0.34 if mode == 1 else (size * 0.18 if mode == 2 else 0)
            self.d.text((cx, y + dy), txt, font=font(sz, bold), fill=color, anchor="ls")
            cx += w
        return total

    def line(self, x1, y1, x2, y2, w=3, color=(40, 40, 40)):
        self.d.line([x1, y1, x2, y2], fill=color, width=w)

    def dash(self, x1, y1, x2, y2, w=2, color=(120, 120, 120)):
        n = 26
        for i in range(n):
            if i % 2:
                continue
            xa = x1 + (x2 - x1) * i / n
            ya = y1 + (y2 - y1) * i / n
            xb = x1 + (x2 - x1) * (i + 1) / n
            yb = y1 + (y2 - y1) * (i + 1) / n
            self.d.line([xa, ya, xb, yb], fill=color, width=w)

    def arrow(self, x1, y1, x2, y2, w=3, color=(40, 40, 40), head=13):
        self.line(x1, y1, x2, y2, w, color)
        ang = math.atan2(y2 - y1, x2 - x1)
        for s in (0.42, -0.42):
            self.line(x2, y2, x2 - head * math.cos(ang + s), y2 - head * math.sin(ang + s), w, color)

    def poly(self, pts, fill=None, outline=(40, 40, 40), w=3):
        self.d.polygon(pts, fill=fill, outline=outline)
        if w > 1:
            self.d.line(pts + [pts[0]], fill=outline, width=w)

    def circle(self, cx, cy, r, fill=None, outline=(40, 40, 40), w=3):
        self.d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=fill, outline=outline, width=w)

    def rect(self, x1, y1, x2, y2, fill=None, outline=(40, 40, 40), w=3, r=6):
        self.d.rounded_rectangle([x1, y1, x2, y2], radius=r, fill=fill, outline=outline, width=w)

    def axes(self, x0, y0, x1, y1, lab_x="x", lab_y="y"):
        self.arrow(x0, y0, x1, y0, 2, (60, 60, 60), 11)
        self.arrow(x0, y0, x0, y1, 2, (60, 60, 60), 11)
        self.t(x1 - 8, y0 + 10, lab_x, 18, False, (60, 60, 60), "mt")
        self.t(x0 - 12, y1 + 2, lab_y, 18, False, (60, 60, 60), "rt")

    def fn_curve(self, fn, xmin, xmax, px, py, n=240, color=(200, 60, 60), w=4):
        """fn(v) -> 数学 y；px(v)->像素x，py(v)->像素y。"""
        pts = []
        for i in range(n + 1):
            v = xmin + (xmax - xmin) * i / n
            try:
                y = fn(v)
            except Exception:
                continue
            if y is None or abs(y) > 1e6:
                continue
            pts.append((px(v), py(y)))
        for i in range(1, len(pts)):
            if abs(pts[i][1] - pts[i - 1][1]) < 120:
                self.d.line([pts[i - 1], pts[i]], fill=color, width=w)

    def note(self, s, y=None):
        self.rich(W // 2, y or H - 40, s, 21, False, (40, 40, 70), "ma")

    def save(self, name):
        if not os.path.isdir(OUT):
            os.makedirs(OUT)
        p = os.path.join(OUT, name + ".png")
        self.im.convert("P", palette=Image.ADAPTIVE, colors=64).save(p, optimize=True)
        return p, os.path.getsize(p)

# ===================== 颜色与常用常量 =====================
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


def save(c, name, acc=None):
    p, size = c.save(name)
    if acc is not None:
        acc.append((name, size))
    print("  %-14s %6.1f KB" % (name, size / 1024.0))
    return p, size
