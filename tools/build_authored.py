# -*- coding: utf-8 -*-
"""把 tools/subjects/<科目>.py 的知识点树合成为 App 用的分科数据（assets/data/*.json）。
源文件按科目分文件，层级就是缩进：章 → 节 → 知识点。"""
import json, os, importlib.util, sys, re, difflib

TOOLS = os.path.dirname(os.path.abspath(__file__))
DATA_VERSION = 15
sys.path.insert(0, TOOLS)

# ---------- LaTeX 标准化（数据层，UI 只负责渲染，二者解耦） ----------
SUP_MAP = {'⁰': '0', '¹': '1', '²': '2', '³': '3', '⁴': '4',
           '⁵': '5', '⁶': '6', '⁷': '7', '⁸': '8', '⁹': '9',
           '⁺': '+', '⁻': '-'}


def latexify(s):
    """把纯文本公式规范成 LaTeX 写法：上标字符→^{}，字母+数字→_{}。
    幂等：已写成 _{}/^{} 的不会被二次处理。"""
    if not s:
        return s
    # 1) 连续上标字符 → ^{...}
    s = re.sub(r'[⁰¹²³⁴⁵⁶⁷⁸⁹⁺⁻]+',
               lambda m: '^{' + ''.join(SUP_MAP[c] for c in m.group(0)) + '}', s)
    # 2) 字母串+数字 → 下标（化学式 H2O、物理 mv2、坐标 x1 等）
    s = re.sub(r'(?<![A-Za-z\\_])([A-Za-z]+)(\d+)',
               lambda m: m.group(1) + '_{' + m.group(2) + '}', s)
    # 3) 右括号+数字 → 下标（Cu(OH)2、Ca(ClO)2、Fe(OH)3）
    s = re.sub(r'\)(\d+)', lambda m: ')_{' + m.group(1) + '}', s)
    return s

def load_module(fname):
    path = os.path.join(TOOLS, fname)
    spec = importlib.util.spec_from_file_location("mod_" + fname.replace(".", "_"), path)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod

subjects_order = ["chinese", "math", "english", "physics", "chem", "bio", "politics", "history", "geography"]

def importance(t):
    # 重要性：3=核心必考，2=重点掌握，1=了解/拓展
    s = t
    if any(k in s for k in ["定义", "概念", "性质", "定理", "公式", "法则", "图象", "公理", "运算", "计算", "判定", "特征"]):
        return 3
    if any(k in s for k in ["应用", "技巧", "方法", "综合", "思想", "策略", "模型", "求解", "解题", "识图", "辨析"]):
        return 2
    if any(k in s for k in ["拓展", "探究", "创新", "常识", "了解", "史料", "意义", "背景"]):
        return 1
    return 2

def to_item(it):
    body = latexify(it.get("body", ""))
    star = it.get("lvl", None)
    if star is None:
        star = importance(it["t"])
    return {"t": latexify(it["t"]), "star": star,
            "sec": [{"h": "", "c": "K", "body": body}]}

def to_child(ch):
    items = [to_item(i) for i in ch.get("items", [])]
    return {"type": ch.get("type", "module"),
            "name": latexify(ch.get("name", "")), "items": items}

def to_block(b):
    return {"name": latexify(b["name"]), "children": [to_child(c) for c in b.get("children", [])]}

def to_subject(key, data):
    return {"s": key, "name": data["name"], "blocks": [to_block(b) for b in data["blocks"]]}


def load_subject(key):
    """读取 tools/subjects/<科目>.py，缩进树即 章 → 节 → 知识点。"""
    mod = load_module("subjects/" + key + ".py")
    name = getattr(mod, "NAME", key)
    tree = getattr(mod, "TREE", {})
    blocks = []
    for bname, secs in tree.items():
        children = []
        for sname, items in secs.items():
            children.append({"type": "module", "name": sname,
                             "items": [dict(x) for x in items]})
        blocks.append({"name": bname, "children": children})
    return {"name": name, "blocks": blocks}


subjects = []
extra_total = 0
for key in subjects_order:
    fp = os.path.join(TOOLS, "subjects", key + ".py")
    if not os.path.exists(fp):
        print("! 缺少科目文件:", fp)
        continue
    subjects.append(to_subject(key, load_subject(key)))

# 按固定科目顺序排列
by_key = {s["s"]: s for s in subjects}
ordered = [by_key[k] for k in subjects_order if k in by_key]

# ---------- 重复知识点合并（同科目内） ----------
def _norm_title(t):
    """标题归一化：去掉空白/标点/虚词，用于判断「原电池工作原理」与「原电池的工作原理」为同一条。"""
    t = re.sub(r"[\s（）()【】\[\]“”\"'，,。、：:；;！!？?—\-·]", "", t)
    for w in ("的", "与", "和", "及", "之", "其"):
        t = t.replace(w, "")
    return t


def _plain(s):
    return s.replace("**", "").replace("\n", "")


def _sim(a, b):
    if not a or not b:
        return 0.0
    return difflib.SequenceMatcher(None, a, b).ratio()


def _cset(s):
    """取汉字/字母/数字集合，用于判断两句话是否在讲同一件事。"""
    return set(re.findall(r"[\u4e00-\u9fa5A-Za-z0-9]", s))


def _jac(a, b):
    A, B = _cset(a), _cset(b)
    if not A or not B:
        return 0.0
    return len(A & B) / len(A | B)


def _merge_items(base, other):
    """把 other 中 base 没有的句子并入 base：序列相似或字集重叠的句子视为重复；
    若新句明显更具体（更长）则替换旧句，最后最多保留 7 行。"""
    bl = [x for x in base["sec"][0]["body"].split("\n") if x.strip()]
    for line in other["sec"][0]["body"].split("\n"):
        core = line.replace("**", "").strip()
        if not core:
            continue
        newline = line.strip()
        dup = -1
        for k, x in enumerate(bl):
            xc = x.replace("**", "").strip()
            if _sim(core, xc) >= 0.68 or _jac(core, xc) >= 0.55:
                dup = k
                break
        if dup >= 0:
            oldc = bl[dup].replace("**", "").strip()
            if len(core) > len(oldc) + 6 and _jac(core, oldc) >= 0.60:
                bl[dup] = newline          # 用更具体的新句替换笼统旧句
            continue
        bl.append(newline)
    base["sec"][0]["body"] = "\n".join(bl[:7])
    base["star"] = max(base.get("star", 2), other.get("star", 2))


