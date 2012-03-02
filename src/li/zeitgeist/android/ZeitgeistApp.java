package li.zeitgeist.android;

import android.app.Application;

import li.zeitgeist.android.provider.ItemProvider;
import li.zeitgeist.android.provider.ThumbnailProvider;
import li.zeitgeist.api.ZeitgeistApi;

public class ZeitgeistApp extends Application {

    public static final String TAG = "Zeitgeist";
    public static final String BASE_URL = "http://zeitgeist.li"; // for now

    private ItemProvider itemProvider;
    private ThumbnailProvider thumbnailProvider;

    public ZeitgeistApp() {
        super();
    }

    public void onCreate() {
        super.onCreate();
        
        itemProvider = new ItemProvider(new ZeitgeistApi(BASE_URL));
        thumbnailProvider = new ThumbnailProvider();
        
        // load the thumbnails of new items
        itemProvider.addNewItemsListener(thumbnailProvider);
        
        itemProvider.start();
    }

    public ItemProvider getItemProvider() {
        return itemProvider;
    }

    public ThumbnailProvider getThumbnailProvider() {
        return thumbnailProvider;
    }
    
}
