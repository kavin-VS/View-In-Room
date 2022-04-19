package com.android.sofa;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.RectF;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.MediaStore;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.SwitchCompat;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.extensions.HdrImageCaptureExtender;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.exifinterface.media.ExifInterface;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputLayout;
import com.google.common.util.concurrent.ListenableFuture;
import com.watermark.androidwm.WatermarkBuilder;
import com.watermark.androidwm.bean.WatermarkPosition;
import com.watermark.androidwm.bean.WatermarkText;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements View.OnTouchListener {

    private int REQUEST_CODE_PERMISSIONS = 1001;
    private final String[] REQUIRED_PERMISSIONS = new String[]{"android.permission.CAMERA",
            "android.permission.WRITE_EXTERNAL_STORAGE", "android.permission.READ_EXTERNAL_STORAGE"
            , "android.permission.INTERNET"};

    private int STORAGE_REQ_CODE = 1;

    private boolean CAMERA_ORIENTATION = false;
    private ConstraintLayout constraintLayout;

    //TODO: Image ZOOM ############################

    private static final String TAG = "MAIN_ACTIVITY";
    private static float MIN_ZOOM = 0.3f, MAX_ZOOM = (float) (Matrix.MSCALE_Y / 2.5f);

    // These matrices will be used to scale points of the image
    Matrix matrix = new Matrix();
    Matrix savedMatrix = new Matrix();

    // The 3 states (events) which the user is trying to perform
    static final int NONE = 0;
    static final int DRAG = 1;
    static final int ZOOM = 2;
    int mode = NONE;

    // these PointF objects are used to record the point(s) the user is touching
    PointF start = new PointF(0, 0);
    PointF mid = new PointF(0, 0);
    float oldDist = 1f;

    boolean doubleBackToExitPressedOnce = false;

    private int xDelta, yDelta;
    private int x, y;
    private PointF saved = new PointF();

    private ScaleGestureDetector scaleGestureDetector;
    private GestureDetector gestureDetector;

    //TODO: Image ZOOM XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX

    private boolean OVERLAY_FACE = false;
    private int lastSX = 1, lastSY = 1;

    private SensorManager mSensorManager;

    private ImageView mCaptureBtn;
    private PreviewView mCameraView;
    private ImageView mImageOverlay;
    private ProgressBar mProgressBar;

    private LinearLayout mLinearBtnContainer;
    private ImageView mSaveBtn, mShareBtn, mRetakeBtn;
    private ImageView mResultView, mGalleryBtn, mFlipBtn;

    private Switch logoVerifier;
    private boolean boolLogo;

    public static FrameLayout mFrameLayout;

    int windowWidth, windowHeight;

    float mScale = 1.0f;
    float exifScale = 1.0f;

    private Bitmap mBitmap;
    private Bitmap presentBitmap;

    public static boolean fromSofa = false;

    private Dialog addWatermarkDialog;
    private MaterialButton skipBtn, addWatermarkBtn;
    private TextInputLayout usernameInput, phoneNoInput, locationInput;

    private Executor executor = Executors.newSingleThreadExecutor();

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);

        initVariable();

        initWaterMarkDialog();

        windowWidth = getWindowManager().getDefaultDisplay().getWidth();
        windowHeight = getWindowManager().getDefaultDisplay().getHeight();

        mSensorManager = (SensorManager) this.getSystemService(Context.SENSOR_SERVICE);

        if (!CAMERA_ORIENTATION)
            checkOrientation();


        mBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.sofa_img_1);

        setImageBit(mBitmap);

        if (allPermissionGranted()) {

            startCamera();

        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS,
                    REQUEST_CODE_PERMISSIONS);
            finish();
        }

        //TODO: CLICK LISTENER  ########################

        mImageOverlay.setOnTouchListener(this);

        mGalleryBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

