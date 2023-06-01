package com.example.project3;

import android.Manifest;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.firebase.FirebaseApp;
import com.google.firebase.ml.vision.FirebaseVision;
import com.google.firebase.ml.vision.common.FirebaseVisionImage;
import com.google.firebase.ml.vision.common.FirebaseVisionImageMetadata;
import com.google.firebase.ml.vision.common.FirebaseVisionPoint;
import com.google.firebase.ml.vision.face.FirebaseVisionFace;
import com.google.firebase.ml.vision.face.FirebaseVisionFaceContour;
import com.google.firebase.ml.vision.face.FirebaseVisionFaceDetector;
import com.google.firebase.ml.vision.face.FirebaseVisionFaceDetectorOptions;
import com.google.firebase.ml.vision.face.FirebaseVisionFaceLandmark;

import java.io.IOException;
import java.util.List;

public class FaceDetectionActivity extends AppCompatActivity implements SurfaceHolder.Callback, Camera.PreviewCallback {

    private static final String TAG = "FaceDetectionActivity";
    private static final int CAMERA_PERMISSION_REQUEST_CODE = 100;

    private Camera camera;
    private SurfaceView surfaceView;
    private ViewGroup rootLayout;
    private View boundingBoxView; // Declare the bounding box view as a member variable


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        FirebaseApp.initializeApp(this);

        surfaceView = findViewById(R.id.camera_preview);
        surfaceView.getHolder().addCallback(this);

