package com.vgram.demo;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.design.widget.BottomNavigationView;
import android.support.v4.app.ActivityCompat;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import org.w3c.dom.Text;

import dji.common.error.DJIError;
import dji.common.error.DJISDKError;
import dji.keysdk.DJIKey;
import dji.keysdk.KeyManager;
import dji.keysdk.ProductKey;
import dji.keysdk.callback.KeyListener;
import dji.log.DJILog;
import dji.sdk.base.BaseProduct;
import dji.sdk.products.Aircraft;
import dji.sdk.sdkmanager.DJISDKManager;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;


/**
 * Connection Activity,
 * 앱 기본화면, 메인 액티비티(RC 액티비티)와 인텐트 연결
 */

public class ConnectionActivity extends Activity implements View.OnClickListener, BottomNavigationView.OnNavigationItemSelectedListener {

    private static final String TAG = ConnectionActivity.class.getName();

    /**레이아웃 구성요소**/
    private TextView mTextConnectionStatus;
    private TextView mTextProduct;
    private TextView mTextModelAvailable;
    private Button mBtnControl;

    /**BottomNavigationView 구성요소 및 리스너**/
    private BottomNavigationView mNavigationView;

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_home:
                return true;
            case R.id.action_image:
                startActivity( new Intent(ConnectionActivity.this, ImageActivity.class));
                finish();
                return true;
            case R.id.action_info:
                startActivity( new Intent(ConnectionActivity.this, InfoActivity.class));
                finish();
                return true;
        }
        return false;
    }

    /**DJI SDK 구성요소**/
    private static final String[] REQUIRED_PERMISSION_LIST = new String[]{
            Manifest.permission.VIBRATE,
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.WAKE_LOCK,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.READ_PHONE_STATE,
    };
    private List<String> missingPermission = new ArrayList<>();
    private AtomicBoolean isRegistrationInProgress = new AtomicBoolean(false);
    private static final int REQUEST_PERMISSION_CODE = 12345;
    private DJIKey firmwareKey;
    private KeyListener firmwareVersionUpdater;
    private boolean hasStartedFirmVersionListener = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        checkAndRequestPermissions();
        setContentView(R.layout.activity_connection);
        initUI();

        // Register the broadcast receiver for receiving the device connection's changes.
        IntentFilter filter = new IntentFilter();
        filter.addAction(DemoApplication.FLAG_CONNECTION_CHANGE);
        registerReceiver(mReceiver, filter);
    }

    /**
     * 허가되지 않은 권한 확인 및 런타임 승인과정
     */
    private void checkAndRequestPermissions() {
        // Check for permissions
        for (String eachPermission : REQUIRED_PERMISSION_LIST) {
            if (ContextCompat.checkSelfPermission(this, eachPermission) != PackageManager.PERMISSION_GRANTED) {
                missingPermission.add(eachPermission);
            }
        }
        // Request for missing permissions
        if (!missingPermission.isEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ActivityCompat.requestPermissions(this,
                    missingPermission.toArray(new String[missingPermission.size()]),
                    REQUEST_PERMISSION_CODE);
        }
    }

    /**
     * 런타임 권한확인 결과 전송
     */
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // Check for granted permission and remove from missing list
        if (requestCode == REQUEST_PERMISSION_CODE) {
            for (int i = grantResults.length - 1; i >= 0; i--) {
                if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                    missingPermission.remove(permissions[i]);
                }
            }
        }
        // If there is enough permission, we will start the registration
        if (missingPermission.isEmpty()) {
            startSDKRegistration();
        } else {
            showToast("승인 실패!");
        }
    }

    private void startSDKRegistration() {
        if (isRegistrationInProgress.compareAndSet(false, true)) {
            AsyncTask.execute(new Runnable() {
                @Override
                public void run() {
                    showToast( "등록중, 기다려주세요...");
                    DJISDKManager.getInstance().registerApp(getApplicationContext(), new DJISDKManager.SDKManagerCallback() {
                        @Override
                        public void onRegister(DJIError djiError) {
                            if (djiError == DJISDKError.REGISTRATION_SUCCESS) {
                                DJILog.e("App registration", DJISDKError.REGISTRATION_SUCCESS.getDescription());
                                DJISDKManager.getInstance().startConnectionToProduct();
                                showToast("등록 완료!");
                            } else {
                                showToast( "SDK등록에 실패하였습니다. 네트워크를 확인하여주세요!");
                            }
                            Log.v(TAG, djiError.getDescription());
                        }

                        @Override
                        public void onProductChange(BaseProduct oldProduct, BaseProduct newProduct) {
                            Log.d(TAG, String.format("onProductChanged oldProduct:%s, newProduct:%s", oldProduct, newProduct));
                        }
                    });
                }
            });
        }
    }

    @Override
    public void onResume() {
        Log.e(TAG, "onResume");
        super.onResume();
        updateTitleBar();
    }

    @Override
    public void onPause() {
        Log.e(TAG, "onPause");
        super.onPause();
    }

    @Override
    public void onStop() {
        Log.e(TAG, "onStop");
        super.onStop();
    }

    public void onReturn(View view){
        Log.e(TAG, "onReturn");
        this.finish();
    }

    @Override
    protected void onDestroy() {
        Log.e(TAG, "onDestroy");
        unregisterReceiver(mReceiver);
        removeFirmwareVersionListener();
        super.onDestroy();
    }

    /**
     * 레이아웃 구성요소 초기화
     */
    private void initUI() {

        mTextConnectionStatus = (TextView) findViewById(R.id.text_connection_status);
        mTextModelAvailable = (TextView) findViewById(R.id.text_model_available);
        mTextProduct = (TextView) findViewById(R.id.text_product_info);

        mBtnControl = (Button) findViewById(R.id.btn_control);
        mBtnControl.setOnClickListener(this);

        //BottomNavigationView Listener
        mNavigationView = (BottomNavigationView) findViewById(R.id.bottom_navigation);
        mNavigationView.setOnNavigationItemSelectedListener(this);
    }

    protected BroadcastReceiver mReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            refreshSDKRelativeUI();
        }
    };

    private void updateTitleBar() {
        boolean ret = false;
        BaseProduct product = DemoApplication.getProductInstance();
        if (product != null) {
            if(product.isConnected()) {
                //The product is connected
                showToast(DemoApplication.getProductInstance().getModel() + " 이 연결되었습니다.");
                refreshSDKRelativeUI();
                ret = true;
            } else {
                if(product instanceof Aircraft) {
                    Aircraft aircraft = (Aircraft)product;
                    if(aircraft.getRemoteController() != null && aircraft.getRemoteController().isConnected()) {
                        // The product is not connected, but the remote controller is connected
                        showToast("RC만 연결되어 있습니다.");
                        ret = true;
                    }
                }
            }
        }
    }

    /**
     * String --> Toast 변환 메소드
     */
    public void showToast(final String msg) {
        runOnUiThread(new Runnable() {
            public void run() {
                Toast.makeText(ConnectionActivity.this, msg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateVersion() {
        String version = null;
        if (DemoApplication.getProductInstance() != null) {
            version = DemoApplication.getProductInstance().getFirmwarePackageVersion();
        }

        if (TextUtils.isEmpty(version)) {
            mTextModelAvailable.setText("Firmware version:N/A"); //Firmware version:
        } else {
            mTextModelAvailable.setText("Firmware version:"+version); //"Firmware version: " +
            removeFirmwareVersionListener();
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {

            case R.id.btn_control: {
                Intent intent = new Intent(this, MainActivity.class);
                startActivity(intent);
                break;
            }

            default:
                break;
        }
    }

    private void refreshSDKRelativeUI() {
        BaseProduct mProduct = DemoApplication.getProductInstance();

        if (null != mProduct && mProduct.isConnected()) {
            Log.v(TAG, "refreshSDK: True");

            String str = mProduct instanceof Aircraft ? "DJIAircraft" : "DJIHandHeld";
            tryUpdateFirmwareVersionWithListener();

            if (null != mProduct.getModel()) {
                mTextProduct.setText("" + mProduct.getModel().getDisplayName());
                mTextConnectionStatus.setText("" + str);
                mTextConnectionStatus.setTextColor(Color.BLUE);
            } else {
                mTextProduct.setText(R.string.product_information);
            }

        } else {
            Log.v(TAG, "refreshSDK: False");

            mTextProduct.setText(R.string.product_information);
            mTextConnectionStatus.setText(R.string.connection_loose);
            mTextConnectionStatus.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
        }
    }

    private void tryUpdateFirmwareVersionWithListener() {
        if (!hasStartedFirmVersionListener) {
            firmwareVersionUpdater = new KeyListener() {
                @Override
                public void onValueChange(final Object o, final Object o1) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            updateVersion();
                        }
                    });
                }
            };
            firmwareKey = ProductKey.create(ProductKey.FIRMWARE_PACKAGE_VERSION);
            if (KeyManager.getInstance() != null) {
                KeyManager.getInstance().addListener(firmwareKey, firmwareVersionUpdater );
            }
            hasStartedFirmVersionListener = true;
        }
        updateVersion();
    }

    private void removeFirmwareVersionListener() {
        if (hasStartedFirmVersionListener) {
            if (KeyManager.getInstance() != null) {
                KeyManager.getInstance().removeListener(firmwareVersionUpdater);
            }
        }
        hasStartedFirmVersionListener = false;
    }

}
