package com.lizard.hsknowledge;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ScrollView;

/** 支持内容过滚的滚动容器：滚到顶/底后还能再拖一点，让首尾内容滑到屏幕中间完整显示。 */
public class OverScrollScrollView extends ScrollView {
    public OverScrollScrollView(Context c) {
        super(c);
        setOverScrollMode(OVER_SCROLL_ALWAYS);
    }

    public OverScrollScrollView(Context c, AttributeSet a) {
        super(c, a);
        setOverScrollMode(OVER_SCROLL_ALWAYS);
    }

    @Override
    protected boolean overScrollBy(int deltaX, int deltaY, int scrollX, int scrollY,
                                   int scrollRangeX, int scrollRangeY, int maxOverScrollX,
                                   int maxOverScrollY, boolean isTouchEvent) {
        int maxY = (int) (getHeight() * 0.35f);
        return super.overScrollBy(deltaX, deltaY, scrollX, scrollY,
                scrollRangeX, scrollRangeY, maxOverScrollX, maxY, isTouchEvent);
    }
}
