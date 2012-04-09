package li.zeitgeist.android;

import java.util.List;

import li.zeitgeist.android.worker.ItemWorker;
import li.zeitgeist.android.worker.ItemWorker.ItemTagSearchListener;
import li.zeitgeist.api.Tag;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.widget.AutoCompleteTextView;

public class TagAutoCompleteTextWatcher implements TextWatcher {
    
    private static final String TAG = ZeitgeistApp.TAG + ":TagAutoCompleteTextWatcher";

    private AutoCompleteTextView textView;
    private final TagAutoCompleteAdapter adapter;
    private ItemWorker itemWorker;
 
    public TagAutoCompleteTextWatcher(AutoCompleteTextView autoCompleteTextView, 
            TagAutoCompleteAdapter adapter, ItemWorker itemWorker) {
        this.textView = autoCompleteTextView;
        this.adapter = adapter;
        this.itemWorker = itemWorker;
    }

    @Override
    public void afterTextChanged(Editable s) {}

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count,
            int after) {}

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
        if(s.length() < textView.getThreshold()) return;
        if(textView.isPerformingCompletion()) return;
 
        Log.v(TAG, "onTextChanged: " + s.toString());
         // Do REST API calls here
         // Fill Adapter with new data
        
        final String query = s.toString();
        itemWorker.searchItemTags(query, new ItemTagSearchListener() {

            @Override
            public void onItemTagSearchResult(List<Tag> tags) {
                adapter.updateTags(tags);
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, error);
            }});
        
    }
    
    
    
}
