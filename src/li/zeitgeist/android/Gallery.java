package li.zeitgeist.android;

import java.lang.ref.SoftReference;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.app.ProgressDialog;

import android.content.Context;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;

import android.os.Handler;
import android.os.Looper;

import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.ViewSwitcher;

import li.zeitgeist.android.worker.ItemWorker;
import li.zeitgeist.android.worker.ThumbWorker;

import li.zeitgeist.api.*;
import li.zeitgeist.api.error.*;

public class Gallery {
    public static final String TAG = ZeitgeistApp.TAG + ":Gallery";

    private GalleryActivity activity;
    private Adapter adapter;

    // to scale the thumbnails
    private int screenWidth;

    // gridview dimensions/spacings/paddings
    // thumb == thumbnail == single imageview
    private static final int THUMB_SPACING = 5;
    private static final int THUMB_PADDING = 2;
    private int thumbMinWidth = 130;
    private int thumbMaxWidth = 200;
    private int thumbWidth;
    private int numColumns;


    // the number of items "left to scroll" before starting to query
    // for older items
    private int scrollThreshold = 5;


    private GridView gridView;

    public Gallery(GalleryActivity activity) {
        this.activity = activity;

        // set screen width
        Display display = activity.getWindowManager().getDefaultDisplay();
        screenWidth = display.getWidth() - 4; /* FIXME: where the 2 spacing comming from? */

        // set grid dimensions:
        numColumns = (int) Math.ceil(screenWidth / thumbMinWidth);
        int targetWidth = (int) Math.floor(screenWidth / numColumns);
        targetWidth = targetWidth * numColumns - ((numColumns+1) * (THUMB_SPACING));
        thumbWidth = (int) Math.floor(targetWidth / numColumns);
        Log.d(TAG, "Grid dimensions: numColumns=" + String.valueOf(numColumns) + 
                   " screenWidth=" + String.valueOf(screenWidth) + 
                   " thumbWidth=" + String.valueOf(thumbWidth) + 
                   " targetWidth=" + String.valueOf(targetWidth)); 

        // adjust scroll threshold
        scrollThreshold = (int) Math.floor(numColumns * 2.5);
        Log.d(TAG, "Scroll threshold set to " + String.valueOf(scrollThreshold));
    }

    public void setGridView(GridView gridView) {
        gridView.setHorizontalSpacing(THUMB_SPACING);
        gridView.setVerticalSpacing(THUMB_SPACING);
        gridView.setColumnWidth(thumbWidth);
        gridView.setNumColumns(numColumns);
        this.adapter = new Adapter((Context)activity);
        gridView.setAdapter(this.adapter);
        gridView.setOnScrollListener(this.adapter);
        this.gridView = gridView;

        Log.v(TAG, "setGridView done.");
    }
    
    public class Adapter extends BaseAdapter implements OnScrollListener {
        Context context;

        private Handler uiHandler;

        private ItemWorker itemWorker;

        private ThumbWorker thumbnailWorker;//TODO ref. thumbWorker

        private boolean loading = true;

        public Adapter(Context context) {
            this.context = context;

            Log.d(TAG, "Adapter constructed! " + screenWidth);

            // this handler can be used to execute code within the UI thread
            // (because this constructor is called within main/ui)
            this.uiHandler = new Handler();

            final ProgressDialog progress = ProgressDialog.show(context, null, "Loading...", true);
            
            itemWorker = new ItemWorker(new ZeitgeistApi("http://ip.apoc.cc:4567"));
            itemWorker.setNewItemsListener(new ItemWorker.NewItemsListener() {
                public void newItems() {
                    uiHandler.post(new Runnable() {
                        public void run() {
                            if (progress.isShowing()) {
                                progress.dismiss();
                            }
                            // the itemWorker's hashtable was updated!
                            Log.d(TAG, "notifyDataSetChanged! count = " + String.valueOf(itemWorker.getItemCount()));
                            notifyDataSetChanged();
                            loading = false;
                        }
                    });
                }
            });
            //itemWorker.start();


            thumbnailWorker = new ThumbWorker(this.uiHandler, thumbWidth - THUMB_PADDING*2);
            //thumbnailWorker.start();
        }

        @Override
        public void onScroll(AbsListView view, int firstVisibleItem,
          int visibleItemCount, int totalItemCount) {
            if (loading) return;

            // Log.d(TAG, "onScroll(firstVisibleItem=" + String.valueOf(firstVisibleItem) + " visibleItemCount=" + String.valueOf(visibleItemCount) + " totalItemCount=" + String.valueOf(totalItemCount) + ")");

            if (totalItemCount - (firstVisibleItem + visibleItemCount) < scrollThreshold) {
                Log.d(TAG, "you've reached the end, loading new data!");
                itemWorker.queryOlderItems();
                loading = true;
            }
        }

        @Override
        public void onScrollStateChanged(AbsListView view, int scrollState) {
            // TODO Auto-generated method stub
        }

        @Override
        protected void finalize() throws Throwable {
            super.finalize();
            itemWorker.stopThread();
            thumbnailWorker.stopThread();
        }

