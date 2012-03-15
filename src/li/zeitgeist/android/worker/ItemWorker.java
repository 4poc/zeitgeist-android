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

import li.zeitgeist.api.*;
import li.zeitgeist.api.Item.Type;
import li.zeitgeist.api.error.ZeitgeistError;

import java.util.*;
import java.util.Map.Entry;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import android.util.Log;

/**
 * ItemProvider for Item Objects.
 * 
 * Is running a thread to download the item list, also provides
 * a position cache that is used for the position -> item id
 * mapping necessary by the gridview adapter.
 * 
 * @author apoc
 * @see GalleryAdapter
 */
public class ItemWorker extends Thread {

    /**
     * Standard android logging tag.
     */
    public static final String TAG = ZeitgeistApp.TAG + ":ItemWorker";

    /**
     * Interface for updated item listeners.
     * 
     * Implementing classes can be registered to be informed when
     * the item position cache changed.
     */
    public interface UpdatedItemsListener {
        public void onUpdatedItems(List<Item> newItemsList);
        public void onError(final String error);
    }

    /**
     * List of updated items listener to inform.
     */
    private List<UpdatedItemsListener> updatedListeners;

    /**
     * Cached position, each time this is changed the adapter
     * needs to be notified about it via the updatedListeners.
     */
    private List<Integer> positionCache;
    
    /**
     * Cached item objects by Id.
     */
    private SortedMap<Integer, Item> itemCache;
    
    /**
     * Hide items of type video from the position cache.
     * 
     * Other items are kept in the itemCache but are excluded
     * from the positionCache.
     */
    private boolean hideVideos = true;
    
    /**
     * Hide items of type image from the position cache.
     * 
     * Other items are kept in the itemCache but are excluded
     * from the positionCache.
     */
    private boolean hideImages = false;
    
    /**
     * The handler for this thread, used to queue the item downloading on.
     */
    private Handler handler;
    
    /**
     * Is set to true during the downloading and processing of items.
     */
    private boolean loading = false;

    /**
     * The Zeitgeist API instance.
     */
    private ZeitgeistApi api;

    /**
     * Constructs and starts worker for downloading and create item instances.
     * @param context of the gallery service.
     */
    public ItemWorker(Context context) {
        api = ZeitgeistApiFactory.createInstance(context);
        
        // initialize caches, in-memory only atm.
        itemCache = new TreeMap<Integer, Item>();
        positionCache = new Vector<Integer>();

        // list of objects that implement the listener interface
        updatedListeners = new Vector<UpdatedItemsListener>();

        // start itself
        if (!isAlive()) {
            start();
        }
    }

    /**
     * Add instance to the listeners for updated items.
     * 
     * @param listener
     */
    public void addUpdatedItemsListener(UpdatedItemsListener listener) {
    	updatedListeners.add(listener);
    }

    /**
     * Query the positionCache for an Id, then return the item instance.
     * 
     * @param position
     * @return item instance
     * @see GalleryAdapter
     */
    public Item getItemByPosition(int position) {
        int id = positionCache.get(position);
        return itemCache.get(id);
    }
    
    /**
     * Return item from cache by Id.
     * 
     * @param id
     * @return item
     */
    public Item getItemById(int id) {
        return itemCache.get(id);
    }
    
    /**
     * The size of the position cache.
     * 
     * @return size
     */
    public int getItemCount() {
        return positionCache.size();
    }

    /**
     * Query for items on the frontpage (newest items).
     */
    public void queryFirstItems() {
        queryItems(-1, -1);
    }

    /**
     * Query for items that are older then whats in the cache.
     */
    public void queryOlderItems() {
        int firstId = -1;
        
        if (itemCache.size() >= 0) {
            firstId = itemCache.firstKey();
        }
        
        queryItems(-1, firstId);
    }

    /**
     * Query for items that are newer then whats in the cache.
     */
    public void queryNewerItems() {
        int lastId = -1;
        
        if (itemCache.size() >= 0) {
            lastId = itemCache.lastKey();
        }
        
        queryItems(lastId, -1);       
    }

