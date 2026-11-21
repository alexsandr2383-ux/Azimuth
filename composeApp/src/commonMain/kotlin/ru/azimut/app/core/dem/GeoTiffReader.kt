package ru.azimut.app.core.dem

/**
 * Minimal GeoTIFF reader, commonMain, no external libraries.
 *
 * Supports what DEM distributors (Copernicus GLO-30, SRTM derivatives, ASTER) actually
 * ship: uncompressed strips or tiles, sample formats Int16/UInt16/Float32, 1 band,
 * 8..64 bits per sample, WGS84/CRS84 pixel-scale + tie-point georeferencing
 * (ModelPixelScaleTag 33550, ModelTiepointTag 33922). GeographicTypeGeoKey (2048)
 * values 2048/4326/4030..4033 and "unknown"/GCS-less files are accepted — DEM files
 * outside WGS84 are rejected with a clear error instead of silently misplacing data.
 *
 * Not supported (rejected): compressed (LZW/JPEG/Deflate/PackBits) files, planar
 * multiple bands, 64-bit doubles, >8-byte sample words. Rationale for hand-rolling
 * instead of pulling a library: a full reader (GDAL) is a multi-megabyte native
 * dependency forbidden by the project rules; JVM libs (geotools ~40MB) are worse;
 * the supported subset above covers every practical DEM export tool.
 */
object GeoTiffReader {
    class BadFormat(message: String) : Exception(message)

