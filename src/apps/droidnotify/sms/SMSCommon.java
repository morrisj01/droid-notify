package apps.droidnotify.sms;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.telephony.SmsMessage.MessageClass;
import android.widget.Toast;

import apps.droidnotify.NotificationActivity;
import apps.droidnotify.R;
import apps.droidnotify.common.Common;
import apps.droidnotify.common.Constants;
import apps.droidnotify.contacts.ContactsCommon;
import apps.droidnotify.email.EmailCommon;
import apps.droidnotify.log.Log;
import apps.droidnotify.phone.PhoneCommon;

/**
 * This class is a collection of SMS/MMS methods.
 * 
 * @author Camille S�vigny
 */
public class SMSCommon {

	//================================================================================
    // Properties
    //================================================================================
	
	private static boolean _debug = false;
	private static Context _context= null;
	
	//================================================================================
	// Public Methods
	//================================================================================
	
	/**
	 * Parse the incoming SMS message directly.
	 * 
	 * @param context - The application context.
	 * @param bundle - Bundle from the incoming intent.
	 * 
	 * @return Bundle - Returns a Bundle that contain the sms notification information.
	 */
	public static Bundle getSMSMessagesFromIntent(Context context, Bundle bundle){
		_debug = Log.getDebug();
		if(_debug) Log.v("SMSCommon.getSMSMessagesFromIntent()");
		try{
			SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
			Bundle smsNotificationBundle = new Bundle();
			int bundleCount = 0;
	    	long timeStamp = 0;
	    	String sentFromAddress = null;
	    	String messageBody = null;
	    	StringBuilder messageBodyBuilder = null;
	    	String messageSubject = null;
	    	long threadID = -1;
	    	long messageID = -1;
    		Bundle smsNotificationBundleSingle = new Bundle();
    		bundleCount++;
			SmsMessage[] msgs = null;
            Object[] pdus = (Object[]) bundle.get("pdus");
            msgs = new SmsMessage[pdus.length];
            for (int i=0; i<msgs.length; i++){
                msgs[i] = SmsMessage.createFromPdu((byte[])pdus[i]);                
            }
            SmsMessage sms = msgs[0];
            //Handle Flash SMS AKA Class 0 Messages
            MessageClass messageClass = sms.getMessageClass();
            if(_debug) Log.v("SMSCommon.getSMSMessagesFromIntent() MessageClass: " + messageClass);
            if(messageClass == MessageClass.CLASS_0){
            	if(preferences.getBoolean(Constants.SMS_IGNORE_CLASS_0_MESSAGES_KEY, false)){
            		return null;
            	}
            }
            timeStamp = sms.getTimestampMillis();
            //Adjust the timestamp to the localized time of the users phone.
            timeStamp = Common.convertGMTToLocalTime(context, timeStamp, preferences.getBoolean(Constants.SMS_TIME_IS_UTC_KEY, false));
    		//long timeStampAdjustment = Long.parseLong(preferences.getString(Constants.SMS_TIMESTAMP_ADJUSTMENT_KEY, "0")) * 60 * 60 * 1000;
    		//timeStamp = timeStamp + timeStampAdjustment;
            sentFromAddress = sms.getDisplayOriginatingAddress().toLowerCase();
            sentFromAddress = sentFromAddress.contains("@") ? EmailCommon.removeEmailFormatting(sentFromAddress) : PhoneCommon.removePhoneNumberFormatting(sentFromAddress);
            messageSubject = sms.getPseudoSubject();
            messageBodyBuilder = new StringBuilder();
            //Get the entire message body from the new message.
    		int messagesLength = msgs.length;
            for (int i = 0; i < messagesLength; i++){                
            	//messageBody.append(msgs[i].getMessageBody().toString());
            	messageBodyBuilder.append(msgs[i].getDisplayMessageBody().toString());
            }   
            messageBody = messageBodyBuilder.toString();
            if(messageBody.startsWith(sentFromAddress)){
            	messageBody = messageBody.substring(sentFromAddress.length()).replace("\n", "<br/>").trim();
            }
            if(messageSubject != null && !messageSubject.equals("")){
				messageBody = "<b>" + messageSubject + "</b><br/>" + messageBody.replace("\n", "<br/>").trim();
			}else{
				messageBody = messageBody.replace("\n", "<br/>").trim();
			}
    		threadID = getThreadID(context, sentFromAddress, Constants.NOTIFICATION_TYPE_SMS);
    		messageID = getMessageID(context, sentFromAddress, threadID, messageBody, timeStamp);
    		Bundle smsContactInfoBundle = sentFromAddress.contains("@") ? ContactsCommon.getContactsInfoByEmail(context, sentFromAddress) : ContactsCommon.getContactsInfoByPhoneNumber(context, sentFromAddress);
			long contactID = -1;				
			//Basic Notification Information.
			smsNotificationBundleSingle.putString(Constants.BUNDLE_SENT_FROM_ADDRESS, sentFromAddress);
			smsNotificationBundleSingle.putString(Constants.BUNDLE_MESSAGE_BODY, messageBody);
			smsNotificationBundleSingle.putLong(Constants.BUNDLE_MESSAGE_ID, messageID);
			smsNotificationBundleSingle.putLong(Constants.BUNDLE_THREAD_ID,threadID);
			smsNotificationBundleSingle.putLong(Constants.BUNDLE_TIMESTAMP, timeStamp);
			smsNotificationBundleSingle.putInt(Constants.BUNDLE_NOTIFICATION_TYPE, Constants.NOTIFICATION_TYPE_SMS);
			if(smsContactInfoBundle != null){
    			//Contact Information.
				contactID = smsContactInfoBundle.getLong(Constants.BUNDLE_CONTACT_ID, -1);
				smsNotificationBundleSingle.putLong(Constants.BUNDLE_CONTACT_ID, contactID);
				smsNotificationBundleSingle.putString(Constants.BUNDLE_CONTACT_NAME, smsContactInfoBundle.getString(Constants.BUNDLE_CONTACT_NAME));
				smsNotificationBundleSingle.putLong(Constants.BUNDLE_PHOTO_ID, smsContactInfoBundle.getLong(Constants.BUNDLE_PHOTO_ID, -1));
				smsNotificationBundleSingle.putString(Constants.BUNDLE_LOOKUP_KEY, smsContactInfoBundle.getString(Constants.BUNDLE_LOOKUP_KEY));
			}
			smsNotificationBundle.putBundle(Constants.BUNDLE_NOTIFICATION_BUNDLE_NAME + "_" + String.valueOf(bundleCount), smsNotificationBundleSingle);
		    smsNotificationBundle.putInt(Constants.BUNDLE_NOTIFICATION_BUNDLE_COUNT, bundleCount);
    		return smsNotificationBundle;
		}catch(Exception ex){
			Log.e("SMSCommon.getSMSMessagesFromIntent() ERROR: " + ex.toString());
			return null;
		}
	}

