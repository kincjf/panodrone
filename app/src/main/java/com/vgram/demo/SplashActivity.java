package com.vgram.demo;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/**
 * 로딩화면 이미지
 * 이미지는 Theme 로 style value에 지정됨
 */

public class SplashActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        startActivity(new Intent(this, ConnectionActivity.class));
        finish();
    }
}
