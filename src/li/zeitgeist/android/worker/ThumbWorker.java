package li.zeitgeist.android.worker;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;

import java.lang.ref.SoftReference;

import java.net.MalformedURLException;
import java.net.URL;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import android.graphics.BitmapFactory;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;

import android.graphics.Bitmap;

import android.util.Log;

import android.widget.ImageView;
import android.widget.ViewSwitcher;

import li.zeitgeist.android.*;

import android.support.v4.util.LruCache;

// holds a hashtable with softreferences to bitmaps
// thumbnails are cached on the sdcard: /sdcard/zeitgeist_cache/thumb_<ID>.<gif/png/jpg>
// bitmaps are queried by item id, if the bitmap doesn't exist
// (not yet downloaded) or the soft reference is null either
// loaded from the sdcard cache or download it.
public class ThumbWorker extends Thread {

    public static final String TAG = ZeitgeistApp.TAG + ":ThumbnailWorker";

    private Handler uiHandler;
    private Handler handler;

    public interface NewThumbListener {
        public void newThumb(Bitmap bitmap);
    }

    // Item ID => Bitmap (soft ref)
    // private Map<Integer, SoftReference<Bitmap>> cache;
    // private Map<Integer, Bitmap> cache;
    // in-memory cache (fastest)
    private static LruCache<Integer, Bitmap> cache = null;
    
    // disk cache on sdcard (slow)
    private File diskCache;

    private int thumbWidth;

    // because the thumbWidth is used to scale the bitmap down you need to think
    // about the padding!
    public ThumbWorker(Handler uiHandler, int thumbWidth) {
        this.uiHandler = uiHandler;
        this.thumbWidth = thumbWidth;// thumbnails are square
        // cache = new HashMap<Integer, SoftReference<Bitmap>>();
        // cache = new HashMap<Integer, Bitmap>();
        // maybe make this customizable or something
        int cacheSize = 80; // 2 * 1024 * 1024; // 4MiB
        if (cache == null) {
            cache = new LruCache<Integer, Bitmap>(cacheSize) {
                // protected int sizeOf(Integer key, Bitmap value) {
                //     return value.getRowBytes() * value.getHeight();
                // }
                // protected void entryRemoved(boolean evicted, Integer key, Bitmap oldValue, Bitmap newValue) {
                //     // oldValue.recycle();
                // }
            };
        }

        // file cache on sdcard
        File externalStorageDirectory = Environment.getExternalStorageDirectory();
        diskCache = new File(externalStorageDirectory, "zeitgeist_cache");
        if (!diskCache.exists()) {
        	diskCache.mkdir();
        	Log.d(TAG, "disk cache directory created " + diskCache.getAbsolutePath());
        }
    }

    private Bitmap loadBitmap(String url) {
        // FOR NOW
        Bitmap bitmap = null;
        try {
            bitmap = BitmapFactory.decodeStream((InputStream)new URL(url).getContent());
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return bitmap;
    }

    public void queueThumb(final int id, final String url, final NewThumbListener newThumbListener) {
        // gets executed within the UI thread...
        Bitmap bitmap = null;
        synchronized (cache) {
            bitmap = cache.get(id); // in-memory cache
        }
        if (bitmap != null) {
            newThumbListener.newThumb(bitmap);
            return;
        }

        // what if there is already another task running/queued about this id?

        handler.post(new Runnable() { // use disk-cache or web (in thread)
            public void run() {
                Bitmap bitmap = null;

                // file object for disk cache
                File bitmapFile = new File(diskCache, "thumb_" + String.valueOf(id) + ".jpg");

                if (bitmapFile.exists()) { // disk cache
                    Log.v(TAG, "cache miss, load from sdcard: " + String.valueOf(id));
                    bitmap = BitmapFactory.decodeFile(bitmapFile.getAbsolutePath());
                }
                else { // download from web
                    Log.v(TAG, "cache miss, load from web: " + String.valueOf(id));
                    bitmap = loadBitmap(url);
                    
                    // cache bitmap on disk
                    try {
                        FileOutputStream out = new FileOutputStream(bitmapFile);
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out);
                        out.close();
                    }
                    catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                synchronized (cache) {
                    // put in in-memory cache
                    cache.put(id, bitmap);
                }

                if (bitmap == null) {
                    Log.e(TAG, "unable to retreive image thumbnail: " + url);
                }
                else {
                    final Bitmap newBitmap = bitmap;
                    uiHandler.post(new Runnable() {
                        public void run() {
                            newThumbListener.newThumb(newBitmap);
                        }});
                }
            }
        });


    }


    public synchronized void stopThread() {
        if (this.isAlive()) {
            handler.post(new Runnable() {
                public void run() {
                    Log.i(TAG, "Stopping thread!");
                    Looper.myLooper().quit();
                }
            });
        }
    }

    @Override
    public void run() {
        try {
            Looper.prepare();

            handler = new Handler();

            Looper.loop(); // gogogo!
        }
        catch (Throwable t) {
            Log.e(TAG, "ThumbnailThread halted because of error: ", t);
        }
    }

}

