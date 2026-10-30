package com.lizard.hsknowledge;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.OverScroller;
import android.widget.ScrollView;

/**
 * 圆屏过滚滚动容器：滚到顶/底后继续拖，内容跟手拖出最多 45% 屏高，松手由 OverScroller 平滑回弹到 0。
 *
 * 不依赖系统的 overScrollBy / EdgeEffect（它们在这类设备上要么拖不动、要么瞬时回弹），
 * 而是自己在 onTouchEvent 里用 scrollTo 控制过滚 + Scroller 平滑回弹。松手一定回弹到 0，无残留。
 * 方屏 maxOver=0，直接走系统默认行为。
 */
public class OverScrollScrollView extends ScrollView {
    private final int maxOver;
    private final OverScroller scroller;
    private float lastY;
    private int overY = 0;
    private boolean overScrolling;

    public OverScrollScrollView(Context c) {
        super(c);
        maxOver = computeMaxOver(c);
        scroller = new OverScroller(c);
        setOverScrollMode(OVER_SCROLL_NEVER);
    }

    public OverScrollScrollView(Context c, AttributeSet a) {
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

    private int maxScrollY() {
        if (getChildCount() == 0) return 0;
        View child = getChildAt(0);
        return Math.max(0, child.getHeight() - (getHeight() - getPaddingTop() - getPaddingBottom()));
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (maxOver <= 0) return super.onTouchEvent(ev);
        int action = ev.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                scroller.forceFinished(true);
                if (overY != 0) {
                    scrollTo(0, getScrollY() + overY);
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
                        scrollTo(0, getScrollY() - delta);
                        overY = target;
                    }
                    overScrolling = true;
                    return true;
                }
                int sy = getScrollY();
                if (sy <= 0 && dy > 0) {
                    int target = clamp(dy);
                    scrollTo(0, sy - target);
                    overY = target;
                    overScrolling = true;
                    return true;
                }
                if (sy >= maxScrollY() && dy < 0) {
                    int target = clamp(dy);
                    scrollTo(0, sy - target);
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
                scrollTo(0, getScrollY() - delta);
                overY = cur;
            }
            postInvalidateOnAnimation();
        }
    }
}
