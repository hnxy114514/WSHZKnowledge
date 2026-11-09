# -*- coding: utf-8 -*-
"""英语 · 递归节点树。分组节点 = name + children，叶子 = name + body。

只写内容：编号、日期、星级由 tools/build_authored.py 按层级自动生成，
源文件里不要写 id / d / star。加一层就加一层 children，UI 无需改动。

由 tools/migrate_tree.py 从 subjects_legacy/ 一次性转换而来。
"""
NAME = '英语'

TREE = [
    {"name": '第一板块 词汇', "children": [
        {"name": '1.1 构词法与词性', "children": [
            {"name": '派生法：前后缀',
             "body": """**核心结论：前缀改词义，后缀改词性**，词根决定基本含义。
构词结构：**前缀 + 词根 + 后缀**，如 un + happy + ness → unhappiness。
否定前缀有同化规则：**in- 在 b/m/p 前变 im-，在 l 前变 il-，在 r 前变 ir-**（impossible、illegal、irregular）。
常见拼写变化三条：
- **词尾不发音 e 常去掉**：make→making。
- **辅音+y 变 i 再加后缀**：happy→happiness。
- **重读闭音节双写末尾辅音**：run→running。
**语法填空靠句法位置反推词性，再选后缀**：冠词/形容词后填名词，系动词后填形容词，修饰动词填副词。
易错：**不是所有 -ly 都是副词**（friendly、lovely 是形容词）；**前缀一般不改变词性**；同一词根加不同后缀词义可能分叉。"""},
            {"name": '合成法与转化法',
             "body": '合成法：两个或多个词合成新词，如 blackboard、well-known。\n合成形容词常由副词+过去分词构成，如 well-prepared。\n转化法：词形不变而词性改变，如 water（名词→动词）。\n转化后词义常与原义相关。\n**构词法知识有助于阅读中猜测词义**。'},
            {"name": '词性与句子成分',
             "body": """**十大词类：名、动、形、副、代、数、冠、介、连、感叹**；句子成分主要是主、谓、宾、表、定、状、补。
对应关系：**名词/代词作主语、宾语、表语；动词作谓语；形容词作定语或表语；副词修饰动词、形容词或全句**。
**判断词性要结合句法位置**，这是语法填空有提示词时的第一步：
- **冠词、形容词或物主代词之后 → 名词**，并注意单复数。
- **系动词之后 → 形容词**；**修饰动词或整句 → 副词**。
- **介词之后 → 名词、代词或动名词**。
**形容词与副词混用、名词漏加复数、谓语动词漏第三人称单数**，是最高频的三处失分。
与「常见后缀与词性判断」分工：那条靠词缀判断，本条靠句子结构判断，两者可互相验证。"""},
            {"name": '常见前缀的含义',
             "body": '否定前缀：un-、in-、im-、il-、ir-、dis-、non-（如 unable、impossible、dislike）。\n方向与程度：re-（再）、pre-（预先）、post-（之后）、sub-（下）、super-（超）、over-（过度）、under-（不足）。\n人与关系：co-（共同）、anti-（反对）、auto-（自动）、inter-（相互）。\n数量：mono-（单）、bi-（双）、tri-（三）、multi-（多）。\n**判断词义时先剥离前缀，再结合词根推断**。'},
            {"name": '常见后缀与词性判断',
             "body": '名词后缀：-tion、-ment、-ness、-ity、-ance、-ship、-er/-or（人）。\n形容词后缀：-ful、-less、-ous、-able、-ive、-al、-ic。\n动词后缀：-ize/-ise、-ify、-en。\n副词后缀：-ly（多数副词）。\n**语法填空中若空格前有冠词或形容词，多填名词；有系动词多填形容词**。'},
        ]},
        {"name": '1.2 高频短语与词语辨析', "children": [
            {"name": '动词短语搭配',
             "body": '常见短语：account for（解释）、break down（出故障）、carry out（执行）、come up with（想出）。\ngive up、give in、give away、give off 意义各不相同。\nturn to、turn up、turn down、turn out 要区分。\n**短语动词的宾语位置需注意（代词放中间）**。\n固定搭配是完形填空的常考点。'},
            {"name": '近义词辨析',
             "body": '**affect 与 effect：前者动词影响，后者名词效果**。\nrise 不及物，raise 及物。\naccept（主动接受）与 receive（收到）。\nbesides（除…还有）与 except（除…之外）。\nelder 与 older：前者只用于人且作定语。'},
            {"name": '介词与固定搭配',
             "body": '时间介词：in 用于年月季节，on 用于具体某天，at 用于时刻。\n位置介词：in、on、at 的空间感不同。\n常见搭配：be interested in、be good at、be afraid of。\n介词后接名词、代词或动名词。\n**介词搭配错误是语法填空的高频失分点**。'},
            {"name": '易混动词辨析',
             "body": "raise（及物，举起、提高）与 rise（不及物，上升）易混。\n**lay（放置，过去式 laid）与 lie（躺，过去式 lay；说谎，lied）易混**。\nspend 人作主语，cost 物作主语，take 用 it 作形式主语。\nborrow（借入）与 lend（借出）方向相反。\n记易混词要结合例句和搭配，如 raise one's hand、the sun rises。"},
            {"name": '一词多义的辨析',
             "body": '英语中许多词有多种含义，要根据语境判断。\n如 get 可表示得到、变得、到达等。\ntake 可表示拿、花费、乘坐、服用等。\nrun 可表示跑、经营、流动等。\n**词义辨析要结合上下文，不能死记单一意思**。\n积累一词多义有助于提高阅读和完形能力。'},
            {"name": '高频名词与形容词搭配',
             "body": """名词搭配的固定介词（整块记）：
- **solution to**（解决办法）、**access to**（通往/使用权）。
- **effect on**（对……的影响）、**reason for**（原因）、**increase in**（增长）。
形容词搭配：be aware of、be capable of、be responsible for、be similar to、be different from、be keen on。
动宾搭配：make a decision、take measures、pay attention to、gain experience、draw a conclusion。
**中文思维易错搭配**：
- discuss about（✗，discuss 及物）、marry with（✗，marry sb）。
- affect/effect 词性用反、join in 与 take part in 对象不同。
**搭配要整块记，不要逐词直译**；写作里把 make、take、have、pay 与名词的组合记熟，比背单个名词实用。
辨析要看搭配对象：high 修饰高度与程度，tall 修饰人或细长物；a heavy rain 不说 a big rain。"""},
        ]},
    ]},
    {"name": '第二板块 语法', "children": [
        {"name": '2.1 时态与语态', "children": [
            {"name": '常用时态',
             "body": '一般现在时：表习惯、真理、客观事实。\n一般过去时：表过去发生的动作，常与明确过去时间连用。\n**现在完成时：表过去动作对现在的影响，或持续到现在**。\n过去完成时：表过去某一时间之前完成的动作，即过去的过去。\n将来时：will do 表意愿或预测，be going to 表计划。'},
            {"name": '被动语态',
             "body": '构成：be+过去分词，时态体现在 be 上。\n只有及物动词才有被动语态。\n**happen、occur、belong to 等不及物动词无被动**。\n被动语态常用于不知道或不必说明施动者的情况。\n情态动词被动：must be done、should have been done。'},
            {"name": '主谓一致',
             "body": """语法一致：主语单复数决定谓语形式；and 连接两个并列主语一般用复数。
**就近原则：either…or、neither…nor、not only…but also、or、there be 由最近的名词定**。
就远原则：with、as well as、together with、rather than、besides 不影响谓语，看前面的主语。
意义一致：family、team、class 强调整体用单数，强调成员用复数。
**the number of + 复数名词 → 单数；a number of + 复数名词 → 复数**。
不定式、动名词、主语从句作主语一律视为单数。
分数／百分数／most of + 名词，谓语随 of 后的名词；many a + 单数名词谓语用单数。"""},
            {"name": '完成时态的用法',
             "body": """现在完成时：**have/has + 过去分词**，两条用法——**动作已完成但对现在有影响**（I have lost my key，所以进不去门）、**动作持续到现在**（I have lived here for 5 years）。
标志词：already、yet、ever、never、since、for、so far、recently。
**瞬间动词不能与一段时间连用**：用 have had 而不是 have bought，用 have been in 而不是 have come。
过去完成时：**had + 过去分词**，表示「过去的过去」，常与 by、before、when 从句搭配。
**现在完成时不能与明确的过去时间连用**（× I have seen him yesterday），这是最高频的错。"""},
            {"name": '一般过去时与现在完成时的区别',
             "body": '一般过去时只强调动作发生在过去，与现在没有联系。\n现在完成时强调过去的动作对现在造成的影响或结果。\n**有明确过去时间状语（yesterday、last year、ago）用一般过去时**。\njust、already、yet、so far 等常用现在完成时。\nSince 引导时间状语从句时，主句用现在完成时。'},
            {"name": '将来时的表达方式',
             "body": '一般将来时用 will + 动词原形，表示将来的动作或状态。\n**be going to 表示打算或根据迹象判断将要发生**。\nbe doing 可表示按计划安排将要发生的动作。\nbe to do 表示按计划、安排将要发生。\n一般现在时也可表示按时刻表将要发生的动作。'},
            {"name": '进行时态的用法',
             "body": '**现在进行时表示说话时正在进行的动作**。\n结构为 be + 现在分词。\n也可表示按计划将要发生的动作，如 I am leaving tomorrow。\n过去进行时表示过去某一时刻正在进行的动作。\n现在完成进行时表示从过去持续到现在的动作。'},
            {"name": '过去完成时',
             "body": """构成：**had + 过去分词**，表示「在过去某一时刻之前已经完成」。
必须有**一个过去的时间参照点**：By the time we arrived, the film had begun（参照点是 arrived）。
三类典型场景：**by + 过去时间**、**before/after 引导的先后**、**宾语从句中主句是过去时、从句动作更早**（He said he had finished）。
**没有过去参照点就不能用过去完成时**，这是最常见的误用。
与一般过去时的区别：**多个过去动作按时间顺序叙述时用一般过去时**，只有需要强调「更早」时才用过去完成时。"""},
            {"name": '现在完成进行时',
             "body": '现在完成进行时表示从过去持续到现在的动作。\n结构为 have/has been + 现在分词。\n强调动作的持续性和未完成性。\n如 I have been reading for two hours。\n**与现在完成时的区别：完成进行时强调持续过程**。'},
            {"name": '一般现在时的用法',
             "body": '一般现在时表示经常性、习惯性的动作或状态。\n也表示客观真理和事实。\n如 The earth moves around the sun。\n**第三人称单数动词加 s 或 es**。\n一般现在时还可表示按时刻表将要发生的动作。'},
            {"name": '过去进行时',
             "body": """**was/were + 现在分词，表示过去某一时刻或某段时间正在进行的动作**。
常与 at that time、at nine last night、this time yesterday、when/while 从句连用。
**长动作进行时 + 短动作一般过去时**（I was reading when he came in），长动作做背景。
while 引导的从句多用进行时；两个动作同时进行都用过去进行时。
与一般过去时区别：过去进行时不强调完成，只描述过程，常用来铺垫背景。
状态动词（know、belong、like）一般不用于进行时。
也可表示过去按计划即将发生的动作（was leaving），语气更委婉。"""},
            {"name": '一般过去时的用法',
             "body": """构成：**动词过去式**（be 用 was/were），否定与疑问用 did + 动词原形。
四条核心用法：**过去某时的动作或状态**（I saw him yesterday）、**过去一段时间的习惯**（He smoked ten years ago）、**一连串过去的动作**（He stood up, opened the door and left）、**委婉语气**（I wondered if you could help）。
时间标志：yesterday、last week、in 1998、two days ago、when 引导的从句。
**与现在完成时的分界：一般过去时只说「发生在过去」，不交代与现在的关系；现在完成时必须体现「对现在的影响」或「持续到现在」**。
**used to do 表示过去常常而现已不再**；be used to doing 是「习惯于」，别混。"""},
            {"name": '将来进行时',
             "body": """结构：**will be + 现在分词**。
两条用法：**将来某时刻正在进行的动作**（At 8 tomorrow I will be studying）、**按计划安排将发生**（We will be meeting at the airport）。
**与一般将来时的区别：将来进行时强调「正在」的画面感，语气也更委婉**（Will you be using the car? 比 Will you use the car? 客气）。
**临时决定用 will do，不用 will be doing**。
常见标志：**at this time tomorrow、at 8 next Monday、all day tomorrow**。
**时间状语从句中仍用一般现在时表将来**（When you arrive, I will be waiting）。"""},
            {"name": '过去将来时',
             "body": """结构：**would + 动词原形**（也可用 was/were going to）。
用法：**站在过去某个时间点看将来**（He said he would come）。
三类场景：**宾语从句（said/told/thought + would do）**、**间接引语**、**过去条件句的主句**（If I had time, I would go）。
对应关系：**will→would，is going to→was going to，shall→should**。
**判断标志：主句是过去时，从句说的是「在那之后」的事，就用过去将来时**。
**would 还可表过去习惯**（He would sit for hours），要看语境区分。"""},
            {"name": '时态的一致性',
             "body": '主从句时态要呼应。\n**主句过去时，从句用过去时态**。\n主句现在时，从句根据实际用时态。\n客观真理用一般现在时，即使主句是过去时。\n时态一致是语法的重要规则。\n宾语从句注意陈述语序与时态配合。'},
            {"name": '将来完成时',
             "body": '将来完成时表示将来某时刻前已完成的动作。\n**结构为 will have + 过去分词**。\n常与 by 引导的时间状语连用。\n如 I will have finished by then。\n将来完成时强调动作的完成。'},
            {"name": '时态的判定信号词',
             "body": '**时态题先找时间信号词，再定动作状态**。\nalready、just、yet、so far、in the past few years 常配现在完成时；ago、last week、in 1990 常配一般过去时。\nnow、at present、look 提示现在进行时；by the end of last year 提示过去完成时；by the end of next year 提示将来完成时。\n时间状语从句与条件状语从句中，主将从现：主句用将来时，从句用一般现在时。\n没有信号词时看上下文语境，特别注意两个动作的先后关系。'},
            {"name": '虚拟语气中的时态',
             "body": '虚拟语气按其所在时间分三类，时态整体向前推一步。\n**与现在事实相反：从句用过去式（be 用 were），could/might do**。\n与过去事实相反：从句用 had done，主句用 would have done。\n与将来事实相反：从句用 were to do 或 should do，主句用 would do。\n名词性从句中，suggest、demand、insist、require 等表示要求建议时，从句用 should do 且 should 可省略。\nwish 后的从句同样按时间前推，if only 用法相同。'},
        ]},
        {"name": '2.2 非谓语动词', "children": [
            {"name": '不定式',
             "body": '形式：to do、to be done、to have done。\n常作目的状语、宾语、定语、主语。\n**在 make、let、have 后作宾补时省略 to**。\n不定式表将来或未发生的动作。\n某些形容词后接不定式（glad to do）。'},
            {"name": '动名词',
             "body": '形式：doing、being done、having done。\n可作主语、宾语、表语、定语。\n**介词后必须用动名词**。\n常见只接动名词的动词：enjoy、finish、practise、mind、avoid。\n动名词作主语时谓语用单数。'},
            {"name": '分词',
             "body": """**现在分词 doing 表主动或进行；过去分词 done 表被动或完成**。
- **作定语**：单个分词前置（a sleeping baby），分词短语后置（the boy standing there）。
- **作状语**：**逻辑主语必须与句子主语一致**（× Walking home, the rain started）。
- **作宾补**：**see sb doing（正在进行）vs see sb do（全过程）**；have sth done（让别人做）。
- **表感受与特征**：**interesting（事物特征）vs interested（人的感受）**，同类还有 exciting/excited、tiring/tired。
**判断用 doing 还是 done：看逻辑主语与该动词是主动还是被动关系**。"""},
            {"name": '独立主格与with复合结构',
             "body": '独立主格：名词/代词+分词/不定式/形容词，与主句无语法连接。\n**with 复合结构：with+宾语+宾补，常作伴随状语**。\n示例：With the work finished, he went home。\n这种结构中宾补与宾语有逻辑关系。\n是写作提分的有效句式。'},
            {"name": '不定式与动名词的区别',
             "body": '不定式 to do 常表目的、将来，动名词 doing 常表习惯、一般。\n**forget/remember to do 表示忘记/记得要做（未做），doing 表示忘记/记得做过**。\nstop to do 停下来去做另一件事，stop doing 停止正在做的事。\nwant、hope、decide、plan 后接不定式，enjoy、finish、mind 后接动名词。\n介词后接动名词，如 be good at doing。'},
            {"name": '非谓语动词作定语',
             "body": '非谓语动词作定语时要注意与所修饰词的主被动关系和时态关系。\n**现在分词 doing 表主动、进行，过去分词 done 表被动、完成**。\n不定式 to do 表将来，常修饰抽象名词。\nThe boy standing there is my brother（主动进行）。\nThe bridge built last year is strong（被动完成）。'},
            {"name": '非谓语动词作宾语',
             "body": """只接不定式：**want、hope、decide、plan、expect、refuse、manage、promise、agree、offer**。
只接动名词：**enjoy、finish、mind、avoid、suggest、practise、admit、consider、look forward to**。
两者都可但意义不同：**remember/forget to do（还没做）vs doing（做过了）**；**stop to do（停下来去做）vs stop doing（停止做）**；**try to do（努力做）vs try doing（试着做）**；**regret to do（遗憾要告知）vs regret doing（后悔做过）**。
介词后一律用动名词：be interested in doing、succeed in doing、prevent sb from doing。
**allow/advise/permit + doing，但 + sb to do**（allow doing / allow sb to do），这是高频考点。"""},
            {"name": '非谓语动词作状语',
             "body": '非谓语动词作状语表示时间、原因、条件、伴随等。\n现在分词作状语表主动，过去分词作状语表被动。\n不定式作状语常表目的、结果。\n**逻辑主语要与句子主语一致**。\n独立主格结构有自己的逻辑主语，如 The work done, he left。'},
            {"name": '非谓语动词作主语',
             "body": """不定式作主语：**表具体、一次性、将来的动作**——To master a language takes years。
动名词作主语：**表一般性、习惯性、已存在的动作**——Reading broadens the mind。
**非谓语作主语，谓语一律用单数**（Two hours is enough）。
常用 it 作形式主语：**It is + adj. + to do**（It is important to learn English）；动名词多用 **It is no use doing**。
固定句型：**There is no + doing**（There is no telling what will happen）。
前后对称：**To see is to believe**（to do 对 to do），Seeing is believing（doing 对 doing），**一边用不定式一边用动名词算错**。"""},
            {"name": '非谓语动词的否定',
             "body": '**非谓语动词的否定在非谓语前加 not**。\n如 not to do、not doing、not done。\nnot knowing what to do。\n否定形式要紧跟非谓语。\n注意否定位置。'},
            {"name": '非谓语动词作宾语补足语',
             "body": '非谓语动词可作宾语补足语。\n感官动词后接现在分词或不定式。\n**使役动词后接不定式省略 to**。\nhave 后接过去分词表被动。\n选择看与宾语的关系。'},
            {"name": '非谓语动词的逻辑主语',
             "body": '非谓语动词的逻辑主语要与句子主语一致。\n不一致时用独立主格结构。\n现在分词逻辑主语是动作发出者。\n过去分词逻辑主语是动作承受者。\n**判断逻辑主语是使用非谓语的关键**。'},
            {"name": '非谓语动词的时态语态',
             "body": '非谓语动词有各种时态和语态形式。\ndoing 表主动进行，done 表被动完成。\nto do 表将来，to be done 表将来被动。\nhaving done 表主动完成。\nhaving been done 表被动完成。\n**选择形式看与逻辑主语的关系**。'},
            {"name": '非谓语的解题三步',
             "body": '第一步判断句子是否已有谓语：**若已有谓语，则另一个动词必须变为非谓语**。\n第二步确定逻辑主语：非谓语的逻辑主语通常是句子的主语或它修饰的名词。\n第三步判定关系与时间：主动用 doing，被动用 done，目的或将来用 to do，先发生用 having done。\n固定搭配要单独记，如 be used to doing（习惯于）、used to do（过去常常）、be devoted to doing。\n最后检查是否与句中其他成分重复或矛盾。'},
        ]},
        {"name": '2.3 从句', "children": [
            {"name": '定语从句',
             "body": """限制性定语从句：**紧跟先行词、无逗号、不可省略**，删掉句子意思不完整。
非限制性定语从句：**用逗号隔开、可删、不能用 that**，只作补充说明。
关系代词：**who（人，主格）、whom（人，宾格）、whose（所属）、which（物）、that（人或物，限定时用）**。
关系副词：**when=in/on which、where=in/at which、why=for which**。
**介词后只能用 whom 或 which**，不能用 that/who。
**只能用 that 的场合：先行词被 all/only/the first/最高级修饰，或先行词既有人又有物**。
**判 which 还是 where：看从句缺不缺主语或宾语——缺就用 which，不缺就用 where**。"""},
            {"name": '同位语从句与定语从句的区别',
             "body": """**同位语从句说明先行词的内容，定语从句修饰先行词**。
**关键区别在 that 是否作成分**：同位语从句的 that 不作任何成分、只起连接作用；定语从句的 that 必须充当主语或宾语。
**先行词不同**：同位语从句常跟 news、fact、idea、hope、belief、promise 等抽象名词。
**判断方法：把先行词与从句单独拿出来——能说「先行词就是从句的内容」的是同位语从句**。
同位语从句的 that **不能省略**；定语从句作宾语时 that 可省。
**which 不能引导同位语从句**（× the news which he won），这是常设的错项。"""},
            {"name": '状语从句的类型与连词',
             "body": """九类，按功能记：
- **时间**：when、while、as、before、after、until、as soon as。
- **条件**：if、unless、as long as、in case。
- **让步**：although、though、even if、while（虽）。
- **原因**：because、since、as（语气由强到弱）。
- **结果**：so…that、such…that；**目的**：so that、in order that。
- **比较**：than、as…as；**方式**：as if；**地点**：where。
**主将从现：时间与条件状语从句中用一般现在时表将来**（When he comes, I will tell him）。
**although 与 but 不能连用，because 与 so 不能连用**，这是最高频的中式英语错误。"""},
            {"name": '名词性从句的语序与引导词',
             "body": """名词性从句四种：主语从句、宾语从句、表语从句、同位语从句，**一律用陈述语序（主语＋谓语）**。
三类引导词：
- **that**：无实义、不作成分；**只在宾语从句中可省，主语/表语/同位语从句中不可省**。
- **whether / if**：表「是否」，不作成分。**介词后、句首主语从句、紧跟 or not、后接不定式，只能用 whether**。
- **连接代词/副词**：what、who、which、when、where、why、how，**在从句中必须充当成分**。

**what 与 that 的分水岭：从句缺主语/宾语/表语用 what，成分齐全用 that**。
**what = the thing that，本身含先行词，前面不能再有名词**。
**it 作形式主语**：It is + adj./n. + that 从句，避免头重脚轻（It is clear that he was right.）。
**同位语从句解释抽象名词的内容（news、fact、idea、belief、promise），that 不作成分**；定语从句的 that 必须作成分。"""},
        ]},
        {"name": '2.4 特殊句式', "children": [
            {"name": '倒装句',
             "body": """**完全倒装**：地点或方位状语置于句首、谓语为不及物动词时，**整个谓语提到主语前**（Here comes the bus.）。
**注意：主语是人称代词时不倒装**（Here it comes.）。

**部分倒装**：只把助动词 / 情态动词 / be 提到主语前，三类触发：
- **否定或半否定词置首**：never、hardly、seldom、little、not only…but also。
- **only + 状语（副词、介词短语、从句）置首**：Only then did I realize it.
- **so/such…that、neither/nor 置首**：So busy is he that…；**Neither/Nor do I 表「也不」，So do I 表「也」**。

**not until 置首时倒装的是主句，不是从句**（Not until he came did I leave.）。
**as 引导让步状语从句要倒装表语或状语**（Young as he is, he knows a lot.）。
**only 修饰主语时不倒装**（Only he knows it.）。"""},
            {"name": '强调句与省略',
             "body": """**强调句：It is/was + 被强调部分 + that/who + 句子其余部分**。
强调人可用 who 或 that，**强调时间、地点、原因等一律用 that**，不用 when / where / why。
**检验方法：去掉 It is/was 和 that，剩下能还原成完整句子才是强调句**。
**强调谓语用 do/does/did + 动词原形**（He did come yesterday.）。
**not…until 的强调式：It is/was not until…that…，that 后不再用否定**。

省略三处常见：
- **状语从句中「主语＋be」可省**（When (he was) asked, he kept silent.），条件是主从主语一致且含 be。
- **不定式保留 to、省去后面动词**（I'd like to, but…）。
- **比较结构与并列句省去重复成分**（He runs faster than I (do).）。
**省略不能造成歧义，被省成分必须能从上下文唯一还原**。"""},
            {"name": 'it 的用法与反义疑问',
             "body": """it 四类用法：
- **形式主语**：It is + adj./n. + to do / that 从句（It is important to keep calm.）。
- **形式宾语**：think/find/make + it + 宾补 + to do / that（I find it hard to believe.）。
- **指时间、天气、距离、环境**：It is 5 o'clock. / It is raining.
- **强调句中的 it** 无实义，见「强调句与省略」。

**反义疑问：前肯后否、前否后肯，助动词与时态与前句一致**。
- **含 never、hardly、seldom、little、few、nothing 视为否定，后面用肯定**。
- **I think/believe + 宾语从句，疑问部分与从句一致**；主语不是第一人称时与主句一致。
- **祈使句用 will you；Let's… 用 shall we；Let us… 用 will you**。
- **there be 句型用 be there**（There is a book, isn't there?）。
- **must 表推测按实际时态反问**（He must be tired, isn't he?）。"""},
            {"name": '虚拟语气的用法',
             "body": """**核心规则：与事实相反时，时态整体向前推一步**。
- **与现在相反**：if + 过去式（be 一律用 were），主句 **would/could/might + do**。
- **与过去相反**：if + had done，主句 **would/could/might + have done**。
- **与将来相反**：if + were to do / should do / 过去式，主句 **would/could/might + do**。

**错综时间条件句：从句与主句各按自己的时间定形式**（If you had studied harder, you would be in a better school now.）。
**省略 if 时把 had/were/should 提前构成倒装**（Were I you, I would…）。
**suggest、demand、insist、require、order 表建议要求命令，从句用 (should) + do，should 可省**。
**wish / if only / as if / would rather 后的从句同样按时态前推**。
**It's (high) time + 从句用过去式**（It's time we left.）。"""},
            {"name": '主谓一致的规则',
             "body": """三条原则，按顺序判：
- **语法一致**：主语的单复数决定谓语。
- **意义一致**：**family、team、class、audience 等集合名词，指整体用单数、指成员用复数**。
- **就近一致**：**either…or、neither…nor、not only…but also、or、there be 连接主语时，谓语与最近的主语一致**。

高频考点：
- **不定式、动名词、主语从句作主语，谓语用单数**。
- **and 连接两个名词指同一人/物时用单数**（The writer and teacher is…）；**each/every/many a + 单数名词用单数**。
- **「分数 / 百分数 / the rest / most / some / all + of + 名词」谓语随 of 后的名词**。
- **a number of + 复数（谓语复数）；the number of + 复数（谓语单数）**。
- **with、as well as、together with、besides 引出的是附加成分，不影响谓语**（Tom as well as his friends is…）。
- **more than one + 单数名词，谓语用单数**。"""},
            {"name": '省略句的用法',
             "body": """**省略的前提：被省成分能从上下文唯一还原，不产生歧义**。
四类固定省略：
- **状语从句**：主从主语一致且含 be，可省「主语＋be」（When (I was) asked, I kept silent. / If (it is) necessary.）。
- **并列句**：省去重复的主语、谓语或宾语（I like tea, and she (likes) coffee.）。
- **比较结构**：than / as 后省去重复部分（He runs faster than I (do).）。
- **不定式**：保留 to、省去后面的动词（—Will you go? —I'd like to.）。

**答语中 so / not 替代整个从句**：I think so. / I hope not.
**so do I（也）、neither/nor do I（也不），助动词要与前句时态一致**。
**省略后检查剩下成分是否仍搭配**：介词不能丢、时态与单复数不能串。
**不定式的完成式保留 to have**（—Have you finished? —I hope to have.）。"""},
            {"name": '祈使句',
             "body": """**祈使句省略主语 you，以动词原形开头**，表命令、请求、建议、劝告。
否定式：**句首加 Don't 或 Never**（Don't be late.）；**强调式在动词前加 Do**（Do be careful.）。
**Let 型祈使句**：Let's + 动词原形（含对方）；Let us / Let him + 动词原形。
**「祈使句 + and/or + 陈述句」相当于条件句**：
- **祈使句 + and + 将来时**：Work hard, and you will pass.（= If you work hard…）
- **祈使句 + or + 将来时**：Hurry up, or you'll be late.（= If you don't…）

**无动词祈使句**：This way, please. / No smoking. / Hands up.
**祈使句的反义疑问用 will you，Let's 用 shall we**。
**陈述句、疑问句改祈使句要去掉主语和助动词，只留动词原形**。"""},
            {"name": '感叹句',
             "body": """两种结构：
- **What + (a/an) + adj. + n. + 主语 + 谓语！**（What a beautiful flower it is!）
- **How + adj./adv. + 主语 + 谓语！**（How beautiful the flower is!）
**感叹句用陈述语序，不倒装**。
**选择标准只有一条：What 修饰名词，How 修饰形容词或副词**。
**不可数名词与复数名词前不加 a/an**：What fine weather! / What beautiful flowers!
常见省略：What a pity! / How nice!
**易错：How 后面不能直接跟名词**（× How a beautiful flower）。"""},
            {"name": 'there be 与省略句',
             "body": """**there be 表「存在」，be 与后面第一个名词一致（就近原则）**：There is a book and two pens.
**可与情态动词、seem、happen、used to 连用**：there seems to be / there must be / there used to be。
**there be 的时态、疑问、否定都由 be 承担**：There isn't any water. / Is there…?
**there be 与 have 的分界：there be 表存在，have 表所属**，不能混用（× There has a book.）。
**there be + 主语 + doing / to do / done** 作后置定语，注意主动被动。
**省略句**：When (he was) asked, he kept silent. / I like it better than she (does).
**so do I / neither (nor) do I 表「也 / 也不」，助动词与前句保持一致**。"""},
        ]},
    ]},
    {"name": '第三板块 题型策略', "children": [
        {"name": '3.1 阅读理解', "children": [
            {"name": '细节理解题',
             "body": '先读题干，确定关键词，再回原文定位。\n**注意同义替换是正确答案的常见形式**。\n排除偷换时间、地点、对象、程度的选项。\n部分题需跨段整合信息。\n顺序原则：题目顺序与文章顺序基本一致。'},
            {"name": '主旨大意与推理判断',
             "body": """**主旨题看首段、各段首句和末段，不要只看出现次数最多的词**。
标题要覆盖全文、简洁、点出对象，不能只是某一段的细节。
排除两类干扰项：过于具体（只概括一段）、过于宽泛（超出原文范围）。
推理判断必须基于原文，**原文照抄的选项往往不是答案，推断要有一步转化**。
态度题抓形容词、副词与转折词的褒贬：positive／critical／neutral／doubtful。
问「写作目的」看文体与首末段呼吁，问「出处」看用词与格式线索。"""},
            {"name": '词义猜测与篇章结构',
             "body": '词义猜测看上下文：定义、举例、同义反义、构词。\n**注意破折号、冒号后常是解释**。\n篇章结构题要把握文章的组织方式。\n注意逻辑连接词：however、therefore、moreover。\n七选五题要注意代词与关键词的呼应。'},
            {"name": '推理判断题的解题技巧',
             "body": """推断题问的是「没直说但能推出」，答案不能与原文矛盾，也不能过度延伸。
**but、however、yet、in fact 之后常是作者真正立场，是推断的关键依据**。
分清四类设问：infer/imply（隐含）、conclude（结论）、attitude（态度）、source（出处）。
错误项特征：与原文直接矛盾、把可能说成必然、加入原文没有的比较或因果。
**推断作者态度看用词感情色彩，不要用常识或自己的好恶代进去**。
先定位题干关键词所在句，再看它前后各一句，答案通常在这三句内。"""},
            {"name": '作者态度题的判断',
             "body": '作者态度题要求判断作者对某事物的态度或观点。\n找表达态度的形容词，如 positive、negative、critical。\n**注意转折词后的态度，常是作者真实态度**。\n区分作者态度和文中人物的态度。\n选项中的态度词要结合语境判断褒贬。'},
            {"name": '阅读速度的提升',
             "body": '提高阅读速度要扩大视幅，减少回读。\n**按意群阅读，不要逐词阅读**。\n先看题目再读文章，带着问题阅读。\n抓住关键词和主题句，跳过细节。\n平时多阅读，积累词汇，提高理解速度。'},
            {"name": '阅读理解的同义替换',
             "body": '阅读理解选项常对原文进行同义替换。\n识别同义替换是解题的关键。\n如 important→significant，big→large。\n注意词性变化和句式转换。\n**答案往往是原文的同义表达**。'},
            {"name": '阅读理解的干扰项特征',
             "body": '阅读理解干扰项有常见特征。\n偷换概念、张冠李戴。\n以偏概全、无中生有。\n过度推断、绝对化表述。\n**识别干扰项有助于排除错误选项**。\n选择与原文意思最相符的选项。'},
            {"name": '阅读理解的长难句分析',
             "body": """三步拆句：
1. **数谓语动词**：一句话有几个谓语，就有几个分句。
2. **定主干**：主语 + 谓语 + 宾语或表语，其余全是修饰。
3. **剥修饰**：定语从句、状语从句、分词短语、插入语，逐层挂回主干。
四个断句信号：**that/which/who 引导定语从句，because/although/when 引导状语从句，逗号后常是插入语，and/but/or 连接并列成分**。
**主语后跟一长串修饰时，先把修饰整体括起来，句子立刻变短**。
**读长句的目标不是翻译每个词，而是分清「谁做了什么」**。"""},
            {"name": '阅读理解的题文顺序',
             "body": """两种策略，按题型选：
- **先题后文**：先看题干圈关键词，带着问题扫读定位，**适合细节题、数字题**。
- **先文后题**：通读把握结构再答题，**适合主旨题、标题题、态度题**。
细节题：**回原文定位 + 识别同义替换**（正确项几乎不原词照抄，而是换说法）。
主旨题：**看首段、末段与每段首句**，警惕只覆盖一段内容的「以偏概全」选项。
**错误项四类：偷换概念、无中生有、范围扩大或缩小、正话反说**。
**时间分配：一篇不超过 7 分钟，卡住先跳过，回头再定位**。"""},
            {"name": '七选五的解题步骤',
             "body": '先读首段与各段首句，把握文章结构与话题。\n再看选项中的线索词：代词、连接词、重复出现的名词。\n根据空格位置判断功能：段首多为概括句，段中为衔接句，段末为总结句。\n利用指代一致、逻辑一致（转折、因果、并列）排除干扰项。\n**选完后通读全文，检验语义与语法是否连贯**。'},
            {"name": '阅读理解的文章结构',
             "body": '阅读理解要把握文章结构。\n总分、分总、总分总结构。\n**每段有主题句**。\n理清结构有助于理解。\n把握段落间的逻辑关系。'},
            {"name": '推理判断题的常见设错',
             "body": '推理判断题的错项主要有四种：过度推断、无中生有、张冠李戴、偷换程度。\n过度推断是超出原文合理范围，如原文说某措施有帮助，选项说它是唯一办法。\n无中生有是选项信息原文完全没提，常见于因果关系的强行添加。\n张冠李戴是把 A 的观点安到 B 身上，或把研究者的看法说成作者的看法。\n偷换程度是把 sometimes 换成 always、把 may 换成 must。\n**做题时坚持「一步推理」原则**：答案必须能由原文直接推出，不需要额外常识补足。'},
        ]},
        {"name": '3.2 完形填空与语法填空', "children": [
            {"name": '完形填空解题策略',
             "body": '**先快速通读全文把握大意，再逐空作答**。\n注意前后语境的逻辑关系。\n关注复现词（同义、反义、同根词）。\n常识与文化背景也有帮助。\n做完后再通读一遍检查连贯性。'},
            {"name": '语法填空',
             "body": '有提示词：注意词性转换与形式变化。\n无提示词：多考冠词、介词、连词、代词。\n**括号内动词要判断作谓语还是非谓语**。\n名词注意单复数，形容词注意比较级。\n大小写与拼写错误一律不得分。'},
            {"name": '短文改错',
             "body": """短文改错共约 10 处错误，**分布通常是 1 处多词、1 处缺词、8 处错词**，改动方式只有加、删、改三种。
高频考点五类：**冠词、介词、时态、主谓一致、非谓语**；另有代词指代、形容词副词混用、连词逻辑。
四步做题：**通读全文抓大意与时态 → 逐句找错 → 按上述类别逐项复查 → 复读检查是否通顺**。
**一致性检查最出活**：时态是否通篇一致、代词指代是否明确、主谓是否一致、逻辑连词是否与句意相符。
**每句一般只有一处错误**，一句话改两处就要重新判断；改动不能改变原意，也不能只加词不删词。
修改符号要规范：**漏词用 ∧ 补上、多词用 \\ 划掉、错词在下方写出正确词**。"""},
            {"name": '完形填空的复现技巧',
             "body": '完形填空常考查词汇复现，包括原词复现、同义复现、反义复现。\n做题时注意前后文反复出现的关键词。\n**同义复现：用不同词表达相同或相近的意思**。\n反义复现：前后文形成对比，提示答案。\n做完后通读全文，检查答案是否符合语境和逻辑。'},
            {"name": '完形填空的情感线索',
             "body": """完形的答案多由**情感走向**决定，而不是由语法决定。
三步抓线索：
- **首段定基调**：第一段往往交代心情底色（焦虑、期待、失落）。
- **标记情感词与转折**：happy→upset、but、however、instead 之后情感常反转，**答案多落在反转之后**。
- **看人物弧线**：从紧张到释然、从自私到担当，空格处的词要符合当下这个阶段。
**同段前后句的情感倾向必须一致**；选出词后回读一遍，情绪不通就是错的。
**成对记高频情感词**：relieved/guilty、embarrassed/proud、desperate/hopeful。
**别把「语气委婉」当成「情感平淡」**，后者往往是转折的铺垫。"""},
            {"name": '完形填空的逻辑关系',
             "body": '完形填空要注意句子间的逻辑关系。\n**转折关系：but、however、although**。\n因果关系：because、so、therefore。\n递进关系：besides、moreover。\n对比关系：while、on the contrary。'},
            {"name": '完形填空的固定搭配',
             "body": '完形填空常考固定搭配。\n如动词短语、介词短语、形容词短语。\nlook forward to、be used to、take part in。\n**掌握常见固定搭配有助于快速解题**。\n平时多积累固定搭配和习惯用法。'},
            {"name": '语法填空的考点分布',
             "body": '有提示词：考查词性转换（名、动、形、副）、非谓语动词、时态语态、比较级。\n无提示词：多填介词、冠词、代词、连词、关系词。\n判断依据：空格前后的词性与句子结构。\n固定搭配与习惯用法要靠积累，如 in addition to、be responsible for。\n**每空只填一词（少数情况填两词），注意大小写**。'},
        ]},
        {"name": '3.3 听力与书写规范', "children": [
            {"name": '听力笔记与干扰项排除',
             "body": '边听边记关键词，用缩写与符号提高记录速度。\n注意数量信息与对应单位的匹配。\n**干扰项常是原文出现但不符合题意的信息**。\n注意说话人的语气与言外之意。\n对话题要抓住人物关系与谈话目的。'},
            {"name": '听力理解的关键技巧',
             "body": """四步法：
1. **播放前抢读选项**：圈出选项差异，**差异处就是你要听的点**。
2. **抓转折词**：but、however、actually 之后多为真正答案，前面常是干扰。
3. **速记关键信息**：数字、时间、地点、人物关系，用符号记。
4. **第二遍只核对不确定的题**，不在已确定的题上耗时。
**听不清一个词就放弃它，跟上语流抓大意**，停下来回想会连丢三题。
**预判题型**：选项都是地点就重点听 where，选项都是数字就重点听价格与时间。
**注意同义替换**：原文 not cheap，选项可能写 expensive。"""},
            {"name": '听力高频场景词汇',
             "body": """按场景记「听到就能预判下文」的词：
- 餐厅：menu /ˈmenjuː/、order /ˈɔːdə(r)/、reserve /rɪˈzɜːv/、bill /bɪl/、tip /tɪp/、for here or to go。
- 机场车站：boarding pass /ˈbɔːdɪŋ pɑːs/、delay /dɪˈleɪ/、platform /ˈplætfɔːm/、one-way / round-trip ticket。
- 医院：appointment /əˈpɔɪntmənt/、symptom /ˈsɪmptəm/、prescription /prɪˈskrɪpʃn/、take medicine /teɪk ˈmedsn/。
- 学校：lecture /ˈlektʃə(r)/、assignment /əˈsaɪnmənt/、deadline /ˈdedlaɪn/、scholarship /ˈskɒləʃɪp/。
- 购物：discount /ˈdɪskaʊnt/、refund /ˈriːfʌnd/、try on、**out of stock** /ˌaʊt əv ˈstɒk/。
**听力不是逐词听懂，而是抓场景词预判下文**：听到 refund，后面多半接「为什么不能退」。
数字题注意 **-teen 与 -ty 的重音差别**（thirteen /ˌθɜːˈtiːn/ 重音在后，thirty /ˈθɜːti/ 重音在前）。"""},
            {"name": '卷面与书写规范要点',
             "body": '字母大小写规范：句首、专有名词、标题实词首字母大写。\n字迹清晰、间距均匀，避免涂改与大段划掉。\n书面表达分段清晰，一般三段：开头点题、主体展开、结尾总结。\n标点用英文标点：句号用实心点，逗号后空一格。\n**控制词数，写在答题区域内，超出部分无效**。'},
            {"name": '听力的人物关系判断',
             "body": """三类线索：
- **称呼**：Mr./Mrs./Ms.、sir、madam、honey、dear、doctor、professor——**称呼直接暴露身份差距**。
- **场景词**：menu/order（服务员与顾客）、prescription/symptom（医患）、assignment/deadline（师生）、interview（面试官与应聘者）。
- **语气**：正式客气多为服务或上下级关系，随意亲昵多为家人朋友。
**抓第一句的称呼加一个场景词，基本就能定关系**。
**注意关系可能中途变化**：开头是同事、结尾成了客户，要听最后一句。
**选项常给近义关系（teacher/student 与 interviewer/interviewee），必须靠场景词区分**。"""},
            {"name": '听力的地点判断',
             "body": """**地点题靠场景词判断，不靠等一个地名出现**。
- 餐厅：menu、order、bill、tip、reserve a table。
- 医院：doctor、fever、medicine、prescription、appointment。
- 学校：exam、assignment、lecture、professor、library。
- 机场／车站：flight、board、gate、delay、platform、ticket。
- 银行／邮局：account、deposit、withdraw、parcel、stamp、postage。
同音与近音词是主要陷阱（如 bill / pill、menu / money）。
**先抓说话人身份与职业词，再由身份反推场所**，比逐词听更稳。"""},
            {"name": '听力的推理判断',
             "body": '听力中要能进行推理判断。\n根据说话人的语气判断态度。\n根据上下文推断对话含义。\n判断对话发生的场景和人物关系。\n**推理要基于听到的信息，不能凭空猜测**。\n注意言外之意和隐含信息。'},
            {"name": '数字与时间信息的记录',
             "body": """四类：**价格、时间、日期、电话号码**，必须边听边记阿拉伯数字。
价格干扰项多：**half price、discount、20% off、for two、total**，听到 buy two 记得乘 2。
**时间牢记 past 与 to**：a quarter past nine 是 9:15，a quarter to nine 是 8:45。
日期：**英式 日/月/年，美式 月/日/年**；听清 the fifth of May 与 May fifth。
**先看疑问词再答**：how much 问价格、how long 问时长、how often 问频率、when 问时间点。
**数字题几乎都要运算**（两人各一张、打八折、迟到 15 分钟），先记数字最后再算。"""},
        ]},
    ]},
    {"name": '第四板块 写作', "children": [
        {"name": '4.1 应用文写作', "children": [
            {"name": '常见应用文体裁',
             "body": """- 书信：**Dear Sir/Madam → 三段（说明来意、展开细节、期待回复）→ Yours sincerely, Li Hua**。
- 通知：**标题 Notice 居中**，正文交代「何事、何时、何地、参加人、要求」，落款单位与日期。
- 演讲稿：**称呼 → 主题句 → 两三个论证（举例或数据）→ 号召收尾**，多用 we 与呼告。
- 电子邮件：**主题行要具体**（Application for the Volunteer Program），正文一段一件事，结尾礼貌收束。
- 日记：**日期、星期、天气 → 正文 → 感受**，主要用过去时。
**共同要求：格式分不能丢（称呼、落款、日期），要点齐全比辞藻更重要**；字数不足时补细节，不要重复空话。"""},
            {"name": '评分要点',
             "body": """五条评分维度：
- **内容要点齐全**：覆盖题目要求的全部信息点，**漏一个要点就降档**。
- **词汇与语法**：准确优先，**高级表达适当加分但不强求**。
- **连贯性**：靠衔接词与指代保持顺畅。
- **得体性**：称呼与语气符合文体和对象。
- **书写与字数**：一般 80 词左右，**少于要求会扣分**。
**提分优先级：要点齐全 > 语法正确 > 表达高级**，顺序不能颠倒。
**检查清单：时态、主谓一致、名词单复数、拼写**，这四项占失分的大半。"""},
            {"name": '书信类应用文格式',
             "body": '书信格式包含称呼、正文、结束语、署名和日期五部分。\n称呼顶格写，后用逗号；正文每段开头可缩进。\n**结束语常用 Yours sincerely（熟人）/ Yours faithfully（正式）**。\n正文开头常用 I am writing to... 表明写作目的。\n结尾常用 Looking forward to your reply 表达期待。'},
            {"name": '通知类应用文的写法',
             "body": '**通知类应用文要先写标题 Notice，再写正文**。\n正文开头交代事由，用 We are going to... 等句式。\n中间说明时间、地点、参加对象和具体要求。\n结尾表达期待，如 Please be on time / Everyone is welcome。\n落款写发布单位和日期，语言简洁、格式规范。'},
            {"name": '邀请信的写法',
             "body": '**邀请信开头说明写信目的**，用 I am writing to invite you to...。\n正文说明活动的时间、地点和内容。\n表达期待对方参加，用 We would be honored if you could come。\n结尾用 Looking forward to your reply。\n语气要诚恳礼貌，格式规范。'},
            {"name": '感谢信的写法',
             "body": '**感谢信开头表达感谢**，用 I am writing to express my thanks for...。\n正文说明感谢的原因和具体事例。\n表达感激之情，如 I really appreciate your help。\n结尾再次感谢，用 Thanks again for your kindness。\n语气真诚，格式规范。'},
            {"name": '建议信的写法',
             "body": '建议信开头说明写信目的，用 I am writing to give some suggestions。\n正文提出建议，用 Firstly、Secondly 等连接词。\n**每条建议要具体、可行**。\n结尾表达希望建议被采纳，用 I hope my suggestions will be helpful。\n语气委婉，格式规范。'},
            {"name": '道歉信的写法',
             "body": """三段式：**致歉点题 → 说明原因并给补救 → 再次致歉请求谅解**。
开头 I am writing to apologize for… / I am terribly sorry that…。
原因只写一句，别辨解；补救要具体：I will make it up to you / Could we…?。
结尾 Once again, I apologize for any inconvenience. / I hope you can accept my apology.
**语气诚恳、用第一人称，不要写成通知或解释信**。
注意格式：称呼（Dear…,）、落款（Yours sincerely, Li Hua）不能漏。
时态：事由用过去时，补救用将来时。"""},
            {"name": '演讲稿的写法',
             "body": "演讲稿开头要有称呼和问候语。\n如 Good morning, everyone。\n正文提出观点，展开论述。\n用 First、Second 等连接词使结构清晰。\n**结尾发出号召**，用 Let's... 或 Thank you。"},
            {"name": '请假条的写法',
             "body": '请假条是向老师或领导请假的便条。\n**写明请假原因和天数**。\n语气诚恳，理由合理。\n格式有称呼、正文、落款（署名和日期）。\n请假条要简洁明了。\n常用开头 I am writing to ask for leave...，结尾表示感谢。'},
            {"name": '高分句式与衔接词',
             "body": """开篇：I am writing to inform you that…／It is a great pleasure to…
衔接：firstly、besides、what is more、however、as a result、in conclusion。
高级结构（每篇用两三个即可）：**定语从句、非谓语作状语、倒装（Not only…but also）、强调句（It is…that…）**。
**避免全篇简单句堆砌，长短句结合**。
结尾：I would appreciate it if you could…／Looking forward to your reply。
**忌堆砌生僻词**：用错的高级词比用对的基础词扣分更多。
**句式多样性的判断标准：一篇里至少有三种不同结构**。"""},
            {"name": '倡议书的写作要点',
             "body": """结构四段：
1. **标题**：倡仪书／A Letter of Appeal，或直接写主题（Let's Protect Our Campus）。
2. **称呼与背景**：说明为什么要倡议（现状 + 问题），**用数据或现象增强说服力**。
3. **具体倡议**：**分点写（Firstly/Secondly/Finally），每点一个可执行动作**。
4. **号召与落款**：呼吁行动 + 署名与日期。
**语言要有感染力：多用 we、祈使句、情态动词 should/must**。
**倡议内容必须可执行**（「随手关灯」比「爱护环境」有效）。
**格式分不能丢：标题、称呼、落款、日期**。"""},
            {"name": '投诉信的写法',
             "body": '投诉信开头说明投诉的问题，用 I am writing to complain about。\n正文描述问题的具体情况，包括时间、经过和影响。\n提出解决方案或合理要求，表达期待处理。\n**语气要礼貌而坚定，不卑不亢**。\n结尾用 I look forward to your reply，投诉信格式规范。'},
            {"name": '电子邮件的写作',
             "body": '电子邮件由主题、称呼、正文、结束语、署名组成。\n**主题行简洁明确，概括邮件内容**。\n称呼用 Dear...，正文简明扼要。\n语气要得体，根据对象调整。\n电子邮件格式规范，便于沟通。'},
            {"name": '申请信的写作',
             "body": '申请信是向对方申请职位或机会的书信。\n开头说明申请目的，用 I am writing to apply for。\n**正文介绍自己的情况、优势和申请理由**。\n表达对机会的重视和期待。\n结尾用 Looking forward to your reply 表达期待。'},
        ]},
        {"name": '4.2 读后续写', "children": [
            {"name": '常见问题与改进',
             "body": '**避免情节跳跃、逻辑断裂，两段之间要有承接句**。\n避免与原文内容重复照抄，续写必须有所发展。\n避免语法错误与中式英语，多用学过的固定搭配。\n字数控制在规定范围内，一般每段 60~80 词。\n书写工整、分段清晰，时态与原文保持一致。'},
            {"name": '读后续写的写作策略',
             "body": '读后续写要先读懂原文情节、人物和主题，再续写两段。\n**续写内容要与原文情节衔接自然，符合人物性格**。\n注意每段首句已给出，续写要围绕首句展开。\n描写要具体，用动作、心理、环境描写使故事生动。\n结尾要点题升华，与原文主题呼应，不要出现新人物新情节。'},
            {"name": '读后续写的语言表达',
             "body": """**全文时态一致，读后续写一般用一般过去时**，与原文保持一致。
用非谓语、状语从句、with 复合结构使句子有长短变化，别全用简单句。
细节代替评价：写 his palms were icy，不写 he was very afraid。
**段与段之间要有过渡（时间、地点、情绪），保持故事连贯**。
用词避免重复：said / replied / added / murmured 交替，人称与视角不跳。
结尾回扣原文的关键物件或标题，完成情感升华，不要另起新人物。
点与原文语言风格一致，别突然换成书面议论腔。"""},
            {"name": '读后续写的环境描写',
             "body": '环境描写能烘托气氛、衬托人物心情。\n用感官描写（视觉、听觉、嗅觉）使环境生动。\n环境描写与故事情节和人物心情相呼应。\n如阴雨天烘托悲伤心情，阳光明媚烘托喜悦。\n**环境描写要简洁，不宜过多，服务主题**。'},
            {"name": '读后续写的人物塑造',
             "body": '读后续写要通过描写塑造人物形象。\n用动作描写表现人物行为，用心理描写表现内心。\n用语言描写表现人物性格，用外貌描写勾勒形象。\n**人物性格要与原文一致，不能前后矛盾**。\n通过人物在事件中的表现体现主题。'},
            {"name": '读后续写的主题把握',
             "body": """主题分三层定：
1. **看首段与末段**——核心情感往往就写在那里（亲情、成长、勇气、善意）。
2. **看人物的变化**——他从什么状态走到什么状态，这就是主题。
3. **看反复出现的意象或标题**——那通常是主题的具象化。
续写必须**承接同一主题**，不能另起一个道理。
**点题落在细节上**：不写「我懂得了坚持」，写「我把那双磨破的鞋收进柜子，第二天照样五点起床」。
**价值取向必须积极**：冲突可以尖锐，但结尾要落在理解、成长或担当上。
**最常见的跑题：把「亲情」写成「成功的励志故事」**。"""},
            {"name": '读后续写的对话描写',
             "body": '对话描写能推动情节发展、刻画人物。\n**对话要符合人物身份和性格**。\n用恰当的引语动词，如 said、replied、shouted。\n对话中穿插动作和神态描写，使画面生动。\n对话要简洁，避免冗长。'},
            {"name": '读后续写的语言衔接',
             "body": """三层衔接，缺一层就不连贯：
- **句间**：then、suddenly、before I could react、as a result。
- **段间**：第一段结尾留一个动作或悬念，第二段开头直接承接，不另起话题。
- **意象呼应**：开头的物件、天气、动作，结尾再出现一次。
**时态一致：续写统一用一般过去时，回忆部分可用过去完成时**，时态跳会大量扣分。
**主语一致：一段之内不要频繁换主语**，视角乱了读者会跟丢。
**长短句交替：紧张处用短句，抒情处用长句**，节奏本身就是衔接。"""},
            {"name": '读后续写的动作描写',
             "body": """**动作要拆成连续的小动作（动作链），一个笼统动词撑不起画面**。
用精准动词代替泛动词：go → rush/stumble/creep，look → glance/stare/gaze，say → whisper/murmur/exclaim。
用非谓语与 with 复合结构写伴随：With her hands trembling, she unfolded the letter.
动作配身体反应（手抖、屏息、僵住）比直接写 afraid / nervous 更有感染力。
**动作链要按时间顺序排，一句三四个动词即可，别堆成一长串**。
动作必须推动情节：每个动作后要接结果或转向，否则就是流水账。
与所给段落首句衔接，先接住上文的情绪再往下写。"""},
            {"name": '读后续写的细节描写',
             "body": """本条只讲**选哪些细节、放几处**；动作链、环境、对话的具体写法各有专条。
**一条细节只服务一个目的**：推动情节、显出性格或衬出情绪，三者都不沾就删掉。
**每段 2~3 处细节足够**，多了会变成流水账，挤掉情节推进。
五感里**优先写动作与身体反应**（手抖、屏息、僵住），视觉次之，嗅觉触觉点缀一两处即可。
**结论别直接喊出来，用具体动作或物件承载**：情绪、感动、成长都靠画面让读者自己读出（句式示范见「读后续写的语言表达」）。
**禁止形容词堆砌和无关细节**（为写景而写景、反复描写天气）。
自检法：**删掉这句，情节与人物还成立吗？成立就删**；留下的细节要能在结尾再呼应一次。"""},
            {"name": '读后续写的结尾技巧',
             "body": '读后续写的结尾要简洁有力。\n**结尾点明主题，升华中心**。\n与开头呼应，结构完整。\n可含蓄留白，引发思考。\n避免冗长拖沓的结尾。\n结尾常回归情感或感悟，一两句点睛即可。'},
            {"name": '读后续写的情节起伏',
             "body": """两段续写的通用骨架：**第一段解决眼前危机，第二段情感升华**。
制造起伏四个手段：
- **设置障碍**：目的刚要达成被打断（车坏了、门锁了）。
- **误会与反转**：先给出坏结果，再揭示真相。
- **压缩时间**：用 suddenly、at that moment、before I could react 提速。
- **收在细节**：不写「我很感动」，写「我把那张纸折好，放进胸前的口袋」。
**忌平铺直叙「做完这件事就回家了」——每段至少一次转折**。
结尾用同一个意象或同一句话与开头呼应，**比空喊感悟得分高**。"""},
            {"name": '续写的情节构思',
             "body": """本条只讲**动笔前的规划**；制造起伏、语言衔接、动作描写的具体写法各有专条。
读原文先圈四样：**人物关系、当前冲突、反复出现的伏笔、两段已给出的段首句**。
**两段分工**：第一段承接冲突并解决眼前问题，第二段收束情感、回扣主题；**中段之间要有一句承接**。
列提纲：每段写 2~3 个关键动作 + 1 句点题，先写成 4 句中文骨架再动笔。
**不引入新人物、不新增大事件、不突然跳转时间地点**，情节必须能从原文推出来。
**段首句已经给定，续写第一句要接住它的情绪和动作**，不能另起话题。
收尾检查：字数、时态、与原文伏笔是否至少呼应两处。"""},
            {"name": '读后续写的时间线索',
             "body": """本条讲**时间轴怎么排**；句间连接词的具体用法见「读后续写的语言衔接」。
动笔前先定位**原文故事发生在哪一天的什么时刻**，两段续写通常紧接其后，**控制在几小时到一天内，不要跳几天**。
时间词分三层用：**瞬间**（suddenly、at that moment）、**稍后**（later、after a while）、**跨日**（the next day）。
**回忆、插叙要改用过去完成时并明确标记**，否则读者会以为是顺叙。
**时态统一用一般过去时**，与时间线配套；一段之内时间不能倒着走。
用时间词分段推进能让层次清楚，但**别每句都加时间状语**，堆多了反而拖慢节奏。
易错：**段首句已给的时间信息被忽略**、把「第二天」与「当天傍晚」混在一个段落里。"""},
            {"name": '读后续写的伏笔',
             "body": """伏笔是原文故意留下、等你接的线头，**找到它等于找到情节方向**。
三类最常见：
- **反复出现的物件**（围巾、钥匙）——续写里让它再次出现并起作用。
- **未完成的动作或未说出口的话**（他张了张嘴）——续写里把它完成。
- **反常的细节**（人物的异样表情、天气突变）——往往是转折依据。
操作：**动笔前把原文最后两段的具体细节圈出来，至少挑两处呼应**，一段开头呼应、一段结尾回应。
**只写「他终于明白了」不算呼应**，要让具体物件或动作再次出现。"""},
            {"name": '读后续写的悬念设置',
             "body": """悬念是**续写自己埋下、本段不解答的疑问**；与「伏笔」不同——**伏笔是原文给的线头，悬念是你新设的钩**。
三种可用类型：
- **信息差**：读者知道而人物不知道（或反过来），如「她没看见门后的影子」。
- **物件**：一个反复出现却未说明用途的东西，留到第二段揭示。
- **动作中断**：他张了张嘴、手停在半空，用未完成动作制造期待。
**常在段末最后一句设悬念**，第二段开头必须直接承接并把它解决掉。
**悬念必须在两段内回收**，结尾还留着新疑问就等于没写完；也不能靠「突然出现陌生人」硬造。
一段一个悬念即可，**悬念太多会挤掉情感升华的篇幅**。"""},
            {"name": '续写的高频情节模板',
             "body": '**读后续写的情节推进讲究「冲突—转折—解决—感悟」四步**。\n第一段通常承接原文悬念，写人物遇到困难或做出决定，末尾留一个转折点给第二段。\n第二段解决冲突，写出行动与结果，最后用一两句心理描写点出成长或感悟。\n常用情感线索：紧张—挣扎—释然，避免全程平铺直叙。\n动作与神态描写要具体，如 rushed、stared、let out a long breath、a lump rose in my throat。\n主题句要自然，从事件提炼道理，如 It was then that I realized kindness costs nothing but means everything.'},
        ]},
    ]},
]
