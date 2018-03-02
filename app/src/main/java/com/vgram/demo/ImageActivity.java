package com.vgram.demo;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.graphics.Color;
import android.graphics.ColorSpace;
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
import dji.common.useraccount.UserAccountState;
import dji.common.util.CommonCallbacks;
import dji.keysdk.DJIKey;
import dji.keysdk.KeyManager;
import dji.keysdk.ProductKey;
import dji.keysdk.callback.KeyListener;
import dji.log.DJILog;
import dji.sdk.base.BaseProduct;
import dji.sdk.products.Aircraft;
import dji.sdk.sdkmanager.DJISDKManager;
import dji.sdk.useraccount.UserAccountManager;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Image Activity
 * 이미지 총괄 액티비티,
 * 드론 SD카드 미디어 관리 및 파노라마 기능 제공
 * 구글 및 기본 갤러리 어플을 이용한 이미지 얻어오기 및 native 함수를 이용한 stitch 제공
 */

public class ImageActivity extends Activity implements View.OnClickListener, BottomNavigationView.OnNavigationItemSelectedListener {

    private static final String TAG = ImageActivity.class.getName();

    /**레이아웃 구성요소**/
    private Button mBtnGallery;

    private Button mBtnSelect;
    private Button mBtnStitch;

    /**Panorama 구성요소**/
    private String stitchCost = "";

    private final int GALLERY_CODE=1112;

    private final double STITCH_IMAGE_SCALE = 0.5;
    private final String STITCHING_RESULT_IMAGES_DIRECTORY = Environment.getExternalStorageDirectory().getPath() + "/VgramDemo/";

    private ArrayList imageList = new ArrayList<String>();

    /**BottomNavigationView 구성요소 및 리스너**/
    private BottomNavigationView mNavigationView;

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_home:
                startActivity( new Intent(ImageActivity.this, ConnectionActivity.class));
                finish();
                return true;
            case R.id.action_image:
                return true;
            case R.id.action_info:
                startActivity( new Intent(ImageActivity.this, InfoActivity.class));
                finish();
                return true;
        }
        return false;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image);

        initUI();
    }

    /**
     * 레이아웃의 점검 이후 id에 따라 추가해야함
     * 기능 테스트시, enabled의 주석처리이후 체크(드론 가지고있지 않을 때)
     */
    private void initUI() {

        mBtnGallery = (Button) findViewById(R.id.btn_gallery);
        mBtnGallery.setOnClickListener(this);

        mBtnSelect = (Button)findViewById(R.id.btn_select);
        mBtnSelect.setOnClickListener(this);
        mBtnStitch = (Button)findViewById(R.id.btn_stitch);
        mBtnStitch.setOnClickListener(this);

        //BottomNavigationView Listener
        mNavigationView = (BottomNavigationView) findViewById(R.id.bottom_navigation);
        mNavigationView.setOnNavigationItemSelectedListener(this);
        mNavigationView.setSelectedItemId(R.id.action_image);
    }

    /**
     * String --> Toast 변환 메소드
     */
    public void showToast(final String msg) {
        runOnUiThread(new Runnable() {
            public void run() {
                Toast.makeText(ImageActivity.this, msg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * View OnClick 메소드 오버라이드
     */
    @Override
    public void onClick(View v) {
        switch (v.getId()) {

            case R.id.btn_gallery: {
                Intent intent = new Intent(this, FileActivity.class);
                startActivity(intent);
                break;
            }

            case R.id.btn_select: {
                openGallery();
                break;
            }

            case R.id.btn_stitch: {
                showToast("스티칭 시작...");
                startStitch();
                break;
            }

            default:
                break;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK) {
            Log.i("Result", String.valueOf(resultCode));
            switch (requestCode) {

                case GALLERY_CODE: {

                    // Multi-Select 지원하지 않는 기기
                    if (data.getClipData() == null) {
                        Log.i("Single-Choice", String.valueOf(data.getData()));
                        imageList.add(getRealPathFromURI(data.getData()));
                    } else {
                        ClipData clipData = data.getClipData();
                        Log.i("Multi-Choice", String.valueOf(clipData.getItemCount()));

                        if (clipData.getItemCount() > 10) {
                            showToast("사진은 최대 10장입니다.");
                            return;
                        } else if (clipData.getItemCount() == 1) {
                            String dataStr = getRealPathFromURI(clipData.getItemAt(0).getUri());
                            Log.i("Multi-Choice", String.valueOf(clipData.getItemAt(0).getUri()));
                            Log.i("Single-Choice", clipData.getItemAt(0).getUri().getPath());
                            imageList.add(dataStr);
                        } else if (clipData.getItemCount() > 1 && clipData.getItemCount() < 10) {
                            for (int i = 0; i < clipData.getItemCount(); i++) {
                                Log.i("Single-Choice", String.valueOf(clipData.getItemAt(i).getUri()));
                                imageList.add(getRealPathFromURI(clipData.getItemAt(i).getUri()));
                            }
                        }
                    }

                    showToast(imageList.size() + "개가 선택되었습니다.");

                    break;
                }
                default:
                    break;
            }
        }else{
            return;
        }
    }


    //갤러리 인텐트 호출하기 위한 메소드
    private void openGallery() {

        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.setType(android.provider.MediaStore.Images.Media.CONTENT_TYPE);
        startActivityForResult(Intent.createChooser(intent,"다중 선택은 '포토'를 선택하세요."), GALLERY_CODE);

    }

    //Stitch 후 네이티브 코드 호출위한 메소드
    private void startStitch() {
        new Thread() {
            @Override
            public void run() {
                String[] source = new String[imageList.size()];
                imageList.toArray(source);
                //Stitch Success == 0, else Error occurs..
                if(Stitch(source, STITCHING_RESULT_IMAGES_DIRECTORY + "Panorama.jpg", STITCH_IMAGE_SCALE) == 0) {
                    showToast("스티칭 성공!\n걸린시간 : " + stitchCost);
                }else{
                    showToast("스티칭 실패..");
                }
                imageList.clear();
            }
        }.start();
    }

    //Stitch Timer
    private void stitchingCostSet(double cost) {
        String strCost = "" + cost;
        stitchCost = strCost.substring(0, strCost.indexOf('.') + 2);
    }

    //uri -> real path
    private String getRealPathFromURI(Uri uri) {
        Cursor cursor = getContentResolver().query(uri, null, null, null, null);
        cursor.moveToNext();
        String path = cursor.getString(cursor.getColumnIndex("_data"));
        cursor.close();

        return path;
    }

    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("native-lib");
    }

    /**
     * A native method that is implemented by the 'native-lib' native library,
     * which is packaged with this application.
     */
    public native int Stitch(String[] source, String result, double scale);

}