	/**
	 * Get all unread SMS messages and load them.
	 * 
	 * @param context - The application context.
	 */
	public static Bundle getAllUnreadSMSMessages(Context context){
		if(_debug) Log.v("SMSCommon.getAllUnreadSMSMessages()" );
		Bundle smsNotificationBundle = new Bundle();
		Cursor cursor = null;
        try{
    		int bundleCount = 0;
    		final String[] projection = new String[] { "_id", "thread_id", "body", "address", "date"};
    		final String selection = "read=?";
    		final String[] selectionArgs = new String[] {"0"};
    		final String sortOrder = "date DESC";
		    cursor = context.getContentResolver().query(
		    		Uri.parse("content://sms/inbox"),
		    		projection,
		    		selection,
					selectionArgs,
					sortOrder);
		    if(cursor == null){
		    	if(_debug) Log.v("SMSCommon.getAllUnreadSMSMessages() Currsor is null. Exiting...");
		    	return null;
		    }
    		boolean isFirst = true;
		    while(cursor.moveToNext()){ 
	    		Bundle smsNotificationBundleSingle = new Bundle();
		    	long messageID = cursor.getLong(cursor.getColumnIndex("_id"));
		    	long threadID = cursor.getLong(cursor.getColumnIndex("thread_id"));
		    	String messageBody = cursor.getString(cursor.getColumnIndex("body"));
		    	String sentFromAddress = cursor.getString(cursor.getColumnIndex("address"));
		    	sentFromAddress = sentFromAddress.contains("@") ? EmailCommon.removeEmailFormatting(sentFromAddress) : PhoneCommon.removePhoneNumberFormatting(sentFromAddress);
		    	long timeStamp = cursor.getLong(cursor.getColumnIndex("date"));
		    	timeStamp = Common.convertGMTToLocalTime(context, timeStamp, true);
	    		if(!isFirst){
    	    		bundleCount++;
                	Bundle smsContactInfoBundle = sentFromAddress.contains("@") ? ContactsCommon.getContactsInfoByEmail(context, sentFromAddress) : ContactsCommon.getContactsInfoByPhoneNumber(context, sentFromAddress);
                	long contactID = -1;				
					//Basic Notification Information.
					smsNotificationBundleSingle.putString(Constants.BUNDLE_SENT_FROM_ADDRESS, sentFromAddress);
					smsNotificationBundleSingle.putString(Constants.BUNDLE_MESSAGE_BODY, messageBody.replace("\n", "<br/>"));
					smsNotificationBundleSingle.putLong(Constants.BUNDLE_MESSAGE_ID, messageID);
					smsNotificationBundleSingle.putLong(Constants.BUNDLE_THREAD_ID,threadID);
					smsNotificationBundleSingle.putLong(Constants.BUNDLE_TIMESTAMP, timeStamp);
					smsNotificationBundleSingle.putInt(Constants.BUNDLE_NOTIFICATION_TYPE, Constants.NOTIFICATION_TYPE_SMS);
        			if(smsContactInfoBundle != null){
    	    			//Contact Information.
    					contactID = smsContactInfoBundle.getLong(Constants.BUNDLE_CONTACT_ID, -1);
    					smsNotificationBundleSingle.putLong(Constants.BUNDLE_CONTACT_ID, contactID);
    					smsNotificationBundleSingle.putString(Constants.BUNDLE_CONTACT_NAME, smsContactInfoBundle.getString(Constants.BUNDLE_CONTACT_NAME));
    					smsNotificationBundleSingle.putLong(Constants.BUNDLE_PHOTO_ID, smsContactInfoBundle.getLong(Constants.BUNDLE_PHOTO_ID, -1));
    					smsNotificationBundleSingle.putString(Constants.BUNDLE_LOOKUP_KEY, smsContactInfoBundle.getString(Constants.BUNDLE_LOOKUP_KEY));
                    }
        			smsNotificationBundle.putBundle(Constants.BUNDLE_NOTIFICATION_BUNDLE_NAME + "_" + String.valueOf(bundleCount), smsNotificationBundleSingle);
		    	}
				isFirst = false;
	    	}
			cursor.close();
		    smsNotificationBundle.putInt(Constants.BUNDLE_NOTIFICATION_BUNDLE_COUNT, bundleCount);
		}catch(Exception ex){
			Log.e("SMSCommon.getAllUnreadSMSMessages() ERROR: " + ex.toString());
    		if(cursor != null){
				cursor.close();
			}
			return null;
		}
		return smsNotificationBundle;
	}
	
	/**
	 * Query the mms inbox and check for any new messages.
	 * 
	 * @param context - The application context.
	 * 
	 * @return Bundle - Returns a Bundle that contain the mms notification information.
	 */
	public static Bundle getMMSMessagesFromDisk(Context context){
		_debug = Log.getDebug();
		if(_debug) Log.v("SMSCommon.getMMSMessagesFromDisk()");
		Bundle mmsNotificationBundle = new Bundle();
		Cursor cursor = null;
        try{
    		int bundleCount = 0;
    		final String[] projection = new String[] {"_id", "thread_id", "date"};
    		final String selection = "read=?";
    		final String[] selectionArgs = new String[] {"0"};
    		final String sortOrder = "date DESC";
		    cursor = context.getContentResolver().query(
		    		Uri.parse("content://mms/inbox"),
		    		projection,
		    		selection,
					selectionArgs,
					sortOrder);
		    if(cursor == null){
		    	if(_debug) Log.v("SMSCommon.getMMSMessagesFromDisk() Currsor is null. Exiting...");
		    	return null;
		    }
	    	while(cursor.moveToNext()){
	    		Bundle mmsNotificationBundleSingle = new Bundle();
	    		bundleCount++;	
	    		long messageID = cursor.getLong(cursor.getColumnIndex("_id"));
	    		long threadID = cursor.getLong(cursor.getColumnIndex("thread_id"));
	    		long timeStamp = cursor.getLong(cursor.getColumnIndex("date")) * 1000;
		    	timeStamp = Common.convertGMTToLocalTime(context, timeStamp, true);
		    	String sentFromAddress = getMMSAddress(context, messageID);
		    	sentFromAddress = sentFromAddress.contains("@") ? EmailCommon.removeEmailFormatting(sentFromAddress) : PhoneCommon.removePhoneNumberFormatting(sentFromAddress);
		    	String messageBody = getMMSText(context, messageID);
		    	Bundle mmsContactInfoBundle = sentFromAddress.contains("@") ? ContactsCommon.getContactsInfoByEmail(context, sentFromAddress) : ContactsCommon.getContactsInfoByPhoneNumber(context, sentFromAddress);
		    	long contactID = -1;
			    if(_debug) Log.v("SMSCommon.getMMSMessagesFromDisk() MMS Message Found! - MessageID: " + messageID);
				//Basic Notification Information.
				mmsNotificationBundleSingle.putString(Constants.BUNDLE_SENT_FROM_ADDRESS, sentFromAddress);
				mmsNotificationBundleSingle.putString(Constants.BUNDLE_MESSAGE_BODY, messageBody.replace("\n", "<br/>"));
				mmsNotificationBundleSingle.putLong(Constants.BUNDLE_MESSAGE_ID, messageID);
				mmsNotificationBundleSingle.putLong(Constants.BUNDLE_THREAD_ID, threadID);
				mmsNotificationBundleSingle.putLong(Constants.BUNDLE_TIMESTAMP, timeStamp);
				mmsNotificationBundleSingle.putInt(Constants.BUNDLE_NOTIFICATION_TYPE, Constants.NOTIFICATION_TYPE_MMS);
    			if(mmsContactInfoBundle != null){
	    			//Contact Information.
					contactID = mmsContactInfoBundle.getLong(Constants.BUNDLE_CONTACT_ID, -1);
					mmsNotificationBundleSingle.putLong(Constants.BUNDLE_CONTACT_ID, contactID);
					mmsNotificationBundleSingle.putString(Constants.BUNDLE_CONTACT_NAME, mmsContactInfoBundle.getString(Constants.BUNDLE_CONTACT_NAME));
					mmsNotificationBundleSingle.putLong(Constants.BUNDLE_PHOTO_ID, mmsContactInfoBundle.getLong(Constants.BUNDLE_PHOTO_ID, -1));
					mmsNotificationBundleSingle.putString(Constants.BUNDLE_LOOKUP_KEY, mmsContactInfoBundle.getString(Constants.BUNDLE_LOOKUP_KEY));
				}
    			mmsNotificationBundle.putBundle(Constants.BUNDLE_NOTIFICATION_BUNDLE_NAME + "_" + String.valueOf(bundleCount), mmsNotificationBundleSingle);
		    	break;
		    }
			cursor.close();
		    mmsNotificationBundle.putInt(Constants.BUNDLE_NOTIFICATION_BUNDLE_COUNT, bundleCount);
		}catch(Exception ex){
			Log.e("SMSCommon.getMMSMessagesFromDisk() ERROR: " + ex.toString());
    		if(cursor != null){
				cursor.close();
			}
			return null;
		}
		return mmsNotificationBundle;	
	}
	
