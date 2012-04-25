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
import li.zeitgeist.android.worker.ItemWorker.UpdatedItemsListener;

import li.zeitgeist.api.Item;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.ViewSwitcher;

/**
 * ListView Adapter for the Thumbnail Gallery.
 *
 * This uses the itemWorker as a datasource for the Item instances
 * the API provides, the Thumbnail URL of the items is then used to
 * download the thumbnail bitmaps (and cache them) in the
 * thumbnailWorker.
 */
public class GalleryAdapter extends BaseAdapter implements UpdatedItemsListener {


    
    /**
     * Standard android logging tag.
     */
    private static final String TAG = ZeitgeistApp.TAG + ":GalleryAdapter";
    
    /**
     * The GalleryActivity instance that created the adapter.
     */
    private GalleryActivity galleryActivity;
    
    /**
     * Worker thread for item metadata.
     */
    private ItemWorker itemWorker;
    
    /**
     * Worker thread pool that downloads thumbnail bitmaps.
     */
    private ThumbnailWorker thumbnailWorker;
    
    /**
     * Constructs the adapter.
     * 
     * @param galleryActivity that creates the adapter
     * @param itemWorker
     * @param thumbnailWorker
     */
    public GalleryAdapter(GalleryActivity galleryActivity,
            ItemWorker itemWorker, 
            ThumbnailWorker thumbnailWorker) {
        super();
        this.galleryActivity = galleryActivity;
        this.itemWorker = itemWorker;
        this.thumbnailWorker = thumbnailWorker;

        itemWorker.addUpdatedItemsListener(this);
    }

    @Override
    public int getCount() {
        return itemWorker.getItemCount();
    }

    @Override
    public Item getItem(int position) {
        return itemWorker.getItemByPosition(position);
    }

    @Override
    public long getItemId(int position) {
        return getItem(position).getId();
    }
    
    @Override
    public void onUpdatedItems(List<Item> newItems) {
        galleryActivity.getGridView().post(new Runnable() {
            public void run() {
                galleryActivity.hideProgressDialog();
                galleryActivity.hideGalleryBarProgressIcon();
                notifyDataSetChanged();
            }
        });
    }

    @Override
    public void onError(final String error) {
        galleryActivity.getGridView().post(new Runnable() {
            public void run() {
                Log.v(TAG, "onError called");
                galleryActivity.hideProgressDialog();
                galleryActivity.hideGalleryBarProgressIcon();
                galleryActivity.showErrorAlert(error);
            }
        });
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        final ViewSwitcher viewSwitcher;
        
        // create a new view or recycle an old one
        if (convertView == null) {
            viewSwitcher = galleryActivity.createItemViewSwitcher();
        }
        else {
            viewSwitcher = (ViewSwitcher) convertView; // use recycled view
        }
        
        
        final ProgressBar progress = (ProgressBar) viewSwitcher.getChildAt(0);
        final ImageView thumbnail = (ImageView) viewSwitcher.getChildAt(1);
        
        // the rendering of the last item is triggering the loading
        // of older items at the bottom.
        if (itemWorker.getItemCount() == position+1 && !itemWorker.isLockedQuery()) {
            itemWorker.queryOlderItems();
        }
        
        final Item item = getItem(position);
        if (item != null) {
            Log.d(TAG, String.format("getView(%d) -> %d", position, item.getId()));
            
            Integer tag = (Integer) viewSwitcher.getTag();
            if (tag != null && tag == item.getId()) {
                // nothing
            }
            else {
                viewSwitcher.setDisplayedChild(0);
                viewSwitcher.setTag(item.getId());
            }
            
            if (thumbnailWorker.isMemCached(item)) {
                Log.v(TAG, "updateItemView in ui thread, memcached: " + String.valueOf(item.getId()));
                Bitmap bitmap = thumbnailWorker.getBitmapByItem(item);
                if ((Integer) viewSwitcher.getTag() != item.getId()) {
                    Log.w(TAG, "warning tag mismatch: " + String.valueOf(item.getId()) + " (tagged) item: " + String.valueOf((Integer)viewSwitcher.getTag()));
                }
                else {
                    thumbnail.setImageBitmap(bitmap);
                    viewSwitcher.setDisplayedChild(1);
                }
                
            }
            else {
                // load bitmap from disk or web and update the view
                // within the UI thread, other loadThumbnail()'s override the callback
                Log.v(TAG, "loadThumbnail() for id: " + String.valueOf(item.getId()));
                thumbnailWorker.loadThumbnail(item, 
                        new ThumbnailWorker.LoadedThumbnailListener() {
                    @Override
                    public void onLoadedThumbnail(final int id, final Bitmap bitmap) {

                        Log.v(TAG, "[DEBUG] onLoadedThumbnail callback returned for id: " + String.valueOf(id));
                        galleryActivity.runOnUiThread(new Runnable() {
                            public void run() {
                                Log.v(TAG, "[DEBUG] viewSwitcher.post(Runnable()) for " + String.valueOf(id));
                                if ((Integer) viewSwitcher.getTag() != id) {
                                    Log.w(TAG, "warning tag mismatch: " + String.valueOf(id) + " (tagged) item: " + String.valueOf((Integer)viewSwitcher.getTag()));
                                    return;
                                }

                                thumbnail.setImageBitmap(bitmap);
                                viewSwitcher.setDisplayedChild(1);
                            }
                        });
                    }
                });
            }
        }
        else {
            Log.w(TAG, String.format("getView(%d) -> null", position));
        }

        /*
        // hide previous image, show progress circle
        galleryActivity.showItemViewProgressBar(view);
        


        
        
        
        // tag this view with the item id

        */
        
        return viewSwitcher;
    }


/*
    public View createItemView() {
        Log.d(TAG, "create new item view");


    }
    
    **
     * Return the thumbnail image view of the viewswitcher.
     * 
     * @param viewSwitcher
     * @return imageview instance
     *
    private ImageView getImageViewFromViewSwitcher(ViewSwitcher viewSwitcher) {
        return (ImageView) viewSwitcher.getChildAt(1);
    }
    
    **
     * Switch to the progress/loading bar of the view(switcher)
     * 
     * @param view
     *
    public void showItemViewProgressBar(View view) {
        ((ViewSwitcher) view).setDisplayedChild(0);
    }
    
    **
     * Switch to the thumbnail image view of the provided view(switcher)
     * 
     * @param view
     *
    private void showItemViewImageView(View view) {
        ((ViewSwitcher) view).setDisplayedChild(1);
    }
    
    **
     * Update the View(Switcher) thumbnail ImageView's bitmap.
     * 
     * This sets the bitmap and resets the layout.
     * 
     * @param view
     * @param bitmap
     *
    public void updateItemView(View view, Bitmap bitmap) {
        showItemViewImageView(view);
        
        ImageView imageView = getImageViewFromViewSwitcher((ViewSwitcher) view);
        imageView.setLayoutParams(imageViewLayoutParams);
        imageView.setImageBitmap(bitmap);
    }*/

}
