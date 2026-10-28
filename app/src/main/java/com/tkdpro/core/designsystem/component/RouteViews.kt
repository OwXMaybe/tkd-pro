package com.tkdpro.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.tkdpro.R
import com.tkdpro.core.domain.run.RoutePoint
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import kotlin.math.cos

/**
 * Offline route sketch drawn from the stored GPS points (equirectangular projection).
 * Works without network or map tiles; each pause segment is drawn separately.
 */
@Composable
fun RouteSketch(points: List<RoutePoint>, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.primary
    if (points.size < 2) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.route_no_points), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    Canvas(modifier.padding(8.dp)) {
        val lat0 = points.map { it.latitude }.average()
        val k = cos(Math.toRadians(lat0))
        val xs = points.map { it.longitude * k }
        val ys = points.map { it.latitude }
        val minX = xs.min(); val maxX = xs.max(); val minY = ys.min(); val maxY = ys.max()
        val spanX = (maxX - minX).coerceAtLeast(1e-9)
        val spanY = (maxY - minY).coerceAtLeast(1e-9)
        val scale = minOf(size.width / spanX, size.height / spanY).toFloat()
        val offX = (size.width - spanX * scale).toFloat() / 2
        val offY = (size.height - spanY * scale).toFloat() / 2
        fun project(i: Int) = Offset(offX + ((xs[i] - minX) * scale).toFloat(), size.height - offY - ((ys[i] - minY) * scale).toFloat())
        val path = Path()
        points.indices.forEach { i ->
            val p = project(i)
            if (i == 0 || points[i].segment != points[i - 1].segment) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
        }
        drawPath(path, color, style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(color, 5.dp.toPx(), project(0))
        drawCircle(color.copy(alpha = 0.5f), 7.dp.toPx(), project(points.lastIndex))
    }
}

/**
 * MapLibre map with the recorded route. Uses OpenFreeMap vector tiles (no API key).
 * When tiles cannot be loaded (offline), the [RouteSketch] fallback remains visible underneath.
 */
@Composable
fun RouteMap(points: List<RoutePoint>, modifier: Modifier = Modifier, followLast: Boolean = false) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val routeColor = MaterialTheme.colorScheme.primary.toArgb()
    val mapRef = remember { mutableStateOf<MapLibreMap?>(null) }
    val styleLoaded = remember { mutableStateOf(false) }

    Box(modifier) {
        RouteSketch(points, Modifier.fillMaxSize())
        val mapView = remember {
            MapLibre.getInstance(context)
            MapView(context).apply {
                onCreate(null)
                getMapAsync { map ->
                    map.uiSettings.isRotateGesturesEnabled = false
                    map.uiSettings.isAttributionEnabled = true
                    map.setStyle(Style.Builder().fromUri(MAP_STYLE)) { style ->
                        style.addSource(GeoJsonSource(SOURCE_ID))
                        style.addLayer(
                            LineLayer(LAYER_ID, SOURCE_ID).withProperties(
                                PropertyFactory.lineColor(routeColor),
                                PropertyFactory.lineWidth(4.5f),
                                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                            ),
                        )
                        mapRef.value = map
                        styleLoaded.value = true
                    }
                }
            }
        }
        DisposableEffect(lifecycle) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> mapView.onStart()
                    Lifecycle.Event.ON_RESUME -> mapView.onResume()
                    Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                    Lifecycle.Event.ON_STOP -> mapView.onStop()
                    else -> Unit
                }
            }
            lifecycle.addObserver(observer)
            onDispose {
                lifecycle.removeObserver(observer)
                mapView.onPause()
                mapView.onStop()
                mapView.onDestroy()
            }
        }
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())

        LaunchedEffect(points, styleLoaded.value) {
            val map = mapRef.value ?: return@LaunchedEffect
            val style = map.style ?: return@LaunchedEffect
            val features = points.groupBy { it.segment }.values.filter { it.size >= 2 }.map { seg ->
                Feature.fromGeometry(LineString.fromLngLats(seg.map { Point.fromLngLat(it.longitude, it.latitude) }))
            }
            style.getSourceAs<GeoJsonSource>(SOURCE_ID)?.setGeoJson(FeatureCollection.fromFeatures(features))
            if (points.isEmpty()) return@LaunchedEffect
            if (followLast) {
                val last = points.last()
                map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(last.latitude, last.longitude), 16.0))
            } else if (points.size >= 2) {
                val bounds = LatLngBounds.Builder().includes(points.map { LatLng(it.latitude, it.longitude) }).build()
                map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 60))
            } else {
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(points[0].latitude, points[0].longitude), 16.0))
            }
        }
    }
}

private const val MAP_STYLE = "https://tiles.openfreemap.org/styles/liberty"
private const val SOURCE_ID = "route-source"
private const val LAYER_ID = "route-layer"
