package li.zeitgeist.android;

import li.zeitgeist.android.preference.SeekBarPreference;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceActivity;

public class SettingsActivity extends PreferenceActivity implements OnPreferenceChangeListener {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preference);
        
        
        SeekBarPreference thumbnailPreference = 
                (SeekBarPreference) findPreference("thumbnailSize");
        
        // TODO: don't hardcode this:
        thumbnailPreference.setDefaultValue(120);
        thumbnailPreference.setMax(200);
        
        thumbnailPreference.setOnPreferenceChangeListener(this);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        // TODO Auto-generated method stub
        
        if (preference.getKey().equals("thumbnailSize")) {
        }
        
        return false;
    }
}
