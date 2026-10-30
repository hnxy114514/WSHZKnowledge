package com.lizard.hsknowledge;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ListView;
import android.widget.OverScroller;

/**
 * 圆屏过滚列表：滚到顶/底后继续拖，内容跟手拖出最多 45% 屏高，松手由 OverScroller 平滑回弹到 0。
 *
 * 关键点（踩过坑）：
 * - 松手一定回弹到 0，绝不停住，所以不会残留过滚偏移、不会影响点击和位置；
 * - 回弹走 OverScroller 动画（和 ScrollView 一样平滑），不用系统的 EdgeEffect（那是瞬时回弹，会「卡一下突然弹」）。
 * - 方屏 maxOver=0，直接走系统默认行为。
 */
public class OverScrollListView extends ListView {
    private final int maxOver;
    private final OverScroller scroller;
    private float lastY;
    private int overY = 0;
    private boolean overScrolling;

    public OverScrollListView(Context c) {
        super(c);
        maxOver = computeMaxOver(c);
        scroller = new OverScroller(c);
        setOverScrollMode(OVER_SCROLL_NEVER);
    }

    public OverScrollListView(Context c, AttributeSet a) {
        super(c, a);
        maxOver = computeMaxOver(c);
        scroller = new OverScroller(c);
        setOverScrollMode(OVER_SCROLL_NEVER);
    }

    private static int computeMaxOver(Context c) {
        try {
            if (c.getResources().getConfiguration().isScreenRound()) {
                return (int) (c.getResources().getDisplayMetrics().heightPixels * 0.45f);
            }
        } catch (Exception ignored) { }
        return 0;
    }

    private int clamp(int v) {
        return Math.max(-maxOver, Math.min(maxOver, v));
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

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (maxOver <= 0) return super.onTouchEvent(ev);
        int action = ev.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                scroller.forceFinished(true);
                // 兜底：万一有过滚残留，先回正，保证点击/位置干净
                if (overY != 0) {
                    moveChildren(-overY);
                    overY = 0;
                }
                overScrolling = false;
                lastY = ev.getY();
                break;
            case MotionEvent.ACTION_MOVE: {
                float y = ev.getY();
                int dy = (int) (y - lastY);
                lastY = y;
                if (overScrolling || overY != 0) {
                    int target = clamp(overY + dy);
                    int delta = target - overY;
                    if (delta != 0) {
                        moveChildren(delta);
                        overY = target;
                    }
                    overScrolling = true;
                    return true;
                }
                if (atTop() && dy > 0) {
                    int target = clamp(dy);
                    moveChildren(target);
                    overY = target;
                    overScrolling = true;
                    return true;
                }
                if (atBottom() && dy < 0) {
                    int target = clamp(dy);
                    moveChildren(target);
                    overY = target;
                    overScrolling = true;
                    return true;
                }
                break;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (overScrolling || overY != 0) {
                    overScrolling = false;
                    // 平滑回弹到 0（阻尼回弹，同 ScrollView 手感）
                    scroller.startScroll(0, overY, 0, -overY, 250);
                    postInvalidateOnAnimation();
                    return true;
                }
                break;
        }
        return super.onTouchEvent(ev);
    }

    @Override
    public void computeScroll() {
        if (scroller.computeScrollOffset()) {
            int cur = scroller.getCurrY();
            int delta = cur - overY;
            if (delta != 0) {
                moveChildren(delta);
                overY = cur;
            }
            postInvalidateOnAnimation();
        }
    }
}
