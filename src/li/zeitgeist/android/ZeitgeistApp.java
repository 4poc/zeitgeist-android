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

import li.zeitgeist.android.provider.ItemProvider;
import li.zeitgeist.android.provider.ThumbnailProvider;
import li.zeitgeist.api.ZeitgeistApi;

public class ZeitgeistApp extends Application {

    public static final String TAG = "Zeitgeist";
    public static final String BASE_URL = "http://zeitgeist.li"; // for now

    private ItemProvider itemProvider;
    private ThumbnailProvider thumbnailProvider;

    public ZeitgeistApp() {
        super();
    }

    public void onCreate() {
        super.onCreate();
        
        itemProvider = new ItemProvider(new ZeitgeistApi(BASE_URL));
        thumbnailProvider = new ThumbnailProvider(this);
        
        // load the thumbnails of new items
        itemProvider.addUpdatedItemsListener(thumbnailProvider);
        
        itemProvider.start();
    }

    public ItemProvider getItemProvider() {
        return itemProvider;
    }

    public ThumbnailProvider getThumbnailProvider() {
        return thumbnailProvider;
    }
    
}
