package com.geo.ledger.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

@Composable
fun SkinSelector() {
    val current = LocalGeoSkin.current
    val select = LocalSelectSkin.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("主题皮肤", style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GeoSkin.entries.forEach { skin ->
                val selected = current == skin
                val ink = if (skin == GeoSkin.GRAPHITE) GraphiteInk else Color(0xFF3F5964)
                Surface(shape = RoundedCornerShape(18.dp), color = if (skin == GeoSkin.GRAPHITE) Color(0xFFE9EDF2) else GeoBackground,
                    border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) ink else Color(0xFFCAD0D7)),
                    modifier = Modifier.weight(1f).selectable(selected, role = Role.RadioButton, onClick = { select(skin) })) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(ink).padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(Modifier.width(28.dp).height(3.dp).background(Color.White.copy(alpha = .5f)))
                            Box(Modifier.width(56.dp).height(7.dp).background(Color.White.copy(alpha = .9f)))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(skin.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            RadioButton(selected, onClick = null, modifier = Modifier.size(20.dp))
                        }
                        Text(skin.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        Text("即时切换 · 自动保存 · 不影响账本", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
