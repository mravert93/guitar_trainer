package com.ravert.guitar_trainer.cloudinary

import java.security.MessageDigest
import java.util.Base64
import java.util.UUID

data class CloudinaryUploadSignature(
    val cloudName: String,
    val apiKey: String,
    val timestamp: Long,
    val signature: String,
    val publicId: String,
    val uploadUrl: String,
    val deliveryType: String,
)

data class CloudinaryDestroySignature(
    val apiKey: String,
    val timestamp: Long,
    val signature: String,
    val publicId: String,
    val destroyUrl: String,
    val deliveryType: String,
)

class CloudinaryService private constructor(
    private val cloudName: String,
    private val apiKey: String,
    private val apiSecret: String,
    private val folder: String,
) {
    fun createVideoUploadSignature(songUuid: UUID, timestamp: Long = System.currentTimeMillis() / 1000): CloudinaryUploadSignature {
        val publicId = expectedVideoPublicId(songUuid)
        val parameters = sortedMapOf(
            "invalidate" to "true",
            "overwrite" to "true",
            "public_id" to publicId,
            "timestamp" to timestamp.toString(),
            "type" to AuthenticatedDeliveryType,
        )
        val signature = signApiParameters(parameters)

        return CloudinaryUploadSignature(
            cloudName = cloudName,
            apiKey = apiKey,
            timestamp = timestamp,
            signature = signature,
            publicId = publicId,
            uploadUrl = "https://api.cloudinary.com/v1_1/$cloudName/video/upload",
            deliveryType = AuthenticatedDeliveryType,
        )
    }

    fun authenticatedVideoUrl(publicId: String, format: String, version: Long? = null): String {
        val assetPath = listOfNotNull(version?.let { "v$it" }, "$publicId.$format").joinToString("/")
        val signature = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(MessageDigest.getInstance("SHA-1").digest((assetPath + apiSecret).toByteArray()))
            .take(8)
        return "https://res.cloudinary.com/$cloudName/video/$AuthenticatedDeliveryType/s--$signature--/$assetPath"
    }

    fun createVideoDestroySignature(
        publicId: String,
        timestamp: Long = System.currentTimeMillis() / 1000,
    ): CloudinaryDestroySignature {
        val parameters = sortedMapOf(
            "invalidate" to "true",
            "public_id" to publicId,
            "timestamp" to timestamp.toString(),
            "type" to AuthenticatedDeliveryType,
        )
        return CloudinaryDestroySignature(
            apiKey = apiKey,
            timestamp = timestamp,
            signature = signApiParameters(parameters),
            publicId = publicId,
            destroyUrl = "https://api.cloudinary.com/v1_1/$cloudName/video/destroy",
            deliveryType = AuthenticatedDeliveryType,
        )
    }

    fun expectedVideoPublicId(songUuid: UUID): String = "$folder/$VideoFolder/$songUuid"

    private fun signApiParameters(parameters: Map<String, String>): String =
        sha1Hex(parameters.entries.joinToString("&") { (key, value) -> "$key=$value" } + apiSecret)

    companion object {
        private const val VideoFolder = "premium-tabs"
        private const val DefaultFolder = "dct-tutorials"
        private const val AuthenticatedDeliveryType = "authenticated"

        fun fromEnvironment(): CloudinaryService? {
            val cloudName = System.getenv("CLOUDINARY_CLOUD_NAME")?.trim().orEmpty()
            val apiKey = System.getenv("CLOUDINARY_API_KEY")?.trim().orEmpty()
            val apiSecret = System.getenv("CLOUDINARY_API_SECRET")?.trim().orEmpty()
            if (cloudName.isBlank() || apiKey.isBlank() || apiSecret.isBlank()) return null
            val folder = System.getenv("CLOUDINARY_FOLDER")
                ?.trim()
                ?.trim('/')
                ?.takeIf { it.isNotBlank() }
                ?: DefaultFolder

            return fromCredentials(cloudName, apiKey, apiSecret, folder)
        }

        internal fun fromCredentials(
            cloudName: String,
            apiKey: String,
            apiSecret: String,
            folder: String = DefaultFolder,
        ): CloudinaryService {
            require(CloudinaryFolderRegex.matches(folder)) {
                "CLOUDINARY_FOLDER may contain letters, numbers, underscores, hyphens, and slashes"
            }
            return CloudinaryService(cloudName, apiKey, apiSecret, folder)
        }

        private val CloudinaryFolderRegex = Regex("^[A-Za-z0-9_-]+(?:/[A-Za-z0-9_-]+)*$")
    }
}

private fun sha1Hex(value: String): String =
    MessageDigest.getInstance("SHA-1")
        .digest(value.toByteArray())
        .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
