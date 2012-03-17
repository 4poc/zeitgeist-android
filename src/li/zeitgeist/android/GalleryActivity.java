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
import android.content.*;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;

import android.view.*;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.View.OnClickListener;
import android.widget.*;
import android.widget.AdapterView.OnItemClickListener;

import li.zeitgeist.android.worker.ItemWorker;
import li.zeitgeist.android.worker.ThumbnailWorker;
import li.zeitgeist.api.Item;
import li.zeitgeist.api.Item.Type;

/**
 * Main Activity of Zeitgeist for Android.
 * 
 * Displays a gallery with thumbnails of the zeitgeist 
 * installation. Uses the Zeitgeist Java API to download
 * the videos, images meta information, allows endless
 * scrolling through everything.
 */
public class GalleryActivity extends Activity 
  implements OnItemClickListener, OnMenuItemClickListener {

    /**
     * Standard android logging tag.
     */
    private static final String TAG = ZeitgeistApp.TAG + ":GalleryActivity";
    
    /**
     * Process dialog to show during loading of items.
     */
    private ProgressDialog progressDialog = null;
    
    /**
     * Instance of the listview/GridView the gallery is displaying.
     */
    private GridView gridView = null;
    
    /**
     * Spacing between thumbnails.
     */
    private static final int THUMB_SPACING = 5;
    
    /**
     * Padding between thumbnails.
     */
    private static final int THUMB_PADDING = 2;
    
    /**
     * Width of the screen, varies between devices and in landscape mode.
     */
    private int screenWidth;
    
    /**
     * Minimum approximate width of thumbnails.
     * 
     * The size of the thumbnails (square images) is determined by
     * this setting, the user can customize this. It is adjusted
     * so that there is never blank space left.
     */
    private int thumbMinWidth = 120;

    /**
     * Calculated apposite width of the thumbnails.
     */
    private int thumbWidth;
    
    /**
     * Number of thumbnails in each row.
     */
    private int numColumns;
    
    /**
     * LayoutParams of each Thumbnail ImageView.
     * 
     * This can change at runtime based on user settings and
     * the size of the screen.
     */
    private ViewSwitcher.LayoutParams imageViewLayoutParams;
    
    /**
     * Worker thread downloads item metadata.
     */
    private ItemWorker itemWorker;
    
    /**
     * Worker thread pool downloads thumbnail bitmaps.
     */
    private ThumbnailWorker thumbnailWorker;
    
    /**
     * Service that holds the worker instances.
     */
    private GalleryService boundService;
    
    /**
     * If the service has been bound to this activity.
     */
    private boolean isBoundService;
    
    /**
     * ListView adapter for the GridView.
     */
    GalleryAdapter adapter;
    
    /**
     * OnClick listener for the GalleryBar.
     * 
     * The GalleryBar is the header bar that is always visible
     * and allows to filter for videos/images.
     */
    GalleryBarOnClickListener galleryBarOnClickListener;

    /**
     * Constructs the main activity.
     */
    public GalleryActivity() {
        super();
        Log.v(TAG, "constructed");
    }
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.v(TAG, "onCreate()");
        
        // bind the worker, starts (if not already) the workers
        doBindService();
        
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

        // stores the memory cache of item objects to sdcard
        itemWorker.saveItemDiskCache();
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.v(TAG, "onResume()");
        
        updateThumbnailSize();
    }
    
    /**
     * Bind the service, this creates and starts the workers.
     * 
     * If not already running. Does not happen immediately, the service
     * connection is used to do the stuff that needs to happen after
     * the service is created and started.
     */
    private void doBindService() {
        bindService(new Intent(this, GalleryService.class), serviceConnection,
                Context.BIND_AUTO_CREATE);
        isBoundService = true;
    }
    
    /**
     * Unbounds the service.
     */
    private void doUnbindService() {
        if (isBoundService) {
            // Detach our existing connection.
            unbindService(serviceConnection);
            isBoundService = false;
        }
    }
    
    /**
     * Service connection instance called when the service is ready.
     */
    private ServiceConnection serviceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            Log.v(TAG, "onServiceConnected");
            
            // get the service instance, either creates one or uses
            // an existing
            boundService = ((GalleryService.GalleryServiceBinder) service).getService();
            
            // get worker instances:
            itemWorker = boundService.getItemWorker();
            thumbnailWorker = boundService.getThumbnailWorker();

            // check for new items
            itemWorker.queryFirstItems();
            
            // create a new listview adapter
            adapter = new GalleryAdapter(GalleryActivity.this, itemWorker, thumbnailWorker);
            gridView.setAdapter(adapter);
            
            // change the icon selection based on current filter settings
            galleryBarOnClickListener.updateShowIcons();

            // no need to wait for the callback if theres something to see
            if (itemWorker.getItemCount() > 0) {
                hideProgressDialog();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {}
    };

    /**
     * Calculates the size of the thumbnail based on the user setting.
     * 
     * The width/height (thumbnails are square at the moment) is
     * calculated based on the thumbnailSize setting and the current
     * width of the screen, so that there is no space left over.
     */
    public void updateThumbnailSize() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        thumbMinWidth = prefs.getInt("thumbnailSize", 0);
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
        
        // handle clicks in the options menu within the activity:
        menu.findItem(R.id.galleryMenuSettingsItem).setOnMenuItemClickListener(this);
        menu.findItem(R.id.galleryMenuRefreshItem).setOnMenuItemClickListener(this);
        
        return true;
    }
    
    /**
     * Dismiss the loading dialog.
     */
    public void hideProgressDialog() {
    	if (!isFinishing() && progressDialog != null && progressDialog.isShowing()) {
    	    // progressDialog.getWindow()
    		// progressDialog.dismiss(); this causes an exception??!
    		progressDialog.hide();
    	}
    }

    /**
     * Show an alert dialog with a error message.
     * 
     * @param error message
     */
    public void showErrorAlert(String error) {
        AlertDialog alertDialog = new AlertDialog.Builder(this).create();  
        alertDialog.setTitle("Error");  
        alertDialog.setMessage(error);
        alertDialog.setButton("OK", new DialogInterface.OnClickListener() {  
            public void onClick(DialogInterface dialog, int which) {  
                return;  
            } });   
        alertDialog.show();
    }

	/**
	 * Create a new view to be used as a item within the GridView.
	 * 
	 * This is called by the GalleryAdapter to create a new view,
	 * the same views are later recycled by the gridview to display
	 * other images.
	 * 
	 * The View is a ViewSwitcher that either shows a progress/loading
	 * bar or the image thumbnail.
	 * 
	 * @return new view instance
	 */
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
	
	/**
	 * Return the thumbnail image view of the viewswitcher.
	 * 
	 * @param viewSwitcher
	 * @return imageview instance
	 */
	private ImageView getImageViewFromViewSwitcher(ViewSwitcher viewSwitcher) {
        return (ImageView) viewSwitcher.getChildAt(1);
    }
	
    /**
     * Switch to the progress/loading bar of the view(switcher)
     * 
     * @param view
     */
    public void showItemViewProgressBar(View view) {
	    ((ViewSwitcher) view).setDisplayedChild(0);
	}
	
    /**
     * Switch to the thumbnail image view of the provided view(switcher)
     * 
     * @param view
     */
    private void showItemViewImageView(View view) {
        ((ViewSwitcher) view).setDisplayedChild(1);
    }
	
	/**
	 * Update the View(Switcher) thumbnail ImageView's bitmap.
	 * 
	 * This sets the bitmap and resets the layout.
	 * 
	 * @param view
	 * @param bitmap
	 */
	public void updateItemView(View view, Bitmap bitmap) {
        showItemViewImageView(view);
        
        ImageView imageView = getImageViewFromViewSwitcher((ViewSwitcher) view);
        imageView.setLayoutParams(imageViewLayoutParams);
        imageView.setImageBitmap(bitmap);
	}

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        Item item = itemWorker.getItemByPosition(position);
        
        if (item == null) return; // huh?
        
        // click on video thumbnails starts browser or youtube application/etc.
        if (item.getType() == Type.VIDEO) {
            Intent openLinkIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(item.getSource()));
            startActivity(openLinkIntent);
        }
        else { // click starts the item activity that shows the fullsized image
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
        // click on settings opens the settings activity
        case R.id.galleryMenuSettingsItem:
            Intent settingsActivity = new Intent(getBaseContext(), SettingsActivity.class);
            startActivity(settingsActivity);
            break;
        
        // click on refresh queries the loading in the itemWorker
        case R.id.galleryMenuRefreshItem:
            Toast.makeText(this, "Fetching new items...", Toast.LENGTH_SHORT).show();
            itemWorker.queryNewerItems();
            break;

        }
        return true;
    }
    
    /**
     * Returns the GalleryAdapter instance.
     * 
     * @return GalleryAdapter
     */
    public GalleryAdapter getAdapter() {
        return adapter;
    }

    /**
     * Returns the GridView instance.
     * 
     * @return GridView
     */
    public GridView getGridView() {
        return gridView;
    }

    /**
     * OnClickListener for the header bar icons.
     * 
     * Handle the icons shown always on top of the gallery, to
     * filter for videos and images.
     */
    private class GalleryBarOnClickListener implements OnClickListener {
        /**
         * Used as a background color to indicate the active element.
         */
        private int active_color;
        
        /**
         * Constructs a new listener, use a resource as the active bg color.
         */
        public GalleryBarOnClickListener() {
            active_color = getResources().getColor(R.color.gallery_bar_active);
        }
        
        /**
         * Update the icon's background color to indicate if there active.
         * 
         * This updates the state of the icons based on the current
         * filter setting of the item worker. Note that the itemWorker
         * loads everything and only later applies the filtering for
         * videos and images that is controled here. 
         */
        public void updateShowIcons() {
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
        
        /**
         * Sets the background color to indicate that its active (if param is set).
         * 
         * @param view should be a imageview
         * @param active if true use the active color, transparent otherwise.
         */
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
