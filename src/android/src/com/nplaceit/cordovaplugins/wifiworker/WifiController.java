package com.nplaceit.cordovaplugins.wifiworker;

import java.util.List;

import android.content.Context;
import android.database.Cursor;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiConfiguration.AuthAlgorithm;
import android.net.wifi.WifiConfiguration.GroupCipher;
import android.net.wifi.WifiConfiguration.PairwiseCipher;
import android.net.wifi.WifiConfiguration.Protocol;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiConfiguration.KeyMgmt;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;

public class WifiController {

	public static final String INVALID_BSSID = "00:00:00:00:00:00";

	// Constants used for different security types
	public static final String WPA2 = "WPA2";
	public static final String WPA = "WPA";
	public static final String WEP = "WEP";
	public static final String OPEN = "Open";
	// For EAP Enterprise fields
	public static final String WPA_EAP = "WPA-EAP";
	public static final String IEEE8021X = "IEEE8021X";

	public static final String[] EAP_METHOD = { "PEAP", "TLS", "TTLS" };

	public static final int WEP_PASSWORD_AUTO = 0;
	public static final int WEP_PASSWORD_ASCII = 1;
	public static final int WEP_PASSWORD_HEX = 2;

	private static final String TAG = WifiController.class.getSimpleName();

	WifiManager mWifiManager;
	Context mContext;
	Dao mDatabase = null;

	public WifiController(Context context, WifiManager wifiManager) {
		mContext = context;
		mWifiManager = wifiManager;
	}

	/**
	 * Adds networks by a ScanResult
	 * 
	 * @param SSID
	 * @return
	 */
	public Boolean addNetwork(ScanResult scanResult, String preSharedKey) {
		return addNetwork(scanResult.SSID, getScanResultSecurity(scanResult),
				preSharedKey);
	}

	/**
	 * Adds networks by a WifiConfiguration
	 * 
	 * @param SSID
	 * @return
	 */
	public Boolean addNetwork(WifiConfiguration wifiConfiguration,
			String preSharedKey) {
		return addNetwork(wifiConfiguration.SSID,
				getWifiConfigurationSecurity(wifiConfiguration), preSharedKey);
	}

	/**
	 * Adds open networks
	 * 
	 * @param SSID
	 * @return
	 */
	public Boolean addNetwork(String SSID) {
		return addNetwork(SSID, OPEN, null);
	}

	/**
	 * Adds non-open networks
	 * 
	 * @param SSID
	 * @param securityType
	 * @param preSharedKey
	 * @return
	 */
	public Boolean addNetwork(String SSID, String securityType,
			String preSharedKey) {

		WifiConfiguration config = new WifiConfiguration();
		config.SSID = convertToQuotedString(SSID);
		setupSecurity(config, securityType, preSharedKey);

		config.networkId = ssidToNetworkId(config.SSID);

		int id;

		if (config.networkId == -1) {
			Log.d(TAG, "ADDING: " + config.SSID + " | " + config.preSharedKey);
			id = mWifiManager.addNetwork(config);
		} else {
			Log.d(TAG, "UPDATING: " + config.SSID + " | " + config.preSharedKey);
			id = mWifiManager.updateNetwork(config);
		}

		if (id == -1) {
			Log.d(TAG, "Failed to add: " + config.SSID);
			return false;
		}

		if (!mWifiManager.saveConfiguration()) {
			Log.d(TAG, "Failed save: " + config.SSID);
			return false;
		}

		return true;

	}

