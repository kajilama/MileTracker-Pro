package com.example.data.util

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

object ReceiptStorageManager {

    fun saveReceiptImage(context: Context, sourceUri: Uri): String? {
        return try {
            val receiptsDir = File(context.filesDir, "receipts")
            if (!receiptsDir.exists()) {
                receiptsDir.mkdirs()
            }

            val fileName = "receipt_${UUID.randomUUID()}.jpg"
            val destFile = File(receiptsDir, fileName)

            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            destFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
