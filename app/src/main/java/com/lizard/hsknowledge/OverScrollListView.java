package com.lizard.hsknowledge;

import android.animation.ValueAnimator;
import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ListView;

/**
 * 圆屏内容过滚列表（方屏不启用）。
 *
 * 行为：
 * - 滚到顶后手指往下拉、滚到底后手指往上推，内容跟着手指移动（过滚）；
 * - 有效区上限 maxOver（45% 屏高）：范围内松手就停在拖到的位置，不回弹，方便查看被遮挡的内容；
 * - 拖过 maxOver 后进入橡皮筋区（带阻尼，最多再拖 overshootLimit），松手回弹到 maxOver；
 * - 反向滑动回到 0 后，恢复正常滚动。
 */
public class OverScrollListView extends ListView {
    private final boolean round;
    private final int maxOver;         // 松手停留的最大过滚量（45% 屏高）
    private final int overshootLimit;  // 超出 maxOver 后还能拖的硬上限（橡皮筋）
    private float lastY;
    private int overScrollY = 0;
    private ValueAnimator anim;

    public OverScrollListView(Context c) {
        super(c);
        round = isRound(c);
        maxOver = (int) (c.getResources().getDisplayMetrics().heightPixels * 0.45f);
        overshootLimit = maxOver / 2;
        setOverScrollMode(OVER_SCROLL_NEVER);
    }

    public OverScrollListView(Context c, AttributeSet a) {
        super(c, a);
        round = isRound(c);
        maxOver = (int) (c.getResources().getDisplayMetrics().heightPixels * 0.45f);
        overshootLimit = maxOver / 2;
        setOverScrollMode(OVER_SCROLL_NEVER);
    }

    private static boolean isRound(Context c) {
        try { return c.getResources().getConfiguration().isScreenRound(); }
        catch (Exception e) { return false; }
    }

    private void cancelAnim() {
        if (anim != null) {
            anim.cancel();
            anim = null;
        }
    }

    private void moveChildren(int delta) {
        for (int i = 0; i < getChildCount(); i++) {
            View ch = getChildAt(i);
            if (ch != null) ch.offsetTopAndBottom(delta);
        }
    }

    private boolean atTop() {
        return getFirstVisiblePosition() == 0 && getChildCount() > 0
                && getChildAt(0).getTop() >= getPaddingTop();
    }

    private boolean atBottom() {
        return getLastVisiblePosition() >= getAdapter().getCount() - 1
                && getChildCount() > 0
                && getChildAt(getChildCount() - 1).getBottom() <= getHeight() - getPaddingBottom();
    }

    /** 目标过滚量做橡皮筋折算：有效区 [-maxOver,maxOver] 内跟手，超出部分乘 0.4，再受硬上限约束。 */
    private int applyRubber(int raw) {
        float t = raw;
        if (t > maxOver) {
            t = maxOver + (t - maxOver) * 0.4f;
        } else if (t < -maxOver) {
            t = -maxOver + (t + maxOver) * 0.4f;
        }
        int hardMax = maxOver + overshootLimit;
        if (t > hardMax) t = hardMax;
        if (t < -hardMax) t = -hardMax;
        return Math.round(t);
    }

    private void setOverScroll(int v) {
        if (v == overScrollY) return;
        int delta = v - overScrollY;
        moveChildren(delta);
        overScrollY = v;
    }

    /** 回弹动画：从当前 overScrollY 动画到 target。 */
    private void animateTo(int target) {
        cancelAnim();
        final int from = overScrollY;
        if (from == target) return;
        anim = ValueAnimator.ofInt(from, target);
        anim.setDuration(180);
        anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator a) {
                setOverScroll((Integer) a.getAnimatedValue());
            }
        });
        anim.start();
    }

    /** 复位过滚：内容回正到真实滚动位置、overScrollY 归零。切换页面前调用，避免偏移残留污染位置保存。 */
    public void reset() {
        cancelAnim();
        if (overScrollY != 0) {
            moveChildren(-overScrollY);
            overScrollY = 0;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (!round || maxOver <= 0) return super.onTouchEvent(ev);
        int action = ev.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                cancelAnim();
                lastY = ev.getY();
                break;
            case MotionEvent.ACTION_MOVE: {
                float y = ev.getY();
                int dy = (int) (y - lastY);
                lastY = y;
                if (overScrollY != 0) {
                    // 已在过滚状态：继续跟手（含橡皮筋），滑回 0 后交回系统滚动
                    setOverScroll(applyRubber(overScrollY + dy));
                    return true;
                }
                if (atTop() && dy > 0) {
                    setOverScroll(applyRubber(dy));
                    return true;
                }
                if (atBottom() && dy < 0) {
                    setOverScroll(applyRubber(dy));
                    return true;
                }
                break;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (overScrollY != 0) {
                    // 拖进橡皮筋区（超出 maxOver）才回弹；范畴内停住不回弹
                    if (overScrollY > maxOver) animateTo(maxOver);
                    else if (overScrollY < -maxOver) animateTo(-maxOver);
                    lastY = 0f;
                    return true;
                }
                break;
        }
        return super.onTouchEvent(ev);
    }
}
