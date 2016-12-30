package com.crazyman.library;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.view.Window;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PermissionTool extends AppCompatActivity {

    private static final int REQUEST_CODE_PERMISSION = 1;
    private static final int REQUEST_CODE_SETTING = 2;

    public static final String EXTRA_PERMISSION = "extra_permission";
    public static final String EXTRA_PERMISSION_DESC = "extra_permission_desc";

    private String[] mPermissions;
    private Map<String, String> mPermissionDesc = new HashMap<>();

    private List<String> mNeedPermissions = new ArrayList<>();
    private List<String> mNotShowPermissionList = new ArrayList<>();

    private SharedPreferences mPermissionSharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        getWindow().requestFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(savedInstanceState);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.hide();
        }

        mPermissionSharedPreferences = getSharedPreferences(getPackageName() + ".permission", MODE_PRIVATE);
        mPermissions = getIntent().getStringArrayExtra(EXTRA_PERMISSION);
        String[] descs = getIntent().getStringArrayExtra(EXTRA_PERMISSION_DESC);
        if (mPermissions == null || descs == null || mPermissions.length != descs.length) {
            new AlertDialog.Builder(this)
                    .setMessage(R.string.params_error)
                    .setNegativeButton(R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            finish();
                        }
                    });
            return;
        }

        for (int i = 0; i < mPermissions.length; i++) {
            mPermissionDesc.put(mPermissions[i], descs[i]);
        }

        doRequestPermissions();
    }

    private void doRequestPermissions() {
        //过滤一下不需要申请的权限
        mNeedPermissions.clear();
        for (int i = 0; i < mPermissions.length; i++) {
            String permission = mPermissions[i];
            if (ActivityCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                mNeedPermissions.add(permission);
            }
        }
        if (mNeedPermissions.size() == 0) {
            //所有权限都拥有，直接返回成功
            notifyOk();
            return;
        }

        //检查是否能弹出授权对话框
        mNotShowPermissionList.clear();
        for (String permission : mNeedPermissions) {
            if (!mPermissionSharedPreferences.getBoolean(permission, false)) {
                //如果以前没有申请此权限，那么可以直接申请的
                continue;
            }
            if (!ActivityCompat.shouldShowRequestPermissionRationale(this, permission)) {
                mNotShowPermissionList.add(permission);
            }
        }
        if (mNotShowPermissionList.size() > 0) {
            //有些权限不能动态申请了，因为不能弹出权限框，显示相关信息
            showNeedPermission(mNotShowPermissionList);
            return;
        }

        //申请权限
        SharedPreferences.Editor editor = mPermissionSharedPreferences.edit();
        String[] permissions = new String[mNeedPermissions.size()];
        int i = 0;
        for (String permission : mNeedPermissions) {
            permissions[i++] = permission;
            editor.putBoolean(permission, true);
        }
        editor.apply();
        ActivityCompat.requestPermissions(this, permissions, REQUEST_CODE_PERMISSION);
    }

    public void showNeedPermission(List<String> needList) {
        String message = getResources().getString(R.string.can_not_request_permission) + ":\n";
        for (String permission : needList) {
            message += mPermissionDesc.get(permission) + "\n";
        }
        new AlertDialog.Builder(this)
                .setTitle(null)
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton(R.string.open_permission_in_settings, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Intent intent = new Intent();
                        intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        intent.setData(Uri.parse("package:" + PermissionTool.this.getPackageName()));
                        startActivityForResult(intent, REQUEST_CODE_SETTING);
                    }
                })
                .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        notifyFailed();
                    }
                })
                .create().show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CODE_PERMISSION) {
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    notifyFailed();
                    return;
                }
            }
            notifyOk();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE_SETTING) {
            doRequestPermissions();
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void notifyOk() {
        setResult(Activity.RESULT_OK);
        finish();
    }

    private void notifyFailed() {
        setResult(Activity.RESULT_CANCELED);
        finish();
    }

    //检查是否拥有权限
    public static boolean hasPermissions(Context contexts, String[] permissions) {
        for (String permission : permissions) {
            if (ActivityCompat.checkSelfPermission(contexts, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    /**
     * 申请权限
     * @param activity  从Activity中申请
     * @param permissions 需要申请的权限数组
     * @param tips 权限描述数组，必须和permissions一一对应
     * @param requestCode startActivityForResult中的请求码
     *
     * 调用后请在onActivityResult中接收结果， Activity.RESULT_OK为成功，Activity.RESULT_CANCEL为失败
     */
    public static void requestPermission(Activity activity, String[] permissions, String[] tips, int requestCode) {
        Intent intent = new Intent(activity, PermissionTool.class);
        intent.putExtra(EXTRA_PERMISSION, permissions);
        intent.putExtra(EXTRA_PERMISSION_DESC, tips);
        activity.startActivityForResult(intent, requestCode);
    }

    /**
     * 申请权限
     * @param fragment  从Fragment中申请
     * @param permissions 需要申请的权限数组
     * @param tips 权限描述数组，必须和permissions一一对应
     * @param requestCode startActivityForResult中的请求码
     *
     * 调用后请在onActivityResult中接收结果， Activity.RESULT_OK为成功，Activity.RESULT_CANCEL为失败
     */
    public static void requestPermission(Fragment fragment, String[] permissions, String[] tips, int requestCode) {
        Intent intent = new Intent(fragment.getContext(), PermissionTool.class);
        intent.putExtra(EXTRA_PERMISSION, permissions);
        intent.putExtra(EXTRA_PERMISSION_DESC, tips);
        fragment.startActivityForResult(intent, requestCode);
    }
}