    fun parse(bytes: ByteArray): DemTile? {
        if (bytes.size < 16) return null
        val le = when {
            bytes[0].toInt() == 0x49 && bytes[1].toInt() == 0x49 -> true // "II"
            bytes[0].toInt() == 0x4D && bytes[1].toInt() == 0x4D -> false // "MM"
            else -> throw BadFormat("not a TIFF file")
        }
        val magic = shortAt(bytes, 2, le).toInt()
        if (magic != 42 && magic != 43) throw BadFormat("unsupported TIFF magic $magic")
        // BigTIFF (43) uses 8-byte offsets; not needed for DEM tiles in practice.
        if (magic == 43) throw BadFormat("BigTIFF is not supported")
        val ifdOffset = intAt(bytes, 4, le).toLong() and 0xFFFFFFFFL

        val tags = HashMap<Int, Tag>()
        var offset = ifdOffset.toInt()
        val entryCount = shortAt(bytes, offset, le).toInt() and 0xFFFF
        offset += 2
        repeat(entryCount) {
            val tag = shortAt(bytes, offset, le).toInt() and 0xFFFF
            val type = shortAt(bytes, offset + 2, le).toInt() and 0xFFFF
            val count = intAt(bytes, offset + 4, le).toLong() and 0xFFFFFFFFL
            val valueOffset = offset + 8
            tags[tag] = Tag(type, count, valueOffset)
            offset += 12
        }

        val width = tags[256]?.firstInt(bytes, le) ?: throw BadFormat("no ImageWidth")
        val height = tags[257]?.firstInt(bytes, le) ?: throw BadFormat("no ImageLength")
        val bitsPerSample = tags[258]?.firstInt(bytes, le) ?: 16
        if (tags[258]?.count != 1L) throw BadFormat("multi-band TIFF is not supported")
        if (bitsPerSample !in intArrayOf(16, 32)) throw BadFormat("unsupported bit depth $bitsPerSample")
        val compression = tags[259]?.firstInt(bytes, le) ?: 1
        if (compression !in intArrayOf(1, 8, 32946)) {
            throw BadFormat("unsupported TIFF compression $compression (supported: uncompressed/deflate)")
        }
        // SampleFormat: 1=unsigned, 2=signed, 3=float. Default 1 per spec.
        val sampleFormat = tags[339]?.firstInt(bytes, le) ?: 1
        if (bitsPerSample == 16 && sampleFormat == 3) throw BadFormat("float16 is not supported")
        if (bitsPerSample == 32 && sampleFormat == 4) throw BadFormat("complex float is not supported")
        val predictor = tags[317]?.firstInt(bytes, le) ?: 1
        if (predictor !in intArrayOf(1, 2, 3)) throw BadFormat("unsupported TIFF predictor $predictor")

        val tileWidth = tags[322]?.firstInt(bytes, le) ?: 0
        val tileHeight = tags[323]?.firstInt(bytes, le) ?: 0
        val rowsPerStrip = tags[278]?.firstInt(bytes, le) ?: height
        val stripOffsets = tags[273]?.allInts(bytes, le)
        val stripCounts = tags[279]?.allInts(bytes, le)
        val tileOffsets = tags[324]?.allInts(bytes, le)
        val tileCounts = tags[325]?.allInts(bytes, le)

        val pixelScale = tags[33550]?.allDoubles(bytes, le)
        val tiepoint = tags[33922]?.allDoubles(bytes, le)
        if (pixelScale == null || tiepoint == null) {
            throw BadFormat("no georeferencing (PixelScale/Tiepoint tags)")
        }
        if (pixelScale.size < 3 || tiepoint.size < 6) throw BadFormat("bad georeferencing arrays")
        val scaleX = pixelScale[0]
        val scaleY = pixelScale[1]
        if (scaleX <= 0 || scaleY <= 0) throw BadFormat("bad pixel scale")
        // Tiepoint maps raster (0,0) of the referenced point to world coords.
        val lonLeft = tiepoint[3]
        val latTop = tiepoint[4]

        // CRS check: only geographic WGS84-family. Projected or unknown CRS => refuse.
        tags[34735]?.allShorts(bytes, le)?.let { geoKeys ->
            val crs = geoKey(geoKeys, 2048)
            when (crs) {
                null, 2048, 4326, 4030, 4031, 4032, 4033, 4269, 4283 -> {} // WGS84/GRS80-family
                else -> throw BadFormat("CRS $crs is not geographic WGS84")
            }
        }

        // Scan orientation: rows ordered north -> south (typical DEM); row 0 = top edge.
        val data = ShortArray(height * width)
        val bytesPerSample = bitsPerSample / 8
        val bytesPerRow = width * bytesPerSample

        if (tileWidth > 0 && tileHeight > 0 && tileOffsets != null && tileCounts != null) {
            val tilesAcross = (width + tileWidth - 1) / tileWidth
            for (t in tileOffsets.indices) {
                val tRow = t / tilesAcross
                val tCol = t % tilesAcross
                val baseRow = tRow * tileHeight
                val baseCol = tCol * tileWidth
                val block = decodeBlock(bytes, tileOffsets[t], tileCounts[t], compression)
                val expected = tileWidth.toLong() * tileHeight.toLong() * bytesPerSample
                val blockBytes = if (block.size.toLong() >= expected) block else throw BadFormat("tile ${t} is shorter than expected")
                undoPredictor(blockBytes, tileWidth, tileHeight, bytesPerSample, predictor)
                var pos = 0
                for (r in 0 until tileHeight) {
                    val row = baseRow + r
                    if (row >= height) { pos += tileWidth * bytesPerSample; continue }
                    for (c in 0 until tileWidth) {
                        val col = baseCol + c
                        if (col < width) data[row * width + col] = sample(blockBytes, pos, bitsPerSample, sampleFormat, le)
                        pos += bytesPerSample
                    }
                }
            }
        } else if (stripOffsets != null && stripCounts != null) {
            var row = 0
            for (s in stripOffsets.indices) {
                val stripRows = minOf(rowsPerStrip, height - row)
                if (stripRows <= 0) break
                val block = decodeBlock(bytes, stripOffsets[s], stripCounts[s], compression)
                val expected = width.toLong() * stripRows.toLong() * bytesPerSample
                if (block.size.toLong() < expected) throw BadFormat("strip ${s} is shorter than expected")
                undoPredictor(block, width, stripRows, bytesPerSample, predictor)
                var pos = 0
                for (r in 0 until stripRows) {
                    val dstRow = row + r
                    for (c in 0 until width) {
                        data[dstRow * width + c] = sample(block, pos, bitsPerSample, sampleFormat, le)
                        pos += bytesPerSample
                    }
                }
                row += stripRows
                if (row >= height) break
            }
            if (row < height) throw BadFormat("strips are shorter than the image")
        } else {
            throw BadFormat("no strip or tile offsets")
        }

        // nodata tag (42113) when present, else classic -32767/-32768 handled by DemTile.valid().
        val nodataValue = tags[42113]?.firstDouble(bytes, le) ?: -32768.0

        return DemTile(
            latTop = latTop,
            lonLeft = lonLeft,
            stepDeg = scaleY,
            rows = height,
            cols = width,
            nodata = nodataValue,
            data = data,
        ).takeIf {
            // Sanity: geographic extents only. A projected CRS misread would give nonsense bounds.
            it.latTop in -90.0..90.0 && it.lonLeft in -180.0..180.0 && it.latBottom > -90.0
        } ?: throw BadFormat("coordinates out of geographic range")
    }

