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
package li.zeitgeist.android.preference;

import android.content.Context;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

public class SeekBarPreference extends DialogPreference implements SeekBar.OnSeekBarChangeListener {

    private static final String androidns="http://schemas.android.com/apk/res/android";

    private Context context;
    
    private int defaultValue;
    private int value;
    private int min = 0;
    private int max = 200;
    
    private SeekBar seekBar;
    private TextView textView;

    public SeekBarPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.context = context;
        
        defaultValue = attrs.getAttributeIntValue(androidns, "defaultValue", 0);
    }
    
    @Override 
    protected View onCreateDialogView() {
        super.onCreateDialogView();
        
        if (shouldPersist()) {
            value = getPersistedInt(defaultValue);
        }

        LinearLayout layout = new LinearLayout(context);
        
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(5, 5, 5, 5);
        
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.FILL_PARENT, 
                LinearLayout.LayoutParams.WRAP_CONTENT);

        seekBar = new SeekBar(context);
        
        seekBar.setLayoutParams(params);
        
        updateSeekBar();
        
        seekBar.setOnSeekBarChangeListener(this);

        layout.addView(seekBar);

        textView = new TextView(context);
        
        updateTextView();
        
        textView.setGravity(Gravity.CENTER);
        
        textView.setLayoutParams(params);
        
        layout.addView(textView);
        
        return layout;
    }
    
    private void updateTextView() {
        if (textView != null) {
            textView.setText(String.valueOf(value));
        }
    }

    @Override 
    protected void onBindDialogView(View v) {
        super.onBindDialogView(v);
        updateSeekBar();
    }
    
    private void updateSeekBar() {
        seekBar.setMax(max - min);
        seekBar.setProgress(value - min);
    }

    @Override
    protected void onSetInitialValue(boolean restore, Object defaultValueObj) {
        super.onSetInitialValue(restore, defaultValueObj);
        int defaultValue = 42;
        if (defaultValueObj != null) {
            defaultValue = (Integer) defaultValueObj;
        }
        
        if (restore) {
            value = shouldPersist() ? getPersistedInt(defaultValue) : defaultValue;
        }
        else {
            value = defaultValue;
        }

        updateTextView();
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        value = progress + min;
        if (shouldPersist()) {
            persistInt(value);
        }
        callChangeListener(new Integer(value));
        updateTextView();
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {}

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {}
    
    public int getValue() {
        return value;
    }
    
    public int getMax() {
        return max;
    }
    
    public void setMax(int max) {
        this.max = max;
        if (seekBar != null) {
            seekBar.setMax(max);
        }
    }
    
    public void setMin(int min) {
        this.min = min;
    }
    
    
}
