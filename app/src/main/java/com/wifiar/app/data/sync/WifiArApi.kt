package com.wifiar.app.data.sync

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

interface WifiArApi {

    @POST("auth/register")
    suspend fun register(@Body body: RegisterRequest): UserOut

    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): TokenResponse

    @GET("auth/me")
    suspend fun me(@Header("Authorization") authorization: String): UserOut

    @POST("sessions")
    suspend fun createSession(
        @Header("Authorization") authorization: String,
        @Body body: SessionCreateRequest,
    ): SessionSummaryOut

    @POST("sessions/{id}/points")
    suspend fun uploadPoints(
        @Header("Authorization") authorization: String,
        @Path("id") sessionId: String,
        @Body body: BulkRssiUpload,
    ): BulkUploadResult

    @POST("sessions/{id}/speedtests")
    suspend fun uploadSpeedTests(
        @Header("Authorization") authorization: String,
        @Path("id") sessionId: String,
        @Body body: BulkSpeedTestUpload,
    ): BulkUploadResult

    @GET("sessions")
    suspend fun listSessions(
        @Header("Authorization") authorization: String,
    ): List<SessionSummaryOut>

    @GET("sessions/{id}/heatmap")
    suspend fun getHeatmap(
        @Header("Authorization") authorization: String,
        @Path("id") sessionId: String,
    ): HeatmapGridOut

    @DELETE("sessions/{id}")
    suspend fun deleteSession(
        @Header("Authorization") authorization: String,
        @Path("id") sessionId: String,
    )
}
