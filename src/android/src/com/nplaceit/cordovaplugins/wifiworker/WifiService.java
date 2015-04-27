/**
 * 
 */
package com.nplaceit.cordovaplugins.wifiworker;

import java.util.List;

import android.app.IntentService;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.support.v4.app.NotificationCompat;
import android.text.TextUtils;
import android.util.Log;

import com.wiflip.CordovaApp;
import com.wiflip.R;

/**
 * @author Daniel
 * 
 */
public class WifiService extends IntentService {

	private static final String TAG = "WIFIService";

	/**
	 * When not initialized
	 */
	public static final int STATE_UNKNOWN = 0;

	/**
	 * Nothing is being done
	 */
	public static final int STATE_NONE = 1;

	/**
	 * Looking for the passwords of networks from a scanResult
	 */
	public static final int STATE_SEARCHING_PASSWORDS = 2;

	/**
	 * Currently trying to connect to a network
	 */
	public static final int STATE_CONNECTING_TO_NETWORK = 3;

	/**
	 * The system is trying to connect to a network, this association was not
	 * started by this service
	 */
	public static final int STATE_OUTSIDE_ASSOCIATION = 4;

	/**
	 * The system is removing a network started by this service
	 */
	public static final int STATE_REMOVING_NETWORK = 5;

	/**
	 * When cordova is working in the wifi management, such as the user is
	 * connecting to a network manually
	 */
	public static final int STATE_CORDOVA_WORKING = 6;

	private WifiManager mWifiManager = null;

	private Dao database = null;

	private int currentState = STATE_UNKNOWN;

	private WifiController mWifiController;

	public WifiService() {
		super(WifiService.class.getSimpleName());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.IntentService#onHandleIntent(android.content.Intent)
	 */
	@Override
	protected void onHandleIntent(Intent intent) {

		Boolean isConnected = getWifiController().isCurrentlyConencted();

		String action = intent.getAction();

		if (isConnected
				&& action.equals(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)) {
			Log.d(TAG, "No need for scan, already connected");
		} else {

			/**
			 * If the database is not ready, just ends the service
			 */
			if (!getDatabase().isDatabaseReady()) {
				Log.d(TAG, "Database not ready");
			} else {

				if (action != null) {
					Log.d(TAG, "onHandleIntent called:" + action);

					if (action.equals(WifiManager.WIFI_STATE_CHANGED_ACTION)) {
						Log.d(TAG, "Wifi avaiability changed");

					} else if (action
							.equals(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)) {

						if (!isConnected) {
							if (getCurrentState() == STATE_NONE) {
								if (getWifiManager().isWifiEnabled()) {
									searchPasswords();
								} else {
									Log.d(TAG,
											"Wifi is off, no need to search for passwords");
								}
							} else {

								if (getCurrentState() == STATE_REMOVING_NETWORK) {
									Log.d(TAG, "Currently removing a network");
									setCurrentState(STATE_NONE);
								} else if (getCurrentState() == STATE_CORDOVA_WORKING) {
									Log.d(TAG, "Currently cordova is working");
									setCurrentState(STATE_NONE);
								} else {
									Log.d(TAG, "Not able to search passwords");
								}
							}
						} else {
							Log.d(TAG, "Already connected");
						}

					} else if (action
							.equals(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION)) {

					} else if (action.equals("ERROR_AUTHENTICATING")) {
						if (getCurrentState() == STATE_CONNECTING_TO_NETWORK) {

							String currentBSSID = getDatabase()
									.getCurrentBSSID();
							String currentSSID = getDatabase().getCurrentSSID();

							if (currentBSSID != null) {
								getDatabase().markNetworkAsFailed(currentBSSID);
							}
							if (currentSSID != null) {
								setCurrentState(STATE_REMOVING_NETWORK);
								getWifiController().removeNetwork(currentSSID);
							} else {
								setCurrentState(STATE_NONE);
							}

						}

					} else if (action.equals("SUCCESS_AUTHENTICATING")) {
						if (getCurrentState() == STATE_CONNECTING_TO_NETWORK) {
							Log.d(TAG, "NETWORK SUCCESS!");
							String currentSSID = getDatabase().getCurrentSSID();
							notifyConnection(currentSSID);
							setCurrentState(STATE_NONE);
						}

					} else if (action.equals("ASSOCIATING")) {
						if (getCurrentState() == STATE_NONE
								|| getCurrentState() == STATE_SEARCHING_PASSWORDS) {
							setCurrentState(STATE_OUTSIDE_ASSOCIATION);
						}

					} else if (action.equals("DISCONNECTED")) {

						String currentSSID = getDatabase().getCurrentSSID();

						if (getCurrentState() == STATE_OUTSIDE_ASSOCIATION) {
							setCurrentState(STATE_NONE);
						}

						if (currentSSID != null
								&& !TextUtils.isEmpty(currentSSID)) {
							Log.d(TAG, "Removing network: " + currentSSID);
							setCurrentState(STATE_REMOVING_NETWORK);
							getDatabase().removeCurrentConnectingNetwork();
							getWifiController().removeNetwork(currentSSID);
						}
					}
				}

			}

		}

	}

	private void notifyConnection(String networkSSID) {

		if (!getDatabase().isNotificationEnabled()) {
			return;
		}

		String contentText = getResources().getString(
				R.string.connectionNotification)
				+ " " + networkSSID;

		NotificationCompat.Builder builder = new NotificationCompat.Builder(
				this).setSmallIcon(com.wiflip.R.drawable.icon)
				.setContentTitle("Wiflip").setContentText(contentText);

		Intent notificationIntent = new Intent(this, CordovaApp.class);

		PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
				notificationIntent, PendingIntent.FLAG_CANCEL_CURRENT);

		builder.setContentIntent(contentIntent);
		builder.setAutoCancel(true);
		builder.setLights(Color.BLUE, 500, 1000);
		long[] pattern = { 150, 150, 150, 150 };
		builder.setVibrate(pattern);

		/*
		 * builder.setStyle(new NotificationCompat.InboxStyle()
		 * .setBigContentTitle("Wiflip") .setSummaryText(
		 * getResources().getString(R.string.connectionYou))
		 * .addLine(contentText));
		 */

		builder.setSound(Uri.parse("android.resource://"
				+ this.getPackageName() + "/" + com.wiflip.R.raw.connected));

		NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

		manager.notify(1, builder.build());

	}

