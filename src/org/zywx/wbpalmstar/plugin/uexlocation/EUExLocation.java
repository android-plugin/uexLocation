package org.zywx.wbpalmstar.plugin.uexlocation;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.widget.Toast;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.json.JSONException;
import org.json.JSONObject;
import org.zywx.wbpalmstar.engine.EBrowserActivity;
import org.zywx.wbpalmstar.engine.EBrowserView;
import org.zywx.wbpalmstar.engine.universalex.EUExBase;
import org.zywx.wbpalmstar.engine.universalex.EUExCallback;


public class EUExLocation extends EUExBase{
    private static final String TAG = "EUExLocation";

	public static final String onFunction 	= "uexLocation.onChange";
	public static final String functiong 	= "uexLocation.cbGetAddress";
	public static final String functionl 	= "uexLocation.cbOpenLocation";

    public static final String WGS84 = "wgs84";
    public static final String BD09 = "bd09";
    public static final String GCJ02 = "gcj02";


	protected static int count = -1;
	protected int mMyId;
	private LCallback mLCallback;
	private QueryTask mQueryTask;
	private String getAddressFunId;
	private String openLocationFunId;
    private String defaultType = BD09;

    private String[] openLocationParams;

	public EUExLocation(Context context, EBrowserView inParent) {
		super(context, inParent);
		count++;
		mMyId = count;
		mLCallback = new LCallback();
	}

	public void openLocation(String[] parm) {
		openLocationParams = parm;
		// android6.0以上动态权限申请
		if (mContext.checkCallingOrSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
				!= PackageManager.PERMISSION_GRANTED){
			requsetPerssions(Manifest.permission.ACCESS_FINE_LOCATION, "请先申请权限"
					+ Manifest.permission.ACCESS_FINE_LOCATION, 1);
		} else {
			if (!checkSetting()) {
				if (parm.length > 0) {
					openLocationFunId = parm[parm.length - 1];
					callbackToJs(Integer.parseInt(openLocationFunId), false, EUExCallback.F_C_FAILED);
				} else {
					jsCallback(functionl, 0, EUExCallback.F_C_INT, EUExCallback.F_C_FAILED);
				}
			} else {
				if (parm.length > 0) {
					openLocationFunId = parm[parm.length - 1];
					callbackToJs(Integer.parseInt(openLocationFunId), false, EUExCallback.F_C_SUCCESS);
					if (parm.length == 2) {
						defaultType = parm[0]; //设置类型
					}
				} else {
					jsCallback(functionl, 0, EUExCallback.F_C_INT, EUExCallback.F_C_SUCCESS);
				}
			}
			BaiduLocation bdl = BaiduLocation.get(mContext);
			bdl.openLocation(mLCallback);
		}
	}

	public void getAddress(String[] parm) {
		if(parm.length < 3){
			return;
		}
		String inLatitude = parm[0];
		String inLongitude = parm[1];
        if (parm.length == 4) {
            getAddressFunId = parm[3];
        }

		int flag = 0;

        try {
            flag = Integer.valueOf(parm[2]);
        } catch (NumberFormatException e) {
            e.printStackTrace();
        }

		if(null != mQueryTask){
			mQueryTask.shutDown();
			mQueryTask = null;
		}
		mQueryTask = new QueryTask(inLatitude, inLongitude, flag);
		mQueryTask.start();
	}

    /**
     * 坐标转换
     * @param params
     */
    public String convertLocation(String [] params) {
        if (params == null &&  params.length < 1) {
            errorCallback(0, 0, "error params!");
            return null;
        }
        JSONObject jsonObject;
        try {
            jsonObject = new JSONObject(params[0]);
            double latitude = jsonObject.optDouble("latitude", 0.0);
            double longitude = jsonObject.optDouble("longitude", 0.0);

            String from = jsonObject.optString("from", "");
            String to = jsonObject.optString("to", "");
            boolean isValid =  (WGS84.equalsIgnoreCase(from) || BD09.equalsIgnoreCase(from) || GCJ02.equalsIgnoreCase(from))
                    && (WGS84.equalsIgnoreCase(to) || BD09.equalsIgnoreCase(to) || GCJ02.equalsIgnoreCase(to));
            if (!isValid) {
                Log.i(TAG, "invalid params");
                return null;
            }
            double [] result = null;
            if (WGS84.equalsIgnoreCase(from) && BD09.equalsIgnoreCase(to)) {
                result = CoordTransform.WGS84ToBD09(longitude, latitude);
            } else if (WGS84.equalsIgnoreCase(from) && GCJ02.equalsIgnoreCase(to)) {
                result = CoordTransform.WGS84ToGCJ02(longitude, latitude);
            } else if (BD09.equalsIgnoreCase(from) && GCJ02.equalsIgnoreCase(to)) {
                result = CoordTransform.BD09ToGCJ02(longitude, latitude);
            } else if (BD09.equalsIgnoreCase(from) && WGS84.equalsIgnoreCase(to)) {
                result = CoordTransform.BD09ToWGS84(longitude, latitude);
            } else if (GCJ02.equalsIgnoreCase(from) && WGS84.equalsIgnoreCase(to)) {
                result = CoordTransform.GCJ02ToWGS84(longitude, latitude);
            } else if (GCJ02.equalsIgnoreCase(from) && BD09.equalsIgnoreCase(to)) {
                result = CoordTransform.GCJ02ToBD09(longitude, latitude);
            } else {
                //如果传入的from, to 非法，则不处理
                result = new double[2];
                result[0] = longitude;
                result[1] = latitude;
            }
            if (result != null) {
                JSONObject resultObj = new JSONObject();
                resultObj.put("longitude", result[0]);
                resultObj.put("latitude", result[1]);
                return resultObj.toString();
            }
        } catch (JSONException e) {
            Log.i(TAG, "JSONException:" + e.getMessage());
        }
        return null;
    }

