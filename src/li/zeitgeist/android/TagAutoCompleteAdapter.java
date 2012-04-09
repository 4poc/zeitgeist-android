package li.zeitgeist.android;

import java.util.List;
import java.util.Vector;

import li.zeitgeist.api.Item;
import li.zeitgeist.api.Tag;
import android.content.Context;
import android.database.DataSetObserver;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Adapter;
import android.widget.ArrayAdapter;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.TextView;

public class TagAutoCompleteAdapter extends ArrayAdapter<Tag> implements Filterable {
    
    private static final String TAG = ZeitgeistApp.TAG + ":TagAutoCompleteAdapter";

    private int layoutResourceId;
    
    private List<Tag> tags;
    
    private Handler handler;
    
    private LayoutInflater inflater;
    
    public TagAutoCompleteAdapter(Context context, int textViewResourceId) {
        super(context, textViewResourceId);
        this.layoutResourceId = textViewResourceId;
        
        tags = new Vector<Tag>();
        
        handler = new Handler();
        
        inflater = LayoutInflater.from(context);
    }
    
    private Filter filter = new Filter() {
        
        @Override
        protected FilterResults performFiltering(CharSequence constraint) {
            FilterResults filterResults = new FilterResults();
 
            if (constraint != null) {
                Log.v(TAG, "performFiltering: " + constraint.toString());
                
                // filter adapter datastructure
                /*for (Tag tag : tags) {
                    if (!tag.getName().contains(constraint)) {
                        tags.remove(tag);
                    }
                }*/
                
                filterResults.count = getCount();
            }
            
 
            return filterResults;
        }
 
        @Override
        protected void publishResults(CharSequence contraint, FilterResults results) {
            if (results != null && results.count > 0) {
                notifyDataSetChanged();
            }
        }
    };

    @Override
    public Filter getFilter() {
        return filter;
    }
    
    static class ViewHolder {
        TextView tagName;
        TextView tagCount;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if(convertView == null) {
            convertView = inflater.inflate(layoutResourceId, parent, false);
            
            holder = new ViewHolder();
            holder.tagName = (TextView) convertView.findViewById(R.id.tagAutocompleteTagName);
            holder.tagCount = (TextView) convertView.findViewById(R.id.tagAutocompleteTagCount);
            convertView.setTag(holder);
        }
        else {
            holder = (ViewHolder) convertView.getTag();
        }
        
        Tag tag = getItem(position);
        
        if (tag != null) {
            holder.tagName.setText(tag.getName());
            holder.tagCount.setText(String.valueOf(tag.getCount()));
        }
        
        return convertView;
    }

    @Override
    public int getCount() {
        return tags.size();
    }

    @Override
    public Tag getItem(int position) {
        return tags.get(position);
    }

    @Override
    public long getItemId(int position) {
        return getItem(position).getId();
    }

    public void updateTags(final List<Tag> newTags) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                // TODO Auto-generated method stub
                tags = newTags;
                notifyDataSetChanged();
            }});
    }
}
