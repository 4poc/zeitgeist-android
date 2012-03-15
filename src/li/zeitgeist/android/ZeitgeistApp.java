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

import android.app.Application;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import li.zeitgeist.android.services.ItemService;
import li.zeitgeist.android.services.ThumbnailProvider;
import li.zeitgeist.api.ZeitgeistApi;

public class ZeitgeistApp extends Application {

    public static final String TAG = "Zeitgeist";

    private ItemService itemProvider;
    private ThumbnailProvider thumbnailProvider;

    public ZeitgeistApp() {
        super();
    }

    public void onCreate() {
        super.onCreate();
        
        PreferenceManager.setDefaultValues(this, R.xml.preference, false);
        
        //itemProvider = new ItemService(getApi());
        thumbnailProvider = new ThumbnailProvider(this, getApi());
        
        // load the thumbnails of new items
        //itemProvider.addUpdatedItemsListener(thumbnailProvider);
    }

    public ItemService getItemProvider() {
        return itemProvider;
    }

    public ThumbnailProvider getThumbnailProvider() {
        return thumbnailProvider;
    }

    public ZeitgeistApi getApi() {
        SharedPreferences prefs = 
            PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        String baseUrl = prefs.getString("baseUrl", "http://zeitgeist.li");
        String eMail = prefs.getString("eMail", ""); 
        String apiSecret =  prefs.getString("apiSecret", ""); 

        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }

        return new ZeitgeistApi(baseUrl, eMail, apiSecret);
    }
    
}