# 人工逐对确认过的重复知识点：(科目, 保留标题, 合并掉标题)
MANUAL_PAIRS = [
    ("chem", "化学反应速率的计算", "化学反应速率的表示方法"),
    ("chem", "蒸馏与分馏", "蒸馏操作"),
    ("physics", "变压器的能量关系", "变压器的功率关系"),
    ("politics", "社会存在与社会意识的辩证关系", "社会存在决定社会意识"),
    ("english", "完形填空的答题策略", "完形填空的解题步骤"),
    ("english", "读后续写的情节设计", "读后续写的情节构思"),
    ("politics", "实践的特点", "实践的特征"),
    ("chinese", "科普文的阅读方法", "科普文的阅读要点"),
    ("chinese", "古代文学的高峰成就", "中国古代文学的高峰"),
    ("english", "申请信的写法", "申请信的写作"),
    ("english", "感谢信的写法", "感谢信的写作要点"),
    ("geography", "自然带的判断", "自然带的判读"),
    ("geography", "水循环的类型与意义", "水循环的类型与环节"),
    ("chinese", "文言文翻译的方法", "文言翻译的方法"),
    ("chinese", "论述类文本的结构分析", "论述类文本的论证结构"),
    ("bio", "种群增长的曲线", "种群数量变化曲线"),
    ("politics", "物质与意识的辩证关系", "物质与意识的关系"),
    ("geography", "中国的农业分布", "中国的农业区划"),
    ("math", "圆的方程", "圆的标准式与一般式互化"),
    ("chinese", "古代地理常识", "古代地名常识"),
    ("history", "明清进步思想的影响", "明清进步思想家的共同点"),
    ("politics", "国际关系的决定因素", "国际关系的基本形式"),
    ("english", "进行时态的用法", "现在进行时的用法"),
    ("chinese", "论述类文本的论证思路分析", "论述类文本的论证思路"),
    ("chinese", "论述类文本的论证结构分析", "论述类文本的结构分析"),
    ("chinese", "小说的人物形象分析", "小说的人物形象"),
    ("chinese", "小说环境描写的作用", "小说的环境描写"),
    ("chinese", "文言文断句的方法", "文言文断句"),
    ("chinese", "文言文断句", "文言文断句的技巧"),
    ("math", "函数最值的求法", "函数的最值"),
    ("math", "等差数列的判定与性质", "等差数列的判定"),
    ("math", "等比数列的性质与判定", "等比数列的性质"),
    ("math", "等比数列前 n 项和的性质", "等比数列前 n 项和"),
    ("math", "两直线的位置关系", "两条直线的位置关系"),
    ("math", "频率分布直方图的应用", "频率分布直方图"),
    ("math", "计数原理的综合", "计数原理的综合应用"),
    ("math", "二项式系数的性质与应用", "二项式系数的性质"),
    ("math", "二项式定理", "二项式定理的应用"),
    ("math", "二项式系数与项的系数", "二项式系数与系数"),
    ("english", "被动语态的用法", "被动语态"),
    ("english", "非谓语动词作定语", "非谓语动词作定语的区别"),
    ("english", "阅读理解的细节定位", "阅读理解的细节定位技巧"),
    ("english", "倡议书的写作要点", "倡议书的写作"),
    ("english", "读后续写的环境描写", "读后续写的环境描写作用"),
    ("physics", "共点力平衡", "共点力平衡的条件"),
    ("physics", "超重与失重", "超重与失重的判断"),
    ("physics", "平抛运动", "平抛运动的规律"),
    ("physics", "功能关系", "功能关系的应用"),
    ("physics", "机械能守恒的判断条件", "机械能守恒的判断"),
    ("physics", "弹性碰撞与完全非弹性碰撞的结论", "弹性碰撞与完全非弹性碰撞"),
    ("physics", "串并联电路", "串并联电路的特点"),
    ("physics", "电功与电热的区别", "电功与电热"),
    ("physics", "洛伦兹力", "洛伦兹力的特点"),
    ("physics", "右手定则的应用", "右手定则"),
    ("physics", "理想气体状态方程", "理想气体状态方程的应用"),
    ("physics", "简谐运动的能量特点", "简谐运动的能量"),
    ("physics", "波长、频率与波速", "波长、频率与波速的关系"),
    ("chem", "物质的量浓度的计算", "物质的量浓度"),
    ("chem", "胶体的性质与应用", "胶体的性质"),
    ("chem", "离子方程式的书写步骤", "离子方程式的书写"),
    ("chem", "碳酸钠与碳酸氢钠", "碳酸钠与碳酸氢钠的比较"),
    ("chem", "硅及其化合物的性质", "硅及其化合物"),
    ("chem", "二氧化硫", "二氧化硫的性质"),
    ("chem", "核外电子排布规律", "核外电子排布"),
    ("chem", "元素周期律", "元素周期律的实质"),
    ("chem", "化学键与物质类别的关系", "化学键与物质类别"),
    ("chem", "盖斯定律的应用", "盖斯定律"),
    ("chem", "平衡常数的计算", "平衡常数"),
    ("chem", "平衡常数", "平衡常数的意义"),
    ("chem", "pH 的计算", "pH 的计算方法"),
    ("chem", "电离平衡常数的应用", "电离平衡常数"),
    ("chem", "同分异构体的书写方法", "同分异构体的书写"),
    ("chem", "酯化反应与酯的水解", "酯化反应与水解"),
    ("chem", "晶体的类型与性质", "晶体的类型"),
    ("bio", "细胞分化", "细胞分化的特点"),
    ("bio", "细胞癌变", "细胞癌变的原因"),
    ("bio", "DNA 的复制", "DNA 复制的条件"),
    ("bio", "中心法则", "中心法则的内容"),
    ("bio", "分离定律的验证方法", "分离定律的验证"),
    ("bio", "人类遗传病", "人类遗传病的类型"),
    ("bio", "隔离与物种形成的关系", "隔离与物种形成"),
    ("bio", "群落的演替", "群落演替的类型"),
    ("bio", "种群密度的调查方法", "种群的密度调查"),
    ("bio", "生态系统的能量流动", "生态系统的能量流动特点"),
    ("bio", "显微镜的使用", "显微镜的使用方法"),
    ("politics", "企业的经营与发展", "企业的经营"),
    ("politics", "效率与公平的关系", "效率与公平"),
    ("politics", "政府的职能与作用", "政府的职能"),
    ("politics", "文化对人的影响的特点", "文化对人的影响"),
    ("politics", "文化继承与发展的关系", "文化的继承与发展"),
    ("politics", "文化创新", "文化创新的作用"),
    ("politics", "实践与认识", "实践与认识的关系"),
    ("history", "小农经济的特点与影响", "小农经济的特点"),
    ("history", "近代不平等条约的影响", "近代不平等条约"),
    ("history", "新文化运动", "新文化运动的意义"),
    ("history", "民族资本主义的发展历程", "民族资本主义的发展"),
    ("history", "人民代表大会制度", "人民代表大会制度的特点"),
    ("history", "和平共处五项原则", "和平共处五项原则的意义"),
    ("history", "改革开放", "改革开放的历程"),
    ("history", "经济体制改革", "经济体制改革的历程"),
    ("history", "家庭联产承包责任制", "家庭联产承包责任制的意义"),
    ("history", "罗马法的发展历程", "罗马法的发展"),
    ("history", "美国 1787 年宪法的原则", "美国 1787 年宪法"),
    ("history", "新航路开辟", "新航路开辟的影响"),
    ("history", "启蒙运动", "启蒙运动的影响"),
    ("history", "启蒙运动", "启蒙运动的性质"),
    ("history", "宗教改革", "宗教改革的影响"),
    ("geography", "热力环流", "热力环流的应用"),
    ("geography", "逆温现象及其影响", "逆温现象"),
    ("geography", "气候类型的判读", "气候类型的判读方法"),
    ("geography", "气候类型的判读", "气候类型的判读步骤"),
    ("geography", "气候类型的成因分析", "气候类型的成因"),
    ("geography", "锋面系统", "锋面系统的判断"),
    ("geography", "水循环的类型与意义", "水循环的类型"),
    ("geography", "自然带的分布", "自然带分布规律"),
    ("geography", "自然带的垂直分布", "自然带的垂直分布规律"),
    ("geography", "人口迁移", "人口迁移的影响"),
    ("geography", "城市功能区", "城市功能分区"),
    ("geography", "农业区位因素及其变化", "农业区位因素"),
    ("geography", "农业可持续发展措施", "农业的可持续发展"),
    ("geography", "中国的自然资源特点", "中国的自然资源"),
    ("geography", "农业可持续发展措施", "农业可持续发展"),
    ("geography", "气候类型的判读", "气候类型判读的步骤"),
    ("history", "启蒙运动", "启蒙运动的意义"),
    ("chinese", "小说的人物形象分析", "小说阅读的人物形象分析"),
    ("chinese", "语言表达的简明连贯得体", "语言表达连贯、得体"),
    ("chinese", "诗歌表达技巧的赏析", "诗歌的表达技巧"),
    ("chinese", "古代文学的意象", "古代文学的意象传统"),
    ("chinese", "古代文学的流派", "中国古代文学流派"),
    ("chinese", "文言文的特殊句式", "文言文的句式"),
    ("chinese", "文言虚词的作用", "文言虚词的用法"),
    ("chinese", "古代文人的称号", "古代文人的雅号"),
    ("chinese", "标点符号的用法", "标点符号的使用"),
    ("math", "裂项相消法", "裂项相消"),
    ("math", "倒序相加法", "倒序相加"),
    ("math", "直线与圆的位置关系与弦长", "直线与圆的位置关系"),
    ("math", "抛物线的标准方程与准线", "抛物线的标准方程"),
    ("math", "对数函数的图象与性质", "对数函数的性质"),
    ("math", "椭圆的定义与标准方程", "椭圆的定义与方程"),
    ("math", "双曲线的方程与渐近线", "双曲线的渐近线"),
    ("math", "复数的四则运算与共轭", "复数的四则运算"),
    ("math", "复数的几何意义与轨迹", "复数的几何意义"),
    ("math", "指数函数的图象与性质", "指数函数的性质"),
    ("math", "条件概率与乘法公式", "条件概率的公式"),
    ("math", "数量积的性质与应用", "数量积的应用"),
    ("math", "异面直线所成的角", "异面直线与角"),
    ("english", "名词性从句的语序与引导词", "名词性从句的引导词"),
    ("english", "定语从句的关系词", "定语从句的关系词选择"),
    ("english", "完形填空的答题策略", "完形填空解题策略"),
    ("english", "状语从句的类型与连词", "状语从句的连词"),
    ("english", "听力的场景词汇", "听力高频场景词汇"),
    ("english", "电子邮件的写法", "电子邮件的写作"),
    ("physics", "平均速度与瞬时速度", "平均速度与瞬时速度的辨析"),
    ("physics", "正弦式交变电流", "正弦式交变电流的产生"),
    ("physics", "变压器的制约关系", "理想变压器的制约关系"),
    ("physics", "曲线运动的条件与特点", "曲线运动的特点"),
    ("physics", "动量守恒的条件与近似", "动量守恒的条件"),
    ("physics", "楞次定律的推广含义", "楞次定律的推广"),
    ("physics", "核反应与核能", "核反应方程与核能"),
    ("chem", "电解池原理", "电解的原理"),
    ("chem", "化学反应速率的表示", "反应速率的表示"),
    ("chem", "分子间作用力与氢键", "分子间作用力"),
    ("chem", "影响反应速率的因素", "影响速率的因素"),
    ("chem", "影响化学平衡的因素", "影响平衡的因素"),
    ("chem", "热化学方程式的书写", "热化学方程式"),
    ("chem", "电子式的书写", "电子式书写规范"),
    ("chem", "阿伏加德罗常数判断的常见陷阱", "阿伏加德罗常数常考陷阱"),
    ("chem", "转化率的计算", "转化率与平衡计算"),
    ("bio", "内环境的理化性质与稳态", "内环境的理化性质"),
    ("bio", "肺炎双球菌转化实验", "肺炎双球菌的转化"),
    ("bio", "细胞的全能性与表达", "细胞的全能性"),
    ("bio", "细胞核的结构与功能", "细胞核的功能"),
    ("bio", "染色体的行为变化", "染色体的变化"),
    ("bio", "细胞膜的结构与功能", "细胞膜的功能"),
    ("politics", "我国的民族区域自治制度", "民族区域自治制度"),
    ("politics", "文化继承与发展的关系", "文化继承与文化发展的关系"),
    ("politics", "文化的多样性与传播", "文化多样性与文化传播"),
    ("politics", "影响消费水平的因素", "影响消费的因素"),
    ("politics", "文化创新的途径与方向", "文化创新的途径"),
    ("politics", "联系的普遍性、客观性与多样性", "联系的普遍性与客观性"),
    ("politics", "中华文化的特点", "中华文化的特征"),
    ("history", "新中国初期的外交方针", "新中国的外交方针"),
    ("history", "辛亥革命的历史意义", "辛亥革命的意义"),
    ("history", "明清君主专制的强化", "君主专制的强化"),
    ("history", "西周分封制与宗法制", "分封制与宗法制"),
    ("history", "战后资本主义新变化的表现", "二战后资本主义的新变化"),
    ("history", "中国古代科技成就", "古代科技成就"),
    ("history", "新中国初期的外交", "新中国的外交"),
    ("geography", "工业区位因素的变化", "工业区位的变化"),
    ("geography", "流域的综合治理", "流域综合治理的思路"),
    ("english", "定语从句的关系词", "定语从句关系词的选择"),
    ("physics", "交流电的图象与四值", "交流电的四值"),
    ("chinese", "诗歌的炼字", "诗歌的炼字炼句"),
    ("chinese", "病句的辨析与修改", "病句的辨析"),
    ("math", "和差角公式与二倍角", "和差角公式"),
    ("math", "求解析式", "由图象求解析式"),
    ("math", "复数的概念与运算", "复数的概念"),
    ("math", "椭圆的定义与标准方程", "椭圆的标准方程"),
    ("math", "双曲线的定义与方程", "双曲线的定义"),
    ("math", "二项式定理", "二项式定理的通项"),
    ("math", "独立重复试验与二项分布", "独立重复试验"),
    ("physics", "质点、参考系与坐标系", "质点与参考系"),
    ("physics", "加速度", "加速度的理解"),
    ("physics", "力的合成", "力的合成法则"),
    ("physics", "小船渡河", "小船渡河问题"),
    ("physics", "功能关系", "功能关系的理解"),
    ("physics", "电势与等势面", "等势面"),
    ("physics", "带电粒子的加速与偏转", "带电粒子的加速"),
    ("physics", "伏安法测电阻", "伏安法测电阻的误差"),
    ("physics", "洛伦兹力", "洛伦兹力不做功"),
    ("physics", "楞次定律", "楞次定律的理解"),
    ("physics", "磁通量的变化", "磁通量"),
    ("physics", "有效值", "有效值的计算"),
    ("physics", "简谐运动", "简谐运动的回复力"),
    ("physics", "核力与结合能", "结合能"),
    ("physics", "半衰期的计算", "半衰期"),
    ("chem", "溶液稀释与混合的计算", "溶液稀释的计算"),
    ("chem", "碳酸钠与碳酸氢钠", "碳酸钠与碳酸氢钠的鉴别"),
    ("chem", "燃烧热与中和热", "燃烧热"),
    ("chem", "醇、醛、羧酸的性质", "羧酸的性质"),
    ("chem", "气体的净化与收集", "气体的净化"),
    ("bio", "细胞中的无机盐与渗透压", "细胞中的无机盐"),
    ("bio", "酶的本质与特性", "酶的本质"),
    ("bio", "酶的高效性", "酶的高效性实验"),
    ("bio", "细胞周期", "细胞周期的阶段"),
    ("bio", "中心法则与基因对性状的控制", "基因对性状的控制"),
    ("bio", "伴性遗传的特点", "伴 X 隐性遗传的特点"),
    ("bio", "生态系统的稳定性与环境保护", "生态系统的稳定性"),
    ("bio", "生态系统的组成成分", "生态系统的组成"),
    ("politics", "宏观调控", "宏观调控的手段"),
    ("politics", "社会主义市场经济", "社会主义市场经济的基本特征"),
    ("politics", "公民的政治权利与义务", "公民的政治权利"),
    ("politics", "民主决策", "公民参与民主决策"),
    ("politics", "人民代表大会制度", "人民代表大会制度的优势"),
    ("politics", "认识的反复性与无限性", "认识的反复性"),
    ("politics", "发展的实质", "发展的实质与要求"),
    ("politics", "社会存在与社会意识", "社会存在与社会意识的辩证关系"),
    ("history", "重农抑商与海禁政策", "重农抑商政策"),
    ("history", "经济体制改革的推进过程", "经济体制改革"),
    ("geography", "地图三要素与比例尺", "地图的三要素"),
    ("geography", "地球自转的地理意义", "地球自转的意义"),
    ("geography", "自然带的地域分异规律", "地域分异的规律"),
    ("geography", "城市化的表现与问题", "城市化的表现"),
    ("geography", "世界主要气候与河流", "世界主要的河流"),
    ("math", "异面直线所成的角", "异面直线所成的角与距离"),
    ("politics", "人民代表大会制度", "我国的人民代表大会制度"),
]


