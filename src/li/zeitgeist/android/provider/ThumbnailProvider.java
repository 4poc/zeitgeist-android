/**
 * Zeitgeist for Android
 * Copyright (C) 2012  Matthias Hecker <http://apoc.cc/>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package li.zeitgeist.android.provider;

import li.zeitgeist.android.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

import java.net.URL;

import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;

import android.os.Environment;
import android.os.Handler;

import android.support.v4.util.LruCache;

import android.util.Log;
import android.view.View;
import android.widget.ViewSwitcher;

import li.zeitgeist.android.ZeitgeistApp;
import li.zeitgeist.android.provider.ItemProvider.UpdatedItemsListener;

import li.zeitgeist.api.Item;
import li.zeitgeist.api.Item.Type;

/**
 * Loads Item Thumbnails as Bitmaps.
 *
 * This class downloads and caches thumbnails from Zeitgeist. It uses
 * a fixed size ThreadPool (ExecutorService) for downloading and
 * reading from disk cache (sdcard). It maintains two caches to store 
 * the bitmaps: A LruCache with a fixed size and a disk cache on 
 * the sdcard.
 */
public class ThumbnailProvider implements UpdatedItemsListener {

    private static final String TAG = ZeitgeistApp.TAG + ":ThumbnailProvider";

    private static final int THREADS = 8;

    public interface LoadedThumbnailListener {
        public void onLoadedThumbnail(final int id, final Bitmap bitmap);
    }
    
    private Map<Integer, List<LoadedThumbnailListener>> loadedListeners;

    // in-memory cache
    private LruCache<Integer, Bitmap> memCache = null;

    // disk cache
    private File diskCache;

    // the thread pool
    private ExecutorService pool;
    
    private Bitmap videoOverlayBitmap;
    
    private Context context;

    /**
     * Constructs the thumbnail loader.
     *
     * This starts the thread pool, initializes the memory cache,
     * and creates the directories for the disk-cache (if necessary).
     */
    public ThumbnailProvider(Context context) {
        Log.v(TAG, "constructed");
        // start thread pool
        pool = Executors.newFixedThreadPool(THREADS);

        this.context = context;
        
        // initialize memory cache
        /*
        
         TODO: somehow that doesn't work at all, why?
        
            int memCacheSize = 2 * 1024 * 1024; // 2 MiB
            memCache = new LruCache<Integer, Bitmap>(memCacheSize) {
                protected int sizeOf(Integer key, Bitmap value) {
                    return value.getRowBytes() * value.getHeight();
                }
            };
        
        */
        memCache = new LruCache<Integer, Bitmap>(150); // 150 * ~16 KiB = 2250 KiB

        // create disk cache directory
        File externalStorageDirectory = context.getExternalFilesDir(null);
        diskCache = new File(externalStorageDirectory, "cache");
        if (!diskCache.exists()) {
            diskCache.mkdirs();
        }
        Log.d(TAG, "disk cache: " + diskCache.getAbsolutePath());
        
        // map of assigned thumbnail load listener by item id
        loadedListeners = new HashMap<Integer, List<LoadedThumbnailListener>>();
        
        videoOverlayBitmap = BitmapFactory.decodeResource(context.getResources(), R.drawable.video_overlay);
    }
    
    private void addLoadedListener(int id, LoadedThumbnailListener listener) {
        synchronized (loadedListeners) {
            List<LoadedThumbnailListener> list = loadedListeners.get(id);
            if (list == null) {
                list = new ArrayList<LoadedThumbnailListener>();
                
            }
            
            list.add(listener);
            
            loadedListeners.put(id, list);
        }
    }
    
    private List<LoadedThumbnailListener> getLoadedListeners(int id) {
        synchronized (loadedListeners) {
            return loadedListeners.get(id);
        }
    }
    
    private boolean hasLoadedListener(int id) {
        synchronized (loadedListeners) {
            return loadedListeners.containsKey(id);
        }
    }

    private void removeLoadedListener(int id) {
        synchronized (loadedListeners) {
            loadedListeners.remove(id);
        }
    }
    
    private void callLoadedListener(int id, Bitmap bitmap) {

        List<LoadedThumbnailListener> listeners = getLoadedListeners(id);
        
        if (bitmap != null) {
            for (LoadedThumbnailListener listener : listeners) {
                
                listener.onLoadedThumbnail(id, bitmap);
            }
        }
        
        removeLoadedListener(id);
    }

