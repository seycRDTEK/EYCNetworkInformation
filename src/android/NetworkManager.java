/*
 Licensed to the Apache Software Foundation (ASF) under one
 or more contributor license agreements.  See the NOTICE file
 distributed with this work for additional information
 regarding copyright ownership.  The ASF licenses this file
 to you under the Apache License, Version 2.0 (the
 "License"); you may not use this file except in compliance
 with the License.  You may obtain a copy of the License at
 
 http://www.apache.org/licenses/LICENSE-2.0
 
 Unless required by applicable law or agreed to in writing,
 software distributed under the License is distributed on an
 "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 KIND, either express or implied.  See the License for the
 specific language governing permissions and limitations
 under the License.
 */
package org.apache.cordova.networkinformation;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.apache.cordova.CordovaWebView;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

import android.app.Activity;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;

public class NetworkManager extends CordovaPlugin {
    
    public static int NOT_REACHABLE = 0;
    public static int REACHABLE_VIA_CARRIER_DATA_NETWORK = 1;
    public static int REACHABLE_VIA_WIFI_NETWORK = 2;
    
    public static final String WIFI = "wifi";
    public static final String WIMAX = "wimax";
    // mobile
    public static final String MOBILE = "mobile";
    
    // Android L calls this Cellular, because I have no idea!
    public static final String CELLULAR = "cellular";
    // 2G network types
    public static final String GSM = "gsm";
    public static final String GPRS = "gprs";
    public static final String EDGE = "edge";
    // 3G network types
    public static final String CDMA = "cdma";
    public static final String UMTS = "umts";
    public static final String HSPA = "hspa";
    public static final String HSUPA = "hsupa";
    public static final String HSDPA = "hsdpa";
    public static final String ONEXRTT = "1xrtt";
    public static final String EHRPD = "ehrpd";
    // 4G network types
    public static final String LTE = "lte";
    public static final String UMB = "umb";
    public static final String HSPA_PLUS = "hspa+";
    // return type
    public static final String TYPE_UNKNOWN = "unknown";
    public static final String TYPE_ETHERNET = "ethernet";
    public static final String TYPE_WIFI = "wifi";
    public static final String TYPE_2G = "2g";
    public static final String TYPE_3G = "3g";
    public static final String TYPE_4G = "4g";
    public static final String TYPE_NONE = "none";
    
    private static final String LOG_TAG = "NetworkManager";
    
    private CallbackContext connectionCallbackContext;
    
    ConnectivityManager sockMan;
    BroadcastReceiver receiver;
    private String lastInfo = null;
    
    private final int levels = 32;
    
    /**
     * Sets the context of the Command. This can then be used to do things like
     * get file paths associated with the Activity.
     *
     * @param cordova The context of the main Activity.
     * @param webView The CordovaWebView Cordova is running in.
     */
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
        this.sockMan = (ConnectivityManager) cordova.getActivity().getSystemService(Context.CONNECTIVITY_SERVICE);
        this.connectionCallbackContext = null;
        
