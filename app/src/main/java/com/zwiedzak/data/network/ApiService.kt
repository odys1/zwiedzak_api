package com.zwiedzak.data.network

import com.zwiedzak.data.model.AuthResponse
import com.zwiedzak.data.model.LoginRequest
import com.zwiedzak.data.model.NearbyPlacesResponse
import com.zwiedzak.data.model.Place
import com.zwiedzak.data.model.PlaceVisitsResponse
import com.zwiedzak.data.model.RegisterRequest
import com.zwiedzak.data.model.RegistrationResponse
import com.zwiedzak.data.model.ResendRegistrationRequest
import com.zwiedzak.data.model.VerifyRegistrationRequest
import com.zwiedzak.data.model.AddPlaceImageRequest
import com.zwiedzak.data.model.CategoriesResponse
import com.zwiedzak.data.model.CreatePlaceRequest
import com.zwiedzak.data.model.SubmitRatingRequest
import com.zwiedzak.data.model.UpdateVisitStatusRequest
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.PUT
import retrofit2.http.PATCH
import retrofit2.http.Query

interface ApiService {
    @GET("api/v1/categories")
    suspend fun getCategories(): CategoriesResponse

    @POST("api/v1/auth/register")
    suspend fun register(
        @Body request: RegisterRequest
    ): RegistrationResponse

    @POST("api/v1/auth/register/verify")
    suspend fun verifyRegistration(
        @Body request: VerifyRegistrationRequest
    ): AuthResponse

    @POST("api/v1/auth/register/resend")
    suspend fun resendRegistration(
        @Body request: ResendRegistrationRequest
    )

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
        @Query("food") food: Boolean? = null,
        @Query("mine") mine: Boolean? = null
    ): NearbyPlacesResponse

    @GET("api/v1/places/search")
    suspend fun searchPlaces(
        @Query("name") name: String? = null,
        @Query("address") address: String? = null,
        @Query("visited") visited: Boolean? = null,
        @Query("limit") limit: Int? = null,
        @Query("mine") mine: Boolean? = null
    ): NearbyPlacesResponse

    @GET("api/v1/places/{id}")
    suspend fun getPlaceDetails(
        @Path("id") id: String
    ): Place

    @POST("api/v1/places")
    suspend fun createPlace(
        @Body request: CreatePlaceRequest
    ): Response<ResponseBody>

    @PATCH("api/v1/places/{id}")
    suspend fun editPlace(
        @Path("id") id: String,
        @Body body: RequestBody
    ): Response<ResponseBody>

    @PUT("api/v1/places/{id}/me")
    suspend fun updateVisitStatus(
        @Path("id") id: String,
        @Body request: UpdateVisitStatusRequest
    ): Response<Unit>

    @GET("api/v1/places/{id}/visits")
    suspend fun getPlaceVisits(
        @Path("id") id: String,
        @Query("limit") limit: Int = 5,
        @Query("offset") offset: Int = 0
    ): PlaceVisitsResponse

    @POST("api/v1/places/{id}/images")
    suspend fun addPlaceImage(
        @Path("id") id: String,
        @Body request: AddPlaceImageRequest
    ): Response<Unit>

    @Multipart
    @POST("api/v1/places/{id}/images")
    suspend fun uploadPlaceImage(
        @Path("id") id: String,
        @Part image: MultipartBody.Part,
        @Part("caption") caption: RequestBody?
    ): Response<Unit>

    @POST("api/v1/places/{id}/ratings")
    suspend fun submitRating(
        @Path("id") id: String,
        @Body request: SubmitRatingRequest
    ): Response<Unit>

    @GET("health")
    suspend fun checkHealth(): Map<String, Any>
}
