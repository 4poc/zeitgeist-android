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

import li.zeitgeist.android.preference.SeekBarPreference;
import li.zeitgeist.api.ZeitgeistApi;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;

import com.google.zxing.integration.android.*;

public class SettingsActivity extends PreferenceActivity implements OnPreferenceChangeListener, OnPreferenceClickListener {
    /**
     * Standard android logging tag.
     */
    private static final String TAG = ZeitgeistApp.TAG + ":CreateItemActivity";
    
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        IntentResult scanResult = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (scanResult != null) {
            String qrcode = scanResult.getContents();
            Log.v(TAG, "qrcode scan success: " + qrcode);
            
            // parse qrcode into url, email and secret:
            int auth_token = qrcode.indexOf("#auth:");
            if (auth_token != -1) {
                String baseUrl = qrcode.substring(0, auth_token);
                String eMail = qrcode.substring(auth_token + 6, qrcode.indexOf('|'));
                String apiSecret = qrcode.substring(qrcode.indexOf('|') + 1);
                
                Log.v(TAG, "qrcode baseUrl: " + baseUrl);
                Log.v(TAG, "qrcode eMail: " + eMail);
                // Log.v(TAG, "qrcode apiSecret: " + apiSecret);
                
                ZeitgeistApi api = ZeitgeistApiFactory.createInstance(this);
                if (api.testAuth(baseUrl, eMail, apiSecret)) {
                    SharedPreferences prefs = 
                            PreferenceManager.getDefaultSharedPreferences(this);
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putString("baseUrl", baseUrl);
                    editor.putString("eMail", eMail);
                    editor.putString("apiSecret", apiSecret);
                    editor.commit();
                    // TODO: should do: itemWorker.resetApiInstance();
                    Log.v(TAG, "Successfully changed baseUrl, eMail and apiSecret!");
                }
                else {
                    Log.e(TAG, "Scanned credentials are incorrect.");
                }
            }
            else {
                Log.e(TAG, "QR Code with invalid token scanned: " + qrcode);
            }
            
            
    
    
    
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preference);
        
        SeekBarPreference thumbnailPreference = 
                (SeekBarPreference) findPreference("thumbnailSize");
        
        // TODO: don't hardcode this:
        thumbnailPreference.setMin(50);
        thumbnailPreference.setMax(200);
        
        thumbnailPreference.setOnPreferenceChangeListener(this);
        
        Preference scanForApiSecret = findPreference("scanForApiSecret");
        scanForApiSecret.setOnPreferenceClickListener(this);
        
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        // TODO Auto-generated method stub
        
        if (preference.getKey().equals("thumbnailSize")) {
        }
        
        return false;
    }

    @Override
    public boolean onPreferenceClick(Preference arg0) {
        IntentIntegrator integrator = new IntentIntegrator(this);
        integrator.initiateScan();
        return false;
    }
}
