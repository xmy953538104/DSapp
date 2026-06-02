package dev.chungjungsoo.gptmobile.data.database

import dev.chungjungsoo.gptmobile.data.database.entity.ChatAttachmentListConverter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatAttachmentListConverterTest {
    @Test
    fun `unknown provider refs are filtered without dropping attachment`() {
        val json = """
            [
              {
                "localFilePath": "/tmp/a.png",
                "preparedFilePath": "/tmp/a.png",
                "displayName": "a.png",
                "mimeType": "image/png",
                "sizeBytes": 1,
                "providerRefs": [
                  {
                    "platformUid": "legacy-platform",
                    "remoteType": "REMOVED_PROVIDER_FILE",
                    "remoteId": "legacy-file",
                    "mimeType": "image/png",
                    "uploadedAt": 1
                  }
                ]
              }
            ]
        """.trimIndent()

        val attachments = ChatAttachmentListConverter().fromString(json)

        assertEquals(1, attachments.size)
        assertEquals("a.png", attachments.single().displayName)
        assertTrue(attachments.single().providerRefs.isEmpty())
    }
}