	public void closeLocation(String[] parm) {
		BaiduLocation bdl = BaiduLocation.get(mContext);
		bdl.closeLocation(mLCallback);
	}

	private boolean checkSetting(){
		try{
			LocationManager lm = (LocationManager)mContext.getSystemService(Context.LOCATION_SERVICE);
			boolean gpsEnable = lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
			boolean netEnable = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER);

			ConnectivityManager cm = (ConnectivityManager)mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
			NetworkInfo networkInfos = cm.getActiveNetworkInfo();
			boolean net = false;
			boolean wifi = false;
			if(networkInfos != null){
				net = networkInfos.getState() == NetworkInfo.State.CONNECTED;
				wifi = networkInfos.getType() == ConnectivityManager.TYPE_WIFI;
			}
			return gpsEnable || netEnable || net || wifi;
		}catch (Exception e) {
			;
		}
		return false;
	}

	@Override
	public boolean clean() {
		BaiduLocation bdl = BaiduLocation.get(mContext);
		bdl.closeLocation(mLCallback);
		if(null != mQueryTask){
			mQueryTask.shutDown();
			mQueryTask = null;
		}
		return true;
	}

	private class QueryTask extends Thread{

		public String mLatitude;
		public String mLongitude;
		public HttpGet mHttpGet;
		public DefaultHttpClient mHttpClient;
		private boolean mShutDown;
		private int mFlag;

		public QueryTask(String inLat, String inLon, int flag){
			mLatitude = inLat;
			mLongitude = inLon;
			mFlag = flag;
		}

		@Override
		public void run() {
			try {
                mHttpGet = new HttpGet("http://api.map.baidu.com/geocoder/v2/?output=json&ak=lqAjltjYASwZRLQnOj2aTA9Y&location=" + mLatitude + "," + mLongitude);
                mHttpClient = new DefaultHttpClient();
				HttpResponse response = mHttpClient.execute(mHttpGet);
				int responseCode = response.getStatusLine().getStatusCode();
				if (responseCode == HttpStatus.SC_OK) {
					HttpEntity httpEntity = response.getEntity();
					String charSet = EntityUtils.getContentCharSet(httpEntity);
					if (null == charSet) {
						charSet = "UTF-8";
					}
					String str = new String(EntityUtils.toByteArray(httpEntity), charSet);
					if(mShutDown){
						return;
					}
					JSONObject json = new JSONObject(str);
					String status = json.getString("status");
					if(!"0".equals(status)){
						if (null != getAddressFunId) {
							callbackToJs(Integer.parseInt(getAddressFunId), false, EUExCallback.F_C_FAILED,  json.getString("message"));
						}else{
							errorCallback(0, EUExCallback.F_E_UEXlOCATION_GETADDRESS, json.getString("message"));
						}
						return;
					}
					JSONObject result = json.getJSONObject("result");
					if(mFlag == 1){
						JSONObject cbResult = new JSONObject();
						if(result.has("formatted_address")){
							cbResult.put("formatted_address", result.getString("formatted_address"));
						}
						if(result.has("addressComponent")){
							cbResult.put("addressComponent", result.getJSONObject("addressComponent"));
						}
						if(result.has("location")){
							cbResult.put("location", result.getJSONObject("location"));
						}
                        jsCallback(functiong, 0, EUExCallback.F_C_JSON, cbResult.toString());
                        if (null != getAddressFunId) {
                            final JSONObject ret = cbResult;
                            ((Activity)mContext).runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    callbackToJs(Integer.parseInt(getAddressFunId), false, EUExCallback.F_C_SUCCESS, ret);
                                }
                            });
                        }
					}else{
						final String formatted_address = result.getString("formatted_address");
                        jsCallback(functiong, 0, EUExCallback.F_C_TEXT, formatted_address);
                        if (null != getAddressFunId) {
                            ((Activity) mContext).runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    callbackToJs(Integer.parseInt(getAddressFunId), false, EUExCallback.F_C_SUCCESS, formatted_address);
                                }
                            });
                        }
					}
					return;
				}
			} catch (Exception e) {
				errorCallback(0, EUExCallback.F_E_UEXlOCATION_GETADDRESS, "netWork error");
				return;
			}finally{
				if(null != mHttpGet){
					mHttpGet.abort();
				}
				if(null != mHttpClient){
					mHttpClient.getConnectionManager().shutdown();
				}
			}
			errorCallback(0, EUExCallback.F_E_UEXlOCATION_GETADDRESS, "netWork error");
		}

		public void shutDown(){
			mShutDown = true;
			if(null != mHttpGet){
				mHttpGet.abort();
			}
			if(null != mHttpClient){
				mHttpClient.getConnectionManager().shutdown();
			}
		}
	}

	private class LCallback implements LocationCallback{

		public LCallback(){
			;
		}

		@Override
		public void onLocation(double lat, double log, float radius) {
            //将baidu坐标系转成指定的
            double [] result = transferByType(log, lat, BD09, defaultType);
			String js = SCRIPT_HEADER + "if(" + onFunction + "){" + onFunction + "(" + result[1] + "," + result[0] + "," + radius + ");}";
			mBrwView.loadUrl(js);
		}
	}

    /**
     *
     * @param longitude
     * @param latitude
     * @param to
     * @return array[0]: longitude, array[1]: latitude
     */
    private double[] transferByType(double longitude, double latitude, String from, String to) {
        double [] result;
        if (WGS84.equalsIgnoreCase(from) && BD09.equalsIgnoreCase(to)) {
            result = CoordTransform.WGS84ToBD09(longitude, latitude);
        } else if (WGS84.equalsIgnoreCase(from) && GCJ02.equalsIgnoreCase(to)) {
            result = CoordTransform.WGS84ToGCJ02(longitude, latitude);
        } else if (BD09.equalsIgnoreCase(from) && GCJ02.equalsIgnoreCase(to)) {
            result = CoordTransform.BD09ToGCJ02(longitude, latitude);
        } else if (BD09.equalsIgnoreCase(from) && WGS84.equalsIgnoreCase(to)) {
            result = CoordTransform.BD09ToWGS84(longitude, latitude);
        } else if (GCJ02.equalsIgnoreCase(from) && WGS84.equalsIgnoreCase(to)) {
            result = CoordTransform.GCJ02ToWGS84(longitude, latitude);
        } else if (GCJ02.equalsIgnoreCase(from) && BD09.equalsIgnoreCase(to)) {
            result = CoordTransform.GCJ02ToBD09(longitude, latitude);
        } else {
            //如果传入的from, to 非法，则不处理
            result = new double[2];
            result[0] = longitude;
            result[1] = latitude;
        }
        return result;
    }

    public void getAddressByType(String [] params) {
        if(params.length < 2) {
            return;
        }
        String paramString = params[0];
        try {
            JSONObject jsonObject = new JSONObject(paramString);
            double latitude = jsonObject.getDouble("latitude");
            double longitude = jsonObject.getDouble("longitude");
            String type = jsonObject.optString("type", BD09);

            double [] result = transferByType(longitude, latitude, type, BD09);

            int flag = jsonObject.optInt("flag", 0);
            getAddressFunId = params[1];
            if(null != mQueryTask){
                mQueryTask.shutDown();
                mQueryTask = null;
            }
            mQueryTask = new QueryTask(String.valueOf(result[1]), String.valueOf(result[0]), flag);
            mQueryTask.start();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

	@Override
	public void onRequestPermissionResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
		super.onRequestPermissionResult(requestCode, permissions, grantResults);
		if (requestCode == 1){
			if (grantResults[0] != PackageManager.PERMISSION_DENIED){
				openLocation(openLocationParams);
			} else {
				// 对于 ActivityCompat.shouldShowRequestPermissionRationale
				// 1：用户拒绝了该权限，没有勾选"不再提醒"，此方法将返回true。
				// 2：用户拒绝了该权限，有勾选"不再提醒"，此方法将返回 false。
				// 3：如果用户同意了权限，此方法返回false
				// 拒绝了权限且勾选了"不再提醒"
				if (!ActivityCompat.shouldShowRequestPermissionRationale((EBrowserActivity)mContext, permissions[0])) {
					Toast.makeText(mContext, "请先设置权限" + permissions[0], Toast.LENGTH_LONG).show();
				} else {
					requsetPerssions(Manifest.permission.ACCESS_FINE_LOCATION, "请先申请权限" + permissions[0], 1);
				}
			}
		}
	}

}
