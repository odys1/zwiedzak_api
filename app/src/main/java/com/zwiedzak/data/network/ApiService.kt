package com.zwiedzak.data.network

import com.zwiedzak.data.model.AuthResponse
import com.zwiedzak.data.model.LoginRequest
import com.zwiedzak.data.model.NearbyPlacesResponse
import com.zwiedzak.data.model.Place
import com.zwiedzak.data.model.RegisterRequest
import com.zwiedzak.data.model.AddPlaceImageRequest
import com.zwiedzak.data.model.CreatePlaceRequest
import com.zwiedzak.data.model.SubmitRatingRequest
import com.zwiedzak.data.model.UpdateVisitStatusRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.PUT
import retrofit2.http.Query

interface ApiService {
    @POST("api/v1/auth/register")
    suspend fun register(
        @Body request: RegisterRequest
    ): AuthResponse

    @POST("api/v1/auth/login")
    suspend fun login(
        @Body request: LoginRequest
    ): AuthResponse

    @GET("api/v1/places/nearby")
    suspend fun getNearbyPlaces(
        @Query("lat") latitude: Double,
        @Query("lng") longitude: Double,
        @Query("radiusMeters") radiusMeters: Int = 10000,
        @Query("limit") limit: Int = 50,
        @Query("food") food: Boolean? = null
    ): NearbyPlacesResponse

    @GET("api/v1/places/search")
    suspend fun searchPlaces(
        @Query("name") name: String? = null,
        @Query("address") address: String? = null,
        @Query("visited") visited: Boolean? = null,
        @Query("limit") limit: Int? = null
    ): NearbyPlacesResponse

    @GET("api/v1/places/{id}")
    suspend fun getPlaceDetails(
        @Path("id") id: String
    ): Place

    @POST("api/v1/places")
    suspend fun createPlace(
        @Body request: CreatePlaceRequest
    ): Response<Unit>

    @PUT("api/v1/places/{id}/me")
    suspend fun updateVisitStatus(
        @Path("id") id: String,
        @Body request: UpdateVisitStatusRequest
    ): Response<Unit>

    @POST("api/v1/places/{id}/images")
    suspend fun addPlaceImage(
        @Path("id") id: String,
        @Body request: AddPlaceImageRequest
    ): Response<Unit>

    @POST("api/v1/places/{id}/ratings")
    suspend fun submitRating(
        @Path("id") id: String,
        @Body request: SubmitRatingRequest
    ): Response<Unit>

    @GET("health")
    suspend fun checkHealth(): Map<String, Any>
}
