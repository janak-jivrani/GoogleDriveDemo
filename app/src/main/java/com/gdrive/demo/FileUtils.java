package com.gdrive.demo;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.OpenableColumns;

import androidx.annotation.RequiresApi;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;


//we created FileUtils for getting the correct path of selected file
class FileUtils {

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    public static String getPath(final Context context, final Uri uri) {

        // DocumentProvider
        if (DocumentsContract.isDocumentUri(context, uri)) {

            if (isExternalStorageDocument(uri)) {// ExternalStorageProvider
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];
                String storageDefinition;

                if ("primary".equalsIgnoreCase(type)) {
                    return Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + split[1];
                } else {
                    if (Environment.isExternalStorageRemovable()) {
                        storageDefinition = "EXTERNAL_STORAGE";
                    } else {
                        storageDefinition = "SECONDARY_STORAGE";
                    }

                    final String externalStorage = System.getenv(storageDefinition);
                    final String[] splitpath = externalStorage.split("/");
                    String returnPath;
                    if (splitpath[2].equalsIgnoreCase(type)) {
                        returnPath = externalStorage + "/" + split[1];
                    } else {
                        returnPath = "/" + splitpath[1] + "/" + split[0] + "/" + split[1];
                    }

                    return returnPath;
                }

            } else if (isDownloadsDocument(uri)) {// DownloadsProvider
                //String id = DocumentsContract.getDocumentId(uri);
                String returnPath = null;

                String fileNameFile = getFileName(context, uri);
                if (fileNameFile != null) {
                    File nFile = new File(Environment.getExternalStorageDirectory().toString() + "/Download/" + fileNameFile);
                    if (nFile.exists()) {
                        return nFile.getAbsolutePath();
                    }
                }
                String id = DocumentsContract.getDocumentId(uri);

                if (id != null) {
                    if (id.startsWith("raw:")) {
                        returnPath = id.substring(4);
                    } else if (id.contains("/")) {
                        if (id.startsWith("/")) {
                            returnPath = id;
                        } else {
                            returnPath = id.substring(id.indexOf("/"));
                        }
                    } else {
                        String[] contentUriPrefixesToTry = new String[]{
                                "content://downloads/public_downloads",
                                "content://downloads/my_downloads",
                                "content://downloads/all_downloads"
                        };

                        for (String contentUriPrefix : contentUriPrefixesToTry) {
                            Uri contentUri = ContentUris.withAppendedId(Uri.parse(contentUriPrefix), Long.valueOf(id));
                            try {
                                String path = getDataColumn(context, contentUri, null, null);
                                if (path != null) {
                                    returnPath = path;
                                    break;
                                }
                            } catch (Exception e) {
                            }
                        }
                    }

                    if (returnPath == null) {
                        // path could not be retrieved using ContentResolver, therefore copy file to accessible cache using streams
                        String fileName = getFileName(context, uri);
                        File cacheDir = getDocumentCacheDir(context);
                        File file = generateFileName(fileName, cacheDir);
                        if (file != null) {
                            returnPath = file.getAbsolutePath();
                            saveFileFromUri(context, uri, returnPath);
                        }
                    }

                }

                return returnPath;

            } else if (isMediaDocument(uri)) {// MediaProvider
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];

                Uri contentUri = null;
                if ("image".equals(type)) {
                    contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                } else if ("video".equals(type)) {
                    contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                } else if ("audio".equals(type)) {
                    contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                } else if ("document".equals(type)) {
                    contentUri = MediaStore.Files.getContentUri("external");
                }
                final String selection = "_id=?";
                final String[] selectionArgs = new String[]{
                        split[1]
                };

                return getDataColumn(context, contentUri, selection, selectionArgs);
            }
        } else if (uri.toString().startsWith("content://com.lenovo.FileBrowser.FileProvider/root_path")) {
            try {
                return URLDecoder.decode(uri.toString().replace("content://com.lenovo.FileBrowser.FileProvider/root_path", ""), "UTF-8");
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        } else if ("content".equalsIgnoreCase(uri.getScheme())) {// MediaStore (and general)
            // Return the remote address
            if (isGooglePhotosUri(uri)) {
                return uri.getLastPathSegment();
            }

            return getDataColumn(context, uri, null, null);

        } else if ("file".equalsIgnoreCase(uri.getScheme())) {// File
            return uri.getPath();
        } else {
            return uri.toString();
        }

        return null;
    }

    public static void saveFileFromUri(Context context, Uri uri, String destinationPath) {
        InputStream is = null;
        BufferedOutputStream bos = null;
        try {
            is = context.getContentResolver().openInputStream(uri);
            bos = new BufferedOutputStream(new FileOutputStream(destinationPath, false));
            byte[] buf = new byte[1024];
            is.read(buf);
            do {
                bos.write(buf);
            } while (is.read(buf) != -1);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                is.close();
                bos.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static File generateFileName(String name, File directory) {
        File file = new File(directory, name);
        if (file.exists()) {
            String fileName = name;
            String extension = "";
            int dotIndex = name.lastIndexOf('.');
            if (dotIndex > 0) {
                fileName = name.substring(0, dotIndex);
                extension = name.substring(dotIndex);
            }
            int index = 0;
            while (file.exists()) {
                index++;
                name = fileName + "(" + index + ")" + extension;
                file = new File(directory, name);
            }
        }
        try {
            if (!file.createNewFile()) {
                return null;
            }
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        return file;
    }

    public static File getDocumentCacheDir(Context context) {
        File dir = new File(context.getCacheDir(), "documents");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    public static String getFileName(Context context, Uri fileUri) {
        String fileName = null;
        try {
            Cursor returnCursor = context.getContentResolver().query(fileUri, null, null, null, null);
            returnCursor.moveToFirst();
            int nameIndex = returnCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
            fileName = returnCursor.getString(nameIndex);
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
        return fileName;
    }

    public static String getDataColumn(Context context, Uri uri, String selection, String[] selectionArgs) {

        Cursor cursor = null;
        final String column = "_data";
        final String[] projection = {column};

        try {
            cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs, null);
            if (cursor != null && cursor.moveToFirst()) {
                final int index = cursor.getColumnIndexOrThrow(column);
                return cursor.getString(index);
            }
        } finally {
            if (cursor != null)
                cursor.close();
        }
        return null;
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is ExternalStorageProvider.
     */
    public static boolean isExternalStorageDocument(Uri uri) {
        return "com.android.externalstorage.documents".equals(uri.getAuthority());
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is DownloadsProvider.
     */
    public static boolean isDownloadsDocument(Uri uri) {
        return "com.android.providers.downloads.documents".equals(uri.getAuthority());
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is MediaProvider.
     */
    public static boolean isMediaDocument(Uri uri) {
        return "com.android.providers.media.documents".equals(uri.getAuthority());
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is Google Photos.
     */
    public static boolean isGooglePhotosUri(Uri uri) {
        return "com.google.android.apps.photos.content".equals(uri.getAuthority());
    }
}
