package com.lizard.hsknowledge;

import android.animation.ValueAnimator;
import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ScrollView;

/**
 * 圆屏内容过滚滚动容器（方屏不启用）。
 *
 * 行为同 OverScrollListView：有效区上限 45% 屏高，范围内松手停住；
 * 超出后带阻尼再拖一点，松手回弹到 45%。
 */
public class OverScrollScrollView extends ScrollView {
    private final boolean round;
    private final int maxOver;
    private final int overshootLimit;
    private float lastY;
    private int overScrollY = 0;
    private ValueAnimator anim;

    public OverScrollScrollView(Context c) {
        super(c);
        round = isRound(c);
        maxOver = (int) (c.getResources().getDisplayMetrics().heightPixels * 0.45f);
        overshootLimit = maxOver / 2;
        setOverScrollMode(OVER_SCROLL_NEVER);
    }

    public OverScrollScrollView(Context c, AttributeSet a) {
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

    private int maxScrollY() {
        if (getChildCount() == 0) return 0;
        View child = getChildAt(0);
        return Math.max(0, child.getHeight() - (getHeight() - getPaddingTop() - getPaddingBottom()));
    }

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
        scrollTo(0, getScrollY() - delta);
        overScrollY = v;
    }

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
            scrollTo(0, getScrollY() + overScrollY);
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
                    setOverScroll(applyRubber(overScrollY + dy));
                    return true;
                }
                boolean top = getScrollY() <= 0;
                boolean bottom = getScrollY() >= maxScrollY();
                if (top && dy > 0) {
                    setOverScroll(applyRubber(dy));
                    return true;
                }
                if (bottom && dy < 0) {
                    setOverScroll(applyRubber(dy));
                    return true;
                }
                break;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (overScrollY != 0) {
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