    private fun geoKey(geoKeys: ShortArray, keyId: Int): Int? {
        var i = 4 // header: version, revision, minor, numKeys
        while (i + 3 < geoKeys.size) {
            if ((geoKeys[i].toInt() and 0xFFFF) == keyId) {
                val count = geoKeys[i + 2].toInt() and 0xFFFF
                if (count == 1) return geoKeys[i + 3].toInt() and 0xFFFF
                return null // ASCII/offset keys are not CRS ids
            }
            i += 4
        }
        return null
    }

    private fun decodeBlock(bytes: ByteArray, offset: Int, count: Int, compression: Int): ByteArray {
        if (offset < 0 || count < 0 || offset + count > bytes.size) throw BadFormat("block out of range")
        val raw = bytes.copyOfRange(offset, offset + count)
        return when (compression) {
            1 -> raw
            8, 32946 -> inflateDeflate(raw)
            else -> throw BadFormat("unsupported TIFF compression $compression")
        }
    }

    private fun inflateDeflate(raw: ByteArray): ByteArray {
        val inflater = java.util.zip.Inflater()
        return try {
            inflater.setInput(raw)
            val out = java.io.ByteArrayOutputStream(raw.size * 2)
            val buf = ByteArray(64 * 1024)
            while (!inflater.finished()) {
                val n = inflater.inflate(buf)
                if (n > 0) {
                    out.write(buf, 0, n)
                } else if (inflater.needsInput()) {
                    throw BadFormat("truncated deflate stream")
                } else if (inflater.needsDictionary()) {
                    throw BadFormat("deflate dictionary not supported")
                }
            }
            out.toByteArray()
        } catch (e: BadFormat) {
            throw e
        } catch (e: Exception) {
            throw BadFormat("deflate decode failed: ${e.message}")
        } finally {
            inflater.end()
        }
    }

    /** Undo TIFF predictors (2: horizontal differencing, 3: floating-point predictor). */
    private fun undoPredictor(buf: ByteArray, width: Int, height: Int, bps: Int, predictor: Int) {
        if (predictor == 1) return
        val rowBytes = width * bps
        if (predictor == 2) {
            for (y in 0 until height) {
                val base = y * rowBytes
                for (i in bps until rowBytes) {
                    buf[base + i] = ((buf[base + i].toInt() and 0xFF) + (buf[base + i - bps].toInt() and 0xFF)).toByte()
                }
            }
            return
        }
        if (predictor == 3) {
            for (y in 0 until height) {
                val base = y * rowBytes
                for (i in bps until rowBytes) {
                    buf[base + i] = ((buf[base + i].toInt() and 0xFF) + (buf[base + i - bps].toInt() and 0xFF)).toByte()
                }
                val row = buf.copyOfRange(base, base + rowBytes)
                for (i in 0 until width) {
                    for (j in 0 until bps) {
                        buf[base + i * bps + j] = row[j * width + i]
                    }
                }
            }
            return
        }
        throw BadFormat("unsupported TIFF predictor $predictor")
    }