//                MAX_ZOOM = (float) (Matrix.MSCALE_Y / 4.0f);

                mImageOverlay.setImageDrawable(null);

                mImageOverlay.setScaleType(ImageView.ScaleType.CENTER_INSIDE);

                Intent i = new Intent();
                i.setType("image/*");
                i.setAction(Intent.ACTION_GET_CONTENT);
                startActivityForResult(Intent.createChooser(i,
                        "Select a Image"), STORAGE_REQ_CODE);
            }
        });

        //TODO: CLICK LISTENER  XXXXXXXXXXXXXXXXXXXXXXXXX
    }

    /**
     * Init Variables ######
     */

    private void initVariable() {
        mFrameLayout = findViewById(R.id.frame_layout);
        mProgressBar = findViewById(R.id.progressBar);
        constraintLayout = findViewById(R.id.constraintLayout);

        mLinearBtnContainer = findViewById(R.id.linearLayout);
        mFlipBtn = findViewById(R.id.main_mirror_btn);
        mSaveBtn = findViewById(R.id.main_save_btn);
        mRetakeBtn = findViewById(R.id.main_retake_btn);
        mShareBtn = findViewById(R.id.main_share_btn);

        mCaptureBtn = findViewById(R.id.capture_btn);
        mCameraView = findViewById(R.id.camera_preview);

        mResultView = findViewById(R.id.result_view);
        mImageOverlay = findViewById(R.id.imageOverlay);
        mGalleryBtn = findViewById(R.id.galleryView);

    }

    private void initWaterMarkDialog() {
        addWatermarkDialog = new Dialog(this);
        addWatermarkDialog.setContentView(R.layout.save_watermark_dialog);
        addWatermarkDialog.setCancelable(true);
        addWatermarkDialog.getWindow()
                .setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);

        usernameInput = addWatermarkDialog.findViewById(R.id.name_input);
        phoneNoInput = addWatermarkDialog.findViewById(R.id.phone_no_input);
        locationInput = addWatermarkDialog.findViewById(R.id.location_input);
        skipBtn = addWatermarkDialog.findViewById(R.id.save_and_skip_btn);
        addWatermarkBtn = addWatermarkDialog.findViewById(R.id.add_watermark_btn);
        logoVerifier = addWatermarkDialog.findViewById(R.id.logo_verifier_switch);
    }

    /**
     * Image Overlay Related M & F ###
     **/

    @Override
    public boolean onTouch(View v, MotionEvent event) {

        ImageView view = (ImageView) v;

        view.setScaleType(ImageView.ScaleType.MATRIX);

        switch (event.getAction() & MotionEvent.ACTION_MASK) {

            case MotionEvent.ACTION_DOWN:   // first finger down only

                matrix.set(view.getImageMatrix());

                savedMatrix.set(matrix);
                start.set(event.getX(), event.getY());
                Log.d(TAG, "mode=DRAG"); // write to LogCat
                mode = DRAG;

               /* if (System.currentTimeMillis() - startTime <= MAX_DURATION) {
                    Log.d(TAG, "DoubleTap: TRUE");
                }*/

                break;

            case MotionEvent.ACTION_UP: // first finger lifted

//                startTime = System.currentTimeMillis();

            case MotionEvent.ACTION_POINTER_UP: // second finger lifted

                mode = NONE;
                Log.d(TAG, "mode=NONE");
                break;

            case MotionEvent.ACTION_POINTER_DOWN: // first and second finger down

                oldDist = spacing(event);
                if (oldDist > 5f) {
                    savedMatrix.set(matrix);
                    midPoint(mid, event);
                    mode = ZOOM;
                    Log.d(TAG, "mode=ZOOM");
                }
                break;

            case MotionEvent.ACTION_MOVE:

                if (mode == DRAG) {
                    matrix.set(savedMatrix);
                    matrix.postTranslate((event.getX() - start.x), (event.getY() - start.y));
                    // create the transformation in the matrix  of points

                } else if (mode == ZOOM) {
                    float[] f = new float[9];
                    // pinch zooming
                    float newDist = spacing(event);

                    if (newDist > 5f) {
                        matrix.set(savedMatrix);
                        mScale = newDist / oldDist;

                        exifScale = mScale;

                        matrix.postScale(mScale, mScale, mid.x, mid.y);
                    }

                    matrix.getValues(f);
                    float scaleX = f[Matrix.MSCALE_X];
                    float scaleY = f[Matrix.MSCALE_Y];

                    if (scaleX <= MIN_ZOOM) {
                        matrix.postScale((MIN_ZOOM) / scaleX, (MIN_ZOOM) / scaleY, mid.x, mid.y);
                    } else if (scaleX >= MAX_ZOOM) {
                        matrix.postScale((MAX_ZOOM) / scaleX, (MAX_ZOOM) / scaleY, mid.x, mid.y);
                    }
                }
                break;
        }

        view.setImageMatrix(matrix);

        return true;
    }

    //TODO: OVERLAY  XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX


    /**
     * CameraX Related M & F #####
     **/

    private void startCamera() {

        final ListenableFuture<ProcessCameraProvider> cameraProviderListenable =
                ProcessCameraProvider.getInstance(this);

        cameraProviderListenable.addListener(new Runnable() {
            @Override
            public void run() {
                try {
                    ProcessCameraProvider cameraProvider = cameraProviderListenable.get();
                    bindPreview(cameraProvider);

                } catch (Exception e) {
                    Toast.makeText(MainActivity.this,
                            e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        }, ContextCompat.getMainExecutor(this));
    }

    void bindPreview(ProcessCameraProvider cameraProvider) {

        Preview preview = new Preview.Builder().build();

        CameraSelector mCameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK).build();

        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder().build();

        ImageCapture.Builder builder = new ImageCapture.Builder();

        HdrImageCaptureExtender hdrImageCaptureExtender = HdrImageCaptureExtender.create(builder);

        if (hdrImageCaptureExtender.isExtensionAvailable(mCameraSelector)) {
            hdrImageCaptureExtender.enableExtension(mCameraSelector);
        }

        final ImageCapture capture = builder.setTargetRotation(this.getWindowManager()
                .getDefaultDisplay().getRotation()).build();

        preview.setSurfaceProvider(mCameraView.getSurfaceProvider());

        Camera mCamera = cameraProvider.bindToLifecycle(this,
                mCameraSelector, preview, imageAnalysis, capture);

        mCamera.getCameraControl().cancelFocusAndMetering();

        mCaptureBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                CAMERA_ORIENTATION = true;

                mGalleryBtn.setRotation(0f);
                mSaveBtn.setRotation(0f);
                mRetakeBtn.setRotation(0f);
                mShareBtn.setRotation(0f);

                mFlipBtn.setVisibility(View.GONE);
                mCaptureBtn.setVisibility(View.INVISIBLE);
                mGalleryBtn.setVisibility(View.INVISIBLE);
                mProgressBar.setVisibility(View.VISIBLE);

                mCameraView.setVisibility(View.GONE);
                mResultView.setVisibility(View.VISIBLE);

                final File file = new File(getCacheDirectoryName(), ".cache.jpg");

                ImageCapture.OutputFileOptions outputFileOptions =
                        new ImageCapture.OutputFileOptions
                                .Builder(file).build();

                capture.takePicture(outputFileOptions, executor, new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {

                                if (file.exists()) {
                                    Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());

                                    exifRotation(file, bitmap);

                                    mProgressBar.setVisibility(View.INVISIBLE);
                                    mCaptureBtn.setVisibility(View.INVISIBLE);

                                    mShareBtn.setVisibility(View.GONE);
                                    mSaveBtn.setVisibility(View.VISIBLE);
                                    mRetakeBtn.setVisibility(View.VISIBLE);

                                    clickFun();

                                } else {
                                    Toast.makeText(MainActivity.this,
                                            "File Not Founded", Toast.LENGTH_SHORT).show();
                                }
                            }
                        });
                    }

                    @Override
                    public void onError(@NonNull final ImageCaptureException exception) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(MainActivity.this, exception.getMessage(),
                                        Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                });
            }
        });
    }

    //TODO: CAMERA-X  XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX


    /**
     * M & F Only #####
     **/

    private float spacing(MotionEvent event) {
        float x = event.getX(0) - event.getX(1);
        float y = event.getY(0) - event.getY(1);
        return (float) Math.sqrt(x * x + y * y);
    }

    private void midPoint(PointF point, MotionEvent event) {
        float x = event.getX(0) / 2 + event.getX(1) / 2;
        float y = event.getY(0) / 2 + event.getY(1) / 2;
        Log.d(TAG, "mid=" + point.x + "," + point.y);
        point.set(x, y);
    }


    public void checkOrientation() {
        mSensorManager.registerListener(new SensorEventListener() {

            int orientation = -1;

            float imageAngle = 0;

            @Override
            public void onSensorChanged(SensorEvent event) {

                /*float xValue = event.values[0];
                float yValue = event.values[1];
                float zValue = event.values[2];
                Log.d("Values", " x :" + xValue + "; y :" + yValue + "; z :" + zValue);*/

                if (!CAMERA_ORIENTATION) {

                    if (event.values[1] > 6.5) {

                        if (orientation != 0) {

//                            MAX_ZOOM = (float) (Matrix.MSCALE_Y / 5.0f);

                            imageAngle = 0f;

                            mFlipBtn.setRotation(0f);
                            mGalleryBtn.setRotation(0f);
                            mSaveBtn.setRotation(0f);
                            mRetakeBtn.setRotation(0f);
                            mShareBtn.setRotation(0f);

                            mImageOverlay.setScaleType(ImageView.ScaleType.CENTER_INSIDE);

                            mImageOverlay.setImageBitmap(rotateImage(presentBitmap, 0f, lastSX, lastSY));

                        }
                        orientation = 0;
                    } else if (event.values[1] < 6.5 && event.values[0] > 6.5) {

                        if (orientation != 1) {

//                            MAX_ZOOM = (float) (Matrix.MSCALE_Y / 2.5f);

                            imageAngle = 90f;

                            mFlipBtn.setRotation(90f);
                            mGalleryBtn.setRotation(90f);
                            mSaveBtn.setRotation(90f);
                            mRetakeBtn.setRotation(90f);
                            mShareBtn.setRotation(90f);

                            mImageOverlay.setScaleType(ImageView.ScaleType.CENTER_INSIDE);

                            if (lastSX != -1) {
                                mImageOverlay.setImageBitmap(rotateImage(presentBitmap, 90f, lastSX, lastSY));
                            } else {
                                mImageOverlay.setImageBitmap(rotateImage(presentBitmap, 90f, 1, -1));
                            }
                        }
                        orientation = 1;
                    } else if (event.values[1] < -6.5) {

                        if (orientation != 2) {

//                            MAX_ZOOM = (float) (Matrix.MSCALE_Y / 5.0f);

                            imageAngle = 180f;

                            mFlipBtn.setRotation(180f);
                            mGalleryBtn.setRotation(180f);
                            mSaveBtn.setRotation(180f);
                            mRetakeBtn.setRotation(180f);
                            mShareBtn.setRotation(180f);

                            mImageOverlay.setScaleType(ImageView.ScaleType.CENTER_INSIDE);

                            mImageOverlay.setImageBitmap(rotateImage(presentBitmap, 180f, lastSX, lastSY));

                        }
                        orientation = 2;
                    } else if (event.values[1] < 6.5 && event.values[0] < -6.5) {

                        if (orientation != 3) {

//                            MAX_ZOOM = (float) (Matrix.MSCALE_Y / 2.5f);

                            imageAngle = 270f;

                            mFlipBtn.setRotation(270f);
                            mGalleryBtn.setRotation(270f);
                            mSaveBtn.setRotation(270f);
                            mRetakeBtn.setRotation(270f);
                            mShareBtn.setRotation(270f);

                            mImageOverlay.setScaleType(ImageView.ScaleType.CENTER_INSIDE);

                            if (lastSX != -1) {
                                mImageOverlay.setImageBitmap(rotateImage(presentBitmap, 270f, lastSX, lastSY));
                            } else {
                                mImageOverlay.setImageBitmap(rotateImage(presentBitmap, 270f, 1, -1));
                            }
                        }
                        orientation = 3;
                    }

                    mFlipBtn.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            //                imageOverlay.setImageBitmap(rotateImage(presentBitmap, 90));
                            //                imageOverlay.setRotationY(imageOverlay.getRotationY() + 180);
                            if (!OVERLAY_FACE) {
                                lastSX = -1;
                                lastSY = 1;
                                mImageOverlay.setImageBitmap(flipImage(presentBitmap, lastSX, lastSY));
                                OVERLAY_FACE = true;
                            } else {
                                lastSX = 1;
                                lastSY = 1;
                                mImageOverlay.setImageBitmap(flipImage(presentBitmap, lastSX, lastSY));
                                OVERLAY_FACE = false;
                            }

                            if (imageAngle == 90f) {
                                if (lastSX != -1) {
                                    mImageOverlay.setImageBitmap(rotateImage(presentBitmap, 90f, lastSX, lastSY));
                                } else {
                                    mImageOverlay.setImageBitmap(rotateImage(presentBitmap, 90f, 1, -1));
                                }
                            } else if (imageAngle == 180f) {
                                mImageOverlay.setImageBitmap(rotateImage(presentBitmap, 180f, lastSX, lastSY));
                            } else if (imageAngle == 270f) {
                                if (lastSX != -1) {
                                    mImageOverlay.setImageBitmap(rotateImage(presentBitmap, 270f, lastSX, lastSY));
                                } else {
                                    mImageOverlay.setImageBitmap(rotateImage(presentBitmap, 270f, 1, -1));
                                }
                            }
                        }
                    });

                } else {
                    mFlipBtn.setRotation(0f);
                    mGalleryBtn.setRotation(0f);
                    mSaveBtn.setRotation(0f);
                    mRetakeBtn.setRotation(0f);
                    mShareBtn.setRotation(0f);
                }
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {
                // TODO Auto-generated method stub
            }
        }, mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_GAME);
    }


    private void exifRotation(File mFile, Bitmap bitmap) {
        ExifInterface ei = null;
        try {
            ei = new ExifInterface(mFile.getAbsolutePath());
        } catch (IOException e) {
            Toast.makeText(MainActivity.this, e.getMessage(), Toast.LENGTH_SHORT).show();
        }
        int ori = ei.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_UNDEFINED);

        Bitmap rotateBit = null;
        switch (ori) {

            case ExifInterface.ORIENTATION_ROTATE_90:
                rotateBit = exifImage(bitmap, 90);
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                rotateBit = exifImage(bitmap, 180);
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                rotateBit = exifImage(bitmap, 270);
                break;
            case ExifInterface.ORIENTATION_NORMAL:

            default:
                rotateBit = bitmap;
        }

        mResultView.setImageBitmap(rotateBit);
    }


    private Bitmap logoWatermark(Bitmap src) {

        float scale;
        int w = src.getWidth();
        int h = src.getHeight();

        final Bitmap result = Bitmap.createBitmap(w, h, src.getConfig());

        Bitmap logoBitmap = BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher_foreground);

        int centreX = (logoBitmap.getWidth() - w) / 2;
        int centreY = (logoBitmap.getHeight() - h) / 2;

        scale = (float) (((float) h * 0.10) / (float) logoBitmap.getHeight());

        Matrix matrix = new Matrix();
        matrix.postScale(scale, scale);

        RectF f = new RectF(0, 0, logoBitmap.getWidth(), logoBitmap.getHeight());
        matrix.mapRect(f);

        matrix.postTranslate(0, 0);

        final Canvas canvas = new Canvas(result);
        canvas.drawBitmap(src, 0, 0, null);
        final Canvas canvasLoc = new Canvas(result);
        canvasLoc.drawBitmap(src, 0, 0, null);

        final Paint p = new Paint();
        p.setColor(Color.WHITE);
        p.setAlpha(200);
        p.setAntiAlias(true);
        p.setTextSize(45);
        p.setTextAlign(Paint.Align.CENTER);
        Paint.FontMetrics metric = p.getFontMetrics();
        int textHeight = (int) Math.ceil(metric.descent - metric.ascent);
        int y = (int) (textHeight - metric.descent);

        Paint paint = new Paint();
        paint.setAlpha(150);

        addWatermarkBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!usernameInput.getEditText().getText().toString().isEmpty() &
                        !phoneNoInput.getEditText().getText().toString().isEmpty()) {

                    if (!locationInput.getEditText().getText().toString().isEmpty()) {

                        canvas.drawText(usernameInput.getEditText().getText().toString() +
                                        " ~ " + phoneNoInput.getEditText().getText().toString(),
                                (float) (result.getWidth() / 2), (result.getHeight() / 10), p);
                        canvasLoc.drawText(locationInput.getEditText().getText().toString(),
                                (float) (result.getWidth() / 2), (result.getHeight() / 8), p);

                    } else {
                        canvas.drawText(usernameInput.getEditText().getText().toString() +
                                        " ~ " + phoneNoInput.getEditText().getText().toString(),
                                (float) (result.getWidth() / 2), (result.getHeight() / 10), p);
                    }

                    if (logoVerifier.isChecked()) {
                        canvas.drawBitmap(logoBitmap, matrix, paint);
                    }

                    addWatermarkDialog.dismiss();

                    mFlipBtn.setVisibility(View.GONE);
                    mSaveBtn.setVisibility(View.GONE);
                    mLinearBtnContainer.setVisibility(View.GONE);
                    constraintLayout.setVisibility(View.GONE);

                    if (!addWatermarkDialog.isShowing() &&
                            mLinearBtnContainer.getVisibility() == View.GONE &&
                            constraintLayout.getVisibility() == View.GONE) {
                        saveByViewFile();
//                        mResultView.setImageBitmap(src);
                    }

                    mLinearBtnContainer.setVisibility(View.VISIBLE);
                    constraintLayout.setVisibility(View.VISIBLE);
                    mShareBtn.setVisibility(View.VISIBLE);
                    mImageOverlay.setVisibility(View.INVISIBLE);

                    Toast.makeText(MainActivity.this,
                            "Image Saved with Text\n@/sdcard/CamPro/IMG_.jpg",
                            Toast.LENGTH_SHORT).show();

                } else {
                    usernameInput.requestFocus();
                    usernameInput.setError("Required Field");
                    phoneNoInput.setError("Required Field");
                }
            }
        });

        skipBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addWatermarkDialog.dismiss();

                if (logoVerifier.isChecked()) {
                    canvas.drawBitmap(logoBitmap, matrix, paint);
                }

                usernameInput.getEditText().setText("");
                phoneNoInput.getEditText().setText("");
                locationInput.getEditText().setText("");

                mFlipBtn.setVisibility(View.GONE);

                mLinearBtnContainer.setVisibility(View.GONE);
                constraintLayout.setVisibility(View.GONE);

                if (!addWatermarkDialog.isShowing() &&
                        mLinearBtnContainer.getVisibility() == View.GONE &&
                        constraintLayout.getVisibility() == View.GONE) {
                    saveFinal();
                }

                mLinearBtnContainer.setVisibility(View.VISIBLE);
                constraintLayout.setVisibility(View.VISIBLE);
                mShareBtn.setVisibility(View.VISIBLE);

                Toast.makeText(MainActivity.this,
                        "Image Saved\n@/sdcard/CamPro/IMG_.jpg",
                        Toast.LENGTH_SHORT).show();

                mSaveBtn.setVisibility(View.GONE);
