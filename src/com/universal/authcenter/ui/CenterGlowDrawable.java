package com.universal.authcenter.ui;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

public final class CenterGlowDrawable extends Drawable {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    @Override
    public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        if (bounds.isEmpty() || paint.getShader() == null) {
            return;
        }
        canvas.drawRect(bounds, paint);
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        super.onBoundsChange(bounds);
        if (bounds.isEmpty()) {
            paint.setShader(null);
            return;
        }

        float centerX = bounds.exactCenterX();
        float centerY = bounds.exactCenterY();
        float radius = (float) Math.hypot(
                Math.max(centerX - bounds.left, bounds.right - centerX),
                Math.max(centerY - bounds.top, bounds.bottom - centerY)
        );
        if (radius <= 0f) {
            paint.setShader(null);
            return;
        }

        paint.setShader(new RadialGradient(
                centerX,
                centerY,
                radius,
                new int[] {
                        Color.rgb(190, 190, 190),
                        Color.rgb(82, 82, 82),
                        Color.rgb(16, 16, 16)
                },
                new float[] {0f, 0.48f, 1f},
                Shader.TileMode.CLAMP
        ));
    }

    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
        invalidateSelf();
    }

    @Override
    public void setColorFilter(android.graphics.ColorFilter colorFilter) {
        paint.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return android.graphics.PixelFormat.OPAQUE;
    }
}
