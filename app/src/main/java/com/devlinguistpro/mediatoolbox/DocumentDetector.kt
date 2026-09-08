package com.devlinguistpro.mediatoolbox

import android.graphics.Bitmap
import android.graphics.PointF
import androidx.camera.core.ImageProxy
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** Local, offline document boundary detector used by the scanner preview and capture pipeline. */
object DocumentDetector {
    data class Quad(
        val topLeft: PointF,
        val topRight: PointF,
        val bottomRight: PointF,
        val bottomLeft: PointF,
        val confidence: Float,
        val width: Int,
        val height: Int
    )

    private val openCvReady by lazy { OpenCVLoader.initLocal() }

    fun detect(bitmap: Bitmap): Quad? {
        if (!openCvReady || bitmap.width < 160 || bitmap.height < 160) return null
        val src = Mat()
        return try {
            Utils.bitmapToMat(bitmap, src)
            detectMat(src)
        } finally {
            src.release()
        }
    }

    fun detect(image: ImageProxy): Quad? {
        if (!openCvReady) return null
        val plane = image.planes.firstOrNull() ?: return null
        val crop = image.cropRect
        val width = crop.width()
        val height = crop.height()
        if (width < 160 || height < 160) return null
        val gray = Mat(height, width, org.opencv.core.CvType.CV_8UC1)
        return try {
            copyPlane(plane.buffer, plane.rowStride, plane.pixelStride, crop.left, crop.top, width, height, gray)
            detectGray(gray, width, height)?.let { quad ->
                // Convert crop-local coordinates back into ImageProxy coordinates. This keeps
                // the detector correct when CameraX supplies a non-full crop rect.
                quad.copy(
                    topLeft = PointF(quad.topLeft.x + crop.left, quad.topLeft.y + crop.top),
                    topRight = PointF(quad.topRight.x + crop.left, quad.topRight.y + crop.top),
                    bottomRight = PointF(quad.bottomRight.x + crop.left, quad.bottomRight.y + crop.top),
                    bottomLeft = PointF(quad.bottomLeft.x + crop.left, quad.bottomLeft.y + crop.top),
                    width = image.width,
                    height = image.height
                )
            }
        } finally {
            gray.release()
        }
    }

    fun warp(bitmap: Bitmap, quad: Quad, maxSide: Int = 2200): Bitmap? {
        if (!openCvReady || quad.width <= 0 || quad.height <= 0) return null
        val src = Mat()
        val sourceQuad = MatOfPoint2f(
            Point(quad.topLeft.x.toDouble(), quad.topLeft.y.toDouble()),
            Point(quad.topRight.x.toDouble(), quad.topRight.y.toDouble()),
            Point(quad.bottomRight.x.toDouble(), quad.bottomRight.y.toDouble()),
            Point(quad.bottomLeft.x.toDouble(), quad.bottomLeft.y.toDouble())
        )
        val points = sourceQuad.toArray()
        val sourceWidth = max(1.0, distance(points[0], points[1]))
        val sourceWidthBottom = max(1.0, distance(points[3], points[2]))
        val sourceHeight = max(1.0, distance(points[0], points[3]))
        val sourceHeightRight = max(1.0, distance(points[1], points[2]))
        val targetWidth = max(1, min(maxSide, ((sourceWidth + sourceWidthBottom) / 2.0).toInt()))
        val targetHeight = max(1, min(maxSide, ((sourceHeight + sourceHeightRight) / 2.0).toInt()))
        val destinationQuad = MatOfPoint2f(
            Point(0.0, 0.0),
            Point((targetWidth - 1).toDouble(), 0.0),
            Point((targetWidth - 1).toDouble(), (targetHeight - 1).toDouble()),
            Point(0.0, (targetHeight - 1).toDouble())
        )
        val transform = Imgproc.getPerspectiveTransform(sourceQuad, destinationQuad)
        val warped = Mat()
        return try {
            Utils.bitmapToMat(bitmap, src)
            Imgproc.warpPerspective(src, warped, transform, Size(targetWidth.toDouble(), targetHeight.toDouble()))
            val result = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(warped, result)
            result
        } catch (_: Exception) {
            null
        } finally {
            src.release()
            sourceQuad.release()
            destinationQuad.release()
            transform.release()
            warped.release()
        }
    }

    private fun detectMat(src: Mat): Quad? {
        val gray = Mat()
        return try {
            if (src.channels() == 1) src.copyTo(gray) else Imgproc.cvtColor(src, gray, Imgproc.COLOR_RGBA2GRAY)
            detectGray(gray, src.cols(), src.rows())
        } finally {
            gray.release()
        }
    }

