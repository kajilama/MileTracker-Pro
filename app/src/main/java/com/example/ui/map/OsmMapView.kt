package com.example.ui.map

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

data class MapWaypoint(
    val title: String,
    val lat: Double,
    val lng: Double,
    val stopNumber: Int? = null,
    val isDropoff: Boolean = false
)

private class MapWebInterface(private val onFallback: () -> Unit) {
    @JavascriptInterface
    fun triggerFallback() {
        onFallback()
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun OsmMapView(
    modifier: Modifier = Modifier,
    startLat: Double? = null,
    startLng: Double? = null,
    startTitle: String = "Start Location",
    endLat: Double? = null,
    endLng: Double? = null,
    endTitle: String = "Destination",
    waypoints: List<MapWaypoint> = emptyList(),
    roadPoints: List<Pair<Double, Double>> = emptyList(),
    isRoundTrip: Boolean = false,
    currentLat: Double? = null,
    currentLng: Double? = null,
    interactive: Boolean = true,
    height: Dp = 240.dp
) {
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var useNativeVectorView by remember { mutableStateOf(false) }

    // Always anchor to valid US coordinates: default to 35.3855, -119.1022 (Bakersfield, CA / 8400 Rosedale Hwy area)
    val effectiveStartLat = startLat ?: currentLat ?: 35.3855
    val effectiveStartLng = startLng ?: currentLng ?: -119.1022
    val hasDestination = endLat != null && endLng != null
    val effectiveEndLat = endLat ?: (if (isRoundTrip) effectiveStartLat else (effectiveStartLat + 0.025))
    val effectiveEndLng = endLng ?: (if (isRoundTrip) effectiveStartLng else (effectiveStartLng + 0.035))

    val htmlContent = remember(effectiveStartLat, effectiveStartLng, effectiveEndLat, effectiveEndLng, hasDestination, waypoints, roadPoints, isRoundTrip, currentLat, currentLng) {
        buildOpenStreetMapHtml(
            startLat = effectiveStartLat,
            startLng = effectiveStartLng,
            startTitle = startTitle.replace("'", "\\'"),
            hasDestination = hasDestination,
            endLat = effectiveEndLat,
            endLng = effectiveEndLng,
            endTitle = endTitle.replace("'", "\\'"),
            waypoints = waypoints,
            roadPoints = roadPoints,
            isRoundTrip = isRoundTrip,
            currentLat = currentLat ?: effectiveStartLat,
            currentLng = currentLng ?: effectiveStartLng
        )
    }

    LaunchedEffect(htmlContent) {
        if (!useNativeVectorView) {
            webViewRef?.loadDataWithBaseURL("https://www.openstreetmap.org", htmlContent, "text/html", "UTF-8", null)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
            .testTag("osm_map_view")
    ) {
        if (!useNativeVectorView) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        settings.cacheMode = WebSettings.LOAD_DEFAULT
                        settings.userAgentString = "MileTrackerPro/1.0 (Android; DeliveryRouteTracker)"

                        addJavascriptInterface(MapWebInterface {
                            post { useNativeVectorView = true }
                        }, "AndroidMap")

                        webChromeClient = WebChromeClient()
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                isLoading = true
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                isLoading = false
                            }

                            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                                if (request?.isForMainFrame == true) {
                                    useNativeVectorView = true
                                    isLoading = false
                                }
                            }
                        }
                        loadDataWithBaseURL("https://www.openstreetmap.org", htmlContent, "text/html", "UTF-8", null)
                        webViewRef = this
                    }
                },
                update = { wv ->
                    webViewRef = wv
                }
            )
        } else {
            // High-fidelity Native Vector Route Map: ZERO dependencies, ZERO API keys, ZERO watermarks, instant offline rendering!
            NativeVectorRouteCanvas(
                startTitle = startTitle,
                endTitle = if (hasDestination) endTitle else null,
                waypoints = waypoints,
                roadPoints = roadPoints,
                isRoundTrip = isRoundTrip
            )
        }

        // Floating Loading Indicator
        if (isLoading && !useNativeVectorView) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Bottom Left Mode Badge (100% Free, Zero API Keys, No Watermarks)
        Surface(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp),
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            shadowElevation = 2.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Map,
                    contentDescription = null,
                    modifier = Modifier.size(13.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = if (useNativeVectorView) "Native Vector Map (100% Free • No API Key)" else "OpenStreetMap Standard (Free • No Watermark)",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Floating Map Controls (Zoom, Fit, Mode Switcher)
        if (interactive) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Layer Mode Switcher
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    shadowElevation = 3.dp
                ) {
                    IconButton(
                        onClick = { useNativeVectorView = !useNativeVectorView },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Layers,
                            contentDescription = "Switch Map View",
                            modifier = Modifier.size(18.dp),
                            tint = if (useNativeVectorView) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                if (!useNativeVectorView) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                        shadowElevation = 3.dp
                    ) {
                        IconButton(
                            onClick = { webViewRef?.evaluateJavascript("if(window.map) map.zoomIn();", null) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Zoom In", modifier = Modifier.size(18.dp))
                        }
                    }

                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                        shadowElevation = 3.dp
                    ) {
                        IconButton(
                            onClick = { webViewRef?.evaluateJavascript("if(window.map) map.zoomOut();", null) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "Zoom Out", modifier = Modifier.size(18.dp))
                        }
                    }

                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                        shadowElevation = 3.dp
                    ) {
                        IconButton(
                            onClick = { webViewRef?.evaluateJavascript("if(window.fitRoute) fitRoute();", null) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.CenterFocusStrong, contentDescription = "Center Route", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NativeVectorRouteCanvas(
    startTitle: String,
    endTitle: String?,
    waypoints: List<MapWaypoint>,
    roadPoints: List<Pair<Double, Double>> = emptyList(),
    isRoundTrip: Boolean
) {
    val surfaceColor = MaterialTheme.colorScheme.surface
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    val primaryColor = MaterialTheme.colorScheme.primary

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(surfaceColor)
            .padding(12.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Dynamic Map Street Grid background
            for (i in 1..5) {
                val y = h * (i / 6f)
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(w, y),
                    strokeWidth = if (i % 2 == 0) 6f else 3f
                )
            }
            for (i in 1..5) {
                val x = w * (i / 6f)
                drawLine(
                    color = gridColor,
                    start = Offset(x, 0f),
                    end = Offset(x, h),
                    strokeWidth = if (i % 2 == 0) 6f else 3f
                )
            }

            if (roadPoints.size >= 2) {
                // Map real GPS coordinates to canvas space
                val lats = roadPoints.map { it.first }
                val lngs = roadPoints.map { it.second }
                val minLat = lats.minOrNull() ?: 0.0
                val maxLat = lats.maxOrNull() ?: 1.0
                val minLng = lngs.minOrNull() ?: 0.0
                val maxLng = lngs.maxOrNull() ?: 1.0

                val spanLat = (maxLat - minLat).coerceAtLeast(0.0001)
                val spanLng = (maxLng - minLng).coerceAtLeast(0.0001)

                val paddingX = w * 0.12f
                val paddingY = h * 0.16f
                val usableW = w - (paddingX * 2f)
                val usableH = h - (paddingY * 2f)

                fun project(lat: Double, lng: Double): Offset {
                    val nx = ((lng - minLng) / spanLng).toFloat()
                    val ny = (1.0f - ((lat - minLat) / spanLat).toFloat())
                    return Offset(paddingX + nx * usableW, paddingY + ny * usableH)
                }

                val path = Path()
                roadPoints.forEachIndexed { idx, pt ->
                    val offset = project(pt.first, pt.second)
                    if (idx == 0) path.moveTo(offset.x, offset.y) else path.lineTo(offset.x, offset.y)
                }

                // Outer route glow
                drawPath(
                    path = path,
                    color = primaryColor.copy(alpha = 0.25f),
                    style = Stroke(width = 12f, cap = StrokeCap.Round)
                )

                // Road polyline
                drawPath(
                    path = path,
                    color = primaryColor,
                    style = Stroke(
                        width = 5f,
                        cap = StrokeCap.Round,
                        join = androidx.compose.ui.graphics.StrokeJoin.Round
                    )
                )

                // Start pin
                val startPt = project(roadPoints.first().first, roadPoints.first().second)
                drawCircle(Color(0xFF10B981).copy(alpha = 0.3f), radius = 18f, center = startPt)
                drawCircle(Color(0xFF10B981), radius = 12f, center = startPt)
                drawCircle(Color.White, radius = 5f, center = startPt)

                // Dropoff waypoint pins
                waypoints.forEach { wp ->
                    val wpOffset = project(wp.lat, wp.lng)
                    drawCircle(Color(0xFFF59E0B).copy(alpha = 0.3f), radius = 16f, center = wpOffset)
                    drawCircle(Color(0xFFF59E0B), radius = 10f, center = wpOffset)
                    drawCircle(Color.White, radius = 4f, center = wpOffset)
                }

                // End pin
                val endPt = project(roadPoints.last().first, roadPoints.last().second)
                if (!isRoundTrip) {
                    drawCircle(Color(0xFFEF4444).copy(alpha = 0.3f), radius = 18f, center = endPt)
                    drawCircle(Color(0xFFEF4444), radius = 12f, center = endPt)
                    drawCircle(Color.White, radius = 5f, center = endPt)
                }
            } else {
                // Fallback schematic road
                val startPoint = Offset(w * 0.16f, h * 0.72f)
                val endPoint = if (isRoundTrip) Offset(w * 0.22f, h * 0.74f) else Offset(w * 0.84f, h * 0.28f)
                val path = Path().apply {
                    moveTo(startPoint.x, startPoint.y)
                    lineTo(w * 0.45f, h * 0.35f)
                    lineTo(endPoint.x, endPoint.y)
                    if (isRoundTrip) lineTo(startPoint.x, startPoint.y)
                }
                drawPath(path, primaryColor, style = Stroke(5f, cap = StrokeCap.Round))
                drawCircle(Color(0xFF10B981), radius = 12f, center = startPoint)
                drawCircle(Color.White, radius = 5f, center = startPoint)
            }
        }

        // Route overview pill header
        Surface(
            modifier = Modifier.align(Alignment.TopStart),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shadowElevation = 2.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.DirectionsCar,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isRoundTrip) "$startTitle (Round-Trip • ${waypoints.size} Stops)" else "$startTitle → ${endTitle ?: "Destination"}",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

private fun buildOpenStreetMapHtml(
    startLat: Double,
    startLng: Double,
    startTitle: String,
    hasDestination: Boolean,
    endLat: Double,
    endLng: Double,
    endTitle: String,
    waypoints: List<MapWaypoint>,
    roadPoints: List<Pair<Double, Double>> = emptyList(),
    isRoundTrip: Boolean,
    currentLat: Double?,
    currentLng: Double?
): String {
    val waypointsJs = StringBuilder("[")
    waypoints.forEachIndexed { i, wp ->
        waypointsJs.append("{ lat: ${wp.lat}, lng: ${wp.lng}, title: '${wp.title.replace("'", "\\'")}', num: ${wp.stopNumber ?: (i + 1)} },")
    }
    waypointsJs.append("]")

    val preloadedRoadJs = StringBuilder("[")
    roadPoints.forEach { pt ->
        preloadedRoadJs.append("[${pt.first}, ${pt.second}],")
    }
    preloadedRoadJs.append("]")

    return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="utf-8" />
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
            <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" crossorigin="" />
            <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js" crossorigin=""></script>
            <style>
                html, body, #map {
                    height: 100%;
                    width: 100%;
                    margin: 0;
                    padding: 0;
                    background-color: #f1f5f9;
                }
                .leaflet-control-attribution {
                    display: none !important;
                }
                .custom-marker {
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    font-weight: bold;
                    color: white;
                    font-size: 12px;
                }
                .pin-start {
                    background-color: #10B981;
                    width: 26px;
                    height: 26px;
                    border-radius: 50%;
                    border: 3px solid #ffffff;
                    box-shadow: 0 2px 6px rgba(0,0,0,0.3);
                    display: flex;
                    align-items: center;
                    justify-content: center;
                }
                .pin-stop {
                    background-color: #F59E0B;
                    width: 24px;
                    height: 24px;
                    border-radius: 50%;
                    border: 2px solid #ffffff;
                    box-shadow: 0 2px 6px rgba(0,0,0,0.3);
                    display: flex;
                    align-items: center;
                    justify-content: center;
                }
                .pin-end {
                    background-color: #EF4444;
                    width: 26px;
                    height: 26px;
                    border-radius: 50%;
                    border: 3px solid #ffffff;
                    box-shadow: 0 2px 6px rgba(0,0,0,0.3);
                    display: flex;
                    align-items: center;
                    justify-content: center;
                }
                .pin-current {
                    background-color: #3B82F6;
                    width: 18px;
                    height: 18px;
                    border-radius: 50%;
                    border: 2px solid #ffffff;
                    box-shadow: 0 0 0 6px rgba(59, 130, 246, 0.3);
                }
            </style>
        </head>
        <body>
            <div id="map"></div>
            <script>
                window.onerror = function() {
                    if (window.AndroidMap) window.AndroidMap.triggerFallback();
                };

                setTimeout(function() {
                    if (!window.L && window.AndroidMap) {
                        window.AndroidMap.triggerFallback();
                    }
                }, 2000);

                var startLat = $startLat;
                var startLng = $startLng;
                var hasDest = $hasDestination;
                var endLat = $endLat;
                var endLng = $endLng;
                var isRoundTrip = $isRoundTrip;
                var waypoints = $waypointsJs;

                try {
                    var map = L.map('map', {
                        zoomControl: false,
                        attributionControl: false
                    }).setView([startLat, startLng], 13);

                    // 100% Free Pure OpenStreetMap Standard Tiles (Zero API keys, Zero Watermarks)
                    var primaryTiles = L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
                        maxZoom: 19
                    });
                    
                    primaryTiles.on('tileerror', function() {
                        if (!window.hasSwitchedBackup) {
                            window.hasSwitchedBackup = true;
                            // Seamless free OpenStreetMap Humanitarian (HOT) tile mirror fallback
                            L.tileLayer('https://tile.openstreetmap.fr/hot/{z}/{x}/{y}.png', { maxZoom: 19 }).addTo(map);
                        }
                    });
                    primaryTiles.addTo(map);

                    var startIcon = L.divIcon({
                        className: 'custom-marker',
                        html: '<div class="pin-start">S</div>',
                        iconSize: [26, 26],
                        iconAnchor: [13, 13]
                    });

                    var startMarker = L.marker([startLat, startLng], { icon: startIcon }).addTo(map);
                    startMarker.bindPopup('<b>Start Location</b><br/>$startTitle');

                    var routePoints = [ [startLat, startLng] ];
                    var bounds = [ [startLat, startLng] ];

                    waypoints.forEach(function(wp) {
                        var stopIcon = L.divIcon({
                            className: 'custom-marker',
                            html: '<div class="pin-stop">' + wp.num + '</div>',
                            iconSize: [24, 24],
                            iconAnchor: [12, 12]
                        });
                        var m = L.marker([wp.lat, wp.lng], { icon: stopIcon }).addTo(map);
                        m.bindPopup('<b>Stop ' + wp.num + ' (Customer Dropoff)</b><br/>' + wp.title);
                        routePoints.push([wp.lat, wp.lng]);
                        bounds.push([wp.lat, wp.lng]);
                    });

                    if (isRoundTrip) {
                        routePoints.push([startLat, startLng]);
                    } else if (hasDest && (startLat !== endLat || startLng !== endLng)) {
                        var endIcon = L.divIcon({
                            className: 'custom-marker',
                            html: '<div class="pin-end">E</div>',
                            iconSize: [26, 26],
                            iconAnchor: [13, 13]
                        });
                        var endMarker = L.marker([endLat, endLng], { icon: endIcon }).addTo(map);
                        endMarker.bindPopup('<b>Final Destination</b><br/>$endTitle');
                        routePoints.push([endLat, endLng]);
                        bounds.push([endLat, endLng]);
                    }

                    // Real Navigation Road Routing
                    var preloadedRoad = $preloadedRoadJs;
                    if (preloadedRoad.length > 1) {
                        var roadPolyline = L.polyline(preloadedRoad, {
                            color: '#0D9488',
                            weight: 6,
                            opacity: 0.95,
                            lineCap: 'round',
                            lineJoin: 'round'
                        }).addTo(map);
                        map.fitBounds(roadPolyline.getBounds(), { padding: [35, 35] });
                    } else {
                        var osrmCoords = [startLng + ',' + startLat];
                        waypoints.forEach(function(wp) { osrmCoords.push(wp.lng + ',' + wp.lat); });
                        if (isRoundTrip) {
                            osrmCoords.push(startLng + ',' + startLat);
                        } else if (hasDest && (startLat !== endLat || startLng !== endLng)) {
                            osrmCoords.push(endLng + ',' + endLat);
                        }

                        function drawStraightFallback() {
                            if (routePoints.length > 1) {
                                L.polyline(routePoints, {
                                    color: '#0D9488',
                                    weight: 5,
                                    opacity: 0.9,
                                    dashArray: '8, 8',
                                    lineCap: 'round'
                                }).addTo(map);
                            }
                        }

                        if (osrmCoords.length >= 2) {
                            var osrmUrl = 'https://router.project-osrm.org/route/v1/driving/' + osrmCoords.join(';') + '?overview=full&geometries=geojson';
                            fetch(osrmUrl)
                                .then(function(res) { return res.json(); })
                                .then(function(data) {
                                    if (data && data.routes && data.routes.length > 0 && data.routes[0].geometry && data.routes[0].geometry.coordinates) {
                                        var roadCoords = data.routes[0].geometry.coordinates.map(function(c) {
                                            return [c[1], c[0]];
                                        });
                                        var roadPolyline = L.polyline(roadCoords, {
                                            color: '#0D9488',
                                            weight: 6,
                                            opacity: 0.95,
                                            lineCap: 'round',
                                            lineJoin: 'round'
                                        }).addTo(map);
                                        map.fitBounds(roadPolyline.getBounds(), { padding: [35, 35] });
                                    } else {
                                        drawStraightFallback();
                                    }
                                })
                                .catch(function(err) {
                                    drawStraightFallback();
                                });
                        } else {
                            drawStraightFallback();
                        }
                    }

                    ${if (currentLat != null && currentLng != null) """
                    var curIcon = L.divIcon({
                        className: 'custom-marker',
                        html: '<div class="pin-current"></div>',
                        iconSize: [18, 18],
                        iconAnchor: [9, 9]
                    });
                    L.marker([$currentLat, $currentLng], { icon: curIcon }).addTo(map).bindPopup('<b>Current Location</b>');
                    bounds.push([$currentLat, $currentLng]);
                    """ else ""}

                    window.fitRoute = function() {
                        if (bounds.length > 1) {
                            map.fitBounds(bounds, { padding: [35, 35] });
                        } else {
                            map.setView([startLat, startLng], 14);
                        }
                    };

                    setTimeout(function() {
                        window.fitRoute();
                    }, 300);
                } catch(e) {
                    if (window.AndroidMap) window.AndroidMap.triggerFallback();
                }
            </script>
        </body>
        </html>
    """.trimIndent()
}