	/**
	 * Get all unread MMS messages and load them.
	 * 
	 * @param context - The application context.
	 */
	public static Bundle getAllUnreadMMSMessages(Context context){
		if(_debug) Log.v("SMSCommon.getAllUnreadMMSMessages()");
		Bundle mmsNotificationBundle = new Bundle();
		Cursor cursor = null;
        try{
    		int bundleCount = 0;
        	final String[] projection = new String[] {"_id", "thread_id", "date"};
    		final String selection = "read=?";
    		final String[] selectionArgs = new String[] {"0"};
    		final String sortOrder = "date DESC";
		    cursor = context.getContentResolver().query(
		    		Uri.parse("content://mms/inbox"),
		    		projection,
		    		selection,
					selectionArgs,
					sortOrder);
		    if(cursor == null){
		    	if(_debug) Log.v("SMSCommon.getAllUnreadMMSMessages() Currsor is null. Exiting...");
		    	return null;
		    }
			boolean isFirst = true;
	    	while(cursor.moveToNext()){
	    		//Do not grab the first unread MMS message.
	    		if(!isFirst){
		    		Bundle mmsNotificationBundleSingle = new Bundle();
		    		long messageID = cursor.getLong(cursor.getColumnIndex("_id"));
		    		long threadID = cursor.getLong(cursor.getColumnIndex("thread_id"));
			    	String messageBody = SMSCommon.getMMSText(context, messageID);
			    	String sentFromAddress = SMSCommon.getMMSAddress(context, messageID);
			    	sentFromAddress = sentFromAddress.contains("@") ? EmailCommon.removeEmailFormatting(sentFromAddress) : PhoneCommon.removePhoneNumberFormatting(sentFromAddress);
		    		long timeStamp = cursor.getLong(cursor.getColumnIndex("date")) * 1000;
			    	timeStamp = Common.convertGMTToLocalTime(context, timeStamp, true);
		    		bundleCount++;
			    	Bundle mmsContactInfoBundle = sentFromAddress.contains("@") ? ContactsCommon.getContactsInfoByEmail(context, sentFromAddress) : ContactsCommon.getContactsInfoByPhoneNumber(context, sentFromAddress);
			    	long contactID = -1;				
					//Basic Notification Information.
					mmsNotificationBundleSingle.putString(Constants.BUNDLE_SENT_FROM_ADDRESS, sentFromAddress);
					mmsNotificationBundleSingle.putString(Constants.BUNDLE_MESSAGE_BODY, messageBody.replace("\n", "<br/>"));
					mmsNotificationBundleSingle.putLong(Constants.BUNDLE_MESSAGE_ID, messageID);
					mmsNotificationBundleSingle.putLong(Constants.BUNDLE_THREAD_ID, threadID);
					mmsNotificationBundleSingle.putLong(Constants.BUNDLE_TIMESTAMP, timeStamp);
					mmsNotificationBundleSingle.putInt(Constants.BUNDLE_NOTIFICATION_TYPE, Constants.NOTIFICATION_TYPE_MMS);
	    			if(mmsContactInfoBundle != null){
		    			//Contact Information.
						contactID = mmsContactInfoBundle.getLong(Constants.BUNDLE_CONTACT_ID, -1);
						mmsNotificationBundleSingle.putLong(Constants.BUNDLE_CONTACT_ID, contactID);
						mmsNotificationBundleSingle.putString(Constants.BUNDLE_CONTACT_NAME, mmsContactInfoBundle.getString(Constants.BUNDLE_CONTACT_NAME));
						mmsNotificationBundleSingle.putLong(Constants.BUNDLE_PHOTO_ID, mmsContactInfoBundle.getLong(Constants.BUNDLE_PHOTO_ID, -1));
						mmsNotificationBundleSingle.putString(Constants.BUNDLE_LOOKUP_KEY, mmsContactInfoBundle.getString(Constants.BUNDLE_LOOKUP_KEY));
					}
	    			mmsNotificationBundle.putBundle(Constants.BUNDLE_NOTIFICATION_BUNDLE_NAME + "_" + String.valueOf(bundleCount), mmsNotificationBundleSingle);
		    	}
				isFirst = false;
	    	}
			cursor.close();
		    mmsNotificationBundle.putInt(Constants.BUNDLE_NOTIFICATION_BUNDLE_COUNT, bundleCount);
		}catch(Exception ex){
			Log.e("SMSCommon.getAllUnreadMMSMessages() ERROR: " + ex.toString());
    		if(cursor != null){
				cursor.close();
			}
			return null;
		}
		return mmsNotificationBundle;
	}
	
//	/**
//	 * Load the SMS/MMS thread id for this notification.
//	 * 
//	 * @param context - Application Context.
//	 * @param phoneNumber - Notifications's phone number.
//	 */
//	public static long getThreadID(Context context, String address, int messageType){
//		_debug = Log.getDebug();
//		if(_debug) Log.v("SMSCommon.getThreadID()");
//		address = address.contains("@") ? EmailCommon.removeEmailFormatting(address) : PhoneCommon.removePhoneNumberFormatting(address);
//		String messageURI = "content://sms/inbox";
//		long threadID = -1;
//		if(address == null|| address.equals("")){
//			if(_debug) Log.v("SMSCommon.getThreadID() Address provided is null or empty. Exiting...");
//			return -1;
//		}
//		try{
//			final String[] projection = new String[] { "_id", "thread_id" };
//			final String selection = "address=" + DatabaseUtils.sqlEscapeString(address);
//			final String[] selectionArgs = null;
//			final String sortOrder = null;
//			Cursor cursor = null;
//			try{
//		    	cursor = context.getContentResolver().query(
//		    		Uri.parse(messageURI),
//		    		projection,
//		    		selection,
//					selectionArgs,
//					sortOrder);
//		    	if(cursor != null){
//		    		if(cursor.moveToFirst()){
//		    			threadID = cursor.getLong(cursor.getColumnIndex("thread_id"));
//		    			if(_debug) Log.v("SMSCommon.getThreadID() Thread ID Found. THREAD_ID =  " + threadID);
//		    		}
//		    	}else{
//			    	if(_debug) Log.v("SMSCommon.getThreadID() Currsor is null. Exiting...");
//			    	return -1;
//		    	}
//				cursor.close();
//	    	}catch(Exception e){
//		    	Log.e("SMSCommon.getThreadID() EXCEPTION: " + e.toString());
//	    		if(cursor != null){
//					cursor.close();
//				}
//		    	return -1;
//	    	}
//		    if(threadID < 0){
//		    	if(_debug) Log.v("SMSCommon.getThreadID() Thread ID NOT Found: ADDRESS = " + address + " MESSAGE_TYPE = " + messageType);
//		    }
//	    	return threadID;
//		}catch(Exception ex){
//			Log.e("SMSCommon.getThreadID() ERROR: " + ex.toString());
//			return -1;
//		}
//	}	
	
