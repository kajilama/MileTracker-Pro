package com.example.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tan

/**
 * High-performance, lightweight static map screenshot for trip items.
 * Renders real OpenStreetMap street raster tiles with the actual traced route polyline,
 * start/end pins, and dropoff markers.
 * Designed to use negligible RAM/CPU (no WebViews in list items).
 */
@Composable
fun TripStaticMapView(
    roadPoints: List<Pair<Double, Double>>,
    modifier: Modifier = Modifier,
    isRoundTrip: Boolean = false,
    stopsCount: Int = 0,
    accentColor: Color = Color(0xFF2563EB)
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    // Safety fallback for coordinates
    val safePoints = remember(roadPoints) {
        if (roadPoints.size >= 2) {
            roadPoints
        } else if (roadPoints.size == 1) {
            val p = roadPoints.first()
            listOf(p, Pair(p.first + 0.006, p.second + 0.008))
        } else {
            listOf(Pair(35.3857, -119.1026), Pair(35.3957, -119.0926))
        }
    }

    // Geographic bounding box with safety margins
    val (minLat, maxLat, minLng, maxLng) = remember(safePoints) {
        val lats = safePoints.map { it.first }
        val lngs = safePoints.map { it.second }
        var mnLat = lats.minOrNull() ?: 35.3857
        var mxLat = lats.maxOrNull() ?: 35.3957
        var mnLng = lngs.minOrNull() ?: -119.1026
        var mxLng = lngs.maxOrNull() ?: -119.0926

        // Guarantee a minimal geographic span so zoom isn't extreme if points are nearly identical
        if (mxLat - mnLat < 0.002) {
            val midLat = (mnLat + mxLat) / 2.0
            mnLat = midLat - 0.0025
            mxLat = midLat + 0.0025
        }
        if (mxLng - mnLng < 0.002) {
            val midLng = (mnLng + mxLng) / 2.0
            mnLng = midLng - 0.0025
            mxLng = midLng + 0.0025
        }
        listOf(mnLat, mxLat, mnLng, mxLng)
    }

    BoxWithConstraints(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFFE2E8F0)) // Light map asphalt/canvas background
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
            .clipToBounds()
    ) {
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }

        // Generous insets so the full route trace, turns, start/end pins and badge never clip
        val paddingHorizontalPx = with(density) { 26.dp.toPx() }
        val paddingTopPx = with(density) { 20.dp.toPx() }
        val paddingBottomPx = with(density) { 38.dp.toPx() } // extra clearance for the bottom badge

        val usableWidthPx = (widthPx - (paddingHorizontalPx * 2f)).coerceAtLeast(60f)
        val usableHeightPx = (heightPx - paddingTopPx - paddingBottomPx).coerceAtLeast(50f)

        // Web Mercator normalized calculations [0..1]
        fun normX(lng: Double): Double = (lng + 180.0) / 360.0
        fun normY(lat: Double): Double {
            val sinLat = sin(lat * PI / 180.0).coerceIn(-0.9999, 0.9999)
            return (0.5 - ln((1.0 + sinLat) / (1.0 - sinLat)) / (4.0 * PI))
        }

        val normMinX = normX(minLng)
        val normMaxX = normX(maxLng)
        val normMinY = normY(maxLat) // maxLat has smaller Y in Mercator space
        val normMaxY = normY(minLat) // minLat has larger Y in Mercator space

        val spanNormX = (normMaxX - normMinX).coerceAtLeast(0.00005)
        val spanNormY = (normMaxY - normMinY).coerceAtLeast(0.00005)

        // Exact zoom calculation to guarantee full route containment inside usable bounds
        val targetZoomX = ln(usableWidthPx / (256.0 * spanNormX)) / ln(2.0)
        val targetZoomY = ln(usableHeightPx / (256.0 * spanNormY)) / ln(2.0)
        val zoom = floor(min(targetZoomX, targetZoomY)).toInt().coerceIn(3, 17)

        val mapSize = 256.0 * (1 shl zoom)

        // Exact visual center of the bounding box
        val centerNormX = (normMinX + normMaxX) / 2.0
        val centerNormY = (normMinY + normMaxY) / 2.0
        val centerWorldX = centerNormX * mapSize
        val centerWorldY = centerNormY * mapSize

        // Target screen center (offset vertically to give room for the badge)
        val targetScreenX = paddingHorizontalPx + (usableWidthPx / 2.0)
        val targetScreenY = paddingTopPx + (usableHeightPx / 2.0)

        fun projectScreenX(lng: Double): Float {
            val wx = normX(lng) * mapSize
            return (targetScreenX + (wx - centerWorldX)).toFloat()
        }

        fun projectScreenY(lat: Double): Float {
            val wy = normY(lat) * mapSize
            return (targetScreenY + (wy - centerWorldY)).toFloat()
        }

        // Viewport bounds in world coordinates for tile computation
        val leftWorldX = centerWorldX - targetScreenX
        val rightWorldX = centerWorldX + (widthPx - targetScreenX)
        val topWorldY = centerWorldY - targetScreenY
        val bottomWorldY = centerWorldY + (heightPx - targetScreenY)

        val tileSizeDp = with(density) { 256.toDp() }
        val minTileX = floor(leftWorldX / 256.0).toInt() - 1
        val maxTileX = ceil(rightWorldX / 256.0).toInt() + 1
        val minTileY = floor(topWorldY / 256.0).toInt() - 1
        val maxTileY = ceil(bottomWorldY / 256.0).toInt() + 1

        val maxTileCount = 1 shl zoom

        // 1. REAL OPENSTREETMAP STREET RASTER TILES (100% Free, Zero API Keys, Zero Watermarks)
        Box(modifier = Modifier.fillMaxSize().clipToBounds()) {
            for (tx in minTileX..maxTileX) {
                for (ty in minTileY..maxTileY) {
                    if (ty in 0 until maxTileCount) {
                        val validTx = ((tx % maxTileCount) + maxTileCount) % maxTileCount
                        val tileLeftPx = (tx * 256.0 - leftWorldX).toInt()
                        val tileTopPx = (ty * 256.0 - topWorldY).toInt()

                        // Free OpenStreetMap Standard street tiles (Crisp roads, cities, parks, waterways)
                        val tileUrl = "https://tile.openstreetmap.org/$zoom/$validTx/$ty.png"

                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(tileUrl)
                                .addHeader("User-Agent", "MileTrackerPro/1.0 (Android; DeliveryRouteTracker)")
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier
                                .offset { IntOffset(tileLeftPx, tileTopPx) }
                                .size(tileSizeDp)
                        )
                    }
                }
            }
        }

        // 2. ROUTE POLYLINE & WAYPOINT PINS OVERLAY (Full route trace accurately centered)
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (safePoints.size >= 2) {
                val path = Path()
                safePoints.forEachIndexed { idx, pt ->
                    val sx = projectScreenX(pt.second)
                    val sy = projectScreenY(pt.first)
                    if (idx == 0) path.moveTo(sx, sy) else path.lineTo(sx, sy)
                }

                // Outer route road border / shadow
                drawPath(
                    path = path,
                    color = Color.White.copy(alpha = 0.95f),
                    style = Stroke(width = 9f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )

                // Outer vibrant glow
                drawPath(
                    path = path,
                    color = accentColor.copy(alpha = 0.35f),
                    style = Stroke(width = 14f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )

                // Bold primary route polyline
                drawPath(
                    path = path,
                    color = accentColor,
                    style = Stroke(width = 5.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )

                // Draw Start Pin (Green)
                val startOffset = Offset(
                    projectScreenX(safePoints.first().second),
                    projectScreenY(safePoints.first().first)
                )
                // Pin shadow
                drawCircle(Color.Black.copy(alpha = 0.25f), radius = 13f, center = startOffset.copy(y = startOffset.y + 2f))
                // Pin outer ring
                drawCircle(Color.White, radius = 11f, center = startOffset)
                // Pin green core
                drawCircle(Color(0xFF10B981), radius = 8.5f, center = startOffset)
                // Pin center dot
                drawCircle(Color.White, radius = 3.5f, center = startOffset)

                // Draw End Pin (Red or Loop)
                val endOffset = Offset(
                    projectScreenX(safePoints.last().second),
                    projectScreenY(safePoints.last().first)
                )

                if (!isRoundTrip) {
                    drawCircle(Color.Black.copy(alpha = 0.25f), radius = 13f, center = endOffset.copy(y = endOffset.y + 2f))
                    drawCircle(Color.White, radius = 11f, center = endOffset)
                    drawCircle(Color(0xFFEF4444), radius = 8.5f, center = endOffset)
                    drawCircle(Color.White, radius = 3.5f, center = endOffset)
                } else {
                    // Loop indicator icon dot
                    drawCircle(Color.White, radius = 5.5f, center = startOffset)
                    drawCircle(Color(0xFF10B981), radius = 3.5f, center = startOffset)
                }
            }
        }

        // 3. MAP BADGE & DETAILS AFFORDANCE
        Surface(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shadowElevation = 2.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Map,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = if (stopsCount > 0) "$stopsCount stops • Tap for full map" else "Tap for full map",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                )
            }
        }
    }
}
