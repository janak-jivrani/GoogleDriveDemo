package com.gdrive.demo;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.gdrive.demo.databinding.ActivityUploadFileBinding;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.karumi.dexter.Dexter;
import com.karumi.dexter.MultiplePermissionsReport;
import com.karumi.dexter.PermissionToken;
import com.karumi.dexter.listener.PermissionRequest;
import com.karumi.dexter.listener.multi.MultiplePermissionsListener;

import net.steamcrafted.loadtoast.LoadToast;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.FormBody;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class UploadFileActivity extends AppCompatActivity {


    ActivityUploadFileBinding binding;
    LoadToast loadToast;

    private static final String TAG = "UploadFileActivity";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityUploadFileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        loadToast = new LoadToast(this);
        requestForStoragePermission();

        setUpClickEvents();

    }

    private void setUpClickEvents() {
        binding.ivShare.setOnClickListener(v -> {
            Intent sendIntent = new Intent();
            sendIntent.setAction(Intent.ACTION_SEND);
            sendIntent.putExtra(Intent.EXTRA_SUBJECT, "Check uploaded file on drive");
            sendIntent.putExtra(Intent.EXTRA_TEXT, binding.tvURL.getText().toString());
            sendIntent.setType("text/plain");
            Intent shareIntent = Intent.createChooser(sendIntent, null);
            startActivity(shareIntent);
        });
        binding.ivCopy.setOnClickListener(v -> {
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            android.content.ClipData clip = android.content.ClipData.newPlainText("text label", binding.tvURL.getText().toString());
            clipboard.setPrimaryClip(clip);
        });
        binding.btnUploadFile.setOnClickListener(v -> selectFileToUpload());
    }

    // This method will get call when user click on upload file button
    public void selectFileToUpload() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        String[] mimeTypes = {"text/csv", "application/pdf", "application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"};
        intent.setType("*/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
        filePickerStartForResult.launch(intent);
    }


    ActivityResultLauncher<Intent> filePickerStartForResult = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), new ActivityResultCallback<ActivityResult>() {
        @Override
        public void onActivityResult(ActivityResult mResult) {
            if (mResult.getResultCode() == Activity.RESULT_OK) {
                Intent resultData = mResult.getData();
                if (resultData == null) {
                    //no data present
                    return;
                }

                loadToast.setText("Uploading file...");
                loadToast.show();

                // Get the Uri of the selected file
                Uri selectedFileUri = resultData.getData();
                Log.e(TAG, "selected File Uri: " + selectedFileUri);
                // Get the path
                String selectedFilePath = null;

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    // path could not be retrieved using ContentResolver, therefore copy file to accessible cache using streams
                    String fileName = FileUtils.getFileName(UploadFileActivity.this, selectedFileUri);
                    File cacheDir = FileUtils.getDocumentCacheDir(UploadFileActivity.this);
                    File file = FileUtils.generateFileName(fileName, cacheDir);
                    if (file != null) {
                        selectedFilePath = file.getAbsolutePath();
                        FileUtils.saveFileFromUri(UploadFileActivity.this, selectedFileUri, selectedFilePath);
                    }
                } else {
                    selectedFilePath = FileUtils.getPath(UploadFileActivity.this, selectedFileUri);
                }

                Log.e(TAG, "Selected File Path:" + selectedFilePath);

                if (selectedFilePath != null && !selectedFilePath.equals("")) {

                    MultipartBody.Part file = getMultipartFile("files[0]", selectedFilePath);

                    RequestBody mealType = FormBody.create(MediaType.parse("text/plain"), "android_assignment");
                    RequestBody serving = FormBody.create(MediaType.parse("text/plain"), "kamakshi");

                    Map<String, RequestBody> requestBodyMap = new HashMap<>();
                    requestBodyMap.put("sub_dir1", mealType);
                    requestBodyMap.put("sub_dir2", serving);

                    String auth = "bearer eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJzdWIiOjQzNzA5OTksImlzcyI6Imh0dHBzOi8vYmUxMi5wbGF0Zm9ybS5zaW1wbGlmaWkuY29tL2FwaS92MS9hZG1pbi9hdXRoZW50aWNhdGUiLCJpYXQiOjE2ODQyMTEyMTIsImV4cCI6MTc0NDY5MTIxMiwibmJmIjoxNjg0MjExMjEyLCJqdGkiOiJ3b29HeDFmZ0I2N1FGc0pJIn0.DF__mHMdlHIT6lQgfG76_h_LgrjL4D9u_ivTXGiTlBM";
                    ApiClient.getApiClientService().uploadFile(auth,requestBodyMap,file).enqueue(new Callback<JsonObject>() {
                        @Override
                        public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                            if (response.isSuccessful()) {
                                showMessage("File uploaded ...!!");
                                String result = response.body().get("response").getAsJsonObject().get("data").getAsJsonArray().get(0).getAsJsonObject().get("url").getAsString();
                                loadToast.success();
                                binding.cvCopy.setVisibility(View.VISIBLE);
                                binding.tvURL.setText(result);
                            } else {
                                loadToast.error();
                            }
                        }

                        @Override
                        public void onFailure(Call<JsonObject> call, Throwable t) {
                            t.printStackTrace();
                            loadToast.error();
                            showMessage("Couldn't able to upload file, error: " + t);
                        }
                    });
                } else {
                    loadToast.error();
                    Toast.makeText(UploadFileActivity.this, "Cannot upload file to server", Toast.LENGTH_SHORT).show();
                }
            }
        }
    });

    public MultipartBody.Part getMultipartFile(String name, String image_path) {
        if (image_path != null) {
            try {
                File file = new File(image_path);
                return MultipartBody.Part.createFormData(name, file.getName(),
                        RequestBody.create(MediaType.parse("application/form-data"), file));

            } catch (Exception e) {
                return null;
            }
        } else
            return null;
    }

    private void requestForStoragePermission() {
        if (android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.Q) {
            String[] str = new String[]{Manifest.permission.READ_EXTERNAL_STORAGE};

            Dexter.withContext(this).withPermissions(str).withListener(new MultiplePermissionsListener() {
                @Override
                public void onPermissionsChecked(MultiplePermissionsReport report) {
                    // check if all permissions are granted
                    if (report.areAllPermissionsGranted()) {
                        Toast.makeText(getApplicationContext(), "Permission is granted!", Toast.LENGTH_SHORT).show();

                    }

                    // check for permanent denial of any permission
                    if (report.isAnyPermissionPermanentlyDenied()) {
                        // show alert dialog navigating to Settings
                        showSettingsDialog();
                    }
                }

                @Override
                public void onPermissionRationaleShouldBeShown(List<PermissionRequest> permissions, PermissionToken token) {
                    token.continuePermissionRequest();
                }
            }).withErrorListener(error -> Toast.makeText(getApplicationContext(), "Error occurred! ", Toast.LENGTH_SHORT).show()).onSameThread().check();
        }
    }

    /**
     * Showing Alert Dialog with Settings option
     * Navigates user to app settings
     * NOTE: Keep proper title and message depending on your app
     */
    private void showSettingsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(UploadFileActivity.this);
        builder.setTitle("Need Permissions");
        builder.setMessage("This app needs permission to use this feature. You can grant them in app settings.");
        builder.setPositiveButton("GOTO SETTINGS", (dialog, which) -> {
            dialog.cancel();
            openSettings();
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();

    }

    // navigating user to app settings
    private void openSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        Uri uri = Uri.fromParts("package", getPackageName(), null);
        intent.setData(uri);
        startActivityForResult(intent, 101);
    }

    public void showMessage(String message) {
        Log.i(TAG, message);
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }


}