    /**
     * Query for items that come after or before whats provided.
     * 
     * After and/or before can be -1 to ignore. They can not both
     * be set.
     * 
     * @param after exclusive, the Id to search after (or -1 to ignore)
     * @param before exclusive, search before the Id (or -1 to ignore)
     */
    private void queryItems(final int after, final int before) {
        if (!isAlive() || handler == null) {
            return;
        }
        
    	loading = true; // true until the items are downloaded and processed
        handler.post(new Runnable() {
            public void run() {
                Log.d(TAG, "list items with after=" + String.valueOf(after) +
                        " before=" + String.valueOf(before));

                try {
                    List<Item> newItemsList;
                    if (after > -1) {
                    	newItemsList = api.listAfter(after);
                    }
                    else if (before > -1) {
                    	newItemsList = api.listBefore(before);
                    }
                    else {
                    	newItemsList = api.list();
                    }

                    // map the list to an hash with ID as key:
                    for (Item item : newItemsList) {
                        itemCache.put(item.getId(), item);
                    }
                    
                    Log.d(TAG, "put " + String.valueOf(itemCache.size()) + 
                            " items in cache.");

                    // update/rebuild position cache
                    createPositionCache();

                    // inform the listeners that the something has changed
                    callUpdatedItems(newItemsList);
                } catch (ZeitgeistError e) {
                    Log.e(TAG, "Zeitgeist Error: " + e.getError());
                    if (updatedListeners != null) {
                        // call the listener callback in ui thread
                        for (UpdatedItemsListener listener : updatedListeners) {
                            listener.onError(e.getError());
                        }
                    }
                } finally {
                    // finish loading stuff
                	loading = false;
                }
            }

        });
    }

    /**
     * Stop the thread if alive.
     */
    public synchronized void stopThread() {
    	loading = false;
        if (isAlive()) {
            handler.post(new Runnable() {
                public void run() {
                    Log.i(TAG, "stopping thread");
                    Looper.myLooper().quit();
                }
            });
        }
    }
    
    /**
     * Return if items are currently being downloaded or processed.
     * 
     * @return loading boolean
     */
    public boolean isLoading() {
    	return loading;
    }

    /**
     * Create sorted position cache with item IDs.
     * 
     * The position cache is used by the gridview adapter for
     * position(list index) -> item ID mapping. Thats also the
     * place where images or videos are ignored and in the future
     * it should also be possible to tell the itemProvider only
     * to show items with specific tags or other things.
     */
    private void createPositionCache() {
        List<Integer> newPositionCache = new Vector<Integer>();
        
        Iterator<Entry<Integer, Item>> iter = itemCache.entrySet().iterator();
        while(iter.hasNext()) {
            Item item = iter.next().getValue();
            
            // filtering by type
            Type type = item.getType();
            if ( (type == Type.AUDIO) ||
                 (type == Type.VIDEO && hideVideos) ||    
                 (type == Type.IMAGE && hideImages) ) {
                continue;
            }

            newPositionCache.add(item.getId());
        }
        Collections.reverse(newPositionCache);
        positionCache = newPositionCache;
    }
    
    /**
     * Informs the listeners about a changed positionCache.
     * 
     * This means either a change in the filtering settings (filterImages,
     * filterVideos, etc.) that triggered a changed positionCache,
     * or that newer or older items had been downloaded.
     * 
     * @param newItemsList (optional) list of items
     */
    private void callUpdatedItems(List<Item> newItemsList) {
        if (updatedListeners != null) {
            // call the listener callback in ui thread
            for (UpdatedItemsListener listener : updatedListeners) {
                listener.onUpdatedItems(newItemsList);
            }
        }
    }

    /**
     * Set the filter for videos.
     * 
     * If set to true, videos are ignored for the position cache
     * (that is rebuilt)
     * 
     * @param filterVideos
     */
    public void setHideVideos(boolean hideVideos) {
        this.hideVideos = hideVideos;
        
        // update position cache (based on the changed filtering)
        createPositionCache();
        
        // inform the listeners about it (triggers UI change)
        callUpdatedItems(null);
    }
    
    /**
     * Set the filter for images.
     * 
     * If set to true, images are ignored for the position cache
     * (that is rebuilt)
     * 
     * @param filterImages
     */
    public void setHideImages(boolean hideImages) {
        this.hideImages = hideImages;
        
        // update position cache (based on the changed filtering)
        createPositionCache();
        
        // inform the listeners about it (triggers UI change)
        callUpdatedItems(null);
    }
    
    /**
     * Returns true if images are hidden.
     * 
     * @return hideImages boolean
     */
    public boolean isHiddenImages() {
        return hideImages;
    }
    
    /**
     * Returns true if videos are hidden.
     * 
     * @return hideVideos boolean
     */
    public boolean isHiddenVideos() {
        return hideVideos;
    }

    @Override
    public void run() {
        // start thread and load first items on page.
        try {
            Looper.prepare();

            handler = new Handler();
            queryFirstItems();

            Looper.loop(); // gogogo!
        }
        catch (Throwable t) {
            Log.e(TAG, "ListThread halted because of error: ", t);
        }
    }

}