        rootLayout = findViewById(android.R.id.content);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (checkCameraPermission()) {
            startCameraPreview();
        } else {
            requestCameraPermission();
        }
    }

    private boolean checkCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestCameraPermission() {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCameraPreview();
            } else {
                // Camera permission denied, handle accordingly
            }
        }
    }

    private void startCameraPreview() {
        camera = camera.open();

        try {
            camera.setPreviewDisplay(surfaceView.getHolder());
            camera.setDisplayOrientation(90);
            camera.setPreviewCallback(this);
        } catch (IOException e) {
            Log.e(TAG, "Error setting camera preview: " + e.getMessage());
        }

        // Configure the face detector options
        FirebaseVisionFaceDetectorOptions options =
                new FirebaseVisionFaceDetectorOptions.Builder()
                        .setContourMode(FirebaseVisionFaceDetectorOptions.ALL_CONTOURS)
                        .setClassificationMode(FirebaseVisionFaceDetectorOptions.ALL_CLASSIFICATIONS)
                        .setLandmarkMode(FirebaseVisionFaceDetectorOptions.ALL_LANDMARKS)
                        .setMinFaceSize(1.5f)
                        .setPerformanceMode(FirebaseVisionFaceDetectorOptions.ACCURATE)
                        .enableTracking()
                        .build();

        // Create the face detector instance
        FirebaseVisionFaceDetector faceDetector = FirebaseVision.getInstance().getVisionFaceDetector(options);

        // Start the camera preview
        camera.startPreview();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        // Empty implementation
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        camera.stopPreview();
        camera.setPreviewCallback(null);
        camera.release();
        camera = null;
    }

    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {
        Camera.Parameters parameters = camera.getParameters();
        Camera.Size size = parameters.getPreviewSize();

        FirebaseVisionFaceDetectorOptions options =
                new FirebaseVisionFaceDetectorOptions.Builder()
                        .build();

        FirebaseVisionFaceDetector detector = FirebaseVision.getInstance().getVisionFaceDetector(options);

        FirebaseVisionImageMetadata metadata = new FirebaseVisionImageMetadata.Builder()
                .setWidth(size.width)
                .setHeight(size.height)
                .setFormat(FirebaseVisionImageMetadata.IMAGE_FORMAT_NV21)
                .setRotation(FirebaseVisionImageMetadata.ROTATION_90)
                .build();

        FirebaseVisionImage image = FirebaseVisionImage.fromByteArray(data, metadata);

        detector.detectInImage(image)
                .addOnSuccessListener(faces -> {
                    // Remove the previous bounding boxes and landmarks if they exist
                    rootLayout.removeView(boundingBoxView);
                    boundingBoxView = null;

                    for (FirebaseVisionFace face : faces) {
                        float left = face.getBoundingBox().left;
                        float top = face.getBoundingBox().top;
                        float right = face.getBoundingBox().right;
                        float bottom = face.getBoundingBox().bottom;


                        Log.d(TAG, "Face detected");

                        String smile = "";
                        float smilingProbability = face.getSmilingProbability();
                        if (smilingProbability > 0) {
                            if (smilingProbability > 0.8) {
                                smile = "Great";
                            } else if (smilingProbability > 0.6) {
                                smile = "Good";
                            } else if (smilingProbability > 0.4) {
                                smile = "Okay";
                            } else {
                                smile = "Bad";
                            }

                        }else smile = "DONT GET";

                        String roast = generateRoast(smile);
                        drawBoundingBox(left, top, right, bottom, face, smile);

                        Log.d(TAG, "Smile: " + smile + ", Roast: " + roast);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error detecting faces: " + e.getMessage());
                });
    }

    private Camera getFrontCameraInstance() {
        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
        int cameraCount = Camera.getNumberOfCameras();
        for (int cameraIndex = 0; cameraIndex < cameraCount; cameraIndex++) {
            Camera.getCameraInfo(cameraIndex, cameraInfo);
            if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                try {
                    return Camera.open(cameraIndex);
                } catch (RuntimeException e) {
                    Log.e(TAG, "Error opening front camera: " + e.getMessage());
                }
            }
        }
        return null;
    }

    private void drawBoundingBox(float left, float top, float right, float bottom, FirebaseVisionFace face, String smile) {
        // Create a new bounding box view
        if (boundingBoxView != null) {
            rootLayout.removeView(boundingBoxView);
            boundingBoxView = null;
        }

        boundingBoxView = new View(this) {
            @Override
            protected void onDraw(android.graphics.Canvas canvas) {
                android.graphics.Paint boundingBoxPaint = new android.graphics.Paint();
                boundingBoxPaint.setColor(android.graphics.Color.RED);
                boundingBoxPaint.setStyle(android.graphics.Paint.Style.STROKE);
                boundingBoxPaint.setStrokeWidth(4);

                // Draw the bounding box
                canvas.drawRect(left, top, right, bottom, boundingBoxPaint);

                android.graphics.Paint landmarkPaint = new android.graphics.Paint();
                landmarkPaint.setColor(android.graphics.Color.GREEN);
                landmarkPaint.setStyle(android.graphics.Paint.Style.FILL);
                landmarkPaint.setStrokeWidth(4);

                // Draw the landmarks
                List<FirebaseVisionPoint> landmarks = face.getContour(FirebaseVisionFaceContour.ALL_POINTS).getPoints();
                for (FirebaseVisionPoint landmark : landmarks) {
                    float x = landmark.getX();
                    float y = landmark.getY();
                    canvas.drawCircle(x, y, 10, landmarkPaint);
                }
                android.graphics.Paint textPaint = new android.graphics.Paint();
                textPaint.setColor(android.graphics.Color.WHITE);
                textPaint.setStyle(android.graphics.Paint.Style.FILL);
                textPaint.setTextSize(24);

                // Draw the smile text
                canvas.drawText("Smile: " + smile, left, top - 20, textPaint);
            }
        };

        rootLayout.addView(boundingBoxView, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private String generateRoast(String smile) {
        String roast = "";
        switch (smile) {
            case "Great":
                roast = "You have a fantastic smile!";
                break;
            case "Good":
                roast = "Keep up the good work!";
                break;
            case "Okay":
                roast = "You can do better!";
                break;
            case "Bad":
                roast = "Please smile more often!";
                break;
            default:
                roast = "Unknown smile!";
                break;
        }
        return roast;
    }
}
