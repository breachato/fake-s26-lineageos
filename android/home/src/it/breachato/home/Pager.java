package it.breachato.home;

import android.animation.ValueAnimator;
import android.content.Context;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;

import it.breachato.ui.Glass;

/**
 * Horizontal pages that follow the finger and snap; a downward drag reports a pull for search.
 * Child views are full-size pages laid out side by side through translationX.
 */
class Pager extends FrameLayout {
    interface Listener {
        void onPage(int page);
        void onPullDown();
    }

    private final int slop;
    private Listener listener;
    private int page;
    private float offset;          // pixels, current scroll
    private float downX, downY, startOffset;
    private boolean dragging, pulling;
    private VelocityTracker vt;
    private ValueAnimator anim;

    Pager(Context c) {
        super(c);
        slop = ViewConfiguration.get(c).getScaledTouchSlop();
    }

    void setListener(Listener l) { listener = l; }

    int page() { return page; }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        apply();
    }

    private void apply() {
        int w = getWidth();
        for (int i = 0; i < getChildCount(); i++) {
            View v = getChildAt(i);
            float x = i * w - offset;
            v.setTranslationX(x);
            // pages ease in a little as they slide
            float k = Math.min(1f, Math.abs(x) / Math.max(1f, w));
            v.setAlpha(1f - 0.35f * k);
        }
    }

    void snapTo(int p, boolean animate) {
        page = Math.max(0, Math.min(getChildCount() - 1, p));
        float target = page * getWidth();
        if (anim != null) anim.cancel();
        if (!animate) { offset = target; apply(); return; }
        anim = ValueAnimator.ofFloat(offset, target).setDuration(420);
        anim.setInterpolator(Glass.EASE);
        anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator a) {
                offset = (float) a.getAnimatedValue();
                apply();
            }
        });
        anim.start();
        if (listener != null) listener.onPage(page);
    }

    @Override public boolean onInterceptTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getX(); downY = e.getY(); startOffset = offset;
                dragging = false; pulling = false;
                return false;
            case MotionEvent.ACTION_MOVE:
                float dx = e.getX() - downX, dy = e.getY() - downY;
                if (Math.abs(dx) > slop && Math.abs(dx) > Math.abs(dy)) { dragging = true; return true; }
                if (dy > slop * 2 && dy > Math.abs(dx) * 1.5f) { pulling = true; return true; }
                return false;
        }
        return false;
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (vt == null) vt = VelocityTracker.obtain();
        vt.addMovement(e);
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getX(); downY = e.getY(); startOffset = offset;
                if (anim != null) anim.cancel();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (pulling) return true;
                float dx = e.getX() - downX;
                if (!dragging && Math.abs(dx) > slop) dragging = true;
                if (!dragging && e.getY() - downY > slop * 2) { pulling = true; return true; }
                if (dragging) {
                    float max = (getChildCount() - 1) * getWidth();
                    float o = startOffset - dx;
                    if (o < 0) o *= 0.35f;                    // rubber band at the edges
                    if (o > max) o = max + (o - max) * 0.35f;
                    offset = o;
                    apply();
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                vt.computeCurrentVelocity(1000);
                float vx = vt.getXVelocity();
                vt.recycle();
                vt = null;
                if (pulling) {
                    pulling = false;
                    if (listener != null) listener.onPullDown();
                    return true;
                }
                int target = Math.round(offset / Math.max(1, getWidth()));
                if (vx < -900) target = page + 1;
                else if (vx > 900) target = page - 1;
                snapTo(target, true);
                dragging = false;
                return true;
        }
        return super.onTouchEvent(e);
    }
}
