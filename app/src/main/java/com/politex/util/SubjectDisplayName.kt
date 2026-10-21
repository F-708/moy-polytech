package com.politex.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
internal fun subjectDisplayName(subjectRaw: String): String {
    val renameRevision = SubjectRenameStore.revision.intValue
    return remember(subjectRaw, renameRevision) {
        SubjectFormatter.getShortName(subjectRaw)
    }
}
