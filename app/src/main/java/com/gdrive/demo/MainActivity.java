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
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.gdrive.demo.databinding.ActivityMainBinding;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.Scope;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.karumi.dexter.Dexter;
import com.karumi.dexter.MultiplePermissionsReport;
import com.karumi.dexter.PermissionToken;
import com.karumi.dexter.listener.PermissionRequest;
import com.karumi.dexter.listener.multi.MultiplePermissionsListener;

import net.steamcrafted.loadtoast.LoadToast;

import java.io.File;
import java.util.Collections;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    static GoogleDriveServiceHelper mDriveServiceHelper;
    GoogleSignInClient googleSignInClient;
    LoadToast loadToast;
    ActivityMainBinding binding;
    ActivityResultLauncher<Intent> singInStartForResult = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), mResult -> {
        if (mResult.getResultCode() == Activity.RESULT_OK) {
            Intent resultData = mResult.getData();
            if (resultData != null)
                handleSignInResult(resultData);
        }
    });

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

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    // path could not be retrieved using ContentResolver, therefore copy file to accessible cache using streams
                    String fileName = FileUtils.getFileName(MainActivity.this, selectedFileUri);
                    File cacheDir = FileUtils.getDocumentCacheDir(MainActivity.this);
                    File file = FileUtils.generateFileName(fileName, cacheDir);
                    if (file != null) {
                        selectedFilePath = file.getAbsolutePath();
                        FileUtils.saveFileFromUri(MainActivity.this, selectedFileUri, selectedFilePath);
                    }
                } else {
                    selectedFilePath = FileUtils.getPath(MainActivity.this, selectedFileUri);
                }

                Log.e(TAG, "Selected File Path:" + selectedFilePath);

                if (selectedFilePath != null && !selectedFilePath.equals("")) {

                    if (mDriveServiceHelper != null) {
                        mDriveServiceHelper.uploadFileToGoogleDrive(selectedFilePath).addOnSuccessListener(result -> {
                            showMessage("File uploaded ...!!");
                            try {
                                mDriveServiceHelper.handleFilePermission(result).addOnSuccessListener(results -> {
                                    loadToast.success();
                                    binding.cvCopy.setVisibility(View.VISIBLE);
                                    binding.tvURL.setText("https://drive.google.com/file/d/" + result);
                                }).addOnFailureListener(e -> {
                                    e.printStackTrace();
                                    loadToast.error();
                                });
                            } catch (GoogleJsonResponseException e) {
                                loadToast.error();
                                throw new RuntimeException(e);
                            }
                        }).addOnFailureListener(e -> {
                            loadToast.error();
                            showMessage("Couldn't able to upload file, error: " + e);
                        });
                    }
                } else {
                    loadToast.error();
                    Toast.makeText(MainActivity.this, "Cannot upload file to server", Toast.LENGTH_SHORT).show();
                }
            }
        }
    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
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
        binding.btnSignIn.setOnClickListener(v -> requestSignIn());
        binding.btnSignOut.setOnClickListener(v -> signOut());
        binding.btnUploadFile.setOnClickListener(v -> selectFileToUpload());
    }

    /**
     * Starts a sign-in activity for google.
     */
    private void requestSignIn() {

        GoogleSignInOptions signInOptions = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).requestScopes(new Scope(DriveScopes.DRIVE_FILE)).requestEmail().build();
        googleSignInClient = GoogleSignIn.getClient(this, signInOptions);

        singInStartForResult.launch(googleSignInClient.getSignInIntent());
    }


    /**
     * Handles the {@code result} of a completed sign-in activity initiated from {@link
     * #requestSignIn()}.
     */
    private void handleSignInResult(Intent result) {
        GoogleSignIn.getSignedInAccountFromIntent(result).addOnSuccessListener(googleAccount -> {
            Log.d(TAG, "Signed in as " + googleAccount.getEmail());

            // Use the authenticated account to sign in to the Drive service.
            GoogleAccountCredential credential = GoogleAccountCredential.usingOAuth2(this, Collections.singleton(DriveScopes.DRIVE_FILE));
            credential.setSelectedAccount(googleAccount.getAccount());
            Drive googleDriveService = new Drive.Builder(AndroidHttp.newCompatibleTransport(), new GsonFactory(), credential).setApplicationName("Drive API Migration").build();

            // The DriveServiceHelper encapsulates all REST API and SAF functionality.
            // Its instantiation is required before handling any onClick actions.
            mDriveServiceHelper = new GoogleDriveServiceHelper(googleDriveService);

            //enable other button as sign-in complete
            binding.btnSignIn.setEnabled(false);
            binding.btnUploadFile.setEnabled(true);
            binding.btnSignOut.setEnabled(true);

            showMessage("Sign-In done...!!");
        }).addOnFailureListener(exception -> {
            Log.e(TAG, "Unable to sign in.", exception);
            showMessage("Unable to sign in.");
            binding.btnSignIn.setEnabled(true);
        });
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

    // This method will get call when user click on sign-out button
    public void signOut() {
        if (googleSignInClient != null) {
            googleSignInClient.signOut().addOnCompleteListener(this, task -> {
                binding.btnSignIn.setEnabled(true);
                binding.btnUploadFile.setEnabled(false);
                binding.btnSignOut.setEnabled(false);
                showMessage("Sign-Out is done...!!");
                binding.cvCopy.setVisibility(View.GONE);
                binding.tvURL.setText("");
            }).addOnFailureListener(exception -> {
                binding.btnSignIn.setEnabled(false);
                showMessage("Unable to sign out.");
                Log.e(TAG, "Unable to sign out.", exception);
            });
        }
    }

    private void requestForStoragePermission() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) {
            String[] str = new String[]{Manifest.permission.READ_EXTERNAL_STORAGE};

            Dexter.withContext(this).withPermissions(str).withListener(new MultiplePermissionsListener() {
                @Override
                public void onPermissionsChecked(MultiplePermissionsReport report) {
                    // check if all permissions are granted
                    if (report.areAllPermissionsGranted()) {
                        Toast.makeText(getApplicationContext(), "All permissions are granted!", Toast.LENGTH_SHORT).show();
                        requestSignIn();
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
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
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