        // We need to listen to connectivity events to update navigator.connection
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        intentFilter.addAction(WifiManager.RSSI_CHANGED_ACTION);
        if (this.receiver == null) {
            this.receiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    // (The null check is for the ARM Emulator, please use Intel Emulator for better results)
                    if(NetworkManager.this.webView != null)
                        updateConnectionInfo(sockMan.getActiveNetworkInfo());
                }
            };
            webView.getContext().registerReceiver(this.receiver, intentFilter);
        }
        
    }
    
    /**
     * Executes the request and returns PluginResult.
     *
     * @param action            The action to execute.
     * @param args              JSONArry of arguments for the plugin.
     * @param callbackContext   The callback id used when calling back into JavaScript.
     * @return                  True if the action was valid, false otherwise.
     */
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) {
        if (action.equals("getConnectionInfo")) {
            this.connectionCallbackContext = callbackContext;
            NetworkInfo info = sockMan.getActiveNetworkInfo();
            String connectionType = "";
            //try {
            connectionType = this.getConnectionInfo(info);
            //} catch (JSONException e) { }
            
            PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, connectionType);
            pluginResult.setKeepCallback(true);
            callbackContext.sendPluginResult(pluginResult);
            return true;
        }
        return false;
    }
    
    /**
     * Stop network receiver.
     */
    public void onDestroy() {
        if (this.receiver != null) {
            try {
                webView.getContext().unregisterReceiver(this.receiver);
            } catch (Exception e) {
                Log.e(LOG_TAG, "Error unregistering network receiver: " + e.getMessage(), e);
            } finally {
                receiver = null;
            }
        }
    }
    
    //--------------------------------------------------------------------------
    // LOCAL METHODS
    //--------------------------------------------------------------------------
    
    /**
     * Updates the JavaScript side whenever the connection changes
     *
     * @param info the current active network info
     * @return
     */
    private void updateConnectionInfo(NetworkInfo info) {
        // send update to javascript "navigator.network.connection"
        // Jellybean sends its own info
        String thisInfo = this.getConnectionInfo(info);
        if(!thisInfo.equals(lastInfo))
        {
            sendUpdate(thisInfo);
            lastInfo = thisInfo;
        }
        
    }
    
    /**
     * Get the latest network connection information
     *
     * @param info the current active network info
     * @return a JSONObject that represents the network info
     */
    private String getConnectionInfo(NetworkInfo info) {
        String type = TYPE_NONE;
        String extraInfo = "\"\"";
        //This is added by SEYC
        String signalStrength = "0";
        
        if (info != null) {
            // If we are not connected to any network set type to none
            if (!info.isConnected()) {
                type = TYPE_NONE;
            }
            else {
                type = getType(info);
                if(info.getTypeName().equalsIgnoreCase("wifi")){
                    signalStrength = getWifiSignalStrengthPercentage(this.cordova.getActivity()).toString();
                }
                else{
                    signalStrength = "100";
                }
                if (info.getExtraInfo() != null){
                    extraInfo = info.getExtraInfo();
                }
            }
        }
        
        Log.d("CordovaNetworkManager", "Connection Type: " + type);
        Log.d("CordovaNetworkManager", "Connection Extra Info: " + extraInfo);
        Log.d("CordovaNetworkManager", "Connection signalStrength: " + signalStrength);
        
        String connectionInfo = "{";
        connectionInfo = connectionInfo + "\"type\":\""+type+"\",";
        // extraInfo got " (double quotes) at the start and the end
        connectionInfo = connectionInfo + "\"extraInfo\":"+extraInfo+",";
        connectionInfo = connectionInfo + "\"signalStrength\":\""+signalStrength+"\"";
        connectionInfo = connectionInfo + "}";
        
        return connectionInfo;
    }
    
    //This is added by SEYC
    /**
     *
     * @param activity - Cordova Activity object
     * @return int - Wifi signal strength (in asu)
     */
    public int getWifiSignalStrength(Activity activity){
        int MIN_RSSI        = -100;
        int MAX_RSSI        = -55;
        
        WifiManager wifiManager = (WifiManager) activity.getSystemService(Context.WIFI_SERVICE);
        WifiInfo info = wifiManager.getConnectionInfo();
        int rssi = info.getRssi();
        
        int version = 1;
        try{
            version = android.os.Build.VERSION.SDK_INT;
        }
        catch(Exception ex){
            ex.printStackTrace();
        }
        /*In SDK versions before Android 4.0, there is a bug in calucluateSignalLevel method. hence a conditional check is made to use
         * use calucluateSignalLevel method only for SDK versions > =Android 4.0
         * For previous SDK versions the corrected code (from newer versions of Android) is used directly*/
        if (version >= android.os.Build.VERSION_CODES.ICE_CREAM_SANDWICH){
            return WifiManager.calculateSignalLevel(rssi, levels);
        } else {
            // this is the code since 4.0.1
            if (rssi <= MIN_RSSI) {
                return 0;
            } else if (rssi >= MAX_RSSI) {
                return levels - 1;
            } else {
                float inputRange = (MAX_RSSI - MIN_RSSI);
                float outputRange = (levels - 1);
                return (int)((float)(rssi - MIN_RSSI) * outputRange / inputRange);
            }
        }
    }
    
    /**
     * Converts WiFi signal strength from ASU to percentage
     * @param activity - Cordova Activity object
     * @return int - Wifi signal strength in percentage
     */
    public Integer getWifiSignalStrengthPercentage(Activity activity){
        Integer signalLevel = new Integer(getWifiSignalStrength(activity));
        return (Integer)(signalLevel * 100 / (levels - 1));
    }
    
    /**
     * Create a new plugin result and send it back to JavaScript
     *
     * @param connection the network info to set as navigator.connection
     */
    private void sendUpdate(String info) {
        if (connectionCallbackContext != null) {
            PluginResult result = new PluginResult(PluginResult.Status.OK, info);
            result.setKeepCallback(true);
            connectionCallbackContext.sendPluginResult(result);
        }
        webView.postMessage("networkconnection", info);
    }
    
    /**
     * Determine the type of connection
     *
     * @param info the network info so we can determine connection type.
     * @return the type of mobile network we are on
     */
    private String getType(NetworkInfo info) {
        if (info != null) {
            String type = info.getTypeName();
            
            if (type.toLowerCase().equals(WIFI)) {
                return TYPE_WIFI;
            }
            else if (type.toLowerCase().equals(MOBILE) || type.toLowerCase().equals(CELLULAR)) {
                type = info.getSubtypeName();
                if (type.toLowerCase().equals(GSM) ||
                    type.toLowerCase().equals(GPRS) ||
                    type.toLowerCase().equals(EDGE)) {
                    return TYPE_2G;
                }
                else if (type.toLowerCase().startsWith(CDMA) ||
                         type.toLowerCase().equals(UMTS) ||
                         type.toLowerCase().equals(ONEXRTT) ||
                         type.toLowerCase().equals(EHRPD) ||
                         type.toLowerCase().equals(HSUPA) ||
                         type.toLowerCase().equals(HSDPA) ||
                         type.toLowerCase().equals(HSPA)) {
                    return TYPE_3G;
                }
                else if (type.toLowerCase().equals(LTE) ||
                         type.toLowerCase().equals(UMB) ||
                         type.toLowerCase().equals(HSPA_PLUS)) {
                    return TYPE_4G;
                }
            }
        }
        else {
            return TYPE_NONE;
        }
        return TYPE_UNKNOWN;
    }
}
