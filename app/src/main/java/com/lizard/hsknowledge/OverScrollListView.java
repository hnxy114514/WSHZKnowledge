package com.lizard.hsknowledge;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ListView;

/** 支持内容过滚的列表：滚到顶/底后还能再拖一点，让首尾内容滑到屏幕中间完整显示。 */
public class OverScrollListView extends ListView {
    public OverScrollListView(Context c) {
        super(c);
        setOverScrollMode(OVER_SCROLL_ALWAYS);
    }

    public OverScrollListView(Context c, AttributeSet a) {
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