	/**
	 * Get the SMS/MMS Thread ID for this notification.
	 * 
	 * @param context - Application context.
	 * @param address - SMS/MMS sent from address.
	 * @param messageType - Message type (SMS/MMS);
	 * 
	 * @return long - Returns the Thread ID of this SMS/MMS message.
	 */
	public static long getThreadID(Context context, String address, int messageType){
		_debug = Log.getDebug();
		if(_debug) Log.v("SMSCommon.getThreadID()");
		address = address.contains("@") ? EmailCommon.removeEmailFormatting(address) : PhoneCommon.removePhoneNumberFormatting(address);
		if(address == null || address.equals("")){
			if(_debug) Log.v("SMSCommon.getThreadID() Address provided is null or empty. Exiting...");
			return -1;
		}
		long threadID = -1;
		Cursor cursor = null;
		try{
			final String[] projection = new String[] { "_id", "thread_id" };
			//final String selection = "address=" + DatabaseUtils.sqlEscapeString(address);
			final String selection = "address=?";
			final String[] selectionArgs = new String[]{address};
			final String sortOrder = null;
	    	cursor = context.getContentResolver().query(
	    		Uri.parse("content://sms/inbox"),
	    		projection,
	    		selection,
				selectionArgs,
				sortOrder);
	    	if(cursor.moveToFirst()){
	   			threadID = cursor.getLong(cursor.getColumnIndex("thread_id"));
	   			if(_debug) Log.v("SMSCommon.getThreadID() Thread ID Found: " + threadID);
	    	}else{
		    	if(_debug) Log.v("SMSCommon.getThreadID() Currsor is null. Exiting...");
		    	return -1;
	    	}
			cursor.close();
			return threadID;
	   	}catch(Exception e){
		    	Log.e("SMSCommon.getThreadID() ERROR: " + e.toString());
	   		if(cursor != null){
					cursor.close();
				}
		    	return -1;
	   	}
	}
	
//	/**
//	 * Load the SMS/MMS message id for this notification.
//	 * 
//	 * @param context - Application Context.
//	 * @param threadId - Notifications's threadID.
//	 * @param timestamp - Notifications's timeStamp.
//	 */
//	public static long getMessageID(Context context, long threadID, String messageBody, long timeStamp, int messageType){
//		_debug = Log.getDebug();
//		if(_debug) Log.v("SMSCommon.getMessageID()");
//		String messageURI = "content://sms/inbox";
//		if(messageBody == null){
//			if(_debug) Log.v("SMSCommon.getMessageID() Message body provided is null. Exiting...");
//			return -1;
//		} 
//		long messageID = -1;
//		try{
//			final String[] projection = new String[] {"_id", "body"};
//			final String selection;
//			final String[] selectionArgs;
//			if(threadID < 0){
//				selection = null;
//				selectionArgs = null;
//			}else{
//				selection = "thread_id=?";
//				selectionArgs = new String[]{String.valueOf(threadID)};
//			}
//			final String sortOrder = null;
//		    Cursor cursor = null;
//		    try{
//		    	cursor = context.getContentResolver().query(
//		    		Uri.parse(messageURI),
//		    		projection,
//		    		selection,
//					selectionArgs,
//					sortOrder);
//			    if(cursor == null){
//			    	if(_debug) Log.v("SMSCommon.getMessageID() Currsor is null. Exiting...");
//			    	return -1;
//			    }
//			    while(cursor.moveToNext()){ 
//		    		if(cursor.getString(cursor.getColumnIndex("body")).replace("\n", "<br/>").trim().equals(messageBody)){
//		    			messageID = cursor.getLong(cursor.getColumnIndex("_id"));
//		    			if(_debug) Log.v("SMSCommon.getMessageID() Message ID Found. MESSAGE_ID = " + messageID);
//		    			break;
//		    		}
//			    }
//				cursor.close();
//		    }catch(Exception ex){
//				Log.e("SMSCommon.getMessageID() ERROR: " + ex.toString());
//				if(cursor != null){
//					cursor.close();
//				}
//				return -1;
//			}
//		    if(messageID < 0){
//		    	if(_debug) Log.v("SMSCommon.getMessageID() Message ID NOT Found: THREAD_ID = " + threadID + " MESSAGE_BODY = " + messageBody);
//		    }
//		    return messageID;
//		}catch(Exception ex){
//			Log.e("SMSCommon.loadMessageID() ERROR: " + ex.toString());
//			return -1;
//		}
//	}
	
	/**
	 * Get the SMS Message ID for this notification.
	 * 
	 * @param context - Application context.
	 * @param address - The sent from address of this message.
	 * @param threadID - The Thread ID of this message.
	 * @param messageBody - The text of the message.
	 * @param timeStamp - The timeStamp of the message.
	 * 
	 * @return long - Returns the Message ID of this SMS message.
	 */
	public static long getMessageID(Context context, String address, long threadID, String messageBody, long timeStamp){
		_debug = Log.getDebug();
		if(_debug) Log.v("SMSCommon.getMessageID()");
		if(messageBody == null){
			if(_debug) Log.v("SMSCommon.getMessageID() Message body provided is null. Exiting...");
			return -1;
		}else{
			messageBody = messageBody.replace("<br/>", "\n").replace("<br />", "\n").trim();
		}
		address = address.contains("@") ? EmailCommon.removeEmailFormatting(address) : PhoneCommon.removePhoneNumberFormatting(address);
		if(address == null || address.equals("")){
			if(_debug) Log.v("SMSCommon.getMessageID() Address provided is null or empty. Exiting...");
			return -1;
		}
		long messageID = -1;
	    Cursor cursor = null;
	    try{
			final String[] projection = new String[] {"_id", "body"};
			final String selection;
			final String[] selectionArgs;
			final String sortOrder = "date DESC";
			if(threadID < 0){
				selection = "address=?";
				selectionArgs = new String[]{address};
			}else{
				selection = "thread_id=?";
				selectionArgs = new String[]{String.valueOf(threadID)};
			}
	    	cursor = context.getContentResolver().query(
	    		Uri.parse("content://sms/inbox"),
	    		projection,
	    		selection,
				selectionArgs,
				sortOrder);
		    if(cursor == null){
		    	if(_debug) Log.v("SMSCommon.getMessageID() Currsor is null. Exiting...");
		    	return -1;
		    }
		    if(cursor.moveToFirst()){
		    	int size = cursor.getCount();
		    	for(int i=0;i<size;i++){
			    	String currentMessageBody = cursor.getString(cursor.getColumnIndex("body")).trim();
			    	//if(_debug) Log.v("SMSCommon.getMessageID() smsMessageBody: " + currentMessageBody + " MessageBody: " + messageBody);
		    		if(currentMessageBody.equals(messageBody)){
		    			messageID = cursor.getLong(cursor.getColumnIndex("_id"));
		    			if(_debug) Log.v("SMSCommon.getMessageID() Message ID Found. MESSAGE_ID: " + messageID);
		    			cursor.close();
		    			return messageID;
		    		}
		    		cursor.moveToNext();
		    	}
		    }
			cursor.close();
			return -1;
	    }catch(Exception ex){
			Log.e("SMSCommon.getMessageID() ERROR: " + ex.toString());
			if(cursor != null){
				cursor.close();
			}
			return -1;
		}
	}

