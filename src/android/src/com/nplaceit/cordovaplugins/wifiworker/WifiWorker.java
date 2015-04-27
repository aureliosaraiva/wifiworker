package com.nplaceit.cordovaplugins.wifiworker;

import java.util.List;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.location.Location;
import android.net.wifi.ScanResult;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.util.Log;

public class WifiWorker extends CordovaPlugin {

	private WifiManager wifiManager;

	BroadcastReceiver networkReceiver = null;
	CallbackContext listenerCallbackContext = null;
	WifiController mWifiController = null;

	private static final String TAG = "WifiWorker";

	private static final String ACTION_WIFI_ENABLED = "isWifiEnabled";
	private static final String ACTION_ENABLE_WIFI = "enableWifi";
	private static final String ACTION_START_SCAN = "startScan";
	private static final String ACTION_GET_CONNECTED_NETWORK = "getConnectedNetwork";
	private static final String ACTION_GET_SCAN_RESULT = "getScanResult";
	private static final String ACTION_GET_NEARBY_NETWORKS = "getNearbyNetworks";
	private static final String ACTION_GET_CONFIG_NETWORKS = "getConfiguredNetworks";
	private static final String ACTION_CONNECT_TO_NETWORK = "connectToNetwork";
	private static final String ACTION_ADD_NETWORK = "addNetwork";
	private static final String ACTION_REMOVE_NETWORK = "removeNetwork";
	private static final String ACTION_LISTEN_WIFI_CHANGE = "listenToWifiChanges";
	private static final String ACTION_REMOVE_WIFI_PASSWORD = "removeNetworkPassword";

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.apache.cordova.CordovaPlugin#initialize(org.apache.cordova.
	 * CordovaInterface, org.apache.cordova.CordovaWebView)
	 */
	@Override
	public void initialize(CordovaInterface cordova, CordovaWebView webView) {
		super.initialize(cordova, webView);
		wifiManager = (WifiManager) cordova.getActivity().getSystemService(
				Context.WIFI_SERVICE);
		this.registerReceiver();
	}

	private WifiController getWifiController() {
		if (mWifiController == null) {
			mWifiController = new WifiController(cordova.getActivity()
					.getApplicationContext(), wifiManager);
		}
		return mWifiController;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.apache.cordova.CordovaPlugin#execute(java.lang.String,
	 * org.json.JSONArray, org.apache.cordova.CallbackContext)
	 */
	@Override
	public boolean execute(String action, final JSONArray args,
			final CallbackContext callbackContext) throws JSONException {

		if (action.equals(ACTION_WIFI_ENABLED)) {
			this.isWifiEnabled(callbackContext);

		} else if (action.equals(ACTION_ENABLE_WIFI)) {
			this.enableWifi(callbackContext);

		} else if (action.equals(ACTION_GET_CONNECTED_NETWORK)) {
			this.getConnectedNetwork(callbackContext);

		} else if (action.equals(ACTION_START_SCAN)) {
			this.startScan(callbackContext);

		} else if (action.equals(ACTION_GET_SCAN_RESULT)) {
			this.getScanResult(callbackContext);

		} else if (action.equals(ACTION_GET_NEARBY_NETWORKS)) {
			this.getNearbyNetworks(callbackContext, args);

		} else if (action.equals(ACTION_GET_CONFIG_NETWORKS)) {
			this.getConfiguredNetworks(callbackContext);

		} else if (action.equals(ACTION_CONNECT_TO_NETWORK)) {
			this.connectToNetwork(callbackContext, args);

		} else if (action.equals(ACTION_ADD_NETWORK)) {

			cordova.getThreadPool().execute(new Runnable() {
				@Override
				public void run() {
					addNetwork(callbackContext, args);
				}
			});

		} else if (action.equals(ACTION_REMOVE_NETWORK)) {

			cordova.getThreadPool().execute(new Runnable() {
				@Override
				public void run() {
					removeNetwork(callbackContext, args);
				}
			});

		} else if (action.equals(ACTION_REMOVE_WIFI_PASSWORD)) {

			cordova.getThreadPool().execute(new Runnable() {
				@Override
				public void run() {
					removeNetworkPassword(callbackContext, args);
				}
			});

		} else if (action.equals(ACTION_LISTEN_WIFI_CHANGE)) {
			listenerCallbackContext = callbackContext;

		} else {
			return false;
		}

		return true;

	}

	private void registerReceiver() {

		IntentFilter intentFilter = new IntentFilter();

		intentFilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
		intentFilter.addAction(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION);

		this.networkReceiver = new BroadcastReceiver() {

			@Override
			public void onReceive(Context context, Intent intent) {

				if (listenerCallbackContext == null) {
					return;
				}

				String action = intent.getAction();

				if (action.equals(WifiManager.WIFI_STATE_CHANGED_ACTION)) {
					int wifiState = intent.getIntExtra(
							WifiManager.EXTRA_WIFI_STATE,
							WifiManager.WIFI_STATE_UNKNOWN);

					switch (wifiState) {
					case WifiManager.WIFI_STATE_DISABLING:
					case WifiManager.WIFI_STATE_DISABLED:
						Log.d(TAG, "WIFI Disabled");
						sendListenerResult("DISABLED");
						break;
					case WifiManager.WIFI_STATE_ENABLING:
					case WifiManager.WIFI_STATE_ENABLED:
						Log.d(TAG, "WIFI Enabled");
						sendListenerResult("ENABLED");
						break;
					default:
						break;
					}
				} else if (action
						.equals(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION)) {

					Log.d("WifiReceiver", "<<<<<Supplicant");

					SupplicantState supplicantState = ((SupplicantState) intent
							.getParcelableExtra(WifiManager.EXTRA_NEW_STATE));

					switch (supplicantState) {
					case ASSOCIATED:
						Log.i("SupplicantState", "ASSOCIATED");
						break;
					case ASSOCIATING:
						Log.i("SupplicantState", "ASSOCIATING");
						break;
					case AUTHENTICATING:
						sendListenerResult("AUTHENTICATING");
						Log.i("SupplicantState", "Authenticating...");
						break;
					case COMPLETED:
						sendListenerResult("COMPLETED",
								getConnectionInfoObject());
						Log.i("SupplicantState", "Connected");
						break;
					case DISCONNECTED:
						sendListenerResult("DISCONNECTED");
						Log.i("SupplicantState", "Disconnected");
						break;
					case DORMANT:
						Log.i("SupplicantState", "DORMANT");
						break;
					case FOUR_WAY_HANDSHAKE:
						Log.i("SupplicantState", "FOUR_WAY_HANDSHAKE");
						break;
					case GROUP_HANDSHAKE:
						Log.i("SupplicantState", "GROUP_HANDSHAKE");
						break;
					case INACTIVE:
						Log.i("SupplicantState", "INACTIVE");
						break;
					case INTERFACE_DISABLED:
						Log.i("SupplicantState", "INTERFACE_DISABLED");
						break;
					case INVALID:
						Log.i("SupplicantState", "INVALID");
						break;
					case SCANNING:
						sendListenerResult("SCANNING");
						Log.i("SupplicantState", "SCANNING");
						break;
					case UNINITIALIZED:
						Log.i("SupplicantState", "UNINITIALIZED");
						break;
					default:
						Log.i("SupplicantState", "Unknown");
						break;
					}

					int supl_error = intent.getIntExtra(
							WifiManager.EXTRA_SUPPLICANT_ERROR, -1);
					if (supl_error == WifiManager.ERROR_AUTHENTICATING) {
						sendListenerResult("ERROR_AUTHENTICATING");
						Log.i("ERROR_AUTHENTICATING", "ERROR_AUTHENTICATING");
					}

				}

			}
		};

		this.cordova.getActivity().registerReceiver(networkReceiver,
				intentFilter);
	}

	private JSONObject getConnectionInfoObject() {

		JSONObject networkInfo = new JSONObject();

		WifiInfo wifiInfo = wifiManager.getConnectionInfo();

		try {
			if (wifiInfo.getBSSID() != null) {
				if (!wifiInfo.getBSSID().equals(WifiController.INVALID_BSSID)) {

					networkInfo.put("bssid", wifiInfo.getBSSID());
					networkInfo.put("ssid", wifiInfo.getSSID());
					networkInfo.put("level", WifiManager.calculateSignalLevel(
							wifiInfo.getRssi(), 5));

					if (wifiInfo.getNetworkId() != -1) {

						List<WifiConfiguration> testList = wifiManager
								.getConfiguredNetworks();

						for (WifiConfiguration testWifi : testList) {
							if (wifiInfo.getNetworkId() == testWifi.networkId) {
								if (testWifi.preSharedKey != null) {
									networkInfo
											.put("capabilities", "[WPA-PSK]");
								}
								break;
							}
						}

					}

				}
			}

		} catch (JSONException e) {
			e.printStackTrace();
		}

		return networkInfo;
	}

	private boolean getConnectedNetwork(CallbackContext callbackContext) {
		callbackContext.success(getConnectionInfoObject());
		return true;
	}

	private void sendListenerResult(String resultType) {
		JSONObject data = new JSONObject();
		sendListenerResult(resultType, data);
	}

	private void sendListenerResult(String resultType, JSONObject data) {

		JSONObject callbackData = new JSONObject();

		PluginResult pluginResult;

		try {
			callbackData.put("type", resultType);
			callbackData.put("data", data);

			pluginResult = new PluginResult(PluginResult.Status.OK,
					callbackData);
			pluginResult.setKeepCallback(true);
			listenerCallbackContext.sendPluginResult(pluginResult);

		} catch (JSONException e) {
			Log.d(TAG,
					"SendToListenerResult: [JSON EXCEPTION] " + e.getMessage());
			e.printStackTrace();

			pluginResult = new PluginResult(PluginResult.Status.ERROR);
			pluginResult.setKeepCallback(true);
			listenerCallbackContext.sendPluginResult(pluginResult);
		}
	}

	private boolean isWifiEnabled(CallbackContext callbackContext) {
		boolean isEnabled = wifiManager.isWifiEnabled();
		callbackContext.success(isEnabled ? "1" : "0");
		return isEnabled;
	}

	private boolean enableWifi(CallbackContext callbackContext) {
		if (wifiManager.setWifiEnabled(true)) {
			callbackContext.success(1);
			return true;
		} else {
			callbackContext.error(0);
			return false;
		}
	}

	private boolean startScan(CallbackContext callbackContext) {
		if (wifiManager.startScan()) {
			callbackContext.success();
			return true;
		} else {
			callbackContext.error("Error while scanning");
			return false;
		}
	}

	private boolean getScanResult(final CallbackContext callbackContext) {

		cordova.getThreadPool().execute(new Runnable() {

			@Override
			public void run() {

				List<ScanResult> scanResult = wifiManager.getScanResults();

				JSONArray list = new JSONArray();

				if (scanResult != null) {
					for (ScanResult scan : scanResult) {
						JSONObject level = new JSONObject();
						try {
							level.put("level", WifiManager
									.calculateSignalLevel(scan.level, 5));
							level.put("ssid", scan.SSID);
							level.put("bssid", scan.BSSID);
							level.put("capabilities", scan.capabilities);
							list.put(level);
						} catch (JSONException e) {
							e.printStackTrace();
						}
					}
				}

				callbackContext.success(list);

			}
		});

		return true;

	}

	private boolean getConfiguredNetworks(CallbackContext callbackContext) {
		List<WifiConfiguration> wifiList = wifiManager.getConfiguredNetworks();
		JSONArray list = new JSONArray();

		for (WifiConfiguration wifi : wifiList) {
			JSONObject level = new JSONObject();
			try {
				level.put("ssid", wifi.SSID);
				level.put("bssid", wifi.BSSID);
				level.put("hiddenSSID", wifi.hiddenSSID);
				level.put("networkId", wifi.networkId);
				level.put("priority", wifi.priority);
				level.put("status", wifi.status);
				level.put("wepTxKeyIndex", wifi.wepTxKeyIndex);
				level.put("preSharedKey", wifi.preSharedKey);
				list.put(level);
			} catch (JSONException e) {
				e.printStackTrace();
			}
		}

		callbackContext.success(list);

		return true;

	}

	private boolean getNearbyNetworks(final CallbackContext callbackContext,
			final JSONArray data) {

		cordova.getThreadPool().execute(new Runnable() {

			@Override
			public void run() {

				try {

					JSONArray list = new JSONArray();

					double latitude = data.getDouble(0);
					double longitude = data.getDouble(1);
					double maxDistance = data.optDouble(2, 9999);

					Cursor networksCursor = getWifiController()
							.getNearbyNetworks(longitude, latitude);

					if (networksCursor != null && networksCursor.moveToFirst()) {
						do {

							if (!networksCursor.isNull(networksCursor
									.getColumnIndex("latitude"))
									&& !networksCursor.isNull(networksCursor
											.getColumnIndex("longitude"))) {

								double networkLatitude = networksCursor
										.getDouble(networksCursor
												.getColumnIndex("latitude"));
								double networkLongitude = networksCursor
										.getDouble(networksCursor
												.getColumnIndex("longitude"));

								float[] results = new float[1];
								Location.distanceBetween(latitude, longitude,
										networkLatitude, networkLongitude,
										results);

								if (results[0] <= maxDistance) {
									JSONObject network = new JSONObject();

									for (String columnName : networksCursor
											.getColumnNames()) {
										network.put(
												columnName,
												networksCursor
														.getString(networksCursor
																.getColumnIndex(columnName)));
									}

									list.put(network);
								}

							}

						} while (networksCursor.moveToNext());

					}

					callbackContext.success(list);

				} catch (Exception e) {
					callbackContext.error(e.getMessage());
					e.printStackTrace();
				}

			}
		});

		return true;

	}

	private boolean addNetwork(final CallbackContext callbackContext,
			final JSONArray data) {

		cordova.getThreadPool().execute(new Runnable() {

			@Override
			public void run() {

				try {

					String SSID = data.getString(0);
					String pass = data.optString(1);
					Boolean networkFound = false;

					List<ScanResult> scanResult = wifiManager.getScanResults();

					JSONArray list = new JSONArray();

					for (ScanResult scan : scanResult) {
						if (scan.SSID.equals(SSID)) {
							networkFound = true;
							if (getWifiController().addNetwork(scan, pass)) {
								callbackContext.success(list);
							} else {
								callbackContext.error(1);
							}
							break;
						}
					}

					if (!networkFound) {
						callbackContext.error(0);
					}

				} catch (Exception e) {
					callbackContext.error(e.getMessage());
					Log.d(TAG, e.getMessage());
				}

			}
		});

		return true;

	}

	private boolean removeNetwork(CallbackContext callbackContext,
			JSONArray data) {

		try {

			String SSID = data.getString(0);

			if (getWifiController().removeNetwork(SSID)) {
				callbackContext.success(data);
			} else {
				callbackContext.error(0);
			}

		} catch (Exception e) {
			callbackContext.error(e.getMessage());
			Log.d(TAG, e.getMessage());
			return false;
		}

		return true;
	}

	private boolean removeNetworkPassword(CallbackContext callbackContext,
			JSONArray data) {
		try {

			String SSID = data.getString(0);
			WifiConfiguration targetWifi = null;

			List<WifiConfiguration> wifiList = wifiManager
					.getConfiguredNetworks();

			for (WifiConfiguration wifi : wifiList) {
				if (wifi.SSID.equals(SSID)) {
					targetWifi = wifi;
				}
			}

			if (!targetWifi.equals(null)) {
				targetWifi.preSharedKey = "";
				wifiManager.updateNetwork(targetWifi);
				wifiManager.saveConfiguration();
				callbackContext.success();
				return true;
			} else {
				callbackContext.error("BSSID not found");
				return false;
			}

		} catch (Exception e) {
			callbackContext.error(e.getMessage());
			return false;
		}
	}

	private boolean connectToNetwork(CallbackContext callbackContext,
			JSONArray data) {

		String SSID = "";

		try {
			SSID = data.getString(0);
		} catch (Exception e) {
			callbackContext.error(e.getMessage());
			Log.d(TAG, e.getMessage());
			return false;
		}

		int netId = ssidToNetworkId(SSID);

		if (netId >= 0) {

			if (getWifiController().connectToNetwork(netId, true)) {
				Log.d(TAG, "Conection: SUCCESS");
				callbackContext.success(SSID);
				return true;
			} else {
				Log.d(TAG, "Conection: FAILED");
				callbackContext.error("Failed to connect");
				return false;
			}

		} else {
			callbackContext.error("The network was not found");
			return false;
		}

	}

	private int ssidToNetworkId(String SSID) {
		List<WifiConfiguration> currentNetworks = wifiManager
				.getConfiguredNetworks();
		int networkId = -1;

		SSID = WifiController.convertToQuotedString(SSID);

		for (WifiConfiguration test : currentNetworks) {
			if (test.SSID.equals(SSID)) {
				networkId = test.networkId;
				break;
			}
		}

		return networkId;
	}

	public void onDestroy() {
		this.cordova.getActivity().unregisterReceiver(this.networkReceiver);
	}

}
