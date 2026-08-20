package com.workbuddy.notes

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Environment
import android.text.TextUtils
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** 分享与导出：分享成图、导出 PDF / DOCX。 */
object ShareUtil {

    /** 把单条便签渲染成图片并分享 */
    fun shareNoteImage(context: Context, note: Note) {
        try {
            val bmp = renderNote(note)
            val file = File(context.cacheDir, "share_${note.id}.png")
            FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bmp.recycle()
            val uri = FileProvider.getUriForFile(context, "com.workbuddy.notes.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "分享便签"))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /** 渲染便签到 Bitmap（薄荷底 + 文字 + 标签 + 日期/农历）。 */
    private fun renderNote(note: Note): Bitmap {
        val w = 800
        val padding = 48
        val paint = Paint().apply {
            color = Color.parseColor("#212121")
            textSize = 34f
            isAntiAlias = true
        }
        val display = if (note.locked) "🔒 加密便签" else note.text.takeIf { it.isNotBlank() }
            ?: (if (note.hasAnyImage()) "[图片 / 涂鸦]" else if (note.audioPath != null) "[语音]" else "（空便签）")
        val lines = wrap(display, paint, w - 2 * padding)
        val lineH = (paint.textSize * 1.45).toInt()

        val tags = note.tagList()
        val dateLine = buildDateLine(note)
        val footerLines = (if (tags.isNotEmpty()) 1 else 0) + if (dateLine != null) 1 else 0
        val h = padding * 2 + maxOf(lineH * lines.size, 60) + footerLines * 40 + 40

        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.parseColor("#D9F0E5"))
        var y = padding + paint.textSize.toInt()
        lines.forEach { line ->
            c.drawText(line, padding.toFloat(), y.toFloat(), paint)
            y += lineH
        }
        val small = Paint().apply {
            color = Color.parseColor("#1F3D2E")
            textSize = 26f
            isAntiAlias = true
        }
        var y2 = y + 20
        if (tags.isNotEmpty()) {
            c.drawText(tags.joinToString(" ") { "#$it" }, padding.toFloat(), y2.toFloat(), small)
            y2 += 40
        }
        if (dateLine != null) {
            c.drawText(dateLine, padding.toFloat(), y2.toFloat(), small)
        }
        return bmp
    }

    private fun buildDateLine(note: Note): String? {
        if (note.eventDate == null) return null
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = note.eventDate!! }
        val lunar = try { " · ${Lunar.lunarMonthDay(cal)}" } catch (_: Exception) { "" }
        return "📅${note.eventLabel ?: ""}${Ui.countdownText(note.eventDate!!)}$lunar"
    }

    /** 按宽度折行（兼容中英文） */
    private fun wrap(text: String, paint: Paint, maxWidth: Int): List<String> {
        val result = mutableListOf<String>()
        text.split("\n").forEach { para ->
            if (para.isEmpty()) { result.add(""); return@forEach }
            val sb = StringBuilder()
            var lineW = 0f
            para.forEach { ch ->
                val cw = paint.measureText(ch.toString())
                if (lineW + cw > maxWidth && sb.isNotEmpty()) {
                    result.add(sb.toString())
                    sb.clear()
                    lineW = 0f
                }
                sb.append(ch)
                lineW += cw
            }
            result.add(sb.toString())
        }
        return result
    }
}

/** 导出工具：批量导出全部（未删除）便签到 PDF / DOCX。 */
object Export {

    fun exportPdf(context: Context, notes: List<Note>): File {
        val doc = PdfDocument()
        val pageW = 595
        val pageH = 842
        val margin = 40f
        val body = Paint().apply { textSize = 11f; color = Color.BLACK }
        val head = Paint().apply { textSize = 13f; color = Color.BLACK; typeface = Typeface.DEFAULT_BOLD }

        var pageNum = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNum).create())
        var canvas = page.canvas
        var y = margin

