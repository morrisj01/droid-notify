package apps.droidnotify.services;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import apps.droidnotify.common.Common;
import apps.droidnotify.log.Log;

/**
 * This class handles scheduled Calendar Event notifications that we want to display.
 * 
 * @author Camille S�vigny
 */
public class CalendarService extends WakefulIntentService {
	
	//================================================================================
    // Properties
    //================================================================================

	private boolean _debug = false;

	//================================================================================
	// Public Methods
	//================================================================================
	
	/**
	 * Class Constructor.
	 */
	public CalendarService() {
		super("CalendarService");
		_debug = Log.getDebug();
		if (_debug) Log.v("CalendarService.CalendarService()");
	}

	//================================================================================
	// Protected Methods
	//================================================================================
	
	/**
	 * Display the notification for this Calendar Event.
	 * 
	 * @param intent - Intent object that we are working with.
	 */
	@Override
	protected void doWakefulWork(Intent intent) {
		if (_debug) Log.v("CalendarService.doWakefulWork()");
		try{
			Context context = getApplicationContext();
	    	Bundle bundle = intent.getExtras();
	    	Common.startNotificationActivity(context, bundle);
		}catch(Exception ex){
			Log.e("CalendarService.doWakefulWork() ERROR: " + ex.toString());
		}
	}
	
}