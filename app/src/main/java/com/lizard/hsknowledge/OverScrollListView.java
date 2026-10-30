package com.lizard.hsknowledge;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ListView;

/**
 * 圆屏过滚列表：用系统 overScrollBy 放大过滚距离，回弹走系统逻辑。
 * 过滚距离由外部（MainActivity）按 m.round 显式传入，避免 isScreenRound() 判断不一致导致拖不动。
 */
public class OverScrollListView extends ListView {
    private int maxOver;

    public OverScrollListView(Context c) {
        super(c);
        setOverScrollMode(OVER_SCROLL_ALWAYS);
    }

    public OverScrollListView(Context c, AttributeSet a) {
        super(c, a);
        setOverScrollMode(OVER_SCROLL_ALWAYS);
    }

    /** 设置过滚距离（圆屏传 45% 屏高，方屏传 0）。 */
    public void setMaxOver(int v) {
        maxOver = v;
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
