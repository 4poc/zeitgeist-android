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
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.List;
import java.util.Vector;

import li.zeitgeist.android.R.id;
import li.zeitgeist.android.worker.ItemWorker;
import li.zeitgeist.api.Item;
import li.zeitgeist.api.Item.Type;
import li.zeitgeist.api.ZeitgeistApi;
import li.zeitgeist.api.error.ZeitgeistError;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.AdapterView.OnItemClickListener;

public class CreateItemActivity extends Activity implements OnClickListener {

    /**
     * Standard android logging tag.
     */
    private static final String TAG = ZeitgeistApp.TAG + ":CreateItemActivity";
    
    
    /**
     * Item Worker instance from the service.
     */
    private ItemWorker itemWorker;
    
    private GalleryService boundService;
    
    private boolean isBoundService;

    private ImageView thumbnailView;
    private TextView sourceUrlTitleView;
    private EditText sourceUrlView;
    private AutoCompleteTextView tagsView;
    private CheckBox announceView;
    private Button cancelView;
    private Button shareView;
    
    private File localImage;
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.v(TAG, "onCreate");
        
        // Disable the title bar
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        // Set main layout
        setContentView(R.layout.create_item);
        
        // binds the local gallery service, and get item worker
        doBindService();
        
        thumbnailView = (ImageView) findViewById(R.id.createItemThumbnail);
        sourceUrlTitleView = (TextView) findViewById(R.id.createItemSourceUrlTitle);
        sourceUrlView = (EditText) findViewById(R.id.createItemSourceUrl);
        tagsView = (AutoCompleteTextView) findViewById(R.id.createItemTags);
        announceView = (CheckBox) findViewById(R.id.createItemAnnounce);
        cancelView = (Button) findViewById(R.id.createItemCancel);
        shareView = (Button) findViewById(R.id.createItemShare);
        
        cancelView.setOnClickListener(this);
        shareView.setOnClickListener(this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.v(TAG, "onDestroy");
        
        doUnbindItemService();
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.v(TAG, "onPause");
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.v(TAG, "onResume");
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
            
            // initialize tag autocomplete
            final TagAutoCompleteAdapter autocompleteAdapter = 
                    new TagAutoCompleteAdapter(CreateItemActivity.this, 
                            R.layout.tag_autocomplete_item);
            autocompleteAdapter.setNotifyOnChange(true);
           
            tagsView.addTextChangedListener(new TagAutoCompleteTextWatcher(tagsView, autocompleteAdapter, itemWorker));
            tagsView.setAdapter(autocompleteAdapter);
            tagsView.setOnItemClickListener(new OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> arg0, View itemView, int arg2,
                        long arg3) {
                    TextView tagName = 
                            (TextView) itemView.findViewById(R.id.tagAutocompleteTagName);
                    
                    tagsView.setText(tagName.getText());
                    
                }});
            

            // Get the item object this activity is about:
            Bundle bundle = getIntent().getExtras();
            
            localImage = (File) bundle.get("local_image");
            if (localImage != null) {
                // no source url: (and no filename)
                sourceUrlTitleView.setVisibility(View.GONE);
                sourceUrlView.setVisibility(View.GONE);
                
                // load thumbnail:
                Bitmap localImageThumbnail = createThumbnailBitmap(localImage);
                thumbnailView.setImageBitmap(localImageThumbnail);
                
                //localImage.delete();
            }
            
            // ...
            
            
            
            
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {}
    };

    private Bitmap createThumbnailBitmap(File f){
        try {
            //Decode image size
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(new FileInputStream(f),null,o);

            //The new size we want to scale to
            final int REQUIRED_SIZE=200;

            //Find the correct scale value. It should be the power of 2.
            int scale=1;
            while(o.outWidth/scale/2>=REQUIRED_SIZE && o.outHeight/scale/2>=REQUIRED_SIZE)
                scale*=2;

            //Decode with inSampleSize
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize=scale;
            return BitmapFactory.decodeStream(new FileInputStream(f), null, o2);
        } catch (FileNotFoundException e) {}
        return null;
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
        case R.id.createItemCancel:
            
            break;
        case R.id.createItemShare:
            
            ImageUploadTask task = new ImageUploadTask();
            task.execute(localImage);
            
            //localImage.delete();
            
            
            break;
        }
    }
    
    private class ImageUploadTask extends AsyncTask<File, Integer, List<Item>> {
        
        private ProgressDialog dialog;

        @Override
        protected void onPostExecute(List<Item> result) {
            // TODO Auto-generated method stub
            super.onPostExecute(result);
            //dialog.dismiss();
            dialog.hide();
        }

        @Override
        protected void onPreExecute() {
            // TODO Auto-generated method stub
            super.onPreExecute();
            
            dialog = new ProgressDialog(CreateItemActivity.this);
            dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            dialog.setMessage("Uploading Picture...");
            dialog.setCancelable(false);
            dialog.show();
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            super.onProgressUpdate(values);
            dialog.setProgress((int) (values[0]));
        }

        @Override
        protected List<Item> doInBackground(File... params) {
            File file = params[0];
            // TODO Auto-generated method stub
            
            
            ZeitgeistApi api = ZeitgeistApiFactory.createInstance(CreateItemActivity.this);
            
            List<File> files = new Vector<File>();
            files.add(file);
            final long totalSize = file.length();
            
            try {
                api.createByFiles(files, tagsView.getText().toString(), 
                        announceView.isChecked(), new ZeitgeistApi.OnProgressListener() {
                            @Override
                            public void onProgress(long transferred) {
                                publishProgress((int) ((transferred / (float) totalSize) * 100));
                            }});
            } catch (ZeitgeistError e) {
                e.printStackTrace();
                dialog.hide();
            }
            
            return null;
        }
        
    }
    
}
