package li.zeitgeist.android;

import li.zeitgeist.android.provider.ItemProvider;
import li.zeitgeist.android.provider.ThumbnailProvider;
import li.zeitgeist.api.Item;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

public class ItemActivity extends Activity {

    private static final String TAG = ZeitgeistApp.TAG + ":ItemActivity";
    
    private ThumbnailProvider thumbnailProvider;
    private ItemProvider itemProvider;
    
    public ItemActivity() {
        super();
        Log.v(TAG, "constructed");
    }
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.v(TAG, "onCreate()");
        
        itemProvider = ((ZeitgeistApp)getApplication()).getItemProvider();
        thumbnailProvider = ((ZeitgeistApp)getApplication()).getThumbnailProvider();

        // Disable the title bar
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        // Set main layout
        setContentView(R.layout.item);
        
        Bundle bundle = getIntent().getExtras();
        if (bundle == null) {
            Log.e(TAG, "bundle from intent is null!");
            return;
        }
        final Item item = itemProvider.getItemById(bundle.getInt("id"));
        
        final ImageView itemThumbnail = (ImageView) findViewById(R.id.itemThumbnail);

        if (thumbnailProvider.isMemCached(item)) {
            Bitmap bitmap = thumbnailProvider.getBitmapByItem(item);
            itemThumbnail.setImageBitmap(bitmap);
        }
        else {
            // show progress bar
            // hide in thread
            final ProgressDialog progressDialog = 
                    ProgressDialog.show(this, null, "Load Thumbnail...", true);
            thumbnailProvider.loadThumbnail(item, 
                    new ThumbnailProvider.LoadedThumbnailListener() {
                @Override
                public void onLoadedThumbnail(final Bitmap bitmap) {
                    itemThumbnail.post(new Runnable() {
                        public void run() {
                            progressDialog.hide();
                            itemThumbnail.setImageBitmap(bitmap);
                        }
                    });
                }
            });
        }

        TextView itemIdText = (TextView) findViewById(R.id.itemIdText);
        itemIdText.setText("Item ID: " + String.valueOf(item.getId()));
        
        Button backToGalleryButton = (Button) findViewById(R.id.backToGallery);
        backToGalleryButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                onBackPressed();
            }
            
        });
        
        

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
