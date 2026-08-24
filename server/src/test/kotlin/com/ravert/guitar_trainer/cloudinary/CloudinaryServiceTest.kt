package com.ravert.guitar_trainer.cloudinary

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class CloudinaryServiceTest {
    private val songUuid = UUID.fromString("00000000-0000-0000-0000-000000000000")
    private val service = CloudinaryService.fromCredentials(
        cloudName = "demo",
        apiKey = "key",
        apiSecret = "secret",
    )

    @Test
    fun `signed upload contains only browser-safe credentials`() {
        val upload = service.createVideoUploadSignature(songUuid, timestamp = 1_700_000_000L)

        assertEquals("key", upload.apiKey)
        assertEquals("dct-tutorials/premium-tabs/$songUuid", upload.publicId)
        assertEquals("authenticated", upload.deliveryType)
        assertEquals(true, upload.overwrite)
        assertEquals(true, upload.invalidate)
        assertEquals("d3897785802e7107acca6c9648a618f48169b202", upload.signature)
        assertEquals("https://api.cloudinary.com/v1_1/demo/video/upload", upload.uploadUrl)
    }

    @Test
    fun `authenticated delivery URL includes version and signature`() {
        val url = service.authenticatedVideoUrl(
            publicId = "dct-tutorials/premium-tabs/$songUuid",
            format = "mp4",
            version = 123,
        )

        assertEquals(
            "https://res.cloudinary.com/demo/video/authenticated/s--qW1nQ_CS--/v123/dct-tutorials/premium-tabs/$songUuid.mp4",
            url,
        )
    }
}