# 人工定稿：合并后逐条精修过的正文（避免合并拼接产生语义重复）
BODY_OVERRIDES = {
    ("chem", "原电池工作原理"): (
        "把化学能转化为电能的装置。\n"
        "构成条件：两个活泼性不同的电极、电解质溶液、闭合回路、自发的氧化还原反应。\n"
        "负极：失电子、发生氧化反应、电子流出。\n"
        "正极：得电子、发生还原反应、电子流入。\n"
        "**电子由负极经外电路流向正极，电流方向相反**。\n"
        "离子在电解质溶液中定向移动，构成闭合回路。"
    ),
    ("english", "进行时态的用法"): (
        "现在进行时表示说话时正在进行的动作，结构为 be + 现在分词。\n"
        "也可表示按计划将要发生的动作，如 I am leaving tomorrow。\n"
        "过去进行时表示过去某一时刻正在进行的动作，常与 at that time 连用。\n"
        "现在完成进行时表示从过去持续到现在（还可能继续）的动作。\n"
        "**表示状态的动词（know、belong、like）一般不用进行时**。"
    ),
    ("chinese", "古代地名常识"): (
        "古代地理常识包括山河、地区称谓。\n"
        "「江」指长江，「河」指黄河。\n"
        "**「山东」指崤山以东，不是现在的山东省**。\n"
        "「关中」指函谷关以西，「塞外」指长城以北。\n"
        "「江表」「江东」指长江以南地区。\n"
        "掌握地理常识有助于理解文言文。"
    ),
    ("chinese", "中国古代文学的高峰"): (
        "古代文学的发展中有几个高峰时期。\n"
        "先秦诸子散文、汉赋是早期高峰。\n"
        "**唐诗、宋词、元曲是文学发展的鼎盛**。\n"
        "明清小说是又一个高峰。\n"
        "各时期体裁不同、风格各异，共同构成中国文学的辉煌。"
    ),
    ("politics", "国际关系与外交政策"): (
        "国际关系的决定因素是国家利益。\n"
        "**国家间的共同利益是合作的基础，利益对立是冲突的根源**。\n"
        "国际关系的基本形式有竞争、合作与冲突。\n"
        "国家力量是影响国际关系的重要因素。\n"
        "维护国家利益是主权国家对外活动的出发点和落脚点。"
    ),
    ("history", "明清之际的进步思想"): (
        "明清之际进步思想具有启蒙意义。\n"
        "代表人物有黄宗羲、顾炎武、王夫之。\n"
        "批判君主专制，主张限制君权。\n"
        "**提倡经世致用，反对空谈**。\n"
        "反映了资本主义萌芽的发展，为近代思想解放提供借鉴。"
    ),
    ("chinese", "常见文言实词与一词多义"): (
        "文言实词是读懂文言文的基础，重点掌握一词多义、古今异义、通假字、词类活用、偏义复词五类。\n一词多义：如「兵」有兵器、士兵、军队、战争等义，必须结合语境判断。\n古今异义：如「妻子」古指妻子和儿女，「走」古指跑，「去」古指离开。\n通假字：如「知」通「智」、「说」通「悦」，应按本字理解。\n词类活用：名词作动词、名词作状语、形容词作动词、使动用法、意动用法。\n**推断方法：字形推断、语境推断、结构对称推断、课文迁移、成语印证**。"
    ),
    ("chinese", "论点、论据与论证方法"): (
        "论点是作者对论述对象所持的见解和主张，常在标题、开头、结尾或段首出现。\n中心论点统摄全文，分论点从不同角度支撑中心论点，二者多为总分或递进关系。\n论据分事实论据（事例、数据、史实）和道理论据（名言、原理、定律）。\n事实论据要典型确凿，道理论据要权威贴切。\n答题思路：先找中心论点，再梳理分论点与论据类型，最后分析论据能否支撑论点。\n**注意区分论题与论点：论题是讨论的话题，论点是明确的判断句**。"
    ),
    ("chinese", "表达技巧与语言赏析"): (
        "小说常用表现手法：对比、衬托、象征、伏笔、照应、悬念、抑扬、虚实结合、以小见大。\n对比：把不同人物或情境并置，突出性格或主题。\n衬托：以次要人物或环境烘托主要人物，分正衬与反衬。\n象征：用具体事物寄托抽象意义，如《药》中的人血馒头象征愚昧。\n伏笔与照应：前文埋线、后文呼应，使情节严密。悬念制造疑问，增强可读性。\n抑扬：欲扬先抑或欲抑先扬，形成反差。\n**答题按「手法＋具体内容＋表达效果」三步作答**。"
    ),
    ("chinese", "诗歌意象的把握"): (
        "意象是寄托诗人情感的物象，是「意」与「象」的结合。\n常见意象有固定含义：月表思乡，柳表惜别，梅表高洁，菊表隐逸，雁表思念，梧桐表凄凉，杜鹃表哀怨。\n分析三步：找出意象、描绘画面、体会情感。\n多个意象叠加构成意境，意境是整体氛围，如雄浑、清幽、萧瑟、恬淡。\n同一意象在不同诗中含义可能不同，要结合语境判断，不能生搬硬套。\n**答题格式：描绘画面＋概括意境＋点明情感**。"
    ),
    ("chinese", "古代文人的称号"): (
        "古代文人常以字、号、籍贯、官职、谥号相称，阅读时需辨明。\n以字称：李白字太白，杜甫字子美，苏轼字子瞻。\n**以号称：青莲居士（李白）、少陵野老（杜甫）、东坡居士（苏轼）、易安居士（李清照）**。\n以籍贯称：柳河东（柳宗元）、韩昌黎（韩愈）、孟襄阳（孟浩然）。\n以官职称：杜工部（杜甫）、王右丞（王维）、白太傅（白居易）。\n以谥号称：范文正（范仲淹）、欧阳文忠（欧阳修）。"
    ),
    ("math", "圆锥曲线中的最值问题"): (
        "求圆锥曲线最值常用三种方法。\n定义法：利用椭圆、双曲线、抛物线的定义转化距离，如椭圆上点到两焦点距离之和为 2a。\n几何法：借助图形性质，如圆上点到定点距离的最值在连心线上取得。\n**代数法：设点坐标或直线方程，把目标化为函数，再用二次函数、基本不等式或导数求最值**。\n弦长最值常与中点弦、垂直条件结合；面积最值常拆成「底×高」。\n注意变量的取值范围（点在曲线上、斜率是否存在），避免取到范围外的解。"
    ),
    ("english", "推理判断题的解题技巧"): (
        "推断题要求根据文字推出未明说的含义，常见类型有推断隐含意义、作者意图、下文内容和人物性格。\n解题要点：立足原文，不凭常识；排除照抄原文的选项，那是细节题答案；排除过度推断的选项。\n关注转折词（but、however）、情态动词（may、should）和评价性形容词。\n**正确答案往往是原文的同义改写或合理引申**。\n「文章出处」「读者对象」也属推断，看用词和体裁判断。"
    ),
    ("english", "阅读理解的文章结构"): (
        "常见结构有总分总、总分、分总、并列、递进、对比、问题—解决。\n总分总：开头提出观点，中间分述，结尾总结，多见于说明文和议论文。\n**问题—解决：先摆问题，再分析原因，最后给方案，多见于科普和社会类文章**。\n时间顺序：按事件发展叙述，常见于记叙文和人物传记。\n空间顺序：按方位展开，常见于地点介绍。\n标志词：first、then、finally、however、therefore、for example。"
    ),
    ("english", "语法填空"): (
        "四步法：通读全文抓大意，判断所填词的词性，结合语法与语境确定形式，最后复查。\n有提示词：名词考虑单复数、所有格；动词考虑时态、语态、非谓语；形容词副词考虑比较级、最高级和词性转换。\n无提示词：多填介词、连词、冠词、代词，看搭配和逻辑关系。\n注意固定搭配和常见句式，如 so...that、not only...but also。\n**一个空一般只填一词，拼写和大小写要规范**。"
    ),
    ("english", "通知类应用文的写法"): (
        "通知（Notice）用于告知事项，分口头通知和书面通知。\n格式：标题 Notice 居中；正文写清时间、地点、事件、要求；**落款写发布单位或人名、日期**。\n开头常用：Attention, please. / May I have your attention, please?\n正文常用：There will be a... / We are going to... / Please be on time.\n结尾常用：Everyone is welcome. / Please attend on time.\n语言简洁明确、语气正式，时态多用将来时或一般现在时。"
    ),
    ("physics", "核裂变与核聚变"): (
        "核裂变：重核分裂成中等质量的核，同时放出能量和中子，如 ^{235}U 吸收中子后裂变。\n**链式反应：裂变产生的中子再引发新的裂变，需达到临界体积才能持续**。\n核聚变：轻核结合成较重的核，放出巨大能量，如氘氚聚变生成氦。\n聚变需在极高温度下进行（热核反应），太阳内部就在发生聚变。\n核能计算用质能方程 ΔE=Δmc^{2}，其中 Δm 为质量亏损。\n裂变已用于核电站，聚变仍处于受控研究阶段（如托卡马克装置）。"
    ),
    ("physics", "温度与内能"): (
        "改变内能有做功和热传递两种方式，二者等效。\n做功：外界对物体做功，内能增加；物体对外界做功，内能减少，如压缩气体、摩擦生热。\n热传递：热量从高温物体传到低温物体，分传导、对流、辐射三种方式。\n**热传递的条件是存在温度差，传递的是能量而不是温度**。\n热力学第一定律：ΔU=W+Q，W 为外界对气体做功，Q 为气体吸收的热量。\n绝热过程 Q=0；理想气体等温过程 ΔU=0。"
    ),
    ("physics", "电磁感应中的电路问题"): (
        "**把产生感应电动势的部分等效为电源**：电动势 E=nΔΦ/Δt 或 E=BLv，内阻为该段导体的电阻。\n其余部分为外电路，按闭合电路欧姆定律求解：I=E/(R+r)。\n判断电流方向用右手定则（切割磁感线）或楞次定律。\n路端电压 U=IR=E−Ir，注意区分电动势与路端电压。\n多个电源串联时注意方向：同向相加、反向相减。\n常见模型：单棒切割、双棒切割、线框进出磁场。"
    ),
    ("chem", "烯烃与炔烃"): (
        "烯烃含碳碳双键，通式 C_{n}H_{2n}（n≥2），官能团为 C=C。\n加成反应：与 H_{2}、X_{2}、HX、H_{2}O 加成，双键变单键，如乙烯使溴水褪色。\n氧化反应：使酸性 KMnO_{4} 溶液褪色；燃烧火焰明亮并带黑烟。\n加聚反应：n CH_{2}=CH_{2} → [CH_{2}−CH_{2}]_{n}，生成聚乙烯。\n**不对称烯烃与 HX 加成遵循马氏规则：氢加到含氢较多的碳原子上**。\n鉴别烯烃与烷烃：用溴水或酸性 KMnO_{4}，烯烃能使二者褪色。"
    ),
    ("chem", "烃的分类"): (
        "烃只含碳、氢两种元素，按碳骨架分为链烃和环烃。\n链烃（脂肪烃）分烷烃 C_{n}H_{2n+2}、烯烃 C_{n}H_{2n}、炔烃 C_{n}H_{2n-2}。\n环烃分脂环烃和芳香烃，芳香烃含苯环，如苯 C_{6}H_{6}、甲苯 C_{7}H_{8}。\n烷烃只含碳碳单键，性质稳定，主要发生取代反应。\n烯烃、炔烃含不饱和键，易发生加成、氧化、加聚。\n**苯环结构特殊，易取代、难加成，不能使溴水褪色，但能萃取溴**。"
    ),
    ("chem", "有机合成路线设计"): (
        "有机合成的核心是官能团的引入、转化和碳骨架的构建。\n**逆合成分析：从目标产物倒推，找出前一步原料，逐层向前直到常见原料**。\n官能团引入：烷烃卤代引入 −X；烯烃加水引入 −OH；醇氧化引入 −CHO、−COOH；酯化引入 −COO−。\n官能团转化：醇→醛→酸→酯；卤代烃→醇→烯。\n碳骨架构建：增长碳链用加成、取代；缩短碳链用裂化、氧化。\n注意保护基团和反应顺序，避免副反应。"
    ),
    ("chem", "高分子化合物"): (
        "高分子由单体通过聚合反应生成，分加聚和缩聚两类。\n加聚：含 C=C 的单体打开双键相连，无小分子生成，如聚乙烯、聚丙烯、聚苯乙烯。\n缩聚：单体间脱去小分子（H_{2}O、HCl 等），如聚酯（涤纶）、聚酰胺（尼龙）。\n**判断方法：主链全为碳原子一般是加聚；主链含 −COO−、−CONH− 等一般是缩聚**。\n单体推断：加聚物找主链重复单元还原双键；缩聚物按酯基或肽键断开加 H 和 OH。\n高分子链有线性、支链、网状三种结构，影响其性质。"
    ),
    ("bio", "有氧呼吸的过程"): (
        "细胞呼吸分有氧呼吸和无氧呼吸。\n有氧呼吸在细胞质基质和线粒体中进行，C_{6}H_{12}O_{6}+6O_{2}→6CO_{2}+6H_{2}O+能量，分三阶段。\n无氧呼吸只在细胞质基质中进行，产物为乳酸或酒精和 CO_{2}，释放少量能量。\n第一阶段（糖酵解）两者相同：葡萄糖分解为丙酮酸，产生少量 ATP 和 [H]。\n**有氧呼吸第三阶段释放能量最多**，[H] 与 O_{2} 结合生成水。\n无氧呼吸只在第一阶段产生 ATP，第二阶段不产生 ATP。"
    ),
    ("bio", "基因突变的特点"): (
        "基因突变有五个特点。\n普遍性：所有生物都可能发生。\n随机性：可发生在任何时期、任何部位、任何基因上。\n低频性：自然突变率很低。\n**不定向性：可产生多种等位基因**。\n多害少利性：多数突变对生物有害，少数有利或中性。\n基因突变是生物变异的根本来源，为进化提供原始材料。"
    ),
    ("politics", "主次矛盾与辩证否定"): (
        "辩证否定是事物自身的否定，即自己否定自己、自己发展自己。\n**实质是「扬弃」：既克服又保留**，克服旧事物中消极的内容，保留积极合理的因素。\n特点是发展的环节（实现新质代替旧质）和联系的环节（新旧事物相互联系）。\n方法论：树立创新意识，不唯上、不唯书、只唯实；既不能肯定一切，也不能否定一切。\n与形而上学否定观的区别：后者是外力否定、全盘否定或全盘肯定。"
    ),
    ("history", "经济重心南移"): (
        "经济重心南移是一个长期过程。\n魏晋南北朝：北方战乱，人口南迁，江南初步开发。\n隋唐：南方经济继续发展，安史之乱后北方衰退，南移加速。\n**南宋：经济重心完全南移**，出现「苏湖熟，天下足」的局面。\n原因：北方战乱、人口南迁带来劳动力和技术；南方自然条件优越、社会相对安定。\n影响：南方成为国家财政的主要来源，文化重心也随之南移。"
    ),
    ("geography", "内力作用与板块构造"): (
        "内力作用与外力作用共同塑造地表形态，二者同时进行、相互对立。\n内力作用：能量来自地球内部放射性衰变，表现为地壳运动、岩浆活动、变质作用，使地表起伏加大。\n外力作用：能量来自太阳辐射，表现为风化、侵蚀、搬运、沉积，使地表趋于平缓。\n**一般内力作用占主导，决定地表形态的基本格局**；外力作用在其基础上进行雕塑。\n不同地区主导作用不同：板块交界处内力强，河流中下游外力强。"
    ),
    ("geography", "工业区位因素"): (
        "工业布局要综合考虑经济、社会、环境三方面。\n经济效益：靠近原料、燃料、市场，交通便利，降低成本。\n社会效益：带动就业，促进区域发展，兼顾国防需要。\n**环境效益：污染企业布局在盛行风的下风向、河流下游**；对环境要求高的企业布局在上风向、上游。\n集聚与分散：有联系的企业集聚形成工业地域，污染重或占地大的宜分散。\n还要考虑政策、劳动力、技术等因素。"
    ),
    ("geography", "太阳系与地球的普通性"): (
        "地球的普通性指它与太阳系其他行星相比没有特殊之处。\n运动特征：与其他行星一样具有同向性（都绕日自西向东）、共面性（轨道面接近同一平面）、近圆性（轨道接近圆）。\n结构特征：与其他类地行星（水星、金星、火星）相似，有固体表面和分层的内部结构。\n质量、体积、密度在类地行星中最大，但仍属行星范畴。\n**地球的特殊性在于存在生命**：日地距离适中、质量体积适中、有液态水、宇宙环境安全稳定。"
    ),
}


