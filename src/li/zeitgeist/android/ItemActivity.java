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
package li.zeitgeist.android;

import java.util.List;

import li.zeitgeist.android.worker.*;
import li.zeitgeist.api.Item;
import li.zeitgeist.api.Item.Type;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.text.ClipboardManager;
import android.util.Log;
import android.view.*;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.View.OnClickListener;
import android.widget.*;
import android.webkit.*;

public class ItemActivity extends Activity implements OnMenuItemClickListener, OnClickListener, ItemWorker.UpdatedItemsListener {

    /**
     * Standard android logging tag.
     */
    private static final String TAG = ZeitgeistApp.TAG + ":ItemActivity";
    
    private Item item;
    
    private ItemWorker itemWorker;
    
    private ThumbnailWorker thumbnailWorker;
    
    private GalleryService boundService;
    
    private boolean isBoundService;
    
    private ImageView itemBarDetailIcon;

    private ViewSwitcher itemDetailViewSwitcher;
    
    private ImageView detailThumbnail;
    
    private TextView detailId;
    
    private TextView detailTags;

    private WebView itemWebView;

    private boolean switchToPreviousItem;
    
    private boolean switchToNextItem;
    
    public ItemActivity() {
        super();
        Log.v(TAG, "constructed");
    }
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.v(TAG, "onCreate()");
        
        // binds the local gallery service, and get item/thumbnail workers
        doBindService();

        // Disable the title bar
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        // Set main layout
        setContentView(R.layout.item);

        // always visible item bar icons:
        findViewById(R.id.itemBarLogo).setOnClickListener(this);
        findViewById(R.id.itemBarPreviousIcon).setOnClickListener(this);
        findViewById(R.id.itemBarNextIcon).setOnClickListener(this);
        
        // detail icon is only visible when the webview is shown
        itemBarDetailIcon = (ImageView) findViewById(R.id.itemBarDetailIcon);
        itemBarDetailIcon.setOnClickListener(this);
        
        // views that display detail information about that item:
        detailThumbnail = (ImageView) findViewById(R.id.itemDetailThumbnail);
        detailThumbnail.setOnClickListener(this);
        detailId = (TextView) findViewById(R.id.itemDetailId);
        detailTags = (TextView) findViewById(R.id.itemDetailTags);

        // switches between the details and the webview that displays full-sized
        itemDetailViewSwitcher = 
                (ViewSwitcher) findViewById(R.id.itemDetailViewSwitcher);
        
