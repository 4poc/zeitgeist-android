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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.*;
import java.util.Map.Entry;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import android.util.Log;

/**
 * ItemWorker for Item Objects.
 * 
 * Is running a thread to download the item list, also provides
 * a position cache that is used for the position -> item id
 * mapping necessary by the gridview adapter.
 * There is a single ItemWorker instance for the application.
 * Also provides methods to update tags.
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
     * Interface to listen for updated tags.
     * 
     * Updates when tags are added or removed, the item
     * includes the updated list of tags associated with the item.
     */
    public interface UpdatedItemTagsListener {
        public void onUpdatedItemTags(final Item item);
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
     * Locked Item Query, last query returned 0 items.
     * 
     * This is useful to determine that it makes no sense
     * to automatically query the same thing again.
     * Needs to be reset if the properties change, and
     * should be ignored for queries newer items.
     */
    private boolean lockedQuery;
    
    /**
     * File on the sdcard storing the serialized itemCache.
     */
    private File itemDiskCache;
    
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
     * Only show items with this tag.
     */
    private String showTagName = null;
    
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
        
        // get File object pointing to the disk cache of the itemCache
        File externalStorageDirectory = context.getExternalFilesDir(null);
        itemDiskCache = new File(externalStorageDirectory, "item_cache.bin");
        Log.d(TAG, "item disk cache: " + itemDiskCache.getAbsolutePath());
        
        // load existing cache:
        loadItemDiskCache(); 
    }

    private void loadItemDiskCache() {
        if (itemDiskCache.exists()) {
            Log.v(TAG, "load item cache from disk");
            try {
                FileInputStream fis = new FileInputStream(itemDiskCache);
                ObjectInputStream is = new ObjectInputStream(fis);
                
                itemCache = (TreeMap<Integer, Item>) is.readObject();
                
                // update/rebuild position cache
                createPositionCache();
                
            } catch (FileNotFoundException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } catch (ClassNotFoundException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }
    
    public void saveItemDiskCache() {
        Log.v(TAG, "saving item cache on disk");
        FileOutputStream fos;
        ObjectOutputStream os;
        try {
            fos = new FileOutputStream(itemDiskCache);
            os = new ObjectOutputStream(fos);
            os.writeObject(itemCache);
        
        } catch (FileNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
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
     * Update Item Tags by ID.
     * 
     * This method creates or removes tags from an Item specified
     * by ID. The listener is called (within the itemWorker thread!)
     * with the updated Item object.
     * The tags param is a comma separated list of tags, tags prefixed
     * by - are deleted.
     * 
     * @param id of the item.
     * @param tags comma separated list of tags to add or remove
     * @param listener to call
     */
    public void updateItemTags(final int id, final String tags, 
            final UpdatedItemTagsListener listener) {
        if (!isAlive() || handler == null) {
            return;
        }
        
        Log.v(TAG, String.format("updateItemTags for %d with tags: %s", id, tags));
        handler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    Item item = api.update(id, tags);
                    
                    // update in cache
                    if (itemCache.containsKey(id)) {
                        itemCache.put(id, item);
                    }
                    
                    listener.onUpdatedItemTags(item);
                } catch (ZeitgeistError e) {
                    Log.e(TAG, "Zeitgeist Error: " + e.getError());
                    listener.onError(e.getError());
                }
            }});
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
                    if (showTagName != null) {
                        if (after > -1) {
                            newItemsList = api.listByTagAfter(showTagName, after);
                        }
                        else if (before > -1) {
                            newItemsList = api.listByTagBefore(showTagName, before);
                        }
                        else {
                            newItemsList = api.listByTag(showTagName);
                        }
                    }
                    else {
                        if (after > -1) { 
                            newItemsList = api.listAfter(after);
                        }
                        else if (before > -1) {
                            newItemsList = api.listBefore(before);
                        }
                        else {
                            newItemsList = api.list();
                        }
                    }
                    
                    // remember that the last query returned 0 results,
                    // so we don't automatically query the same thing again.
                    if (newItemsList.size() == 0) {
                        lockedQuery = true;
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
     * place where images or videos are ignored and filtered
     * for the selected tag.
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
            
            // filtering by tag
            if (showTagName != null &&
                !item.hasTag(showTagName)) {
                continue;
            }

            newPositionCache.add(item.getId());
        }
        Collections.reverse(newPositionCache);
        Log.v(TAG, "new position cache has entries: " + String.valueOf(newPositionCache.size()));
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
    
    /**
     * Set a tag to show by name.
     * 
     * This sets the tag filter, it only shows items that
     * are associated with that tag, this could require a
     * requery of items.
     * The query method will only look for items with that
     * tag.
     * 
     * @param name
     */
    public void setShowTag(String name) {
        Log.v(TAG, "set tag filtering for " + name);
        this.showTagName = name;
        
        // re-create the postition cache only with items of that tag
        createPositionCache();
        
        if (positionCache.size() == 0) {
            // look for items (this will only query items with the tag)
            queryFirstItems();
        }
        
        // allow for queries again
        resetLockedQuery();
        
        // inform the listeners about it (triggers UI change)
        callUpdatedItems(null);
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

    /**
     * Search the position cache for the previous ID before the provided itemId.
     * 
     * @param itemId
     * @return the previous ID.
     */
    public int getPreviousItemId(int itemId) {
        int pos = positionCache.indexOf(itemId) - 1;
        
        if (pos >= 0 && pos < positionCache.size()) {
            return positionCache.get(pos);
        }
        else {
            return itemId;
        }
    }
    
    /**
     * Search the position cache for the next ID after the provided itemId.
     * 
     * @param itemId
     * @return the next ID.
     */
    public int getNextItemId(int itemId) {
        int pos = positionCache.indexOf(itemId) + 1;
        
        if (pos >= 0 && pos < positionCache.size()) {
            return positionCache.get(pos);
        }
        else {
            return itemId;
        }
    }
    
    /**
     * Empty query returned last.
     * @return if the last query yielded 0 results.
     */
    public boolean isLockedQuery() {
        return lockedQuery;
    }
    
    /**
     * Reset (set to false) the query lock.
     */
    public void resetLockedQuery() {
        lockedQuery = false;
    }

}
