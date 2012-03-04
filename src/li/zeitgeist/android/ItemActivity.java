package li.zeitgeist.android;

import li.zeitgeist.android.provider.ItemProvider;
// import li.zeitgeist.android.provider.ThumbnailProvider;
import li.zeitgeist.api.Item;
import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.text.ClipboardManager;
import android.util.Log;
import android.view.*;
import android.view.MenuItem.OnMenuItemClickListener;
import android.widget.*;
import android.webkit.*;

public class ItemActivity extends Activity implements OnMenuItemClickListener {

    private static final String TAG = ZeitgeistApp.TAG + ":ItemActivity";
    
    // private ThumbnailProvider thumbnailProvider;
    private ItemProvider itemProvider;
    
    private Item item;
    
    public ItemActivity() {
        super();
        Log.v(TAG, "constructed");
    }

    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.v(TAG, "onCreate()");
        
        itemProvider = ((ZeitgeistApp)getApplication()).getItemProvider();
        // thumbnailProvider = ((ZeitgeistApp)getApplication()).getThumbnailProvider();

        // Disable the title bar
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        // Set main layout
        setContentView(R.layout.item);
        
        // Get the item object this activity is about:
        Bundle bundle = getIntent().getExtras();
        if (bundle == null) {
            Log.e(TAG, "bundle from intent is null!");
            return;
        }
        item = itemProvider.getItemById(bundle.getInt("id"));
        
        
        // Find and setup the WebView to show the item
        WebView webView = (WebView) findViewById(R.id.webView);
        webView.setBackgroundColor(R.color.item_webview_background);

        WebSettings settings = webView.getSettings();
        settings.setBuiltInZoomControls(true);
        settings.setSupportZoom(true);
        settings.setDefaultZoom(WebSettings.ZoomDensity.FAR);
        
        final ProgressBar progressBar = new ProgressBar(this);
        progressBar.setMax(100);
        progressBar.bringToFront();
        
        webView.setWebChromeClient(new WebChromeClient() {
            public void onProgressChanged(WebView view, int progress) {
                progressBar.setProgress(progress);
                if (progress >= 100) {
                    progressBar.setVisibility(View.INVISIBLE);
                }
            }
        });

        webView.setWebViewClient(new WebViewClient() {
          public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
            new AlertDialog.Builder(view.getContext())
            .setTitle("Oops")
            .setMessage("Error: " + description)
            .setPositiveButton(android.R.string.ok, null)
            .show();
          }
        });
        
        webView.loadUrl(ZeitgeistApp.BASE_URL + item.getImage().getImage());
        
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.item_menu, menu);
        
        menu.findItem(R.id.itemMenuBackItem).setOnMenuItemClickListener(this);
        menu.findItem(R.id.itemMenuCopyUrlItem).setOnMenuItemClickListener(this);
        
        return true;
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.v(TAG, "onDestroy()");
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
    public boolean onMenuItemClick(MenuItem menuItem) {
        switch (menuItem.getItemId()) {
        case R.id.itemMenuBackItem:
            onBackPressed();
            break;
            
        case R.id.itemMenuCopyUrlItem:
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            clipboard.setText(ZeitgeistApp.BASE_URL + item.getImage().getImage());
            Toast.makeText(this, "URL Copied", Toast.LENGTH_SHORT).show();
            break;
        }
        
        
        return true;
    }
}
