package edu.uestc.diaryinuestc.ui.diary;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcel;
import android.os.PersistableBundle;
import android.provider.Settings;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.Menu;
import android.view.SearchEvent;
import android.view.View;
import android.widget.Toast;

import com.amap.api.location.AMapLocationClient;
import com.amap.api.maps.AMap;
import com.amap.api.maps.MapsInitializer;
import com.amap.api.maps.UiSettings;
import com.amap.api.maps.model.BitmapDescriptor;
import com.amap.api.maps.model.MyLocationStyle;
import com.amap.api.services.core.AMapException;
import com.amap.api.services.core.PoiItem;
import com.amap.api.services.poisearch.PoiResult;
import com.amap.api.services.poisearch.PoiSearch;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import edu.uestc.diaryinuestc.R;
import edu.uestc.diaryinuestc.databinding.ActivityGdlocationPickerBinding;
import edu.uestc.diaryinuestc.ui.me.ThemeSelectActivity;

public class GDLocationPickerActivity extends AppCompatActivity implements PoiSearch.OnPoiSearchListener {

    private static final String TAG = "GDLocationPicker";
    /**
     * 需要进行检测的权限数组
     */
    protected String[] needPermissions = {
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
//            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.READ_PHONE_STATE,
            BACK_LOCATION_PERMISSION
    };

    private static final int PERMISSION_REQUESTCODE = 0;

    /**
     * 判断是否需要检测，防止不停的弹框
     */
    private boolean isNeedCheck = true;

    private static final String AGREE_KEY = "PRIVACY_AGREE_KEY";
    private boolean agree = false;

    //是否需要检测后台定位权限，设置为true时，如果用户没有给予后台定位权限会弹窗提示
    private boolean needCheckBackLocation = false;
    //如果设置了target > 28，需要增加这个权限，否则不会弹出"始终允许"这个选择框
    private static String BACK_LOCATION_PERMISSION = "android.permission.ACCESS_BACKGROUND_LOCATION";
    private ActivityGdlocationPickerBinding binding;

    private UiSettings mUiSettings;//定义一个UiSettings对象
    private PoiSearch poiSearch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeSelectActivity.setThemeToActivity(this, null);
        if (Build.VERSION.SDK_INT > 28
                && getApplicationContext().getApplicationInfo().targetSdkVersion > 28) {
            needPermissions = new String[]{
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION,
//                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.READ_PHONE_STATE,
                    BACK_LOCATION_PERMISSION
            };
            needCheckBackLocation = true;
        }

        binding = ActivityGdlocationPickerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.searchToolbar.setNavigationOnClickListener((v) -> finish());

        //necessary WARNING!
        AMapLocationClient.updatePrivacyShow(this, true, true);
        AMapLocationClient.updatePrivacyAgree(this, true);

        privacyCompliance();

        binding.map.onCreate(savedInstanceState);
        AMap aMap = binding.map.getMap();

        aMap.setMapType(AMap.MAP_TYPE_NORMAL);

        MyLocationStyle myLocationStyle;
        myLocationStyle = new MyLocationStyle();
        myLocationStyle.myLocationType(MyLocationStyle.LOCATION_TYPE_FOLLOW);//连续定位、且将视角移动到地图中心点，定位蓝点跟随设备移动。（1秒1次定位）
        myLocationStyle.interval(10000); //设置连续定位模式下的定位间隔，只在连续定位模式下生效，单次定位模式下不会生效。单位为毫秒。
        aMap.setMyLocationStyle(myLocationStyle);//设置定位蓝点的Style
        aMap.setMyLocationEnabled(true);// 设置为true表示启动显示定位蓝点，false表示隐藏定位蓝点并不进行定位，默认是false。

        mUiSettings = aMap.getUiSettings();//实例化UiSettings类对象
        //指南针
        mUiSettings.setCompassEnabled(true);
        //比例尺
        mUiSettings.setScaleControlsEnabled(true);


//        aMap.setLocationSource(this);//通过aMap对象设置定位数据源的监听

        mUiSettings.setMyLocationButtonEnabled(true); //显示默认的定位按钮

        aMap.setMyLocationEnabled(true);// 可触发定位并显示当前位置


