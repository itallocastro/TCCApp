package com.example.detectmangodisease.api

import android.graphics.Bitmap
import com.example.detectmangodisease.dto.ResponsePredict
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface IEndpoint {

    @POST("predict")
    suspend fun predictImage(@Body file: RequestBody): Response<ResponsePredict>
}