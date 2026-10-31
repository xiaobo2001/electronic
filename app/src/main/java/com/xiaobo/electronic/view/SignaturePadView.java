package com.xiaobo.electronic.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.CornerPathEffect;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * 手写签名画板。
 *
 * <p>为什么要有它：文字烟花只能打现成的字，而"她的名字""我想说的话"如果由你亲手写下，
 * 再炸成漫天烟花，是打字替代不了的心意。笔迹最终转成一张透明底的描边位图，
 * 交给烟花库的 {@code FireworkConfig.Shape.CUSTOM} 采样成粒子——
 * 库会按位图上的非透明像素布点，所以写出来的形状就是炸开后的形状。
 *
 * <p>绘制上是普通的 Path 采集，难点在"导出"：手写往往只占画板一角，
 * 直接按画板尺寸导出会让烟花小得看不见，所以这里先算笔迹包围盒，
 * 再等比放大并居中到目标画布，笔迹粗细也同步放大，保证细笔画也能被采样到。
 */
public class SignaturePadView extends View {

    private final Paint inkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint guidePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<Path> strokes = new ArrayList<>();
    private Path current;
    private float lastX, lastY;

    public SignaturePadView(Context c) {
        super(c);
        init();
    }

    public SignaturePadView(Context c, AttributeSet attrs) {
        super(c, attrs);
        init();
    }

    public SignaturePadView(Context c, AttributeSet attrs, int defStyle) {
        super(c, attrs, defStyle);
        init();
    }

    private void init() {
        inkPaint.setColor(0xFFFFFFFF);
        inkPaint.setStyle(Paint.Style.STROKE);
        inkPaint.setStrokeCap(Paint.Cap.ROUND);
        inkPaint.setStrokeJoin(Paint.Join.ROUND);
        inkPaint.setStrokeWidth(9f);
        inkPaint.setPathEffect(new CornerPathEffect(40f));
        inkPaint.setShadowLayer(12f, 0f, 0f, 0xFFFF69B4); // 粉光描边，夜里看得清自己写了什么

        guidePaint.setColor(0x22FFFFFF);
        guidePaint.setStyle(Paint.Style.STROKE);
        guidePaint.setStrokeWidth(1.5f);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth(), h = getHeight();
        // 米字格：给用户一个"写到哪了"的参照，不留白也不会写出界
        guidePaint.setPathEffect(new android.graphics.DashPathEffect(new float[]{10f, 10f}, 0f));
        canvas.drawLine(0, h / 2f, w, h / 2f, guidePaint);
        canvas.drawLine(w / 2f, 0, w / 2f, h, guidePaint);
        guidePaint.setPathEffect(null);
        canvas.drawRect(2f, 2f, w - 2f, h - 2f, guidePaint);

        for (Path p : strokes) canvas.drawPath(p, inkPaint);
        if (current != null) canvas.drawPath(current, inkPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX(), y = event.getY();
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                current = new Path();
                current.moveTo(x, y);
                lastX = x;
                lastY = y;
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (current != null) {
                    // 二次贝塞尔：把手指轨迹抹平，避免折线感与抖动尖刺
                    current.quadTo(lastX, lastY, (x + lastX) / 2f, (y + lastY) / 2f);
                    lastX = x;
                    lastY = y;
                    invalidate();
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (current != null) {
                    current.lineTo(x, y);
                    strokes.add(current);
                    current = null;
                    invalidate();
                }
                return true;
        }
        return super.onTouchEvent(event);
    }

    public boolean isEmpty() {
        return strokes.isEmpty();
    }

    public void undo() {
        if (!strokes.isEmpty()) {
            strokes.remove(strokes.size() - 1);
            invalidate();
        }
    }

    public void clear() {
        strokes.clear();
        current = null;
        invalidate();
    }

    /**
     * 导出为烟花可用的形状位图：透明底、白描边、按笔迹包围盒放大居中。
     *
     * @param size 输出边长（px）。过大只会拖慢采样，360~420 足够看清笔迹。
     * @return 位图；空白画板返回 null
     */
    public Bitmap toShapeBitmap(int size) {
        if (strokes.isEmpty()) return null;

        // 1) 量出笔迹范围
        RectF box = new RectF(Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE);
        RectF tmp = new RectF();
        for (Path p : strokes) {
            p.computeBounds(tmp, false);
            box.union(tmp);
        }
        float pad = inkPaint.getStrokeWidth(); // 线宽也要算进去，否则粗笔画边缘被切
        box.inset(-pad, -pad);
        float bw = Math.max(1f, box.width());
        float bh = Math.max(1f, box.height());

        // 2) 等比缩放到画布的 82%，居中
        float target = size * 0.82f;
        float scale = Math.min(target / bw, target / bh);
        float dx = (size - bw * scale) / 2f - box.left * scale;
        float dy = (size - bh * scale) / 2f - box.top * scale;

        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        Paint p = new Paint(inkPaint);
        // 笔迹放大后线宽同步放大。
        // 【清晰度】库对自定义位图是按网格采样的：step = size/26，粒子数上限固定约 676 个，
        // 与 FireworkConfig.count 无关。笔画必须明显粗于一个 step 才能被稳定采到，
        // 否则大量网格点落在笔画外，字就散成一片看不清。系数 0.055 ≈ 1.5 倍步长。
        float stroke = Math.max(size * 0.055f, inkPaint.getStrokeWidth() * scale);
        p.setStrokeWidth(stroke);
        p.setShadowLayer(size * 0.05f, 0f, 0f, 0xFFFFFFFF);

        Matrix m = new Matrix();
        m.setScale(scale, scale);
        m.postTranslate(dx, dy);
        for (Path path : strokes) {
            Path copy = new Path(path);
            copy.transform(m);
            c.drawPath(copy, p);
        }
        return bmp;
    }

    /** 预览用的小图（菜单里显示"我的签名"缩略）。 */
    public Bitmap toThumbnail(int size) {
        Bitmap src = toShapeBitmap(size);
        if (src == null) return null;
        Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Paint bg = new Paint();
        bg.setColor(Color.parseColor("#33111111"));
        c.drawRect(0, 0, size, size, bg);
        c.drawBitmap(src, 0, 0, null);
        src.recycle();
        return out;
    }
}
