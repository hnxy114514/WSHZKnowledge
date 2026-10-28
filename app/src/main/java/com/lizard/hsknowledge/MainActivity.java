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
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Build;
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
import android.widget.ScrollView;
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
    /** API 只试直连 + 前几个镜像，避免逐个超时干等。 */
    private static final int API_TRIES = 3;
    private static final String VERSION_JSON_RAW =
            "https://raw.githubusercontent.com/" + REPO + "/main/cloud/version.json";
    private static final String APK_FILE = "update.apk";
    private static final String APK_AUTHORITY = "com.lizard.hsknowledge.fileprovider";
    private static final int C_TEXT = 0xFFE8E8E8;    // 正文
    private static final int C_SUB = 0xFF8A8A8A;     // 次要

    // ---------- 数据模型 ----------
    static class Section { String h, c, body; }
    static class Item { String t; int star; String img; List<Section> sec = new ArrayList<Section>(); }
    static class Child { String type; String name; List<Item> items = new ArrayList<Item>(); List<Section> sec = new ArrayList<Section>(); }
    static class Block { String name; List<Child> children = new ArrayList<Child>(); }
    static class Subject { String key, name; List<Block> blocks = new ArrayList<Block>(); }
    static class Hit { String path; Item item; }

    private final List<Subject> subjects = new ArrayList<Subject>();
    private final Map<String, Subject> subjectMap = new HashMap<String, Subject>();
    private final List<Hit> allHits = new ArrayList<Hit>();
    private final List<Hit> searchHits = new ArrayList<Hit>();

    // ---------- 导航 ----------
    private static final int S_SUBJECT = 0, S_BLOCK = 1, S_CHILD = 2, S_ITEM = 3, S_DETAIL = 4, S_SETTINGS = 5,
            S_SEARCH = 6, S_FEEDBACK = 7;
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
    private static final String[] FB_TYPES = {"内容有误", "公式显示", "闪退卡顿", "功能建议"};
    private LinearLayout feedbackScreen, feedbackInner;
    private ScrollView feedbackScroll;
    private TextView fbStatusTxt, fbLoginBtn, fbSubmitBtn, fbCodeBig;
    private ImageView fbQr;
    private EditText fbDesc;
    private final TextView[] fbTypeBtns = new TextView[4];
    private int fbType = 0;
    private String fbDraft = "";
    private String ghToken, ghLogin;
    private String fbCode, fbCodeUrl;
    private AlertDialog codeDialog;
    private String lastPath = "";
    private FrameLayout progBox;
    private View progFill;
    private TextView progTitle, progDetail;
    private int progBarW;

    // ---------- 设置 ----------
    private SharedPreferences prefs;
    private float fontSizeSp = 14f;
    private float listSizeSp = 15f;
    private boolean isRound = false;
    // 右滑返回手势（设置里可关）
    private static final float PREV_PARALLAX = 0.32f;  // 预览页的起始左移比例（视差）
    private boolean swipeBack = true;
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
                titleSize = 15f; subtitleSize = 11f; backSize = 12f; secTagSize = 10f;
            } else {
                sidePad = dp(14); topPad = dp(14); bottomPad = dp(14);
                roundSafe = 0;
                titleSize = 17f; subtitleSize = 12f; backSize = 13f; secTagSize = 11f;
            }
        }
    }
    private Metrics m;

    private int dp(float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try { isRound = getResources().getConfiguration().isScreenRound(); } catch (Exception ignored) { }
        m = new Metrics();
        prefs = getSharedPreferences("settings", MODE_PRIVATE);
        fontSizeSp = prefs.getFloat("font_size", m.round ? 13f : 14f);
        listSizeSp = prefs.getFloat("list_size", m.round ? 14f : 15f);
        swipeBack = prefs.getBoolean("swipe_back", true);
        loadGitHub();
        loadData();
        loadOrderKeys();
        buildUi();
        showScreen(S_SUBJECT);
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

    /** 检查 GitHub Release 最新版，有新版则先申请安装权限，再下载 APK 覆盖安装。 */
    private void checkUpdate(final boolean silent) {
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
                    runOnUiThread(new Runnable() {
                        public void run() {
                            if (Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
                                progressHide();
                                requestInstallPermission();
                                return;
                            }
                            new Thread(new Runnable() {
                                public void run() {
                                    try {
                                        if (!reuseDownloaded(remoteTag)) {
                                            downloadApkWithMirrors(url, remoteTag);
                                        }
                                        progressShow("下载完成", "正在打开安装程序…", 100);
                                        runOnUiThread(new Runnable() {
                                            public void run() {
                                                progressHide();
                                                installApk();
                                            }
                                        });
                                    } catch (final Exception e) {
                                        progressHide();
                                        toast("下载失败：" + e.getMessage());
                                    }
                                }
                            }).start();
                        }
                    });
                } catch (final Exception e) {
                    progressHide();
                    if (!silent) toast("检查更新失败：" + e.getMessage());
                }
            }
        }).start();
    }

    private static class RelInfo {
        String tag;
        String apkUrl;
    }

    /** 先走 API；API 全不通时改用仓库里的静态 version.json（静态文件更容易代理）。 */
    private RelInfo fetchReleaseInfo() throws Exception {
        RelInfo r = new RelInfo();
        try {
            JSONObject obj = fetchLatestRelease();
            r.tag = obj.optString("tag_name", "");
            JSONArray assets = obj.optJSONArray("assets");
            if (assets != null) {
                for (int i = 0; i < assets.length(); i++) {
                    JSONObject a = assets.getJSONObject(i);
                    if (a.optString("name", "").endsWith(".apk")) {
                        r.apkUrl = a.optString("browser_download_url", "");
                        break;
                    }
                }
            }
        } catch (Exception e) {
            r.tag = fetchTagFallback();
        }
        if (r.tag == null || r.tag.length() == 0) throw new Exception("无法获取版本号");
        if (r.apkUrl == null || r.apkUrl.length() == 0) r.apkUrl = apkUrlFor(r.tag);
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
        for (int i = 0; i <= API_MIRRORS.length; i++) {
            String u = (i == 0) ? RELEASES_API : API_MIRRORS[i - 1] + RELEASES_API;
            try {
                return new JSONObject(httpGetText(u, 5000, 6000));
            } catch (Exception e) {
                last = e;
            }
        }
        throw (last == null) ? new Exception("无法连接更新服务器") : last;
    }

    private String fetchTagFallback() throws Exception {
        Exception last = null;
        int tries = Math.min(API_TRIES, MIRRORS.length);
        for (int i = 0; i <= tries; i++) {
            String u = (i == 0) ? VERSION_JSON_RAW : MIRRORS[i - 1] + VERSION_JSON_RAW;
            try {
                String v = new JSONObject(httpGetText(u, 5000, 6000)).optString("version", "");
                if (v.length() > 0) return v.startsWith("v") ? v : "v" + v;
            } catch (Exception e) {
                last = e;
            }
        }
        throw (last == null) ? new Exception("无法获取版本号") : last;
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

    private void requestInstallPermission() {
        try {
            startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + getPackageName())));
            toast("请开启「允许安装未知应用」，再回来点一次检查更新");
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
        card.addView(progTitle, wrap());

        progDetail = new TextView(this);
        progDetail.setTextSize(m.subtitleSize - 1.5f);
        progDetail.setTextColor(C_SUB);
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
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (Build.VERSION.SDK_INT >= 24) {
            i.setDataAndType(Uri.parse("content://" + APK_AUTHORITY + "/" + APK_FILE), "application/vnd.android.package-archive");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } else {
            i.setDataAndType(Uri.fromFile(f), "application/vnd.android.package-archive");
        }
        startActivity(i);
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

        // 主页
        subjectList = makeList();
        subjectList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                int idx = pos - 1;
                if (idx < 0) { showScreen(S_SEARCH); return; }
                if (idx >= orderKeys.size()) { showScreen(S_SETTINGS); return; }
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
        b.setOnClickListener(onClick);
        return b;
    }

    /** 作为列表/正文首项的返回键：随内容一起滚动，滚出屏幕后不再占用顶部空间。 */
    private TextView makeBackHeader(String label, View.OnClickListener onClick) {
        TextView b = makeBack(label, onClick);
        b.setLayoutParams(new AbsListView.LayoutParams(
                AbsListView.LayoutParams.MATCH_PARENT, AbsListView.LayoutParams.WRAP_CONTENT));
        return b;
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
                tv.setTextSize(listSizeSp);
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
                    title.setTextSize(listSizeSp);
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
            empty.setTextSize(fontSizeSp);
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

                TextView body = new TextView(this);
                body.setText(applyMarkup(s.body));
                body.setTextSize(fontSizeSp);
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
            float hook = size * 0.32f;
            float w = hook + in[0] + size * 0.12f;
            float asc = in[1] + size * 0.18f;
            float desc = Math.max(in[2], size * 0.06f) + size * 0.07f;
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
            float hook = size * 0.32f;
            float innerX = x + hook;
            drawList(c, p, t.kids.get(0), innerX, base, size, color);
            // 根号：一个连续 Path 把「勾」与「顶部横线」连成一体，比例随内容高度伸缩
            float top = base - in[1] - size * 0.12f;
            float bot = base + Math.max(in[2], size * 0.06f) + size * 0.08f;
            Path rad = new Path();
            rad.moveTo(x + hook * 0.78f, bot);
            rad.lineTo(x + hook * 0.12f, top + size * 0.10f);
            rad.lineTo(x + hook * 0.44f, top);
            rad.lineTo(innerX + in[0] + size * 0.08f, top);
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

        InlineSpan(Inl root) { this(root, 0); }

        InlineSpan(Inl root, int script) {
            this.root = root;
            this.script = script;
        }

        private float sizeOf(float orig) {
            return script == 0 ? orig : orig * 0.74f;
        }

        private float baseOf(float orig, float y) {
            if (script == 1) return y - orig * 0.30f;
            if (script == 2) return y + orig * 0.18f;
            return y;
        }

        @Override
        public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
            float orig = paint.getTextSize();
            float size = sizeOf(orig);
            float[] m = measList(root, paint, size);
            paint.setTextSize(orig);
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
            drawInl(canvas, paint, root, x, baseOf(orig, y), sizeOf(orig), color);
            paint.setTextSize(orig);
            paint.setColor(color);
            paint.setTextAlign(Paint.Align.LEFT);
            paint.setStyle(Paint.Style.FILL);
        }
    }

    private SpannableStringBuilder applyMarkup(String src) {
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
                    ssb.setSpan(new InlineSpan(root), st, ssb.length(),
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
                    ssb.setSpan(new InlineSpan(root), st, ssb.length(),
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
                    ssb.setSpan(new InlineSpan(root, sup ? 1 : 2), start, ssb.length(),
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    i = next;
                    continue;
                }
                ssb.append(inner);
                if (sup) ssb.setSpan(new SuperscriptSpan(), start, ssb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                else ssb.setSpan(new SubscriptSpan(), start, ssb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                ssb.setSpan(new RelativeSizeSpan(0.70f), start, ssb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                i = next;
            } else if (c == '*' && i + 1 < n && txt.charAt(i + 1) == '*') {
                int end = txt.indexOf("**", i + 2);
                if (end < 0) { ssb.append(c); i++; continue; }
                String inner = txt.substring(i + 2, end);
                // 递归处理重点内容内的上下标，再整体加粗 + 高亮色
                SpannableStringBuilder innerSb = applyMarkup(inner);
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
        settingsInner.addView(sectionHeader("显示"), wrap());
        settingsInner.addView(settingRow("正文字号", (int) fontSizeSp, new View.OnClickListener() {
            public void onClick(View v) {
                fontSizeSp = clamp(fontSizeSp - 1, 11f, 22f);
                saveSettings(); renderSettings();
            }
        }, new View.OnClickListener() {
            public void onClick(View v) {
                fontSizeSp = clamp(fontSizeSp + 1, 11f, 22f);
                saveSettings(); renderSettings();
            }
        }), wrap());
        settingsInner.addView(settingRow("列表字号", (int) listSizeSp, new View.OnClickListener() {
            public void onClick(View v) {
                listSizeSp = clamp(listSizeSp - 1, 12f, 20f);
                saveSettings(); renderSettings();
            }
        }, new View.OnClickListener() {
            public void onClick(View v) {
                listSizeSp = clamp(listSizeSp + 1, 12f, 20f);
                saveSettings(); renderSettings();
            }
        }), wrap());

        settingsInner.addView(toggleRow("右滑返回", swipeBack, new View.OnClickListener() {
            public void onClick(View v) {
                swipeBack = !swipeBack;
                prefs.edit().putBoolean("swipe_back", swipeBack).apply();
                renderSettings();
            }
        }), wrap());
        TextView swipeHint = new TextView(this);
        swipeHint.setText("从屏幕左侧 1/4 内向右拖动：当前页跟手滑出、露出上一页预览，滑到一半以上松手即返回，不够则弹回。查看大图时不生效，需点「‹ 关闭」。");
        swipeHint.setTextSize(m.subtitleSize - 2f);
        swipeHint.setTextColor(C_SUB);
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
        updHint.setPadding(dp(4), dp(3), dp(4), dp(2));
        settingsInner.addView(updHint, wrap());
        TextView fbEntry = new TextView(this);
        fbEntry.setText("反馈与建议");
        fbEntry.setTextSize(m.backSize);
        fbEntry.setTextColor(C_PRIMARY);
        fbEntry.setGravity(Gravity.CENTER);
        fbEntry.setPadding(dp(10), dp(10), dp(10), dp(10));
        fbEntry.setBackgroundDrawable(pill(C_PILL));
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
        fbHint.setPadding(dp(4), dp(3), dp(4), dp(2));
        settingsInner.addView(fbHint, wrap());

        TextView orderHint = new TextView(this);
        orderHint.setText("主页科目顺序（点 ↑↓ 调整，自动保存）");
        orderHint.setTextSize(m.subtitleSize - 2f);
        orderHint.setTextColor(C_SUB);
        orderHint.setPadding(dp(4), dp(2), dp(4), dp(4));
        settingsInner.addView(orderHint, wrap());
        for (int idx = 0; idx < orderKeys.size(); idx++) {
            settingsInner.addView(orderRow(idx), wrap());
        }

        settingsInner.addView(sectionHeader("关于"), wrap());
        TextView about = new TextView(this);
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
        String verName = appVersion();
        int verCode = appVersionCode();
        about.setText("腕上知识汇总 v" + verName + "（build " + verCode + "）"
                + "\n屏幕：" + (m.round ? "圆形（已启用圆形安全区）" : "方形")
                + "\n知识点 " + itemCount + " 条 · 配图 " + imgCount + " 张");
        about.setTextSize(m.subtitleSize - 1f);
        about.setTextColor(C_SUB);
        about.setPadding(dp(4), dp(4), dp(4), dp(4));
        settingsInner.addView(about, wrap());

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
                saveOrderKeys();
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
                saveOrderKeys(); renderSettings();
            }
        });
        TextView dn = orderBtn("▼", idx < orderKeys.size() - 1, new View.OnClickListener() {
            public void onClick(View v) {
                if (idx >= orderKeys.size() - 1) return;
                String k = orderKeys.remove(idx);
                orderKeys.add(idx + 1, k);
                saveOrderKeys(); renderSettings();
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
        row.setOnClickListener(onClick);

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

    private View settingRow(String label, int value,
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

        TextView minusBtn = new TextView(this);
        minusBtn.setText(" − ");
        minusBtn.setTextSize(16);
        minusBtn.setTypeface(Typeface.DEFAULT_BOLD);
        minusBtn.setTextColor(C_PRIMARY);
        minusBtn.setPadding(dp(12), dp(4), dp(12), dp(4));
        minusBtn.setOnClickListener(minus);

        TextView val = new TextView(this);
        val.setText(value + "sp");
        val.setTextSize(m.backSize);
        val.setTextColor(Color.WHITE);
        val.setGravity(Gravity.CENTER);
        val.setPadding(dp(6), 0, dp(6), 0);

        TextView plusBtn = new TextView(this);
        plusBtn.setText(" + ");
        plusBtn.setTextSize(16);
        plusBtn.setTypeface(Typeface.DEFAULT_BOLD);
        plusBtn.setTextColor(C_PRIMARY);
        plusBtn.setPadding(dp(12), dp(4), dp(12), dp(4));
        plusBtn.setOnClickListener(plus);

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
        screen = s;
        View[] screens = {subjectScreen, blockScreen, childScreen, listScreen, detailScreen, settingsScreen,
                searchScreen, feedbackScreen};
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
            List<String> names = new ArrayList<String>();
            names.add("搜索（全部知识点）");
            for (String k : orderKeys) {
                Subject su = subjectMap.get(k);
                if (su != null) names.add(su.name);
            }
            names.add("设置");
            subjectList.setAdapter(stringAdapter(names, C_PRIMARY));
        } else if (s == S_SETTINGS) {
            renderSettings();
        } else if (s == S_SEARCH) {
            // 保留上次输入（只有点 ✕ 才清空），从详情返回时能接着看结果
            refreshGlobalSearch();
        } else if (s == S_FEEDBACK) {
            updateFeedbackUi();
        }
    }

    @Override
    protected void onDestroy() {
        dismissCodeDialog();
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
        fbStatusTxt.setTextColor(C_SUB);
        fbStatusTxt.setGravity(Gravity.CENTER);
        fbStatusTxt.setPadding(0, dp(4), 0, dp(5));
        feedbackInner.addView(fbStatusTxt, wrap());

        fbQr = new ImageView(this);
        fbQr.setScaleType(ImageView.ScaleType.FIT_CENTER);
        fbQr.setVisibility(View.GONE);
        try {
            InputStream qs = getAssets().open("qr_login.png");
            fbQr.setImageBitmap(BitmapFactory.decodeStream(qs));
            qs.close();
        } catch (Exception ignored) { }
        LinearLayout.LayoutParams qlp = new LinearLayout.LayoutParams(dp(112), dp(112));
        qlp.gravity = Gravity.CENTER_HORIZONTAL;
        qlp.topMargin = dp(4);
        feedbackInner.addView(fbQr, qlp);

        fbCodeBig = new TextView(this);
        fbCodeBig.setTextSize(m.titleSize + 4f);
        fbCodeBig.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        fbCodeBig.setTextColor(C_PRIMARY);
        fbCodeBig.setGravity(Gravity.CENTER);
        fbCodeBig.setPadding(dp(8), dp(8), dp(8), dp(8));
        fbCodeBig.setBackgroundDrawable(pill(0xFF14384A));
        fbCodeBig.setVisibility(View.GONE);
        LinearLayout.LayoutParams codeLp = wrap();
        codeLp.topMargin = dp(6);
        feedbackInner.addView(fbCodeBig, codeLp);

        fbLoginBtn = new TextView(this);
        fbLoginBtn.setTextSize(m.backSize);
        fbLoginBtn.setTypeface(Typeface.DEFAULT_BOLD);
        fbLoginBtn.setGravity(Gravity.CENTER);
        fbLoginBtn.setPadding(dp(10), dp(9), dp(10), dp(9));
        fbLoginBtn.setBackgroundDrawable(pill(C_PILL));
        fbLoginBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (loginRunning) {
                    if (fbCodeUrl != null) openBrowser(fbCodeUrl);
                    else toast("正在获取登录码…");
                    return;
                }
                if (ghToken == null) startDeviceLogin(); else logoutGitHub();
            }
        });
        LinearLayout.LayoutParams loginLp = wrap();
        loginLp.topMargin = dp(6);
        feedbackInner.addView(fbLoginBtn, loginLp);

        feedbackInner.addView(sectionHeader("类型"), wrap());
        for (int row = 0; row < 2; row++) {
            LinearLayout line = new LinearLayout(this);
            line.setOrientation(LinearLayout.HORIZONTAL);
            for (int col = 0; col < 2; col++) {
                final int idx = row * 2 + col;
                TextView b = new TextView(this);
                b.setText(FB_TYPES[idx]);
                b.setTextSize(m.subtitleSize - 1f);
                b.setGravity(Gravity.CENTER);
                b.setPadding(dp(4), dp(9), dp(4), dp(9));
                b.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        fbType = idx;
                        updateTypeButtons();
                    }
                });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                        LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                if (col == 0) lp.rightMargin = dp(4); else lp.leftMargin = dp(4);
                if (row == 0) lp.bottomMargin = dp(4);
                line.addView(b, lp);
                fbTypeBtns[idx] = b;
            }
            feedbackInner.addView(line, wrap());
        }

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
        diag.setPadding(dp(4), dp(5), dp(4), dp(8));
        feedbackInner.addView(diag, wrap());

        TextView note = new TextView(this);
        note.setText("提交后会以你的 GitHub 账号在该仓库公开建一条 Issue，别人也能看到。");
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
    }

    private void updateFeedbackUi() {
        if (fbStatusTxt == null) return;
        if (loginRunning) {
            if (fbCode == null) {
                fbStatusTxt.setText("正在获取登录码…");
                fbLoginBtn.setText("请稍候…");
                fbCodeBig.setVisibility(View.GONE);
                fbQr.setVisibility(View.GONE);
            } else {
                fbStatusTxt.setText("手机扫下面二维码，输入这个码授权");
                fbLoginBtn.setText("或用本机浏览器打开");
                fbCodeBig.setText(fbCode);
                fbCodeBig.setVisibility(View.VISIBLE);
                fbQr.setVisibility(View.VISIBLE);
            }
            fbLoginBtn.setTextColor(C_PRIMARY);
            updateTypeButtons();
            return;
        }
        fbCodeBig.setVisibility(View.GONE);
        fbQr.setVisibility(View.GONE);
        if (ghToken == null) {
            fbStatusTxt.setText("尚未登录 GitHub，登录后才能提交");
            fbLoginBtn.setText("登录 GitHub");
            fbLoginBtn.setTextColor(C_PRIMARY);
        } else {
            fbCodeBig.setVisibility(View.GONE);
            fbStatusTxt.setText("已登录 " + (ghLogin != null ? "@" + ghLogin : "GitHub"));
            fbLoginBtn.setText("退出登录");
            fbLoginBtn.setTextColor(C_SUB);
        }
        updateTypeButtons();
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
        updateFeedbackUi();
    }

    private void logoutGitHub() {
        loginRunning = false;
        dismissCodeDialog();
        ghToken = null;
        ghLogin = null;
        prefs.edit().remove("gh_token").remove("gh_login").apply();
        updateFeedbackUi();
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
                    updateFeedbackUi();
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
                dismissCodeDialog();
                updateFeedbackUi();
                toast("登录未完成：" + ("access_denied".equals(err) ? "已取消授权" : err));
                return;
            } catch (Exception ignored) {
                // 网络抖动，下一轮继续
            }
        }
        loginRunning = false;
        dismissCodeDialog();
        updateFeedbackUi();
        if (ghToken == null) toast("登录超时，可再点一次登录");
    }

    private void fetchGitHubLogin() {
        new Thread(new Runnable() {
            public void run() {
                String login = null;
                try {
                    login = new JSONObject(httpGetAuthed(GH_USER)).optString("login", "");
                } catch (Exception ignored) { }
                loginRunning = false;
                dismissCodeDialog();
                saveGitHub(ghToken, login);
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
            toast("请先登录 GitHub");
            return;
        }
        fbSubmitBtn.setText("提交中…");
        fbSubmitBtn.setEnabled(false);
        new Thread(new Runnable() {
            public void run() {
                try {
                    JSONObject payload = new JSONObject();
                    payload.put("title", "[" + FB_TYPES[fbType] + "] " + firstLine(desc, 20));
                    payload.put("body", feedbackBody(desc));
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

    private String feedbackBody(String desc) {
        return "**类型**：" + FB_TYPES[fbType] + "\n\n"
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
            if (token != null) cn.setRequestProperty("Authorization", "token " + token);
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
}
