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
package li.zeitgeist.android;

import li.zeitgeist.android.worker.*;

import android.app.Service;
import android.content.Intent;
import android.os.*;
import android.util.Log;

/**
 * Gallery Local Service.
 * 
 * Starts and destroys the workers for items and the thumbnail
 * downloader. They both start threads to do their work, the
 * local service holds a single application wide instance of
 * them.
 */
public class GalleryService extends Service {

    /**
     * Standard android logging tag.
     */
    public static final String TAG = ZeitgeistApp.TAG + ":GalleryService";
    
    /**
     * Binder for this local-only service, works like a singleton.
     */
    public class GalleryServiceBinder extends Binder {
        public GalleryService getService() {
            return GalleryService.this;
        }
    }
    
    /**
     * Binder class instance.
     */
    private final GalleryServiceBinder binder = new GalleryServiceBinder();
    
    /**
     * Item Worker instance, downloads items as json and deserializes.
     */
    private ItemWorker itemWorker;
    
    /**
     * Thumbnail Worker instance, downloads thumbnail images in a thread pool.
     */
    private ThumbnailWorker thumbnailWorker;
    
    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate");

        // Instantiate them once:
        itemWorker = new ItemWorker(this);
        thumbnailWorker = new ThumbnailWorker(this);
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");

        // stop worker threads
        itemWorker.stopThread();
        thumbnailWorker.stopThreadPool();
    }
    
    /**
     * Return the worker instance.
     * @return instance
     */
    public ItemWorker getItemWorker() {
        return itemWorker;
    }
    
    /**
     * Return the worker instance.
     * @return instance
     */
    public ThumbnailWorker getThumbnailWorker() {
        return thumbnailWorker;
    }
}
