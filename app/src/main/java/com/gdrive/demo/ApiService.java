package com.gdrive.demo;

import com.google.gson.JsonObject;

import java.util.Map;

import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.http.Header;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;
import retrofit2.http.PartMap;

public interface ApiService {

    @Multipart
    @POST("uploadimages")
    Call<JsonObject> uploadFile(@Header("Authorization") String authorization,@PartMap Map<String, RequestBody> requestBodyMap, @Part MultipartBody.Part file);

}
