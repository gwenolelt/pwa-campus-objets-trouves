package corsica.universita.objetscampus.data.remote

import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

/**
 * DTO reseau correspondant exactement au backend Netlify Functions de la PWA
 * (`TP/netlify/functions/items.mjs` -> `/api/items`).
 */
@Serializable
data class RemoteItemDto(
    val id: String = "",
    val title: String = "",
    val category: String = "Autre",
    val description: String = "",
    val dateFound: String = "",
    val locationFound: String = "",
    val depositLocation: String = "Accueil — Bâtiment A",
    val photo: String? = null,
    val status: String = "active",
    val dateReturned: String? = null,
    val version: Int = 1,
    val synced: Boolean = true,
    val createdAt: Long = 0L
)

interface SyncApi {
    @GET("items")
    suspend fun fetchAll(): List<RemoteItemDto>

    @POST("items")
    suspend fun pushNew(@Body item: RemoteItemDto): RemoteItemDto

    @PUT("items/{id}")
    suspend fun updateItem(
        @Path("id") id: String,
        @Header("If-Match") ifMatchVersion: String,
        @Body item: RemoteItemDto
    ): Response<RemoteItemDto>
}
