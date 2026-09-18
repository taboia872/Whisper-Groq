package com.whispergroq.utils;

import android.content.Context;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import com.google.android.material.color.MaterialColors;
import com.whispergroq.R;

/**
 * Small tooltip bubble anchored next to an info icon (per user request:
 * info icons are ~80% the size of the others and show their explanation
 * in a balloon near the icon, not a Toast).
 */
public final class InfoTooltip {
    private InfoTooltip() {}

    public static void show(Context ctx, View anchor, int textRes) {
        show(ctx, anchor, ctx.getString(textRes));
    }

    /** Tooltip with clickable link(s) — pass pre-built HTML (e.g. from
     *  getString(res, url)). */
    public static void show(Context ctx, View anchor, CharSequence htmlMessage) {
        TextView tv = new TextView(ctx);
        tv.setText(htmlMessage);
        tv.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tv.setTextColor(MaterialColors.getColor(ctx,
                com.google.android.material.R.attr.colorOnSurface, 0xFF1E1B29));
        int padH = dp(ctx, 14);
        int padV = dp(ctx, 10);
        tv.setPadding(padH, padV, padH, padV);
        tv.setMaxWidth(dp(ctx, 280));

        LinearLayout wrap = new LinearLayout(ctx);
        wrap.setBackground(ctx.getDrawable(R.drawable.tooltip_bg));
        wrap.addView(tv);

        PopupWindow popup = new PopupWindow(wrap,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        popup.setOutsideTouchable(true);
        popup.setFocusable(false);
        popup.setElevation(dp(ctx, 6));
        popup.showAsDropDown(anchor, 0, dp(ctx, 4));
    }

    private static int dp(Context c, int v) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics()));
    }
}