//                        mirrorBtn.setVisibility(View.INVISIBLE);
            }
        });
        return result;
    }

    // TODO: OnClick #####################################

    private void clickFun() {

        mSaveBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                addWatermarkDialog.show();

                usernameInput.getEditText().setText("");
                phoneNoInput.getEditText().setText("");
                locationInput.getEditText().setText("");

                usernameInput.getEditText().setFocusable(true);

                mFlipBtn.setVisibility(View.GONE);
                mLinearBtnContainer.setVisibility(View.GONE);
                constraintLayout.setVisibility(View.GONE);

                if (mLinearBtnContainer.getVisibility() == View.GONE &&
                        constraintLayout.getVisibility() == View.GONE) {
                    saveCacheFile();
                }

                mLinearBtnContainer.setVisibility(View.VISIBLE);
                constraintLayout.setVisibility(View.VISIBLE);
            }
        });

        mRetakeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                mCaptureBtn.setVisibility(View.VISIBLE);
                mGalleryBtn.setVisibility(View.VISIBLE);
                mCameraView.setVisibility(View.VISIBLE);

                mResultView.setVisibility(View.GONE);
                mResultView.setImageBitmap(null);

                mFlipBtn.setVisibility(View.VISIBLE);
                mImageOverlay.setVisibility(View.VISIBLE);

                mSaveBtn.setVisibility(View.INVISIBLE);
                mShareBtn.setVisibility(View.INVISIBLE);
                mRetakeBtn.setVisibility(View.INVISIBLE);

                CAMERA_ORIENTATION = false;

                mImageOverlay.setImageBitmap(presentBitmap);

