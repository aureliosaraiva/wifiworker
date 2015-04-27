package com.nplaceit.cordovaplugins.wifiworker;

import java.io.File;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.net.wifi.ScanResult;
import android.text.TextUtils;
import android.util.Log;

public class Dao {

	private static final String TAG = "DAO";

	private static final String SERVICE_STATE_TABLE = "service_state";
	private static final String SERVICE_STATE_COLUMN_STATE = "current_state";
	private static final String SERVICE_STATE_COLUMN_STATE_TIME = "state_time";
	private static final String SERVICE_STATE_COLUMN_CURRENT_BSSID = "current_bssid";
	private static final String SERVICE_STATE_COLUMN_CURRENT_SSID = "current_ssid";

	private static Dao daoInstance = null;

	private Context mContext;
	private SQLiteDatabase mSQLiteDatabase = null;

	private Boolean databaseIsReady = false;

	Dao(Context context, String dbname) {
		mContext = context;
		this.openDatabase(dbname);
	}

	public static synchronized Dao getInstance(Context context, String dbname) {
		if (daoInstance == null) {
			daoInstance = new Dao(context, dbname);
		}
		return daoInstance;
	}

	public void setCurrentConnectingNetwork(ScanResult scanResult) {

		ContentValues args = new ContentValues();

		args.put(SERVICE_STATE_COLUMN_CURRENT_BSSID, scanResult.BSSID);
		args.put(SERVICE_STATE_COLUMN_CURRENT_SSID, scanResult.SSID);

		int affected = mSQLiteDatabase.update(SERVICE_STATE_TABLE, args, null,
				null);

		if (affected == 0) {
			mSQLiteDatabase.insert(SERVICE_STATE_TABLE, null, args);
		}

	}

	public void removeCurrentConnectingNetwork() {

		ContentValues args = new ContentValues();

		args.put(SERVICE_STATE_COLUMN_CURRENT_BSSID, "");
		args.put(SERVICE_STATE_COLUMN_CURRENT_SSID, "");

		mSQLiteDatabase.update(SERVICE_STATE_TABLE, args, null, null);

	}

	private void openDatabase(String dbname) {

		if (mSQLiteDatabase == null) {

			File dbfile = mContext.getDatabasePath(dbname);

			if (!dbfile.exists()) {
				dbfile.getParentFile().mkdirs();
			}

			mSQLiteDatabase = SQLiteDatabase.openOrCreateDatabase(dbfile, null);

		} else {
			Log.d(TAG, "DATABASE IS ALREADY OPEN!");
		}

	}

	public void closeDatabase() {
		mSQLiteDatabase.close();
	}

	/**
	 * The database is created by the cordova-side code, so this methods ensures
	 * that cordova has done it
	 * 
	 * @return if the servuce_state table does exists
	 */
	public Boolean isDatabaseReady() {
		if (databaseIsReady == false) {
			databaseIsReady = tableExists(SERVICE_STATE_TABLE);
		}
		return databaseIsReady;
	}

	private Boolean tableExists(String tableName) {
		Cursor cursor = mSQLiteDatabase.rawQuery(
				"select DISTINCT tbl_name from sqlite_master where tbl_name = '"
						+ tableName + "'", null);

		if (cursor != null) {
			if (cursor.getCount() > 0) {
				cursor.close();
				return true;
			}
			cursor.close();
		}
		return false;
	}

	public Cursor getHotspotInfo(String bSSID) {
		Cursor cursor = null;
		try {
			cursor = mSQLiteDatabase
					.rawQuery(
							"SELECT bssid, ssid, senha FROM hotspot WHERE bssid=? AND (disabled is null OR disabled!=1)",
							new String[] { bSSID });
		} catch (SQLiteException e) {
			Log.d(TAG, e.getMessage());
		}

		return cursor;
	}

