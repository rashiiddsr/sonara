/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun Material3MenuGroup(
    items: List<Material3MenuItemData>
) {
    // Flat rows like a native menu: no card or border around each item.
    Column(modifier = Modifier.fillMaxWidth()) {
        items.forEach { item ->
            Material3MenuItemRow(item = item)
        }
    }
}

@Composable
private fun Material3MenuItemRow(
    item: Material3MenuItemData
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                enabled = item.onClick != null,
                onClick = { item.onClick?.invoke() }
            )
            .heightIn(min = 52.dp)
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        item.icon?.let { icon ->
            icon()
            Spacer(modifier = Modifier.width(20.dp))
        }

        Column(
            modifier = Modifier.weight(1f)
        ) {
            ProvideTextStyle(MaterialTheme.typography.bodyLarge) {
                item.title()
            }

        }
        item.trailingContent?.let { trailing ->
            Spacer(modifier = Modifier.width(8.dp))
            trailing()
        }
    }
}

data class Material3MenuItemData(
    val icon: (@Composable () -> Unit)? = null,
    val title: @Composable () -> Unit,
    val description: (@Composable () -> Unit)? = null,
    val onClick: (() -> Unit)? = null,
    val cardColors: CardColors? = null,
    val trailingContent: (@Composable () -> Unit)? = null
)
