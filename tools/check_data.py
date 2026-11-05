# -*- coding: utf-8 -*-
"""知识点数据体检：重复编号 / 重复标题 / 空内容 / 标记与 LaTeX 显示隐患。
只读，不改数据。用法（在仓库根跑）：python tools/check_data.py"""
import json, os, re, sys, collections

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))   # tools/check_data.py -> 仓库根
DATA = os.path.join(ROOT, "app", "src", "main", "assets", "data")

# ---- 渲染器认识的 LaTeX 命令（与 MainActivity 的 LX / LX_ACC / LX_WRAP 保持一致）----
LX = """alpha beta gamma delta Delta epsilon varepsilon zeta eta theta Theta iota kappa lambda Lambda
mu nu xi pi Pi rho sigma Sigma tau upsilon phi varphi Phi chi psi Psi omega Omega times cdot div pm mp
ast star circ bullet oplus otimes sum prod int oint partial nabla hbar ell infty le leq ge geq ne neq
approx equiv sim simeq propto ll gg in notin ni subset subseteq subsetneqq supset supseteq cup cap
emptyset varnothing complement forall exists nexists neg land lor to rightarrow leftarrow leftrightarrow
Rightarrow Leftarrow Leftrightarrow implies iff longlongrightarrow longleftarrow uparrow downarrow
nearrow searrow mapsto angle perp parallel therefore because degree prime ldots dots cdots vdots ddots
sin cos tan cot sec csc arcsin arccos arctan log ln lg lim exp max min sup inf det gcd quad qquad""".split()

LX_ACC = "overline bar hat widehat tilde widetilde dot ddot underline".split()
LX_WRAP = "text textrm mathrm mathbf mathit operatorname mbox".split()
NOARG = ("left right displaystyle textstyle limits nolimits big Big bigl bigr Bigl Bigr "
         "begin end frac dfrac tfrac sqrt vec overrightarrow").split()
KNOWN = set(LX) | set(LX_ACC) | set(LX_WRAP) | set(NOARG)

SUBJ_CODE = {"chinese":"CH","math":"MA","english":"EN","physics":"PH","chem":"CM",
             "bio":"BI","politics":"PL","history":"HI","geography":"GE"}

SUP_MAP = {'⁰':'0','¹':'1','²':'2','³':'3','⁴':'4','⁵':'5','⁶':'6','⁷':'7','⁸':'8','⁹':'9','⁺':'+','⁻':'-'}

problems = collections.OrderedDict()
def add(kind, msg):
    problems.setdefault(kind, []).append(msg)

def braces_ok(s):
    """忽略 \\{ \\} 后的花括号配平"""
    d = 0
    i = 0
    while i < len(s):
        c = s[i]
        if c == '\\':
            i += 2
            continue
        if c == '{':
            d += 1
        elif c == '}':
            d -= 1
            if d < 0:
                return False
        i += 1
    return d == 0

