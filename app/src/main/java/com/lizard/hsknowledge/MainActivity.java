package com.lizard.hsknowledge;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
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

import java.io.ByteArrayOutputStream;
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
        String body = "";                       // 叶子正文（单段，多行用换行）
        List<Node> children = new ArrayList<Node>();
        List<ExtraBlock> extras = new ArrayList<ExtraBlock>();
        int star; String img, d;
        String daily;                          // 非空 = 每日一句入口（phil/poem），句子走接口
        Node parent; Subject subj;
        int savedY = -1;                        // 离开时记下列表位置，返回时恢复
        boolean isLeaf() { return children.isEmpty(); }
        /** 列表行显示的短标记，由数据里的 mark 汇总（例 → 「（例）」），界面不自造文案。 */
        String marks() {
            LinkedHashSet<String> set = new LinkedHashSet<String>();
            for (ExtraBlock b : extras) if (b.mark != null && b.mark.length() > 0) set.add(b.mark);
            return TextUtils.join("/", set);
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

    private final List<Subject> subjects = new ArrayList<Subject>();
    private final Map<String, Subject> subjectMap = new HashMap<String, Subject>();
    private final List<Node> allHits = new ArrayList<Node>();
    /** 所有分组节点（有下级的），供全局搜索按标题命中；点进去就是那一层的列表。 */
    private final List<Node> allGroups = new ArrayList<Node>();
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
            S_AI_WEB = 18, S_AI_HUB = 19, S_AI_API_CFG = 20, S_AI_WEB_CFG = 21;
    private static final int SCREEN_COUNT = 22;
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
    /** 从哪个页面点进来的：返回时原路退回，而不是一律回主页。 */
    private int aiWebBack = S_SUBJECT;
    /** 两个设置子页从哪进来的：设置 → 问 AI 子页，或主页「问 AI」直接进来。 */
    private int aiCfgBack = S_AI_HUB;
    /** 网页只加载一次；反复进出不打断正在进行的对话。 */
    private boolean aiWebLoaded = false;
    /** 是否往网页注入「隐藏下载 APP 入口」脚本；关掉 = 网页完全原样，用于排查和兜底。 */
    private boolean aiWebHideDl = true;
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
    private TextView detailBackBtn, settingsBackBtn, extraBackBtn;
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

    // 收藏与历史记录（都按节点稳定编号记在本地）
    private LinearLayout favScreen, histScreen, favSubScreen;
    private SvList favList, histList, favSubList;
    private TextView favTitle, histTitle, favSubTitle;
    /** 收藏统一成一套：分组节点和叶子知识点都能收藏，存的是稳定编号。 */
    private final LinkedHashSet<String> favIds = new LinkedHashSet<String>();
    private final List<String> histIds = new ArrayList<String>();
    /** 稳定编号 → 节点（分组与叶子都在内）；收藏页、搜索、历史、今日都查它。 */
    private final Map<String, Node> nodeById = new HashMap<String, Node>();
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
    private long lastVibrate = 0;           // 节流：避免连点狂震
    private boolean swipeTracking = false;
    private boolean swipeActive = false;     // 已进入拖动返回
    private boolean swipeBusy = false;       // 出场动画进行中
    private boolean sliderTouching = false;  // 正在拖动滑块（字号/缩放），此时不触发右滑返回
    private boolean swipeFromInput = false;  // 起手点在输入框内
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
        aiMode = prefs.getString("ai_mode", "web");
        aiWebHideDl = prefs.getBoolean("ai_web_hide_dl", true);
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
        root.getViewTreeObserver().addOnPreDrawListener(
                new android.view.ViewTreeObserver.OnPreDrawListener() {
                    public boolean onPreDraw() {
                        root.getViewTreeObserver().removeOnPreDrawListener(this);
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
                cleanupOldApk();     // 装完新版本后把遗留的安装包删掉
                loadData();
                loadMarks();
                loadOrderKeys();
                loadDailyMem();      // 上次取到的句子（重启后先拿它垫上，不至于一片空白）
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
        buildUi();               // 有开屏容器时，root 是垫在开屏下面加进去的
        boolean tuned = prefs.getBoolean("display_tuned", false);
        if (!tuned) tuningFirstRun = true;
        skipPageAnim = true;     // 首屏直接显示，不做淡入（开屏还在上面盖着）
        if (!tuned) {
            showScreen(S_DISPLAY);       // 第一次装：先进显示调节，让用户把字号调舒服
        } else {
            showScreen(S_SUBJECT);
        }
        hideBootSplash();
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
        if (id == null) return "";
        String[] p = id.split("-");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < p.length; i++) {
            if (p[i].matches("\\d{6}")) continue;   // 跳过 6 位日期段
            if (sb.length() > 0) sb.append("-");
            sb.append(p[i]);
        }
        return sb.toString();
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
            JSONObject orderObj = readJsonAsset("data/order.json");
            JSONArray orderArr = orderObj.getJSONArray("order");
            for (int i = 0; i < orderArr.length(); i++) {
                try {
                    JSONObject oe = orderArr.getJSONObject(i);
                    String key = oe.optString("s", "");
                    String name = oe.optString("name", key);
                    JSONObject subjFile = readJsonAsset("data/" + key + ".json");
                    JSONObject so = subjFile.optJSONObject("subject");
                    if (so == null) continue;
                    so.put("s", key);
                    so.put("name", name);
                    Subject s = parseSubject(so);
                    subjects.add(s);
                    subjectMap.put(s.key, s);
                } catch (Exception ignored) { }
            }
            buildHits();
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
                        if (!silent) toast("已是最新版 v" + local);
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
        box.getViewTreeObserver().addOnGlobalLayoutListener(
                new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
                    public void onGlobalLayout() {
                        int want = body.getMeasuredHeight() + dp(6);   // 多 6dp，避免行距/标点的误差裁掉末行
                        int h = Math.min(want, maxH);
                        if (sc.getLayoutParams() != null && sc.getLayoutParams().height != h) {
                            sc.getLayoutParams().height = h;
                            sc.requestLayout();
                        }
                        // 只量一次：正文高度不会变了，量完就撤监听，免得改高度触发重布局又回调
                        try {
                            box.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                        } catch (Exception ignored) { }
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

    /** 直连失败时换支持 API 转发的镜像取 release 信息。 */
    private JSONObject fetchLatestRelease() throws Exception {
        Exception last = null;
        // 必须带时间戳参数：镜像普遍缓存 /releases/latest，不带的话刚发版也会拿回上一个版本
        String q = "?nocache=" + System.currentTimeMillis();
        // 先走启动时测出来的最优通道，多数情况一步就中
        try {
            return new JSONObject(httpGetText(apiBase() + RELEASES_API + q, 5000, 6000));
        } catch (Exception e) { last = e; }
        for (int i = 0; i <= API_MIRRORS.length; i++) {
            String u = (i == 0) ? RELEASES_API : API_MIRRORS[i - 1] + RELEASES_API;
            try {
                return new JSONObject(httpGetText(u + q, 5000, 6000));
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
        return 0;
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
                cn.setConnectTimeout(8000);
                cn.setReadTimeout(20000);
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
                    fos.write(b, 0, n);
                    done += n;
                    if (done < 150 * 1024 && System.currentTimeMillis() - t0 > 15000) {
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
        log("APP", "开始记录日志 · 版本 " + appVersion() + "（build " + appVersionCode() + "）· "
                + Build.MODEL + " · Android " + Build.VERSION.RELEASE + " · " + (m.round ? "圆屏" : "方屏"));
    }

    /** 关闭日志记录。 */
    private void stopLogging() {
        if (logEnabled) log("APP", "停止记录日志");
        logEnabled = false;
    }

    /** 全局崩溃捕获：只在日志开启时写堆栈，其余情况交给系统默认处理。 */
    private void installCrashHandler() {
        final Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
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
                try { d.dismiss(); } catch (Exception ignored) { }
            }
        });
        btn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (!btn.isEnabled()) return;
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
        buildFeedbackScreen();
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
        buildProfileScreen();
        root.addView(profileScreen, fp());

        changelogScreen = newScreen();
        buildChangelogScreen();
        root.addView(changelogScreen, fp());

        aboutScreen = newScreen();
        buildAboutScreen();
        root.addView(aboutScreen, fp());

        displayScreen = newScreen();
        buildDisplayScreen();
        root.addView(displayScreen, fp());

        todayScreen = newScreen();
        buildTodayScreen();
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
        buildAiWebScreen();
        root.addView(aiWebScreen, fp());
        aiHubScreen = newScreen();
        buildAiHubScreen();
        root.addView(aiHubScreen, fp());
        aiApiScreen = newScreen();
        buildAiApiScreen();
        root.addView(aiApiScreen, fp());
        aiWebCfgScreen = newScreen();
        buildAiWebCfgScreen();
        root.addView(aiWebCfgScreen, fp());

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
            WebView wv = new AiWebView(this);
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

    // ---- 双指手势：轻点两下切裁切版/原版，长按 2 秒退出 ----
    /** 进页面时把手势说明显示一会儿再淡出：第一次进来要看清楚，之后每次只闪 1 秒。
     *  设置页里另有一份常驻说明。 */
    private void showAiGestureHint() {
        if (aiGestureHint == null) return;
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
                aiWebCancelChildGesture();
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

    /** 给网页补一个 ACTION_CANCEL：告诉它「这一串触摸作废了」，撤掉正在走的长按 / 双击识别。 */
    private void aiWebCancelChildGesture() {
        long now = SystemClock.uptimeMillis();
        MotionEvent cancel = MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, 0f, 0f, 0);
        try { super.dispatchTouchEvent(cancel); }
        catch (Throwable ignored) { }
        cancel.recycle();
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
                aiMode = "api".equals(aiMode) ? "web" : "api";
                prefs.edit().putString("ai_mode", aiMode).apply();
                markSubjectsDirty();
                renderAiHub();
            }
        }), wrap());
        aiHubInner.addView(aiHint("当前：" + (api ? "API AI（自行配置）" : "DeepSeek 网页版")
                + "。关 = 网页版，开 = 自行配置；切换后主页「问 AI」走对应模式。"), wrap());

        aiHubInner.addView(aiSettingBtn("API AI 设置", new View.OnClickListener() {
            public void onClick(View v) { aiCfgBack = S_AI_HUB; showScreen(S_AI_API_CFG); }
        }), wrap());
        LinearLayout.LayoutParams lp2 = wrap();
        lp2.topMargin = dp(6);
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
        aiApiInner.addView(sectionHeader("自行配置模式"), wrap());
        aiApiInner.addView(aiHint("用 OpenAI 兼容接口，地址和密钥由你自己填，模型通过接口拉取并用来测试密钥是否正确。"), wrap());
        aiApiInner.addView(aiHint("当前状态：正在接入。本版请先用 DeepSeek 网页版。"), wrap());
        aiApiInner.addView(aiHint("接入后这里可填地址、密钥、测试连接并拉取模型；对话页里可切模型、调思考强度、上下文轮数、最大回复长度和温度。"), wrap());
        padInner(aiApiInner);
        if (keepY >= 0) scrollToY(aiApiScroll, keepY);
        else scrollToTop(aiApiScroll);
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
        aiWebCfgInner.addView(aiHint("手势：双指轻点两下切「裁切版 / 原版」（四边避开圆屏 / 网页铺满整屏）；双指长按 2 秒退出问 AI。当前：" + (aiWebCrop ? "裁切版" : "原版") + "。网页页没有返回和刷新按钮，系统返回键照常可用；网页里的长按复制、自动填充已关掉。"), wrap());
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

        aiWebCfgInner.addView(sectionHeader("登录"), wrap());
        aiWebCfgInner.addView(aiSettingBtn("重新登录（清除网页登录态）", new View.OnClickListener() {
            public void onClick(View v) { aiWebRelogin(); }
        }), wrap());
        aiWebCfgInner.addView(aiHint("登录由网页自己管理，App 不保存密钥。清除后下次打开要重新登录。"), wrap());

        padInner(aiWebCfgInner);
        if (keepY >= 0) scrollToY(aiWebCfgScroll, keepY);
        else scrollToTop(aiWebCfgScroll);
    }

    /** 主页「问 AI」按当前模式分流；实时读 aiMode，切换后不用重绘主页列表也生效。 */
    private void openAiFromHome() {
        if ("api".equals(aiMode)) {
            aiCfgBack = S_SUBJECT;
            showScreen(S_AI_API_CFG);
            toast("自行配置模式的对话还在接入，本版请先用 DeepSeek 网页版");
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

    /** 滚到指定位置。内容分帧渲染时可能还没铺够高，scrollTo 会被钳制到半路，
     *  所以没到位就下一帧再定一次（最多 5 次）；期间用户自己滑过（位置已超过目标）就不再动。 */
    private void scrollToY(final ScrollView sc, final int y) {
        final int[] tries = new int[]{0};
        final Runnable r = new Runnable() {
            public void run() {
                sc.scrollTo(0, y);
                View child = sc.getChildAt(0);
                int range = (child == null ? 0 : child.getHeight()) - sc.getHeight();
                if (tries[0]++ < 5 && y > 0 && sc.getScrollY() < y && range >= y) sc.post(this);
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
        /** 续载时先让外部把下一批数据补进来（数据全集不是一次就有的列表用，如「今日新增」）。 */
        Runnable fill;

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
                    if (!selfScroll) pendingY = -1;   // 用户自己滑了，就不再替他定位
                    if (nearEnd()) more();
                }
            });
        }

        /** 顶部占位之后、底部占位之前的固定头部（返回键、标题、搜索框），随内容一起滚动。
         *  和行一样留 6dp 底边距，否则返回键、搜索框会跟下面一行贴在一起。 */
        void addHead(View v) {
            heads.add(v);
            box.addView(v, box.indexOfChild(botSp), wrapM());
        }

        /** 换一批数据：清空已有行，从头分页渲染。gen 换了旧的续载帧会自己退出，不会串到新数据上。 */
        void bind(RowSource s) {
            clearRows();
            src = s;
            shown = 0;
            gen++;
            want = Math.min(s == null ? 0 : s.size(), first);
            if (want > 0) {
                stepGen = gen;
                busy = true;
                sc.post(step);
            } else {
                busy = false;
            }
            // 空结果也要收一次占位（没有行就不会走 step），否则内容孤零零停在屏幕中间
            sc.post(new Runnable() { public void run() { fitSpacers(); } });
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
            savedY = -1;
            pendingY = overDist();
            locate();
        }

        /** 返回到本页：回到离开时的位置；没记过就按首次进入处理。 */
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
                }
            });
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


    /** 页面上的小按钮：胶囊底、居中、点了有反馈。 */
    private TextView dailyBtn(String text, View.OnClickListener l) {
        TextView b = new TextView(this);
        b.setText(text);
        b.setTextSize(m.subtitleSize - 1f);
        b.setTextColor(C_PRIMARY);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(4), dp(7), dp(4), dp(7));
        applyClickBg(b, C_PILL);
        b.setOnClickListener(l);
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
            openDetail(n.name, n.body, n.path(), S_NODE, n.img, n.extras, n.id);
            return;
        }
        navStack.add(n);
        curNode = n;
        renderNodes();
        showScreen(S_NODE);
        slideNodeList(1);
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
            openDetail(n.name, n.body, n.path(), back, n.img, n.extras, n.id);
        } else {
            showScreen(S_NODE);
            slideNodeList(1);
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
        slideNodeList(1);
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
            slideNodeList(-1);
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
        itemSearchBox.setText("");
        setPath(listTitle, currentPath());
        bindNodes();
    }

    private void refreshNodes() {
        if (screen != S_NODE) return;   // 搜索框只在节点页生效
        bindNodes();
    }

    /** 节点页内部换层（进下一级 / 退回上一级）时的轻动画：列表跟手方向轻微滑入，
     *  让人一眼看出是往里进还是往外退。dir = 1 进、-1 退。 */
    private void slideNodeList(int dir) {
        if (nodeList == null || nodeList.sc == null) return;
        View v = nodeList.sc;
        v.animate().cancel();
        v.setTranslationX(dir * dp(7));
        v.setAlpha(0.55f);
        v.animate().translationX(0f).alpha(1f)
                .setDuration(150)
                .setInterpolator(new DecelerateInterpolator(1.6f))
                .start();
    }

    /** 真正把当前层的节点绑到列表；renderNodes 与 refreshNodes 共用。 */
    private void bindNodes() {
        String q = itemSearchBox.getText().toString().trim();
        shownNodes.clear();
        for (Node n : currentNodes()) {
            if (q.length() == 0) { shownNodes.add(n); continue; }
            if (n.name.contains(q)) { shownNodes.add(n); continue; }
            if (n.isLeaf()) {
                if (n.body.contains(q)) shownNodes.add(n);
            } else {
                // 分组：名字没命中时，若它的后代有命中的也列出来
                if (subtreeMatches(n, q)) shownNodes.add(n);
            }
        }
        nodeList.bind(new RowSource() {
            public int size() { return shownNodes.size(); }
            public View make(final int i) { return nodeRow(shownNodes.get(i)); }
        });
    }

    private boolean subtreeMatches(Node n, String q) {
        for (Node c : n.children) {
            if (c.name.contains(q)) return true;
            if (c.isLeaf()) {
                if (c.body.contains(q)) return true;
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
            String blob = (n.name + " " + (n.body == null ? "" : n.body)).toLowerCase();
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
            public void onClick(View v) { closeFullscreen(); }
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
                exBtn.setText(blk.unit.length() > 0
                        ? blk.name + " · " + blk.items.size() + " " + blk.unit
                        : blk.name + " · " + blk.items.size());
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
            "Rightarrow","⇒","Leftarrow","⇐","Leftrightarrow","⇔","implies","⇒","iff","⇔",
            "longrightarrow","⟶","longrightarrow","⟶","uparrow","↑","downarrow","↓",
            "nearrow","↗","searrow","↘","mapsto","↦",
            "angle","∠","perp","⊥","parallel","∥","therefore","∴","because","∵",
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
            if (c != '\\') { out.append(c); i++; continue; }
            int j = i + 1;
            if (j >= n) { out.append(c); i++; continue; }
            char c1 = s.charAt(j);
            if (!Character.isLetter(c1)) {
                if (c1 == ',' || c1 == ';' || c1 == ':' || c1 == ' ' || c1 == '!') { out.append(' '); }
                else if (c1 == '\\') { out.append('\n'); }
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
            if ("sqrt".equals(cmd)) {
                int p = k;
                String idx = "";
                if (p < n && s.charAt(p) == '[') {
                    int e = s.indexOf(']', p);
                    if (e > 0) { idx = latexToText(s.substring(p + 1, e)); p = e + 1; }
                }
                String a = latexToText(readArg(s, p, used));
                // 根号用占位符保留结构，由 UI 画成带顶部横线的根式
                if (idx.length() > 0) out.append(idx);
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
            out.append('\\').append(cmd);
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
            float asc = barUp + gap + a[1] + a[2];
            float desc = Math.max(b[1] + b[2] + gap - barUp, s2 * 0.18f);
            return new float[]{w, asc, desc};
        }
        if (grp.type == 4) {
            float[] in = measList(grp.kids.get(0), p, size);
            float hook = size * 0.42f;
            float w = hook + in[0] + size * 0.12f;
            float asc = in[1] + size * 0.18f;
            float desc = Math.max(in[2], size * 0.06f) + size * 0.10f;
            return new float[]{w, asc, desc};
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
            drawCentered(c, p, t.kids.get(0), cx, barY - gap - a[2], s2, color);
            Paint.Style oldStyle = p.getStyle();
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(1.2f, s2 * 0.07f));
            p.setColor(color);
            float half = Math.max(a[0], b[0]) / 2f + s2 * 0.10f;
            c.drawLine(cx - half, barY, cx + half, barY, p);
            p.setStyle(oldStyle);
            drawCentered(c, p, t.kids.get(1), cx, barY + gap + b[1], s2, color);
        } else if (t.type == 4) {
            float[] in = measList(t.kids.get(0), p, size);
            float hook = size * 0.42f;
            float innerX = x + hook;
            drawList(c, p, t.kids.get(0), innerX, base, size, color);
            // 根号一笔画完：顶部短横 → 向左下撇到最低点 → 从最低点回笔升到顶部 → 长横线盖住被开方数。
            // 关键是不能省掉「回笔」那一笔，少了它就只剩「一捺加一横」，根本不像根号。
            float top = base - in[1] - size * 0.12f;
            float bot = base + Math.max(in[2], size * 0.06f) + size * 0.08f;
            float x0 = x;                                   // 短横左端
            float xs = x + hook * 0.30f;                    // 短横右端，撇从这开始
            float xl = x + hook * 0.10f;                    // 最低点
            float xr = x + hook * 0.62f;                    // 回笔升回顶部，长横线从这起
            float right = innerX + in[0] + size * 0.10f;    // 长横线右端
            Path rad = new Path();
            rad.moveTo(x0, top);
            rad.lineTo(xs, top);
            rad.lineTo(xl, bot);
            rad.lineTo(xr, top);
            rad.lineTo(right, top);
            Paint.Style oldStyle = p.getStyle();
            Paint.Cap oldCap = p.getStrokeCap();
            Paint.Join oldJoin = p.getStrokeJoin();
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
            p.setStrokeWidth(Math.max(1.3f, size * 0.06f));
            p.setColor(color);
            c.drawPath(rad, p);
            p.setStyle(oldStyle);
            p.setStrokeCap(oldCap);
            p.setStrokeJoin(oldJoin);
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

        InlineSpan(Inl root) { this(root, 0, false); }

        InlineSpan(Inl root, int script) { this(root, script, false); }

        InlineSpan(Inl root, int script, boolean hi) {
            this.root = root;
            this.script = script;
            this.hi = hi;
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
            applyHi(paint);
            drawInl(canvas, paint, root, x, baseOf(orig, y), sizeOf(orig), paint.getColor());
            paint.setTextSize(orig);
            clearHi(paint, color, ob);
            paint.setTextAlign(Paint.Align.LEFT);
            paint.setStyle(Paint.Style.FILL);
        }
    }

    private SpannableStringBuilder applyMarkup(String src) {
        return applyMarkup(src, false);
    }

    /** @param hl 当前是否处在 **重点标亮** 内；公式不会自动继承颜色 span，需显式传递。 */
    private SpannableStringBuilder applyMarkup(String src, boolean hl) {
        SpannableStringBuilder ssb = new SpannableStringBuilder();
        String txt = latexToText(src);
        int i = 0, n = txt.length();
        while (i < n) {
            char c = txt.charAt(i);
            if (c == '\uE000' || c == '\uE003') {
                int[] b = findBlock(txt, i + 1);
                if (b[1] > i) {
                    Inl root = Inl.group(parseInl(txt.substring(i, b[1] + 1)));
                    int st = ssb.length();
                    ssb.append('\u00A0');
                    ssb.setSpan(new InlineSpan(root, 0, hl), st, ssb.length(),
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
                    Inl root = Inl.group(parseInl(txt.substring(i, e + 1)));
                    int st = ssb.length();
                    ssb.append('\u00A0');
                    ssb.setSpan(new InlineSpan(root, 0, hl), st, ssb.length(),
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
    private void renderSettings() {
        // 原地重绘（调科目顺序、切开关）时保住当前滚动位置：以前末尾无条件 scrollToTop，
        // 点一次 ▲▼ 就被弹回最上面，得重新往下滑到排序那一块才能接着调。
        int keepY = (settingsInner.getChildCount() > 0) ? settingsScroll.getScrollY() : -1;
        settingsInner.removeAllViews();
        if (overDist() > 0) settingsInner.addView(makeScrollSpacer());
        settingsInner.addView(settingsBackBtn, wrapM());
        settingsInner.addView(sectionHeader("显示"), wrap());
        TextView displayEntry = new TextView(this);
        displayEntry.setText("显示调节");
        displayEntry.setTextSize(m.backSize);
        displayEntry.setTypeface(Typeface.DEFAULT_BOLD);
        displayEntry.setTextColor(C_PRIMARY);
        displayEntry.setGravity(Gravity.CENTER);
        displayEntry.setPadding(dp(10), dp(10), dp(10), dp(10));
        displayEntry.setBackground(clickBg(C_PILL));
        applyIcon(displayEntry, "ic_settings", C_PRIMARY, 15);
        displayEntry.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                vibrate();
                tuningFirstRun = false;
                showScreen(S_DISPLAY);
            }
        });
        settingsInner.addView(displayEntry, wrap());

        TextView displayHint = new TextView(this);
        displayHint.setText("调整字号和整体缩放。");
        displayHint.setTextSize(m.subtitleSize - 2f);
        displayHint.setTextColor(C_SUB);
        displayHint.setGravity(Gravity.CENTER);
        displayHint.setPadding(dp(4), dp(3), dp(4), dp(2));
        settingsInner.addView(displayHint, wrap());

        settingsInner.addView(toggleRow("震动反馈", haptic, new View.OnClickListener() {
            public void onClick(View v) {
                haptic = !haptic;
                prefs.edit().putBoolean("haptic", haptic).apply();
                renderSettings();
            }
        }), wrap());
        TextView hapticHint = new TextView(this);
        hapticHint.setText("点击时轻微震动。");
        hapticHint.setTextSize(m.subtitleSize - 2f);
        hapticHint.setTextColor(C_SUB);
        hapticHint.setGravity(Gravity.CENTER);
        hapticHint.setPadding(dp(4), dp(2), dp(4), dp(4));
        settingsInner.addView(hapticHint, wrap());

        settingsInner.addView(toggleRow("右滑返回", swipeBack, new View.OnClickListener() {
            public void onClick(View v) {
                swipeBack = !swipeBack;
                prefs.edit().putBoolean("swipe_back", swipeBack).apply();
                renderSettings();
            }
        }), wrap());
        TextView swipeHint = new TextView(this);
        swipeHint.setText("从左边缘向右滑，返回上一页。");
        swipeHint.setTextSize(m.subtitleSize - 2f);
        swipeHint.setTextColor(C_SUB);
        swipeHint.setGravity(Gravity.CENTER);
        swipeHint.setPadding(dp(4), dp(2), dp(4), dp(4));
        settingsInner.addView(swipeHint, wrap());

        settingsInner.addView(sectionHeader("常用"), wrap());
        TextView upd = new TextView(this);
        upd.setText("检查更新");
        upd.setTextSize(m.backSize);
        upd.setTextColor(C_PRIMARY);
        upd.setGravity(Gravity.CENTER);
        upd.setPadding(dp(10), dp(10), dp(10), dp(10));
        upd.setBackground(clickBg(C_PILL));
        applyIcon(upd, "ic_update", C_PRIMARY, 15);
        upd.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                checkUpdate(false);
            }
        });
        settingsInner.addView(upd, wrap());

        TextView updHint = new TextView(this);
        updHint.setText("下载并安装新版本。");
        updHint.setTextSize(m.subtitleSize - 2f);
        updHint.setTextColor(C_SUB);
        updHint.setGravity(Gravity.CENTER);
        updHint.setPadding(dp(4), dp(3), dp(4), dp(2));
        settingsInner.addView(updHint, wrap());
        TextView fbEntry = new TextView(this);
        fbEntry.setText("反馈与建议");
        fbEntry.setTextSize(m.backSize);
        fbEntry.setTextColor(C_PRIMARY);
        fbEntry.setGravity(Gravity.CENTER);
        fbEntry.setPadding(dp(10), dp(10), dp(10), dp(10));
        fbEntry.setBackground(clickBg(C_PILL));
        applyIcon(fbEntry, "ic_feedback", C_PRIMARY, 15);
        fbEntry.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { goBack(S_FEEDBACK); }
        });
        LinearLayout.LayoutParams fbLp = wrap();
        fbLp.topMargin = dp(6);
        settingsInner.addView(fbEntry, fbLp);
        TextView fbHint = new TextView(this);
        fbHint.setText("提交问题或建议，需要 GitHub 账号。");
        fbHint.setTextSize(m.subtitleSize - 2f);
        fbHint.setTextColor(C_SUB);
        fbHint.setGravity(Gravity.CENTER);
        fbHint.setPadding(dp(4), dp(3), dp(4), dp(2));
        settingsInner.addView(fbHint, wrap());

        // 设置主页里「问 AI」只留一个入口，模式切换、两种设置和重新登录都收进子页
        settingsInner.addView(sectionHeader("问 AI"), wrap());
        TextView aiEntry = new TextView(this);
        aiEntry.setText("问 AI");
        aiEntry.setTextSize(m.backSize);
        aiEntry.setTypeface(Typeface.DEFAULT_BOLD);
        aiEntry.setTextColor(C_PRIMARY);
        aiEntry.setGravity(Gravity.CENTER);
        aiEntry.setPadding(dp(10), dp(10), dp(10), dp(10));
        aiEntry.setBackground(clickBg(C_PILL));
        aiEntry.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { showScreen(S_AI_HUB); }
        });
        settingsInner.addView(aiEntry, wrap());

        settingsInner.addView(sectionHeader("其他"), wrap());
        TextView profEntry = new TextView(this);
        profEntry.setText("个人主页");
        profEntry.setTextSize(m.backSize);
        profEntry.setTypeface(Typeface.DEFAULT_BOLD);
        profEntry.setTextColor(C_PRIMARY);
        profEntry.setGravity(Gravity.CENTER);
        profEntry.setPadding(dp(10), dp(10), dp(10), dp(10));
        profEntry.setBackground(clickBg(C_PILL));
        applyIcon(profEntry, "ic_person", C_PRIMARY, 15);
        profEntry.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { showScreen(S_PROFILE); }
        });
        settingsInner.addView(profEntry, wrap());

        TextView orderHint = new TextView(this);
        orderHint.setText("主页科目顺序，点 ↑↓ 调整。");
        orderHint.setTextSize(m.subtitleSize - 2f);
        orderHint.setTextColor(C_SUB);
        orderHint.setGravity(Gravity.CENTER);
        orderHint.setPadding(dp(4), dp(2), dp(4), dp(4));
        settingsInner.addView(orderHint, wrap());
        for (int idx = 0; idx < orderKeys.size(); idx++) {
            settingsInner.addView(orderRow(idx), wrap());
        }

        // 记录日志开关：点击后由 toggleRow 自己震动，这里只处理状态切换。
        // 上面是排序行（一行挨一行没有外边距），这里要自己留出间隔，否则两块会贴在一起
        LinearLayout.LayoutParams logLp = wrap();
        logLp.topMargin = dp(8);
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
        }), logLp);
        TextView logHint = new TextView(this);
        logHint.setText("开启后在本机记录运行日志，方便排查问题。");
        logHint.setTextSize(m.subtitleSize - 2f);
        logHint.setTextColor(C_SUB);
        logHint.setGravity(Gravity.CENTER);
        logHint.setPadding(dp(4), dp(3), dp(4), dp(2));
        settingsInner.addView(logHint, wrap());

        TextView routeEntry = new TextView(this);
        routeEntry.setText("重新连接服务器");
        routeEntry.setTextSize(m.backSize);
        routeEntry.setTextColor(C_PRIMARY);
        routeEntry.setGravity(Gravity.CENTER);
        routeEntry.setPadding(dp(10), dp(10), dp(10), dp(10));
        routeEntry.setBackground(clickBg(C_PILL));
        applyIcon(routeEntry, "ic_route", C_PRIMARY, 15);
        routeEntry.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                vibrate();
                if (routeHint != null) {
                    routeHint.setTextColor(C_SUB);
                    routeHint.setText("正在检测各条线路…");
                }
                startRouteProbe(true);
            }
        });
        LinearLayout.LayoutParams rtLp = wrap();
        rtLp.topMargin = dp(6);
        settingsInner.addView(routeEntry, rtLp);

        routeHint = new TextView(this);
        routeHint.setGravity(Gravity.CENTER);
        routeHint.setTextSize(m.subtitleSize - 2f);
        routeHint.setTextColor(C_SUB);
        routeHint.setPadding(dp(4), dp(3), dp(4), dp(2));
        settingsInner.addView(routeHint, wrap());
        refreshRouteHint();

        settingsInner.addView(sectionHeader("关于"), wrap());
        TextView aboutEntry = new TextView(this);
        aboutEntry.setText("关于本应用");
        aboutEntry.setTextSize(m.backSize);
        aboutEntry.setTypeface(Typeface.DEFAULT_BOLD);
        aboutEntry.setTextColor(C_PRIMARY);
        aboutEntry.setGravity(Gravity.CENTER);
        aboutEntry.setPadding(dp(10), dp(10), dp(10), dp(10));
        aboutEntry.setBackground(clickBg(C_PILL));
        applyIcon(aboutEntry, "ic_info", C_PRIMARY, 15);
        aboutEntry.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                vibrate();
                showScreen(S_ABOUT);
            }
        });
        settingsInner.addView(aboutEntry, wrap());
        TextView aboutHint = new TextView(this);
        aboutHint.setText("版本、更新日志与感谢名单。");
        aboutHint.setTextSize(m.subtitleSize - 2f);
        aboutHint.setTextColor(C_SUB);
        aboutHint.setGravity(Gravity.CENTER);
        aboutHint.setPadding(dp(4), dp(3), dp(4), dp(2));
        settingsInner.addView(aboutHint, wrap());

        TextView reset = new TextView(this);
        reset.setText("恢复默认设置");
        reset.setTextSize(m.backSize);
        reset.setTextColor(0xFFFFB74D);
        reset.setGravity(Gravity.CENTER);
        reset.setPadding(dp(10), dp(10), dp(10), dp(10));
        reset.setBackground(clickBg(C_PILL));
        reset.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // 误触代价太大（以前一点就直接执行，还连收藏一起清掉），先确认
                confirmAction("恢复默认设置？",
                        "字号、缩放、震动、右滑返回和科目顺序回到默认。收藏、浏览记录、每日一句和登录都保留。",
                        "确认恢复", 0xFFE57373, new Runnable() {
                            public void run() { resetSettingsToDefault(); }
                        });
            }
        });
        LinearLayout.LayoutParams rlp = wrap();
        rlp.topMargin = dp(12);
        settingsInner.addView(reset, rlp);
        if (overDist() > 0) settingsInner.addView(makeScrollSpacer());
        if (keepY >= 0) scrollToY(settingsScroll, keepY);   // 重绘：回到原来的位置
        else scrollToTop(settingsScroll);                   // 首次进入：贴顶
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
        row.setBackgroundDrawable(pill(C_CARD));
        LinearLayout.LayoutParams rlp = wrap();
        rlp.bottomMargin = dp(4);

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
                saveOrderKeys(); markSubjectsDirty(); renderSettings();
            }
        });
        TextView dn = orderBtn("▼", idx < orderKeys.size() - 1, new View.OnClickListener() {
            public void onClick(View v) {
                if (idx >= orderKeys.size() - 1) return;
                String k = orderKeys.remove(idx);
                orderKeys.add(idx + 1, k);
                saveOrderKeys(); markSubjectsDirty(); renderSettings();
            }
        });

        row.addView(nm);
        row.addView(up);
        row.addView(dn);
        return row;
    }

    private TextView orderBtn(String label, boolean enabled, View.OnClickListener onClick) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextSize(m.backSize);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(enabled ? C_PRIMARY : 0xFF555555);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(12), dp(6), dp(12), dp(6));
        t.setBackgroundDrawable(pill(enabled ? 0xFF242424 : 0xFF151515));
        if (enabled) t.setOnClickListener(onClick);
        return t;
    }

    /** 开关型设置行：点整行切换，右侧显示开/关。 */
    private View toggleRow(String label, boolean on, View.OnClickListener onClick) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10), dp(8), dp(10), dp(8));
        row.setBackgroundDrawable(pill(C_CARD));
        final View.OnClickListener user = onClick;
        row.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                vibrate();
                if (user != null) user.onClick(v);
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

    // ================= 页面切换（带动画） =================
    private void showScreen(int s) {
        final int old = screen;
        SvList leaving = svOf(screen);
        if (leaving != null) {
            leaving.savedY = leaving.sc.getScrollY();
            // 上一个页面若正好在放 slideNodeList 的位移动画，被 cancel 会停在半中间，
            // 这里统一归位，免得下次进来列表偏着。
            leaving.sc.setTranslationX(0f);
            leaving.sc.setAlpha(1f);
        }
        screen = s;
        // 顺序必须与 S_* 常量一一对应（0..15）
        View[] screens = {subjectScreen, nodeScreen, detailScreen, settingsScreen,
                searchScreen, feedbackScreen, mineScreen, profileScreen, changelogScreen, aboutScreen,
                displayScreen, todayScreen, extraScreen, favScreen, histScreen, favSubScreen,
                dailyScreen, sentScreen, aiWebScreen, aiHubScreen, aiApiScreen, aiWebCfgScreen};
        View target = screens[s];
        boolean shown = target.getVisibility() == View.VISIBLE && target.getAlpha() > 0.95f
                && Math.abs(target.getTranslationX()) < dp(4);
        for (int i = 0; i < screens.length; i++) {
            if (i == s) continue;
            screens[i].animate().cancel();
            screens[i].setVisibility(View.GONE);
            screens[i].setTranslationX(0f);
            screens[i].setTranslationY(0f);
            screens[i].setAlpha(1f);
        }
        target.animate().cancel();
        target.setVisibility(View.VISIBLE);
        // 动画的起点状态先摆好，但**不在这里启动**：这一页的内容（列表绑定、详情卡片重建）
        // 是在下面才渲染的，渲染期间如果已经可见，会露出「空页面 → 内容突然出现」的闪屏。
        // 所以先设成全透明（alpha 0，用户看不到中间态），等本帧渲染完、下一帧再淡入。
        // 问 AI 这一屏里有网页视图：不能在 alpha 0 的淡入层里初始化网页，
        // 否则首帧绘制丢失，淡入结束后就是一片黑。这一屏直接满不透明地显示。
        boolean animate = !shown && !skipPageAnim && s != S_AI_WEB;
        skipPageAnim = false;
        float dx = 0f, dy = 0f;
        if (shown) {
            // 已经在位（例如右滑返回时上一页已跟手归位），别再动它，免得跳一下
            target.setTranslationX(0f);
            target.setTranslationY(0f);
            target.setAlpha(1f);
        } else if (!animate) {
            // 启动首屏：开屏还盖在上面，这里必须直接满不透明，不能再淡入
            target.setTranslationX(0f);
            target.setTranslationY(0f);
            target.setAlpha(1f);
        } else {
            int dir = (old == s) ? 0 : (pageDepth(s) - pageDepth(old));
            dx = dir > 0 ? dp(12) : (dir < 0 ? -dp(12) : 0f);
            dy = (dir == 0) ? dp(7) : 0f;
            target.setTranslationX(dx);
            target.setTranslationY(dy);
            target.setAlpha(0f);
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
        }

        // 内容、滚动位置都就绪了，下一帧再淡入：中间不会有一帧「空白页」被看见。
        if (animate) {
            final View tv = target;
            final int fn = s;
            tv.post(new Runnable() {
                public void run() {
                    // 这一帧里用户可能又点走了：目标已不是这一页就别再让它淡入
                    if (screen != fn || tv.getVisibility() != View.VISIBLE) return;
                    tv.animate().cancel();
                    tv.animate().translationX(0f).translationY(0f).alpha(1f)
                            .setDuration(185)
                            .setInterpolator(new DecelerateInterpolator(1.6f))
                            .start();
                }
            });
        }
    }

    /** 页面在导航里的深浅：用来决定切页动画往哪个方向滑。 */
    private static int pageDepth(int s) {
        if (s == S_SUBJECT) return 0;
        if (s == S_FAV_SUB || s == S_DETAIL || s == S_DAILY || s == S_SENT || s == S_AI_HUB) return 2;
        if (s == S_EXTRA || s == S_AI_API_CFG || s == S_AI_WEB_CFG) return 3;
        return 1;
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
            default: return null;
        }
    }

    /** 拖动预览时把下载进度卡重新提到最上层。 */
    private void ensureOverlaysOnTop() {
        if (progBox != null && progBox.getVisibility() == View.VISIBLE) progBox.bringToFront();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        // 问 AI 页的双指手势优先：从第二指按下到最后一指抬起期间事件全部由这里处理，
        // 不传给网页，避免误触按钮，也避开 WebView 自带的双指缩放。
        if (fullscreenView == null && (screen == S_AI_WEB || aiTwoActive)) {
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
            renderNodes();
            nodeList.enter();
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
                        handleBack();
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
                            renderNodes();
                            // 本级搜索的关键词原来被 renderNodes 清掉了，退回时还原
                            if (query != null && query.length() > 0) itemSearchBox.setText(query);
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
                String raw = null;
                String query = GH_ISSUES + "?state=closed&per_page=100";
                // 总时限：一个个源试下去最坏能拖几十秒，到点就收手改用缓存
                final long deadline = android.os.SystemClock.uptimeMillis() + 25000L;

                // 0) 启动时测出来的最优通道，命中就不必再逐个试
                if (apiBase().length() > 0) {
                    try {
                        raw = httpGetText(apiBase() + query, 5000, 6000);
                    } catch (Exception ignored) { }
                }

                // 1) 直连，超时设短一点，连不上就赶紧换镜像，别让用户干等
                if (raw == null) {
                    try {
                        raw = httpGetText(query, 5000, 6000);
                    } catch (Exception ignored) { }
                }

                // 2) 能转发 api.github.com 的镜像
                if (raw == null) {
                    for (int i = 0; i < API_MIRRORS.length && raw == null; i++) {
                        if (android.os.SystemClock.uptimeMillis() > deadline) break;
                        setThanksHint("切换镜像 " + hostOf(API_MIRRORS[i]) + "…");
                        try {
                            raw = httpGetText(API_MIRRORS[i] + query, 5000, 6000);
                        } catch (Exception ignored) { }
                    }
                }

                // 3) 仓库里的静态备份名单：raw 基本所有镜像都代，这一步成功率最高
                if (raw == null) {
                    for (int i = 0; i <= MIRRORS.length && raw == null; i++) {
                        if (android.os.SystemClock.uptimeMillis() > deadline) break;
                        String u = (i == 0) ? THANKS_SNAPSHOT : MIRRORS[i - 1] + THANKS_SNAPSHOT;
                        setThanksHint(i == 0 ? "读取备份名单…"
                                : "切换镜像 " + hostOf(MIRRORS[i - 1]) + "…");
                        try {
                            String s = httpGetText(u, 5000, 6000);
                            if (s != null && s.length() > 2) raw = s;
                        } catch (Exception ignored) { }
                    }
                }

                List<Map.Entry<String, Integer>> list = null;
                boolean fetched = false;      // 真的取到了数据（哪怕结果是空的）
                if (raw != null) {
                    try {
                        list = parseThanks(new JSONArray(raw));
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
                public void onClick(View v) { onAvatarTap(); }
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
        // 技巧占大头，关怀穿插：普通用户 80/20；有一类用户更想看关心的话，按 50/50。
        int techPct = special ? 50 : 80;
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

    private TextView pvTag, pvTitle, pvBody, pvList, pvSub;
    /** 预览卡片（拖动缩放时实时放大的那一张）。 */
    private LinearLayout pvCard;

    private void buildDisplayScreen() {
        displayScroll = new OverScrollScrollView(this);
        displayScroll.setVerticalScrollBarEnabled(false);
        roundPadScroll(displayScroll);
        displayInner = new LinearLayout(this);
        displayInner.setOrientation(LinearLayout.VERTICAL);
        displayScroll.addView(displayInner);
        displayScreen.addView(displayScroll, lw());
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
        showScreen(backTo);         // 切页动画自带淡入，这里不再叠加第二层
    }

    /**
     * 拖动缩放滑块时的实时预览：**只放大预览卡片**。
     *
     * 之前试过把整个 root 等比缩放，问题很明显：内容超出屏幕被裁、滑块自己被放大变形、
     * 缩放是「拉伸」而非「重排」，比例跟真正缩放后的结果对不上，看着很怪。
     * 现在只让预览卡片按比例长大——卡片本来就是用来预览效果的，
     * 界面本体保持不动，松手后再由 applyDisplayChange 精确重排。
     */
    private void applyLiveScale(float k) {
        if (pvCard == null) return;
        pvCard.setPivotX(pvCard.getWidth() / 2f);
        pvCard.setPivotY(pvCard.getHeight() / 2f);
        pvCard.setScaleX(k);
        pvCard.setScaleY(k);
    }

    /** 复位预览卡片的缩放（松手重建前调用，避免和新的比例叠加）。 */
    private void resetLiveScale() {
        if (pvCard == null) return;
        pvCard.setScaleX(1f);
        pvCard.setScaleY(1f);
    }

    /** 预览区按当前值实时重算（界面缩放通过放大预览字号来体现）。 */
    private void applyPreview() {
        if (pvTitle == null) return;
        float k = uiScale;
        pvTag.setTextSize(Math.max(8f, (curSubSize() - 1f) * k));
        pvTitle.setTextSize(curTitleSize() * k);
        pvBody.setTextSize(fontSizeSp * k);
        pvList.setTextSize(listSizeSp * k);
        pvSub.setTextSize(curSubSize() * k);
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
        sb.setPadding(0, 0, 0, 0);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            float cur = value;
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                float v = min + progress * step;
                cur = v;
                val.setText(shownValue(v, unit));
                onChange.onChanged(v);
            }
            public void onStartTrackingTouch(SeekBar bar) { sliderTouching = true; }
            public void onStopTrackingTouch(SeekBar bar) {
                sliderTouching = false;
                vibrate();
                if (onRelease != null) onRelease.onReleased(cur);
            }
        });
        box.addView(sb, wrap());
        return box;
    }

    private String shownValue(float v, String unit) {
        if ("%".equals(unit)) return Math.round(v * 100) + "%";
        return Math.round(v) + unit;
    }

    private void renderDisplayScreen() {
        if (displayInner == null) return;
        displayInner.removeAllViews();
        pvTag = pvTitle = pvBody = pvList = pvSub = null;
        pvCard = null;

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
                ? "第一次使用，先按自己的习惯把字号和界面大小调舒服。松手后整个界面立刻按新比例重排，调好点「完成，开始使用」。"
                : "拖动滑块调整，上面的预览卡片会实时放大；松手后整个界面（字号、按钮、间距、图标）按新比例重新排版。");
        tip.setTextSize(Math.max(9f, m.subtitleSize - 1f));
        tip.setTextColor(C_SUB);
        tip.setGravity(Gravity.CENTER);
        tip.setPadding(dp(6), dp(3), dp(6), dp(6));
        displayInner.addView(tip, wrap());

        // ---- 预览卡片 ----
        LinearLayout card = new LinearLayout(this);
        pvCard = card;
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(11), dp(9), dp(11), dp(10));
        card.setBackgroundDrawable(pill(C_CARD));

        pvTag = new TextView(this);
        pvTag.setText("第 5 章 · 示例分类");
        pvTag.setTextColor(C_SUB);
        card.addView(pvTag, wrap());

        pvTitle = new TextView(this);
        pvTitle.setText("示例知识点标题");
        pvTitle.setGravity(Gravity.CENTER);
        pvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        pvTitle.setTextColor(0xFFF2F2F2);
        pvTitle.setPadding(0, dp(2), 0, dp(2));
        card.addView(pvTitle, wrap());

        pvBody = new TextView(this);
        pvBody.setText("正文长这样：这里是一段说明文字，读懂它大概就知道正文的字号合不合适了。");
        pvBody.setTextColor(C_TEXT);
        card.addView(pvBody, wrap());

        pvList = new TextView(this);
        pvList.setText("列表项：三角恒等变换 ›");
        pvList.setGravity(Gravity.CENTER);
        pvList.setTextColor(0xFFEDEDED);
        pvList.setPadding(0, dp(5), 0, 0);
        card.addView(pvList, wrap());

        pvSub = new TextView(this);
        pvSub.setText("小字：路径、提示、计数用这个字号");
        pvSub.setGravity(Gravity.CENTER);
        pvSub.setTextColor(C_SUB);
        card.addView(pvSub, wrap());

        LinearLayout.LayoutParams cardLp = wrap();
        cardLp.topMargin = dp(6);
        displayInner.addView(card, cardLp);

        applyPreview();

        // ---- 字号滑块 ----
        displayInner.addView(sectionHeader("字号"), wrap());
        displayInner.addView(sliderRow("正文字号", fontSizeSp, 11, 22, 1, "sp",
                new OnFloatChange() {
                    public void onChanged(float v) { fontSizeSp = v; applyPreview(); }
                }, new OnFloatRelease() {
                    public void onReleased(float v) { applyDisplayChange(S_DISPLAY); }
                }), wrap());
        displayInner.addView(sliderRow("列表字号", listSizeSp, 12, 20, 1, "sp",
                new OnFloatChange() {
                    public void onChanged(float v) { listSizeSp = v; applyPreview(); }
                }, new OnFloatRelease() {
                    public void onReleased(float v) { applyDisplayChange(S_DISPLAY); }
                }), wrap());
        displayInner.addView(sliderRow("标题字号", curTitleSize(), 13, 26, 1, "sp",
                new OnFloatChange() {
                    public void onChanged(float v) { titleSizeSp = v; applyPreview(); }
                }, new OnFloatRelease() {
                    public void onReleased(float v) { applyDisplayChange(S_DISPLAY); }
                }), wrap());
        displayInner.addView(sliderRow("说明小字", curSubSize(), 9, 16, 1, "sp",
                new OnFloatChange() {
                    public void onChanged(float v) { subSizeSp = v; applyPreview(); }
                }, new OnFloatRelease() {
                    public void onReleased(float v) { applyDisplayChange(S_DISPLAY); }
                }), wrap());

        // ---- 界面大小 ----
        displayInner.addView(sectionHeader("界面大小"), wrap());
        displayInner.addView(sliderRow("整体缩放", uiScale, 0.85f, 1.40f, 0.05f, "%",
                new OnFloatChange() {
                    public void onChanged(float v) {
                        uiScale = v;
                        applyPreview();
                        applyLiveScale(v);   // 拖动过程中只放大预览卡片，界面本体松手后重建
                    }
                }, new OnFloatRelease() {
                    public void onReleased(float v) {
                        // 先把预览卡片的缩放复位，再精确重建，避免两套缩放叠在一起
                        resetLiveScale();
                        applyDisplayChange(S_DISPLAY);
                    }
                }), wrap());
        TextView scaleHint = new TextView(this);
        scaleHint.setText("拖动时上面的预览卡片会实时放大，松手后整个界面按新比例重新排版。调大后文字、按钮、间距、图标一起变大，适合手指粗或看不清的人；表盘小的机型建议不要超过 115%。");
        scaleHint.setTextSize(Math.max(9f, m.subtitleSize - 2f));
        scaleHint.setTextColor(C_SUB);
        scaleHint.setGravity(Gravity.CENTER);
        scaleHint.setPadding(dp(4), dp(2), dp(4), dp(4));
        displayInner.addView(scaleHint, wrap());

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
        doneLp.topMargin = dp(10);
        displayInner.addView(done, doneLp);

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

    /** 读取 assets/changelog.json，按版本倒序渲染；当前版本高亮。 */
    private void fillChangelog() {
        changelogFilled = true;
        changelogList.removeAllViews();
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
        changelogSub.setText("当前 v" + cur + " · 共记录 " + arr.length() + " 个版本");
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
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

        fbSubmitBtn = new TextView(this);
        fbSubmitBtn.setText("提交反馈");
        fbSubmitBtn.setTextSize(m.backSize);
        fbSubmitBtn.setTypeface(Typeface.DEFAULT_BOLD);
        fbSubmitBtn.setGravity(Gravity.CENTER);
        fbSubmitBtn.setPadding(dp(10), dp(11), dp(10), dp(11));
        fbSubmitBtn.setTextColor(C_PRIMARY);
        fbSubmitBtn.setBackgroundDrawable(pill(0xFF14384A));
        fbSubmitBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { submitFeedback(); }
        });
        feedbackInner.addView(fbSubmitBtn, wrap());

        TextView mineBtn = new TextView(this);
        mineBtn.setText("我的反馈记录");
        mineBtn.setTextSize(m.backSize);
        mineBtn.setTypeface(Typeface.DEFAULT_BOLD);
        mineBtn.setGravity(Gravity.CENTER);
        mineBtn.setPadding(dp(10), dp(10), dp(10), dp(10));
        mineBtn.setTextColor(C_PRIMARY);
        mineBtn.setBackgroundDrawable(pill(C_PILL));
        applyIcon(mineBtn, "ic_assignment", C_PRIMARY, 15);
        mineBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
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
        fbAllBtn = new TextView(this);
        fbAllBtn.setText("查看全部反馈");
        fbAllBtn.setTextSize(m.backSize - 1f);
        fbAllBtn.setTypeface(Typeface.DEFAULT_BOLD);
        fbAllBtn.setGravity(Gravity.CENTER);
        fbAllBtn.setPadding(dp(10), dp(9), dp(10), dp(9));
        fbAllBtn.setTextColor(0xFF4FC3F7);
        fbAllBtn.setBackgroundDrawable(pill(0xFF10222B));
        applyIcon(fbAllBtn, "ic_person", 0xFF4FC3F7, 15);
        fbAllBtn.setVisibility(View.GONE);
        fbAllBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
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
            public void onClick(View v) { dismissCodeDialog(); }
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

    private Bitmap loadAvatarBitmap(String url, String login) {
        try {
            File cache = new File(getFilesDir(), "avatar_" + (login == null || login.length() == 0 ? "me" : login) + ".png");
            if (cache.exists() && cache.length() > 0) {
                Bitmap b = BitmapFactory.decodeFile(cache.getAbsolutePath());
                if (b != null) return circleCrop(b);
            }
            if (url == null || url.length() == 0) return null;
            HttpURLConnection cn = (HttpURLConnection) new URL(url).openConnection();
            cn.setConnectTimeout(10000);
            cn.setReadTimeout(15000);
            cn.setInstanceFollowRedirects(true);
            cn.setRequestProperty("User-Agent", "HZKnowledge-Android");
            InputStream is = cn.getInputStream();
            File tmp = new File(getFilesDir(), "avatar.tmp");
            FileOutputStream fos = new FileOutputStream(tmp);
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) fos.write(buf, 0, n);
            fos.close();
            is.close();
            cn.disconnect();
            Bitmap src = BitmapFactory.decodeFile(tmp.getAbsolutePath());
            if (src == null) return null;
            if (cache.exists()) cache.delete();
            tmp.renameTo(cache);
            return circleCrop(src);
        } catch (Exception e) {
            return null;
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
