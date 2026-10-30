package com.lizard.hsknowledge;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ScrollView;

/** 支持内容过滚的滚动容器：只在圆屏开启，滚到顶/底后还能再拖一点，让首尾内容滑到屏幕中间完整显示。 */
public class OverScrollScrollView extends ScrollView {
    private final boolean round;

    public OverScrollScrollView(Context c) {
        super(c);
        round = isRound(c);
        setOverScrollMode(OVER_SCROLL_ALWAYS);
    }

    public OverScrollScrollView(Context c, AttributeSet a) {
        super(c, a);
        round = isRound(c);
        setOverScrollMode(OVER_SCROLL_ALWAYS);
    }

    private static boolean isRound(Context c) {
        try { return c.getResources().getConfiguration().isScreenRound(); }
        catch (Exception e) { return false; }
    }

    @Override
    protected boolean overScrollBy(int deltaX, int deltaY, int scrollX, int scrollY,
                                   int scrollRangeX, int scrollRangeY, int maxOverScrollX,
                                   int maxOverScrollY, boolean isTouchEvent) {
        int maxY = round ? (int) (getHeight() * 0.35f) : 0;
        return super.overScrollBy(deltaX, deltaY, scrollX, scrollY,
                scrollRangeX, scrollRangeY, maxOverScrollX, maxY, isTouchEvent);
    }
}
