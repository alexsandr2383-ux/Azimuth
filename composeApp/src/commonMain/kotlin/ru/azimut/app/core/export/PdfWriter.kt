package ru.azimut.app.core.export

/** DEFLATE for PDF streams; both targets are JVM (java.util.zip). */
expect object Deflate {
    fun compress(bytes: ByteArray): ByteArray
}

/**
 * Minimal single-page PDF writer (no external library): one page with the sheet
 * image (FlateDecode RGB) and an ASCII footer. Cyrillic in the text layer is not
 * possible without font embedding, so all human text lives in the image; the PDF
 * text layer carries only ASCII-safe numbers and labels (non-ASCII is dropped
 * honestly rather than replaced with junk glyphs).
 */
class PdfWriter(
    private val pageWidthPt: Float = 842f, // A4 landscape
    private val pageHeightPt: Float = 595f,
) {
    fun build(
        rgb: IntArray,
        width: Int,
        height: Int,
        footerLines: List<String>,
    ): ByteArray {
        val raw = ByteArray(width * height * 3)
        var p = 0
        for (pixel in rgb) {
            raw[p++] = ((pixel shr 16) and 0xFF).toByte()
            raw[p++] = ((pixel shr 8) and 0xFF).toByte()
            raw[p++] = (pixel and 0xFF).toByte()
        }
        val imageBytes = Deflate.compress(raw)
        val asciiLines = footerLines.map { it.filter { ch -> ch.code in 32..126 } }

        // Objects 1..6: catalog, pages, page, image, content stream, font.
        val objects = mutableListOf<ByteArray>()
        fun obj(text: String): ByteArray = text.toByteArray(Charsets.ISO_8859_1)

        objects.add(obj("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n"))
        objects.add(obj("2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n"))
        objects.add(
            obj(
                "3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 $pageWidthPt $pageHeightPt] " +
                    "/Resources << /XObject << /Im1 4 0 R >> /Font << /F1 6 0 R >> >> " +
                    "/Contents 5 0 R >>\nendobj\n",
            ),
        )
        objects.add(
            obj(
                "4 0 obj\n<< /Type /XObject /Subtype /Image /Width $width /Height $height " +
                    "/ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /FlateDecode " +
                    "/Length ${imageBytes.size} >>\nstream\n",
            ) + imageBytes + obj("\nendstream\nendobj\n"),
        )
        val imageHeight = pageHeightPt - 70f
        val content = buildString {
            append("q $pageWidthPt 0 0 $imageHeight 0 ${(pageHeightPt - imageHeight).toInt()} cm /Im1 Do Q\n")
            append("BT /F1 9 Tf 24 20 Td\n")
            asciiLines.forEachIndexed { i, line ->
                if (i > 0) append(" 0 -12 Td\n")
                append("(${escapePdf(line)}) Tj\n")
            }
            append("ET\n")
        }
        objects.add(obj("5 0 obj\n<< /Length ${content.length} >>\nstream\n$content\nendstream\nendobj\n"))
        objects.add(obj("6 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Courier >>\nendobj\n"))

        val out = java.io.ByteArrayOutputStream()
        out.write("%PDF-1.4\n%\u00E2\u00E3\u00CF\u00D3\n".toByteArray(Charsets.ISO_8859_1))
        val offsets = mutableListOf<Int>()
        for (body in objects) {
            offsets.add(out.size())
            out.write(body)
        }
        val xrefStart = out.size()
        val xref = buildString {
            append("xref\n0 ${objects.size + 1}\n")
            append("0000000000 65535 f \n")
            offsets.forEach { append("%010d 00000 n \n".format(it)) }
            append("trailer\n<< /Size ${objects.size + 1} /Root 1 0 R >>\nstartxref\n$xrefStart\n%%EOF\n")
        }
        out.write(xref.toByteArray(Charsets.ISO_8859_1))
        return out.toByteArray()
    }

    private fun escapePdf(s: String): String = s
        .replace("\\", "\\\\")
        .replace("(", "\\(")
        .replace(")", "\\)")
}