        // customize webview that displays the full-sized images
        itemWebView = (WebView) findViewById(R.id.itemWebView);
        itemWebView.setBackgroundColor(R.color.item_webview_background);
        WebSettings settings = itemWebView.getSettings();
        settings.setBuiltInZoomControls(true);
        settings.setSupportZoom(true);
        settings.setDefaultZoom(WebSettings.ZoomDensity.FAR);
        settings.setUseWideViewPort(true);
    }
    
    private void doBindService() {
        bindService(new Intent(this, GalleryService.class), serviceConnection,
                Context.BIND_AUTO_CREATE);
        isBoundService = true;
    }

    private void doUnbindItemService() {
        if (isBoundService) {
            // Detach our existing connection.
            unbindService(serviceConnection);
            isBoundService = false;
        }
    }

    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            Log.v(TAG, "onServiceConnected");
            
            // get the service instance, either creates one or uses
            // an existing
            boundService = ((GalleryService.GalleryServiceBinder) service).getService();
            
            // get the worker instances
            itemWorker = boundService.getItemWorker();
            thumbnailWorker = boundService.getThumbnailWorker();
            
            // the user may request older/newer items via the next/prev icons
            itemWorker.addUpdatedItemsListener(ItemActivity.this);
            
            // Get the item object this activity is about:
            Bundle bundle = getIntent().getExtras();
            if (bundle == null) {
                Log.e(TAG, "bundle from intent is null!");
                return;
            }
            item = itemWorker.getItemById(bundle.getInt("id"));
            
            // it uses a preference to store if the details or 
            // the webview should be displayed
            if (isShowItemDetails() || item.getType() == Type.VIDEO) {
                showDetails();
            }
            else {
                showWebView();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {}
    };

    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.item_menu, menu);
        
        menu.findItem(R.id.itemMenuBackItem).setOnMenuItemClickListener(this);
        menu.findItem(R.id.itemMenuCopyUrlItem).setOnMenuItemClickListener(this);
        
        return true;
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.v(TAG, "onDestroy()");
        
        doUnbindItemService();
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

    private void showDetails() {
        // remember display mode in shared preferences
        setShowItemDetails(true);
        
        // switch the viewswitcher to the details (linearlayout)
        itemDetailViewSwitcher.setDisplayedChild(0);
        
        // hide the detail icon in the item bar
        itemBarDetailIcon.setVisibility(View.GONE);
        
        // load thumbnail bitmap and assign to the thumbnail imageview
        if (thumbnailWorker.isMemCached(item)) {
            detailThumbnail.setImageBitmap(thumbnailWorker.getBitmapByItem(item));
        }
        else {
            // load bitmap from disk or web and update the view
            thumbnailWorker.loadThumbnail(item, 
                    new ThumbnailWorker.LoadedThumbnailListener() {
                @Override
                public void onLoadedThumbnail(final int id, final Bitmap bitmap) {

                    detailThumbnail.post(new Runnable() {
                        public void run() {
                            detailThumbnail.setImageBitmap(bitmap);
                        }
                    });
                }
            });
        }
        
        // display the item id
        detailId.setText("Item #" + String.valueOf(item.getId()));
        
        // display the item tags
        detailTags.setText("Tagged: " + Utils.join(item.getTagNames(), ", "));
    }
    
    private void showWebView() {
        // remember the last display mode
        setShowItemDetails(false);
        
        // click on video thumbnails starts browser or youtube application/etc.
        if (item.getType() == Type.VIDEO) {
            Intent openLinkIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(item.getSource()));
            startActivity(openLinkIntent);
            return;
        }
        
        // tell the viewswitcher to switch to the webview:
        itemDetailViewSwitcher.setDisplayedChild(1);
        
        // make the detail icon visible, allows to switch between detail/webview
        itemBarDetailIcon.setVisibility(View.VISIBLE);
        
        // show a progressbar during loading of the image
        final ProgressBar progressBar = new ProgressBar(this);
        progressBar.setMax(100);
        progressBar.bringToFront();
        itemWebView.setWebChromeClient(new WebChromeClient() {
            public void onProgressChanged(WebView view, int progress) {
                progressBar.setProgress(progress);
                if (progress >= 100) {
                    progressBar.setVisibility(View.INVISIBLE);
                }
            }
        });
        itemWebView.setWebViewClient(new WebViewClient() {
          public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
            new AlertDialog.Builder(view.getContext())
            .setTitle("Oops")
            .setMessage("Error: " + description)
            .setPositiveButton(android.R.string.ok, null)
            .show();
          }
        });
        
        // load the absolute full-sized image of this item
        itemWebView.loadUrl(item.getImage().getImageUrl());
    }
    
    private boolean isShowItemDetails() {
        SharedPreferences p = 
                PreferenceManager.getDefaultSharedPreferences(this);
        
        return p.getBoolean("showItemDetails", true);
    }
    
    private void setShowItemDetails(boolean showItemDetails) {
        SharedPreferences prefs = 
                PreferenceManager.getDefaultSharedPreferences(this);
        
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean("showItemDetails", showItemDetails);
        editor.commit();
    }

    private void switchToItemById(int itemId) {
        Intent showItemActivityIntent = new Intent(this, ItemActivity.class);
        Bundle itemIdBundle = new Bundle();
        itemIdBundle.putInt("id", itemId);
        showItemActivityIntent.putExtras(itemIdBundle);
        startActivity(showItemActivityIntent);
    }
    
    @Override
    public void onClick(View v) {
        switch (v.getId()) {
        // if the user clicks on the thumbnail it switches to the full-sized
        // image in a webview
        case R.id.itemDetailThumbnail:
            showWebView();
            break;
            
        // allows to switch back to the details
        case R.id.itemBarDetailIcon:
            showDetails();
            break;
        
        // click on the zeitgeist logo always returns to the gallery
        case R.id.itemBarLogo:
            startActivity(new Intent(this, GalleryActivity.class));
            break;
            
        // previous item, it uses the last position cache (filtering
        //  in the gallery)
        case R.id.itemBarPreviousIcon:
            int prevItemId = itemWorker.getPreviousItemId(item.getId());
            if (prevItemId == item.getId()) {
                // item unavailable query for it:
                itemWorker.queryNewerItems();

                Toast.makeText(this, "Receiving newer items...", Toast.LENGTH_SHORT).show();
                switchToPreviousItem = true;
            }
            else {
                switchToItemById(prevItemId);
            }
            break;

        // next item
        case R.id.itemBarNextIcon:
            int nextItemId = itemWorker.getNextItemId(item.getId());
            if (nextItemId == item.getId()) {
                // item unavailable query for it:
                itemWorker.queryOlderItems();
                
                Toast.makeText(this, "Receiving older items...", Toast.LENGTH_SHORT).show();
                switchToNextItem = true;
            }
            else {
                switchToItemById(nextItemId);
            }
            break;

        }        
    }

    @Override
    public void onUpdatedItems(List<Item> newItemsList) {
        if (switchToPreviousItem) {
            for (int i = newItemsList.size(); i >= 0; i--) {
                if (newItemsList.get(i).getId() > item.getId()) {
                    switchToItemById(newItemsList.get(i).getId());
                    break;
                }
            }
        }
        if (switchToNextItem) {
            for (int i = 0; i < newItemsList.size(); i++) {
                if (newItemsList.get(i).getId() < item.getId()) {
                    switchToItemById(newItemsList.get(i).getId());
                    break;
                }
            }
        }
        switchToNextItem = false;
        switchToPreviousItem = false;
    }

    @Override
    public void onError(String error) {
    }
    
    @Override
    public boolean onMenuItemClick(MenuItem menuItem) {
        switch (menuItem.getItemId()) {
        // back to the gallery
        case R.id.itemMenuBackItem:
            // onBackPressed();
            startActivity(new Intent(this, GalleryActivity.class));
            break;
            
        // copies the full-sized image url
        case R.id.itemMenuCopyUrlItem:
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            clipboard.setText(item.getImage().getImageUrl());
            Toast.makeText(this, "URL Copied", Toast.LENGTH_SHORT).show();
            break;
        }
        
        return true;
    }


}
