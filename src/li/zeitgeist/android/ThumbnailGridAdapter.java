package li.zeitgeist.android;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.drawable.BitmapDrawable;

import android.app.Activity;

import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.view.ViewGroup;

import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;

public class ThumbnailGridAdapter extends BaseAdapter {
    private GalleryActivity mainActivity;
    private int itemWidth;

    public ThumbnailGridAdapter(GalleryActivity mainActivity) {
        this.mainActivity = mainActivity;
    }

    public int getCount() {
        return 32;
    }

    public Object getItem(int position) {
        return null;
    }

    public long getItemId(int position) {
        return 0;
    }

    public View getView(int position, View convertView, ViewGroup parent) {
/*        ImageView imageView;
        if (convertView == null) {  // if it's not recycled, initialize some attributes

            int thumbnailWidth = mainActivity.getThumbnailWidth();
            int padding = 4;

            imageView = new ImageView(mainActivity);
            imageView.setLayoutParams(new GridView.LayoutParams(thumbnailWidth, thumbnailWidth));
            
            Bitmap bitmap = BitmapFactory.decodeResource(mainActivity.getResources(), R.drawable.test_thumb);

            // scale the squares down to thumbnailWidth (excluding the padding)
            int dstWidth = thumbnailWidth - padding*2;
            Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, dstWidth, dstWidth, true);
            
            imageView.setImageBitmap(resizedBitmap);
            imageView.setScaleType(ImageView.ScaleType.CENTER);
            imageView.setBackgroundColor(R.color.item_border);
            imageView.setPadding(padding, padding, padding, padding);
            
        } else {
            imageView = (ImageView) convertView;
        }
        return imageView;*/
        return null;
    }
}