	/**
	 * Gets the address of the MMS message.
	 * 
	 * @param messageID - The MMS message ID.
	 * 
	 * @return String - The phone or email address of the MMS message.
	 */
	public static String getMMSAddress(Context context, long messageID){
		_debug = Log.getDebug();
		if(_debug) Log.v("SMSCommon.getMMSAddress()");
		final String[] projection = new String[] {"address"};
		final String selection = "msg_id=?";
		final String[] selectionArgs = new String[]{String.valueOf(messageID)};
		final String sortOrder = null;
		String messageAddress = null;
		Cursor cursor = null;
        try{
		    cursor = context.getContentResolver().query(
		    		Uri.parse("content://mms/" + String.valueOf(messageID) + "/addr"),
		    		projection,
		    		selection,
					selectionArgs,
					sortOrder);
		    if(cursor == null){
		    	if(_debug) Log.v("SMSCommon.getMMSAddress() Currsor is null. Exiting...");
		    	return null;
		    }
		    while(cursor.moveToNext()){
		    	messageAddress = cursor.getString(cursor.getColumnIndex("address"));
	            break;
	        }
			cursor.close();
		}catch(Exception ex){
			Log.e("SMSCommon.getMMSAddress() ERROR: " + ex.toString());
			if(cursor != null){
				cursor.close();
			}
			return null;
		}	   
	    return messageAddress;
	}
	
	/**
	 * Read the message text of the MMS message.
	 * 
	 * @param messageID - The MMS message ID.
	 * 
	 * @return String - The message text of the MMS message.
	 */
	public static String getMMSText(Context context, long messageID){
		_debug = Log.getDebug();
		if(_debug) Log.v("SMSCommon.getMMSText()");
		final String[] projection = new String[] {"_id", "ct", "_data", "text"};
		final String selection = "mid=?";
		final String[] selectionArgs = new String[]{String.valueOf(messageID)};
		final String sortOrder = null;
		StringBuilder messageText = new StringBuilder();
		Cursor cursor = null;
        try{
		    cursor = context.getContentResolver().query(
		    		Uri.parse("content://mms/part"),
		    		projection,
		    		selection,
					selectionArgs,
					sortOrder);
		    if(cursor == null){
		    	if(_debug) Log.v("SMSCommon.getMMSText() Currsor is null. Exiting...");
		    	return null;
		    }
		    while(cursor.moveToNext()){
		        long partId = cursor.getLong(cursor.getColumnIndex("_id"));
		        String contentType = cursor.getString(cursor.getColumnIndex("ct"));
		        String text = cursor.getString(cursor.getColumnIndex("text"));
		        if(text != null){
	            	if(!messageText.toString().equals("")){
	            		messageText.append(" ");
	            	}
			        messageText.append(text);
		        }
		        if(contentType.equals("text/plain")){
		            String data = cursor.getString(cursor.getColumnIndex("_data"));
		            if(data != null){
		            	if(!messageText.toString().equals("")){
		            		messageText.append(" ");
		            	}
		            	messageText.append(getMMSTextFromPart(context, partId));
		            }
		        }
	        }
			cursor.close();
		}catch(Exception ex){
			Log.e("SMSCommon.getMMSText ERROR: " + ex.toString());
			if(cursor != null){
				cursor.close();
			}
			return null;
		}   
	    return messageText.toString();  
	}

	/**
	 * Read the message text of the MMS message.
	 * 
	 * @param messageID - The MMS message ID.
	 * 
	 * @return String - The message text of the MMS message.
	 */
	private static String getMMSTextFromPart(Context context, long messageID){
		if(_debug) Log.v("SMSCommon.getMMSTextFromPart()");
	    InputStream inputStream = null;
	    StringBuilder messageText = new StringBuilder();
	    try {
	    	inputStream = context.getContentResolver().openInputStream(Uri.parse("content://mms/part/" + String.valueOf(messageID)));
	        if(inputStream != null){
	            InputStreamReader inputStreamReader = new InputStreamReader(inputStream, "UTF-8");
	            BufferedReader reader = new BufferedReader(inputStreamReader);
	            String temp = reader.readLine();
	            while(temp != null){
	            	messageText.append(temp);
	                temp = reader.readLine();
	            }
	        }
	    }catch(Exception ex){
	    	Log.e("SMSCommon.getMMSTextFromPart() ERROR: " + ex.toString());
	    }finally {
	    	try{
	    		inputStream.close();
	    	}catch(Exception ex){
	    		Log.e("SMSCommon.getMMSTextFromPart() ERROR: " + ex.toString());
	    	}
	    }
	    return messageText.toString();
	}
	
	/**
	 * Start the intent for any android messaging application to send a reply.
	 * 
	 * @param context - Application Context.
	 * @param notificationActivity - A reference to the parent activity.
	 * @param phoneNumber - The number/address/screen name we want to send a message to.
	 * @param requestCode - The request code we want returned.
	 * 
	 * @return boolean - Returns true if the activity can be started.
	 */
	public static boolean startMessagingAppReplyActivity(Context context, NotificationActivity notificationActivity, String phoneNumber, int requestCode){
		_debug = Log.getDebug();
		if(_debug) Log.v("SMSCommon.startMessagingAppReplyActivity()");
		if(phoneNumber == null){
			Toast.makeText(context, context.getString(R.string.app_android_reply_messaging_address_error), Toast.LENGTH_LONG).show();
			return false;
		}
		try{
			Intent intent = new Intent(Intent.ACTION_SENDTO);
			if(phoneNumber.contains("@")){
			    intent.setData(Uri.parse("smsto:" + EmailCommon.removeEmailFormatting(phoneNumber)));
			}else{
			    intent.setData(Uri.parse("smsto:" + PhoneCommon.removePhoneNumberFormatting(phoneNumber)));
			}
		    // Exit the app once the SMS is sent.
		    intent.putExtra("compose_mode", true);
	        notificationActivity.startActivityForResult(intent, requestCode);
	        Common.setInLinkedAppFlag(context, true);
	        return true;
		}catch(Exception ex){
			Log.e("SMSCommon.startMessagingAppReplyActivity() ERROR: " + ex.toString());
			Toast.makeText(context, context.getString(R.string.app_android_messaging_app_error), Toast.LENGTH_LONG).show();
			Common.setInLinkedAppFlag(context, false);
			return false;
		}
	}

	/**
	 * Start the intent for any android messaging application to view the message thread.
	 * 
	 * @param context - Application Context.
	 * @param notificationActivity - A reference to the parent activity.
	 * @param phoneNumber - The phone number we want to send a message to.
	 * @param requestCode - The request code we want returned.
	 * 
	 * @return boolean - Returns true if the activity can be started.
	 */
	public static boolean startMessagingAppViewThreadActivity(Context context, NotificationActivity notificationActivity, long threadID, int requestCode){
		_debug = Log.getDebug();
		if(_debug) Log.v("SMSCommon.startMessagingAppViewThreadActivity()");
		if(threadID < 0){
			Toast.makeText(context, context.getString(R.string.app_android_message_not_found_error), Toast.LENGTH_LONG).show();
			return false;
		}
		try{
			Intent intent = new Intent(Intent.ACTION_VIEW);
			intent.setData(Uri.parse("content://mms-sms/conversations/" + String.valueOf(threadID)));
	        notificationActivity.startActivityForResult(intent, requestCode);
	        Common.setInLinkedAppFlag(context, true);
	        return true;
		}catch(Exception ex){
			Log.e("SMSCommon.startMessagingAppViewThreadActivity() ERROR: " + ex.toString());
			Toast.makeText(context, context.getString(R.string.app_android_messaging_app_error), Toast.LENGTH_LONG).show();
			Common.setInLinkedAppFlag(context, false);
			return false;
		}
	}