        fun newPage() {
            doc.finishPage(page)
            pageNum++
            page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNum).create())
            canvas = page.canvas
            y = margin
        }

        notes.forEach { note ->
            val title = headerOf(note)
            if (y + 30 > pageH - margin) newPage()
            canvas.drawText(title, margin, y, head)
            y += 20f
            val content = if (note.locked) "🔒 加密便签" else note.text.takeIf { it.isNotBlank() }
                ?: (if (note.hasAnyImage()) "[图片 / 涂鸦]" else if (note.audioPath != null) "[语音]" else "（空便签）")
            wrapPdf(content, body, (pageW - 2 * margin).toInt()).forEach { line ->
                if (y + 16 > pageH - margin) newPage()
                canvas.drawText(line, margin, y, body)
                y += 15f
            }
            val tags = note.tagList()
            val date = buildDateLineSafe(note)
            if (tags.isNotEmpty()) {
                if (y + 16 > pageH - margin) newPage()
                canvas.drawText("标签：${tags.joinToString(" ", "#")}", margin, y, body)
                y += 15f
            }
            if (date != null) {
                if (y + 16 > pageH - margin) newPage()
                canvas.drawText(date, margin, y, body)
                y += 15f
            }
            y += 12f // 便签间距
        }
        doc.finishPage(page)

        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            ?: context.filesDir
        val file = File(dir, "札记导出_${System.currentTimeMillis()}.pdf")
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
        return file
    }

    fun exportDocx(context: Context, notes: List<Note>): File {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            ?: context.filesDir
        val file = File(dir, "札记导出_${System.currentTimeMillis()}.docx")

        val body = StringBuilder()
        body.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
        body.append("<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body>")
        notes.forEach { note ->
            body.append(paragraph(headerOf(note), bold = true))
            val content = if (note.locked) "🔒 加密便签" else note.text.takeIf { it.isNotBlank() }
                ?: (if (note.hasAnyImage()) "[图片 / 涂鸦]" else if (note.audioPath != null) "[语音]" else "（空便签）")
            content.split("\n").forEach { body.append(paragraph(escapeXml(it))) }
            val tags = note.tagList()
            if (tags.isNotEmpty()) body.append(paragraph("标签：" + tags.joinToString(" ", "#")))
            buildDateLineSafe(note)?.let { body.append(paragraph(escapeXml(it))) }
            body.append("<w:p><w:pPr><w:spacing w:after=\"200\"/></w:pPr></w:p>")
        }
        body.append("</w:body></w:document>")

        val contentTypes = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
            "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
            "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
            "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
            "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>" +
            "</Types>"
        val rels = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
            "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
            "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/>" +
            "</Relationships>"

        ZipOutputStream(FileOutputStream(file)).use { zos ->
            fun add(name: String, data: String) {
                zos.putNextEntry(ZipEntry(name))
                zos.write(data.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
            add("[Content_Types].xml", contentTypes)
            add("_rels/.rels", rels)
            add("word/document.xml", body.toString())
        }
        return file
    }

    fun shareFile(context: Context, file: File, mime: String) {
        try {
            val uri = FileProvider.getUriForFile(context, "com.workbuddy.notes.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "导出便签"))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun headerOf(note: Note): String = when (note.module) {
        Module.QUAD -> "四象限 · ${Note.QUAD_ZONES[note.quadZone - 1]}"
        Module.IDEA -> "点子存放处"
        Module.UNDECIDED -> "未想清楚的事"
    }

    private fun buildDateLineSafe(note: Note): String? {
        if (note.eventDate == null) return null
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = note.eventDate!! }
        val lunar = try { " · ${Lunar.lunarMonthDay(cal)}" } catch (_: Exception) { "" }
        return "📅${note.eventLabel ?: ""}${Ui.countdownText(note.eventDate!!)}$lunar"
    }

    private fun paragraph(text: String, bold: Boolean = false): String {
        val rpr = if (bold) "<w:rPr><w:b/></w:rPr>" else ""
        return "<w:p><w:r>$rpr<w:t xml:space=\"preserve\">${escapeXml(text)}</w:t></w:r></w:p>"
    }

    private fun escapeXml(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    private fun wrapPdf(text: String, paint: Paint, maxWidth: Int): List<String> {
        val result = mutableListOf<String>()
        text.split("\n").forEach { para ->
            if (para.isEmpty()) { result.add(""); return@forEach }
            val sb = StringBuilder()
            var lineW = 0f
            para.forEach { ch ->
                val cw = paint.measureText(ch.toString())
                if (lineW + cw > maxWidth && sb.isNotEmpty()) {
                    result.add(sb.toString())
                    sb.clear()
                    lineW = 0f
                }
                sb.append(ch)
                lineW += cw
            }
            result.add(sb.toString())
        }
        return result
    }
}
