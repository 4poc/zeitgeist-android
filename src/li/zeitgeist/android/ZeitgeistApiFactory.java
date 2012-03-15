package li.zeitgeist.android;

import li.zeitgeist.api.ZeitgeistApi;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

public class ZeitgeistApiFactory {
    
    public static ZeitgeistApi createInstance(Context context) {
        SharedPreferences prefs = 
                PreferenceManager.getDefaultSharedPreferences(context);
        String baseUrl = prefs.getString("baseUrl", "http://zeitgeist.li");
        String eMail = prefs.getString("eMail", ""); 
        String apiSecret =  prefs.getString("apiSecret", ""); 

        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }

        return new ZeitgeistApi(baseUrl, eMail, apiSecret);
    }

}
