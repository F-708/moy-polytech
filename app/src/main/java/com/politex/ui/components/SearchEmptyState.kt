package com.politex.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

@Composable
fun SearchEmptyState(
    query: String,
    modifier: Modifier = Modifier,
    hint: String = "",
    onReset: (() -> Unit)? = null,
    resetLabel: String = "Сбросить",
    resetTestTag: String = "search_reset_btn",
    resetFilled: Boolean = true,
    hintTestTag: String? = null,
    icon: ImageVector = Icons.Default.SearchOff,
) {
    val action: (@Composable () -> Unit)? =
        if (onReset == null) {
            null
        } else {
            {
                BrandActionButton(
                    icon = Icons.Default.Close,
                    label = resetLabel,
                    testTag = resetTestTag,
                    filled = resetFilled,
                    onClick = onReset,
                )
            }
        }
    EmptyState(
        title = "Ничего не найдено по запросу «${query.trim()}»",
        hint = hint,
        hintTestTag = hintTestTag,
        icon = icon,
        modifier = modifier,
        action = action,
    )
}
