package com.zero.activityhookdemo;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;


/**
 * TargetActivity并没有在AndroidManifest.xml中注册，但是依旧可以启动，
 * 用的就是hook技术，主要原理就是反射
 *
 */
public class TargetActivity extends AppCompatActivity {


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_target);
    }


}
