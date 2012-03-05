package li.zeitgeist.android.preference;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.View;

public class SizePreviewView extends View {
    private Paint linePaint;
    private int thumbnailSize;
    
    public SizePreviewView(Context context, int thumbnailSize) {
        super(context);
        
        this.thumbnailSize = thumbnailSize;
        
        linePaint = new Paint();
        linePaint.setColor(Color.WHITE);
    }
    
    public void onDraw(Canvas canvas) {
        Rect rect = new Rect(0, 0, thumbnailSize, thumbnailSize);
        
        canvas.drawRect(rect, linePaint);
        // canvas.drawRoundRect(rect, rx, ry, paint)
    }
}
