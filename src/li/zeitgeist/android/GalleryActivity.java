package li.zeitgeist.android;

import android.util.*;
import android.app.Activity;
import android.os.Bundle;

import android.view.Display;
import android.view.Window;
import android.widget.GridView;

import li.zeitgeist.android.loader.ThumbLoader;

/**
 * Main Activity for Zeitgeist
 *
 * Displays the main layout, a GridView with Thumbnails downloaded from
 * Zeitgeist...
 */
public class GalleryActivity extends Activity {
    private static final String TAG = ZeitgeistApp.TAG + ":GalleryActivity";

    private ThumbLoader thumbLoader;

    public GalleryActivity() {
        super();
        Log.v(TAG, "constructed");
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.v(TAG, "onCreate() called");

        // Disable the title bar
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        // Set main layout
        setContentView(R.layout.main);


        // create the gallery
        // Gallery gallery = new Gallery(this);
        // this stores gridview and sets the sizes dependent
        // on screenwidth
        // gallery.setGridView((GridView)findViewById(R.id.thumbnailGrid));
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.v(TAG, "onDestroy()");

    }

    @Override
    public void onPause() {
        super.onPause();
        Log.v(TAG, "onPause()");
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.v(TAG, "onResume()");
    }

}

