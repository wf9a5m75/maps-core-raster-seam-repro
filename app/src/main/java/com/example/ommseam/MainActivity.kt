package com.example.ommseam

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import io.openmobilemaps.mapscore.MapsCore
import io.openmobilemaps.mapscore.map.loader.DataLoader
import io.openmobilemaps.mapscore.map.view.MapView
import io.openmobilemaps.mapscore.map.view.MapViewState
import io.openmobilemaps.mapscore.shared.map.MapConfig
import io.openmobilemaps.mapscore.shared.map.coordinates.Coord
import io.openmobilemaps.mapscore.shared.map.coordinates.CoordinateSystemFactory
import io.openmobilemaps.mapscore.shared.map.coordinates.CoordinateSystemIdentifiers
import io.openmobilemaps.mapscore.shared.map.layers.tiled.DefaultTiled2dMapLayerConfigs
import io.openmobilemaps.mapscore.shared.map.layers.tiled.Tiled2dMapZoomInfo
import io.openmobilemaps.mapscore.shared.map.layers.tiled.raster.Tiled2dMapRasterLayerInterface
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Only OMM + Android Canvas: no vector renderer, map service, or API key. */
class MainActivity : ComponentActivity() {
    private lateinit var server: TileServer
    private lateinit var mapView: MapView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapsCore.initialize()
        val masked = intent.getBooleanExtra("mask", true)
        server = TileServer(checkNotNull(getExternalFilesDir(null))).also { it.start() }
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        layout.addView(TextView(this).apply {
            text = "OMM 4.0.0 raster seam / mask=$masked\nTop text crosses the blue tile boundary. Bottom text is identical, inside a tile. Drag to pan."
            textSize = 16f
            setPadding(16, 12, 16, 12)
        })
        val buttons = LinearLayout(this)
        buttons.addView(Button(this).apply {
            text = "Reset camera"
            setOnClickListener { resetCamera() }
        })
        buttons.addView(Button(this).apply {
            text = "Toggle mask"
            setOnClickListener { intent.putExtra("mask", !masked); recreate() }
        })
        layout.addView(buttons)
        mapView = MapView(this)
        layout.addView(mapView, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(layout)
        mapView.setupMap(MapConfig(CoordinateSystemFactory.getEpsg3857System()), resources.displayMetrics.densityDpi.toFloat(), false, false)
        mapView.registerLifecycle(lifecycle)
        lifecycleScope.launch {
            mapView.mapViewState.first { it != MapViewState.UNINITIALIZED }
            val layer = Tiled2dMapRasterLayerInterface.create(
                DefaultTiled2dMapLayerConfigs.webMercatorCustom(
                    "synthetic", server.urlTemplate(),
                    Tiled2dMapZoomInfo(1f, 0, 0, true, masked, false, true), 4, 4,
                ),
                arrayListOf(DataLoader(this@MainActivity, cacheDir, 1024L * 1024, "", "omm-seam-repro")),
            )
            // The default quad + stencil path. Do not opt into mask-tile geometry.
            mapView.requireMapInterface().insertLayerAt(layer.asLayerInterface(), 0)
            resetCamera()
            Log.i("OmmSeam", "mask=$masked PNG=1024px z=4 tileY=7/8 local-only port=${server.port}")
        }
    }

    private fun resetCamera() {
        mapView.requireMapInterface().getCamera().moveToCenterPositionZoom(
            Coord(CoordinateSystemIdentifiers.EPSG3857(), 40075016.68557849 / 32.0, 0.0, 0.0),
            559082264.029 / 16.0, false,
        )
    }

    override fun onDestroy() {
        server.close()
        super.onDestroy()
    }
}
