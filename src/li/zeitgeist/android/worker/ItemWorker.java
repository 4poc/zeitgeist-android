package li.zeitgeist.android.worker;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import android.os.Handler;
import android.os.Looper;

import android.util.Log;

import li.zeitgeist.android.ZeitgeistApp;

import li.zeitgeist.api.*;
import li.zeitgeist.api.error.ZeitgeistError;

// holds a vector of items and downloads new ones upon request
public class ItemWorker extends Thread {

    public static final String TAG = ZeitgeistApp.TAG + ":ItemWorker";

    // informs the listener that there some new items to show
    public interface NewItemsListener {
        public void newItems();
    }

    private NewItemsListener newListener = null;

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

    private ZeitgeistApi api;

    public ItemWorker(ZeitgeistApi api) {
        this.api = api;
        // cache = new HashMap<Integer, Item>();
        cache = new Vector<Item>();
    }

    public void setNewItemsListener(NewItemsListener newListener) {
        this.newListener = newListener;
    }

    public Item getItemByPosition(int position) {
        return cache.get(position);
    }

    // How many items are there currently availible
    public int getItemCount() {
        return cache.size();
    }

    public void queryOlderItems() {
        queryItems(-1, firstId);
    }

    public void queryNewerItems() {
        queryItems(lastId, -1);
    }

    private void queryItems(final int after, final int before) {
        handler.post(new Runnable() {
            public void run() {
                Log.d(TAG, "Query Items: after="+String.valueOf(after)+" before="+String.valueOf(before));

                try {
                    List<Item> result;

                    if (after >= 0) {
                            result = api.listAfter(after);
                    }
                    else if (before >= 0) {
                        result = api.listBefore(before);
                    }
                    else {
                        result = api.list();
                    }

                    Log.d(TAG, "Query returned " + String.valueOf(result.size()) + " items.");

                    int high = result.get(0).getId(),
                        low = result.get(result.size() - 1).getId();
                    Log.d(TAG, "high="+String.valueOf(high)+" low="+String.valueOf(low));
                    if (firstId == -1 || low < firstId) // the oldest id (the smallest)
                        firstId = low;
                    if (lastId == -1 || high > lastId) // the newest id (the biggest)
                        lastId = high;

                    for (Item item : result) {
                        cache.add(item);
                        // cache.put(item.getId(), item);
                    }

                    if (newListener != null) {
                        newListener.newItems();
                    }

                } catch (ZeitgeistError e) {
                    e.printStackTrace();
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