	/**
	 * Gets and the current state, fetches from database if the state is unknown
	 * 
	 * @return The current state
	 */
	private int getCurrentState() {
		if (currentState == STATE_UNKNOWN) {
			currentState = getDatabase().getServiceState();
		}
		return currentState;
	}

	/**
	 * Sends a new service state to the database
	 * 
	 * @param state
	 */
	private void setCurrentState(int state) {
		currentState = state;
		getDatabase().setServiceState(state);
	}

	private void searchPasswords() {

		setCurrentState(STATE_SEARCHING_PASSWORDS);

		Log.d(TAG, "Searching passwords");

		List<ScanResult> scanResults = getWifiManager().getScanResults();

		Boolean foundPassword = false;
		String hotspotPassword = null;
		ScanResult foundNetwork = null;
		Cursor hotspotInfo = null;

		for (ScanResult scanResult : scanResults) {

			Log.d(TAG, "Searching password for " + scanResult.SSID + " - "
					+ scanResult.BSSID);

			hotspotInfo = searchNetworkInfo(scanResult);

			if (hotspotInfo != null) {
				if (hotspotInfo.moveToFirst()) {
					hotspotPassword = hotspotInfo.getString(hotspotInfo
							.getColumnIndex("senha"));

					if (hotspotPassword.length() > 0) {
						foundNetwork = scanResult;
						foundPassword = true;
					}
				}
				hotspotInfo.close();
			}

			if (foundPassword) {
				break;
			}
		}

		if (!foundPassword) {
			Log.d(TAG, "No passwords found");
			setCurrentState(STATE_NONE);
		} else {

			if (getCurrentState() == STATE_SEARCHING_PASSWORDS) {

				Log.d(TAG, "Passwords FOUND:" + hotspotPassword);

				if (getWifiController().addNetwork(foundNetwork,
						hotspotPassword)) {
					Log.d(TAG, "Network added!");
					if (getWifiController().connectToNetwork(foundNetwork,
							false)) {
						getDatabase().setCurrentConnectingNetwork(foundNetwork);
						setCurrentState(STATE_CONNECTING_TO_NETWORK);
						Log.d(TAG, "Network connection started");
					} else {
						setCurrentState(STATE_NONE);
						Log.d(TAG, "Network connection start FAILED");
					}
				} else {
					setCurrentState(STATE_NONE);
					Log.d(TAG, "Network failed to add");
				}
			} else {
				Log.d(TAG,
						"Passwords search was interrupted. The system might have started an "
								+ "association that was not called from this service");
			}
		}

	}

	private Cursor searchNetworkInfo(ScanResult scanResult) {
		Cursor hotspotInfo = getDatabase().getHotspotInfo(scanResult.BSSID);

		if (hotspotInfo != null) {
			hotspotInfo.moveToFirst();

			if (hotspotInfo != null && hotspotInfo.getCount() > 0) {
				Log.d(TAG, "Network found; BSSID: " + scanResult.BSSID
						+ "; SSID: " + scanResult.SSID);
			}
		}

		return hotspotInfo;
	}

	private WifiManager getWifiManager() {
		if (mWifiManager == null) {
			mWifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
		}
		return mWifiManager;
	}

	private WifiController getWifiController() {
		if (mWifiController == null) {
			mWifiController = new WifiController(getApplicationContext(),
					getWifiManager());
		}
		return mWifiController;
	}

	private Dao getDatabase() {
		if (database == null) {
			database = Dao.getInstance(getApplicationContext(),
					"wiflipdatabase");
			Log.d(TAG, "Database initialized");
		}
		return database;
	}

}