	/**
	 * Start the intent for any android messaging application to view the messaging inbox.
	 * 
	 * @param context - Application Context.
	 * @param notificationActivity - A reference to the parent activity.
	 * @param requestCode - The request code we want returned.
	 * 
	 * @return boolean - Returns true if the activity can be started.
	 */
	public static boolean startMessagingAppViewInboxActivity(Context context, NotificationActivity notificationActivity, int requestCode){
		_debug = Log.getDebug();
		if(_debug) Log.v("SMSCommon.startMessagingAppViewInboxActivity()");
		try{
			Intent intent = new Intent(Intent.ACTION_MAIN);
		    intent.setType("vnd.android-dir/mms-sms");
	        notificationActivity.startActivityForResult(intent, requestCode);
	        Common.setInLinkedAppFlag(context, true);
	        return true;
		}catch(Exception ex){
			Log.e("SMSCommon.startMessagingAppViewInboxActivity() ERROR: " + ex.toString());
			Toast.makeText(context, context.getString(R.string.app_android_messaging_app_error), Toast.LENGTH_LONG).show();
			Common.setInLinkedAppFlag(context, false);
			return false;
		}
	}
	
	/**
	 * Delete a an entire SMS/MMS thread.
	 * 
	 * @param context - The application context.
	 * @param threadID - The Thread ID that we want to delete.
	 * @param notificationType - The notification type.
	 * 
	 * @return boolean - Returns true if the thread was deleted successfully.
	 */
	public static boolean deleteMessageThread(Context context, long threadID, int notificationType){
		_debug = Log.getDebug();
		if(_debug) Log.v("SMSCommon.deleteMessageThread()");
		try{
			if(threadID < 0){
				if(_debug) Log.v("SMSCommon.deleteMessageThread() Thread ID < 0. Exiting...");
				return false;
			}
			if(notificationType == Constants.NOTIFICATION_TYPE_MMS){
				context.getContentResolver().delete(
						Uri.parse("content://mms/conversations/" + String.valueOf(threadID)), 
						null, 
						null);
			}else{
				context.getContentResolver().delete(
						Uri.parse("content://sms/conversations/" + String.valueOf(threadID)), 
						null, 
						null);
			}
			return true;
		}catch(Exception ex){
			Log.e("SMSCommon.deleteMessageThread() ERROR: " + ex.toString());
			return false;
		}
	}

	/**
	 * Delete a single SMS/MMS message.
	 * 
	 * @param context - The application context.
	 * @param messageID - The Message ID that we want to delete.
	 * 
	 * @return boolean - Returns true if the message was deleted successfully.
	 */
	public static boolean deleteSingleMessage(Context context, long messageID, long threadID, int notificationType){
		_debug = Log.getDebug();
		if(_debug) Log.v("SMSCommon.deleteSingleMessage()");
		try{
			if(messageID < 0){
				if(_debug) Log.v("SMSCommon.deleteSingleMessage() Message ID < 0. Exiting...");
				return false;
			}	
			String selection = null;
			String[] selectionArgs = null;
			if(notificationType == Constants.NOTIFICATION_TYPE_MMS){
				context.getContentResolver().delete(
						Uri.parse("content://mms/" + String.valueOf(messageID)),
						selection, 
						selectionArgs);
			}else{
				context.getContentResolver().delete(
						Uri.parse("content://sms/" + String.valueOf(messageID)),
						selection, 
						selectionArgs);
			}
			//Mark the thread as being read. Without this, the thread may be displayed as unread again.
			setThreadRead(context, threadID, true);
			return true;
		}catch(Exception ex){
			Log.e("SMSCommon.deleteSingleMessage() ERROR: " + ex.toString());
			return false;
		}
	}

	/**
	 * Mark a single SMS/MMS message as being read or not.
	 * 
	 * @param context - The application context.
	 * @param messageID - The Message ID that we want to alter.
	 * @param threadID - The Thread ID that we want to alter.
	 * @param isViewed - The boolean value indicating if it was read or not.
	 * 
	 * @return boolean - Returns true if the message was updated successfully.
	 */
	public static boolean setMessageRead(Context context, long messageID, long threadID, boolean isViewed){
		_debug = Log.getDebug();
		if(_debug) Log.v("SMSCommon.setMessageRead()");
		try{
			if(messageID < 0){
				if(_debug) Log.v("SMSCommon.setMessageRead() Message ID < 0. Exiting...");
				return false;
			}
			ContentValues contentValues = new ContentValues();
			if(isViewed){
				contentValues.put("READ", 1);
			}else{
				contentValues.put("READ", 0);
			}
			String selection = null;
			String[] selectionArgs = null;			
			context.getContentResolver().update(
					Uri.parse("content://sms/" + String.valueOf(messageID)), 
		    		contentValues, 
		    		selection, 
		    		selectionArgs);
			return true;
		}catch(Exception ex){
			Log.e("SMSCommon.setMessageRead() ERROR: " + ex.toString());
			return false;
		}
	}

	/**
	 * Mark a SMS/MMS thread as being read or not.
	 * 
	 * @param context - The application context.
	 * @param threadID - The Thread ID that we want to alter.
	 * @param isViewed - The boolean value indicating if it was read or not.
	 * 
	 * @return boolean - Returns true if the message was updated successfully.
	 */
	public static boolean setThreadRead(Context context, long threadID, boolean isViewed){
		_debug = Log.getDebug();
		if(_debug) Log.v("SMSCommon.setThreadRead()");
		try{
			if(threadID < 0){
				if(_debug) Log.v("SMSCommon.setThreadRead() Thread ID < 0. Exiting...");
				return false;
			}
			ContentValues contentValues = new ContentValues();
			if(isViewed){
				contentValues.put("READ", 1);
			}else{
				contentValues.put("READ", 0);
			}
			String selection = null;
			String[] selectionArgs = null;			
			context.getContentResolver().update(
					Uri.parse("content://mms-sms/conversations/" + String.valueOf(threadID)), 
		    		contentValues, 
		    		selection, 
		    		selectionArgs);
			return true;
		}catch(Exception ex){
			Log.e("SMSCommon.setThreadRead() ERROR: " + ex.toString());
			return false;
		}
	}	
	
