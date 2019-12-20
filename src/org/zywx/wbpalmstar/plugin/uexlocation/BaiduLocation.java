package org.zywx.wbpalmstar.plugin.uexlocation;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import com.baidu.location.BDLocation;
import com.baidu.location.BDLocationListener;
import com.baidu.location.LocationClient;
import com.baidu.location.LocationClientOption;

import org.zywx.wbpalmstar.base.BDebug;

import java.util.ArrayList;

public class BaiduLocation {

    private static final String TAG = "BaiduLocation";

    static final String LOCAL = "BaiduLocSdkInfo";
    static final String LAST_LAT = "last_lat";
    static final String LAST_LOG = "last_log";
    static final String LAST_RAD = "last_rad";
    static final String coor_gcj02 = "gcj02";
    static final String coor_bd09 = "bd09";
    static final String coor_bd09ll = "bd09ll";
    static final String coor_default = coor_bd09ll;

    static final long STEP = 1000L * 60 * 1;
    static final int INTERVAL = 1000 * 30;

    private static BaiduLocation instance;

    private LocationClient mLocClient;
    private MyLocationListener mListener;
    private SharedPreferences mLocalInfo;
    private long mLastLocationTime;
    private ArrayList<LocationCallback> mCallbackList;

    private BaiduLocation(Context ctx) {
        mLocalInfo = ctx.getSharedPreferences(LOCAL, Context.MODE_MULTI_PROCESS);
        mCallbackList = new ArrayList<LocationCallback>();
        initLocation(ctx.getApplicationContext());
    }

    public static BaiduLocation get(Context ctx) {
        if (null == instance) {
            instance = new BaiduLocation(ctx);
        }
        return instance;
    }

    public void openLocation(LocationCallback callback) {
        if (!mCallbackList.contains(callback)) {
            mCallbackList.add(callback);
        }
        if (!mLocClient.isStarted()) {
            mLocClient.start();
        } else {
            requestLocation();
        }
    }

    public void closeLocation(LocationCallback callback) {
        mCallbackList.remove(callback);
        if (0 == mCallbackList.size()) {
            mLocClient.stop();
        }
    }

    private void initLocation(Context ctx) {
        mLocClient = new LocationClient(ctx);
        mListener = new MyLocationListener();
        mLocClient.registerLocationListener(mListener);
        LocationClientOption option = new LocationClientOption();
        option.setOpenGps(true);
        option.setIsNeedAddress(true);
        option.setCoorType(coor_default);
        option.setScanSpan(INTERVAL);
        mLocClient.setLocOption(option);
    }

    private void saveLastLoc(double lat, double log, float radius) {
        Editor edit = mLocalInfo.edit();
        edit.putString(LAST_LAT, Double.toString(lat));
        edit.putString(LAST_LOG, Double.toString(log));
        edit.putString(LAST_RAD, Float.toString(radius));
        edit.commit();
    }

    private int requestLocation() {
        int result = 6;
        long now = System.currentTimeMillis();
        double llat = getLastLat();
        double llog = getLastLog();
        float lrad = getLastRad();
        if ((now - mLastLocationTime) < STEP && (llat > 0 && llog > 0)) {
            innerLocCallback(llat, llog, lrad);
        } else {
            return asyLocation();
        }
        return result;
    }

    /**
     * 0：正常<br>
     * 1：SDK还未启动<br>
     * 2：没有监听函数<br>
     * 6：请求间隔过短<br>
     *
     * @return code
     */
    private int asyLocation() {
        int result = mLocClient.requestLocation();
        return result;
    }

    private double getLastLat() {
        double result = 0;
        String lat = mLocalInfo.getString(LAST_LAT, null);
        if (null != lat) {
            result = Double.parseDouble(lat);
        }
        return result;
    }

    private double getLastLog() {
        double result = 0;
        String log = mLocalInfo.getString(LAST_LOG, null);
        if (null != log) {
            result = Double.parseDouble(log);
        }
        return result;
    }

    private float getLastRad() {
        float result = 0;
        String radius = mLocalInfo.getString(LAST_RAD, null);
        if (null != radius) {
            result = Float.parseFloat(radius);
        }
        return result;
    }

    private void innerLocCallback(double lat, double log, float radius) {
        for (LocationCallback back : mCallbackList) {
            back.onLocation(lat, log, radius);
        }
    }

    /**
     * 61: GPS<br>
     * 65: 定位缓存的结果<br>
     * 66: 离线定位结果.通过requestOfflineLocaiton调用时对应的返回结果<br>
     * 68: 网络连接失败时,查找本地离线定位时对应的返回结果<br>
     * 62: 扫描整合定位依据失败.此时定位结果无效<br>
     * 63: 网络异常,没有成功向服务器发起请求.此时定位结果无效<br>
     * 67: 离线定位失败.通过requestOfflineLocaiton调用时对应的返回结果<br>
     * 161: 表示网络定位结果<br>
     * 162: 服务端定位失败<br>
     * 163: 服务端定位失败<br>
     * 164: 服务端定位失败<br>
     * 165: 服务端定位失败<br>
     * 166: 服务端定位失败<br>
     * 167: 服务端定位失败<br>
     */
    private class MyLocationListener implements BDLocationListener {

        public MyLocationListener() {
            ;
        }

        @Override
        public void onReceiveLocation(BDLocation location) {
            double dLat = 0;
            double dLog = 0;
            float dRadius = 0;
            if (location != null) {
                boolean locOk = true;
                int type = location.getLocType();
                switch (type) {
                    case 61:  //GPS
                    case 65:  //定位缓存的结果
                    case 66:  //离线定位结果.通过requestOfflineLocaiton调用时对应的返回结果
                    case 68:  //网络连接失败时,查找本地离线定位时对应的返回结果
                    case 161: //表示网络定位结果
                        locOk = true;
                        break;
                    case 62:  //扫描整合定位依据失败.此时定位结果无效。
                    case 63:  //网络异常,没有成功向服务器发起请求.此时定位结果无效
                    case 67: //离线定位失败.通过requestOfflineLocaiton调用时对应的返回结果
                    case 162: //服务端定位失败.
                    case 163: //服务端定位失败.
                    case 164: //服务端定位失败.
                    case 165: //服务端定位失败.
                    case 166: //服务端定位失败.
                    case 167: //服务端定位失败.
                        locOk = false;
                        break;
                }
                if (locOk) {
                    dLat = location.getLatitude();
                    dLog = location.getLongitude();
                    dRadius = location.getRadius();
                    saveLastLoc(dLat, dLog, dRadius);
                    mLastLocationTime = System.currentTimeMillis();
                }else{
                    BDebug.e(TAG, "onReceiveLocation Error TypeCode: " + type);
                }
            }
            innerLocCallback(dLat, dLog, dRadius);
        }

    }
}