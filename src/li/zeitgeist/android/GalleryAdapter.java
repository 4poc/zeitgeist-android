package li.zeitgeist.android;

import java.util.List;

import li.zeitgeist.android.provider.*;
import li.zeitgeist.android.provider.ItemProvider.NewItemsListener;

import li.zeitgeist.api.Item;
import android.graphics.Bitmap;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

public class GalleryAdapter extends BaseAdapter implements NewItemsListener {

    private static final String TAG = ZeitgeistApp.TAG + ":GalleryAdapter";
    
    private GalleryActivity galleryActivity;
    private ItemProvider itemProvider;
    private ThumbnailProvider thumbnailProvider;
    
    public GalleryAdapter(GalleryActivity galleryActivity,
      ItemProvider itemProvider, 
      ThumbnailProvider thumbnailProvider) {
        super();
        this.galleryActivity = galleryActivity;
        this.itemProvider = itemProvider;
        this.thumbnailProvider = thumbnailProvider;
        
        itemProvider.addNewItemsListener(this);
    }

    @Override
    public int getCount() {
        return itemProvider.getItemCount();
    }

    @Override
    public Item getItem(int position) {
        return itemProvider.getItemByPosition(position);
    }

    @Override
    public long getItemId(int position) {
        return getItem(position).getId();
    }
    
    public void onNewItems(List<Item> newItems) {
        galleryActivity.getGridView().post(new Runnable() {
            public void run() {
                galleryActivity.hideProgressDialog();
                notifyDataSetChanged();
                //itemProvider.queryOlderItems();
            }
        });
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
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
        
        final Item item = getItem(position);
        if (thumbnailProvider.isMemCached(item)) {
            galleryActivity.updateItemView(view, thumbnailProvider.getBitmapByItem(item));
        }
        else {
            // load bitmap from disk or web and update the view
            // within the UI thread, other loadThumbnail()'s override the callback
            thumbnailProvider.loadThumbnail(item, 
                    new ThumbnailProvider.LoadedThumbnailListener() {
                @Override
                public void onLoadedThumbnail(final Bitmap bitmap) {
                    view.post(new Runnable() {
                        public void run() {
                            galleryActivity.updateItemView(view, bitmap);
                        }
                    });
                }
            });
        }

        return view;
    }
}
