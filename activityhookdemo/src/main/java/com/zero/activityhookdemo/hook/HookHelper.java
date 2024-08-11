package com.zero.activityhookdemo.hook;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class HookHelper {

    private static final String TAG = "Zero";

    public static final String EXTRA_TARGET_INTENT = "extra_target_intent";

    /**
     * 通过上下文，hook Instrumentation
     * @param activity
     */
    public static void hookInstrumentation(Activity activity) {
        //TODO:
        Class<?> activityClass = Activity.class;
        //通过Activity.class 拿到 mInstrumentation字段
        Field field = null;
        try {
            field = activityClass.getDeclaredField("mInstrumentation");
            field.setAccessible(true);
            //根据activity内mInstrumentation字段 获取Instrumentation对象
            Instrumentation instrumentation = (Instrumentation) field.get(activity);
            //创建代理对象,注意了因为Instrumentation是类，不是接口 所以我们只能用静态代理，
            Instrumentation instrumentationProxy = new ProxyInstrumentation(instrumentation);
            //进行替换
            field.set(activity, instrumentationProxy);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    /**
     * 通过持有的方式-编写代理类
     */
    static class ProxyInstrumentation extends Instrumentation {

        private static final String TAG = "Zero";
        // ActivityThread中原始的对象, 保存起来
        Instrumentation mBase;

        public ProxyInstrumentation(Instrumentation base) {
            mBase = base;
        }

        public ActivityResult execStartActivity(
                Context who, IBinder contextThread, IBinder token, Activity target,
                Intent intent, int requestCode, Bundle options) {

            Log.d(TAG, "执行了startActivity, 参数如下: " + "who = [" + who + "], " +
                    "contextThread = [" + contextThread + "], token = [" + token + "], " +
                    "target = [" + target + "], intent = [" + intent +
                    "], requestCode = [" + requestCode + "], options = [" + options + "]");

            // 由于这个方法是隐藏的,因此需要使用反射调用;首先找到这个方法
            //execStartActivity有重载，别找错了
            try {
                Method execStartActivity = Instrumentation.class.getDeclaredMethod(
                        "execStartActivity",
                        Context.class, IBinder.class, IBinder.class, Activity.class,
                        Intent.class, int.class, Bundle.class);
                execStartActivity.setAccessible(true);
                return (ActivityResult) execStartActivity.invoke(mBase, who,
                        contextThread, token, target, intent, requestCode, options);
            } catch (Exception e) {
                throw new RuntimeException("do not support!!! pls adapt it");
            }
        }

        /**
         * 重写newActivity 因为newActivity 方法有变
         * 原来是：(Activity)cl.loadClass(className).newInstance();
         *
         * @param cl
         * @param className
         * @param intent
         * @return
         * @throws InstantiationException
         * @throws IllegalAccessException
         * @throws ClassNotFoundException
         */
        @Override
        public Activity newActivity(ClassLoader cl, String className, Intent intent)
                throws InstantiationException, IllegalAccessException, ClassNotFoundException {

            return mBase.newActivity(cl, className, intent);
        }
    }

    /**
     * 通过hook ActvityThread 中 Instrumentation
     */
    public static void hookActivityThreadInstrumentation() {
        try {
            // 先获取到当前的ActivityThread对象
            //ActivityThread 有唯一实例
            //    private static volatile ActivityThread sCurrentActivityThread;
            //通过currentActivityThread()方法返回
            //    public static ActivityThread currentActivityThread() {
            //        return sCurrentActivityThread;
            //    }
            Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
            Method currentActivityThreadMethod = activityThreadClass.getDeclaredMethod("currentActivityThread");
            currentActivityThreadMethod.setAccessible(true);
            //currentActivityThread是一个static函数所以可以直接invoke，不需要带实例参数
            Object currentActivityThread = currentActivityThreadMethod.invoke(null);

            // 拿到原始的 mInstrumentation字段
            Field mInstrumentationField = activityThreadClass.getDeclaredField("mInstrumentation");
            mInstrumentationField.setAccessible(true);
            Instrumentation mInstrumentation = (Instrumentation) mInstrumentationField.get(currentActivityThread);

            // 创建代理对象
            Instrumentation proxyInstrumentation = new ProxyInstrumentation(mInstrumentation);
            // 偷梁换柱
            mInstrumentationField.set(currentActivityThread, proxyInstrumentation);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * hookAMS的思路
     * 1.找到了Hook的点
     * 2.hook点 动态代理 静态?
     * 3.获取到getDefault的IActivityManager原始对象
     * 4.动态代理 准备classloader 接口
     * 5.classloader，获取当前线程
     * 6.接口Class.forName("android.app.IActivityManager");
     * 7.Proxy.newProxyInstance() 得到一个IActivityManagerProxy
     * 8.IActivityManaqerProxy融入到framework
     */
    public static void hookAMS() {
        //TODO:
        try {
            Field gDefaultField = null;
            //26版本后的区别
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                //这是需要反射的Singleton
//                public abstract class Singleton<T> {
//                    private T mInstance;
//
//                    protected abstract T create();
//
//                    public final T get() {
//                        synchronized (this) {
//                            if (mInstance == null) {
//                                mInstance = create();
//                            }
//                            return mInstance;
//                        }
//                    }
//                }

                Class<?> activityManager = Class.forName("android.app.ActivityManager");
                gDefaultField = activityManager.getDeclaredField("IActivityManagerSingleton");
            } else {
                Class<?> activityManagerNativeClass = Class.forName("android.app.ActivityManagerNative");
                gDefaultField = activityManagerNativeClass.getDeclaredField("gDefault");
            }
            gDefaultField.setAccessible(true);

            Object gDefault = gDefaultField.get(null);

            // gDefault是一个 android.util.Singleton对象; 我们取出这个单例里面的字段
            Class<?> singleton = Class.forName("android.util.Singleton");
            Field mInstanceField = singleton.getDeclaredField("mInstance");
            mInstanceField.setAccessible(true);

            // ActivityManagerNative 的gDefault对象里面原始的 IActivityManager对象
            Object rawIActivityManager = mInstanceField.get(gDefault);

            // 创建一个这个对象的代理对象, 然后替换这个字段, 让我们的代理对象帮忙干活
            //动态代理的是一个接口！！！！重要的事情说三遍，动态代理的是接口！！！动态代理的是接口！！！动态代理的是接口！！！
            Class<?> iActivityManagerInterface = Class.forName("android.app.IActivityManager");
            Object proxy = Proxy.newProxyInstance(
                    Thread.currentThread().getContextClassLoader(),
                    new Class<?>[]{iActivityManagerInterface},
                    new AMSInvocationHandler(rawIActivityManager));

            //融入到framework
            mInstanceField.set(gDefault, proxy);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static class AMSInvocationHandler implements InvocationHandler {

        private static final String TAG = "AMSInvocationHandler";

        Object iamObject;

        public AMSInvocationHandler(Object iamObject) {
            this.iamObject = iamObject;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            Log.i(TAG, "invoke: method " + method.getName());
            if ("startActivity".equals(method.getName())) {
                Log.i(TAG, "ready to startActivity");
                for (Object object : args) {
                    Log.d(TAG, "invoke: object=" + object);
                }
            }
            return method.invoke(iamObject, args);
        }
    }

    /**
     * Hook AMS 实现替换startActivity
     */
    public static void hookAMSInterceptStartActivity() {
        try {
            Field gDefaultField = null;
            Log.i(TAG, "hookAMSInterceptStartActivity: " + Build.VERSION.SDK_INT);
            //当前关键版本节点 28， 26
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                Class<?> clazz = Class.forName("android.app.ActivityTaskManager");
                gDefaultField = clazz.getDeclaredField("IActivityTaskManagerSingleton");
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Class<?> activityManager = Class.forName("android.app.ActivityManager");
                gDefaultField = activityManager.getDeclaredField("IActivityManagerSingleton");
            } else {
                Class<?> activityManagerNativeClass = Class.forName("android.app.ActivityManagerNative");
                gDefaultField = activityManagerNativeClass.getDeclaredField("gDefault");
            }
            Log.i(TAG, "hookAMSInterceptStartActivity: " + gDefaultField);
            gDefaultField.setAccessible(true);
            Object gDefault = gDefaultField.get(null);

            // gDefault是一个 android.util.Singleton对象; 我们取出这个单例里面的字段
            Class<?> singleton = Class.forName("android.util.Singleton");
            Field mInstanceField = singleton.getDeclaredField("mInstance");
            mInstanceField.setAccessible(true);

            // ActivityManagerNative 的gDefault对象里面原始的 IActivityManager对象
            Object rawIActivityManager = mInstanceField.get(gDefault);

            // 创建一个这个对象的代理对象, 然后替换这个字段, 让我们的代理对象帮忙干活
            // 版本28后manager类不同（流程不同）
            Class<?> iActivityManagerInterface;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                iActivityManagerInterface = Class.forName("android.app.IActivityTaskManager");
            } else {
                iActivityManagerInterface = Class.forName("android.app.IActivityManager");

            }
            Object proxy = Proxy.newProxyInstance(Thread.currentThread().getContextClassLoader(),
                    new Class<?>[]{iActivityManagerInterface}, new IActivityManagerHandler(rawIActivityManager));

            //融入framework
            mInstanceField.set(gDefault, proxy);
        } catch (Exception e) {
            Log.e(TAG, "hookAMSInterceptStartActivity: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * hook ActivityThread 中的 mH（Handler），回填mCallBack，实现无注册activity展示
     */
    public static void hookH() {
        try {
            // 先获取到当前的ActivityThread对象
            //    一个app中只有一个ActvityThread 且 类中设置为了 static参数，所以可以获得唯一对象
            //    private static volatile ActivityThread sCurrentActivityThread;
            Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
            Field currentActivityThreadField = activityThreadClass.getDeclaredField("sCurrentActivityThread");
            currentActivityThreadField.setAccessible(true);
            Object currentActivityThread = currentActivityThreadField.get(null);

            // 由于ActivityThread一个进程只有一个,我们获取这个对象的mH
            Field mHField = activityThreadClass.getDeclaredField("mH");
            mHField.setAccessible(true);
            Handler mH = (Handler) mHField.get(currentActivityThread);

            // 设置它的回调, 根据源码:
            // 我们自己给他设置一个回调,就会替代之前的回调;
            //        public void dispatchMessage(Message msg) {
            //            if (msg.callback != null) {
            //                handleCallback(msg);
            //            } else {
            //                if (mCallback != null) {
            //                    if (mCallback.handleMessage(msg)) {
            //                        return;
            //                    }
            //                }
            //                handleMessage(msg);
            //            }
            //        }

            Field mCallBackField = Handler.class.getDeclaredField("mCallback");
            mCallBackField.setAccessible(true);

            mCallBackField.set(mH, new ActivityThreadHandlerCallback(mH));
        } catch (Exception e) {
            Log.e(TAG, "hookH: " + e.getMessage());
            e.printStackTrace();
        }

    }


}
