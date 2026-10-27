package com.lizard.hsknowledge;

import android.app.Activity;
import android.content.Context;
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
import android.os.Bundle;
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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 汇总知识 v14
 * 数据/UI 分离：UI 只渲染，数据来自 assets/data/<科目>.json（每科一文件）。
 * 层级：学科 → 大块(章) → 小节(节)/专题 → 具体知识点 → 详情
 * 自适应：一套系统两套参数（Metrics），运行时判断 isScreenRound——
 *  圆屏收进圆内接安全区、方屏用满宽，同一套代码自动切换。
 * 数学排版：文本/上标/下标/分数/根号统一由行内排版引擎绘制（支持互相嵌套）。
 * 每页(除主页)顶部都有“‹ 返回”（无实体返回键的设备也能返回）。
 *  - 全局搜索 + 节内搜索；列表项完整换行不截断。
 *  - 详情正文支持 ^{上标} _{下标} **重点(加粗+高亮色)**，重点色由数据层标记。
 *  - 配图：双击全屏，全屏内双指缩放、单指拖拽（圆屏可把四角内容拖到可见区）。
 *  - 设置：分「显示 / 数据 / 关于」三类，含科目顺序自定义。
 */
public class MainActivity extends Activity {

    // ---------- 主题色 ----------
    private static final int C_BG = 0xFF000000;      // 全局背景：纯黑
    private static final int C_CARD = 0xFF151515;    // 卡片
    private static final int C_PILL = 0xFF1C1C1C;    // 按钮/列表项
    private static final int C_PRIMARY = 0xFF4FC3F7; // 主色（青蓝）
    private static final int C_HI = 0xFFFFC107;      // 重点高亮（琥珀金）

    // 云端更新地址：GitHub 仓库中的 cloud/update.json（raw 直链）
    private static final String UPDATE_URL = "https://raw.githubusercontent.com/hnxy114514/WSHZKnowledge/main/cloud/update.json";
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
    private static final int S_SUBJECT = 0, S_BLOCK = 1, S_CHILD = 2, S_ITEM = 3, S_DETAIL = 4, S_SETTINGS = 5, S_SEARCH = 6;
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
    private EditText itemSearchBox, gSearchBox;
    private ScrollView detailScroll, settingsScroll;
    private LinearLayout detailInner, settingsInner;
    private FrameLayout fullscreenView;

    // ---------- 设置 ----------
    private SharedPreferences prefs;
    private float fontSizeSp = 14f;
    private float listSizeSp = 15f;
    private boolean isRound = false;
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
        loadData();
        loadOrderKeys();
        buildUi();
        showScreen(S_SUBJECT);
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

