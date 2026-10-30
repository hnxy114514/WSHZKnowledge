package com.lizard.hsknowledge;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ScrollView;

/**
 * 圆屏过滚滚动容器：只重写 overScrollBy 把过滚距离放大到 45% 屏高，其余全交给系统。
 * 滚到顶/底后继续拖，内容跟手拖出、松手由系统阻尼回弹。方屏 maxOverScrollY=0，行为与普通 ScrollView 一致。
 */
public class OverScrollScrollView extends ScrollView {
    private final int maxOver;

    public OverScrollScrollView(Context c) {
        super(c);
        maxOver = computeMaxOver(c);
        setOverScrollMode(OVER_SCROLL_ALWAYS);
    }

    public OverScrollScrollView(Context c, AttributeSet a) {
        super(c, a);
        maxOver = computeMaxOver(c);
        setOverScrollMode(OVER_SCROLL_ALWAYS);
    }

    private static int computeMaxOver(Context c) {
        try {
            if (c.getResources().getConfiguration().isScreenRound()) {
                return (int) (c.getResources().getDisplayMetrics().heightPixels * 0.45f);
            }
        } catch (Exception ignored) { }
        return 0;
    }

    @Override
    protected boolean overScrollBy(int deltaX, int deltaY, int scrollX, int scrollY,
                                   int scrollRangeX, int scrollRangeY,
                                   int maxOverScrollX, int maxOverScrollY,
                                   boolean isTouchEvent) {
        return super.overScrollBy(deltaX, deltaY, scrollX, scrollY,
                scrollRangeX, scrollRangeY,
                maxOverScrollX, maxOver, isTouchEvent);
    }
}
