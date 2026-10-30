package com.lizard.hsknowledge;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ListView;

/**
 * 圆屏过滚列表：滚到顶/底后继续拖，内容跟手拖出（最多 maxOver），松手用「每帧减半」的阻尼动画平滑回弹到 0。
 *
 * 不用系统 EdgeEffect（它的回弹瞬时、在 Wear OS 上卡顿），也不用 OverScroller（状态复杂、容易乱弹）。
 * 回弹是确定性的：overY 每帧减半直到归零，先快后慢，手感接近 ScrollView 的 springBack。
 * 松手必回弹到 0、DOWN 时兜底归零，不残留、不影响点击。方屏 maxOver=0，走系统默认行为。
 */
public class OverScrollListView extends ListView {
    private int maxOver;
    private float lastY;
    private int overY = 0;
    private boolean overScrolling;
    private boolean bouncing;

    public OverScrollListView(Context c) {
        super(c);
        setOverScrollMode(OVER_SCROLL_NEVER);
    }

    public OverScrollListView(Context c, AttributeSet a) {
        super(c, a);
        setOverScrollMode(OVER_SCROLL_NEVER);
    }

    /** 设置过滚距离（圆屏传 35% 屏高，方屏传 0）。 */
    public void setMaxOver(int v) {
        maxOver = v;
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
                bouncing = false;
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
                    bounceBack();
                    return true;
                }
                break;
        }
        return super.onTouchEvent(ev);
    }

    /** 松手后平滑回弹：overY 每帧减半，直到归零。 */
    private void bounceBack() {
        if (overY == 0) return;
        bouncing = true;
        postOnAnimation(backRunner);
    }

    private final Runnable backRunner = new Runnable() {
        @Override
        public void run() {
            if (!bouncing || overY == 0) {
                bouncing = false;
                return;
            }
            int step = overY / 2;
            if (step == 0) step = overY > 0 ? 1 : -1;
            int target = overY - step;
            if (Math.abs(target) <= 2) target = 0;
            int delta = target - overY;
            moveChildren(delta);
            overY = target;
            if (overY != 0) {
                postOnAnimation(this);
            } else {
                bouncing = false;
            }
        }
    };
}
