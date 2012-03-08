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

import android.text.ClipboardManager;
import android.util.*;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;

import android.view.*;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.View.OnClickListener;
import android.widget.*;
import android.widget.AbsListView.OnScrollListener;
import android.widget.AdapterView.OnItemClickListener;

import li.zeitgeist.android.provider.ItemProvider;
import li.zeitgeist.android.provider.ThumbnailProvider;
import li.zeitgeist.api.Item;
import li.zeitgeist.api.Item.Type;

public class GalleryActivity extends Activity 
  implements OnScrollListener, OnItemClickListener, OnMenuItemClickListener {


    private static final String TAG = ZeitgeistApp.TAG + ":GalleryActivity";

    private ThumbnailProvider thumbnailProvider;
    private ItemProvider itemProvider;
    
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

    GalleryAdapter adapter;

    public GalleryActivity() {
        super();
        Log.v(TAG, "constructed");
    }
    

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.v(TAG, "onCreate()");

        // get the global provider instances
        itemProvider = ((ZeitgeistApp)getApplication()).getItemProvider();
        thumbnailProvider = ((ZeitgeistApp)getApplication()).getThumbnailProvider();

        // Disable the title bar
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        // Set main layout
        setContentView(R.layout.gallery);
        
        // set the screen width (800 or 480 on desire z)
        Display display = getWindowManager().getDefaultDisplay();
        screenWidth = display.getWidth() - 4; /* FIXME: where the 2 spacing coming from? */

        // create adapter
        adapter = new GalleryAdapter(this, itemProvider, thumbnailProvider);

        // get the gallery gridview
        gridView = (GridView) findViewById(R.id.thumbnailGrid);
        gridView.setHorizontalSpacing(THUMB_SPACING);
        gridView.setVerticalSpacing(THUMB_SPACING);
        gridView.setAdapter(adapter);
        // gridView.setOnScrollListener(this);
        gridView.setOnItemClickListener(this);
        
        // calculates and sets the thumbnail item size (thumbWidth)
        updateThumbnailSize();
        
        // show progress dialog per default
        progressDialog = ProgressDialog.show(this, null, "Loading...", true);
        if (itemProvider.getItemCount() > 0) {
        	progressDialog.hide();
        }
        
        // gallery bar (icon header bar) assigns onclick listener
        GalleryBarOnClickListener listener = new GalleryBarOnClickListener();
        ((ImageView) findViewById(R.id.galleryBarShowImagesIcon))
            .setOnClickListener(listener);
        ((ImageView) findViewById(R.id.galleryBarShowVideosIcon))
            .setOnClickListener(listener);
        ((ImageView) findViewById(R.id.galleryBarPreferencesIcon))
            .setOnClickListener(listener);
        
        // change the background based on the settings of itemProvider
        listener.updateShowItems();
    }
    
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
    
	@Override
    public void onDestroy() {
        super.onDestroy();
        Log.v(TAG, "onDestroy()");

        progressDialog.dismiss();
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
    
    private int lastVisibleItemCount = 0;

	@Override
	public void onScroll(AbsListView view, int firstVisibleItem,
			int visibleItemCount, int totalItemCount) {
		if (itemProvider.isLoading()) {
		    Log.d(TAG, "itemProvider is loading");
		    return;
		}

        if (totalItemCount - (firstVisibleItem + visibleItemCount) < scrollThreshold &&
                lastVisibleItemCount != visibleItemCount) {
            Log.d(TAG, "you've reached the end, loading new items");
            itemProvider.queryOlderItems();
            lastVisibleItemCount = visibleItemCount;
        }
	}

	@Override
	public void onScrollStateChanged(AbsListView view, int scrollState) {}
	
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
        Item item = itemProvider.getItemByPosition(position);
        
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
            itemProvider.queryNewerItems();
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
            if (itemProvider.getHideImages()) {
                setViewBackground(showImagesView, true);
            }
            else {
                setViewBackground(showImagesView, false);
            }

            View showVideosView = findViewById(R.id.galleryBarShowVideosIcon);
            if (itemProvider.getHideVideos()) {
                setViewBackground(showVideosView, true);
            }
            else {
                setViewBackground(showVideosView, false);
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
                if (itemProvider.getHideImages()) { // are images hidden?
                    // show images...
                    itemProvider.setHideImages(false);
                    
                    // active background:
                    setViewBackground(imageView, true);
                }
                else {
                    itemProvider.setHideImages(true);
                    setViewBackground(imageView, false);
                }
                break;
            case R.id.galleryBarShowVideosIcon:
                if (itemProvider.getHideVideos()) {
                    itemProvider.setHideVideos(false);
                    setViewBackground(imageView, true);
                }
                else {
                    itemProvider.setHideVideos(true);
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