        @Override
        public int getCount() {
            return itemWorker.getItemCount();
        }

        @Override
        public Object getItem(int position) {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public long getItemId(int position) {
            // TODO Auto-generated method stub
            return 0;
        }

        // assigned to the view tag
        class ThumbViewHolder {
            int id;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewSwitcher viewSwitcher;
            // ImageView imageView = null;
            // static final Bitmap bitmap = BitmapFactory.decodeResource(context.getResources(), R.drawable.process);
            if (convertView == null) {
                Log.d(TAG, "Construct New View for position " + String.valueOf(position));
                viewSwitcher = new ViewSwitcher(context);

                ProgressBar progress = new ProgressBar(context);

                ViewSwitcher.LayoutParams layoutParams = new ViewSwitcher.LayoutParams(thumbWidth / 2, thumbWidth / 2);
                layoutParams.gravity = Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL;


                progress.setLayoutParams(layoutParams);

                progress.setPadding(THUMB_PADDING, THUMB_PADDING, THUMB_PADDING, THUMB_PADDING);
                viewSwitcher.addView(progress);

                ImageView imageView = new ImageView(context); 
                imageView.setLayoutParams(new GridView.LayoutParams(thumbWidth, thumbWidth));
                imageView.setPadding(THUMB_PADDING, THUMB_PADDING, THUMB_PADDING, THUMB_PADDING);
                imageView.setBackgroundColor(R.color.item_border);
                imageView.setScaleType(ImageView.ScaleType.CENTER);
                viewSwitcher.addView(imageView);

                // imageView = new ImageView(context);
                // imageView.setLayoutParams(new GridView.LayoutParams(thumbWidth, thumbWidth));
                // imageView.setBackgroundColor(R.color.item_border);
                // imageView.setPadding(THUMB_PADDING, THUMB_PADDING, THUMB_PADDING, THUMB_PADDING);
                // imageView.setScaleType(ImageView.ScaleType.CENTER);
            }
            else {
                // imageView = (ImageView) convertView;
                viewSwitcher = (ViewSwitcher) convertView;
            }

            final Item item = itemWorker.getItemByPosition(position);

            Integer id = (Integer) viewSwitcher.getTag();
            // int id = (Integer) viewSwitcher.getTag();
            // ThumbViewHolder holder = (ThumbViewHolder) viewSwitcher.getTag();

            // first load or recycled with different thumbnail:
            if (id == null || id != item.getId()) {
                viewSwitcher.setTag(item.getId());
                // ThumbViewHolder holder = new ThumbViewHolder();
                // holder.id = item.getId();
                // viewSwitcher.setTag(holder);

                // ImageView image = (ImageView) viewSwitcher.getChildAt(1);
                viewSwitcher.setDisplayedChild(0); // progress bar
                if (item.getImage() != null && item.getImage().getThumbnail() != null) {
                    final ViewSwitcher finalViewSwitcher = viewSwitcher;
                    thumbnailWorker.queueThumb(item.getId(), "http://ip.apoc.cc:4567" + item.getImage().getThumbnail(), 
                            new ThumbWorker.NewThumbListener() {
                                public void newThumb(Bitmap bitmap) {
                                    Bitmap scaledBitmap = Bitmap.createScaledBitmap(bitmap, thumbWidth - THUMB_PADDING*2, thumbWidth - THUMB_PADDING*2, true);
                                    int tagId = (Integer) finalViewSwitcher.getTag();
                                    if (tagId == item.getId()) {
                                        ImageView image = (ImageView) finalViewSwitcher.getChildAt(1);
                                        image.setImageBitmap(scaledBitmap);
                                        finalViewSwitcher.setDisplayedChild(1);
                                    }
                                }
                            });
                }


                    // thumbnailWorker.queryThumbnail(item.getId(), "http://ip.apoc.cc:4567" + item.getImage().getThumbnail(), viewSwitcher);
            }

            // imageView.setImageBitmap(null);
            // imageView.setTag(item.getId());
            // if (item.getImage() != null && item.getImage().getThumbnail() != null) {
            //     // this queries the set of the bitmap
            //     thumbnailWorker.queryThumbnail(item.getId(), "http://ip.apoc.cc:4567" + item.getImage().getThumbnail(), imageView);
            // }


            // return imageView;


            return viewSwitcher;



            /*
            TextView textView = null;
            if (convertView == null) {
                textView = new TextView(context);
                textView.setLayoutParams(new GridView.LayoutParams(thumbWidth, thumbWidth));
                textView.setBackgroundColor(R.color.item_border);
                textView.setPadding(THUMB_PADDING, THUMB_PADDING, THUMB_PADDING, THUMB_PADDING);
            }
            else {
                textView = (TextView) convertView;
            }

            Item item = itemWorker.getItemByPosition(position);

            textView.setText("View #" + String.valueOf(position) + " zg#" + String.valueOf(item.getId()));

            // create/recycle image view ...
            // this sets the bitmap after its retreived in a seperate thread:
            // thumbnailWorker.queryThumbnail(item.getId(), item.getImage().getThumbnail(), imageView);

            return textView;
            */
        }
    }


}