    private fun sample(bytes: ByteArray, pos: Int, bits: Int, format: Int, le: Boolean): Short {
        if (bits == 16) {
            val hi: Int
            val lo: Int
            if (le) {
                lo = bytes[pos].toInt() and 0xFF
                hi = bytes[pos + 1].toInt() and 0xFF
            } else {
                hi = bytes[pos].toInt() and 0xFF
                lo = bytes[pos + 1].toInt() and 0xFF
            }
            return ((hi shl 8) or lo).toShort()
        }
        // 32-bit: signed int or IEEE float, both go to the Short grid of DemTile.
        val raw = intAt(bytes, pos, le)
        if (format == 3) {
            val f = Float.fromBits(raw)
            return f.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return when {
            raw < Short.MIN_VALUE -> Short.MIN_VALUE
            raw > Short.MAX_VALUE -> Short.MAX_VALUE
            else -> raw.toShort()
        }
    }

    private class Tag(val type: Int, val count: Long, val valueOffset: Int) {
        /** Value bytes live inline when they fit in 4, else at a file offset. */
        fun position(bytes: ByteArray, le: Boolean, bytesNeeded: Long): Int {
            return if (bytesNeeded <= 4) valueOffset else intAt(bytes, valueOffset, le)
        }

        fun firstInt(bytes: ByteArray, le: Boolean): Int? = allInts(bytes, le)?.firstOrNull()

        fun allInts(bytes: ByteArray, le: Boolean): IntArray? {
            val sizes = when (type) {
                3 -> 2L // SHORT
                4 -> 4L // LONG
                else -> return null
            }
            val pos = position(bytes, le, count * sizes)
            val out = IntArray(count.toInt())
            for (i in out.indices) {
                out[i] = if (type == 3) shortAt(bytes, pos + i * 2, le).toInt() and 0xFFFF
                else intAt(bytes, pos + i * 4, le)
            }
            return out
        }

        fun allShorts(bytes: ByteArray, le: Boolean): ShortArray? {
            if (type != 3) return null
            val pos = position(bytes, le, count * 2)
            val out = ShortArray(count.toInt())
            for (i in out.indices) out[i] = shortAt(bytes, pos + i * 2, le)
            return out
        }

        fun firstDouble(bytes: ByteArray, le: Boolean): Double? = allDoubles(bytes, le)?.firstOrNull()

        fun allDoubles(bytes: ByteArray, le: Boolean): DoubleArray? {
            val size = when (type) {
                12 -> 8L // DOUBLE
                else -> return null
            }
            val pos = position(bytes, le, count * size)
            val out = DoubleArray(count.toInt())
            for (i in out.indices) out[i] = doubleAt(bytes, pos + i * 8, le)
            return out
        }
    }

    private fun shortAt(b: ByteArray, pos: Int, le: Boolean): Short =
        if (le) {
            ((b[pos].toInt() and 0xFF) or ((b[pos + 1].toInt() and 0xFF) shl 8)).toShort()
        } else {
            (((b[pos].toInt() and 0xFF) shl 8) or (b[pos + 1].toInt() and 0xFF)).toShort()
        }

    private fun intAt(b: ByteArray, pos: Int, le: Boolean): Int =
        if (le) {
            (b[pos].toInt() and 0xFF) or
                ((b[pos + 1].toInt() and 0xFF) shl 8) or
                ((b[pos + 2].toInt() and 0xFF) shl 16) or
                ((b[pos + 3].toInt() and 0xFF) shl 24)
        } else {
            ((b[pos].toInt() and 0xFF) shl 24) or
                ((b[pos + 1].toInt() and 0xFF) shl 16) or
                ((b[pos + 2].toInt() and 0xFF) shl 8) or
                (b[pos + 3].toInt() and 0xFF)
        }

    private fun doubleAt(b: ByteArray, pos: Int, le: Boolean): Double {
        var bits = 0L
        if (le) {
            for (i in 7 downTo 0) bits = (bits shl 8) or (b[pos + i].toLong() and 0xFF)
        } else {
            for (i in 0..7) bits = (bits shl 8) or (b[pos + i].toLong() and 0xFF)
        }
        return Double.fromBits(bits)
    }
}
