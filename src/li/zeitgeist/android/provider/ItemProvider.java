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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    // informs the listener that there some new items to show
    public interface NewItemsListener {
        public void onNewItems(List<Item> newItemsList);
    }

    //private NewItemsListener newListener = null;
    private List<NewItemsListener> newListener;

    // Memory Cache of Items TODO: dump this into a database or something
    // private Map<Integer, Item> cache;
    private List<Item> cache;

    // first and last item ids that are present in the cache
    // the cache gets only populated in ranges, so you can
    // assume that every item inbetween are present
    private int firstId = -1;
    private int lastId = -1;

    // thread handler allows to execute code within this thread
    Handler handler;
    
    private Handler uiThreadHandler;
    
    private boolean loading = false;

    private ZeitgeistApi api;

    public ItemProvider(ZeitgeistApi api) {
        this.api = api;
        // cache = new HashMap<Integer, Item>();
        cache = new Vector<Item>();
        
        uiThreadHandler = new Handler();
        
        newListener = new Vector<NewItemsListener>();
    }

    public void addNewItemsListener(NewItemsListener listener) {
    	newListener.add(listener);
    }

    public Item getItemByPosition(int position) {
        return cache.get(position);
    }
    
    public Item getItemById(int id) {
        for (Item item : cache) {
            if (item.getId() == id) {
                return item;
            }
        }
        return null;
    }

    private int itemCount;
    
    // How many items are there currently availible
    public int getItemCount() {
        return itemCount;
    }

    public void queryOlderItems() {
        queryItems(-1, firstId);
    }

    public void queryNewerItems() {
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

                    int high = newItemsList.get(0).getId(),
                        low = newItemsList.get(newItemsList.size() - 1).getId();
                    Log.d(TAG, "high="+String.valueOf(high)+" low="+String.valueOf(low));
                    if (firstId == -1 || low < firstId) // the oldest id (the smallest)
                        firstId = low;
                    if (lastId == -1 || high > lastId) // the newest id (the biggest)
                        lastId = high;

                    for (Item item : newItemsList) {
                        // ignore items without images
                        if (item.getType() != Type.IMAGE ||
                          item.getImage() == null) {
                            Log.d(TAG, "remove item because type is: " + String.valueOf(item.getType()));
                            newItemsList.remove(item);
                            continue;
                        }
                        
                        cache.add(item);
                        // cache.put(item.getId(), item);
                    }
                    
                    // make the new size public:
                    itemCount = cache.size();

                	loading = false;
                    if (newListener != null) {
                    	// call the listener callback in ui thread
                    	for (NewItemsListener listener : newListener) {
                    		listener.onNewItems(newItemsList);
                    	}
                    }

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

}