	/**
	 * Fetches the current state of service
	 * 
	 * @return
	 */
	public int getServiceState() {

		int currentState = WifiService.STATE_UNKNOWN;
		String currentSeconds = Long
				.toString(System.currentTimeMillis() / 1000);

		Cursor cursor = null;

		/**
		 * Only accepts current_state that occurred 30 seconds from now
		 */
		cursor = mSQLiteDatabase.rawQuery("SELECT * FROM "
				+ SERVICE_STATE_TABLE + " WHERE " + currentSeconds + " - "
				+ SERVICE_STATE_COLUMN_STATE_TIME + "<30", null);

		if (cursor != null) {
			if (cursor.moveToFirst()) {
				currentState = cursor.getInt(cursor
						.getColumnIndex(SERVICE_STATE_COLUMN_STATE));
			}
		}

		if (currentState == WifiService.STATE_UNKNOWN) {
			currentState = WifiService.STATE_NONE;
		}

		return currentState;

	}

	public void setServiceState(int state) {

		ContentValues args = new ContentValues();

		args.put(SERVICE_STATE_COLUMN_STATE, state);
		args.put(SERVICE_STATE_COLUMN_STATE_TIME,
				Long.toString(System.currentTimeMillis() / 1000));

		int affected = mSQLiteDatabase.update(SERVICE_STATE_TABLE, args, null,
				null);

		if (affected == 0) {
			mSQLiteDatabase.insert(SERVICE_STATE_TABLE, null, args);
		}

	}

	public String getCurrentBSSID() {
		Cursor cursor = null;
		String currentBSSID = null;

		cursor = mSQLiteDatabase.rawQuery("SELECT "
				+ SERVICE_STATE_COLUMN_CURRENT_BSSID + " FROM "
				+ SERVICE_STATE_TABLE, null);

		if (cursor != null) {
			if (cursor.moveToFirst()) {
				currentBSSID = cursor.getString(cursor
						.getColumnIndex(SERVICE_STATE_COLUMN_CURRENT_BSSID));
			}
		}

		return currentBSSID;

	}

	public String getCurrentSSID() {
		Cursor cursor = null;
		String currentSSID = null;

		cursor = mSQLiteDatabase.rawQuery("SELECT "
				+ SERVICE_STATE_COLUMN_CURRENT_SSID + " FROM "
				+ SERVICE_STATE_TABLE, null);

		if (cursor != null) {
			if (cursor.moveToFirst()) {
				currentSSID = cursor.getString(cursor
						.getColumnIndex(SERVICE_STATE_COLUMN_CURRENT_SSID));
			}
		}

		return currentSSID;

	}

	/**
	 * When the authentication failed, this should be called
	 * 
	 * @param BSSID
	 * @return
	 */
	public int markNetworkAsFailed(String BSSID) {

		ContentValues values = new ContentValues();
		int affected;

		values.put("disabled", 1);

		affected = mSQLiteDatabase.update("hotspot", values, "bssid=?",
				new String[] { BSSID });

		Log.d(TAG, "Network saved as BAD");

		return affected;

	}

	/**
	 * Fetches networks from the database
	 * 
	 * @param columns
	 * @param selection
	 * @param selectionArgs
	 * @return
	 */
	public Cursor getNetworks(String columns, String selection,
			String[] selectionArgs, String order, String limit) {

		Cursor cursor;
		String queryString;

		if (columns == null || TextUtils.isEmpty(columns)) {
			columns = "*";
		}

		queryString = "SELECT " + columns + " FROM hotspot";

		if (selection != null && !TextUtils.isEmpty(selection)) {
			queryString = queryString.concat(" WHERE ").concat(selection);
		}

		if (order != null && !TextUtils.isEmpty(order)) {
			queryString = queryString.concat(" ORDER BY ").concat(order);
		}

		if (limit != null && !TextUtils.isEmpty(limit)) {
			queryString = queryString.concat(" LIMIT ").concat(limit);
		}

		cursor = mSQLiteDatabase.rawQuery(queryString, selectionArgs);

		return cursor;

	}

	public boolean isNotificationEnabled() {

		Cursor cursor = null;
		Boolean enabled = false;

		cursor = mSQLiteDatabase.rawQuery(
				"SELECT notifications FROM definitions", null);

		if (cursor != null) {
			if (cursor.moveToFirst()) {
				enabled = cursor.getInt(0) > 0;
			}
		}

		return enabled;
	}

}