	private static boolean isHex(CharSequence key) {
		if (key == null) {
			return false;
		}
		for (int i = key.length() - 1; i >= 0; i--) {
			char c = key.charAt(i);
			if (!(c >= '0' && c <= '9' || c >= 'A' && c <= 'F' || c >= 'a'
					&& c <= 'f')) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Check if wepKey is a valid hexadecimal string.
	 * 
	 * @param wepKey
	 *            the input to be checked
	 * @return true if the input string is indeed hex or empty. False if the
	 *         input string is non-hex or null.
	 */
	static boolean isHexWepKey(CharSequence wepKey) {
		if (wepKey == null) {
			return false;
		}
		final int len = wepKey.length();
		// WEP-40, WEP-104, and some vendors using 256-bit WEP (WEP-232?)
		return (len == 10 || len == 26 || len == 58) && isHex(wepKey);
	}

	/**
	 * Remove a network from the configured network lists, by its SSID
	 * 
	 * @param SSID
	 * @return
	 */
	public Boolean removeNetwork(String SSID) {
		Boolean result = false;

		if (!TextUtils.isEmpty(SSID)) {

			SSID = convertToQuotedString(SSID);

			List<WifiConfiguration> wifiList = mWifiManager
					.getConfiguredNetworks();

			if (wifiList != null) {
				for (WifiConfiguration wifiConfiguration : wifiList) {
					if (wifiConfiguration.SSID.equals(SSID)) {

						// Lollipop bug
						if (Build.VERSION.SDK_INT >= 21) {
							Log.d(TAG, "For lollipop, removing password only");
							wifiConfiguration.preSharedKey = "";
							if (wifiConfiguration.networkId != -1) {
								mWifiManager
										.disableNetwork(wifiConfiguration.networkId);
							}
						} else {
							result = mWifiManager
									.removeNetwork(wifiConfiguration.networkId);
						}

						mWifiManager.saveConfiguration();
						Log.d(TAG,
								"Network removed from the configuration list: "
										+ SSID);

						break;
					}
				}
			}
		}

		return result;
	}

	public Boolean connectToNetwork(ScanResult network, Boolean disableOthers) {
		return connectToNetwork(ssidToNetworkId(network.SSID), disableOthers);
	}

	public Boolean connectToNetwork(WifiConfiguration network,
			Boolean disableOthers) {
		return connectToNetwork(ssidToNetworkId(network.SSID), disableOthers);
	}

	public Boolean connectToNetwork(int netId, Boolean disableOthers) {

		Log.d(TAG, "Trying to connect to netId: " + netId);

		Boolean result;

		mWifiManager.disableNetwork(netId);

		result = mWifiManager.enableNetwork(netId, disableOthers);
		mWifiManager.reconnect();

		return result;
	}

	public static String convertToQuotedString(String string) {
		if (string == null) {
			return null;
		}
		if (TextUtils.isEmpty(string)) {
			return "";
		}
		int lastPos = string.length() - 1;
		if (lastPos < 0
				|| (string.charAt(0) == '"' && string.charAt(lastPos) == '"')) {
			return string;
		}
		return '\"' + string + '\"';
	}

	private int ssidToNetworkId(String SSID) {
		List<WifiConfiguration> currentNetworks = mWifiManager
				.getConfiguredNetworks();
		int networkId = -1;

		SSID = convertToQuotedString(SSID);

		if (currentNetworks != null) {
			for (WifiConfiguration test : currentNetworks) {

				if (test.SSID != null && test.SSID.equals(SSID)) {
					networkId = test.networkId;
					break;
				}
			}
		}

		return networkId;
	}

	public Boolean isCurrentlyConencted() {
		WifiInfo wifi = mWifiManager.getConnectionInfo();
		if (wifi == null || wifi.getBSSID() == null
				|| wifi.getBSSID().equals(INVALID_BSSID)) {
			return false;
		}
		return true;
	}

	/**
	 * Fill in the security fields of WifiConfiguration config.
	 * 
	 * @param config
	 *            The object to fill.
	 * @param security
	 *            If is OPEN, password is ignored.
	 * @param password
	 *            Password of the network if security is not OPEN.
	 */
	static private void setupSecurity(WifiConfiguration config,
			String security, final String password) {

		config.allowedAuthAlgorithms.clear();
		config.allowedGroupCiphers.clear();
		config.allowedKeyManagement.clear();
		config.allowedPairwiseCiphers.clear();
		config.allowedProtocols.clear();

		if (TextUtils.isEmpty(security)) {
			security = OPEN;
			Log.w(TAG, "Empty security, assuming open");
		}

		if (security.equals(WEP)) {
			int wepPasswordType = WEP_PASSWORD_AUTO;
			// If password is empty, it should be left untouched
			if (!TextUtils.isEmpty(password)) {
				if (wepPasswordType == WEP_PASSWORD_AUTO) {
					if (isHexWepKey(password)) {
						config.wepKeys[0] = password;
					} else {
						config.wepKeys[0] = convertToQuotedString(password);
					}
				} else {
					config.wepKeys[0] = wepPasswordType == WEP_PASSWORD_ASCII ? convertToQuotedString(password)
							: password;
				}
			}

			config.wepTxKeyIndex = 0;

			config.allowedAuthAlgorithms.set(AuthAlgorithm.OPEN);
			config.allowedAuthAlgorithms.set(AuthAlgorithm.SHARED);

			config.allowedKeyManagement.set(KeyMgmt.NONE);

			config.allowedGroupCiphers.set(GroupCipher.WEP40);
			config.allowedGroupCiphers.set(GroupCipher.WEP104);

		} else if (security.equals(WPA) || security.equals(WPA2)) {
			config.allowedGroupCiphers.set(GroupCipher.TKIP);
			config.allowedGroupCiphers.set(GroupCipher.CCMP);

			config.allowedKeyManagement.set(KeyMgmt.WPA_PSK);

			config.allowedPairwiseCiphers.set(PairwiseCipher.CCMP);
			config.allowedPairwiseCiphers.set(PairwiseCipher.TKIP);

			config.allowedProtocols.set(security.equals(WPA2) ? Protocol.RSN
					: Protocol.WPA);

			// If password is empty, it should be left untouched
			if (!TextUtils.isEmpty(password)) {
				if (password.length() == 64 && isHex(password)) {
					// Goes unquoted as hex
					config.preSharedKey = password;
				} else {
					// Goes quoted as ASCII
					config.preSharedKey = convertToQuotedString(password);
				}
			}

		} else if (security.equals(OPEN)) {
			config.allowedKeyManagement.set(KeyMgmt.NONE);
		} else if (security.equals(WPA_EAP) || security.equals(IEEE8021X)) {
			config.allowedGroupCiphers.set(GroupCipher.TKIP);
			config.allowedGroupCiphers.set(GroupCipher.CCMP);
			if (security.equals(WPA_EAP)) {
				config.allowedKeyManagement.set(KeyMgmt.WPA_EAP);
			} else {
				config.allowedKeyManagement.set(KeyMgmt.IEEE8021X);
			}
			if (!TextUtils.isEmpty(password)) {
				config.preSharedKey = convertToQuotedString(password);
			}
		}

	}

	/**
	 * @return The security of a given {@link WifiConfiguration}.
	 */
	static public String getWifiConfigurationSecurity(
			WifiConfiguration wifiConfig) {

		if (wifiConfig.allowedKeyManagement.get(KeyMgmt.NONE)) {
			// If we never set group ciphers, wpa_supplicant puts all of them.
			// For open, we don't set group ciphers.
			// For WEP, we specifically only set WEP40 and WEP104, so CCMP
			// and TKIP should not be there.

			// Comment out the first condition to be compatible with Android
			// 4.0(? Maybe 3.0??)
			if (// !wifiConfig.allowedGroupCiphers.get(GroupCipher.CCMP)
				// &&
			(wifiConfig.allowedGroupCiphers.get(GroupCipher.WEP40) || wifiConfig.allowedGroupCiphers
					.get(GroupCipher.WEP104))) {
				return WEP;
			} else {
				return OPEN;
			}
		} else if (wifiConfig.allowedProtocols.get(Protocol.RSN)) {
			return WPA2;
		} else if (wifiConfig.allowedKeyManagement.get(KeyMgmt.WPA_EAP)) {
			return WPA_EAP;
		} else if (wifiConfig.allowedKeyManagement.get(KeyMgmt.IEEE8021X)) {
			return IEEE8021X;
		} else if (wifiConfig.allowedProtocols.get(Protocol.WPA)) {
			return WPA;
		} else {
			Log.w(TAG,
					"Unknown security type from WifiConfiguration, falling back on open.");
			return OPEN;
		}
	}

	static final String[] SECURITY_MODES = { WEP, WPA, WPA2, WPA_EAP, IEEE8021X };

	/**
	 * @return The security of a given {@link ScanResult}.
	 */
	public static String getScanResultSecurity(ScanResult scanResult) {
		final String cap = scanResult.capabilities;
		for (int i = SECURITY_MODES.length - 1; i >= 0; i--) {
			if (cap.contains(SECURITY_MODES[i])) {
				return SECURITY_MODES[i];
			}
		}

		return OPEN;
	}

	private Dao getDatabase() {
		if (mDatabase == null) {
			mDatabase = Dao.getInstance(mContext, "wiflipdatabase");
			Log.d(TAG, "Database initialized");
		}
		return mDatabase;
	}

	public Cursor getNearbyNetworks(double longitude, double latitude) {

		String distanceQuery = buildDistanceQuery(latitude, longitude);

		Cursor cursor = getDatabase().getNetworks(null,
				"disabled is null OR disabled=''", null,
				distanceQuery + " DESC", "50");

		return cursor;

	}

	public static String buildDistanceQuery(double latitude, double longitude) {
		final double coslat = Math.cos(deg2rad(latitude));
		final double sinlat = Math.sin(deg2rad(latitude));
		final double coslng = Math.cos(deg2rad(longitude));
		final double sinlng = Math.sin(deg2rad(longitude));
		return "(" + coslat + "*coslat" + "*(coslng*" + coslng + "+sinlng*"
				+ sinlng + ")+" + sinlat + "*sinlat" + ")";
	}

	public static double deg2rad(double deg) {
		return (deg * Math.PI / 180.0);
	}

	public static double convertPartialDistanceToKm(double result) {
		return Math.acos(result) * 6371;
	}

}