//                Intent i = getIntent();
//                startActivity(i);
//                finish();
//                imageOverlay.setImageBitmap(presentBitmap);
            }
        });

        mShareBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                mLinearBtnContainer.setVisibility(View.GONE);
                constraintLayout.setVisibility(View.GONE);

                Bitmap screenBit = null;

                if (mLinearBtnContainer.getVisibility() == View.GONE &
                        constraintLayout.getVisibility() == View.GONE) {
                    screenBit = ScreenshotUtil.getInstance().takeScreenshotForView(mResultView);
                }

                mLinearBtnContainer.setVisibility(View.VISIBLE);
                constraintLayout.setVisibility(View.VISIBLE);

                Uri uri = shareImage(screenBit);

                Intent intent = new Intent();
                intent.setAction(Intent.ACTION_SEND);
                intent.putExtra(Intent.EXTRA_STREAM, uri);
                intent.setType("image/png");
                startActivity(Intent.createChooser(intent, getResources().getText(R.string.app_name)));
            }
        });
    }


    // TODO: OnClick XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX


    //TODO: Methods and Functions XXXXXXXXXXXXXXXXXXXXXXXXXXXXXX


    /**
     * File And Share #####
     * Related M & F #####
     */

    private void saveFinal() {
        Bitmap screenBit2 = ScreenshotUtil.getInstance()
                .takeScreenshotForScreen(MainActivity.this);

        WatermarkText watermarkText = new WatermarkText("~View In Room~")
                .setPositionX(0.5)
                .setPositionY(0.5)
                .setTextColor(Color.WHITE)
                .setTextAlpha(90)
                .setRotation(30)
                .setTextSize(12);

        Bitmap screenBit = WatermarkBuilder
                .create(MainActivity.this, screenBit2)
                .loadWatermarkText(watermarkText)
                .setTileMode(true)
                .getWatermark()
                .getOutputImage();


        File shareF = new File(MainActivity.this.getFilesDir(), getBatchDirectoryName());
        FileOutputStream fos = null;

        mImageOverlay.setVisibility(View.INVISIBLE);
        mResultView.setImageBitmap(screenBit);
        waterCall(screenBit);

        try {
            shareF.mkdirs();
            SimpleDateFormat mDateFormat = new SimpleDateFormat("yyyyMMddHHmmss", Locale.US);
            File file = new File(getBatchDirectoryName(),
                    "IMG_" + mDateFormat.format(new Date()) + ".jpg");

            fos = new FileOutputStream(file);
            assert screenBit != null;
            screenBit.compress(Bitmap.CompressFormat.JPEG, 90, fos);
            fos.flush();
            fos.close();

//            retakeBtn.setImageResource(R.drawable.go_back);

        } catch (IOException e) {
            Toast.makeText(MainActivity.this, e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void saveCacheFile() {
        Bitmap screenBit = ScreenshotUtil.getInstance()
                .takeScreenshotForScreen(MainActivity.this);

        File shareF = new File(MainActivity.this.getFilesDir(), getCacheDirectoryName());
        FileOutputStream fos = null;

        mImageOverlay.setVisibility(View.INVISIBLE);
        mResultView.setImageBitmap(screenBit);
        waterCall(screenBit);

        try {
            shareF.mkdirs();
//            SimpleDateFormat mDateFormat = new SimpleDateFormat("yyyyMMddHHmmss", Locale.US);
            File file = new File(getCacheDirectoryName(),
                    ".im_cache.jpg");

            fos = new FileOutputStream(file);
            assert screenBit != null;
            screenBit.compress(Bitmap.CompressFormat.JPEG, 90, fos);
            fos.flush();
            fos.close();

//            retakeBtn.setImageResource(R.drawable.go_back);

        } catch (IOException e) {
            Toast.makeText(MainActivity.this, e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void saveByViewFile() {
        Bitmap screenBit2 = ScreenshotUtil.getInstance().takeScreenshotForView(mResultView);

        WatermarkText watermarkText = new WatermarkText("-View In Room-")
                .setPositionX(0.5)
                .setPositionY(0.5)
                .setTextColor(Color.WHITE)
                .setTextAlpha(70)
                .setRotation(30)
                .setTextSize(12);

        Bitmap screenBit = WatermarkBuilder
                .create(MainActivity.this, screenBit2)
                .loadWatermarkText(watermarkText)
                .setTileMode(true)
                .getWatermark()
                .getOutputImage();

        File shareF = new File(MainActivity.this.getFilesDir(), getBatchDirectoryName());
        FileOutputStream fos = null;

        mImageOverlay.setVisibility(View.INVISIBLE);
        mResultView.setImageBitmap(screenBit);
        waterCall(screenBit);

        try {
            shareF.mkdirs();
            SimpleDateFormat mDateFormat = new SimpleDateFormat("yyyyMMddHHmmss", Locale.US);
            File file = new File(getBatchDirectoryName(),
                    "IMG_" + mDateFormat.format(new Date()) + ".jpg");

            fos = new FileOutputStream(file);
            assert screenBit != null;
            screenBit.compress(Bitmap.CompressFormat.JPEG, 90, fos);
            fos.flush();
            fos.close();

//            retakeBtn.setImageResource(R.drawable.go_back);

        } catch (IOException e) {
            Toast.makeText(MainActivity.this, e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private Uri shareImage(Bitmap bitmap) {
        File shareFile = new File(MainActivity.this.getFilesDir(), getCacheDirectoryName());
        Uri uri = null;
        FileOutputStream fos;
        try {
            shareFile.mkdirs();
            File file = new File(getCacheDirectoryName(), ".shared.jpg");

            fos = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos);
            fos.flush();
            fos.close();
            uri = FileProvider.getUriForFile(MainActivity.this,
                    "com.android.sofa.fileprovider", file);

        } catch (Exception e) {
            Toast.makeText(MainActivity.this, e.getMessage(), Toast.LENGTH_SHORT).show();
        }
        return uri;
    }

    // TODO: File and Share XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX


    /**
     * Bitmaps And Angles #####
     * Related M & F #####
     */

    private void waterCall(Bitmap bitmap) {
        Bitmap temp = logoWatermark(bitmap);
        mResultView.setImageBitmap(temp);
    }

    private void setImageBit(Bitmap bit) {
        presentBitmap = bit;
        Glide.with(MainActivity.this).load(presentBitmap).into(mImageOverlay);
    }

    public static Bitmap rotateImage(Bitmap bitmap, float angle, int sx, int sy) {
        Matrix matrix = new Matrix();
        matrix.postRotate(angle);
        matrix.postScale(sx, sy, bitmap.getWidth() / 2f, bitmap.getHeight() / 2f);
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(),
                matrix, true);
    }

    public static Bitmap exifImage(Bitmap bitmap, float angle) {
        Matrix matrix = new Matrix();
        matrix.postRotate(angle);
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(),
                matrix, true);
    }

    public static Bitmap flipImage(Bitmap bitmap, int sx, int sy) {
        Matrix matrix = new Matrix();
        matrix.postScale(sx, sy, bitmap.getWidth() / 2f, bitmap.getHeight() / 2f);
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(),
                matrix, true);
    }

    // TODO: Bitmap and Angle XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX


    public String getBatchDirectoryName() {

        String app_folder_path = "";
        app_folder_path = Environment.getExternalStorageDirectory().toString() + "/CamPro";
        File dir = new File(app_folder_path);
        if (!dir.exists() && !dir.mkdirs()) {

        }
        return app_folder_path;
    }

    public String getCacheDirectoryName() {

        String app_folder_path = "";
        app_folder_path = Environment.getExternalStorageDirectory().toString() +
                "/Android/data/" + getPackageName() + "/Files/Caches";
        File dir = new File(app_folder_path);
        if (!dir.exists() && !dir.mkdirs()) {
        }
        return app_folder_path;
    }

    /**
     * Permission and Storage #####
     * Related M & F #####
     */

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK && requestCode == STORAGE_REQ_CODE && data != null) {
            try {
                Bitmap bitmap = MediaStore.Images.Media
                        .getBitmap(this.getContentResolver(), data.getData());
                setImageBit(bitmap);
            } catch (IOException e) {
                Toast.makeText(MainActivity.this, e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        } else if (resultCode == Activity.RESULT_CANCELED) {
            Toast.makeText(MainActivity.this,
                    "Image Not Selected", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean allPermissionGranted() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionGranted()) {
                startCamera();
            } else {
                Toast.makeText(this, "Permissions Denied", Toast.LENGTH_SHORT).show();
                this.finish();
                ActivityCompat.requestPermissions(this,
                        REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
            }
        }
    }

    //TODO: Permission and Storage XXXXXXXXXXXXXXXXXXXXXX

    /**
     * Full Screen #####
     * Related M & F #####
     */

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        hideSystemUI(getWindow());
    }


    // This snippet hides the system bars.
    private void hideSystemUI(Window window) {
        // Set the IMMERSIVE flag.
        // Set the content to appear under the system bars so that the content
        // doesn't resize when the system bars hide and show.
        View mDecorView = window.getDecorView();
        mDecorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION // hide nav bar
                | View.SYSTEM_UI_FLAG_FULLSCREEN // hide status bar
                | View.SYSTEM_UI_FLAG_IMMERSIVE
                | View.SYSTEM_UI_FLAG_LOW_PROFILE
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        );
    }

    // This snippet shows the system bars. It does this by removing all the flags
    // except for the ones that make the content appear under the system bars.
    private void showSystemUI() {
        View mDecorView = getWindow().getDecorView();
        mDecorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }

    //TODO: Full Screen XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX

    @Override
    public void onBackPressed() {
        if (doubleBackToExitPressedOnce) {
            super.onBackPressed();
            return;
        }

        this.doubleBackToExitPressedOnce = true;

        Toast.makeText(this, "Click twice to exit",
                Toast.LENGTH_SHORT).show();

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                doubleBackToExitPressedOnce = false;
            }
        }, 2000);
    }
}