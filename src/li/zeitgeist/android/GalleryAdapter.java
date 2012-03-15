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
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

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
                galleryActivity.showErrorAlert(error);
            }
        });
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {

        Log.v(TAG, "getView, position: " + String.valueOf(position));
        
        final View view;
        if (convertView == null) {
            // creates an empty view
            view = galleryActivity.createItemView();
        }
        else {
            view = convertView; // use recycled view
        }

        // hide previous image, show progress circle
        galleryActivity.showItemViewProgressBar(view);
        
        // last item also triggers the loading of older items
        if (itemWorker.getItemCount() == position+1) {
            itemWorker.queryOlderItems();
            //return view;
        }
        
        final Item item = getItem(position);
        
        // tag this view with the item id
        view.setTag(item.getId());
        Log.v(TAG, "view tagged: " + String.valueOf(item.getId()));
        
        if (thumbnailWorker.isMemCached(item)) {
            Log.v(TAG, "updateItemView in ui thread, memcached: " + String.valueOf(item.getId()));
            galleryActivity.updateItemView(view, thumbnailWorker.getBitmapByItem(item));
        }
        else {
            // load bitmap from disk or web and update the view
            // within the UI thread, other loadThumbnail()'s override the callback
            Log.v(TAG, "loadThumbnail() for id: " + String.valueOf(item.getId()));
            thumbnailWorker.loadThumbnail(item, 
                    new ThumbnailWorker.LoadedThumbnailListener() {
                @Override
                public void onLoadedThumbnail(final int id, final Bitmap bitmap) {

                    Log.v(TAG, "onLoadedThumbnail callback returned for id: " + String.valueOf(id));
                    view.post(new Runnable() {
                        public void run() {
                            if ((Integer) view.getTag() != id) {
                                Log.w(TAG, "warning tag mismatch: " + String.valueOf(id) + " (tagged) item: " + String.valueOf((Integer)view.getTag()));
                                return;
                            }
                            
                            galleryActivity.updateItemView(view, bitmap);
                        }
                    });
                }
            });
        }

        return view;
    }
}
