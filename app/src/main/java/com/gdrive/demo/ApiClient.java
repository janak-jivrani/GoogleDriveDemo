package com.gdrive.demo;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ApiClient {
    private static final String BASE_URL="https://be12.platform.simplifii.com/api/v1/s3/";
    static ApiService apiService;

    public static ApiService getApiClientService() {
        if (apiService == null) {
            OkHttpClient okHttpClient = new OkHttpClient().newBuilder().connectTimeout(1, TimeUnit.MINUTES).readTimeout(1, TimeUnit.MINUTES).writeTimeout(1, TimeUnit.MINUTES)
                    .addInterceptor(new HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY)).build();
            Retrofit retrofit = new Retrofit.Builder().baseUrl(BASE_URL).client(okHttpClient).addConverterFactory(GsonConverterFactory.create()).build();
            apiService = retrofit.create(ApiService.class);
        }
        return apiService;
    }
}
