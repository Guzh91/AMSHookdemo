package com.zero.activityhookdemo;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.TextView;

import com.zero.activityhookdemo.hook.HookHelper;


public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(newBase);
//        HookHelper.hookAMSInterceptStartActivity();
//        HookHelper.hookH();
    }

    /**
     * 通过  上下文 hook
     * @param view
     */
    public void onBtnHook1Clicked(View view) {
        HookHelper.hookInstrumentation(this);
        Intent intent = new Intent(this, TargetActivity.class);
//        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        //通过getApplicationContext启动不了
//        getApplicationContext().startActivity(intent);
    }

    public void onBtnHook2Clicked(View view) {
        HookHelper.hookActivityThreadInstrumentation();
        Intent intent = new Intent(this, TargetActivity.class);
//        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
//        getApplicationContext().startActivity(intent);
    }

    public void onBtnHook3Clicked(View view) {
        HookHelper.hookAMS();
        Intent intent = new Intent(this, StubActivity.class);
        startActivity(intent);
    }

    public void onBtnHook4Clicked(View view) {
        if (!HookHelper.isAMSHooked){
            HookHelper.hookAMSInterceptStartActivity();
            HookHelper.hookH();
        }
        Intent intent = new Intent(this, TargetActivity.class);
        startActivity(intent);
    }
}
