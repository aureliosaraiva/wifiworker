package com.nplaceit.cordovaplugins.wifiworker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiManager;
import android.util.Log;

public class WifiBroadcastReceiver extends BroadcastReceiver {

	private static final String TAG = WifiBroadcastReceiver.class
			.getSimpleName();

	WifiManager mWifiManager = null;
	Intent wifiService;
	Context mContext = null;

	@Override
	public void onReceive(Context context, Intent intent) {

		mContext = context;

		wifiService = new Intent(context, WifiService.class);

		String action = intent.getAction();

		if (action.equals(WifiManager.WIFI_STATE_CHANGED_ACTION)) {

			int state = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, -1);

			wifiService.setAction(action);
			wifiService.putExtra("state", state);

			context.startService(wifiService);

		} else if (action.equals(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)) {
			wifiService.setAction(action);
			context.startService(wifiService);
		} else if (action.equals(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION)) {

			SupplicantState supplicantState = ((SupplicantState) intent
					.getParcelableExtra(WifiManager.EXTRA_NEW_STATE));

			switch (supplicantState) {
			case ASSOCIATED:
				Log.i(TAG, "SupplicantState: ASSOCIATED");
				break;
			case ASSOCIATING:
				Log.i(TAG, "SupplicantState: ASSOCIATING");
				wifiService.setAction("ASSOCIATING");
				context.startService(wifiService);
				break;
			case AUTHENTICATING:
				Log.i(TAG, "SupplicantState: Authenticating...");
				break;
			case COMPLETED:
				Log.i(TAG, "SupplicantState: Connected");

				wifiService.setAction("SUCCESS_AUTHENTICATING");
				context.startService(wifiService);

				break;
			case DISCONNECTED:
				Log.i(TAG, "SupplicantState: Disconnected");
				wifiService.setAction("DISCONNECTED");
				context.startService(wifiService);
				break;
			case DORMANT:
				Log.i(TAG, "SupplicantState: DORMANT");
				break;
			case FOUR_WAY_HANDSHAKE:
				Log.i(TAG, "SupplicantState: FOUR_WAY_HANDSHAKE");
				break;
			case GROUP_HANDSHAKE:
				Log.i(TAG, "SupplicantState: GROUP_HANDSHAKE");
				break;
			case INACTIVE:
				Log.i(TAG, "SupplicantState: INACTIVE");
				break;
			case INTERFACE_DISABLED:
				Log.i(TAG, "SupplicantState: INTERFACE_DISABLED");
				break;
			case INVALID:
				Log.i(TAG, "SupplicantState: INVALID");
				break;
			case SCANNING:
				Log.i(TAG, "SupplicantState: SCANNING");
				break;
			case UNINITIALIZED:
				Log.i(TAG, "SupplicantState: UNINITIALIZED");
				break;
			default:
				Log.i(TAG, "SupplicantState: Unknown");
				break;
			}

			int supl_error = intent.getIntExtra(
					WifiManager.EXTRA_SUPPLICANT_ERROR, -1);

			if (supl_error == WifiManager.ERROR_AUTHENTICATING) {

				Log.i(TAG, "SupplicantState: ERROR_AUTHENTICATING");

				wifiService.setAction("ERROR_AUTHENTICATING");
				context.startService(wifiService);
			}

		}

	}
}