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
    
    // private static final String androidns="http://schemas.android.com/apk/res/android";

    private Context context;
    
    private int value;
    private int max = 100;
    
    private SeekBar seekBar;
    private TextView textView = null;

    public SeekBarPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.context = context;
        
        // String title = attrs.getAttributeValue(androidns, "title");
    }
    
    @Override 
    protected View onCreateDialogView() {
        super.onCreateDialogView();

        LinearLayout layout = new LinearLayout(context);
        
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(5, 5, 5, 5);
        
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.FILL_PARENT, 
                LinearLayout.LayoutParams.WRAP_CONTENT);

        seekBar = new SeekBar(context);
        
        seekBar.setLayoutParams(params);
        
        seekBar.setMax(max);
        seekBar.setProgress(value);
        
        seekBar.setOnSeekBarChangeListener(this);

        layout.addView(seekBar);

        textView = new TextView(context);
        
        textViewValueUpdate();
        
        textView.setGravity(Gravity.CENTER);
        
        textView.setLayoutParams(params);
        
        layout.addView(textView);
        
        return layout;
    }
    
    private void textViewValueUpdate() {
        if (textView != null) {
            textView.setText(String.valueOf(value+50));
        }
    }

    @Override 
    protected void onBindDialogView(View v) {
        super.onBindDialogView(v);
        seekBar.setMax(max);
        seekBar.setProgress(value);
    }

    @Override
    protected void onSetInitialValue(boolean restore, Object defaultValue) {
        super.onSetInitialValue(restore, defaultValue);
        if (restore) {
            value = shouldPersist() ? getPersistedInt(120) : 120;
        }
        else if (defaultValue != null) {
            value = (Integer) defaultValue;
        }
        else {
            value = 120;
        }

        textViewValueUpdate();
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        value = progress;
        if (shouldPersist()) {
            persistInt(value);
        }
        callChangeListener(new Integer(value));
        textViewValueUpdate();
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
    
}
