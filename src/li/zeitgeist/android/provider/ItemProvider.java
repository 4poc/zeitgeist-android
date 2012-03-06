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

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.Vector;
import java.util.concurrent.CopyOnWriteArrayList;

import android.os.Handler;
import android.os.Looper;

import android.util.Log;

import li.zeitgeist.android.ZeitgeistApp;

import li.zeitgeist.api.*;
import li.zeitgeist.api.Item.Type;
import li.zeitgeist.api.error.ZeitgeistError;

// holds a vector of items and downloads new ones upon request
public class ItemProvider extends Thread {

    public static final String TAG = ZeitgeistApp.TAG + ":ItemProvider";

    /*
     * Informs about a changed position cache. New or deleted
     * items etc.
     */
    public interface UpdatedItemsListener {
        public void onUpdatedItems(List<Item> newItemsList);
    }

    //private NewItemsListener newListener = null;
    private List<UpdatedItemsListener> newListener;

    /**
     * Cached position, each time this is changed the adapter
     * needs to be notified about it.
     */
    private List<Integer> positionCache;
    
    /**
     * Cached item objects by Id.
     */
    private SortedMap<Integer, Item> itemCache;
    
    
    private boolean filterVideos = true;
    
    private boolean filterImages = false;

    
    // thread handler allows to execute code within this thread
    Handler handler;
    
    private boolean loading = false;

    private ZeitgeistApi api;

    public ItemProvider(ZeitgeistApi api) {
        this.api = api;
       
        // initialize caches, in-memory only atm.
        itemCache = new TreeMap<Integer, Item>();
        positionCache = new Vector<Integer>();
        
        newListener = new Vector<UpdatedItemsListener>();
    }

    public void addNewItemsListener(UpdatedItemsListener listener) {
    	newListener.add(listener);
    }

    public Item getItemByPosition(int position) {
        int id = positionCache.get(position);
        return itemCache.get(id);
    }
    
    public Item getItemById(int id) {
        return itemCache.get(id);
    }
    
    // How many items are there currently availible
    public int getItemCount() {
        return positionCache.size();
    }

    public void queryOlderItems() {
        int firstId = -1;
        
        if (itemCache.size() >= 0) {
            firstId = itemCache.firstKey();
        }
        
        queryItems(-1, firstId);
    }

    public void queryNewerItems() {
        int lastId = -1;
        
        if (itemCache.size() >= 0) {
            lastId = itemCache.lastKey();
        }
        
        queryItems(lastId, -1);       
    }

    private void queryItems(final int after, final int before) {
    	loading = true;
        handler.post(new Runnable() {
            public void run() {
                Log.d(TAG, "Query Items: after="+String.valueOf(after)+" before="+String.valueOf(before));

                try {
                    List<Item> newItemsList;

                    if (after >= 0) {
                    	newItemsList = api.listAfter(after);
                    }
                    else if (before >= 0) {
                    	newItemsList = api.listBefore(before);
                    }
                    else {
                    	newItemsList = api.list();
                    }
                    
                    newItemsList = new CopyOnWriteArrayList<Item>(newItemsList);

                    Log.d(TAG, "Query returned " + String.valueOf(newItemsList.size()) + " items.");

                    for (Item item : newItemsList) {
                        itemCache.put(item.getId(), item);
                    }

                    // update/rebuild position cache
                    createPositionCache();

                    // inform the listeners that the something has changed
                    callUpdatedItems(newItemsList);

                    // finish loading stuff
                	loading = false;

                } catch (ZeitgeistError e) {
                    e.printStackTrace();
                    
                }
            }

        });
    }

    public synchronized void stopThread() {
    	loading = false;
        if (this.isAlive()) {
            handler.post(new Runnable() {
                public void run() {
                    Log.i(TAG, "Stopping thread!");
                    Looper.myLooper().quit();
                }
            });
        }
    }
    
    public boolean isLoading() {
    	return loading;
    }

    @Override
    public void run() {
        try {
            Looper.prepare();

            handler = new Handler();

            // start by query the latest page (frontpage)
            // with the newest items
            queryItems(-1, -1);

            Looper.loop(); // gogogo!
        }
        catch (Throwable t) {
            Log.e(TAG, "ListThread halted because of error: ", t);
        }
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
        // TODO Auto-generated method stub
        List<Integer> newPositionCache = new Vector<Integer>();
        
        Iterator<Entry<Integer, Item>> iter = itemCache.entrySet().iterator();
        while(iter.hasNext()) {
            Item item = iter.next().getValue();
            
            // filtering by type
            Type type = item.getType();
            if ( (type == Type.AUDIO) ||
                 (type == Type.VIDEO && filterVideos) ||    
                 (type == Type.IMAGE && filterImages) ) {
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
        if (newListener != null) {
            // call the listener callback in ui thread
            for (UpdatedItemsListener listener : newListener) {
                listener.onUpdatedItems(newItemsList);
            }
        }
    }

    public void setFilterVideos(boolean filterVideos) {
        this.filterVideos = filterVideos;
        
        // update position cache (based on the changed filtering)
        createPositionCache();
        
        // inform the listeners about it (triggers UI change)
        callUpdatedItems(null);
    }
    
    public void setFilterImages(boolean filterImages) {
        this.filterImages = filterImages;
        
        // update position cache (based on the changed filtering)
        createPositionCache();
        
        // inform the listeners about it (triggers UI change)
        callUpdatedItems(null);
    }
    
    public boolean getFilterVideos() {
        return filterVideos;
    }

    public boolean getFilterImages() {
        return filterImages;
    }
    
}


