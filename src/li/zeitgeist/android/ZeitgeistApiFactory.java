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

import li.zeitgeist.api.ZeitgeistApi;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

public class ZeitgeistApiFactory {
    
    public static ZeitgeistApi createInstance(Context context) {
        SharedPreferences prefs = 
                PreferenceManager.getDefaultSharedPreferences(context);
        String baseUrl = prefs.getString("baseUrl", null);
        String eMail = prefs.getString("eMail", null); 
        String apiSecret =  prefs.getString("apiSecret", null); 

        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }

        return new ZeitgeistApi(baseUrl, eMail, apiSecret);
    }

}
