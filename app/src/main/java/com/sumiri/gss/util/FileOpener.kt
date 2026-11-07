package com.sumiri.gss.util

import android.content.Context
import android.content.Intent
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

object FileOpener {
    fun openFile(context: Context, file: File) {
        runCatching {
            val extension = file.extension.lowercase()
            var mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)

            // 如果无法从后缀解析出具体 MIME 类型（如 .sav, .dat 等），使用 */* 开放给所有能打开文件的应用
            if (mimeType.isNull_or_blank() || mimeType == "application/octet-stream") {
                mimeType = "*/*"
            }

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(intent, "选择打开方式")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        }.onFailure { err ->
            Toast.makeText(context, "无法打开此文件: ${err.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun String?.isNull_or_blank(): Boolean = this == null || this.trim().isEmpty()
}
