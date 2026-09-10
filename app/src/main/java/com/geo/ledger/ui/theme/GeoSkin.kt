package com.geo.ledger.ui.theme

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class GeoSkin(val key: String, val title: String, val description: String) {
    CLASSIC("classic", "经典", "熟悉的暖白界面"),
    GRAPHITE("graphite", "石墨银", "银灰留白 · 石墨卡片");

    companion object {
        fun fromKey(key: String?): GeoSkin = entries.firstOrNull { it.key == key } ?: CLASSIC
    }
}

/** Device-local appearance only: never part of the ledger or an imported archive. */
class SkinPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("geo_appearance", Context.MODE_PRIVATE)
    fun read(): GeoSkin = GeoSkin.fromKey(preferences.getString("skin", null))
    fun select(skin: GeoSkin) { preferences.edit().putString("skin", skin.key).apply() }
}

val LocalGeoSkin = staticCompositionLocalOf { GeoSkin.CLASSIC }
val LocalSelectSkin = staticCompositionLocalOf<(GeoSkin) -> Unit> { {} }
val isGraphite: Boolean @Composable get() = LocalGeoSkin.current == GeoSkin.GRAPHITE

@Composable
fun GeoSkinHost(content: @Composable () -> Unit) {
    val context = LocalContext.current.applicationContext
    var preferences by remember { mutableStateOf<SkinPreferences?>(null) }
    var selected by remember { mutableStateOf<GeoSkin?>(null) }
    LaunchedEffect(context) {
        val loaded = withContext(Dispatchers.IO) {
            SkinPreferences(context).let { it to it.read() }
        }
        preferences = loaded.first
        selected = loaded.second
    }
    // Do not first render the wrong skin while the preference is loading.
    selected?.let { skin ->
        CompositionLocalProvider(LocalSelectSkin provides { value ->
            preferences?.select(value)
            selected = value
        }) {
            GeoTheme(skin = skin, content = content)
        }
    }
}
