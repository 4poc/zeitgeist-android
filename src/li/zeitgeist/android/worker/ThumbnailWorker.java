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
package li.zeitgeist.android.worker;

import li.zeitgeist.android.ZeitgeistApiFactory;
import li.zeitgeist.android.ZeitgeistApp;
import li.zeitgeist.android.worker.ItemWorker.UpdatedItemsListener;

import li.zeitgeist.android.R;

import li.zeitgeist.api.Item;
import li.zeitgeist.api.Item.Type;

import java.io.*;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.net.URL;

import android.content.Context;
import android.graphics.*;
import android.support.v4.util.LruCache;
import android.util.Log;

import li.zeitgeist.api.ZeitgeistApi;

/**
 * Concurrently loads thumbnail bitmaps and cache them on disk and memory.
 *
 * This class downloads and caches thumbnails from Zeitgeist. It uses
 * a fixed size ThreadPool (ExecutorService) for downloading and
 * reading from disk cache (sdcard). It maintains two caches to store 
 * the bitmaps: A LruCache with a fixed size and a disk cache on 
 * the sdcard.
 */
public class ThumbnailWorker implements UpdatedItemsListener {

    /**
     * Standard android logging tag.
     */
    private static final String TAG = ZeitgeistApp.TAG + ":ThumbnailWorker";

    /**
     * How many threads the ThreadPool (ExecutorService) should use.
     */
    private static final int THREADS = 8;

    /**
     * Interface for loaded thumbnail listeners.
     */
    public interface LoadedThumbnailListener {
        public void onLoadedThumbnail(final int id, final Bitmap bitmap);
    }

    /**
     * In-Memory cache, with a fixed size.
     */
    private LruCache<Integer, Bitmap> memCache = null;

    /**
     * Disk cache, points to sdcard application cache.
     */
    private File diskCache;

    /**
     * Thread Pool for downloading thumbnail images.
     */
    private ExecutorService pool;
    
    /**
     * The bitmap to overlay video thumbnails with.
     */
    private Bitmap videoOverlayBitmap;
    
    /**
     * Zeitgeist Java API instance.
     */
    private ZeitgeistApi api;
  
    /**
     * Constructs the thumbnail loader.
     *
     * This starts the thread pool, initializes the memory cache,
     * and creates the directories for the disk-cache (if necessary).
     * 
     * @param context of the gallery service.
     */
    public ThumbnailWorker(Context context) {
        Log.v(TAG, "constructed");
        
        api = ZeitgeistApiFactory.createInstance(context);
        
        // start thread pool
        pool = Executors.newFixedThreadPool(THREADS);

        
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
        // 150 * ~16 KiB = 2250 KiB
        memCache = new LruCache<Integer, Bitmap>(150); 

        // create disk cache directory
        File externalStorageDirectory = context.getExternalFilesDir(null);
        diskCache = new File(externalStorageDirectory, "cache");
        if (!diskCache.exists()) {
            diskCache.mkdirs();
        }
        Log.d(TAG, "disk cache: " + diskCache.getAbsolutePath());

        // load video overlay bitmap
        videoOverlayBitmap = BitmapFactory.decodeResource(
                context.getResources(), R.drawable.video_overlay);
    }

    /**
     * Loads thumbnail bitmap from disk or web.
     *
     * @param item api item object
     * @param loadedListener called with the bitmap
     */
    public void loadThumbnail(final Item item, 
      final LoadedThumbnailListener loadedListener) {
        pool.submit(new Runnable() {
            public void run() {
                Bitmap bitmap = getBitmapByItem(item);
                loadedListener.onLoadedThumbnail(item.getId(), bitmap);
            }
        });
    }
    

    /**
     * Retrieve thumbnail bitmap from cache or web return.
     * 
     * @param item
     * @return bitmap instance
     */
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

    /**
     * Draw the video overlay (play icon) on top of the bitmap.
     * 
     * @param bitmap to draw bitmap on.
     * @return bitmap instance with the overlay on it.
     */
    private Bitmap drawVideoOverlay(Bitmap bitmap) {
        return drawBitmapOverlay(bitmap, videoOverlayBitmap);
    }
    
    /**
     * Draw overlay on a bitmap.
     * 
     * @param bitmap
     * @param overlay
     * @return new bitmap
     */
    private Bitmap drawBitmapOverlay(Bitmap bitmap, Bitmap overlay) {
        // create plain empty bitmap
        Bitmap plainBitmap = Bitmap.createBitmap(
                bitmap.getWidth(), bitmap.getHeight(), bitmap.getConfig());
        
        // canvas to draw on it
        Canvas canvas = new Canvas(plainBitmap);
        
        // draw the bitmap then the overlay
        canvas.drawBitmap(bitmap, new Matrix(), null);
        canvas.drawBitmap(overlay, new Matrix(), null);
        
        // recycle the original bitmap
        bitmap.recycle();
        
        return plainBitmap;
    }

    /**
     * True if the thumbnail bitmap exists on the sdcard cache.
     * 
     * @param item
     * @return boolean
     */
    private boolean isDiskCached(Item item) {
        return getDiskCacheFile(item).exists();
    }

    /**
     * Return disk cached thumbnail file.
     * 
     * @param item
     * @return file instance.
     */
    private File getDiskCacheFile(Item item) {
        return new File(diskCache, String.format("thumb_%d.jpg", item.getId()));
    }
    
    /**
     * Loads the bitmap from the sdcard into a Bitmap instance and return.
     * 
     * @param item
     * @return newly loaded Bitmap instance.
     */
    private Bitmap loadFromDiskCache(Item item) {
        Log.v(TAG, "load from disk cache");
        File file = getDiskCacheFile(item);
        return BitmapFactory.decodeFile(file.getAbsolutePath());
    }

    /**
     * Store item thumbnail bitmap instance to disk cache.
     * 
     * @param item
     * @param bitmap
     */
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

    /**
     * Return true if the thumbnail bitmap exists in memory.
     * 
     * @param item
     * @return boolean
     */
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
    
    /**
     * Store bitmap instance in memory lru cache.
     * 
     * @param item
     * @param bitmap
     */
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
        String url = api.getBaseUrl() + item.getImage().getThumbnail();
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
	    // load the thumbnails prematurely if new items are loaded.
	    if (newItemsList != null) {
	        for (Item item : newItemsList) {
	            loadThumbnail(item, null);
	        }	        
	    }
	}

    @Override
    public void onError(String error) {
    }

    /**
     * Stops running downloads and shuts down the thread pool.
     */
    public void stopThreadPool() {
        Log.i(TAG, "stopping thread pool");
        pool.shutdownNow();
    }
}