    private fun detectGray(gray: Mat, width: Int, height: Int): Quad? {
        val scale = min(1.0, 900.0 / max(width, height).toDouble())
        val work = Mat()
        return try {
            if (scale < 0.999) Imgproc.resize(gray, work, Size(width * scale, height * scale)) else gray.copyTo(work)
            Imgproc.GaussianBlur(work, work, Size(5.0, 5.0), 0.0)
            val edges = Mat()
            try {
                Imgproc.Canny(work, edges, 45.0, 130.0)
                val contours = ArrayList<MatOfPoint>()
                val hierarchy = Mat()
                try {
                    Imgproc.findContours(edges, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)
                    val imageArea = work.cols().toDouble() * work.rows().toDouble()
                    var best: Quad? = null
                    var bestScore = 0.0
                    for (contour in contours) {
                        val area = abs(Imgproc.contourArea(contour))
                        val areaRatio = area / imageArea
                        if (areaRatio < 0.12 || areaRatio > 0.92) continue
                        val contour2f = MatOfPoint2f(*contour.toArray())
                        val approximation = MatOfPoint2f()
                        val convex = MatOfPoint()
                        try {
                            val perimeter = Imgproc.arcLength(contour2f, true)
                            Imgproc.approxPolyDP(contour2f, approximation, perimeter * 0.018, true)
                            val points = approximation.toArray()
                            if (points.size != 4) continue
                            convex.fromArray(*points)
                            if (!Imgproc.isContourConvex(convex)) continue
                            val ordered = order(points)
                            val sideLengths = arrayOf(
                                distance(ordered[0], ordered[1]),
                                distance(ordered[1], ordered[2]),
                                distance(ordered[2], ordered[3]),
                                distance(ordered[3], ordered[0])
                            )
                            val shortestSide = sideLengths.minOrNull() ?: continue
                            val longestSide = sideLengths.maxOrNull() ?: continue
                            if (shortestSide < min(work.cols(), work.rows()) * 0.12) continue
                            if (longestSide / shortestSide > 4.5) continue
                            val rectangularity = angleScore(ordered)
                            if (rectangularity < 0.55) continue
                            val sideConsistency = 1.0 - min(
                                1.0,
                                (abs(sideLengths[0] - sideLengths[2]) + abs(sideLengths[1] - sideLengths[3])) /
                                    max(1.0, sideLengths.sum()) * 2.0
                            )
                            val areaScore = areaRatio.coerceIn(0.0, 1.0)
                            val marginScore = marginScore(ordered, work.cols(), work.rows())
                            val score = areaScore * 0.50 + rectangularity * 0.25 + sideConsistency * 0.15 + marginScore * 0.10
                            if (score > bestScore) {
                                bestScore = score
                                best = Quad(
                                    topLeft = PointF((ordered[0].x / scale).toFloat(), (ordered[0].y / scale).toFloat()),
                                    topRight = PointF((ordered[1].x / scale).toFloat(), (ordered[1].y / scale).toFloat()),
                                    bottomRight = PointF((ordered[2].x / scale).toFloat(), (ordered[2].y / scale).toFloat()),
                                    bottomLeft = PointF((ordered[3].x / scale).toFloat(), (ordered[3].y / scale).toFloat()),
                                    confidence = score.toFloat().coerceIn(0f, 1f),
                                    width = width,
                                    height = height
                                )
                            }
                        } finally {
                            contour2f.release(); approximation.release(); convex.release()
                        }
                    }
                    return best
                } finally {
                    hierarchy.release(); contours.forEach { it.release() }
                }
            } finally { edges.release() }
        } finally { work.release() }
    }

    private fun copyPlane(buffer: ByteBuffer, rowStride: Int, pixelStride: Int, left: Int, top: Int, width: Int, height: Int, destination: Mat) {
        val data = ByteArray(width * height)
        val duplicate = buffer.duplicate()
        for (y in 0 until height) {
            val rowStart = (top + y) * rowStride + left * pixelStride
            for (x in 0 until width) {
                val index = rowStart + x * pixelStride
                data[y * width + x] = if (index >= 0 && index < duplicate.limit()) duplicate.get(index) else 0
            }
        }
        destination.put(0, 0, data)
    }

    private fun order(points: Array<Point>): Array<Point> {
        val sums = points.map { it.x + it.y }
        val diffs = points.map { it.x - it.y }
        val topLeft = points[sums.indices.minBy { sums[it] }]
        val bottomRight = points[sums.indices.maxBy { sums[it] }]
        val topRight = points[diffs.indices.maxBy { diffs[it] }]
        val bottomLeft = points[diffs.indices.minBy { diffs[it] }]
        return arrayOf(topLeft, topRight, bottomRight, bottomLeft)
    }

    private fun angleScore(points: Array<Point>): Double {
        var score = 0.0
        for (i in points.indices) {
            val previous = points[(i + 3) % 4]; val current = points[i]; val next = points[(i + 1) % 4]
            val ax = previous.x - current.x; val ay = previous.y - current.y
            val bx = next.x - current.x; val by = next.y - current.y
            val denominator = max(1e-9, hypot(ax, ay) * hypot(bx, by))
            val cosine = ((ax * bx + ay * by) / denominator).coerceIn(-1.0, 1.0)
            val angle = acos(cosine)
            score += 1.0 - min(1.0, abs(Math.PI / 2.0 - angle) / (Math.PI / 2.0))
        }
        return score / 4.0
    }

    private fun marginScore(points: Array<Point>, width: Int, height: Int): Double {
        val margin = minOf(points.minOf { it.x }, points.minOf { it.y }, width - points.maxOf { it.x }, height - points.maxOf { it.y })
        return (margin / min(width, height).toDouble() / 0.35).coerceIn(0.0, 1.0)
    }

    private fun distance(a: Point, b: Point): Double = hypot(a.x - b.x, a.y - b.y)
}