def apply_overrides(ordered):
    n = 0
    index = {x["s"]: x for x in ordered}
    for (key, title), body in BODY_OVERRIDES.items():
        subj = index.get(key)
        if subj is None:
            continue
        hit = False
        for b in subj["blocks"]:
            for c in b["children"]:
                for it in c.get("items", []):
                    if it["t"] == title:
                        it["sec"][0]["body"] = body
                        hit = True
                        n += 1
        if not hit:
            print("! 定稿未匹配:", key, title)
    return n


PARALLEL_PAIRS = {
    ("物理", "查理定律", "盖吕萨克定律"),
    ("物理", "左手定则", "右手定则"),
    ("物理", "电场", "磁场"),
    ("数学", "指数函数", "对数函数"),
    ("数学", "等差数列", "等比数列"),
}


def _is_parallel(subj_name, t1, t2):
    return (subj_name, t1, t2) in PARALLEL_PAIRS or (subj_name, t2, t1) in PARALLEL_PAIRS


def dedupe_items(ordered):
    """合并重复知识点。
    规则（同一科目内）：① 归一化标题相同且同小节 → 合并；
    ② 标题互相包含且正文相似 ≥0.62 → 合并；③ 正文相似 ≥0.82 → 合并。
    跨小节同标题但正文差异明显（<0.72）时不合并，输出告警由人工改标题区分。"""
    removed, reports, warns = 0, [], []
    index = {x["s"]: x for x in ordered}

    def find_item(subj, title):
        for b in subj["blocks"]:
            for c in b["children"]:
                for it in c.get("items", []):
                    if it["t"] == title:
                        return c, it
        return None, None

    # ① 人工确认的重复对
    for key, keep_t, drop_t in MANUAL_PAIRS:
        subj = index.get(key)
        if subj is None:
            continue
        ck, ik = find_item(subj, keep_t)
        cd, idd = find_item(subj, drop_t)
        if ik is None or idd is None or ik is idd:
            continue
        _merge_items(ik, idd)
        cd["items"] = [x for x in cd["items"] if x is not idd]
        removed += 1
        reports.append((subj["name"], ck["name"], keep_t, cd["name"], drop_t))

    # ② 规则判定的重复（同标题/包含/正文高度相似）
    for subj in ordered:
        slots = []
        for b in subj["blocks"]:
            for c in b["children"]:
                for it in c.get("items", []):
                    slots.append([b, c, it, False])
        for i in range(len(slots)):
            b1, c1, it1, dead1 = slots[i]
            if dead1:
                continue
            for j in range(i + 1, len(slots)):
                b2, c2, it2, dead2 = slots[j]
                if dead2:
                    continue
                nt1, nt2 = _norm_title(it1["t"]), _norm_title(it2["t"])
                body1 = _plain(it1["sec"][0]["body"])
                body2 = _plain(it2["sec"][0]["body"])
                same_sec = (c1 is c2)
                title_same = (nt1 == nt2)
                contain = (nt1 in nt2 or nt2 in nt1) and abs(len(nt1) - len(nt2)) <= 6
                sim = _sim(body1, body2)
                if title_same:
                    if not same_sec and sim < 0.72:
                        warns.append((subj["name"], c1["name"], it1["t"], c2["name"], it2["t"]))
                        continue
                    should = True
                elif contain and sim >= 0.62:
                    should = True
                elif sim >= 0.82:
                    should = True
                else:
                    should = False
                if not should:
                    continue
                if _is_parallel(subj["name"], it1["t"], it2["t"]):
                    continue
                # 标题只差「的」时，取更通顺的带「的」版本
                if nt1 == nt2 and "的" in it2["t"] and "的" not in it1["t"]:
                    it1["t"] = it2["t"]
                _merge_items(it1, it2)
                slots[j][3] = True
                removed += 1
                reports.append((subj["name"], c1["name"], it1["t"], c2["name"], it2["t"]))
        for b, c, it, dead in slots:
            if dead:
                c["items"] = [x for x in c["items"] if x is not it]
    return removed, reports, warns


