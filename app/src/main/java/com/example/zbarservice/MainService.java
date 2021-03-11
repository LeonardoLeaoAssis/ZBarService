package com.example.zbarservice;

import android.Manifest;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.intentfilter.androidpermissions.PermissionManager;
import com.intentfilter.androidpermissions.models.DeniedPermission;
import com.intentfilter.androidpermissions.models.DeniedPermissions;

import net.sourceforge.zbar.Config;
import net.sourceforge.zbar.Image;
import net.sourceforge.zbar.ImageScanner;
import net.sourceforge.zbar.Symbol;
import net.sourceforge.zbar.SymbolSet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainService extends Service implements View.OnTouchListener {

    private Camera mCamera;
    private CameraPreview mPreview;
    private Handler autoFocusHandler;

    private ImageScanner scanner;

    private boolean barcodeScanned = false;
    private boolean previewing = true;

    private View barcode_scanner;
    private WindowManager windowManager;

    static {
        System.loadLibrary("iconv");
    }

    @Override
    public void onCreate() {
        super.onCreate();

        windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);

        List<String> permissions = new ArrayList<>();
        permissions.add(Manifest.permission.CAMERA);
                                                permissions.add(Manifest.permission.SYSTEM_ALERT_WINDOW);

        PermissionManager permissionManager = PermissionManager.getInstance(getApplicationContext());
        permissionManager.checkPermissions(permissions/*singleton(Manifest.permission.CAMERA)*/, new PermissionManager.PermissionRequestListener() {

            @Override
            public void onPermissionGranted() {
                Toast.makeText(getApplicationContext(), "Permissions Granted", Toast.LENGTH_SHORT).show();

                initControls();
            }

            @Override
            public void onPermissionDenied(DeniedPermissions deniedPermissions) {
                String deniedPermissionsText = "Denied: " + Arrays.toString(deniedPermissions.toArray());
                Toast.makeText(getApplicationContext(), deniedPermissionsText, Toast.LENGTH_SHORT).show();

                for (DeniedPermission deniedPermission : deniedPermissions) {
                    if(deniedPermission.shouldShowRationale()) {
                        // Display a rationale about why this permission is required
                    }
                }
            }

        });

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                barcode_scanner.setVisibility(View.VISIBLE);
            }
        }, 15000);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void initControls() {
        autoFocusHandler = new Handler();
        mCamera = getCameraInstance();

        // Instance barcode scanner
        scanner = new ImageScanner();
        scanner.setConfig(0, Config.X_DENSITY, 3);
        scanner.setConfig(0, Config.Y_DENSITY, 3);

        mPreview = new CameraPreview(getApplicationContext(), mCamera, previewCb, autoFocusCB);

        addOverlayView(mPreview);
   }

    public static Camera getCameraInstance() {
        Camera c = null;

        try {
            c = Camera.open(0);
        } catch (Exception e) {
            Log.i("log", Log.getStackTraceString(e));
        }

        return c;
    }

    private void addOverlayView(View mPreview) {
        final WindowManager.LayoutParams params;
        int layoutParamsType;

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            layoutParamsType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        }
        else {
            layoutParamsType = WindowManager.LayoutParams.TYPE_PHONE;
        }

        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutParamsType,
                0,
                PixelFormat.TRANSLUCENT);

        params.gravity = Gravity.CENTER | Gravity.START;
        params.x = 0;
        params.y = 0;

        FrameLayout interceptorLayout = new FrameLayout(this) {

            @Override
            public boolean dispatchKeyEvent(KeyEvent event) {

                // Only fire on the ACTION_DOWN event, or you'll get two events (one for _DOWN, one for _UP)
                if (event.getAction() == KeyEvent.ACTION_DOWN) {

                    // Check if the HOME button is pressed
                    if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {

                        //Log.v(TAG, "BACK Button Pressed");

                        // As we've taken action, we'll return true to prevent other apps from consuming the event as well
                        return true;
                    }
                }

                // Otherwise don't intercept the event
                return super.dispatchKeyEvent(event);
            }
        };

        LayoutInflater inflater = ((LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE));

        if (inflater != null) {
            barcode_scanner = inflater.inflate(R.layout.barcode_scanner, interceptorLayout);

            FrameLayout preview = (FrameLayout) barcode_scanner.findViewById(R.id.cameraPreview);
            preview.addView(mPreview);

            Button b = barcode_scanner.findViewById(R.id.ScanButton);
            b.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View v) {
                    barcode_scanner.setVisibility(View.GONE);
                }

            });

            barcode_scanner.setOnTouchListener(this);
            windowManager.addView(barcode_scanner, params);
        }
    }

    private void releaseCamera() {
        if (mCamera != null) {
            previewing = false;
            mCamera.setPreviewCallback(null);
            mCamera.release();
            mCamera = null;
        }
    }

    private Runnable doAutoFocus = new Runnable() {
        public void run() {
            if (previewing)
                mCamera.autoFocus(autoFocusCB);
        }
    };

    Camera.PreviewCallback previewCb = new Camera.PreviewCallback() {
        public void onPreviewFrame(byte[] data, Camera camera) {
            Camera.Parameters parameters = camera.getParameters();
            Camera.Size size = parameters.getPreviewSize();

            Image barcode = new Image(size.width, size.height, "Y800");
            barcode.setData(data);

            int result = scanner.scanImage(barcode);

            Log.i("log", "foto " + System.currentTimeMillis());

            if (result != 0) {
                //previewing = false;
                //mCamera.setPreviewCallback(null);
                //mCamera.stopPreview();

                SymbolSet syms = scanner.getResults();
                for (Symbol sym : syms) {
                    Log.i("log", "scan");
                    Log.i("<<<<<<Asset Code>>>>> ",
                            "<<<<Bar Code>>> " + sym.getData());
                    String scanResult = sym.getData().trim();

                    Toast.makeText(getApplicationContext(), scanResult, Toast.LENGTH_SHORT).show();

                    barcodeScanned = true;

                    break;
                }
            }
        }
    };

    // Mimic continuous auto-focusing
    Camera.AutoFocusCallback autoFocusCB = new Camera.AutoFocusCallback() {
        public void onAutoFocus(boolean success, Camera camera) {
            autoFocusHandler.postDelayed(doAutoFocus, 1000);
        }
    };

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (barcode_scanner != null) {
            windowManager.removeView(barcode_scanner);

            barcode_scanner = null;
        }
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        return false;
    }
}
