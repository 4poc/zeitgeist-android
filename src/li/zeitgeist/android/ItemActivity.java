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

import li.zeitgeist.android.worker.ItemWorker;
// import li.zeitgeist.android.provider.ThumbnailProvider;
import li.zeitgeist.api.Item;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.text.ClipboardManager;
import android.util.Log;
import android.view.*;
import android.view.MenuItem.OnMenuItemClickListener;
import android.widget.*;
import android.webkit.*;

public class ItemActivity extends Activity implements OnMenuItemClickListener {

    private static final String TAG = ZeitgeistApp.TAG + ":ItemActivity";
    
    private ItemWorker itemWorker;
    
    private GalleryService boundService;
    
    private boolean isBoundService;

    
    private Item item;
    
    private WebView webView;
    
    public ItemActivity() {
        super();
        Log.v(TAG, "constructed");
    }

    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.v(TAG, "onCreate()");
        
        doBindService();


        // Disable the title bar
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        // Set main layout
        setContentView(R.layout.item);
        

        
        // Find and setup the WebView to show the item
        webView = (WebView) findViewById(R.id.webView);
        webView.setBackgroundColor(R.color.item_webview_background);

        WebSettings settings = webView.getSettings();
        settings.setBuiltInZoomControls(true);
        settings.setSupportZoom(true);
        settings.setDefaultZoom(WebSettings.ZoomDensity.FAR);
        settings.setUseWideViewPort(true);
        
        final ProgressBar progressBar = new ProgressBar(this);
        progressBar.setMax(100);
        progressBar.bringToFront();
        
        webView.setWebChromeClient(new WebChromeClient() {
            public void onProgressChanged(WebView view, int progress) {
                progressBar.setProgress(progress);
                if (progress >= 100) {
                    progressBar.setVisibility(View.INVISIBLE);
                }
            }
        });

        webView.setWebViewClient(new WebViewClient() {
          public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
            new AlertDialog.Builder(view.getContext())
            .setTitle("Oops")
            .setMessage("Error: " + description)
            .setPositiveButton(android.R.string.ok, null)
            .show();
          }
        });

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
            
            // get the item worker instance
            itemWorker = boundService.getItemWorker();
            
            
            // Get the item object this activity is about:
            Bundle bundle = getIntent().getExtras();
            if (bundle == null) {
                Log.e(TAG, "bundle from intent is null!");
                return;
            }
            item = itemWorker.getItemById(bundle.getInt("id"));
            
            webView.loadUrl(item.getImage().getImageUrl());
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

    @Override
    public boolean onMenuItemClick(MenuItem menuItem) {
        switch (menuItem.getItemId()) {
        case R.id.itemMenuBackItem:
            onBackPressed();
            break;
            
        case R.id.itemMenuCopyUrlItem:
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            clipboard.setText(item.getImage().getImageUrl());
            Toast.makeText(this, "URL Copied", Toast.LENGTH_SHORT).show();
            break;
        }
        
        
        return true;
    }
}