# ---------- 分数化：把 a/b 这类明确分式写成 \frac{a}{b}，UI 渲染成上下结构 ----------
_MULTI_UNITS = ("mol", "mmol", "μmol", "kg", "mg", "km", "cm", "mm", "dm", "nm",
                "min", "mL", "μL", "kJ", "kW", "mV", "mA", "kPa", "MPa", "kHz",
                "MHz", "kcal", "cal", "Pa", "Hz", "℃", "dB")
_GREEK = "αβγδεζηθικλμνξοπρστυφχψωΑΒΓΔΕΖΗΘΙΚΛΜΝΞΟΠΡΣΤΥΦΧΨΩ"
# 注意：减号 − / 连字符 - 不在原子集里，作为「深度 0 的分隔符」终止分子/分母扫描，
# 这样 1/n−1/(n+1) 会正确拆成 \frac{1}{n}−\frac{1}{(n+1)}；括号内的 −（如 (n−m)）不受影响。
_ATOM = set("0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
            "^_{}()|√πΔ′°·!\\²³¹⁻×" + _GREEK)
# 单字母计量单位（用于识别 L·s、N·m 这类复合单位；x·lna 中的 x、lna 不在其中）
_U1 = set("LNmsKJWVACg")


def _strip_script(t):
    return re.sub(r"[\^_]\{[^{}]*\}", "", t)


