package li.zeitgeist.android.loader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

import java.net.URL;

import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

import java.util.Map;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import android.os.Environment;

import android.support.v4.util.LruCache;

import android.util.Log;

import li.zeitgeist.android.ZeitgeistApp;

import li.zeitgeist.api.Item;

/**
 * Loads Item Thumbnails as Bitmaps.
 *
 * This class downloads and caches thumbnails from Zeitgeist. It uses
 * a fixed size ThreadPool (ExecutorService) for downloading and
 * reading from disk cache (sdcard). It maintains two caches to store 
 * the bitmaps: A LruCache with a fixed size and a disk cache on 
 * the sdcard.
 */
public class ThumbLoader {

    private static final String TAG = ZeitgeistApp.TAG + ":ThumbLoader";

    private static final int THREADS = 6;
    
    public interface LoadedListener {
        public void loaded(Bitmap bitmap);
    }
    
    /**
     * Map of loaded listeners that are executed when the
     * id (key) has its thumbnail loaded.
     *
     * New listeners do invalidate/override old ones.
     */
    private Map<Integer, LoadedListener> runningListeners;

    // in-memory cache
    private LruCache<Integer, Bitmap> memCache = null;

    // disk cache
    private File diskCache;

    // the thread pool
    private ExecutorService pool;

    /**
     * Constructs the thumbnail loader.
     *
     * This starts the threadpool, initializes the memory cache,
     * and creates the directories for the disk-cache (if necessary).
     */
    public ThumbLoader() {
        Log.v(TAG, "Initialize ThumbLoader");
        // start threadpool
        pool = Executors.newFixedThreadPool(THREADS);

        // init memory cache
        int memCacheSize = 2 * 1024 * 1024; // 2 MiB
        memCache = new LruCache<Integer, Bitmap>(memCacheSize) {
            protected int sizeOf(Integer key, Bitmap value) {
                return value.getRowBytes() * value.getHeight();
            }
        };

        // create disk cache directory
        File externalStorageDirectory = Environment.getExternalStorageDirectory();
        diskCache = new File(externalStorageDirectory, "zeitgeist/cache");
        if (!diskCache.exists()) {
            diskCache.mkdirs();
        	Log.d(TAG, "disk cache directory created " + diskCache.getAbsolutePath());
        }
    }

    /**
     * Loads thumbnail bitmap from disk or web.
     *
     * @param item api item object
     * @param runnable called with the bitmap
     */
    public void queueThumbLoading(final Item item, final LoadedListener listener) {
        if (!isItemLoading(item)) {
            pool.submit(new Runnable() {
                public void run() {
                    loadThumb(item);
                }
            });
        }
        runningListeners.put(item.getId(), listener);
    }

    private boolean isItemLoading(Item item) {
        int id = item.getId();
        return runningListeners.containsKey(id);
    }

    private void loadThumb(Item item) {
        if (item.getImage() == null) {
            Log.e(TAG, "tried to load item without image");
            return;
        }

        Bitmap bitmap = null;
        if (isMemCached(item)) {
            bitmap = loadThumbFromMemCache(item);
        }
        else {
            if (isDiskCached(item)) {
                bitmap = loadThumbFromDiskCache(item);
            }
            else {
                bitmap = loadThumbFromWeb(item);
                saveThumbToDiskCache(item, bitmap);
            }
            saveThumbToMemCache(item, bitmap);
        }

        runListener(item, bitmap);
    }

    private boolean isDiskCached(Item item) {
        return getThumbDiskCacheFile(item).exists();
    }

    private File getThumbDiskCacheFile(Item item) {
        return new File(diskCache, "thumb_" + String.valueOf(item.getId()) + ".jpg");
    }
    
    private Bitmap loadThumbFromDiskCache(Item item) {
        File file = getThumbDiskCacheFile(item);
        return BitmapFactory.decodeFile(file.getAbsolutePath());
    }

    private void saveThumbToDiskCache(Item item, Bitmap bitmap) {
        File file = getThumbDiskCacheFile(item);

        // cache bitmap on disk
        try {
            FileOutputStream out = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out);
            out.close();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public boolean isMemCached(Item item) {
        Bitmap bitmap = null;
        synchronized (memCache) {
            bitmap = memCache.get(item.getId());
        }
        return bitmap != null;
    }
    
    /**
     * Return thumbnail bitmap from memory cache.
     *
     * @param int unique item id
     * @return Bitmap or null
     */
    public Bitmap loadThumbFromMemCache(Item item) {
        Bitmap bitmap = null;
        synchronized (memCache) {
            bitmap = memCache.get(item.getId());
        }
        return bitmap;
    }
    
    private void saveThumbToMemCache(Item item, Bitmap bitmap) {
        synchronized (memCache) {
            // put in in-memory cache
            memCache.put(item.getId(), bitmap);
        }
    }

    /**
     * Loads an thumbnail image of an item.
     *
     * @param item
     * @return bitmap
     */
    private Bitmap loadThumbFromWeb(Item item) {
        String url = item.getImage().getThumbnail();
        Bitmap bitmap = null;
        try {
            bitmap = BitmapFactory.decodeStream((InputStream)new URL(url).getContent());
        } catch (Exception e) {
            Log.e(TAG, "unable to download thumbnail: " + url);
            e.printStackTrace();
        }
        return bitmap;
    }

    private void runListener(Item item, Bitmap bitmap) {
        runningListeners.get(item.getId()).loaded(bitmap);
    }

}

