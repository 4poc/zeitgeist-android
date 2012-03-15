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

import android.app.AlertDialog;

import android.content.DialogInterface;

import android.util.*;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;

import android.view.*;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.View.OnClickListener;
import android.widget.*;
import android.widget.AbsListView.OnScrollListener;
import android.widget.AdapterView.OnItemClickListener;

import li.zeitgeist.android.worker.ItemWorker;
import li.zeitgeist.android.worker.ThumbnailProvider;
import li.zeitgeist.api.Item;
import li.zeitgeist.api.Item.Type;

public class GalleryActivity extends Activity 
  implements OnItemClickListener, OnMenuItemClickListener {

    private static final String TAG = ZeitgeistApp.TAG + ":GalleryActivity";

    private ThumbnailProvider thumbnailProvider;
    
    private ProgressDialog progressDialog = null;
    
    private ViewSwitcher.LayoutParams imageViewLayoutParams;
    
    private GridView gridView = null;
    
    private static final int THUMB_SPACING = 5;
    private static final int THUMB_PADDING = 2;
    
    private int screenWidth;
    private int thumbMinWidth = 120;
    private int thumbMaxWidth = 200;
    private int thumbWidth;
    private int numColumns;
    private int scrollThreshold = 5;
    
    
    private ItemWorker itemWorker;
    
    private GalleryService boundService;
    
    private boolean isBoundService;
    
    
    
    GalleryAdapter adapter;
    
    GalleryBarOnClickListener galleryBarOnClickListener;

    public GalleryActivity() {
        super();
        Log.v(TAG, "constructed");
    }
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.v(TAG, "onCreate()");
        
        doBindService();
        

        // get the global provider instances
        

        //this.startService(new Intent(this, ItemService.class));
        
        //itemProvider = ((ZeitgeistApp)getApplication()).getItemProvider();
        thumbnailProvider = ((ZeitgeistApp)getApplication()).getThumbnailProvider();

        // Disable the title bar
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        // Set main layout
        setContentView(R.layout.gallery);
        
        // set the screen width (800 or 480 on desire z)
        Display display = getWindowManager().getDefaultDisplay();
        screenWidth = display.getWidth() - 4;
        
        // set the gridview dimensions
        gridView = (GridView) findViewById(R.id.thumbnailGrid);
        gridView.setHorizontalSpacing(THUMB_SPACING);
        gridView.setVerticalSpacing(THUMB_SPACING);
        gridView.setOnItemClickListener(this);
        
        // calculates and sets the thumbnail item size (thumbWidth)
        updateThumbnailSize();
                
        // gallery bar (icon header bar) assigns onclick listener
        galleryBarOnClickListener = new GalleryBarOnClickListener();
        ((ImageView) findViewById(R.id.galleryBarShowImagesIcon))
            .setOnClickListener(galleryBarOnClickListener);
        ((ImageView) findViewById(R.id.galleryBarShowVideosIcon))
            .setOnClickListener(galleryBarOnClickListener);
        ((ImageView) findViewById(R.id.galleryBarPreferencesIcon))
            .setOnClickListener(galleryBarOnClickListener);
        
        // show progress dialog per default
        progressDialog = ProgressDialog.show(this, null, "Loading...", true);
    }
    
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.v(TAG, "onDestroy()");

        progressDialog.dismiss();
        
        doUnbindService();
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
        
        updateThumbnailSize();
    }
    

    
    private void doBindService() {
        bindService(new Intent(this, GalleryService.class), serviceConnection,
                Context.BIND_AUTO_CREATE);
        isBoundService = true;
    }
    
    private void doUnbindService() {
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

            // check for new items
            itemWorker.queryFirstItems();
            
            // create a new listview adapter
            adapter = new GalleryAdapter(GalleryActivity.this, itemWorker, thumbnailProvider);
            gridView.setAdapter(adapter);
            
            // change the icon selection based on current filter settings
            galleryBarOnClickListener.updateShowItems();

            // no need to wait for the callback if theres something to see
            if (itemWorker.getItemCount() > 0) {
                hideProgressDialog();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {}
    };
    

    
    public void updateThumbnailSize() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        thumbMinWidth = prefs.getInt("thumbnailSize", 70);
        if (thumbMinWidth <= 0) {
            Log.w(TAG, "thumbnailSize is zero!");
            thumbMinWidth = 120;
        }

        numColumns = (int) Math.ceil(screenWidth / thumbMinWidth);
        int targetWidth = (int) Math.floor(screenWidth / numColumns);
        targetWidth = targetWidth * numColumns - ((numColumns+1) * (THUMB_SPACING));
        thumbWidth = (int) Math.floor(targetWidth / numColumns);
        Log.d(TAG, "grid dimensions: numColumns=" + String.valueOf(numColumns) + 
                   " screenWidth=" + String.valueOf(screenWidth) + 
                   " thumbWidth=" + String.valueOf(thumbWidth) + 
                   " targetWidth=" + String.valueOf(targetWidth));
        
        scrollThreshold = (int) Math.floor(numColumns * 2.5);
        Log.d(TAG, "scroll threshold set to " + String.valueOf(scrollThreshold));

        imageViewLayoutParams = new ViewSwitcher.LayoutParams(thumbWidth, thumbWidth);

        // set the gridview properties, this will also refresh for different
        // thumbnail sizes:
        gridView.setColumnWidth(thumbWidth);
        gridView.setNumColumns(numColumns);
        gridView.invalidateViews();
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.gallery_menu, menu);
        
        menu.findItem(R.id.galleryMenuSettingsItem).setOnMenuItemClickListener(this);
        menu.findItem(R.id.galleryMenuRefreshItem).setOnMenuItemClickListener(this);
        
        return true;
    }
    
    public void hideProgressDialog() {
    	if (!isFinishing() && progressDialog != null && progressDialog.isShowing()) {
    	    // progressDialog.getWindow()
    		// progressDialog.dismiss(); this causes an exception
    		progressDialog.hide();
    	}
    }

    public void showErrorAlert(String error) {
        // new AlertDialog.Builder(this).setTitle("Error").setMessage(error);
        AlertDialog alertDialog = new AlertDialog.Builder(this).create();  
    alertDialog.setTitle("Error");  
    alertDialog.setMessage(error);
    alertDialog.setButton("OK", new DialogInterface.OnClickListener() {  
      public void onClick(DialogInterface dialog, int which) {  
        return;  
    } });   
    alertDialog.show();
    }

	public View createItemView() {
        Log.d(TAG, "create new item view");

        // create view switcher
        ViewSwitcher viewSwitcher = new ViewSwitcher(this);
        viewSwitcher.setBackgroundColor(R.color.gallery_item_view_background);
        viewSwitcher.setPadding(THUMB_PADDING, THUMB_PADDING, THUMB_PADDING, THUMB_PADDING);

        // create progressbar
        ViewSwitcher.LayoutParams progressBarLayoutParams =
                 new ViewSwitcher.LayoutParams(
                         (int) Math.floor(thumbWidth / 2.5), 
                         (int) Math.floor(thumbWidth / 2.5));
        progressBarLayoutParams.gravity = Gravity.CENTER;
        ProgressBar progressBar = new ProgressBar(this);
        progressBar.setLayoutParams(progressBarLayoutParams);
        
        // create image view
        ImageView imageView = new ImageView(this);
        imageView.setLayoutParams(imageViewLayoutParams);
        imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        
        // add to view switcher
        viewSwitcher.addView(progressBar);
        viewSwitcher.addView(imageView);
        viewSwitcher.setDisplayedChild(0);
        
        return viewSwitcher;
	}
	
	private ProgressBar getProgressBarFromViewSwitcher(ViewSwitcher viewSwitcher) {
	    return (ProgressBar) viewSwitcher.getChildAt(0);
	}
	
	private ImageView getImageViewFromViewSwitcher(ViewSwitcher viewSwitcher) {
        return (ImageView) viewSwitcher.getChildAt(1);
    }
	
    public void showItemViewProgressBar(View view) {
	    ((ViewSwitcher) view).setDisplayedChild(0);
	}
	
    private void showItemViewImageView(View view) {
        ((ViewSwitcher) view).setDisplayedChild(1);
    }
	
	public void updateItemView(View view, Bitmap bitmap) {
        // bitmap = Bitmap.createScaledBitmap(bitmap, 
	    //       thumbWidth - THUMB_PADDING * 2, 
	    //       thumbWidth - THUMB_PADDING * 2, 
	    //       true);
        
        showItemViewImageView(view);
        
        ImageView imageView = getImageViewFromViewSwitcher((ViewSwitcher) view);
        imageView.setLayoutParams(imageViewLayoutParams);
        imageView.setImageBitmap(bitmap);
	}

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        Item item = itemWorker.getItemByPosition(position);
        
        if (item == null) return;
        
        if (item.getType() == Type.VIDEO) {
            Intent openLinkIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(item.getSource()));
            startActivity(openLinkIntent);
        }
        else {
            Intent showItemActivityIntent = new Intent(this, ItemActivity.class);
            Bundle itemIdBundle = new Bundle();
            itemIdBundle.putInt("id", item.getId());
            showItemActivityIntent.putExtras(itemIdBundle);
            startActivity(showItemActivityIntent);
        }
    }

    @Override
    public boolean onMenuItemClick(MenuItem menuItem) {
        switch (menuItem.getItemId()) {
        case R.id.galleryMenuSettingsItem:
            Intent settingsActivity = new Intent(getBaseContext(), SettingsActivity.class);
            startActivity(settingsActivity);
            break;
        
        case R.id.galleryMenuRefreshItem:
            Toast.makeText(this, "Fetching new items...", Toast.LENGTH_SHORT).show();
            itemWorker.queryNewerItems();
            break;

        }
        
        
        return true;
    }
    
    public GalleryAdapter getAdapter() {
        return adapter;
    }

    public GridView getGridView() {
        return gridView;
    }
    
    
    private class GalleryBarOnClickListener implements OnClickListener {
        
        /**
         * Used as a background color to indicate the active element.
         */
        private int active_color;
        
        public GalleryBarOnClickListener() {
            active_color = getResources().getColor(R.color.gallery_bar_active);
        }
        
        public void updateShowItems() {
            View showImagesView = findViewById(R.id.galleryBarShowImagesIcon);
            if (itemWorker.isHiddenImages()) {
                setViewBackground(showImagesView, false);
            }
            else {
                setViewBackground(showImagesView, true);
            }

            View showVideosView = findViewById(R.id.galleryBarShowVideosIcon);
            if (itemWorker.isHiddenVideos()) {
                setViewBackground(showVideosView, false);
            }
            else {
                setViewBackground(showVideosView, true);
            }
        }
        
        private void setViewBackground(View view, boolean active) {
            if (active) {
                view.setBackgroundColor(active_color);
            }
            else {
                view.setBackgroundColor(Color.TRANSPARENT);
            }
        }
        
        @Override
        public void onClick(View v) {
            ImageView imageView = (ImageView) v;
            
            switch (imageView.getId()) {
            case R.id.galleryBarShowImagesIcon:
                if (itemWorker.isHiddenImages()) { // are images hidden?
                    // show images...
                    itemWorker.setHideImages(false);
                    
                    // active background:
                    setViewBackground(imageView, true);
                }
                else {
                    itemWorker.setHideImages(true);
                    setViewBackground(imageView, false);
                }
                break;
            case R.id.galleryBarShowVideosIcon:
                if (itemWorker.isHiddenVideos()) {
                    itemWorker.setHideVideos(false);
                    setViewBackground(imageView, true);
                }
                else {
                    itemWorker.setHideVideos(true);
                    setViewBackground(imageView, false);
                }
                break;
            case R.id.galleryBarPreferencesIcon:
                Intent settingsActivity = new Intent(getBaseContext(), 
                        SettingsActivity.class);
                startActivity(settingsActivity);
                break;
            }
        }
        
    }
    
}

