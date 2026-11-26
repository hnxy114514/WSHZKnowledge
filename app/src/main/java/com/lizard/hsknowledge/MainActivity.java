package com.lizard.hsknowledge;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.BitmapShader;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.Layout;
import android.text.Spannable;
import android.text.Spanned;
import android.text.SpannableStringBuilder;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.ReplacementSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.text.style.SubscriptSpan;
import android.text.style.SuperscriptSpan;
import android.text.style.TypefaceSpan;
import android.text.style.UnderlineSpan;
import android.util.Base64;
import android.util.TypedValue;
import android.view.ActionMode;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.ViewOutlineProvider;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.io.StringWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 腕上知识汇总：九科知识点 + 数学公式排版 + 重点高亮 + APK 自动更新。
 */
public class MainActivity extends Activity {

    // ---------- 主题色 ----------
    private static final int C_BG = 0xFF000000;      // 全局背景：纯黑
    private static final int C_CARD = 0xFF151515;    // 卡片
    private static final int C_PILL = 0xFF1C1C1C;    // 按钮/列表项
    private static final int C_PRIMARY = 0xFF4FC3F7; // 主色（青蓝）
    private static final int C_HI = 0xFFFFC107;      // 重点高亮（琥珀金）

    private static final String REPO = "hnxy114514/WSHZKnowledge";
    private static final String RELEASES_API = "https://api.github.com/repos/" + REPO + "/releases/latest";
    /** 测试通道用：整个 Release 列表（GitHub 的 latest 接口会排除预发布，列表不会）。 */
    private static final String RELEASES_LIST_API = "https://api.github.com/repos/" + REPO + "/releases";
    private static final String RELEASE_DL = "https://github.com/" + REPO + "/releases/download/";
    /** 国内直连 GitHub 不稳，逐个尝试以下加速镜像（前缀 + 原地址）。顺序即实测优先级。 */
    private static final String[] MIRRORS = {
            "https://gh-proxy.com/",
            "https://ghproxy.net/",
            "https://ghfast.top/",
            "https://gh.llkk.cc/",
            "https://github.moeyy.xyz/",
    };
    /** 能转发 api.github.com 的镜像（多数镜像只代 github.com 与 raw）。 */
    private static final String[] API_MIRRORS = {
            "https://gh-proxy.com/",
    };
    private static final String VERSION_JSON_RAW =
            "https://raw.githubusercontent.com/" + REPO + "/main/cloud/version.json";
    /** 预发布通道的版本标记：只有发预发布时才有这个文件，正式版会删掉。 */
    private static final String BETA_JSON_RAW =
            "https://raw.githubusercontent.com/" + REPO + "/main/cloud/beta.json";
    private static final String APK_FILE = "update.apk";
    private static final String APK_AUTHORITY = "com.lizard.hsknowledge.fileprovider";
    private static final int C_TEXT = 0xFFE8E8E8;    // 正文
    private static final int C_SUB = 0xFF8A8A8A;     // 次要

    // ---------- 数据模型 ----------
    // 层级只有一种东西：Node。有 children 就是分组节点，否则是叶子知识点。
    // 叶子只有一段正文（body 字符串），数据里没有分节、没有第二种正文结构。
    // 附加内容块：例题、译文等**共用同一套结构**，按钮名与每行标签都由数据给出，
    // 界面不认识「例题」「译文」，只认识「块 → 若干组 → 每组若干行」。
    static class ExtraLine { String tag, text, tone; }        // tag 可空；tone = head / body / note
    static class ExtraItem { List<ExtraLine> lines = new ArrayList<ExtraLine>(); }
    static class ExtraBlock {
        String name = "", unit = "";                          // 例：「例题」+「道」、「译文」+「句」
        String mark = "";                                     // 列表行里的短标记，例：「例」「译」
        List<ExtraItem> items = new ArrayList<ExtraItem>();
    }
    static class Node {
        String id, name;
        // 正文懒解码：v3.12.8 实测启动 2455ms 里绝大部分是 new String 解 1.3MB 中文。
        // 缓存读入时先留原始字节，第一次真正要显示时才转成 String，列表/搜索不受影响。
        String body;                            // null = 还没解码；空正文解码后是 ""
        byte[] bodyRaw;
        List<Node> children = new ArrayList<Node>();
        // 附加内容（例题/译文）同理：启动不建这四千多个对象，进详情页才解析。
        List<ExtraBlock> extras = new ArrayList<ExtraBlock>();
        byte[] extrasRaw;                       // 非空 = 还没解析
        String marksCache;                      // 列表行标记，构建时算好，免得为了一个「例」触发解析
        int star; String img, d;
        String daily;                          // 非空 = 每日一句入口（phil/poem），句子走接口
        Node parent; Subject subj;
        int savedY = -1;                        // 离开时记下列表位置，返回时恢复
        boolean isLeaf() { return children.isEmpty(); }

        /** 正文：第一次用到才解码。 */
        String body() {
            if (body == null) {
                body = (bodyRaw == null) ? "" : new String(bodyRaw, java.nio.charset.StandardCharsets.UTF_8);
                bodyRaw = null;                 // 解完就把字节扔掉，别占双份
            }
            return body;
        }

        /** 附加内容：第一次用到才解析成对象。 */
        List<ExtraBlock> extras() {
            if (extrasRaw != null) {
                byte[] raw = extrasRaw;
                extrasRaw = null;
                try {
                    extras = readExtrasFrom(new DataInputStream(new java.io.ByteArrayInputStream(raw)));
                } catch (Exception e) {
                    extras = new ArrayList<ExtraBlock>();
                }
            }
            return extras;
        }

        /** 列表行显示的短标记，由数据里的 mark 汇总（例 → 「（例）」），界面不自造文案。 */
        String marks() {
            if (marksCache != null) return marksCache;
            LinkedHashSet<String> set = new LinkedHashSet<String>();
            for (ExtraBlock b : extras()) if (b.mark != null && b.mark.length() > 0) set.add(b.mark);
            marksCache = TextUtils.join("/", set);
            return marksCache;
        }
        /** 面包屑/收藏页显示的完整路径，由父链现算，不写死层数。
         *  节点建好后父子关系不再变，所以算一次存起来：列表滚动时每行都要用，别反复拼。 */
        private String pathCache;

        String path() {
            if (pathCache != null) return pathCache;
            ArrayList<String> seg = new ArrayList<String>();
            for (Node p = parent; p != null; p = p.parent) seg.add(0, p.name);
            if (subj != null) seg.add(0, subj.name);
            pathCache = TextUtils.join(" › ", seg);
            return pathCache;
        }
    }
    static class Subject { String key, name; List<Node> roots = new ArrayList<Node>(); }

    private final List<Subject> subjects = new ArrayList<Subject>(16);
    private final Map<String, Subject> subjectMap = new HashMap<String, Subject>(16);
    private final List<Node> allHits = new ArrayList<Node>(1800);   // 叶子约 1572，一次分配够，免得反复扩容
    /** 所有分组节点（有下级的），供全局搜索按标题命中；点进去就是那一层的列表。 */
    private final List<Node> allGroups = new ArrayList<Node>(400);  // 分组约 342
    /** 日期 → 当天修改过的叶子，读数据时建一次：「今日新增」直接取，不再每次全表扫。 */
    private final Map<String, List<Node>> nodesByDate = new LinkedHashMap<String, List<Node>>();
    private final List<Node> searchHits = new ArrayList<Node>();

    // ---------- 导航 ----------
    // 层级只有一种列表页 S_NODE：底下有几级下级就走几层，靠 children 自动判断。
    // 常量连续编号，screens 顺序与之一一对应（screenView 是唯一映射表）。
    private static final int S_SUBJECT = 0, S_NODE = 1, S_DETAIL = 2, S_SETTINGS = 3,
            S_SEARCH = 4, S_FEEDBACK = 5, S_MYFEEDBACK = 6, S_PROFILE = 7,
            S_CHANGELOG = 8, S_ABOUT = 9, S_DISPLAY = 10, S_TODAY = 11,
            S_EXTRA = 12, S_FAV = 13, S_HIST = 14, S_FAV_SUB = 15, S_DAILY = 16, S_SENT = 17,
            S_AI_WEB = 18, S_AI_HUB = 19, S_AI_API_CFG = 20, S_AI_WEB_CFG = 21, S_AI_CHAT = 22,
            S_ORDER = 23,
            // 单词：主页 / 背词 / 搜索 / 设置（都在英语科目里进，或从设置页进）
            S_WORD_HOME = 24, S_WORD_STUDY = 25, S_WORD_SEARCH = 26, S_WORD_SETTINGS = 27,
            S_WORD_VOCAB = 28;   // 生词本
    private static final int SCREEN_COUNT = 29;
    private int screen = S_SUBJECT;
    private Subject curSubject;
    /** 从科目根走到当前所在的分组节点；空 = 正在看某科目的顶层节点。 */
    private final ArrayList<Node> navStack = new ArrayList<Node>();
    /** 当前列表页正在展示的节点（其 children 就是列表内容）。 */
    private Node curNode;
    /** 从收藏/搜索/历史/今日跳进节点树时，退到栈顶该回哪个页面。 */
    private int nodeEntryBack = -1;
    /** 跳进来时补进导航栈的祖先层数：退到这个深度就直接回入口页，不用一层层退到主页。 */
    private int nodeEntryDepth = 0;

    // ---------- 问 AI（DeepSeek 网页版） ----------
    // 这一页只是把 DeepSeek 网页装进来：登录、密钥、模型、对话记录全由网页自己管，
    // App 不存任何密钥、不读聊天内容、不写日志。网页页不放任何按钮，
    // 全靠双指手势：轻点两下切裁切版/原版，长按 2 秒退出；系统返回键照常可用。
    private LinearLayout aiWebScreen, aiWebErrorBox;
    private FrameLayout aiWebHost;
    private WebView aiWebView;
    private ProgressBar aiWebProgress;
    private TextView aiWebErrorTxt, aiWebErrorBtn, aiGestureHint;
    /** 手势提示淡出、双指长按计时共用一个主线程 Handler。 */
    private final Handler aiGestureHandler = new Handler(Looper.getMainLooper());
    private Runnable aiGestureHideTask = null, aiGestureLongTask = null;
    /** 裁切版 = 四边留 roundSafe 避开圆屏；原版 = 网页铺满整屏。与缩放档位是两回事。 */
    private boolean aiWebCrop = true;
    /** 手势提示只在第一次长时间显示，之后每次 1 秒，别老挡着网页。 */
    private static final long AI_HINT_FIRST_MS = 6000L;
    private static final long AI_HINT_LATER_MS = 1000L;
    /** 双指手势序列状态（从第二指按下到最后一指抬起，期间事件不传给网页）。 */
    private boolean aiTwoActive = false, aiTwoLongFired = false, aiTwoMoved = false;
    private long aiTwoDownAt = 0L, aiLastTwoTapAt = 0L;
    private float aiTwoStartX = 0f, aiTwoStartY = 0f;
    private static final long AI_TWO_TAP_GAP_MS = 450L;
    private static final long AI_TWO_LONG_MS = 2000L;
    /** 三个设置子页：都用既有的 OverScrollScrollView + roundPadScroll + padInner 圆屏占位。 */
    private LinearLayout aiHubScreen, aiApiScreen, aiWebCfgScreen;
    private LinearLayout aiHubInner, aiApiInner, aiWebCfgInner;
    private ScrollView aiHubScroll, aiApiScroll, aiWebCfgScroll;
    /** 当前用的模式：web = DeepSeek 网页版，api = 自行配置；存在 prefs 的 ai_mode。 */
    private String aiMode = "web";

    // ---- API AI（自行配置）：接口地址 / 密钥 / 模型 / 对话参数 ----
    // 和网页版相反：这一模式必须在本机存密钥，所以单独放一个 prefs 文件，并在备份规则里排除，
    // 存的是异或 + Base64 的混淆串（不是加密，只为不让明文躺在备份/日志里）。
    private static final String API_SECRET_PREF = "ai_api_secret";
    private static final String API_SECRET_KEY = "k";
    /** 预设：0 = DeepSeek 官方，1 = OpenAI 官方，2 = 自定义。 */
    private static final String[] API_PRESETS = {"DeepSeek 官方", "OpenAI 官方", "自定义"};
    private static final String[] API_PRESET_BASE = {
            "https://api.deepseek.com", "https://api.openai.com/v1", ""};
    private static final String[] API_PRESET_MODEL = {"deepseek-chat", "gpt-4o-mini", ""};
    private static final float[] API_TEMPS = {0.2f, 0.7f, 1.0f, 1.3f};
    private static final int API_CTX_DEFAULT = 8;        // 上下文轮数默认
    private static final int API_CTX_MIN = 1, API_CTX_MAX = 50;
    private static final int API_MAX_DEFAULT = 1024;     // 最大回复长度默认（用户指定）
    private static final int API_MAX_MIN = 128, API_MAX_MAX = 32768;
    private static final String[] API_THINK = {"关闭", "标准", "深度"};

    private int aiApiPreset = 0;
    private String aiApiEndpoint = API_PRESET_BASE[0];   // 不带尾斜杠
    private String aiApiModel = API_PRESET_MODEL[0];
    private String aiApiSecret = "";                    // 明文只在内存，落盘走 seal()
    private int aiApiTempIdx = 1, aiApiThinkIdx = 0;
    /** 上下文轮数与最大回复长度：用户自己填的整数，不再是点击切档位。 */
    private int aiApiCtx = API_CTX_DEFAULT, aiApiMax = API_MAX_DEFAULT;
    /** 拉取到的模型列表（只在内存，重进页面会重拉）。 */
    private final List<String> aiApiModels = new ArrayList<String>();
    /** 正在拉模型 / 测连接的提示；null = 空闲。 */
    private String aiApiBusy = null;

    // ---- API 对话页（S_AI_CHAT）----
    private LinearLayout aiChatScreen, aiChatInner, aiChatBar;
    private ScrollView aiChatScroll;
    private TextView aiChatModelBtn, aiChatInputBtn;
    private int aiChatBack = S_SUBJECT;
    /** 会话只在内存里：退出 App 就清空，不写盘、不上传。 */
    private final List<ChatMsg> chatMsgs = new ArrayList<ChatMsg>();
    private volatile boolean chatBusy = false, chatStopFlag = false;
    private volatile HttpURLConnection chatConn = null;
    /** 流式刷新节流：80ms 一次，避免一字一刷把主线程刷爆。 */
    private long chatUiAt = 0L;
    private TextView chatStreamTv = null, chatStreamReasonTv = null, chatThinkingTv = null;
    /** 对话内容变了才需要重画；页面来回切、切「思考」开关都不该重画。 */
    private boolean chatDirty = true;
    /** 这次进对话页要不要停在顶部（顶上有按钮，默认停在顶部；发消息才往下滚）。 */
    private boolean chatScrollTopOnRender = false;
    /** 切换「思考」开关时重建对话的代际号：连点时只让最后一次生效。 */
    private int chatReloadGen = 0;
    /** 「思考」开关按钮的引用，切开关时就地改文字，不用整页重画。 */
    private TextView chatThinkBtn = null;
    /** 每条的正文/折叠提示视图，用来就地更新（折叠、切思考）而不是整页重建。 */
    private final java.util.Map<ChatMsg, TextView> chatBodies = new java.util.LinkedHashMap<ChatMsg, TextView>();
    private final java.util.Map<ChatMsg, TextView> chatFoldTips = new java.util.LinkedHashMap<ChatMsg, TextView>();
    /** 每条回答底部的「相关知识点」区块，折叠时一起收起。 */
    private final java.util.Map<ChatMsg, View> chatRelatedBoxes = new java.util.LinkedHashMap<ChatMsg, View>();
    /** 开场白：除了「简短」，重点是把公式写法固定下来——App 只能画它认识的结构，
     *  所以要在系统提示里告诉模型怎么写分式、根号、上下标，再交给 applyMarkup 渲染。 */
    private static final String CHAT_SYSTEM_PROMPT =
            "你是手表上的知识助手，回答要简短、直接，能分点就分点，不要长篇大论。\n"
            + "凡是数学公式，必须按下面的写法输出，应用会照着画成分式、根号和上下标：\n"
            + "1. 公式用 $ 包起来，例如 $\\sin\\alpha+\\sin\\beta=2\\sin\\frac{\\alpha+\\beta}{2}\\cos\\frac{\\alpha-\\beta}{2}$\n"
            + "2. 分数写成 \\frac{分子}{分母}；根号写成 \\sqrt{被开方数}；n 次根写成 \\sqrt[n]{被开方数}\n"
            + "3. 上下标写成 a^{2}、a_{n}；希腊字母写成 \\alpha \\beta \\gamma \\theta \\pi\n"
            + "4. 一行只写一个公式，不要用 aligned、array、矩阵等环境，也不要贴图片\n"
            + "5. 乘号写 \\times，除号写 \\div，不等号写 \\ne \\le \\ge\n"
            + "回答的最后另起一行，只写一行关键词：形如 【相关:等比数列|数列求和|前n项和】，"
            + "给 2~6 个最能概括本题考点的词，每个 2~6 个字，用竖线分隔，不要编号、不要解释、不要加标题。"
            + "这一行应用会隐藏掉，只用来匹配本软件里的知识点，写错或省略不影响正文。\n"
            + "列表用「•」开头，不要用 - 或 * 做项目符号，公式单独成行时前面也不要加任何符号"
            + "（横杠贴在公式前会被看成负号）；可以用 **文字** 强调重点；"
            + "不要用 Markdown 表格、代码块和标题符号。";

    /** 本机没有 WebView 组件：自动走 API 模式，网页版不可选。 */
    private boolean aiNoWebView = false;
    /** 本次启动刚刚因为缺 WebView 自动切换过，界面出来后提示一句。 */
    private boolean aiSwitchedByNoWebView = false;

    /** 是否在对话里显示思考过程；默认关（手表屏幕小，思考会把正文挤下去）。 */
    private boolean chatShowThink = false;

    /** 一条消息：role = user / assistant；reasoning 只对推理模型有值。 */
    private static class ChatMsg {
        String role;
        String content = "";
        String reasoning = "";
        boolean streaming = false;
        String err = null;
        /** 接口给的结束原因：length = 被 max_tokens 截断。 */
        String finish = "";
        /** 被截断了，气泡下给一个「继续」。 */
        boolean truncated = false;
        /** 双击折叠：只对已答完的回答生效。 */
        boolean folded = false;
        /** 上一次点击的时间，用来判双击。 */
        long lastTapAt = 0L;
        /** 这次流里实际收到多少段正文，用来判断「空回复」。 */
        int chunks = 0;
        /** 正文渲染结果的缓存：进页面、切开关时不用再解析一遍公式。 */
        CharSequence bodySpan;
        String bodyKey;
        /** 这条回答匹配到的相关知识点（答完后算一次，渲染直接复用）。 */
        List<Node> related;
        ChatMsg(String r) { role = r; }
    }
    /** 从哪个页面点进来的：返回时原路退回，而不是一律回主页。 */
    private int aiWebBack = S_SUBJECT;
    /** 两个设置子页从哪进来的：设置 → 问 AI 子页，或主页「问 AI」直接进来。 */
    private int aiCfgBack = S_AI_HUB;
    /** 网页只加载一次；反复进出不打断正在进行的对话。 */
    private boolean aiWebLoaded = false;
    /** 是否往网页注入「隐藏下载 APP 入口」脚本；关掉 = 网页完全原样，用于排查和兜底。 */
    private boolean aiWebHideDl = true;
    /** 强制网页走暗色：给 WebView 一个 UI_MODE_NIGHT_YES 的 Context，
     *  让网页的 prefers-color-scheme: dark 成立（网站「跟随系统」就会自动暗色）。
     *  默认开（App 整体暗色）；关掉可对照排查。 */
    private boolean aiWebDark = true;
    /** 网页显示缩放档位：0 自动 / 1 更小 / 2 原样，存在 prefs 的 ai_web_zoom。
     *  纯 View 层缩放，不往网页注入任何脚本。 */
    private int aiWebZoomMode = 0;
    /** 让网页按这个 CSS 宽度排版，再整体缩回手表屏幕的圆形安全区。320dp 是常见移动端最小宽度，
     *  再大就要缩得更小、字看不清；太小网页又会把元素挤在一起。 */
    private static final float AI_WEB_TARGET_CSS_W = 320f;
    private static final float AI_WEB_MIN_SCALE = 0.40f;
    private static final String AI_WEB_URL = "https://chat.deepseek.com/";
    /** 默认 UA 会被网页挡成 429，必须用正常浏览器 UA（这条实测能拿到 200）。 */
    private static final String AI_WEB_UA =
            "Mozilla/5.0 (Linux; Android 11; OWW231) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/120.0.0.0 Mobile Safari/537.36";

    // ---------- 每日一句 ----------
    // 句子来自一言（hitokoto）接口，不落进 assets：每天首次打开刷新一次，也可手动换一批。
    // 收藏/历史不存知识点编号，而是「本地号 + 句子原文」：号可回收顶替，
    // 不再被任何收藏/历史引用的文本要从 pool 里彻底删掉，避免本地越攒越多。
    private LinearLayout dailyScreen;
    private SvList dailyList;
    private TextView dailyTitle, dailyHint, dailyBtnFav;
    /** 句子卡片与加载块都是固定的 head：翻页只改文字，不重建列表，页面不会跳回顶部。 */
    private LinearLayout dailyCardBox, dailyLoadingBox;
    private TextView dailyMain, dailyOrigin;
    private ProgressBar dailyBar;
    private LinearLayout dailyBtns;
    private TextView dailyRefreshBtn;
    private Node dailyNode;
    private String dailyKind = "phil";
    private int dailyBack = S_NODE;
    private boolean dailyLoading = false;
    private final List<String> dailyItems = new ArrayList<String>();
    /** 本次运行取到的句子（只放内存，不写盘；下次启动重新调接口）。 */
    private final Map<String, List<String>> dailyMem = new LinkedHashMap<String, List<String>>();
    private int dailyIdx = 0;
    private final Map<Integer, String> dailyPool = new LinkedHashMap<Integer, String>();
    private final List<Integer> dailyFav = new ArrayList<Integer>();
    private final List<Integer> dailyHist = new ArrayList<Integer>();
    private static final int DAILY_WANT = 10;      // 每次至少备 10 条，少了要补
    private static final int DAILY_HIST_MAX = 50;
    // 每日一句的收藏 / 浏览记录列表（句子存在 pool 里，这里只列号）
    private LinearLayout sentScreen;
    private SvList sentList;
    private TextView sentTitle;
    private String sentMode = "fav";
    /** 点「换一批」时先把当前这批临时存下来：拉取失败或用户点取消，就还原它。 */
    private final List<String> dailyBackup = new ArrayList<String>();
    private boolean dailyRefreshing = false;
    private long dailySeq = 0;              // 每次刷新 +1，用来丢弃「已经取消掉」的在途结果
    /** 最近一次做过「每日一句」跨天检查的日期（回到前台时判重用）。 */
    private String dailyCheckedTag = "";
    private final List<Node> shownNodes = new ArrayList<Node>();
    private int detailBack = S_NODE;
    /** 当前详情页的附加内容块（例题、译文…）：有几块就画几个按钮，为空则都不画。 */
    private List<ExtraBlock> curExtras = new ArrayList<ExtraBlock>();

    // ---------- UI ----------
    private FrameLayout root;
    private LinearLayout subjectScreen, nodeScreen, detailScreen, settingsScreen, searchScreen, extraScreen;
    private SvList subjectList, nodeList, searchList;
    private TextView listTitle, detailTitle, detailPath, searchTitle, extraTitle, extraPath, extraKind;
    private TextView detailBackBtn, settingsBackBtn, extraBackBtn, orderBackBtn;
    private EditText itemSearchBox, gSearchBox;
    private ScrollView detailScroll, settingsScroll, extraScroll;
    private LinearLayout detailInner, settingsInner, extraInner;
    private FrameLayout fullscreenView;

    // ---------- 反馈 ----------
    // 反馈类型：按「用户看到什么现象」分类，方便一眼对上号。
    private static final String[] FB_TYPES = {"显示问题", "内容有误", "闪退卡顿", "功能建议", "其他问题"};
    /** 每个类型下面的一句话说明，随选中类型变化，告诉用户什么情况该选它。 */
    private static final String[] FB_TYPE_HINTS = {
            "界面看着不对：公式错位、显示不全、有残影、缩放没生效、按钮找不到",
            "知识点内容写错了：答案不对、史实记错、公式写错",
            "用着用着退出、卡住不动、反应很慢",
            "想要个新功能，或者哪里用着别扭、想改一改",
            "上面几种都不是，先放这儿",
    };
    /** 旧反馈里用过的类型名，解析历史反馈时映射到新分类。 */
    private static final String[] FB_TYPES_OLD = {"内容有误", "公式显示", "闪退卡顿", "功能建议", "其他问题"};
    private LinearLayout feedbackScreen, feedbackInner;
    private ScrollView feedbackScroll;
    private TextView fbStatusTxt, fbSubmitBtn, fbAllBtn;
    /** 类型说明行：告诉用户「什么情况该选这一类」。 */
    private TextView fbTypeHint;
    private LinearLayout profileScreen, profInner;
    private ScrollView profScroll;
    private TextView profStatusTxt, profLoginBtn, profCodeBig, profLogin, profName;
    private ImageView profAvatar, profQr;
    private FrameLayout profAvatarBox;
    private ProgressBar profAvatarSpin;
    private EditText fbDesc;
    private final TextView[] fbTypeBtns = new TextView[FB_TYPES.length];
    private int fbType = 0;
    private String fbDraft = "";
    private String ghToken, ghLogin;
    private String fbCode, fbCodeUrl;
    private AlertDialog codeDialog;
    private AlertDialog updateDialog;
    private String pendingUrl, pendingTag;
    private boolean pendingDownload = false;
    private boolean pendingInstall = false;
    private volatile boolean downloadCancel = false;
    /** 当前正在下载的连接：取消时立即 disconnect，让阻塞的读取马上抛异常退出。 */
    private HttpURLConnection activeCn = null;
    private boolean foreground = false;
    // 日志记录：默认关闭，需在设置里手动开启（开启要 3 秒倒计时确认）
    private boolean logEnabled = false;
    private File logFile = null;
    private boolean fbAttachLog = false;
    private String lastPath = "";
    private boolean subjectDirty = true;
    private String lastQuery = null;
    private final boolean[] restoreNext = new boolean[SCREEN_COUNT];
    /** 普通滚动页（非 SvList）离开时的位置：返回时用它复位。 */
    private final int[] savedScrollY = new int[SCREEN_COUNT];
    private LinearLayout bootSplash;      // 启动开屏（数据就绪后淡出移除）
    private FrameLayout bootHost;         // 开屏与主界面共用的容器
    private boolean skipPageAnim = false; // 启动首屏不做淡入（开屏还盖着）
    private TextView bubbleView;          // 唯一的提示气泡（全局复用，避免叠加）
    private long bootStart = 0;
    private String loadError = null;   // 下一刻进入该页时回到离开时的位置（只有「返回上一级」才置）

    // 关于 / 感谢名单
    private LinearLayout aboutScreen, aboutInner, thanksBox, thanksLoading;

    // 显示调节（字号 / 界面缩放，带实时预览）
    private LinearLayout displayScreen, displayInner;
    private ScrollView displayScroll;
    // 首页科目排序（设置页的子页）
    private LinearLayout orderScreen, orderInner;
    private ScrollView orderScroll;

    // ---- 单词功能（四屏共用一套词书数据）----
    private LinearLayout wordHomeScreen, wordHomeInner, wordStudyScreen, wordStudyInner,
            wordSearchScreen, wordSearchInner, wordSettingsScreen, wordSettingInner,
            wordVocabScreen, wordVocabInner;
    private ScrollView wordHomeScroll, wordStudyScroll, wordSearchScroll, wordSettingScroll,
            wordVocabScroll;

    // 收藏与历史记录（都按节点稳定编号记在本地）
    private LinearLayout favScreen, histScreen, favSubScreen;
    private SvList favList, histList, favSubList;
    private TextView favTitle, histTitle, favSubTitle;
    /** 收藏统一成一套：分组节点和叶子知识点都能收藏，存的是稳定编号。 */
    private final LinkedHashSet<String> favIds = new LinkedHashSet<String>();
    private final List<String> histIds = new ArrayList<String>();
    /** 稳定编号 → 节点（分组与叶子都在内）；收藏页、搜索、历史、今日都查它。 */
    private final Map<String, Node> nodeById = new HashMap<String, Node>(4096);   // 每个节点存两个键，预留足够容量
    /** 每日一句的收藏 / 浏览记录：点一下复制整句，收藏模式长按取消收藏。 */
    private void renderSent() {
        loadDailyStore();
        final boolean fav = "fav".equals(sentMode);
        List<Integer> src = fav ? dailyFav : dailyHist;
        final List<Integer> rows = new ArrayList<Integer>();
        for (int no : src) if (dailyPool.containsKey(no)) rows.add(no);
        sentTitle.setText((fav ? "每日一句收藏 · " : "每日一句最近浏览 · ") + rows.size() + " 条");
        sentList.bind(new RowSource() {
            public int size() { return rows.isEmpty() ? 1 : rows.size(); }
            public View make(final int i) {
                if (rows.isEmpty()) {
                    return textRow(fav ? "还没有收藏句子，去「每日一句」里点收藏" : "还没有看过句子",
                            C_SUB, null);
                }
                final int no = rows.get(i);
                final String text = dailyPool.get(no);
                int cut = text.indexOf('\n');
                String main = (cut >= 0) ? text.substring(0, cut) : text;
                String origin = (cut >= 0) ? text.substring(cut + 1).trim() : "";
                View row = hitRow(main, origin, null);
                if (fav) {
                    row.setOnLongClickListener(new View.OnLongClickListener() {
                        public boolean onLongClick(View v) {
                            vibrate();
                            toggleDailyFav(text);
                            toast("已取消收藏");
                            renderSent();
                            return true;
                        }
                    });
                }
                return row;
            }
        });
    }

    /** 列表顶部的灰色操作提示（收藏页的长按说明等），只有一句、居中。 */
    private TextView grayTip(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(m.subtitleSize - 2.5f);
        t.setTextColor(C_SUB);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, 0, 0, dp(6));
        return t;
    }

    /** 收藏页第一级点进的科目。 */
    private String favSubKey = "";
    private static final int HIST_MAX = 50;
    /** 是否正在下载更新包：同一时间只允许一条下载线。 */
    private boolean updating = false;

    /** 当前详情页这条知识点的编号（无编号的专题不能收藏、不进历史）。 */
    private String curDetailId = "";

    // 今日新增（分页加载）
    private LinearLayout todayScreen;
    private SvList todayList;
    private TextView todayTitle;
    private final List<Node> todayAll = new ArrayList<Node>();
    private final List<Node> todayShown = new ArrayList<Node>();
    private String todayBuiltFor = "";
    private TextView todayFallbackBtn;      // 今天没新增时，给一个「看最近一批」的入口
    private boolean todayFallbackOn = false;
    private ScrollView aboutScroll;
    private TextView aboutInfo, thanksState, thanksLoadTxt;
    private TextView routeHint;
    private boolean thanksLoaded = false, thanksLoadingBusy = false;
    private final Map<String, Bitmap> avatarCache = new HashMap<String, Bitmap>();

    // 更新日志
    private LinearLayout changelogScreen, changelogInner, changelogList;
    private ScrollView changelogScroll;
    private TextView changelogSub;
    private int changelogBack = S_SETTINGS;
    private boolean changelogFilled = false;

    // 我的反馈记录
    private LinearLayout mineScreen;
    private SvList mineList;
    private TextView mineHeader;
    private boolean mineAllMode = false;   // true=查看仓库全部反馈（仅项目所有者）
    private boolean mineDirty = false;     // 反馈被编辑/撤回/删除过，回到列表要重新拉取
    private AlertDialog editIssueDlg;
    private JSONObject curIssue;           // 当前打开的反馈，编辑/撤回/删除用
    private final List<JSONObject> mineIssues = new ArrayList<JSONObject>();
    private final List<String[]> mineRows = new ArrayList<String[]>();
    private FrameLayout progBox;
    private View progFill;
    private TextView progTitle, progDetail;
    private int progBarW;

    // ---------- 设置 ----------
    private SharedPreferences prefs;
    private float fontSizeSp = 14f;
    private float listSizeSp = 15f;
    private float titleSizeSp = 0f;      // 0 表示按屏型取默认值
    private float subSizeSp = 0f;
    private float uiScale = 1f;          // 界面整体缩放（显示大小）
    private boolean tuningFirstRun = false;
    /** 重建界面后要回到的页面（-1 表示按默认逻辑走）。 */
    private static int pendingScreen = -1;
    private boolean isRound = false;
    // 右滑返回手势（设置里可关）
    private static final float PREV_PARALLAX = 0.32f;  // 预览页的起始左移比例（视差）
    private boolean swipeBack = true;
    // 交互震动反馈（设置里可关）
    private boolean haptic = true;
    // 测试通道：开启后更新检查把 GitHub 上的「预发布」也算进来（设置里可开，默认关）
    private boolean betaChannel = false;
    /** 这次测试通道有没有查失败（只用于给用户一句明确提示，不参与判断）。 */
    private boolean betaFetchFailed = false;
    /** 哪些屏幕的内容已经建过。重页面改成首次进入才建，启动时不再一口气建 22 个（v3.12.6）。 */
    private final boolean[] screenBuilt = new boolean[SCREEN_COUNT];
    private long lastVibrate = 0;           // 节流：避免连点狂震
    private boolean swipeTracking = false;
    private boolean swipeActive = false;     // 已进入拖动返回
    private boolean swipeBusy = false;       // 出场动画进行中
    private boolean sliderTouching = false;  // 正在拖动滑块（字号/缩放），此时不触发右滑返回
    private boolean swipeFromInput = false;  // 起手点在输入框内
    private boolean nodeRefreshLocked = false; // renderNodes 里程序性清空搜索框时锁住监听，避免二次绑定
    private boolean swipeNavigating = false;   // 右滑返回触发的切页：预览已就位，只确认、不重渲染/重定位
    private View swipeView;                  // 正在被拖动的当前页
    private View swipePrev;                  // 下面露出的上一级页面（预览）
    private View swipeGhost;                 // 同页换层时顶在上面的快照（跟着手指走）
    private boolean swipeSame = false;       // 本次拖动是「同一页内退一层」（分组回退）
    private Node swipePopped = null;         // 已弹出的分组节点（没滑够要放回去）
    private int swipeOldY = -1;              // 拖动前列表的滚动位置
    private String swipeQuery = null;        // 拖动前「本级搜索」的关键词（取消时还原）
    private float swipeX, swipeY;
    private List<String> orderKeys = new ArrayList<String>();
    private static final String[] DEFAULT_ORDER = {"chinese", "math", "english", "physics", "chem", "bio", "politics", "history", "geography"};

    // ---------- 一套系统两套参数 ----------
    private class Metrics {
        final boolean round;
        final int sidePad, topPad, bottomPad;
        final int roundSafe; // 圆屏四角被裁的安全边距，用于需贴边的元素（如全屏关闭按钮）
        final float titleSize, subtitleSize, backSize, secTagSize;
        Metrics() {
            int w = getResources().getDisplayMetrics().widthPixels;
            int h = getResources().getDisplayMetrics().heightPixels;
            round = isRound;
            if (round) {
                sidePad = dp(12); topPad = dp(14); bottomPad = dp(12);
                roundSafe = (int) Math.round(Math.min(w, h) * 0.146f);
            } else {
                sidePad = dp(14); topPad = dp(14); bottomPad = dp(14);
                roundSafe = 0;
            }
            // 字号：优先用「显示调节」里的值，未设置时按屏型取默认。
            // 整体缩放必须乘进字号，否则只有间距和图标变大，字还是原样，看着就像没生效。
            float k = uiScale;
            titleSize = (titleSizeSp > 0 ? titleSizeSp : (round ? 15f : 17f)) * k;
            subtitleSize = (subSizeSp > 0 ? subSizeSp : (round ? 11f : 12f)) * k;
            backSize = Math.max(9f, listSizeSp - 2f) * k;
            secTagSize = Math.max(8f, subtitleSize - 1f);
        }
    }
    private Metrics m;

    /** 正文字号（含整体缩放）。 */
    private float fsSp() { return fontSizeSp * uiScale; }

    /** 列表字号（含整体缩放）。 */
    private float lsSp() { return listSizeSp * uiScale; }

    /** 问 AI 对话页专用字号：绑「正文字号 / 列表字号」而不是「说明小字」，
     *  下限也要跟着 uiScale 走，不能写死 9f（写死会把小档位全钳成一样大，看着像没生效）。
     *  以前用 m.subtitleSize（说明小字 11sp）+ Math.max(9f,…)，调档位几乎看不出变化。 */
    private float chatBodySize() { return Math.max(10f * uiScale, fsSp()); }
    private float chatBarSize()  { return Math.max(9f * uiScale, lsSp() - 3f); }
    private float chatTinySize() { return Math.max(8f * uiScale, fsSp() - 3f); }

    private int dp(float v) {
        float px = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics());
        return Math.round(px * uiScale);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try { isRound = getResources().getConfiguration().isScreenRound(); } catch (Exception ignored) { }
        prefs = getSharedPreferences("settings", MODE_PRIVATE);
        fontSizeSp = prefs.getFloat("font_size", isRound ? 13f : 14f);
        listSizeSp = prefs.getFloat("list_size", isRound ? 14f : 15f);
        titleSizeSp = prefs.getFloat("title_size", 0f);
        subSizeSp = prefs.getFloat("sub_size", 0f);
        uiScale = prefs.getFloat("ui_scale", 1f);
        m = new Metrics();
        swipeBack = prefs.getBoolean("swipe_back", true);
        haptic = prefs.getBoolean("haptic", true);
        betaChannel = prefs.getBoolean("beta_channel", false);
        // 日志开关也必须落盘：手表内存小，切后台常被回收，重进后开关自己变回「关」，
        // 用户就永远攒不出一份完整日志（踩过）。这里恢复后由第一条 BOOT 日志新建文件。
        logEnabled = prefs.getBoolean("log_enabled", false);
        aiMode = prefs.getString("ai_mode", "web");
        loadApiConfig();
        // 没有 WebView 就自动改用 API 模式（网页版在这台设备上根本打不开）
        aiNoWebView = !aiHasWebView();
        if (aiNoWebView && !"api".equals(aiMode)) {
            aiMode = "api";
            prefs.edit().putString("ai_mode", aiMode)
                    .putBoolean("ai_choice_done", true).apply();
            aiSwitchedByNoWebView = true;
        }
        aiWebHideDl = prefs.getBoolean("ai_web_hide_dl", true);
        aiWebDark = prefs.getBoolean("ai_web_dark", true);
        aiWebZoomMode = prefs.getInt("ai_web_zoom", 0);
        aiWebCrop = prefs.getBoolean("ai_web_crop", true);
        loadGitHub();         // 只读 prefs，很快
        bootStart = System.currentTimeMillis();
        showBootSplash();     // 先把开屏画出来，绝不留黑屏
        bootLoad();           // 数据在子线程读，读完整再建界面
    }

    // ================= 启动开屏 =================
    /** 开屏最短显示时长：数据读得飞快时也让 logo 至少亮一下，不要一闪就跳。 */
    private static final long BOOT_MIN_MS = 320;

    /** 开屏：图标 + 转圈 + 一行字。纯 View，创建只花几毫秒，可立即上屏。 */
    private void showBootSplash() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setBackgroundColor(C_BG);

        ImageView logo = new ImageView(this);
        int rid = getResources().getIdentifier("ic_launcher_foreground", "mipmap", getPackageName());
        if (rid != 0) logo.setImageResource(rid);
        box.addView(logo, new LinearLayout.LayoutParams(dp(52), dp(52)));

        ProgressBar bar = new ProgressBar(this);
        bar.setIndeterminate(true);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(dp(28), dp(28));
        bp.topMargin = dp(10);
        try {
            bar.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(C_PRIMARY));
        } catch (Exception ignored) { }
        box.addView(bar, bp);

        TextView tip = new TextView(this);
        tip.setText("正在载入知识库…");
        tip.setTextSize(m.subtitleSize - 2f);
        tip.setTextColor(C_SUB);
        tip.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tp.topMargin = dp(9);
        box.addView(tip, tp);

        // 开屏放进一个容器里（而不是直接 setContentView(box)）：
        // 主界面建好后是「加进同一个容器、垫在开屏下面」，等它画完第一帧再撤开屏。
        // 两次 setContentView 会先移除旧视图，中间必然露一帧窗口底色，看起来就是闪一下。
        bootSplash = box;
        bootHost = new FrameLayout(this);
        bootHost.setBackgroundColor(C_BG);
        bootHost.addView(box, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(bootHost);
    }

    /** 主界面画完第一帧再撤开屏：先淡出再移除，任何一帧都不会露底。 */
    private void hideBootSplash() {
        if (bootHost == null || bootSplash == null) return;
        final FrameLayout host = bootHost;
        final View splash = bootSplash;
        bootSplash = null;
        bootHost = null;
        // 固定注册时的观察者引用，回调里也从它移除：边暂停边 detach 时再 getViewTreeObserver()
        // 可能拿到另一个实例，remove 会抛 "This ViewTreeObserver is not alive"（11-10 的崩溃栈）。
        final android.view.ViewTreeObserver splashVto = root.getViewTreeObserver();
        splashVto.addOnPreDrawListener(
                new android.view.ViewTreeObserver.OnPreDrawListener() {
                    public boolean onPreDraw() {
                        try { splashVto.removeOnPreDrawListener(this); } catch (Throwable ignored) { }
                        // 开屏淡出 + 轻微放大，主界面同时从 0.98 回到 1：两层一起动才有「揭开」的感觉
                        root.setScaleX(0.98f);
                        root.setScaleY(0.98f);
                        root.animate().scaleX(1f).scaleY(1f).setDuration(340)
                                .setInterpolator(new DecelerateInterpolator(1.4f)).start();
                        splash.animate().alpha(0f).scaleX(1.06f).scaleY(1.06f)
                                .setDuration(280)
                                .setInterpolator(new DecelerateInterpolator(1.4f)).start();
                        host.postDelayed(new Runnable() {
                            public void run() {
                                if (splash.getParent() == host) host.removeView(splash);
                            }
                        }, 300);
                        return true;
                    }
                });
    }

    /** 子线程读数据：assets 里的 JSON 解析不碰任何 View。 */
    private void bootLoad() {
        new Thread(new Runnable() {
            public void run() {
                long t0 = System.currentTimeMillis();
                cleanupOldApk();     // 装完新版本后把遗留的安装包删掉
                long t1 = System.currentTimeMillis();
                loadData();
                long t2 = System.currentTimeMillis();
                loadMarks();
                loadOrderKeys();
                loadDailyMem();      // 上次取到的句子（重启后先拿它垫上，不至于一片空白）
                long t3 = System.currentTimeMillis();
                log("BOOT", "cleanup " + (t1 - t0) + "ms · loadData " + (t2 - t1)
                        + "ms · marks/order/daily " + (t3 - t2) + "ms");
                runOnUiThread(new Runnable() {
                    public void run() { bootFinish(); }
                });
            }
        }).start();
    }

    /** 数据就绪：等开屏露一下脸，再进主界面。 */
    private void bootFinish() {
        if (isFinishing() || isDestroyed()) return;
        long cost = System.currentTimeMillis() - bootStart;
        long wait = Math.max(0, BOOT_MIN_MS - cost);
        getWindow().getDecorView().postDelayed(new Runnable() {
            public void run() { enterApp(); }
        }, wait);
    }

    /** 建界面、定首屏、挂后台任务。开屏在这里被主界面替换。 */
    private void enterApp() {
        if (isFinishing() || isDestroyed()) return;
        long tBuild = System.currentTimeMillis();
        buildUi();               // 有开屏容器时，root 是垫在开屏下面加进去的
        long tBuildEnd = System.currentTimeMillis();
        boolean tuned = prefs.getBoolean("display_tuned", false);
        if (!tuned) tuningFirstRun = true;
        skipPageAnim = true;     // 首屏直接显示，不做淡入（开屏还在上面盖着）
        if (!tuned) {
            showScreen(S_DISPLAY);       // 第一次装：先进显示调节，让用户把字号调舒服
        } else {
            showScreen(S_SUBJECT);
        }
        hideBootSplash();
        // 启动耗时打点：开日志后这几行会进日志文件，反馈时能直接带出来
        log("BOOT", "buildUi " + (tBuildEnd - tBuild) + "ms · 首屏就绪 " + (System.currentTimeMillis() - tBuildEnd)
                + "ms · 自启动共 " + (System.currentTimeMillis() - bootStart) + "ms");
        if (loadError != null) {
            toast("数据加载失败：" + loadError);
            loadError = null;
        }
        // 后台探测最优通道（直连 / 镜像），探测结果给所有联网页面共用
        startRouteProbe();
        checkUpdate(true);
        installCrashHandler();
        root.postDelayed(new Runnable() {
            public void run() { showGreeting(); }
        }, 700);
        // 每天第一次打开：两类各刷一批新句子（后台请求，不打扰界面）
        root.postDelayed(new Runnable() {
            public void run() { checkDailyForToday(); }
        }, 1600);
    }

    /** 收藏星这一列的宽度（标题左边也留同宽，保证标题相对整行居中）。 */
    private int STAR_W() { return dp(34); }

    /** 收藏星：分组节点与叶子知识点共用一套，按稳定编号判断高亮。 */
    private void paintStar(TextView star, String id) {
        boolean on = favIds.contains(stableKey(id));
        star.setTextColor(on ? C_HI : C_SUB);
        star.setContentDescription(on ? "取消收藏" : "收藏");
    }

    /** 点一下收藏星：统一进 favIds。返回切换后是否收藏着。 */
    private boolean toggleFav(String id) {
        String key = stableKey(id);
        if (key.length() == 0) return false;
        boolean on;
        if (favIds.remove(key)) on = false;
        else { favIds.add(key); on = true; }
        saveFav();
        return on;
    }

    /** 去掉会变的日期段，得到稳定标识。
     *  编号：分组 = <科目码>-<各级序号>；叶子 = <日期>-<科目码>-<各级序号>。
     *  日期段只在叶子、且位于最前（唯一的 6 位数字段），跳过它即可；
     *  分组编号不含日期、原样返回。兼容旧版本（日期曾在末段/第 2 段）。 */
    private static String stableKey(String id) {
        if (id == null || id.length() == 0) return "";
        // 手写切分：id.split("-") 会给每个编号造一个数组加一堆子串，启动时 1914 个编号、
        // 一万三千多个片段，全是要分配的临时对象，直接扫一遍拼出来省得多。
        int n = id.length();
        StringBuilder sb = new StringBuilder(n);
        int i = 0;
        while (i < n) {
            int j = id.indexOf('-', i);
            if (j < 0) j = n;
            if (!isDate6At(id, i, j)) {
                if (sb.length() > 0) sb.append('-');
                sb.append(id, i, j);
            }
            i = j + 1;
        }
        return sb.toString();
    }

    /** 这一段是不是 6 位纯数字的日期段（起止下标，左闭右开）。
     *  不用 `seg.matches("\\d{6}")`：String.matches 每次都重新编译 Pattern，
     *  启动时要跑一万三千多次，在手表上就是几百毫秒纯浪费。 */
    private static boolean isDate6At(String s, int from, int to) {
        if (to - from != 6) return false;
        for (int i = from; i < to; i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') return false;
        }
        return true;
    }

    /** 读本地的收藏与历史（存的是稳定编号）。 */
    private void loadMarks() {
        favIds.clear();
        histIds.clear();
        String f = prefs.getString("fav_ids", "");
        if (f.length() > 0) for (String id : f.split(",")) if (id.length() > 0) favIds.add(id);
        String h = prefs.getString("hist_ids", "");
        if (h.length() > 0) for (String id : h.split(",")) if (id.length() > 0) histIds.add(id);
    }

    private void saveFav() {
        prefs.edit().putString("fav_ids", TextUtils.join(",", favIds)).apply();
    }

    private void saveHist() {
        prefs.edit().putString("hist_ids", TextUtils.join(",", histIds)).apply();
    }

    /** 进历史：同编号先去掉再放最前，最多 50 条，超出丢掉最旧的。 */
    private void pushHistory(String id) {
        String key = stableKey(id);
        if (key.length() == 0) return;
        histIds.remove(key);
        histIds.add(0, key);
        while (histIds.size() > HIST_MAX) histIds.remove(histIds.size() - 1);
        saveHist();
    }

    /** 按稳定编号取节点；查不到就是这条已经不在库里了。 */
    private Node nodeOf(String id) {
        if (id == null || id.length() == 0) return null;
        Node n = nodeById.get(id);                 // 收藏/历史里存的就是稳定编号，先直接查
        if (n == null) n = nodeById.get(stableKey(id));   // 万一存的是完整编号，再折算一次
        return n;
    }

    private void loadOrderKeys() {
        orderKeys.clear();
        String saved = prefs.getString("subject_order", null);
        if (saved != null) {
            for (String k : saved.split(",")) {
                String t = k.trim();
                if (t.length() > 0 && subjectMap.containsKey(t) && !orderKeys.contains(t)) orderKeys.add(t);
            }
        }
        for (String k : DEFAULT_ORDER) {
            if (subjectMap.containsKey(k) && !orderKeys.contains(k)) orderKeys.add(k);
        }
    }

    private void markSubjectsDirty() {
        subjectDirty = true;
    }

    private void saveOrderKeys() {
        prefs.edit().putString("subject_order", TextUtils.join(",", orderKeys)).apply();
    }

    // ================= 数据加载（每科一个文件 + order.json 索引） =================
    private JSONObject readJsonAsset(String path) throws Exception {
        InputStream is = getAssets().open(path);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
        is.close();
        return new JSONObject(new String(bos.toByteArray(), StandardCharsets.UTF_8));
    }

    /** 递归解析节点：有 children 就是分组，否则是叶子（正文只有一个 body 字段）。 */
    // ---------- 二进制缓存（v3.12.8）：解析一次，之后启动直接读 ----------
    // 手表上 JSON 词法分析占了大头（2.6 秒），而建 Node 对象只需几百毫秒。
    // 把整棵树按固定顺序写成二进制，下次启动跳过词法分析直接建对象。
    // 换版本（versionCode 变）或读失败都回退到 JSON，最坏只是慢，不会崩。

    private static final int CACHE_MAGIC = 0x485a4b34;   // "HZK4"（正文/附加内容存字节，懒解码）

    private File kbCacheFile() {
        return new File(getFilesDir(), "kb_cache.bin");
    }

    /** 读缓存：先读 APK 里预生成的那份（构建时就做好了，首次安装也快），
     *  再读本地目录（同一版本内解析过一次后落的），都没有才走 JSON。 */
    private boolean loadFromCache() {
        try {
            if (loadCacheFrom(getAssets().open("kb_cache.bin"))) {
                dropStaleLocalCache();   // APK 里那份能用，本地旧版本的缓存就没必要占 1.35MB
                return true;
            }
        } catch (Throwable ignored) { }
        File f = kbCacheFile();
        if (!f.exists() || f.length() < 64) return false;
        try {
            return loadCacheFrom(new FileInputStream(f));
        } catch (Throwable e) {
            return false;
        }
    }

    /** 清掉版本对不上的本地缓存；同版本的留着（assets 读失败时它是第二道保险）。 */
    private void dropStaleLocalCache() {
        File f = kbCacheFile();
        if (!f.exists()) return;
        DataInputStream in = null;
        try {
            in = new DataInputStream(new BufferedInputStream(new FileInputStream(f)));
            int magic = in.readInt();
            int ver = in.readInt();
            if (magic != CACHE_MAGIC || ver != appVersionCode()) f.delete();
        } catch (Throwable e) {
            f.delete();                  // 读不出来就是坏的，别留着
        } finally {
            try { if (in != null) in.close(); } catch (Exception ignored) { }
        }
    }

    /** 读缓存；任何异常都清空半截数据并返回 false。
     *  先把整个文件读进内存再解析，两段分别计时——手表上「读」和「建对象」谁是大头，
     *  日志里一眼能看出（曾经以为瓶颈在 JSON 词法，实测是别的）。 */
    private boolean loadCacheFrom(InputStream raw) {
        long t0 = System.currentTimeMillis();
        byte[] all;
        try {
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream(1536 * 1024);
            byte[] buf = new byte[32 * 1024];
            int n;
            while ((n = raw.read(buf)) > 0) bos.write(buf, 0, n);
            raw.close();
            all = bos.toByteArray();
        } catch (Throwable e) {
            try { raw.close(); } catch (Exception ignored) { }
            return false;
        }
        long tRead = System.currentTimeMillis() - t0;
        long t1 = System.currentTimeMillis();
        DataInputStream in = new DataInputStream(new java.io.ByteArrayInputStream(all));
        try {
            if (in.readInt() != CACHE_MAGIC) return false;
            if (in.readInt() != appVersionCode()) return false;   // 换了版本就作废
            int subjN = in.readInt();
            for (int i = 0; i < subjN; i++) {
                Subject s = new Subject();
                s.key = rStr(in);
                s.name = rStr(in);
                int roots = in.readInt();
                for (int j = 0; j < roots; j++) s.roots.add(readNodeFromCache(in, null, s));
                subjects.add(s);
                subjectMap.put(s.key, s);
            }
            log("BOOT", "缓存读取 " + tRead + "ms（" + (all.length / 1024) + "KB）· 建对象 "
                    + (System.currentTimeMillis() - t1) + "ms");
            return true;
        } catch (Throwable e) {
            // 文件损坏 / 格式对不上：把读了一半的清掉，让调用方走 JSON
            subjects.clear();
            subjectMap.clear();
            nodeById.clear();
            return false;
        }
    }

    private Node readNodeFromCache(DataInputStream in, Node parent, Subject s) throws Exception {
        Node n = new Node();
        n.parent = parent;
        n.subj = s;
        n.id = rStr(in);
        n.name = rStr(in);
        if (in.readByte() == 1) {
            n.bodyRaw = rBytes(in);          // 只留字节，等真正显示再解码（启动最耗时的一步就在这）
            n.daily = rStr(in);
            n.star = in.readInt();
            n.img = rStr(in);
            n.d = rStr(in);
            n.marksCache = rStr(in);
            int exLen = in.readInt();
            if (exLen > 0) {
                byte[] ex = new byte[exLen];
                in.readFully(ex);
                n.extrasRaw = ex;            // 附加内容同样拖延到进详情页再解析
            }
        } else {
            int cn = in.readInt();
            for (int i = 0; i < cn; i++) n.children.add(readNodeFromCache(in, n, s));
        }
        if (n.id.length() > 0) {
            nodeById.put(stableKey(n.id), n);
            nodeById.put(n.id, n);
        }
        return n;
    }

    /** 从缓存里的一段字节解析附加内容（block 序列）。 */
    static List<ExtraBlock> readExtrasFrom(DataInputStream in) throws Exception {
        int bn = in.readInt();
        List<ExtraBlock> list = new ArrayList<ExtraBlock>();
        for (int i = 0; i < bn; i++) {
            ExtraBlock b = new ExtraBlock();
            b.name = rStr(in);
            b.unit = rStr(in);
            b.mark = rStr(in);
            int itn = in.readInt();
            for (int j = 0; j < itn; j++) {
                ExtraItem it = new ExtraItem();
                int ln = in.readInt();
                for (int k = 0; k < ln; k++) {
                    ExtraLine l = new ExtraLine();
                    l.tag = rStr(in);
                    l.text = rStr(in);
                    l.tone = rStr(in);
                    it.lines.add(l);
                }
                b.items.add(it);
            }
            list.add(b);
        }
        return list;
    }

    /** 写缓存。任何失败都只是删掉临时文件，下次照旧走 JSON。 */
    private void writeKbCache() {
        File tmp = new File(getFilesDir(), "kb_cache.tmp");
        DataOutputStream out = null;
        try {
            out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(tmp)));
            out.writeInt(CACHE_MAGIC);
            out.writeInt(appVersionCode());
            out.writeInt(subjects.size());
            for (Subject s : subjects) {
                wStr(out, s.key);
                wStr(out, s.name);
                out.writeInt(s.roots.size());
                for (Node n : s.roots) writeNodeToCache(out, n);
            }
            out.flush();
            out.close();
            out = null;
            File f = kbCacheFile();
            if (f.exists()) f.delete();
            if (!tmp.renameTo(f)) tmp.delete();
        } catch (Throwable e) {
            try { if (out != null) out.close(); } catch (Exception ignored) { }
            tmp.delete();
        }
    }

    private void writeNodeToCache(DataOutputStream o, Node n) throws Exception {
        wStr(o, n.id);
        wStr(o, n.name);
        if (n.isLeaf()) {
            o.writeByte(1);
            wStr(o, n.body());          // 写缓存时正文一定已解码（来自 JSON 解析路径）
            wStr(o, n.daily);
            o.writeInt(n.star);
            wStr(o, n.img);
            wStr(o, n.d);
            wStr(o, n.marks());
            byte[] ex = extrasBytes(n);
            o.writeInt(ex.length);
            o.write(ex);
        } else {
            o.writeByte(0);
            o.writeInt(n.children.size());
            for (Node c : n.children) writeNodeToCache(o, c);
        }
    }

    /** 把附加内容序列化成一段字节（格式与 readExtrasFrom 对应）。 */
    private byte[] extrasBytes(Node n) throws Exception {
        List<ExtraBlock> list = n.extras();
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        DataOutputStream d = new DataOutputStream(bos);
        d.writeInt(list.size());
        for (ExtraBlock b : list) {
            wStr(d, b.name);
            wStr(d, b.unit);
            wStr(d, b.mark);
            d.writeInt(b.items.size());
            for (ExtraItem it : b.items) {
                d.writeInt(it.lines.size());
                for (ExtraLine l : it.lines) {
                    wStr(d, l.tag);
                    wStr(d, l.text);
                    wStr(d, l.tone);
                }
            }
        }
        d.flush();
        return bos.toByteArray();
    }

    /** writeUTF 不接受 null。 */
    private String nn(String s) {
        return s == null ? "" : s;
    }

    /** 写一个字符串：4 字节长度 + 标准 UTF-8 字节。
     *  不用 DataOutputStream.writeUTF——它是 modified UTF-8（\0 和增补字符编码不同），
     *  构建脚本用 Python 预生成缓存时对不上；自己定格式两边都能精确复刻。 */
    static void wStr(DataOutputStream o, String s) throws Exception {
        byte[] b = (s == null ? "" : s).getBytes("UTF-8");
        o.writeInt(b.length);
        o.write(b);
    }

    static String rStr(DataInputStream in) throws Exception {
        int n = in.readInt();
        if (n < 0 || n > 4 * 1024 * 1024) throw new Exception("cache: bad string len " + n);
        byte[] b = new byte[n];
        in.readFully(b);
        return new String(b, "UTF-8");
    }

    /** 读原始字节，不当字符串解码（正文和附加内容走这里，省掉启动时的大头开销）。 */
    static byte[] rBytes(DataInputStream in) throws Exception {
        int n = in.readInt();
        if (n < 0 || n > 4 * 1024 * 1024) throw new Exception("cache: bad bytes len " + n);
        byte[] b = new byte[n];
        in.readFully(b);
        return b;
    }

    private Node parseNode(JSONObject no, Node parent, Subject s) {
        Node n = new Node();
        n.id = no.optString("id", "");
        n.name = no.optString("name", "");
        n.parent = parent;
        n.subj = s;
        JSONArray kids = no.optJSONArray("children");
        if (kids != null) {
            for (int i = 0; i < kids.length(); i++) {
                n.children.add(parseNode(kids.optJSONObject(i), n, s));
            }
        } else {
            n.body = no.optString("body", "");
            n.daily = no.optString("daily", "");
            n.star = no.optInt("star", 2);
            n.img = no.optString("img", "");
            n.d = no.optString("d", "");
            try {
                JSONArray blks = no.optJSONArray("extra");
                if (blks != null) {
                    for (int i = 0; i < blks.length(); i++) {
                        JSONObject bo = blks.optJSONObject(i);
                        if (bo == null) continue;
                        ExtraBlock b = new ExtraBlock();
                        b.name = bo.optString("name", "");
                        b.unit = bo.optString("unit", "");
                        b.mark = bo.optString("mark", "");
                        JSONArray its = bo.optJSONArray("items");
                        if (its != null) {
                            for (int j = 0; j < its.length(); j++) {
                                JSONObject io2 = its.optJSONObject(j);
                                JSONArray lns = (io2 == null) ? null : io2.optJSONArray("lines");
                                if (lns == null) continue;
                                ExtraItem it = new ExtraItem();
                                for (int m = 0; m < lns.length(); m++) {
                                    JSONObject lo = lns.optJSONObject(m);
                                    if (lo == null) continue;
                                    ExtraLine ln = new ExtraLine();
                                    ln.tag = lo.optString("tag", "");
                                    ln.text = lo.optString("text", "");
                                    ln.tone = lo.optString("tone", "body");
                                    if (ln.text.length() > 0) it.lines.add(ln);
                                }
                                if (!it.lines.isEmpty()) b.items.add(it);
                            }
                        }
                        if (!b.items.isEmpty()) n.extras.add(b);
                    }
                }
            } catch (Exception ignored) { }
        }
        if (n.id.length() > 0) {
            nodeById.put(stableKey(n.id), n);
            nodeById.put(n.id, n);
        }
        return n;
    }

    /** 全量解析：把整科文件读成 JSONObject 再递归建 Node。
     *  v3.12.8 试过改成 JsonReader 流式读，实测在这台手表上反而更慢（2830ms vs 2608ms），
     *  已回退。要再提速请走「二进制缓存」那条路，别重复试流式。 */
    private Subject parseSubject(JSONObject so) throws Exception {
        Subject s = new Subject();
        s.key = so.optString("s", "");
        s.name = so.optString("name", s.key);
        JSONArray ns = so.optJSONArray("nodes");
        if (ns != null) {
            for (int j = 0; j < ns.length(); j++) {
                s.roots.add(parseNode(ns.getJSONObject(j), null, s));
            }
        }
        return s;
    }

    /** 收集所有叶子节点，供全局搜索 / 今日新增使用。任意深度都能收全。
     *  分组（有下级的节点）也单独收一份：章节标题本身也要能搜到。 */
    private void buildHits() {
        allHits.clear();
        allGroups.clear();
        for (Subject s : subjects) {
            collectLeaves(s.roots);
        }
    }

    private void collectLeaves(List<Node> nodes) {
        for (Node n : nodes) {
            if (n.isLeaf()) {
                allHits.add(n);
                if (n.d != null && n.d.length() > 0) {
                    List<Node> bucket = nodesByDate.get(n.d);
                    if (bucket == null) {
                        bucket = new ArrayList<Node>();
                        nodesByDate.put(n.d, bucket);
                    }
                    bucket.add(n);
                }
            } else {
                allGroups.add(n);            // 分组：标题也进搜索
                collectLeaves(n.children);
            }
        }
    }

    private void loadData() {
        try {
            nodeById.clear();
            long tStart = System.currentTimeMillis();
            // 先试二进制缓存：命中就不用碰 JSON（换版本后自动失效）
            if (loadFromCache()) {
                buildHits();
                log("BOOT", "走缓存 " + (System.currentTimeMillis() - tStart) + "ms · 科目 " + subjects.size()
                        + " · 分组 " + allGroups.size() + " · 叶子 " + allHits.size());
                return;
            }
            JSONObject orderObj = readJsonAsset("data/order.json");
            JSONArray orderArr = orderObj.getJSONArray("order");
            StringBuilder cost = new StringBuilder();   // 各科解析耗时，写进日志方便定位
            for (int i = 0; i < orderArr.length(); i++) {
                try {
                    long tOne = System.currentTimeMillis();
                    JSONObject oe = orderArr.getJSONObject(i);
                    String key = oe.optString("s", "");
                    String name = oe.optString("name", key);
                    JSONObject subjFile = readJsonAsset("data/" + key + ".json");
                    JSONObject so = subjFile.optJSONObject("subject");
                    if (so == null) continue;
                    so.put("s", key);
                    so.put("name", name);
                    Subject s = parseSubject(so);
                    if (s.roots.isEmpty()) continue;
                    subjects.add(s);
                    subjectMap.put(s.key, s);
                    cost.append(name).append(' ').append(System.currentTimeMillis() - tOne).append("ms ");
                } catch (Exception ignored) { }
            }
            long tHits = System.currentTimeMillis();
            buildHits();
            log("BOOT", "各科解析 " + cost + "| buildHits " + (System.currentTimeMillis() - tHits) + "ms");
            // 流式解析的自检：这三个数必须和构建日志的「科目 9 / 分组 342 / 叶子 1572」一致
            log("BOOT", "解析统计：科目 " + subjects.size() + " · 分组 " + allGroups.size()
                    + " · 叶子 " + allHits.size());
            // 万一解析逻辑出问题，宁可明确报错也不让用户对着空白主页发愣
            if (subjects.isEmpty() || allHits.isEmpty()) {
                loadError = "知识库没读出来（科目 " + subjects.size() + "，条目 " + allHits.size() + "）";
            } else {
                writeKbCache();          // 解析成功就落缓存，下次启动直接读
            }
        } catch (Exception ex) {
            // 现在是在子线程读数据，不能直接弹 Toast（无 Looper），记下来回主线程再提示
            loadError = String.valueOf(ex.getMessage());
        }
    }

    /** 检查 GitHub Release 最新版：有新版先展示更新说明，确认后再下载覆盖安装。 */
    private void checkUpdate(final boolean silent) {
        // 已经在下载、或者更新弹窗正开着，就不再开第二轮：两条线同时下同一个包必然失败
        if (updating || (updateDialog != null && updateDialog.isShowing())) {
            if (!silent) toast(updating ? "正在下载更新，稍等一下" : "更新提示已经弹出来了");
            return;
        }
        downloadCancel = false;
        if (!silent) progressShow("正在检查更新…", "连接更新服务器", -1);
        new Thread(new Runnable() {
            public void run() {
                try {
                    final String local = appVersion();
                    RelInfo rel = fetchReleaseInfo();
                    final String remoteTag = rel.tag;
                    if (compareSemver(local, remoteTag) >= 0) {
                        progressHide();
                        if (!silent) {
                            // 开了测试通道却查不到预发布时要把话说清楚，否则用户只会以为「没有新版」
                            toast((betaChannel && betaFetchFailed)
                                    ? "测试通道没连上，已按正式版判断：v" + local + " 是最新"
                                    : "已是最新版 v" + local);
                        }
                        return;
                    }
                    final String url = rel.apkUrl;
                    final String notes = rel.notes;
                    runOnUiThread(new Runnable() {
                        public void run() {
                            if (!alive()) return;
                            progressHide();
                            showUpdateDialog(remoteTag, notes, url, silent);
                        }
                    });
                } catch (final Exception e) {
                    progressHide();
                    if (!silent) toast("检查更新失败：" + e.getMessage());
                }
            }
        }).start();
    }

    /** 更新说明弹窗：先看这次改了什么，再决定「立即更新」还是「稍后」。 */
    private void showUpdateDialog(final String tag, final String notes, final String url, boolean silent) {
        final String ver = tagWithV(tag);
        // 选过「稍后」的版本，开机自动检查时不再打扰；手动检查仍然提示
        if (silent && ver.equals(prefs.getString("skip_version", ""))) return;
        if (updating) return;                                            // 正在下载就别再弹
        if (updateDialog != null && updateDialog.isShowing()) return;    // 已经弹着一个，不叠第二个
        dismissUpdateDialog();

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(13), dp(11), dp(13), dp(11));
        box.setBackgroundDrawable(pill(C_CARD));
        // 这里不能再给整块开软件层：层里的内容不会跟着内层 ScrollView 的滚动及时重画，
        // 滑起来就会留下上一帧的文字（残影）。改成滚动时整块重绘，见下面 sc 的监听。

        TextView t = new TextView(this);
        t.setText("发现新版本 " + ver);
        t.setTextSize(m.subtitleSize + 1f);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(C_PRIMARY);
        t.setGravity(Gravity.CENTER);
        box.addView(t, wrap());

        TextView cur = new TextView(this);
        cur.setText("当前 v" + appVersion() + "（build " + appVersionCode() + "）");
        cur.setTextSize(m.subtitleSize - 2f);
        cur.setTextColor(C_SUB);
        cur.setGravity(Gravity.CENTER);
        cur.setPadding(0, dp(2), 0, dp(6));
        box.addView(cur, wrap());

        String desc = trimNotes(notes);
        if (desc.length() == 0) desc = "这次没有单独写说明，安装后可在「设置 → 关于 → 更新日志」里查看全部记录。";

        TextView head = new TextView(this);
        head.setText("更新内容");
        head.setGravity(Gravity.CENTER);
        head.setTextSize(m.subtitleSize - 1.5f);
        head.setTextColor(C_SUB);
        head.setPadding(dp(2), 0, dp(2), dp(2));
        box.addView(head, wrap());

        ScrollView sc = new ScrollView(this);
        sc.setBackgroundColor(C_CARD);
        TextView body = new TextView(this);
        body.setText(desc);
        body.setTextSize(m.subtitleSize - 1f);
        body.setTextColor(C_TEXT);
        body.setLineSpacing(dp(2), 1.16f);
        body.setPadding(dp(2), 0, dp(2), dp(1));
        sc.addView(body);
        int maxH = Math.min(dp(190), (int) (getResources().getDisplayMetrics().heightPixels * 0.45f));
        box.addView(sc, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, maxH));

        // 滚动时整块重绘，卡片区域每帧都完整刷新，避免留下上一帧的文字。
        tuneDialogScroll(sc, box);

        TextView go = new TextView(this);
        go.setText("立即更新");
        go.setTextSize(m.backSize);
        go.setTypeface(Typeface.DEFAULT_BOLD);
        go.setTextColor(C_PRIMARY);
        go.setGravity(Gravity.CENTER);
        go.setPadding(dp(8), dp(10), dp(8), dp(10));
        go.setBackground(clickBg(0xFF14384A));
        go.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                vibrate();
                dismissUpdateDialog();
                prefs.edit().remove("skip_version").apply();
                startUpdate(url, tag);
            }
        });
        LinearLayout.LayoutParams glp = wrap();
        glp.topMargin = dp(9);
        box.addView(go, glp);

        TextView later = new TextView(this);
        later.setText("稍后");
        later.setTextSize(m.subtitleSize - 1f);
        later.setTextColor(C_SUB);
        later.setGravity(Gravity.CENTER);
        later.setPadding(dp(8), dp(8), dp(8), dp(2));
        later.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                vibrate();
                dismissUpdateDialog();
                prefs.edit().putString("skip_version", ver).apply();
            }
        });
        box.addView(later, wrap());

        TextView more = new TextView(this);
        more.setText("查看历次更新日志");
        more.setTextSize(m.subtitleSize - 2.5f);
        more.setTextColor(C_PRIMARY);
        more.setGravity(Gravity.CENTER);
        more.setPadding(dp(8), dp(2), dp(8), 0);
        more.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                vibrate();
                dismissUpdateDialog();
                changelogBack = (screen == S_CHANGELOG) ? S_SETTINGS : screen;
                showScreen(S_CHANGELOG);
            }
        });
        box.addView(more, wrap());

        updateDialog = new AlertDialog.Builder(this).setView(box).create();
        updateDialog.setCancelable(true);
        // 窗口背景透明：弹窗以外完全不遮内容；像素格式也要带透明，否则滚动会留残影。
        tuneDialogWindow(updateDialog);
        updateDialog.show();
        try {
            Window w = updateDialog.getWindow();
            int sw = getResources().getDisplayMetrics().widthPixels;
            w.setLayout((int) (sw * (m.round ? 0.74f : 0.86f)), WindowManager.LayoutParams.WRAP_CONTENT);
        } catch (Exception ignored) { }

        // 正文区高度贴合实际字数：内容短就刚好放下（不滚动、下面也不空一大块），
        // 内容长才封顶到 maxH 再滚动。等弹窗真正布局完在量 body 的真实高度（此时宽度是真实的）。
        final android.view.ViewTreeObserver dlgVto = box.getViewTreeObserver();
        dlgVto.addOnGlobalLayoutListener(
                new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
                    public void onGlobalLayout() {
                        int want = body.getMeasuredHeight() + dp(6);   // 多 6dp，避免行距/标点的误差裁掉末行
                        int h = Math.min(want, maxH);
                        if (sc.getLayoutParams() != null && sc.getLayoutParams().height != h) {
                            sc.getLayoutParams().height = h;
                            sc.requestLayout();
                        }
                        // 只量一次：正文高度不会变了，量完就撤监听，免得改高度触发重布局又回调
                        try { dlgVto.removeOnGlobalLayoutListener(this); } catch (Throwable ignored) { }
                    }
                });
    }

    private void dismissUpdateDialog() {
        try {
            if (updateDialog != null && updateDialog.isShowing()) updateDialog.dismiss();
        } catch (Exception ignored) { }
        updateDialog = null;
    }

    /** Release 说明太长，整理成小屏能读的纯文本（去空行、去 Markdown 记号）。 */
    private String trimNotes(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (String raw : s.replace("\r\n", "\n").split("\n")) {
            String l = raw.trim();
            if (l.length() == 0) continue;
            if (l.startsWith("#")) l = l.replace("#", "").trim();
            if (l.startsWith("- ") || l.startsWith("* ") || l.startsWith("• ")) l = "· " + l.substring(2).trim();
            int cut = l.indexOf(" - ");
            if (cut > 0 && cut < 12 && l.charAt(0) == 'v') l = l.substring(cut + 3).trim();
            if (l.length() == 0) continue;
            if (sb.length() > 0) sb.append('\n');
            sb.append(l);
        }
        return sb.toString();
    }

    /** 用户确认更新后：权限不足先去申请，回来接着下载；然后下载并唤起安装器。 */
    private void startUpdate(final String url, final String tag) {
        if (updating) { toast("已经在下载了"); return; }
        if (Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
            pendingUrl = url;
            pendingTag = tag;
            pendingDownload = true;
            requestInstallPermission();
            return;
        }
        updating = true;
        new Thread(new Runnable() {
            public void run() {
                try {
                    // 从弹窗到点下载之间可能又发了新版，动手前再确认一次，别下到上一个版本
                    String useTag = tag, useUrl = url;
                    progressShow("正在确认最新版本…", "连接更新服务器", -1);
                    final String[] got = new String[2];
                    Thread probe = new Thread(new Runnable() {
                        public void run() {
                            try {
                                RelInfo rel = fetchReleaseInfo();
                                got[0] = rel.tag;
                                got[1] = rel.apkUrl;
                            } catch (Exception ignored) { }
                        }
                    });
                    probe.start();
                    try { probe.join(6000); } catch (Exception ignored) { }
                    if (got[0] != null && got[0].length() > 0 && compareSemver(got[0], useTag) > 0) {
                        useTag = got[0];
                        useUrl = got[1];
                    }
                    if (!reuseDownloaded(useTag)) {
                        downloadApkWithMirrors(useUrl, useTag);
                    }
                    if (downloadCancel) {
                        downloadCancel = false;
                        progressHide();
                        toast("已取消更新");
                        return;
                    }
                    prefs.edit().putString("downloaded_tag", "v" + tag).apply();
                    toast("下载完成，正在打开安装程序…");
                    runOnUiThread(new Runnable() {
                        public void run() {
                            progressHide();
                            if (!foreground) {
                                // 应用不在前台时系统会静默拦截启动安装器，等回来再弹
                                pendingInstall = true;
                                toast("更新已下载，回到应用后自动安装");
                                return;
                            }
                            // 刚 rename 完 APK、进度条也刚收起，安装器立刻拉起容易被系统当成
                            // 「后台启动界面」拦掉；先等一会儿，再要求窗口有焦点才真正启动
                            root.postDelayed(new Runnable() {
                                public void run() { installApkWithRetry(0); }
                            }, 1200);
                        }
                    });
                } catch (final Exception e) {
                    progressHide();
                    if (downloadCancel) {
                        downloadCancel = false;
                        toast("已取消更新");
                    } else {
                        toast("下载失败：" + e.getMessage());
                    }
                } finally {
                    updating = false;   // 下载这条线走完了，才允许下一次更新
                }
            }
        }).start();
    }

    private static class RelInfo {
        String tag;
        String apkUrl;
        String notes;
    }

    /** 先走 API；API 不通或给的还是旧版本时，用仓库里的静态 version.json 兜底/纠偏。 */
    private RelInfo fetchReleaseInfo() throws Exception {
        RelInfo r = new RelInfo();
        String apiTag = "", apiUrl = "", apiNotes = "";
        try {
            JSONObject obj = fetchLatestRelease();
            apiTag = obj.optString("tag_name", "");
            apiNotes = obj.optString("body", "");
            JSONArray assets = obj.optJSONArray("assets");
            if (assets != null) {
                for (int i = 0; i < assets.length(); i++) {
                    JSONObject a = assets.getJSONObject(i);
                    String n = a.optString("name", "");
                    if (!n.endsWith(".apk")) continue;
                    // 资源名必须对得上 tag，防止拿到上一个版本留下的旧包
                    if (apiTag.length() == 0 || n.contains(apiTag)) {
                        apiUrl = a.optString("browser_download_url", "");
                        break;
                    }
                }
            }
        } catch (Exception ignored) { }

        // 镜像有缓存，API 有可能返回上一个版本；两边比一下，取更新的
        String staticTag = "";
        try { staticTag = fetchStaticTag(); } catch (Exception ignored) { }

        String best = apiTag;
        if (best.length() == 0) best = staticTag;
        else if (staticTag.length() > 0 && compareSemver(staticTag, best) > 0) best = staticTag;
        if (best == null || best.length() == 0) throw new Exception("无法获取版本号");

        r.tag = best;
        boolean apiWins = best.equals(apiTag);
        r.notes = apiWins ? apiNotes : "";
        // 只有 API 这条胜出、且下载地址确实属于这个 tag，才用 API 给的地址，其余一律按 tag 拼
        r.apkUrl = (apiWins && apiUrl.length() > 0 && apiUrl.contains(best))
                ? apiUrl : apkUrlFor(best);
        return r;
    }

    /** 版本号统一带一个 v（tag 本身已含 v 时不重复）。 */
    private String tagWithV(String tag) {
        if (tag == null || tag.length() == 0) return "";
        return (tag.startsWith("v") || tag.startsWith("V")) ? tag : "v" + tag;
    }

    private String apkUrlFor(String tag) {
        return RELEASE_DL + tag + "/HZKnowledge-" + tag + ".apk";
    }

    /** 直连失败时换支持 API 转发的镜像取 release 信息。
     *  正式版通道永远都查；测试开关只是「多给两个候选」，不能比正式版还差——
     *  否则正式版比缓存里的预发布新时，开了测试版的人反而收不到更新。 */
    private JSONObject fetchLatestRelease() throws Exception {
        // 必须带时间戳参数：镜像普遍缓存 /releases/latest，不带的话刚发版也会拿回上一个版本
        String q = "?nocache=" + System.currentTimeMillis();
        JSONObject normal = null;
        Exception last = null;
        try {
            normal = new JSONObject(fetchApiText(RELEASES_API + q));
        } catch (Exception e) {
            last = e;
        }
        if (!betaChannel) {
            if (normal != null) return normal;
            throw (last == null) ? new Exception("无法连接更新服务器") : last;
        }

        // 测试通道：候选有三个，取版本号最高的那个
        // ① 正式版 latest（最稳，且带更新说明）；② Release 列表第一条（实时，可能含预发布）；
        // ③ beta.json（几十字节、raw 镜像，但有几分钟 CDN 缓存，只在前两个都拿不到时才靠它）。
        JSONObject best = normal;
        String bestTag = (normal == null) ? "" : normal.optString("tag_name", "");
        JSONObject listTop = null;
        try {
            String lu = RELEASES_LIST_API + "?per_page=1&nocache=" + System.currentTimeMillis();
            JSONArray arr = new JSONArray(fetchApiText(lu, 8000, 15000));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o != null && !o.optBoolean("draft", false)) {
                    listTop = o;
                    break;
                }
            }
        } catch (Exception e) {
            log("UPD", "Release 列表查询失败：" + e);
        }
        if (listTop != null) {
            String t = listTop.optString("tag_name", "");
            if (best == null || compareSemver(t, bestTag) > 0) {
                best = listTop;
                bestTag = t;
            }
        }
        String btag = fetchBetaTag();
        if (btag.length() > 0 && (best == null || compareSemver(btag, bestTag) > 0)) {
            try {
                JSONObject o = new JSONObject();
                o.put("tag_name", btag);
                best = o;
                bestTag = btag;
            } catch (Exception ignored) { }
        }
        betaFetchFailed = (best == null);
        if (best == null) throw (last == null) ? new Exception("无法获取版本号") : last;
        return best;
    }

    /** 依次走「启动时测出的最优通道 → 各镜像」取一段 GitHub API 文本。 */
    private String fetchApiText(String fullUrl) throws Exception {
        return fetchApiText(fullUrl, 5000, 6000);
    }

    /** 同上，超时可指定：Release 列表比 latest 大十倍（20KB vs 2KB），手表上得给宽一点。 */
    private String fetchApiText(String fullUrl, int connectMs, int readMs) throws Exception {
        Exception last = null;
        // 先走启动时测出来的最优通道，多数情况一步就中
        try {
            return httpGetText(apiBase() + fullUrl, connectMs, readMs);
        } catch (Exception e) { last = e; }
        for (int i = 0; i <= API_MIRRORS.length; i++) {
            String u = (i == 0) ? fullUrl : API_MIRRORS[i - 1] + fullUrl;
            try {
                return httpGetText(u, connectMs, readMs);
            } catch (Exception e) {
                last = e;
            }
        }
        throw (last == null) ? new Exception("无法连接更新服务器") : last;
    }

    /** 读仓库里的静态版本号（raw，镜像覆盖面最广）。既当兜底，也用来给 API 结果做交叉校验。 */
    private String fetchStaticTag() throws Exception {
        Exception last = null;
        String q = "?nocache=" + System.currentTimeMillis();
        List<String> cands = new ArrayList<String>();
        addNoDup(cands, rawBase());
        addNoDup(cands, "");
        for (int i = 0; i < Math.min(2, MIRRORS.length); i++) addNoDup(cands, MIRRORS[i]);
        for (String base : cands) {
            try {
                String v = new JSONObject(httpGetText(base + VERSION_JSON_RAW + q, 4000, 5000))
                        .optString("version", "");
                if (v.length() > 0) return v.startsWith("v") ? v : "v" + v;
            } catch (Exception e) { last = e; }
        }
        throw (last == null) ? new Exception("无法获取版本号") : last;
    }

    /** 测试通道的版本号：读仓库里的 cloud/beta.json。
     *  为什么不用 API 列表：列表响应 20KB 左右、未认证每小时只有 60 次额度，
     *  手表网络下经常超时或撞限流（用户看到的就是「测试通道没连上」）。
     *  beta.json 只有几十字节、走 raw 镜像（覆盖最广），成功率和正式版号那条路一样。
     *  拿不到返回空串，调用方再退回 API 列表。 */
    private String fetchBetaTag() {
        String q = "?nocache=" + System.currentTimeMillis();
        List<String> cands = new ArrayList<String>();
        addNoDup(cands, rawBase());
        addNoDup(cands, "");
        for (int i = 0; i < Math.min(3, MIRRORS.length); i++) addNoDup(cands, MIRRORS[i]);
        for (String base : cands) {
            try {
                JSONObject o = new JSONObject(httpGetText(base + BETA_JSON_RAW + q, 5000, 7000));
                String tag = o.optString("tag", "");
                if (tag.length() > 0) return tag.startsWith("v") || tag.startsWith("V") ? tag : "v" + tag;
            } catch (Exception ignored) { }
        }
        return "";
    }

    private void addNoDup(List<String> list, String v) {
        if (v != null && !list.contains(v)) list.add(v);
    }

    private String httpGetText(String urlStr, int connectMs, int readMs) throws Exception {
        HttpURLConnection cn = (HttpURLConnection) new URL(urlStr).openConnection();
        try {
            cn.setConnectTimeout(connectMs);
            cn.setReadTimeout(readMs);
            cn.setInstanceFollowRedirects(true);
            cn.setRequestProperty("Accept", "application/vnd.github+json");
            cn.setRequestProperty("User-Agent", "HZKnowledge-Android");
            InputStream is = cn.getInputStream();
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] b = new byte[8192];
            int n;
            while ((n = is.read(b)) > 0) bos.write(b, 0, n);
            is.close();
            return new String(bos.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            cn.disconnect();
        }
    }

    /** 给有道词典用的请求：Accept 必须是 application/json，别的都返回 500。
     *  httpGetText 里钉死的是 GitHub 的 Accept（application/vnd.github+json），
     *  有道 jsonapi 对这个头直接 500，词组/句子查不出来、弹窗打不开——就是这个原因。
     *  suggest 接口倒是不挑，但两条路统一走这里，省得以后再踩。 */
    private String httpGetTextDict(String urlStr, int connectMs, int readMs) throws Exception {
        HttpURLConnection cn = (HttpURLConnection) new URL(urlStr).openConnection();
        try {
            cn.setConnectTimeout(connectMs);
            cn.setReadTimeout(readMs);
            cn.setInstanceFollowRedirects(true);
            cn.setRequestProperty("Accept", "application/json");
            cn.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Linux; Android 11) AppleWebKit/537.36 (KHTML, like Gecko)");
            InputStream is = cn.getInputStream();
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] b = new byte[8192];
            int n;
            while ((n = is.read(b)) > 0) bos.write(b, 0, n);
            is.close();
            return new String(bos.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            cn.disconnect();
        }
    }

    // ================= 启动时的通道探测 =================
    /** 探测 api.github.com 用的轻量地址（无需鉴权、体积极小）。 */
    private static final String PROBE_API = "https://api.github.com/zen";
    private static final String ROUTE_API_KEY = "route_api";
    private static final String ROUTE_RAW_KEY = "route_raw";
    private static final String ROUTE_NONE = "none";

    /** 探测出来的最优通道：null = 还没测出来 / 全不通，"" = 直连。 */
    private static volatile String routeApi = null;
    private static volatile String routeRaw = null;
    private boolean routeProbing = false;
    private long lastReProbe = 0;

    /** 启动时没探到通道（或网络后来才连上）时再探一次；60 秒内最多一次，别把手表刷爆。 */
    private void reProbeIfNeeded() {
        long now = System.currentTimeMillis();
        if (now - lastReProbe < 60000L) return;
        lastReProbe = now;
        runOnUiThread(new Runnable() {
            public void run() { if (alive() && !routeProbing) startRouteProbe(false); }
        });
    }

    /** App 启动时后台跑一次：并发试直连与各镜像，挑最快能用的记下来。
     *  不弹任何界面；只有真连上了才浮一个气泡说明走的是哪条路。 */
    private void startRouteProbe() { startRouteProbe(false); }

    /** @param manual 设置里手动点的：不沿用上次结果，重新挨个测一遍，且无论成败都给提示。 */
    private void startRouteProbe(final boolean manual) {
        if (manual) {
            if (routeProbing) {
                toast("正在检测，稍等");
                return;
            }
            routeApi = null;
            routeRaw = null;
        } else {
            // 先套用上次测出来的结果，保证首屏就有可用通道
            routeApi = savedRoute(ROUTE_API_KEY);
            routeRaw = savedRoute(ROUTE_RAW_KEY);
        }
        if (routeProbing) return;
        routeProbing = true;
        new Thread(new Runnable() {
            public void run() {
                // 两条通道同时探，别让启动等待翻倍
                final java.util.concurrent.CountDownLatch done =
                        new java.util.concurrent.CountDownLatch(2);
                final String[] got = new String[2];
                new Thread(new Runnable() {
                    public void run() {
                        got[0] = probeBest(apiPrefixes(), PROBE_API);
                        done.countDown();
                    }
                }).start();
                new Thread(new Runnable() {
                    public void run() {
                        got[1] = probeBest(rawPrefixes(), VERSION_JSON_RAW);
                        done.countDown();
                    }
                }).start();
                try {
                    done.await(8, java.util.concurrent.TimeUnit.SECONDS);
                } catch (Exception ignored) { }
                String api = got[0];
                String raw = got[1];
                if (api != null) {
                    routeApi = api;
                    prefs.edit().putString(ROUTE_API_KEY, api).apply();
                } else {
                    prefs.edit().putString(ROUTE_API_KEY, ROUTE_NONE).apply();
                }
                if (raw != null) {
                    routeRaw = raw;
                    prefs.edit().putString(ROUTE_RAW_KEY, raw).apply();
                } else {
                    prefs.edit().putString(ROUTE_RAW_KEY, ROUTE_NONE).apply();
                }
                routeProbing = false;
                final String pick = (api != null) ? api : raw;
                runOnUiThread(new Runnable() {
                    public void run() {
                        if (!alive()) return;
                        refreshRouteHint();
                    }
                });
                if (manual) {
                    // 手动点的，成败都要有回话
                    if (pick == null) showBubble("没连上服务器，稍后再试");
                    else showBubble("已连接服务器 · " + routeName(pick));
                    return;
                }
                if (pick == null) return;              // 全不通就别报喜
                // 首启引导页不打扰；点「完成」后 recreate 会重跑一次，那时再补上
                if (tuningFirstRun) return;
                showBubble("已连接服务器 · " + routeName(pick));
            }
        }).start();
    }

    private String[] apiPrefixes() {
        String[] out = new String[API_MIRRORS.length + 1];
        out[0] = "";
        for (int i = 0; i < API_MIRRORS.length; i++) out[i + 1] = API_MIRRORS[i];
        return out;
    }

    private String[] rawPrefixes() {
        String[] out = new String[MIRRORS.length + 1];
        out[0] = "";
        for (int i = 0; i < MIRRORS.length; i++) out[i + 1] = MIRRORS[i];
        return out;
    }

    /** 并发探测所有候选，取「能用且最快」的那个前缀；全都连不上返回 null。 */
    private String probeBest(String[] prefixes, String url) {
        final Object lock = new Object();
        final String[] best = {null};
        final long[] bestCost = {Long.MAX_VALUE};
        final java.util.concurrent.CountDownLatch left =
                new java.util.concurrent.CountDownLatch(prefixes.length);
        for (int i = 0; i < prefixes.length; i++) {
            final String pre = prefixes[i];
            new Thread(new Runnable() {
                public void run() {
                    long t0 = android.os.SystemClock.uptimeMillis();
                    try {
                        String s = httpGetText(pre + url, 4000, 4000);
                        if (s != null && s.length() > 0) {
                            long cost = android.os.SystemClock.uptimeMillis() - t0;
                            synchronized (lock) {
                                if (cost < bestCost[0]) {
                                    bestCost[0] = cost;
                                    best[0] = pre;
                                }
                            }
                        }
                    } catch (Exception ignored) { }
                    left.countDown();
                }
            }).start();
        }
        try {
            left.await(6, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception ignored) { }
        synchronized (lock) { return best[0]; }
    }

    /** 设置页里那行「当前走哪条路」的说明。 */
    private void refreshRouteHint() {
        if (routeHint == null) return;
        String a = (routeApi == null) ? "未连通" : routeName(routeApi);
        String r = (routeRaw == null) ? "未连通" : routeName(routeRaw);
        routeHint.setTextColor((routeApi == null && routeRaw == null) ? 0xFFE57373 : C_SUB);
        routeHint.setText("GitHub 接口：" + a + "；文件与头像：" + r);
    }

    private String savedRoute(String key) {
        String v = prefs.getString(key, "");
        return ROUTE_NONE.equals(v) ? null : v;
    }

    /** 拼请求前缀：测出来了就用测出来的，没测出来（或全不通）就直连。 */
    private String apiBase() { return routeApi == null ? "" : routeApi; }

    /**
     * 带 token 的接口必须直连，不能走镜像。
     * 镜像（gh-proxy.com 等）按 URL 缓存响应、会忽略 Authorization 头，
     * 而 /user 这类接口是「凭 token 返回当前用户」，走镜像会拿到缓存里别人的账号。
     * 实测：同一 URL 带本人的 token 经镜像返回的是 aeroheaven1 / HHPLow，
     * 带伪造的无效 token 也返回 HHPLow —— 镜像根本没看 token。
     */
    private String apiAuthedBase() { return ""; }
    private String rawBase() { return routeRaw == null ? "" : routeRaw; }

    private String routeName(String prefix) {
        return (prefix == null || prefix.length() == 0) ? "直连" : hostOf(prefix);
    }

    /** 屏幕下方浮出一个小气泡，淡入停留后自动淡出移除，不拦截任何操作。 */
    /** 全局唯一的提示气泡：底部圆角卡片、白字、淡入淡出。
     *  toast / 启动问候 / 服务器状态 / 更新结果 全部走这里，界面上只存在一种提示样式。 */
    private void showBubble(final String text) {
        if (text == null || text.length() == 0) return;
        runOnUiThread(new Runnable() {
            public void run() {
                if (root == null) {
                    // 界面还没建好（极少见）：退回系统提示，别把消息吞掉
                    try {
                        Toast.makeText(getApplicationContext(), text, Toast.LENGTH_SHORT).show();
                    } catch (Exception ignored) { }
                    return;
                }
                if (bubbleView == null) {
                    TextView tv = new TextView(MainActivity.this);
                    tv.setTextSize(m.subtitleSize - 1f);
                    tv.setTextColor(C_TEXT);
                    tv.setGravity(Gravity.CENTER);
                    tv.setPadding(dp(14), dp(8), dp(14), dp(8));
                    tv.setBackgroundDrawable(pill(C_CARD));
                    tv.setMaxWidth((int) (getResources().getDisplayMetrics().widthPixels * 0.86f));
                    tv.setClickable(false);
                    tv.setFocusable(false);
                    FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
                    lp.bottomMargin = dp(18);
                    tv.setLayoutParams(lp);
                    bubbleView = tv;
                    root.addView(tv);
                }
                // 已经在弹的：换文字、重新计时，不叠加第二个气泡；
                // 已经可见的就不要再从透明淡入一次，否则连续提示会闪。
                boolean visible = bubbleView.getAlpha() > 0.9f;
                bubbleView.setText(text);
                bubbleView.animate().cancel();
                if (!visible) {
                    bubbleView.setAlpha(0f);
                    bubbleView.animate().alpha(1f).setDuration(160).start();
                } else {
                    bubbleView.setAlpha(1f);
                }
                bubbleView.removeCallbacks(bubbleHide);
                bubbleView.postDelayed(bubbleHide, 1900);
            }
        });
    }

    /** 到点淡出并移除；被新的提示打断时会被 removeCallbacks 取消。 */
    private final Runnable bubbleHide = new Runnable() {
        public void run() {
            if (bubbleView == null) return;
            bubbleView.animate().cancel();
            bubbleView.animate().alpha(0f).setDuration(280).withEndAction(new Runnable() {
                public void run() {
                    if (bubbleView != null && bubbleView.getParent() instanceof ViewGroup) {
                        ((ViewGroup) bubbleView.getParent()).removeView(bubbleView);
                    }
                    bubbleView = null;
                }
            }).start();
        }
    };

    private void requestInstallPermission() {
        try {
            startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + getPackageName())));
            toast("请开启「允许安装未知应用」，回到应用后会自动继续");
        } catch (Exception e) {
            toast("请在系统设置中开启安装未知应用权限");
        }
    }

    private int[] parseSemver(String v) {
        int[] parts = new int[3];
        int idx = 0;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < v.length() && idx < 3; i++) {
            char c = v.charAt(i);
            if (c >= '0' && c <= '9') {
                sb.append(c);
            } else if (sb.length() > 0) {
                parts[idx++] = parseIntSafe(sb.toString());
                sb.setLength(0);
                if (idx >= 3) break;
            }
        }
        if (sb.length() > 0 && idx < 3) parts[idx++] = parseIntSafe(sb.toString());
        return parts;
    }

    private int parseIntSafe(String s) {
        try { return Integer.parseInt(s); } catch (Exception e) { return 0; }
    }

    private int compareSemver(String a, String b) {
        int[] x = parseSemver(a), y = parseSemver(b);
        for (int i = 0; i < 3; i++) {
            if (x[i] < y[i]) return -1;
            if (x[i] > y[i]) return 1;
        }
        // 三段一样，再看测试版序号：3.12.5pre2 比 3.12.5pre1 新，但都比正式版 3.12.5 旧。
        // 不比这一段的话，连着发 pre1/pre2 时 App 会认为「已是最新」，测试版根本收不到。
        int p = preIndex(a), q = preIndex(b);
        if (p == 0 && q == 0) return 0;
        if (p == 0) return 1;                  // a 是正式版 → 比 b 的测试版新
        if (q == 0) return -1;
        return (p < q) ? -1 : (p > q ? 1 : 0);
    }

    /** 取版本号里的测试版序号（3.12.5pre2 → 2），不是测试版返回 0。 */
    private int preIndex(String v) {
        if (v == null) return 0;
        String s = v.toLowerCase();
        int i = s.indexOf("pre");
        if (i < 0) return 0;
        int j = i + 3, n = 0;
        while (j < s.length() && s.charAt(j) >= '0' && s.charAt(j) <= '9') {
            n = n * 10 + (s.charAt(j) - '0');
            j++;
        }
        return n > 0 ? n : 1;                  // 只写 pre 不带数字，按 pre1 算
    }

    /** 同一个版本下载过一次就直接复用，不再重复下载。 */
    private boolean reuseDownloaded(String tag) {
        File f = new File(getFilesDir(), APK_FILE);
        return f.exists() && f.length() > 200 * 1024 && tag.equals(prefs.getString("apk_tag", ""));
    }

    /** 直连 + 各镜像依次尝试下载，边下边更新进度；成功后落到 filesDir/update.apk。 */
    private void downloadApkWithMirrors(String originUrl, String tag) throws Exception {
        File dir = getFilesDir();
        // 每次下载前先清掉上次遗留的安装包（含半截的 .tmp），避免旧包占空间、也避免装错版本
        deleteQuietly(new File(dir, APK_FILE));
        deleteQuietly(new File(dir, APK_FILE + ".tmp"));
        String lastErr = null;
        for (int i = 0; i <= MIRRORS.length; i++) {
            if (downloadCancel) throw new Exception("已取消");
            String src = sourceName(i);
            String u = (i == 0) ? originUrl : MIRRORS[i - 1] + originUrl;
            progressShow("正在下载 " + tagWithV(tag), i == 0 ? "直连，正在连接…" : "切换镜像 " + src + "…", 0);
            File tmp = new File(dir, APK_FILE + ".tmp");
            if (tmp.exists() && !tmp.delete()) { }
            HttpURLConnection cn = null;
            FileOutputStream fos = null;
            try {
                cn = (HttpURLConnection) new URL(u).openConnection();
                activeCn = cn;
                // 直连只在前面几秒试一下：超过 5 秒还没动静就直接换镜像，别让人干等（反馈 #43）
                boolean direct = (i == 0);
                cn.setConnectTimeout(direct ? 5000 : 8000);
                cn.setReadTimeout(direct ? 5000 : 20000);
                cn.setInstanceFollowRedirects(true);
                cn.setRequestProperty("User-Agent", "HZKnowledge-Android");
                int total = cn.getContentLength();
                InputStream is = cn.getInputStream();
                fos = new FileOutputStream(tmp);
                byte[] b = new byte[16384];
                int n, done = 0, lastPct = -1;
                long t0 = System.currentTimeMillis();
                while ((n = is.read(b)) > 0) {
                    if (downloadCancel) throw new Exception("已取消");
                    if (direct && done == 0 && System.currentTimeMillis() - t0 > 5000) {
                        throw new Exception("直连太慢");
                    }
                    fos.write(b, 0, n);
                    done += n;
                    if (done < 150 * 1024 && System.currentTimeMillis() - t0 > (direct ? 8000 : 15000)) {
                        throw new Exception("连接太慢");
                    }
                    int pct = (total > 0) ? (int) (done * 100L / total) : -1;
                    if (pct != lastPct) {
                        lastPct = pct;
                        progressShow("正在下载 " + tagWithV(tag), progressText(done, total, src), pct);
                    }
                }
                is.close();
                fos.close();
                fos = null;
                if (total > 0 && done < total) throw new Exception("连接中断");
                if (done < 200 * 1024) throw new Exception("返回内容不是安装包");
                File dst = new File(dir, APK_FILE);
                if (dst.exists() && !dst.delete()) { }
                if (!tmp.renameTo(dst)) throw new Exception("保存安装包失败");
                prefs.edit().putString("apk_tag", tag).apply();
                return;
            } catch (Exception e) {
                lastErr = e.getMessage();
                if (tmp.exists() && !tmp.delete()) { }
            } finally {
                if (fos != null) { try { fos.close(); } catch (Exception ignored) { } }
                if (cn != null) { try { cn.disconnect(); } catch (Exception ignored) { } }
                if (activeCn == cn) activeCn = null;
            }
        }
        throw new Exception(lastErr == null ? "所有下载源都失败" : lastErr);
    }

    private String sourceName(int i) {
        if (i <= 0) return "直连";
        String s = MIRRORS[i - 1].replace("https://", "");
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    private String progressText(long done, long total, String src) {
        if (total > 0) {
            return String.format(Locale.US, "%d%% · %.1f/%.1f MB · %s",
                    (int) (done * 100L / total), done / 1048576.0, total / 1048576.0, src);
        }
        return String.format(Locale.US, "%.1f MB · %s", done / 1048576.0, src);
    }

    /** 进度卡片：不挡操作、不拦截点击，随时可见下载状态。 */
    private void progressShow(final String title, final String detail, final int percent) {
        runOnUiThread(new Runnable() {
            public void run() {
                if (root == null) return;
                if (progBox == null) buildProgressBox();
                progTitle.setText(title);
                progDetail.setText(detail);
                setProgFill(percent);
                progBox.setVisibility(View.VISIBLE);
            }
        });
    }

    private void progressHide() {
        runOnUiThread(new Runnable() {
            public void run() {
                if (progBox != null) progBox.setVisibility(View.GONE);
            }
        });
    }

    private void buildProgressBox() {
        progBarW = dp(140);
        progBox = new FrameLayout(this);
        progBox.setVisibility(View.GONE);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(11), dp(14), dp(13));
        card.setBackgroundDrawable(pill(C_CARD));
        card.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { }
        });

        progTitle = new TextView(this);
        progTitle.setTextSize(m.subtitleSize);
        progTitle.setTypeface(Typeface.DEFAULT_BOLD);
        progTitle.setTextColor(C_TEXT);
        progTitle.setGravity(Gravity.CENTER);
        card.addView(progTitle, wrap());

        progDetail = new TextView(this);
        progDetail.setTextSize(m.subtitleSize - 1.5f);
        progDetail.setTextColor(C_SUB);
        progDetail.setGravity(Gravity.CENTER);
        progDetail.setPadding(0, dp(3), 0, 0);
        card.addView(progDetail, wrap());

        FrameLayout bar = new FrameLayout(this);
        bar.setBackgroundDrawable(pill(C_PILL));
        progFill = new View(this);
        progFill.setBackgroundDrawable(pill(C_PRIMARY));
        bar.addView(progFill, new FrameLayout.LayoutParams(0, FrameLayout.LayoutParams.MATCH_PARENT));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(progBarW, dp(5));
        blp.topMargin = dp(9);
        card.addView(bar, blp);

        TextView cancelBtn = new TextView(this);
        cancelBtn.setText("取消");
        cancelBtn.setTextSize(m.subtitleSize - 1f);
        cancelBtn.setTextColor(C_SUB);
        cancelBtn.setGravity(Gravity.CENTER);
        cancelBtn.setPadding(dp(8), dp(7), dp(8), dp(2));
        cancelBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                vibrate();
                downloadCancel = true;
                // 立即断开当前下载连接，让阻塞的读取马上抛异常退出，别再等超时
                if (activeCn != null) { try { activeCn.disconnect(); } catch (Exception ignored) { } }
                progressHide();
                toast("已取消更新");
            }
        });
        LinearLayout.LayoutParams cancelLp = wrap();
        cancelLp.topMargin = dp(5);
        card.addView(cancelBtn, cancelLp);

        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        progBox.addView(card, clp);
        root.addView(progBox, fp());
    }

    private void setProgFill(int percent) {
        if (progFill == null) return;
        int p = percent < 0 ? 0 : (percent > 100 ? 100 : percent);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) progFill.getLayoutParams();
        lp.width = progBarW * p / 100;
        progFill.setLayoutParams(lp);
    }

    /** 拉起安装器前确认 App 仍在前台、窗口已拿到焦点；拿不到就多等一拍再试，
     *  最多试 4 次，避免安装器被系统当成后台启动而静默忽略。 */
    private void installApkWithRetry(final int attempt) {
        if (!foreground || isFinishing()) {
            pendingInstall = true;
            toast("更新已下载，回到应用后自动安装");
            return;
        }
        if (!hasWindowFocus() && attempt < 4) {
            root.postDelayed(new Runnable() {
                public void run() { installApkWithRetry(attempt + 1); }
            }, 400);
            return;
        }
        installApk();
    }

    /** 启动时清理：安装成功的旧包（版本号已经等于下载标记）和任何残留 apk 都删掉。 */
    private void cleanupOldApk() {
        try {
            File dir = getFilesDir();
            String tag = prefs.getString("downloaded_tag", "");
            File f = new File(dir, APK_FILE);
            if (f.exists()) {
                String cur = "v" + getVersionName();
                boolean installed = tag.length() > 0 && tag.equals(cur);
                // 已经装上了这一版，或者压根没记录，就把文件删掉
                if (installed || tag.length() == 0) {
                    deleteQuietly(f);
                    prefs.edit().remove("downloaded_tag").apply();
                }
            }
            deleteQuietly(new File(dir, APK_FILE + ".tmp"));
            File[] fs = dir.listFiles();
            if (fs != null) {
                for (File x : fs) {
                    if (x.isFile() && x.getName().toLowerCase(java.util.Locale.US).endsWith(".apk")
                            && !x.getName().equals(APK_FILE)) {
                        deleteQuietly(x);
                    }
                }
            }
        } catch (Exception ignored) { }
    }

    private void deleteQuietly(File f) {
        try { if (f != null && f.exists()) f.delete(); } catch (Exception ignored) { }
    }

    private String getVersionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "";
        }
    }

    private void installApk() {
        File f = new File(getFilesDir(), APK_FILE);
        if (!f.exists() || f.length() < 100 * 1024) {
            toast("安装包不存在或不完整，请重新检查更新");
            return;
        }
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (Build.VERSION.SDK_INT >= 24) {
            i.setDataAndType(Uri.parse("content://" + APK_AUTHORITY + "/" + APK_FILE), "application/vnd.android.package-archive");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } else {
            i.setDataAndType(Uri.fromFile(f), "application/vnd.android.package-archive");
        }
        try {
            startActivity(i);
        } catch (Exception e) {
            toast("打不开安装程序：" + e.getMessage());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        foreground = true;
        if (pendingDownload && Build.VERSION.SDK_INT >= 26
                && getPackageManager().canRequestPackageInstalls()) {
            // 刚授权完「安装未知应用」，回来接着下载，不用再点一次检查更新
            pendingDownload = false;
            startUpdate(pendingUrl, pendingTag);
            return;
        }
        if (pendingInstall) {
            pendingInstall = false;
            // 等这一帧布局稳定再拉安装器
            root.postDelayed(new Runnable() {
                public void run() { installApkWithRetry(0); }
            }, 300);
        }
        // 跨天回到前台（App 在后台挂着过了夜）也要把当天那批刷出来；
        // checkDailyForToday 自己按 daily_day_* 判重，同一天调多少次都只刷一次。
        if (root != null && !todayTag().equals(dailyCheckedTag)) {
            dailyCheckedTag = todayTag();
            root.post(new Runnable() {
                public void run() { checkDailyForToday(); }
            });
        }
        if (aiWebView != null) {
            try { aiWebView.resumeTimers(); aiWebView.onResume(); }
            catch (Throwable ignored) { }
        }
    }

    @Override
    protected void onPause() {
        foreground = false;
        if (aiWebView != null) {
            try { aiWebView.onPause(); aiWebView.pauseTimers(); }
            catch (Throwable ignored) { }
        }
        super.onPause();
    }

    /** 所有提示都走同一个气泡（见 showBubble），不再用系统 Toast，避免样式各行其是。 */
    private void toast(final String msg) {
        showBubble(msg);
    }

    /** Activity 是否还活着。后台线程回来碰 UI 前先查这个，销毁了就别动。 */
    private boolean alive() {
        return !isFinishing() && !isDestroyed();
    }

    // ================= 日志记录（默认关，手动开） =================

    /** 写一条日志。只有开启后才会落盘；平时零开销。 */
    private void log(String tag, String msg) {
        if (!logEnabled) return;
        try {
            if (logFile == null) {
                File dir = new File(getFilesDir(), "logs");
                if (!dir.exists()) dir.mkdirs();
                String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
                logFile = new File(dir, "app_" + ts + ".log");
            }
            String line = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(new Date())
                    + " [" + tag + "] " + msg + "\n";
            FileOutputStream fos = new FileOutputStream(logFile, true);
            fos.write(line.getBytes(StandardCharsets.UTF_8));
            fos.close();
        } catch (Exception ignored) { }
    }

    /** 开启日志记录：先落一条启动信息，方便定位版本与设备。 */
    private void startLogging() {
        logEnabled = true;
        prefs.edit().putBoolean("log_enabled", true).apply();
        log("APP", "开始记录日志 · 版本 " + appVersion() + "（build " + appVersionCode() + "）· "
                + Build.MODEL + " · Android " + Build.VERSION.RELEASE + " · " + (m.round ? "圆屏" : "方屏"));
    }

    /** 关闭日志记录。 */
    private void stopLogging() {
        if (logEnabled) log("APP", "停止记录日志");
        logEnabled = false;
        prefs.edit().putBoolean("log_enabled", false).apply();
    }

    /** 全局崩溃捕获：只在日志开启时写堆栈，其余情况交给系统默认处理。 */
    private void installCrashHandler() {
        final Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                // 崩溃堆栈无论如何都留一份在 crash.log（最多留最近 8KB）：
                // 日志开关是用户手动开的，崩溃时往往没开，事后就查不到原因了。
                try {
                    StringWriter sw = new StringWriter();
                    e.printStackTrace(new PrintWriter(sw));
                    String text = "== " + new java.text.SimpleDateFormat("MM-dd HH:mm:ss",
                            java.util.Locale.US).format(new Date()) + " 线程 " + t.getName()
                            + "\n" + sw.toString();
                    java.io.File f = new java.io.File(getFilesDir(), "crash.log");
                    if (f.exists() && f.length() > 8192) {
                        String old = readFileQuiet(f);
                        text = old.substring(Math.max(0, old.length() - 4096)) + "\n" + text;
                    }
                    java.io.FileOutputStream fos = new java.io.FileOutputStream(f, false);
                    fos.write(text.getBytes(StandardCharsets.UTF_8));
                    fos.close();
                } catch (Throwable ignored) { }
                if (logEnabled) {
                    try {
                        StringWriter sw = new StringWriter();
                        e.printStackTrace(new PrintWriter(sw));
                        log("CRASH", "线程 " + t.getName() + "\n" + sw.toString());
                    } catch (Exception ignored) { }
                }
                if (prev != null) prev.uncaughtException(t, e);
            }
        });
    }

    /** 读整个文件；读不到就当空串。 */
    private static String readFileQuiet(java.io.File f) {
        try {
            java.io.FileInputStream in = new java.io.FileInputStream(f);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] b = new byte[4096];
            int n;
            while ((n = in.read(b)) > 0) bos.write(b, 0, n);
            in.close();
            return new String(bos.toByteArray(), StandardCharsets.UTF_8);
        } catch (Throwable t) {
            return "";
        }
    }

    /** 读日志末尾最多 maxChars 个字符（给反馈附带用）。 */
    private String readLogTail(int maxChars) {
        if (logFile == null || !logFile.exists()) return "";
        try {
            long len = logFile.length();
            long start = Math.max(0, len - maxChars);
            RandomAccessFile raf = new RandomAccessFile(logFile, "r");
            raf.seek(start);
            byte[] buf = new byte[(int) (len - start)];
            raf.readFully(buf);
            raf.close();
            String out = new String(buf, StandardCharsets.UTF_8);
            // 起点可能落在半个中文字符上，跳过第一行之前的内容
            int nl = out.indexOf('\n');
            if (nl >= 0 && start > 0) out = out.substring(nl + 1);
            return out;
        } catch (Exception e) {
            return "";
        }
    }

    /** 3 秒倒计时确认框：倒计时结束后「开启」按钮才可点。 */
    private void showLogEnableDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14), dp(14), dp(14), dp(12));
        box.setBackgroundDrawable(pill(C_CARD));

        TextView title = new TextView(this);
        title.setText("开启记录日志");
        title.setTextSize(m.subtitleSize + 1f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(C_PRIMARY);
        title.setGravity(Gravity.CENTER);
        box.addView(title, wrap());

        TextView hint = new TextView(this);
        hint.setText("日志会保存在本机，用于排查问题（比如闪退）。倒计时结束后点「开启」开始记录。");
        hint.setTextSize(m.subtitleSize - 1.5f);
        hint.setTextColor(C_TEXT);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(dp(4), dp(8), dp(4), dp(8));
        box.addView(hint, wrap());

        final TextView btn = new TextView(this);
        btn.setTextSize(m.backSize);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setGravity(Gravity.CENTER);
        btn.setPadding(dp(10), dp(11), dp(10), dp(11));
        btn.setBackground(clickBg(0xFF14384A));
        btn.setTextColor(C_SUB);
        btn.setEnabled(false);
        box.addView(btn, wrap());

        TextView cancel = new TextView(this);
        cancel.setText("取消");
        cancel.setTextSize(m.subtitleSize - 1f);
        cancel.setTextColor(C_SUB);
        cancel.setGravity(Gravity.CENTER);
        cancel.setPadding(dp(8), dp(9), dp(8), dp(2));
        box.addView(cancel, wrap());

        final AlertDialog d = new AlertDialog.Builder(this).setView(box).create();
        d.setCancelable(true);
        tuneDialogWindow(d);
        d.show();

        cancel.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                vibrate();
                try { d.dismiss(); } catch (Exception ignored) { }
            }
        });
        btn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (!btn.isEnabled()) return;
                vibrate();
                try { d.dismiss(); } catch (Exception ignored) { }
                startLogging();
                toast("已开启记录日志");
                renderSettings();
            }
        });

        final int[] left = {3};
        final Handler h = new Handler(Looper.getMainLooper());
        final Runnable[] step = new Runnable[1];
        step[0] = new Runnable() {
            @Override
            public void run() {
                if (!d.isShowing()) return;
                if (left[0] > 0) {
                    btn.setText("请稍候（" + left[0] + " 秒）");
                    left[0]--;
                    h.postDelayed(step[0], 1000);
                } else {
                    btn.setText("开启");
                    btn.setTextColor(C_PRIMARY);
                    btn.setEnabled(true);
                }
            }
        };
        h.postDelayed(step[0], 1000);
    }

    // ================= UI =================
    private void buildUi() {
        getWindow().setBackgroundDrawable(new ColorDrawable(C_BG));
        java.util.Arrays.fill(screenBuilt, false);   // 新树里所有页面都要重新按需构建
        bubbleView = null;        // 旧 root 连同旧气泡一起被替换，这里只清引用
        root = new FrameLayout(this);
        root.setBackgroundColor(C_BG);
        if (bootHost != null) {
            bootHost.addView(root, 0, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        } else {
            setContentView(root);
        }

        subjectScreen = newScreen();
        nodeScreen = newScreen();
        detailScreen = newScreen();
        extraScreen = newScreen();
        settingsScreen = newScreen();
        searchScreen = newScreen();
        feedbackScreen = newScreen();

        root.addView(subjectScreen, fp());
        root.addView(nodeScreen, fp());
        root.addView(detailScreen, fp());
        root.addView(extraScreen, fp());
        root.addView(settingsScreen, fp());
        root.addView(searchScreen, fp());
        root.addView(feedbackScreen, fp());

        mineScreen = newScreen();
        mineList = new SvList();
        mineHeader = makeBackHeader("‹ 返回反馈", new View.OnClickListener() {
            public void onClick(View v) { goBack(S_FEEDBACK); }
        });
        mineList.addHead(mineHeader);
        mineScreen.addView(mineList.sc, lw());
        root.addView(mineScreen, fp());

        profileScreen = newScreen();
        root.addView(profileScreen, fp());

        changelogScreen = newScreen();
        root.addView(changelogScreen, fp());

        aboutScreen = newScreen();
        root.addView(aboutScreen, fp());

        displayScreen = newScreen();
        root.addView(displayScreen, fp());

        // 科目排序：从设置页点进来，单独一页放列表，设置页不再被 9 行排序挤长
        orderScreen = newScreen();
        root.addView(orderScreen, fp());

        // 单词四屏：内容都在首次进入时建（见 ensureScreenBuilt），启动时不碰
        wordHomeScreen = newScreen();
        root.addView(wordHomeScreen, fp());
        wordStudyScreen = newScreen();
        root.addView(wordStudyScreen, fp());
        wordSearchScreen = newScreen();
        root.addView(wordSearchScreen, fp());
        wordSettingsScreen = newScreen();
        root.addView(wordSettingsScreen, fp());

        wordVocabScreen = newScreen();
        root.addView(wordVocabScreen, fp());

        todayScreen = newScreen();
        root.addView(todayScreen, fp());

        // 收藏 / 最近浏览：都是 SvList，圆屏占位与过滚跟其他列表一致
        favScreen = newScreen();
        favList = new SvList();
        favList.addHead(makeBackHeader("‹ 返回", new View.OnClickListener() {
            public void onClick(View v) { goBack(S_SUBJECT); }
        }));
        favTitle = pageTitle("我的收藏");
        favList.addHead(favTitle);
        favList.addHead(grayTip("长按可取消收藏"));
        favScreen.addView(favList.sc, lw());
        root.addView(favScreen, fp());

        favSubScreen = newScreen();
        favSubList = new SvList();
        favSubList.addHead(makeBackHeader("‹ 返回我的收藏", new View.OnClickListener() {
            public void onClick(View v) { goBack(S_FAV); }
        }));
        favSubTitle = pageTitle("我的收藏");
        favSubList.addHead(favSubTitle);
        favSubList.addHead(grayTip("长按可取消这一条"));
        favSubScreen.addView(favSubList.sc, lw());
        root.addView(favSubScreen, fp());

        // 每日一句：句子来自接口，页面只负责「显示今天这一句 + 换一句 + 收藏」
        dailyScreen = newScreen();
        dailyList = new SvList();
        dailyList.addHead(makeBackHeader("‹ 返回", new View.OnClickListener() {
            public void onClick(View v) { handleBack(); }
        }));
        dailyTitle = pageTitle("每日一句");
        dailyList.addHead(dailyTitle);
        dailyHint = new TextView(this);
        dailyHint.setTextSize(m.subtitleSize - 2.5f);
        dailyHint.setTextColor(C_SUB);
        dailyHint.setGravity(Gravity.CENTER);
        dailyHint.setPadding(dp(4), 0, dp(4), dp(6));
        dailyHint.setText("每天第一次打开会自动换一句，也可以点下面的按钮换一批");
        dailyList.addHead(dailyHint);

        // 句子卡片（固定，不随翻页重建）
        dailyCardBox = new LinearLayout(this);
        dailyCardBox.setOrientation(LinearLayout.VERTICAL);
        dailyCardBox.setPadding(dp(12), dp(13), dp(12), dp(13));
        GradientDrawable dcb = new GradientDrawable();
        dcb.setColor(C_CARD);
        dcb.setCornerRadius(dp(12));
        dailyCardBox.setBackgroundDrawable(dcb);
        dailyMain = new TextView(this);
        dailyMain.setTextSize(fsSp() + 0.5f);
        dailyMain.setTextColor(C_TEXT);
        dailyMain.setLineSpacing(dp(4), 1.35f);
        dailyMain.setGravity(Gravity.CENTER);
        dailyCardBox.addView(dailyMain, wrap());
        dailyOrigin = new TextView(this);
        dailyOrigin.setTextSize(m.subtitleSize - 2f);
        dailyOrigin.setTextColor(C_SUB);
        dailyOrigin.setGravity(Gravity.CENTER);
        dailyOrigin.setPadding(0, dp(9), 0, 0);
        dailyCardBox.addView(dailyOrigin, wrap());
        dailyList.addHead(dailyCardBox);
        dailyCardBox.setVisibility(View.GONE);

        // 加载块（进度圈 + 文字 + 取消），默认隐藏
        dailyLoadingBox = new LinearLayout(this);
        dailyLoadingBox.setOrientation(LinearLayout.VERTICAL);
        dailyLoadingBox.setGravity(Gravity.CENTER);
        dailyLoadingBox.setPadding(dp(10), dp(22), dp(10), dp(16));
        dailyBar = new ProgressBar(this);
        dailyBar.setIndeterminate(true);
        try {
            dailyBar.setIndeterminateTintList(ColorStateList.valueOf(C_PRIMARY));
        } catch (Exception ignored) { }
        dailyLoadingBox.addView(dailyBar, new LinearLayout.LayoutParams(dp(30), dp(30)));
        TextView loadingTxt = new TextView(this);
        loadingTxt.setText("正在从网络拉取…");
        loadingTxt.setTextSize(fsSp());
        loadingTxt.setTextColor(C_SUB);
        loadingTxt.setGravity(Gravity.CENTER);
        loadingTxt.setPadding(0, dp(10), 0, 0);
        dailyLoadingBox.addView(loadingTxt, wrap());
        dailyLoadingBox.addView(dailyBtn("取消", new View.OnClickListener() {
            public void onClick(View v) { vibrate(); cancelDailyRefresh(); }
        }), wrap());
        dailyLoadingBox.setVisibility(View.GONE);
        dailyList.addHead(dailyLoadingBox);

        dailyBtns = new LinearLayout(this);
        dailyBtns.setOrientation(LinearLayout.HORIZONTAL);
        dailyBtns.setGravity(Gravity.CENTER_VERTICAL);
        dailyBtns.addView(dailyBtn("‹ 上一句", new View.OnClickListener() {
            public void onClick(View v) { stepDaily(-1); }
        }), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        dailyBtnFav = dailyBtn("☆", new View.OnClickListener() {
            public void onClick(View v) { toggleDailyFavCurrent(); }
        });
        dailyBtnFav.setTextSize(m.subtitleSize + 1.5f);   // 星要显眼，别的页面也是这颗星
        dailyBtns.addView(dailyBtnFav, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        dailyBtns.addView(dailyBtn("下一句 ›", new View.OnClickListener() {
            public void onClick(View v) { stepDaily(1); }
        }), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        dailyList.addHead(dailyBtns);

        dailyRefreshBtn = dailyBtn("换一批（从网络重新取）", new View.OnClickListener() {
            public void onClick(View v) { vibrate(); startDailyRefresh(); }
        });
        dailyRefreshBtn.setPadding(dp(6), dp(8), dp(6), dp(8));
        dailyList.addHead(dailyRefreshBtn);
        dailyScreen.addView(dailyList.sc, lw());
        root.addView(dailyScreen, fp());

        // 每日一句的收藏 / 最近浏览：都只列句子文本，点一下复制，长按取消收藏
        sentScreen = newScreen();
        sentList = new SvList();
        sentList.addHead(makeBackHeader("‹ 返回", new View.OnClickListener() {
            public void onClick(View v) { handleBack(); }
        }));
        sentTitle = pageTitle("每日一句");
        sentList.addHead(sentTitle);
        sentScreen.addView(sentList.sc, lw());
        root.addView(sentScreen, fp());

        histScreen = newScreen();
        histList = new SvList();
        histList.addHead(makeBackHeader("‹ 返回", new View.OnClickListener() {
            public void onClick(View v) { goBack(S_SUBJECT); }
        }));
        histTitle = pageTitle("最近浏览");
        histList.addHead(histTitle);
        TextView clearHist = dailyBtn("清除全部浏览记录", new View.OnClickListener() {
            public void onClick(View v) { confirmClearHist(); }
        });
        clearHist.setTextColor(C_SUB);
        histList.addHead(clearHist);
        histScreen.addView(histList.sc, lw());
        root.addView(histScreen, fp());

        // 问 AI：网页页 + 三个设置子页，都不读知识点数据
        aiWebScreen = newScreen();
        root.addView(aiWebScreen, fp());
        aiHubScreen = newScreen();
        root.addView(aiHubScreen, fp());
        aiApiScreen = newScreen();
        root.addView(aiApiScreen, fp());
        aiWebCfgScreen = newScreen();
        root.addView(aiWebCfgScreen, fp());
        aiChatScreen = newScreen();
        root.addView(aiChatScreen, fp());

        // 因为缺 WebView 自动切过模式：界面铺好之后提示一句，别在启动瞬间被开屏盖住
        if (aiSwitchedByNoWebView) {
            aiSwitchedByNoWebView = false;
            root.postDelayed(new Runnable() {
                public void run() {
                    if (alive()) toast("本机没有 WebView，已自动改用 API 配置模式");
                }
            }, 1200);
        }

        // 主页
        subjectList = new SvList();
        subjectScreen.addView(subjectList.sc, lw());

        // 通用节点列表页：返回键按栈回退、面包屑、节内搜索，都作为随列表滚动的 header
        listTitle = new TextView(this);
        listTitle.setTextSize(m.subtitleSize);
        listTitle.setTypeface(Typeface.DEFAULT_BOLD);
        listTitle.setTextColor(C_PRIMARY);
        listTitle.setGravity(Gravity.CENTER);
        listTitle.setPadding(0, 0, 0, dp(4));
        listTitle.setMaxLines(3);

        itemSearchBox = new EditText(this);
        itemSearchBox.setHint("在本级搜索…");
        itemSearchBox.setTextSize(m.subtitleSize - 1f);
        itemSearchBox.setTextColor(Color.WHITE);
        itemSearchBox.setHintTextColor(C_SUB);
        itemSearchBox.setSingleLine(true);
        itemSearchBox.setBackgroundDrawable(pill(C_PILL));
        itemSearchBox.setPadding(dp(12), dp(8), dp(12), dp(8));
        itemSearchBox.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            public void onTextChanged(CharSequence s, int a, int b, int c) { }
            public void afterTextChanged(Editable s) { refreshNodes(); }
        });

        nodeList = new SvList();
        nodeList.addHead(makeBackHeader("‹ 返回上一页", new View.OnClickListener() {
            public void onClick(View v) { handleBack(); }
        }));
        nodeList.addHead(listTitle);
        nodeList.addHead(itemSearchBox);
        // 英语科目专属：背单词 / 搜单词（其他科目隐藏，见 renderNodes）
        englishEntryBar = buildEnglishEntryBar();
        englishEntryBar.setVisibility(View.GONE);
        nodeList.addHead(englishEntryBar);
        nodeScreen.addView(nodeList.sc, lw());

        // 详情页
        detailBackBtn = makeBack("‹ 返回", new View.OnClickListener() {
            public void onClick(View v) { goBack(detailBack); }
        });
        detailPath = new TextView(this);
        detailPath.setTextSize(m.subtitleSize);          // 与知识点页的面包屑一个规格
        detailPath.setTypeface(Typeface.DEFAULT_BOLD);
        detailPath.setTextColor(C_SUB);
        detailPath.setPadding(dp(2), 0, dp(2), dp(1));
        detailPath.setMaxLines(2);

        detailTitle = new TextView(this);
        detailTitle.setTextSize(m.titleSize);
        detailTitle.setTypeface(Typeface.DEFAULT_BOLD);
        detailTitle.setTextColor(C_PRIMARY);
        detailTitle.setGravity(Gravity.CENTER);
        detailTitle.setPadding(0, dp(2), 0, dp(6));
        detailTitle.setMaxLines(3);

        detailScroll = new OverScrollScrollView(this);
        detailScroll.setFillViewport(true);
        roundPadScroll(detailScroll);
        detailInner = new LinearLayout(this);
        detailInner.setOrientation(LinearLayout.VERTICAL);
        detailScroll.addView(detailInner, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        detailScreen.addView(detailScroll, lw());

        // 例题页
        extraBackBtn = makeBack("‹ 返回知识点", new View.OnClickListener() {
            public void onClick(View v) { goBack(S_DETAIL); }
        });
        extraPath = new TextView(this);
        extraPath.setTextSize(m.subtitleSize);        // 与知识点页的面包屑一个规格
        extraPath.setTypeface(Typeface.DEFAULT_BOLD);
        extraPath.setTextColor(C_SUB);
        extraPath.setPadding(dp(2), 0, dp(2), dp(1));
        extraPath.setMaxLines(2);
        // extraPath 随内容滚动，在 openExtra 里加进 extraInner

        extraTitle = new TextView(this);
        extraTitle.setTextSize(m.titleSize);
        extraTitle.setTypeface(Typeface.DEFAULT_BOLD);
        extraTitle.setTextColor(C_PRIMARY);
        extraTitle.setGravity(Gravity.CENTER);
        extraTitle.setPadding(0, dp(2), 0, dp(6));
        extraTitle.setMaxLines(3);
        // extraTitle 随内容滚动，在 openExtra 里加进 extraInner

        // 页面副标题：内容由数据给（例题 / 译文…），界面只负责显示
        extraKind = new TextView(this);
        extraKind.setTextSize(m.subtitleSize - 1f);
        extraKind.setTypeface(Typeface.DEFAULT_BOLD);
        extraKind.setTextColor(C_HI);
        extraKind.setGravity(Gravity.CENTER);
        extraKind.setPadding(0, 0, 0, dp(8));

        extraScroll = new OverScrollScrollView(this);
        extraScroll.setFillViewport(true);
        roundPadScroll(extraScroll);
        extraInner = new LinearLayout(this);
        extraInner.setOrientation(LinearLayout.VERTICAL);
        extraScroll.addView(extraInner, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        extraScreen.addView(extraScroll, lw());

        // 设置页
        settingsBackBtn = makeBack("‹ 返回", new View.OnClickListener() {
            public void onClick(View v) { goBack(S_SUBJECT); }
        });
        settingsScroll = new OverScrollScrollView(this);
        settingsScroll.setFillViewport(true);
        roundPadScroll(settingsScroll);
        settingsInner = new LinearLayout(this);
        settingsInner.setOrientation(LinearLayout.VERTICAL);
        settingsScroll.addView(settingsInner, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        settingsScreen.addView(settingsScroll, lw());

        // 全局搜索页
        // 全局搜索页
        searchTitle = new TextView(this);
        searchTitle.setText("搜索知识点与分组");
        searchTitle.setTextSize(m.subtitleSize + 2f);
        searchTitle.setTypeface(Typeface.DEFAULT_BOLD);
        searchTitle.setTextColor(C_PRIMARY);
        searchTitle.setGravity(Gravity.CENTER);
        searchTitle.setPadding(0, 0, 0, dp(3));
        gSearchBox = new EditText(this);
        gSearchBox.setHint("输入关键词（如 导数、电场、光合作用）…");
        gSearchBox.setTextSize(m.subtitleSize - 1f);
        gSearchBox.setTextColor(Color.WHITE);
        gSearchBox.setHintTextColor(C_SUB);
        gSearchBox.setSingleLine(true);
        gSearchBox.setBackgroundDrawable(pill(C_PILL));
        gSearchBox.setPadding(dp(12), dp(8), dp(12), dp(8));
        LinearLayout searchBar = new LinearLayout(this);
        searchBar.setOrientation(LinearLayout.HORIZONTAL);
        searchBar.setGravity(Gravity.CENTER_VERTICAL);
        searchBar.addView(gSearchBox, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView clearBtn = new TextView(this);
        clearBtn.setText("✕");
        clearBtn.setTextSize(m.subtitleSize);
        clearBtn.setTypeface(Typeface.DEFAULT_BOLD);
        clearBtn.setTextColor(C_SUB);
        clearBtn.setGravity(Gravity.CENTER);
        clearBtn.setBackground(clickBg(C_PILL));
        LinearLayout.LayoutParams clrp = new LinearLayout.LayoutParams(dp(40), dp(40));
        clrp.leftMargin = dp(6);
        clearBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                vibrate();
                gSearchBox.setText("");
            }
        });
        searchBar.addView(clearBtn, clrp);

        // 搜索框行：输入框 + 清空按钮
        gSearchBox.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            public void onTextChanged(CharSequence s, int a, int b, int c) { }
            public void afterTextChanged(Editable s) { refreshGlobalSearch(); }
        });
        searchList = new SvList();
        searchList.addHead(makeBackHeader("‹ 返回", new View.OnClickListener() {
            public void onClick(View v) { goBack(S_SUBJECT); }
        }));
        searchList.addHead(searchTitle);
        searchList.addHead(searchBar);
        searchScreen.addView(searchList.sc, lw());
    }

    // ================= 问 AI（DeepSeek 网页版） =================
    // 网页是 DeepSeek 的，代码不在我们手里：这一层只做「去掉下载 APP 入口」一件事。
    // 输入框、按钮的尺寸一律保持网页默认，不改比例；登录态由网页自己的 Cookie 保持，
    // App 不存密钥、不写日志。SPA 会重绘，所以脚本要能反复跑（延时重试 + MutationObserver）。
    //
    // 【黑屏根因 / 别再改回去】老脚本命中下载入口后会「向上爬最多 3 层父元素」再
    // display:none，条件是父元素文字 ≤40 字。DeepSeek 首屏/登录页的外层容器文字很短
    // 甚至为空，于是一路爬到 #root / body，把整个页面根节点隐藏 → 只剩 App 纯黑底，
    // 表现就是「闪一下网页然后全黑」；MutationObserver 又会在每次重绘时重跑，恢复不了。
    // 现在：只隐藏命中的小叶子元素自身，明确排除根节点，加保险丝和根节点自检，
    // 任何异常都不可能把整页弄没。
    private static final String AI_WEB_JS = "(function(){"
            + "if(window.__hzWatch){try{window.__hzWatch.run();}catch(e){}return;}"
            + "var re=/下载\\s*(APP|App|app|客户端|应用)|获取\\s*(APP|App)|APP\\s*下载|手机\\s*(APP|客户端)|Download\\s*(the\\s*)?App|Get\\s*the\\s*app|App\\s*Store/i;"
            + "var MAX=12,hid=0;"
            + "function txt(el){return ((el.innerText||el.textContent||'')+'').trim();}"
            + "function key(el){var c=(typeof el.className==='string')?el.className:'';return c+' '+(el.id||'');}"
            + "function isRoot(el){"
            + "if(!el||el===document.documentElement||el===document.body)return true;"
            + "var t=(el.tagName||'').toLowerCase();"
            + "if(t==='html'||t==='body'||t==='main')return true;"
            + "var id=(el.id||'').toLowerCase();"
            + "if(id==='root'||id==='app'||id==='__next')return true;"
            + "return false;"
            + "}"
            + "function isDl(el){"
            + "var t=txt(el);"
            + "if(t.length>30)return false;"
            + "if(t&&re.test(t))return true;"
            + "var h=(el.getAttribute&&el.getAttribute('href'))||'';"
            + "if(/\\.apk($|\\?)|play\\.google\\.com|apps\\.apple\\.com|\\/download\\/app|download|app-store|google-play|getapp/i.test(h))return true;"
            + "if(/download|app-store|google-play|getapp/i.test(key(el)))return true;"
            + "return false;"
            + "}"
            + "function small(el){"
            + "if(isRoot(el))return false;"
            + "if(el.children&&el.children.length>3)return false;"
            + "var r=el.getBoundingClientRect&&el.getBoundingClientRect();"
            + "if(!r)return false;"
            + "if(r.width>window.innerWidth*0.95||r.height>window.innerHeight*0.18)return false;"
            + "return true;"
            + "}"
            + "function guard(){"
            + "try{var rs=[document.body,document.getElementById('root'),document.getElementById('app')];"
            + "for(var i=0;i<rs.length;i++){var r=rs[i];if(r&&r.style&&r.style.display==='none')r.style.removeProperty('display');}"
            + "}catch(e){}"
            + "}"
            + "function run(){"
            + "try{guard();if(hid>=MAX)return;"
            + "var all=document.querySelectorAll('a,button,[role=\"button\"],div,span,li,[class*=\"download\"],[id*=\"download\"]');"
            + "for(var i=0;i<all.length;i++){"
            + "if(hid>=MAX)break;"
            + "var el=all[i];"
            + "if(el.getAttribute&&el.getAttribute('data-hz-h')==='1')continue;"
            + "if(!isDl(el)||!small(el))continue;"
            + "el.setAttribute('data-hz-h','1');"
            + "el.style.setProperty('display','none','important');"
            + "hid++;"
            + "}"
            + "}catch(e){}"
            + "}"
            + "window.__hzWatch={run:run};run();"
            + "var timer=null;"
            + "try{var mo=new MutationObserver(function(){if(timer)clearTimeout(timer);timer=setTimeout(run,420);});"
            + "mo.observe(document.documentElement||document.body,{childList:true,subtree:true});}catch(e){}"
            + "})()";

    /** 圆屏上点得准的按钮：沿用设置页的 clickBg。现在只给错误层的「重试」用。 */
    private TextView aiBarBtn(String text, final View.OnClickListener onClick) {
        TextView b = new TextView(this);
        b.setText(text);
        b.setTextSize(m.backSize - 1f);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(C_PRIMARY);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(6), dp(7), dp(6), dp(7));
        b.setMinHeight(dp(34));
        b.setBackground(clickBg(C_PILL));
        b.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                vibrate();
                if (onClick != null) onClick.onClick(v);
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.leftMargin = dp(2);
        lp.rightMargin = dp(2);
        b.setLayoutParams(lp);
        return b;
    }

    private void buildAiWebScreen() {
        // 调字号/缩放会就地重建整棵 View 树：旧网页先销毁，别留一个没人引用的 WebView。
        if (aiWebView != null) {
            try { aiWebView.destroy(); } catch (Throwable ignored) { }
            aiWebView = null;
        }
        cancelAiGestureTasks();
        aiWebLoaded = false;
        aiWebScreen.removeAllViews();
        // 列表页的左右缩进不能带到网页页：WebView 是按可用宽度缩放的，容器比它窄就会
        // 把右边裁掉一截、输入框被截断。这里整屏给网页，安全边距统一由 aiWebHost 承担。
        aiWebScreen.setPadding(0, 0, 0, 0);

        // 圆屏四角是物理裁掉的：裁切版用既有的 roundSafe（短边×0.146）给容器四边留安全区，
        // 网页、错误层、进度条、手势提示一起内缩；切到原版时安全区清零，网页铺满整屏。
        // 方屏 roundSafe=0，两种模式一样。
        aiWebHost = new FrameLayout(this);
        aiWebHost.setBackgroundColor(C_BG);
        int safe = aiWebSafePad();
        aiWebHost.setPadding(safe, safe, safe, safe);
        aiWebScreen.addView(aiWebHost,
                new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        aiWebErrorBox = new LinearLayout(this);
        aiWebErrorBox.setOrientation(LinearLayout.VERTICAL);
        aiWebErrorBox.setGravity(Gravity.CENTER);
        aiWebErrorBox.setBackgroundColor(C_BG);
        aiWebErrorBox.setPadding(dp(14), dp(12), dp(14), dp(12));
        aiWebErrorTxt = new TextView(this);
        aiWebErrorTxt.setTextSize(m.subtitleSize - 1f);
        aiWebErrorTxt.setTextColor(C_TEXT);
        aiWebErrorTxt.setGravity(Gravity.CENTER);
        aiWebErrorTxt.setLineSpacing(dp(3), 1.3f);
        aiWebErrorBox.addView(aiWebErrorTxt, wrap());
        aiWebErrorBtn = aiBarBtn("重试", new View.OnClickListener() {
            public void onClick(View v) { aiWebHideError(); aiWebLoad(); }
        });
        LinearLayout.LayoutParams ebLp = wrap();
        ebLp.topMargin = dp(10);
        aiWebErrorBox.addView(aiWebErrorBtn, ebLp);
        aiWebErrorBox.setVisibility(View.GONE);

        aiWebProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        aiWebProgress.setMax(100);
        aiWebProgress.setVisibility(View.GONE);
        try { aiWebProgress.setProgressTintList(ColorStateList.valueOf(C_PRIMARY)); }
        catch (Throwable ignored) { }

        aiGestureHint = new TextView(this);
        aiGestureHint.setText("双指轻点两下：裁切版 / 原版\n双指长按 2 秒：退出");
        aiGestureHint.setTextSize(m.subtitleSize - 1f);
        aiGestureHint.setTextColor(0xEEFFFFFF);
        aiGestureHint.setGravity(Gravity.CENTER);
        aiGestureHint.setBackgroundColor(0xDD101010);
        aiGestureHint.setPadding(dp(12), dp(9), dp(12), dp(9));
        aiGestureHint.setLineSpacing(dp(2), 1.25f);
        aiGestureHint.setVisibility(View.GONE);

        try {
            // 只给 WebView 换一个「夜间」Context：网页里 prefers-color-scheme: dark 就成立，
            // 网站的「跟随系统」会自动切暗色。不动 Activity 本身的主题，其它页面不受影响，
            // 也不需要往网页注入任何脚本（之前注入脚本藏到根节点导致过黑屏）。
            Context webCtx = this;
            if (aiWebDark && Build.VERSION.SDK_INT >= 23) {
                try {
                    // 用包在 Activity 上的 ContextThemeWrapper 覆盖 uiMode，而不是裸的
                    // createConfigurationContext：后者不带 Activity 身份，少数厂商 WebView
                    // 弹键盘 / 取窗口 token 时会出问题。这样只改配置、不改身份。
                    android.view.ContextThemeWrapper cw =
                            new android.view.ContextThemeWrapper(this, getTheme());
                    Configuration cfg = new Configuration(getResources().getConfiguration());
                    cfg.uiMode = (cfg.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                            | Configuration.UI_MODE_NIGHT_YES;
                    cw.applyOverrideConfiguration(cfg);
                    webCtx = cw;
                } catch (Throwable ignored) { webCtx = this; }
            }
            WebView wv;
            try {
                wv = new AiWebView(webCtx);
            } catch (Throwable ctorFail) {
                // 极少数厂商 WebView 不接受 override Context：退回原生 Context 再建一次。
                // 宁可暗色不生效，也绝不能因为这个功能让网页打不开。
                if (webCtx != this) wv = new AiWebView(this);
                else throw ctorFail;
            }
            aiWebView = wv;
            // 顺序就是层级：网页最底 → 错误层 → 进度条 → 手势提示。一次排好，
            // 运行时不再 bringToFront（加载期间反复 requestLayout 会打断网页首次绘制）。
            aiWebHost.addView(wv, aiWebZoomLayoutParams(wv));
            aiWebHost.addView(aiWebErrorBox, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            aiWebHost.addView(aiWebProgress, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, dp(3), Gravity.TOP));
            aiWebHost.addView(aiGestureHint, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER));
            configureAiWebView(wv);
        } catch (Throwable t) {
            // 系统没装 WebView 组件时，new WebView 会直接抛异常
            aiWebView = null;
            if (aiWebErrorBox.getParent() == null) {
                aiWebHost.addView(aiWebErrorBox, new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            }
            if (aiWebProgress.getParent() == null) {
                aiWebHost.addView(aiWebProgress, new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT, dp(3), Gravity.TOP));
            }
            if (aiGestureHint.getParent() == null) {
                aiWebHost.addView(aiGestureHint, new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                        Gravity.CENTER));
            }
            aiWebShowError("这台设备没有 WebView 组件，无法使用 DeepSeek 网页版。\n"
                    + "一般手表都自带；如果没有，需要装上系统的 WebView。", false);
        }
    }

    // ---- 双指手势：轻点两下切裁切版/原版，长按 2 秒退出（只用于圆屏手表） ----
    /** 双指手势只在圆屏手表上启用：裁切版是为了避开圆屏四角被物理裁掉的区域，
     *  手机方屏没有这回事，退出用系统返回键即可，不需要长按。 */
    private boolean aiTwoGestureEnabled() {
        return m != null && m.round;
    }

    /** 进页面时把手势说明显示一会儿再淡出：第一次进来要看清楚，之后每次只闪 1 秒。
     *  设置里另有一份常驻说明。方屏（手机）没有双指手势，不弹这个浮层。 */
    private void showAiGestureHint() {
        if (aiGestureHint == null || !aiTwoGestureEnabled()) return;
        boolean first = !prefs.getBoolean("ai_hint_seen", false);
        if (first) prefs.edit().putBoolean("ai_hint_seen", true).apply();
        if (aiGestureHideTask != null) aiGestureHandler.removeCallbacks(aiGestureHideTask);
        aiGestureHideTask = null;
        aiGestureHint.animate().cancel();
        aiGestureHint.setAlpha(1f);
        aiGestureHint.setVisibility(View.VISIBLE);
        aiGestureHideTask = new Runnable() {
            public void run() {
                aiGestureHideTask = null;
                if (aiGestureHint == null) return;
                aiGestureHint.animate().alpha(0f).setDuration(400)
                        .withEndAction(new Runnable() {
                            public void run() {
                                if (aiGestureHint != null) aiGestureHint.setVisibility(View.GONE);
                            }
                        }).start();
            }
        };
        aiGestureHandler.postDelayed(aiGestureHideTask,
                first ? AI_HINT_FIRST_MS : AI_HINT_LATER_MS);
    }

    private void hideAiGestureHint() {
        if (aiGestureHideTask != null) {
            aiGestureHandler.removeCallbacks(aiGestureHideTask);
            aiGestureHideTask = null;
        }
        if (aiGestureHint != null) {
            aiGestureHint.animate().cancel();
            aiGestureHint.setVisibility(View.GONE);
        }
    }

    private void cancelAiGestureTasks() {
        if (aiGestureHideTask != null) aiGestureHandler.removeCallbacks(aiGestureHideTask);
        if (aiGestureLongTask != null) aiGestureHandler.removeCallbacks(aiGestureLongTask);
        aiGestureHideTask = null;
        aiGestureLongTask = null;
        aiTwoActive = false;
        aiLastTwoTapAt = 0L;
    }

    /** 切换裁切版 / 原版。只改安全区，不动缩放档位——两者是独立的。 */
    private void toggleAiWebCrop() {
        aiWebCrop = !aiWebCrop;
        prefs.edit().putBoolean("ai_web_crop", aiWebCrop).apply();
        applyAiWebCrop();
        toast(aiWebCrop ? "裁切版：四边避开圆屏" : "原版：网页铺满屏幕");
    }

    private void applyAiWebCrop() {
        if (aiWebHost == null) return;
        int safe = aiWebSafePad();
        aiWebHost.setPadding(safe, safe, safe, safe);
        applyAiWebZoom();          // 可用宽高变了，缩放与网页尺寸一起重算
        aiWebHost.invalidate();
    }

    /** 双指序列从第二指按下开始接管，直到最后一指抬起；期间事件不传给网页，
     *  既避免误触网页按钮，也避开 WebView 自带的双指缩放。返回 true 表示已消费。 */
    private boolean handleAiTwoFinger(MotionEvent ev) {
        int action = ev.getActionMasked();
        int n = ev.getPointerCount();
        if (!aiTwoActive) {
            if (n >= 2 && (action == MotionEvent.ACTION_POINTER_DOWN
                    || action == MotionEvent.ACTION_DOWN)) {
                aiTwoActive = true;
                aiTwoDownAt = System.currentTimeMillis();
                aiTwoLongFired = false;
                aiTwoMoved = false;
                aiTwoStartX = ev.getX();
                aiTwoStartY = ev.getY();
                // 单指按下时右滑跟踪已经开过头，双指接管后归位，别留下半截拖动
                swipeTracking = false;
                if (swipeActive) resetSwipe();
                // 第一指按下时网页已经收到 ACTION_DOWN，接管后必须补一个 CANCEL，
                // 否则网页还等着自己的长按计时到点，弹出「复制 / 全选」气泡——双指长按误触就是它。
                cancelChildGesture();
                scheduleAiTwoLong();
                return true;
            }
            return false;
        }
        switch (action) {
            case MotionEvent.ACTION_MOVE:
                if (Math.abs(ev.getX() - aiTwoStartX) > dp(22)
                        || Math.abs(ev.getY() - aiTwoStartY) > dp(22)) {
                    aiTwoMoved = true;
                    cancelAiTwoLong();
                }
                break;
            case MotionEvent.ACTION_POINTER_UP:
                cancelAiTwoLong();   // 已不是两指，长按不成立
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                cancelAiTwoLong();
                long now = System.currentTimeMillis();
                boolean quick = !aiTwoLongFired && !aiTwoMoved
                        && (now - aiTwoDownAt) < 900L;
                if (action == MotionEvent.ACTION_UP && quick) {
                    if (aiLastTwoTapAt > 0L && now - aiLastTwoTapAt <= AI_TWO_TAP_GAP_MS) {
                        aiLastTwoTapAt = 0L;
                        toggleAiWebCrop();
                    } else {
                        aiLastTwoTapAt = now;
                    }
                } else {
                    aiLastTwoTapAt = 0L;
                }
                aiTwoActive = false;
                break;
        }
        return true;
    }

    private void scheduleAiTwoLong() {
        cancelAiTwoLong();
        aiGestureLongTask = new Runnable() {
            public void run() {
                aiGestureLongTask = null;
                if (!aiTwoActive || aiTwoMoved || aiTwoLongFired) return;
                aiTwoLongFired = true;
                aiLastTwoTapAt = 0L;
                toast("已退出问 AI");
                goBack(aiWebBack);
            }
        };
        aiGestureHandler.postDelayed(aiGestureLongTask, AI_TWO_LONG_MS);
    }

    private void cancelAiTwoLong() {
        if (aiGestureLongTask != null) {
            aiGestureHandler.removeCallbacks(aiGestureLongTask);
            aiGestureLongTask = null;
        }
    }

    // ---- 网页显示缩放（纯 View 变换，零注入） ----
    // 网页自己写死了 viewport「width=device-width, initial-scale=1, maximum-scale=1,
    // minimum-scale=1, user-scalable=no」：系统双指缩放和 setInitialScale 都会被它覆盖，
    // 而手表圆屏的 CSS 宽度往往只有 200dp 出头，网页按这个窄视口排版 → UI 挤成一团，
    // 登录框 / 人机验证弹窗 / 侧边栏都超出屏幕，左右被裁、按钮点不到。
    // 做法：把 WebView 控件按更宽的尺寸排版（网页以为屏幕有 320dp），再用 Android 的
    // View 缩放整体缩回手表屏幕。触摸坐标由系统自动逆变换，网页侧不用改、也不用注入脚本。
    // 尺寸一律基于「安全区内的可用宽高」，否则缩放后的网页比容器宽，右边又会被裁。
    /** 圆屏四角安全边距：复用既有的 Metrics.roundSafe（短边×0.146），再加 2dp 余量；方屏为 0。 */
    private int aiWebSafePad() {
        if (!aiWebCrop || m == null || !m.round) return 0;   // 原版 / 方屏：不加安全区
        return m.roundSafe + dp(2);
    }

    private String aiWebZoomLabel() {
        return aiWebZoomMode == 1 ? "更小" : (aiWebZoomMode == 2 ? "原样" : "自动");
    }

    private float aiWebZoomFactor() {
        if (aiWebZoomMode == 2) return 1f;
        float dens = getResources().getDisplayMetrics().density;
        if (dens <= 0f) dens = 1f;
        int availW = getResources().getDisplayMetrics().widthPixels - 2 * aiWebSafePad();
        float k = Math.min(1f, (availW / dens) / AI_WEB_TARGET_CSS_W);
        if (k < AI_WEB_MIN_SCALE) k = AI_WEB_MIN_SCALE;
        if (aiWebZoomMode == 1) k = Math.max(0.35f, k * 0.85f);
        return k;
    }

    private FrameLayout.LayoutParams aiWebZoomLayoutParams(WebView wv) {
        float k = aiWebZoomFactor();
        wv.setPivotX(0f);
        wv.setPivotY(0f);
        wv.setScaleX(k);
        wv.setScaleY(k);
        if (k >= 1f) return new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        int safe = aiWebSafePad();
        int availW = getResources().getDisplayMetrics().widthPixels - 2 * safe;
        int availH = getResources().getDisplayMetrics().heightPixels - 2 * safe;
        return new FrameLayout.LayoutParams((int) (availW / k), (int) (availH / k));
    }

    /** 切档位时就地更新，不重载网页、不打断登录和正在进行的对话。 */
    private void applyAiWebZoom() {
        if (aiWebView == null) return;
        aiWebView.setLayoutParams(aiWebZoomLayoutParams(aiWebView));
        aiWebView.invalidate();
        if (aiWebHost != null) aiWebHost.invalidate();
    }

    /** 问 AI 专供的 WebView：不给网页弹系统的「复制 / 全选 / 自动填充」那一套。
     *  双指轻点两下、双指长按都要求网页别自己抢长按和双击——网页弹出选择手柄和气泡菜单，
     *  既挡住手势，也会把登录框里的输入搅乱。全部走 Android 层关掉，不注入脚本、不碰网页 DOM。 */
    private static class AiWebView extends WebView {
        AiWebView(Context c) { super(c); }

        // 长按不选词：performLongClick 直接吃掉，Chromium 就不会开始选择
        @Override
        public boolean performLongClick() {
            return true;
        }

        // 双指/双击选词后 Chromium 会向宿主申请 ActionMode（那条复制工具条）。
        // 返回 null 等于「不给」，选择手柄和复制/全选菜单都不会出现。
        @Override
        public ActionMode startActionMode(ActionMode.Callback cb) {
            return null;
        }

        @Override
        public ActionMode startActionMode(ActionMode.Callback cb, int type) {
            return null;
        }
    }

    private void configureAiWebView(WebView wv) {
        WebSettings s = wv.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setUserAgentString(AI_WEB_UA);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        // 保持 false：布局宽度就等于 WebView 控件宽度，配合上面的 View 层缩放，
        // 让网页按较宽视口排版、整体缩回屏幕。改成 true 会再叠一层 WebView 内部缩放，
        // 又会把左右裁掉（网页的 viewport 是写死的，改不动）。
        s.setUseWideViewPort(false);
        s.setLoadWithOverviewMode(false);
        // 双指手势归 App 全局处理（轻点两下切版、长按退出），关掉 WebView 自带缩放，
        // 免得和手势抢事件；网页本身也写了 user-scalable=no，缩放本来就没用。
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setGeolocationEnabled(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        s.setMediaPlaybackRequiresUserGesture(true);
        // 暗色：夜间 Context 已经让 prefers-color-scheme: dark 成立，网站自己的暗色样式会生效。
        // 系统「算法反色」必须关掉，否则会在网站暗色之上再反一次，图片和品牌色糊成一团。
        // API 33+ 用 setAlgorithmicDarkeningAllowed(false)；29~32 用 FORCE_DARK_OFF（同样不反色）。
        // 全程 try/catch：不同厂商 WebView 实现有差异，这一项失败也不该让网页打不开。
        if (aiWebDark) {
            try {
                if (Build.VERSION.SDK_INT >= 33) {
                    s.setAlgorithmicDarkeningAllowed(false);
                } else if (Build.VERSION.SDK_INT >= 29) {
                    s.setForceDark(WebSettings.FORCE_DARK_OFF);
                }
            } catch (Throwable ignored) { }
        }
        wv.setBackgroundColor(C_BG);
        wv.setHorizontalScrollBarEnabled(false);
        wv.setVerticalScrollBarEnabled(false);
        wv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        // 关掉网页的系统级「复制 / 全选 / 自动填充」：双指轻点两下和双指长按是这一屏唯一的手势，
        // 网页自己再弹选择框就会打架，输入密码时冒出的自动填充气泡也会挡住键盘。
        // ① 表单数据与密码一律不存、不提示保存；② 整棵网页声明不参与自动填充；
        // ③ 关长按、去掉触感反馈；④ AiWebView 拒绝一切 ActionMode（复制/全选工具条）。
        s.setSaveFormData(false);
        s.setSavePassword(false);
        wv.setLongClickable(false);
        wv.setHapticFeedbackEnabled(false);
        wv.setOnLongClickListener(new View.OnLongClickListener() {
            public boolean onLongClick(View v) { return true; }   // 吃掉，不往下传
        });
        if (Build.VERSION.SDK_INT >= 26) {
            wv.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        }
        // 网页页没有常驻工具条，滚动不再驱动任何浮层收展。

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(wv, true);

        wv.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest req) {
                String u = req.getUrl().toString();
                if (u.startsWith("https://chat.deepseek.com")
                        || u.startsWith("https://deepseek.com")
                        || u.startsWith("https://www.deepseek.com")) return false;
                if (isAppDownloadUrl(u)) {
                    toast("已拦下下载 APP 的入口");
                    return true;
                }
                try {
                    Intent it = new Intent(Intent.ACTION_VIEW, Uri.parse(u));
                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(it);
                } catch (Exception e) {
                    toast("这个链接在手表上打不开");
                }
                return true;
            }

            @Override
            public void onPageStarted(WebView v, String url, Bitmap favicon) {
                aiWebProgress.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView v, String url) {
                aiWebProgress.setVisibility(View.GONE);
                v.invalidate();                     // 兜底：网页首帧没贴合时主动要一次重绘
                if (aiWebHost != null) aiWebHost.invalidate();
                aiWebInject(v);
                // SPA 首屏可能比 onPageFinished 还晚，补两次即可；脚本自己也带观察器，
                // 次数太多会反复遍历整棵 DOM，在手表上拖慢渲染。
                final WebView wv2 = v;
                for (long d : new long[]{600L, 2000L}) {
                    aiWebHost.postDelayed(new Runnable() {
                        public void run() {
                            if (alive() && aiWebView == wv2) aiWebInject(wv2);
                        }
                    }, d);
                }
            }

            @Override
            public void onReceivedError(WebView v, WebResourceRequest req, WebResourceError err) {
                if (!req.isForMainFrame()) return;
                aiWebShowError("网页没加载出来。可能是网络不通，也可能网页拒绝了手表访问。\n"
                        + "点「重试」再试一次；一直不行就换个网络。", true);
            }

            @Override
            public void onReceivedHttpError(WebView v, WebResourceRequest req,
                                            WebResourceResponse resp) {
                if (!req.isForMainFrame()) return;
                int code = resp.getStatusCode();
                if (code == 429 || code == 403) {
                    aiWebShowError("网页暂时拒绝了这次访问（" + code + "）。\n"
                            + "等一会儿点「重试」，或换个网络再试。", true);
                } else if (code >= 400) {
                    aiWebShowError("网页返回了错误（" + code + "）。点「重试」再试一次。", true);
                }
            }
        });

        wv.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView v, int p) {
                aiWebProgress.setProgress(p);
                if (p >= 100) aiWebProgress.setVisibility(View.GONE);
            }
        });
    }

    /** 下载 APP 的链接直接拦掉，不让手表跳到应用商店去。 */
    private boolean isAppDownloadUrl(String u) {
        String s = u.toLowerCase(Locale.ROOT);
        if (s.contains(".apk") || s.contains("play.google.com") || s.contains("apps.apple.com"))
            return true;
        return s.contains("download") && s.contains("app");
    }

    /** 只注入优化脚本，不碰网页自己的业务逻辑；SPA 每次重绘都会重跑一遍。
     *  开关关掉时一个字都不注入：这是「网页是否原样」的对照手段，也是兜底。 */
    private void aiWebInject(WebView v) {
        if (!aiWebHideDl) return;
        try { v.evaluateJavascript(AI_WEB_JS, null); }
        catch (Throwable ignored) { }
    }

    private void aiWebLoad() {
        if (aiWebView == null) {
            aiWebShowError("这台设备没有 WebView 组件，无法使用 DeepSeek 网页版。\n"
                    + "一般手表都自带；如果没有，需要装上系统的 WebView。", false);
            return;
        }
        aiWebLoaded = true;
        aiWebHideError();
        aiWebProgress.setProgress(0);
        aiWebProgress.setVisibility(View.VISIBLE);
        // 等这一屏真正完成一次布局再加载：以前在切页淡入（alpha 0）时就 loadUrl，
        // 网页在不可见的硬件层里初始化，首帧丢失，淡入结束后就一直黑屏。
        final WebView toLoad = aiWebView;
        aiWebHost.post(new Runnable() {
            public void run() {
                if (alive() && aiWebLoaded && aiWebView == toLoad) toLoad.loadUrl(AI_WEB_URL);
            }
        });
    }

    private void aiWebShowError(String msg, boolean retryable) {
        if (aiWebErrorBox == null) return;
        aiWebErrorTxt.setText(msg);
        aiWebErrorBtn.setVisibility(retryable ? View.VISIBLE : View.GONE);
        aiWebErrorBox.setVisibility(View.VISIBLE);
        if (aiWebProgress != null) aiWebProgress.setVisibility(View.GONE);
        hideAiGestureHint();   // 提示别盖住错误文字；退出靠双指长按，设置页里有说明
    }

    private void aiWebHideError() {
        if (aiWebErrorBox != null) aiWebErrorBox.setVisibility(View.GONE);
    }

    private void aiWebRelogin() {
        confirmAction("重新登录？",
                "会清掉本机保存的网页登录状态，下次打开要重新登录。收藏、浏览记录和知识点都不受影响。",
                "清除并重新登录", 0xFFFFB74D, new Runnable() {
                    public void run() { aiWebClearLogin(); }
                });
    }

    private void aiWebClearLogin() {
        try {
            CookieManager cm = CookieManager.getInstance();
            cm.removeAllCookies(null);
            cm.flush();
        } catch (Throwable ignored) { }
        try { WebStorage.getInstance().deleteAllData(); } catch (Throwable ignored) { }
        if (aiWebView != null) {
            try {
                aiWebView.clearCache(true);
                aiWebView.clearFormData();
                aiWebView.clearHistory();
            } catch (Throwable ignored) { }
        }
        aiWebLoaded = false;
        toast("已清除网页登录状态");
    }

    /** 系统有没有 WebView 组件。API 26 起能直接问；更早的按「默认有」，真没有会在 new WebView 时被兜住。 */
    private boolean aiHasWebView() {
        try {
            if (Build.VERSION.SDK_INT >= 26) return WebView.getCurrentWebViewPackage() != null;
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    // ===== 问 AI 的三个设置子页 =====
    // 滚动容器、圆屏占位、返回键、开关、按钮全都照「关于 / 反馈 / 显示调节」的既有写法，
    // 不另起一套：OverScrollScrollView + roundPadScroll + inner + padInner + scrollToTop。

    /** 设置页里那种居中的说明小字。 */
    private TextView aiHint(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(m.subtitleSize - 2f);
        t.setTextColor(C_SUB);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(4), dp(3), dp(4), dp(2));
        return t;
    }

    /** 设置页里那种可点击的入口按钮。 */
    private TextView aiSettingBtn(String text, final View.OnClickListener onClick) {
        TextView b = new TextView(this);
        b.setText(text);
        b.setTextSize(m.backSize);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(C_PRIMARY);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(10), dp(10), dp(10), dp(10));
        b.setBackground(clickBg(C_PILL));
        b.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                vibrate();
                if (onClick != null) onClick.onClick(v);
            }
        });
        return b;
    }

    private void buildAiHubScreen() {
        aiHubScroll = new OverScrollScrollView(this);
        aiHubScroll.setVerticalScrollBarEnabled(false);
        roundPadScroll(aiHubScroll);
        aiHubInner = new LinearLayout(this);
        aiHubInner.setOrientation(LinearLayout.VERTICAL);
        aiHubScroll.addView(aiHubInner);
        aiHubScreen.addView(aiHubScroll, lw());
        renderAiHub();
    }

    private void renderAiHub() {
        int keepY = (aiHubInner.getChildCount() > 0) ? aiHubScroll.getScrollY() : -1;
        aiHubInner.removeAllViews();
        aiHubInner.addView(makeBack("‹ 返回设置", new View.OnClickListener() {
            public void onClick(View v) { goBack(S_SETTINGS); }
        }), wrapM());
        aiHubInner.addView(pageTitle("问 AI"), wrap());

        boolean api = "api".equals(aiMode);
        aiHubInner.addView(toggleRow("API AI（自行配置）", api, new View.OnClickListener() {
            public void onClick(View v) {
                if (aiNoWebView) {
                    toast("本机没有 WebView，只能用 API 模式");
                    return;
                }
                aiMode = "api".equals(aiMode) ? "web" : "api";
                prefs.edit().putString("ai_mode", aiMode)
                        .putBoolean("ai_choice_done", true).apply();
                markSubjectsDirty();
                renderAiHub();
            }
        }), wrap());
        aiHubInner.addView(aiHint(aiNoWebView
                ? "本机没有 WebView 组件，网页版用不了，已固定为 API 模式。"
                : ("当前：" + (api ? "API AI（自行配置）" : "DeepSeek 网页版")
                + "。关 = 网页版，开 = 自行配置；切换后主页「问 AI」走对应模式。")), wrap());
        if (api) {
            aiHubInner.addView(aiHint(aiApiReady()
                    ? ("接口：" + aiApiModel + "，可以测连接")
                    : ("还没配好：" + aiApiMissing())), wrap());
        }

        // 两个入口之间统一留 8dp：以前第二个按钮只有 topMargin，第一个没留，挨在一起了
        LinearLayout.LayoutParams lp1 = wrap();
        lp1.topMargin = dp(8);
        aiHubInner.addView(aiSettingBtn("API AI 设置", new View.OnClickListener() {
            public void onClick(View v) { aiCfgBack = S_AI_HUB; showScreen(S_AI_API_CFG); }
        }), lp1);
        LinearLayout.LayoutParams lp2 = wrap();
        lp2.topMargin = dp(8);
        aiHubInner.addView(aiSettingBtn("DeepSeek 网页版设置", new View.OnClickListener() {
            public void onClick(View v) { aiCfgBack = S_AI_HUB; showScreen(S_AI_WEB_CFG); }
        }), lp2);

        padInner(aiHubInner);
        if (keepY >= 0) scrollToY(aiHubScroll, keepY);
        else scrollToTop(aiHubScroll);
    }

    private void buildAiApiScreen() {
        aiApiScroll = new OverScrollScrollView(this);
        aiApiScroll.setVerticalScrollBarEnabled(false);
        roundPadScroll(aiApiScroll);
        aiApiInner = new LinearLayout(this);
        aiApiInner.setOrientation(LinearLayout.VERTICAL);
        aiApiScroll.addView(aiApiInner);
        aiApiScreen.addView(aiApiScroll, lw());
        renderAiApi();
    }

    private void renderAiApi() {
        int keepY = (aiApiInner.getChildCount() > 0) ? aiApiScroll.getScrollY() : -1;
        aiApiInner.removeAllViews();
        aiApiInner.addView(makeBack(aiCfgBack == S_SUBJECT ? "‹ 返回主页" : "‹ 返回问 AI",
                new View.OnClickListener() {
            public void onClick(View v) { goBack(aiCfgBack); }
        }), wrapM());
        aiApiInner.addView(pageTitle("API AI 设置"), wrap());

        // 状态摘要：一眼看清「能不能用、还差什么」
        TextView sum = new TextView(this);
        sum.setText(aiApiSummary());
        sum.setTextSize(m.subtitleSize - 2f);
        sum.setTextColor(aiApiReady() ? C_PRIMARY : C_SUB);
        sum.setGravity(Gravity.CENTER);
        sum.setLineSpacing(dp(2), 1.2f);
        sum.setPadding(dp(6), dp(7), dp(6), dp(7));
        sum.setBackgroundDrawable(pill(C_CARD));
        aiApiInner.addView(sum, wrapM());

        if (aiApiBusy != null) aiApiInner.addView(aiHint(aiApiBusy), wrap());

        aiApiInner.addView(sectionHeader("服务商"), wrap());
        aiApiInner.addView(apiPresetRow(), gap());
        aiApiInner.addView(aiHint("预设会填好地址和常见模型；自己搭的转发接口选「自定义」。"), wrap());
        aiApiInner.addView(aiHint("这是本软件更适配的方式：对话里回答提到的知识点会带下划线，"
                + "点一下直接跳到那条知识点（网页版没有这个）。"), wrap());

        aiApiInner.addView(sectionHeader("接口"), wrap());
        aiApiInner.addView(apiValueRow("地址", aiApiEndpoint.length() == 0 ? "未填" : aiApiEndpoint,
                new View.OnClickListener() {
                    public void onClick(View v) { editApiEndpoint(); }
                }), gap());
        aiApiInner.addView(apiValueRow("密钥", apiKeyMask(), new View.OnClickListener() {
            public void onClick(View v) { editApiSecret(); }
        }), gap());
        aiApiInner.addView(apiValueRow("模型", aiApiModel.length() == 0 ? "未填" : aiApiModel,
                new View.OnClickListener() {
                    public void onClick(View v) { pickApiModel(); }
                }), gap());

        aiApiInner.addView(aiSettingBtn("拉取模型列表", new View.OnClickListener() {
            public void onClick(View v) { aiApiFetchModels(); }
        }), gap());
        aiApiInner.addView(aiSettingBtn("测试连接", new View.OnClickListener() {
            public void onClick(View v) { aiApiTest(); }
        }), gap());

        aiApiInner.addView(sectionHeader("对话参数"), wrap());
        aiApiInner.addView(apiValueRow("温度", String.valueOf(API_TEMPS[aiApiTempIdx]),
                new View.OnClickListener() {
                    public void onClick(View v) {
                        aiApiTempIdx = (aiApiTempIdx + 1) % API_TEMPS.length;
                        saveApiConfig();
                        renderAiApi();
                    }
                }), gap());
        aiApiInner.addView(apiValueRow("上下文轮数", aiApiCtx + " 轮",
                new View.OnClickListener() {
                    public void onClick(View v) { editApiInt("上下文轮数", 1, 50, aiApiCtx, true); }
                }), gap());
        aiApiInner.addView(apiValueRow("最大回复长度", aiApiMax + " 字",
                new View.OnClickListener() {
                    public void onClick(View v) { editApiInt("最大回复长度", 128, 32768, aiApiMax, false); }
                }), gap());
        aiApiInner.addView(apiValueRow("思考强度", API_THINK[aiApiThinkIdx],
                new View.OnClickListener() {
                    public void onClick(View v) {
                        aiApiThinkIdx = (aiApiThinkIdx + 1) % API_THINK.length;
                        saveApiConfig();
                        renderAiApi();
                    }
                }), gap());
        aiApiInner.addView(aiHint("两项都点开直接填数字：轮数越多带的历史越长、越费 token；"
                + "最大回复长度按接口的字算（中文更费），太小会把公式和回答截断，被截断时对话里会出现「继续」。"
                + "公式多的回答建议 1024 以上；填太大也可能被接口本身的上限挡回来。"), wrap());
        aiApiInner.addView(aiHint("温度越高越发散；思考强度是发给接口的参数，只对支持的模型生效。"
                + "对话页顶部的「思考 开/关」只控制显不显示思考过程，两者互不影响。"), wrap());

        aiApiInner.addView(sectionHeader("关于密钥"), wrap());
        aiApiInner.addView(aiHint("密钥只存在这台手表上：单独存放、不进系统备份、不上传、不写日志。"
                + "换设备要重新填。手表键盘难打，可以从别处复制后点「粘贴」。"), wrap());
        aiApiInner.addView(aiHint("接口只允许 https：明文 http 会让密钥在网络里裸奔。"
                + "自建转发要填完整路径，比如 https://你的域名/v1。"), wrap());

        TextView clear = new TextView(this);
        clear.setText("清除密钥");
        clear.setTextSize(m.subtitleSize - 1f);
        clear.setGravity(Gravity.CENTER);
        clear.setTextColor(0xFFFFB74D);
        clear.setPadding(dp(8), dp(9), dp(8), dp(9));
        clear.setBackgroundDrawable(pill(C_PILL));
        clear.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                vibrate();
                confirmAction("清除密钥？", "只删本机保存的这一串，地址、模型和对话参数保留。", "清除",
                        0xFFFFB74D, new Runnable() {
                            public void run() {
                                saveApiSecret("");
                                toast("密钥已清除");
                                renderAiApi();
                            }
                        });
            }
        });
        LinearLayout.LayoutParams clp = wrap();
        clp.topMargin = dp(6);
        aiApiInner.addView(clear, clp);

        padInner(aiApiInner);
        if (keepY >= 0) scrollToY(aiApiScroll, keepY);
        else scrollToTop(aiApiScroll);
    }

    /** 行间距 4dp 的布局参数，省得每处都写一遍。 */
    private LinearLayout.LayoutParams gap() {
        LinearLayout.LayoutParams lp = wrap();
        lp.bottomMargin = dp(4);
        return lp;
    }

    // ---- 配置读写 ----

    private void loadApiConfig() {
        aiApiPreset = prefs.getInt("api_preset", 0);
        if (aiApiPreset < 0 || aiApiPreset >= API_PRESETS.length) aiApiPreset = 0;
        aiApiEndpoint = prefs.getString("api_endpoint", API_PRESET_BASE[aiApiPreset]);
        aiApiModel = prefs.getString("api_model", API_PRESET_MODEL[aiApiPreset]);
        aiApiTempIdx = clampIdx(prefs.getInt("api_temp", 1), API_TEMPS.length);
        // 新版存整数；老版本存的是 {4,8,16} / {256,512,1024,2048} 的档位下标，做一次迁移
        if (prefs.contains("api_ctx_num") || prefs.contains("api_max_num")) {
            aiApiCtx = clampInt(prefs.getInt("api_ctx_num", API_CTX_DEFAULT),
                    API_CTX_MIN, API_CTX_MAX, API_CTX_DEFAULT);
            aiApiMax = clampInt(prefs.getInt("api_max_num", API_MAX_DEFAULT),
                    API_MAX_MIN, API_MAX_MAX, API_MAX_DEFAULT);
        } else {
            int[] OLD_CTX = {4, 8, 16}, OLD_MAX = {256, 512, 1024, 2048};
            aiApiCtx = OLD_CTX[clampIdx(prefs.getInt("api_ctx", 1), OLD_CTX.length)];
            aiApiMax = OLD_MAX[clampIdx(prefs.getInt("api_max", 2), OLD_MAX.length)];
        }
        aiApiThinkIdx = clampIdx(prefs.getInt("api_think", 0), API_THINK.length);
        // 拉取过的模型列表也存本地：不存的话杀掉重进列表就空了，顶部按钮会退化成手填框
        aiApiModels.clear();
        String models = prefs.getString("api_models", "");
        if (models.length() > 0) {
            try {
                JSONArray a = new JSONArray(models);
                for (int i = 0; i < a.length(); i++) {
                    String id = a.optString(i, "").trim();
                    if (id.length() > 0 && !aiApiModels.contains(id)) aiApiModels.add(id);
                }
            } catch (Throwable ignored) { }
        }
        aiApiSecret = apiUnseal(secretPrefs().getString(API_SECRET_KEY, ""));
        chatShowThink = prefs.getBoolean("chat_show_think", false);
    }

    private static int clampIdx(int v, int len) {
        return (v < 0 || v >= len) ? 0 : v;
    }

    /** 用户填的整数：越界就退回默认值，免得手滑填个 999999 把接口打爆。 */
    private static int clampInt(int v, int min, int max, int def) {
        if (v < min || v > max) return def;
        return v;
    }

    private SharedPreferences secretPrefs() {
        return getSharedPreferences(API_SECRET_PREF, Context.MODE_PRIVATE);
    }

    private void saveApiConfig() {
        JSONArray a = new JSONArray();
        for (String s : aiApiModels) a.put(s);
        prefs.edit()
                .putInt("api_preset", aiApiPreset)
                .putString("api_endpoint", aiApiEndpoint)
                .putString("api_model", aiApiModel)
                .putString("api_models", a.toString())
                .putInt("api_temp", aiApiTempIdx)
                .putInt("api_ctx_num", aiApiCtx)
                .putInt("api_max_num", aiApiMax)
                .putInt("api_think", aiApiThinkIdx)
                .apply();
    }

    private void saveApiSecret(String key) {
        aiApiSecret = key == null ? "" : key.trim();
        secretPrefs().edit().putString(API_SECRET_KEY, apiSeal(aiApiSecret)).apply();
    }

    /** 落盘前套一层异或 + Base64：只为不让明文躺在备份文件里，不当作加密用。 */
    private static String apiSeal(String s) {
        if (s == null || s.length() == 0) return "";
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        for (int i = 0; i < b.length; i++) b[i] ^= (byte) (0x5A + (i * 31) % 128);
        return Base64.encodeToString(b, Base64.NO_WRAP);
    }

    private static String apiUnseal(String s) {
        if (s == null || s.length() == 0) return "";
        try {
            byte[] b = Base64.decode(s, Base64.NO_WRAP);
            for (int i = 0; i < b.length; i++) b[i] ^= (byte) (0x5A + (i * 31) % 128);
            return new String(b, StandardCharsets.UTF_8);
        } catch (Throwable t) {
            return "";
        }
    }

    private boolean aiApiReady() {
        return aiApiEndpoint.trim().length() > 0 && aiApiSecret.length() > 0
                && aiApiModel.trim().length() > 0;
    }

    private String aiApiMissing() {
        if (aiApiEndpoint.trim().length() == 0) return "先填接口地址";
        if (aiApiSecret.length() == 0) return "先填密钥";
        if (aiApiModel.trim().length() == 0) return "先填模型名";
        return "";
    }

    private String aiApiSummary() {
        String s = API_PRESETS[aiApiPreset] + " · "
                + (aiApiModel.trim().length() == 0 ? "未选模型" : aiApiModel);
        if (aiApiEndpoint.trim().length() == 0) return s + "\n地址未填";
        if (aiApiSecret.length() == 0) return s + "\n密钥未填，填完才能测";
        if (aiApiModel.trim().length() == 0) return s + "\n模型未填";
        return s + "\n配置完整，可以测连接";
    }

    private String apiKeyMask() {
        int n = aiApiSecret.length();
        if (n == 0) return "未填";
        if (n <= 10) return "已填 ·••••";
        return aiApiSecret.substring(0, 3) + "••••"
                + aiApiSecret.substring(n - 4);
    }

    // ---- 页面上的行与交互 ----

    /** 三选一的服务商条：选中项用主色底，一眼看清当前是哪个。 */
    private View apiPresetRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < API_PRESETS.length; i++) {
            final int idx = i;
            boolean on = idx == aiApiPreset;
            TextView b = new TextView(this);
            b.setText(API_PRESETS[i]);
            b.setTextSize(Math.max(9f, m.subtitleSize - 2f));
            b.setGravity(Gravity.CENTER);
            b.setTypeface(on ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            b.setTextColor(on ? C_PRIMARY : C_SUB);
            b.setPadding(dp(3), dp(9), dp(3), dp(9));
            b.setBackgroundDrawable(pill(on ? 0xFF14384A : C_PILL));
            b.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    vibrate();
                    applyApiPreset(idx);
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            lp.leftMargin = dp(2);
            lp.rightMargin = dp(2);
            row.addView(b, lp);
        }
        return row;
    }

    private void applyApiPreset(int idx) {
        aiApiPreset = idx;
        String base = API_PRESET_BASE[idx];
        if (base.length() > 0) {
            aiApiEndpoint = base;
            aiApiModel = API_PRESET_MODEL[idx];
        }
        aiApiModels.clear();
        saveApiConfig();
        renderAiApi();
        toast(base.length() > 0 ? ("已切到" + API_PRESETS[idx] + "，填密钥就能测") : "自定义：自己填地址和模型");
    }

    /** 一行「标签 —— 值」，点整行触发编辑；值太长时保留头尾、中间省略。 */
    private View apiValueRow(String label, String value, View.OnClickListener onClick) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10), dp(8), dp(10), dp(8));
        row.setBackgroundDrawable(pill(C_CARD));
        row.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                vibrate();
                if (onClick != null) onClick.onClick(v);
            }
        });

        TextView name = new TextView(this);
        name.setText(label);
        name.setTextSize(m.backSize);
        name.setTextColor(C_SUB);
        name.setSingleLine(true);

        TextView val = new TextView(this);
        val.setText(value);
        val.setTextSize(m.subtitleSize - 1f);
        val.setTextColor(C_PRIMARY);
        val.setGravity(Gravity.END);
        val.setSingleLine(true);
        val.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        vlp.leftMargin = dp(8);
        val.setLayoutParams(vlp);

        row.addView(name);
        row.addView(val);
        return row;
    }

    private void editApiEndpoint() {
        apiInputDialog("接口地址", "https://api.deepseek.com", aiApiEndpoint, false,
                new ApiTextSink() {
                    public void onText(String v) {
                        String s = v == null ? "" : v.trim();
                        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
                        if (s.length() == 0) {
                            toast("地址不能为空");
                            return;
                        }
                        if (!s.startsWith("https://") || s.length() < 14) {
                            toast("要 https:// 开头的完整地址");
                            return;
                        }
                        aiApiEndpoint = s;
                        aiApiPreset = guessApiPreset(s);
                        aiApiModels.clear();
                        saveApiConfig();
                        renderAiApi();
                    }
                });
    }

    private int guessApiPreset(String base) {
        for (int i = 0; i < API_PRESET_BASE.length; i++) {
            if (API_PRESET_BASE[i].length() > 0 && API_PRESET_BASE[i].equals(base)) return i;
        }
        return API_PRESET_BASE.length - 1;
    }

    private void editApiSecret() {
        apiInputDialog("API 密钥", "粘贴或输入密钥", aiApiSecret, true, new ApiTextSink() {
            public void onText(String v) {
                String s = v == null ? "" : v.trim();
                if (s.length() == 0) {
                    toast("没填内容，密钥没变");
                    return;
                }
                if (s.indexOf(' ') >= 0) {
                    toast("密钥里不该有空格，检查是否复制多了");
                    return;
                }
                saveApiSecret(s);
                renderAiApi();
                toast(s.length() < 16 ? "已保存，但这个长度不像正常密钥" : "密钥已保存");
            }
        });
    }

    /** 设置页点「模型」：模型名只有一个本地变量，改法有两种——从拉取的列表里选、或手动输入。 */
    private void pickApiModel() {
        if (aiApiModels.isEmpty()) {
            editApiModelName(null);
            return;
        }
        showApiModelPicker(null, true);
    }

    /** 手动改模型名：只在设置页用，对话页顶部不给输入框。 */
    private void editApiModelName(final Runnable after) {
        apiInputDialog("模型名", "如 deepseek-chat", aiApiModel, false, new ApiTextSink() {
            public void onText(String v) {
                String s = v == null ? "" : v.trim();
                if (s.length() == 0) {
                    toast("模型名不能为空");
                    return;
                }
                aiApiModel = s;
                saveApiConfig();
                if (after != null) after.run();
                else renderAiApi();
            }
        });
    }

    /** 拉取到的模型列表：模型多的服务商有几十个，所以放进滚动区。 */
    private void showApiModelPicker() { showApiModelPicker(null, true); }

    /** after 非空时选完调它（对话页要重绘自己，而不是设置页）；否则刷新设置页。
     *  allowManual 只在设置页为 true：选择列表底部带一行「手动输入模型名」。 */
    private void showApiModelPicker(final Runnable after, final boolean allowManual) {
        if (aiApiModels.isEmpty()) return;
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(12), dp(10), dp(12), dp(8));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF151515);
        bg.setCornerRadius(dp(16));
        box.setBackgroundDrawable(bg);

        final AlertDialog d = new AlertDialog.Builder(this).setView(box).create();

        TextView t = new TextView(this);
        t.setText("选模型（" + aiApiModels.size() + " 个）");
        t.setTextSize(m.subtitleSize);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(Color.WHITE);
        t.setGravity(Gravity.CENTER);
        box.addView(t, wrap());

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < aiApiModels.size(); i++) {
            final String id = aiApiModels.get(i);
            TextView b = new TextView(this);
            b.setText(id);
            b.setTextSize(m.subtitleSize - 1f);
            b.setTypeface(id.equals(aiApiModel) ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            b.setTextColor(id.equals(aiApiModel) ? C_PRIMARY : C_TEXT);
            b.setGravity(Gravity.CENTER);
            b.setSingleLine(true);
            b.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            b.setPadding(dp(8), dp(9), dp(8), dp(9));
            b.setBackgroundDrawable(pill(C_PILL));
            b.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    vibrate();
                    try { d.dismiss(); } catch (Throwable ignored) { }
                    aiApiModel = id;
                    saveApiConfig();
                    if (after != null) after.run();
                    else renderAiApi();
                }
            });
            LinearLayout.LayoutParams lp = wrap();
            lp.bottomMargin = dp(4);
            list.addView(b, lp);
        }

        ScrollView sv = new OverScrollScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.addView(list, wrap());
        tuneDialogScroll(sv, list);   // 滚动时整块重绘，否则窗口只刷脏区、留下残影
        final int pickMaxH = m.round ? dp(150) : dp(240);
        final LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, pickMaxH);
        slp.topMargin = dp(4);
        box.addView(sv, slp);
        // 只有两三个模型时不该留一大片空白：等弹窗显示、内容布局完再量一次高度，
        // 比上限小就按内容收缩（模型多的时候仍然封顶在 pickMaxH，可以滚）。
        d.setOnShowListener(new DialogInterface.OnShowListener() {
            public void onShow(DialogInterface dlg) {
                list.post(new Runnable() {
                    public void run() {
                        int h = list.getHeight();
                        if (h > 0 && h < pickMaxH) {
                            slp.height = h + dp(2);
                            sv.setLayoutParams(slp);
                        }
                    }
                });
            }
        });

        if (allowManual) {
            TextView manual = new TextView(this);
            manual.setText("手动输入模型名");
            manual.setTextSize(m.subtitleSize - 1f);
            manual.setTextColor(C_PRIMARY);
            manual.setGravity(Gravity.CENTER);
            manual.setPadding(dp(8), dp(7), dp(8), dp(7));
            manual.setBackgroundDrawable(clickBg(C_PILL));
            manual.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    vibrate();
                    try { d.dismiss(); } catch (Throwable ignored) { }
                    editApiModelName(after);
                }
            });
            LinearLayout.LayoutParams mlp2 = wrap();
            mlp2.topMargin = dp(4);
            box.addView(manual, mlp2);
        }

        TextView no = new TextView(this);
        no.setText("取消");
        no.setTextSize(m.subtitleSize - 1f);
        no.setGravity(Gravity.CENTER);
        no.setTextColor(C_SUB);
        no.setPadding(dp(8), dp(9), dp(8), dp(4));
        no.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                vibrate();
                try { d.dismiss(); } catch (Throwable ignored) { }
            }
        });
        box.addView(no, wrap());

        tuneDialogWindow(d);
        d.show();
        sizeDialog(d, 0.9f);
    }

    // ---- 通用小输入弹窗：一行输入 + 粘贴 + 保存 ----

    private interface ApiTextSink {
        void onText(String text);
    }

    /** 上下文轮数 / 最大回复长度：自己填数字，不再是点一下换个档位。
     *  越界直接给提示并拒绝保存，不默默改值（免得用户以为填成功了）。 */
    private void editApiInt(String title, final int min, final int max, int cur,
                            final boolean isCtx) {
        apiInputDialog(title, min + "～" + max, String.valueOf(cur), false, true,
                new ApiTextSink() {
                    public void onText(String v) {
                        String s = v == null ? "" : v.trim();
                        int n;
                        try {
                            n = Integer.parseInt(s);
                        } catch (Exception e) {
                            toast("请填数字");
                            return;
                        }
                        if (n < min || n > max) {
                            toast("请填 " + min + "～" + max + " 之间");
                            return;
                        }
                        if (isCtx) aiApiCtx = n;
                        else aiApiMax = n;
                        saveApiConfig();
                        renderAiApi();
                    }
                });
    }

    private void apiInputDialog(String title, String hint, String initial, boolean secret,
                                final ApiTextSink sink) {
        apiInputDialog(title, hint, initial, secret, false, sink);
    }

    /** digitsOnly=true 时让系统输入法切成数字键盘，并只收数字字符（键盘仍是系统输入法，不是自绘键盘）。 */
    private void apiInputDialog(String title, String hint, String initial, boolean secret,
                                boolean digitsOnly, final ApiTextSink sink) {
        apiInputDialog(title, hint, initial, secret, digitsOnly, true, sink);
    }

    /** showPaste=false 时不给「粘贴」——纯数字项粘进来也没用，多一个按钮反倒碍事。 */
    private void apiInputDialog(String title, String hint, String initial, boolean secret,
                                boolean digitsOnly, boolean showPaste, final ApiTextSink sink) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(13), dp(12), dp(13), dp(8));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF151515);
        bg.setCornerRadius(dp(16));
        box.setBackgroundDrawable(bg);

        TextView t = new TextView(this);
        t.setText(title);
        t.setTextSize(m.subtitleSize);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(Color.WHITE);
        t.setGravity(Gravity.CENTER);
        box.addView(t, wrap());

        final EditText ed = new EditText(this);
        ed.setText(initial == null ? "" : initial);
        ed.setHint(hint);
        ed.setSingleLine(true);
        ed.setTextSize(m.subtitleSize - 1f);
        ed.setTextColor(Color.WHITE);
        ed.setHintTextColor(C_SUB);
        ed.setBackgroundDrawable(pill(C_PILL));
        ed.setPadding(dp(10), dp(8), dp(10), dp(8));
        // 密码型不显示明文；地址/模型名用 URI 类型，避免首字母自动大写把地址写坏；数字项切数字键盘。
        // 数字键盘是系统输入法的一种布局（仍是系统键盘），再叠一层 InputFilter 兜住粘贴进来的非数字。
        ed.setInputType(secret
                ? (android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD)
                : (digitsOnly
                ? android.text.InputType.TYPE_CLASS_NUMBER
                : (android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_URI)));
        if (digitsOnly) {
            ed.setFilters(new android.text.InputFilter[]{new android.text.InputFilter() {
                public CharSequence filter(CharSequence source, int start, int end,
                                           Spanned dest, int dstart, int dend) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = start; i < end; i++) {
                        char c = source.charAt(i);
                        if (c >= '0' && c <= '9') sb.append(c);
                    }
                    // 只保留数字；被剔掉的字符由系统丢弃，不影响用户继续输入
                    return sb.length() == (end - start) ? null : sb.toString();
                }
            }});
        }
        LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        elp.topMargin = dp(6);
        box.addView(ed, elp);

        LinearLayout util = new LinearLayout(this);
        util.setOrientation(LinearLayout.HORIZONTAL);
        TextView paste = null;
        if (showPaste) {
            paste = new TextView(this);
            paste.setText("粘贴");
            paste.setTextSize(m.subtitleSize - 2f);
            paste.setGravity(Gravity.CENTER);
            paste.setTextColor(C_SUB);
            paste.setPadding(dp(6), dp(7), dp(6), dp(7));
            paste.setBackgroundDrawable(pill(C_PILL));
            util.addView(paste, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        }
        if (secret) {
            TextView wipe = new TextView(this);
            wipe.setText("清空");
            wipe.setTextSize(m.subtitleSize - 2f);
            wipe.setGravity(Gravity.CENTER);
            wipe.setTextColor(0xFFFFB74D);
            wipe.setPadding(dp(6), dp(7), dp(6), dp(7));
            wipe.setBackgroundDrawable(pill(C_PILL));
            LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            wlp.leftMargin = dp(4);
            util.addView(wipe, wlp);
            wipe.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    vibrate();
                    ed.setText("");
                }
            });
        }
        if (showPaste || secret) {
            LinearLayout.LayoutParams ulp = wrap();
            ulp.topMargin = dp(4);
            box.addView(util, ulp);
        }

        final AlertDialog d = new AlertDialog.Builder(this).setView(box).create();

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        TextView save = new TextView(this);
        save.setText("保存");
        save.setTextSize(m.backSize);
        save.setTypeface(Typeface.DEFAULT_BOLD);
        save.setGravity(Gravity.CENTER);
        save.setTextColor(C_PRIMARY);
        save.setPadding(dp(10), dp(11), dp(10), dp(11));
        save.setBackgroundDrawable(pill(0xFF14384A));
        bar.addView(save, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 2f));

        TextView cancel = new TextView(this);
        cancel.setText("取消");
        cancel.setTextSize(m.subtitleSize - 1f);
        cancel.setGravity(Gravity.CENTER);
        cancel.setTextColor(C_SUB);
        cancel.setPadding(dp(8), dp(11), dp(8), dp(11));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        clp.leftMargin = dp(6);
        bar.addView(cancel, clp);
        LinearLayout.LayoutParams blp = wrap();
        blp.topMargin = dp(6);
        box.addView(bar, blp);

        if (paste != null) {
            paste.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    vibrate();
                    String s = clipboardText();
                    if (s.length() == 0) {
                        toast("剪贴板是空的");
                        return;
                    }
                    ed.setText(s);
                    ed.setSelection(ed.getText().length());
                    toast("已粘贴");
                }
            });
        }
        save.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                vibrate();
                String v2 = ed.getText().toString();
                try { d.dismiss(); } catch (Throwable ignored) { }
                sink.onText(v2);
            }
        });
        cancel.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                vibrate();
                try { d.dismiss(); } catch (Throwable ignored) { }
            }
        });

        tuneDialogWindow(d);  // 必须 show() 之前：否则窗口按不透明格式建，滚动会留残影
        tuneDialogInput(d);   // 键盘弹出时弹窗不跳，内容不被顶走
        tuneEditScroll(ed, box);
        d.show();
        sizeDialog(d, 0.86f);
    }

    /** 读剪贴板第一项；没有就是空串。 */
    private String clipboardText() {
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm == null || !cm.hasPrimaryClip() || cm.getPrimaryClip() == null) return "";
            CharSequence cs = cm.getPrimaryClip().getItemAt(0).coerceToText(this);
            return cs == null ? "" : cs.toString().trim();
        } catch (Throwable t) {
            return "";
        }
    }

    // ---- 网络：拉模型 / 测连接 ----

    private String apiUrl(String path) {
        String b = aiApiEndpoint == null ? "" : aiApiEndpoint.trim();
        while (b.endsWith("/")) b = b.substring(0, b.length() - 1);
        if (!path.startsWith("/")) path = "/" + path;
        return b + path;
    }

    /** 安全取字符串：Android 的 optString 遇到 JSON null 会返回字面量 "null"，
     *  流式里每块都带一个 "content": null，就会拼出一长串 null。这里统一挡掉。 */
    private static String jsonStr(JSONObject o, String key) {
        if (o == null || key == null) return "";
        try {
            if (!o.has(key) || o.isNull(key)) return "";
            Object v = o.opt(key);
            if (v == null || v == JSONObject.NULL) return "";
            String s = String.valueOf(v);
            return "null".equals(s) ? "" : s;
        } catch (Throwable t) {
            return "";
        }
    }

    /** 一次接口调用的结果：HTTP 码 + 正文 + 网络层错误 + 耗时。 */
    private static class ApiRes {
        int code = 0;
        String body = "";
        String err = null;
        long ms = 0;
        boolean ok() { return err == null && code >= 200 && code < 300; }
    }

    /** 同步调用，必须在子线程跑；返回后自己 runOnUiThread 处理。 */
    private ApiRes apiCall(String method, String path, JSONObject payload) {
        ApiRes r = new ApiRes();
        long t0 = System.currentTimeMillis();
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(apiUrl(path)).openConnection();
            c.setRequestMethod(method);
            c.setConnectTimeout(15000);
            c.setReadTimeout(30000);
            // 不跟随跳转：跟到别的域会把 Authorization 一起带过去
            c.setInstanceFollowRedirects(false);
            c.setRequestProperty("Authorization", "Bearer " + aiApiSecret);
            c.setRequestProperty("Accept", "application/json");
            c.setRequestProperty("User-Agent", "HZKnowledge-Android");
            if (payload != null) {
                byte[] data = payload.toString().getBytes(StandardCharsets.UTF_8);
                c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                c.setDoOutput(true);
                c.setFixedLengthStreamingMode(data.length);
                OutputStream os = c.getOutputStream();
                os.write(data);
                os.flush();
                os.close();
            }
            r.code = c.getResponseCode();
            InputStream is;
            try {
                is = (r.code >= 200 && r.code < 300) ? c.getInputStream() : c.getErrorStream();
            } catch (Throwable t) {
                is = c.getErrorStream();
            }
            r.body = readAllText(is);
        } catch (java.net.SocketTimeoutException e) {
            r.err = "连接超时：检查网络和接口地址";
        } catch (java.net.UnknownHostException e) {
            r.err = "域名解析不了：地址可能写错";
        } catch (javax.net.ssl.SSLException e) {
            r.err = "安全连接失败：地址不是 https 或证书有问题";
        } catch (Throwable e) {
            r.err = "连不上：" + (e == null || e.getClass() == null
                    ? "未知原因" : e.getClass().getSimpleName());
        } finally {
            if (c != null) {
                try { c.disconnect(); } catch (Throwable ignored) { }
            }
            r.ms = System.currentTimeMillis() - t0;
        }
        return r;
    }

    private String readAllText(InputStream is) {
        if (is == null) return "";
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] b = new byte[8192];
            int n;
            while ((n = is.read(b)) > 0) bos.write(b, 0, n);
            is.close();
            String s = new String(bos.toByteArray(), StandardCharsets.UTF_8);
            // 只留前 4000 字：错误正文一般很短，成功正文前面就是有用的部分
            return s.length() > 4000 ? s.substring(0, 4000) : s;
        } catch (Throwable t) {
            return "";
        }
    }

    /** 把接口失败翻成人话，并带上服务端自己说的原因（截断）。 */
    private String apiErrText(ApiRes r) {
        if (r.err != null) return r.err;
        String detail = apiServerMsg(r.body);
        if (r.code == 400) return "请求被拒绝（400）" + detail;
        if (r.code == 401) return "密钥不对（401）" + detail;
        if (r.code == 402) return "余额不足（402）" + detail;
        if (r.code == 403) return "没有权限（403）" + detail;
        if (r.code == 404) return "地址不对（404）：有的服务要带 /v1" + detail;
        if (r.code == 429) return "请求太频繁或额度用尽（429）" + detail;
        if (r.code >= 500) return "服务端故障（" + r.code + "），稍后再试" + detail;
        if (r.code == 0) return "没拿到响应" + detail;
        return "接口返回 " + r.code + detail;
    }

    private String apiServerMsg(String body) {
        if (body == null || body.trim().length() == 0) return "";
        try {
            JSONObject o = new JSONObject(body);
            JSONObject e = o.optJSONObject("error");
            String m = e == null ? jsonStr(o, "message") : jsonStr(e, "message");
            if (m.length() == 0) m = jsonStr(o, "detail");
            m = firstLine(m == null ? "" : m.trim(), 40);
            return m.length() == 0 ? "" : "：" + m;
        } catch (Throwable t) {
            String s = firstLine(body.trim(), 40);
            return s.length() == 0 ? "" : "：" + s;
        }
    }

    private void aiApiFetchModels() {
        if (aiApiBusy != null) return;
        if (aiApiEndpoint.trim().length() == 0) {
            toast("先填接口地址");
            return;
        }
        if (aiApiSecret.length() == 0) {
            toast("先填密钥");
            return;
        }
        aiApiBusy = "正在拉取模型列表…";
        renderAiApi();
        final String base = aiApiEndpoint;
        final String key = aiApiSecret;
        new Thread(new Runnable() {
            public void run() {
                final ApiRes r = apiCall("GET", "/models", null);
                final List<String> ids = new ArrayList<String>();
                if (r.ok()) {
                    try {
                        JSONArray arr = new JSONObject(r.body).optJSONArray("data");
                        if (arr != null) {
                            for (int i = 0; i < arr.length(); i++) {
                                JSONObject o = arr.optJSONObject(i);
                                String id = jsonStr(o, "id");
                                if (id.length() > 0 && !ids.contains(id)) ids.add(id);
                            }
                        }
                    } catch (Throwable ignored) { }
                }
                final boolean keep = base.equals(aiApiEndpoint) && key.equals(aiApiSecret);
                runOnUiThread(new Runnable() {
                    public void run() {
                        aiApiBusy = null;
                        if (!alive()) return;
                        if (screen == S_AI_API_CFG) renderAiApi();
                        if (!keep) { toast("配置已改，重新拉一次"); return; }
                        if (!r.ok()) { toast(apiErrText(r)); return; }
                        if (ids.isEmpty()) { toast("接口没返回模型，手动填模型名"); return; }
                        aiApiModels.clear();
                        aiApiModels.addAll(ids);
                        // 还没选过模型：直接把第一个填上，省得再点一次
                        if (aiApiModel.trim().length() == 0) aiApiModel = ids.get(0);
                        // 列表和当前模型一起落盘：下次进来顶部按钮还能直接切
                        saveApiConfig();
                        if (screen == S_AI_API_CFG) renderAiApi();
                        showApiModelPicker();
                    }
                });
            }
        }).start();
    }

    private void aiApiTest() {
        if (aiApiBusy != null) return;
        if (!aiApiReady()) {
            toast(aiApiMissing());
            return;
        }
        aiApiBusy = "正在测试连接…（最长 30 秒）";
        renderAiApi();
        final String base = aiApiEndpoint;
        final String key = aiApiSecret;
        final String model = aiApiModel;
        new Thread(new Runnable() {
            public void run() {
                ApiRes r;
                JSONObject p = new JSONObject();
                try {
                    p.put("model", model);
                    JSONArray msgs = new JSONArray();
                    JSONObject m1 = new JSONObject();
                    m1.put("role", "user");
                    m1.put("content", "只回复两个字：收到");
                    msgs.put(m1);
                    p.put("messages", msgs);
                    p.put("max_tokens", 16);
                    p.put("temperature", 0);
                    p.put("stream", false);
                    r = apiCall("POST", "/chat/completions", p);
                } catch (Throwable t) {
                    r = new ApiRes();
                    r.err = "请求拼装失败";
                }
                final ApiRes res = r;
                final boolean keep = base.equals(aiApiEndpoint) && key.equals(aiApiSecret);
                runOnUiThread(new Runnable() {
                    public void run() {
                        aiApiBusy = null;
                        if (!alive()) return;
                        if (screen == S_AI_API_CFG) renderAiApi();
                        if (!keep) { toast("配置已改，重新测一次"); return; }
                        if (!res.ok()) { toast(apiErrText(res)); return; }
                        String reply = apiReplyText(res.body);
                        long ms = res.ms;
                        String took = (ms / 100) / 10f + " 秒";
                        toast("连接正常（" + took + "）" + (reply.length() == 0 ? "" : "：" + reply));
                    }
                });
            }
        }).start();
    }

    private String apiReplyText(String body) {
        try {
            JSONArray ch = new JSONObject(body).optJSONArray("choices");
            if (ch == null || ch.length() == 0) return "";
            JSONObject c0 = ch.optJSONObject(0);
            JSONObject msg = c0 == null ? null : c0.optJSONObject("message");
            if (msg == null) return "";
            String s = jsonStr(msg, "content");
            if (s.trim().length() == 0) s = jsonStr(msg, "reasoning_content");
            return firstLine(s == null ? "" : s.trim(), 24);
        } catch (Throwable t) {
            return "";
        }
    }

    // ================= API 对话页（自行配置模式） =================

    private void buildAiChatScreen() {
        // 消息列表占满剩余高度；顶部的返回 / 模型 / 清空做成「内容头」跟着列表滚，
        // 不钉在最上面——圆屏顶边是圆弧，钉死的按钮会落在圆弧外点不到。
        aiChatScroll = new OverScrollScrollView(this);
        aiChatScroll.setVerticalScrollBarEnabled(false);
        roundPadScroll(aiChatScroll);
        aiChatInner = new LinearLayout(this);
        aiChatInner.setOrientation(LinearLayout.VERTICAL);
        aiChatScroll.addView(aiChatInner, wrap());
        aiChatScreen.addView(aiChatScroll, lw());

        // 底部：输入 + 停止（停止只在回复中出现）。它是居中的一条，不会碰到圆弧
        buildChatInputBar();

        loadChatHistory();   // 上次的对话（进程被杀后回来的情况）在这一步恢复
        renderAiChat();
    }

    /** 对话页的内容头：返回 / 当前模型（点一下换）/ 清空。每次重绘都新建，状态从字段里取。 */
    private View chatTopBar() {
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        top.addView(makeBack("‹", new View.OnClickListener() {
            public void onClick(View v) { goBack(aiChatBack); }
        }), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView model = new TextView(this);
        aiChatModelBtn = model;
        aiChatModelBtn.setTextSize(chatBarSize());
        aiChatModelBtn.setTextColor(C_SUB);
        aiChatModelBtn.setGravity(Gravity.CENTER);
        aiChatModelBtn.setSingleLine(true);
        aiChatModelBtn.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        aiChatModelBtn.setPadding(dp(8), dp(6), dp(8), dp(6));
        aiChatModelBtn.setBackgroundDrawable(clickBg(C_PILL));
        aiChatModelBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                vibrate();
                pickChatModel();
            }
        });
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        mlp.leftMargin = dp(4);
        mlp.rightMargin = dp(4);
        top.addView(aiChatModelBtn, mlp);

        // 「思考」显示开关：只控显示，不影响发给接口的思考强度
        TextView think = new TextView(this);
        chatThinkBtn = think;
        think.setText(chatShowThink ? "思考 开" : "思考 关");
        think.setTextSize(chatBarSize());
        think.setTypeface(chatShowThink ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        think.setTextColor(chatShowThink ? C_PRIMARY : C_SUB);
        think.setGravity(Gravity.CENTER);
        think.setSingleLine(true);
        think.setPadding(dp(7), dp(6), dp(7), dp(6));
        think.setBackgroundDrawable(clickBg(C_PILL));
        think.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                vibrate();
                chatShowThink = !chatShowThink;
                prefs.edit().putBoolean("chat_show_think", chatShowThink).apply();
                // 按钮本身先跟着变，否则点了看不出状态（反馈 #44）
                if (chatThinkBtn != null) {
                    chatThinkBtn.setText(chatShowThink ? "思考 开" : "思考 关");
                    chatThinkBtn.setTextColor(chatShowThink ? C_PRIMARY : C_SUB);
                    chatThinkBtn.setTypeface(chatShowThink ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
                }
                // 整页重建：历史对话每条的思考块要跟着增删，只翻可见性做不到
                //（关掉时那些块根本没建）。先转个圈再重建，顺便盖住滚动位置的变化。
                reloadChatWithSpinner();
                toast(chatShowThink ? "显示思考过程" : "已隐藏思考过程");
            }
        });
        top.addView(think, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView clear = new TextView(this);
        clear.setText("清空");
        clear.setTextSize(chatBarSize());
        clear.setTextColor(C_SUB);
        clear.setGravity(Gravity.CENTER);
        clear.setPadding(dp(8), dp(6), dp(8), dp(6));
        clear.setBackgroundDrawable(clickBg(C_PILL));
        clear.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                vibrate();
                if (chatMsgs.isEmpty()) {
                    toast("还没有对话");
                    return;
                }
                confirmAction("清空这次对话？", "只清屏上的内容，接口那边不会被动到。",
                        "清空", 0xFFFFB74D, new Runnable() {
                            public void run() { chatReset(); }
                        });
            }
        });
        top.addView(clear, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return top;
    }

    private void buildChatInputBar() {
        // 底部就两个按钮：发消息（主，平时独占一行）和取消（只在生成中出现）。
        // 圆屏越靠下越窄，所以底部留出安全距离，并按圆的几何把整条宽度收在圆内，
        // 这样两个按钮都贴着屏幕下沿、完整可见，也不需要滚动。
        aiChatBar = new LinearLayout(this);
        aiChatBar.setOrientation(LinearLayout.HORIZONTAL);
        aiChatBar.setGravity(Gravity.CENTER);   // 按钮改成按内容宽度后必须水平居中，否则贴左边

        // 贴着屏幕最下沿：圆屏只留一点点（4dp 起），宽度由下面的圆几何另算，不会出圆
        int bottomGap = m.round ? Math.max(dp(5), Math.round(m.roundSafe * 0.55f)) : dp(4);
        aiChatBar.setPadding(0, dp(2), 0, bottomGap);
        // 高度交给内容自己算（字号 + 上下 6dp 内边距），和顶部「思考 / 清空」完全一致。
        // 原来固定 38dp、按 2:1 铺满整行，看着像一条色块，比上面那排按钮大一圈。
        int barH = LinearLayout.LayoutParams.WRAP_CONTENT;

        // 一个按钮就够：平时是「发消息」，生成中就地变成「取消」，点它中断。
        // 以前是两个按钮并排（2:1），既占地方又多一个状态，没必要。
        aiChatInputBtn = new TextView(this);
        aiChatInputBtn.setTextSize(chatBarSize());
        aiChatInputBtn.setGravity(Gravity.CENTER);
        aiChatInputBtn.setSingleLine(true);
        aiChatInputBtn.setPadding(dp(14), dp(6), dp(14), dp(6));
        aiChatInputBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                vibrate();
                if (chatBusy) chatStop();
                else chatAskInput();
            }
        });
        aiChatBar.addView(aiChatInputBtn,
                new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, barH));
        updateChatBar();     // 文案 / 配色按当前状态先摆好

        // 按圆几何限制整条宽度：按钮下沿离屏幕底 bottomGap 时，圆在该高度只剩 2*sqrt(2Rd-d^2) 宽
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        if (m.round) {
            double r = Math.min(getResources().getDisplayMetrics().widthPixels,
                    getResources().getDisplayMetrics().heightPixels) / 2.0;
            double d = bottomGap;                       // 按钮下沿到圆底的距离
            double half = Math.sqrt(Math.max(1.0, 2 * r * d - d * d));
            int cap = (int) Math.floor(half * 2) - dp(2);   // 留 2dp 余量
            int avail = getResources().getDisplayMetrics().widthPixels - 2 * m.sidePad;
            // 贴着下沿时圆只剩这么宽：取 cap，但至少给 76dp，否则两个按钮放不下字
            blp.width = Math.max(Math.min(dp(76), avail), Math.min(avail, cap));
            blp.gravity = Gravity.CENTER_HORIZONTAL;
        }
        aiChatScreen.addView(aiChatBar, blp);
    }

    /** 进页面时先给个「正在整理」占位，下一帧再真正渲染：
     *  这样切页动画不会被解析公式卡住，用户也不会觉得点了没反应。 */
    private void showChatLoadingThenRender() {
        if (aiChatInner == null) return;
        aiChatInner.removeAllViews();
        TextView t = new TextView(this);
        t.setText("正在整理对话…");
        t.setTextSize(chatTinySize());
        t.setTextColor(C_SUB);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(8), dp(20), dp(8), dp(20));
        aiChatInner.addView(t, wrap());
        aiChatInner.post(new Runnable() {
            public void run() {
                if (alive() && screen == S_AI_CHAT) renderAiChat();
            }
        });
    }

    /** 切「思考」开关后重建整页：先只放一个旋转圆圈，让用户看出「在重新整理」，
     *  再整页重画。转圈同时把滚动位置的变化盖住，省得再做一套位置恢复。 */
    private void reloadChatWithSpinner() {
        if (aiChatInner == null) return;
        final int gen = ++chatReloadGen;
        chatDirty = false;
        chatStreamTv = null;
        chatStreamReasonTv = null;
        chatThinkingTv = null;
        chatBodies.clear();
        chatFoldTips.clear();
        chatRelatedBoxes.clear();
        aiChatInner.removeAllViews();
        // 顶栏（返回/模型/思考/清空）保留着，别让它跟着一起消失再出现
        aiChatInner.addView(chatTopBar(), wrapM());

        LinearLayout holder = new LinearLayout(this);
        holder.setOrientation(LinearLayout.VERTICAL);
        holder.setGravity(Gravity.CENTER);
        holder.setPadding(0, dp(36), 0, dp(36));
        ProgressBar spin = new ProgressBar(this);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(dp(26), dp(26));
        holder.addView(spin, slp);
        aiChatInner.addView(holder, wrap());

        aiChatInner.postDelayed(new Runnable() {
            public void run() {
                if (gen != chatReloadGen) return;          // 连点时只认最后一次
                if (!alive() || screen != S_AI_CHAT) return;
                renderAiChat();
            }
        }, 260);
    }

    /** 重画整页：消息不多，整页重建比局部更新更不容易出错。 */
    private void renderAiChat() {
        if (aiChatInner == null) return;
        chatDirty = false;
        updateChatBar();
        chatStreamTv = null;
        chatStreamReasonTv = null;
        chatThinkingTv = null;
        chatBodies.clear();
        chatFoldTips.clear();
        chatRelatedBoxes.clear();
        aiChatInner.removeAllViews();
        aiChatInner.addView(chatTopBar(), wrapM());
        if (aiChatModelBtn != null) {
            aiChatModelBtn.setText(aiApiModel.length() == 0 ? "未选模型" : aiApiModel);
        }

        if (chatMsgs.isEmpty()) {
            TextView hint = new TextView(this);
            hint.setText("点下面一行写问题。\n回答里提到的知识点带下划线，点一下就跳过去。\n"
                    + "回答来自你配置的接口，App 只做转发：不存盘、不上传。");
            hint.setTextSize(chatTinySize());
            hint.setTextColor(C_SUB);
            hint.setGravity(Gravity.CENTER);
            hint.setLineSpacing(dp(3), 1.3f);
            hint.setPadding(dp(8), dp(14), dp(8), dp(14));
            aiChatInner.addView(hint, wrap());
        } else {
            for (int i = 0; i < chatMsgs.size(); i++) {
                ChatMsg msg = chatMsgs.get(i);
                LinearLayout.LayoutParams lp = wrap();
                lp.bottomMargin = dp(6);
                aiChatInner.addView(chatBubble(msg), lp);
            }
        }
        // 上下各加一段屏高 25% 的占位（圆屏），内容才不会贴在圆弧边上
        padInner(aiChatInner);
        if (chatScrollTopOnRender) {
            chatScrollTopOnRender = false;
            scrollChatToTop();
        } else {
            scrollChatToBottom(false);
        }
    }

    /** 停在对话顶部：顶部占位之后就是「返回 / 模型 / 思考 / 清空」那一行。 */
    private void scrollChatToTop() {
        if (aiChatScroll == null) return;
        aiChatScroll.post(new Runnable() {
            public void run() {
                if (alive() && aiChatScroll != null) aiChatScroll.scrollTo(0, overDist());
            }
        });
    }

    /** 带兜底的渲染入口：公式解析或绘制出任何问题（包括嵌套过深导致 StackOverflowError），
     *  都退回纯文本，绝不让一条回复把整个 App 带崩。 */
    private CharSequence safeMarkup(String src) {
        String text = src == null ? "" : src;
        try {
            if (text.length() > 12000) {          // 超长回复不做公式排版，省内存也省时间
                return fixListMarks(text);
            }
            return applyMarkup(fixListMarks(fontSafe(text)));
        } catch (Throwable t) {
            log("CHAT", "公式渲染失败，退回纯文本：" + t);
            return fixListMarks(text);
        }
    }

    /** 行首的「- 」「* 」项目符号换成「• 」：横杠紧贴公式会被读成负号。
     *  只在行首、且后面跟空白时替换（"-1 ≤ x" 这种负数不动）；其它位置的横杠保持原样。 */
    private static String fixListMarks(String s) {
        if (s == null || s.length() == 0) return "";
        StringBuilder out = new StringBuilder(s.length() + 8);
        boolean lineStart = true;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (lineStart && (c == '-' || c == '*') && i + 1 < s.length()
                    && (s.charAt(i + 1) == ' ' || s.charAt(i + 1) == '\t')) {
                out.append('\u2022');
                lineStart = false;
                continue;              // 后面的空格照常输出，成「• 」
            }
            if (c == '\n') lineStart = true;
            else if (c != ' ' && c != '\t') lineStart = false;
            out.append(c);
        }
        return out.toString();
    }

    /** 气泡上的点击：320ms 内两下算双击，切换折叠。 */
    private void onBubbleTap(ChatMsg msg) {
        long now = SystemClock.uptimeMillis();
        if (now - msg.lastTapAt < 320L) {
            msg.lastTapAt = 0L;
            vibrate();
            msg.folded = !msg.folded;
            applyFold(msg);
        } else {
            msg.lastTapAt = now;
        }
    }

    /** 折叠只改这一条的显示：设行数、翻提示可见性，不重建整页（重建要重新解析公式，手表上会卡）。 */
    private void applyFold(ChatMsg msg) {
        TextView body = chatBodies.get(msg);
        if (body != null) {
            if (msg.folded) {
                body.setMaxLines(4);
                body.setEllipsize(TextUtils.TruncateAt.END);
            } else {
                body.setMaxLines(Integer.MAX_VALUE);
                body.setEllipsize(null);
            }
        }
        TextView tip = chatFoldTips.get(msg);
        if (tip != null) tip.setVisibility(msg.folded ? View.VISIBLE : View.GONE);
        View rel = chatRelatedBoxes.get(msg);
        if (rel != null) rel.setVisibility(msg.folded ? View.GONE : View.VISIBLE);
    }

    /** 兼容旧回答：把【知识:xxx】整个外壳去掉，只留 xxx 当普通文字。
     *  v3.4.16 起不再用行内链接（和底部「相关知识点」重复），这条只是兜底，别让标签露出来。 */
    private static String stripKbTags(String text) {
        if (text == null || text.indexOf("【知识") < 0) return text;
        StringBuilder out = new StringBuilder();
        int from = 0;
        while (true) {
            int s0 = text.indexOf("【知识", from);
            if (s0 < 0) { out.append(text, from, text.length()); break; }
            int e0 = text.indexOf('】', s0);
            if (e0 < 0) { out.append(text, from, text.length()); break; }
            out.append(text, from, s0);
            String inner = text.substring(s0 + 3, e0)
                    .replace("：", "").replace(":", "").trim();
            out.append(inner);
            from = e0 + 1;
        }
        return out.toString();
    }

    /** 文本归一化的缓存：一次渲染要对上千个标题做正则，缓存下来省很多时间。
     *  用 ConcurrentHashMap：答完在后代线程算相关知识点时也会写它，普通 HashMap 并发写会坏。 */
    private static final java.util.concurrent.ConcurrentHashMap<String, String> normCache =
            new java.util.concurrent.ConcurrentHashMap<String, String>();

    /** 去掉编号、括号注解、空白和「的」，只留主干用来比对标题。 */
    private static String normTitle(String s) {
        if (s == null) return "";
        String hit = normCache.get(s);
        if (hit != null) return hit;
        String t = s.replaceAll("^[0-9０-９.．、\\s]+", "");
        t = t.replaceAll("[（(][^）)]*[）)]", "");
        t = t.replace("的", "").replaceAll("\\s+", "");
        if (normCache.size() < 4000) normCache.put(s, t);
        return t;
    }

    /** 标题匹配打分：完全同名最高，其次是去掉编号/括号后同名，再看前缀、包含，
     *  同等条件下短的优先、分组略优先——「基本不等式」应该落到「2.2 基本(均值)不等式」
     *  而不是「基本不等式的实际应用」。 */
    private static int kbScore(String kw, String name, boolean group) {
        if (name == null) return -1;
        String k = kw == null ? "" : kw.trim();
        if (k.length() == 0) return -1;
        // 同分时偏叶子：叶子才是真正的内容，分组只是容器；
        // 这样「基本不等式」会落到「基本(均值)不等式」而不是「2.2 基本(均值)不等式」这一层
        int bonus = group ? 0 : 10;
        if (name.equals(k)) return 1000 + bonus;
        String nk = normTitle(k), nn = normTitle(name);
        if (nn.length() > 0 && nn.equals(nk)) return 900 + bonus;
        if (nn.length() > 0 && nn.startsWith(nk)) return 700 - (nn.length() - nk.length()) + bonus;
        if (nn.length() > 0 && nn.contains(nk)) return 500 - (nn.length() - nk.length()) + bonus;
        if (nn.length() > 0 && nk.contains(nn)) return 300 - (nk.length() - nn.length()) + bonus;
        return -1;
    }

    /** 回答末尾的关键词行标记：模型按 system prompt 输出「【相关:a|b|c】」，应用解析后隐藏。 */
    private static final String REL_TAG = "【相关";
    /** 一条回答最多展示几个相关知识点。 */
    private static final int REL_LIMIT = 3;
    /** 太泛的关键词：几乎每科都有，拿它匹配会命中一堆不相干的标题。 */
    private static final String[] REL_STOP = {
            "公式", "方法", "定义", "性质", "概念", "原理", "结论", "应用",
            "计算", "求解", "问题", "题目", "考点", "知识点", "内容", "区别", "关系"
    };

    private static boolean isGenericKey(String k) {
        if (k == null) return true;
        String t = k.trim();
        if (t.length() < 2) return true;                 // 单字必泛
        for (String s : REL_STOP) if (s.equals(t)) return true;
        return false;
    }

    /** 把正文末尾的【相关:…】整行剔掉（流式输出到一半时连半截一起藏住，别让用户看到残标签）。 */
    private static String stripRelatedTag(String text) {
        if (text == null) return "";
        int i = text.lastIndexOf(REL_TAG);
        if (i < 0) return text;
        int end = text.indexOf('】', i);
        if (end < 0) return text.substring(0, i);      // 还没写完：先藏掉
        return (text.substring(0, i) + text.substring(end + 1)).trim();
    }

    /** 取出【相关:…】里的关键词，最多 6 个、每个 2~12 字（太短太泛，太长基本匹配不到）。 */
    private static List<String> parseRelatedKeys(String text) {
        List<String> out = new ArrayList<String>();
        if (text == null) return out;
        int i = text.lastIndexOf(REL_TAG);
        if (i < 0) return out;
        int end = text.indexOf('】', i);
        if (end < 0) return out;
        String inner = text.substring(i + REL_TAG.length(), end)
                .replace("：", "").replace(":", "").trim();
        for (String p : inner.split("[|｜/、,，]")) {
            String k = p.trim();
            if (k.length() >= 2 && k.length() <= 12 && !isGenericKey(k) && !out.contains(k)) out.add(k);
            if (out.size() >= 6) break;
        }
        return out;
    }

    /** 按关键词重合度挑最相关的知识点：多个词命中同一个节点会累加分数，分组降权，取前 limit 个。 */
    private List<Node> rankKnowledge(List<String> keys, int limit) {
        if (keys == null || keys.isEmpty() || limit <= 0) return new ArrayList<Node>();
        final java.util.HashMap<Node, Integer> score = new java.util.HashMap<Node, Integer>();
        for (String k : keys) {
            for (Node n : allHits) {
                int sc = kbScore(k, n.name, false);
                if (sc > 0) {
                    Integer old = score.get(n);
                    score.put(n, (old == null ? 0 : old) + sc);
                }
            }
            for (Node n : allGroups) {
                int sc = kbScore(k, n.name, true);
                if (sc > 0) {
                    Integer old = score.get(n);
                    // 分组是容器，同分时让叶子排在前面
                    score.put(n, (old == null ? 0 : old) + sc - 120);
                }
            }
        }
        List<Node> list = new ArrayList<Node>(score.keySet());
        java.util.Collections.sort(list, new java.util.Comparator<Node>() {
            public int compare(Node a, Node b) {
                int sa = score.get(a), sb = score.get(b);
                if (sa != sb) return sb - sa;
                return a.name == null ? 0 : a.name.compareTo(b.name == null ? "" : b.name);
            }
        });
        if (list.size() > limit) return new ArrayList<Node>(list.subList(0, limit));
        return list;
    }

    /** 没有关键词行时的兜底：直接看哪些知识点标题出现在「提问 + 回答」里，越长的标题越可信。 */
    private List<Node> rankKnowledgeByText(String text, int limit) {
        List<Node> out = new ArrayList<Node>();
        if (text == null || text.length() < 4 || limit <= 0) return out;
        List<Node> found = new ArrayList<Node>();
        for (Node n : allHits) {
            String name = n.name == null ? "" : n.name.trim();
            if (name.length() < 3) continue;                 // 两字标题太容易误中（如「集合」）
            if (text.contains(name)) found.add(n);
        }
        if (found.isEmpty()) return out;
        java.util.Collections.sort(found, new java.util.Comparator<Node>() {
            public int compare(Node a, Node b) {
                int la = a.name == null ? 0 : a.name.length();
                int lb = b.name == null ? 0 : b.name.length();
                return lb - la;                              // 长标题优先：更具体
            }
        });
        for (int i = 0; i < found.size() && i < limit; i++) out.add(found.get(i));
        return out;
    }

    /** 一条消息气泡：用户主色底、AI 卡片底；长按复制，出错时给「重试」。 */
    private View chatBubble(final ChatMsg msg) {
        final boolean user = "user".equals(msg.role);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundDrawable(pill(user ? 0xFF14384A : C_CARD));
        box.setPadding(dp(10), dp(7), dp(10), dp(8));

        // 思考过程：只有开关打开才建这个块（关掉时连块都不建，避免流式里被强制显示出来）
        if (!user && chatShowThink
                && (msg.streaming || (msg.reasoning != null && msg.reasoning.length() > 0))) {
            // 推理内容可能比正文晚到：先建一个空行占位，流式时再显形
            TextView rt = new TextView(this);
            rt.setText(msg.reasoning.length() == 0 ? "" : "思考：" + prettyReasoning(msg.reasoning));
            rt.setTextSize(chatTinySize());
            rt.setTextColor(C_SUB);
            rt.setLineSpacing(dp(1), 1.2f);
            rt.setVisibility(msg.reasoning.length() > 0 ? View.VISIBLE : View.GONE);
            LinearLayout.LayoutParams rlp = wrap();
            rlp.bottomMargin = dp(3);
            box.addView(rt, rlp);
            if (msg.streaming) chatStreamReasonTv = rt;
        } else if (!user && !chatShowThink && msg.streaming && msg.content.length() == 0) {
            // 思考藏起来时，生成期间只说一句「思考中…」，正文一开始就撤掉
            TextView wait = new TextView(this);
            wait.setText("思考中…");
            wait.setTextSize(chatTinySize());
            wait.setTextColor(C_SUB);
            LinearLayout.LayoutParams wlp = wrap();
            wlp.bottomMargin = dp(3);
            box.addView(wait, wlp);
            chatThinkingTv = wait;
        }

        final TextView body = new TextView(this);
        String text;
        if (msg.err != null) text = msg.err;
        else if (msg.content.length() == 0) text = msg.streaming ? "…" : "（空回复）";
        else text = msg.content;
        // 末尾【相关:…】关键词行 + 旧式【知识:…】标签都去掉，只留正文文字
        if (!user) text = stripKbTags(stripRelatedTag(text));
        // AI 的回复走知识点同一套渲染：\frac / \sqrt / ^{} / **重点** 都会被画出来；
        // 用户自己打的字保持原样，免得一个下划线就被当成下标。
        if (user) {
            body.setText(text);
        } else {
            // 公式解析很贵：同一条内容只解析一次，进页面/切开关时直接复用
            if (msg.bodySpan == null || !text.equals(msg.bodyKey)) {
                msg.bodySpan = safeMarkup(text);
                msg.bodyKey = text;
            }
            body.setText(msg.bodySpan);
        }
        body.setTextSize(chatBodySize());
        body.setTextColor(msg.err != null ? 0xFFFFB74D : C_TEXT);
        body.setLineSpacing(dp(2), 1.25f);
        box.addView(body, wrap());
        if (msg.streaming) chatStreamTv = body;
        chatBodies.put(msg, body);

        // 正文只处理双击折叠（行内知识点链接已取消，统一放到底部「相关知识点」）
        final boolean canFold = !user && !msg.streaming && msg.err == null
                && msg.content.length() > 100;
        if (canFold && msg.folded) {
            body.setMaxLines(4);
            body.setEllipsize(TextUtils.TruncateAt.END);
        }
        if (canFold) {
            body.setOnTouchListener(new View.OnTouchListener() {
                float downX, downY;
                boolean moved;
                public boolean onTouch(View v, MotionEvent ev) {
                    switch (ev.getActionMasked()) {
                        case MotionEvent.ACTION_DOWN:
                            downX = ev.getX();
                            downY = ev.getY();
                            moved = false;
                            return true;                 // 先占住，UP 时再决定是链接还是双击
                        case MotionEvent.ACTION_MOVE:
                            if (Math.abs(ev.getX() - downX) > dp(8)
                                    || Math.abs(ev.getY() - downY) > dp(8)) moved = true;
                            return false;                // 拖动交回给外面的滚动容器
                        case MotionEvent.ACTION_CANCEL:
                            moved = true;
                            return false;
                        case MotionEvent.ACTION_UP:
                            if (moved) return false;
                            if (canFold) onBubbleTap(msg);
                            return true;
                        default:
                            return false;
                    }
                }
            });
        }
        if (canFold) {
            TextView tip = new TextView(this);
            tip.setText("双击展开");
            tip.setTextSize(chatTinySize());
            tip.setTextColor(C_SUB);
            tip.setGravity(Gravity.CENTER);
            tip.setVisibility(msg.folded ? View.VISIBLE : View.GONE);
            LinearLayout.LayoutParams tlp = wrap();
            tlp.topMargin = dp(3);
            box.addView(tip, tlp);
            chatFoldTips.put(msg, tip);
        }

        // 相关知识点：模型标过的 / 末尾关键词匹配到的 / 正文里出现的，最多 3 个，点一下跳过去。
        // 折叠时一起收起，免得折叠了还占着几行。
        if (!user && !msg.streaming && msg.err == null
                && msg.related != null && !msg.related.isEmpty()) {
            LinearLayout relBox = new LinearLayout(this);
            relBox.setOrientation(LinearLayout.VERTICAL);
            relBox.setVisibility(msg.folded ? View.GONE : View.VISIBLE);
            TextView rt = new TextView(this);
            rt.setText("相关知识点");
            rt.setTextSize(chatTinySize());
            rt.setTextColor(C_SUB);
            relBox.addView(rt, wrap());
            for (final Node n : msg.related) {
                TextView row = new TextView(this);
                row.setText("› " + n.name);
                row.setTextSize(chatBarSize());
                row.setTextColor(C_PRIMARY);
                row.setPadding(dp(2), dp(3), dp(2), dp(3));
                row.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        vibrate();
                        openNodeFrom(n, S_AI_CHAT);
                    }
                });
                relBox.addView(row, wrap());
            }
            LinearLayout.LayoutParams rlp2 = wrap();
            rlp2.topMargin = dp(6);
            box.addView(relBox, rlp2);
            chatRelatedBoxes.put(msg, relBox);
        }

        if (msg.truncated && !user && msg.err == null) {
            TextView more = new TextView(this);
            more.setText("继续");
            more.setTextSize(chatBarSize());
            more.setTypeface(Typeface.DEFAULT_BOLD);
            more.setTextColor(C_PRIMARY);
            more.setGravity(Gravity.CENTER);
            more.setPadding(dp(8), dp(6), dp(8), dp(6));
            more.setBackgroundDrawable(clickBg(C_PILL));
            more.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    vibrate();
                    chatContinue();
                }
            });
            LinearLayout.LayoutParams mlp2 = wrap();
            mlp2.topMargin = dp(5);
            box.addView(more, mlp2);
        }

        if (msg.err != null && !user) {
            TextView retry = new TextView(this);
            retry.setText("重试");
            retry.setTextSize(chatBarSize());
            retry.setTypeface(Typeface.DEFAULT_BOLD);
            retry.setTextColor(C_PRIMARY);
            retry.setGravity(Gravity.CENTER);
            retry.setPadding(dp(8), dp(6), dp(8), dp(6));
            retry.setBackgroundDrawable(clickBg(C_PILL));
            retry.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    vibrate();
                    chatRetry(msg);
                }
            });
            LinearLayout.LayoutParams tlp = wrap();
            tlp.topMargin = dp(5);
            box.addView(retry, tlp);
        }
        return box;
    }

    private void scrollChatToBottom(boolean smooth) {
        if (aiChatScroll == null) return;
        final boolean s = smooth;
        aiChatScroll.post(new Runnable() {
            public void run() {
                if (!alive() || aiChatScroll == null) return;
                // 底部有一段占位，滚到底会空出一截；减掉它，最后一条正好停在输入条上方
                int h = Math.max(0, aiChatInner.getHeight() - overDist());
                if (s) aiChatScroll.smoothScrollTo(0, h);
                else aiChatScroll.scrollTo(0, h);
            }
        });
    }

    private void updateChatBar() {
        if (aiChatInputBtn == null) return;
        // 一个按钮两种身份：平时「发消息」（主色实底），生成中「取消」（灰底橙字）
        if (chatBusy) {
            aiChatInputBtn.setText("取消");
            aiChatInputBtn.setTextColor(0xFFFFB74D);
            aiChatInputBtn.setTypeface(Typeface.DEFAULT);
            aiChatInputBtn.setBackgroundDrawable(clickBg(C_PILL));
        } else {
            aiChatInputBtn.setText("发消息");
            aiChatInputBtn.setTextColor(C_PRIMARY);
            aiChatInputBtn.setTypeface(Typeface.DEFAULT_BOLD);
            aiChatInputBtn.setBackgroundDrawable(clickBg(0xFF14384A));
        }
    }

    /** 输入弹窗：多行，输入法能换行；写完点「发送」。 */
    private void chatAskInput() {
        if (chatBusy) {
            toast("还在回复，先点停止");
            return;
        }
        if (!aiApiReady()) {
            toast(aiApiMissing());
            return;
        }
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(13), dp(12), dp(13), dp(8));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF151515);
        bg.setCornerRadius(dp(16));
        box.setBackgroundDrawable(bg);

        TextView t = new TextView(this);
        t.setText("问点什么");
        t.setTextSize(chatBarSize());
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(Color.WHITE);
        t.setGravity(Gravity.CENTER);
        box.addView(t, wrap());

        final EditText ed = new EditText(this);
        ed.setHint("输入问题");
        ed.setTextSize(chatBodySize());
        ed.setTextColor(Color.WHITE);
        ed.setHintTextColor(C_SUB);
        ed.setBackgroundDrawable(pill(C_PILL));
        ed.setPadding(dp(10), dp(8), dp(10), dp(8));
        ed.setGravity(Gravity.TOP | Gravity.START);
        ed.setMinLines(2);
        ed.setMaxLines(4);
        ed.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(m.round ? 66 : 84));
        elp.topMargin = dp(6);
        box.addView(ed, elp);

        final AlertDialog d = new AlertDialog.Builder(this).setView(box).create();

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        TextView send = new TextView(this);
        send.setText("发送");
        send.setTextSize(m.backSize);
        send.setTypeface(Typeface.DEFAULT_BOLD);
        send.setGravity(Gravity.CENTER);
        send.setTextColor(C_PRIMARY);
        send.setPadding(dp(10), dp(11), dp(10), dp(11));
        send.setBackgroundDrawable(pill(0xFF14384A));
        bar.addView(send, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 2f));
        TextView cancel = new TextView(this);
        cancel.setText("取消");
        cancel.setTextSize(m.subtitleSize - 1f);
        cancel.setGravity(Gravity.CENTER);
        cancel.setTextColor(C_SUB);
        cancel.setPadding(dp(8), dp(11), dp(8), dp(11));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        clp.leftMargin = dp(6);
        bar.addView(cancel, clp);
        LinearLayout.LayoutParams blp = wrap();
        blp.topMargin = dp(6);
        box.addView(bar, blp);

        send.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                vibrate();
                String q = ed.getText().toString().trim();
                if (q.length() == 0) {
                    toast("还没写内容");
                    return;
                }
                try { d.dismiss(); } catch (Throwable ignored) { }
                chatSend(q);
            }
        });
        cancel.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                vibrate();
                try { d.dismiss(); } catch (Throwable ignored) { }
            }
        });

        tuneDialogWindow(d);
        tuneDialogInput(d);
        d.show();
        sizeDialog(d, 0.86f);
    }

    /** 对话页顶部的模型按钮：只从已保存的列表里切，改名字统一去设置页。 */
    private void pickChatModel() {
        if (!aiApiModels.isEmpty()) {
            showApiModelPicker(new Runnable() {
                public void run() { renderAiChat(); }
            }, false);
            return;
        }
        toast("还没有模型列表，去「问 AI 设置 → API AI 设置」拉取或手填");
    }

    private void chatSend(String q) {
        if (chatBusy) {
            toast("还在回复，先点停止");
            return;
        }
        if (!aiApiReady()) {
            toast(aiApiMissing());
            return;
        }
        ChatMsg u = new ChatMsg("user");
        u.content = q;
        chatMsgs.add(u);
        ChatMsg a = new ChatMsg("assistant");
        a.streaming = true;
        chatMsgs.add(a);
        renderAiChat();
        startChatStream();
    }

    /** 被长度截断后接着写：上一轮的正文已经在上下文里，让模型从断点往下写。 */
    private void chatContinue() {
        if (chatBusy) {
            toast("还在回复，先点停止");
            return;
        }
        for (int i = chatMsgs.size() - 1; i >= 0; i--) {
            if ("assistant".equals(chatMsgs.get(i).role)) {
                chatMsgs.get(i).truncated = false;
                break;
            }
        }
        ChatMsg u = new ChatMsg("user");
        u.content = "继续，接着上面断掉的地方往下写，不要重复已经写过的内容。";
        chatMsgs.add(u);
        ChatMsg a = new ChatMsg("assistant");
        a.streaming = true;
        chatMsgs.add(a);
        chatDirty = true;
        renderAiChat();
        startChatStream();
    }

    private void chatRetry(ChatMsg failed) {
        if (chatBusy) {
            toast("还在回复，先点停止");
            return;
        }
        int i = chatMsgs.indexOf(failed);
        if (i >= 0) chatMsgs.remove(i);
        ChatMsg a = new ChatMsg("assistant");
        a.streaming = true;
        chatMsgs.add(a);
        chatDirty = true;
        renderAiChat();
        startChatStream();
    }

    /** 组上下文：一条 system（约束简短）+ 最近 N 轮问答；没说完的空消息不算。 */
    private JSONArray chatContext(int rounds) {
        JSONArray arr = new JSONArray();
        try {
            JSONObject sys = new JSONObject();
            sys.put("role", "system");
            sys.put("content", CHAT_SYSTEM_PROMPT);
            arr.put(sys);
        } catch (Throwable ignored) { }
        List<ChatMsg> hist = new ArrayList<ChatMsg>();
        for (int i = 0; i < chatMsgs.size(); i++) {
            ChatMsg m = chatMsgs.get(i);
            if (m.content != null && m.content.length() > 0) hist.add(m);
        }
        int max = Math.max(1, rounds) * 2;
        int from = Math.max(0, hist.size() - max);
        for (int i = from; i < hist.size(); i++) {
            ChatMsg m = hist.get(i);
            try {
                JSONObject o = new JSONObject();
                o.put("role", m.role);
                o.put("content", m.content);
                arr.put(o);
            } catch (Throwable ignored) { }
        }
        return arr;
    }

    private void startChatStream() {
        chatBusy = true;
        chatStopFlag = false;
        updateChatBar();
        final String model = aiApiModel;
        final float temp = API_TEMPS[aiApiTempIdx];
        final int maxTok = aiApiMax;
        final int think = aiApiThinkIdx;
        final JSONArray ctx = chatContext(aiApiCtx);
        final ChatMsg target = chatMsgs.get(chatMsgs.size() - 1);
        Thread t = new Thread(new Runnable() {
            public void run() {
                chatRun(model, temp, maxTok, think, ctx, target);
            }
        });
        t.setDaemon(true);
        t.start();
    }

    /** 请求体分三档：全参数 → 去掉思考强度 → 只留模型和消息。
     *  有些网关见到不认识的参数会直接 400，逐档降级才不会因为一个可选参数把对话卡死。 */
    private JSONObject chatBody(String model, JSONArray ctx, float temp, int maxTok,
                                int think, int level) {
        JSONObject b = new JSONObject();
        try {
            b.put("model", model);
            b.put("messages", ctx);
            b.put("stream", true);
            if (level < 2) {
                b.put("max_tokens", maxTok);
                b.put("temperature", temp);
            }
            if (level == 0 && think > 0) {
                b.put("reasoning_effort", think == 1 ? "medium" : "high");
            }
        } catch (Throwable ignored) { }
        return b;
    }

    private void chatRun(String model, float temp, int maxTok, int think,
                         JSONArray ctx, ChatMsg target) {
        for (int level = 0; level < 3; level++) {
            if (chatStopFlag) return;
            JSONObject body = chatBody(model, ctx, temp, maxTok, think, level);
            HttpURLConnection c = null;
            int code = 0;
            String errBody = "";
            try {
                c = (HttpURLConnection) new URL(apiUrl("/chat/completions")).openConnection();
                c.setConnectTimeout(15000);
                c.setReadTimeout(120000);          // 推理模型可能要想很久，读超时给足
                c.setRequestMethod("POST");
                c.setInstanceFollowRedirects(false);   // 不跟跳转，免得密钥被带到别的域
                c.setDoOutput(true);
                c.setRequestProperty("Authorization", "Bearer " + aiApiSecret);
                c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                c.setRequestProperty("Accept", "text/event-stream");
                c.setRequestProperty("User-Agent", "HZKnowledge-Android");
                byte[] data = body.toString().getBytes(StandardCharsets.UTF_8);
                c.setFixedLengthStreamingMode(data.length);
                OutputStream os = c.getOutputStream();
                os.write(data);
                os.flush();
                os.close();
                chatConn = c;
                code = c.getResponseCode();
                if (code == 200) {
                    // 重试前先清掉上一轮的内容，免得拼出半截重复
                    target.content = "";
                    target.reasoning = "";
                    target.finish = "";
                    target.truncated = false;
                    boolean got = chatPump(c, target);
                    if (chatStopFlag) { chatDone(target); return; }
                    if (target.err != null) { chatFail(target, target.err); return; }
                    if (!got) {
                        // 空回复：记一笔，然后换更保守的参数再试一次（去掉思考强度/温度）
                        noteDiag("空回复 level=" + level + " model=" + model
                                + " finish=" + target.finish + " chunks=" + target.chunks);
                        if (level < 2) continue;
                        chatFail(target, "接口这次没返回内容，点「重试」");
                        return;
                    }
                    chatDone(target);
                    return;
                }
                errBody = readAllText(c.getErrorStream());
            } catch (java.net.SocketTimeoutException e) {
                if (chatStopFlag) chatDone(target);
                else chatFail(target, "等回复超时了，可以重试或换个模型");
                return;
            } catch (Throwable e) {
                if (chatStopFlag) chatDone(target);
                else chatFail(target, "连不上：" + (e == null ? "未知原因"
                        : e.getClass().getSimpleName()));
                return;
            } finally {
                chatConn = null;
                if (c != null) {
                    try { c.disconnect(); } catch (Throwable ignored) { }
                }
            }
            if (code == 400 && level < 2) continue;   // 参数不被支持：降级重试
            chatFail(target, apiErrText(code, errBody));
            return;
        }
        chatFail(target, "没有拿到回复");
    }

    /** 读 SSE 并填充 target，返回**是否拿到了正文**；出错时把原因写进 target.err，
     *  收尾（chatDone/chatFail）交给调用方，好在「空回复」时先重试一次。 */
    private boolean chatPump(HttpURLConnection c, ChatMsg target) {
        BufferedReader br = null;
        try {
            br = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8));
            String line;
            boolean sawDone = false;
            int chunks = 0;
            while (!chatStopFlag && (line = br.readLine()) != null) {
                String s = line.trim();
                if (s.length() == 0) continue;
                if (!s.startsWith("data:")) {
                    if (s.startsWith("{")) {   // 不支持流式：整段当普通回复解析
                        chatParseWhole(s, target);
                        break;
                    }
                    continue;
                }
                String d = s.substring(5).trim();
                if ("[DONE]".equals(d)) { sawDone = true; break; }
                try {
                    JSONObject o = new JSONObject(d);
                    JSONArray ch = o.optJSONArray("choices");
                    if (ch == null || ch.length() == 0) continue;
                    JSONObject c0 = ch.optJSONObject(0);
                    if (c0 == null) continue;
                    // 结束原因：length = 被 max_tokens 截断，stop = 正常说完
                    String fr = jsonStr(c0, "finish_reason");
                    if (fr.length() > 0) target.finish = fr;
                    JSONObject delta = c0.optJSONObject("delta");
                    if (delta == null) delta = c0.optJSONObject("message");
                    if (delta == null) continue;
                    String think = jsonStr(delta, "reasoning_content");
                    String piece = jsonStr(delta, "content");
                    // 思考内容留个上限，别把内存堆满
                    if (think.length() > 0 && target.reasoning.length() < 4000) {
                        target.reasoning += think;
                    }
                    if (piece.length() > 0) {
                        target.content += piece;
                        chunks++;
                    }
                    chatPush(target, false);
                } catch (Throwable ignored) { }
            }
            target.chunks = chunks;
            if (chatStopFlag && target.content.length() > 0
                    && target.content.indexOf("（已停止）") < 0) {
                target.content += "\n（已停止）";
            } else if (!chatStopFlag && target.content.length() > 0) {
                // 被 max_tokens 截断，或连接没走到 [DONE] 就断了：都标成可继续
                if ("length".equals(target.finish) || !sawDone) target.truncated = true;
            }
            return target.content.length() > 0;
        } catch (Throwable t) {
            if (!chatStopFlag) {
                target.err = "读取回复失败：" + (t == null ? "未知原因"
                        : t.getClass().getSimpleName());
            }
            target.chunks = target.content.length() > 0 ? 1 : 0;
            return target.content.length() > 0;
        } finally {
            try { if (br != null) br.close(); } catch (Throwable ignored) { }
        }
    }

    private void chatParseWhole(String json, ChatMsg target) {
        try {
            JSONArray ch = new JSONObject(json).optJSONArray("choices");
            JSONObject c0 = ch == null || ch.length() == 0 ? null : ch.optJSONObject(0);
            JSONObject msg = c0 == null ? null : c0.optJSONObject("message");
            if (msg != null) {
                target.reasoning = jsonStr(msg, "reasoning_content");
                target.content = jsonStr(msg, "content");
            }
            String fr = jsonStr(c0, "finish_reason");
            if ("length".equals(fr)) target.truncated = true;
            if (msg != null && target.content.length() > 0) target.chunks = 1;
        } catch (Throwable ignored) { }
    }

    /** 流式刷新：80ms 一次，主线程只改文本，不重建列表。 */
    private void chatPush(final ChatMsg msg, boolean force) {
        long now = SystemClock.uptimeMillis();
        if (!force && now - chatUiAt < 150L) return;
        chatUiAt = now;
        runOnUiThread(new Runnable() {
            public void run() {
                if (!alive() || screen != S_AI_CHAT) return;
                if (chatStreamTv != null) {
                    chatStreamTv.setText(msg.content.length() == 0
                            ? "…" : safeMarkup(msg.content + "▍"));
                }
                if (chatStreamReasonTv != null && chatShowThink && msg.reasoning.length() > 0) {
                    chatStreamReasonTv.setText("思考：" + prettyReasoning(msg.reasoning));
                    chatStreamReasonTv.setVisibility(View.VISIBLE);
                }
                if (chatThinkingTv != null) {
                    // 关掉思考时的「思考中…」：正文一出现就撤掉
                    chatThinkingTv.setVisibility(msg.content.length() == 0 ? View.VISIBLE : View.GONE);
                }
                scrollChatToBottom(false);   // 流式跟随时直接跳到底，别每 80ms 起一次平滑动画
            }
        });
    }

    /** 这条回答对应的提问（往前找最近一条带内容的 user 消息），兜底匹配用。 */
    private String lastUserQuestion(ChatMsg msg) {
        int i = chatMsgs.indexOf(msg);
        for (int j = (i < 0 ? chatMsgs.size() : i) - 1; j >= 0; j--) {
            ChatMsg m = chatMsgs.get(j);
            if ("user".equals(m.role) && m.content != null && m.content.length() > 0) return m.content;
        }
        return "";
    }

    /** 答完之后算一次「相关知识点」，存到消息上，渲染时直接用（后台线程，不卡界面）。 */
    private void computeRelated(final ChatMsg msg) {
        if (msg == null || "user".equals(msg.role) || msg.err != null) return;
        final String raw = msg.content == null ? "" : msg.content;
        if (raw.length() < 4) return;
        List<Node> rel = new ArrayList<Node>();

        // ① 末尾【相关:…】关键词，按重合度排
        List<Node> ranked = rankKnowledge(parseRelatedKeys(raw), REL_LIMIT);
        for (Node n : ranked) {
            if (rel.size() >= REL_LIMIT) break;
            if (!rel.contains(n)) rel.add(n);
        }

        // ② 没有关键词时兜底：看「提问 + 回答」里直接出现了哪些标题
        if (rel.size() < REL_LIMIT) {
            String text = lastUserQuestion(msg) + "\n" + stripRelatedTag(raw);
            for (Node n : rankKnowledgeByText(text, REL_LIMIT)) {
                if (rel.size() >= REL_LIMIT) break;
                if (!rel.contains(n)) rel.add(n);
            }
        }
        msg.related = rel;
    }

    private void chatDone(final ChatMsg msg) {
        msg.streaming = false;
        chatBusy = false;
        chatStopFlag = false;
        try { computeRelated(msg); } catch (Throwable ignored) { }
        saveChatHistory();
        runOnUiThread(new Runnable() {
            public void run() {
                updateChatBar();
                if (!alive()) return;
                if (chatMsgs.indexOf(msg) < 0) return;   // 已被清空
                chatDirty = true;
                if (screen == S_AI_CHAT) renderAiChat();
                if (msg.truncated) toast("回复没写完，点「继续」接着写");
            }
        });
    }

    private void chatFail(final ChatMsg msg, final String text) {
        msg.err = text;
        msg.streaming = false;
        chatBusy = false;
        chatStopFlag = false;
        saveChatHistory();
        runOnUiThread(new Runnable() {
            public void run() {
                updateChatBar();
                if (!alive()) return;
                if (chatMsgs.indexOf(msg) < 0) return;   // 已被清空
                chatDirty = true;
                if (screen == S_AI_CHAT) renderAiChat();
                toast(text);
            }
        });
    }

    private void chatStop() {
        if (!chatBusy) return;
        vibrate();
        chatStopFlag = true;
        HttpURLConnection c = chatConn;
        if (c != null) {
            try { c.disconnect(); } catch (Throwable ignored) { }
        }
        toast("已停止");
    }

    /** 对话落盘的位置。API 模式的对话原来只在内存，手表进程被回收后重进就全空了。 */
    private File chatStoreFile() {
        return new File(getFilesDir(), "chat_api.json");
    }

    /** 存最近 100 条（半截的流不存）。切后台被系统杀掉后回来还能看到。 */
    private void saveChatHistory() {
        final JSONArray arr = new JSONArray();
        int from = Math.max(0, chatMsgs.size() - 100);
        for (int i = from; i < chatMsgs.size(); i++) {
            ChatMsg m = chatMsgs.get(i);
            if (m.streaming) continue;
            try {
                JSONObject o = new JSONObject();
                o.put("r", m.role);
                o.put("c", m.content);
                o.put("t", m.reasoning);
                o.put("f", m.folded);
                o.put("x", m.truncated);
                if (m.err != null) o.put("e", m.err);
                arr.put(o);
            } catch (Exception ignored) { }
        }
        new Thread(new Runnable() {
            public void run() {
                try {
                    java.io.FileOutputStream fo = new java.io.FileOutputStream(chatStoreFile());
                    fo.write(arr.toString().getBytes("UTF-8"));
                    fo.flush();
                    fo.close();
                } catch (Exception ignored) { }
            }
        }).start();
    }

    /** 进对话页时恢复；内存里已有内容就不覆盖。 */
    private void loadChatHistory() {
        if (!chatMsgs.isEmpty()) return;
        File f = chatStoreFile();
        if (!f.exists() || f.length() < 2) return;
        try {
            byte[] b = new byte[(int) f.length()];
            java.io.FileInputStream fi = new java.io.FileInputStream(f);
            try { fi.read(b); } finally { fi.close(); }
            JSONArray arr = new JSONArray(new String(b, "UTF-8"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                ChatMsg m = new ChatMsg(o.optString("r", "user"));
                m.content = o.optString("c", "");
                m.reasoning = o.optString("t", "");
                m.folded = o.optBoolean("f", false);
                m.truncated = o.optBoolean("x", false);
                if (o.has("e")) m.err = o.optString("e", null);
                chatMsgs.add(m);
            }
        } catch (Exception ignored) { }
    }

    private void chatReset() {
        chatStopFlag = true;
        HttpURLConnection c = chatConn;
        if (c != null) {
            try { c.disconnect(); } catch (Throwable ignored) { }
        }
        chatMsgs.clear();
        chatBusy = false;
        chatStopFlag = false;
        chatDirty = true;
        saveChatHistory();      // 存一份空的，别让下次进来又冒出旧对话
        renderAiChat();
        toast("已清空");
    }

    private String apiErrText(int code, String body) {
        ApiRes r = new ApiRes();
        r.code = code;
        r.body = body;
        return apiErrText(r);
    }

    private void buildAiWebCfgScreen() {
        aiWebCfgScroll = new OverScrollScrollView(this);
        aiWebCfgScroll.setVerticalScrollBarEnabled(false);
        roundPadScroll(aiWebCfgScroll);
        aiWebCfgInner = new LinearLayout(this);
        aiWebCfgInner.setOrientation(LinearLayout.VERTICAL);
        aiWebCfgScroll.addView(aiWebCfgInner);
        aiWebCfgScreen.addView(aiWebCfgScroll, lw());
        renderAiWebCfg();
    }

    private void renderAiWebCfg() {
        int keepY = (aiWebCfgInner.getChildCount() > 0) ? aiWebCfgScroll.getScrollY() : -1;
        aiWebCfgInner.removeAllViews();
        aiWebCfgInner.addView(makeBack(aiCfgBack == S_SUBJECT ? "‹ 返回主页" : "‹ 返回问 AI",
                new View.OnClickListener() {
            public void onClick(View v) { goBack(aiCfgBack); }
        }), wrapM());
        aiWebCfgInner.addView(pageTitle("DeepSeek 网页版设置"), wrap());

        aiWebCfgInner.addView(sectionHeader("运行环境"), wrap());
        TextView warn = aiHint("注意：没有 WebView 组件就无法使用 DeepSeek 网页版（默认手表已带 WebView）。\n"
                + "本机检测：" + (aiHasWebView() ? "已检测到 WebView" : "未检测到 WebView 组件"));
        warn.setTypeface(Typeface.DEFAULT_BOLD);
        warn.setTextColor(0xFFFFB74D);
        warn.setLineSpacing(dp(2), 1.3f);
        aiWebCfgInner.addView(warn, wrap());

        aiWebCfgInner.addView(sectionHeader("网页显示"), wrap());
        aiWebCfgInner.addView(aiSettingBtn("网页缩放：" + aiWebZoomLabel(), new View.OnClickListener() {
            public void onClick(View v) {
                aiWebZoomMode = (aiWebZoomMode + 1) % 3;
                prefs.edit().putInt("ai_web_zoom", aiWebZoomMode).apply();
                applyAiWebZoom();
                renderAiWebCfg();
            }
        }), wrap());
        aiWebCfgInner.addView(aiHint("默认「自动」：按屏幕宽度把网页整体缩小，登录框、验证码和侧边栏都能完整显示。点一下可切「更小」或「原样」，切换不重载网页。"), wrap());
        aiWebCfgInner.addView(aiHint(aiTwoGestureEnabled()
                ? "手势：双指轻点两下切「裁切版 / 原版」（四边避开圆屏 / 网页铺满整屏）；双指长按 2 秒退出问 AI。当前：" + (aiWebCrop ? "裁切版" : "原版") + "。网页页没有返回和刷新按钮，系统返回键照常可用；网页里的长按复制、自动填充已关掉。"
                : "方屏不用裁切，网页铺满整屏；退出用系统返回键。网页里没有返回和刷新按钮，长按复制、自动填充已关掉。"), wrap());
        aiWebCfgInner.addView(toggleRow("隐藏网页里的下载 APP 入口", aiWebHideDl,
                new View.OnClickListener() {
            public void onClick(View v) {
                aiWebHideDl = !aiWebHideDl;
                prefs.edit().putBoolean("ai_web_hide_dl", aiWebHideDl).apply();
                renderAiWebCfg();
                // 关掉后要让上一版脚本隐藏过的元素回来，只能重载网页
                if (aiWebView != null) {
                    if (aiWebHideDl) aiWebInject(aiWebView);
                    else aiWebView.reload();
                }
            }
        }), wrap());
        aiWebCfgInner.addView(aiHint("关掉后 App 不往网页注入任何脚本，网页完全按原样显示。网页打开就黑屏时，先关掉它做对照。"), wrap());

        aiWebCfgInner.addView(toggleRow("网页跟随暗色", aiWebDark,
                new View.OnClickListener() {
            public void onClick(View v) {
                aiWebDark = !aiWebDark;
                prefs.edit().putBoolean("ai_web_dark", aiWebDark).apply();
                // 夜间模式是建 WebView 时定下来的，改开关必须重建网页视图才生效
                try { buildAiWebScreen(); screenBuilt[S_AI_WEB] = true; } catch (Throwable ignored) { }
                renderAiWebCfg();
                toast(aiWebDark ? "已切到暗色，重新进入问 AI 生效" : "已恢复网页原样");
            }
        }), wrap());
        aiWebCfgInner.addView(aiHint("让网页以为系统是深色模式，网站的「跟随系统」就会自动变暗，"
                + "和 App 风格一致。只改系统给网页的信号，不往网页注入脚本；关掉可做对照。"), wrap());

        aiWebCfgInner.addView(sectionHeader("登录"), wrap());
        aiWebCfgInner.addView(aiSettingBtn("重新登录（清除网页登录态）", new View.OnClickListener() {
            public void onClick(View v) { aiWebRelogin(); }
        }), wrap());
        aiWebCfgInner.addView(aiHint("登录由网页自己管理，App 不保存密钥。清除后下次打开要重新登录。"), wrap());

        padInner(aiWebCfgInner);
        if (keepY >= 0) scrollToY(aiWebCfgScroll, keepY);
        else scrollToTop(aiWebCfgScroll);
    }

    /** 第一次进 AI 界面时问用哪种方式：网页版开箱即用，API 要自己配地址和密钥。 */
    private void askAiModeFirstTime() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(13), dp(12), dp(13), dp(8));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF151515);
        bg.setCornerRadius(dp(16));
        box.setBackgroundDrawable(bg);

        TextView t = new TextView(this);
        t.setText("用哪种 AI？");
        t.setTextSize(m.subtitleSize);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(Color.WHITE);
        t.setGravity(Gravity.CENTER);
        box.addView(t, wrap());

        TextView webTip = new TextView(this);
        webTip.setText("网页版：打开 DeepSeek 网页，登录就能用，不用配置、不用花钱。\n更容易上手，普通用户建议选这个。");
        webTip.setTextSize(m.subtitleSize - 2f);
        webTip.setTextColor(C_TEXT);
        webTip.setLineSpacing(dp(2), 1.25f);
        webTip.setPadding(dp(6), dp(8), dp(6), dp(2));
        box.addView(webTip, wrap());

        TextView apiTip = new TextView(this);
        apiTip.setText("API 模式：自己填地址和密钥，按用量花 token 费用。\n"
                + "和本软件结合更紧：回答里会给出带下划线的知识点，点一下就跳过去。");
        apiTip.setTextSize(m.subtitleSize - 2f);
        apiTip.setTextColor(C_SUB);
        apiTip.setLineSpacing(dp(2), 1.25f);
        apiTip.setPadding(dp(6), dp(4), dp(6), dp(6));
        box.addView(apiTip, wrap());

        final AlertDialog d = new AlertDialog.Builder(this).setView(box).create();

        TextView web = new TextView(this);
        web.setText("用网页版（推荐）");
        web.setTextSize(m.backSize);
        web.setTypeface(Typeface.DEFAULT_BOLD);
        web.setGravity(Gravity.CENTER);
        web.setTextColor(C_PRIMARY);
        web.setPadding(dp(10), dp(11), dp(10), dp(11));
        web.setBackgroundDrawable(pill(0xFF14384A));
        LinearLayout.LayoutParams wlp = wrap();
        wlp.topMargin = dp(4);
        box.addView(web, wlp);

        TextView api = new TextView(this);
        api.setText("用 API 模式");
        api.setTextSize(m.subtitleSize - 1f);
        api.setGravity(Gravity.CENTER);
        api.setTextColor(C_SUB);
        api.setPadding(dp(10), dp(10), dp(10), dp(10));
        api.setBackgroundDrawable(pill(C_PILL));
        LinearLayout.LayoutParams alp = wrap();
        alp.topMargin = dp(6);
        box.addView(api, alp);

        TextView later = new TextView(this);
        later.setText("以后再说");
        later.setTextSize(m.subtitleSize - 2f);
        later.setGravity(Gravity.CENTER);
        later.setTextColor(C_SUB);
        later.setPadding(dp(8), dp(9), dp(8), dp(2));
        box.addView(later, wrap());

        web.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                vibrate();
                aiMode = "web";
                prefs.edit().putString("ai_mode", aiMode)
                        .putBoolean("ai_choice_done", true).apply();
                try { d.dismiss(); } catch (Throwable ignored) { }
                openAiFromHome();
            }
        });
        api.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                vibrate();
                aiMode = "api";
                prefs.edit().putString("ai_mode", aiMode)
                        .putBoolean("ai_choice_done", true).apply();
                try { d.dismiss(); } catch (Throwable ignored) { }
                openAiFromHome();
                toast(aiApiReady() ? "已经是配好的接口" : "先在这页填地址和密钥");
            }
        });
        later.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                vibrate();
                // 不选就按网页版走，别再每次进来都问
                prefs.edit().putBoolean("ai_choice_done", true).apply();
                try { d.dismiss(); } catch (Throwable ignored) { }
                openAiFromHome();
            }
        });

        tuneDialogWindow(d);
        d.show();
        sizeDialog(d, 0.86f);
    }

    /** 主页「问 AI」按当前模式分流；实时读 aiMode，切换后不用重绘主页列表也生效。 */
    private void openAiFromHome() {
        // 有 WebView 的设备，第一次进 AI 界面先问清楚用哪种方式（没有 WebView 的已经在启动时定好了）
        if (!aiNoWebView && !prefs.getBoolean("ai_choice_done", false)) {
            askAiModeFirstTime();
            return;
        }
        if (aiNoWebView && !"api".equals(aiMode)) {
            aiMode = "api";
            prefs.edit().putString("ai_mode", aiMode).apply();
            toast("本机没有 WebView，已自动改用 API 配置模式");
        }
        if ("api".equals(aiMode)) {
            aiChatBack = S_SUBJECT;
            aiCfgBack = S_SUBJECT;
            if (aiApiReady()) {
                showScreen(S_AI_CHAT);
            } else {
                showScreen(S_AI_API_CFG);
                toast(aiApiMissing());
            }
        } else {
            aiWebBack = S_SUBJECT;
            showScreen(S_AI_WEB);
        }
    }

    private LinearLayout newScreen() {
        LinearLayout ll = new LinearLayout(this);
        ll.setOrientation(LinearLayout.VERTICAL);
        ll.setBackgroundColor(C_BG);   // 每页自带黑底：右滑预览时两页各自独立，不会互相透出
        ll.setPadding(m.sidePad, 0, m.sidePad, 0);   // 上下不 pad，黑边由滚动容器内部的透明安全区承担
        return ll;
    }

    /** 圆屏给滚动容器加内部安全 padding（上下各 roundSafe）：内容滚到顶/底时停在圆弧安全区，
     *  中间滚动时内容可穿过 padding（clipToPadding=false），不会出现固定黑边。方屏不加。 */
    private void roundPadScroll(ScrollView sc) {
        if (m == null || !m.round) return;
        if (sc instanceof OverScrollScrollView) {
            ((OverScrollScrollView) sc).setMaxOver(overDist());
        }
    }

    /** 圆屏占位距离：25% 屏高，方屏 0。 */
    private int overDist() {
        return (m != null && m.round)
                ? (int) (getResources().getDisplayMetrics().heightPixels * 0.25f) : 0;
    }

    private View makeScrollSpacer() {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, overDist()));
        return v;
    }

    /** 正文页（关于 / 更新日志 / 反馈 / 个人主页 / 显示调节）也和列表一样加上下占位，
     *  否则这些页没有上下留白，首尾内容划不到屏幕中间。 */
    private void padInner(LinearLayout inner) {
        if (overDist() <= 0) return;
        inner.addView(makeScrollSpacer(), 0);
        inner.addView(makeScrollSpacer());
    }

    /** 正文页进入时定位：跳过顶部占位，让第一个内容贴最顶端。 */
    private void scrollToTop(final ScrollView sc) {
        scrollToY(sc, overDist());
    }

    /** 页面 → 它的滚动容器（只有普通 ScrollView 页；列表页由 SvList 自己管）。 */
    private ScrollView scrollOf(int s) {
        switch (s) {
            case S_DETAIL: return detailScroll;
            case S_EXTRA: return extraScroll;
            case S_SETTINGS: return settingsScroll;
            case S_ORDER: return orderScroll;
            case S_AI_HUB: return aiHubScroll;
            case S_AI_API_CFG: return aiApiScroll;
            case S_AI_WEB_CFG: return aiWebCfgScroll;
            case S_ABOUT: return aboutScroll;
            case S_DISPLAY: return displayScroll;
            case S_CHANGELOG: return changelogScroll;
            case S_FEEDBACK: return feedbackScroll;
            case S_PROFILE: return profScroll;
            case S_WORD_HOME: return wordHomeScroll;
            case S_WORD_STUDY: return wordStudyScroll;
            case S_WORD_SEARCH: return wordSearchScroll;
            case S_WORD_SETTINGS: return wordSettingScroll;
            case S_WORD_VOCAB: return wordVocabScroll;
            default: return null;
        }
    }

    /** 滚到指定位置。内容分帧渲染时可能还没铺够高，scrollTo 会被钳制到半路，
     *  所以没到位就下一帧再定一次（最多 5 次）；期间用户自己滑过（位置已超过目标）就不再动。
     *  重要：绝不用 OnPreDraw / OnGlobalLayout 这类「每帧 / 每布局」监听器来定位——
     *  在圆形手表上会启动即崩溃（v3.4.2、v3.4.4 两次翻车都是这个根子：圆屏 overDist≠0 才走这条
     *  注册监听器的路径，方屏 overDist=0 直接返回不注册，所以只有圆屏崩）。post 延迟到当前帧绘制
     *  之后执行，稳定、不崩，圆屏方屏通用。 */
    private void scrollToY(final ScrollView sc, final int y) {
        if (sc == null) return;
        final int target = Math.max(0, y);
        final int[] tries = new int[]{0};
        final Runnable r = new Runnable() {
            public void run() {
                sc.scrollTo(0, target);
                View child = sc.getChildAt(0);
                int range = (child == null ? 0 : child.getHeight()) - sc.getHeight();
                // 内容还没铺够时 scrollTo 会被钳制，这时不能算完成，等下一批行 / 下一帧再补
                if (tries[0]++ < 5 && target > 0 && sc.getScrollY() < target && range >= target) sc.post(this);
            }
        };
        sc.post(r);
    }

    /** 行数据源：只告诉列表「一共几行、第几行长什么样」，行由列表按需造，不一次全建。 */
    private interface RowSource {
        int size();
        View make(int index);
    }

    /** 列表容器：ScrollView + 竖排 LinearLayout，行就是普通 View（不再用 ListView）。
     *  · 回弹走系统 overScrollBy / springBack，手感与设置页、详情页一致；
     *  · 圆屏首尾各加一段透明占位，首尾内容能滑到屏幕中间；
     *  · 大列表分页：首屏 30 行，滚到还剩约一屏时续载下一批 40 行，每帧只追加 15 行，滑动不顿。 */
    private class SvList {
        // 首屏 12 行（手表一屏约五六行，够铺两屏）、每次续载 24 行、每帧最多插 6 行。
        // 行是复杂 View（多 span + 卡片），一帧塞太多会掉帧；6 行基本在 16ms 内。
        private final int first = 12, page = 24, chunk = 6;
        final OverScrollScrollView sc;
        final LinearLayout box;
        private final List<View> heads = new ArrayList<View>();
        private final List<View> rows = new ArrayList<View>();
        private final View topSp, botSp;
        private RowSource src;
        private int shown, want;
        private boolean busy;
        private int gen, stepGen;
        int savedY = -1;
        /** 待定位的位置：行是分帧加的，内容高度一变就要再定一次，直到用户自己滑。 */
        private int pendingY = -1;
        private boolean selfScroll;
        /** 右滑预览换层时置 true：这一批首屏行同步渲染完再露出预览，避免空列表再分帧加载闪一下。 */
        private boolean syncFirstRender = false;
        /** 续载时先让外部把下一批数据补进来（数据全集不是一次就有的列表用，如「今日新增」）。 */
        Runnable fill;
        /** 首屏渲染完 + 定位到位后回调一次（showScreen 用它来显示，做到「渲染完成后再打开」）。 */
        Runnable onReady;
        /** 已渲染行 + 滚动位置的缓存：key = 数据标识（如当前层路径），返回上一层时复用、直接定位回去。 */
        private class RowCache {
            final List<View> rows = new ArrayList<View>();
            int scrollY = -1;
        }
        private final java.util.Map<String, RowCache> rowCache = new java.util.HashMap<String, RowCache>();
        private String curKey = "";

        SvList() {
            sc = new OverScrollScrollView(MainActivity.this);
            sc.setVerticalScrollBarEnabled(false);
            sc.setMaxOver(overDist());
            box = new LinearLayout(MainActivity.this);
            box.setOrientation(LinearLayout.VERTICAL);
            sc.addView(box, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            topSp = makeScrollSpacer();
            box.addView(topSp);
            botSp = makeScrollSpacer();
            box.addView(botSp);
            sc.setScrollListener(new OverScrollScrollView.OnScrollListener() {
                public void onScroll(int y, int h, int contentH) {
                    // 不在这里清 pendingY：onScroll 是延后回调的，程序自己滚出来的事件也会走到这里，
                    // 一旦误清，后面新增行时就不再补定位，页面会停在顶部（返回定位失效就是这么来的）。
                    if (nearEnd()) more();
                }
            });
            // 只有「手指真的按下」才算用户接管，这时才放弃程序定位
            sc.setOnTouchListener(new View.OnTouchListener() {
                public boolean onTouch(View v, MotionEvent e) {
                    if (e.getActionMasked() == MotionEvent.ACTION_DOWN) pendingY = -1;
                    return false;                     // 不拦事件，滚动照常
                }
            });
        }

        /** 顶部占位之后、底部占位之前的固定头部（返回键、标题、搜索框），随内容一起滚动。
         *  和行一样留 6dp 底边距，否则返回键、搜索框会跟下面一行贴在一起。 */
        void addHead(View v) {
            heads.add(v);
            box.addView(v, box.indexOfChild(botSp), wrapM());
        }

        void bind(RowSource s) { bind(null, s); }

        /** 换一批数据：带缓存。key 相同直接复用已渲染的行 + 离开时的滚动位置（返回上一层快、定位正确），
         *  不同才清空重渲；只缓存首屏，续载出来的行不缓存。 */
        void bind(String key, RowSource s) {
            final String k = key == null ? "" : key;
            final boolean syncNow = syncFirstRender;
            syncFirstRender = false;
            // 先把当前显示的行和滚动位置挪进缓存，供返回时复用
            if (!curKey.isEmpty() && !rows.isEmpty()) {
                RowCache c = new RowCache();
                c.rows.addAll(rows);
                c.scrollY = sc.getScrollY();
                for (View v : rows) box.removeView(v);
                rowCache.put(curKey, c);
                if (rowCache.size() > 6) {   // 最多缓存 6 层，防止内存涨
                    String oldest = rowCache.keySet().iterator().next();
                    rowCache.remove(oldest);
                }
            }
            clearRows();
            src = s;
            shown = 0;
            gen++;
            curKey = k;

            // 命中缓存：复用已渲染的行，并记下该层的离开位置（restore 用）
            RowCache hit = rowCache.remove(k);
            if (hit != null && !hit.rows.isEmpty()) {
                for (View v : hit.rows) {
                    rows.add(v);
                    box.addView(v, box.getChildCount() - 1, wrapM());
                    shown++;
                }
                savedY = hit.scrollY;
                busy = false;
                sc.post(new Runnable() { public void run() { fitSpacers(); } });
                return;
            }

            // 未命中：新层。预览换层时同步把首屏铺满再露出，避免空列表闪一下；
            // 正常进入仍走分帧，避免一次塞太多行掉帧。
            savedY = -1;
            want = Math.min(s == null ? 0 : s.size(), first);
            if (want > 0 && syncNow) {
                busy = false;
                while (shown < want && shown < s.size()) {
                    View row = s.make(shown);
                    rows.add(row);
                    box.addView(row, box.getChildCount() - 1, wrapM());
                    shown++;
                }
                sc.post(new Runnable() {
                    public void run() {
                        fitSpacers();
                        fireReady();
                    }
                });
                return;
            }
            if (want > 0) {
                stepGen = gen;
                busy = true;
                sc.post(step);
            } else {
                busy = false;
                sc.post(new Runnable() {
                    public void run() {
                        fitSpacers();
                        fireReady();
                    }
                });
            }
        }

        private void start(int target) {
            if (src == null || busy) return;
            if (fill != null && target > src.size()) fill.run();
            want = Math.min(src.size(), target);
            if (want <= shown) return;
            stepGen = gen;
            busy = true;
            sc.post(step);
        }

        private void more() { start(shown + page); }

        private void clearRows() {
            for (View v : rows) box.removeView(v);
            rows.clear();
            shown = 0;
        }

        private final Runnable step = new Runnable() {
            public void run() {
                if (src == null || gen != stepGen) { busy = false; return; }
                int end = Math.min(want, shown + chunk);
                while (shown < end && shown < src.size()) {
                    View v = src.make(shown);
                    rows.add(v);
                    box.addView(v, box.getChildCount() - 1, wrapM());
                    shown++;
                }
                if (shown < want && shown < src.size()) {
                    sc.post(this);
                } else {
                    busy = false;
                    // 刚加完行，box 还没重新布局，这时量高度会把底部占位算得过大——等一帧再量
                    sc.post(new Runnable() {
                        public void run() {
                            fitSpacers();
                            locate();                 // 内容变高了，补一次定位
                            if (nearEnd()) more();   // 一批加完时人还停在底部，接着补
                            sc.post(new Runnable() { public void run() { fireReady(); } });
                        }
                    });
                }
            }
        };

        /** 占位恒为 overDist()，另外把底部占位补足到「可滚动范围至少两个占位」：
         *  内容不满一屏时 ScrollView 本来就滚不动，不补的话顶部占位等于白加，
         *  首尾内容就划不到屏幕中间（用户反馈「很多列表还是没有上下空白」）。
         *  补足后可滚动范围是 [0, ≥2×占位]：0 是顶部空白、占位处是内容贴顶（默认进入位置），
         *  再往上拖配合系统过滚，第一条内容就能停在屏幕中间。 */
        private void fitSpacers() {
            int h = sc.getHeight();
            if (h <= 0) return;
            int pad = overDist();
            int real = box.getHeight() - topSp.getHeight() - botSp.getHeight();
            int bot = pad + Math.max(0, h - real - pad);
            if (topSp.getHeight() != pad) setSpacer(topSp, pad);
            if (botSp.getHeight() != bot) setSpacer(botSp, bot);
        }

        private void setSpacer(View v, int h) {
            v.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, h));
        }

        /** 还剩不到一屏就提前续载，滑到那儿时行已经在，不会卡一下。 */
        private boolean nearEnd() {
            int h = sc.getHeight();
            if (h <= 0) return false;
            return box.getHeight() - (sc.getScrollY() + h) < h + dp(40);
        }

        /** 进入页面：跳过顶部占位，让第一个内容贴最顶端。 */
        void enter() {
            pendingY = overDist();
            locate();
        }

        /** 返回到本页：回到离开时的位置；这一层没记过位置（首次进入）就按默认回顶部。 */
        void restore() {
            if (savedY < 0) { enter(); return; }
            pendingY = savedY;
            locate();
        }

        /** 定到 pendingY。行一批批加，内容高度一直在变，所以每批渲染完都会再调一次。 */
        private void locate() {
            if (pendingY < 0) return;
            final int y = pendingY;
            sc.post(new Runnable() {
                public void run() {
                    selfScroll = true;
                    sc.scrollTo(0, y);
                    selfScroll = false;
                    // 内容还没铺够时 scrollTo 会被钳制，这时不能算完成，等下一批行再补
                    if (Math.abs(sc.getScrollY() - y) <= dp(2)) {
                        pendingY = -1;
                        fireReady();
                    } else if (!busy) {
                        fireReady();   // 已渲染完却还是定位不到（内容不足一屏）也要就绪，别一直等
                    }
                }
            });
        }

        /** 通知外部「本列表首屏已渲染完、可以显示了」。取走即清，重复调用无副作用。 */
        private void fireReady() {
            if (onReady != null) {
                Runnable r = onReady;
                onReady = null;
                r.run();
            }
        }
    }

    /** 可点击行统一带震动，点击区域就是整行。 */
    private View.OnClickListener rowClick(final Runnable act) {
        return new View.OnClickListener() {
            public void onClick(View v) {
                vibrate();
                if (act != null) act.run();
            }
        };
    }

    /** 普通文字行（主页学科、章、节这类）。 */
    private TextView textRow(String text, int color, Runnable act) {
        TextView tv = new TextView(this);
        applyClickBg(tv, C_PILL);
        tv.setText(applyMarkup(text));
        tv.setTextSize(lsSp());
        tv.setTextColor(color);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dp(10), dp(9), dp(10), dp(9));
        tv.setMaxLines(6);
        tv.setEllipsize(null);
        tv.setLineSpacing(dp(1), 1.08f);
        if (act != null) tv.setOnClickListener(rowClick(act));
        return tv;
    }

    /** 通用节点行：分组显示下级项数，叶子显示星级与「（例）」标记；
     *  所有节点（含中间分组）右侧都带收藏星，左侧同宽占位保证标题居中。 */
    private View nodeRow(final Node n) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        applyClickBg(row, C_PILL);
        row.setPadding(dp(10), dp(9), dp(10), dp(9));

        TextView name = new TextView(this);
        name.setTextSize(lsSp());
        name.setTextColor(0xFFF5F5F5);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setGravity(Gravity.CENTER);
        name.setMaxLines(6);
        name.setEllipsize(null);
        name.setLineSpacing(dp(1), 1.08f);
        // 星级 + 标题交给 applyMarkup 渲染（正文里的 **重点**、分数占位都在这一步处理）；
        // 「（例）」要单独上色，不能塞进整串一起渲染，否则会跟着正文变成白色。
        SpannableStringBuilder label;
        if (n.isLeaf()) {
            String prefix = (n.star > 0) ? (starText(n.star) + "  ") : "";
            label = applyMarkup(prefix + n.name);
            String mk = n.marks();
            if (mk.length() > 0) {
                int st = label.length();
                label.append("（").append(mk).append("）");
                label.setSpan(new ForegroundColorSpan(C_HI), st, label.length(),
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                label.setSpan(new RelativeSizeSpan(0.92f), st, label.length(),
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        } else {
            label = applyMarkup(n.name + "  (" + n.children.size() + ")");
        }
        name.setText(label);
        View pad = new View(this);
        row.addView(pad, new LinearLayout.LayoutParams(STAR_W(), dp(1)));
        row.addView(name, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        final boolean dailyEntry = (n.daily != null && n.daily.length() > 0);
        final TextView star = new TextView(this);
        star.setText("★");
        star.setTextSize(m.subtitleSize);
        star.setTypeface(Typeface.DEFAULT_BOLD);
        star.setGravity(Gravity.CENTER);
        star.setPadding(dp(8), dp(2), dp(4), dp(2));
        if (dailyEntry) {
            star.setVisibility(View.INVISIBLE);   // 入口本身不收藏，句子在页面里收藏
        } else {
            paintStar(star, n.id);
        }
        if (!dailyEntry) {
            star.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    vibrate();
                    boolean on = toggleFav(n.id);
                    toast(on ? "已收藏，可在主页「我的收藏」里找到" : "已取消收藏");
                    paintStar(star, n.id);
                }
            });
        }
        row.addView(star, new LinearLayout.LayoutParams(STAR_W(),
                LinearLayout.LayoutParams.WRAP_CONTENT));

        row.setOnClickListener(rowClick(new Runnable() {
            public void run() { openNode(n); }
        }));
        return row;
    }

    /** 收藏二级页 / 历史页的节点行：标题 + 短路径小字（平铺，任意层级混排也清楚）。 */
    private View nodeHitRow(final Node n, int back) {
        return hitRow(n.name, shortPath(n.path()), new Runnable() {
            public void run() { openNodeFrom(n, back); }
        });
    }

    /** 搜索 / 今日新增的结果行：标题占主位，路径小字排在下面。 */
    private View hitRow(String title, String path, Runnable act) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(10), dp(8), dp(10), dp(8));
        applyClickBg(box, C_PILL);

        TextView t = new TextView(this);
        t.setTextSize(lsSp());
        t.setTextColor(0xFFF0F0F0);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setGravity(Gravity.CENTER);
        t.setMaxLines(2);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setText(applyMarkup(title));
        box.addView(t, wrap());

        TextView p = new TextView(this);
        p.setTextSize(m.subtitleSize - 2f);
        p.setTextColor(C_SUB);
        p.setGravity(Gravity.CENTER);
        p.setMaxLines(1);
        p.setEllipsize(TextUtils.TruncateAt.END);
        p.setPadding(0, dp(2), 0, 0);
        p.setText(path);
        box.addView(p, wrap());
        if (act != null) box.setOnClickListener(rowClick(act));
        return box;
    }

    /** 页面标题（收藏、最近浏览这类列表页顶上那行）。 */
    private TextView pageTitle(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(m.subtitleSize + 2f);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(C_PRIMARY);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, 0, 0, dp(3));
        return t;
    }

    // ================= 每日一句（联网取句 + 本地文本收藏） =================
    /** 清除全部浏览记录：知识点历史与每日一句历史一起清，句子文本没被收藏的也一并删掉。 */
    private void confirmClearHist() {
        new AlertDialog.Builder(this)
                .setTitle("清除浏览记录")
                .setMessage("会清空「最近浏览」里的全部记录（含每日一句）。收藏不受影响。")
                .setNegativeButton("取消", null)
                .setPositiveButton("清除", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        histIds.clear();
                        saveHist();
                        loadDailyStore();
                        List<Integer> olds = new ArrayList<Integer>(dailyHist);
                        dailyHist.clear();
                        for (int no : olds) dropDailyIfUnused(no);
                        saveDailyStore();
                        toast("已清除浏览记录");
                        renderHist();
                    }
                })
                .show();
    }


    /** 页面上的小按钮：胶囊底、居中、点了有反馈（震动在这里统一加，调用方不用管）。 */
    private TextView dailyBtn(String text, final View.OnClickListener l) {
        TextView b = new TextView(this);
        b.setText(text);
        b.setTextSize(m.subtitleSize - 1f);
        b.setTextColor(C_PRIMARY);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(4), dp(7), dp(4), dp(7));
        applyClickBg(b, C_PILL);
        b.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                vibrate();
                l.onClick(v);
            }
        });
        return b;
    }

    private String todayTag() {
        return new SimpleDateFormat("yyMMdd", Locale.US).format(new Date());
    }

    /** 从节点进来：先把「今天这一批」准备好再切页，避免空页面闪一下。 */
    private void openDaily(Node n, int back) {
        dailyNode = n;
        dailyKind = (n.daily == null || n.daily.length() == 0) ? "phil" : n.daily;
        dailyBack = back;
        dailyRefreshing = false;
        dailyBackup.clear();
        loadDailyStore();
        dailyTitle.setText("每日一句 · " + n.name);
        // 句子来源是接口，但每天取到的那批会落盘（daily_last_*）：
        // 「今天已经取过」就直接用这批，重启 App 也不会变成空白；跨天了才重新取。
        boolean fresh = todayTag().equals(prefs.getString("daily_day_" + dailyKind, ""));
        dailyItems.clear();
        if (fresh) {
            List<String> mem = dailyMem.get(dailyKind);
            if (mem != null) dailyItems.addAll(mem);
        }
        dailyIdx = 0;
        if (dailyItems.isEmpty()) refreshDaily(dailyKind, false);   // 跨天或首次：自动取一批
        showScreen(S_DAILY);                                        // 由 renderDaily 画加载态或正文
    }

    /** showScreen 调到：页面已有内容就只重画，没有就先拿今天那批（含落盘恢复的）垫上。 */
    private void renderDaily() {
        boolean fresh = todayTag().equals(prefs.getString("daily_day_" + dailyKind, ""));
        if (dailyItems.isEmpty() && fresh) {
            List<String> mem = dailyMem.get(dailyKind);
            if (mem != null) {
                dailyItems.addAll(mem);
                dailyIdx = 0;
            }
        }
        if (dailyNode != null) dailyTitle.setText("每日一句 · " + dailyNode.name);
        if (dailyRefreshing || (dailyItems.isEmpty() && dailyLoadingKinds.contains(dailyKind))) {
            renderDailyLoading();
        } else {
            renderDailyBody();
        }
    }

    /** 点「换一批」：先把现在这批收进备份，页面换成加载态；拉到了就换新，失败/取消就还原。 */
    private void startDailyRefresh() {
        if (dailyRefreshing) return;
        if (dailyLoadingKinds.contains(dailyKind)) {
            // 已经在取：直接进加载态等这一批回来，不重复发请求
            dailyRefreshing = true;
            dailyBackup.clear();
            dailyBackup.addAll(dailyItems);
            dailyItems.clear();
            dailyIdx = 0;
            renderDailyLoading();
            return;
        }
        dailyRefreshing = true;
        dailySeq++;
        dailyBackup.clear();
        dailyBackup.addAll(dailyItems);
        dailyItems.clear();
        dailyIdx = 0;
        renderDailyLoading();
        refreshDaily(dailyKind, true);
    }

    private void cancelDailyRefresh() {
        if (!dailyRefreshing) return;
        dailyRefreshing = false;
        dailySeq++;                       // 在途结果作废
        dailyItems.clear();
        dailyItems.addAll(dailyBackup);
        dailyBackup.clear();
        dailyIdx = 0;
        renderDailyBody();
    }

    /** 加载态：固定的进度圈 + 取消按钮，翻页按钮暂时不响应。 */
    private void renderDailyLoading() {
        dailyHint.setText("正在从网络拉取…");
        if (dailyCardBox != null) dailyCardBox.setVisibility(View.GONE);
        // 拉取期间上一句/下一句/收藏/换一批都没有意义，先收起来，只留「取消」
        if (dailyBtns != null) dailyBtns.setVisibility(View.GONE);
        if (dailyRefreshBtn != null) dailyRefreshBtn.setVisibility(View.GONE);
        if (dailyLoadingBox != null) dailyLoadingBox.setVisibility(View.VISIBLE);
    }

    private void renderDailyBody() {
        if (dailyLoadingBox != null) dailyLoadingBox.setVisibility(View.GONE);
        if (dailyBtns != null) dailyBtns.setVisibility(View.VISIBLE);
        if (dailyRefreshBtn != null) dailyRefreshBtn.setVisibility(View.VISIBLE);
        if (dailyItems.isEmpty()) {
            if (dailyCardBox != null) dailyCardBox.setVisibility(View.GONE);
            dailyHint.setText("还没取到句子，点下面「换一批」试试");
            paintDailyFav();
            return;
        }
        if (dailyIdx < 0) dailyIdx = dailyItems.size() - 1;
        if (dailyIdx >= dailyItems.size()) dailyIdx = 0;
        String cur = dailyItems.get(dailyIdx);
        pushDailyHist(cur);                 // 看过就记一条（文本 + 本地号）
        paintDailyFav();
        dailyHint.setText("第 " + (dailyIdx + 1) + " / " + dailyItems.size() + " 句");
        int cut = cur.indexOf('\n');
        dailyMain.setText(applyMarkup(cut >= 0 ? cur.substring(0, cut) : cur));
        String origin = (cut >= 0) ? cur.substring(cut + 1).trim() : "";
        dailyOrigin.setText(origin);
        dailyOrigin.setVisibility(origin.length() > 0 ? View.VISIBLE : View.GONE);
        if (dailyCardBox != null) dailyCardBox.setVisibility(View.VISIBLE);
    }

    private void stepDaily(int d) {
        if (dailyItems.isEmpty()) return;
        vibrate();
        dailyIdx += d;
        if (dailyIdx < 0) dailyIdx = dailyItems.size() - 1;
        if (dailyIdx >= dailyItems.size()) dailyIdx = 0;
        renderDailyBody();
    }

    private String currentDailyText() {
        if (dailyItems.isEmpty()) return "";
        int i = Math.max(0, Math.min(dailyIdx, dailyItems.size() - 1));
        return dailyItems.get(i);
    }

    private void paintDailyFav() {
        if (dailyBtnFav == null) return;
        boolean on = isDailyFav(currentDailyText());
        dailyBtnFav.setText(on ? "★" : "☆");
        dailyBtnFav.setTextColor(on ? C_HI : C_SUB);
    }

    private void toggleDailyFavCurrent() {
        String text = currentDailyText();
        if (text.length() == 0) return;
        vibrate();
        boolean on = toggleDailyFav(text);
        toast(on ? "已收藏，可在「我的收藏 › 每日一句」里找到" : "已取消收藏");
        paintDailyFav();
    }

    // ----- 本地存储：号 → 文本，收藏与历史都只存号 -----
    private void loadDailyStore() {
        dailyPool.clear();
        dailyFav.clear();
        dailyHist.clear();
        try {
            JSONObject o = new JSONObject(prefs.getString("daily_pool", "{}"));
            java.util.Iterator<String> it = o.keys();
            while (it.hasNext()) {
                String k = it.next();
                try { dailyPool.put(Integer.parseInt(k), o.optString(k, "")); } catch (Exception ignored) { }
            }
        } catch (Exception ignored) { }
        for (String x : prefs.getString("daily_fav", "").split(",")) {
            if (x.length() == 0) continue;
            try { dailyFav.add(Integer.parseInt(x)); } catch (Exception ignored) { }
        }
        for (String x : prefs.getString("daily_hist", "").split(",")) {
            if (x.length() == 0) continue;
            try { dailyHist.add(Integer.parseInt(x)); } catch (Exception ignored) { }
        }
    }

    /** 句子之间用 \u0001 分隔（句子正文里本来就有换行，不能用逗号或换行做分隔符）。 */
    private static final String DAILY_SEP = "\u0001";

    /** 把「今天这一批」写进 prefs：退出再进来先显示它，不用等网络。
     *  以前只放内存（dailyMem），一重启就空，用户以为句子被删了。 */
    private void saveDailyMem(String kind, List<String> list) {
        if (kind == null || list == null) return;
        StringBuilder sb = new StringBuilder();
        for (String s : list) {
            if (s == null || s.length() == 0) continue;
            if (sb.length() > 0) sb.append(DAILY_SEP);
            sb.append(s);
        }
        prefs.edit().putString("daily_last_" + kind, sb.toString()).apply();
    }

    /** 启动时把上次那批读回内存（两类都读）。 */
    private void loadDailyMem() {
        for (String kind : new String[]{"phil", "poem"}) {
            String raw = prefs.getString("daily_last_" + kind, "");
            if (raw.length() == 0) continue;
            List<String> list = new ArrayList<String>();
            for (String s : raw.split(DAILY_SEP)) {
                if (s.length() > 0) list.add(s);
            }
            if (!list.isEmpty()) dailyMem.put(kind, list);
        }
    }

    private static String joinInts(List<Integer> list) {        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(list.get(i));
        }
        return sb.toString();
    }

    private void saveDailyStore() {
        JSONObject o = new JSONObject();
        for (Map.Entry<Integer, String> e : dailyPool.entrySet()) {
            try { o.put(String.valueOf(e.getKey()), e.getValue()); } catch (Exception ignored) { }
        }
        prefs.edit()
                .putString("daily_pool", o.toString())
                .putString("daily_fav", joinInts(dailyFav))
                .putString("daily_hist", joinInts(dailyHist))
                .apply();
    }

    /** 文本对应的本地号：已有就复用，没有就取当前空着的最小的号（号回收靠这里）。 */
    private int ensureDailyNo(String text) {
        for (Map.Entry<Integer, String> e : dailyPool.entrySet()) {
            if (text.equals(e.getValue())) return e.getKey();
        }
        int no = 1;
        while (dailyPool.containsKey(no)) no++;
        dailyPool.put(no, text);
        return no;
    }

    /** 这个号已经不被任何收藏/历史引用：把文本彻底删掉，号空出来给新句子用。 */
    private void dropDailyIfUnused(int no) {
        if (dailyFav.contains(no) || dailyHist.contains(no)) return;
        dailyPool.remove(no);
    }

    private boolean isDailyFav(String text) {
        if (text.length() == 0) return false;
        for (int no : dailyFav) {
            if (text.equals(dailyPool.get(no))) return true;
        }
        return false;
    }

    private boolean toggleDailyFav(String text) {
        int no = ensureDailyNo(text);
        if (dailyFav.contains(no)) {
            dailyFav.remove(Integer.valueOf(no));
            dropDailyIfUnused(no);
            saveDailyStore();
            return false;
        }
        dailyFav.add(no);
        saveDailyStore();
        return true;
    }

    /** 浏览记录：同一条提到最前，最多 50 条；被挤出去的号若没人收藏就删文本。 */
    private void pushDailyHist(String text) {
        if (text == null || text.length() == 0) return;
        int no = ensureDailyNo(text);
        dailyHist.remove(Integer.valueOf(no));
        dailyHist.add(0, no);
        while (dailyHist.size() > DAILY_HIST_MAX) {
            int old = dailyHist.remove(dailyHist.size() - 1);
            dropDailyIfUnused(old);
        }
        saveDailyStore();
    }

    // ----- 取句：只在内存里放本次运行取到的这一批 -----
    /** 每天第一次打开刷新一次；两类各自记日期，互不影响。 */
    private void checkDailyForToday() {
        String td = todayTag();
        if (!td.equals(prefs.getString("daily_day_phil", ""))) refreshDaily("phil", false);
        if (!td.equals(prefs.getString("daily_day_poem", ""))) refreshDaily("poem", false);
    }

    /** 分别记录「这一类正在取」，两路互不阻塞（两类可能同时刷新）。 */
    private final java.util.Set<String> dailyLoadingKinds = new java.util.HashSet<String>();

    /** 调一言接口取一批句子：每类至少备 10 条，多了不嫌多；取不到就提示，绝不编句子。 */
    private void refreshDaily(final String kind, final boolean manual) {
        if (dailyLoadingKinds.contains(kind)) return;
        dailyLoadingKinds.add(kind);
        final long mySeq = dailySeq;
        if (manual && dailyHint != null && kind.equals(dailyKind)) dailyHint.setText("正在从网络取句子…");
        new Thread(new Runnable() {
            public void run() {
                final List<String> got = new ArrayList<String>();
                String cats = "phil".equals(kind) ? "c=k&c=d" : "c=i";
                for (int i = 0; i < DAILY_WANT * 3 && got.size() < DAILY_WANT; i++) {
                    try {
                        String body = httpGetText("https://v1.hitokoto.cn/?" + cats
                                + "&encode=json&charset=utf-8", 4000, 5000);
                        JSONObject o = new JSONObject(body);
                        String t = o.optString("hitokoto", "").trim();
                        String from = o.optString("from", "").trim();
                        String who = o.optString("from_who", "").trim();
                        if (t.length() == 0) continue;
                        if ("null".equalsIgnoreCase(who)) who = "";
                        if ("null".equalsIgnoreCase(from)) from = "";
                        String src = who.length() > 0 ? who : from;
                        String line = src.length() > 0 ? (t + "\n—— " + src) : t;
                        if (!got.contains(line)) got.add(line);
                    } catch (Exception ignored) { }
                }
                runOnUiThread(new Runnable() {
                    public void run() {
                        dailyLoadingKinds.remove(kind);
                        if (mySeq != dailySeq) return;      // 用户已经点了取消或又换了一批，这次结果作废
                        if (got.isEmpty()) {
                            if (manual || dailyRefreshing) {
                                dailyRefreshing = false;
                                dailyItems.clear();
                                dailyItems.addAll(dailyBackup);   // 失败就把原来那批放回来
                                dailyBackup.clear();
                                dailyIdx = 0;
                                if (kind.equals(dailyKind) && screen == S_DAILY) renderDailyBody();
                                toast("没取到句子，检查一下网络再试");
                                return;
                            }
                            // 自动刷新失败：别把页面卡在「正在拉取」上，
                            // 有上次那批就先垫上，没有就让人手动去点「换一批」
                            if (kind.equals(dailyKind) && screen == S_DAILY) {
                                if (dailyItems.isEmpty()) {
                                    List<String> mem = dailyMem.get(kind);
                                    if (mem != null) dailyItems.addAll(mem);
                                }
                                dailyIdx = 0;
                                renderDailyBody();
                                if (dailyItems.isEmpty()) {
                                    dailyHint.setText("没连上网络，点下面「换一批」再试试");
                                } else {
                                    dailyHint.setText("网络没连上，这是上次那批 · 第 "
                                            + (dailyIdx + 1) + " / " + dailyItems.size() + " 句");
                                }
                            }
                            return;
                        }
                        dailyMem.put(kind, new ArrayList<String>(got));
                        saveDailyMem(kind, got);            // 落盘：下次进来先用这批
                        markDailyDay(kind);
                        if (manual) toast("已换一批 · " + got.size() + " 句");
                        if (dailyRefreshing) dailyRefreshing = false;
                        dailyBackup.clear();                    // 拉到了，临时备份没用了
                        if (kind.equals(dailyKind) && screen == S_DAILY) {
                            dailyItems.clear();
                            dailyItems.addAll(got);
                            dailyIdx = 0;
                            renderDailyBody();
                        }
                    }
                });
            }
        }).start();
    }

    private void markDailyDay(String kind) {
        prefs.edit().putString("daily_day_" + kind, todayTag()).apply();
    }

    /** 收藏页第一级：只列「有收藏的科目」，点进去才显示该科目收藏的内容。 */
    private void renderFav() {
        final Map<String, Integer> counts = new LinkedHashMap<String, Integer>();
        int found = 0;
        for (String id : favIds) {
            Node n = nodeOf(id);
            if (n == null) continue;      // 这条已经不在库里了
            found++;
            String subj = (n.subj != null) ? n.subj.name : "其他";
            Integer c = counts.get(subj);
            counts.put(subj, (c == null ? 0 : c) + 1);
        }
        final List<String> subs = new ArrayList<String>(counts.keySet());
        loadDailyStore();
        final int sentCount = dailyFav.size();
        favTitle.setText((found == 0 && sentCount == 0) ? "还没有收藏"
                : "我的收藏 · " + (found + sentCount) + " 项");
        final int extra = sentCount > 0 ? 1 : 0;
        favList.bind(new RowSource() {
            public int size() { return subs.isEmpty() ? (extra > 0 ? 1 : 1) : subs.size() + extra; }
            public View make(final int i) {
                if (extra > 0 && i == 0) {
                    View row = textRow("每日一句  (" + sentCount + ")", C_PRIMARY, new Runnable() {
                        public void run() {
                            sentMode = "fav";
                            showScreen(S_SENT);
                        }
                    });
                    row.setOnLongClickListener(new View.OnLongClickListener() {
                        public boolean onLongClick(View v) {
                            vibrate();
                            confirmClearDailyFav();
                            return true;
                        }
                    });
                    return row;
                }
                if (subs.isEmpty()) return textRow("还没有收藏任何内容", C_SUB, null);
                final int idx = i - extra;
                final String name = subs.get(idx);
                View row = textRow(name + "  (" + counts.get(name) + ")", C_PRIMARY, new Runnable() {
                    public void run() {
                        favSubKey = subjectKeyOf(name);
                        favSubTitle.setText(name + " · 我的收藏");
                        showScreen(S_FAV_SUB);
                    }
                });
                row.setOnLongClickListener(new View.OnLongClickListener() {
                    public boolean onLongClick(View v) {
                        vibrate();
                        confirmClearSubjectFav(name);
                        return true;
                    }
                });
                return row;
            }
        });
    }

    /** 长按科目：把这个科目下的收藏一次取消（先确认，避免误触）。 */
    private void confirmClearSubjectFav(final String name) {
        confirmAction("取消收藏？", "把「" + name + "」下的收藏全部取消？", "确认取消收藏", 0xFFFFB74D,
                new Runnable() {
                    public void run() {
                        for (String id : new ArrayList<String>(favIds)) {
                            Node n = nodeOf(id);
                            if (n != null && n.subj != null && n.subj.name.equals(name)) {
                                favIds.remove(id);
                                Node p = nodeOf(id);
                                if (p != null) p.savedY = -1;
                            }
                        }
                        saveFav();
                        toast("已取消收藏");
                        renderFav();
                    }
                });
    }

    /** 长按「每日一句」：把句子的收藏一次取消，没人引用的文本一并清掉。 */
    private void confirmClearDailyFav() {
        confirmAction("取消收藏？", "把「每日一句」的收藏全部取消？", "确认取消收藏", 0xFFFFB74D,
                new Runnable() {
                    public void run() {
                        loadDailyStore();
                        List<Integer> olds = new ArrayList<Integer>(dailyFav);
                        dailyFav.clear();
                        for (int no : olds) dropDailyIfUnused(no);
                        saveDailyStore();
                        toast("已取消收藏");
                        renderFav();
                    }
                });
    }

    /** 收藏页第二级：该科目下所有收藏节点，倒序平铺，每行带短路径小字。
     *  长按任意一行就取消这一条的收藏（和每日一句那边的长按是同一套操作）。 */
    private void renderFavSub() {
        final List<Node> rows = new ArrayList<Node>();
        for (String id : favIds) {
            Node n = nodeOf(id);
            if (n == null || n.subj == null || !n.subj.key.equals(favSubKey)) continue;
            rows.add(0, n);               // 倒序：最近收藏的排最上面
        }
        favSubList.bind(new RowSource() {
            public int size() { return rows.size(); }
            public View make(final int i) {
                final Node n = rows.get(i);
                View row = nodeHitRow(n, S_FAV_SUB);
                row.setOnLongClickListener(new View.OnLongClickListener() {
                    public boolean onLongClick(View v) {
                        vibrate();
                        if (!clearFavOne(n)) return true;
                        toast("已取消收藏");
                        if (favCountInSubject() == 0) {
                            // 这个科目已经没有收藏了，直接退回收藏首页，不留一个空列表
                            goBack(S_FAV);
                        } else {
                            renderFavSub();
                            renderFav();
                        }
                        return true;
                    }
                });
                return row;
            }
        });
    }

    /** 当前科目还剩几条收藏（收藏二级页取消后判断要不要退回上一级）。 */
    private int favCountInSubject() {
        int c = 0;
        for (String id : favIds) {
            Node n = nodeOf(id);
            if (n != null && n.subj != null && n.subj.key.equals(favSubKey)) c++;
        }
        return c;
    }

    /** 取消某一条收藏（收藏二级页长按用）。返回是否真的取消了。 */
    private boolean clearFavOne(Node n) {
        if (n == null) return false;
        String key = stableKey(n.id);
        if (key.length() == 0 || !favIds.remove(key)) return false;
        n.savedY = -1;
        saveFav();
        return true;
    }

    private String subjectKeyOf(String name) {
        for (Subject su : subjects) if (su.name.equals(name)) return su.key;
        return "";
    }

    /** 最近浏览：按时间倒序，最多 50 条（HIST_MAX，超出自动清掉最旧的）。 */
    private void renderHist() {
        final List<Node> rows = new ArrayList<Node>();
        for (String id : histIds) {
            Node n = nodeOf(id);
            if (n != null) rows.add(n);
        }
        loadDailyStore();
        final int sentHist = dailyHist.size();
        histTitle.setText((rows.isEmpty() && sentHist == 0) ? "还没有浏览记录"
                : "最近浏览 · " + (rows.size() + sentHist) + " 条");
        final List<Object> items = new ArrayList<Object>();
        items.add("最多保留 50 条，超出自动清除最旧的");
        if (sentHist > 0) items.add("daily");     // 每日一句的浏览记录（另存文本，不占知识点编号）
        items.addAll(rows);
        histList.bind(new RowSource() {
            public int size() { return items.size(); }
            public View make(final int i) {
                if (i > 0 && "daily".equals(items.get(i))) {
                    return textRow("每日一句  (" + sentHist + ")", C_PRIMARY, new Runnable() {
                        public void run() {
                            sentMode = "hist";
                            showScreen(S_SENT);
                        }
                    });
                }
                if (i == 0) {
                    TextView note = new TextView(MainActivity.this);
                    note.setText((String) items.get(0));
                    note.setTextSize(m.subtitleSize - 2.5f);
                    note.setTextColor(C_SUB);
                    note.setGravity(Gravity.CENTER);
                    note.setPadding(dp(4), dp(2), dp(4), dp(6));
                    return note;
                }
                return nodeHitRow((Node) items.get(i), S_HIST);
            }
        });
    }

    /** 面包屑最前面那一段，点了直接回主页（省得一页一页往外退）。 */
    private static final String HOME_SEG = "首页";

    /** 面包屑：按导航栈动态拼，有几级就几段，每段可点跳回那一层。 */
    private void setPath(TextView tv, String full) {
        if (full == null) return;
        String[] seg = full.split(" · ")[0].split(" › ");   // 路径末尾可能带编号，先切掉
        SpannableStringBuilder sb = new SpannableStringBuilder();
        int start = 0;
        sb.append(HOME_SEG);
        sb.setSpan(pathSpan(HOME_SEG, -1), start, sb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        for (int i = 0; i < seg.length; i++) {
            sb.append(" › ");
            start = sb.length();
            sb.append(seg[i]);
            if (i < seg.length - 1) {
                final int kind = i;       // 0=科目，>=1=第 i 级分组
                sb.setSpan(pathSpan(seg[i], kind), start, sb.length(),
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else {
                sb.setSpan(new ForegroundColorSpan(C_SUB), start, sb.length(),
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        tv.setText(sb);
        tv.setHighlightColor(Color.TRANSPARENT);
        attachSpanTap(tv);
    }

    /** kind：-1=首页；0=科目；>=1=导航栈里的第 (kind-1) 级分组。 */
    private ClickableSpan pathSpan(final String name, final int kind) {
        return new ClickableSpan() {
            public void onClick(View v) {
                vibrate();
                if (kind == -1) {
                    // 主动点了面包屑「首页」：脱离外部入口的返回链，之后按返回回主页
                    nodeEntryBack = -1;
                    nodeEntryDepth = 0;
                    goBack(S_SUBJECT);
                    return;
                }
                if (kind == 0) {
                    for (Subject su : subjects) {
                        if (su.name.equals(name)) {
                            curSubject = su;
                            navStack.clear();
                            curNode = null;
                            // 主动点科目名回到顶层，等同于从主页重新进这一科
                            nodeEntryBack = -1;
                            nodeEntryDepth = 0;
                            renderNodes();
                            restoreNext[S_NODE] = true;
                            showScreen(S_NODE);
                            return;
                        }
                    }
                    return;
                }
                // 跳到栈里第 (kind-1) 级：截断栈，回到那一层
                int keep = kind;           // 保留前 keep 个分组节点
                while (navStack.size() > keep) navStack.remove(navStack.size() - 1);
                curNode = navStack.isEmpty() ? null : navStack.get(navStack.size() - 1);
                renderNodes();
                restoreNext[S_NODE] = true;
                showScreen(S_NODE);
            }
            public void updateDrawState(TextPaint ds) {
                ds.setUnderlineText(false);      // 不要链接下划线，手表上太挤
                ds.setColor(C_PRIMARY);
            }
        };
    }

    /** 让 ClickableSpan 可点又不抢滚动：按下到抬起几乎没位移才算点击，其余一律放行给滚动容器。
     *  不用 LinkMovementMethod——它在 DOWN 命中文字时就消费事件，手指在标题上起手会滑不动。 */
    private void attachSpanTap(final TextView tv) {
        tv.setOnTouchListener(new View.OnTouchListener() {
            private float x0, y0;
            private boolean moved;
            public boolean onTouch(View v, MotionEvent ev) {
                switch (ev.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        x0 = ev.getX();
                        y0 = ev.getY();
                        moved = false;
                        // 必须消费掉按下：不然这个 TextView 不会成为事件目标，抬手事件根本不会再发到这里
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        if (Math.abs(ev.getX() - x0) > dp(12) || Math.abs(ev.getY() - y0) > dp(12)) moved = true;
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!moved) {
                            ClickableSpan[] sp = spanAt(tv, (int) ev.getX(), (int) ev.getY());
                            if (sp.length > 0) sp[0].onClick(tv);
                        }
                        return true;
                }
                return false;
            }
        });
    }

    private ClickableSpan[] spanAt(TextView tv, int x, int y) {
        CharSequence t = tv.getText();
        Layout l = tv.getLayout();
        if (l == null || !(t instanceof Spanned)) return new ClickableSpan[0];
        int line = l.getLineForVertical(y - tv.getTotalPaddingTop() + tv.getScrollY());
        int off = l.getOffsetForHorizontal(line, x - tv.getTotalPaddingLeft());
        if (off < 0 || off > t.length()) return new ClickableSpan[0];
        return ((Spanned) t).getSpans(off, off, ClickableSpan.class);
    }

    /** 两行行：主行 + 状态小字（我的反馈记录）。 */
    private View pairRow(String a, String b, String colorHex, Runnable act) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(10), dp(8), dp(10), dp(8));
        applyClickBg(box, C_PILL);

        TextView t = new TextView(this);
        t.setTextSize(lsSp() - 1f);
        t.setTextColor(0xFFF0F0F0);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setGravity(Gravity.CENTER);
        t.setMaxLines(2);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setText(a);
        box.addView(t, wrap());

        TextView s = new TextView(this);
        s.setTextSize(m.subtitleSize - 2f);
        s.setGravity(Gravity.CENTER);
        s.setMaxLines(1);
        s.setPadding(0, dp(2), 0, 0);
        s.setText(b);
        try {
            s.setTextColor(Color.parseColor(colorHex));
        } catch (Exception ignored) {
            s.setTextColor(C_SUB);
        }
        box.addView(s, wrap());
        if (act != null) box.setOnClickListener(rowClick(act));
        return box;
    }

    private TextView makeBack(String label, View.OnClickListener onClick) {
        TextView b = new TextView(this);
        b.setText(label);
        b.setTextSize(m.backSize);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(C_PRIMARY);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(10), dp(8), dp(10), dp(8));
        b.setBackground(clickBg(C_PILL));
        final View.OnClickListener user = onClick;
        b.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                vibrate();
                if (user != null) user.onClick(v);
            }
        });
        b.setOnLongClickListener(new View.OnLongClickListener() {
            public boolean onLongClick(View v) {
                if (screen == S_SUBJECT) return false;
                vibrate();
                showScreen(S_SUBJECT);
                toast("已回到首页");
                return true;
            }
        });
        return b;
    }

    /** 作为列表首项的返回键：随内容一起滚动，滚出屏幕后不再占用顶部空间。 */
    private TextView makeBackHeader(String label, View.OnClickListener onClick) {
        return makeBack(label, onClick);
    }

    /** 交互震动反馈：轻点一下。受设置开关与节流控制，避免连点狂震。 */
    private void vibrate() {
        if (!haptic) return;
        long now = System.currentTimeMillis();
        if (now - lastVibrate < 60) return;
        lastVibrate = now;
        try {
            Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (v == null) return;
            if (Build.VERSION.SDK_INT >= 26) {
                v.vibrate(VibrationEffect.createOneShot(18, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                v.vibrate(18);
            }
        } catch (Exception ignored) { }
    }

    private GradientDrawable pill(int fill) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(13));
        return g;
    }

    /** 统一的可点击背景：圆角 + 蓝色涟漪。 */
    private android.graphics.drawable.Drawable clickBg(int fill) {
        try {
            GradientDrawable content = pill(fill);
            GradientDrawable mask = new GradientDrawable();
            mask.setShape(GradientDrawable.RECTANGLE);
            mask.setColor(Color.WHITE);
            mask.setCornerRadius(dp(13));
            return new RippleDrawable(ColorStateList.valueOf(0x334FC3F7), content, mask);
        } catch (Exception e) {
            return pill(fill);
        }
    }

    /** 设置涟漪背景，并用 clipToOutline 让 background（圆角 content）的默认 outline 裁剪涟漪，
     *  保证涟漪不溢出圆角。不自定义 outlineProvider（自定义的 roundRect 在 layout 前宽高为 0 会失效）。 */
    private void applyClickBg(final View v, int fill) {
        v.setBackground(clickBg(fill));
        v.setClipToOutline(true);
    }

    private FrameLayout.LayoutParams fp() {
        return new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
    }

    private LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    /** 同上，但底部留出间距。新页的按钮之间都靠它撑开——不在调用处一个个手写 margin。 */
    private LinearLayout.LayoutParams wrapGap(int bottomDp) {
        LinearLayout.LayoutParams lp = wrap();
        lp.bottomMargin = dp(bottomDp);
        return lp;
    }

    private LinearLayout.LayoutParams wrapM() {
        LinearLayout.LayoutParams lp = wrap();
        lp.bottomMargin = dp(6);
        return lp;
    }

    private LinearLayout.LayoutParams lw() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
    }

    // ================= 导航（数据驱动，层级深度不限） =================
    /** 当前列表页该显示哪些节点：没进任何分组就是科目顶层，否则是当前分组的 children。 */
    private List<Node> currentNodes() {
        return (curNode == null) ? curSubject.roots : curNode.children;
    }

    /** 打开一个节点：有下级就压栈列下级，是叶子就直接看正文。层数由数据决定。 */
    private void openNode(final Node n) {
        if (n.daily != null && n.daily.length() > 0) { openDaily(n, S_NODE); return; }
        if (n.isLeaf()) {
            openDetail(n.name, n.body(), n.path(), S_NODE, n.img, n.extras(), n.id);
            return;
        }
        navStack.add(n);
        curNode = n;
        renderNodes();
        showScreen(S_NODE);
    }

    /** 从搜索/收藏/历史/今日跳进某个节点：把它的祖先链补进导航栈，返回时能一路退回来。 */
    private void openNodeFrom(final Node n, int back) {
        if (n.daily != null && n.daily.length() > 0) { openDaily(n, back); return; }
        // 无论叶子还是分组，都先把祖先链补进栈：详情页/节点页的面包屑与返回才有得退
        ArrayList<Node> chain = new ArrayList<Node>();
        Node top = n.isLeaf() ? n.parent : n;
        for (Node p = top; p != null; p = p.parent) chain.add(0, p);
        navStack.clear();
        navStack.addAll(chain);
        curNode = chain.isEmpty() ? null : chain.get(chain.size() - 1);
        if (n.subj != null) curSubject = n.subj;
        nodeEntryBack = back;
        // 祖先链是「补」进来的，不是用户一层层点进来的：返回时退到这个深度就回入口页
        nodeEntryDepth = chain.size();
        renderNodes();
        if (n.isLeaf()) {
            openDetail(n.name, n.body(), n.path(), back, n.img, n.extras(), n.id);
        } else {
            showScreen(S_NODE);
        }
    }

    /** 进入某科目：清空栈，展示顶层节点。 */
    private void openSubject(Subject su) {
        curSubject = su;
        nodeEntryBack = -1;
        nodeEntryDepth = 0;
        navStack.clear();
        curNode = null;
        renderNodes();
        showScreen(S_NODE);
    }

    /** 返回上一级：用户自己点进的层逐层退；退到「从外部入口进来时的深度」就直接回那一页。 */
    private boolean nodeBack() {
        int depth = navStack.size();
        if (depth > nodeEntryDepth) {
            navStack.remove(depth - 1);
            curNode = navStack.isEmpty() ? null : navStack.get(navStack.size() - 1);
            renderNodes();
            restoreNext[S_NODE] = true;
            showScreen(S_NODE);
            return true;
        }
        if (nodeEntryBack >= 0) {           // 从收藏/搜索/今日/历史进来：一次返回就回那一页
            int t = nodeEntryBack;
            nodeEntryBack = -1;
            nodeEntryDepth = 0;
            navStack.clear();
            curNode = null;
            goBack(t);
            return true;
        }
        return false;                       // 正常从主页进的科目顶层：交给 backTarget 回主页
    }

    /** 面包屑当前路径字符串（首页 › 科目 › 各级分组）。 */
    private String currentPath() {
        ArrayList<String> seg = new ArrayList<String>();
        seg.add(curSubject.name);
        for (Node p : navStack) seg.add(p.name);
        return TextUtils.join(" › ", seg);
    }

    private void renderNodes() {
        // 程序性清空搜索框：锁住监听，否则 afterTextChanged→refreshNodes 会多绑定一次列表，
        // 第二次 bind 时缓存已被取走，只能重新分帧渲染 → 预览一片空白、位置也丢回顶端。
        nodeRefreshLocked = true;
        itemSearchBox.setText("");
        nodeRefreshLocked = false;
        setPath(listTitle, currentPath());
        if (englishEntryBar != null) {
            boolean top = navStack.isEmpty() && curSubject != null && "english".equals(curSubject.key);
            englishEntryBar.setVisibility(top ? View.VISIBLE : View.GONE);
        }
        bindNodes();
    }

    private LinearLayout englishEntryBar;

    /** 英语科目页顶部那两个入口：背单词 / 搜单词。只在这一科的顶层显示。 */
    private LinearLayout buildEnglishEntryBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER);
        bar.setPadding(0, dp(2), 0, dp(5));

        TextView w = wordBtn("背单词", true);
        w.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                vibrate();
                wordHomeBack = S_NODE;      // 从科目页进的，返回就该回科目页
                showScreen(S_WORD_HOME);
            }
        });
        LinearLayout.LayoutParams lp1 = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp1.rightMargin = dp(4);
        bar.addView(w, lp1);

        TextView s = wordBtn("搜单词", true);
        s.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                vibrate();
                wordSearchBack = S_NODE;    // 同上：别退到背单词页去
                showScreen(S_WORD_SEARCH);
            }
        });
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp2.leftMargin = dp(4);
        bar.addView(s, lp2);
        return bar;
    }

    private void refreshNodes() {
        if (screen != S_NODE || nodeRefreshLocked) return;   // 搜索框只在节点页生效
        bindNodes();
    }

    /** 真正把当前层的节点绑到列表；renderNodes 与 refreshNodes 共用。 */
    private void bindNodes() {
        String q = itemSearchBox.getText().toString().trim();
        shownNodes.clear();
        for (Node n : currentNodes()) {
            if (q.length() == 0) { shownNodes.add(n); continue; }
            if (n.name.contains(q)) { shownNodes.add(n); continue; }
            if (n.isLeaf()) {
                if (n.body().contains(q)) shownNodes.add(n);
            } else {
                // 分组：名字没命中时，若它的后代有命中的也列出来
                if (subtreeMatches(n, q)) shownNodes.add(n);
            }
        }
        nodeList.bind(currentPath() + "|" + q, new RowSource() {
            public int size() { return shownNodes.size(); }
            public View make(final int i) { return nodeRow(shownNodes.get(i)); }
        });
    }

    private boolean subtreeMatches(Node n, String q) {
        for (Node c : n.children) {
            if (c.name.contains(q)) return true;
            if (c.isLeaf()) {
                if (c.body().contains(q)) return true;
            } else if (subtreeMatches(c, q)) return true;
        }
        return false;
    }

    private void refreshGlobalSearch() {
        String q = gSearchBox.getText().toString().trim();
        lastQuery = q;
        searchHits.clear();
        int groupTotal = 0;
        if (q.length() == 0) {
            searchTitle.setText("搜索知识点与分组");
            searchList.bind(new RowSource() {
                public int size() { return 1; }
                public View make(int i) { return textRow("输入关键词开始搜索", C_SUB, null); }
            });
            return;
        }
        String ql = q.toLowerCase();
        // 先扫分组：章节标题本身也要能搜到（以前索引里只有叶子，分组永远搜不出来）。
        // 分组只列它自己，不把它下面的知识点一起铺出来——不然搜「古诗文」会刷出上百条。
        for (Node n : allGroups) {
            if (!n.name.toLowerCase().contains(ql)) continue;
            groupTotal++;
            if (searchHits.size() < HIT_LIMIT) searchHits.add(n);
        }
        int leafTotal = 0;
        for (Node n : allHits) {
            String blob = (n.name + " " + n.body()).toLowerCase();
            if (!blob.contains(ql)) continue;
            leafTotal++;
            if (searchHits.size() < HIT_LIMIT) searchHits.add(n);
        }
        int total = groupTotal + leafTotal;
        String count = total == 0 ? "" : ("命中 " + total + " 条"
                + (groupTotal > 0 && leafTotal > 0 ? "（分组 " + groupTotal + " · 知识点 " + leafTotal + "）" : "")
                + " · " + q
                + (total > HIT_LIMIT ? "（先列出前 " + HIT_LIMIT + " 条）" : ""));
        searchTitle.setText(total == 0 ? "没有找到「" + q + "」" : count);
        searchList.bind(new RowSource() {
            public int size() { return searchHits.size(); }
            public View make(final int i) {
                final Node n = searchHits.get(i);
                boolean grp = !n.isLeaf();
                // 分组行：小字前面标「分组」，跟知识点区分开
                String sub = grp ? ("分组 · " + shortPath(n.path())) : shortPath(n.path());
                return hitRow(n.name, sub, new Runnable() {
                    public void run() { openNodeFrom(n, S_SEARCH); }
                });
            }
        });
    }

    /** 路径只留「科目 › 章」，节名省掉，给标题腾地方。 */
    /** 收藏/历史/搜索列表的小字路径：科目 › … › 直接父级，深层也不丢归属。 */
    private String shortPath(String p) {
        if (p == null || p.length() == 0) return "";
        String[] seg = p.split(" › ");
        if (seg.length <= 2) return p;
        if (seg.length <= 4) return TextUtils.join(" › ", seg);
        return seg[0] + " › … › " + seg[seg.length - 2] + " › " + seg[seg.length - 1];
    }

    private String starText(int s) {
        if (s <= 0) return "·";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s && i < 3; i++) sb.append("★");
        return sb.toString();
    }

    private int secColor(String c) {
        if ("K".equals(c)) return 0xFF4FC3F7;
        if ("M".equals(c)) return 0xFF81C784;
        if ("E".equals(c)) return 0xFFFFB74D;
        if ("W".equals(c)) return 0xFFE57373;
        if ("F".equals(c)) return 0xFF4DB6AC;
        return 0xFFBA68C8;
    }

    // ================= 配图（详情缩略图 + 双击全屏） =================
    private void addFigure(String path) {
        try {
            InputStream is = getAssets().open(path);
            final Bitmap bm = BitmapFactory.decodeStream(is);
            is.close();
            if (bm == null) return;
            ImageView iv = new ImageView(this);
            iv.setImageBitmap(bm);
            iv.setAdjustViewBounds(true);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(0xFFFFFFFF);
            bg.setCornerRadius(dp(8));
            iv.setBackgroundDrawable(bg);
            iv.setPadding(dp(2), dp(2), dp(2), dp(2));
            final GestureDetector gd = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onDown(MotionEvent e) { return true; }
                @Override
                public boolean onDoubleTap(MotionEvent e) {
                    showFullscreen(bm);
                    return true;
                }
            });
            iv.setOnTouchListener(new View.OnTouchListener() {
                public boolean onTouch(View v, MotionEvent ev) { return gd.onTouchEvent(ev); }
            });
            iv.setContentDescription("双击全屏查看");
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(8);
            detailInner.addView(iv, lp);
        } catch (Exception ignored) { }
    }

    private void showFullscreen(final Bitmap bm) {
        final FrameLayout full = new FrameLayout(this);
        full.setBackgroundColor(Color.BLACK);
        fullscreenView = full;

        ZoomPanView zv = new ZoomPanView(this);
        zv.setImageBitmap(bm);
        full.addView(zv, fp());

        TextView close = new TextView(this);
        close.setText("‹ 关闭");
        close.setTextSize(m.backSize);
        close.setTypeface(Typeface.DEFAULT_BOLD);
        close.setTextColor(C_PRIMARY);
        close.setGravity(Gravity.CENTER);
        close.setPadding(dp(12), dp(9), dp(12), dp(9));
        close.setBackgroundDrawable(pill(0xCC1C1C1C));
        close.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                vibrate();
                closeFullscreen();
            }
        });
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.START);
        clp.topMargin = m.topPad + m.roundSafe + dp(2);
        clp.leftMargin = m.sidePad + m.roundSafe + dp(2);
        full.addView(close, clp);

        TextView hint = new TextView(this);
        hint.setText("双指缩放 · 单指拖动 · 点‹关闭返回");
        hint.setTextSize(m.subtitleSize - 2f);
        hint.setTextColor(0xFF9A9A9A);
        hint.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams hlp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        hlp.bottomMargin = m.bottomPad;
        full.addView(hint, hlp);

        root.addView(full, fp());
        full.setAlpha(0f);
        full.animate().alpha(1f).setDuration(200).start();
    }

    private void closeFullscreen() {
        if (fullscreenView != null) {
            root.removeView(fullscreenView);
            fullscreenView = null;
        }
    }

    /** 全屏图片：双指以焦点缩放、单指拖拽、双击定点缩放（圆屏四角可拖入）。 */
    private class ZoomPanView extends ImageView {
        private final Matrix matrix = new Matrix();
        private float scale = 1f;          // 1 = 适配屏幕
        private float baseScale = 1f;
        private float minScale = 1f, maxScale = 5f;
        private ScaleGestureDetector scaleDet;
        private GestureDetector gestureDet;
        private boolean inited = false;

        ZoomPanView(Context c) {
            super(c);
            setScaleType(ScaleType.MATRIX);
            scaleDet = new ScaleGestureDetector(c, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                @Override
                public boolean onScale(ScaleGestureDetector detector) {
                    float factor = detector.getScaleFactor();
                    float ns = scale * factor;
                    if (ns < minScale || ns > maxScale) return false;
                    factor = ns / scale;
                    scale = ns;
                    // 以双指焦点为中心缩放，保留已有平移
                    matrix.postScale(factor, factor, detector.getFocusX(), detector.getFocusY());
                    clamp();
                    setImageMatrix(matrix);
                    return true;
                }
            });
            gestureDet = new GestureDetector(c, new GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onDown(MotionEvent e) { return true; }
                @Override
                public boolean onScroll(MotionEvent e1, MotionEvent e2, float dx, float dy) {
                    if (scaleDet.isInProgress()) return false;   // 双指缩放时不平移
                    matrix.postTranslate(-dx, -dy);
                    clamp();
                    setImageMatrix(matrix);
                    return true;
                }
                @Override
                public boolean onDoubleTap(MotionEvent e) {
                    if (scale > 1.05f) { resetToFit(); }
                    else {
                        scale = 2.5f;
                        matrix.postScale(2.5f, 2.5f, e.getX(), e.getY());
                        clamp();
                        setImageMatrix(matrix);
                    }
                    return true;
                }
            });
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            super.onLayout(changed, left, top, right, bottom);
            if (!inited && getDrawable() != null && getWidth() > 0 && getHeight() > 0) {
                inited = true;
                resetToFit();
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent ev) {
            scaleDet.onTouchEvent(ev);
            gestureDet.onTouchEvent(ev);
            return true;
        }

        private void resetToFit() {
            int vw = getWidth(), vh = getHeight();
            int iw = drawableW(), ih = drawableH();
            if (vw == 0 || vh == 0 || iw == 0 || ih == 0) return;
            baseScale = Math.min((float) vw / iw, (float) vh / ih);
            scale = 1f;
            matrix.reset();
            matrix.setScale(baseScale, baseScale);
            matrix.postTranslate((vw - iw * baseScale) / 2f, (vh - ih * baseScale) / 2f);
            setImageMatrix(matrix);
        }

        private int drawableW() {
            return getDrawable() == null ? 0 : getDrawable().getIntrinsicWidth();
        }

        private int drawableH() {
            return getDrawable() == null ? 0 : getDrawable().getIntrinsicHeight();
        }

        private void clamp() {
            float[] v = new float[9];
            matrix.getValues(v);
            int vw = getWidth(), vh = getHeight();
            int iw = drawableW(), ih = drawableH();
            if (vw == 0 || vh == 0 || iw == 0 || ih == 0) return;
            float sw = iw * v[Matrix.MSCALE_X];
            float sh = ih * v[Matrix.MSCALE_Y];
            float tx = v[Matrix.MTRANS_X];
            float ty = v[Matrix.MTRANS_Y];
            // 允许把任意边缘拖入可见区（圆屏四角可拖入）
            float minX = vw - sw, maxX = 0f;
            float minY = vh - sh, maxY = 0f;
            if (sw <= vw) { minX = 0f; maxX = vw - sw; }
            if (sh <= vh) { minY = 0f; maxY = vh - sh; }
            tx = Math.max(minX, Math.min(maxX, tx));
            ty = Math.max(minY, Math.min(maxY, ty));
            v[Matrix.MTRANS_X] = tx;
            v[Matrix.MTRANS_Y] = ty;
            matrix.setValues(v);
        }
    }

    // ================= 详情 =================
    /** 详情页的一张卡片（纯 UI 结构，不是数据层级）：h 为空就不显示顶部标签。 */
    static class Card { String h, c, body; }

    static Card card(String h, String c, String body) {
        Card k = new Card();
        k.h = h; k.c = c; k.body = body;
        return k;
    }

    /** 知识点详情：正文只有一段，直接铺成一张无标签卡片。 */
    private void openDetail(String title, String body, String path, int back, String img,
                            List<ExtraBlock> extras, String id) {
        List<Card> cards = new ArrayList<Card>();
        cards.add(card("", "K", body == null ? "" : body));
        openDetailCards(title, cards, path, back, img, extras, id);
    }

    /** 详情页主体：反馈详情这类要用多张带标签的卡片，所以卡片由调用方给定。 */
    private void openDetailCards(String title, List<Card> cards, String path, int back, String img,
                                 List<ExtraBlock> extras, String id) {
        detailBack = back;
        curExtras = (extras == null) ? new ArrayList<ExtraBlock>() : extras;
        lastPath = (path == null) ? "" : path;
        curDetailId = (id == null) ? "" : id;
        if (curDetailId.length() > 0) pushHistory(curDetailId);
        detailTitle.setText(applyMarkup(title));
        setPath(detailPath, path);
        detailInner.removeAllViews();
        if (overDist() > 0) detailInner.addView(makeScrollSpacer());
        detailInner.addView(detailBackBtn, wrapM());
        detailInner.addView(detailPath, wrap());
        // 标题与收藏星并排：星在标题右边，按一下收藏 / 取消，编号记在本地
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        if (detailTitle.getParent() instanceof ViewGroup) {
            ((ViewGroup) detailTitle.getParent()).removeView(detailTitle);
        }
        if (curDetailId.length() > 0) {
            // 左边垫一个和收藏星同宽的透明占位，标题才不会被右边的星顶偏
            View pad = new View(this);
            titleRow.addView(pad, new LinearLayout.LayoutParams(STAR_W(), dp(1)));
        }
        titleRow.addView(detailTitle, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        if (curDetailId.length() > 0) {
            final TextView star = new TextView(this);
            star.setText("★");
            star.setTextSize(m.titleSize);
            star.setTypeface(Typeface.DEFAULT_BOLD);
            star.setGravity(Gravity.CENTER);
            star.setPadding(dp(8), dp(2), dp(4), dp(2));
            paintStar(star, curDetailId);
            star.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    vibrate();
                    boolean on = toggleFav(curDetailId);
                    toast(on ? "已收藏，可在主页「我的收藏」里找到" : "已取消收藏");
                    paintStar(star, curDetailId);
                }
            });
            titleRow.addView(star, new LinearLayout.LayoutParams(STAR_W(),
                    LinearLayout.LayoutParams.WRAP_CONTENT));
        }
        detailInner.addView(titleRow, wrap());
        if (img != null && img.length() > 0) addFigure(img);

        if (cards == null || cards.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("（暂无内容）");
            empty.setTextSize(fsSp());
            empty.setTextColor(C_SUB);
            empty.setGravity(Gravity.CENTER);
            detailInner.addView(empty, wrap());
        } else {
            for (Card s : cards) {
                LinearLayout card = new LinearLayout(this);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setPadding(dp(11), dp(9), dp(11), dp(9));
                GradientDrawable cb = new GradientDrawable();
                cb.setColor(C_CARD);
                cb.setCornerRadius(dp(11));
                card.setBackgroundDrawable(cb);

                // 分类标签只在有实际文字时才显示；空文字会退化成无意义的纯色块（蓝色球）
                if (s.h != null && s.h.trim().length() > 0) {
                    TextView tag = new TextView(this);
                    tag.setText(s.h);
                    tag.setTextSize(m.secTagSize);
                    tag.setTypeface(Typeface.DEFAULT_BOLD);
                    tag.setTextColor(Color.WHITE);
                    tag.setPadding(dp(9), dp(3), dp(9), dp(3));
                    GradientDrawable tg = new GradientDrawable();
                    tg.setColor(secColor(s.c));
                    tg.setCornerRadius(dp(9));
                    tag.setBackgroundDrawable(tg);
                    LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                    tlp.bottomMargin = dp(5);
                    card.addView(tag, tlp);
                }

                TextView body = new TextView(this);
                body.setText(applyMarkup(s.body));
                body.setTextSize(fsSp());
                body.setTextColor(C_TEXT);
                body.setLineSpacing(dp(3), 1.25f);
                card.addView(body, wrap());

                LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                clp.bottomMargin = dp(8);
                detailInner.addView(card, clp);
            }
        }
        // 附加内容按钮：数据里有几块就画几个（例题、译文…），
        // 按钮文字 = 数据给的「块名 · 组数 单位」，界面不写死任何名字。
        if (curExtras != null) {
            final String ttl = title;
            for (final ExtraBlock blk : curExtras) {
                if (blk.items.isEmpty()) continue;
                TextView exBtn = new TextView(this);
                // 按钮文字 = 「块名 · 组数 单位」。unit 留空时只写块名——正常数据不会留空
                // （必背 60 篇是「译文 · 51 句」和「例题 · 25 道」两块两个按钮），
                // 这里兜住漏写 unit 的情况，免得按钮后面挂一个「· 12 」的空数字。
                if (blk.unit.length() == 0) {
                    exBtn.setText(blk.name);
                } else {
                    exBtn.setText(blk.name + " · " + blk.items.size() + " " + blk.unit);
                }
                exBtn.setTextSize(m.subtitleSize);
                exBtn.setTypeface(Typeface.DEFAULT_BOLD);
                exBtn.setTextColor(0xFF1A242E);
                exBtn.setGravity(Gravity.CENTER);
                exBtn.setPadding(dp(10), dp(11), dp(10), dp(11));
                exBtn.setBackgroundDrawable(pill(C_HI));
                exBtn.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        vibrate();
                        openExtra(blk, ttl, lastPath);
                    }
                });
                LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                elp.topMargin = dp(2);
                detailInner.addView(exBtn, elp);
            }
        }
        if (overDist() > 0) detailInner.addView(makeScrollSpacer());
        scrollToTop(detailScroll);
        showScreen(S_DETAIL);
    }

    /** 附加内容页（例题 / 译文共用同一套渲染）：一组一张卡，卡内每行 = 数据给的标签 + 文本。
     *  界面不认识「例题」「译文」，只按 tone 决定字号与颜色，以后加新类型不用改这里。 */
    private void openExtra(ExtraBlock b, String title, String path) {
        extraTitle.setText(applyMarkup(title));
        extraKind.setText(b.name);
        setPath(extraPath, path);
        extraInner.removeAllViews();
        if (overDist() > 0) extraInner.addView(makeScrollSpacer());
        extraInner.addView(extraBackBtn, wrapM());
        extraInner.addView(extraPath, wrap());
        extraInner.addView(extraTitle, wrap());
        extraInner.addView(extraKind, wrap());

        for (ExtraItem it : b.items) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(11), dp(10), dp(11), dp(10));
            GradientDrawable cb = new GradientDrawable();
            cb.setColor(C_CARD);
            cb.setCornerRadius(dp(11));
            card.setBackgroundDrawable(cb);

            boolean first = true;
            for (ExtraLine ln : it.lines) {
                if (ln.tag != null && ln.tag.trim().length() > 0) {
                    TextView tag = new TextView(this);
                    tag.setText(ln.tag);
                    tag.setTextSize(m.secTagSize);
                    tag.setTypeface(Typeface.DEFAULT_BOLD);
                    tag.setTextColor(Color.WHITE);
                    tag.setPadding(dp(9), dp(3), dp(9), dp(3));
                    GradientDrawable tg = new GradientDrawable();
                    tg.setColor(toneChipColor(ln.tone));
                    tg.setCornerRadius(dp(9));
                    tag.setBackgroundDrawable(tg);
                    LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                    tlp.topMargin = first ? 0 : dp(9);
                    tlp.bottomMargin = dp(5);
                    card.addView(tag, tlp);
                }
                TextView body = new TextView(this);
                body.setText(applyMarkup(ln.text));
                body.setTextSize(toneTextSize(ln.tone));
                body.setTextColor(toneTextColor(ln.tone));
                if ("head".equals(ln.tone)) body.setTypeface(Typeface.DEFAULT_BOLD);
                body.setLineSpacing(dp(3), 1.3f);
                card.addView(body, wrap());
                first = false;
            }

            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            clp.bottomMargin = dp(9);
            extraInner.addView(card, clp);
        }

        if (overDist() > 0) extraInner.addView(makeScrollSpacer());
        scrollToTop(extraScroll);
        showScreen(S_EXTRA);
    }

    /** 三种语义角色 → 字号 / 颜色 / 标签底色：样式只在这里，数据只说「这行是什么角色」。 */
    private float toneTextSize(String tone) {
        if ("head".equals(tone)) return fsSp();
        if ("note".equals(tone)) return fsSp() - 2f;
        return fsSp() - 0.5f;
    }

    private int toneTextColor(String tone) {
        return "note".equals(tone) ? C_SUB : C_TEXT;
    }

    private int toneChipColor(String tone) {
        if ("head".equals(tone)) return secColor("K");
        if ("note".equals(tone)) return 0xFF39424E;
        return C_HI;
    }

    // ================= LaTeX 渲染引擎（子集） =================
    private static final HashMap<String, String> LX = new HashMap<String, String>();
    private static final HashMap<String, String> LX_ACC = new HashMap<String, String>();
    private static final HashMap<String, String> LX_WRAP = new HashMap<String, String>();
    static {
        String[] pairs = {
            "alpha","α","beta","β","gamma","γ","delta","δ","Delta","Δ",
            "epsilon","ε","varepsilon","ε","zeta","ζ","eta","η","theta","θ","Theta","Θ",
            "iota","ι","kappa","κ","lambda","λ","Lambda","Λ","mu","μ","nu","ν","xi","ξ",
            "pi","π","Pi","Π","rho","ρ","sigma","σ","Sigma","Σ","tau","τ","upsilon","υ",
            "phi","φ","varphi","φ","Phi","Φ","chi","χ","psi","ψ","Psi","Ψ",
            "omega","ω","Omega","Ω",
            "times","×","cdot","·","div","÷","pm","±","mp","∓","ast","*","star","⋆",
            "circ","∘","bullet","•","oplus","⊕","otimes","⊗","sum","∑","prod","∏",
            "int","∫","oint","∮","partial","∂","nabla","∇","hbar","ℏ","ell","ℓ","infty","∞",
            "le","≤","leq","≤","ge","≥","geq","≥","ne","≠","neq","≠","approx","≈",
            "equiv","≡","sim","∼","simeq","≃","propto","∝","ll","≪","gg","≫",
            "in","∈","notin","∉","ni","∋","subset","⊂","subseteq","⊆","subsetneqq","⫋",
            "supset","⊃","supseteq","⊇","cup","∪","cap","∩","emptyset","∅","varnothing","∅",
            "complement","∁","forall","∀","exists","∃","nexists","∄","neg","¬","land","∧","lor","∨",
            "to","→","rightarrow","→","leftarrow","←","leftrightarrow","↔",
            // 化学平衡用得多：可逆箭头 ⇌ 这类字体经常缺字，先映射成符号，后面再按字形兜底
            "rightleftharpoons","⇌","leftrightharpoons","⇌","rightleftarrows","⇄",
            "leftrightarrows","⇄","rightleftharpoonup","↼","updownarrow","↕",
            "longrightarrow","⟶","longleftarrow","⟵","Longrightarrow","⟹","Longleftarrow","⟸",
            "Rightarrow","⇒","Leftarrow","⇐","Leftrightarrow","⇔","implies","⇒","iff","⇔",
            "longrightarrow","⟶","longrightarrow","⟶","uparrow","↑","downarrow","↓",
            "nearrow","↗","searrow","↘","mapsto","↦",
            "angle","∠","perp","⊥","parallel","∥","therefore","∴","because","∵",
            "triangle","△","odot","⊙","square","□","bigcirc","○","arc","⌒",
            "leqslant","≤","geqslant","≥","backslash","＼","mid","|","nmid","∤",
            "degree","°","prime","′","ldots","…","dots","…","cdots","⋯","vdots","⋮","ddots","⋱",
            "sin","sin","cos","cos","tan","tan","cot","cot","sec","sec","csc","csc",
            "arcsin","arcsin","arccos","arccos","arctan","arctan",
            "log","log","ln","ln","lg","lg","lim","lim","exp","exp",
            "max","max","min","min","sup","sup","inf","inf","det","det","gcd","gcd",
            "quad"," ","qquad","\u3000","%","%"
        };
        for (int i = 0; i + 1 < pairs.length; i += 2) LX.put(pairs[i], pairs[i + 1]);
        String[] accs = {
            "overline","\u0305","bar","\u0304",
            "hat","\u0302","widehat","\u0302","tilde","\u0303","widetilde","\u0303",
            "dot","\u0307","ddot","\u0308","underline","\u0332"
        };
        for (int i = 0; i + 1 < accs.length; i += 2) LX_ACC.put(accs[i], accs[i + 1]);
        LX_WRAP.put("text", "");
        LX_WRAP.put("textrm", "");
        LX_WRAP.put("mathrm", "");
        LX_WRAP.put("mathbf", "");
        LX_WRAP.put("mathit", "");
        LX_WRAP.put("operatorname", "");
        LX_WRAP.put("mbox", "");
        // 化学式：\ce{H2O} 之类直接输出内部文本，别把 \ce 露在正文里
        LX_WRAP.put("mathbb", "");
        LX_WRAP.put("mathcal", "");
        LX_WRAP.put("mathfrak", "");
        LX_WRAP.put("mathsf", "");
        LX_WRAP.put("mathtt", "");
        LX_WRAP.put("ce", "");
        LX_WRAP.put("chem", "");
    }

    private static String readArg(String s, int pos, int[] used) {
        int n = s.length();
        if (pos >= n) { used[0] = pos; return ""; }
        if (s.charAt(pos) == '{') {
            int depth = 0, i = pos;
            StringBuilder sb = new StringBuilder();
            while (i < n) {
                char c = s.charAt(i);
                if (c == '{') { depth++; if (depth > 1) sb.append(c); }
                else if (c == '}') {
                    depth--;
                    if (depth == 0) { i++; break; }
                    sb.append(c);
                } else sb.append(c);
                i++;
            }
            used[0] = i;
            return sb.toString();
        }
        char c = s.charAt(pos);
        if (c == '\\') {
            int j = pos + 1;
            while (j < n && Character.isLetter(s.charAt(j))) j++;
            if (j > pos + 1) { used[0] = j; return s.substring(pos, j); }
            used[0] = Math.min(n, pos + 2);
            return s.substring(pos, used[0]);
        }
        used[0] = pos + 1;
        return String.valueOf(c);
    }

    private static String latexToText(String s) {
        if (s == null) return "";
        StringBuilder out = new StringBuilder();
        int i = 0, n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            if (c == '$') { i++; continue; }
            if (c == '&') { out.append(' '); i++; continue; }   // cases/矩阵里的列分隔
            if (c != '\\') { out.append(c); i++; continue; }
            int j = i + 1;
            if (j >= n) { out.append(c); i++; continue; }
            char c1 = s.charAt(j);
            if (!Character.isLetter(c1)) {
                if (c1 == ',' || c1 == ';' || c1 == ':' || c1 == ' ' || c1 == '!') { out.append(' '); }
                else if (c1 == '\\') { out.append('\n'); }
                // 反斜杠加括号/中括号只是公式定界符（模型常用 \( \) 这种写法），丢掉，
                // 别把括号画到正文里
                else if (c1 == '(' || c1 == ')' || c1 == '[' || c1 == ']') { }
                else { out.append(c1); }
                i = j + 1;
                continue;
            }
            int k = j;
            while (k < n && Character.isLetter(s.charAt(k))) k++;
            String cmd = s.substring(j, k);
            int[] used = new int[1];

            if ("vec".equals(cmd) || "overrightarrow".equals(cmd)) {
                String a = latexToText(readArg(s, k, used));
                out.append(MK_V_BEG).append(a).append(MK_V_END);
                i = used[0];
                continue;
            }

            String acc = LX_ACC.get(cmd);
            if (acc != null) {
                String inner = latexToText(readArg(s, k, used));
                if (inner.length() > 0) out.append(inner).append(acc);
                i = used[0];
                continue;
            }
            if ("frac".equals(cmd) || "dfrac".equals(cmd) || "tfrac".equals(cmd)) {
                String a = latexToText(readArg(s, k, used));
                String b = latexToText(readArg(s, used[0], used));
                // 用占位符保留上下结构，交给 applyMarkup 渲染成真分数
                out.append('\uE000').append(a).append('\uE001').append(b).append('\uE002');
                i = used[0];
                continue;
            }
            if ("binom".equals(cmd) || "dbinom".equals(cmd) || "tbinom".equals(cmd)) {
                String a = latexToText(readArg(s, k, used));
                String b = latexToText(readArg(s, used[0], used));
                out.append("C(").append(a).append(",").append(b).append(")");
                i = used[0];
                continue;
            }
            if ("sqrt".equals(cmd)) {
                int p = k;
                String idx = "";
                if (p < n && s.charAt(p) == '[') {
                    int e = s.indexOf(']', p);
                    if (e > 0) { idx = latexToText(s.substring(p + 1, e)); p = e + 1; }
                }
                String a = latexToText(readArg(s, p, used));
                // 根号用占位符保留结构，由 UI 画成带顶部横线的根式
                // n 次根（立方根等）：把次数写成上标，否则只是一个普通数字贴在根号前
                if (idx.length() > 0) out.append("^{").append(idx).append('}');
                out.append('\uE003').append(a).append('\uE004');
                i = used[0];
                continue;
            }
            String wrap = LX_WRAP.get(cmd);
            if (wrap != null) {
                out.append(latexToText(readArg(s, k, used)));
                i = used[0];
                continue;
            }
            if ("left".equals(cmd) || "right".equals(cmd) || "displaystyle".equals(cmd)
                    || "textstyle".equals(cmd) || "limits".equals(cmd) || "nolimits".equals(cmd)
                    || "big".equals(cmd) || "Big".equals(cmd) || "bigl".equals(cmd)
                    || "bigr".equals(cmd) || "Bigl".equals(cmd) || "Bigr".equals(cmd)) {
                i = k;
                if (i < n && (s.charAt(i) == '.' || s.charAt(i) == '{' || s.charAt(i) == '}')) {
                    if (s.charAt(i) == '.') i++;
                }
                continue;
            }
            if ("begin".equals(cmd) || "end".equals(cmd)) {
                readArg(s, k, used);
                i = used[0];
                continue;
            }
            String sym = LX.get(cmd);
            if (sym != null) { out.append(sym); i = k; continue; }
            // 不认识的命令：把参数内容留下、命令名丢掉（\textbf{x} → x）。
            // 这样最多丢样式，不会把 LaTeX 源码当正文显示出来。
            if (k < n && s.charAt(k) == '{') {
                out.append(latexToText(readArg(s, k, used)));
                i = used[0];
                continue;
            }
            i = k;
        }
        return out.toString();
    }

    private static String wrapNum(String a) {
        if (a.length() <= 1) return a;
        return "(" + a + ")";
    }

    // ================= 行内数学排版 =================
    // 结构：文本 / 上标 / 下标 / 分数 / 根号，可任意嵌套（根号里有分数、分数里有根号都能画）
    private static final char MK_F_BEG = '\uE000';
    private static final char MK_F_MID = '\uE001';
    private static final char MK_F_END = '\uE002';
    private static final char MK_R_BEG = '\uE003';
    private static final char MK_R_END = '\uE004';
    private static final char MK_V_BEG = '\uE005';
    private static final char MK_V_END = '\uE006';
    private static final float SUB_SCALE = 0.62f;    // 上下标相对字号
    private static final float FRAC_SCALE = 0.74f;   // 分子分母相对字号

    private static class Inl {
        int type;                 // 0 文本 1 上标 2 下标 3 分数 4 根号 5 组
        String s;
        List<Inl> kids = new ArrayList<Inl>();

        Inl(int t) { type = t; }

        static Inl txt(String v) {
            Inl x = new Inl(0);
            x.s = v;
            return x;
        }

        static Inl script(int t, String v) {
            Inl x = new Inl(t);
            x.s = v;
            return x;
        }

        static Inl frac(Inl a, Inl b) {
            Inl x = new Inl(3);
            x.kids.add(a);
            x.kids.add(b);
            return x;
        }

        static Inl sqrt(Inl a) {
            Inl x = new Inl(4);
            x.kids.add(a);
            return x;
        }

        static Inl vec(Inl a) {
            Inl x = new Inl(6);
            x.kids.add(a);
            return x;
        }

        /** 把列表包成一个组；空列表给空文本，单元素直接返回。 */
        static Inl group(List<Inl> list) {
            if (list.isEmpty()) return Inl.txt("");
            if (list.size() == 1) return list.get(0);
            Inl x = new Inl(5);
            x.kids.addAll(list);
            return x;
        }
    }

    /** 找到分数/根号占位块的结束位置（支持嵌套），返回 {分子分母分隔位, 结束位}。 */
    private static int[] findBlock(String s, int from) {
        int depth = 1, mid = -1, i = from;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == MK_F_BEG || c == MK_R_BEG) depth++;
            else if (c == MK_F_END || c == MK_R_END) {
                depth--;
                if (depth == 0) return new int[]{mid, i};
            } else if (c == MK_F_MID && depth == 1) mid = i;
            i++;
        }
        return new int[]{mid, -1};
    }

    private static List<Inl> parseInl(String s) {
        List<Inl> out = new ArrayList<Inl>();
        StringBuilder buf = new StringBuilder();
        int i = 0, n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            if (c == MK_F_BEG) {
                int[] b = findBlock(s, i + 1);
                if (b[1] < 0 || b[0] < 0) { buf.append(c); i++; continue; }
                if (buf.length() > 0) { out.add(Inl.txt(buf.toString())); buf.setLength(0); }
                out.add(Inl.frac(Inl.group(parseInl(s.substring(i + 1, b[0]))),
                        Inl.group(parseInl(s.substring(b[0] + 1, b[1])))));
                i = b[1] + 1;
            } else if (c == MK_R_BEG) {
                int[] b = findBlock(s, i + 1);
                if (b[1] < 0) { buf.append(c); i++; continue; }
                if (buf.length() > 0) { out.add(Inl.txt(buf.toString())); buf.setLength(0); }
                out.add(Inl.sqrt(Inl.group(parseInl(s.substring(i + 1, b[1])))));
                i = b[1] + 1;
            } else if (c == MK_V_BEG) {
                int e = s.indexOf(MK_V_END, i + 1);
                if (e < 0) { buf.append(c); i++; continue; }
                if (buf.length() > 0) { out.add(Inl.txt(buf.toString())); buf.setLength(0); }
                out.add(Inl.vec(Inl.group(parseInl(s.substring(i + 1, e)))));
                i = e + 1;
            } else if (c == '^' || c == '_') {
                int j = i + 1;
                if (j >= n) { buf.append(c); i++; continue; }
                String arg;
                int next;
                if (s.charAt(j) == '{') {
                    int depth = 0, e = j;
                    StringBuilder sb = new StringBuilder();
                    while (e < n) {
                        char cc = s.charAt(e);
                        if (cc == '{') { depth++; if (depth > 1) sb.append(cc); }
                        else if (cc == '}') {
                            depth--;
                            if (depth == 0) { e++; break; }
                            sb.append(cc);
                        } else sb.append(cc);
                        e++;
                    }
                    arg = sb.toString();
                    next = e;
                } else {
                    arg = String.valueOf(s.charAt(j));
                    next = j + 1;
                }
                if (buf.length() > 0) { out.add(Inl.txt(buf.toString())); buf.setLength(0); }
                out.add(Inl.script(c == '^' ? 1 : 2, arg));
                i = next;
            } else {
                buf.append(c);
                i++;
            }
        }
        if (buf.length() > 0) out.add(Inl.txt(buf.toString()));
        return out;
    }

    /** 测量一组元素，返回 {宽, 基线上高度, 基线下高度}。 */
    /** 墨迹上沿：基线以上多少像素。
     *  文本节点必须用 getTextBounds 量真实字高，不能用 FontMetrics.ascent——后者是整行字体的
     *  上沿（西文约 0.9em），而 x、a 这类字母只有 0.5em 高，用字体上沿画根号横线，
     *  横线就会飘在字母上方老远，看着跟字母没关系（v3.2.0 真机反馈就是这个）。 */
    private static float inkTop(Inl t, Paint p, float size) {
        if (t == null) return size * 0.5f;
        if (t.type == 0) return inkTopText(t.s, p, size);
        if (t.type == 1 || t.type == 2) {
            // 上下标节点的文字存在 s 里，kids 是空的：不能递归 kids，否则直接越界崩掉
            float inner = inkTopText(t.s, p, size * SUB_SCALE);
            if (t.type == 1) return inner + size * 0.34f;
            return Math.max(size * 0.34f, inner - size * 0.20f);
        }
        if (t.type == 3) {
            float s2 = size * FRAC_SCALE;
            return s2 * 0.30f + s2 * 0.14f + inkTop(t.kids.get(0), p, s2);
        }
        if (t.type == 4) return inkTop(t.kids.get(0), p, size) + size * 0.14f;
        if (t.type == 6) return inkTop(t.kids.get(0), p, size) + size * 0.30f;
        float m = size * 0.5f;
        for (Inl k : t.kids) m = Math.max(m, inkTop(k, p, size));
        return m;
    }

    /** 字体自带的根号字形：返回 {宽度, 基线以上高度, 基线以下高度}。 */
    private static float[] sqrtGlyph(Paint p, float size) {
        p.setTextSize(size);
        Rect r = new Rect();
        p.getTextBounds("\u221A", 0, 1, r);
        float top = Math.max(1f, -r.top);
        float bot = Math.max(1f, r.bottom);
        float w = p.measureText("\u221A");
        if (w < 1f) w = size * 0.5f;
        return new float[]{w, top, bot};
    }

    /** 一段文字的墨迹上沿（基线以上）；空白或量不到时给 0.5em 的常规高度。 */
    private static float inkTopText(String str, Paint p, float size) {
        p.setTextSize(size);
        String s = str == null ? "" : str;
        if (s.trim().length() == 0) return size * 0.5f;
        Rect r = new Rect();
        p.getTextBounds(s, 0, s.length(), r);
        float h = -r.top;
        return h <= 1f ? size * 0.5f : h;
    }

    /** 一段文字的墨迹下沿（基线以下）；量不到就是 0。 */
    private static float inkBottomText(String str, Paint p, float size) {
        p.setTextSize(size);
        String s = str == null ? "" : str;
        if (s.trim().length() == 0) return 0f;
        Rect r = new Rect();
        p.getTextBounds(s, 0, s.length(), r);
        return Math.max(0f, r.bottom);
    }

    /** 墨迹下沿：基线以下多少像素。分式的分子要按这个贴到横线上方，
     *  用字体 descent 会把分子顶得太高、分母又被推得太低，中间空一大截。 */
    private static float inkBottom(Inl t, Paint p, float size) {
        if (t == null) return 0f;
        if (t.type == 0) {
            p.setTextSize(size);
            String str = t.s == null ? "" : t.s;
            if (str.trim().length() == 0) return 0f;
            Rect r = new Rect();
            p.getTextBounds(str, 0, str.length(), r);
            return Math.max(0f, r.bottom);
        }
        if (t.type == 1 || t.type == 2) {
            float inner = inkBottomText(t.s, p, size * SUB_SCALE);
            if (t.type == 1) return Math.max(0f, inner - size * 0.34f);
            return inner + size * 0.20f;
        }
        if (t.type == 3) {
            float s2 = size * FRAC_SCALE;
            float gap = s2 * 0.14f, barUp = s2 * 0.30f;
            float d = inkTop(t.kids.get(1), p, s2) + inkBottom(t.kids.get(1), p, s2) + gap - barUp;
            return Math.max(d, s2 * 0.18f);
        }
        if (t.type == 4) {
            return Math.max(inkBottom(t.kids.get(0), p, size), size * 0.06f) + size * 0.08f;
        }
        if (t.type == 6) return inkBottom(t.kids.get(0), p, size);
        float m = 0f;
        for (Inl k : t.kids) m = Math.max(m, inkBottom(k, p, size));
        return m;
    }

    private static float[] measList(Inl grp, Paint p, float size) {
        if (grp.type == 0) {
            p.setTextSize(size);
            float tw = p.measureText(grp.s);
            Paint.FontMetrics fm = p.getFontMetrics();
            return new float[]{tw, Math.max(1f, -fm.ascent), Math.max(1f, fm.descent)};
        }
        if (grp.type == 1 || grp.type == 2) {
            float s2 = size * SUB_SCALE;
            p.setTextSize(s2);
            float tw = p.measureText(grp.s);
            Paint.FontMetrics fm = p.getFontMetrics();
            float up = Math.max(1f, -fm.ascent), dn = Math.max(1f, fm.descent);
            if (grp.type == 1) {
                float shift = size * 0.34f;
                return new float[]{tw, up + shift, Math.max(1f, dn - shift)};
            }
            float shift = size * 0.20f;
            return new float[]{tw, Math.max(1f, up - shift), dn + shift};
        }
        if (grp.type == 3) {
            float s2 = size * FRAC_SCALE;
            float[] a = measList(grp.kids.get(0), p, s2);
            float[] b = measList(grp.kids.get(1), p, s2);
            float gap = s2 * 0.14f, barUp = s2 * 0.30f;
            float w = Math.max(a[0], b[0]) + s2 * 0.26f;
            float asc = barUp + gap + inkBottom(grp.kids.get(0), p, s2)
                    + inkTop(grp.kids.get(0), p, s2);
            float desc = Math.max(inkTop(grp.kids.get(1), p, s2)
                    + inkBottom(grp.kids.get(1), p, s2) + gap - barUp, s2 * 0.18f);
            return new float[]{w, asc, desc};
        }
        if (grp.type == 4) {
            float[] in = measList(grp.kids.get(0), p, size);
            float barH = Math.max(inkTop(grp.kids.get(0), p, size) + size * 0.10f, size * 0.62f);
            float w = size * 0.52f + in[0] + size * 0.03f;
            return new float[]{w, barH, size * 0.10f};
        }
        if (grp.type == 6) {
            float[] in = measList(grp.kids.get(0), p, size);
            return new float[]{in[0], in[1] + size * 0.30f, in[2]};
        }
        float w = 0f, asc = 1f, desc = 1f;
        for (Inl k : grp.kids) {
            float[] m = measList(k, p, size);
            w += m[0];
            asc = Math.max(asc, m[1]);
            desc = Math.max(desc, m[2]);
        }
        return new float[]{w, asc, desc};
    }

    private static void drawList(Canvas c, Paint p, Inl grp, float x, float base,
                                 float size, int color) {
        if (grp.type != 5) {
            drawInl(c, p, grp, x, base, size, color);
            return;
        }
        float cur = x;
        for (Inl k : grp.kids) {
            float[] m = measList(k, p, size);
            drawInl(c, p, k, cur, base, size, color);
            cur += m[0];
        }
    }

    private static void drawCentered(Canvas c, Paint p, Inl grp, float cx, float base,
                                     float size, int color) {
        float[] m = measList(grp, p, size);
        drawList(c, p, grp, cx - m[0] / 2f, base, size, color);
    }

    private static void drawInl(Canvas c, Paint p, Inl t, float x, float base,
                                float size, int color) {
        if (t.type == 0) {
            p.setTextSize(size);
            p.setColor(color);
            p.setTextAlign(Paint.Align.LEFT);
            p.setStyle(Paint.Style.FILL);
            c.drawText(t.s, x, base, p);
        } else if (t.type == 1) {
            p.setTextSize(size * SUB_SCALE);
            p.setColor(color);
            p.setTextAlign(Paint.Align.LEFT);
            p.setStyle(Paint.Style.FILL);
            c.drawText(t.s, x, base - size * 0.34f, p);
        } else if (t.type == 2) {
            p.setTextSize(size * SUB_SCALE);
            p.setColor(color);
            p.setTextAlign(Paint.Align.LEFT);
            p.setStyle(Paint.Style.FILL);
            c.drawText(t.s, x, base + size * 0.20f, p);
        } else if (t.type == 3) {
            float s2 = size * FRAC_SCALE;
            float[] a = measList(t.kids.get(0), p, s2);
            float[] b = measList(t.kids.get(1), p, s2);
            float w = Math.max(a[0], b[0]) + s2 * 0.26f;
            float cx = x + w / 2f;
            float gap = s2 * 0.14f, barUp = s2 * 0.30f;
            float barY = base - barUp;
            // 分子贴横线上方、分母贴下方，用墨迹下沿/上沿定位；用字体 descent 会留出很大一段空白
            drawCentered(c, p, t.kids.get(0), cx,
                    barY - gap - inkBottom(t.kids.get(0), p, s2), s2, color);
            Paint.Style oldStyle = p.getStyle();
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(1.2f, s2 * 0.07f));
            p.setColor(color);
            float half = Math.max(a[0], b[0]) / 2f + s2 * 0.10f;
            c.drawLine(cx - half, barY, cx + half, barY, p);
            p.setStyle(oldStyle);
            drawCentered(c, p, t.kids.get(1), cx,
                    barY + gap + inkTop(t.kids.get(1), p, s2), s2, color);
        } else if (t.type == 4) {
            Inl kid = t.kids.get(0);
            float[] in = measList(kid, p, size);
            float ink = inkTop(kid, p, size);
            // 模仿字体里的 √：左上短横 → 斜向右下到最低点 → 斜向右上到横线高度 → 横线盖住被开方数。
            // 整条是一条 Path 一笔画完，笔宽统一、拐角圆接，勾和横线自然连在一起。
            float barH = Math.max(ink + size * 0.10f, size * 0.62f);
            float barY = base - barH;
            float tickY = Math.min(base - size * 0.10f, barY + size * 0.16f);
            float botY = base + size * 0.10f;
            float x0 = x + size * 0.02f;      // 短横左端
            float x1 = x + size * 0.20f;      // 短横右端
            float x2 = x + size * 0.32f;      // 最低点
            float x3 = x + size * 0.50f;      // 斜升到横线的位置
            float innerX = x + size * 0.52f;  // 被开方数起点
            float right = innerX + in[0] + size * 0.03f;
            Path rad = new Path();
            rad.moveTo(x0, tickY);
            rad.lineTo(x1, tickY);
            rad.lineTo(x2, botY);
            rad.lineTo(x3, barY);
            rad.lineTo(right, barY);
            Paint.Style oldStyle = p.getStyle();
            Paint.Cap oldCap = p.getStrokeCap();
            Paint.Join oldJoin = p.getStrokeJoin();
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
            p.setStrokeWidth(Math.max(1.3f, size * 0.055f));
            p.setColor(color);
            c.drawPath(rad, p);
            p.setStyle(oldStyle);
            p.setStrokeCap(oldCap);
            p.setStrokeJoin(oldJoin);
            drawList(c, p, kid, innerX, base, size, color);
        } else if (t.type == 6) {
            drawList(c, p, t.kids.get(0), x, base, size, color);
            float[] in = measList(t.kids.get(0), p, size);
            float ay = base - in[1] - size * 0.10f;
            float x1 = x + in[0] * 0.08f, x2 = x + in[0] * 0.88f;
            Paint.Style oldStyle = p.getStyle();
            Paint.Cap oldCap = p.getStrokeCap();
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeWidth(Math.max(1.2f, size * 0.055f));
            p.setColor(color);
            c.drawLine(x1, ay, x2, ay, p);
            float h = size * 0.13f;
            c.drawLine(x2, ay, x2 - h, ay - h * 0.75f, p);
            c.drawLine(x2, ay, x2 - h, ay + h * 0.75f, p);
            p.setStyle(oldStyle);
            p.setStrokeCap(oldCap);
        } else {
            drawList(c, p, t, x, base, size, color);
        }
    }

    /** 把一棵行内元素树渲染成一个整体。 */
    private class InlineSpan extends ReplacementSpan {
        private final Inl root;
        private final int script;      // 0 正常 1 上标 2 下标
        private final boolean hi;      // 落在「重点标亮」范围内：用高亮色 + 加粗绘制
        /** 这段公式的原始文本：度量或绘制万一出错，就退回去画原文，绝不让内容凭空消失。 */
        private final String raw;

        InlineSpan(Inl root) { this(root, 0, false, ""); }

        InlineSpan(Inl root, int script) { this(root, script, false, ""); }

        InlineSpan(Inl root, int script, boolean hi) { this(root, script, hi, ""); }

        InlineSpan(Inl root, int script, boolean hi, String raw) {
            this.root = root;
            this.script = script;
            this.hi = hi;
            this.raw = raw == null ? "" : raw;
        }

        /** 公式是自绘的 ReplacementSpan，不会自动继承 ForegroundColorSpan/StyleSpan，
         *  所以标亮状态必须显式带进来，否则分式、根式不会被标亮。 */
        private void applyHi(Paint paint) {
            if (hi) {
                paint.setColor(C_HI);
                paint.setFakeBoldText(true);
            }
        }

        private void clearHi(Paint paint, int color, boolean bold) {
            paint.setColor(color);
            paint.setFakeBoldText(bold);
        }

        private float sizeOf(float orig) {
            return script == 0 ? orig : orig * 0.68f;
        }

        private float baseOf(float orig, float y) {
            if (script == 1) return y - orig * 0.30f;
            if (script == 2) return y + orig * 0.18f;
            return y;
        }

        @Override
        public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
            float orig = paint.getTextSize();
            int oc = paint.getColor();
            boolean ob = paint.isFakeBoldText();
            try {
                int w = measure(paint, fm, orig, oc, ob);
                if (w <= 0 || w > 4000) throw new IllegalStateException("宽度异常 " + w);
                return w;
            } catch (Throwable t) {
                // 度量炸了或结果不合理：按原文留位（下面 draw 也会画原文），内容不会消失
                paint.setTextSize(orig);
                clearHi(paint, oc, ob);
                return Math.max(1, (int) paint.measureText(raw.length() == 0 ? "\u00A0" : raw));
            }
        }

        private int measure(Paint paint, Paint.FontMetricsInt fm, float orig, int oc, boolean ob) {
            applyHi(paint);
            float size = sizeOf(orig);
            float[] m = measList(root, paint, size);
            paint.setTextSize(orig);
            clearHi(paint, oc, ob);
            if (fm != null) {
                float up = m[1], dn = m[2];
                if (script == 1) {
                    up += orig * 0.30f;
                    dn = Math.max(1f, dn - orig * 0.30f);
                } else if (script == 2) {
                    up = Math.max(1f, up - orig * 0.18f);
                    dn += orig * 0.18f;
                }
                fm.ascent = -(int) Math.ceil(up);
                fm.descent = (int) Math.ceil(dn);
                fm.top = fm.ascent;
                fm.bottom = fm.descent;
            }
            return Math.max(1, Math.round(m[0]));
        }

        @Override
        public void draw(Canvas canvas, CharSequence text, int start, int end, float x,
                         int top, int y, int bottom, Paint paint) {
            float orig = paint.getTextSize();
            int color = paint.getColor();
            boolean ob = paint.isFakeBoldText();
            try {
                applyHi(paint);
                drawInl(canvas, paint, root, x, baseOf(orig, y), sizeOf(orig), paint.getColor());
            } catch (Throwable t) {
                // 绘制出错：把这段公式的原文画出来（而不是留一片空白），并记一笔方便排查
                try {
                    paint.setTextSize(orig);
                    paint.setColor(color);
                    paint.setStyle(Paint.Style.FILL);
                    paint.setTextAlign(Paint.Align.LEFT);
                    if (raw.length() > 0) canvas.drawText(raw, x, y, paint);
                    noteRenderError(t, raw);
                } catch (Throwable ignored) { }
            } finally {
                paint.setTextSize(orig);
                clearHi(paint, color, ob);
                paint.setTextAlign(Paint.Align.LEFT);
                paint.setStyle(Paint.Style.FILL);
            }
        }
    }

    /** 高风险符号的等价写法：手表字体是裁剪过的，⇌、⇄、⟶ 这类常常没有字形，
     *  直接画会变成方框（看着就是乱码）。每行第一个是原字符，后面是依次尝试的备选。 */
    private static final String[][] GLYPH_FALLBACK = {
            {"⇌", "↔", "<->"}, {"⇄", "↔", "<->"}, {"⇆", "↔", "<->"}, {"⇋", "↔", "<->"},
            {"⇔", "↔", "<->"}, {"⟷", "↔", "<->"}, {"↔", "<->", "-"},
            {"⟶", "→", "->"}, {"⟵", "←", "<-"}, {"⟹", "→", "=>"}, {"⟸", "←", "<="},
            {"→", "->", ">"}, {"←", "<-", "<"}, {"⇒", "=>", ">"}, {"⇐", "<=", "<"},
            {"↕", "↑↓", "|"}, {"↼", "<-", "-"}, {"⇀", "->", "-"},
            {"∑", "Σ", "sum"}, {"∏", "Π", "prod"}, {"∫", "∫", "S"}, {"∮", "∫", "S"},
            {"≈", "~", "="}, {"≦", "<=", "<"}, {"≧", ">=", ">"},
            {"≤", "<=", "<"}, {"≥", ">=", ">"}, {"≠", "!=", "!"},
            {"±", "+-", "+"}, {"×", "x", "x"}, {"÷", "/", "/"}, {"·", ".", "."},
            {"°", "o", "o"}, {"√", "√", "v"}, {"∂", "d", "d"}, {"∞", "inf", "8"},
            {"∈", "in", "e"}, {"∪", "U", "u"}, {"∩", "n", "n"}
    };
    private Paint glyphProbe = null;

    private void ensureGlyphProbe() {
        if (glyphProbe != null) return;
        glyphProbe = new Paint();
        glyphProbe.setTypeface(Typeface.DEFAULT);
        glyphProbe.setTextSize(16f * getResources().getDisplayMetrics().scaledDensity);
        glyphProbe.setAntiAlias(true);
    }

    private boolean hasGlyph(String ch) {
        ensureGlyphProbe();
        try { return glyphProbe.hasGlyph(ch); } catch (Throwable t) { return true; }
    }

    /** 这枚字符在字体里能用哪种写法：原字符有字形就用原来的，否则依次试备选。 */
    private String pickGlyph(String ch) {
        for (String[] row : GLYPH_FALLBACK) {
            if (!row[0].equals(ch)) continue;
            if (hasGlyph(row[0])) return row[0];
            for (int k = 1; k < row.length; k++) {
                if (hasGlyph(row[k])) return row[k];
            }
            return row[row.length - 1];
        }
        return null;
    }

    /** 把字体画不出来的符号换成等价写法；文本里没有高风险符号时原样返回（不做逐字检测）。 */
    private String fontSafe(String src) {
        if (src == null || src.length() == 0) return "";
        boolean need = false;
        for (String[] row : GLYPH_FALLBACK) {
            if (src.indexOf(row[0]) >= 0) { need = true; break; }
        }
        if (!need) return src;
        StringBuilder out = new StringBuilder(src.length() + 8);
        for (int i = 0; i < src.length(); i++) {
            String ch = String.valueOf(src.charAt(i));
            String rep = pickGlyph(ch);
            out.append(rep == null ? ch : rep);
        }
        return out.toString();
    }

    /** 思考内容按「可读文本」显示：命令换成符号、丢掉公式占位符、缺字换等价写法。 */
    private String prettyReasoning(String r) {
        if (r == null || r.length() == 0) return "";
        return fontSafe(humanizeRaw(latexToText(r)));
    }

    /** Markdown 折成「纯文本 + 我们认识的标记」：标题加粗、列表换圆点、代码用等宽、
     *  链接只留文字、表格去掉竖线。宁可少显示结构，也不要把 # ` | 这些符号甩给用户。 */
    private static String mdFold(String src) {
        if (src == null || src.length() == 0) return "";
        String s = src;
        // 围栏代码块：去掉 ``` 围栏，保留内容
        s = s.replaceAll("(?m)^[ \t]*```[^\n]*\n?", "");
        // 图片 / 链接：只留文字
        s = s.replaceAll("!\\[([^\\]]*)\\]\\([^)]*\\)", "$1");
        s = s.replaceAll("\\[([^\\]]*)\\]\\([^)]*\\)", "$1");
        // 行内代码：用私有标记包住，渲染层套等宽字体
        s = s.replaceAll("`([^`\n]*)`", "\uE009$1\uE00A");
        // 分隔线
        s = s.replaceAll("(?m)^[ \t]*([-*_]){3,}[ \t]*$", "────────");
        // 表格：只处理「整行以 | 开头并以 | 结尾」的行，把行内竖线换空格。
        // 不能全局替换 |，否则数学里的绝对值 |x| 会变成 " x "。
        java.util.regex.Matcher tm = java.util.regex.Pattern
                .compile("(?m)^[ \t]*\\|(.+)\\|[ \t]*$").matcher(s);
        StringBuffer tbuf = new StringBuffer();
        while (tm.find()) {
            tm.appendReplacement(tbuf, java.util.regex.Matcher.quoteReplacement(
                    " " + tm.group(1).replace('|', ' ') + " "));
        }
        tm.appendTail(tbuf);
        s = tbuf.toString();
        // 对齐行（|---|---| 之类）整行删掉
        s = s.replaceAll("(?m)^[ \t]*[-:| ]{3,}[ \t]*$", "");
        // 标题：# 开头 → 整行加粗
        s = s.replaceAll("(?m)^[ \t]{0,3}#{1,6}[ \t]*(.+?)[ \t]*#*[ \t]*$", "**$1**");
        // 引用：> 开头 → 竖线
        s = s.replaceAll("(?m)^[ \t]{0,3}>[ \t]?", "│ ");
        // 删除线
        s = s.replaceAll("~~([^~\n]*)~~", "$1");
        return s;
    }

    /** 把内部占位符还原成人能看的写法：兜底显示原文时不能把 \uE000 这种私用字符甩给用户。 */
    private static String humanizeRaw(String s) {
        if (s == null) return "";
        return s.replace('\uE000', ' ').replace('\uE001', '/').replace('\uE002', ' ')
                .replace('\uE003', '\u221A').replace('\uE004', ' ')
                .replace('\uE005', ' ').replace('\uE006', ' ');
    }

    /** 记一条诊断（和公式异常共用 render.log，限 4KB），用来查「空回复」这类现象。 */
    private void noteDiag(String text) {
        try {
            String line = "· " + text + "\n";
            java.io.File f = new java.io.File(getFilesDir(), "render.log");
            if (!f.exists() || f.length() > 4096) {
                java.io.FileOutputStream fos = new java.io.FileOutputStream(f, false);
                fos.write((line).getBytes(StandardCharsets.UTF_8));
                fos.close();
            } else {
                java.io.FileOutputStream fos = new java.io.FileOutputStream(f, true);
                fos.write(line.getBytes(StandardCharsets.UTF_8));
                fos.close();
            }
        } catch (Throwable ignored) { }
    }

    /** 记一笔公式排版错误（最多留 4KB）：这些错误现在不崩了，但要留下线索。 */
    private void noteRenderError(Throwable t, String raw) {
        try {
            String line = t.getClass().getSimpleName() + "："
                    + (raw == null ? "" : (raw.length() > 120 ? raw.substring(0, 120) : raw)) + "\n";
            java.io.File f = new java.io.File(getFilesDir(), "render.log");
            if (!f.exists() || f.length() > 4096) {
                java.io.FileOutputStream fos = new java.io.FileOutputStream(f, false);
                fos.write(line.getBytes(StandardCharsets.UTF_8));
                fos.close();
            } else {
                java.io.FileOutputStream fos = new java.io.FileOutputStream(f, true);
                fos.write(line.getBytes(StandardCharsets.UTF_8));
                fos.close();
            }
        } catch (Throwable ignored) { }
    }

    private SpannableStringBuilder applyMarkup(String src) {
        return applyMarkup(src, false);
    }

    /** @param hl 当前是否处在 **重点标亮** 内；公式不会自动继承颜色 span，需显式传递。 */
    private SpannableStringBuilder applyMarkup(String src, boolean hl) {
        SpannableStringBuilder ssb = new SpannableStringBuilder();
        String txt = latexToText(mdFold(src));
        int i = 0, n = txt.length();
        while (i < n) {
            char c = txt.charAt(i);
            if (c == '\uE000' || c == '\uE003') {
                int[] b = findBlock(txt, i + 1);
                if (b[1] > i) {
                    String block = txt.substring(i, b[1] + 1);
                    Inl root = Inl.group(parseInl(block));
                    int st = ssb.length();
                    ssb.append('\u00A0');
                    ssb.setSpan(new InlineSpan(root, 0, hl, humanizeRaw(block)), st, ssb.length(),
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    i = b[1] + 1;
                    continue;
                }
                i++;
                continue;
            }
            if (c == '\uE005') {
                int e = txt.indexOf('\uE006', i + 1);
                if (e > i) {
                    String block = txt.substring(i, e + 1);
                    Inl root = Inl.group(parseInl(block));
                    int st = ssb.length();
                    ssb.append('\u00A0');
                    ssb.setSpan(new InlineSpan(root, 0, hl, humanizeRaw(block)), st, ssb.length(),
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    i = e + 1;
                    continue;
                }
                i++;
                continue;
            }
            if (c == '\uE009') {                     // Markdown 行内代码
                int e = txt.indexOf('\uE00A', i + 1);
                if (e > i) {
                    String code = txt.substring(i + 1, e);
                    int st = ssb.length();
                    ssb.append(code);
                    ssb.setSpan(new TypefaceSpan("monospace"), st, ssb.length(),
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    ssb.setSpan(new ForegroundColorSpan(C_HI), st, ssb.length(),
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    i = e + 1;
                    continue;
                }
                i++;
                continue;
            }
            if ((c == '^' || c == '_') && i + 1 < n) {
                boolean sup = (c == '^');
                int j = i + 1;
                String inner;
                int next;
                if (txt.charAt(j) == '{') {
                    int depth = 0, e = j;
                    StringBuilder sb = new StringBuilder();
                    while (e < n) {
                        char cc = txt.charAt(e);
                        if (cc == '{') { depth++; if (depth > 1) sb.append(cc); }
                        else if (cc == '}') {
                            depth--;
                            if (depth == 0) { e++; break; }
                            sb.append(cc);
                        } else sb.append(cc);
                        e++;
                    }
                    inner = sb.toString();
                    next = e;
                } else {
                    char nx = txt.charAt(j);
                    boolean mark = Character.isLetterOrDigit(nx)
                            || nx == '+' || nx == '-' || nx == '−' || nx == '°' || nx == '′';
                    if (!mark) { ssb.append(c); i++; continue; }
                    inner = String.valueOf(nx);
                    next = j + 1;
                }
                int start = ssb.length();
                boolean hasBlock = inner.indexOf(MK_F_BEG) >= 0 || inner.indexOf(MK_R_BEG) >= 0;
                if (hasBlock) {
                    Inl root = Inl.group(parseInl(inner));
                    ssb.append('\u00A0');
                    ssb.setSpan(new InlineSpan(root, sup ? 1 : 2, hl), start, ssb.length(),
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    i = next;
                    continue;
                }
                ssb.append(inner);
                if (sup) ssb.setSpan(new SuperscriptSpan(), start, ssb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                else ssb.setSpan(new SubscriptSpan(), start, ssb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                ssb.setSpan(new RelativeSizeSpan(0.64f), start, ssb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                i = next;
            } else if (c == '*' && i + 1 < n && txt.charAt(i + 1) == '*') {
                int end = txt.indexOf("**", i + 2);
                if (end < 0) { ssb.append(c); i++; continue; }
                String inner = txt.substring(i + 2, end);
                // 递归处理重点内容内的分式、根式与上下标，再整体加粗 + 高亮色
                SpannableStringBuilder innerSb = applyMarkup(inner, true);
                int start = ssb.length();
                ssb.append(innerSb);
                ssb.setSpan(new StyleSpan(Typeface.BOLD), start, ssb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                ssb.setSpan(new ForegroundColorSpan(C_HI), start, ssb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                i = end + 2;
            } else {
                ssb.append(c);
                i++;
            }
        }
        return ssb;
    }

    // ================= 设置页（分类） =================
    /** 设置页里的一行入口：圆角底、居中、可带图标。所有入口都走这一个方法，样式不会各写一套。 */
    private TextView settingsEntry(String text, String icon, int color, boolean bold,
                                   View.OnClickListener listener) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(m.backSize);
        if (bold) v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setTextColor(color);
        v.setGravity(Gravity.CENTER);
        v.setPadding(dp(10), dp(10), dp(10), dp(10));
        v.setBackground(clickBg(C_PILL));
        if (icon != null) applyIcon(v, icon, color, 15);
        v.setOnClickListener(listener);
        return v;
    }

    /** 入口 / 开关下面那行小灰字。afterToggle 时下边多留一点，和下一块分开。 */
    private TextView settingsHint(String text, boolean afterToggle) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(m.subtitleSize - 2f);
        v.setTextColor(C_SUB);
        v.setGravity(Gravity.CENTER);
        v.setPadding(dp(4), dp(afterToggle ? 2 : 3), dp(4), dp(afterToggle ? 4 : 2));
        return v;
    }

    /** 只加上边距的布局参数（同组里第二个入口起用）。 */
    private LinearLayout.LayoutParams topGap(int topDp) {
        LinearLayout.LayoutParams lp = wrap();
        lp.topMargin = dp(topDp);
        return lp;
    }

    private void renderSettings() {
        // 原地重绘（切开关、清缓存）时保住滚动位置：以前末尾无条件 scrollToTop，
        // 点一次开关就被弹回最上面，得重新往下滑回原来那块。
        int keepY = (settingsInner.getChildCount() > 0) ? settingsScroll.getScrollY() : -1;
        settingsInner.removeAllViews();
        if (overDist() > 0) settingsInner.addView(makeScrollSpacer());
        settingsInner.addView(settingsBackBtn, wrapM());

        // 分组按「用户在找什么」来分。以前「背单词设置」挂到「问 AI」标题下面、
        // 「个人主页」和「首页科目排序」混在「其他」里，找东西只能靠记位置。
        settingsInner.addView(sectionHeader("显示"), wrap());
        settingsInner.addView(settingsEntry("显示调节", "ic_settings", C_PRIMARY, true,
                new View.OnClickListener() {
                    public void onClick(View v) {
                        vibrate();
                        tuningFirstRun = false;
                        showScreen(S_DISPLAY);
                    }
                }), wrap());
        settingsInner.addView(settingsHint("调整字号和整体缩放。", false), wrap());
        settingsInner.addView(toggleRow("震动反馈", haptic, new View.OnClickListener() {
            public void onClick(View v) {
                haptic = !haptic;
                prefs.edit().putBoolean("haptic", haptic).apply();
                // 打开的那一刻震一下（说明已经生效）；关掉时不震
                if (haptic) vibrate();
                renderSettings();
            }
        }, false), wrap());
        settingsInner.addView(settingsHint("点击时轻微震动。", true), wrap());
        settingsInner.addView(toggleRow("右滑返回", swipeBack, new View.OnClickListener() {
            public void onClick(View v) {
                swipeBack = !swipeBack;
                prefs.edit().putBoolean("swipe_back", swipeBack).apply();
                renderSettings();
            }
        }), wrap());
        settingsInner.addView(settingsHint("从左边缘向右滑，返回上一页。", true), wrap());

        settingsInner.addView(sectionHeader("学习"), wrap());
        settingsInner.addView(settingsEntry("背单词设置", "ic_assignment", C_PRIMARY, true,
                new View.OnClickListener() {
                    public void onClick(View v) {
                        vibrate();
                        wordSettingBack = S_SETTINGS;
                        showScreen(S_WORD_SETTINGS);
                    }
                }), wrap());
        settingsInner.addView(settingsHint("词书、每天新词数、发音与助记图。", false), wrap());
        settingsInner.addView(settingsEntry("首页科目排序", "ic_sort", C_PRIMARY, false,
                new View.OnClickListener() {
                    public void onClick(View v) {
                        vibrate();
                        showScreen(S_ORDER);
                    }
                }), topGap(6));
        settingsInner.addView(settingsHint("调整主页科目的先后顺序。", false), wrap());

        settingsInner.addView(sectionHeader("问 AI"), wrap());
        settingsInner.addView(settingsEntry("问 AI", "ic_ai", C_PRIMARY, true,
                new View.OnClickListener() {
                    public void onClick(View v) {
                        vibrate();
                        showScreen(S_AI_HUB);
                    }
                }), wrap());
        settingsInner.addView(settingsHint("模型、接口与网页版入口。", false), wrap());

        settingsInner.addView(sectionHeader("账号与反馈"), wrap());
        settingsInner.addView(settingsEntry("个人主页", "ic_person", C_PRIMARY, true,
                new View.OnClickListener() {
                    public void onClick(View v) {
                        vibrate();
                        showScreen(S_PROFILE);
                    }
                }), wrap());
        settingsInner.addView(settingsHint("昵称、头像与我的反馈。", false), wrap());
        settingsInner.addView(settingsEntry("反馈与建议", "ic_feedback", C_PRIMARY, false,
                new View.OnClickListener() {
                    public void onClick(View v) {
                        vibrate();
                        goBack(S_FEEDBACK);
                    }
                }), topGap(6));
        settingsInner.addView(settingsHint("提交问题或建议，需要 GitHub 账号。", false), wrap());

        settingsInner.addView(sectionHeader("更新与维护"), wrap());
        settingsInner.addView(settingsEntry("检查更新", "ic_update", C_PRIMARY, false,
                new View.OnClickListener() {
                    public void onClick(View v) {
                        vibrate();
                        checkUpdate(false);
                    }
                }), wrap());
        settingsInner.addView(settingsHint("下载并安装新版本。", false), wrap());
        // 测试通道：默认关。关着时查 /releases/latest，服务端就不返回预发布，
        // 普通用户永远看不到测试包；只有自己开这一项，才把列表里的预发布也算进更新。
        settingsInner.addView(toggleRow("接收测试版", betaChannel, new View.OnClickListener() {
            public void onClick(View v) {
                betaChannel = !betaChannel;
                prefs.edit().putBoolean("beta_channel", betaChannel).apply();
                renderSettings();
            }
        }), wrap());
        settingsInner.addView(settingsHint("收到预发布版本，自测用。", true), wrap());
        settingsInner.addView(settingsEntry("清理缓存", "ic_clean", C_PRIMARY, false,
                new View.OnClickListener() {
                    public void onClick(View v) {
                        vibrate();
                        confirmClearCache();
                    }
                }), topGap(8));
        settingsInner.addView(settingsHint(cacheSizeHint(), false), wrap());
        // 记录日志开关：点击后由 toggleRow 自己震动，这里只处理状态切换。
        settingsInner.addView(toggleRow("记录日志", logEnabled, new View.OnClickListener() {
            public void onClick(View v) {
                if (logEnabled) {
                    stopLogging();
                    toast("已停止记录日志");
                    renderSettings();
                } else {
                    showLogEnableDialog();
                }
            }
        }), topGap(8));
        settingsInner.addView(settingsHint("开启后在本机记录运行日志，方便排查问题。", true), wrap());
        settingsInner.addView(settingsEntry("重新连接服务器", "ic_route", C_PRIMARY, false,
                new View.OnClickListener() {
                    public void onClick(View v) {
                        vibrate();
                        if (routeHint != null) {
                            routeHint.setTextColor(C_SUB);
                            routeHint.setText("正在检测各条线路…");
                        }
                        startRouteProbe(true);
                    }
                }), topGap(8));
        routeHint = settingsHint("", false);
        settingsInner.addView(routeHint, wrap());
        refreshRouteHint();

        settingsInner.addView(sectionHeader("关于"), wrap());
        settingsInner.addView(settingsEntry("关于本应用", "ic_info", C_PRIMARY, true,
                new View.OnClickListener() {
                    public void onClick(View v) {
                        vibrate();
                        showScreen(S_ABOUT);
                    }
                }), wrap());
        settingsInner.addView(settingsHint("版本、更新日志与感谢名单。", false), wrap());
        // 会清东西的操作单独放最后，不和「关于」挤在一起
        settingsInner.addView(settingsEntry("恢复默认设置", null, 0xFFFFB74D, false,
                new View.OnClickListener() {
                    public void onClick(View v) {
                        vibrate();
                        // 误触代价太大（以前一点就直接执行，还连收藏一起清掉），先确认
                        confirmAction("恢复默认设置？",
                                "字号、缩放、震动、右滑返回和科目顺序回到默认。收藏、浏览记录、每日一句和登录都保留。",
                                "确认恢复", 0xFFE57373, new Runnable() {
                                    public void run() { resetSettingsToDefault(); }
                                });
                    }
                }), topGap(12));

        if (overDist() > 0) settingsInner.addView(makeScrollSpacer());
        if (keepY >= 0) scrollToY(settingsScroll, keepY);   // 重绘：回到原来的位置
        else scrollToTop(settingsScroll);                   // 首次进入：贴顶
    }

    // ---- 清理缓存 ----
    // 只清发音和助记图（这两样都能重新下），词书正文、.idx、.prog 和收藏一律不碰。

    private long wordCacheSize() {
        return dirSize(voiceDir()) + dirSize(picDir());
    }

    private long dirSize(File d) {
        if (d == null || !d.isDirectory()) return 0L;
        File[] fs = d.listFiles();
        if (fs == null) return 0L;
        long n = 0L;
        for (File f : fs) n += f.isDirectory() ? dirSize(f) : f.length();
        return n;
    }

    private static String sizeText(long b) {
        if (b >= 1024L * 1024L) return String.format(java.util.Locale.US, "%.1f MB", b / 1048576f);
        if (b >= 1024L) return (b / 1024L) + " KB";
        return b + " B";
    }

    /** 设置页那行小字：现在占多少。 */
    private String cacheSizeHint() {
        long n = wordCacheSize();
        return n > 0 ? "发音和助记图缓存，共 " + sizeText(n) + "。"
                : "发音和助记图缓存，暂时是空的。";
    }

    private void confirmClearCache() {
        final long n = wordCacheSize();
        if (n <= 0) { toast("没有可清理的缓存"); return; }
        confirmAction("清理缓存？",
                "发音和助记图缓存共 " + sizeText(n) + "，清掉之后再用到会重新下载。词书和背诵记录不受影响。",
                "确认清理", 0xFFFFB74D, new Runnable() {
                    public void run() { clearWordCaches(); }
                });
    }

    private void clearWordCaches() {
        long before = wordCacheSize();
        stopVoice();                 // 正在播的先停，否则文件可能被占用
        clearDir(voiceDir());
        clearDir(picDir());
        wPicBm = null;               // 当前词的助记图也在内存里，一起放掉
        wPicUrl = null;
        toast(before > 0 ? "已清理 " + sizeText(before) : "没有可清理的缓存");
        renderSettings();
    }

    /** 删目录里的文件，目录本身留着（下次写入不用重建）。 */
    private void clearDir(File d) {
        if (d == null || !d.isDirectory()) return;
        File[] fs = d.listFiles();
        if (fs == null) return;
        for (File f : fs) {
            if (f.isDirectory()) clearDir(f);
            else deleteQuietly(f);
        }
    }


    /** 「恢复默认设置」：只重置设置类键值，收藏/历史/每日一句/登录这些用户数据一律不动。
     *  以前是 prefs.edit().clear()，一点就把收藏和登录一起清了（用户反馈「全部东西都没了」）。 */
    private void resetSettingsToDefault() {
        prefs.edit()
                .remove("font_size").remove("list_size").remove("title_size").remove("sub_size")
                .remove("ui_scale")
                .remove("swipe_back").remove("haptic")
                .remove("subject_order")
                .apply();
        fontSizeSp = isRound ? 13f : 14f;
        listSizeSp = isRound ? 14f : 15f;
        titleSizeSp = 0f;
        subSizeSp = 0f;
        uiScale = 1f;
        swipeBack = true;
        haptic = true;
        orderKeys.clear();
        for (String k : DEFAULT_ORDER) if (subjectMap.containsKey(k)) orderKeys.add(k);
        saveOrderKeys();
        markSubjectsDirty();
        toast("已恢复默认设置");
        // 字号是建界面时的快照，改完必须就地重建，否则界面还是旧样子
        applyDisplayChange(S_SETTINGS);
    }

    private TextView sectionHeader(String label) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setGravity(Gravity.CENTER);
        t.setTextSize(m.subtitleSize);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(C_PRIMARY);
        t.setPadding(dp(2), dp(10), dp(2), dp(6));
        return t;
    }

    private View orderRow(final int idx) {
        final String key = orderKeys.get(idx);
        final String name = subjectMap.get(key).name;
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(8), dp(4), dp(8), dp(4));
        // 行自己不画底色、也不留行距：圆角底由 renderOrder 的外层容器统一给。
        // 每行各画一个圆角卡片会排成一摞独立胶囊，上下又不相连，看着很碎。

        TextView nm = new TextView(this);
        nm.setText((idx + 1) + ". " + name);
        nm.setTextSize(m.backSize);
        nm.setTextColor(C_TEXT);
        nm.setPadding(dp(4), dp(4), dp(4), dp(4));
        nm.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView up = orderBtn("▲", idx > 0, new View.OnClickListener() {
            public void onClick(View v) {
                if (idx <= 0) return;
                String k = orderKeys.remove(idx);
                orderKeys.add(idx - 1, k);
                saveOrderKeys(); markSubjectsDirty(); renderOrder();   // 排序页自己重画，别去重画设置页
            }
        });
        TextView dn = orderBtn("▼", idx < orderKeys.size() - 1, new View.OnClickListener() {
            public void onClick(View v) {
                if (idx >= orderKeys.size() - 1) return;
                String k = orderKeys.remove(idx);
                orderKeys.add(idx + 1, k);
                saveOrderKeys(); markSubjectsDirty(); renderOrder();   // 排序页自己重画，别去重画设置页
            }
        });

        row.addView(nm);
        row.addView(up);
        row.addView(dn);
        return row;
    }

    private TextView orderBtn(String label, boolean enabled, final View.OnClickListener onClick) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextSize(m.backSize);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(enabled ? C_PRIMARY : 0xFF555555);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(12), dp(6), dp(12), dp(6));
        t.setBackgroundDrawable(pill(enabled ? 0xFF242424 : 0xFF151515));
        if (enabled) t.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                vibrate();
                onClick.onClick(v);
            }
        });
        return t;
    }

    /** 开关型设置行：点整行切换，右侧显示开/关。 */
    private View toggleRow(String label, boolean on, View.OnClickListener onClick) {
        return toggleRow(label, on, onClick, true);
    }

    /** autoVibrate=false 用于「震动反馈」这一行自己：它要按切换后的状态决定震不震，
     *  不能让这里先震（关→开时还关着，震不出来；开→关时又白震一下）。 */
    private View toggleRow(String label, boolean on, final View.OnClickListener onClick,
                           final boolean autoVibrate) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10), dp(8), dp(10), dp(8));
        row.setBackgroundDrawable(pill(C_CARD));
        row.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (autoVibrate) vibrate();
                if (onClick != null) onClick.onClick(v);
            }
        });

        TextView name = new TextView(this);
        name.setText(label);
        name.setTextSize(m.backSize);
        name.setTextColor(C_TEXT);
        name.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView state = new TextView(this);
        state.setText(on ? "开" : "关");
        state.setTextSize(m.backSize);
        state.setTypeface(Typeface.DEFAULT_BOLD);
        state.setTextColor(on ? C_PRIMARY : C_SUB);
        state.setGravity(Gravity.CENTER);
        state.setPadding(dp(14), dp(3), dp(14), dp(3));
        state.setBackgroundDrawable(pill(on ? 0xFF14384A : C_PILL));

        row.addView(name);
        row.addView(state);
        return row;
    }

    private View settingRow(String label, int value, int min, int max,
                            View.OnClickListener minus, View.OnClickListener plus) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(8), dp(7), dp(8), dp(7));
        row.setBackgroundDrawable(pill(C_CARD));
        LinearLayout.LayoutParams rlp = wrap();
        rlp.bottomMargin = dp(4);

        TextView name = new TextView(this);
        name.setText(label);
        name.setTextSize(m.backSize);
        name.setTextColor(C_TEXT);
        name.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        boolean canMinus = value > min;
        TextView minusBtn = new TextView(this);
        minusBtn.setText(" − ");
        minusBtn.setTextSize(16f * uiScale);
        minusBtn.setTypeface(Typeface.DEFAULT_BOLD);
        minusBtn.setTextColor(canMinus ? C_PRIMARY : 0xFF3E3E3E);
        minusBtn.setPadding(dp(12), dp(4), dp(12), dp(4));
        minusBtn.setEnabled(canMinus);
        final View.OnClickListener minusU = minus;
        minusBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { vibrate(); if (minusU != null) minusU.onClick(v); }
        });

        TextView val = new TextView(this);
        val.setText(value + "sp");
        val.setTextSize(m.backSize);
        val.setTextColor(Color.WHITE);
        val.setGravity(Gravity.CENTER);
        val.setPadding(dp(6), 0, dp(6), 0);

        boolean canPlus = value < max;
        TextView plusBtn = new TextView(this);
        plusBtn.setText(" + ");
        plusBtn.setTextSize(16f * uiScale);
        plusBtn.setTypeface(Typeface.DEFAULT_BOLD);
        plusBtn.setTextColor(canPlus ? C_PRIMARY : 0xFF3E3E3E);
        plusBtn.setPadding(dp(12), dp(4), dp(12), dp(4));
        plusBtn.setEnabled(canPlus);
        final View.OnClickListener plusU = plus;
        plusBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { vibrate(); if (plusU != null) plusU.onClick(v); }
        });

        row.addView(name);
        row.addView(minusBtn);
        row.addView(val);
        row.addView(plusBtn);
        return row;
    }

    private float clamp(float v, float min, float max) {
        if (v < min) return min;
        if (v > max) return max;
        return v;
    }

    private void saveSettings() {
        SharedPreferences.Editor e = prefs.edit();
        e.putFloat("font_size", fontSizeSp);
        e.putFloat("list_size", listSizeSp);
        e.apply();
    }

    /** 首次进入某页时才建它的内容。
     *  启动时把 20 多个页面全建一遍要一两秒（WebView、AI 对话、显示调节都不轻），
     *  而首屏只用得到主页 —— 其余等真正点进去再建，那点开销分摊到每次点击上就感觉不到。
     *  只延后「内容」，屏幕本身仍由 buildUi 一次性 newScreen 并 addView，screens[] 下标不变。 */
    private void ensureScreenBuilt(int s) {
        if (s < 0 || s >= SCREEN_COUNT || screenBuilt[s]) return;
        screenBuilt[s] = true;
        switch (s) {
            case S_FEEDBACK: buildFeedbackScreen(); break;
            case S_PROFILE: buildProfileScreen(); break;
            case S_CHANGELOG: buildChangelogScreen(); break;
            case S_ABOUT: buildAboutScreen(); break;
            case S_DISPLAY: buildDisplayScreen(); break;
            case S_ORDER: buildOrderScreen(); break;
            case S_TODAY: buildTodayScreen(); break;
            case S_AI_WEB: buildAiWebScreen(); break;
            case S_AI_HUB: buildAiHubScreen(); break;
            case S_AI_API_CFG: buildAiApiScreen(); break;
            case S_AI_WEB_CFG: buildAiWebCfgScreen(); break;
            case S_AI_CHAT: buildAiChatScreen(); break;
            case S_WORD_HOME: buildWordHomeScreen(); break;
            case S_WORD_STUDY: buildWordStudyScreen(); break;
            case S_WORD_SEARCH: buildWordSearchScreen(); break;
            case S_WORD_SETTINGS: buildWordSettingsScreen(); break;
            case S_WORD_VOCAB: buildWordVocabScreen(); break;
            default: break;
        }
    }

    // ================= 页面切换（带动画） =================
    private void showScreen(int s) {
        final int old = screen;
        if (old == S_WORD_STUDY && s != S_WORD_STUDY) stopVoice();   // 离开学习页就闭嘴
        ensureScreenBuilt(s);     // 懒建的页在这里补上内容，后面的渲染分支才不会拿到空容器
        // 右滑返回：preparePrev 已经把「要退回的那一页」渲染好、摆在离开时的滚动位置并露出来了。
        // 这条路径只做可见性确认，绝不重新 bind / 重新定位，否则列表会被清空重载（闪一下）或弹回顶端。
        if (swipeNavigating) {
            swipeNavigating = false;
            View[] ss = {subjectScreen, nodeScreen, detailScreen, settingsScreen,
                    searchScreen, feedbackScreen, mineScreen, profileScreen, changelogScreen, aboutScreen,
                    displayScreen, todayScreen, extraScreen, favScreen, histScreen, favSubScreen,
                    dailyScreen, sentScreen, aiWebScreen, aiHubScreen, aiApiScreen, aiWebCfgScreen,
                    aiChatScreen, orderScreen, wordHomeScreen, wordStudyScreen, wordSearchScreen,
                    wordSettingsScreen, wordVocabScreen};
            screen = s;
            for (int i = 0; i < ss.length; i++) {
                if (i == s) continue;
                ss[i].animate().cancel();
                ss[i].setVisibility(View.GONE);
                ss[i].setTranslationX(0f);
                ss[i].setTranslationY(0f);
                ss[i].setAlpha(1f);
            }
            View t = ss[s];
            t.animate().cancel();
            t.setTranslationX(0f);
            t.setTranslationY(0f);
            t.setAlpha(1f);
            t.setVisibility(View.VISIBLE);
            restoreNext[s] = false;
            skipPageAnim = false;
            SvList sl = svOf(s);
            if (sl != null) sl.onReady = null;
            return;
        }
        SvList leaving = svOf(screen);
        if (leaving != null) {
            // 记下离开时的滚动位置，返回本页时恢复。
            // 但**同页换层（s == screen，如章节→科目）绝不能记**：那时 leaving 就是目标列表自己，
            // 记下来会把 bind() 刚设好的「上一层离开位置」覆盖成当前层的位置，导致返回时弹回顶端。
            if (screen != s) leaving.savedY = leaving.sc.getScrollY();
            // 统一归位：防御性地清掉可能残留的位移/透明度。
            leaving.sc.setTranslationX(0f);
            leaving.sc.setAlpha(1f);
        }
        // 离开的是普通滚动页：记下当前滚动位置，返回时复位（修复「返回上一级定位错误/回到顶端」）
        ScrollView leavingSc = scrollOf(screen);
        if (leavingSc != null) savedScrollY[screen] = leavingSc.getScrollY();
        screen = s;
        // 顺序必须与 S_* 常量一一对应（0..23）
        View[] screens = {subjectScreen, nodeScreen, detailScreen, settingsScreen,
                searchScreen, feedbackScreen, mineScreen, profileScreen, changelogScreen, aboutScreen,
                displayScreen, todayScreen, extraScreen, favScreen, histScreen, favSubScreen,
                dailyScreen, sentScreen, aiWebScreen, aiHubScreen, aiApiScreen, aiWebCfgScreen,
                aiChatScreen, orderScreen, wordHomeScreen, wordStudyScreen, wordSearchScreen,
                wordSettingsScreen, wordVocabScreen};
        View target = screens[s];
        boolean shown = target.getVisibility() == View.VISIBLE && target.getAlpha() > 0.95f
                && Math.abs(target.getTranslationX()) < dp(4);
        for (int i = 0; i < screens.length; i++) {
            if (i == s || i == old) continue;   // 旧页面先留着，等新页渲染完成再收起
            screens[i].animate().cancel();
            screens[i].setVisibility(View.GONE);
            screens[i].setTranslationX(0f);
            screens[i].setTranslationY(0f);
            screens[i].setAlpha(1f);
        }
        target.animate().cancel();
        // 打开/返回页面：先把目标页渲染完（动态加载的渲染完屏幕可见部分）+ 定位好，再显示，
        // 中间不露占位也不黑屏。切页的淡入 / 位移动画都去掉；右滑返回的跟手动画不在这里、不受影响。
        // 需要「渲染后再显示」：正向进入、返回、同页换层（内容重建）。
        // 直接显示的例外：启动首屏（开屏盖着）、右滑返回已归位（shown）、问 AI 网页页（透明层会丢首帧）。
        boolean needReveal = !shown && !skipPageAnim && s != S_AI_WEB;
        if (s == old && !skipPageAnim && s != S_AI_WEB) needReveal = true;   // 同页换层也重建
        skipPageAnim = false;
        if (needReveal) {
            // 先隐藏（不绘制、不占交互），下面渲染 + 定位完成后在末尾 reveal 显示
            target.setTranslationX(0f);
            target.setTranslationY(0f);
            target.setAlpha(1f);
            target.setVisibility(View.INVISIBLE);
        } else {
            // shown（右滑返回已归位）或启动首屏或问 AI：直接显示
            target.setTranslationX(0f);
            target.setTranslationY(0f);
            target.setAlpha(1f);
            target.setVisibility(View.VISIBLE);
        }

        if (s == S_SUBJECT) {
            if (subjectDirty) {
                final List<String> keys = new ArrayList<String>(orderKeys);
                subjectList.bind(new RowSource() {
                    public int size() { return keys.size() + 6; }
                    public View make(final int i) {
                        Runnable toSearch = new Runnable() {
                            public void run() { showScreen(S_SEARCH); }
                        };
                        Runnable toFav = new Runnable() {
                            public void run() { showScreen(S_FAV); }
                        };
                        Runnable toHist = new Runnable() {
                            public void run() { showScreen(S_HIST); }
                        };
                        Runnable toToday = new Runnable() {
                            public void run() { showScreen(S_TODAY); }
                        };
                        Runnable toAi = new Runnable() {
                            public void run() { openAiFromHome(); }
                        };
                        Runnable toSettings = new Runnable() {
                            public void run() { showScreen(S_SETTINGS); }
                        };
                        if (i == 0) return textRow("搜索（全部知识点）", C_PRIMARY, toSearch);
                        if (i == 1) return textRow("我的收藏", C_PRIMARY, toFav);
                        if (i == keys.size() + 2) return textRow("最近浏览", C_PRIMARY, toHist);
                        if (i == keys.size() + 3) return textRow("新增知识点（今日）", C_PRIMARY, toToday);
                        if (i == keys.size() + 4) return textRow("问 AI", C_PRIMARY, toAi);
                        if (i == keys.size() + 5) return textRow("设置", C_PRIMARY, toSettings);
                        final String k = keys.get(i - 2);
                        Subject su = subjectMap.get(k);
                        return textRow(su == null ? k : su.name, C_PRIMARY, new Runnable() {
                            public void run() {
                                Subject su2 = subjectMap.get(k);
                                if (su2 != null) openSubject(su2);
                            }
                        });
                    }
                });
                subjectDirty = false;
            }
        } else if (s == S_SETTINGS) {
            renderSettings();
            scrollToTop(settingsScroll);   // 进这一页从顶部开始；页内改排序/开关由 renderSettings 自己保位
        } else if (s == S_ORDER) {
            renderOrder();
            scrollToTop(orderScroll);
        } else if (s == S_WORD_HOME) {
            renderWordHome();
            scrollToTop(wordHomeScroll);
        } else if (s == S_WORD_STUDY) {
            // 卡片由 openWordStudy() 里的 nextCard() 负责铺，这里不重画（避免翻面状态被冲掉）
        } else if (s == S_WORD_SEARCH) {
            renderWordSearch();
        } else if (s == S_WORD_VOCAB) {
            renderWordVocab();
            scrollToTop(wordVocabScroll);
        } else if (s == S_WORD_SETTINGS) {
            renderWordSettings();
            scrollToTop(wordSettingScroll);
        } else if (s == S_SEARCH) {
            // 关键词没变就不重设数据，返回时列表位置不会跳回顶部
            String q = gSearchBox.getText().toString().trim();
            if (lastQuery == null || !lastQuery.equals(q)) {
                refreshGlobalSearch();
            }
        } else if (s == S_MYFEEDBACK) {
            if (mineDirty) { mineDirty = false; loadMyIssues(); }
        } else if (s == S_FEEDBACK) {
            updateFeedbackUi();
            scrollToTop(feedbackScroll);
            // 启动时网络还没就绪、通道没探到：进反馈页再试一次，不用手动去设置里点
            if (routeApi == null) reProbeIfNeeded();
        } else if (s == S_PROFILE) {
            updateProfileUi();
            scrollToTop(profScroll);
            if (ghToken != null) loadProfileFromGitHub();
            maybePlayHearts();
        } else if (s == S_DISPLAY) {
            renderDisplayScreen();
        } else if (s == S_TODAY) {
            String td = new java.text.SimpleDateFormat("yyMMdd", java.util.Locale.US)
                    .format(new java.util.Date());
            // 跨天了就回到「只看今天」，不要把昨天在看的那个批次带过来
            if (todayAll.isEmpty() || !td.equals(todayBuiltFor)) buildTodayData(false);
        } else if (s == S_ABOUT) {
            refreshAboutInfo();
            scrollToTop(aboutScroll);
            if (!thanksLoaded) loadThanks();
        } else if (s == S_CHANGELOG) {
            if (!changelogFilled) fillChangelog();
            scrollToTop(changelogScroll);
        } else if (s == S_FAV) {
            renderFav();
        } else if (s == S_FAV_SUB) {
            renderFavSub();
        } else if (s == S_DAILY) {
            renderDaily();
        } else if (s == S_SENT) {
            renderSent();
        } else if (s == S_HIST) {
            renderHist();
        } else if (s == S_AI_WEB) {
            // 只加载一次：反复进出不打断网页里正在进行的对话
            if (!aiWebLoaded) aiWebLoad();
            showAiGestureHint();
        } else if (s == S_AI_HUB) {
            renderAiHub();
            scrollToTop(aiHubScroll);
        } else if (s == S_AI_API_CFG) {
            renderAiApi();
            scrollToTop(aiApiScroll);
        } else if (s == S_AI_CHAT) {
            chatScrollTopOnRender = true;
            if (chatDirty) {
                showChatLoadingThenRender();
            } else {
                scrollChatToTop();
                chatScrollTopOnRender = false;
            }
        } else if (s == S_AI_WEB_CFG) {
            renderAiWebCfg();
            scrollToTop(aiWebCfgScroll);
        }

        // 只有「返回上一级」才回到离开时的位置；正向点进页面一律回到默认位置（跳过顶部占位贴顶）。
        SvList enter = svOf(s);
        if (enter != null) {
            if (restoreNext[s]) {
                restoreNext[s] = false;
                enter.restore();
            } else {
                enter.enter();
            }
        } else {
            // 普通滚动页：返回时回到离开的位置；正向进入交给上面各分支的 scrollToTop 贴顶。
            ScrollView tsc = scrollOf(s);
            if (tsc != null && restoreNext[s]) {
                restoreNext[s] = false;
                scrollToY(tsc, savedScrollY[s]);
            }
        }

        // 渲染 + 定位都就绪后再显示：正文页 scrollToTop 已 post 排队，这里再 post 必然排在定位之后；
        // 列表页是分帧渲染，等首屏渲染完 + 定位到位（SvList.onReady 回调）再显示。
        if (needReveal) {
            final View tv = target;
            final int fn = s;
            final int oldS = old;
            final boolean[] done = new boolean[]{false};
            final Runnable reveal = new Runnable() {
                public void run() {
                    if (done[0] || screen != fn) return;
                    done[0] = true;
                    tv.setVisibility(View.VISIBLE);
                    // 渲染完成后再显示：新页淡入 + 旧页淡出，时长拉长到 320ms。
                    // 两页叠加渐变，过渡柔和不像「闪一下」，且谁在上层都不影响观感。
                    tv.setAlpha(0f);
                    tv.animate().alpha(1f).setDuration(320)
                            .setInterpolator(new DecelerateInterpolator(1.6f)).start();
                    if (oldS != fn && oldS >= 0 && oldS < screens.length) {
                        final View ov = screens[oldS];
                        ov.animate().cancel();
                        ov.animate().alpha(0f).setDuration(320)
                                .withEndAction(new Runnable() {
                                    public void run() {
                                        ov.setVisibility(View.GONE);
                                        ov.setAlpha(1f);
                                        ensureOverlaysOnTop();
                                    }
                                }).start();
                    }
                }
            };
            if (enter != null) {
                enter.onReady = reveal;
            } else {
                tv.post(reveal);
            }
            // 兜底：万一渲染回调链没触发（返回已渲染页、缓存命中等），也保证页面一定显示，
            // 绝不再出现「页面停在 INVISIBLE → 返回没反应 / 黑屏」。reveal 幂等，只显示一次。
            tv.postDelayed(reveal, 200);
        } else {
            // 直接显示的场景（右滑预览已归位 / 启动首屏 / 问 AI）：必须把旧页收掉。
            // 右滑时 preparePrev 把旧页 bringToFront 提到最上层，不隐藏就会盖住新页，
            // 表现为「右滑到底又弹回原页、再滑一次退两级」。
            if (old != s && old >= 0 && old < screens.length) {
                View ov = screens[old];
                ov.animate().cancel();
                ov.setTranslationX(0f);
                ov.setAlpha(1f);
                ov.setVisibility(View.GONE);
            }
            if (enter != null) enter.onReady = null;   // 清掉可能残留的回调
        }
    }

    private SvList svOf(int s) {
        switch (s) {
            case S_SUBJECT: return subjectList;
            case S_NODE: return nodeList;
            case S_SEARCH: return searchList;
            case S_MYFEEDBACK: return mineList;
            case S_TODAY: return todayList;
            case S_FAV: return favList;
            case S_HIST: return histList;
            case S_FAV_SUB: return favSubList;
            case S_DAILY: return dailyList;
            case S_SENT: return sentList;
            default: return null;
        }
    }

    /** 返回上一级：先把「恢复位置」标记打在目标页上，再切页。
     *  这样从子页退回时回到原处，之后重新点进这一页则是默认位置，不会停在半中间。 */
    private void goBack(int t) {
        if (t < 0) return;
        restoreNext[t] = true;
        showScreen(t);
    }

    @Override
    protected void onDestroy() {
        log("APP", "onDestroy");
        dismissCodeDialog();
        dismissUpdateDialog();
        cancelAiGestureTasks();
        if (aiWebView != null) {
            try { aiWebView.destroy(); } catch (Throwable ignored) { }
            aiWebView = null;
        }
        super.onDestroy();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent ev) {
        // 系统返回键在分发前就拦下来：网页控件持有焦点时按键会先落到 WebView 上，
        // 有些机型/输入法组合下 onBackPressed 收不到。这里统一走 onBackPressed，
        // 和实体键、右滑返回是同一条逻辑。
        if (ev.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            if (ev.getAction() == KeyEvent.ACTION_DOWN && ev.getRepeatCount() == 0) {
                onBackPressed();
            }
            return true;
        }
        return super.dispatchKeyEvent(ev);
    }

    @Override
    public void onBackPressed() {
        if (fullscreenView != null) { closeFullscreen(); return; }
        if (screen == S_SUBJECT) { super.onBackPressed(); return; }
        handleBack();
    }

    /** 返回上一级（实体键与右滑手势共用）。 */
    private void handleBack() {
        if (fullscreenView != null) { closeFullscreen(); return; }
        if (screen == S_DISPLAY && tuningFirstRun) {
            // 第一次必须点「完成，开始使用」才算确认，返回键不放行
            toast("先点下面的「完成，开始使用」");
            return;
        }
        // 问 AI 页：返回键一律退出这一屏，不再先退网页自己的历史。
        // 网页是单页应用，历史记录一直在加，canGoBack() 长期为 true —— 以前按返回只是
        // 网页内部翻一页，看上去跟没反应一样。要回到网页上一页，用网页自己的界面。
        if (screen == S_NODE && nodeBack()) return;   // 还有上级分组就先退一层
        goBack(backTarget());
    }

    /** 当前页的上一级页面（主页返回 -1）。 */
    private int backTarget() {
        if (screen == S_DETAIL) return detailBack;
        if (screen == S_EXTRA) return S_DETAIL;
        if (screen == S_NODE) return S_SUBJECT;   // 栈空时才到这里（有栈已被 nodeBack 处理）
        if (screen == S_SETTINGS) return S_SUBJECT;
        if (screen == S_ORDER) return S_SETTINGS;
        if (screen == S_SEARCH) return S_SUBJECT;
        if (screen == S_FEEDBACK) return S_SETTINGS;
        if (screen == S_MYFEEDBACK) return S_FEEDBACK;
        if (screen == S_PROFILE) return S_SETTINGS;
        if (screen == S_CHANGELOG) return changelogBack == S_CHANGELOG ? S_SETTINGS : changelogBack;
        if (screen == S_ABOUT) return S_SETTINGS;
        if (screen == S_DISPLAY) return tuningFirstRun ? -1 : S_SETTINGS;
        if (screen == S_TODAY) return S_SUBJECT;
        if (screen == S_FAV) return S_SUBJECT;
        if (screen == S_HIST) return S_SUBJECT;
        if (screen == S_FAV_SUB) return S_FAV;
        if (screen == S_DAILY) return dailyBack;
        if (screen == S_SENT) return "hist".equals(sentMode) ? S_HIST : S_FAV;
        if (screen == S_AI_WEB) return aiWebBack;
        if (screen == S_AI_CHAT) return aiChatBack;
        // 单词四屏：主页回它的来处（英语科目或主页），子页回主页
        if (screen == S_WORD_HOME) return wordHomeBack;
        if (screen == S_WORD_STUDY) return S_WORD_HOME;
        if (screen == S_WORD_SEARCH) return wordSearchBack;
        if (screen == S_WORD_SETTINGS) return wordSettingBack;
        if (screen == S_WORD_VOCAB) return S_WORD_HOME;
        if (screen == S_AI_HUB) return S_SETTINGS;
        if (screen == S_AI_API_CFG || screen == S_AI_WEB_CFG) return aiCfgBack;
        return -1;
    }

    private View screenView(int s) {
        switch (s) {
            case S_SUBJECT: return subjectScreen;
            case S_NODE: return nodeScreen;
            case S_DETAIL: return detailScreen;
            case S_EXTRA: return extraScreen;
            case S_SETTINGS: return settingsScreen;
            case S_SEARCH: return searchScreen;
            case S_FEEDBACK: return feedbackScreen;
            case S_MYFEEDBACK: return mineScreen;
            case S_PROFILE: return profileScreen;
            case S_CHANGELOG: return changelogScreen;
            case S_ABOUT: return aboutScreen;
            case S_DISPLAY: return displayScreen;
            case S_TODAY: return todayScreen;
            case S_FAV: return favScreen;
            case S_HIST: return histScreen;
            case S_FAV_SUB: return favSubScreen;
            case S_DAILY: return dailyScreen;
            case S_SENT: return sentScreen;
            case S_AI_WEB: return aiWebScreen;
            case S_AI_HUB: return aiHubScreen;
            case S_AI_API_CFG: return aiApiScreen;
            case S_AI_WEB_CFG: return aiWebCfgScreen;
            case S_AI_CHAT: return aiChatScreen;
            case S_ORDER: return orderScreen;
            case S_WORD_HOME: return wordHomeScreen;
            case S_WORD_STUDY: return wordStudyScreen;
            case S_WORD_SEARCH: return wordSearchScreen;
            case S_WORD_SETTINGS: return wordSettingsScreen;
            case S_WORD_VOCAB: return wordVocabScreen;
            default: return null;
        }
    }

    /** 拖动预览时把下载进度卡重新提到最上层。 */
    private void ensureOverlaysOnTop() {
        if (progBox != null && progBox.getVisibility() == View.VISIBLE) progBox.bringToFront();
    }

    /** 手势被上层接管时（双指接管、右滑返回），给下面已经收下 ACTION_DOWN 的控件补一个 CANCEL：
     *  告诉它「这一串触摸作废了」，撤掉还在走的长按 / 双击识别。
     *  只拦 MOVE 不补 CANCEL 是不够的——控件收不到后续事件，它的长按计时照走，
     *  滑到一半突然弹出「取消收藏」确认框、按钮长按生效，都是这么来的。 */
    private void cancelChildGesture() {
        long now = SystemClock.uptimeMillis();
        MotionEvent cancel = MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, 0f, 0f, 0);
        try { super.dispatchTouchEvent(cancel); }
        catch (Throwable ignored) { }
        cancel.recycle();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        // 问 AI 页的双指手势优先（仅圆屏手表）：从第二指按下到最后一指抬起期间事件全部由这里处理，
        // 不传给网页，避免误触按钮，也避开 WebView 自带的双指缩放。
        if (fullscreenView == null && (aiTwoActive || (screen == S_AI_WEB && aiTwoGestureEnabled()))) {
            if (handleAiTwoFinger(ev)) return true;
        }
        // 网页页也保留右滑返回，但起手区和所需距离都收紧，
        // 免得网页里横滑、拖光标被误判成返回。
        if (swipeBack && fullscreenView == null && !sliderTouching) {
            int action = ev.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                swipeX = ev.getX();
                swipeY = ev.getY();
                swipeTracking = true;
                swipeActive = false;
                swipeFromInput = inEditText(swipeX, swipeY);
            } else if (action == MotionEvent.ACTION_MOVE && swipeTracking) {
                float dx = ev.getX() - swipeX;
                float dy = Math.abs(ev.getY() - swipeY);
                if (!swipeActive && !swipeBusy && swipeView == null) {
                    // 起手必须在左侧 1/4 内（输入框内更严格，避免拖动光标被当成返回）
                    float zone = swipeFromInput ? dp(18)
                            : (screen == S_AI_WEB ? dp(20) : swipeZone());
                    if (screen != S_SUBJECT && swipeX <= zone && dx > dp(16) && dx > dy * 1.5f) {
                        View cur = currentScreenView();
                        if (cur != null) {
                            // 手势从现在起归返回用：先给起手处那个控件（列表行、按钮）补 CANCEL，
                            // 撤掉它排着的长按。快照要在 CANCEL 之后拍，免得把按下态画进去。
                            cancelChildGesture();
                            cur.animate().cancel();
                            boolean ok = preparePrev(cur);
                            swipeActive = true;
                            // 没拍出预览也照样跟手：松手仍然返回，只是没有拖动预览
                            if (ok && swipeView == null) swipeView = cur;
                        }
                    }
                }
                if (swipeActive) {
                    if (swipeView == null) return true;            // 没有可跟手的页，先把手势吃掉
                    float d = Math.max(0f, dx);
                    float w = Math.max(1f, swipeView.getWidth());
                    swipeView.setTranslationX(d);                  // 当前页跟手右移
                    if (swipePrev != null) {                       // 上一级页面从左侧淡入式跟进
                        float prog = Math.min(1f, d / w);
                        swipePrev.setTranslationX(-w * PREV_PARALLAX * (1f - prog));
                    }
                    return true;
                }
            } else if (action == MotionEvent.ACTION_UP) {
                if (swipeActive) {
                    swipeTracking = false;
                    swipeActive = false;
                    float need = swipeFromInput ? dp(80)
                            : (screen == S_AI_WEB ? dp(64) : dp(46));
                    boolean far = ev.getX() - swipeX > need;
                    if (swipeView == null) {
                        if (far) handleBack();
                        return true;
                    }
                    if (far) finishSwipe(); else resetSwipe();
                    return true;
                }
                swipeTracking = false;
            } else if (action == MotionEvent.ACTION_CANCEL) {
                swipeTracking = false;
                if (swipeActive) {
                    swipeActive = false;
                    resetSwipe();
                    return true;
                }
            }
        } else {
            swipeTracking = false;
        }
        return super.dispatchTouchEvent(ev);
    }

    /** 起手判定区：屏幕左侧 1/4。 */
    private float swipeZone() {
        int w = (root != null && root.getWidth() > 0)
                ? root.getWidth() : getResources().getDisplayMetrics().widthPixels;
        return Math.max(dp(44), w * 0.25f);
    }

    private boolean inEditText(float x, float y) {
        return hitIn(gSearchBox, x, y) || hitIn(itemSearchBox, x, y);
    }

    private boolean hitIn(View v, float x, float y) {
        if (v == null || v.getVisibility() != View.VISIBLE) return false;
        int[] loc = new int[2];
        v.getLocationInWindow(loc);
        return x >= loc[0] && x <= loc[0] + v.getWidth()
                && y >= loc[1] && y <= loc[1] + v.getHeight();
    }

    private View currentScreenView() {
        switch (screen) {
            case S_NODE: return nodeScreen;
            case S_DETAIL: return detailScreen;
            case S_EXTRA: return extraScreen;
            case S_SETTINGS: return settingsScreen;
            case S_SEARCH: return searchScreen;
            case S_FEEDBACK: return feedbackScreen;
            case S_MYFEEDBACK: return mineScreen;
            case S_PROFILE: return profileScreen;
            case S_CHANGELOG: return changelogScreen;
            case S_ABOUT: return aboutScreen;
            case S_DISPLAY: return displayScreen;
            case S_TODAY: return todayScreen;
            case S_FAV: return favScreen;
            case S_HIST: return histScreen;
            case S_FAV_SUB: return favSubScreen;
            case S_DAILY: return dailyScreen;
            case S_SENT: return sentScreen;
            case S_AI_WEB: return aiWebScreen;
            case S_AI_HUB: return aiHubScreen;
            case S_AI_API_CFG: return aiApiScreen;
            case S_AI_WEB_CFG: return aiWebCfgScreen;
            case S_AI_CHAT: return aiChatScreen;
            case S_ORDER: return orderScreen;
            case S_WORD_HOME: return wordHomeScreen;
            case S_WORD_STUDY: return wordStudyScreen;
            case S_WORD_SEARCH: return wordSearchScreen;
            case S_WORD_SETTINGS: return wordSettingsScreen;
            case S_WORD_VOCAB: return wordVocabScreen;
            default: return subjectScreen;
        }
    }

    /** 右滑要落到哪一页；-1 表示这次返回仍留在本页（分组回退，只换列表内容）。 */
    private int previewTarget() {
        if (screen == S_NODE) {
            if (navStack.size() > nodeEntryDepth) return -1;  // 用户自己点进的层：本页内换层
            if (nodeEntryBack >= 0) return nodeEntryBack;     // 补进来的祖先链退完了：回入口页
            return S_SUBJECT;
        }
        return backTarget();
    }

    /** 拖动开始时把上一级页面放到当前页下面，并左移一点做视差预览。
     *  返回 false 表示这次没什么可露出（不跟手，交给子视图照常处理）。 */
    private boolean preparePrev(View cur) {
        swipePrev = null;
        swipeGhost = null;
        swipeSame = false;
        swipePopped = null;
        swipeOldY = -1;
        swipeQuery = null;
        float w = (root != null && root.getWidth() > 0) ? root.getWidth() : cur.getWidth();
        int t = previewTarget();
        if (t < 0) {
            // 分组回退：落点还是这一页，只是列表要换成上一级。
            // 把当前画面拍成快照顶在上面当「被拖走的页」，底下先把上一级渲染好，
            // 拖的时候看到的就是真正要退回去的那一层，不会先闪一下主页再跳。
            ImageView ghost = snapshot(cur);
            if (ghost == null || nodeList == null || nodeList.sc == null) {
                removeGhost(ghost);
                return false;
            }
            swipeOldY = nodeList.sc.getScrollY();
            swipeQuery = itemSearchBox.getText().toString();
            swipePopped = navStack.remove(navStack.size() - 1);
            curNode = navStack.isEmpty() ? null : navStack.get(navStack.size() - 1);
            nodeList.syncFirstRender = true;   // 预览换层：首屏同步渲染完再露出，不空一帧
            renderNodes();
            // 缓存里存着「离开这一层时的滚动位置」，命中就定位回去；
            // 只有从没来过这层（没缓存）才用 enter() 从顶部占位开始。
            if (nodeList.savedY >= 0) nodeList.restore(); else nodeList.enter();
            cur.animate().cancel();
            cur.setAlpha(1f);
            cur.setTranslationX(-w * PREV_PARALLAX);
            swipePrev = cur;
            swipeGhost = ghost;
            swipeSame = true;
            swipeView = ghost;
            cur.bringToFront();       // 上一级在下面
            ghost.bringToFront();     // 快照压在最上面
            ensureOverlaysOnTop();
            return true;
        }
        View prev = (t >= 0) ? screenView(t) : null;
        if (prev != null && prev != cur) {
            prev.animate().cancel();
            prev.setVisibility(View.VISIBLE);
            prev.setAlpha(1f);
            prev.setTranslationX(-w * PREV_PARALLAX);
            prev.bringToFront();      // 先放下面一层
            swipePrev = prev;
        }
        cur.bringToFront();           // 当前页始终在最上层
        ensureOverlaysOnTop();
        return true;
    }

    /** 把当前页画成一张位图盖回原处：同页换层时用它当「正在拖走的页」。 */
    private ImageView snapshot(View v) {
        int w = v.getWidth(), h = v.getHeight();
        if (w <= 0 || h <= 0) return null;
        try {
            Bitmap bm = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            v.draw(new Canvas(bm));
            ImageView iv = new ImageView(this);
            iv.setImageBitmap(bm);
            iv.setScaleType(ImageView.ScaleType.FIT_XY);
            root.addView(iv, fp());
            return iv;
        } catch (Throwable e) {
            return null;
        }
    }

    private void removeGhost(View ghost) {
        if (ghost == null) return;
        if (ghost.getParent() instanceof ViewGroup) {
            ((ViewGroup) ghost.getParent()).removeView(ghost);
        }
        if (ghost instanceof ImageView) ((ImageView) ghost).setImageDrawable(null);
    }

    /** 松手过阈值：当前页滑出、预览页归位，随后真正切页。 */
    private void finishSwipe() {
        final View v = swipeView;
        final View prev = swipePrev;
        final View ghost = swipeGhost;
        final boolean same = swipeSame;
        swipeView = null;
        swipePrev = null;
        swipeGhost = null;
        swipeSame = false;
        swipePopped = null;
        swipeQuery = null;
        if (v == null) return;
        swipeBusy = true;
        float w = Math.max(v.getWidth(), dp(80));
        v.animate().translationX(w).alpha(0.85f).setDuration(170)
                .withEndAction(new Runnable() {
                    public void run() {
                        if (same) {
                            // 同页换层：底下已经是退回后的那一层，收掉快照就完事
                            if (prev != null) {
                                prev.animate().cancel();
                                prev.setTranslationX(0f);
                            }
                            removeGhost(ghost);
                            swipeBusy = false;
                            ensureOverlaysOnTop();
                            return;
                        }
                        v.setTranslationX(0f);
                        v.setAlpha(1f);
                        if (prev != null) {
                            // 预览页先归位并停掉动画：切页时它已经在位，不会再淡入一下
                            prev.animate().cancel();
                            prev.setTranslationX(0f);
                            prev.setAlpha(1f);
                        }
                        swipeBusy = false;
                        // 预览页已就位，handleBack→showScreen 只确认、不重渲染；finally 防标志残留
                        swipeNavigating = true;
                        try { handleBack(); } finally { swipeNavigating = false; }
                    }
                }).start();
        if (prev != null) {
            prev.animate().translationX(0f).setDuration(170).start();
        }
    }

    /** 没滑够：两页同时回弹，预览页缩回后隐藏。 */
    private void resetSwipe() {
        final View v = swipeView;
        final View prev = swipePrev;
        final View ghost = swipeGhost;
        final boolean same = swipeSame;
        final Node popped = swipePopped;
        final int oldY = swipeOldY;
        final String query = swipeQuery;
        swipeView = null;
        swipePrev = null;
        swipeGhost = null;
        swipeSame = false;
        swipePopped = null;
        swipeOldY = -1;
        swipeQuery = null;
        if (v == null) return;
        swipeBusy = true;
        float w = Math.max(v.getWidth(), dp(80));
        v.animate().translationX(0f).alpha(1f).setDuration(160)
                .withEndAction(new Runnable() {
                    public void run() {
                        if (same) {
                            // 没滑够：层级退回去、列表重建成原来那层，再把快照换下来
                            if (popped != null) {
                                navStack.add(popped);
                                curNode = popped;
                            }
                            // 先还原本级搜索词（锁住监听），再用它一次性绑定、同步渲染首屏
                            nodeRefreshLocked = true;
                            itemSearchBox.setText(query == null ? "" : query);
                            nodeRefreshLocked = false;
                            setPath(listTitle, currentPath());
                            nodeList.syncFirstRender = true;
                            bindNodes();
                            nodeList.savedY = oldY;
                            nodeList.restore();
                            if (prev != null) {
                                prev.animate().cancel();
                                prev.setTranslationX(0f);
                            }
                            // 行是分帧加的：等第一屏画好再撤快照，否则会闪一下空列表
                            ghost.postDelayed(new Runnable() {
                                public void run() {
                                    removeGhost(ghost);
                                    swipeBusy = false;
                                    ensureOverlaysOnTop();
                                }
                            }, 70);
                            return;
                        }
                        swipeBusy = false;
                        ensureOverlaysOnTop();
                    }
                }).start();
        if (prev != null && !same) {
            prev.animate().translationX(-w * PREV_PARALLAX).setDuration(160)
                    .withEndAction(new Runnable() {
                        public void run() {
                            prev.setVisibility(View.GONE);
                            prev.setTranslationX(0f);
                        }
                    }).start();
        }
    }

    // ================= 反馈（GitHub Device Flow + Issues） =================
    /** 在 GitHub 创建 OAuth App 后填这里（client_id 不敏感，可随 APK 分发）。 */
    private static final String GH_CLIENT_ID = "Ov23liuXn1Lh4Cy0UpcS";
    private static final String GH_DEVICE_CODE = "https://github.com/login/device/code";
    private static final String GH_ACCESS_TOKEN = "https://github.com/login/oauth/access_token";
    private static final String GH_USER = "https://api.github.com/user";
    private static final String GH_ISSUES = "https://api.github.com/repos/" + REPO + "/issues";

    private boolean loginRunning = false;

    // ================= 关于 / 感谢名单 =================
    /** 致谢名单的置顶对象（本地约定，源码里不做明文标注）。 */
    private static final String THANKS_PINNED = new String(
            android.util.Base64.decode("bHVja3l5eTA3MjE=", android.util.Base64.NO_WRAP));
    private static final String REPO_OWNER = "hnxy114514";
    private static final String HINT_TEXT = "I love Lucky \u2764\uFE0F";
    /** 进入个人页时触发满屏装饰图案的账号（本地约定，不对外说明）。 */
    private static final String[] HEART_USERS = {"luckyyy0721"};
    private static final String[] REST_TIPS = {
        "学习再忙，也别忘了抬头看看远处，让眼睛歇一歇。",
        "该休息啦，劳逸结合才能走得更远。",
        "你已经很努力了，休息一下不算偷懒。",
        "手腕酸了吧？起来活动活动，喝口水。",
        "别一直盯着屏幕，放松一下眼睛和肩颈。",
        "今天也要记得早点休息，身体是革命的本钱。",
        "学累了就歇会儿，效率比时长更重要。",
        "深呼吸，放松一下，你已经做得很好了。",
        "记得按时吃饭、按时睡觉，照顾好自己。",
        "忙里偷闲也是一种能力，休息好了再继续。"
    };
    /** 使用技巧：界面上看不出来的操作，靠启动时随机出一条，久了能学会。 */
    private static final String[] TIPS = {
        "页面顶上的路径每一段都能点，想回哪一层就点哪一段。",
        "返回键长按不放，可以一步回到首页。",
        "从屏幕左边缘往右滑也能返回上一页（可在设置里开启）。",
        "列表页上方那个搜索只搜当前这一层，想搜全部就回首页搜。",
        "搜索不只看标题，正文里的字也能搜到。",
        "分组那一行右边也有星星，点一下能把整块收进收藏。",
        "必背篇目滑到最底下有「译文」，可以一句一句对着看。",
        "科目顺序能在设置里上下移动，常用的放前面。"
    };
    private static final String[] SPECIAL_TIPS = {
        "别太累了，我会心疼的，记得歇一歇。",
        "想你了，学习再忙也要记得休息哦。",
        "你今天也辛苦了，早点休息，做个好梦。",
        "屏幕看久了眼睛会酸，休息一下，我要你健健康康的。",
        "无论多忙，都要照顾好自己，这是我最在意的事。",
        "累了就歇一会儿，就当是我在陪你。",
        "记得喝水、记得吃饭、记得想我。",
        "你的努力我都看在眼里，但身体最重要，听话休息一下。",
        "今晚早点睡，明天才有精神，爱你。",
        "别熬太晚，我要你平平安安的。"
    };
    private boolean heartRunning = false;
    private int tapCount = 0;
    private long lastTapTime = 0;

    private static final int AVATAR_DP = 34;
    /** 每次进关于页都按当前数据重算一次，保证数字是实时的。 */
    private void refreshAboutInfo() {
        if (aboutInfo == null) return;
        int itemCount = allHits.size(), imgCount = 0;
        for (Node n : allHits) if (n.img != null && n.img.length() > 0) imgCount++;
        aboutInfo.setText("腕上知识汇总 v" + appVersion() + "（build " + appVersionCode() + "）"
                + "\n作者：@" + REPO_OWNER
                + "\n屏幕：" + (m.round ? "圆形（已启用圆形安全区）" : "方形")
                + "\n共 " + subjects.size() + " 科 · 知识点 " + itemCount + " 条 · 配图 " + imgCount + " 张");
    }


    private void buildAboutScreen() {
        aboutScroll = new OverScrollScrollView(this);
        aboutScroll.setVerticalScrollBarEnabled(false);
        roundPadScroll(aboutScroll);
        aboutInner = new LinearLayout(this);
        aboutInner.setOrientation(LinearLayout.VERTICAL);
        aboutScroll.addView(aboutInner);
        aboutScreen.addView(aboutScroll, lw());

        aboutInner.addView(makeBack("‹ 返回设置", new View.OnClickListener() {
            public void onClick(View v) { goBack(S_SETTINGS); }
        }), wrapM());

        TextView title = new TextView(this);
        title.setText("关于");
        title.setTextSize(m.titleSize);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(C_PRIMARY);
        title.setGravity(Gravity.CENTER);
        aboutInner.addView(title, wrap());

        aboutInfo = new TextView(this);
        aboutInfo.setTextSize(m.subtitleSize - 1f);
        aboutInfo.setTextColor(C_SUB);
        aboutInfo.setGravity(Gravity.CENTER);
        aboutInfo.setPadding(dp(8), dp(9), dp(8), dp(9));
        aboutInfo.setBackgroundDrawable(pill(C_PILL));
        LinearLayout.LayoutParams ilp = wrap();
        ilp.topMargin = dp(6);
        aboutInner.addView(aboutInfo, ilp);
        refreshAboutInfo();

        TextView logEntry = new TextView(this);
        logEntry.setText("更新日志");
        logEntry.setTextSize(m.backSize);
        logEntry.setTextColor(C_PRIMARY);
        logEntry.setGravity(Gravity.CENTER);
        logEntry.setPadding(dp(10), dp(10), dp(10), dp(10));
        logEntry.setBackground(clickBg(C_PILL));
        applyIcon(logEntry, "ic_history", C_PRIMARY, 15);
        logEntry.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                vibrate();
                changelogBack = S_ABOUT;
                showScreen(S_CHANGELOG);
            }
        });
        LinearLayout.LayoutParams llp = wrap();
        llp.topMargin = dp(6);
        aboutInner.addView(logEntry, llp);

        aboutInner.addView(sectionHeader("感谢名单"), wrap());
        // 词库来源与版权说明（用户要求：写在感谢名单里，侵权可联系）
        TextView dictNote = new TextView(this);
        dictNote.setText("单词词库来自公开英语词表项目（有道背单词整理版），仅供个人学习使用。"
                + "若相关词条侵犯了您的权益，请联系 3444140854@qq.com，核实后会立即移除。");
        dictNote.setTextSize(m.subtitleSize - 3f);
        dictNote.setTextColor(0xFF6A6A6A);
        dictNote.setPadding(dp(6), 0, dp(6), dp(6));
        dictNote.setLineSpacing(dp(1), 1.15f);
        aboutInner.addView(dictNote, wrap());
        TextView tip = new TextView(this);
        tip.setText("以下朋友提交的反馈被采纳，特此致谢（按采纳数量排序）");
        tip.setGravity(Gravity.CENTER);
        tip.setTextSize(m.subtitleSize - 2f);
        tip.setTextColor(C_SUB);
        tip.setPadding(dp(4), dp(2), dp(4), dp(4));
        aboutInner.addView(tip, wrap());

        // 加载中：转圈 + 文案
        thanksLoading = new LinearLayout(this);
        thanksLoading.setOrientation(LinearLayout.HORIZONTAL);
        thanksLoading.setGravity(Gravity.CENTER);
        thanksLoading.setPadding(dp(4), dp(10), dp(4), dp(10));
        ProgressBar spin = new ProgressBar(this);
        try {
            spin.getIndeterminateDrawable()
                    .setColorFilter(C_PRIMARY, android.graphics.PorterDuff.Mode.SRC_IN);
        } catch (Exception ignored) { }
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(dp(18), dp(18));
        slp.rightMargin = dp(8);
        thanksLoading.addView(spin, slp);
        thanksLoadTxt = new TextView(this);
        thanksLoadTxt.setText("正在加载名单…");
        thanksLoadTxt.setTextSize(m.subtitleSize - 1f);
        thanksLoadTxt.setTextColor(C_SUB);
        thanksLoadTxt.setSingleLine(true);
        thanksLoadTxt.setEllipsize(TextUtils.TruncateAt.END);
        thanksLoading.addView(thanksLoadTxt, wrap());
        thanksLoading.setVisibility(View.GONE);
        aboutInner.addView(thanksLoading, wrap());

        // 空态 / 失败重试
        thanksState = new TextView(this);
        thanksState.setTextSize(m.subtitleSize - 1f);
        thanksState.setTextColor(C_SUB);
        thanksState.setGravity(Gravity.CENTER);
        thanksState.setPadding(dp(6), dp(9), dp(6), dp(9));
        thanksState.setVisibility(View.GONE);
        thanksState.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                vibrate();
                loadThanks();
            }
        });
        aboutInner.addView(thanksState, wrap());

        thanksBox = new LinearLayout(this);
        thanksBox.setOrientation(LinearLayout.VERTICAL);
        aboutInner.addView(thanksBox, wrap());
        padInner(aboutInner);
    }

    /** 仓库里的静态备份名单（raw，几乎所有镜像都代，成功率最高）。 */
    private static final String THANKS_SNAPSHOT =
            "https://raw.githubusercontent.com/" + REPO + "/main/cloud/thanks.json";
    /** 上次成功加载的名单，网络全不通时兜底显示。 */
    private static final String THANKS_CACHE = "thanks_cache";

    /** 取数开关：只在「一个都拿不到」时才用缓存兜底。 */
    private void setThanksHint(final String s) {
        runOnUiThread(new Runnable() {
            public void run() {
                if (thanksLoadTxt != null) thanksLoadTxt.setText(s);
            }
        });
    }

    private String hostOf(String url) {
        String s = url.replace("https://", "").replace("http://", "");
        int p = s.indexOf('/');
        if (p > 0) s = s.substring(0, p);
        return s;
    }

    /** 把 issues 数组聚合成「提交人 → 采纳条数」并排序（自己的不计入，指定的人置顶）。 */
    private List<Map.Entry<String, Integer>> parseThanks(JSONArray arr) {
        Map<String, Integer> cnt = new HashMap<String, Integer>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null || o.has("pull_request")) continue;
            boolean accepted = false;
            JSONArray labels = o.optJSONArray("labels");
            if (labels != null) {
                for (int k = 0; k < labels.length(); k++) {
                    Object lo = labels.opt(k);
                    String name = (lo instanceof JSONObject)
                            ? ((JSONObject) lo).optString("name", "") : String.valueOf(lo);
                    if (name.contains("已修复") || name.contains("已处理")) {
                        accepted = true;
                        break;
                    }
                }
            }
            if (!accepted) continue;
            JSONObject u = o.optJSONObject("user");
            String login = (u == null) ? null : u.optString("login", "");
            if (login == null || login.length() == 0) continue;
            if (login.equalsIgnoreCase(REPO_OWNER)) continue;   // 自己的不算致谢对象
            Integer old = cnt.get(login);
            cnt.put(login, old == null ? 1 : old + 1);
        }
        List<Map.Entry<String, Integer>> list =
                new ArrayList<Map.Entry<String, Integer>>(cnt.entrySet());
        java.util.Collections.sort(list, new java.util.Comparator<Map.Entry<String, Integer>>() {
            public int compare(Map.Entry<String, Integer> a, Map.Entry<String, Integer> b) {
                boolean pa = a.getKey().equalsIgnoreCase(THANKS_PINNED);
                boolean pb = b.getKey().equalsIgnoreCase(THANKS_PINNED);
                if (pa != pb) return pa ? -1 : 1;       // 指定的人永远置顶
                int c = b.getValue() - a.getValue();     // 采纳多的在前
                if (c != 0) return c;
                return a.getKey().compareToIgnoreCase(b.getKey());
            }
        });
        return list;
    }

    /** 缓存：只存「用户名 + 条数」，不存整份 issues，省空间也省解析。 */
    private String packThanks(List<Map.Entry<String, Integer>> list) {
        JSONArray a = new JSONArray();
        for (Map.Entry<String, Integer> e : list) {
            JSONObject o = new JSONObject();
            try {
                o.put("u", e.getKey());
                o.put("n", e.getValue());
            } catch (Exception ignored) { }
            a.put(o);
        }
        return a.toString();
    }

    private List<Map.Entry<String, Integer>> unpackThanks(String s) {
        List<Map.Entry<String, Integer>> list = new ArrayList<Map.Entry<String, Integer>>();
        if (s == null || s.length() < 2) return list;
        try {
            JSONArray a = new JSONArray(s);
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o == null) continue;
                String u = o.optString("u", "");
                if (u.length() == 0) continue;
                list.add(new java.util.AbstractMap.SimpleEntry<String, Integer>(
                        u, o.optInt("n", 1)));
            }
        } catch (Exception ignored) { }
        return list;
    }

    /** 拉取已采纳的反馈，按提交人聚合后排序（自己提的不计入）。
     *  取数顺序：直连 api.github.com（5 秒超时）→ 能转发 API 的镜像 → 仓库静态备份名单（走全量镜像）；
     *  全都拿不到就用上次缓存的名单，页面不会一直空着。 */
    /** 拉全部已关闭 issue：接口一页最多 100 条，只拉第一页的话
     *  issue 超过 100 条就会漏人、条数也会少（感谢名单数量对不上就是这么来的）。 */
    private org.json.JSONArray fetchAllClosed(String base, long deadline) {
        org.json.JSONArray all = new org.json.JSONArray();
        try {
            for (int page = 1; page <= 5; page++) {
                if (android.os.SystemClock.uptimeMillis() > deadline) break;
                String s = httpGetText(base + "?state=closed&per_page=100&page=" + page, 5000, 6000);
                org.json.JSONArray part = new org.json.JSONArray(s);
                for (int i = 0; i < part.length(); i++) all.put(part.opt(i));
                if (part.length() < 100) break;     // 不满一页，已经是最后一页
            }
        } catch (Exception e) {
            return all.length() > 0 ? all : null;
        }
        return all.length() > 0 ? all : null;
    }

    private void loadThanks() {
        if (thanksLoadingBusy) return;
        thanksLoadingBusy = true;
        thanksLoaded = false;
        thanksBox.removeAllViews();
        thanksState.setVisibility(View.GONE);
        thanksLoading.setVisibility(View.VISIBLE);
        setThanksHint("正在连接…");
        android.view.animation.AlphaAnimation fade =
                new android.view.animation.AlphaAnimation(0f, 1f);
        fade.setDuration(180);
        fade.setFillAfter(true);
        thanksLoading.startAnimation(fade);

        new Thread(new Runnable() {
            public void run() {
                org.json.JSONArray rawArr = null;
                // 总时限：一个个源试下去最坏能拖几十秒，到点就收手改用缓存
                final long deadline = android.os.SystemClock.uptimeMillis() + 25000L;

                // 0) 启动时测出来的最优通道，命中就不必再逐个试
                if (apiBase().length() > 0) {
                    rawArr = fetchAllClosed(apiBase() + GH_ISSUES, deadline);
                }

                // 1) 直连，超时设短一点，连不上就赶紧换镜像，别让用户干等
                if (rawArr == null) {
                    rawArr = fetchAllClosed(GH_ISSUES, deadline);
                }

                // 2) 能转发 api.github.com 的镜像
                if (rawArr == null) {
                    for (int i = 0; i < API_MIRRORS.length && rawArr == null; i++) {
                        if (android.os.SystemClock.uptimeMillis() > deadline) break;
                        setThanksHint("切换镜像 " + hostOf(API_MIRRORS[i]) + "…");
                        rawArr = fetchAllClosed(API_MIRRORS[i] + GH_ISSUES, deadline);
                    }
                }

                // 3) 仓库里的静态备份名单：raw 基本所有镜像都代，这一步成功率最高
                if (rawArr == null) {
                    for (int i = 0; i <= MIRRORS.length && rawArr == null; i++) {
                        if (android.os.SystemClock.uptimeMillis() > deadline) break;
                        String u = (i == 0) ? THANKS_SNAPSHOT : MIRRORS[i - 1] + THANKS_SNAPSHOT;
                        setThanksHint(i == 0 ? "读取备份名单…"
                                : "切换镜像 " + hostOf(MIRRORS[i - 1]) + "…");
                        try {
                            String s = httpGetText(u, 5000, 6000);
                            if (s != null && s.length() > 2) rawArr = new org.json.JSONArray(s);
                        } catch (Exception ignored) { }
                    }
                }

                List<Map.Entry<String, Integer>> list = null;
                boolean fetched = false;      // 真的取到了数据（哪怕结果是空的）
                if (rawArr != null) {
                    try {
                        list = parseThanks(rawArr);
                        fetched = true;
                        if (!list.isEmpty()) {
                            prefs.edit().putString(THANKS_CACHE, packThanks(list)).apply();
                        }
                    } catch (Exception ignored) { }
                }
                boolean fromCache = false;
                if (!fetched) {
                    list = unpackThanks(prefs.getString(THANKS_CACHE, ""));
                    fromCache = !list.isEmpty();
                }

                final List<Map.Entry<String, Integer>> out = list;
                final boolean cached = fromCache;
                final boolean got = fetched;
                runOnUiThread(new Runnable() {
                    public void run() {
                        thanksLoadingBusy = false;
                        thanksLoading.setVisibility(View.GONE);
                        if (out.isEmpty()) {
                            // 取到了但确实没人被采纳 ≠ 加载失败，别混成一句
                            if (got) {
                                thanksState.setText("还没有被采纳的反馈");
                                thanksState.setTextColor(C_SUB);
                            } else {
                                thanksState.setText("名单加载失败，点这里重试");
                                thanksState.setTextColor(0xFFE57373);
                            }
                            thanksState.setVisibility(View.VISIBLE);
                            return;
                        }
                        if (cached) {
                            TextView note = new TextView(MainActivity.this);
                            note.setText("网络不通，下面是上次加载的名单");
                            note.setTextSize(m.subtitleSize - 2f);
                            note.setTextColor(0xFFE57373);
                            note.setGravity(Gravity.CENTER);
                            note.setPadding(dp(4), dp(3), dp(4), dp(3));
                            LinearLayout.LayoutParams nlp = wrap();
                            nlp.topMargin = dp(4);
                            thanksBox.addView(note, nlp);
                        }
                        for (int i = 0; i < out.size(); i++) {
                            Map.Entry<String, Integer> e = out.get(i);
                            LinearLayout.LayoutParams lp = wrap();
                            lp.topMargin = dp(8);
                            thanksBox.addView(thanksRow(i, e.getKey(), e.getValue()), lp);
                        }
                        // 走缓存说明这次没连上，下次进页面要重新试一次
                        thanksLoaded = !cached;
                    }
                });
            }
        }).start();
    }

    /** 一行致谢：圆形头像 + 名次/用户名 + 采纳条数。 */
    private View thanksRow(int index, String login, int count) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10), dp(9), dp(10), dp(9));
        row.setBackgroundDrawable(pill(C_PILL));

        int size = dp(AVATAR_DP);
        ImageView av = new ImageView(this);
        av.setImageBitmap(letterAvatar(login, size));
        FrameLayout avBox = new FrameLayout(this);
        int box = size + dp(14);       // 头像本身小，扩大点击区域
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(box, box);
        blp.rightMargin = dp(4);
        avBox.addView(av, new FrameLayout.LayoutParams(size, size, Gravity.CENTER));
        GradientDrawable avBg = new GradientDrawable();
        avBg.setShape(GradientDrawable.OVAL);
        avBg.setColor(0xFF1B1B1B);
        avBox.setBackgroundDrawable(avBg);
        int spinSize = Math.max(dp(16), Math.round(size * 0.66f));
        ProgressBar spin = makeAvatarSpinner(spinSize);
        spin.setVisibility(View.GONE);
        avBox.addView(spin, new FrameLayout.LayoutParams(spinSize, spinSize, Gravity.CENTER));
        row.addView(avBox, blp);

        if (login.equalsIgnoreCase(THANKS_PINNED)) {
            avBox.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    vibrate();
                    onAvatarTap();
                }
            });
        }

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView name = new TextView(this);
        name.setText((index + 1) + ". @" + login);
        name.setTextSize(lsSp());
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setTextColor(index == 0 ? C_HI : 0xFFF0F0F0);
        name.setMaxLines(1);
        name.setEllipsize(TextUtils.TruncateAt.END);
        col.addView(name, wrap());
        TextView sub = new TextView(this);
        sub.setText("采纳 " + count + " 条");
        sub.setTextSize(m.subtitleSize - 1f);
        sub.setTextColor(index == 0 ? C_HI : C_SUB);
        col.addView(sub, wrap());
        row.addView(col, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        loadAvatar(login, av, spin, size);
        return row;
    }

    /** 连续点击计数：超过 2 秒重新开始，满 5 次给一次反馈。 */
    private void onAvatarTap() {
        long now = android.os.SystemClock.uptimeMillis();
        if (now - lastTapTime > 2000) tapCount = 0;
        lastTapTime = now;
        tapCount++;
        if (tapCount < 5) return;
        tapCount = 0;
        vibrate();
        showCard();
    }

    /** 连点五下的彩蛋提示（也走统一气泡）。 */
    private void showCard() {
        showCard(HINT_TEXT);
    }

    private void showGreeting() {
        boolean special = false;
        if (ghLogin != null) {
            for (String u : HEART_USERS) {
                if (u.equalsIgnoreCase(ghLogin)) { special = true; break; }
            }
        }
        String[] care = special ? SPECIAL_TIPS : REST_TIPS;
        // 技巧与关怀穿插：普通用户 60/40（以前是 80/20，关怀太少，用户反馈「几乎只见小贴士」）；
        // 有一类用户更想看关心的话，按 50/50。
        int techPct = special ? 50 : 60;
        java.util.Random r = new java.util.Random();
        String text = (r.nextInt(100) < techPct)
                ? TIPS[r.nextInt(TIPS.length)]
                : care[r.nextInt(care.length)];
        showCard(text);
    }

    /** 启动问候语：和别的提示用同一种气泡，不再单独做一套居中的卡片。 */
    private void showCard(final String text) {
        showBubble(text);
    }

    /** 先用首字母色块占位，头像下载成功再替换（网络差也不会空白）。 */
    private Bitmap letterAvatar(String login, int size) {
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        int[] palette = {0xFF35566F, 0xFF4A6B5A, 0xFF6E5140, 0xFF5B4E7A, 0xFF3F6A72};
        p.setColor(palette[Math.abs(login.hashCode()) % palette.length]);
        c.drawCircle(size / 2f, size / 2f, size / 2f, p);
        String ch = login.length() > 0 ? login.substring(0, 1).toUpperCase() : "?";
        p.setColor(0xFFF2F6FB);
        p.setTextSize(size * 0.52f);
        p.setTypeface(Typeface.DEFAULT_BOLD);
        p.setTextAlign(Paint.Align.CENTER);
        Paint.FontMetrics fm = p.getFontMetrics();
        c.drawText(ch, size / 2f, size / 2f - (fm.ascent + fm.descent) / 2f, p);
        return bmp;
    }

    /** 裁成圆形，填满 ImageView。 */
    private Bitmap circleCrop(Bitmap src, int size) {
        Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        BitmapShader shader = new BitmapShader(src,
                android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP);
        Matrix mx = new Matrix();
        float scale = (float) size / Math.min(src.getWidth(), src.getHeight());
        mx.setScale(scale, scale);
        mx.postTranslate((size - src.getWidth() * scale) / 2f,
                (size - src.getHeight() * scale) / 2f);
        shader.setLocalMatrix(mx);
        p.setShader(shader);
        c.drawCircle(size / 2f, size / 2f, size / 2f, p);
        return out;
    }

    /** 登录账号命中名单时，飘一次满屏装饰图案（纯视觉，不拦触摸）。 */
    private void maybePlayHearts() {
        if (heartRunning) return;
        if (ghToken == null) return;                            // 未登录不触发
        if (ghLogin == null || ghLogin.length() == 0) return;   // 账号为空不触发
        boolean hit = false;
        for (String u : HEART_USERS) {
            if (u.equalsIgnoreCase(ghLogin)) { hit = true; break; }
        }
        if (!hit) return;
        playHearts();
    }

    /** 满屏装饰图案可用的彩色字形（系统缺字形的会被探测筛掉，避免豆腐块）。 */
    // 在根布局上盖一层不接收触摸的浮层，撒一把向上飘的图案，播完自动移除。
    private static final String[] HEART_EMOJI = {
            "\u2764\uFE0F",                 // ❤️
            "\uD83D\uDC95",                 // 💕
            "\uD83D\uDC97",                 // 💗
            "\uD83D\uDC96",                 // 💖
            "\uD83D\uDC9D",                 // 💝
            "\uD83D\uDC98",                 // 💘
            "\uD83D\uDC9E",                 // 💞
            "\u2763\uFE0F",                 // ❣️
            "\uD83D\uDC93",                 // 💓
            "\uD83D\uDC9F",                 // 💟
            "\uD83D\uDC9B",                 // 💛
            "\uD83D\uDC9A",                 // 💚
            "\uD83D\uDC99",                 // 💙
            "\uD83D\uDC9C",                 // 💜
            "\uD83E\uDDE1",                 // 🧡
            "\uD83E\uDD0D",                 // 🤍
            "\uD83E\uDE77",                 // 🩷
    };

    private void playHearts() {
        final FrameLayout layer = new FrameLayout(this);
        layer.setClickable(false);
        layer.setFocusable(false);
        layer.setClipChildren(false);
        root.addView(layer, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        layer.bringToFront();

        final int w = root.getWidth() > 0 ? root.getWidth() : getResources().getDisplayMetrics().widthPixels;
        final int h = root.getHeight() > 0 ? root.getHeight() : getResources().getDisplayMetrics().heightPixels;

        // emoji 字形比常规字体行高大，View 方框必须明显大于字号，否则上下会被裁掉一半。
        final java.util.List<String> glyphs = new java.util.ArrayList<String>();
        Paint probe = new Paint();
        for (int i = 0; i < HEART_EMOJI.length; i++) {
            if (Build.VERSION.SDK_INT < 23 || probe.hasGlyph(HEART_EMOJI[i])) glyphs.add(HEART_EMOJI[i]);
        }
        final boolean useEmoji = !glyphs.isEmpty();
        final int heartRes = useEmoji ? 0
                : getResources().getIdentifier("ic_favorite", "drawable", getPackageName());
        final int[] palette = {0xFFFF4F6D, 0xFFFF7A94, 0xFFF06292, 0xFFFF5E7E,
                0xFFE0609B, 0xFFFF93AE, 0xFFFFC2CF};
        final java.util.Random rnd = new java.util.Random();
        final int count = 26;
        heartRunning = true;

        for (int i = 0; i < count; i++) {
            int sz = dp(14 + rnd.nextInt(15));          // 14~28dp，视觉大小
            int box = Math.round(sz * 1.7f);            // 方框比字号大 70%，留给字形余量
            final View heart;
            if (useEmoji) {
                TextView tv = new TextView(this);
                tv.setText(glyphs.get(rnd.nextInt(glyphs.size())));
                tv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, dp(sz));
                tv.setGravity(android.view.Gravity.CENTER);
                tv.setIncludeFontPadding(false);
                tv.setSingleLine(true);
                heart = tv;
            } else {
                ImageView iv = new ImageView(this);
                if (heartRes != 0) iv.setImageResource(heartRes);
                try {
                    iv.setImageTintList(ColorStateList.valueOf(palette[rnd.nextInt(palette.length)]));
                } catch (Exception ignored) { }
                heart = iv;
                box = sz;
            }
            heart.setAlpha(0f);
            heart.setClickable(false);
            heart.setFocusable(false);

            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(box), dp(box));
            lp.leftMargin = rnd.nextInt(Math.max(1, w - dp(box)));
            lp.topMargin = h - dp(16);
            layer.addView(heart, lp);

            float rise = h * (0.55f + rnd.nextFloat() * 0.75f);
            float drift = (rnd.nextFloat() - 0.5f) * dp(40);
            long delay = rnd.nextInt(1500);
            long dur = 2600 + rnd.nextInt(1500);
            float rot = (rnd.nextFloat() - 0.5f) * 46f;

            heart.animate().setStartDelay(delay).setDuration(dur)
                    .alpha(1f).translationY(-rise).translationX(drift).rotation(rot)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator())
                    .withEndAction(new Runnable() {
                        public void run() {
                            heart.animate().alpha(0f).setDuration(420).start();
                        }
                    }).start();
        }

        root.postDelayed(new Runnable() {
            public void run() {
                try { root.removeView(layer); } catch (Exception ignored) { }
                heartRunning = false;
            }
        }, 1500 + 4100 + 500);
    }



    /** 给文本按钮加左侧矢量图标（Material Icons，随主题色 tint）。 */
    private void applyIcon(TextView tv, String name, int color, int sizeDp) {
        if (tv == null) return;
        int id = getResources().getIdentifier(name, "drawable", getPackageName());
        if (id == 0) return;
        try {
            Drawable d = getResources().getDrawable(id).mutate();
            d.setTint(color);
            int s = dp(sizeDp);
            d.setBounds(0, 0, s, s);
            // 右侧补一个同宽的透明占位：否则「图标 + 文字」会作为整体居中，
            // 文字被左侧图标顶偏，看上去就不在按钮正中。
            Drawable pad = new ColorDrawable(Color.TRANSPARENT);
            pad.setBounds(0, 0, s, s);
            tv.setCompoundDrawables(d, null, pad, null);
            tv.setCompoundDrawablePadding(dp(7));
        } catch (Exception ignored) { }
    }

    /** 反馈页那几个入口按钮：字号、内边距、图标尺寸必须完全一致，
     *  否则高矮不齐看着很别扭（反馈 #46 就是这么来的）。新增同类按钮一律用它。 */
    private TextView fbActionBtn(String text, String icon, int textColor, int bg) {
        TextView b = new TextView(this);
        b.setText(text);
        b.setTextSize(m.backSize);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setGravity(Gravity.CENTER);
        b.setSingleLine(true);
        b.setPadding(dp(10), dp(11), dp(10), dp(11));
        b.setTextColor(textColor);
        b.setBackgroundDrawable(pill(bg));
        if (icon != null) applyIcon(b, icon, textColor, 15);
        return b;
    }

    /** 头像转圈：小尺寸下用细圈，颜色跟随主色。 */
    private ProgressBar makeAvatarSpinner(int sizePx) {
        ProgressBar p = new ProgressBar(this);
        try {
            p.getIndeterminateDrawable()
                    .setColorFilter(C_PRIMARY, android.graphics.PorterDuff.Mode.SRC_IN);
        } catch (Exception ignored) { }
        return p;
    }

    /** 下载头像：过程中转圈，成功换头像，失败退回首字母，转圈一定收起。 */
    private void loadAvatar(final String login, final ImageView iv, final ProgressBar spin,
                            final int size) {
        Bitmap cached = avatarCache.get(login);
        if (cached != null) {
            iv.setImageBitmap(cached);
            iv.setVisibility(View.VISIBLE);
            if (spin != null) spin.setVisibility(View.GONE);
            return;
        }
        if (spin != null) {
            spin.setVisibility(View.VISIBLE);
            iv.setVisibility(View.INVISIBLE);
        }
        new Thread(new Runnable() {
            public void run() {
                Bitmap got = null;
                try {
                    Bitmap raw = null;
                    String gh = "https://github.com/" + login + ".png?size=" + (size * 2);
                    String av = "https://avatars.githubusercontent.com/" + login + "?size=" + (size * 2);
                    List<String> cand = new ArrayList<String>();
                    cand.add(rawBase() + gh);       // 启动时测出来的最优通道优先
                    cand.add(gh);
                    cand.add(av);
                    // 头像只是点缀，只多试两个镜像，别为一张图等太久
                    for (int i = 0; i < Math.min(2, MIRRORS.length); i++) cand.add(MIRRORS[i] + gh);
                    String[] urls = cand.toArray(new String[0]);
                    for (String u : urls) {
                        try {
                            byte[] data = httpGetBytes(u);
                            raw = BitmapFactory.decodeByteArray(data, 0, data.length);
                            if (raw != null && raw.getWidth() >= 8) break;
                        } catch (Exception ignored) { }
                    }
                    if (raw != null && raw.getWidth() >= 8) {
                        got = circleCrop(raw, size);
                        avatarCache.put(login, got);
                    }
                } catch (Exception ignored) {
                    // 失败就保留首字母头像，不影响名单显示
                }
                final Bitmap result = got;
                runOnUiThread(new Runnable() {
                    public void run() {
                        if (spin != null) spin.setVisibility(View.GONE);
                        iv.setVisibility(View.VISIBLE);
                        if (result != null) iv.setImageBitmap(result);
                    }
                });
            }
        }).start();
    }

    /** 无需登录的 GitHub 读取（公开仓库）。 */
    private String httpGetPublic(String url) throws Exception {
        byte[] data = httpGetBytes(url);
        return new String(data, "UTF-8");
    }

    /** 简单 GET，返回原始字节（头像等二进制用）。 */
    private byte[] httpGetBytes(String url) throws Exception {
        java.net.HttpURLConnection cn = (java.net.HttpURLConnection) new URL(url).openConnection();
        try {
            cn.setConnectTimeout(6000);
            cn.setReadTimeout(9000);
            cn.setInstanceFollowRedirects(true);
            cn.setRequestProperty("Accept", "application/vnd.github+json");
            cn.setRequestProperty("User-Agent", "HZKnowledge-Android");
            java.io.InputStream is = cn.getInputStream();
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] b = new byte[8192];
            int n;
            while ((n = is.read(b)) > 0) bos.write(b, 0, n);
            is.close();
            return bos.toByteArray();
        } finally {
            cn.disconnect();
        }
    }

    // ================= 今日新增 =================
    private static final int TODAY_PAGE = 40;
    /** 一次最多渲染多少行：再多行 View 堆在 ScrollView 里会拖垮手表，宁可提示换个词。 */
    private static final int HIT_LIMIT = 400;

    private void buildTodayScreen() {
        todayList = new SvList();
        todayList.addHead(makeBackHeader("\u2039 返回学科", new View.OnClickListener() {
            public void onClick(View v) { goBack(S_SUBJECT); }
        }));

        todayTitle = new TextView(this);
        todayTitle.setText("今日新增");
        todayTitle.setTextSize(m.subtitleSize + 2f);
        todayTitle.setTypeface(Typeface.DEFAULT_BOLD);
        todayTitle.setTextColor(C_PRIMARY);
        todayTitle.setGravity(Gravity.CENTER);
        todayTitle.setPadding(0, 0, 0, dp(3));
        todayList.addHead(todayTitle);

        todayFallbackBtn = new TextView(this);
        todayFallbackBtn.setTextSize(m.subtitleSize - 1f);
        todayFallbackBtn.setTextColor(C_SUB);
        todayFallbackBtn.setGravity(Gravity.CENTER);
        todayFallbackBtn.setPadding(dp(8), dp(6), dp(8), dp(6));
        todayFallbackBtn.setBackground(clickBg(C_PILL));
        todayFallbackBtn.setVisibility(View.GONE);
        todayList.addHead(todayFallbackBtn);

        todayScreen.addView(todayList.sc, lw());
    }

    /** 重新收集「今日新增」的知识点，先只放出第一页。
     *  严格按设备当天日期匹配：今天没新增就老实说没有，不再把历史上最近的一批
     *  当成「今日」（那样 28 号的内容会一直挂着，日期永远是错的）。
     *  fallback 为 true 时才去取最近一批，由下面的按钮切换。 */
    private void buildTodayData(boolean fallback) {
        if (todayList == null) return;
        todayAll.clear();
        todayShown.clear();

        String today = new java.text.SimpleDateFormat("yyMMdd", java.util.Locale.US)
                .format(new java.util.Date());
        // 直接查日期索引：不再为了拿「今天有哪些」把一千多个节点扫一遍
        boolean hasToday = nodesByDate.containsKey(today);
        String newest = "";
        for (String d : nodesByDate.keySet()) {
            if (newest.length() == 0 || d.compareTo(newest) > 0) newest = d;
        }
        // 今天有新增就只显示今天的，「看最近一批」这个开关自动失效；
        // 今天没有时才允许退回历史上最近的一批
        if (hasToday) fallback = false;
        todayFallbackOn = fallback;
        String pick = fallback ? newest : today;
        todayBuiltFor = today;

        if (pick.length() > 0) {
            List<Node> bucket = nodesByDate.get(pick);
            if (bucket != null) todayAll.addAll(bucket);
        }

        String newestLabel = (newest.length() == 6)
                ? newest.substring(2, 4) + "-" + newest.substring(4, 6) : "";
        if (todayAll.isEmpty()) {
            todayTitle.setText("今日暂无新增");
        } else if (!fallback) {
            todayTitle.setText("今日新增 \u00b7 " + todayAll.size() + " 条");
        } else {
            todayTitle.setText("最近新增（" + newestLabel + "）\u00b7 " + todayAll.size() + " 条");
        }
        applyTodayFallbackBtn(hasToday, newest, newestLabel);

        // 数据不是一次到位的：续载时先由 fill 补一批，再渲染
        todayList.fill = new Runnable() {
            public void run() { loadMoreToday(); }
        };
        // 先垫一批数据再 bind：今日新增的行来自 todayShown，不先填就是空列表
        // （以前只有滚动触发续载才填，首次进来是空的）
        loadMoreToday();
        todayList.bind(new RowSource() {
            public int size() { return todayShown.size(); }
            public View make(final int i) {
                final Node n = todayShown.get(i);
                return hitRow(n.name, shortPath(n.path()), new Runnable() {
                    public void run() { openNodeFrom(n, S_TODAY); }
                });
            }
        });
    }

    /** 今天没新增时给一个「看最近一批」的入口；已经在看最近一批时给一个「只看今天」的回路。 */
    private void applyTodayFallbackBtn(boolean hasToday, String newest, String newestLabel) {
        if (todayFallbackBtn == null) return;
        if (hasToday || newest.length() != 6) {
            todayFallbackBtn.setVisibility(View.GONE);
            return;
        }
        todayFallbackBtn.setVisibility(View.VISIBLE);
        todayFallbackBtn.setText(todayFallbackOn ? "只看今日新增"
                : "看看最近一批（" + newestLabel + "）");
        todayFallbackBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { vibrate(); buildTodayData(!todayFallbackOn); }
        });
    }

    /** 往数据里补一页；行由 SvList 分帧追加，滑起来无感。 */
    private void loadMoreToday() {
        if (todayShown.size() >= todayAll.size() || todayShown.size() >= HIT_LIMIT) return;
        int from = todayShown.size();
        int to = Math.min(Math.min(todayAll.size(), from + TODAY_PAGE), HIT_LIMIT);
        for (int i = from; i < to; i++) todayShown.add(todayAll.get(i));
    }

    // ================= 显示调节 =================
    private interface OnFloatChange { void onChanged(float v); }

    /** 滑块松手时的回调：用于在用户调完后统一保存并重建界面。 */
    private interface OnFloatRelease { void onReleased(float v); }

    /** 高级字号区是否展开（只在本次界面有效，不落盘）。 */
    private boolean displayAdvancedOpen = false;

    private void buildDisplayScreen() {
        displayScroll = new OverScrollScrollView(this);
        displayScroll.setVerticalScrollBarEnabled(false);
        roundPadScroll(displayScroll);
        displayInner = new LinearLayout(this);
        displayInner.setOrientation(LinearLayout.VERTICAL);
        displayScroll.addView(displayInner);
        displayScreen.addView(displayScroll, lw());
    }

    /** 首页科目排序页：设置页只留一个入口，9 行排序单独放一页。 */
    private void buildOrderScreen() {
        orderBackBtn = makeBack("‹ 返回", new View.OnClickListener() {
            public void onClick(View v) { goBack(S_SETTINGS); }
        });
        orderScroll = new OverScrollScrollView(this);
        orderScroll.setVerticalScrollBarEnabled(false);
        roundPadScroll(orderScroll);
        orderInner = new LinearLayout(this);
        orderInner.setOrientation(LinearLayout.VERTICAL);
        orderScroll.addView(orderInner);
        orderScreen.addView(orderScroll, lw());
    }

    // ================= 单词：词书 / 索引 / 进度 =================
    // 词书按需下载（原始 zip 存在仓库 dict/ 目录），下载后解压成 JSON Lines，
    // 另建一份「行偏移索引」。查词时按偏移读那一行，不整本解析——词库再大也不拖慢启动。

    /** 一本词书。words/size 来自内置清单 dict_books.json。 */
    private static class WordBook {
        String id, name, file;
        int words;
        long size;
        WordBook(String id, String name, String file, int words, long size) {
            this.id = id;
            this.name = name;
            this.file = file;
            this.words = words;
            this.size = size;
        }
    }

    private final List<WordBook> wordBooks = new ArrayList<WordBook>();
    private WordBook curBook;                       // 当前要背的词书
    private final ArrayList<String> wWords = new ArrayList<String>();   // 词头（按文件行序 = 学习顺序）
    private final ArrayList<Integer> wOff = new ArrayList<Integer>();   // 每行在 JSON 里的字节偏移
    private final ArrayList<Integer> wLen = new ArrayList<Integer>();   // 每行字节长度
    private int[] wSorted;                          // 按词头字母排序后的下标，搜索用
    private byte[] wProg;                           // 进度：每词 8 字节
    private boolean wordIdxReady = false;
    private int wordDailyNew = 10;                  // 每日新词数
    private static final java.util.regex.Pattern W_HEAD =
            java.util.regex.Pattern.compile("\"wordHead\"\\s*:\\s*\"([^\"]+)\"");
    /** buildWordIndex 里按字节找词头用，比正则快得多（不需要整行解码）。 */
    private static final byte[] HEAD_KEY = ("\"wordHead\"").getBytes();

    private File dictDir() {
        File d = new File(getFilesDir(), "dict");
        if (!d.exists()) d.mkdirs();
        return d;
    }
    private File bookJson(String id) { return new File(dictDir(), id + ".json"); }
    private File bookIdx(String id) { return new File(dictDir(), id + ".idx"); }
    private File bookProg(String id) { return new File(dictDir(), id + ".prog"); }

    // ================= 单词：发音与助记图 =================

    private android.media.MediaPlayer wPlayer;
    private int wAutoSpoken = -1;      // 已经自动发音过的词下标，翻面时不再响一次
    private String wCurHead = "";      // 当前词（展开时再读一遍用）

    // ---- 一期：当天重来池 + 复习统计 ----
    private int[] wRetry;              // 本轮评过「不认识」的词
    private int wRetryN;
    private int wRound = 1;            // 第几轮，最多 3
    private int wStatDue, wStatFresh;  // 本次开始时的到期数 / 新词数
    private int wStatNewDone, wStatReviewDone, wStatHard;   // 本次新学 / 复习 / 没记住
    private int wMode;                                      // 0 混合 / 1 只复习 / 2 生词本
    private final java.util.HashSet<Integer> wCounted = new java.util.HashSet<Integer>();
    private Bitmap wPicBm;             // 当前词的助记图（翻面复用，避免每次重解码）
    private String wPicUrl;

    private File voiceDir() {
        File d = new File(dictDir(), "voice");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    private File picDir() {
        File d = new File(dictDir(), "pic");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    /** 有没有网。只用来决定「能不能发音 / 看图」，背单词本身不受影响。 */
    private boolean online() {
        try {
            android.net.ConnectivityManager cm = (android.net.ConnectivityManager)
                    getSystemService(CONNECTIVITY_SERVICE);
            if (cm == null) return false;
            android.net.NetworkInfo ni = cm.getActiveNetworkInfo();
            return ni != null && ni.isConnected();
        } catch (Throwable e) {
            return true;      // 状态拿不到就别拦着，让它去试
        }
    }

    /** 静音模式：不出喇叭、不自动读。和「自动发音」互斥（打开一个会关掉另一个）。 */
    private boolean muteOn() { return prefs.getBoolean("word_mute", false); }

    private boolean autoSpeakOn() {
        return prefs.getBoolean("word_auto_speak", false) && !muteOn();
    }
    private boolean showPicOn() { return prefs.getBoolean("word_show_pic", true); }
    private String accent() { return prefs.getString("word_accent", "us"); }

    /** 缓存文件名：词 + 口音。词里可能有空格、撇号，用 hashCode 避开非法字符。 */
    private static String voiceKey(String word, int type) {
        return Integer.toHexString((word.toLowerCase() + "#" + type).hashCode());
    }

    /** 播一个词的发音：有缓存直接播，没有就下 10 KB 再播。下载在子线程，播放回主线程。 */
    private void speakWord(final String word) {
        if (word == null || word.trim().length() == 0) return;
        if (muteOn()) return;      // 静音模式下任何入口都不出声
        final int type = "uk".equals(accent()) ? 1 : 2;
        final File f = new File(voiceDir(), voiceKey(word, type) + ".mp3");
        if (!online()) {
            toast("离线模式，发音需要联网");
            return;
        }
        new Thread(new Runnable() {
            public void run() {
                try {
                    if (!f.exists() || f.length() < 600) {
                        String q;
                        try { q = java.net.URLEncoder.encode(word.trim(), "UTF-8"); }
                        catch (Exception e) { q = word.trim(); }
                        downToFile("https://dict.youdao.com/dictvoice?audio=" + q
                                + "&type=" + type, f);
                        trimCache(voiceDir(), 5 * 1024 * 1024);
                    }
                    final File target = f;
                    runOnUiThread(new Runnable() {
                        public void run() {
                            target.setLastModified(System.currentTimeMillis());   // 当最近使用时间
                            playVoice(target);
                        }
                    });
                } catch (Throwable e) {
                    runOnUiThread(new Runnable() {
                        public void run() { toast("发音加载失败，检查网络"); }
                    });
                }
            }
        }).start();
    }

    /** 小文件下载（发音 10 KB、助记图 45 KB），不参与词书下载进度。 */
    private void downToFile(String url, File out) throws Exception {
        java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
        try {
            c.setConnectTimeout(8000);
            c.setReadTimeout(15000);
            c.setInstanceFollowRedirects(true);
            c.setRequestProperty("User-Agent", "HZKnowledge-Android");
            int code = c.getResponseCode();
            if (code >= 400) throw new Exception("HTTP " + code);
            InputStream in = c.getInputStream();
            File tmp = new File(out.getAbsolutePath() + ".part");
            java.io.FileOutputStream fos = new java.io.FileOutputStream(tmp);
            byte[] buf = new byte[16 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) fos.write(buf, 0, n);
            fos.flush();
            fos.close();
            in.close();
            if (tmp.length() < 300) { tmp.delete(); throw new Exception("文件不完整"); }
            if (out.exists()) out.delete();
            if (!tmp.renameTo(out)) throw new Exception("写入失败");
        } finally {
            try { c.disconnect(); } catch (Exception ignored) { }
        }
    }

    private void playVoice(File f) {
        try {
            stopVoice();
            wPlayer = new android.media.MediaPlayer();
            wPlayer.setDataSource(f.getAbsolutePath());
            wPlayer.setOnCompletionListener(new android.media.MediaPlayer.OnCompletionListener() {
                public void onCompletion(android.media.MediaPlayer mp) { stopVoice(); }
            });
            wPlayer.prepare();
            wPlayer.start();
        } catch (Throwable e) {
            toast("发音播放失败");
            stopVoice();
        }
    }

    /** 停掉并释放播放器。播下一个词前必须调，否则两句会叠在一起。 */
    private void stopVoice() {
        if (wPlayer == null) return;
        try { wPlayer.stop(); } catch (Throwable ignored) { }
        try { wPlayer.release(); } catch (Throwable ignored) { }
        wPlayer = null;
    }

    /** 目录超限就按「最久没用」删到限内。 */
    private void trimCache(File dir, long limit) {
        try {
            File[] fs = dir.listFiles();
            if (fs == null || fs.length == 0) return;
            long total = 0;
            for (File f : fs) total += f.length();
            if (total <= limit) return;
            java.util.Arrays.sort(fs, new java.util.Comparator<File>() {
                public int compare(File a, File b) {
                    return Long.valueOf(a.lastModified()).compareTo(b.lastModified());
                }
            });
            for (File f : fs) {
                if (total <= limit) break;
                long len = f.length();
                if (f.delete()) total -= len;
            }
        } catch (Throwable ignored) { }
    }

    /** 纯图标按钮用：不补右侧透明占位，图标才会居中（applyIcon 是给「图标+文字」用的）。 */
    private void applyIconOnly(TextView tv, String name, int color, int sizeDp) {
        try {
            int id = getResources().getIdentifier(name, "drawable", getPackageName());
            if (id == 0) return;
            Drawable d = getResources().getDrawable(id).mutate();
            d.setTint(color);
            int sz = dp(sizeDp);
            d.setBounds(0, 0, sz, sz);
            tv.setCompoundDrawables(d, null, null, null);
        } catch (Throwable ignored) { }
    }

    /** 按屏幕宽度降采样解码，别把 1000×1000 的图整张塞进内存（手表扛不住）。 */
    private Bitmap decodeScaled(File f, int maxW) {
        try {
            BitmapFactory.Options probe = new BitmapFactory.Options();
            probe.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getAbsolutePath(), probe);
            int sample = 1;
            while (probe.outWidth / (sample * 2) >= maxW) sample *= 2;
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inSampleSize = sample;
            o.inPreferredConfig = Bitmap.Config.RGB_565;   // 插画用 565 够了，内存省一半
            return BitmapFactory.decodeFile(f.getAbsolutePath(), o);
        } catch (Throwable e) {
            return null;
        }
    }

    // ---------- 助记图索引：任何词书都能用 ----------

    private static final String PIC_BASE = "http://ydschool-online.nos.netease.com/";
    private java.util.HashMap<String, String> picMap;

    /** 词 → 图片地址。第一次用到才读那张表（1162 条 / 78 KB），不影响启动。 */
    private String picUrlFor(String word) {
        if (word == null) return "";
        String k = word.trim().toLowerCase();
        if (k.length() == 0) return "";
        if (picMap == null) loadPicMap();
        if (picMap == null) return "";
        String rel = picMap.get(k);
        return (rel == null) ? "" : PIC_BASE + rel;
    }

    private void loadPicMap() {
        java.util.HashMap<String, String> m = new java.util.HashMap<String, String>(2048);
        InputStream in = null;
        try {
            in = getAssets().open("word_pics.txt");
            BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"));
            String line;
            while ((line = r.readLine()) != null) {
                int t = line.indexOf('\t');
                if (t <= 0 || t >= line.length() - 1) continue;
                m.put(line.substring(0, t), line.substring(t + 1));
            }
            r.close();
        } catch (Throwable ignored) {
            // 表读不到就当没有图，不影响其它功能
        } finally {
            try { if (in != null) in.close(); } catch (Exception ignored) { }
        }
        picMap = m;
    }

    /** 卡片里的助记图：先占位，下好了换成真图；点击全屏（复用详情页那套缩放）。 */
    private void addWordPicture(final String url) {
        if (url == null || url.length() == 0 || !showPicOn()) return;
        final File cache = new File(picDir(), Integer.toHexString(url.hashCode()) + ".png");

        final ImageView iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        iv.setAdjustViewBounds(true);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x11FFFFFF);
        bg.setCornerRadius(dp(10));
        iv.setBackgroundDrawable(bg);
        iv.setMinimumHeight(dp(60));
        iv.setContentDescription("助记图");
        wCardBox.addView(iv, wrapGap(8));

        final int maxW = Math.max(dp(160),
                getResources().getDisplayMetrics().widthPixels - dp(60));   // 卡片与页面内边距
        iv.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                vibrate();
                Bitmap bm = decodeScaled(cache, maxW * 2);   // 放大时用两倍宽，清楚些
                if (bm == null) bm = (Bitmap) iv.getTag();
                if (bm != null) showFullscreen(bm);
            }
        });

        // 翻面会重建整块卡片：同一个词同一张图直接复用上次解好的位图，别重解码一遍
        if (wPicBm != null && url.equals(wPicUrl)) {
            iv.setImageBitmap(wPicBm);
            iv.setTag(wPicBm);
            return;
        }
        // 先看缓存文件：有就直接显示，不联网
        if (cache.exists()) {
            Bitmap bm = decodeScaled(cache, maxW);
            if (bm != null) {
                wPicBm = bm;
                wPicUrl = url;
                iv.setImageBitmap(bm);
                iv.setTag(bm);
                return;
            }
        }
        if (!online()) return;      // 离线就不占位了，留个空框更难看

        new Thread(new Runnable() {
            public void run() {
                try {
                    if (!cache.exists()) {
                        downToFile(url, cache);
                        trimCache(picDir(), 20 * 1024 * 1024);
                    }
                    final Bitmap bm = decodeScaled(cache, maxW);
                    if (bm == null) throw new Exception("解码失败");
                    runOnUiThread(new Runnable() {
                        public void run() {
                            cache.setLastModified(System.currentTimeMillis());
                            wPicBm = bm;
                            wPicUrl = url;
                            iv.setImageBitmap(bm);
                            iv.setTag(bm);
                        }
                    });
                } catch (Throwable e) {
                    runOnUiThread(new Runnable() {
                        public void run() {
                            // 失败了就连占位块一起撤掉，别留一个灰框
                            try { wCardBox.removeView(iv); } catch (Throwable ignored) { }
                        }
                    });
                }
            }
        }).start();
    }


    /** 读内置词书清单；再恢复上次选的词书。 */
    private void loadWordBooks() {
        if (!wordBooks.isEmpty()) return;
        try {
            JSONObject o = readJsonAsset("dict_books.json");
            JSONArray arr = o.optJSONArray("books");
            for (int i = 0; i < arr.length(); i++) {
                JSONObject b = arr.optJSONObject(i);
                if (b == null) continue;
                wordBooks.add(new WordBook(b.optString("id", ""), b.optString("name", ""),
                        b.optString("file", ""), b.optInt("words", 0), b.optLong("size", 0)));
            }
        } catch (Exception ignored) { }
        String cur = prefs.getString("word_book", "");
        curBook = null;
        for (WordBook b : wordBooks) if (b.id.equals(cur)) curBook = b;
        wordDailyNew = prefs.getInt("word_daily_new", 10);
        // 正式版：测试期预置的复习词在这里一次性清干净（遍历所有已下载词书，不只当前那本），
        // 免得用户在测试版里换过词书，正式版只清当前书、别的书还留着假进度。
        if (appVersion().indexOf("pre") < 0) clearSeededWordsForRelease();
    }

    /** 正式版清掉测试期预置的复习词：按 seed_<id> 记录把对应 .prog 的进度归零，再删记录。
     *  直接操作文件，不依赖「当前书已加载」——所以能覆盖用户测试期用过的每一本书。 */
    private void clearSeededWordsForRelease() {
        for (WordBook b : wordBooks) {
            String key = "seed_" + b.id;
            String raw = prefs.getString(key, "");
            if (raw.length() == 0) continue;
            File pf = bookProg(b.id);
            if (pf.exists() && pf.length() > 0) {
                try {
                    byte[] p = new byte[(int) pf.length()];
                    java.io.DataInputStream in = new java.io.DataInputStream(
                            new java.io.BufferedInputStream(new java.io.FileInputStream(pf)));
                    try { in.readFully(p); } finally { try { in.close(); } catch (Exception ignored) { } }
                    boolean dirty = false;
                    for (String x : raw.split(",")) {
                        try {
                            int i = Integer.parseInt(x.trim());
                            if (i < 0 || i * 8 + 7 >= p.length) continue;
                            p[i * 8 + 1] = 0; p[i * 8 + 2] = 0;   // interval
                            p[i * 8 + 3] = 0; p[i * 8 + 4] = 0;   // due
                            p[i * 8 + 5] = 0;                      // reps
                            p[i * 8 + 6] = 0;                      // lapse
                            dirty = true;
                        } catch (Exception ignored) { }
                    }
                    if (dirty) {
                        java.io.FileOutputStream fo = new java.io.FileOutputStream(pf);
                        try { fo.write(p); fo.flush(); } finally { try { fo.close(); } catch (Exception ignored) { } }
                    }
                } catch (Exception ignored) { }
            }
            prefs.edit().remove(key).apply();
        }
    }

    private boolean bookDownloaded(WordBook b) {
        return b != null && bookJson(b.id).exists();
    }

    /** 下载词书：zip 落到临时文件 → 解压 → 建索引 → 删 zip。全程子线程，进度回调刷新设置页。 */
    private void downloadWordBook(final WordBook b) {
        if (downloadingBook != null) { toast("正在下载「" + downloadingBook.name + "」，等它完成"); return; }
        downloadingBook = b;
        dlActView = null;
        dlStage = "下载中";
        downloadPct = 0;
        renderWordSettings();
        new Thread(new Runnable() {
            public void run() {
                File zip = new File(dictDir(), b.id + ".zip.part");
                Exception err = null;
                try {
                    // 直连 + 镜像逐个试：与更新检查共用同一套镜像表
                    List<String> urls = new ArrayList<String>();
                    String path = "https://raw.githubusercontent.com/" + REPO + "/main/dict/" + b.id + ".zip";
                    urls.add(path);
                    for (int i = 0; i < MIRRORS.length && i < 3; i++) urls.add(MIRRORS[i] + path);
                    Exception last = null;
                    for (String u : urls) {
                        try {
                            fetchToFile(u, zip, b.size);
                            err = null;
                            break;
                        } catch (Exception e) {
                            err = e;
                            last = e;
                        }
                    }
                    if (err != null) throw (last == null ? err : last);
                    // 下载 100% 只是把 zip 拉完了，后面解压 + 建索引还有两步，
                    // 手表上二十来兆的词书要好几秒。不切阶段的话界面一直停在 100%，像卡死。
                    showDlProgress("解压中", 0);
                    extractAndIndex(b, zip);
                    zip.delete();
                } catch (Exception e) {
                    err = e;
                    zip.delete();
                }
                final Exception fe = err;
                runOnUiThread(new Runnable() {
                    public void run() {
                        downloadingBook = null;
                        if (fe != null) {
                            toast("下载失败：" + fe.getMessage());
                        } else {
                            toast("「" + b.name + "」已下载");
                            if (curBook == null) {
                                curBook = b;
                                prefs.edit().putString("word_book", b.id).apply();
                            }
                            wordIdxReady = false;
                        }
                        renderWordSettings();
                    }
                });
            }
        }).start();
    }

    private WordBook downloadingBook;
    /** 下载/解压/整理 三个阶段共用一个百分比，跨线程读，必须 volatile。 */
    private volatile int downloadPct;
    /** 当前阶段：「下载中」「解压中」「整理中」——100% 之后还有两步，不标出来就像卡死。 */
    private volatile String dlStage = "下载中";
    /** 正在下载那行的进度按钮；有它就只改这几个字，不重画整页（80 行重建很贵）。 */
    private TextView dlActView;

    /** 带进度的下载。大小已知时按已读数算百分比。 */
    private void fetchToFile(String url, File out, long expectSize) throws Exception {
        java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
        try {
            c.setConnectTimeout(15000);
            c.setReadTimeout(30000);
            c.setInstanceFollowRedirects(true);
            c.setRequestProperty("User-Agent", "HZKnowledge-Android");
            InputStream in = c.getInputStream();
            java.io.FileOutputStream fos = new java.io.FileOutputStream(out);
            long total = c.getContentLength() > 0 ? c.getContentLength() : expectSize;
            long got = 0;
            byte[] buf = new byte[16 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                fos.write(buf, 0, n);
                got += n;
                if (total > 0) {
                    int pct = (int) (got * 100 / total);
                    if (pct / 5 != downloadPct / 5) showDlProgress("下载中", pct);
                }
            }
            fos.flush();
            fos.close();
            in.close();
        } finally {
            try { c.disconnect(); } catch (Exception ignored) { }
        }
        if (out.length() < 1000) throw new Exception("文件不完整");
    }

    /** 推进下载进度。只改那一行按钮上的字——renderWordSettings 会重建 80 行，每 5% 重来一次太亏。 */
    private void showDlProgress(final String stage, final int pct) {
        dlStage = stage;
        downloadPct = pct;
        runOnUiThread(new Runnable() {
            public void run() {
                if (dlActView != null) dlActView.setText(dlLabel());
                else renderWordSettings();
            }
        });
    }

    /** 「下载中 45%」「解压中…」「整理中 60%」 */
    private String dlLabel() {
        if (downloadPct <= 0) return dlStage + "…";
        return dlStage + " " + downloadPct + "%";
    }

    /** 解压 zip 里的 JSON，并建行偏移索引。 */
    private void extractAndIndex(WordBook b, File zip) throws Exception {
        File json = bookJson(b.id);
        java.util.zip.ZipInputStream zin = new java.util.zip.ZipInputStream(
                new BufferedInputStream(new java.io.FileInputStream(zip)));
        try {
            java.util.zip.ZipEntry e = zin.getNextEntry();
            if (e == null) throw new Exception("压缩包是空的");
            java.io.FileOutputStream fos = new java.io.FileOutputStream(json);
            byte[] buf = new byte[32 * 1024];
            int n;
            while ((n = zin.read(buf)) > 0) fos.write(buf, 0, n);
            fos.flush();
            fos.close();
        } finally {
            try { zin.close(); } catch (Exception ignored) { }
        }
        buildWordIndex(b, json);
    }

    /** 扫一遍 JSON 记录每行的字节偏移与词头，写成 <id>.idx。只做一次，之后读索引。
     *
     *  这里必须顺序流式扫，不能用 RandomAccessFile.readLine()：
     *  那个方法是「每次 read() 一个字节」，19 MB 的词书要发近两千万次 native 调用，
     *  手表上几十秒起步——用户看到的「下载到 100% 又卡很久」主要就是这一步（另一步是解压）。
     *  改成 64 KB 缓冲顺序读，并且只在字节里找 "wordHead" 那一小段，
     *  整行的中文释义一个字都不解码（原来每行都要 new 一个 1.5 KB 的 String）。 */
    private void buildWordIndex(WordBook b, File json) throws Exception {
        // 用局部集合，不碰成员数组：这个方法跑在下载线程里，而主线程可能正在搜索页读它们。
        // 下载完成后会把 wordIdxReady 置 false，下次进页面由 ensureWordIndex() 重新读索引。
        ArrayList<String> words = new ArrayList<String>();
        ArrayList<Integer> offs = new ArrayList<Integer>();
        ArrayList<Integer> lens = new ArrayList<Integer>();

        final long total = json.length();
        final byte[] buf = new byte[64 * 1024];
        final byte[] line = new byte[64 * 1024];      // 单行上限；超长行只用于取词头，多余的截掉
        int lineLen = 0;
        long lineStart = 0;                          // 当前行在文件里的起始字节位置
        long pos = 0;                                // 已读总字节
        int last5 = -1;

        InputStream raw = new BufferedInputStream(new java.io.FileInputStream(json), 64 * 1024);
        try {
            int n;
            while ((n = raw.read(buf)) > 0) {
                for (int i = 0; i < n; i++) {
                    byte c = buf[i];
                    if (c == '\n') {
                        String w = headOf(line, lineLen);
                        if (w != null) {
                            words.add(w);
                            offs.add((int) lineStart);
                            lens.add((int) (pos + i - lineStart + 1));   // 含换行符，与 readLine 的取值一致
                        }
                        lineStart = pos + i + 1;
                        lineLen = 0;
                    } else if (lineLen < line.length) {
                        line[lineLen++] = c;
                    }
                }
                pos += n;
                // 「整理中」按已读字节报百分比：这一步是 100% 之后最长的，必须有反馈
                if (total > 0) {
                    int pct = (int) (pos * 100 / total);
                    if (pct / 5 != last5) {
                        last5 = pct / 5;
                        showDlProgress("整理中", pct);
                    }
                }
            }
            // 最后一行可能没有换行符
            if (lineLen > 0) {
                String w = headOf(line, lineLen);
                if (w != null) {
                    words.add(w);
                    offs.add((int) lineStart);
                    lens.add((int) (pos - lineStart));
                }
            }
        } finally {
            try { raw.close(); } catch (Exception ignored) { }
        }

        // 写索引：magic + 条数 + 每条（词、偏移、长度）
        DataOutputStream out = new DataOutputStream(new BufferedOutputStream(
                new java.io.FileOutputStream(bookIdx(b.id))));
        try {
            out.writeInt(0x48575A31);   // "HWZ1"
            out.writeInt(words.size());
            for (int i = 0; i < words.size(); i++) {
                wStr(out, words.get(i));
                out.writeInt(offs.get(i));
                out.writeInt(lens.get(i));
            }
            out.flush();
        } finally {
            out.close();
        }
    }

    /** 一行 JSON 的字节里取 "wordHead" 的值；没有就返回 null。
     *  词头是纯英文（可能带空格、连字符、撇号），按 ASCII 直接切出来，
     *  不像原来那样把整行（含中文释义、例句）都解码成 String。 */
    private static String headOf(byte[] line, int len) {
        for (int i = 0; i + HEAD_KEY.length <= len; i++) {
            int k = 0;
            while (k < HEAD_KEY.length && line[i + k] == HEAD_KEY[k]) k++;
            if (k != HEAD_KEY.length) continue;
            int j = i + HEAD_KEY.length;
            while (j < len && (line[j] == ' ' || line[j] == '\t')) j++;
            if (j >= len || line[j] != ':') continue;
            j++;
            while (j < len && (line[j] == ' ' || line[j] == '\t')) j++;
            if (j >= len || line[j] != '"') continue;
            j++;
            int s = j;
            while (j < len && line[j] != '"') j++;
            if (j > s) return new String(line, s, j - s);   // 词头是 ASCII，默认字符集即可
        }
        return null;
    }

    /** 载入当前词书的索引和进度（进背单词页/搜索页时调，一次约 50ms）。 */
    private boolean ensureWordIndex() {
        if (wordIdxReady) return true;
        loadWordBooks();
        if (curBook == null || !bookDownloaded(curBook)) return false;
        wWords.clear();
        wOff.clear();
        wLen.clear();
        try {
            DataInputStream in = new DataInputStream(new BufferedInputStream(
                    new java.io.FileInputStream(bookIdx(curBook.id))));
            try {
                if (in.readInt() != 0x48575A31) throw new Exception("索引格式不对");
                int n = in.readInt();
                for (int i = 0; i < n; i++) {
                    wWords.add(rStr(in));
                    wOff.add(in.readInt());
                    wLen.add(in.readInt());
                }
            } finally {
                in.close();
            }
        } catch (Exception e) {
            toast("索引读取失败，请重新下载这本词书");
            return false;
        }
        // 排序下标：搜索时按字母序二分
        int n = wWords.size();
        Integer[] idx = new Integer[n];
        for (int i = 0; i < n; i++) idx[i] = i;
        java.util.Arrays.sort(idx, new java.util.Comparator<Integer>() {
            public int compare(Integer a, Integer b) {
                return wWords.get(a).compareToIgnoreCase(wWords.get(b));
            }
        });
        wSorted = new int[n];
        for (int i = 0; i < n; i++) wSorted[i] = idx[i];
        // 进度：每词 8 字节，首次全 0
        byte[] p = new byte[n * 8];
        File pf = bookProg(curBook.id);
        if (pf.exists() && pf.length() == p.length) {
            try {
                java.io.DataInputStream pi = new java.io.DataInputStream(
                        new java.io.BufferedInputStream(new java.io.FileInputStream(pf)));
                try { pi.readFully(p); } catch (Exception ignored) { }
                try { pi.close(); } catch (Exception ignored) { }
            } catch (Exception ignored) { }
        }
        wProg = p;
        wordIdxReady = true;
        seedTestReviewWords();
        return true;
    }

    /** 测试期预置几个复习词，方便试四选一和复习入口。
     *  只在 versionName 带 pre 的包里执行；正式版反而会把当初种下的进度清零。 */
    private void seedTestReviewWords() {
        if (curBook == null || wProg == null) return;
        String key = "seed_" + curBook.id;
        String raw = prefs.getString(key, "");
        boolean pre = appVersion().indexOf("pre") >= 0;

        if (!pre) {
            // 正式版：清掉测试期种下的假进度，别让用户看到凭空的复习词
            if (raw.length() > 0) {
                for (String x : raw.split(",")) {
                    try {
                        int i = Integer.parseInt(x.trim());
                        if (i < 0 || i * 8 + 7 >= wProg.length) continue;
                        setProgShort(i, 1, 0);
                        setProgShort(i, 3, 0);
                        setProgByte(i, 5, 0);
                        setProgByte(i, 6, 0);
                    } catch (Exception ignored) { }
                }
                saveWordProgress();
                prefs.edit().remove(key).apply();
                log("WORD", "正式版：已清掉测试期预置的复习词");
            }
            return;
        }

        if (raw.length() > 0) return;          // 这本已经种过，别每次进页面都重来
        int n = wWords.size();
        if (n < 40) return;

        int today = todayDay();
        StringBuilder ids = new StringBuilder();
        int count = 0;
        for (int i = 20; i < n && count < 6; i++) {
            if (progShort(i, 1) != 0 || progByte(i, 5) != 0) continue;   // 不覆盖真实进度
            setProgShort(i, 1, 2 + count);        // 间隔 2~7 天
            setProgShort(i, 3, today);            // 今天到期
            setProgByte(i, 5, 2);                 // 当成背过两次
            setProgByte(i, 6, count >= 4 ? 2 : 0);// 后两个记成「忘过两次」，顺便能测生词本
            ids.append(i).append(',');
            count++;
        }
        if (count > 0) {
            saveWordProgress();
            prefs.edit().putString(key, ids.toString()).apply();
            log("WORD", "测试包：预置 " + count + " 个复习词（正式版会自动清掉）");
        } else {
            prefs.edit().putString(key, "").apply();
        }
    }

    private void saveWordProgress() {
        if (wProg == null || curBook == null) return;
        try {
            java.io.FileOutputStream fo = new java.io.FileOutputStream(bookProg(curBook.id));
            fo.write(wProg);
            fo.flush();
            fo.close();
        } catch (Exception ignored) { }
    }

    /** 读第 i 个词的完整 JSON（按偏移 seek 一行）。 */
    private JSONObject readWordLine(int i) {
        try {
            RandomAccessFile rf = new RandomAccessFile(bookJson(curBook.id), "r");
            try {
                byte[] b = new byte[wLen.get(i)];
                rf.seek(wOff.get(i));
                rf.readFully(b);
                String s = new String(b, "UTF-8").trim();
                return new JSONObject(s);
            } finally {
                rf.close();
            }
        } catch (Exception e) {
            return null;
        }
    }

    // ---- SM-2 ----
    private int progByte(int i, int k) { return wProg[i * 8 + k] & 0xFF; }
    private void setProgByte(int i, int k, int v) { wProg[i * 8 + k] = (byte) v; }
    private int progShort(int i, int k) {
        return ((wProg[i * 8 + k] & 0xFF) << 8) | (wProg[i * 8 + k + 1] & 0xFF);
    }
    private void setProgShort(int i, int k, int v) {
        wProg[i * 8 + k] = (byte) ((v >> 8) & 0xFF);
        wProg[i * 8 + k + 1] = (byte) (v & 0xFF);
    }
    private int todayDay() {
        return (int) (System.currentTimeMillis() / 86400000L);
    }


    // ================= 单词：设置页（词书管理） =================

    // ================= 单词：生词本 =================

    private void buildWordVocabScreen() {
        wordVocabScroll = new OverScrollScrollView(this);
        wordVocabScroll.setVerticalScrollBarEnabled(false);
        roundPadScroll(wordVocabScroll);
        wordVocabInner = new LinearLayout(this);
        wordVocabInner.setOrientation(LinearLayout.VERTICAL);
        wordVocabScroll.addView(wordVocabInner);
        wordVocabScreen.addView(wordVocabScroll, lw());
    }

    /** 忘过 2 次以上的词。清一色从进度第 6 字节读，不另存列表。 */
    private ArrayList<Integer> vocabIndexes() {
        ArrayList<Integer> list = new ArrayList<Integer>();
        if (wWords == null) return list;
        for (int i = 0; i < wWords.size(); i++) if (progByte(i, 6) >= 2) list.add(i);
        return list;
    }

    private void renderWordVocab() {
        wordVocabInner.removeAllViews();
        wordVocabInner.addView(makeBack("‹ 返回", new View.OnClickListener() {
            public void onClick(View v) { goBack(backTarget()); }
        }), wrapM());
        wordVocabInner.addView(pageTitle("生词本"), wrap());

        if (!ensureWordIndex()) {
            wordVocabInner.addView(grayTip("词书还没准备好，先去设置里下载一本。"), wrap());
            return;
        }
        final ArrayList<Integer> list = vocabIndexes();
        if (list.isEmpty()) {
            wordVocabInner.addView(grayTip("还没有生词。背词时连着两次点「不认识」，那个词就会收到这里。"), wrap());
            padInner(wordVocabInner);
            return;
        }

        wordVocabInner.addView(grayTip("忘过两次以上的词会收到这里，刷会了自动移出。共 "
                + list.size() + " 个。"), wrap());

        TextView brush = wordBtn("刷这些词（" + list.size() + " 个）", true);
        brush.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                vibrate();
                openWordStudy(2);
            }
        });
        wordVocabInner.addView(brush, wrapGap(8));

        for (int i = 0; i < list.size(); i++) {
            wordVocabInner.addView(vocabRow(list.get(i)), wrapGap(4));
        }
        padInner(wordVocabInner);
    }

    /** 生词一行：左边词 + 第一条释义，右边忘了几次。点开看完整词条。 */
    private View vocabRow(final int idx) {
        JSONObject o = readWordLine(idx);
        JSONObject w = (o == null) ? null : o.optJSONObject("content");
        if (w != null) w = w.optJSONObject("word");
        JSONObject cc = (w == null) ? null : w.optJSONObject("content");
        String head = (w == null) ? ("#" + idx) : w.optString("wordHead", "#" + idx);

        String mean = "";
        JSONArray tr = (cc == null) ? null : cc.optJSONArray("trans");
        if (tr != null && tr.length() > 0) {
            JSONObject t = tr.optJSONObject(0);
            if (t != null) mean = (t.optString("pos", "") + " " + t.optString("tranCn", "")).trim();
        }

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(9), dp(12), dp(9));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(C_PILL);
        bg.setCornerRadius(dp(12));
        row.setBackgroundDrawable(bg);

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);

        TextView t1 = new TextView(this);
        t1.setText(head);
        t1.setTextSize(m.backSize - 1f);
        t1.setTypeface(Typeface.DEFAULT_BOLD);
        t1.setTextColor(C_TEXT);
        left.addView(t1);

        if (mean.length() > 0) {
            TextView t2 = new TextView(this);
            t2.setText(mean);
            t2.setTextSize(m.subtitleSize - 2f);
            t2.setTextColor(C_SUB);
            t2.setSingleLine(true);
            t2.setEllipsize(TextUtils.TruncateAt.END);
            left.addView(t2);
        }
        row.addView(left, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView cnt = new TextView(this);
        cnt.setText("忘 " + progByte(idx, 6) + " 次");
        cnt.setTextSize(m.subtitleSize - 2.5f);
        cnt.setTextColor(0xFFFF8A80);
        row.addView(cnt);

        row.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                vibrate();
                showWordDetail(idx);
            }
        });
        return row;
    }

    private void buildWordSettingsScreen() {
        wordSettingScroll = new OverScrollScrollView(this);
        wordSettingScroll.setVerticalScrollBarEnabled(false);
        roundPadScroll(wordSettingScroll);
        wordSettingInner = new LinearLayout(this);
        wordSettingInner.setOrientation(LinearLayout.VERTICAL);
        wordSettingScroll.addView(wordSettingInner);
        wordSettingsScreen.addView(wordSettingScroll, lw());
    }

    /** 词书列表 + 每日新词设置。下载进度回调也会调它重画。 */
    /** 组内分隔线：1px，左右留出圆角的内边距。 */
    private View rowDivider() {
        View v = new View(this);
        v.setBackgroundColor(0x1FFFFFFF);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, dp(0.5f)));
        lp.leftMargin = dp(12);
        lp.rightMargin = dp(12);
        v.setLayoutParams(lp);
        return v;
    }

    /** 组内开关行：外层已经有圆角底了，这里不画行背景，其余与主设置页一致。 */
    private View toggleRowFlat(String label, final String key, final boolean def, final Runnable after) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10), dp(8), dp(10), dp(8));

        TextView left = new TextView(this);
        left.setText(label);
        left.setTextSize(m.backSize);
        left.setTextColor(C_TEXT);
        row.addView(left, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        final TextView right = new TextView(this);
        final boolean on = prefs.getBoolean(key, def);
        right.setText(on ? "开" : "关");
        right.setTextSize(m.backSize);
        right.setTypeface(Typeface.DEFAULT_BOLD);
        right.setTextColor(on ? C_PRIMARY : C_SUB);
        right.setGravity(Gravity.CENTER);
        right.setPadding(dp(14), dp(3), dp(14), dp(3));
        right.setBackgroundDrawable(pill(on ? 0xFF14384A : C_PILL));
        row.addView(right);

        row.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                vibrate();
                boolean now = !prefs.getBoolean(key, def);
                prefs.edit().putBoolean(key, now).apply();
                right.setText(now ? "开" : "关");
                right.setTextColor(now ? C_PRIMARY : C_SUB);
                right.setBackgroundDrawable(pill(now ? 0xFF14384A : C_PILL));
                if (after != null) after.run();
            }
        });
        return row;
    }

    /** 发音口音：右侧两个胶囊「美音 / 英音」，选中的高亮，直接点选，不用轮着切换。 */
    private View accentRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10), dp(8), dp(10), dp(8));

        TextView left = new TextView(this);
        left.setText("发音口音");
        left.setTextSize(m.backSize);
        left.setTextColor(C_TEXT);
        row.addView(left, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout seg = new LinearLayout(this);
        seg.setOrientation(LinearLayout.HORIZONTAL);
        final String[] codes = {"us", "uk"};
        final String[] labels = {"美音", "英音"};
        for (int i = 0; i < codes.length; i++) {
            final String code = codes[i];
            boolean on = code.equals(accent());
            TextView c = new TextView(this);
            c.setText(labels[i]);
            c.setTextSize(m.backSize);
            c.setTypeface(on ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            c.setTextColor(on ? C_PRIMARY : C_SUB);
            c.setGravity(Gravity.CENTER);
            c.setPadding(dp(10), dp(3), dp(10), dp(3));
            c.setBackgroundDrawable(pill(on ? 0xFF14384A : C_PILL));
            c.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    vibrate();
                    if (code.equals(accent())) return;      // 点的就是当前这项
                    prefs.edit().putString("word_accent", code).apply();
                    renderWordSettings();
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            if (i > 0) lp.leftMargin = dp(4);
            seg.addView(c, lp);
        }
        row.addView(seg);
        return row;
    }

    /** 组内的左标签 + 右值行（右值也是胶囊，和开关的「开/关」对齐）。 */
    private View valueRowFlat(String label, String value, View.OnClickListener click) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10), dp(8), dp(10), dp(8));

        TextView left = new TextView(this);
        left.setText(label);
        left.setTextSize(m.backSize);
        left.setTextColor(C_TEXT);
        row.addView(left, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView right = new TextView(this);
        right.setText(value);
        right.setTextSize(m.backSize);
        right.setTypeface(Typeface.DEFAULT_BOLD);
        right.setTextColor(C_PRIMARY);
        right.setGravity(Gravity.CENTER);
        right.setPadding(dp(14), dp(3), dp(14), dp(3));
        right.setBackgroundDrawable(pill(C_PILL));
        row.addView(right);

        if (click != null) row.setOnClickListener(click);
        return row;
    }

    /** 左标签 + 右值（点一下切换）。和开关行同一套排版。 */
    private View valueRow(String label, String value, View.OnClickListener click) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10), dp(8), dp(10), dp(8));
        row.setBackgroundDrawable(pill(C_CARD));

        TextView left = new TextView(this);
        left.setText(label);
        left.setTextSize(m.backSize);
        left.setTextColor(C_TEXT);
        row.addView(left, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView right = new TextView(this);
        right.setText(value);
        right.setTextSize(m.backSize);
        right.setTypeface(Typeface.DEFAULT_BOLD);
        right.setTextColor(C_PRIMARY);
        right.setGravity(Gravity.CENTER);
        right.setPadding(dp(14), dp(3), dp(14), dp(3));
        right.setBackgroundDrawable(pill(C_PILL));
        row.addView(right);

        if (click != null) row.setOnClickListener(click);
        return row;
    }

    /** 设置页开关行：左标签右「开/关」，点了就地切，不重画整页。 */
    private View toggleRow(String label, final String key, final boolean def) {
        return toggleRow(label, key, def, null);
    }

    /** after 在切换完成后执行（用于互斥联动、重画等）。
     *  样式与主设置页的开关完全一致：卡片底 + 右侧胶囊标签 + backSize 字号。 */
    private View toggleRow(String label, final String key, final boolean def, final Runnable after) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10), dp(8), dp(10), dp(8));
        row.setBackgroundDrawable(pill(C_CARD));

        TextView left = new TextView(this);
        left.setText(label);
        left.setTextSize(m.backSize);
        left.setTextColor(C_TEXT);
        row.addView(left, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        final TextView right = new TextView(this);
        final boolean on = prefs.getBoolean(key, def);
        right.setText(on ? "开" : "关");
        right.setTextSize(m.backSize);
        right.setTypeface(Typeface.DEFAULT_BOLD);
        right.setTextColor(on ? C_PRIMARY : C_SUB);
        right.setGravity(Gravity.CENTER);
        right.setPadding(dp(14), dp(3), dp(14), dp(3));
        right.setBackgroundDrawable(pill(on ? 0xFF14384A : C_PILL));
        row.addView(right);

        row.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                vibrate();
                boolean now = !prefs.getBoolean(key, def);
                prefs.edit().putBoolean(key, now).apply();
                right.setText(now ? "开" : "关");
                right.setTextColor(now ? C_PRIMARY : C_SUB);
                right.setBackgroundDrawable(pill(now ? 0xFF14384A : C_PILL));
                if (after != null) after.run();
            }
        });
        return row;
    }

    private void renderWordSettings() {
        dlActView = null;         // 整页重画了，旧引用作废；wordBookRow 里会重新挂上
        loadWordBooks();
        wordSettingInner.removeAllViews();
        wordSettingInner.addView(makeBack("‹ 返回", new View.OnClickListener() {
            public void onClick(View v) { goBack(wordSettingBack); }
        }), wrapM());
        wordSettingInner.addView(pageTitle("背单词设置"), wrap());

        // 我的词书：已下载的都在这里，包括正在用的那本（也能删）
        wordSettingInner.addView(sectionHeader("我的词书"), wrap());
        int readyN = 0;
        for (WordBook b : wordBooks) {
            if (!bookDownloaded(b)) continue;
            wordSettingInner.addView(wordBookRow(b), wrapGap(4));
            readyN++;
        }
        if (readyN == 0) {
            wordSettingInner.addView(grayTip("还没有下载词书。点下面的「下载词书」挑一本开始。"), wrap());
        }

        // 下载词书：单独一个按钮，点开才列可下载的，不占版面
        TextView dlBtn = wordBtn(wordShowDownload ? "收起可下载词书" : "下载词书", true);
        dlBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                vibrate();
                wordShowDownload = !wordShowDownload;
                renderWordSettings();
            }
        });
        wordSettingInner.addView(dlBtn, wrapGap(4));

        if (wordShowDownload) {
            int free = 0;
            for (WordBook b : wordBooks) {
                if (bookDownloaded(b)) continue;
                wordSettingInner.addView(wordBookRow(b), wrapGap(4));
                free++;
            }
            if (free == 0) {
                wordSettingInner.addView(grayTip("全部词书都已经下载了。"), wrap());
            }
        }
        wordSettingInner.addView(grayTip("词库来自公开词表，仅供个人学习使用。"), wrap());

        // 学习辅助：发音与助记图
        wordSettingInner.addView(sectionHeader("学习辅助"), wrap());

        // 自动发音 / 静音模式 / 发音口音 是一组：共用一个圆角底、中间插分隔线，
        // 而不是三张各自独立的胶囊（那样看着像三件不相干的事）。
        LinearLayout voiceGroup = new LinearLayout(this);
        voiceGroup.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable vbg = new GradientDrawable();
        vbg.setColor(C_CARD);
        vbg.setCornerRadius(dp(12));
        voiceGroup.setBackgroundDrawable(vbg);

        voiceGroup.addView(toggleRowFlat("自动发音", "word_auto_speak", false, new Runnable() {
            public void run() {
                // 和静音互斥：开了自动发音就把静音关掉
                if (prefs.getBoolean("word_auto_speak", false)) {
                    prefs.edit().putBoolean("word_mute", false).apply();
                }
                renderWordSettings();
            }
        }));
        voiceGroup.addView(rowDivider());
        voiceGroup.addView(toggleRowFlat("静音模式", "word_mute", false, new Runnable() {
            public void run() {
                if (prefs.getBoolean("word_mute", false)) {
                    prefs.edit().putBoolean("word_auto_speak", false).apply();
                    stopVoice();          // 正在念的立刻停
                }
                renderWordSettings();
            }
        }));
        voiceGroup.addView(rowDivider());
        voiceGroup.addView(accentRow());
        wordSettingInner.addView(voiceGroup, wrapGap(5));
        wordSettingInner.addView(toggleRow("显示助记图", "word_show_pic", true), wrapGap(5));
        wordSettingInner.addView(toggleRow("复习出选择题", "word_quiz", true), wrapGap(5));
        wordSettingInner.addView(grayTip("助记图来自词库自带的图片索引，约 1160 个常用词有；"
                + "换哪本词书都能用，命中就显示。"), wrap());

        wordSettingInner.addView(sectionHeader("学习节奏"), wrap());
        wordSettingInner.addView(valueRow("每天新词", wordDailyNew + " 个",
                new View.OnClickListener() {
                    public void onClick(View v) {
                        vibrate();
                        askDailyNew();
                    }
                }), wrapGap(5));

        wordSettingInner.addView(grayTip("点上面那一行改数量（1～200）。到期复习优先，新词按这个数量追加；"
                + "一天没背不会惩罚，到期词顺延。"), wrap());
        padInner(wordSettingInner);
    }

    private int wordSettingBack = S_WORD_HOME;
    private int wordHomeBack = S_SUBJECT;      // 单词主页的上一级（英语科目页或主页）
    private int wordSearchBack = S_WORD_HOME;  // 搜单词的上一级

    /** 改每天新词数。复用 apiInputDialog：它已经处理了残影（窗口透明 + 滚动重绘）和数字键盘。 */
    private void askDailyNew() {
        apiInputDialog("每天背多少新词", "1～200", String.valueOf(wordDailyNew), false, true, false,
                new ApiTextSink() {
                    public void onText(String v) {
                        String t = (v == null ? "" : v.trim());
                        int n;
                        try {
                            n = Integer.parseInt(t);
                        } catch (Exception e) {
                            toast("请填数字");
                            return;
                        }
                        if (n < 1 || n > 200) { toast("请填 1～200 之间"); return; }
                        wordDailyNew = n;
                        prefs.edit().putInt("word_daily_new", n).apply();
                        renderWordSettings();
                    }
                });
    }

    /** 单词页的主按钮：字号、内边距、圆角统一，只靠底色和加粗区分主次。 */
    private TextView wordBtn(String text, boolean primary) {
        TextView b = new TextView(this);
        b.setText(text);
        b.setTextSize(m.subtitleSize);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(10), dp(11), dp(10), dp(11));
        b.setTextColor(primary ? C_PRIMARY : 0xFFE8E8E8);   // 次要按钮也用白字，灰字在手表上太虚
        b.setTypeface(primary ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        b.setBackground(primary ? pill(0xFF14384A) : clickBg(C_PILL));
        return b;
    }

    /** 列表右侧的小按钮：字号、内边距统一，只有主/次两种配色（之前大小不一就是这里各写各的）。 */
    private TextView wordSmallBtn(String text, boolean primary) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(m.subtitleSize - 1f);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(10), dp(7), dp(10), dp(7));
        t.setTextColor(primary ? C_PRIMARY : 0xFFE8E8E8);
        t.setBackground(primary ? pill(0xFF14384A) : clickBg(C_PILL));
        return t;
    }

    /** 一本词书一行：左边名称与规模，右边动作（下载 / 使用中 / 选用 / 删）。
     *  已下载的都能删，包括正在用的那本——删掉后当前词书清空，主页会提示重新选。 */
    private View wordBookRow(final WordBook b) {
        boolean has = bookDownloaded(b);
        final boolean isCur = (curBook != null && curBook.id.equals(b.id));
        final boolean dl = (downloadingBook != null && downloadingBook.id.equals(b.id));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(9), dp(8), dp(9));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(isCur ? 0xFF12222A : C_PILL);
        bg.setCornerRadius(dp(12));
        row.setBackgroundDrawable(bg);

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);

        TextView t1 = new TextView(this);
        t1.setText(b.name);
        t1.setTextSize(m.backSize - 1f);
        t1.setTextColor(isCur ? C_PRIMARY : C_TEXT);
        t1.setTypeface(isCur ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        left.addView(t1);

        TextView t2 = new TextView(this);
        String state;
        if (dl) state = dlLabel();
        else if (isCur) state = "使用中 · " + b.words + " 词";
        else if (has) state = "已下载 · " + b.words + " 词";
        else state = b.words + " 词 · " + String.format("%.1f", b.size / 1048576.0) + " MB";
        t2.setText(state);
        t2.setTextSize(m.subtitleSize - 2f);
        t2.setTextColor(dl ? C_PRIMARY : C_SUB);
        left.addView(t2);

        row.addView(left, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        final TextView act = wordSmallBtn(dl ? dlLabel() : (isCur ? "使用中" : (has ? "选用" : "下载")),
                !dl && (isCur || !has));
        if (dl) {
            act.setTextColor(C_SUB);
            dlActView = act;      // 进度刷新只改这一个按钮，见 showDlProgress
        }
        row.addView(act);

        if (has && !dl) {
            // 圆的三点按钮：删除、清空记录、换用都收进一个弹窗，行上只留一个入口
            TextView more = new TextView(this);
            more.setPadding(dp(9), dp(9), dp(9), dp(9));
            GradientDrawable circle = new GradientDrawable();
            circle.setShape(GradientDrawable.OVAL);
            circle.setColor(0xFF2E2E2E);
            more.setBackgroundDrawable(circle);
            applyIconOnly(more, "ic_more", 0xFFCFCFCF, 16);
            more.setContentDescription("更多操作");
            LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            mlp.leftMargin = dp(6);
            row.addView(more, mlp);
            more.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    vibrate();
                    showBookActions(b);
                }
            });
        }

        View.OnClickListener pick = new View.OnClickListener() {
            public void onClick(View v) {
                vibrate();
                if (dl) return;
                if (!bookDownloaded(b)) {
                    downloadWordBook(b);
                } else if (!isCur) {
                    curBook = b;
                    prefs.edit().putString("word_book", b.id).apply();
                    wordIdxReady = false;
                    toast("已切换到「" + b.name + "」");
                    renderWordSettings();
                }
            }
        };
        row.setOnClickListener(pick);
        act.setOnClickListener(pick);
        return row;
    }

    /** 词书的更多操作：选它 / 清空背诵记录 / 删除。样式与「选单词书」完全一套。 */
    private void showBookActions(final WordBook b) {
        final boolean isCur = (curBook != null && curBook.id.equals(b.id));

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(12), dp(10), dp(12), dp(8));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF151515);
        bg.setCornerRadius(dp(16));
        box.setBackgroundDrawable(bg);

        final AlertDialog d = new AlertDialog.Builder(this).setView(box).create();

        TextView t = new TextView(this);
        t.setText(b.name);
        t.setTextSize(m.subtitleSize);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(Color.WHITE);
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        box.addView(t, wrap());

        TextView st = new TextView(this);
        st.setText(isCur ? ("使用中 · " + b.words + " 词") : ("已下载 · " + b.words + " 词"));
        st.setTextSize(m.subtitleSize - 2.5f);
        st.setTextColor(C_SUB);
        st.setGravity(Gravity.CENTER);
        st.setPadding(0, 0, 0, dp(6));
        box.addView(st, wrap());

        if (!isCur) {
            box.addView(sheetRow("选择这本", 0xFFE8E8E8, new Runnable() {
                public void run() {
                    curBook = b;
                    prefs.edit().putString("word_book", b.id).apply();
                    wordIdxReady = false;
                    toast("已切换到「" + b.name + "」");
                    renderWordSettings();
                }
            }, d), wrapGap(4));
        }

        box.addView(sheetRow("清空背诵记录", 0xFFE8E8E8, new Runnable() {
            public void run() {
                confirmAction("清空「" + b.name + "」的背诵记录？",
                        "这本词书的进度会全部归零，词书文件保留，可以重新开始背。",
                        "清空", 0xFFFFB74D, new Runnable() {
                            public void run() {
                                clearBookProgress(b);
                            }
                        });
            }
        }, d), wrapGap(4));

        box.addView(sheetRow("删除单词书", 0xFFFF8A80, new Runnable() {
            public void run() {
                confirmAction("删除「" + b.name + "」？",
                        "只删本机文件，重新下载即可恢复，这本的背诵记录也会一起清掉。",
                        "删除", 0xFFFF8A80, new Runnable() {
                            public void run() {
                                bookJson(b.id).delete();
                                bookIdx(b.id).delete();
                                bookProg(b.id).delete();
                                if (curBook != null && curBook.id.equals(b.id)) {
                                    curBook = null;
                                    prefs.edit().remove("word_book").apply();
                                    wProg = null;
                                    wordIdxReady = false;
                                }
                                if (screen == S_WORD_STUDY || screen == S_WORD_VOCAB) {
                                    goBack(S_WORD_HOME);
                                }
                                toast("已删除「" + b.name + "」");
                                renderWordSettings();
                            }
                        });
            }
        }, d), wrapGap(4));

        TextView no = new TextView(this);
        no.setText("取消");
        no.setTextSize(m.subtitleSize - 1f);
        no.setGravity(Gravity.CENTER);
        no.setTextColor(C_SUB);
        no.setPadding(dp(8), dp(9), dp(8), dp(4));
        no.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                vibrate();
                try { d.dismiss(); } catch (Throwable ignored) { }
            }
        });
        box.addView(no, wrap());

        tuneDialogWindow(d);   // 必须 show() 之前：否则窗口按不透明格式建，滚动会留残影
        d.show();
        sizeDialog(d, 0.9f);
    }

    /** 清空一本词书的进度：删文件 + 内存里立刻按空进度重建，避免还停在学习页时空指针。 */
    private void clearBookProgress(WordBook b) {
        bookProg(b.id).delete();
        if (curBook != null && curBook.id.equals(b.id)) {
            wProg = null;
            wordIdxReady = false;
            // 今日新词额度也一起归零，否则清空后今天一个都背不了
            prefs.edit().remove("word_new_day").remove("word_new_cnt").apply();
            if (screen == S_WORD_STUDY) goBack(S_WORD_HOME);
            ensureWordIndex();       // 重新读索引 + 建全 0 的进度
        }
        toast("已清空「" + b.name + "」的背诵记录");
        renderWordSettings();
    }

    /** 弹窗里的一行动作：点了先关弹窗，再执行。 */
    private View sheetRow(String text, int color, final Runnable act, final AlertDialog dlg) {
        TextView row = new TextView(this);
        row.setText(text);
        row.setTextSize(m.subtitleSize);
        row.setTextColor(color);
        row.setGravity(Gravity.CENTER);
        row.setPadding(dp(8), dp(10), dp(8), dp(10));
        row.setBackgroundDrawable(clickBg(C_PILL));
        row.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                vibrate();
                try { dlg.dismiss(); } catch (Throwable ignored) { }
                if (act != null) act.run();
            }
        });
        return row;
    }

    private void buildWordHomeScreen() {
        wordHomeScroll = new OverScrollScrollView(this);
        wordHomeScroll.setVerticalScrollBarEnabled(false);
        roundPadScroll(wordHomeScroll);
        wordHomeInner = new LinearLayout(this);
        wordHomeInner.setOrientation(LinearLayout.VERTICAL);
        wordHomeScroll.addView(wordHomeInner);
        wordHomeScreen.addView(wordHomeScroll, lw());
    }

    private void renderWordHome() {
        loadWordBooks();
        wordHomeInner.removeAllViews();
        wordHomeInner.addView(makeBack("‹ 返回", new View.OnClickListener() {
            public void onClick(View v) { wordGoBack(); }
        }), wrapM());
        wordHomeInner.addView(pageTitle("背单词"), wrap());

        boolean hasBook = (curBook != null && bookDownloaded(curBook));
        // 首页那几个数字都要读索引和进度，没加载就全是 0
        boolean ready = hasBook && ensureWordIndex();
        // 进度卡
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        GradientDrawable cbg = new GradientDrawable();
        cbg.setColor(C_PILL);
        cbg.setCornerRadius(dp(14));
        card.setBackgroundDrawable(cbg);
        TextView big = new TextView(this);
        big.setText(hasBook ? curBook.name : "还没选单词书");
        big.setTextSize(m.backSize);
        big.setTypeface(Typeface.DEFAULT_BOLD);
        big.setTextColor(hasBook ? C_PRIMARY : C_SUB);
        card.addView(big);
        final int[] st = ready ? wordStats() : new int[]{0, 0, 0};
        TextView sub = new TextView(this);
        if (ready) {
            sub.setText("已掌握 " + st[0] + " · 学习中 " + st[1] + " · 未学 " + st[2]);
        } else {
            sub.setText("先从下面选一本，再开始背");
        }
        sub.setTextSize(m.subtitleSize - 2f);
        sub.setTextColor(C_SUB);
        sub.setPadding(0, dp(4), 0, 0);
        card.addView(sub);

        if (ready) {
            // 今日任务：把「还要背多少」摆在最显眼的位置，别让人点进去才知道
            int dueNow = countDue();
            int doneToday = wordNewToday();
            int quotaLeft = Math.max(0, wordDailyNew - doneToday);
            int freshToday = Math.min(quotaLeft, countFresh());
            TextView tl = new TextView(this);
            tl.setText("今日　新词 " + doneToday + "/" + wordDailyNew
                    + (freshToday > 0 ? "（还剩 " + freshToday + "）" : "（今日已背完）")
                    + " · 复习 " + dueNow + " 个");
            tl.setTextSize(m.subtitleSize - 1f);
            tl.setTextColor(dueNow > 0 ? C_PRIMARY : C_TEXT);
            tl.setPadding(0, dp(6), 0, 0);
            card.addView(tl);

            // 已学进度条：不写百分比文字，条本身就能看出大概
            int learned = wWords.size() - st[2];
            float frac = wWords.size() > 0 ? (float) learned / wWords.size() : 0f;
            if (frac < 0.002f) frac = 0.002f;
            if (frac > 0.998f) frac = 0.998f;
            LinearLayout bar = new LinearLayout(this);
            bar.setOrientation(LinearLayout.HORIZONTAL);
            GradientDrawable bgbg = new GradientDrawable();
            bgbg.setColor(0xFF262626);
            bgbg.setCornerRadius(dp(3));
            bar.setBackgroundDrawable(bgbg);
            View fill = new View(this);
            GradientDrawable fg = new GradientDrawable();
            fg.setColor(C_PRIMARY);
            fg.setCornerRadius(dp(3));
            fill.setBackgroundDrawable(fg);
            bar.addView(fill, new LinearLayout.LayoutParams(0, dp(6), frac));
            bar.addView(new View(this), new LinearLayout.LayoutParams(0, dp(6), 1f - frac));
            LinearLayout.LayoutParams blp = wrap();
            blp.topMargin = dp(8);
            card.addView(bar, blp);

            if (st[0] == 0 && st[1] > 0) {
                // 刚背的词间隔只有 1 天，全算「学习中」，这里说清口径，免得以为坏了
                TextView note = new TextView(this);
                note.setText("连着记住 4 次才算「已掌握」，刚背的都记在学习中");
                note.setTextSize(m.subtitleSize - 2.5f);
                note.setTextColor(0xFF6A6A6A);
                note.setPadding(0, dp(6), 0, 0);
                card.addView(note);
            }
        }
        wordHomeInner.addView(card, wrapGap(8));

        // 打卡：连续天数 + 最近 30 天的格子（一行 10 个，三行）
        LinearLayout ciCard = new LinearLayout(this);
        ciCard.setOrientation(LinearLayout.VERTICAL);
        ciCard.setPadding(dp(14), dp(10), dp(14), dp(10));
        GradientDrawable cibg = new GradientDrawable();
        cibg.setColor(C_PILL);
        cibg.setCornerRadius(dp(14));
        ciCard.setBackgroundDrawable(cibg);

        final int streak = checkinStreak();
        TextView ciTitle = new TextView(this);
        ciTitle.setText(streak > 0 ? ("连续打卡 " + streak + " 天") : "还没打卡 · 背完一次记一天");
        ciTitle.setTextSize(m.subtitleSize);
        ciTitle.setTextColor(streak > 0 ? C_PRIMARY : C_SUB);
        ciTitle.setTypeface(Typeface.DEFAULT_BOLD);
        ciCard.addView(ciTitle);

        // 格子只表示「打过多少天」：每打一天多亮一格，从左上角往右、再往下排
        int lit = Math.min(30, checkinHistory().size());
        for (int row = 0; row < 3; row++) {
            LinearLayout line = new LinearLayout(this);
            line.setOrientation(LinearLayout.HORIZONTAL);
            line.setGravity(Gravity.CENTER);
            for (int col = 0; col < 10; col++) {
                int n = row * 10 + col;
                View cell = new View(this);
                GradientDrawable cd = new GradientDrawable();
                cd.setCornerRadius(dp(2));
                cd.setColor(n < lit ? C_PRIMARY : 0xFF2A2A2A);
                cell.setBackgroundDrawable(cd);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(10), dp(10));
                lp.setMargins(dp(2), dp(2), dp(2), dp(2));
                line.addView(cell, lp);
            }
            ciCard.addView(line);
        }
        wordHomeInner.addView(ciCard, wrapGap(8));

        // 开始背单词（没选词书时禁用态）
        // 今日新词配额用完、又没有到期复习 → 按钮转灰，想继续得长按
        final boolean allDone = ready && wordNewToday() >= wordDailyNew && countDue() == 0;
        TextView start = wordBtn("开始背单词", true);
        if (!ready || allDone) {
            start.setTextColor(0xFF4A4A4A);
            start.setTypeface(Typeface.DEFAULT);
            start.setBackground(pill(0xFF141414));
        }
        start.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                vibrate();
                if (curBook == null || !bookDownloaded(curBook)) {
                    toast("先选一本单词书");
                    return;
                }
                if (wordNewToday() >= wordDailyNew && countDue() == 0) {
                    toast("如果想要继续背单词，请长按该按钮。");
                    return;
                }
                openWordStudy();
            }
        });
        start.setOnLongClickListener(new View.OnLongClickListener() {
            public boolean onLongClick(View v) {
                if (curBook == null || !bookDownloaded(curBook)) return false;
                vibrate();
                toast("继续背 " + wordDailyNew + " 个新词");
                openWordStudy(3);       // 放开今日配额
                return true;
            }
        });
        wordHomeInner.addView(start, wrapGap(6));

        // 只复习：独立入口，只做到期的词，不掺新词
        final int dueNow = ready ? countDue() : 0;
        TextView review = wordBtn(dueNow > 0 ? "只复习 " + dueNow + " 个" : "今天没有要复习的", false);
        if (dueNow == 0) {
            review.setTextColor(0xFF4A4A4A);
            review.setBackground(pill(0xFF141414));
        }
        review.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                vibrate();
                if (curBook == null || !bookDownloaded(curBook)) { toast("先选一本单词书"); return; }
                if (countDue() == 0) { toast("今天没有到期的词"); return; }
                openWordStudy(1);
            }
        });
        wordHomeInner.addView(review, wrapGap(6));

        // 生词本：忘过两次以上的词
        final int vocabN = ready ? vocabIndexes().size() : 0;
        TextView vocab = wordBtn(vocabN > 0 ? "生词本 " + vocabN + " 个" : "生词本（空）", false);
        if (vocabN == 0) {
            vocab.setTextColor(0xFF4A4A4A);
            vocab.setBackground(pill(0xFF141414));
        }
        vocab.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                vibrate();
                if (curBook == null || !bookDownloaded(curBook)) { toast("先选一本单词书"); return; }
                showScreen(S_WORD_VOCAB);
            }
        });
        wordHomeInner.addView(vocab, wrapGap(6));

        // 选择 / 更换单词书
        TextView pick = wordBtn(ready ? "更换单词书" : "选择单词书", false);
        pick.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                vibrate();
                showBookPicker();
            }
        });
        wordHomeInner.addView(pick, wrapGap(6));

        // 设置（与设置页里那个入口同一个界面）
        TextView cfg = wordBtn("背单词设置", false);
        cfg.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                vibrate();
                wordSettingBack = S_WORD_HOME;
                showScreen(S_WORD_SETTINGS);
            }
        });
        wordHomeInner.addView(cfg, wrapGap(6));

        // 搜索单词
        TextView find = wordBtn("搜索单词", false);
        find.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                vibrate();
                showScreen(S_WORD_SEARCH);
            }
        });
        wordHomeInner.addView(find, wrapGap(6));
        if (!online()) {
            wordHomeInner.addView(grayTip("离线模式：无法查看助记图片和收听单词发音"), wrap());
        }
        padInner(wordHomeInner);
    }

    /** 主页返回：从英语科目点进来的回节点页，否则回主页。 */
    private void wordGoBack() {
        String key = curSubject == null ? "" : curSubject.key;
        if ("english".equals(key)) goBack(S_NODE);
        else goBack(S_SUBJECT);
    }

    private boolean wordShowDownload = false;   // 设置页是否展开「可下载」

    /** 选单词书：只列已经下载的，样式与「选模型」完全一致。
     *  一本都没下载就直接跳到背单词设置的下载区，免得弹一个空列表。 */
    private void showBookPicker() {
        loadWordBooks();
        final List<WordBook> ready = new ArrayList<WordBook>();
        for (WordBook b : wordBooks) if (bookDownloaded(b)) ready.add(b);
        if (ready.isEmpty()) {
            toast("还没有下载词书，先下载一本");
            wordSettingBack = S_WORD_HOME;
            wordShowDownload = true;
            showScreen(S_WORD_SETTINGS);
            return;
        }

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(12), dp(10), dp(12), dp(8));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF151515);
        bg.setCornerRadius(dp(16));
        box.setBackgroundDrawable(bg);

        final AlertDialog d = new AlertDialog.Builder(this).setView(box).create();

        TextView t = new TextView(this);
        t.setText("选单词书（" + ready.size() + " 本）");
        t.setTextSize(m.subtitleSize);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(Color.WHITE);
        t.setGravity(Gravity.CENTER);
        box.addView(t, wrap());

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < ready.size(); i++) {
            final WordBook b = ready.get(i);
            boolean on = (curBook != null && curBook.id.equals(b.id));
            TextView row = new TextView(this);
            row.setText(b.name + "　" + b.words + " 词");
            row.setTextSize(m.subtitleSize - 1f);
            row.setTypeface(on ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            row.setTextColor(on ? C_PRIMARY : C_TEXT);
            row.setGravity(Gravity.CENTER);
            row.setSingleLine(true);
            row.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            row.setPadding(dp(8), dp(9), dp(8), dp(9));
            row.setBackgroundDrawable(pill(C_PILL));
            row.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    vibrate();
                    try { d.dismiss(); } catch (Throwable ignored) { }
                    curBook = b;
                    prefs.edit().putString("word_book", b.id).apply();
                    wordIdxReady = false;
                    renderWordHome();
                }
            });
            LinearLayout.LayoutParams lp = wrap();
            lp.bottomMargin = dp(4);
            list.addView(row, lp);
        }

        ScrollView sv = new OverScrollScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.addView(list, wrap());
        tuneDialogScroll(sv, list);
        final int pickMaxH = m.round ? dp(150) : dp(240);
        final LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, pickMaxH);
        slp.topMargin = dp(4);
        box.addView(sv, slp);
        // 词书少的时候按内容收高（与选模型弹窗同一套处理，别留一大片空白）
        d.setOnShowListener(new DialogInterface.OnShowListener() {
            public void onShow(DialogInterface dlg) {
                list.post(new Runnable() {
                    public void run() {
                        int h = list.getHeight();
                        if (h > 0 && h < pickMaxH) {
                            slp.height = h + dp(2);
                            sv.setLayoutParams(slp);
                        }
                    }
                });
            }
        });

        TextView no = new TextView(this);
        no.setText("取消");
        no.setTextSize(m.subtitleSize - 1f);
        no.setGravity(Gravity.CENTER);
        no.setTextColor(C_SUB);
        no.setPadding(dp(8), dp(9), dp(8), dp(4));
        no.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                vibrate();
                try { d.dismiss(); } catch (Throwable ignored) { }
            }
        });
        box.addView(no, wrap());

        tuneDialogWindow(d);   // 必须 show() 之前：否则窗口按不透明格式建，滚动会留残影
        d.show();
        sizeDialog(d, 0.9f);
    }

    /** 已学词数（reps>0 或 interval>0 都算学过）。 */
    private int learnedCount() {
        if (!ensureWordIndex() || wProg == null) return 0;
        int n = wProg.length / 8, c = 0;
        for (int i = 0; i < n; i++) if (progByte(i, 5) > 0 || progShort(i, 1) > 0) c++;
        return c;
    }

    // ================= 单词：背词卡片 =================

    private int[] wQueue;          // 今日队列（词下标）
    private int wQueuePos;         // 当前做到第几个
    private int wCurIdx = -1;      // 当前词下标
    private boolean wFlipped;      // 是否已翻到释义面
    private LinearLayout wCardBox; // 卡片区（翻面只重画这块）

    private void buildWordStudyScreen() {
        wordStudyScroll = new OverScrollScrollView(this);
        wordStudyScroll.setVerticalScrollBarEnabled(false);
        roundPadScroll(wordStudyScroll);
        wordStudyInner = new LinearLayout(this);
        wordStudyInner.setOrientation(LinearLayout.VERTICAL);
        wordStudyScroll.addView(wordStudyInner);
        wordStudyScreen.addView(wordStudyScroll, lw());
    }

    private void openWordStudy() { openWordStudy(0); }

    /** mode：0 复习+新词 / 1 只复习 / 2 生词本。 */
    private void openWordStudy(int mode) {
        wMode = mode;
        startWordSession(mode);
    }

    private void startWordSession(int mode) {
        if (!ensureWordIndex()) { toast("词书还没准备好"); return; }
        buildWordQueue(mode);
        wQueuePos = 0;
        wCurIdx = -1;
        wFlipped = false;
        showScreen(S_WORD_STUDY);
        nextCard();
    }

    /** 组今日队列：到期复习在前，再补每日新词。 */
    private void buildWordQueue(int mode) {
        int n = wWords.size(), today = todayDay();
        ArrayList<Integer> due = new ArrayList<Integer>();
        ArrayList<Integer> fresh = new ArrayList<Integer>();

        if (mode == 2) {
            // 生词本：只取「忘过 2 次以上」的，不掺新词、不看到期日
            for (int i = 0; i < n; i++) if (progByte(i, 6) >= 2) due.add(i);
        } else {
            for (int i = 0; i < n; i++) {
                int interval = progShort(i, 1);
                int reps = progByte(i, 5);
                if (interval == 0 && reps == 0) fresh.add(i);
                else if (progShort(i, 3) <= today) due.add(i);
            }
        }
        // 每天的新词配额要扣掉今天已经学过的，否则一天能无限往下背；
        // mode 3 是「长按继续」，用户自己要求超额，就不再扣
        int quotaLeft = (mode == 3) ? wordDailyNew
                : Math.max(0, wordDailyNew - wordNewToday());
        int take = (mode == 0 || mode == 3) ? Math.min(quotaLeft, fresh.size()) : 0;
        wQueue = new int[due.size() + take];
        int k = 0;
        for (int i = 0; i < due.size(); i++) wQueue[k++] = due.get(i);
        for (int i = 0; i < take; i++) wQueue[k++] = fresh.get(i);

        // 重来池：上限就是队列长度（每个词最多进一次）
        wRetry = new int[Math.max(8, wQueue.length + 8)];
        wRetryN = 0;
        wRound = 1;
        wStatDue = due.size();
        wStatFresh = take;
        wStatNewDone = 0;
        wStatReviewDone = 0;
        wStatHard = 0;
        wCounted.clear();
        wMissed.clear();
    }

    /** 到期要复习的词数（首页用）。 */
    private int countDue() { return countDueAt(todayDay()); }

    /** 到指定那天为止要复习多少（完成页预告「明天」用它）。 */
    private int countDueAt(int day) {
        int n = wWords.size(), c = 0;
        for (int i = 0; i < n; i++) {
            if (progShort(i, 1) == 0 && progByte(i, 5) == 0) continue;   // 没学过
            if (progShort(i, 3) <= day) c++;
        }
        return c;
    }

    /** 还没学过的新词数。 */
    private int countFresh() {
        int n = wWords.size(), c = 0;
        for (int i = 0; i < n; i++) {
            if (progShort(i, 1) == 0 && progByte(i, 5) == 0) c++;
        }
        return c;
    }

    /** {已掌握, 学习中, 未学}。掌握 = 间隔满 7 天（1→2→4→7，也就是连着记住 4 次）。 */
    private int[] wordStats() {
        int n = wWords.size(), learned = 0, mastered = 0;
        for (int i = 0; i < n; i++) {
            int interval = progShort(i, 1);
            int reps = progByte(i, 5);
            if (interval == 0 && reps == 0) continue;
            learned++;
            if (interval >= 7) mastered++;      // 连续记住 4 次（1→2→4→7）算掌握
        }
        return new int[]{mastered, learned - mastered, n - learned};
    }

    // ================= 打卡 =================

    /** 完成一次完整会话就记一天；昨天打过卡就续，断了从 1 重新算。 */
    private void doCheckin() {
        int today = todayDay();
        int last = prefs.getInt("word_ci_last", 0);
        if (last == today) return;
        int streak = (last == today - 1) ? prefs.getInt("word_ci_streak", 0) + 1 : 1;
        ArrayList<String> days = checkinHistory();
        if (!days.contains(String.valueOf(today))) days.add(String.valueOf(today));
        while (days.size() > 30) days.remove(0);
        prefs.edit().putInt("word_ci_last", today)
                .putInt("word_ci_streak", streak)
                .putString("word_ci_hist", TextUtils.join(",", days))
                .apply();
    }

    private ArrayList<String> checkinHistory() {
        ArrayList<String> out = new ArrayList<String>();
        String raw = prefs.getString("word_ci_hist", "");
        if (raw.length() == 0) return out;
        for (String x : raw.split(",")) if (x.length() > 0) out.add(x);
        return out;
    }

    /** 当前连续天数：昨天或今天打过才算，断了显示 0。 */
    private int checkinStreak() {
        int last = prefs.getInt("word_ci_last", 0);
        int today = todayDay();
        if (last == today || last == today - 1) return prefs.getInt("word_ci_streak", 0);
        return 0;
    }

    /** 今天已经学了多少个新词。跨天自动归零。 */
    private int wordNewToday() {
        int day = prefs.getInt("word_new_day", 0);
        if (day != todayDay()) return 0;
        return prefs.getInt("word_new_cnt", 0);
    }

    private void bumpNewToday() {
        int day = todayDay();
        int n = (prefs.getInt("word_new_day", 0) == day) ? prefs.getInt("word_new_cnt", 0) : 0;
        prefs.edit().putInt("word_new_day", day).putInt("word_new_cnt", n + 1).apply();
    }

    private void addRetry(int idx) {
        if (wRetryN >= wRetry.length) {
            int[] t = new int[wRetry.length * 2];
            System.arraycopy(wRetry, 0, t, 0, wRetry.length);
            wRetry = t;
        }
        wRetry[wRetryN++] = idx;
    }

    /** 下一张卡。一轮走完还有「不认识」的，就再走一轮（最多 3 轮）。 */
    private void nextCard() {
        if (wQueue == null) {
            renderWordStudy(true);
            return;
        }
        if (wQueuePos >= wQueue.length) {
            if (wRetryN > 0 && wRound < 3) {
                wRound++;
                wQueue = java.util.Arrays.copyOf(wRetry, wRetryN);
                wRetryN = 0;
                wQueuePos = 0;
                toast("第 " + wRound + " 轮 · " + wQueue.length + " 个还不熟的");
            } else {
                renderWordStudy(true);
                return;
            }
        }
        wCurIdx = wQueue[wQueuePos];
        wFlipped = false;
        if (wQuizNext != null) {
            wQuizHandler.removeCallbacks(wQuizNext);
            wQuizNext = null;
        }
        wAutoSpoken = -1;          // 每个词重新出现都该读一次
        wPicBm = null;
        wPicUrl = null;
        stopVoice();
        renderWordStudy(false);
    }

    private void renderWordStudy(boolean finished) {
        wordStudyInner.removeAllViews();
        wordStudyInner.addView(makeBack("‹ 返回", new View.OnClickListener() {
            public void onClick(View v) { goBack(backTarget()); }
        }), wrapM());

        int total = (wQueue == null) ? 0 : wQueue.length;
        if (finished || total == 0) {
            if (total > 0) doCheckin();     // 走到「背完了」这一页才算今天打过卡
            TextView done = new TextView(this);
            done.setText(total == 0 ? "今天没有要背的词" : "今天的都背完了");
            done.setTextSize(m.backSize);
            done.setTextColor(C_PRIMARY);
            done.setGravity(Gravity.CENTER);
            done.setPadding(0, dp(24), 0, dp(10));
            wordStudyInner.addView(done, wrap());
            TextView tip = new TextView(this);
            if (total == 0) {
                tip.setText("去设置里换一本词书，或者明天再来。");
            } else {
                StringBuilder sb = new StringBuilder();
                if (wStatReviewDone > 0) sb.append("复习 ").append(wStatReviewDone).append(" 个");
                if (wStatNewDone > 0) {
                    if (sb.length() > 0) sb.append(" · ");
                    sb.append("新学 ").append(wStatNewDone).append(" 个");
                }
                if (wStatHard > 0) sb.append(" · ").append(wStatHard).append(" 次没记住");
                sb.append("\n明天要复习 ").append(countDueAt(todayDay() + 1)).append(" 个");
                tip.setText(sb.toString());
                tip.setLineSpacing(dp(2), 1.2f);
            }
            tip.setTextSize(m.subtitleSize - 1f);
            tip.setTextColor(C_SUB);
            tip.setGravity(Gravity.CENTER);
            tip.setPadding(dp(12), 0, dp(12), dp(16));
            wordStudyInner.addView(tip, wrap());
            TextView back = new TextView(this);
            back.setText("回到背单词");
            back.setTextSize(m.subtitleSize);
            back.setGravity(Gravity.CENTER);
            back.setPadding(dp(10), dp(10), dp(10), dp(10));
            back.setTextColor(C_PRIMARY);
            back.setBackground(clickBg(C_PILL));
            back.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { goBack(S_WORD_HOME); }
            });
            wordStudyInner.addView(back, wrap());
            padInner(wordStudyInner);
            return;
        }

        // 顶部进度
        TextView prog = new TextView(this);
        prog.setText(wRound > 1
                ? ("第 " + wRound + " 轮 · " + (wQueuePos + 1) + " / " + total)
                : ((wQueuePos + 1) + " / " + total));
        prog.setTextSize(m.subtitleSize - 1f);
        prog.setTextColor(C_SUB);
        prog.setGravity(Gravity.CENTER);
        wordStudyInner.addView(prog, wrapGap(4));

        if (shouldQuiz(wCurIdx)) {
            renderWordQuiz();
        } else {
            renderSelfRateCard();
        }

        if (!online()) {
            wordStudyInner.addView(grayTip("离线模式：无法查看助记图片和收听单词发音"), wrapGap(6));
        }
        padInner(wordStudyInner);
    }

    // ================= 单词：四选一测试（三期） =================

    private boolean wQuizAnswered = false;
    private int wQuizAnswer = 0;                 // 正确答案在选项里的位置
    private final android.os.Handler wQuizHandler =
            new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable wQuizNext;
    /** 本轮答错过的词：下次出现先走自评卡片，看懂了再考。 */
    private final java.util.HashSet<Integer> wMissed = new java.util.HashSet<Integer>();

    /** 背过至少一次的词才出选择题；新词第一次见还是看卡片自评。 */
    private boolean shouldQuiz(int idx) {
        if (idx < 0) return false;
        if (!prefs.getBoolean("word_quiz", true)) return false;
        if (wMissed.contains(idx)) return false;   // 这轮答错过的，下次先给卡片看释义
        return progByte(idx, 5) > 0 || progShort(idx, 1) > 0;
    }

    /** 取第一条中文释义，形如「n. 通道；进入」。 */
    private String firstMeaning(JSONObject cc) {
        if (cc == null) return "";
        JSONArray tr = cc.optJSONArray("trans");
        if (tr == null) return "";
        for (int i = 0; i < tr.length(); i++) {
            JSONObject t = tr.optJSONObject(i);
            if (t == null) continue;
            String cn = t.optString("tranCn", "").trim();
            if (cn.length() == 0) continue;
            String pos = t.optString("pos", "").trim();
            return (pos.length() > 0 ? pos + " " : "") + cn;
        }
        return "";
    }

    /** 第 i 个词的第一条释义。 */
    private String meaningAt(int i) {
        JSONObject o = readWordLine(i);
        JSONObject w = (o == null) ? null : o.optJSONObject("content");
        if (w != null) w = w.optJSONObject("word");
        return firstMeaning(w == null ? null : w.optJSONObject("content"));
    }

    /** 随机找几个别的词的释义当干扰项（去重）。挑不满就返回已有的，按实际数量出题。 */
    private ArrayList<String> randomMeanings(int exclude, String avoid, int count) {
        ArrayList<String> out = new ArrayList<String>();
        int n = wWords.size();
        if (n < 16) return out;
        int tries = 0;
        while (out.size() < count && tries < 40) {
            tries++;
            int i = (int) (Math.random() * n);
            if (i == exclude) continue;
            String m = meaningAt(i);
            if (m.length() == 0 || m.equals(avoid) || out.contains(m)) continue;
            out.add(m);
        }
        return out;
    }

    /** 复习词的四选一：给英文，选正确的中文释义。点对算「认识」，点错算「不认识」。 */
    private void renderWordQuiz() {
        wQuizAnswered = false;
        wCardBox = null;              // 选择题不用那张可翻面的卡
        wFlipped = true;

        JSONObject o = readWordLine(wCurIdx);
        JSONObject word = (o == null) ? null : o.optJSONObject("content");
        JSONObject w = (word == null) ? null : word.optJSONObject("word");
        JSONObject cc = (w == null) ? null : w.optJSONObject("content");
        String head = (w == null) ? "" : w.optString("wordHead", "");
        wCurHead = head;

        final String right = firstMeaning(cc);
        ArrayList<String> wrongs = (right.length() == 0)
                ? new ArrayList<String>() : randomMeanings(wCurIdx, right, 3);
        if (right.length() == 0 || wrongs.isEmpty()) {
            renderSelfRateCard();     // 没释义或凑不出干扰项，退回自评
            return;
        }

        if (autoSpeakOn()) speakWord(head);

        // 题干：词 + 音标（静音时不出喇叭）
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        GradientDrawable cbg = new GradientDrawable();
        cbg.setColor(C_PILL);
        cbg.setCornerRadius(dp(16));
        card.setBackgroundDrawable(cbg);

        TextView tv = new TextView(this);
        tv.setText(head);
        tv.setTextSize(m.titleSize - 1f);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setTextColor(Color.WHITE);
        tv.setGravity(Gravity.CENTER);
        card.addView(tv);

        String phone = (cc == null) ? "" : cc.optString("usphone", "");
        if (phone.length() == 0 && cc != null) phone = cc.optString("ukphone", "");
        LinearLayout phRow = new LinearLayout(this);
        phRow.setOrientation(LinearLayout.HORIZONTAL);
        phRow.setGravity(Gravity.CENTER);
        if (phone.length() > 0) {
            TextView ph = new TextView(this);
            ph.setText("/" + phone + "/");
            ph.setTextSize(m.subtitleSize);
            ph.setTextColor(C_SUB);
            if (!muteOn()) ph.setPadding(0, 0, dp(8), 0);
            phRow.addView(ph);
        }
        if (!muteOn()) {
            TextView spk = new TextView(this);
            spk.setPadding(dp(9), dp(7), dp(9), dp(7));
            spk.setBackground(clickBg(0xFF14384A));
            applyIconOnly(spk, "ic_volume", C_PRIMARY, 15);
            spk.setContentDescription("播放发音");
            spk.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    vibrate();
                    speakWord(wCurHead);
                }
            });
            phRow.addView(spk);
        }
        if (phRow.getChildCount() > 0) {
            LinearLayout.LayoutParams plp = wrap();
            plp.topMargin = dp(4);
            card.addView(phRow, plp);
        }
        wordStudyInner.addView(card, wrapGap(8));

        // 选项竖着排：圆屏横着放不下四个，竖着每个都是整行，点起来也准
        final int total = wrongs.size() + 1;
        final String[] opts = new String[total];
        wQuizAnswer = (int) (Math.random() * total);
        opts[wQuizAnswer] = right;
        int k = 0;
        for (int i = 0; i < total; i++) {
            if (i == wQuizAnswer) continue;
            opts[i] = wrongs.get(k++);
        }

        final TextView[] btns = new TextView[total];
        for (int i = 0; i < total; i++) {
            final int idx = i;
            TextView b = new TextView(this);
            b.setText((char) ('A' + i) + ". " + opts[i]);
            b.setTextSize(m.subtitleSize - 0.5f);
            b.setTextColor(C_TEXT);
            b.setGravity(Gravity.CENTER);
            b.setPadding(dp(10), dp(11), dp(10), dp(11));
            b.setBackground(pill(C_PILL));
            b.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    if (wQuizAnswered) return;
                    wQuizAnswered = true;
                    vibrate();
                    boolean ok = (idx == wQuizAnswer);
                    for (int j = 0; j < btns.length; j++) {
                        if (btns[j] != null) btns[j].setClickable(false);
                    }
                    btns[idx].setTextColor(ok ? C_PRIMARY : 0xFFFF8A80);
                    btns[idx].setBackground(pill(ok ? 0xFF14384A : 0xFF3A1A1A));
                    if (!ok) {
                        btns[wQuizAnswer].setTextColor(C_PRIMARY);
                        btns[wQuizAnswer].setBackground(pill(0xFF14384A));
                    }
                    final boolean good = ok;
                    wQuizNext = new Runnable() {
                        public void run() {
                            if (screen != S_WORD_STUDY) return;
                            rateWord(good ? 5 : 1);
                        }
                    };
                    wQuizHandler.postDelayed(wQuizNext, ok ? 650 : 1700);
                }
            });
            btns[i] = b;
            wordStudyInner.addView(b, wrapGap(5));
        }
    }

    /** 自评卡片：新词第一次见、或者这个课程关了选择题时走这里。 */
    private void renderSelfRateCard() {
        // 卡片区
        wCardBox = new LinearLayout(this);
        wCardBox.setOrientation(LinearLayout.VERTICAL);
        wCardBox.setPadding(dp(14), dp(16), dp(14), dp(16));
        GradientDrawable cbg = new GradientDrawable();
        cbg.setColor(C_PILL);
        cbg.setCornerRadius(dp(16));
        wCardBox.setBackgroundDrawable(cbg);
        wCardBox.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                vibrate();
                // 只能展开，不能收回：收回去只会让人以为「还得再看一遍」
                if (wFlipped) return;
                wFlipped = true;
                renderWordCard();
                // 展开时再读一遍（自动发音开着才做）
                if (autoSpeakOn()) speakWord(wCurHead);
            }
        });
        wordStudyInner.addView(wCardBox, wrapGap(10));
        renderWordCard();

        // 三档评分：翻面前只给「看释义」，避免瞎点
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER);
        final String[] labels = {"不认识", "模糊", "认识"};
        final int[] qs = {1, 3, 5};
        final int[] colors = {0xFFFF8A80, 0xFFFFB74D, C_PRIMARY};
        for (int i = 0; i < labels.length; i++) {
            final int q = qs[i];
            TextView b = new TextView(this);
            b.setText(labels[i]);
            b.setTextSize(m.subtitleSize - 1f);
            b.setGravity(Gravity.CENTER);
            b.setPadding(dp(6), dp(9), dp(6), dp(9));
            b.setTextColor(colors[i]);
            b.setBackground(pill(q == 5 ? 0xFF14384A : C_PILL));
            b.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    vibrate();
                    if (!wFlipped) { wFlipped = true; renderWordCard(); return; }
                    rateWord(q);
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            lp.leftMargin = dp(3);
            lp.rightMargin = dp(3);
            bar.addView(b, lp);
        }
        wordStudyInner.addView(bar, wrapGap(6));
    }

    /** 只重画卡片内容（翻面/切词都不重建整页，避免滚动位置跳动）。 */
    private void renderWordCard() {
        if (wCardBox == null) return;
        wCardBox.removeAllViews();
        JSONObject o = readWordLine(wCurIdx);
        JSONObject word = (o == null) ? null : o.optJSONObject("content");
        JSONObject w = (word == null) ? null : word.optJSONObject("word");
        JSONObject cc = (w == null) ? null : w.optJSONObject("content");

        TextView head = new TextView(this);
        head.setText(w == null ? "（读取失败）" : w.optString("wordHead", ""));
        head.setTextSize(m.titleSize);
        head.setTypeface(Typeface.DEFAULT_BOLD);
        head.setTextColor(Color.WHITE);
        head.setGravity(Gravity.CENTER);
        wCardBox.addView(head, wrap());

        final String headWord = (w == null) ? "" : w.optString("wordHead", "");
        wCurHead = headWord;

        // 音标 + 发音按钮同一行：只给音标不会读，按钮必须挨着它
        if (cc != null) {
            String phone = cc.optString("usphone", "");
            if (phone.length() == 0) phone = cc.optString("ukphone", "");
            LinearLayout phRow = new LinearLayout(this);
            phRow.setOrientation(LinearLayout.HORIZONTAL);
            phRow.setGravity(Gravity.CENTER);

            if (phone.length() > 0) {
                TextView ph = new TextView(this);
                ph.setText("/" + phone + "/");
                ph.setTextSize(m.subtitleSize);
                ph.setTextColor(C_SUB);
                ph.setPadding(0, 0, dp(8), 0);
                phRow.addView(ph);
            }

            if (!muteOn()) {
                TextView spk = new TextView(this);
                spk.setPadding(dp(9), dp(7), dp(9), dp(7));
                spk.setBackground(clickBg(0xFF14384A));
                applyIconOnly(spk, "ic_volume", C_PRIMARY, 15);
                spk.setContentDescription("播放发音");
                spk.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        vibrate();
                        speakWord(headWord);
                    }
                });
                phRow.addView(spk);
            }

            LinearLayout.LayoutParams plp = wrap();
            plp.topMargin = dp(4);
            wCardBox.addView(phRow, plp);
        }

        // 自动发音：卡片一出现就读一遍。翻面会再调一次 renderWordCard，靠 wAutoSpoken 挡住不重复响
        if (autoSpeakOn() && wCurIdx >= 0 && wAutoSpoken != wCurIdx) {
            wAutoSpoken = wCurIdx;
            speakWord(headWord);
        }

        if (!wFlipped) {
            TextView hint = new TextView(this);
            hint.setText("轻点卡片看释义");
            hint.setTextSize(m.subtitleSize - 2f);
            hint.setTextColor(0xFF666666);
            hint.setGravity(Gravity.CENTER);
            hint.setPadding(0, dp(12), 0, dp(2));
            wCardBox.addView(hint, wrap());
            return;
        }

        JSONArray trans = (cc == null) ? null : cc.optJSONArray("trans");
        if (trans != null) {
            for (int i = 0; i < trans.length(); i++) {
                JSONObject t = trans.optJSONObject(i);
                if (t == null) continue;
                String pos = t.optString("pos", "");
                String cn = t.optString("tranCn", "");
                if (cn.length() == 0) continue;
                TextView tv = new TextView(this);
                tv.setText((pos.length() > 0 ? pos + "  " : "") + cn);
                tv.setTextSize(m.backSize - 1f);
                tv.setTextColor(C_TEXT);
                tv.setPadding(0, dp(6), 0, 0);
                wCardBox.addView(tv, wrap());
            }
        }

        // 助记图：词条自带就用自己的；没有就查全局索引，任何词书的同一个词都能配上图
        String picUrl = (cc == null) ? "" : cc.optString("picture", "");
        if (picUrl.length() == 0) picUrl = picUrlFor(headWord);
        if (picUrl.length() > 0) addWordPicture(picUrl);

        JSONObject sen = (cc == null) ? null : cc.optJSONObject("sentence");
        JSONArray ss = (sen == null) ? null : sen.optJSONArray("sentences");
        if (ss != null && ss.length() > 0) {
            JSONObject s0 = ss.optJSONObject(0);
            if (s0 != null) {
                String en = s0.optString("sContent", "");
                String cn = s0.optString("sCn", "");
                if (en.length() > 0) {
                    TextView e1 = new TextView(this);
                    e1.setText(en);
                    e1.setTextSize(m.subtitleSize - 1f);
                    e1.setTextColor(0xFFB0BEC5);
                    e1.setPadding(0, dp(10), 0, 0);
                    wCardBox.addView(e1, wrap());
                }
                if (cn.length() > 0) {
                    TextView e2 = new TextView(this);
                    e2.setText(cn);
                    e2.setTextSize(m.subtitleSize - 2f);
                    e2.setTextColor(C_SUB);
                    wCardBox.addView(e2, wrap());
                }
            }
        }
    }

    /** 记忆曲线 1 → 2 → 4 → 7 → 15 → 30 → 60 天。q=5 认识 / 3 模糊 / 1 不认识。
     *  「不认识」不往后推，而是明天再见 + 本轮进重来池，当天就要再碰一次。 */
    private void rateWord(int q) {
        int i = wCurIdx;
        int interval = progShort(i, 1);
        int reps = progByte(i, 5);
        int today = todayDay();
        boolean wasNew = (interval == 0 && reps == 0);

        if (q >= 3) {
            int next;
            if (interval <= 0) next = 1;
            else if (interval < 2) next = 2;
            else if (interval < 4) next = 4;
            else if (interval < 7) next = 7;
            else if (interval < 15) next = 15;
            else if (interval < 30) next = 30;
            else next = Math.min(60, interval * 2);
            // 模糊：不往后推，也不允许比原来更短（4→7/2=3 那种倒退没道理）
            if (q == 3 && interval > 0) next = Math.max(interval, Math.max(1, next / 2));
            interval = next;
            reps = Math.min(255, reps + 1);
            wMissed.remove(i);      // 看过卡片答对了，下次回到选择题
            // 生词本里点「认识」就是刷会了，直接移出生词本
            if (wMode == 2 && q == 5) setProgByte(i, 6, 0);
        } else {
            // 记一次忘记（第 6 字节），明天再见，本轮还会在重来池里再出现
            setProgByte(i, 6, Math.min(255, progByte(i, 6) + 1));
            reps = 0;
            interval = 1;
            addRetry(i);
            wMissed.add(i);         // 下一轮先给卡片看释义，不连着考
            wStatHard++;
        }

        setProgShort(i, 1, interval);
        setProgShort(i, 3, Math.min(65535, today + interval));
        setProgByte(i, 5, reps);
        saveWordProgress();

        // 同一个词在重来轮里会再评一次，只按「第一次见到」记一笔
        if (wCounted.add(i)) {
            if (wasNew) { wStatNewDone++; bumpNewToday(); } else wStatReviewDone++;
        }
        wQueuePos++;
        nextCard();
    }

    // ================= 单词：搜索 =================

    private static final int WORD_SEARCH_LIMIT = 20;

    private void buildWordSearchScreen() {
        wordSearchScroll = new OverScrollScrollView(this);
        wordSearchScroll.setVerticalScrollBarEnabled(false);
        roundPadScroll(wordSearchScroll);
        wordSearchInner = new LinearLayout(this);
        wordSearchInner.setOrientation(LinearLayout.VERTICAL);
        wordSearchScroll.addView(wordSearchInner);
        wordSearchScreen.addView(wordSearchScroll, lw());
    }

    private android.widget.EditText wSearchBox;
    private LinearLayout wSearchResults;
    private TextView wSearchStatus;

    // 搜词的两条路：有网走有道（免 key、免登录），没网在「所有已下载词书」里按前缀搜。
    // 本地索引只存词头 + 行号，正文点开才读；建一次一直留着，书单变了才重建。
    private static final String SEARCH_SUGGEST = "https://dict.youdao.com/suggest?doctype=json&num=8&q=";
    private static final String SEARCH_DICT = "https://dict.youdao.com/jsonapi?q=";
    private static final int SEARCH_PER_BOOK = 40;      // 每本书最多取这么多前缀命中，再一起排序取前 20

    private String wSearchKey = "";                     // 当前关键词：网络结果回来时比对，别让旧结果盖新结果
    private volatile int wSearchSeq = 0;                // 后台扫描线程也要读它，改了词就停下
    private Runnable wSearchDebounce;
    private final Handler wSearchHandler = new Handler();
    private long wNetSearchDead = 0L;                   // 网络查词失败后，这段时间内直接走本地，别每次都等超时
    private final ArrayList<BookIx> searchIx = new ArrayList<BookIx>();
    private String searchIxSig = "";                    // 书单指纹（id + 两个文件长度）
    private boolean searchIxBuilding = false;
    private int searchIxWords = 0;
    private final java.util.HashMap<String, ArrayList<String[]>> suggestCache =
            new java.util.HashMap<String, ArrayList<String[]>>();

    /** 多书索引：一本书的词头 + 排序下标 + 每行偏移。10 万词也就几 MB，比每次重扫文件划算。 */
    private static class BookIx {
        String id, name;
        final ArrayList<String> words = new ArrayList<String>();
        int[] off, len, sorted;

        void sortWords() {
            int n = words.size();
            Integer[] idx = new Integer[n];
            for (int i = 0; i < n; i++) idx[i] = i;
            java.util.Arrays.sort(idx, new java.util.Comparator<Integer>() {
                public int compare(Integer a, Integer b) {
                    return words.get(a).compareToIgnoreCase(words.get(b));
                }
            });
            sorted = new int[n];
            for (int i = 0; i < n; i++) sorted[i] = idx[i];
        }
    }

    /** 一条搜索结果：词、来自哪本书、书里的第几行。 */
    private static class Hit {
        String word;
        BookIx bk;
        int line;
        Hit(String w, BookIx b, int l) { word = w; bk = b; line = l; }
    }

    private void renderWordSearch() {
        loadWordBooks();
        wSearchKey = "";
        wSearchSeq++;
        wordSearchInner.removeAllViews();
        wordSearchInner.addView(makeBack("‹ 返回", new View.OnClickListener() {
            // 跟着入口走：从英语科目进来就回科目页，从背单词主页进来才主页
            public void onClick(View v) { goBack(backTarget()); }
        }), wrapM());
        wordSearchInner.addView(pageTitle("搜索单词"), wrap());

        boolean anyBook = !downloadedBooks().isEmpty();
        if (!anyBook && !online()) {
            wordSearchInner.addView(grayTip("没有已下载的词书，也没有网络。先去「背单词设置」下载一本，或连上网再搜。"), wrap());
            return;
        }

        wSearchBox = new android.widget.EditText(this);
        wSearchBox.setHint("输入英文或中文");
        wSearchBox.setSingleLine(true);
        wSearchBox.setTextSize(m.subtitleSize);
        wSearchBox.setTextColor(C_TEXT);
        wSearchBox.setHintTextColor(0xFF666666);
        wSearchBox.setBackground(pill(C_PILL));
        wSearchBox.setPadding(dp(12), dp(8), dp(12), dp(8));
        wSearchBox.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        wSearchBox.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            public void onTextChanged(CharSequence s, int a, int b, int c) { }
            public void afterTextChanged(android.text.Editable e) {
                onSearchInput(e.toString().trim());
            }
        });
        wordSearchInner.addView(wSearchBox, wrapGap(6));   // 搜索框和第一条结果不能贴在一起

        wSearchStatus = new TextView(this);
        wSearchStatus.setTextSize(m.subtitleSize - 2f);
        wSearchStatus.setTextColor(C_SUB);
        wSearchStatus.setGravity(Gravity.CENTER);
        wSearchStatus.setPadding(dp(4), dp(2), dp(4), dp(2));
        wordSearchInner.addView(wSearchStatus, wrap());

        wSearchResults = new LinearLayout(this);
        wSearchResults.setOrientation(LinearLayout.VERTICAL);
        wordSearchInner.addView(wSearchResults, wrapGap(4));
        wordSearchInner.addView(grayTip("英文按开头几个字母匹配，中文按释义找；点结果看完整释义。"), wrap());
        padInner(wordSearchInner);

        updateSearchStatus();
        ensureSearchIndexAsync();     // 还没输就先在后台把词库整理好，别等第一下按键
    }

    /** 输入变化：350ms 防抖，打字过程中不每敲一个字母就发一次请求。 */
    private void onSearchInput(String q) {
        wSearchKey = q;
        wSearchSeq++;
        if (wSearchDebounce != null) wSearchHandler.removeCallbacks(wSearchDebounce);
        if (q.length() == 0) {
            if (wSearchResults != null) wSearchResults.removeAllViews();
            updateSearchStatus();
            return;
        }
        final String key = q;
        wSearchDebounce = new Runnable() {
            public void run() {
                if (!key.equals(wSearchKey)) return;
                fillWordResultsNow(key);
            }
        };
        wSearchHandler.postDelayed(wSearchDebounce, 350);
    }

    private void fillWordResultsNow(String key) {
        if (wSearchResults == null || screen != S_WORD_SEARCH) return;
        wSearchResults.removeAllViews();
        if (netSearchUsable()) searchOnline(key);
        else searchLocal(key);
    }

    /** 有网 → 有道；请求失败过一次就静默 60 秒，期间直接搜本地。 */
    private boolean netSearchUsable() {
        if (!online()) return false;
        return System.currentTimeMillis() >= wNetSearchDead;
    }

    // ---- 本地：所有已下载词书一起搜 ----

    /** 书单指纹：书 id + 词书大小 + 索引大小。下载/删除一本书，指纹就变了。 */
    private String searchIxSignature() {
        StringBuilder sb = new StringBuilder();
        for (WordBook b : wordBooks) {
            if (!bookDownloaded(b)) continue;
            sb.append(b.id).append(':')
                    .append(bookJson(b.id).length()).append('/')
                    .append(bookIdx(b.id).length()).append(';');
        }
        return sb.toString();
    }

    private ArrayList<WordBook> downloadedBooks() {
        ArrayList<WordBook> out = new ArrayList<WordBook>();
        for (WordBook b : wordBooks) if (bookDownloaded(b) && bookIdx(b.id).exists()) out.add(b);
        return out;
    }

    /** 后台整理多书索引；已经是最新就直接返回。 */
    private void ensureSearchIndexAsync() {
        if (searchIxBuilding) return;
        final String sig = searchIxSignature();
        if (sig.equals(searchIxSig) && (!searchIx.isEmpty() || sig.length() == 0)) return;
        if (sig.length() == 0) {
            searchIx.clear();
            searchIxSig = "";
            searchIxWords = 0;
            updateSearchStatus();
            return;
        }
        searchIxBuilding = true;
        updateSearchStatus();
        new Thread(new Runnable() {
            public void run() {
                final ArrayList<BookIx> built = buildSearchIndex();
                int total = 0;
                if (built != null) for (BookIx b : built) total += b.words.size();
                final int tot = total;
                runOnUiThread(new Runnable() {
                    public void run() {
                        searchIxBuilding = false;
                        if (built != null) {
                            searchIx.clear();
                            searchIx.addAll(built);
                            searchIxSig = sig;
                            searchIxWords = tot;
                        }
                        updateSearchStatus();
                        if (screen == S_WORD_SEARCH && wSearchKey.length() > 0) fillWordResultsNow(wSearchKey);
                    }
                });
            }
        }).start();
    }

    private ArrayList<BookIx> buildSearchIndex() {
        ArrayList<BookIx> out = new ArrayList<BookIx>();
        for (WordBook b : downloadedBooks()) {
            try {
                BookIx ix = new BookIx();
                ix.id = b.id;
                ix.name = b.name;
                java.io.DataInputStream in = new java.io.DataInputStream(
                        new java.io.BufferedInputStream(new java.io.FileInputStream(bookIdx(b.id))));
                try {
                    if (in.readInt() != 0x48575A31) continue;
                    int n = in.readInt();
                    ix.words.ensureCapacity(n);
                    ix.off = new int[n];
                    ix.len = new int[n];
                    for (int i = 0; i < n; i++) {
                        ix.words.add(rStr(in));
                        ix.off[i] = in.readInt();
                        ix.len[i] = in.readInt();
                    }
                } finally {
                    try { in.close(); } catch (Exception ignored) { }
                }
                ix.sortWords();
                if (!ix.words.isEmpty()) out.add(ix);
            } catch (Throwable ignored) {
                // 单本坏了就跳过，别让整个搜索页打不开
            }
        }
        return out;
    }

    private void searchLocal(String key) {
        if (isChinese(key)) { searchLocalChinese(key); return; }   // 中文按释义反查
        if (searchIxBuilding) { showSearchNote("正在整理词库…"); return; }
        if (!searchIxSig.equals(searchIxSignature())) {
            ensureSearchIndexAsync();
            showSearchNote("正在整理词库…");
            return;
        }
        if (searchIx.isEmpty()) { showSearchNote("还没有已下载的词书"); return; }

        final String low = key.toLowerCase();
        ArrayList<Hit> hits = new ArrayList<Hit>();
        for (int bi = 0; bi < searchIx.size(); bi++) {
            BookIx bk = searchIx.get(bi);
            int[] srt = bk.sorted;
            if (srt == null) continue;
            int lo = 0, hi = srt.length;
            while (lo < hi) {
                int mid = (lo + hi) >>> 1;
                if (bk.words.get(srt[mid]).compareToIgnoreCase(low) < 0) lo = mid + 1;
                else hi = mid;
            }
            int take = 0;
            for (int i = lo; i < srt.length && take < SEARCH_PER_BOOK; i++) {
                String w = bk.words.get(srt[i]);
                if (!startsWithIgnoreCase(w, low)) break;
                hits.add(new Hit(w, bk, srt[i]));
                take++;
            }
        }
        if (hits.isEmpty()) { showSearchNote("没找到以「" + key + "」开头的词"); return; }

        java.util.Collections.sort(hits, new java.util.Comparator<Hit>() {
            public int compare(Hit a, Hit b) { return a.word.compareToIgnoreCase(b.word); }
        });
        ArrayList<Hit> top = new ArrayList<Hit>();
        java.util.HashSet<String> seen = new java.util.HashSet<String>();
        for (int i = 0; i < hits.size() && top.size() < WORD_SEARCH_LIMIT; i++) {
            Hit h = hits.get(i);
            if (seen.add(h.word.toLowerCase())) top.add(h);      // 同一词在多本书里只留一条
        }
        renderLocalHits(top);
    }

    /** 输入里只要有一个非 ASCII 字符就当中文：英文前缀搜不到中文，得按释义反查。 */
    private static boolean isChinese(String s) {
        if (s == null) return false;
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) > 0x7F) return true;
        return false;
    }

    /** 离线中文搜索：把已下载的词书逐本扫一遍，找释义里含这个中文的词。
     *  不建额外索引（建索引要读全部正文，一样慢），扫的时候字节级预筛，
     *  只有「这一行含关键词」才解析 JSON 确认，命中的行直接用 .idx 的词头。 */
    private void searchLocalChinese(final String key) {
        if (searchIxBuilding || !searchIxSig.equals(searchIxSignature())) {
            ensureSearchIndexAsync();
            showSearchNote("正在整理词库…");
            return;
        }
        if (searchIx.isEmpty()) { showSearchNote("还没有已下载的词书"); return; }

        final ArrayList<BookIx> books = new ArrayList<BookIx>(searchIx);
        if (curBook != null) {          // 正在背的那本排最前，通常就是用户想找的
            for (int i = 0; i < books.size(); i++) {
                if (books.get(i).id.equals(curBook.id)) {
                    books.add(0, books.remove(i));
                    break;
                }
            }
        }
        final int seq = wSearchSeq;
        showSearchNote("正在词书里找「" + key + "」…");
        new Thread(new Runnable() {
            public void run() {
                final byte[] kb = key.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                final ArrayList<Hit> hits = new ArrayList<Hit>();
                final ArrayList<String> means = new ArrayList<String>();
                long deadline = System.currentTimeMillis() + 8000L;    // 书多的时候别无限扫
                for (int i = 0; i < books.size(); i++) {
                    scanBookChinese(books.get(i), kb, key, seq, hits, means, WORD_SEARCH_LIMIT, deadline);
                    if (seq != wSearchSeq) return;
                    if (hits.size() >= WORD_SEARCH_LIMIT) break;
                    if (System.currentTimeMillis() > deadline) break;
                }
                runOnUiThread(new Runnable() {
                    public void run() {
                        if (seq != wSearchSeq || screen != S_WORD_SEARCH || wSearchResults == null) return;
                        renderChineseHits(key, hits, means);
                    }
                });
            }
        }).start();
    }

    /** 扫一本书的正文；行号只在「有词头的行」上递增，必须和 .idx 的计数规则一致。 */
    private void scanBookChinese(BookIx bk, byte[] kb, String key, int seq,
                                 ArrayList<Hit> hits, ArrayList<String> means,
                                 int max, long deadline) {
        java.io.BufferedInputStream in = null;
        try {
            in = new java.io.BufferedInputStream(new java.io.FileInputStream(bookJson(bk.id)), 64 * 1024);
            byte[] buf = new byte[64 * 1024];
            byte[] line = new byte[96 * 1024];
            int lineLen = 0, lineNo = 0, n;
            while ((n = in.read(buf)) > 0) {
                for (int i = 0; i < n; i++) {
                    byte c = buf[i];
                    if (c == '\n') {
                        if (lineLen > 0) {
                            String head = headOf(line, lineLen);
                            if (head != null) {
                                if (containsBytes(line, lineLen, kb)) {
                                    String mean = meaningOfLine(line, lineLen);
                                    if (mean != null && mean.indexOf(key) >= 0) {
                                        hits.add(new Hit(head, bk, lineNo));
                                        means.add(mean);
                                        if (hits.size() >= max) return;
                                    }
                                }
                                lineNo++;
                            }
                        }
                        lineLen = 0;
                    } else if (lineLen < line.length) {
                        line[lineLen++] = c;
                    }
                }
                if (seq != wSearchSeq || System.currentTimeMillis() > deadline) return;
            }
            if (lineLen > 0) {
                String head = headOf(line, lineLen);
                if (head != null && containsBytes(line, lineLen, kb)) {
                    String mean = meaningOfLine(line, lineLen);
                    if (mean != null && mean.indexOf(key) >= 0) {
                        hits.add(new Hit(head, bk, lineNo));
                        means.add(mean);
                    }
                }
            }
        } catch (Throwable ignored) {
        } finally {
            try { if (in != null) in.close(); } catch (Exception ignored) { }
        }
    }

    /** 字节里找一段字节（中文按 UTF-8 直接比，不用先解码整行）。 */
    private static boolean containsBytes(byte[] hay, int len, byte[] key) {
        if (key.length == 0 || len < key.length) return false;
        int last = len - key.length;
        outer:
        for (int i = 0; i <= last; i++) {
            if (hay[i] != key[0]) continue;
            for (int k = 1; k < key.length; k++) if (hay[i + k] != key[k]) continue outer;
            return true;
        }
        return false;
    }

    private String meaningOfLine(byte[] line, int len) {
        try { return meaningOf(new JSONObject(new String(line, 0, len, "UTF-8"))); }
        catch (Throwable e) { return null; }
    }

    private void renderChineseHits(String key, ArrayList<Hit> hits, ArrayList<String> means) {
        if (wSearchResults == null) return;
        wSearchResults.removeAllViews();
        if (hits.isEmpty()) { showSearchNote("本地词书里没有含「" + key + "」的词"); return; }
        for (int i = 0; i < hits.size(); i++) {
            Hit h = hits.get(i);
            wSearchResults.addView(wordResultRow(h.word, means.get(i), h.bk.name, h.bk, h.line, null), wrapGap(4));
        }
    }

    /** 大小写不敏感的前缀判断；low 已经是小写，逐字符比，别用正则。 */
    private static boolean startsWithIgnoreCase(String w, String low) {
        if (w == null || w.length() < low.length()) return false;
        for (int i = 0; i < low.length(); i++) {
            char a = w.charAt(i);
            if (a >= 'A' && a <= 'Z') a = (char) (a + 32);
            if (a != low.charAt(i)) return false;
        }
        return true;
    }

    /** 同一本书只开一次文件，20 条释义一次读完——每条都开关文件在手表上能感觉出来。 */
    private void renderLocalHits(ArrayList<Hit> hits) {
        if (wSearchResults == null) return;
        wSearchResults.removeAllViews();
        java.util.HashMap<String, java.io.RandomAccessFile> rafs =
                new java.util.HashMap<String, java.io.RandomAccessFile>();
        try {
            for (int i = 0; i < hits.size(); i++) {
                final Hit h = hits.get(i);
                String mean = "";
                try {
                    java.io.RandomAccessFile rf = rafs.get(h.bk.id);
                    if (rf == null) {
                        rf = new java.io.RandomAccessFile(bookJson(h.bk.id), "r");
                        rafs.put(h.bk.id, rf);
                    }
                    mean = meaningAt(rf, h.bk, h.line);
                } catch (Throwable ignored) { }
                wSearchResults.addView(wordResultRow(h.word, mean, h.bk.name, h.bk, h.line, null), wrapGap(4));
            }
        } finally {
            for (java.io.RandomAccessFile rf : rafs.values()) {
                try { rf.close(); } catch (Exception ignored) { }
            }
        }
    }

    private void showSearchNote(String text) {
        if (wSearchResults == null) return;
        wSearchResults.removeAllViews();
        TextView none = new TextView(this);
        none.setText(text);
        none.setTextSize(m.subtitleSize - 1f);
        none.setTextColor(C_SUB);
        none.setPadding(dp(4), dp(10), dp(4), dp(10));
        none.setGravity(Gravity.CENTER);
        wSearchResults.addView(none, wrap());
    }

    private void updateSearchStatus() {
        if (wSearchStatus == null) return;
        if (searchIxBuilding) { wSearchStatus.setText("正在整理词库…"); return; }
        int books = downloadedBooks().size();
        if (netSearchUsable()) {
            wSearchStatus.setText(books > 0
                    ? "在线 · 有道词典（没网时搜本地 " + books + " 本词书）"
                    : "在线 · 有道词典");
        } else if (books > 0) {
            wSearchStatus.setText("离线 · 本地 " + books + " 本词书，共 " + searchIxWords + " 词");
        } else {
            wSearchStatus.setText("离线，也没有已下载的词书");
        }
    }

    // ---- 在线：有道词典（免 key、免登录） ----

    private void searchOnline(final String key) {
        ArrayList<String[]> cached = suggestCache.get(key.toLowerCase());
        if (cached != null) { renderOnlineRows(cached); return; }

        final int seq = wSearchSeq;
        new Thread(new Runnable() {
            public void run() {
                String body = null;
                try { body = httpGetTextDict(SEARCH_SUGGEST + urlEnc(key), 4000, 6000); }
                catch (Throwable ignored) { body = null; }
                final String b = body;
                runOnUiThread(new Runnable() {
                    public void run() {
                        if (seq != wSearchSeq || screen != S_WORD_SEARCH || wSearchResults == null) return;
                        if (b == null) {
                            // 连了网但没有外网（或接口不通）：退回本地，并把网络静默一分钟
                            wNetSearchDead = System.currentTimeMillis() + 60000L;
                            updateSearchStatus();
                            toast("网络没响应，改搜本地词书");
                            searchLocal(key);
                            return;
                        }
                        ArrayList<String[]> entries = parseSuggest(b);
                        if (entries.isEmpty()) { searchLocal(key); return; }
                        if (suggestCache.size() > 40) suggestCache.clear();
                        suggestCache.put(key.toLowerCase(), entries);
                        renderOnlineRows(entries);
                    }
                });
            }
        }).start();
    }

    private ArrayList<String[]> parseSuggest(String body) {
        ArrayList<String[]> out = new ArrayList<String[]>();
        try {
            JSONObject o = new JSONObject(body);
            JSONObject data = o.optJSONObject("data");
            JSONArray arr = (data == null) ? null : data.optJSONArray("entries");
            if (arr == null) return out;
            for (int i = 0; i < arr.length() && out.size() < 8; i++) {
                JSONObject e = arr.optJSONObject(i);
                if (e == null) continue;
                String w = e.optString("entry", "").trim();
                if (w.length() == 0) continue;
                String explain = stripHtml(e.optString("explain", ""));
                out.add(new String[]{w, explain, englishFrom(explain, w)});
            }
        } catch (Throwable ignored) { }
        return out;
    }

    private void renderOnlineRows(ArrayList<String[]> entries) {
        if (wSearchResults == null) return;
        wSearchResults.removeAllViews();
        for (int i = 0; i < entries.size(); i++) {
            String[] e = entries.get(i);
            String query = (e.length > 2 && e[2] != null && e[2].length() > 0) ? e[2] : e[0];
            wSearchResults.addView(wordResultRow(e[0], e[1], "有道", null, -1, query), wrapGap(4));
        }
    }

    /** 中文词条点开要查对应的英文：explain 是「abandon; give up; waive」，取第一段。 */
    private static String englishFrom(String explain, String word) {
        if (!isChinese(word)) return word;
        String s = explain == null ? "" : explain;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == ';' || c == '；' || c == ',') { s = s.substring(0, i); break; }
        }
        s = s.replace(" sth", "").replace(" sb", "").trim();
        return s.length() > 0 ? s : word;
    }

    /** 点一条网络结果：本地词书里正好有这个词就直接开本地词条（释义、例句都更全），
     *  没有再去查有道。搜索本身是联网的，点开走哪条路只看谁的信息更全。 */
    private void openOnlineWord(final String word) {
        Hit local = findLocalExact(word);
        if (local != null) { showWordDetail(local.bk, local.line); return; }
        toast("正在查 " + word + "…");
        new Thread(new Runnable() {
            public void run() {
                JSONObject o = null;
                try { o = new JSONObject(httpGetTextDict(SEARCH_DICT + urlEnc(word), 4000, 8000)); }
                catch (Throwable ignored) { o = null; }
                final JSONObject r = o;
                runOnUiThread(new Runnable() {
                    public void run() {
                        if (r == null) { toast("查询失败，检查网络"); return; }
                        if (!showOnlineCard(r, word)) toast("有道也没查到这个词");
                    }
                });
            }
        }).start();
    }

    /** 在已建好的多书索引里找完全一样的词（大小写不敏感）。 */
    private Hit findLocalExact(String word) {
        String low = word.toLowerCase();
        for (int bi = 0; bi < searchIx.size(); bi++) {
            BookIx bk = searchIx.get(bi);
            int[] srt = bk.sorted;
            if (srt == null) continue;
            int lo = 0, hi = srt.length;
            while (lo < hi) {
                int mid = (lo + hi) >>> 1;
                if (bk.words.get(srt[mid]).compareToIgnoreCase(low) < 0) lo = mid + 1;
                else hi = mid;
            }
            if (lo < srt.length && bk.words.get(srt[lo]).equalsIgnoreCase(word))
                return new Hit(bk.words.get(srt[lo]), bk, srt[lo]);
        }
        return null;
    }

    /** 有道 jsonapi → 弹窗要的 词 / 音标 / 释义 / 例句。查不到返回 false。 */
    private boolean showOnlineCard(JSONObject o, String word) {
        String head = word;
        String phone = "";
        ArrayList<String> lines = new ArrayList<String>();
        ArrayList<String[]> sents = new ArrayList<String[]>();

        JSONObject simple = o.optJSONObject("simple");
        JSONArray sw = (simple == null) ? null : simple.optJSONArray("word");
        if (sw != null && sw.length() > 0) {
            JSONObject w0 = sw.optJSONObject(0);
            if (w0 != null) {
                phone = w0.optString("usphone", "");
                if (phone.length() == 0) phone = w0.optString("ukphone", "");
                String h = w0.optString("return-phrase", "");
                if (h.length() > 0) head = h;
            }
        }

        JSONObject ec = o.optJSONObject("ec");
        JSONArray ew = (ec == null) ? null : ec.optJSONArray("word");
        if (ew != null && ew.length() > 0) {
            JSONObject w0 = ew.optJSONObject(0);
            if (w0 != null) {
                if (phone.length() == 0) {
                    phone = w0.optString("usphone", "");
                    if (phone.length() == 0) phone = w0.optString("ukphone", "");
                }
                JSONArray trs = w0.optJSONArray("trs");
                if (trs != null) {
                    for (int i = 0; i < trs.length(); i++) {
                        JSONObject t = trs.optJSONObject(i);
                        if (t == null) continue;
                        JSONArray tr = t.optJSONArray("tr");
                        if (tr == null) continue;
                        for (int k = 0; k < tr.length(); k++) {
                            JSONObject trk = tr.optJSONObject(k);
                            if (trk == null) continue;
                            JSONObject l = trk.optJSONObject("l");
                            JSONArray li = (l == null) ? null : l.optJSONArray("i");
                            if (li == null) continue;
                            for (int m = 0; m < li.length(); m++) {
                                String line = li.optString(m, "");
                                if (line != null && line.length() > 0) lines.add(line);
                            }
                        }
                    }
                }
            }
        }

        JSONObject sp = o.optJSONObject("blng_sents_part");
        JSONArray pair = (sp == null) ? null : sp.optJSONArray("sentence-pair");
        if (pair != null) {
            for (int i = 0; i < pair.length() && sents.size() < 2; i++) {
                JSONObject p = pair.optJSONObject(i);
                if (p == null) continue;
                String en = stripHtml(p.optString("sentence", ""));
                String cn = stripHtml(p.optString("sentence-translation", ""));
                if (en.length() == 0) continue;
                sents.add(new String[]{en, cn});
            }
        }

        if (lines.isEmpty()) {
            // 中文词条：有道只给 fanyi（对应英文），没有 ec
            JSONObject fanyi = o.optJSONObject("fanyi");
            String tran = (fanyi == null) ? "" : fanyi.optString("tran", "");
            if (tran.length() > 0) lines.add(tran.replace("\n", "；"));
        }
        if (lines.isEmpty()) return false;
        showDictCard(head, phone, lines, sents);
        return true;
    }

    /** 联想的 explain 和例句里带 <b> 高亮，弹窗里不认标签，先去掉。 */
    private static String stripHtml(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        boolean tag = false;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '<') tag = true;
            else if (ch == '>') tag = false;
            else if (!tag) sb.append(ch);
        }
        return sb.toString().replace("&quot;", "\"").replace("&amp;", "&")
                .replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ");
    }

    private static String urlEnc(String s) {
        try { return java.net.URLEncoder.encode(s, "UTF-8"); }
        catch (Exception e) { return s; }
    }

    /** 一条结果行：词 + 来源（右侧小字）+ 释义。open 为空时点开走「联网查词」。 */
    private View wordResultRow(final String word, String mean, String from,
                               final BookIx bk, final int line, final String query) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(12), dp(8), dp(12), dp(8));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(C_PILL);
        bg.setCornerRadius(dp(10));
        row.setBackgroundDrawable(bg);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView t1 = new TextView(this);
        t1.setText(word);
        t1.setTextSize(m.backSize - 1f);
        t1.setTextColor(C_PRIMARY);
        t1.setSingleLine(true);
        t1.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams t1lp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        top.addView(t1, t1lp);

        if (from != null && from.length() > 0) {
            TextView t3 = new TextView(this);
            t3.setText(from);
            t3.setTextSize(m.subtitleSize - 3f);
            t3.setTextColor(C_SUB);
            t3.setSingleLine(true);
            t3.setEllipsize(TextUtils.TruncateAt.END);
            t3.setMaxWidth(dp(96));      // 书名字长，别把词头挤没了
            t3.setPadding(dp(6), 0, 0, 0);
            top.addView(t3);
        }
        row.addView(top, wrap());

        if (mean != null && mean.length() > 0) {
            TextView t2 = new TextView(this);
            t2.setText(mean);
            t2.setTextSize(m.subtitleSize - 2f);
            t2.setTextColor(C_SUB);
            t2.setSingleLine(true);
            t2.setEllipsize(TextUtils.TruncateAt.END);
            row.addView(t2);
        }

        row.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                vibrate();
                if (bk != null) { showWordDetail(bk, line); return; }
                if (isChinese(word)) {
                    // 中文词条：一个中文往往对应好几个英文，先让用户挑，再查那个词
                    ArrayList<String> cands = parseCandidates(mean, word);
                    if (cands.size() <= 1) {
                        openOnlineWord(cands.isEmpty() ? word : cands.get(0));
                    } else {
                        showWordPickDialog(cands);
                    }
                } else {
                    openOnlineWord(query == null || query.length() == 0 ? word : query);
                }
            }
        });
        return row;
    }

    /** 中文词条的 explain 是一串英文对应（abandon; give up; waive），拆成候选；只留英文。 */
    private static ArrayList<String> parseCandidates(String explain, String word) {
        ArrayList<String> out = new ArrayList<String>();
        String s = explain == null ? "" : explain;
        for (String part : s.split("[;；,，]")) {
            String c = part.trim();
            if (c.length() == 0 || isChinese(c)) continue;
            c = c.replace(" sth", "").replace(" sb", "").trim();
            if (c.length() > 0 && !out.contains(c)) out.add(c);
        }
        if (out.isEmpty() && word != null && word.length() > 0 && !isChinese(word)) out.add(word);
        return out;
    }

    /** 中文搜词命中多个英文：先弹这个让用户挑一个，点完再查那个词。
     *  版式和「选单词书」弹窗同一套，不自造样式。 */
    private void showWordPickDialog(final List<String> cands) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(12), dp(10), dp(12), dp(8));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF151515);
        bg.setCornerRadius(dp(16));
        box.setBackgroundDrawable(bg);

        final AlertDialog d = new AlertDialog.Builder(this).setView(box).create();

        TextView t = new TextView(this);
        t.setText("选一个单词看释义");
        t.setTextSize(m.subtitleSize);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(Color.WHITE);
        t.setGravity(Gravity.CENTER);
        box.addView(t, wrap());

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < cands.size(); i++) {
            final String w = cands.get(i);
            TextView row = new TextView(this);
            row.setText(w);
            row.setTextSize(m.subtitleSize - 1f);
            row.setTextColor(C_TEXT);
            row.setGravity(Gravity.CENTER);
            row.setSingleLine(true);
            row.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            row.setPadding(dp(8), dp(9), dp(8), dp(9));
            row.setBackgroundDrawable(pill(C_PILL));
            row.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    vibrate();
                    try { d.dismiss(); } catch (Throwable ignored) { }
                    openOnlineWord(w);
                }
            });
            LinearLayout.LayoutParams lp = wrap();
            lp.bottomMargin = dp(4);
            list.addView(row, lp);
        }

        ScrollView sv = new OverScrollScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.addView(list, wrap());
        tuneDialogScroll(sv, list);
        final int pickMaxH = m.round ? dp(150) : dp(240);
        final LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, pickMaxH);
        slp.topMargin = dp(4);
        box.addView(sv, slp);
        d.setOnShowListener(new DialogInterface.OnShowListener() {
            public void onShow(DialogInterface dlg) {
                list.post(new Runnable() {
                    public void run() {
                        int h = list.getHeight();
                        if (h > 0 && h < pickMaxH) {
                            slp.height = h + dp(2);
                            sv.setLayoutParams(slp);
                        }
                    }
                });
            }
        });

        TextView no = new TextView(this);
        no.setText("取消");
        no.setTextSize(m.subtitleSize - 1f);
        no.setGravity(Gravity.CENTER);
        no.setTextColor(C_SUB);
        no.setPadding(dp(8), dp(9), dp(8), dp(4));
        no.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                vibrate();
                try { d.dismiss(); } catch (Throwable ignored) { }
            }
        });
        box.addView(no, wrap());

        tuneDialogWindow(d);   // 必须 show() 之前：否则窗口按不透明格式建，滚动会留残影
        d.show();
        sizeDialog(d, 0.9f);
    }

    /** 直接开一条已读出来的词条 JSON。 */
    private String meaningAt(java.io.RandomAccessFile rf, BookIx bk, int line) {
        try {
            byte[] b = new byte[bk.len[line]];
            rf.seek(bk.off[line]);
            rf.readFully(b);
            return meaningOf(new JSONObject(new String(b, "UTF-8").trim()));
        } catch (Throwable e) {
            return "";
        }
    }

    /** 前两个词性的释义拼一行。 */
    private String meaningOf(JSONObject o) {
        JSONObject w = (o == null) ? null : o.optJSONObject("content");
        JSONObject wd = (w == null) ? null : w.optJSONObject("word");
        JSONObject cc = (wd == null) ? null : wd.optJSONObject("content");
        JSONArray tr = (cc == null) ? null : cc.optJSONArray("trans");
        if (tr == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < tr.length() && i < 2; i++) {
            JSONObject t = tr.optJSONObject(i);
            if (t == null) continue;
            if (sb.length() > 0) sb.append("；");
            String pos = t.optString("pos", "");
            if (pos.length() > 0) sb.append(pos).append(" ");
            sb.append(t.optString("tranCn", ""));
        }
        return sb.toString();
    }

    /** 读指定词书第 i 个词的完整 JSON（按偏移 seek 一行）。 */
    private JSONObject readWordLine(BookIx bk, int i) {
        try {
            java.io.RandomAccessFile rf = new java.io.RandomAccessFile(bookJson(bk.id), "r");
            try {
                byte[] b = new byte[bk.len[i]];
                rf.seek(bk.off[i]);
                rf.readFully(b);
                return new JSONObject(new String(b, "UTF-8").trim());
            } finally {
                rf.close();
            }
        } catch (Exception e) {
            return null;
        }
    }

    /** 点结果看完整释义（弹窗，不跳页，保留搜索词）。 */
    /** 词条详情。和「选单词书」「词书操作」同一套弹窗：
     *  以前这里直接 new AlertDialog().setPositiveButton("关闭")，出来的是系统默认样式，
     *  圆角、边框、按钮位置全跟 App 不一样，看着很突兀。 */
    private void showWordDetail(int idx) {
        JSONObject o = readWordLine(idx);
        if (o == null) { toast("读取失败"); return; }
        JSONObject w = o.optJSONObject("content");
        if (w != null) w = w.optJSONObject("word");
        if (w == null) { toast("词条格式不对"); return; }
        showWordDetail(w);
    }

    /** 指定词书 + 行号的词条：搜词结果可能来自别的书，不能走当前书的 wOff / wLen。 */
    private void showWordDetail(BookIx bk, int line) {
        JSONObject o = readWordLine(bk, line);
        if (o == null) { toast("读取失败"); return; }
        JSONObject w = o.optJSONObject("content");
        if (w != null) w = w.optJSONObject("word");
        if (w == null) { toast("词条格式不对"); return; }
        showWordDetail(w);
    }

    /** 把一本书里的一条词条 JSON 拆成弹窗要的三样东西，再交给 showDictCard。 */
    private void showWordDetail(JSONObject w) {
        final JSONObject cc = w.optJSONObject("content");
        final String head = w.optString("wordHead", "");
        String phone = (cc == null) ? "" : cc.optString("usphone", "");
        if (phone.length() == 0 && cc != null) phone = cc.optString("ukphone", "");

        ArrayList<String> lines = new ArrayList<String>();
        JSONArray tr = (cc == null) ? null : cc.optJSONArray("trans");
        if (tr != null) {
            for (int i = 0; i < tr.length(); i++) {
                JSONObject t = tr.optJSONObject(i);
                if (t == null) continue;
                String pos = t.optString("pos", "");
                lines.add((pos.length() > 0 ? pos + "  " : "") + t.optString("tranCn", ""));
            }
        }
        ArrayList<String[]> sents = new ArrayList<String[]>();
        JSONObject sen = (cc == null) ? null : cc.optJSONObject("sentence");
        JSONArray ss = (sen == null) ? null : sen.optJSONArray("sentences");
        if (ss != null) {
            for (int i = 0; i < ss.length() && i < 2; i++) {
                JSONObject s0 = ss.optJSONObject(i);
                if (s0 == null) continue;
                String en = s0.optString("sContent", "");
                if (en.length() == 0) continue;
                sents.add(new String[]{en, s0.optString("sCn", "")});
            }
        }
        showDictCard(head, phone, lines, sents);
    }

    /** 词条弹窗：本地词书和联网查词共用这一套版式（不另写一份）。
     *  lines 每行一条释义（已带词性前缀），sents 是 {英文, 中文} 对，最多两条。 */
    private void showDictCard(String head, String phone, List<String> lines, List<String[]> sents) {
        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setPadding(dp(12), dp(10), dp(12), dp(8));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF151515);
        bg.setCornerRadius(dp(16));
        outer.setBackgroundDrawable(bg);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(2), 0, dp(2), 0);

        // 词 + 音标 + 发音
        TextView tvHead = new TextView(this);
        tvHead.setText(head);
        tvHead.setTextSize(m.backSize);
        tvHead.setTypeface(Typeface.DEFAULT_BOLD);
        tvHead.setTextColor(Color.WHITE);
        content.addView(tvHead, wrap());

        LinearLayout phRow = new LinearLayout(this);
        phRow.setOrientation(LinearLayout.HORIZONTAL);
        phRow.setGravity(Gravity.CENTER_VERTICAL);
        if (phone != null && phone.length() > 0) {
            TextView ph = new TextView(this);
            ph.setText("/" + phone + "/");
            ph.setTextSize(m.subtitleSize - 1f);
            ph.setTextColor(C_SUB);
            ph.setPadding(0, dp(1), dp(8), 0);
            phRow.addView(ph);
        }
        if (!muteOn()) {
            TextView spk = new TextView(this);
            spk.setPadding(dp(8), dp(6), dp(8), dp(6));
            spk.setBackground(clickBg(0xFF14384A));
            applyIconOnly(spk, "ic_volume", C_PRIMARY, 14);
            spk.setContentDescription("播放发音");
            spk.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    vibrate();
                    speakWord(head);
                }
            });
            phRow.addView(spk);
        }
        if (phRow.getChildCount() > 0) {
            LinearLayout.LayoutParams plp = wrap();
            plp.topMargin = dp(2);
            content.addView(phRow, plp);
        }

        // 释义
        if (lines != null) {
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                if (line == null || line.length() == 0) continue;
                TextView tv = new TextView(this);
                tv.setText(line);
                tv.setTextSize(m.subtitleSize - 1f);
                tv.setTextColor(C_TEXT);
                tv.setPadding(0, dp(5), 0, 0);
                content.addView(tv, wrap());
            }
        }

        // 例句（最多两条，多了弹窗太长）
        if (sents != null) {
            for (int i = 0; i < sents.size() && i < 2; i++) {
                String[] p = sents.get(i);
                if (p == null || p[0] == null || p[0].length() == 0) continue;
                TextView e1 = new TextView(this);
                e1.setText(p[0]);
                e1.setTextSize(m.subtitleSize - 2f);
                e1.setTextColor(0xFFB0BEC5);
                e1.setPadding(0, dp(8), 0, 0);
                content.addView(e1, wrap());
                if (p.length > 1 && p[1] != null && p[1].length() > 0) {
                    TextView e2 = new TextView(this);
                    e2.setText(p[1]);
                    e2.setTextSize(m.subtitleSize - 3f);
                    e2.setTextColor(C_SUB);
                    content.addView(e2, wrap());
                }
            }
        }

        final ScrollView sv = new OverScrollScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.addView(content, wrap());
        tuneDialogScroll(sv, content);       // 滚动时整块重绘，否则会留残影

        final int maxH = m.round ? dp(210) : dp(300);
        final LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, maxH);
        outer.addView(sv, slp);

        TextView close = new TextView(this);
        close.setText("关闭");
        close.setTextSize(m.subtitleSize - 1f);
        close.setGravity(Gravity.CENTER);
        close.setTextColor(C_SUB);
        close.setPadding(dp(8), dp(9), dp(8), dp(4));
        outer.addView(close, wrap());

        final AlertDialog d = new AlertDialog.Builder(this).setView(outer).create();
        close.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                vibrate();
                try { d.dismiss(); } catch (Throwable ignored) { }
            }
        });

        // 词条短的时候按内容收高，别留一大片空
        d.setOnShowListener(new DialogInterface.OnShowListener() {
            public void onShow(DialogInterface dlg) {
                content.post(new Runnable() {
                    public void run() {
                        int h = content.getHeight();
                        if (h > 0 && h < maxH) {
                            slp.height = h + dp(2);
                            sv.setLayoutParams(slp);
                        }
                    }
                });
            }
        });

        tuneDialogWindow(d);   // 必须 show() 之前
        d.show();
        sizeDialog(d, 0.9f);
    }

    private void renderOrder() {
        int keepY = (orderInner.getChildCount() > 0) ? orderScroll.getScrollY() : -1;
        orderInner.removeAllViews();
        orderInner.addView(orderBackBtn, wrapM());
        orderInner.addView(pageTitle("首页科目排序"), wrap());
        orderInner.addView(grayTip("点 ▲▼ 调顺序，改完主页立即生效。"), wrap());

        // 9 行连成一块：外层一个圆角底，行自己不画圆角、不留缝，行间只加一条细分隔线
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundDrawable(pill(C_CARD));
        for (int idx = 0; idx < orderKeys.size(); idx++) {
            if (idx > 0) box.addView(orderDivider());
            box.addView(orderRow(idx));
        }
        LinearLayout.LayoutParams blp = wrap();
        blp.topMargin = dp(6);
        orderInner.addView(box, blp);
        padInner(orderInner);      // 圆屏上下各留 25% 屏高：不放的话最后一行滑不到屏幕中间，点不着
        if (keepY >= 0) scrollToY(orderScroll, keepY);   // 上下挪动后保住位置，不弹回顶端
        else scrollToTop(orderScroll);
    }

    /** 连排列表里的分隔线：1 像素，左右留缩进，比卡片底色亮一档。 */
    private View orderDivider() {
        View v = new View(this);
        v.setBackgroundColor(0xFF242424);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, dp(1) / 2));
        lp.leftMargin = dp(10);
        lp.rightMargin = dp(10);
        v.setLayoutParams(lp);
        return v;
    }

    private float curTitleSize() {
        return titleSizeSp > 0 ? titleSizeSp : (m.round ? 15f : 17f);
    }

    private float curSubSize() {
        return subSizeSp > 0 ? subSizeSp : (m.round ? 11f : 12f);
    }

    private void saveDisplaySettings() {
        prefs.edit()
                .putFloat("font_size", fontSizeSp)
                .putFloat("list_size", listSizeSp)
                .putFloat("title_size", titleSizeSp)
                .putFloat("sub_size", subSizeSp)
                .putFloat("ui_scale", uiScale)
                .apply();
    }

    /**
     * 保存显示设置并重建整个界面。
     * 字号与整体缩放是在建界面时算进控件的（字号走 Metrics 快照、尺寸走 dp()），
     * 光改变量不重建，界面看起来就完全没变——所以必须重建。
     * 重建后回到 backTo 指定的页面，别把用户甩回主页。
     */
    private void applyDisplayChange(int backTo) {
        saveDisplaySettings();
        // 字号、尺寸是「建界面时算进控件」的，必须重建视图树；但**不要 recreate()**：
        // 重建 Activity 会重新走一遍窗口创建，窗口背景先露出开屏图、再走一次启动流程，
        // 用户看到的就是「调完显示退出来闪一下黑屏」（踩过）。
        // 就地重建：数据、导航状态、收藏都在内存里，不重读 JSON，直接换一棵 View 树。
        m = new Metrics();          // 字号/缩放是快照，改完必须重新取
        rebuildUi(backTo);
    }

    /** 就地重建整个界面（不销毁 Activity）：几毫秒完成，屏幕不会黑。 */
    private void rebuildUi(final int backTo) {
        buildUi();                  // 内部 setContentView(root)，旧树被替换
        resetLazyContentMarks();    // 新树里的列表容器都是空的，统一把「已填充」标记打回
        showScreen(backTo);         // 切页动画自带淡入，这里不再叠加第二层
    }

    /**
     * 视图树整个换掉后，所有「内容已填充 / 已拉取」的标记都必须作废，否则新容器一直是空的。
     * 以前这些标记散在 rebuildUi 里各写一行，漏一个就出现某个页面空白
     * （问 AI 聊天页不跟随显示大小，就是漏了 chatDirty）。所有同类标记一律加到这里。
     */
    private void resetLazyContentMarks() {
        subjectDirty = true;        // 主页科目列表
        lastQuery = null;           // 全局搜索：强制下次进搜索页重新刷新
        mineDirty = true;           // 我的反馈：强制重新拉取
        chatDirty = true;           // 问 AI 对话：强制重绘气泡（字号随 m 变化）
        thanksLoaded = false;       // 关于页感谢名单
        changelogFilled = false;    // 更新日志页
        todayBuiltFor = "";         // 今日新增：按日期重建数据
        // aiWebLoaded 不用管：buildAiWebScreen() 开头自己设 false
    }

    /** 一行滑块：上方标签与当前值，下方 SeekBar。 */
    private View sliderRow(String label, float value, float min, float max, float step,
                           String unit, OnFloatChange onChange) {
        return sliderRow(label, value, min, max, step, unit, onChange, null);
    }

    private View sliderRow(String label, float value, float min, float max, float step,
                           final String unit, final OnFloatChange onChange,
                           final OnFloatRelease onRelease) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(10), dp(7), dp(10), dp(3));
        box.setBackgroundDrawable(pill(C_CARD));
        LinearLayout.LayoutParams blp = wrap();
        blp.bottomMargin = dp(5);
        box.setLayoutParams(blp);   // 必须挂到自身上：调用方若再传 wrap() 就会把这个间距覆盖掉

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView name = new TextView(this);
        name.setText(label);
        name.setTextSize(m.backSize);
        name.setTextColor(C_TEXT);
        name.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        final TextView val = new TextView(this);
        val.setText(shownValue(value, unit));
        val.setTextSize(m.backSize);
        val.setTypeface(Typeface.DEFAULT_BOLD);
        val.setTextColor(C_PRIMARY);

        top.addView(name);
        top.addView(val);
        box.addView(top, wrap());

        final SeekBar sb = new SeekBar(this);
        int steps = Math.max(1, Math.round((max - min) / step));
        sb.setMax(steps);
        sb.setProgress(Math.round((value - min) / step));
        try {
            sb.setProgressTintList(ColorStateList.valueOf(C_PRIMARY));
            sb.setThumbTintList(ColorStateList.valueOf(C_PRIMARY));
            sb.setProgressBackgroundTintList(ColorStateList.valueOf(0xFF2E2E2E));
        } catch (Exception ignored) { }
        // 左右各留出半个 thumb 的宽度：SeekBar 默认把圆点画在控件边界上，
        // padding 为 0 时滑到最左/最右，圆点会有一半被它自己的边界裁掉（看着像被卡片边缘切了）。
        int thumbPad = dp(9);
        sb.setPadding(thumbPad, 0, thumbPad, 0);
        final boolean[] released = {false};
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            float cur = value;
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                float v = min + progress * step;
                cur = v;
                val.setText(shownValue(v, unit));
                onChange.onChanged(v);
                released[0] = false;
            }
            public void onStartTrackingTouch(SeekBar bar) {
                sliderTouching = true;
                released[0] = false;
            }
            public void onStopTrackingTouch(SeekBar bar) {
                sliderTouching = false;
                released[0] = true;
                vibrate();
                if (onRelease != null) onRelease.onReleased(cur);
            }
        });
        // 有些 ROM / 手表上「点一下档位」不会走 onStopTrackingTouch，只发 ACTION_UP。
        // 兜底 post 到下一帧再判断：onTouchListener 比 onStopTrackingTouch 先执行，
        // 延后一帧才能让「正常松手已提交」的情况先置上 released，避免重建两次。
        sb.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View v, MotionEvent ev) {
                if (ev.getActionMasked() == MotionEvent.ACTION_UP) {
                    final SeekBar bar = sb;
                    bar.post(new Runnable() {
                        public void run() {
                            // Activity 已经销毁就别再重建界面（否则会在 finishing 的窗口上 setContentView）
                            if (!alive() || released[0]) return;
                            released[0] = true;
                            sliderTouching = false;
                            if (onRelease != null) onRelease.onReleased(min + bar.getProgress() * step);
                        }
                    });
                }
                return false;   // 不消费，SeekBar 自己的拖动逻辑照常
            }
        });
        box.addView(sb, wrap());
        return box;
    }

    /** 显示大小的档位：用户看得懂的名字，而不是 0.85~1.40 这种裸数字。
     *  底层仍用 uiScale 存（键不变，老用户设置不丢）。 */
    private static final float[] DISPLAY_STEPS = {0.85f, 0.925f, 1.0f, 1.10f, 1.20f, 1.30f, 1.40f};
    private static final String[] DISPLAY_NAMES = {"很小", "小", "标准", "大", "较大", "特大", "最大"};

    /** 把任意 uiScale 归到最近档位（老版本存的可能是 1.07 这种中间值）。 */
    private int displayStepIndex(float k) {
        int best = 0;
        float d = Float.MAX_VALUE;
        for (int i = 0; i < DISPLAY_STEPS.length; i++) {
            float diff = Math.abs(DISPLAY_STEPS[i] - k);
            if (diff < d) { d = diff; best = i; }
        }
        return best;
    }

    private String shownValue(float v, String unit) {
        if ("%".equals(unit)) return Math.round(v * 100) + "%";
        if ("档".equals(unit)) {
            int i = Math.round(v);
            if (i < 0) i = 0;
            if (i >= DISPLAY_NAMES.length) i = DISPLAY_NAMES.length - 1;
            return DISPLAY_NAMES[i] + " · " + Math.round(DISPLAY_STEPS[i] * 100) + "%";
        }
        return Math.round(v) + unit;
    }

    private void renderDisplayScreen() {
        if (displayInner == null) return;
        displayInner.removeAllViews();

        if (!tuningFirstRun) {
            displayInner.addView(makeBack("‹ 返回设置", new View.OnClickListener() {
                public void onClick(View v) { applyDisplayChange(S_SETTINGS); }
            }), wrapM());
        }

        TextView title = new TextView(this);
        title.setText(tuningFirstRun ? "先调好显示" : "显示调节");
        title.setTextSize(m.titleSize);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(C_PRIMARY);
        title.setGravity(Gravity.CENTER);
        displayInner.addView(title, wrap());

        TextView tip = new TextView(this);
        tip.setText(tuningFirstRun
                ? "第一次使用，选一个看着舒服的大小就行，之后随时能在设置里改。松手立即生效。"
                : "选一个档位，文字、按钮、间距会一起按新比例重新排版，松手立即生效。");
        tip.setTextSize(Math.max(9f, m.subtitleSize - 1f));
        tip.setTextColor(C_SUB);
        tip.setGravity(Gravity.CENTER);
        tip.setPadding(dp(6), dp(3), dp(6), dp(8));
        displayInner.addView(tip, wrap());

        // ---- 主区：只有一个「显示大小」档位滑块 ----
        displayInner.addView(sectionHeader("显示大小"), wrap());
        int steps = DISPLAY_STEPS.length - 1;
        displayInner.addView(sliderRow("显示大小", displayStepIndex(uiScale), 0, steps, 1, "档",
                new OnFloatChange() {
                    public void onChanged(float v) {
                        int i = Math.round(v);
                        if (i < 0) i = 0;
                        if (i >= DISPLAY_STEPS.length) i = DISPLAY_STEPS.length - 1;
                        uiScale = DISPLAY_STEPS[i];
                    }
                }, new OnFloatRelease() {
                    public void onReleased(float v) {
                        applyDisplayChange(S_DISPLAY);   // 松手就地重建，整页按新比例重排
                    }
                }));
        displayInner.addView(aiHint("调大后文字、按钮、间距、图标一起变大，适合手指粗或看不清的人；"
                + "表盘小的机型建议别超过「较大」。"), wrap());

        // ---- 高级：单独调四类字号，默认收起 ----
        final LinearLayout advBox = new LinearLayout(this);
        advBox.setOrientation(LinearLayout.VERTICAL);
        final TextView advBtn = new TextView(this);
        advBtn.setText(displayAdvancedOpen ? "▾ 高级：分别调整字号" : "▸ 高级：分别调整字号");
        advBtn.setTextSize(m.backSize);
        advBtn.setTextColor(C_SUB);
        advBtn.setGravity(Gravity.CENTER);
        advBtn.setPadding(dp(10), dp(10), dp(10), dp(10));
        advBtn.setBackground(clickBg(C_PILL));
        advBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                vibrate();
                displayAdvancedOpen = !displayAdvancedOpen;
                advBtn.setText(displayAdvancedOpen ? "▾ 高级：分别调整字号" : "▸ 高级：分别调整字号");
                advBox.setVisibility(displayAdvancedOpen ? View.VISIBLE : View.GONE);
            }
        });
        LinearLayout.LayoutParams advLp = wrap();
        advLp.topMargin = dp(10);
        displayInner.addView(advBtn, advLp);
        advBox.setVisibility(displayAdvancedOpen ? View.VISIBLE : View.GONE);
        displayInner.addView(advBox, wrap());

        advBox.addView(aiHint("一般不用动这里。只有当你觉得某一类文字单独偏大/偏小时才调。"), wrap());
        advBox.addView(sliderRow("正文字号", fontSizeSp, 11, 22, 1, "sp",
                new OnFloatChange() {
                    public void onChanged(float v) { fontSizeSp = v; }
                }, new OnFloatRelease() {
                    public void onReleased(float v) { applyDisplayChange(S_DISPLAY); }
                }));
        advBox.addView(sliderRow("列表字号", listSizeSp, 12, 20, 1, "sp",
                new OnFloatChange() {
                    public void onChanged(float v) { listSizeSp = v; }
                }, new OnFloatRelease() {
                    public void onReleased(float v) { applyDisplayChange(S_DISPLAY); }
                }));
        advBox.addView(sliderRow("标题字号", curTitleSize(), 13, 26, 1, "sp",
                new OnFloatChange() {
                    public void onChanged(float v) { titleSizeSp = v; }
                }, new OnFloatRelease() {
                    public void onReleased(float v) { applyDisplayChange(S_DISPLAY); }
                }));
        advBox.addView(sliderRow("说明小字", curSubSize(), 9, 16, 1, "sp",
                new OnFloatChange() {
                    public void onChanged(float v) { subSizeSp = v; }
                }, new OnFloatRelease() {
                    public void onReleased(float v) { applyDisplayChange(S_DISPLAY); }
                }));

        // ---- 网页版单独说明：它是第三方网页，显示大小管不到 ----
        displayInner.addView(aiHint("问 AI 的网页版是第三方网页，字号由网页自己控制；"
                + "它的缩放和暗色在「问 AI → 网页版设置」里单独调。"), wrap());

        // ---- 底部按钮 ----
        TextView done = new TextView(this);
        done.setText(tuningFirstRun ? "完成，开始使用" : "完成");
        done.setTextSize(m.backSize);
        done.setTypeface(Typeface.DEFAULT_BOLD);
        done.setGravity(Gravity.CENTER);
        done.setTextColor(C_PRIMARY);
        done.setPadding(dp(10), dp(11), dp(10), dp(11));
        done.setBackground(clickBg(0xFF14384A));
        done.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                vibrate();
                prefs.edit().putBoolean("display_tuned", true).apply();
                tuningFirstRun = false;
                applyDisplayChange(S_SETTINGS);
            }
        });
        LinearLayout.LayoutParams doneLp = wrap();
        doneLp.topMargin = dp(12);
        displayInner.addView(done, doneLp);

        // 首启可跳过：直接按默认档位进主页，不强制用户先调一通
        if (tuningFirstRun) {
            TextView skip = new TextView(this);
            skip.setText("跳过，用默认大小");
            skip.setTextSize(Math.max(9f, m.subtitleSize - 1f));
            skip.setGravity(Gravity.CENTER);
            skip.setTextColor(C_SUB);
            skip.setPadding(dp(10), dp(10), dp(10), dp(10));
            skip.setBackground(clickBg(C_PILL));
            skip.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    vibrate();
                    fontSizeSp = m.round ? 13f : 14f;
                    listSizeSp = m.round ? 14f : 15f;
                    titleSizeSp = 0f;
                    subSizeSp = 0f;
                    uiScale = 1f;
                    prefs.edit().putBoolean("display_tuned", true).apply();
                    tuningFirstRun = false;
                    applyDisplayChange(S_SETTINGS);
                }
            });
            LinearLayout.LayoutParams skipLp = wrap();
            skipLp.topMargin = dp(5);
            displayInner.addView(skip, skipLp);
        }

        TextView reset = new TextView(this);
        reset.setText("恢复默认");
        reset.setTextSize(Math.max(9f, m.subtitleSize - 1f));
        reset.setGravity(Gravity.CENTER);
        reset.setTextColor(C_SUB);
        reset.setPadding(dp(10), dp(8), dp(10), dp(8));
        reset.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                vibrate();
                fontSizeSp = m.round ? 13f : 14f;
                listSizeSp = m.round ? 14f : 15f;
                titleSizeSp = 0f;
                subSizeSp = 0f;
                uiScale = 1f;
                saveDisplaySettings();
                m = new Metrics();
                applyDisplayChange(S_DISPLAY);
            }
        });
        LinearLayout.LayoutParams resetLp = wrap();
        resetLp.topMargin = dp(4);
        displayInner.addView(reset, resetLp);
        padInner(displayInner);
        scrollToTop(displayScroll);
    }

    // ================= 更新日志 =================
    private void buildChangelogScreen() {
        changelogScroll = new OverScrollScrollView(this);
        changelogScroll.setVerticalScrollBarEnabled(false);
        roundPadScroll(changelogScroll);
        changelogInner = new LinearLayout(this);
        changelogInner.setOrientation(LinearLayout.VERTICAL);
        changelogScroll.addView(changelogInner);
        changelogScreen.addView(changelogScroll, lw());

        // 滑到底自动续一页，省得每 5 个版本点一次；changelogBusy 挡住同一帧的重复触发
        changelogScroll.setOnScrollChangeListener(new android.view.View.OnScrollChangeListener() {
            public void onScrollChange(View v, int sx, int sy, int ox, int oy) {
                if (changelogBusy || changelogArr == null) return;
                if (changelogShown >= changelogArr.length()) return;
                View child = changelogScroll.getChildAt(0);
                if (child == null) return;
                if (sy + changelogScroll.getHeight() >= child.getHeight() - dp(120)) {
                    appendChangelog();
                }
            }
        });

        changelogInner.addView(makeBack("‹ 返回", new View.OnClickListener() {
            public void onClick(View v) { goBack(backTarget()); }
        }), wrapM());

        TextView title = new TextView(this);
        title.setText("更新日志");
        title.setTextSize(m.titleSize);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(C_PRIMARY);
        title.setGravity(Gravity.CENTER);
        changelogInner.addView(title, wrap());

        changelogSub = new TextView(this);
        changelogSub.setTextSize(m.subtitleSize - 1f);
        changelogSub.setTextColor(C_SUB);
        changelogSub.setGravity(Gravity.CENTER);
        changelogSub.setPadding(dp(4), dp(2), dp(4), dp(8));
        changelogInner.addView(changelogSub, wrap());

        changelogList = new LinearLayout(this);
        changelogList.setOrientation(LinearLayout.VERTICAL);
        changelogInner.addView(changelogList, wrap());
        padInner(changelogInner);
    }

    private JSONArray changelogArr;
    private int changelogShown;
    private boolean changelogBusy;
    private TextView changelogMore;
    /** 一次只渲染这么多版。184 个版本一次性建 View，打开就是几秒的卡。 */
    private static final int CHANGELOG_PAGE = 5;

    /** 读取 assets/changelog.json，按版本倒序渲染；先出最近几版，其余滑到底或点按钮再加载。 */
    private void fillChangelog() {
        changelogFilled = true;
        changelogList.removeAllViews();
        // 改字号会整树重建再填一次，旧的「查看更多」得先摘掉，否则会叠出两三个
        if (changelogMore != null && changelogMore.getParent() == changelogInner) {
            changelogInner.removeView(changelogMore);
        }
        changelogMore = null;
        String cur = appVersion();
        JSONArray arr = null;
        try {
            arr = readJsonAsset("changelog.json").optJSONArray("log");
        } catch (Exception ignored) { }
        if (arr == null || arr.length() == 0) {
            changelogSub.setText("当前版本 v" + cur);
            TextView empty = new TextView(this);
            empty.setText("暂无更新记录");
            empty.setTextSize(m.subtitleSize);
            empty.setTextColor(C_SUB);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(4), dp(12), dp(4), dp(12));
            changelogList.addView(empty, wrap());
            return;
        }
        changelogArr = arr;
        changelogShown = 0;
        changelogSub.setText("当前 v" + cur + " · 共记录 " + arr.length() + " 个版本");

        // 「查看更多」放在列表后面，appendChangelog 只往 changelogList 里加卡片
        changelogMore = new TextView(this);
        changelogMore.setTextSize(m.subtitleSize - 2f);
        changelogMore.setTextColor(C_PRIMARY);
        changelogMore.setGravity(Gravity.CENTER);
        changelogMore.setPadding(dp(8), dp(10), dp(8), dp(10));
        changelogMore.setBackgroundDrawable(clickBg(C_PILL));
        changelogMore.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                vibrate();
                appendChangelog();
            }
        });
        changelogInner.addView(changelogMore, wrap());

        appendChangelog();
    }

    /** 追加一页。滚动监听可能连发多次，changelogBusy 挡住同一帧里的重复请求。 */
    private void appendChangelog() {
        if (changelogArr == null || changelogBusy) return;
        if (changelogShown >= changelogArr.length()) return;
        changelogBusy = true;

        String cur = appVersion();
        int end = Math.min(changelogShown + CHANGELOG_PAGE, changelogArr.length());
        for (int i = changelogShown; i < end; i++) {
            JSONObject o = changelogArr.optJSONObject(i);
            if (o == null) continue;
            String v = o.optString("v", "");
            String d = o.optString("d", "");
            boolean isCur = v.equals(cur);

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(10), dp(8), dp(10), dp(9));
            card.setBackgroundDrawable(pill(isCur ? 0xFF14384A : C_CARD));

            LinearLayout head = new LinearLayout(this);
            head.setOrientation(LinearLayout.HORIZONTAL);
            head.setGravity(Gravity.CENTER_VERTICAL);

            TextView ver = new TextView(this);
            ver.setText("v" + v);
            ver.setTextSize(m.subtitleSize + 0.5f);
            ver.setTypeface(Typeface.DEFAULT_BOLD);
            ver.setTextColor(isCur ? C_PRIMARY : C_TEXT);
            head.addView(ver, new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            TextView date = new TextView(this);
            date.setText(isCur ? ("当前 · " + d) : d);
            date.setTextSize(m.subtitleSize - 2f);
            date.setTextColor(C_SUB);
            head.addView(date, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            card.addView(head, wrap());

            JSONArray items = o.optJSONArray("items");
            if (items != null) {
                for (int j = 0; j < items.length(); j++) {
                    String line = items.optString(j, "");
                    if (line.length() == 0) continue;
                    TextView item = new TextView(this);
                    item.setText("· " + line);
                    item.setTextSize(m.subtitleSize - 1f);
                    item.setTextColor(C_TEXT);
                    item.setLineSpacing(dp(2), 1.18f);
                    item.setPadding(dp(2), dp(4), dp(2), 0);
                    card.addView(item, wrap());
                }
            }

            LinearLayout.LayoutParams clp = wrap();
            clp.bottomMargin = dp(6);
            changelogList.addView(card, clp);
        }
        changelogShown = end;

        if (changelogMore != null) {
            int left = changelogArr.length() - changelogShown;
            if (left <= 0) {
                changelogMore.setVisibility(View.GONE);
            } else {
                changelogMore.setText("查看更多旧版本（还有 " + left + " 个）");
                changelogMore.setVisibility(View.VISIBLE);
            }
        }
        changelogList.post(new Runnable() {
            public void run() { changelogBusy = false; }
        });
    }

    private void buildFeedbackScreen() {
        feedbackScroll = new OverScrollScrollView(this);
        feedbackScroll.setVerticalScrollBarEnabled(false);
        roundPadScroll(feedbackScroll);
        feedbackInner = new LinearLayout(this);
        feedbackInner.setOrientation(LinearLayout.VERTICAL);
        feedbackScroll.addView(feedbackInner);
        feedbackScreen.addView(feedbackScroll, lw());

        feedbackInner.addView(makeBack("‹ 返回设置", new View.OnClickListener() {
            public void onClick(View v) { goBack(S_SETTINGS); }
        }), wrapM());

        TextView title = new TextView(this);
        title.setText("反馈与建议");
        title.setTextSize(m.titleSize);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(C_PRIMARY);
        title.setGravity(Gravity.CENTER);
        feedbackInner.addView(title, wrap());

        fbStatusTxt = new TextView(this);
        fbStatusTxt.setTextSize(m.subtitleSize - 1f);
        fbStatusTxt.setTextColor(C_PRIMARY);
        fbStatusTxt.setGravity(Gravity.CENTER);
        fbStatusTxt.setPadding(dp(6), dp(8), dp(6), dp(8));
        fbStatusTxt.setBackgroundDrawable(pill(C_PILL));
        fbStatusTxt.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                vibrate();
                if (ghToken == null) showScreen(S_PROFILE);
            }
        });
        LinearLayout.LayoutParams stLp = wrap();
        stLp.topMargin = dp(4);
        feedbackInner.addView(fbStatusTxt, stLp);

        feedbackInner.addView(sectionHeader("类型"), wrap());
        // 3 列排 2 行：2 列排 3 行的话最后一行会被屏幕下缘/圆弧裁掉，点不到
        final int fbCols = 3;
        for (int row = 0; row * fbCols < FB_TYPES.length; row++) {
            LinearLayout line = new LinearLayout(this);
            line.setOrientation(LinearLayout.HORIZONTAL);
            int cols = Math.min(fbCols, FB_TYPES.length - row * fbCols);
            for (int col = 0; col < cols; col++) {
                final int idx = row * fbCols + col;
                TextView b = new TextView(this);
                b.setText(FB_TYPES[idx]);
                b.setTextSize(Math.max(9f, m.subtitleSize - 2f));
                b.setGravity(Gravity.CENTER);
                b.setPadding(dp(3), dp(9), dp(3), dp(9));
                b.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        vibrate();
                        fbType = idx;
                        updateTypeButtons();
                    }
                });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                        LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                if (col > 0) lp.leftMargin = dp(3);
                if (col < cols - 1) lp.rightMargin = dp(3);
                line.addView(b, lp);
                fbTypeBtns[idx] = b;
            }
            LinearLayout.LayoutParams llp = wrap();
            llp.bottomMargin = dp(4);
            feedbackInner.addView(line, llp);
        }

        // 类型说明：跟着当前选中的类型变，用户不用猜哪个合适
        fbTypeHint = new TextView(this);
        fbTypeHint.setTextSize(Math.max(9f, m.subtitleSize - 2f));
        fbTypeHint.setTextColor(C_SUB);
        fbTypeHint.setGravity(Gravity.CENTER);
        fbTypeHint.setPadding(dp(6), dp(4), dp(6), dp(6));
        LinearLayout.LayoutParams thp = wrap();
        thp.bottomMargin = dp(2);
        feedbackInner.addView(fbTypeHint, thp);

        feedbackInner.addView(sectionHeader("描述"), wrap());
        fbDesc = new EditText(this);
        fbDesc.setHint("哪一节、哪句话有问题，或想要什么功能…");
        fbDesc.setTextSize(m.subtitleSize - 1f);
        fbDesc.setTextColor(Color.WHITE);
        fbDesc.setHintTextColor(C_SUB);
        fbDesc.setBackgroundDrawable(pill(C_PILL));
        fbDesc.setPadding(dp(10), dp(8), dp(10), dp(8));
        fbDesc.setMinLines(3);
        fbDesc.setGravity(Gravity.TOP | Gravity.START);
        fbDesc.setText(fbDraft);
        fbDesc.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            public void onTextChanged(CharSequence s, int a, int b, int c) { }
            public void afterTextChanged(Editable s) { fbDraft = s.toString(); }
        });
        feedbackInner.addView(fbDesc, wrap());

        // 附带日志开关：只在日志开启时可开，方便排查闪退类问题
        final TextView attachBtn = new TextView(this);
        attachBtn.setText("附带日志（当前：关）");
        attachBtn.setTextSize(m.backSize);
        attachBtn.setTextColor(logEnabled ? C_PRIMARY : C_SUB);
        attachBtn.setGravity(Gravity.CENTER);
        attachBtn.setPadding(dp(10), dp(10), dp(10), dp(10));
        attachBtn.setBackgroundDrawable(pill(logEnabled && fbAttachLog ? 0xFF14384A : C_PILL));
        attachBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                vibrate();
                if (!logEnabled) {
                    toast("请先在「设置 → 记录日志」里开启日志");
                    return;
                }
                fbAttachLog = !fbAttachLog;
                attachBtn.setText("附带日志（当前：" + (fbAttachLog ? "开" : "关") + "）");
                attachBtn.setBackgroundDrawable(pill(fbAttachLog ? 0xFF14384A : C_PILL));
            }
        });
        LinearLayout.LayoutParams atLp = wrap();
        atLp.topMargin = dp(8);
        atLp.bottomMargin = dp(6);
        feedbackInner.addView(attachBtn, atLp);

        TextView diag = new TextView(this);
        diag.setText("自动附带：版本 " + appVersion() + "（" + appVersionCode() + "）· " + Build.MODEL
                + " · Android " + Build.VERSION.RELEASE + " · " + (m.round ? "圆屏" : "方屏"));
        diag.setTextSize(m.subtitleSize - 2.5f);
        diag.setTextColor(C_SUB);
        diag.setGravity(Gravity.CENTER);
        diag.setPadding(dp(4), dp(5), dp(4), dp(8));
        feedbackInner.addView(diag, wrap());

        TextView note = new TextView(this);
        note.setText("提交后会以你的 GitHub 账号在该仓库公开建一条 Issue，别人也能看到。");
        note.setGravity(Gravity.CENTER);
        note.setTextSize(m.subtitleSize - 2.5f);
        note.setTextColor(C_SUB);
        note.setPadding(dp(4), 0, dp(4), dp(8));
        feedbackInner.addView(note, wrap());

        fbSubmitBtn = fbActionBtn("提交反馈", "ic_feedback", C_PRIMARY, 0xFF14384A);
        fbSubmitBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                vibrate();
                submitFeedback();
            }
        });
        feedbackInner.addView(fbSubmitBtn, wrap());

        TextView mineBtn = fbActionBtn("我的反馈记录", "ic_assignment", C_PRIMARY, C_PILL);
        mineBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                vibrate();
                if (ghToken == null) {
                    toast("请先在个人主页登录 GitHub");
                    showScreen(S_PROFILE);
                    return;
                }
                mineAllMode = false;
                if (mineHeader != null) mineHeader.setText("‹ 返回反馈");
                showScreen(S_MYFEEDBACK);
                loadMyIssues();
            }
        });
        LinearLayout.LayoutParams mlp = wrap();
        mlp.topMargin = dp(8);
        feedbackInner.addView(mineBtn, mlp);

        // 仅项目所有者（@hnxy114514）可见：查看仓库里所有人的反馈（含已/未处理）
        fbAllBtn = fbActionBtn("查看全部反馈", "ic_person", 0xFF4FC3F7, 0xFF10222B);
        fbAllBtn.setVisibility(View.GONE);
        fbAllBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                vibrate();
                if (ghToken == null) {
                    toast("请先在个人主页登录 GitHub");
                    showScreen(S_PROFILE);
                    return;
                }
                mineAllMode = true;
                if (mineHeader != null) mineHeader.setText("‹ 全部反馈");
                showScreen(S_MYFEEDBACK);
                loadMyIssues();
            }
        });
        LinearLayout.LayoutParams allLp = wrap();
        allLp.topMargin = dp(6);
        feedbackInner.addView(fbAllBtn, allLp);
        padInner(feedbackInner);
    }

    private void updateFeedbackUi() {
        if (fbStatusTxt == null) return;
        if (ghToken == null) {
            fbStatusTxt.setText("未登录 GitHub · 点这里去登录");
            fbStatusTxt.setTextColor(C_PRIMARY);
        } else {
            fbStatusTxt.setText("已登录 " + (ghLogin != null ? "@" + ghLogin : "GitHub"));
            fbStatusTxt.setTextColor(C_SUB);
        }
        if (fbAllBtn != null) fbAllBtn.setVisibility(isAdmin() ? View.VISIBLE : View.GONE);
        updateTypeButtons();
    }

    /** 项目所有者本人（@hnxy114514）可查看全部反馈。 */
    private boolean isAdmin() {
        return "hnxy114514".equalsIgnoreCase(ghLogin);
    }

    private void updateProfileUi() {
        if (profStatusTxt == null) return;
        boolean logged = ghToken != null;
        if (profAvatarBox != null) profAvatarBox.setVisibility(logged ? View.VISIBLE : View.GONE);
        profLogin.setVisibility(logged ? View.VISIBLE : View.GONE);
        profName.setVisibility(logged ? View.VISIBLE : View.GONE);

        if (loginRunning) {
            boolean hasCode = fbCode != null;
            profStatusTxt.setText(hasCode ? "手机扫二维码，输入下面的码授权" : "正在获取登录码…");
            profQr.setVisibility(hasCode ? View.VISIBLE : View.GONE);
            profCodeBig.setVisibility(hasCode ? View.VISIBLE : View.GONE);
            if (hasCode) profCodeBig.setText(fbCode);
            profLoginBtn.setText(hasCode ? "或用本机浏览器打开" : "请稍候…");
            profLoginBtn.setTextColor(C_PRIMARY);
            return;
        }
        profQr.setVisibility(View.GONE);
        profCodeBig.setVisibility(View.GONE);
        if (logged) {
            if (profAvatar.getDrawable() == null
                    && (profAvatarSpin == null || profAvatarSpin.getVisibility() != View.VISIBLE)) {
                showAvatarPlaceholder();
            }
            profLogin.setText(ghLogin == null ? "已登录" : "@" + ghLogin);
            profName.setText(prefs.getString("gh_name", ""));
            profStatusTxt.setText("已登录 GitHub，反馈会以这个账号提交");
            profLoginBtn.setText("退出登录");
            profLoginBtn.setTextColor(C_SUB);
        } else {
            profStatusTxt.setText("未登录 GitHub");
            profLoginBtn.setText("登录 GitHub");
            profLoginBtn.setTextColor(C_PRIMARY);
        }
    }

    private void updateTypeButtons() {
        for (int i = 0; i < fbTypeBtns.length; i++) {
            TextView b = fbTypeBtns[i];
            if (b == null) continue;
            boolean on = (i == fbType);
            b.setTextColor(on ? C_PRIMARY : C_SUB);
            b.setTypeface(on ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            b.setBackgroundDrawable(pill(on ? 0xFF14384A : C_PILL));
        }
        // 说明行跟着当前选中的类型变，用户不用猜哪个合适
        if (fbTypeHint != null && fbType >= 0 && fbType < FB_TYPE_HINTS.length) {
            fbTypeHint.setText(FB_TYPE_HINTS[fbType]);
        }
    }

    private void loadGitHub() {
        ghToken = prefs.getString("gh_token", null);
        if (ghToken != null && ghToken.length() == 0) ghToken = null;
        String lg = prefs.getString("gh_login", "");
        ghLogin = (lg == null || lg.length() == 0) ? null : lg;
    }

    private void saveGitHub(String token, String login) {
        ghToken = token;
        ghLogin = login;
        SharedPreferences.Editor e = prefs.edit().putString("gh_token", token == null ? "" : token);
        if (login != null) e.putString("gh_login", login);
        e.apply();
    }

    /** 登录流程结束后的 UI 收尾；后台线程调用安全。 */
    private void afterLoginEnds() {
        runOnUiThread(new Runnable() {
            public void run() {
                if (!alive()) return;
                dismissCodeDialog();
                updateFeedbackUi();
                updateProfileUi();
                if (ghToken != null) loadProfileFromGitHub();
            }
        });
    }

    private void logoutGitHub() {
        log("LOGIN", "退出登录");
        loginRunning = false;
        dismissCodeDialog();
        ghToken = null;
        ghLogin = null;
        // 昵称与头像也要清：否则退出后个人页还显示着上一个人的信息
        prefs.edit().remove("gh_token").remove("gh_login")
                .remove("gh_name").remove("gh_avatar").apply();
        profAvatar.setImageDrawable(null);
        if (profAvatarSpin != null) profAvatarSpin.setVisibility(View.GONE);
        if (profAvatarBox != null) profAvatarBox.setBackgroundDrawable(null);
        updateFeedbackUi();
        updateProfileUi();
        toast("已退出登录");
    }

    /** Device Flow：手表只显示 8 位码，在手机浏览器授权即可。 */
    private void startDeviceLogin() {
        if (GH_CLIENT_ID.length() == 0) {
            toast("登录尚未启用：还差应用的 client_id，配好后即可用");
            return;
        }
        loginRunning = true;
        fbCode = null;
        fbCodeUrl = null;
        updateFeedbackUi();
        new Thread(new Runnable() {
            public void run() {
                try {
                    String body = "client_id=" + GH_CLIENT_ID + "&scope=public_repo";
                    JSONObject o = new JSONObject(httpPost(GH_DEVICE_CODE, body,
                            "application/x-www-form-urlencoded", null));
                    final String device = o.optString("device_code", "");
                    final String code = o.optString("user_code", "");
                    String complete = o.optString("verification_uri_complete", "");
                    int interval = Math.max(5, o.optInt("interval", 5));
                    if (device.length() == 0) {
                        throw new Exception(o.optString("error_description", "获取登录码失败"));
                    }
                    fbCode = code;
                    fbCodeUrl = (complete.length() > 0) ? complete
                            : "https://github.com/login/device?user_code=" + code;
                    runOnUiThread(new Runnable() {
                        public void run() {
                            if (!alive()) return;
                            copyToClipboard(code);
                            updateFeedbackUi();
                            showCodeDialog(code, fbCodeUrl);
                        }
                    });
                    pollDeviceToken(device, interval);
                } catch (final Exception e) {
                    loginRunning = false;
                    afterLoginEnds();
                    toast("登录失败：" + netErr(e));
                }
            }
        }).start();
    }

    private void pollDeviceToken(String device, int interval) {
        long deadline = System.currentTimeMillis() + 5 * 60 * 1000L;
        while (loginRunning && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(interval * 1000L);
            } catch (InterruptedException e) {
                return;
            }
            if (!loginRunning) return;
            try {
                String body = "client_id=" + GH_CLIENT_ID
                        + "&device_code=" + device
                        + "&grant_type=urn:ietf:params:oauth:grant-type:device_code";
                JSONObject o = new JSONObject(httpPost(GH_ACCESS_TOKEN, body,
                        "application/x-www-form-urlencoded", null));
                String token = o.optString("access_token", "");
                if (token.length() > 0) {
                    saveGitHub(token, null);
                    fetchGitHubLogin();
                    return;
                }
                String err = o.optString("error", "");
                if ("slow_down".equals(err)) { interval += 5; continue; }
                if ("authorization_pending".equals(err)) continue;
                loginRunning = false;
                afterLoginEnds();
                toast("登录未完成：" + ("access_denied".equals(err) ? "已取消授权" : err));
                return;
            } catch (Exception ignored) {
                // 网络抖动，下一轮继续
            }
        }
        loginRunning = false;
        afterLoginEnds();
        if (ghToken == null) toast("登录超时，可再点一次登录");
    }

    private void fetchGitHubLogin() {
        new Thread(new Runnable() {
            public void run() {
                String login = null;
                try {
                    // 直连取当前账号：镜像会忽略 token 头，拿到的是别人的账号
                    login = new JSONObject(httpGetAuthed(apiAuthedBase() + GH_USER)).optString("login", "");
                } catch (Exception ignored) { }
                loginRunning = false;
                // 取不到账号名时保留原来的（别把已有账号名抹成空），下次进个人页会再拉一次
                saveGitHub(ghToken, (login == null || login.length() == 0) ? ghLogin : login);
                log("LOGIN", "登录成功 @" + (login == null ? "?" : login));
                afterLoginEnds();
                toast(login == null ? "已登录 GitHub" : "已登录 @" + login);
            }
        }).start();
    }

    private void submitFeedback() {
        final String desc = fbDesc.getText().toString().trim();
        if (desc.length() < 5) {
            toast("请先写清问题或建议（至少 5 个字）");
            return;
        }
        if (ghToken == null) {
            toast("请先在个人主页登录 GitHub");
            showScreen(S_PROFILE);
            return;
        }
        fbSubmitBtn.setText("提交中…");
        fbSubmitBtn.setEnabled(false);
        new Thread(new Runnable() {
            public void run() {
                try {
                    JSONObject payload = new JSONObject();
                    payload.put("title", "[" + FB_TYPES[fbType] + "] " + firstLine(desc, 20));
                    String body = feedbackBody(FB_TYPES[fbType], desc);
                    if (fbAttachLog && logEnabled) {
                        String tail = readLogTail(8192);
                        if (tail.length() > 0) {
                            body += "\n\n---\n\n<details><summary>应用日志（末尾）</summary>\n\n```\n"
                                    + tail + "\n```\n</details>\n";
                        }
                    }
                    // 公式排版/崩溃的记录单独存了两个小文件，跟着反馈一起带上，省得让用户找
                    if (fbAttachLog) {
                        java.io.File rf = new java.io.File(getFilesDir(), "render.log");
                        String rl = readFileQuiet(rf);
                        if (rl.length() > 0) {
                            body += "\n\n---\n\n<details><summary>公式排版异常记录</summary>\n\n```\n"
                                    + (rl.length() > 3000 ? rl.substring(rl.length() - 3000) : rl)
                                    + "\n```\n</details>\n";
                        }
                        java.io.File cf = new java.io.File(getFilesDir(), "crash.log");
                        String cl = readFileQuiet(cf);
                        if (cl.length() > 0) {
                            body += "\n\n---\n\n<details><summary>上次崩溃记录</summary>\n\n```\n"
                                    + (cl.length() > 3000 ? cl.substring(cl.length() - 3000) : cl)
                                    + "\n```\n</details>\n";
                        }
                    }
                    payload.put("body", body);
                    JSONObject o = new JSONObject(httpPost(GH_ISSUES, payload.toString(),
                            "application/json", ghToken));
                    final int num = o.optInt("number", 0);
                    final String msg = o.optString("message", "");
                    runOnUiThread(new Runnable() {
                        public void run() {
                            if (!alive()) return;
                            fbSubmitBtn.setEnabled(true);
                            if (num > 0) {
                                fbDesc.setText("");
                                fbDraft = "";
                                fbSubmitBtn.setText("提交反馈");
                                toast("已提交，感谢反馈（issue #" + num + "）");
                            } else {
                                fbSubmitBtn.setText("提交反馈");
                                toast("提交失败：" + (msg.length() > 0 ? msg : "未知错误") + " · 草稿已保留");
                                if (msg.contains("Bad credentials") || msg.contains("Not Found")) {
                                    logoutGitHub();
                                }
                            }
                        }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        public void run() {
                            fbSubmitBtn.setEnabled(true);
                            fbSubmitBtn.setText("提交反馈");
                            toast("提交失败：" + netErr(e) + " · 草稿已保留，可稍后重试");
                        }
                    });
                }
            }
        }).start();
    }

    private String feedbackBody(String type, String desc) {
        return "**类型**：" + type + "\n\n"
                + "**版本**：" + appVersion() + "（build " + appVersionCode() + "）\n"
                + "**设备**：" + Build.MODEL + " · Android " + Build.VERSION.RELEASE
                + " · " + (m.round ? "圆屏" : "方屏") + "\n"
                + (lastPath.length() > 0 ? "**最近浏览**：" + lastPath + "\n" : "")
                + "\n---\n\n" + desc + "\n";
    }

    private String firstLine(String s, int max) {
        int nl = s.indexOf('\n');
        String t = nl > 0 ? s.substring(0, nl) : s;
        if (t.length() > max) t = t.substring(0, max) + "…";
        return t;
    }

    private String appVersion() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "?";
        }
    }

    private int appVersionCode() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
        } catch (Exception e) {
            return 0;
        }
    }

    private void copyToClipboard(String text) {
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("GitHub 授权码", text));
        } catch (Exception ignored) { }
    }

    /** 原生对话框：显示授权码（已自动复制到剪贴板），再决定去不去浏览器。 */
    private void showCodeDialog(final String code, final String url) {
        dismissCodeDialog();
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(12), dp(13), dp(12), dp(8));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF151515);
        bg.setCornerRadius(dp(16));
        box.setBackgroundDrawable(bg);

        TextView cap = new TextView(this);
        cap.setText("GitHub 授权码");
        cap.setTextSize(m.subtitleSize - 2f);
        cap.setTextColor(C_SUB);
        cap.setGravity(Gravity.CENTER);
        box.addView(cap, wrap());

        TextView codeTxt = new TextView(this);
        codeTxt.setText(code);
        codeTxt.setTextSize(m.titleSize);
        codeTxt.setTypeface(Typeface.DEFAULT_BOLD);
        codeTxt.setTextColor(C_PRIMARY);
        codeTxt.setGravity(Gravity.CENTER);
        codeTxt.setPadding(0, dp(3), 0, dp(3));
        box.addView(codeTxt, wrap());

        TextView copied = new TextView(this);
        copied.setText("手机浏览器打开 github.com/login/device 输入此码");
        copied.setTextSize(m.subtitleSize - 2.5f);
        copied.setTextColor(C_SUB);
        copied.setGravity(Gravity.CENTER);
        box.addView(copied, wrap());

        TextView open = new TextView(this);
        open.setText("用本机浏览器试试");
        open.setTextSize(m.backSize);
        open.setTypeface(Typeface.DEFAULT_BOLD);
        open.setTextColor(C_PRIMARY);
        open.setGravity(Gravity.CENTER);
        open.setPadding(dp(8), dp(9), dp(8), dp(9));
        open.setBackgroundDrawable(pill(0xFF14384A));
        open.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                vibrate();
                dismissCodeDialog();
                openBrowser(url);
            }
        });
        LinearLayout.LayoutParams olp = wrap();
        olp.topMargin = dp(9);
        box.addView(open, olp);

        TextView tip = new TextView(this);
        tip.setText("已复制到剪贴板。手表浏览器可能点不动 Authorize，用手机更稳");
        tip.setTextSize(m.subtitleSize - 3f);
        tip.setTextColor(C_SUB);
        tip.setGravity(Gravity.CENTER);
        tip.setPadding(0, dp(4), 0, 0);
        box.addView(tip, wrap());

        TextView close = new TextView(this);
        close.setText("稍后再说");
        close.setTextSize(m.subtitleSize - 1f);
        close.setTextColor(C_SUB);
        close.setGravity(Gravity.CENTER);
        close.setPadding(dp(8), dp(8), dp(8), dp(2));
        close.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                vibrate();
                dismissCodeDialog();
            }
        });
        box.addView(close, wrap());

        codeDialog = new AlertDialog.Builder(this).setView(box).create();
        codeDialog.setCancelable(false);
        codeDialog.setCanceledOnTouchOutside(false);
        tuneDialogWindow(codeDialog);   // 必须在 show() 之前
        codeDialog.show();
        try {
            Window w = codeDialog.getWindow();
            int sw = getResources().getDisplayMetrics().widthPixels;
            w.setLayout((int) (sw * (m.round ? 0.62f : 0.82f)), WindowManager.LayoutParams.WRAP_CONTENT);
            w.setBackgroundDrawable(new ColorDrawable(0x00000000));
        } catch (Exception ignored) { }
    }

    private void dismissCodeDialog() {
        try {
            if (codeDialog != null && codeDialog.isShowing()) codeDialog.dismiss();
        } catch (Exception ignored) { }
        codeDialog = null;
    }

    private void openBrowser(String url) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception e) {
            toast("没找到浏览器，请在手机上打开 github.com/login/device");
        }
    }

    private String httpPost(String url, String body, String contentType, String token) throws Exception {
        return httpPost(url, body, contentType, token, "token");
    }

    /** scheme 用 token（REST 老写法）还是 Bearer（GraphQL 要求），由调用处决定。 */
    private String httpPost(String url, String body, String contentType, String token,
                            String scheme) throws Exception {
        HttpURLConnection cn = (HttpURLConnection) new URL(url).openConnection();
        try {
            byte[] data = body.getBytes(StandardCharsets.UTF_8);
            cn.setRequestMethod("POST");
            cn.setConnectTimeout(10000);
            cn.setReadTimeout(15000);
            cn.setDoOutput(true);
            cn.setFixedLengthStreamingMode(data.length);
            cn.setRequestProperty("Content-Type", contentType);
            cn.setRequestProperty("Accept", "application/json");
            cn.setRequestProperty("User-Agent", "HZKnowledge-Android");
            if (token != null) cn.setRequestProperty("Authorization", scheme + " " + token);
            OutputStream os = cn.getOutputStream();
            os.write(data);
            os.close();
            int code = cn.getResponseCode();
            InputStream is = (code >= 400) ? cn.getErrorStream() : cn.getInputStream();
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            if (is != null) {
                byte[] b = new byte[4096];
                int n;
                while ((n = is.read(b)) > 0) bos.write(b, 0, n);
                is.close();
            }
            return new String(bos.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            cn.disconnect();
        }
    }

    private String httpGetAuthed(String url) throws Exception {
        HttpURLConnection cn = (HttpURLConnection) new URL(url).openConnection();
        try {
            cn.setConnectTimeout(8000);
            cn.setReadTimeout(12000);
            cn.setRequestProperty("Accept", "application/vnd.github+json");
            cn.setRequestProperty("User-Agent", "HZKnowledge-Android");
            cn.setRequestProperty("Authorization", "token " + ghToken);
            int code = cn.getResponseCode();
            InputStream is = (code >= 400) ? cn.getErrorStream() : cn.getInputStream();
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            if (is != null) {
                byte[] b = new byte[4096];
                int n;
                while ((n = is.read(b)) > 0) bos.write(b, 0, n);
                is.close();
            }
            String s = new String(bos.toByteArray(), StandardCharsets.UTF_8);
            // 以前 4xx 直接抛异常、错误体被丢掉，界面上只剩「加载失败」四个字
            if (code >= 400) throw new Exception("HTTP " + code + (s.length() > 0 ? " " + s : ""));
            return s;
        } finally {
            cn.disconnect();
        }
    }

    /**
     * 反馈列表/评论这类公开数据：优先走启动时探测出的可用通道，再回退到直连和其它镜像。
     * 只用于 GET 公开数据；涉及 token 身份判断的 /user 与所有写操作必须直连——
     * 镜像会忽略 Authorization 头（见 apiAuthedBase 的注释），写请求走镜像会被当成未登录。
     */
    private String readIssues(String path) throws Exception {
        String url = "https://api.github.com/repos/" + REPO + path;
        List<String> cands = new ArrayList<String>();
        addNoDup(cands, apiBase());
        addNoDup(cands, "");
        for (int i = 0; i < API_MIRRORS.length; i++) addNoDup(cands, API_MIRRORS[i]);
        Exception last = null;
        for (String base : cands) {
            try {
                return httpGetAuthed(base + url);
            } catch (Exception e) {
                last = e;
            }
        }
        reProbeIfNeeded();   // 全都失败：多半是启动时没网，后台再探一次通道
        throw (last == null) ? new Exception("没连上 GitHub") : last;
    }

    /** 把网络异常翻成人能看懂的一句话：区分「没连上」「登录过期」「服务器报错」。 */
    private String netErr(Throwable e) {
        String s = (e == null || e.getMessage() == null) ? "" : e.getMessage();
        String low = s.toLowerCase(java.util.Locale.US);
        if (s.contains("401") || s.contains("403") || low.contains("bad credentials"))
            return "登录已过期，去个人页重新登录 GitHub";
        if (low.contains("timeout") || low.contains("timed out")
                || low.contains("failed to connect") || low.contains("unable to resolve host")
                || low.contains("enotfound") || low.contains("network")
                || low.contains("socket") || low.contains("econn") || low.contains("ehost"))
            return "没连上 GitHub，检查网络后重试";
        if (s.length() > 70) s = s.substring(0, 70) + "…";
        return s.length() > 0 ? s : "没连上 GitHub，检查网络后重试";
    }
    // ================= 我的反馈记录 =================

    /** 我的反馈列表：主行标题 + 状态小字；加载失败那一行可以点着重试。 */
    private void bindMine() {
        mineList.bind(new RowSource() {
            public int size() { return mineRows.size(); }
            public View make(final int i) {
                final String[] r = mineRows.get(i);
                Runnable act = null;
                if (i < mineIssues.size()) {
                    final JSONObject o = mineIssues.get(i);
                    act = new Runnable() {
                        public void run() { openIssueDetail(o); }
                    };
                } else if (r[0].contains("加载失败")) {
                    act = new Runnable() {
                        public void run() { loadMyIssues(); }
                    };
                }
                return pairRow(r[0], r[1], r[2], act);
            }
        });
    }

    private void loadMyIssues() {
        mineIssues.clear();
        mineRows.clear();
        mineRows.add(new String[]{"正在加载…", "", "#FF8A8A8A"});
        bindMine();
        final String login = ghLogin;
        final boolean all = mineAllMode;
        new Thread(new Runnable() {
            public void run() {
                try {
                    String path = "/issues?state=all&per_page="
                            + (all ? "100" : "40")
                            + (!all && login != null && login.length() > 0 ? "&creator=" + login : "");
                    JSONArray arr = new JSONArray(readIssues(path));
                    final List<JSONObject> items = new ArrayList<JSONObject>();
                    final List<String[]> rows = new ArrayList<String[]>();
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.getJSONObject(i);
                        if (o.has("pull_request")) continue;
                        items.add(o);
                        rows.add(new String[]{
                                "#" + o.optInt("number") + " " + o.optString("title"),
                                issueStatusText(o) + " · " + shortTime(o.optString("created_at", "")),
                                issueStatusColor(o)});
                    }
                    runOnUiThread(new Runnable() {
                        public void run() {
                            mineIssues.clear();
                            mineIssues.addAll(items);
                            mineRows.clear();
                            if (rows.isEmpty()) {
                                mineRows.add(new String[]{all ? "暂无反馈" : "还没有提交过反馈", "", "#FF8A8A8A"});
                            } else {
                                mineRows.addAll(rows);
                            }
                            bindMine();
                        }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        public void run() {
                            mineRows.clear();
                            mineRows.add(new String[]{"加载失败 · 点这里重试", netErr(e), "#FFE57373"});
                            bindMine();
                        }
                    });
                }
            }
        }).start();
    }

    private String issueStatusText(JSONObject o) {
        boolean working = false, fixed = false;
        JSONArray ls = o.optJSONArray("labels");
        if (ls != null) {
            for (int i = 0; i < ls.length(); i++) {
                JSONObject l = ls.optJSONObject(i);
                if (l == null) continue;
                String n = l.optString("name", "");
                if (n.contains("已修复") || n.contains("已处理")) fixed = true;
                if (n.contains("处理中")) working = true;
            }
        }
        if (fixed) return "已修复";
        if ("closed".equals(o.optString("state"))) {
            // 本人撤回的会在正文末尾留一行标记，跟「已处理」区分开
            if (o.optString("body", "").contains("提交者已撤回")) return "已撤回";
            return "已处理";
        }
        if (working) return "处理中";
        return "待处理";
    }

    private String issueStatusColor(JSONObject o) {
        String s = issueStatusText(o);
        if ("已撤回".equals(s)) return "#FF8A8A8A";
        if ("已修复".equals(s) || "已处理".equals(s)) return "#FF81C784";
        if ("处理中".equals(s)) return "#FF4FC3F7";
        return "#FFFFB74D";
    }

    private String shortTime(String iso) {
        if (iso == null || iso.length() < 10) return "";
        return iso.substring(5, 10);
    }

    /** 打开单条反馈：正文 + 最新回复。 */
    private void openIssueDetail(final JSONObject issue) {
        final int number = issue.optInt("number");
        final String title = issue.optString("title");
        final String body = issue.optString("body", "");
        final String status = issueStatusText(issue);
        toast("正在读取 #" + number + "…");
        new Thread(new Runnable() {
            public void run() {
                String reply = null;
                try {
                    String path = "/issues/" + number + "/comments?per_page=30";
                    JSONArray arr = new JSONArray(readIssues(path));
                    for (int i = 0; i < arr.length(); i++) {
                        String b = arr.getJSONObject(i).optString("body", "");
                        if (b.length() > 0) reply = b;
                    }
                } catch (Exception ignored) { }
                final String finalReply = reply;
                runOnUiThread(new Runnable() {
                    public void run() {
                        List<Card> cards = new ArrayList<Card>();
                        cards.add(card("反馈内容", "K", body.length() == 0 ? "(没有正文)" : body));
                        if (finalReply != null) {
                            cards.add(card("处理回复", "M", finalReply));
                        } else {
                            cards.add(card("处理回复", "E",
                                    "还没有回复。处理后会写在这里，状态变成「已处理」时说明已经修好。"));
                        }
                        openDetailCards("#" + number + " " + title, cards, status + " · " + shortTime(
                                issue.optString("created_at", "")), S_MYFEEDBACK, null, null, null);
                        curIssue = issue;
                        addIssueActions(issue);
                    }
                });
            }
        }).start();
    }
    // ================= 反馈的编辑 / 撤回 / 删除 =================

    private static final String GH_GRAPHQL = "https://api.github.com/graphql";
    /** 改标题 + 正文（编辑）。 */
    private static final String MUT_EDIT =
            "mutation($id: ID!, $title: String!, $body: String!) {"
            + " updateIssue(input: {id: $id, title: $title, body: $body})"
            + " { issue { number } } }";
    /** 撤回：正文末尾加一行说明，同时把状态置为 CLOSED。 */
    private static final String MUT_WITHDRAW =
            "mutation($id: ID!, $body: String!) {"
            + " updateIssue(input: {id: $id, body: $body, state: CLOSED})"
            + " { issue { number state } } }";
    /** 真删除只有 GraphQL 有（REST 没有删 issue 的接口）。 */
    private static final String MUT_DELETE =
            "mutation($id: ID!) { deleteIssue(input: {issueId: $id}) { clientMutationId } }";

    /** 带 token 的 GraphQL 请求，直连不走镜像；GitHub 把错误放在 errors 里，一并抛出来。 */
    private JSONObject ghGraphql(String query, JSONObject vars) throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("query", query);
        payload.put("variables", vars);
        String raw = httpPost(GH_GRAPHQL, payload.toString(), "application/json", ghToken, "Bearer");
        JSONObject o = new JSONObject(raw);
        JSONArray errs = o.optJSONArray("errors");
        if (errs != null && errs.length() > 0) {
            String msg = errs.optJSONObject(0).optString("message", "");
            throw new Exception(msg.length() > 0 ? msg : "请求被拒绝");
        }
        JSONObject data = o.optJSONObject("data");
        return data != null ? data : new JSONObject();
    }

    /** 这条是不是本人发的。 */
    private boolean issueMine(JSONObject o) {
        if (ghLogin == null || ghLogin.length() == 0) return false;
        JSONObject u = o.optJSONObject("user");
        String lg = (u == null) ? "" : u.optString("login", "");
        return ghLogin.equalsIgnoreCase(lg);
    }

    /** 未受理 = 状态是「待处理」：没打「处理中」「已修复」标签，也没被关闭。 */
    private boolean issuePending(JSONObject o) {
        return "待处理".equals(issueStatusText(o));
    }

    /** 反馈详情底部：本人可编辑 / 撤回未受理的；项目所有者可删除任意一条。 */
    private void addIssueActions(final JSONObject issue) {
        if (ghToken == null || detailInner == null) return;
        if (issue.optString("node_id", "").length() == 0) return;
        if (issueMine(issue) && issuePending(issue)) {
            detailInner.addView(issueActBtn("编辑这条反馈", C_PRIMARY, 0xFF14384A,
                    new View.OnClickListener() {
                        public void onClick(View v) { showEditIssueDialog(issue); }
                    }), wrapM());
            detailInner.addView(issueActBtn("撤回这条反馈", 0xFFFFB74D, C_PILL,
                    new View.OnClickListener() {
                        public void onClick(View v) { confirmWithdrawIssue(issue); }
                    }), wrapM());
        }
        if (isAdmin()) {
            detailInner.addView(issueActBtn("删除这条反馈（开发者）", 0xFFE57373, C_PILL,
                    new View.OnClickListener() {
                        public void onClick(View v) { confirmDeleteIssue(issue); }
                    }), wrapM());
        }
    }

    private TextView issueActBtn(String label, int textColor, int bg, View.OnClickListener onClick) {
        final View.OnClickListener user = onClick;
        TextView b = new TextView(this);
        b.setText(label);
        b.setTextSize(m.backSize);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setGravity(Gravity.CENTER);
        b.setTextColor(textColor);
        b.setPadding(dp(10), dp(11), dp(10), dp(11));
        b.setBackgroundDrawable(pill(bg));
        b.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { vibrate(); if (user != null) user.onClick(v); }
        });
        return b;
    }

    /** 编辑自己那条反馈：类型 + 描述，保存时整体覆盖 GitHub 上的标题和正文。 */
    private void showEditIssueDialog(final JSONObject issue) {
        dismissEditIssueDialog();
        final String oldBody = issue.optString("body", "");
        final int[] type = new int[]{parseIssueType(oldBody)};
        String desc = parseIssueDesc(oldBody);

        // 外层：竖排容器 = 上面可滚动的内容区 + 底部固定的操作按钮条。
        // 按钮必须固定在滚动区外，否则屏幕小的时候它被内容顶到下面，用户看不到「保存」按钮。
        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable og = new GradientDrawable();
        og.setColor(0xFF151515);
        og.setCornerRadius(dp(16));
        outer.setBackgroundDrawable(og);

        // 结构：上面是可滚的类型选择，下面是常驻的输入框，再下面是操作按钮条。
        // 输入框必须常驻（不能塞进滚动区），否则屏小的时候它被顶到可视区外看不见。
        // 限高方式跟更新弹窗保持一致：用 LayoutParams 给固定高度，
        // 不要重写 onMeasure 传 AT_MOST（那种方式下滚动时重绘范围算不准，容易留残影）
        final int maxDlgH = Math.min(dp(120),
                (int) (getResources().getDisplayMetrics().heightPixels * 0.30f));
        ScrollView sc = new ScrollView(this);
        // 顺序：标题 → 可滚的类型选择 → 常驻输入框 → 底部按钮条
        TextView cap = new TextView(this);
        cap.setText("编辑这条反馈");
        cap.setTextSize(m.subtitleSize);
        cap.setTypeface(Typeface.DEFAULT_BOLD);
        cap.setTextColor(C_PRIMARY);
        cap.setGravity(Gravity.CENTER);
        cap.setPadding(dp(12), dp(10), dp(12), dp(6));
        outer.addView(cap, wrap());

        // 滚动区必须有不透明背景：背景透明的话滚动只重画脏区，上一帧的像素留在原地就是残影
        sc.setBackgroundColor(0xFF151515);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(12), dp(4), dp(12), dp(2));
        sc.addView(box);
        outer.addView(sc, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, maxDlgH));

        final TextView[] btns = new TextView[FB_TYPES.length];
        // 类型说明：告诉用户每一类对应什么现象，编辑时也能对上号
        final TextView typeHint = new TextView(this);
        typeHint.setTextSize(Math.max(8f, m.subtitleSize - 3f));
        typeHint.setTextColor(C_SUB);
        typeHint.setGravity(Gravity.CENTER);
        typeHint.setPadding(dp(4), dp(2), dp(4), dp(4));
        typeHint.setText(FB_TYPE_HINTS[type[0]]);
        // 3 列排 2 行：2 列排 3 行的话最后一行会被屏幕下缘/圆弧裁掉，点不到
        final int edCols = 3;
        for (int row = 0; row * edCols < FB_TYPES.length; row++) {
            LinearLayout line = new LinearLayout(this);
            line.setOrientation(LinearLayout.HORIZONTAL);
            int cols = Math.min(edCols, FB_TYPES.length - row * edCols);
            for (int col = 0; col < cols; col++) {
                final int idx = row * edCols + col;
                TextView b = new TextView(this);
                b.setText(FB_TYPES[idx]);
                b.setTextSize(Math.max(9f, m.subtitleSize - 2f));
                b.setGravity(Gravity.CENTER);
                b.setPadding(dp(3), dp(9), dp(3), dp(9));
                b.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        vibrate();
                        type[0] = idx;
                        paintEditTypes(btns, type[0]);
                        typeHint.setText(FB_TYPE_HINTS[idx]);
                    }
                });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                        LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                if (col > 0) lp.leftMargin = dp(3);
                if (col < cols - 1) lp.rightMargin = dp(3);
                btns[idx] = b;
                line.addView(b, lp);
            }
            LinearLayout.LayoutParams llp = wrap();
            llp.bottomMargin = dp(4);
            box.addView(line, llp);
        }
        paintEditTypes(btns, type[0]);
        box.addView(typeHint, wrap());

        // 输入框常驻在滚动区外面，永远看得见；高度限死，避免把弹窗顶出屏幕
        final EditText ed = new EditText(this);
        ed.setText(desc);
        ed.setTextSize(m.subtitleSize - 1f);
        ed.setTextColor(Color.WHITE);
        ed.setHint("写清问题或建议");
        ed.setHintTextColor(C_SUB);
        ed.setBackgroundDrawable(pill(C_PILL));
        ed.setPadding(dp(10), dp(8), dp(10), dp(8));
        ed.setMinLines(2);
        ed.setMaxLines(4);
        ed.setGravity(Gravity.TOP | Gravity.START);
        // 不自动抢焦点：抢了就会弹出输入法，键盘挡住弹窗内容还让窗口跳动
        ed.setFocusableInTouchMode(true);
        ed.setFocusable(true);
        LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(72));
        elp.topMargin = dp(4);
        elp.leftMargin = dp(12);
        elp.rightMargin = dp(12);
        outer.addView(ed, elp);

        // ---- 底部固定操作条：保存与取消永远看得见 ----
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(12), dp(4), dp(12), dp(10));

        final TextView save = new TextView(this);
        save.setText("保存修改");
        save.setTextSize(m.backSize);
        save.setTypeface(Typeface.DEFAULT_BOLD);
        save.setGravity(Gravity.CENTER);
        save.setTextColor(C_PRIMARY);
        save.setPadding(dp(10), dp(11), dp(10), dp(11));
        save.setBackgroundDrawable(pill(0xFF14384A));
        bar.addView(save, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 2f));

        TextView cancel = new TextView(this);
        cancel.setText("取消");
        cancel.setTextSize(m.subtitleSize - 1f);
        cancel.setGravity(Gravity.CENTER);
        cancel.setTextColor(C_SUB);
        cancel.setPadding(dp(8), dp(11), dp(8), dp(11));
        cancel.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { vibrate(); dismissEditIssueDialog(); }
        });
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        clp.leftMargin = dp(8);
        bar.addView(cancel, clp);
        outer.addView(bar, wrap());

        save.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                vibrate();
                final String d = ed.getText().toString().trim();
                if (d.length() < 5) {
                    toast("至少写 5 个字");
                    return;
                }
                save.setText("保存中…");
                save.setEnabled(false);
                final int t = type[0];
                new Thread(new Runnable() {
                    public void run() {
                        try {
                            JSONObject vars = new JSONObject();
                            vars.put("id", issue.optString("node_id", ""));
                            vars.put("title", "[" + FB_TYPES[t] + "] " + firstLine(d, 20));
                            vars.put("body", feedbackBody(FB_TYPES[t], d));
                            ghGraphql(MUT_EDIT, vars);
                            runOnUiThread(new Runnable() {
                                public void run() {
                                    dismissEditIssueDialog();
                                    toast("已保存修改");
                                    mineDirty = true;
                                    showScreen(S_MYFEEDBACK);
                                }
                            });
                        } catch (final Exception e) {
                            runOnUiThread(new Runnable() {
                                public void run() {
                                    save.setEnabled(true);
                                    save.setText("保存修改");
                                    toast("保存失败：" + e.getMessage());
                                }
                            });
                        }
                    }
                }).start();
            }
        });

        // 滚动时整块重绘，避免内容区留上一帧的字（残影并伴随闪烁）
        tuneDialogScroll(sc, outer);
        // 输入框是 EditText，它自己会缓存绘制；滚动时让它也重画一遍，否则会留旧帧
        ed.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            public void onFocusChange(View v, boolean hasFocus) {
                if (!hasFocus) ed.invalidate();
            }
        });

        editIssueDlg = new AlertDialog.Builder(this).setView(outer).create();
        editIssueDlg.setCancelable(true);
        // 顺序要紧：窗口格式与输入法策略都要在 show() 之前设好，之后再设就无效了
        tuneDialogWindow(editIssueDlg);
        tuneDialogInput(editIssueDlg);
        editIssueDlg.show();
        sizeDialog(editIssueDlg, 0.90f);
    }

    private void paintEditTypes(TextView[] btns, int on) {
        for (int i = 0; i < btns.length; i++) {
            if (btns[i] == null) continue;
            boolean sel = (i == on);
            btns[i].setTextColor(sel ? C_PRIMARY : C_SUB);
            btns[i].setTypeface(sel ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            btns[i].setBackgroundDrawable(pill(sel ? 0xFF14384A : C_PILL));
        }
    }

    private void dismissEditIssueDialog() {
        if (editIssueDlg == null) return;
        try { editIssueDlg.dismiss(); } catch (Exception ignored) { }
        editIssueDlg = null;
    }

    /**
     * 弹窗窗口统一处理：像素格式必须声明成带透明。
     * 否则合成器按不透明窗口处理，滚动时只重画脏区，上一帧的内容会留在原地形成残影。
     *
     * **必须在 show() 之前调用**：窗口 show 的时候才真正创建，
     * 之后再 setFormat 是无效的（窗口已经按不透明格式建好了）。
     * 这是「更新弹窗没残影、编辑弹窗有残影」的根本差别。
     */
    private void tuneDialogWindow(AlertDialog d) {
        try {
            Window w = d.getWindow();
            if (w == null) return;
            w.setFormat(android.graphics.PixelFormat.TRANSLUCENT);
            w.setBackgroundDrawable(new ColorDrawable(0x00000000));
        } catch (Exception ignored) { }
    }

    /**
     * 输入框在弹窗里时的窗口设置：
     * · 不自动弹输入法（打开弹窗就把键盘顶上来，内容全被挡住）；
     * · 键盘弹出时不改变窗口尺寸（ADJUST_NOTHING），否则弹窗会整体跳一下，
     *   跳动过程中合成器只重画脏区，于是出现拖影和闪烁。
     * 用户点输入框时键盘照常弹出，只是弹窗不动。
     */
    private void tuneDialogInput(AlertDialog d) {
        try {
            Window w = d.getWindow();
            if (w == null) return;
            w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
                    | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        } catch (Exception ignored) { }
    }

    /**
     * 弹窗里的滚动区：去掉边缘光晕与越界回弹，滚动时整块 invalidate。
     * 不这么做的话，内层 ScrollView 一滚，窗口就留着上一帧的字（残影并伴随闪烁）。
     */
    private void tuneDialogScroll(final ScrollView sc, final View card) {
        sc.setVerticalScrollBarEnabled(false);
        sc.setOverScrollMode(View.OVER_SCROLL_NEVER);
        sc.setFadingEdgeLength(0);
        try {
            sc.getViewTreeObserver().addOnScrollChangedListener(
                    new android.view.ViewTreeObserver.OnScrollChangedListener() {
                        public void onScrollChanged() {
                            if (card != null) card.invalidate();
                        }
                    });
        } catch (Exception ignored) { }
    }

    /** 弹窗里的多行输入框：自己滚动时整块重画，避免窗口只刷脏区、留下上一帧的字。 */
    private void tuneEditScroll(final EditText ed, final View card) {
        if (ed == null) return;
        try {
            ed.setOverScrollMode(View.OVER_SCROLL_NEVER);
            ed.getViewTreeObserver().addOnScrollChangedListener(
                    new android.view.ViewTreeObserver.OnScrollChangedListener() {
                        public void onScrollChanged() {
                            if (card != null) card.invalidate();
                        }
                    });
        } catch (Exception ignored) { }
    }

    /** 弹窗宽度按屏宽比例给，圆屏再收一点。必须在 show() 之后调。 */
    private void sizeDialog(AlertDialog d, float ratio) {
        try {
            Window w = d.getWindow();
            if (w == null) return;
            w.setFormat(android.graphics.PixelFormat.TRANSLUCENT);
            w.setBackgroundDrawable(new ColorDrawable(0x00000000));
            int sw = getResources().getDisplayMetrics().widthPixels;
            // 圆屏要收窄：矩形弹窗的左右角会伸到圆弧外（内容被裁、按钮点不到）。
            // 0.78 是折中：再窄 3 列按钮放不下 4 个字，再宽角就出圆弧了。
            w.setLayout((int) (sw * (m.round ? 0.78f : ratio)),
                    WindowManager.LayoutParams.WRAP_CONTENT);
        } catch (Exception ignored) { }
    }

    private void confirmWithdrawIssue(final JSONObject issue) {
        confirmAction("撤回这条反馈？",
                "撤回后它会关闭，开发者不再处理；需要的话可以再重新提一条。",
                "确认撤回", 0xFFFFB74D, new Runnable() {
                    public void run() { withdrawIssue(issue); }
                });
    }

    private void withdrawIssue(final JSONObject issue) {
        toast("正在撤回…");
        final String stamp = new java.text.SimpleDateFormat("MM-dd HH:mm",
                java.util.Locale.CHINA).format(new java.util.Date());
        new Thread(new Runnable() {
            public void run() {
                try {
                    JSONObject vars = new JSONObject();
                    vars.put("id", issue.optString("node_id", ""));
                    vars.put("body", issue.optString("body", "")
                            + "\n\n（提交者已撤回 · " + stamp + "）");
                    ghGraphql(MUT_WITHDRAW, vars);
                    runOnUiThread(new Runnable() {
                        public void run() {
                            toast("已撤回这条反馈");
                            mineDirty = true;
                            showScreen(S_MYFEEDBACK);
                        }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        public void run() { toast("撤回失败：" + e.getMessage()); }
                    });
                }
            }
        }).start();
    }

    private void confirmDeleteIssue(final JSONObject issue) {
        confirmAction("删除 #" + issue.optInt("number") + "？",
                "会直接从仓库里删掉这条反馈，删完找不回来。",
                "确认删除", 0xFFE57373, new Runnable() {
                    public void run() { deleteIssue(issue); }
                });
    }

    private void deleteIssue(final JSONObject issue) {
        toast("正在删除…");
        new Thread(new Runnable() {
            public void run() {
                try {
                    JSONObject vars = new JSONObject();
                    vars.put("id", issue.optString("node_id", ""));
                    ghGraphql(MUT_DELETE, vars);
                    runOnUiThread(new Runnable() {
                        public void run() {
                            toast("已删除 #" + issue.optInt("number"));
                            mineDirty = true;
                            showScreen(S_MYFEEDBACK);
                        }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        public void run() {
                            toast("删除失败：" + e.getMessage() + "（需要有仓库写权限）");
                        }
                    });
                }
            }
        }).start();
    }

    /** 危险操作前先确认一次，避免手滑。 */
    private void confirmAction(String title, String msg, String okText, int okColor,
                                    final Runnable yes) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(13), dp(12), dp(13), dp(8));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF151515);
        bg.setCornerRadius(dp(16));
        box.setBackgroundDrawable(bg);

        TextView t = new TextView(this);
        t.setText(title);
        t.setTextSize(m.subtitleSize);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(Color.WHITE);
        t.setGravity(Gravity.CENTER);
        box.addView(t, wrap());

        TextView msgTv = new TextView(this);
        msgTv.setText(msg);
        msgTv.setTextSize(m.subtitleSize - 2f);
        msgTv.setTextColor(C_SUB);
        msgTv.setGravity(Gravity.CENTER);
        msgTv.setPadding(dp(2), dp(6), dp(2), dp(4));
        box.addView(msgTv, wrap());

        TextView ok = new TextView(this);
        ok.setText(okText);
        ok.setTextSize(m.backSize);
        ok.setTypeface(Typeface.DEFAULT_BOLD);
        ok.setGravity(Gravity.CENTER);
        ok.setTextColor(okColor);
        ok.setPadding(dp(10), dp(11), dp(10), dp(11));
        ok.setBackgroundDrawable(pill(C_PILL));
        LinearLayout.LayoutParams olp = wrap();
        olp.topMargin = dp(4);
        box.addView(ok, olp);

        TextView no = new TextView(this);
        no.setText("取消");
        no.setTextSize(m.subtitleSize - 1f);
        no.setGravity(Gravity.CENTER);
        no.setTextColor(C_SUB);
        no.setPadding(dp(8), dp(9), dp(8), dp(2));
        box.addView(no, wrap());

        final AlertDialog d = new AlertDialog.Builder(this).setView(box).create();
        d.setCancelable(true);
        ok.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                vibrate();
                try { d.dismiss(); } catch (Exception ignored) { }
                if (yes != null) yes.run();
            }
        });
        no.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                vibrate();
                try { d.dismiss(); } catch (Exception ignored) { }
            }
        });
        tuneDialogWindow(d);   // 必须在 show() 之前
        d.show();
        sizeDialog(d, 0.86f);
    }

    /** 从正文里找回当初选的反馈类型。 */
    private int parseIssueType(String body) {
        String key = "**类型**：";
        int i = body.indexOf(key);
        if (i >= 0) {
            int nl = body.indexOf('\n', i);
            String v = (nl > 0 ? body.substring(i + key.length(), nl) : body.substring(i + key.length()));
            v = v.trim();
            for (int k = 0; k < FB_TYPES.length; k++) {
                if (FB_TYPES[k].equals(v)) return k;
            }
            // 老反馈用的类型名：映射到新分类（「公式显示」归到「显示问题」）
            for (int k = 0; k < FB_TYPES_OLD.length; k++) {
                if (FB_TYPES_OLD[k].equals(v)) {
                    if ("公式显示".equals(v)) return 0;      // 显示问题
                    if ("内容有误".equals(v)) return 1;
                    if ("闪退卡顿".equals(v)) return 2;
                    if ("功能建议".equals(v)) return 3;
                    return FB_TYPES.length - 1;
                }
            }
        }
        return FB_TYPES.length - 1;
    }

    /** 从正文里取回用户写的那段描述（--- 之后的）。 */
    private String parseIssueDesc(String body) {
        int i = body.indexOf("---");
        if (i < 0) return body.trim();
        String d = body.substring(i + 3);
        while (d.length() > 0 && (d.charAt(0) == '\n' || d.charAt(0) == ' ')) d = d.substring(1);
        return d.trim();
    }

    // ================= 个人主页 =================

    private void buildProfileScreen() {
        profScroll = new OverScrollScrollView(this);
        profScroll.setVerticalScrollBarEnabled(false);
        roundPadScroll(profScroll);
        profInner = new LinearLayout(this);
        profInner.setOrientation(LinearLayout.VERTICAL);
        profScroll.addView(profInner);
        profileScreen.addView(profScroll, lw());

        profInner.addView(makeBack("‹ 返回设置", new View.OnClickListener() {
            public void onClick(View v) { goBack(S_SETTINGS); }
        }), wrapM());

        TextView title = new TextView(this);
        title.setText("个人主页");
        title.setTextSize(m.titleSize);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(C_PRIMARY);
        title.setGravity(Gravity.CENTER);
        profInner.addView(title, wrap());

        profAvatar = new ImageView(this);
        profAvatar.setVisibility(View.GONE);
        profAvatarBox = new FrameLayout(this);
        profAvatarBox.setVisibility(View.GONE);
        profAvatarBox.addView(profAvatar,
                new FrameLayout.LayoutParams(dp(64), dp(64), Gravity.CENTER));
        profAvatarSpin = makeAvatarSpinner(dp(30));
        profAvatarSpin.setVisibility(View.GONE);
        profAvatarBox.addView(profAvatarSpin,
                new FrameLayout.LayoutParams(dp(30), dp(30), Gravity.CENTER));
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(dp(64), dp(64));
        alp.gravity = Gravity.CENTER_HORIZONTAL;
        alp.topMargin = dp(10);
        profInner.addView(profAvatarBox, alp);

        profLogin = new TextView(this);
        profLogin.setTextSize(m.titleSize);
        profLogin.setTypeface(Typeface.DEFAULT_BOLD);
        profLogin.setTextColor(C_PRIMARY);
        profLogin.setGravity(Gravity.CENTER);
        profLogin.setPadding(0, dp(6), 0, 0);
        profLogin.setVisibility(View.GONE);
        profInner.addView(profLogin, wrap());

        profName = new TextView(this);
        profName.setTextSize(m.subtitleSize);
        profName.setTextColor(C_TEXT);
        profName.setGravity(Gravity.CENTER);
        profName.setPadding(0, dp(2), 0, 0);
        profName.setVisibility(View.GONE);
        profInner.addView(profName, wrap());

        profStatusTxt = new TextView(this);
        profStatusTxt.setTextSize(m.subtitleSize - 1f);
        profStatusTxt.setTextColor(C_SUB);
        profStatusTxt.setGravity(Gravity.CENTER);
        profStatusTxt.setPadding(0, dp(6), 0, dp(6));
        profStatusTxt.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                vibrate();
                if (ghToken != null) loadProfileFromGitHub();
            }
        });
        profInner.addView(profStatusTxt, wrap());

        profQr = new ImageView(this);
        profQr.setScaleType(ImageView.ScaleType.FIT_CENTER);
        profQr.setVisibility(View.GONE);
        try {
            InputStream qs = getAssets().open("qr_login.png");
            profQr.setImageBitmap(BitmapFactory.decodeStream(qs));
            qs.close();
        } catch (Exception ignored) { }
        LinearLayout.LayoutParams qlp = new LinearLayout.LayoutParams(dp(112), dp(112));
        qlp.gravity = Gravity.CENTER_HORIZONTAL;
        qlp.topMargin = dp(4);
        profInner.addView(profQr, qlp);

        profCodeBig = new TextView(this);
        profCodeBig.setTextSize(m.titleSize + 4f);
        profCodeBig.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        profCodeBig.setTextColor(C_PRIMARY);
        profCodeBig.setGravity(Gravity.CENTER);
        profCodeBig.setPadding(dp(8), dp(8), dp(8), dp(8));
        profCodeBig.setBackgroundDrawable(pill(0xFF14384A));
        profCodeBig.setVisibility(View.GONE);
        LinearLayout.LayoutParams codeLp = wrap();
        codeLp.topMargin = dp(6);
        profInner.addView(profCodeBig, codeLp);

        profLoginBtn = new TextView(this);
        profLoginBtn.setTextSize(m.backSize);
        profLoginBtn.setTypeface(Typeface.DEFAULT_BOLD);
        profLoginBtn.setGravity(Gravity.CENTER);
        profLoginBtn.setPadding(dp(10), dp(10), dp(10), dp(10));
        profLoginBtn.setBackgroundDrawable(pill(C_PILL));
        applyIcon(profLoginBtn, "ic_person", C_PRIMARY, 15);
        profLoginBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                vibrate();
                if (loginRunning) {
                    if (fbCodeUrl != null) openBrowser(fbCodeUrl);
                    else toast("正在获取登录码…");
                    return;
                }
                if (ghToken == null) startDeviceLogin();
                else logoutGitHub();
            }
        });
        LinearLayout.LayoutParams blp = wrap();
        blp.topMargin = dp(8);
        profInner.addView(profLoginBtn, blp);

        TextView hint = new TextView(this);
        hint.setText("登录仅用于在 App 内提交反馈；token 只存在手表本地，随时可退出。");
        hint.setTextSize(m.subtitleSize - 2.5f);
        hint.setTextColor(C_SUB);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(dp(4), dp(8), dp(4), dp(4));
        profInner.addView(hint, wrap());
        padInner(profInner);
    }

    /** 拉取昵称与头像（头像落盘缓存，之后离线也能显示）。 */
    private void loadProfileFromGitHub() {
        if (ghToken == null) return;
        final String token = ghToken;
        profStatusTxt.setTextColor(C_SUB);
        profStatusTxt.setText("正在加载头像与昵称…");
        showAvatarLoading();
        new Thread(new Runnable() {
            public void run() {
                // 本地存的只是缓存，真正的账号以直连返回的为准。
                // （老版本可能把镜像缓存里的别人账号存了进来，这里顺带自愈。）
                String cachedLogin = ghLogin == null ? "" : ghLogin;
                String login = cachedLogin;
                String name = prefs.getString("gh_name", "");
                String avatar = prefs.getString("gh_avatar", "");
                boolean netFail = false;
                boolean fixed = false;
                try {
                    // 必须直连：镜像会忽略 token 头，/user 会返回缓存里别人的账号
                    JSONObject o = new JSONObject(httpGetAuthed(apiAuthedBase() + GH_USER));
                    String got = o.optString("login", "");
                    if (got.length() == 0) {
                        netFail = true;
                    } else {
                        // 直连是唯一可信来源，无条件以它为准；与本地不同说明本地存错了，直接覆盖
                        if (cachedLogin.length() > 0 && !got.equalsIgnoreCase(cachedLogin)) {
                            fixed = true;
                            // 顺带删掉按旧账号名存的头像文件，免得下次又命中
                            deleteAvatarCache(cachedLogin);
                        }
                        login = got;
                        name = o.optString("name", "");
                        avatar = o.optString("avatar_url", "");
                        prefs.edit().putString("gh_login", login).putString("gh_name", name)
                                .putString("gh_avatar", avatar).apply();
                    }
                } catch (Exception e) {
                    netFail = true;
                }
                Bitmap bm = loadAvatarBitmap(avatar, login);
                final Bitmap fb = bm;
                final String fl = login;
                final String fn = (name == null) ? "" : name;
                final boolean fail = netFail;
                final boolean wasFixed = fixed;
                runOnUiThread(new Runnable() {
                    public void run() {
                        if (!alive() || !token.equals(ghToken)) return;
                        ghLogin = fl;
                        hideAvatarLoading();
                        if (fb != null) {
                            profAvatar.setBackgroundDrawable(null);
                            profAvatar.setImageBitmap(fb);
                        } else {
                            showAvatarPlaceholder();
                        }
                        profLogin.setText(fl.length() == 0 ? "已登录" : "@" + fl);
                        profName.setText(fn.length() == 0 ? (fail ? "（昵称没加载出来）" : "") : fn);
                        if (wasFixed) {
                            profStatusTxt.setTextColor(C_SUB);
                            profStatusTxt.setText("已登录 GitHub，反馈会以这个账号提交（账号已更正）");
                        } else if (fail) {
                            profStatusTxt.setTextColor(0xFFFFB74D);
                            profStatusTxt.setText("网络不佳，头像和昵称没加载出来 · 点此重试");
                        } else {
                            profStatusTxt.setTextColor(C_SUB);
                            profStatusTxt.setText("已登录 GitHub，反馈会以这个账号提交");
                        }
                    }
                });
            }
        }).start();
    }

    /** 头像加载中：深色圆底 + 中间转圈。 */
    private void showAvatarLoading() {
        if (profAvatar == null || profAvatarSpin == null) return;
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(0xFF1B1B1B);
        profAvatarBox.setBackgroundDrawable(g);
        profAvatar.setVisibility(View.INVISIBLE);
        profAvatarSpin.setVisibility(View.VISIBLE);
        profAvatarBox.setVisibility(View.VISIBLE);
    }

    /** 结束转圈（成功、失败、登出都要调，避免转圈停不下来）。 */
    private void hideAvatarLoading() {
        if (profAvatarSpin != null) profAvatarSpin.setVisibility(View.GONE);
        if (profAvatarBox != null) profAvatarBox.setBackgroundDrawable(null);
        if (profAvatar != null) profAvatar.setVisibility(View.VISIBLE);
    }

    /** 头像占位：灰色圆，表示这里本该有头像。 */
    private void showAvatarPlaceholder() {
        if (profAvatar == null) return;
        hideAvatarLoading();
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(0xFF262626);
        profAvatar.setBackgroundDrawable(g);
        profAvatar.setImageDrawable(null);
        profAvatar.setVisibility(View.VISIBLE);
    }

    /** 删掉某个账号名对应的头像缓存文件（账号更正时用）。 */
    private void deleteAvatarCache(String login) {
        if (login == null || login.length() == 0) return;
        try {
            File f = new File(getFilesDir(), "avatar_" + login + ".png");
            if (f.exists()) f.delete();
        } catch (Exception ignored) { }
    }

    /** 个人主页头像：每次都向 GitHub 要新的，网络没拿到才退回本地缓存。
     *  以前是「缓存命中就直接用、且永不过期」，换了头像也一直显示注册时那张（手表上特别明显）；
     *  这里只走官方源，不挂镜像——镜像会缓存别人的头像或旧图，宁可这次不显示。 */
    private Bitmap loadAvatarBitmap(String url, String login) {
        File cache = new File(getFilesDir(), "avatar_" + (login == null || login.length() == 0 ? "me" : login) + ".png");
        try {
            if (url != null && url.length() > 0) {
                List<String> cand = new ArrayList<String>();
                cand.add(url);                                                     // avatars.githubusercontent.com/...
                if (login != null && login.length() > 0) {
                    cand.add("https://github.com/" + login + ".png?size=200");     // 官方入口 302 到 avatars，多一条官方路
                }
                for (String u : cand) {
                    byte[] data = httpGetBytesQuiet(u);
                    if (data == null || data.length < 512) continue;
                    Bitmap src = BitmapFactory.decodeByteArray(data, 0, data.length);
                    if (src == null || src.getWidth() < 8) continue;
                    try {
                        FileOutputStream fos = new FileOutputStream(cache);
                        fos.write(data);
                        fos.close();
                    } catch (Exception ignored) { }
                    return circleCrop(src);
                }
            }
            // 网络没拿到，用缓存兜底：可能是旧的，但总比一片空白强
            if (cache.exists() && cache.length() > 0) {
                Bitmap b = BitmapFactory.decodeFile(cache.getAbsolutePath());
                if (b != null) return circleCrop(b);
            }
        } catch (Exception ignored) { }
        return null;
    }

    /** 取字节，失败不抛：头像只是点缀，一个源不通就换下一个。
     *  超时单独收紧（头像几 KB，3+5 秒够了），别沿用更新包的 6+9 秒。 */
    private byte[] httpGetBytesQuiet(String url) {
        java.net.HttpURLConnection cn = null;
        try {
            cn = (java.net.HttpURLConnection) new URL(url).openConnection();
            cn.setConnectTimeout(3000);
            cn.setReadTimeout(5000);
            cn.setInstanceFollowRedirects(true);
            cn.setRequestProperty("User-Agent", "HZKnowledge-Android");
            java.io.InputStream is = cn.getInputStream();
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] b = new byte[8192];
            int n;
            while ((n = is.read(b)) > 0) bos.write(b, 0, n);
            is.close();
            return bos.toByteArray();
        } catch (Exception e) {
            return null;
        } finally {
            if (cn != null) cn.disconnect();
        }
    }

    /** 裁成圆形头像。 */
    private Bitmap circleCrop(Bitmap src) {
        try {
            int size = Math.min(src.getWidth(), src.getHeight());
            Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(out);
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setShader(new BitmapShader(src, BitmapShader.TileMode.CLAMP, BitmapShader.TileMode.CLAMP));
            float r = size / 2f;
            c.drawCircle(r, r, r, p);
            return out;
        } catch (Exception e) {
            return src;
        }
    }
}
