package li.zeitgeist.android;

import li.zeitgeist.android.worker.ItemWorker;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
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
    
    
    
    private ItemWorker itemWorker;
    
    
    
    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate");
        
        itemWorker = new ItemWorker(this);
    }
    
    
    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind");
        return binder;
    }


    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        
        itemWorker.stopThread();

    }
    
    public ItemWorker getItemWorker() {
        return itemWorker;
    }
    
}