def check_text(where, s):
    if not s:
        return
    # 1) 加粗标记必须成对
    if s.count('**') % 2 == 1:
        add('加粗标记未配对', '%s：%s' % (where, s[:70]))
        return  # 后面判定不稳，跳过
    # 2) 花括号配平
    if not braces_ok(s):
        add('花括号不配平', '%s：%s' % (where, s[:70]))
    # 3) 不认识的 LaTeX 命令（会原样显示成 \xxx）
    for m in re.finditer(r'\\([A-Za-z]+)', s):
        cmd = m.group(1)
        if cmd not in KNOWN:
            add('未支持的 LaTeX 命令', '%s：\\%s ｜ %s' % (where, cmd, s[:60]))
    # 4) 裸 ^ 或 _ 后面没跟 { 也没跟单个字符（渲染成上/下标失败）
    for m in re.finditer(r'(?<!\\)[\^_](?!\{|\w)', s):
        add('上/下标写法异常', '%s：%s' % (where, s[:70]))
        break
    # 5) \frac 必须有两个参数
    for m in re.finditer(r'\\(?:frac|dfrac|tfrac)\s*\{', s):
        i = m.end() - 1
        cnt = 0
        n1 = n2 = False
        j, d = i, 0
        while j < len(s):
            if s[j] == '\\':
                j += 2; continue
            if s[j] == '{': d += 1
            elif s[j] == '}':
                d -= 1
                if d == 0:
                    cnt += 1
                    j += 1
                    break
            j += 1
        n1 = cnt == 1
        d = 0
        while j < len(s):
            if s[j] == '\\':
                j += 2; continue
            if s[j] == '{': d += 1
            elif s[j] == '}':
                d -= 1
                if d == 0:
                    n2 = True
                    break
            j += 1
        if not (n1 and n2):
            add('frac 参数不完整', '%s：%s' % (where, s[:70]))
    # 6) 残留未转换的上标字符（说明 latexify 没跑到或手写了奇怪字符）
    if re.search(r'[⁰¹²³⁴⁵⁶⁷⁸⁹]', s):
        add('残留上标字符', '%s：%s' % (where, s[:70]))
    # 7) 连续空行 / 行尾反斜杠
    if s.rstrip().endswith('\\'):
        add('行尾多余反斜杠', '%s：%s' % (where, s[-40:]))
    if '\n\n\n' in s:
        add('连续空行', '%s' % where)

def norm(t):
    """标题归一化：去空格/标点/LaTeX，用来找近似重复"""
    t = re.sub(r'\\[A-Za-z]+', '', t)
    t = re.sub(r'[{}$\\^*\s，。、（）()·:：;；,.\-—－_]', '', t)
    return t.lower()

files = sorted(f for f in os.listdir(DATA) if f.endswith('.json'))
all_ids = {}
all_titles = collections.defaultdict(list)   # 科目|归一化标题 -> [(父路径, 标题)]
groups = 0
leaves = 0

# v3 编号：分组 <科目码>-<序号>…；叶子 <日期YYMMDD>-<科目码>-<序号>…（日期在最前）
# 正文里的注音是「字（拼音）」，译文页的原文行不带拼音（读音放在注释里），
# 比对时两边都先剥掉拼音括号，只比字句本身。
PINYIN_RE = re.compile(r'（[a-zāáǎàēéěèīíǐìōóǒòūúǔùǖǘǚǜü ]+）')


def strip_pinyin(t):
    return PINYIN_RE.sub('', t or '')


GROUP_RE = re.compile(r'^[A-Z]{2}(-\d+)+$')
LEAF_RE = re.compile(r'^(\d{6})-([A-Z]{2})(-\d+)+$')