def _is_unit(den):
    """分母是不是计量单位：多字母单位（mol/kg/cm…）保留；单字母复合单位（L·s、N·m）
    按 · 拆开后每段都是单位符号才保留。x·lna 这类数学乘法不判为单位。"""
    d = _strip_script(den)
    d2 = d.strip("()（）[]| ")
    if not d2:
        return False
    for u in _MULTI_UNITS:
        if u in d2:
            return True
    if "·" in d2:
        parts = [p for p in d2.split("·") if p]
        if parts and all(p in _U1 for p in parts):
            return True
    return False


_UNIT_KEYS = set("m s kg km cm mm mol L mL min h g N J W V A C K Pa Hz Ω ℃ T F H S".split())


def _is_unit_like(den, num):
    """分母是单个计量单位符号、且分子里带单位字母时，说明这是「9.8m/s²」这类
    物理量而不是数学分式，保持原样（否则会渲染成 9. 后面挂一个上下分数）。"""
    import re
    d = _strip_script(den).strip("()（）[]| ")
    if d not in _UNIT_KEYS:
        return False
    n = _strip_script(num).strip("()（） ")
    return re.search(r"[A-Za-z]", n) is not None


def _scan_left(s, pos):
    i, dr, ds = pos - 1, 0, 0
    while i >= 0:
        c = s[i]
        if c == ")":
            dr += 1
        elif c == "(":
            if dr == 0:
                break
            dr -= 1
        elif c == "]":
            ds += 1
        elif c == "[":
            if ds == 0:
                break
            ds -= 1
        elif dr == 0 and ds == 0 and c not in _ATOM:
            break
        i -= 1
    return i + 1


