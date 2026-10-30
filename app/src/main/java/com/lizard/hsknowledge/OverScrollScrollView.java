package com.lizard.hsknowledge;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ScrollView;

/**
 * 圆屏过滚滚动容器：用系统 overScrollBy 放大过滚距离，回弹走系统 springBack（平滑、不乱弹）。
 * 过滚距离由外部（MainActivity）按 m.round 显式传入，避免 isScreenRound() 判断不一致导致拖不动。
 */
public class OverScrollScrollView extends ScrollView {
    private int maxOver;
    private OnScrollListener scrollL;

    public interface OnScrollListener {
        void onScroll(int y, int h, int contentH);
    }

    /** 滚近底部时的续载回调（大列表分页用），由外部在 onScroll 里判断。 */
    public void setScrollListener(OnScrollListener l) { scrollL = l; }

    public OverScrollScrollView(Context c) {
        super(c);
        setOverScrollMode(OVER_SCROLL_ALWAYS);
    }

    public OverScrollScrollView(Context c, AttributeSet a) {
        super(c, a);
        setOverScrollMode(OVER_SCROLL_ALWAYS);
    }

    /** 设置过滚距离（圆屏传 25% 屏高，方屏传 0）。 */
    public void setMaxOver(int v) {
        maxOver = v;
    }

    @Override
    protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        super.onScrollChanged(l, t, oldl, oldt);
        View child = getChildAt(0);
        if (scrollL != null) scrollL.onScroll(t, getHeight(), child == null ? 0 : child.getHeight());
    }

    @Override
    protected boolean overScrollBy(int deltaX, int deltaY, int scrollX, int scrollY,
                                   int scrollRangeX, int scrollRangeY,
                                   int maxOverScrollX, int maxOverScrollY,
                                   boolean isTouchEvent) {
        return super.overScrollBy(deltaX, deltaY, scrollX, scrollY,
                scrollRangeX, scrollRangeY, maxOverScrollX, maxOver, isTouchEvent);
    }
}