def walk(nodes, subj, path):
    """递归遍历 nodes：有 children 是分组，否则是叶子（与 App 的判断完全一致）。"""
    global groups, leaves
    seen = {}
    for n in nodes:
        name = (n.get('name') or '').strip()
        nid = n.get('id', '')
        kids = n.get('children') or []
        here = path + [name or '(无标题)']
        where = '%s › %s' % (subj, ' › '.join(x[:24] for x in here))

        if not name:
            add('标题为空', where)
        if not nid:
            add('缺编号', where)
        elif nid in all_ids:
            add('编号重复', '%s  与  %s' % (where, all_ids[nid]))
        else:
            all_ids[nid] = where

        if kids:                                   # ---------- 分组 ----------
            groups += 1
            if not GROUP_RE.match(nid or ''):
                add('分组编号格式不对', '%s：%s' % (where, nid))
            elif not nid.startswith(SUBJ_CODE.get(subj, 'XX')):
                add('编号科目码不符', '%s：%s' % (where, nid))
            if (n.get('body') or '').strip():
                add('分组带了正文（界面不会显示）', where)
            nk = norm(name)
            if nk:
                if nk in seen:
                    add('同一层标题重复', where)
                seen[nk] = 1
                all_titles[nk + '||' + subj].append((tuple(path), name))
            walk(kids, subj, here)
        else:                                      # ---------- 叶子 ----------
            leaves += 1
            dd = n.get('d', '')
            m = LEAF_RE.match(nid or '')
            if not m:
                add('叶子编号格式不对', '%s：%s' % (where, nid))
            else:
                if m.group(1) != dd:
                    add('编号日期与 d 不一致', '%s：id=%s d=%s' % (where, nid, dd))
                if m.group(2) != SUBJ_CODE.get(subj, 'XX'):
                    add('编号科目码不符', '%s：%s' % (where, nid))

            # 星级：0 = 不标星（新增内容常用），1~3 = 真实分级
            st = n.get('star', None)
            if st not in (0, 1, 2, 3):
                add('星级异常', '%s：star=%r' % (where, st))

            body = n.get('body') or ''
            daily = n.get('daily') or ''
            if not body.strip() and not daily:
                add('正文为空', where)
            check_text(where, body)
            check_text(where + '（标题）', name)

            # 原文页不许出现圈号角标：角标只属于译文页那一行原文（课本习惯）
            if re.search(r'\^\{[\u2460-\u2473]', body):
                add('正文里有圈号角标（应只在译文页的原文行）', where)

            # 附加内容块：例题、译文共用同一套结构，这里只校验结构完整性
            for b in (n.get('extra') or []):
                bn = b.get('name') or ''
                if not bn.strip():
                    add('附加块缺 name（按钮文案来自这里）', where)
                items = b.get('items') or []
                if not items:
                    add('附加块没有内容', '%s：%s' % (where, bn))
                for it in items:
                    lines = it.get('lines') or []
                    if not lines:
                        add('附加块有空组', '%s：%s' % (where, bn))
                    for ln in lines:
                        if ln.get('tone') not in ('head', 'body', 'note'):
                            add('附加行 tone 非法', '%s：%s / %r' % (where, bn, ln.get('tone')))
                        check_text('%s（%s·%s）' % (where, bn, ln.get('tag') or '-'),
                                   ln.get('text') or '')

                # 译文块：每条原文去掉角标后，必须能在正文里按顺序找到，防止切句漏句/错位
                if '译文' in bn:
                    pos = 0
                    body_cmp = strip_pinyin(body)
                    for it in items:
                        lines = it.get('lines') or []
                        src = ''
                        for ln in lines:
                            if ln.get('tag') == '原文':
                                src = ln.get('text') or ''
                                break
                        if not src:
                            add('译文组缺原文行', '%s：%s' % (where, bn))
                            continue
                        plain = strip_pinyin(re.sub(r'\^\{[^}]*\}', '', src).strip())
                        k = body_cmp.find(plain, pos)
                        if k < 0:
                            add('译文原文对不上正文（顺序或字句不符）',
                                '%s：%s' % (where, plain[:30]))
                        else:
                            pos = k + len(plain)


for fn in files:
    subj = fn[:-5]
    if subj == 'order':
        continue
    d = json.load(open(os.path.join(DATA, fn), encoding='utf-8'))
    d = d.get('subject') or d
    nodes = d.get('nodes')
    if nodes is None:
        add('数据格式不对', '%s：没有 nodes 字段（是不是还留着旧的三层格式？）' % subj)
        continue
    walk(nodes, subj, [])

# 同一科目里同一个标题出现在不同父路径下：可能是重复录入
for key, lst in all_titles.items():
    if len(lst) > 1:
        subj = key.split('||')[1]
        parents = set(x[0] for x in lst)
        if len(parents) > 1:
            add('同科目内重复标题', '%s：「%s」出现在 %d 个不同位置'
                % (subj, lst[0][1], len(parents)))

print('分组节点：%d' % groups)
print('叶子节点：%d' % leaves)
print('编号总数：%d' % len(all_ids))
if not problems:
    print('\n未发现问题。')
else:
    print('\n发现问题：')
    for k, v in problems.items():
        print('\n【%s】%d 处' % (k, len(v)))
        for x in v[:40]:
            print('  -', x)
        if len(v) > 40:
            print('  ... 其余 %d 处省略' % (len(v) - 40))