	/**
	 * Determine if a SMS/MMS message is unread or not.
	 * 
	 * @param context - The application context.
	 * @param messageID - The Message ID that we want to query.
	 * @param threadID - The Thread ID that we want to query.
	 * 
	 * @return boolean - Returns false if the message was found and is unread, returns true otherwise.
	 */
	public static boolean isMessageRead(Context context, long messageID, long threadID){
		_debug = Log.getDebug();
		if(_debug) Log.v("SMSCommon.isMessageRead()");
		Cursor cursor = null;
		try{
			if(messageID < 0){
				if(_debug) Log.v("SMSCommon.isMessageRead() Message ID < 0. Exiting...");
				return true;
			}
    		final String[] projection = new String[] { "_id", "thread_id", "read"};
			String selection = null;
			String[] selectionArgs = null;
			if(threadID < 0){
				selection = "_id=?";
				selectionArgs = new String[]{String.valueOf(messageID)};
			}else{
				selection = "_id=? AND thread_id=?";
				selectionArgs = new String[]{String.valueOf(messageID), String.valueOf(threadID)};
			}
    		final String sortOrder = "date DESC";
				cursor = context.getContentResolver().query(
						Uri.parse("content://sms/inbox"),
						projection,
						selection, 
						selectionArgs,
						sortOrder);
		    if(cursor == null){
		    	if(_debug) Log.v("SMSCommon.isMessageRead() Currsor is null. Exiting...");
		    	return true;
		    }
		    int messageRead = 1;
		    if(cursor.moveToFirst()){
		    	messageRead = cursor.getInt(cursor.getColumnIndex("read"));
		    	if(_debug) Log.v("SMSCommon.isMessageRead() Message Found - Message Read: " + String.valueOf(messageRead));
	    	}else{
	    		if(_debug) Log.v("SMSCommon.isMessageRead() Message ID: " + String.valueOf(messageID) + " was not found!  Exiting...");
	    		return true;
	    	}
			cursor.close();
		    return messageRead == 0 ? false : true;
		}catch(Exception ex){
			Log.e("SMSCommon.isMessageRead() ERROR: " + ex.toString());
    		if(cursor != null){
				cursor.close();
			}
			return true;
		}
	}
	
	/**
	 * Save a message as a draft.
	 * 
	 * @param context - The application context.
	 * @param address - The address the message it to.
	 * @param message - The message to save.
	 */
	public static void saveMessageDraft(Context context, String address, String message){
		_debug = Log.getDebug();  
		if(_debug) Log.v("SMSCommon.saveMessageDraft()");
		try{
			if(message != null && !message.equals("")){
		    	//Store the message in the draft folder so that it shows in Messaging apps.
		        ContentValues values = new ContentValues();
		        values.put("address", address);
		        values.put("body", message);
		        values.put("date", String.valueOf(System.currentTimeMillis()));
		        values.put("type", "3");
		        String messageAddress = address.contains("@") ? EmailCommon.removeEmailFormatting(address) : PhoneCommon.removePhoneNumberFormatting(address);
		        values.put("thread_id", String.valueOf(SMSCommon.getThreadID(context, messageAddress, 1)));
		        context.getContentResolver().insert(Uri.parse("content://sms/draft"), values);
		        Toast.makeText(context, context.getString(R.string.draft_saved_text), Toast.LENGTH_SHORT).show();
			}
		}catch(Exception ex){
			Log.e("SMSCommon.saveMessageDraft() Insert Into Sent Folder ERROR: " + ex.toString());
		}
	}

	/**
	 * Send a SMS message.
	 * 
	 * @param context - The application context.
	 * @param address - The address the message it to.
	 * @param message - The message to send.
	 */
	public static void sendSMSTask(Context context, String address, String message){
		_context = context;
		new sendSMSAsyncTask().execute(address, message);
	}
	
	//================================================================================
	// Private Methods
	//================================================================================
	
	/**
	 * Send a SMS message.
	 * 
	 * @author Camille S�vigny
	 */
	private static class sendSMSAsyncTask extends AsyncTask<String, Void, Boolean> {
	    
	    /**
	     * Do this work in the background.
	     * 
	     * @param params - The SMS message parameters.
	     */
	    protected Boolean doInBackground(String... params){
			if (_debug) Log.v("SMSCommon.sendSMSAsyncTask.doInBackground()");
			return sendSMS(params[0], params[1]);
	    }
	    
	    /**
	     * Display a message if the SMS encountered an error.
	     * 
	     * @param result - Boolean indicating success.
	     */
	    protected void onPostExecute(Boolean result){
			if (_debug) Log.v("SMSCommon.sendSMSAsyncTask.doInBackground.onPostExecute() RESULT: " + result);
			//Do Nothing
	    }
	    
	}
	
