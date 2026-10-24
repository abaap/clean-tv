package io.github.bayhaqy.tvcare;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

/** A thin gauge ring: a dim track, a red arc for the share in use, the percent large in the middle. */
final class Ring extends View {
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG), arc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint big = new Paint(Paint.ANTI_ALIAS_FLAG), small = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    private int percent = -1;
    private String caption = "";

    Ring(Context c, Typeface face) {
        super(c);
        float d = c.getResources().getDisplayMetrics().density;
        for (Paint p : new Paint[] {track, arc}) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(7 * d);
            p.setStrokeCap(Paint.Cap.ROUND);
        }
        track.setColor(0x26FFFFFF);
        arc.setColor(Care.RED);
        big.setColor(Care.WHITE);
        big.setTypeface(face);
        big.setTextAlign(Paint.Align.CENTER);
        big.setTextSize(30 * d);
        small.setColor(Care.DIM);
        small.setTypeface(face);
        small.setTextAlign(Paint.Align.CENTER);
        small.setTextSize(11 * d);
        small.setLetterSpacing(0.12f);
    }

    void set(int percent, String caption) {
        if (percent == this.percent && caption.equals(this.caption)) return;
        this.percent = percent;
        this.caption = caption;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        // The ring takes the space above the caption line; the caption sits under it, clear of the arc.
        float cap = small.getTextSize() * 2.2f;
        float s = Math.min(getWidth(), getHeight() - cap), pad = track.getStrokeWidth();
        float l = (getWidth() - s) / 2f + pad, t = pad;
        box.set(l, t, l + s - 2 * pad, t + s - 2 * pad);
        c.drawArc(box, 135, 270, false, track);
        if (percent >= 0) c.drawArc(box, 135, 270 * Math.min(100, percent) / 100f, false, arc);
        c.drawText(percent < 0 ? "–" : percent + "%", box.centerX(), box.centerY() + big.getTextSize() * 0.34f, big);
        c.drawText(caption, getWidth() / 2f, getHeight() - small.getTextSize() * 0.3f, small);
    }
}
