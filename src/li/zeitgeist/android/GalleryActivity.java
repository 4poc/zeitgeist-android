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

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import android.app.AlertDialog;

import android.content.DialogInterface;

import android.util.*;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.provider.MediaStore;

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
     * Square loading animation in the gallery bar.
     * 
     * Is hidden by default.
     */
    private ProgressBar galleryBarProgressIcon;

    
    /**
     * Instance of the listview/GridView the gallery is displaying.
     */
    private GridView gridView = null;

    /**
     * Spacing between thumbnails.
     */
    public static final int THUMB_SPACING = 5;
    
    /**
     * Padding between thumbnails.
     */
    public static final int THUMB_PADDING = 2;
    
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
    private LocalService boundService;
    
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
     * Request Code used to identify picture taken response.
     */
    private static final int REQUEST_CODE_PICTURE_TAKEN = 1;

    /**
     * Constructs the main activity.
     */
    public GalleryActivity() {
        super();
        Log.v(TAG, "constructed");
    }
    
    /**
     * Checks if the phone has a camera and a application for it.
     * @return
     */
    public boolean isCameraAvailable() {
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)) {
            return false;
        }
        final PackageManager packageManager = getPackageManager();
        final Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        List<ResolveInfo> list =
                packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
        return list.size() > 0;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.v(TAG, "onCreate()");
        
        // Disable the title bar
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        // Set main layout
        setContentView(R.layout.gallery);
        
        // show initial item loading modal dialog
        showProgressDialog();

        // bind and/or create/start the local service with workers
        doBindService();
        
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
        ((ImageView) findViewById(R.id.galleryBarLogo))
        	.setOnClickListener(galleryBarOnClickListener);
        ((ImageView) findViewById(R.id.galleryBarShowImagesIcon))
            .setOnClickListener(galleryBarOnClickListener);
        ((ImageView) findViewById(R.id.galleryBarShowVideosIcon))
            .setOnClickListener(galleryBarOnClickListener);
        ((ImageView) findViewById(R.id.galleryBarPreferencesIcon))
            .setOnClickListener(galleryBarOnClickListener);
        ((ImageView) findViewById(R.id.galleryBarRefreshIcon))
            .setOnClickListener(galleryBarOnClickListener);        
        
        galleryBarProgressIcon = 
                (ProgressBar) findViewById(R.id.galleryBarProgressIcon);
        
        // show the camera icon if the device has one
        ImageView galleryBarCameraIcon = (ImageView) findViewById(R.id.galleryBarCameraIcon);
        if (isCameraAvailable()) {
            galleryBarCameraIcon.setVisibility(View.VISIBLE);
            galleryBarCameraIcon.setOnClickListener(galleryBarOnClickListener);
        }
        
        
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.v(TAG, "onDestroy()");

        hideProgressDialog();

        doUnbindService();
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.v(TAG, "onPause()");

        // stores the memory cache of item objects to sdcard
        if (itemWorker != null) {
            itemWorker.saveItemDiskCache();
        }
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
        bindService(new Intent(this, LocalService.class), serviceConnection,
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
            boundService = ((LocalService.GalleryServiceBinder) service).getService();
            
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
        menu.findItem(R.id.galleryMenuResetFilters).setOnMenuItemClickListener(this);
        
        return true;
    }
    
    /**
     * Create and show a modal progress dialog.
     * 
     * Its used to indicate the initial loading of items by the 
     * itemWorker. The dialog is dismissed either when the memory
     * or disk cache is restored or items are loaded from web.
     * 
     * There is a second progressBar that indicates the consecutive
     * loading of new items in background.
     */
    public void showProgressDialog() {
        progressDialog = ProgressDialog.show(this, null, "Loading...", true);
    }
    
    /**
     * Dismiss the item loading dialog.
     */
    public void hideProgressDialog() {
    	if (!isFinishing() && progressDialog != null && progressDialog.isShowing()) {
    		progressDialog.dismiss(); // progressDialog.hide();
    		progressDialog = null;
    	}
    }
    
    /**
     * Shows the square loading animation in the gallery bar.
     */
    public void showGalleryBarProgressIcon() {
        galleryBarProgressIcon.setVisibility(View.VISIBLE);
    }
    
    /**
     * Hides the square loading animation in the gallery bar.
     */
    public void hideGalleryBarProgressIcon() {
        galleryBarProgressIcon.setVisibility(View.GONE);
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

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        Item item = itemWorker.getItemByPosition(position);
        
        if (item == null) return; // huh?

        // switch to detailed item activity
        Intent showItemActivityIntent = new Intent(this, ItemActivity.class);
        Bundle itemIdBundle = new Bundle();
        itemIdBundle.putInt("id", item.getId());
        showItemActivityIntent.putExtras(itemIdBundle);
        startActivity(showItemActivityIntent);
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
            showGalleryBarProgressIcon();
            itemWorker.queryNewerItems();
            break;

        // clear filtering for tags etc.
        case R.id.galleryMenuResetFilters:
            itemWorker.setShowTag(null);
            break;
        }
        return true;
    }
    
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.v(TAG, String.format("onActivityResult(%d, %d, ?)", requestCode, resultCode));
        if (requestCode == REQUEST_CODE_PICTURE_TAKEN &&
                resultCode == RESULT_OK) {
            Intent createItemActivity = new Intent(getBaseContext(), 
                    CreateItemActivity.class);
            createItemActivity.putExtra("local_image", getTempImageFile());
            startActivity(createItemActivity);
        }
    }
    
    private File getTempImageFile() {
        return new File(Environment.getExternalStorageDirectory(), "zg_temp.jpg");
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
     * filter for videos and images. Switches the image bitmaps
     * to indicate active filters.
     */
    private class GalleryBarOnClickListener implements OnClickListener {
        
        /**
         * Switches icon bitmaps to indicate active/inactive filters.
         */
        public void updateShowIcons() {
            ImageView showImagesIcon = (ImageView) findViewById(R.id.galleryBarShowImagesIcon);
            if (itemWorker.isHiddenImages()) {
                showImagesIcon.setImageResource(R.drawable.bar_image_inactive);
            }
            else {
                showImagesIcon.setImageResource(R.drawable.bar_image);
            }
            
            ImageView showVideosIcon = (ImageView) findViewById(R.id.galleryBarShowVideosIcon);
            if (itemWorker.isHiddenVideos()) {
                showVideosIcon.setImageResource(R.drawable.bar_video_inactive);
            }
            else {
                showVideosIcon.setImageResource(R.drawable.bar_video);
            }
        }
        
        @Override
        public void onClick(View v) {
            ImageView imageView = (ImageView) v;
            
            switch (imageView.getId()) {
            case R.id.galleryBarLogo:
            	itemWorker.setShowTag(null); // reset filters
            	break;            	
            case R.id.galleryBarShowImagesIcon:
                if (itemWorker.isHiddenImages()) { // are images hidden?
                    // show images...
                    itemWorker.setHideImages(false);
                }
                else {
                    itemWorker.setHideImages(true);
                }
                updateShowIcons();
                break;
            case R.id.galleryBarShowVideosIcon:
                if (itemWorker.isHiddenVideos()) {
                    itemWorker.setHideVideos(false);
                }
                else {
                    itemWorker.setHideVideos(true);
                }
                updateShowIcons();
                break;
            case R.id.galleryBarPreferencesIcon:
                Intent settingsActivity = new Intent(getBaseContext(), 
                        SettingsActivity.class);
                startActivity(settingsActivity);
                break;
            case R.id.galleryBarRefreshIcon:
                showGalleryBarProgressIcon();
                itemWorker.queryNewerItems();
                break;
            case R.id.galleryBarCameraIcon:
                Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                Log.v(TAG, "Request to take picture");
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(getTempImageFile()));
                startActivityForResult(takePictureIntent, REQUEST_CODE_PICTURE_TAKEN);

                break;
            }
        }
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
    public ViewSwitcher createItemViewSwitcher() {
        // create view switcher
        ViewSwitcher viewSwitcher = new ViewSwitcher(this);
        viewSwitcher.setBackgroundColor(R.color.gallery_item_view_background);
        viewSwitcher.setPadding(GalleryActivity.THUMB_PADDING, 
                GalleryActivity.THUMB_PADDING, 
                GalleryActivity.THUMB_PADDING, 
                GalleryActivity.THUMB_PADDING);

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
    
}
