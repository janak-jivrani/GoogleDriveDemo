package com.gdrive.demo;

import android.util.Log;
import android.webkit.MimeTypeMap;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.FileContent;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.Permission;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * A utility for performing creating folder if not present, get the file, upload the file, download the file and
 * delete the file from google drive
 */
public class GoogleDriveServiceHelper {

    private static final String TAG = "GoogleDriveService";
    private final Executor mExecutor = Executors.newSingleThreadExecutor();
    private final Drive mDriveService;

    public GoogleDriveServiceHelper(Drive driveService) {
        mDriveService = driveService;
    }

    /**
     * Upload the file to the user's My Drive Folder.
     */
    public Task<String> uploadFileToGoogleDrive(String path) {

        return Tasks.call(mExecutor, () -> {

            String mimeType = getMimeType(path);

            Log.e(TAG, "uploadFileToGoogleDrive: path: " + path);
            java.io.File mFile = new java.io.File(path);

            File fileMetadata = new File();
            fileMetadata.setName(mFile.getName());
            fileMetadata.setMimeType(mimeType);

            FileContent mediaContent = new FileContent(mimeType, mFile);
            File file = mDriveService.files().create(fileMetadata, mediaContent)
                    .execute();
            return file.getId();
        });
    }

    /**
     * Change the permission of uploaded file
     */
    public Task<Boolean> handleFilePermission(String realFileId) throws GoogleJsonResponseException {
        return Tasks.call(mExecutor, () -> {
            Permission userPermission = new Permission()
                    .setType("anyone")
                    .setRole("reader");

            try {
                mDriveService.permissions().create(realFileId, userPermission)
                        .setFields("id")
                        .execute();
            } catch (Exception e) {
                e.printStackTrace();
            }
            return true;
        });
    }

    public static String getMimeType(String url) {
        String type = null;
        String extension = MimeTypeMap.getFileExtensionFromUrl(url);
        if (extension != null) {
            type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        }
        return type;
    }

}