def _scan_right(s, pos):
    i, n = pos + 1, len(s)
    dr = ds = 0
    while i < n:
        c = s[i]
        if c == "(":
            dr += 1
        elif c == ")":
            if dr == 0:
                break
            dr -= 1
        elif c == "[":
            ds += 1
        elif c == "]":
            if ds == 0:
                break
            ds -= 1
        elif dr == 0 and ds == 0 and c not in _ATOM:
            break
        i += 1
    return i


def fracify(s):
    if not s or "/" not in s:
        return s
    out, i, n = [], 0, len(s)
    while i < n:
        if s[i] != "/":
            out.append(s[i])
            i += 1
            continue
        ls = _scan_left(s, i)
        re_ = _scan_right(s, i)
        num, den = s[ls:i], s[i + 1:re_]
        ok = bool(num) and bool(den) and len(num) <= 60 and len(den) <= 60
        if ok and _is_unit(den):
            ok = False            # 单位里的斜杠（mol、L、s…）保持原样
        if ok and _is_unit_like(den, num):
            ok = False            # 9.8m/s²、km/s 这类物理量不能当分数
        if ok:
            if num:
                del out[-len(num):]      # 分子已逐字符写入，先回退再去重
            num2 = fracify(num) if "/" in num else num
            den2 = fracify(den) if "/" in den else den
            out.append("\\frac{" + num2 + "}{" + den2 + "}")
            i = re_
        else:
            out.append("/")
            i += 1
    return "".join(out)


# 括号套分数再加幂的写法改写成单个分数，窄屏更整齐（数学等价）
_TIDY = (
    ("(\\frac{(a+b)}{2})^{2}", "\\frac{(a+b)^{2}}{4}"),
    ("(\\frac{b}{a})^{2}", "\\frac{b^{2}}{a^{2}}"),
)


def tidy_frac(t):
    for a, b in _TIDY:
        t = t.replace(a, b)
    return t


def sqrtify(s):
    r"""把 √(…) / √2 / √ab 写成 \sqrt{…}，UI 会画成带顶部横线的根式。"""
    if not s or "√" not in s:
        return s
    out, i, n = [], 0, len(s)
    while i < n:
        c = s[i]
        if c != "√":
            out.append(c)
            i += 1
            continue
        j = i + 1
        if j < n and s[j] in "([":
            op = s[j]
            cl = ")" if op == "(" else "]"
            depth, e = 0, j
            while e < n:
                if s[e] == op:
                    depth += 1
                elif s[e] == cl:
                    depth -= 1
                    if depth == 0:
                        break
                e += 1
            if e < n:
                out.append("\\sqrt{" + s[j + 1:e] + "}")
                i = e + 1
                continue
        e = j
        while e < n:
            ch = s[e]
            if (ch.isalnum() and ord(ch) < 128) or ch in _GREEK or ch in "^{}":
                e += 1
            else:
                break
        atom = s[j:e]
        if atom:
            out.append("\\sqrt{" + atom + "}")
            i = e
        else:
            out.append("√")
            i += 1
    return "".join(out)


# 中文/含中文下标的「除以」式：fracify 只吃公式字符，这类词级比值单独精确替换。
# 注意顺序：先长后短，避免「转化量/起始量」误伤「已转化量/起始量」。
_CN_FRAC = (
    ("该核素一个原子的质量 / (^{12}C 原子质量的 1/12)",
     "\\frac{该核素一个原子的质量}{(^{12}C 原子质量的 1/12)}"),
    ("(第一次捕获数 × 第二次捕获数) / 第二次捕获中的标志数",
     "\\frac{(第一次捕获数 × 第二次捕获数)}{第二次捕获中的标志数}"),
    ("(第一次捕获数×第二次捕获数)/重捕中标志数",
     "\\frac{(第一次捕获数×第二次捕获数)}{重捕中标志数}"),
    ("(第一次捕获数×第二次捕获数)/重捕标志数",
     "\\frac{(第一次捕获数×第二次捕获数)}{重捕标志数}"),
    ("下一营养级同化量 / 上一营养级同化量 × 100%",
     "\\frac{下一营养级同化量}{上一营养级同化量} × 100%"),
    ("A 包含的基本事件数/基本事件总数",
     "\\frac{A 包含的基本事件数}{基本事件总数}"),
    ("A 含基本事件数/总基本事件数",
     "\\frac{A 含基本事件数}{总基本事件数}"),
    ("该基因的数目 / 该位点全部等位基因的总数",
     "\\frac{该基因的数目}{该位点全部等位基因的总数}"),
    ("某基因数/该基因座位全部等位基因总数",
     "\\frac{某基因数}{该基因座位全部等位基因总数}"),
    ("生成物浓度幂之积/反应物浓度幂之积",
     "\\frac{生成物浓度幂之积}{反应物浓度幂之积}"),
    ("转化的物质的量/起始物质的量",
     "\\frac{转化的物质的量}{起始物质的量}"),
    ("摩尔体积/阿伏加德罗常数",
     "\\frac{摩尔体积}{阿伏加德罗常数}"),
    ("商品价格总额/货币流通速度",
     "\\frac{商品价格总额}{货币流通速度}"),
    ("事件发生的次数/试验总次数",
     "\\frac{事件发生的次数}{试验总次数}"),
    ("该层个体数/总体个体数",
     "\\frac{该层个体数}{总体个体数}"),
    ("已转化量/起始量",
     "\\frac{已转化量}{起始量}"),
    ("转化量/起始量",
     "\\frac{转化量}{起始量}"),
    ("平方平均数/√2",
     "\\frac{平方平均数}{√2}"),
    ("竖直高度/水平宽度",
     "\\frac{竖直高度}{水平宽度}"),
    ("图上距离/实地距离",
     "\\frac{图上距离}{实地距离}"),
    ("样本量/总体量",
     "\\frac{样本量}{总体量}"),
    ("c标·V标/V待",
     "\\frac{c标·V标}{V待}"),
    ("d·v水/v船",
     "\\frac{d·v水}{v船}"),
    ("频率/组距",
     "\\frac{频率}{组距}"),
    ("路程/时间",
     "\\frac{路程}{时间}"),
    ("P损/P",
     "\\frac{P损}{P}"),
    ("U损/U",
     "\\frac{U损}{U}"),
    ("经度/15",
     "\\frac{经度}{15}"),
)


def _cn_frac(s):
    for a, b in _CN_FRAC:
        s = s.replace(a, b)
    return s


