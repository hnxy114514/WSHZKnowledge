package com.lizard.hsknowledge;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.BitmapShader;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.text.style.ReplacementSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.text.style.SubscriptSpan;
import android.text.style.SuperscriptSpan;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AbsListView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
    static class Section { String h, c, body; }
    static class Item { String t; int star; String img; String id, d; List<Section> sec = new ArrayList<Section>(); }
    static class Child { String type; String name; List<Item> items = new ArrayList<Item>(); List<Section> sec = new ArrayList<Section>(); }
    static class Block { String name, id; List<Child> children = new ArrayList<Child>(); }
    static class Subject { String key, name; List<Block> blocks = new ArrayList<Block>(); }
    static class Hit { String path; Item item; }

    private final List<Subject> subjects = new ArrayList<Subject>();
    private final Map<String, Subject> subjectMap = new HashMap<String, Subject>();
    private final List<Hit> allHits = new ArrayList<Hit>();
    private final List<Hit> searchHits = new ArrayList<Hit>();

    // ---------- 导航 ----------
    private static final int S_SUBJECT = 0, S_BLOCK = 1, S_CHILD = 2, S_ITEM = 3, S_DETAIL = 4, S_SETTINGS = 5,
            S_SEARCH = 6, S_FEEDBACK = 7, S_MYFEEDBACK = 8, S_PROFILE = 9, S_CHANGELOG = 10, S_ABOUT = 11, S_DISPLAY = 12, S_TODAY = 13;
    private int screen = S_SUBJECT;
    private Subject curSubject;
    private Block curBlock;
    private Child curChild;
    private final List<Item> shownItems = new ArrayList<Item>();
    private int detailBack = S_ITEM;

    // ---------- UI ----------
    private FrameLayout root;
    private LinearLayout subjectScreen, blockScreen, childScreen, listScreen, detailScreen, settingsScreen, searchScreen;
    private ListView subjectList, blockList, childList, itemList, searchList;
    private TextView listTitle, detailTitle, detailPath, searchTitle;
    private TextView detailBackBtn, settingsBackBtn;
    private EditText itemSearchBox, gSearchBox;
    private ScrollView detailScroll, settingsScroll;
    private LinearLayout detailInner, settingsInner;
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
    private boolean foreground = false;
    private String lastPath = "";
    private boolean subjectDirty = true;
    private String lastQuery = null;
    private final int[] savedPos = new int[14];
    private final int[] savedTop = new int[14];
    private final boolean[] hasSaved = new boolean[14];
    private final boolean[] restoreNext = new boolean[14];

    // 关于 / 感谢名单
    private LinearLayout aboutScreen, aboutInner, thanksBox, thanksLoading;

    // 显示调节（字号 / 界面缩放，带实时预览）
    private LinearLayout displayScreen, displayInner;

    // 今日新增（分页加载）
    private LinearLayout todayScreen;
    private ListView todayList;
    private TextView todayTitle;
    private ArrayAdapter<Hit> todayAdapter;
    private final List<Hit> todayAll = new ArrayList<Hit>();
    private final List<Hit> todayShown = new ArrayList<Hit>();
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
    private ListView mineList;
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
    private boolean swipeFromInput = false;  // 起手点在输入框内
    private View swipeView;                  // 正在被拖动的当前页
    private View swipePrev;                  // 下面露出的上一级页面（预览）
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
        loadGitHub();
        loadData();
        loadOrderKeys();
        buildUi();
        boolean tuned = prefs.getBoolean("display_tuned", false);
        if (!tuned) tuningFirstRun = true;
        if (pendingScreen >= 0) {
            int back = pendingScreen;
            pendingScreen = -1;
            showScreen(back);
        } else if (!tuned) {
            showScreen(S_DISPLAY);
        } else {
            showScreen(S_SUBJECT);
        }
        // 后台探测最优通道（直连 / 镜像），探测结果给所有联网页面共用
        startRouteProbe();
        checkUpdate(true);
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

    private Subject parseSubject(JSONObject so) throws Exception {
        Subject s = new Subject();
        s.key = so.optString("s", "");
        s.name = so.optString("name", s.key);
        JSONArray bs = so.optJSONArray("blocks");
        if (bs != null) {
            for (int j = 0; j < bs.length(); j++) {
                JSONObject bo = bs.getJSONObject(j);
                Block b = new Block();
                b.name = bo.optString("name", "");
                b.id = bo.optString("id", "");
                JSONArray cs = bo.optJSONArray("children");
                if (cs != null) {
                    for (int k = 0; k < cs.length(); k++) {
                        JSONObject co = cs.getJSONObject(k);
                        Child c = new Child();
                        c.type = co.optString("type", "module");
                        c.name = co.optString("name", "");
                        JSONArray secs = co.optJSONArray("sec");
                        if (secs != null) c.sec = parseSections(secs);
                        JSONArray items = co.optJSONArray("items");
                        if (items != null) {
                            for (int mm = 0; mm < items.length(); mm++) {
                                c.items.add(parseItem(items.getJSONObject(mm)));
                            }
                        }
                        b.children.add(c);
                    }
                }
                s.blocks.add(b);
            }
        }
        return s;
    }

    private void buildHits() {
        for (Subject s : subjects) {
            for (Block b : s.blocks) {
                for (Child c : b.children) {
                    if (!c.items.isEmpty()) {
                        for (Item it : c.items) {
                            Hit h = new Hit();
                            h.path = s.name + " › " + b.name + " › " + c.name;
                            h.item = it;
                            allHits.add(h);
                        }
                    } else if (!c.sec.isEmpty()) {
                        Hit h = new Hit();
                        h.path = s.name + " › " + b.name;
                        Item it = new Item();
                        it.t = c.name;
                        it.star = 0;
                        it.sec = c.sec;
                        h.item = it;
                        allHits.add(h);
                    }
                }
            }
        }
    }

    private void loadData() {
        try {
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
            Toast.makeText(this, "数据加载失败：" + ex.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    /** 检查 GitHub Release 最新版：有新版先展示更新说明，确认后再下载覆盖安装。 */
    private void checkUpdate(final boolean silent) {
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
        int maxH = Math.min(dp(104), (int) (getResources().getDisplayMetrics().heightPixels * 0.30f));
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
        go.setBackgroundDrawable(pill(0xFF14384A));
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
            if (sb.length() > 460) { sb.append("\n…"); break; }
        }
        return sb.toString();
    }

    /** 用户确认更新后：权限不足先去申请，回来接着下载；然后下载并唤起安装器。 */
    private void startUpdate(final String url, final String tag) {
        if (Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
            pendingUrl = url;
            pendingTag = tag;
            pendingDownload = true;
            requestInstallPermission();
            return;
        }
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
                    progressShow("下载完成", "正在打开安装程序…", 100);
                    runOnUiThread(new Runnable() {
                        public void run() {
                            progressHide();
                            pendingInstall = true;
                            if (Build.VERSION.SDK_INT >= 26
                                    && !getPackageManager().canRequestPackageInstalls()) {
                                requestInstallPermission();
                                toast("开启「允许安装未知应用」后会自动继续安装");
                                return;
                            }
                            if (!foreground) {
                                // 应用不在前台时系统会静默拦截启动安装器，等回来后自动弹
                                toast("更新已下载，回到应用后自动安装");
                                return;
                            }
                            pendingInstall = false;
                            installApk();
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
                    public void run() { refreshRouteHint(); }
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
    private void showBubble(final String text) {
        runOnUiThread(new Runnable() {
            public void run() {
                if (root == null) return;
                final FrameLayout box = new FrameLayout(MainActivity.this);
                box.setClickable(false);

                TextView tv = new TextView(MainActivity.this);
                tv.setText(text);
                tv.setTextSize(m.subtitleSize - 1f);
                tv.setTextColor(C_TEXT);
                tv.setGravity(Gravity.CENTER);
                tv.setPadding(dp(12), dp(7), dp(12), dp(7));
                tv.setBackgroundDrawable(pill(C_CARD));

                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
                lp.bottomMargin = dp(16);
                box.addView(tv, lp);
                root.addView(box, fp());

                android.view.animation.AlphaAnimation in =
                        new android.view.animation.AlphaAnimation(0f, 1f);
                in.setDuration(200);
                box.startAnimation(in);

                new android.os.Handler().postDelayed(new Runnable() {
                    public void run() {
                        android.view.animation.AlphaAnimation out =
                                new android.view.animation.AlphaAnimation(1f, 0f);
                        out.setDuration(320);
                        out.setAnimationListener(
                                new android.view.animation.Animation.AnimationListener() {
                                    public void onAnimationStart(android.view.animation.Animation a) { }
                                    public void onAnimationRepeat(android.view.animation.Animation a) { }
                                    public void onAnimationEnd(android.view.animation.Animation a) {
                                        root.removeView(box);
                                    }
                                });
                        box.startAnimation(out);
                    }
                }, 1800);
            }
        });
    }

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
                if (cn != null) cn.disconnect();
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
                toast("正在取消更新…");
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
        if (pendingInstall && Build.VERSION.SDK_INT >= 26
                && getPackageManager().canRequestPackageInstalls()) {
            pendingInstall = false;
            installApk();
        }
    }

    @Override
    protected void onPause() {
        foreground = false;
        super.onPause();
    }

    private void toast(final String msg) {
        runOnUiThread(new Runnable() {
            public void run() { Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show(); }
        });
    }

    private List<Section> parseSections(JSONArray arr) throws Exception {
        List<Section> out = new ArrayList<Section>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject so = arr.getJSONObject(i);
            Section s = new Section();
            s.h = so.optString("h", "知识清单");
            s.c = so.optString("c", "K");
            s.body = so.optString("body", "");
            out.add(s);
        }
        return out;
    }

    private Item parseItem(JSONObject io) {
        Item it = new Item();
        it.t = io.optString("t", "");
        it.star = io.optInt("star", 2);
        it.img = io.optString("img", "");
        it.id = io.optString("id", "");
        it.d = io.optString("d", "");
        try {
            JSONArray secs = io.optJSONArray("sec");
            if (secs != null) it.sec = parseSections(secs);
        } catch (Exception ignored) { }
        return it;
    }

    // ================= UI =================
    private void buildUi() {
        getWindow().setBackgroundDrawable(new ColorDrawable(C_BG));
        root = new FrameLayout(this);
        root.setBackgroundColor(C_BG);
        setContentView(root);

        subjectScreen = newScreen();
        blockScreen = newScreen();
        childScreen = newScreen();
        listScreen = newScreen();
        detailScreen = newScreen();
        settingsScreen = newScreen();
        searchScreen = newScreen();
        feedbackScreen = newScreen();

        root.addView(subjectScreen, fp());
        root.addView(blockScreen, fp());
        root.addView(childScreen, fp());
        root.addView(listScreen, fp());
        root.addView(detailScreen, fp());
        root.addView(settingsScreen, fp());
        root.addView(searchScreen, fp());
        buildFeedbackScreen();
        root.addView(feedbackScreen, fp());

        mineScreen = newScreen();
        mineList = makeList();
        mineHeader = makeBackHeader("‹ 返回反馈", new View.OnClickListener() {
            public void onClick(View v) { showScreen(S_FEEDBACK); }
        });
        mineList.addHeaderView(mineHeader);
        mineScreen.addView(mineList, lw());
        mineList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                if (pos == 0) return;
                vibrate();
                int idx = pos - 1;
                if (idx < 0 || idx >= mineIssues.size()) return;
                openIssueDetail(mineIssues.get(idx));
            }
        });
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

        // 主页
        subjectList = makeList();
        subjectList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                vibrate();
                int idx = pos - 1;
                if (idx < 0) { showScreen(S_SEARCH); return; }
                if (idx == orderKeys.size()) { showScreen(S_TODAY); return; }
                if (idx > orderKeys.size()) { showScreen(S_SETTINGS); return; }
                curSubject = subjectMap.get(orderKeys.get(idx));
                if (curSubject != null) openBlocks();
            }
        });
        subjectScreen.addView(subjectList, lw());

        // 章页
        blockList = makeList();
        blockList.addHeaderView(makeBackHeader("‹ 返回学科", new View.OnClickListener() {
            public void onClick(View v) { showScreen(S_SUBJECT); }
        }));
        blockList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                if (pos == 0) return;   // 返回键 header
                vibrate();
                curBlock = curSubject.blocks.get(pos - 1);
                openChildren();
            }
        });
        blockScreen.addView(blockList, lw());

        // 节页
        childList = makeList();
        childList.addHeaderView(makeBackHeader("‹ 返回上一页", new View.OnClickListener() {
            public void onClick(View v) { showScreen(S_BLOCK); }
        }));
        childList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                if (pos == 0) return;   // 返回键 header
                vibrate();
                curChild = curBlock.children.get(pos - 1);
                if ("topic".equals(curChild.type) && curChild.items.isEmpty()) {
                    openDetail(curChild.name, curChild.sec, curSubject.name + " › " + curBlock.name, S_CHILD, null);
                } else {
                    openItems();
                }
            }
        });
        childScreen.addView(childList, lw());

        // 知识点页
        listTitle = new TextView(this);
        listTitle.setTextSize(m.subtitleSize);
        listTitle.setTypeface(Typeface.DEFAULT_BOLD);
        listTitle.setTextColor(C_PRIMARY);
        listTitle.setGravity(Gravity.CENTER);
        listTitle.setPadding(0, 0, 0, dp(4));
        listTitle.setMaxLines(2);
        listScreen.addView(listTitle, wrap());

        itemSearchBox = new EditText(this);
        itemSearchBox.setHint("在本节内搜索…");
        itemSearchBox.setTextSize(m.subtitleSize - 1f);
        itemSearchBox.setTextColor(Color.WHITE);
        itemSearchBox.setHintTextColor(C_SUB);
        itemSearchBox.setSingleLine(true);
        itemSearchBox.setBackgroundDrawable(pill(C_PILL));
        itemSearchBox.setPadding(dp(12), dp(8), dp(12), dp(8));
        LinearLayout.LayoutParams sblp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        sblp.bottomMargin = dp(6);
        listScreen.addView(itemSearchBox, sblp);
        itemSearchBox.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            public void onTextChanged(CharSequence s, int a, int b, int c) { }
            public void afterTextChanged(Editable s) { refreshItems(); }
        });

        itemList = makeList();
        itemList.addHeaderView(makeBackHeader("‹ 返回上一页", new View.OnClickListener() {
            public void onClick(View v) { showScreen(S_CHILD); }
        }));
        listScreen.addView(itemList, lw());
        itemList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                if (pos == 0) return;   // 返回键 header
                vibrate();
                Item it = shownItems.get(pos - 1);
                openDetail(it.t, it.sec, curSubject.name + " › " + curBlock.name + " › " + curChild.name, S_ITEM, it.img);
            }
        });

        // 详情页
        detailBackBtn = makeBack("‹ 返回", new View.OnClickListener() {
            public void onClick(View v) { showScreen(detailBack); }
        });
        detailPath = new TextView(this);
        detailPath.setTextSize(m.subtitleSize - 2f);
        detailPath.setTextColor(C_SUB);
        detailPath.setPadding(dp(2), 0, dp(2), dp(1));
        detailPath.setMaxLines(2);
        detailScreen.addView(detailPath, wrap());

        detailTitle = new TextView(this);
        detailTitle.setTextSize(m.titleSize);
        detailTitle.setTypeface(Typeface.DEFAULT_BOLD);
        detailTitle.setTextColor(C_PRIMARY);
        detailTitle.setGravity(Gravity.CENTER);
        detailTitle.setPadding(0, dp(2), 0, dp(6));
        detailTitle.setMaxLines(3);
        detailScreen.addView(detailTitle, wrap());

        detailScroll = new ScrollView(this);
        detailScroll.setFillViewport(true);
        detailInner = new LinearLayout(this);
        detailInner.setOrientation(LinearLayout.VERTICAL);
        detailScroll.addView(detailInner, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        detailScreen.addView(detailScroll, lw());

        // 设置页
        settingsBackBtn = makeBack("‹ 返回", new View.OnClickListener() {
            public void onClick(View v) { showScreen(S_SUBJECT); }
        });
        settingsScroll = new ScrollView(this);
        settingsScroll.setFillViewport(true);
        settingsInner = new LinearLayout(this);
        settingsInner.setOrientation(LinearLayout.VERTICAL);
        settingsScroll.addView(settingsInner, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        settingsScreen.addView(settingsScroll, lw());

        // 全局搜索页
        // 全局搜索页
        searchTitle = new TextView(this);
        searchTitle.setText("搜索全部知识点");
        searchTitle.setTextSize(m.subtitleSize + 2f);
        searchTitle.setTypeface(Typeface.DEFAULT_BOLD);
        searchTitle.setTextColor(C_PRIMARY);
        searchTitle.setGravity(Gravity.CENTER);
        searchTitle.setPadding(0, 0, 0, dp(3));
        searchScreen.addView(searchTitle, wrap());
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
        clearBtn.setBackgroundDrawable(pill(C_PILL));
        LinearLayout.LayoutParams clrp = new LinearLayout.LayoutParams(dp(40), dp(40));
        clrp.leftMargin = dp(6);
        clearBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                gSearchBox.setText("");
            }
        });
        searchBar.addView(clearBtn, clrp);

        LinearLayout.LayoutParams gsblp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        gsblp.bottomMargin = dp(6);
        searchScreen.addView(searchBar, gsblp);
        gSearchBox.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            public void onTextChanged(CharSequence s, int a, int b, int c) { }
            public void afterTextChanged(Editable s) { refreshGlobalSearch(); }
        });
        searchList = makeList();
        searchList.addHeaderView(makeBackHeader("‹ 返回", new View.OnClickListener() {
            public void onClick(View v) { showScreen(S_SUBJECT); }
        }));
        searchScreen.addView(searchList, lw());
        searchList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                if (pos == 0) return;                       // 返回键 header
                vibrate();
                if (pos - 1 >= searchHits.size()) return;   // 空提示行
                Hit h = searchHits.get(pos - 1);
                openDetail(h.item.t, h.item.sec, h.path, S_SEARCH, h.item.img);
            }
        });
    }

    private LinearLayout newScreen() {
        LinearLayout ll = new LinearLayout(this);
        ll.setOrientation(LinearLayout.VERTICAL);
        ll.setBackgroundColor(C_BG);   // 每页自带黑底：右滑预览时两页各自独立，不会互相透出
        ll.setPadding(m.sidePad, m.topPad, m.sidePad, m.bottomPad);
        return ll;
    }

    private ListView makeList() {
        ListView lv = new ListView(this);
        lv.setDivider(new ColorDrawable(Color.TRANSPARENT));
        lv.setDividerHeight(dp(6));
        lv.setVerticalScrollBarEnabled(false);
        lv.setPadding(0, dp(4), 0, dp(4));
        lv.setClipToPadding(false);
        // 滚动时中间的项放大、边缘的项缩小变淡，最大化利用（圆）屏显示面积
        lv.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) { }
            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                int listCenter = view.getHeight() / 2;
                for (int i = 0; i < visibleItemCount; i++) {
                    View v = view.getChildAt(i);
                    if (v == null) continue;
                    int cy = v.getTop() + v.getHeight() / 2;
                    float dist = Math.abs(cy - listCenter) / (float) Math.max(1, view.getHeight());
                    float k = Math.min(dist, 1f);
                    float sc = 1.0f - 0.15f * k;
                    v.setPivotX(v.getWidth() / 2f);
                    v.setPivotY(v.getHeight() / 2f);
                    v.setScaleX(sc);
                    v.setScaleY(sc);
                    v.setAlpha(0.55f + 0.45f * (1f - k));
                }
            }
        });
        return lv;
    }

    private TextView makeBack(String label, View.OnClickListener onClick) {
        TextView b = new TextView(this);
        b.setText(label);
        b.setTextSize(m.backSize);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(C_PRIMARY);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(10), dp(8), dp(10), dp(8));
        b.setBackgroundDrawable(pill(C_PILL));
        final View.OnClickListener user = onClick;
        b.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                vibrate();
                if (user != null) user.onClick(v);
            }
        });
        return b;
    }

    /** 作为列表/正文首项的返回键：随内容一起滚动，滚出屏幕后不再占用顶部空间。 */
    private TextView makeBackHeader(String label, View.OnClickListener onClick) {
        TextView b = makeBack(label, onClick);
        b.setLayoutParams(new AbsListView.LayoutParams(
                AbsListView.LayoutParams.MATCH_PARENT, AbsListView.LayoutParams.WRAP_CONTENT));
        return b;
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

    private ArrayAdapter<String> stringAdapter(final List<String> data, final int color) {
        return new ArrayAdapter<String>(this, 0, data) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView tv = (convertView instanceof TextView) ? (TextView) convertView : null;
                if (tv == null) {
                    tv = new TextView(MainActivity.this);
                    GradientDrawable content = pill(C_PILL);
                    GradientDrawable mask = new GradientDrawable();
                    mask.setColor(Color.WHITE);
                    mask.setCornerRadius(dp(13));
                    try {
                        tv.setBackground(new RippleDrawable(ColorStateList.valueOf(0x334FC3F7), content, mask));
                    } catch (Exception ignored) {
                        tv.setBackgroundDrawable(content);
                    }
                }
                tv.setText(applyMarkup(getItem(position)));
                tv.setTextSize(lsSp());
                tv.setTextColor(color);
                tv.setTypeface(Typeface.DEFAULT_BOLD);
                tv.setGravity(Gravity.CENTER);
                tv.setPadding(dp(10), dp(9), dp(10), dp(9));
                tv.setMaxLines(6);
                tv.setEllipsize(null);
                tv.setLineSpacing(dp(1), 1.08f);
                return tv;
            }
        };
    }

    private GradientDrawable pill(int fill) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(13));
        return g;
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

    // ================= 导航 =================
    private void openBlocks() {
        List<String> names = new ArrayList<String>();
        for (Block b : curSubject.blocks) names.add(b.name);
        blockList.setAdapter(stringAdapter(names, 0xFFF5F5F5));
        showScreen(S_BLOCK);
    }

    private void openChildren() {
        List<String> names = new ArrayList<String>();
        for (Child c : curBlock.children) {
            String tag = "topic".equals(c.type) ? "［专题］" : "";
            int cnt = c.items.size();
            names.add(tag + c.name + (cnt > 0 ? "  (" + cnt + ")" : ""));
        }
        childList.setAdapter(stringAdapter(names, 0xFFF5F5F5));
        showScreen(S_CHILD);
    }

    private void openItems() {
        itemSearchBox.setText("");
        listTitle.setText(applyMarkup(curSubject.name + " › " + curBlock.name + " › " + curChild.name));
        shownItems.clear();
        shownItems.addAll(curChild.items);
        renderItems();
        showScreen(S_ITEM);
    }

    private void refreshItems() {
        if (screen != S_ITEM) return;
        String q = itemSearchBox.getText().toString().trim();
        shownItems.clear();
        if (q.length() == 0) {
            shownItems.addAll(curChild.items);
        } else {
            for (Item it : curChild.items) {
                if (it.t.contains(q)) { shownItems.add(it); continue; }
                for (Section s : it.sec) if (s.body.contains(q)) { shownItems.add(it); break; }
            }
        }
        renderItems();
    }

    private void renderItems() {
        List<String> names = new ArrayList<String>();
        for (Item it : shownItems) names.add(starText(it.star) + "  " + it.t);
        itemList.setAdapter(stringAdapter(names, 0xFFF0F0F0));
    }

    private void refreshGlobalSearch() {
        String q = gSearchBox.getText().toString().trim();
        lastQuery = q;
        searchHits.clear();
        if (q.length() == 0) {
            searchTitle.setText("搜索全部知识点");
            List<String> tip = new ArrayList<String>();
            tip.add("输入关键词开始搜索");
            searchList.setAdapter(stringAdapter(tip, C_SUB));
            return;
        }
        String ql = q.toLowerCase();
        for (Hit h : allHits) {
            String blob = (h.item.t + " " + plain(h.item.sec)).toLowerCase();
            if (blob.contains(ql)) searchHits.add(h);
        }
        searchTitle.setText(searchHits.size() == 0 ? "没有找到「" + q + "」"
                : "命中 " + searchHits.size() + " 条 · " + q);
        searchList.setAdapter(searchAdapter(searchHits));
    }

    /** 搜索结果的列表项：标题占主位，路径小字排在下面，避免挤在一行看不清。 */
    private ArrayAdapter<Hit> searchAdapter(final List<Hit> data) {
        return new ArrayAdapter<Hit>(this, 0, data) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                LinearLayout box;
                TextView title, path;
                if (convertView instanceof LinearLayout) {
                    box = (LinearLayout) convertView;
                    title = (TextView) box.getChildAt(0);
                    path = (TextView) box.getChildAt(1);
                } else {
                    box = new LinearLayout(MainActivity.this);
                    box.setOrientation(LinearLayout.VERTICAL);
                    box.setGravity(Gravity.CENTER);
                    box.setPadding(dp(10), dp(8), dp(10), dp(8));
                    GradientDrawable content = pill(C_PILL);
                    GradientDrawable mask = new GradientDrawable();
                    mask.setColor(Color.WHITE);
                    mask.setCornerRadius(dp(13));
                    try {
                        box.setBackground(new RippleDrawable(ColorStateList.valueOf(0x334FC3F7), content, mask));
                    } catch (Exception ignored) {
                        box.setBackgroundDrawable(content);
                    }
                    title = new TextView(MainActivity.this);
                    title.setTextSize(lsSp());
                    title.setTextColor(0xFFF0F0F0);
                    title.setTypeface(Typeface.DEFAULT_BOLD);
                    title.setGravity(Gravity.CENTER);
                    title.setMaxLines(2);
                    title.setEllipsize(TextUtils.TruncateAt.END);
                    box.addView(title, wrap());

                    path = new TextView(MainActivity.this);
                    path.setTextSize(m.subtitleSize - 2f);
                    path.setTextColor(C_SUB);
                    path.setGravity(Gravity.CENTER);
                    path.setMaxLines(1);
                    path.setEllipsize(TextUtils.TruncateAt.END);
                    path.setPadding(0, dp(2), 0, 0);
                    box.addView(path, wrap());
                }
                Hit h = getItem(position);
                title.setText(applyMarkup(h.item.t));
                path.setText(shortPath(h.path));
                return box;
            }
        };
    }

    /** 路径只留「科目 › 章」，节名省掉，给标题腾地方。 */
    private String shortPath(String p) {
        if (p == null || p.length() == 0) return "";
        String[] seg = p.split(" › ");
        if (seg.length >= 3) return seg[0] + " › " + seg[1];
        return p;
    }

    private String plain(List<Section> secs) {
        if (secs == null) return "";
        StringBuilder sb = new StringBuilder();
        for (Section s : secs) sb.append(s.body).append(" ");
        return sb.toString();
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
    private void openDetail(String title, List<Section> secs, String path, int back) {
        openDetail(title, secs, path, back, null);
    }

    private void openDetail(String title, List<Section> secs, String path, int back, String img) {
        detailBack = back;
        lastPath = (path == null) ? "" : path;
        detailTitle.setText(applyMarkup(title));
        detailPath.setText(applyMarkup(path));
        detailInner.removeAllViews();
        detailInner.addView(detailBackBtn, wrapM());
        if (img != null && img.length() > 0) addFigure(img);

        if (secs == null || secs.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("（暂无内容）");
            empty.setTextSize(fsSp());
            empty.setTextColor(C_SUB);
            empty.setGravity(Gravity.CENTER);
            detailInner.addView(empty, wrap());
        } else {
            for (Section s : secs) {
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
        detailScroll.smoothScrollTo(0, 0);
        showScreen(S_DETAIL);
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
        settingsInner.removeAllViews();
        settingsInner.addView(settingsBackBtn, wrapM());
        TextView profEntry = new TextView(this);
        profEntry.setText("个人主页");
        profEntry.setTextSize(m.backSize);
        profEntry.setTypeface(Typeface.DEFAULT_BOLD);
        profEntry.setTextColor(C_PRIMARY);
        profEntry.setGravity(Gravity.CENTER);
        profEntry.setPadding(dp(10), dp(10), dp(10), dp(10));
        profEntry.setBackgroundDrawable(pill(C_PILL));
        applyIcon(profEntry, "ic_person", C_PRIMARY, 15);
        profEntry.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { showScreen(S_PROFILE); }
        });
        settingsInner.addView(profEntry, wrap());

        settingsInner.addView(sectionHeader("显示"), wrap());
        TextView displayEntry = new TextView(this);
        displayEntry.setText("显示调节");
        displayEntry.setTextSize(m.backSize);
        displayEntry.setTypeface(Typeface.DEFAULT_BOLD);
        displayEntry.setTextColor(C_PRIMARY);
        displayEntry.setGravity(Gravity.CENTER);
        displayEntry.setPadding(dp(10), dp(10), dp(10), dp(10));
        displayEntry.setBackgroundDrawable(pill(C_PILL));
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
        displayHint.setText("正文字号、列表字号、标题字号、说明小字与整体界面缩放，都在这里用滑块调，带实时预览。");
        displayHint.setTextSize(m.subtitleSize - 2f);
        displayHint.setTextColor(C_SUB);
        displayHint.setGravity(Gravity.CENTER);
        displayHint.setPadding(dp(4), dp(3), dp(4), dp(2));
        settingsInner.addView(displayHint, wrap());

        settingsInner.addView(toggleRow("右滑返回", swipeBack, new View.OnClickListener() {
            public void onClick(View v) {
                swipeBack = !swipeBack;
                prefs.edit().putBoolean("swipe_back", swipeBack).apply();
                renderSettings();
            }
        }), wrap());
        settingsInner.addView(toggleRow("震动反馈", haptic, new View.OnClickListener() {
            public void onClick(View v) {
                haptic = !haptic;
                prefs.edit().putBoolean("haptic", haptic).apply();
                renderSettings();
            }
        }), wrap());
        TextView hapticHint = new TextView(this);
        hapticHint.setText("点击列表、返回、开关等交互时轻微震动一下，作为触感反馈。");
        hapticHint.setTextSize(m.subtitleSize - 2f);
        hapticHint.setTextColor(C_SUB);
        hapticHint.setGravity(Gravity.CENTER);
        hapticHint.setPadding(dp(4), dp(2), dp(4), dp(4));
        settingsInner.addView(hapticHint, wrap());

        TextView swipeHint = new TextView(this);
        swipeHint.setText("从屏幕左侧 1/4 内向右拖动：当前页跟手滑出、露出上一页预览，滑到一半以上松手即返回，不够则弹回。查看大图时不生效，需点「‹ 关闭」。");
        swipeHint.setTextSize(m.subtitleSize - 2f);
        swipeHint.setTextColor(C_SUB);
        swipeHint.setGravity(Gravity.CENTER);
        swipeHint.setPadding(dp(4), dp(2), dp(4), dp(4));
        settingsInner.addView(swipeHint, wrap());

        settingsInner.addView(sectionHeader("数据管理"), wrap());
        TextView upd = new TextView(this);
        upd.setText("检查更新");
        upd.setTextSize(m.backSize);
        upd.setTextColor(C_PRIMARY);
        upd.setGravity(Gravity.CENTER);
        upd.setPadding(dp(10), dp(10), dp(10), dp(10));
        upd.setBackgroundDrawable(pill(C_PILL));
        applyIcon(upd, "ic_update", C_PRIMARY, 15);
        upd.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                checkUpdate(false);
            }
        });
        settingsInner.addView(upd, wrap());

        TextView updHint = new TextView(this);
        updHint.setText("直连不通会自动换镜像下载；下载时屏幕中央显示进度与下载源，完成后自动打开安装。");
        updHint.setTextSize(m.subtitleSize - 2f);
        updHint.setTextColor(C_SUB);
        updHint.setGravity(Gravity.CENTER);
        updHint.setPadding(dp(4), dp(3), dp(4), dp(2));
        settingsInner.addView(updHint, wrap());
        TextView routeEntry = new TextView(this);
        routeEntry.setText("重新连接服务器");
        routeEntry.setTextSize(m.backSize);
        routeEntry.setTextColor(C_PRIMARY);
        routeEntry.setGravity(Gravity.CENTER);
        routeEntry.setPadding(dp(10), dp(10), dp(10), dp(10));
        routeEntry.setBackgroundDrawable(pill(C_PILL));
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

        TextView fbEntry = new TextView(this);
        fbEntry.setText("反馈与建议");
        fbEntry.setTextSize(m.backSize);
        fbEntry.setTextColor(C_PRIMARY);
        fbEntry.setGravity(Gravity.CENTER);
        fbEntry.setPadding(dp(10), dp(10), dp(10), dp(10));
        fbEntry.setBackgroundDrawable(pill(C_PILL));
        applyIcon(fbEntry, "ic_feedback", C_PRIMARY, 15);
        fbEntry.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { showScreen(S_FEEDBACK); }
        });
        LinearLayout.LayoutParams fbLp = wrap();
        fbLp.topMargin = dp(6);
        settingsInner.addView(fbEntry, fbLp);
        TextView fbHint = new TextView(this);
        fbHint.setText("反馈会以 Issue 形式提交到项目的 GitHub 仓库，需先在手机浏览器完成一次登录授权。");
        fbHint.setTextSize(m.subtitleSize - 2f);
        fbHint.setTextColor(C_SUB);
        fbHint.setGravity(Gravity.CENTER);
        fbHint.setPadding(dp(4), dp(3), dp(4), dp(2));
        settingsInner.addView(fbHint, wrap());

        TextView orderHint = new TextView(this);
        orderHint.setText("主页科目顺序（点 ↑↓ 调整，自动保存）");
        orderHint.setTextSize(m.subtitleSize - 2f);
        orderHint.setTextColor(C_SUB);
        orderHint.setGravity(Gravity.CENTER);
        orderHint.setPadding(dp(4), dp(2), dp(4), dp(4));
        settingsInner.addView(orderHint, wrap());
        for (int idx = 0; idx < orderKeys.size(); idx++) {
            settingsInner.addView(orderRow(idx), wrap());
        }

        settingsInner.addView(sectionHeader("关于"), wrap());
        TextView aboutEntry = new TextView(this);
        aboutEntry.setText("关于本应用");
        aboutEntry.setTextSize(m.backSize);
        aboutEntry.setTypeface(Typeface.DEFAULT_BOLD);
        aboutEntry.setTextColor(C_PRIMARY);
        aboutEntry.setGravity(Gravity.CENTER);
        aboutEntry.setPadding(dp(10), dp(10), dp(10), dp(10));
        aboutEntry.setBackgroundDrawable(pill(C_PILL));
        applyIcon(aboutEntry, "ic_info", C_PRIMARY, 15);
        aboutEntry.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                vibrate();
                showScreen(S_ABOUT);
            }
        });
        settingsInner.addView(aboutEntry, wrap());
        TextView aboutHint = new TextView(this);
        aboutHint.setText("版本信息、更新日志、感谢名单。");
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
        reset.setBackgroundDrawable(pill(C_PILL));
        reset.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                fontSizeSp = m.round ? 13f : 14f;
                listSizeSp = m.round ? 14f : 15f;
                prefs.edit().clear().apply();
                orderKeys.clear();
                for (String k : DEFAULT_ORDER) if (subjectMap.containsKey(k)) orderKeys.add(k);
                saveOrderKeys(); markSubjectsDirty();
                saveSettings();
                renderSettings();
                Toast.makeText(MainActivity.this, "已恢复默认", Toast.LENGTH_SHORT).show();
            }
        });
        LinearLayout.LayoutParams rlp = wrap();
        rlp.topMargin = dp(12);
        settingsInner.addView(reset, rlp);
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
        // 记下离开页面的滚动位置，回来时原样恢复（避免淡入后跳回顶部）
        ListView leaving = listOf(screen);
        if (leaving != null && leaving.getChildCount() > 0) {
            savedPos[screen] = leaving.getFirstVisiblePosition();
            savedTop[screen] = leaving.getChildAt(0).getTop();
            hasSaved[screen] = true;
        }
        screen = s;
        View[] screens = {subjectScreen, blockScreen, childScreen, listScreen, detailScreen, settingsScreen,
                searchScreen, feedbackScreen, mineScreen, profileScreen, changelogScreen, aboutScreen,
                displayScreen, todayScreen};
        View target = screens[s];
        boolean shown = target.getVisibility() == View.VISIBLE && target.getAlpha() > 0.95f
                && Math.abs(target.getTranslationX()) < dp(4);
        for (int i = 0; i < screens.length; i++) {
            if (i == s) continue;
            screens[i].animate().cancel();
            screens[i].setVisibility(View.GONE);
            screens[i].setTranslationX(0f);
            screens[i].setAlpha(1f);
        }
        target.animate().cancel();
        target.setVisibility(View.VISIBLE);
        target.setTranslationX(0f);
        if (shown) {
            target.setAlpha(1f);
        } else {
            target.setAlpha(0f);
            target.animate().alpha(1f).setDuration(180).start();
        }

        if (s == S_SUBJECT) {
            if (subjectDirty) {
                List<String> names = new ArrayList<String>();
                names.add("搜索（全部知识点）");
                for (String k : orderKeys) {
                    Subject su = subjectMap.get(k);
                    if (su != null) names.add(su.name);
                }
                names.add("新增知识点（今日）");
                names.add("设置");
                subjectList.setAdapter(stringAdapter(names, C_PRIMARY));
                subjectDirty = false;
                restoreNext[S_SUBJECT] = true;
            }
        } else if (s == S_SETTINGS) {
            renderSettings();
        } else if (s == S_SEARCH) {
            // 关键词没变就不重设 adapter，返回时列表位置不会跳回顶部
            String q = gSearchBox.getText().toString().trim();
            if (lastQuery == null || !lastQuery.equals(q)) {
                refreshGlobalSearch();
                restoreNext[S_SEARCH] = true;
            }
        } else if (s == S_MYFEEDBACK) {
            if (mineDirty) { mineDirty = false; loadMyIssues(); }
        } else if (s == S_FEEDBACK) {
            updateFeedbackUi();
        } else if (s == S_PROFILE) {
            updateProfileUi();
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
            if (!thanksLoaded) loadThanks();
        } else if (s == S_CHANGELOG) {
            if (!changelogFilled) fillChangelog();
        }

        // 只有刚重建过列表数据的页面才需要把位置摆回去；其余页面由 ListView 自己保留，
        // 额外调用 setSelectionFromTop 反而会触发一次重排，看起来像「往上弹一下」。
        ListView enter = listOf(s);
        if (enter != null && hasSaved[s] && restoreNext[s]) {
            restoreNext[s] = false;
            try {
                enter.setSelectionFromTop(savedPos[s], savedTop[s]);
            } catch (Exception ignored) { }
        }
    }

    private ListView listOf(int s) {
        switch (s) {
            case S_SUBJECT: return subjectList;
            case S_BLOCK: return blockList;
            case S_CHILD: return childList;
            case S_ITEM: return itemList;
            case S_SEARCH: return searchList;
            case S_MYFEEDBACK: return mineList;
            case S_TODAY: return todayList;
            default: return null;
        }
    }

    @Override
    protected void onDestroy() {
        dismissCodeDialog();
        dismissUpdateDialog();
        super.onDestroy();
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
        int t = backTarget();
        if (t >= 0) showScreen(t);
    }

    /** 当前页的上一级页面（主页返回 -1）。 */
    private int backTarget() {
        if (screen == S_DETAIL) return detailBack;
        if (screen == S_ITEM) return S_CHILD;
        if (screen == S_CHILD) return S_BLOCK;
        if (screen == S_BLOCK) return S_SUBJECT;
        if (screen == S_SETTINGS) return S_SUBJECT;
        if (screen == S_SEARCH) return S_SUBJECT;
        if (screen == S_FEEDBACK) return S_SETTINGS;
        if (screen == S_MYFEEDBACK) return S_FEEDBACK;
        if (screen == S_PROFILE) return S_SETTINGS;
        if (screen == S_CHANGELOG) return changelogBack == S_CHANGELOG ? S_SETTINGS : changelogBack;
        if (screen == S_ABOUT) return S_SETTINGS;
        if (screen == S_DISPLAY) return tuningFirstRun ? -1 : S_SETTINGS;
        if (screen == S_TODAY) return S_SUBJECT;
        return -1;
    }

    private View screenView(int s) {
        switch (s) {
            case S_SUBJECT: return subjectScreen;
            case S_BLOCK: return blockScreen;
            case S_CHILD: return childScreen;
            case S_ITEM: return listScreen;
            case S_DETAIL: return detailScreen;
            case S_SETTINGS: return settingsScreen;
            case S_SEARCH: return searchScreen;
            case S_CHANGELOG: return changelogScreen;
            case S_ABOUT: return aboutScreen;
            case S_DISPLAY: return displayScreen;
            case S_TODAY: return todayScreen;
            default: return null;
        }
    }

    /** 拖动预览时把下载进度卡重新提到最上层。 */
    private void ensureOverlaysOnTop() {
        if (progBox != null && progBox.getVisibility() == View.VISIBLE) progBox.bringToFront();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (swipeBack && fullscreenView == null) {
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
                    float zone = swipeFromInput ? dp(18) : swipeZone();
                    if (screen != S_SUBJECT && swipeX <= zone && dx > dp(16) && dx > dy * 1.5f) {
                        swipeActive = true;
                        swipeView = currentScreenView();
                        swipeView.animate().cancel();
                        preparePrev(swipeView);
                    }
                }
                if (swipeActive && swipeView != null) {
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
                    float need = swipeFromInput ? dp(80) : dp(46);
                    if (ev.getX() - swipeX > need) finishSwipe(); else resetSwipe();
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
            case S_BLOCK: return blockScreen;
            case S_CHILD: return childScreen;
            case S_ITEM: return listScreen;
            case S_DETAIL: return detailScreen;
            case S_SETTINGS: return settingsScreen;
            case S_SEARCH: return searchScreen;
            case S_FEEDBACK: return feedbackScreen;
            case S_MYFEEDBACK: return mineScreen;
            case S_PROFILE: return profileScreen;
            case S_CHANGELOG: return changelogScreen;
            case S_ABOUT: return aboutScreen;
            case S_DISPLAY: return displayScreen;
            case S_TODAY: return todayScreen;
            default: return subjectScreen;
        }
    }

    /** 拖动开始时把上一级页面放到当前页下面，并左移一点做视差预览。 */
    private void preparePrev(View cur) {
        int t = backTarget();
        View prev = (t >= 0) ? screenView(t) : null;
        float w = (root != null && root.getWidth() > 0) ? root.getWidth() : cur.getWidth();
        if (prev != null && prev != cur) {
            prev.animate().cancel();
            prev.setVisibility(View.VISIBLE);
            prev.setAlpha(1f);
            prev.setTranslationX(-w * PREV_PARALLAX);
            prev.bringToFront();      // 先放下面一层
            swipePrev = prev;
        } else {
            swipePrev = null;
        }
        cur.bringToFront();           // 当前页始终在最上层
        ensureOverlaysOnTop();
    }

    /** 松手过阈值：当前页滑出、预览页归位，随后真正切页。 */
    private void finishSwipe() {
        final View v = swipeView;
        final View prev = swipePrev;
        swipeView = null;
        swipePrev = null;
        if (v == null) return;
        swipeBusy = true;
        float w = Math.max(v.getWidth(), dp(80));
        v.animate().translationX(w).alpha(0.85f).setDuration(170)
                .withEndAction(new Runnable() {
                    public void run() {
                        v.setTranslationX(0f);
                        v.setAlpha(1f);
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
        swipeView = null;
        swipePrev = null;
        if (v == null) return;
        swipeBusy = true;
        float w = Math.max(v.getWidth(), dp(80));
        v.animate().translationX(0f).alpha(1f).setDuration(160)
                .withEndAction(new Runnable() {
                    public void run() {
                        swipeBusy = false;
                        ensureOverlaysOnTop();
                    }
                }).start();
        if (prev != null) {
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
    private boolean heartRunning = false;
    private int tapCount = 0;
    private long lastTapTime = 0;

    private static final int AVATAR_DP = 34;
    /** 每次进关于页都按当前数据重算一次，保证数字是实时的。 */
    private void refreshAboutInfo() {
        if (aboutInfo == null) return;
        int itemCount = 0, imgCount = 0;
        for (Subject su : subjects) {
            for (Block bl : su.blocks) {
                for (Child ch : bl.children) {
                    for (Item it : ch.items) {
                        itemCount++;
                        if (it.img != null && it.img.length() > 0) imgCount++;
                    }
                }
            }
        }
        aboutInfo.setText("腕上知识汇总 v" + appVersion() + "（build " + appVersionCode() + "）"
                + "\n作者：@" + REPO_OWNER
                + "\n屏幕：" + (m.round ? "圆形（已启用圆形安全区）" : "方形")
                + "\n共 " + subjects.size() + " 科 · 知识点 " + itemCount + " 条 · 配图 " + imgCount + " 张");
    }


    private void buildAboutScreen() {
        aboutScroll = new ScrollView(this);
        aboutScroll.setVerticalScrollBarEnabled(false);
        aboutInner = new LinearLayout(this);
        aboutInner.setOrientation(LinearLayout.VERTICAL);
        aboutScroll.addView(aboutInner);
        aboutScreen.addView(aboutScroll, lw());

        aboutInner.addView(makeBack("‹ 返回设置", new View.OnClickListener() {
            public void onClick(View v) { showScreen(S_SETTINGS); }
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
        logEntry.setBackgroundDrawable(pill(C_PILL));
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

    /** 屏幕中央浮出一张卡片，淡入停留后淡出。 */
    private void showCard() {
        final TextView tv = new TextView(this);
        tv.setText(HINT_TEXT);
        tv.setTextSize(m.titleSize);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setTextColor(C_HI);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dp(20), dp(14), dp(20), dp(14));
        tv.setBackgroundDrawable(pill(0xFF151515));
        tv.setAlpha(0f);
        tv.setScaleX(0.82f);
        tv.setScaleY(0.82f);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.CENTER;
        root.addView(tv, lp);
        tv.bringToFront();
        tv.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(220).start();

        tv.postDelayed(new Runnable() {
            public void run() {
                tv.animate().alpha(0f).setDuration(320).withEndAction(new Runnable() {
                    public void run() {
                        try { root.removeView(tv); } catch (Exception ignored) { }
                    }
                }).start();
            }
        }, 1700);
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

    private void buildTodayScreen() {
        todayTitle = new TextView(this);
        todayTitle.setText("今日新增");
        todayTitle.setTextSize(m.subtitleSize + 2f);
        todayTitle.setTypeface(Typeface.DEFAULT_BOLD);
        todayTitle.setTextColor(C_PRIMARY);
        todayTitle.setGravity(Gravity.CENTER);
        todayTitle.setPadding(0, 0, 0, dp(3));
        todayScreen.addView(todayTitle, wrap());

        todayFallbackBtn = new TextView(this);
        todayFallbackBtn.setTextSize(m.subtitleSize - 1f);
        todayFallbackBtn.setTextColor(C_SUB);
        todayFallbackBtn.setGravity(Gravity.CENTER);
        todayFallbackBtn.setPadding(dp(8), dp(6), dp(8), dp(6));
        todayFallbackBtn.setBackgroundDrawable(pill(C_PILL));
        todayFallbackBtn.setVisibility(View.GONE);
        todayScreen.addView(todayFallbackBtn, wrap());

        todayList = makeList();
        todayList.addHeaderView(makeBackHeader("\u2039 返回学科", new View.OnClickListener() {
            public void onClick(View v) { showScreen(S_SUBJECT); }
        }));
        todayList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                if (pos == 0) return;                      // 返回键
                int idx = pos - 1;
                if (idx < 0 || idx >= todayShown.size()) return;
                vibrate();
                Hit h = todayShown.get(idx);
                openDetail(h.item.t, h.item.sec, h.path, S_TODAY, h.item.img);
            }
        });
        // 滚到接近底部再追加，避免一次把上千条都塞进列表
        todayList.setOnScrollListener(new AbsListView.OnScrollListener() {
            public void onScrollStateChanged(AbsListView v, int state) { }
            public void onScroll(AbsListView v, int first, int visible, int total) {
                if (total > 0 && first + visible >= total - 5) loadMoreToday();
            }
        });
        todayScreen.addView(todayList, lw());
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
        String newest = "";
        boolean hasToday = false;
        for (Subject su : subjects) {
            for (Block b : su.blocks) {
                for (Child c : b.children) {
                    for (Item it : c.items) {
                        if (it.d == null || it.d.length() == 0) continue;
                        if (it.d.equals(today)) hasToday = true;
                        if (newest.length() == 0 || it.d.compareTo(newest) > 0) newest = it.d;
                    }
                }
            }
        }
        // 今天有新增就只显示今天的，「看最近一批」这个开关自动失效；
        // 今天没有时才允许退回历史上最近的一批
        if (hasToday) fallback = false;
        todayFallbackOn = fallback;
        String pick = fallback ? newest : today;
        todayBuiltFor = today;

        if (pick.length() > 0) {
            for (Subject su : subjects) {
                for (Block b : su.blocks) {
                    for (Child c : b.children) {
                        for (Item it : c.items) {
                            if (it.d == null || !it.d.equals(pick)) continue;
                            Hit h = new Hit();
                            h.item = it;
                            h.path = su.name + " \u203a " + b.name
                                    + (it.id != null && it.id.length() > 0 ? "  \u00b7  " + it.id : "");
                            todayAll.add(h);
                        }
                    }
                }
            }
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

        todayAdapter = searchAdapter(todayShown);
        todayList.setAdapter(todayAdapter);
        loadMoreToday();
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

    /** 追加一页，无感：只 notifyDataSetChanged，不重建整表。 */
    private void loadMoreToday() {
        if (todayAdapter == null) return;
        if (todayShown.size() >= todayAll.size()) return;
        int from = todayShown.size();
        int to = Math.min(todayAll.size(), from + TODAY_PAGE);
        for (int i = from; i < to; i++) todayShown.add(todayAll.get(i));
        todayAdapter.notifyDataSetChanged();
    }

    // ================= 显示调节 =================
    private interface OnFloatChange { void onChanged(float v); }

    /** 滑块松手时的回调：用于在用户调完后统一保存并重建界面。 */
    private interface OnFloatRelease { void onReleased(float v); }

    private TextView pvTag, pvTitle, pvBody, pvList, pvSub;
    /** 预览卡片（拖动缩放时实时放大的那一张）。 */
    private LinearLayout pvCard;

    private void buildDisplayScreen() {
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        displayInner = new LinearLayout(this);
        displayInner.setOrientation(LinearLayout.VERTICAL);
        sv.addView(displayInner);
        displayScreen.addView(sv, lw());
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
        pendingScreen = backTo;
        recreate();
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
            public void onStartTrackingTouch(SeekBar bar) { }
            public void onStopTrackingTouch(SeekBar bar) {
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
                        applyLiveScale(v);   // 拖动过程中界面就跟着变
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
        done.setBackgroundDrawable(pill(0xFF14384A));
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
    }

    // ================= 更新日志 =================
    private void buildChangelogScreen() {
        changelogScroll = new ScrollView(this);
        changelogScroll.setVerticalScrollBarEnabled(false);
        changelogInner = new LinearLayout(this);
        changelogInner.setOrientation(LinearLayout.VERTICAL);
        changelogScroll.addView(changelogInner);
        changelogScreen.addView(changelogScroll, lw());

        changelogInner.addView(makeBack("‹ 返回", new View.OnClickListener() {
            public void onClick(View v) { showScreen(backTarget()); }
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
        feedbackScroll = new ScrollView(this);
        feedbackScroll.setVerticalScrollBarEnabled(false);
        feedbackInner = new LinearLayout(this);
        feedbackInner.setOrientation(LinearLayout.VERTICAL);
        feedbackScroll.addView(feedbackInner);
        feedbackScreen.addView(feedbackScroll, lw());

        feedbackInner.addView(makeBack("‹ 返回设置", new View.OnClickListener() {
            public void onClick(View v) { showScreen(S_SETTINGS); }
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
                dismissCodeDialog();
                updateFeedbackUi();
                updateProfileUi();
                if (ghToken != null) loadProfileFromGitHub();
            }
        });
    }

    private void logoutGitHub() {
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
                            copyToClipboard(code);
                            updateFeedbackUi();
                            showCodeDialog(code, fbCodeUrl);
                        }
                    });
                    pollDeviceToken(device, interval);
                } catch (final Exception e) {
                    loginRunning = false;
                    afterLoginEnds();
                    toast("登录失败：" + e.getMessage());
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
                    payload.put("body", feedbackBody(FB_TYPES[fbType], desc));
                    JSONObject o = new JSONObject(httpPost(GH_ISSUES, payload.toString(),
                            "application/json", ghToken));
                    final int num = o.optInt("number", 0);
                    final String msg = o.optString("message", "");
                    runOnUiThread(new Runnable() {
                        public void run() {
                            fbSubmitBtn.setEnabled(true);
                            if (num > 0) {
                                fbDesc.setText("");
                                fbDraft = "";
                                fbSubmitBtn.setText("提交反馈");
                                toast("已提交，感谢反馈（issue #" + num + "）");
                            } else {
                                fbSubmitBtn.setText("提交反馈");
                                toast("提交失败：" + (msg.length() > 0 ? msg : "未知错误"));
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
                            toast("提交失败：" + e.getMessage());
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
            InputStream is = cn.getInputStream();
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] b = new byte[4096];
            int n;
            while ((n = is.read(b)) > 0) bos.write(b, 0, n);
            is.close();
            return new String(bos.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            cn.disconnect();
        }
    }
    // ================= 我的反馈记录 =================

    private void loadMyIssues() {
        mineIssues.clear();
        mineRows.clear();
        mineRows.add(new String[]{"正在加载…", "", "#FF8A8A8A"});
        mineList.setAdapter(pairAdapter(mineRows));
        final String login = ghLogin;
        final boolean all = mineAllMode;
        new Thread(new Runnable() {
            public void run() {
                try {
                    String url = apiAuthedBase() + "https://api.github.com/repos/" + REPO + "/issues?state=all&per_page="
                            + (all ? "100" : "40")
                            + (!all && login != null && login.length() > 0 ? "&creator=" + login : "");
                    JSONArray arr = new JSONArray(httpGetAuthed(url));
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
                            mineList.setAdapter(pairAdapter(mineRows));
                        }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        public void run() {
                            mineRows.clear();
                            mineRows.add(new String[]{"加载失败：" + e.getMessage(), "", "#FFE57373"});
                            mineList.setAdapter(pairAdapter(mineRows));
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

    /** 两行列表：第一行标题，第二行小字（状态 · 日期）。 */
    private ArrayAdapter<String[]> pairAdapter(final List<String[]> rows) {
        return new ArrayAdapter<String[]>(this, 0, rows) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                LinearLayout box;
                TextView title, sub;
                if (convertView instanceof LinearLayout) {
                    box = (LinearLayout) convertView;
                    title = (TextView) box.getChildAt(0);
                    sub = (TextView) box.getChildAt(1);
                } else {
                    box = new LinearLayout(MainActivity.this);
                    box.setOrientation(LinearLayout.VERTICAL);
                    box.setGravity(Gravity.CENTER);
                    box.setPadding(dp(10), dp(8), dp(10), dp(8));
                    GradientDrawable content = pill(C_PILL);
                    GradientDrawable mask = new GradientDrawable();
                    mask.setColor(Color.WHITE);
                    mask.setCornerRadius(dp(13));
                    try {
                        box.setBackground(new RippleDrawable(ColorStateList.valueOf(0x334FC3F7), content, mask));
                    } catch (Exception ignored) {
                        box.setBackgroundDrawable(content);
                    }
                    title = new TextView(MainActivity.this);
                    title.setTextSize(lsSp() - 1f);
                    title.setTextColor(0xFFF0F0F0);
                    title.setTypeface(Typeface.DEFAULT_BOLD);
                    title.setGravity(Gravity.CENTER);
                    title.setMaxLines(2);
                    title.setEllipsize(TextUtils.TruncateAt.END);
                    box.addView(title, wrap());

                    sub = new TextView(MainActivity.this);
                    sub.setTextSize(m.subtitleSize - 2f);
                    sub.setGravity(Gravity.CENTER);
                    sub.setMaxLines(1);
                    sub.setPadding(0, dp(2), 0, 0);
                    box.addView(sub, wrap());
                }
                String[] r = getItem(position);
                title.setText(r[0]);
                sub.setText(r[1]);
                try {
                    sub.setTextColor(Color.parseColor(r[2]));
                } catch (Exception ignored) {
                    sub.setTextColor(C_SUB);
                }
                return box;
            }
        };
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
                    String u = apiAuthedBase() + "https://api.github.com/repos/" + REPO
                            + "/issues/" + number + "/comments?per_page=30";
                    JSONArray arr = new JSONArray(httpGetAuthed(u));
                    for (int i = 0; i < arr.length(); i++) {
                        String b = arr.getJSONObject(i).optString("body", "");
                        if (b.length() > 0) reply = b;
                    }
                } catch (Exception ignored) { }
                final String finalReply = reply;
                runOnUiThread(new Runnable() {
                    public void run() {
                        List<Section> secs = new ArrayList<Section>();
                        Section s1 = new Section();
                        s1.h = "反馈内容";
                        s1.c = "K";
                        s1.body = body.length() == 0 ? "(没有正文)" : body;
                        secs.add(s1);

                        Section s2 = new Section();
                        s2.h = "处理回复";
                        if (finalReply != null) {
                            s2.c = "M";
                            s2.body = finalReply;
                        } else {
                            s2.c = "E";
                            s2.body = "还没有回复。处理后会写在这里，状态变成「已处理」时说明已经修好。";
                        }
                        secs.add(s2);
                        openDetail("#" + number + " " + title, secs, status + " · " + shortTime(
                                issue.optString("created_at", "")), S_MYFEEDBACK, null);
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
        confirmIssueAction("撤回这条反馈？",
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
        confirmIssueAction("删除 #" + issue.optInt("number") + "？",
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
    private void confirmIssueAction(String title, String msg, String okText, int okColor,
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
        profScroll = new ScrollView(this);
        profScroll.setVerticalScrollBarEnabled(false);
        profInner = new LinearLayout(this);
        profInner.setOrientation(LinearLayout.VERTICAL);
        profScroll.addView(profInner);
        profileScreen.addView(profScroll, lw());

        profInner.addView(makeBack("‹ 返回设置", new View.OnClickListener() {
            public void onClick(View v) { showScreen(S_SETTINGS); }
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
                        if (!token.equals(ghToken)) return;
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