    /**
     * Loads thumbnail bitmap from disk or web.
     *
     * @param item api item object
     * @param runnable called with the bitmap
     */
    public void loadThumbnail(final Item item, 
      final LoadedThumbnailListener loadedListener) {
        // store the loaded thumbnail listener,
        boolean alreadyLoading = hasLoadedListener(item.getId());
        

        addLoadedListener(item.getId(), loadedListener);
        
        
        if (alreadyLoading) {
            Log.v(TAG, "alreadyLoading, do not submit new thumb download ID: " + String.valueOf(item.getId()));
            // return; // stop if already loading somewhere else
        }

        pool.submit(new Runnable() {
            public void run() {
                Bitmap bitmap = getBitmapByItem(item);
                callLoadedListener(item.getId(), bitmap);
            }
        });
    }
    

    public Bitmap getBitmapByItem(Item item) {
        if (item.getImage() == null) {
            Log.w(TAG, "tried to load item without image");
            return null;
        }

        Bitmap bitmap = null;
        if (isMemCached(item)) {
            bitmap = loadFromMemCache(item);
        }
        else {
            if (isDiskCached(item)) {
                bitmap = loadFromDiskCache(item);
            }
            else {
                bitmap = loadFromWeb(item);
                if (bitmap == null) {
                	Log.e(TAG, "bitmap from web is null!");
                	return null;
                }
                saveToDiskCache(item, bitmap);
            }
            
            // draws the video overlay into the bitmap (if the item is a video)
            if (item.getType() == Type.VIDEO) {
                bitmap = drawVideoOverlay(bitmap);
            }
            
            saveToMemCache(item, bitmap);
        }
        
        return bitmap;
    }

    private Bitmap drawVideoOverlay(Bitmap bitmap) {
        //Canvas canvas = new Canvas(bitmap);
        //Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        
        //canvas.setDensity(context.getResources().getDisplayMetrics().densityDpi);
        //canvas.drawBitmap(videoOverlayBitmap, 0, 0, paint);
        
        Log.d(TAG, "bitmap width=" + String.valueOf(bitmap.getWidth()) + " height=" + String.valueOf(bitmap.getHeight()));
        
        Bitmap overlay = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), bitmap.getConfig());
        
        bitmap.setDensity(context.getResources().getDisplayMetrics().densityDpi);
        videoOverlayBitmap.setDensity(context.getResources().getDisplayMetrics().densityDpi);
        overlay.setDensity(context.getResources().getDisplayMetrics().densityDpi);
        
        Canvas canvas = new Canvas(overlay);
        
        canvas.drawBitmap(bitmap, new Matrix(), null);
        canvas.drawBitmap(videoOverlayBitmap, new Matrix(), null);
        bitmap.recycle();
        return overlay;

        
        //return bitmap;
    }

    private boolean isDiskCached(Item item) {
        return getDiskCacheFile(item).exists();
    }

    private File getDiskCacheFile(Item item) {
        return new File(diskCache, "thumb_" + String.valueOf(item.getId()) + ".jpg");
    }
    
    private Bitmap loadFromDiskCache(Item item) {
        Log.v(TAG, "load from disk cache");
        File file = getDiskCacheFile(item);
        return BitmapFactory.decodeFile(file.getAbsolutePath());
    }

    private void saveToDiskCache(Item item, Bitmap bitmap) {
        Log.v(TAG, "save to disk cache");
        File file = getDiskCacheFile(item);

        // cache bitmap on disk
        try {
            FileOutputStream out = new FileOutputStream(file);
            try {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out);
            }
            finally {
                out.close();
            }
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
    public Bitmap loadFromMemCache(Item item) {
     // Log.v(TAG, "load from memory cache");
        Bitmap bitmap = null;
        synchronized (memCache) {
            bitmap = memCache.get(item.getId());
        }
        return bitmap;
    }
    
    private void saveToMemCache(Item item, Bitmap bitmap) {
    	// Log.v(TAG, "save to memory cache");
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
    private Bitmap loadFromWeb(Item item) {
        String url = ZeitgeistApp.BASE_URL + item.getImage().getThumbnail();
        Log.d(TAG, "load from web " + url);
        Bitmap bitmap = null;
        InputStream in = null;
        try {
        	try {
            	in = (InputStream)(new URL(url).getContent());
                bitmap = BitmapFactory.decodeStream(in);        		
        	}
        	finally {
        		if (in != null) {
                    in.close();
        		}
        	}

        } catch (Exception e) {
            Log.e(TAG, "unable to download thumbnail: " + url);
            e.printStackTrace();
        }
        return bitmap;
    }

	@Override
	public void onUpdatedItems(List<Item> newItemsList) {
	    if (newItemsList != null) {
	        for (Item item : newItemsList) {
	            loadThumbnail(item, null);
	        }	        
	    }
	}


}