    /** 尝试读取云端已下载的 update.json（filesDir），成功则返回 true。 */
    private boolean tryLoadUpdate() {
        try {
            File f = new File(getFilesDir(), "update.json");
            if (!f.exists()) return false;
            byte[] data = new byte[(int) f.length()];
            FileInputStream fis = new FileInputStream(f);
            fis.read(data);
            fis.close();
            JSONObject obj = new JSONObject(new String(data, StandardCharsets.UTF_8));
            JSONArray subs = obj.optJSONArray("subjects");
            if (subs == null || subs.length() == 0) return false;
            for (int i = 0; i < subs.length(); i++) {
                Subject s = parseSubject(subs.getJSONObject(i));
                subjects.add(s);
                subjectMap.put(s.key, s);
            }
            return true;
        } catch (Exception ignored) {
            return false;
        }
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
        if (tryLoadUpdate()) {
            buildHits();
            return;
        }
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

    /** 后台检查云端更新：有新版本则下载 update.json 到 filesDir，下次启动生效。 */
    private void checkUpdate(final boolean silent) {
        new Thread(new Runnable() {
            public void run() {
                try {
                    URL u = new URL(UPDATE_URL);
                    HttpURLConnection cn = (HttpURLConnection) u.openConnection();
                    cn.setConnectTimeout(8000);
                    cn.setReadTimeout(10000);
                    cn.setInstanceFollowRedirects(true);
                    InputStream is = cn.getInputStream();
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    byte[] b = new byte[8192];
                    int n;
                    while ((n = is.read(b)) > 0) bos.write(b, 0, n);
                    is.close();
                    cn.disconnect();
                    JSONObject obj = new JSONObject(new String(bos.toByteArray(), StandardCharsets.UTF_8));
                    final int remote = obj.optInt("v", 0);
                    int local = 0;
                    try {
                        local = readJsonAsset("data/order.json").optInt("v", 0);
                    } catch (Exception e2) { }
                    if (remote > local) {
                        File f = new File(getFilesDir(), "update.json");
                        FileOutputStream fos = new FileOutputStream(f);
                        fos.write(bos.toByteArray());
                        fos.close();
                        final int r = remote;
                        runOnUiThread(new Runnable() {
                            public void run() {
                                Toast.makeText(MainActivity.this,
                                        "已下载数据 v" + r + "，重启后生效", Toast.LENGTH_LONG).show();
                            }
                        });
                    } else if (!silent) {
                        final int l = local;
                        runOnUiThread(new Runnable() {
                            public void run() {
                                Toast.makeText(MainActivity.this,
                                        "已是最新版本 v" + l, Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                } catch (final Exception e) {
                    if (!silent) {
                        runOnUiThread(new Runnable() {
                            public void run() {
                                Toast.makeText(MainActivity.this,
                                        "检查更新失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
                            }
                        });
                    }
                }
            }
        }).start();
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

        root.addView(subjectScreen, fp());
        root.addView(blockScreen, fp());
        root.addView(childScreen, fp());
        root.addView(listScreen, fp());
        root.addView(detailScreen, fp());
        root.addView(settingsScreen, fp());
        root.addView(searchScreen, fp());

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
        blockScreen.addView(makeBack("‹ 返回学科", new View.OnClickListener() {
            public void onClick(View v) { showScreen(S_SUBJECT); }
        }), wrapM());
        blockList = makeList();
        blockList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                curBlock = curSubject.blocks.get(pos);
                openChildren();
            }
        });
        blockScreen.addView(blockList, lw());

        // 节页
        childScreen.addView(makeBack("‹ 返回上一页", new View.OnClickListener() {
            public void onClick(View v) { showScreen(S_BLOCK); }
        }), wrapM());
        childList = makeList();
        childList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                curChild = curBlock.children.get(pos);
                if ("topic".equals(curChild.type) && curChild.items.isEmpty()) {
                    openDetail(curChild.name, curChild.sec, curSubject.name + " › " + curBlock.name, S_CHILD, null);
                } else {
                    openItems();
                }
            }
        });
        childScreen.addView(childList, lw());

        // 知识点页
        listScreen.addView(makeBack("‹ 返回上一页", new View.OnClickListener() {
            public void onClick(View v) { showScreen(S_CHILD); }
        }), wrapM());
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
        listScreen.addView(itemList, lw());
        itemList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                Item it = shownItems.get(pos);
                openDetail(it.t, it.sec, curSubject.name + " › " + curBlock.name + " › " + curChild.name, S_ITEM, it.img);
            }
        });

        // 详情页
        detailScreen.addView(makeBack("‹ 返回", new View.OnClickListener() {
            public void onClick(View v) { showScreen(detailBack); }
        }), wrapM());
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
        settingsScreen.addView(makeBack("‹ 返回", new View.OnClickListener() {
            public void onClick(View v) { showScreen(S_SUBJECT); }
        }), wrapM());
        settingsScroll = new ScrollView(this);
        settingsScroll.setFillViewport(true);
        settingsInner = new LinearLayout(this);
        settingsInner.setOrientation(LinearLayout.VERTICAL);
        settingsScroll.addView(settingsInner, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        settingsScreen.addView(settingsScroll, lw());

        // 全局搜索页
        searchScreen.addView(makeBack("‹ 返回", new View.OnClickListener() {
            public void onClick(View v) { showScreen(S_SUBJECT); }
        }), wrapM());
        searchTitle = new TextView(this);
        searchTitle.setText("搜索全部知识点");
        searchTitle.setTextSize(m.titleSize);
        searchTitle.setTypeface(Typeface.DEFAULT_BOLD);
        searchTitle.setTextColor(C_PRIMARY);
        searchTitle.setGravity(Gravity.CENTER);
        searchTitle.setPadding(0, 0, 0, dp(4));
        searchScreen.addView(searchTitle, wrap());
        gSearchBox = new EditText(this);
        gSearchBox.setHint("输入关键词（如 导数、电场、光合作用）…");
        gSearchBox.setTextSize(m.subtitleSize - 1f);
        gSearchBox.setTextColor(Color.WHITE);
        gSearchBox.setHintTextColor(C_SUB);
        gSearchBox.setSingleLine(true);
        gSearchBox.setBackgroundDrawable(pill(C_PILL));
        gSearchBox.setPadding(dp(12), dp(8), dp(12), dp(8));
        LinearLayout.LayoutParams gsblp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        gsblp.bottomMargin = dp(6);
        searchScreen.addView(gSearchBox, gsblp);
        gSearchBox.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            public void onTextChanged(CharSequence s, int a, int b, int c) { }
            public void afterTextChanged(Editable s) { refreshGlobalSearch(); }
        });
        searchList = makeList();
        searchScreen.addView(searchList, lw());
        searchList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                Hit h = searchHits.get(pos);
                openDetail(h.item.t, h.item.sec, h.path, S_SEARCH, h.item.img);
            }
        });
    }

    private LinearLayout newScreen() {
        LinearLayout ll = new LinearLayout(this);
        ll.setOrientation(LinearLayout.VERTICAL);
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
        List<String> names = new ArrayList<String>();
        for (Hit h : allHits) {
            String title = h.item.t;
            String blob = title + " " + plain(h.item.sec);
            if (q.length() == 0 || blob.toLowerCase().contains(q.toLowerCase())) {
                searchHits.add(h);
                names.add(title + " ｜ " + h.path);
            }
        }
        searchList.setAdapter(stringAdapter(names, 0xFFF0F0F0));
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
        detailTitle.setText(applyMarkup(title));
        detailPath.setText(applyMarkup(path));
        detailInner.removeAllViews();
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
            "vec","\u20D7","overrightarrow","\u20D7","overline","\u0305","bar","\u0304",
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
            float asc = barUp + gap + a[1];
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
            drawCentered(c, p, t.kids.get(0), cx, barY - gap, s2, color);
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
                fm.ascent = -(int) up;
                fm.descent = (int) dn;
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

        settingsInner.addView(sectionHeader("数据管理"), wrap());
        TextView upd = new TextView(this);
        upd.setText("检查数据更新");
        upd.setTextSize(m.backSize);
        upd.setTextColor(C_PRIMARY);
        upd.setGravity(Gravity.CENTER);
        upd.setPadding(dp(10), dp(10), dp(10), dp(10));
        upd.setBackgroundDrawable(pill(C_PILL));
        upd.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Toast.makeText(MainActivity.this, "正在检查…", Toast.LENGTH_SHORT).show();
                checkUpdate(false);
            }
        });
        settingsInner.addView(upd, wrap());
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
        about.setText("汇总知识 v14.0\n屏幕：" + (m.round ? "圆形（已启用圆形安全区）" : "方形")
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
        View[] screens = {subjectScreen, blockScreen, childScreen, listScreen, detailScreen, settingsScreen, searchScreen};
        View target = screens[s];
        for (int i = 0; i < screens.length; i++) {
            if (i == s) continue;
            screens[i].setVisibility(View.GONE);
        }
        target.setVisibility(View.VISIBLE);
        target.setAlpha(0f);
        target.animate().alpha(1f).setDuration(180).start();

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
            gSearchBox.setText("");
            refreshGlobalSearch();
        }
    }

    @Override
    public void onBackPressed() {
        if (fullscreenView != null) { closeFullscreen(); return; }
        if (screen == S_DETAIL) showScreen(detailBack);
        else if (screen == S_ITEM) showScreen(S_CHILD);
        else if (screen == S_CHILD) showScreen(S_BLOCK);
        else if (screen == S_BLOCK) showScreen(S_SUBJECT);
        else if (screen == S_SETTINGS) showScreen(S_SUBJECT);
        else if (screen == S_SEARCH) showScreen(S_SUBJECT);
        else super.onBackPressed();
    }
}