	/**
	 * Send SMS message.
	 * 
	 * @param address - The address the message it to.
	 * @param message - The message to send.
	 */
	public static boolean sendSMS(String address, String message){
		_debug = Log.getDebug();  
		if(_debug) Log.v("SMSCommon.sendSMS()");
		try{
			
//			final String SMS_SENT = "SMS_SENT";
//			final String SMS_DELIVERED = "SMS_DELIVERED";
//	        PendingIntent sentPI = PendingIntent.getBroadcast(this, 0, new Intent(SMS_SENT), 0);
//	        PendingIntent deliveredPI = PendingIntent.getBroadcast(this, 0, new Intent(SMS_DELIVERED), 0);
//	        PendingIntent sentPI = null;
//	        PendingIntent deliveredPI = null;
//	        //When the SMS has been sent.
//	        registerReceiver(new BroadcastReceiver(){
//	            @Override
//	            public void onReceive(Context arg0, Intent arg1){
//	                switch (getResultCode())
//	                {
//	                    case Activity.RESULT_OK:
//	                        Toast.makeText(getBaseContext(), getString(R.string.message_sent_text), Toast.LENGTH_LONG).show();
//	                        break;
//	                    case SmsManager.RESULT_ERROR_GENERIC_FAILURE:
//	                        Toast.makeText(getBaseContext(), getString(R.string.message_sent_error_generic_failure_text), Toast.LENGTH_LONG).show();
//	                        break;
//	                    case SmsManager.RESULT_ERROR_NO_SERVICE:
//	                        Toast.makeText(getBaseContext(), getString(R.string.message_sent_error_no_service_text), Toast.LENGTH_LONG).show();
//	                        break;
//	                    case SmsManager.RESULT_ERROR_NULL_PDU:
//	                        Toast.makeText(getBaseContext(), getString(R.string.message_sent_error_null_pdu_text), Toast.LENGTH_LONG).show();
//	                        break;
//	                    case SmsManager.RESULT_ERROR_RADIO_OFF:
//	                        Toast.makeText(getBaseContext(), getString(R.string.message_sent_error_radio_off_text), Toast.LENGTH_LONG).show();
//	                        break;
//	                }
//	            }
//	        }, new IntentFilter(SMS_SENT));
//	        //When the SMS has been delivered.
//	        registerReceiver(new BroadcastReceiver(){
//	            @Override
//	            public void onReceive(Context arg0, Intent arg1){
//	                switch (getResultCode())
//	                {
//	                    case Activity.RESULT_OK:
//	                        Toast.makeText(getBaseContext(), getString(R.string.message_delivered_text), Toast.LENGTH_LONG).show();
//	                        break;
//	                    case Activity.RESULT_CANCELED:
//	                        Toast.makeText(getBaseContext(), getString(R.string.message_not_delivered_text), Toast.LENGTH_LONG).show();
//	                        break;                        
//	                }
//	            }
//	        }, new IntentFilter(SMS_DELIVERED)); 
			
			SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(_context);
			//Include the signature.
	        if(preferences.getBoolean(Constants.QUICK_REPLY_SIGNATURE_ENABLED_KEY, false)){
	        	message += " " + preferences.getString(Constants.QUICK_REPLY_SIGNATURE_KEY, _context.getString(R.string.quick_reply_default_signature));
	        }
	        if(_debug) Log.v("SMSCommon.sendSMS() Message: " + message);
			SmsManager smsManager = SmsManager.getDefault();
			if(address.contains("@")){
				//Send to email address
				//Need to set the SMS-to-Email Gateway number for this to work.
				// (USA) Sprint PCS - 6245 [address message]
				// (USA) T-Mobile - 500 [address text | address/subject/text | address#subject#text]
				// (USA) AT&T - 121 [address text | address (subject) text]
				// (USA) AT&T - 111 [address text | address (subject) text]
				// (UK) AQL - 447766 [address text]
				// (UK) AQL - 404142 [address text]
				// (Croatia) T-Mobile - 100 [address#subject#text]
				// (Costa Rica) ICS - 1001 [address : (subject) text]
				//This value can be set in the Advanced Settings preferences.
				int smsToEmailGatewayKey = Integer.parseInt(preferences.getString(Constants.SMS_GATEWAY_KEY, "1"));
				String smsToEmailGatewayNumber = null;
				String smsToEmailMessageHeader = null;
				switch(smsToEmailGatewayKey){
			    	case Constants.SMS_EMAIL_GATEWAY_1:{
			    		// (USA) Sprint PCS - 6245 [address message]
			    		smsToEmailGatewayNumber = "6245";
			    		smsToEmailMessageHeader = address + " ";
			    		break;
			    	}
			    	case Constants.SMS_EMAIL_GATEWAY_2:{
			    		// (USA) T-Mobile - 500 [address text | address/subject/text | address#subject#text]
			    		smsToEmailGatewayNumber = "500";
			    		smsToEmailMessageHeader = address + " ";
			    		break;
			    	}
			    	case Constants.SMS_EMAIL_GATEWAY_3:{
			    		// (USA) AT&T - 121 [address text | address (subject) text]
			    		smsToEmailGatewayNumber = "121";
			    		smsToEmailMessageHeader = address + " ";
			    		break;
			    	}
			    	case Constants.SMS_EMAIL_GATEWAY_4:{
			    		// (USA) AT&T - 111 [address text | address (subject) text]
			    		smsToEmailGatewayNumber = "111";
			    		smsToEmailMessageHeader = address + " ";
			    		break;
			    	}
			    	case Constants.SMS_EMAIL_GATEWAY_5:{
			    		// (UK) AQL - 447766 [address text]
			    		smsToEmailGatewayNumber = "447766";
			    		smsToEmailMessageHeader = address + " ";
			    		break;
			    	}
			    	case Constants.SMS_EMAIL_GATEWAY_6:{
			    		// (UK) AQL - 404142 [address text]
			    		smsToEmailGatewayNumber = "404142";
			    		smsToEmailMessageHeader = address + " ";
			    		break;
			    	}
			    	case Constants.SMS_EMAIL_GATEWAY_7:{
			    		// (USA) AT&T - 121 [address text | address (subject) text]
			    		smsToEmailGatewayNumber = "121";
			    		smsToEmailMessageHeader = address + " ";
			    		break;
			    	}
			    	case Constants.SMS_EMAIL_GATEWAY_8:{
			    		// (Croatia) T-Mobile - 100 [address#subject#text]
			    		smsToEmailGatewayNumber = "100";
			    		smsToEmailMessageHeader = address + "##";
			    		break;
			    	}
				} 
				if(_debug) Log.v("SMSCommon.sendSMS() SmsToEmailGatewayNumber: " + smsToEmailGatewayNumber);
				try{
					if(smsToEmailGatewayNumber != null && smsToEmailMessageHeader != null){
						int smsToEmailMessageHeaderLength = smsToEmailMessageHeader.length();
						if(smsToEmailMessageHeaderLength + message.length() <= 160){
							//Send a single SMS message.
							//if(_debug) Log.v("SMSCommon.sendSMS() Email SMS Message Sent: " + smsToEmailMessageHeader + message);
							if(_debug) Log.v("SMSCommon.sendSMS() Email SMS Message Sent");
							smsManager.sendTextMessage(smsToEmailGatewayNumber, null, smsToEmailMessageHeader + message, null, null);
							writeSentSMSMessage(_context, address, message);
							return true;
						}else{
							//Send multiple smaller SMS messages.
							int splitMessageSize = 160 - smsToEmailMessageHeaderLength;
							if(_debug) Log.v("SMSCommon.sendSMS() SplitMessageSize: " + splitMessageSize);
							String[] messageArray = SMSCommon.splitEqually(message, splitMessageSize);
							int size = messageArray.length;
							for(int i=0; i<size; i++){
								//if(_debug) Log.v("SMSCommon.sendSMS() Email SMS Message Part Sent: " + smsToEmailMessageHeader + messageArray[i]);
								smsManager.sendTextMessage(smsToEmailGatewayNumber, null, smsToEmailMessageHeader + messageArray[i], null, null);
								writeSentSMSMessage(_context, address,  messageArray[i]);
							}
							return true;
						}
					}else{
						Log.e("SMSCommon.sendSMS() SmsToEmailGatewayNumber (" + smsToEmailGatewayNumber + ") or SmsToEmailMessageHeader (" + smsToEmailMessageHeader + ") is null! Exiting...");
						return false;
					}
				}catch(Exception ex){
		    		Log.e("SMSCommon.sendSMS() Send To Email ERROR: " + ex.toString());
		    		return false;
		    	}
			}else{
				try{
					//Send to regular text message number.
					//Split message before sending using multiparts.
					if(preferences.getBoolean(Constants.SMS_SPLIT_MESSAGE_KEY, false)){
						//TODO
					}else{
						if(_debug) Log.v("SMSCommon.sendSMS() Email SMS Message Sent");
						ArrayList<String> parts = smsManager.divideMessage(message);
						smsManager.sendMultipartTextMessage(address, null, parts, null, null);
						writeSentSMSMessage(_context, address, message);
					}
					return true;
				}catch(Exception ex){
		    		Log.e("SMSCommon.sendSMS() Send To Number ERROR: " + ex.toString());
		    		return false;
		    	}
			}
		}catch(Exception ex){
			Log.e("SMSCommon.sendSMS() ERROR: " + ex.toString());
			return false;
		}
    }
	
	/**
	 * Split a string into an array of equal length sub strings.
	 * 
	 * @param input - The input string.
	 * @param size - The size to split the string into.
	 * 
	 * @return String[] - Returns a string array containing all the substrings.
	 */
	private static String[] splitEqually(String input, int size){
        if(_debug) Log.v("SMSCommon.splitEqually()");
		int length = input.length();
		if(length <= size){
			return new String[]{input};
		}
		ArrayList<String> arrayList = new ArrayList<String>((input.length() + size - 1) / size);
	    for (int start = 0; start < length; start += size) {
	    	arrayList.add(input.substring(start, Math.min(input.length(), start + size)));
	    	if(_debug) Log.v("SMSCommon.splitEqually() Message Part: " + input.substring(start, Math.min(input.length(), start + size)));
	    }
	    return arrayList.toArray(new String[arrayList.size()]);
	}
	
	/**
	 * Store a SMS into the system DB as a "sent" sms message.
	 * 
	 * @param context - The application context.
	 * @param address - The address the message it to.
	 * @param message - The message to send.
	 * 
	 * @return boolean - Return true if the operation was successful.
	 */
	private static boolean writeSentSMSMessage(Context context, String address, String message){
        if(_debug) Log.v("SMSCommon.writeSentSMSMessage()");
    	try{
    		if(_debug) Log.v("SMSCommon.writeSentSMSMessage() Storing message in sent message folder.");
            ContentValues values = new ContentValues();
            values.put("address", address);
            values.put("body", message);
            context.getContentResolver().insert(Uri.parse("content://sms/sent"), values);
            return true;
    	}catch(Exception ex){
    		Log.e("SMSCommon.writeSentSMSMessage() Insert Into Sent Folder ERROR: " + ex.toString());
    		return false;
    	}
	}
	
}