        binding.search.setSubmitButtonEnabled(true);
    }

    @Override
    protected void onResume() {
        super.onResume();
        binding.map.onResume();
        if (Build.VERSION.SDK_INT >= 23) {
            if (isNeedCheck) {
                checkPermissions(needPermissions);
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        binding.map.onPause();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState, @NonNull PersistableBundle outPersistentState) {
        super.onSaveInstanceState(outState, outPersistentState);
        binding.map.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding.map.onDestroy();
    }

    private void privacyCompliance() {
        MapsInitializer.updatePrivacyShow(GDLocationPickerActivity.this, true, true);

        SharedPreferences defaultPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        agree = defaultPreferences.getBoolean(GDLocationPickerActivity.AGREE_KEY, false);

        if (agree) return;
        SpannableStringBuilder spannable = new SpannableStringBuilder("\"感谢您使用成电微记！在定位功能中我们必须按照最新监管《隐私权政策》向用户提出声明，特向您说明如下\n1.为向您提供基本功能，我们会收集、使用必要的信息仅用于本地；\n2.基于您的明示授权，我们可能会获取您的位置（为您提供附近的商品、店铺及优惠资讯等）等信息，您有权拒绝或取消授权；\n3.我们会采取业界先进的安全措施保护您的信息安全；\n4.未经您同意，我们不会从第三方处获取、共享或向提供您的信息；\n");
        spannable.setSpan(new ForegroundColorSpan(Color.BLUE), 27, 33, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        AlertDialog requestDialog = new AlertDialog.Builder(this)
                .setTitle("温馨提示(使用高德SDK定位功能权限声明)")
                .setMessage(spannable)
                .setPositiveButton("同意", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        agree = true;
                        defaultPreferences.edit().putBoolean(GDLocationPickerActivity.AGREE_KEY, true).apply();
                        MapsInitializer.updatePrivacyAgree(GDLocationPickerActivity.this, true);
                    }
                })
                .setNegativeButton("不同意", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        agree = false;
                        defaultPreferences.edit().putBoolean(GDLocationPickerActivity.AGREE_KEY, false).apply();
                        MapsInitializer.updatePrivacyAgree(GDLocationPickerActivity.this, false);
                    }
                })
                .setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        if (agree) {
                            Toast.makeText(GDLocationPickerActivity.this, "已同意隐私政策", Toast.LENGTH_SHORT).show();
                        } else {
                            MapsInitializer.updatePrivacyAgree(GDLocationPickerActivity.this, false);
                            Toast.makeText(GDLocationPickerActivity.this, "未同意隐私政策", Toast.LENGTH_SHORT).show();
                            finish();
                        }
                    }
                })
                .show();
    }

    @TargetApi(23)
    private void checkPermissions(String... permissions) {
        try {
            if (Build.VERSION.SDK_INT >= 23 && getApplicationInfo().targetSdkVersion >= 23) {
                List<String> needRequestPermissionList = findDeniedPermissions(permissions);
                if (null != needRequestPermissionList
                        && needRequestPermissionList.size() > 0) {
                    try {
                        String[] array = needRequestPermissionList.toArray(new String[0]);
//                        Method method = getClass().getMethod("requestPermissions", String[].class, int.class);
                        requestPermissions(array, 0);
//                        method.invoke(this, array, 0);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }

        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    /**
     * 获取权限集中需要申请权限的列表
     *
     * @param permissions
     * @return
     */
    @TargetApi(23)
    private List<String> findDeniedPermissions(String[] permissions) {
        try {
            List<String> needRequestPermissionList = new ArrayList<>();
            if (Build.VERSION.SDK_INT >= 23 && getApplicationInfo().targetSdkVersion >= 23) {
                for (String perm : permissions) {
                    if (checkMySelfPermission(perm) != PackageManager.PERMISSION_GRANTED
                            || shouldShowMyRequestPermissionRationale(perm)) {
                        if (!needCheckBackLocation
                                && BACK_LOCATION_PERMISSION.equals(perm)) {
                            continue;
                        }
                        needRequestPermissionList.add(perm);
                    }
                }
            }
            return needRequestPermissionList;
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return null;
    }

    private int checkMySelfPermission(String perm) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                return checkSelfPermission(perm);
            }
//            Method method = getClass().getMethod("checkSelfPermission", String.class);
//            Integer permissionInt = (Integer) method.invoke(this, perm);
//            return permissionInt;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return -1;
    }

    private boolean shouldShowMyRequestPermissionRationale(String perm) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                return shouldShowRequestPermissionRationale(perm);
            }
//            Method method = getClass().getMethod("shouldShowRequestPermissionRationale", new Class[]{String.class});
//            Boolean permissionInt = (Boolean) method.invoke(this, perm);
//            return permissionInt;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * 检测是否说有的权限都已经授权
     *
     * @param grantResults
     * @return
     * @since 2.5.0
     */
    private boolean verifyPermissions(int[] grantResults) {
        try {
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    return false;
                }
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return true;
    }

    @TargetApi(23)
    public void onRequestPermissionsResult(int requestCode,
                                           String[] permissions, int[] paramArrayOfInt) {
        super.onRequestPermissionsResult(requestCode, permissions, paramArrayOfInt);
        try {
            if (Build.VERSION.SDK_INT >= 23) {
                if (requestCode == PERMISSION_REQUESTCODE) {
                    if (!verifyPermissions(paramArrayOfInt)) {
                        Log.e(TAG, Arrays.toString(permissions));
                        showMissingPermissionDialog();
                        isNeedCheck = false;
                    }
                }
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    /**
     * 显示提示信息
     *
     * @since 2.5.0
     */
    private void showMissingPermissionDialog() {
        try {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("提示");
            builder.setMessage("当前应用缺少必要权限。\n请打开定位功能或读写权限，仅用作位置读取\n请点击\"设置\"-\"权限\"-打开所需权限");

            // 拒绝, 退出应用
            builder.setNegativeButton("取消",
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            try {
                                finish();
                            } catch (Throwable e) {
                                e.printStackTrace();
                            }
                        }
                    });

            builder.setPositiveButton("设置",
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            try {
                                startAppSettings();
                            } catch (Throwable e) {
                                e.printStackTrace();
                            }
                        }
                    });

            builder.setCancelable(false);

            builder.show();
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    /**
     * 启动应用的设置
     *
     * @since 2.5.0
     */
    private void startAppSettings() {
        try {
            Intent intent = new Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onPoiSearched(PoiResult poiResult, int i) {
        if (i != 1000) {
            Toast.makeText(this, "搜索失败", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, String.valueOf(poiResult.getPois()), Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onPoiItemSearched(PoiItem poiItem, int i) {

    }
}