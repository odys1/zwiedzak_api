package com.zwiedzak.ui.screens

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.zwiedzak.data.model.VisitStatus

@Composable
internal fun VisitStatusCheckboxes(
    status: VisitStatus,
    showAvoid: Boolean,
    enabled: Boolean,
    onChange: (VisitStatus) -> Unit
) {
    Row(
        modifier = Modifier.heightIn(min = 48.dp).toggleable(
            value = status.isVisited,
            enabled = enabled,
            role = Role.Checkbox,
            onValueChange = { onChange(status.withVisited(it)) }
        ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = status.isVisited, onCheckedChange = null, enabled = enabled)
        Text("Oznacz jako odwiedzone", maxLines = 1, softWrap = false)
    }
    if (showAvoid) {
        Row(
            modifier = Modifier.toggleable(
                value = status == VisitStatus.AVOID,
                enabled = enabled,
                role = Role.Checkbox,
                onValueChange = { onChange(status.withAvoid(it)) }
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = status == VisitStatus.AVOID, onCheckedChange = null, enabled = enabled)
            Text("Nie wracam")
        }
    }
}
