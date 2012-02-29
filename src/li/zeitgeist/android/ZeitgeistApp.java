package li.zeitgeist.android;

import android.app.Application;

import li.zeitgeist.android.loader.ThumbLoader;

public class ZeitgeistApp extends Application {
    public static final String TAG = "Zeitgeist";

    private ThumbLoader thumbLoader;

    public ZeitgeistApp() {
        super();
    }

    public void onCreate() {
        super.onCreate();
        thumbLoader = new ThumbLoader();
    }

    public ThumbLoader getThumbLoader() {
        return thumbLoader;
    }

}