def apply_frac(ordered):
    n = 0
    for subj in ordered:
        if subj["s"] == "english":
            continue            # 英语的斜杠是语法并列/日期/词缀，不转分式
        for b in subj["blocks"]:
            for ch in b["children"]:
                for it in ch.get("items", []):
                    for sec in it.get("sec", []):
                        sec["body"] = tidy_frac(sqrtify(fracify(_cn_frac(sec["body"]))))
                        n += 1
                for sec in ch.get("sec", []):
                    sec["body"] = tidy_frac(sqrtify(fracify(_cn_frac(sec["body"]))))
                    n += 1
    return n


def apply_highlights(ordered):
    """重点高亮已直接写在正文里（**...**），这里只统计数量，渲染交给 UI。"""
    n = 0
    for subj in ordered:
        for b in subj["blocks"]:
            for c in b["children"]:
                for it in c.get("items", []):
                    n += it["sec"][0]["body"].count("**") // 2
    return n, 0


def apply_images(ordered):
    """把工具生成的配图挂到对应知识点（数据层，UI 只负责渲染）。"""
    from img_map import IMG_MAP
    index = {s["s"]: s for s in ordered}
    bound = 0
    for key, ckw, ikw, img in IMG_MAP:
        subj = index.get(key)
        if subj is None:
            print("! 配图科目不存在:", key)
            continue
        target = None
        for b in subj["blocks"]:
            for ch in b["children"]:
                if ckw in ch["name"]:
                    for it in ch["items"]:
                        if "img" not in it and ikw in it["t"]:
                            target = it
                            break
                if target:
                    break
            if target:
                break
        if target is None:  # 回退：全科按标题关键词找
            for b in subj["blocks"]:
                for ch in b["children"]:
                    for it in ch["items"]:
                        if "img" not in it and ikw in it["t"]:
                            target = it
                            break
                    if target:
                        break
                if target:
                    break
        if target is None:
            print("! 配图未匹配:", key, ckw, "/", ikw, "->", img)
            continue
        target["img"] = "img/%s.png" % img
        bound += 1
    return bound



# ---------- 编号：章与知识点（编号里带科目码与加入日期） ----------
import datetime as _dt

SUBJ_CODE = {"chinese": "CH", "math": "MA", "english": "EN", "physics": "PH",
             "chem": "CM", "bio": "BI", "politics": "PL", "history": "HI", "geography": "GE"}


def assign_ids(ordered):
    """给每个章、每个知识点分配可读编号，并持久化到 tools/ids.json。

    规则：<科目码>-<加入日期 YYMMDD>-<章序号>[-<章内序号>]
    例：MA-261028-03（数学第 3 章）、MA-261028-03-012（该章第 12 条）
    已分配过的编号永不变；新出现的章/知识点用构建当天的日期。
    """
    path = os.path.join(TOOLS, "ids.json")
    try:
        with open(path, encoding="utf-8") as f:
            ids = json.load(f)
    except Exception:
        ids = {"v": 1, "blocks": {}, "items": {}}
    today = _dt.date.today().strftime("%y%m%d")
    added_b = added_i = 0
    for s in ordered:
        code = SUBJ_CODE.get(s["s"], "XX")
        for b in s["blocks"]:
            bk = s["s"] + "|" + b["name"]
            rec = ids["blocks"].get(bk)
            if rec is None:
                no = sum(1 for k in ids["blocks"] if k.startswith(s["s"] + "|")) + 1
                rec = {"id": "%s-%s-%02d" % (code, today, no), "d": today}
                ids["blocks"][bk] = rec
                added_b += 1
            b["id"] = rec["id"]
            ch = rec["id"].rsplit("-", 1)[-1]
            cnt = sum(1 for k in ids["items"] if k.startswith(bk + "|"))
            for c in b["children"]:
                for it in c.get("items", []):
                    ik = bk + "|" + it["t"]
                    r2 = ids["items"].get(ik)
                    if r2 is None:
                        cnt += 1
                        r2 = {"id": "%s-%s-%s-%03d" % (code, today, ch, cnt), "d": today}
                        ids["items"][ik] = r2
                        added_i += 1
                    it["id"] = r2["id"]
                    it["d"] = r2["d"]
    with open(path, "w", encoding="utf-8") as f:
        json.dump(ids, f, ensure_ascii=False, indent=0)
    print("编号分配：新增章 %d，新增知识点 %d（日期 %s）" % (added_b, added_i, today))
    return added_b, added_i

img_total = apply_images(ordered)
dedupe_n, dedupe_reports, dedupe_warns = dedupe_items(ordered)
ov_n = apply_overrides(ordered)
frac_n = apply_frac(ordered)
hl_total, hl_miss = apply_highlights(ordered)
id_b, id_i = assign_ids(ordered)

# 分科输出：每个科目一个 json 文件 + order.json 索引（数据库分文件，UI 只读）
data_dir = os.path.join(TOOLS, "..", "app", "src", "main", "assets", "data")
data_dir = os.path.abspath(data_dir)
if not os.path.isdir(data_dir):
    os.makedirs(data_dir)
for s in ordered:
    fp = os.path.join(data_dir, s["s"] + ".json")
    with open(fp, "w", encoding="utf-8") as f:
        json.dump({"v": DATA_VERSION, "subject": s}, f, ensure_ascii=False, indent=1)
order_fp = os.path.join(data_dir, "order.json")
with open(order_fp, "w", encoding="utf-8") as f:
    json.dump({"v": DATA_VERSION, "order": [{"s": s["s"], "name": s["name"]} for s in ordered]},
              f, ensure_ascii=False, indent=1)
# 删除旧的合并文件（不再使用）
old_fp = os.path.join(TOOLS, "..", "app", "src", "main", "assets", "knowledge.json")
old_fp = os.path.abspath(old_fp)
if os.path.exists(old_fp):
    os.remove(old_fp)

n_block = sum(len(s["blocks"]) for s in ordered)
n_item = sum(len(c["items"]) for s in ordered for b in s["blocks"] for c in b["children"])
print("已生成分科数据 assets/data/（每科一文件 + order.json）")
print("科目:", [s["name"] for s in ordered])
print("大块数:", n_block, " 考点数:", n_item, " 其中补充包新增:", extra_total)
print("配图绑定:", img_total, " 分数化节数:", frac_n, " 人工标重点:", hl_total, "未匹配:", hl_miss)
print("重复合并:", dedupe_n, "条  人工定稿:", ov_n, "条")
for r in dedupe_reports:
    print("  合并 %s: [%s] %s  <=  [%s] %s" % r)
for w in dedupe_warns:
    print("  ! 跨小节同标题需人工区分 %s: [%s] %s  vs  [%s] %s" % w)
# 云端更新包：聚合为一个文件，供 App 下载后覆盖本地数据
cloud_dir = os.path.join(TOOLS, "..", "cloud")
cloud_dir = os.path.abspath(cloud_dir)
os.makedirs(cloud_dir, exist_ok=True)
cloud_fp = os.path.join(cloud_dir, "update.json")
with open(cloud_fp, "w", encoding="utf-8") as f:
    json.dump({"v": DATA_VERSION, "subjects": ordered}, f, ensure_ascii=False, separators=(",", ":"))
print("云端更新包:", cloud_fp, os.path.getsize(cloud_fp), "bytes")
# 版本标记：App 在 GitHub API 不可达时读这个文件判断最新版本
import re as _re
_man = os.path.abspath(os.path.join(TOOLS, "..", "app", "src", "main", "AndroidManifest.xml"))
_ver = ""
try:
    _m = _re.search(r'android:versionName="([^"]+)"', open(_man, encoding="utf-8").read())
    if _m:
        _ver = _m.group(1)
except Exception:
    pass
if _ver:
    with open(os.path.join(cloud_dir, "version.json"), "w", encoding="utf-8") as f:
        json.dump({"version": _ver, "tag": "v" + _ver}, f, ensure_ascii=False)
    print("版本标记:", os.path.join(cloud_dir, "version.json"), "->", _ver)
print("目录:", data_dir)
