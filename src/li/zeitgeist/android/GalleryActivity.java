package li.zeitgeist.android;

import android.util.*;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;

import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;
import android.widget.ViewSwitcher;
import android.widget.AbsListView.OnScrollListener;
import android.widget.GridView;

import li.zeitgeist.android.provider.ItemProvider;
import li.zeitgeist.android.provider.ThumbnailProvider;
import li.zeitgeist.api.Item;

public class GalleryActivity extends Activity implements OnScrollListener, OnItemClickListener {

    private static final String TAG = ZeitgeistApp.TAG + ":GalleryActivity";

    private ThumbnailProvider thumbnailProvider;
    private ItemProvider itemProvider;
    
    private ProgressDialog progressDialog = null;
    
    private GridView gridView;
    
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
    
    public GridView getGridView() {
        return gridView;
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
        setContentView(R.layout.gallery);
        
        Display display = getWindowManager().getDefaultDisplay();
        screenWidth = display.getWidth() - 4; /* FIXME: where the 2 spacing coming from? */
        
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
        
        // create adapter
        adapter = new GalleryAdapter(this, itemProvider, thumbnailProvider);

        // get the gallery gridview
        gridView = (GridView)findViewById(R.id.thumbnailGrid);
        gridView.setHorizontalSpacing(THUMB_SPACING);
        gridView.setVerticalSpacing(THUMB_SPACING);
        gridView.setColumnWidth(thumbWidth);
        gridView.setNumColumns(numColumns);
        gridView.setAdapter(adapter);
        gridView.setOnScrollListener(this);
        gridView.setOnItemClickListener(this);
        
        // show progress dialog per default
        progressDialog = ProgressDialog.show(this, null, "Loading...", true);
        if (itemProvider.getItemCount() > 0) {
        	progressDialog.hide();
        }
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
    }

	@Override
	public void onScroll(AbsListView view, int firstVisibleItem,
			int visibleItemCount, int totalItemCount) {
		if (itemProvider.isLoading()) {
		    Log.d(TAG, "itemProvider is loading");
		    return;
		}
		
        Log.d(TAG, "onScroll(firstVisibleItem=" + String.valueOf(firstVisibleItem) + " visibleItemCount=" + String.valueOf(visibleItemCount) + " totalItemCount=" + String.valueOf(totalItemCount) + ")");

        if (totalItemCount - (firstVisibleItem + visibleItemCount) < scrollThreshold) {
            Log.d(TAG, "you've reached the end, loading new items");
            itemProvider.queryOlderItems();
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
        ViewSwitcher.LayoutParams imageViewLayoutParams = 
                new ViewSwitcher.LayoutParams(thumbWidth, thumbWidth);
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
        imageView.setImageBitmap(bitmap);
	}

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        Item item = itemProvider.getItemByPosition(position);
        
        if (item == null) return;
        
        Intent showItemActivityIntent = new Intent(this, ItemActivity.class);
        Bundle itemIdBundle = new Bundle();
        itemIdBundle.putInt("id", item.getId());
        showItemActivityIntent.putExtras(itemIdBundle);
        startActivity(showItemActivityIntent);
    }

}

