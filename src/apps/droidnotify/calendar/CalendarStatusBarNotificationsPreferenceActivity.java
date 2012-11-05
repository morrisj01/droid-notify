package apps.droidnotify.calendar;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;

import apps.droidnotify.R;
import apps.droidnotify.common.Common;
import apps.droidnotify.common.Constants;
import apps.droidnotify.log.Log;

/**
 * This is the "Calendar Status Bar Notifications" applications preference Activity.
 * 
 * @author Camille S�vigny
 */
public class CalendarStatusBarNotificationsPreferenceActivity extends PreferenceActivity implements OnSharedPreferenceChangeListener{
	
	//================================================================================
    // Properties
    //================================================================================

    private Context _context = null;
    private SharedPreferences _preferences = null;
	
	//================================================================================
	// Public Methods
	//================================================================================

	/**
	 * Called when the activity is created. Set up views and buttons.
	 * 
	 * @param bundle - Activity bundle.
	 */
	@SuppressWarnings("deprecation")
	@Override
	protected void onCreate(Bundle bundle){
	    super.onCreate(bundle);
	    _context = getApplicationContext();
	    Common.setApplicationLanguage(_context, this);
	    _preferences = PreferenceManager.getDefaultSharedPreferences(_context);
	    _preferences.registerOnSharedPreferenceChangeListener(this);
	    this.addPreferencesFromResource(R.xml.calendar_status_bar_notifications_preferences);
	    this.setContentView(R.layout.status_bar_notifications_preferences);
	}	
    
	/**
	 * When a SharedPreference is changed this registered function is called.
	 * 
	 * @param sharedPreferences - The Preference object who's key was changed.
	 * @param key - The String value of the preference Key who's preference value was changed.
	 */
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
		if(key.equals(Constants.CALENDAR_STATUS_BAR_NOTIFICATIONS_VIBRATE_SETTING_KEY)){
			updateVibratePreferences();
		}
	}
	
	/**
	 * Updates the vibrate preferences based on the vibrate setting.
	 */
	@SuppressWarnings("deprecation")
	private void updateVibratePreferences(){
		try{
			ListPreference vibratePatternListPreference = (ListPreference) findPreference(Constants.CALENDAR_STATUS_BAR_NOTIFICATIONS_VIBRATE_PATTERN_KEY);
			CheckBoxPreference vibrateInCallCheckBoxPreference = (CheckBoxPreference) findPreference(Constants.CALENDAR_STATUS_BAR_NOTIFICATIONS_IN_CALL_VIBRATE_ENABLED_KEY);
			if(_preferences.getString(Constants.CALENDAR_STATUS_BAR_NOTIFICATIONS_VIBRATE_SETTING_KEY, Constants.STATUS_BAR_NOTIFICATIONS_VIBRATE_DEFAULT).equals(Constants.STATUS_BAR_NOTIFICATIONS_VIBRATE_NEVER_VALUE)){
				if(vibratePatternListPreference != null) vibratePatternListPreference.setEnabled(false);
				if(vibrateInCallCheckBoxPreference != null) vibrateInCallCheckBoxPreference.setEnabled(false);
			}else{
				if(vibratePatternListPreference != null) vibratePatternListPreference.setEnabled(true);
				if(vibrateInCallCheckBoxPreference != null) vibrateInCallCheckBoxPreference.setEnabled(true);
			}
		}catch(Exception ex){
			Log.e(_context, "CalendarStatusBarNotificationsPreferenceActivity.updateVibratePreferences() ERROR: " + ex.toString());
		}
	}
	
}