package com.politex.ui.screens.materials

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.politex.ui.components.AppCard
import com.politex.ui.components.DialogTitleBar
import com.politex.ui.components.ToggleChip
import com.politex.ui.theme.ColorBgMain
import com.politex.ui.theme.ColorBorderLight
import com.politex.ui.theme.ColorBrandBlue
import com.politex.ui.theme.ColorBrandFill
import com.politex.ui.theme.ColorDangerFill
import com.politex.ui.theme.ColorSurfaceVariantLight
import com.politex.ui.theme.ColorTextBody
import com.politex.ui.theme.ColorTextMuted
import com.politex.ui.theme.ColorTextTitle
import com.politex.ui.theme.Tokens
import com.politex.ui.util.bouncyClickable
import com.politex.util.SubjectRenameStore

@Composable
fun MaterialImportTypeDialog(
    onDismiss: () -> Unit,
    onPickFile: () -> Unit,
    onPickImages: () -> Unit,
    onPickText: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnClickOutside = false),
        shape = RoundedCornerShape(2.dp),
        containerColor = ColorBgMain,
        title = { DialogTitleBar("Добавить материал", onDismiss) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DialogActionRow(
                    icon = Icons.Default.Description,
                    title = "Файл",
                    subtitle = "PDF, DOCX, XLS и другие документы",
                    testTag = "import_type_file",
                    onClick = onPickFile,
                )
                DialogActionRow(
                    icon = Icons.Default.Image,
                    title = "Набор изображений",
                    subtitle = "Фото страниц и доски — один материал",
                    testTag = "import_type_images",
                    onClick = onPickImages,
                )
                DialogActionRow(
                    icon = Icons.Default.TextFields,
                    title = "Текст",
                    subtitle = "Записать конспект или объявление вручную",
                    testTag = "import_type_text",
                    onClick = onPickText,
                )
            }
        },
        confirmButton = {},
        dismissButton = {},
    )
}

@Composable
fun MaterialTextDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnClickOutside = false),
        shape = RoundedCornerShape(2.dp),
        containerColor = ColorBgMain,
        title = { DialogTitleBar("Текстовый материал", onDismiss) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Текст", fontSize = 12.sp) },
                    placeholder = { Text("Первая строка станет названием", fontSize = 12.sp) },
                    minLines = 5,
                    maxLines = 12,
                    shape = RoundedCornerShape(2.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("material_text_field"),
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Текст сохраняется в базе и участвует в поиске — в том числе с опечатками.",
                    style = TextStyle(fontSize = 11.sp, color = ColorTextMuted),
                )
            }
        },
        confirmButton = {
            DialogButton(text = "СОХРАНИТЬ", color = ColorBrandFill, enabled = text.isNotBlank(), testTag = "material_text_save") {
                onConfirm(text)
            }
        },
        dismissButton = {
            DialogButton(text = "ОТМЕНА", color = ColorTextBody, testTag = "material_text_cancel", onClick = onDismiss)
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaterialSubjectDialog(
    curriculumSubjects: List<String>,
    usedSubjects: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (subject: String) -> Unit,
) {
    val subjectOptions = remember(curriculumSubjects, usedSubjects) {
        (curriculumSubjects + usedSubjects).distinct().filter { it.isNotBlank() }.sorted().ifEmpty { listOf("Без предмета") }
    }

    var subjectExpanded by remember { mutableStateOf(false) }
    var isCustomSubject by remember { mutableStateOf(false) }
    var customSubject by remember { mutableStateOf("") }
    var selectedSubject by remember { mutableStateOf(subjectOptions.firstOrNull() ?: "") }

    val context = LocalContext.current
    val renameRevision = SubjectRenameStore.revision.intValue
    val displayOf: (String) -> String = remember(renameRevision) {
        { original -> SubjectRenameStore.displayName(context, original) }
    }

    val subject = if (isCustomSubject) customSubject.trim() else selectedSubject
    val canConfirm = subject.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnClickOutside = false),
        shape = RoundedCornerShape(2.dp),
        containerColor = ColorBgMain,
        title = { DialogTitleBar("Куда положить?", onDismiss) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    ToggleChip("Из списка", !isCustomSubject, Modifier.weight(1f)) { isCustomSubject = false }
                    ToggleChip("Другое…", isCustomSubject, Modifier.weight(1f)) { isCustomSubject = true }
                }

                if (isCustomSubject) {
                    OutlinedTextField(
                        value = customSubject,
                        onValueChange = { customSubject = it },
                        label = { Text("Свой предмет", fontSize = 12.sp) },
                        placeholder = { Text("Например: Репетитор", fontSize = 12.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(2.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dest_custom_subject_field"),
                    )
                } else {
                    ExposedDropdownMenuBox(expanded = subjectExpanded, onExpandedChange = { subjectExpanded = it }) {
                        OutlinedTextField(
                            value = displayOf(selectedSubject),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Предмет", fontSize = 12.sp) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = subjectExpanded) },
                            shape = RoundedCornerShape(2.dp),
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                                .testTag("dest_subject_field"),
                        )
                        ExposedDropdownMenu(expanded = subjectExpanded, onDismissRequest = { subjectExpanded = false }) {
                            subjectOptions.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(displayOf(option), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    onClick = {
                                        selectedSubject = option
                                        subjectExpanded = false
                                    },
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            DialogButton(
                text = "ПОЛОЖИТЬ",
                color = ColorBrandFill,
                enabled = canConfirm,
                testTag = "dest_confirm_btn",
            ) {
                onConfirm(subject)
            }
        },
        dismissButton = {
            DialogButton(text = "ОТМЕНА", color = ColorTextBody, testTag = "dest_cancel_btn", onClick = onDismiss)
        },
    )
}

@Composable
fun MaterialRenameDialog(
    initial: String,
    label: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnClickOutside = false),
        shape = RoundedCornerShape(2.dp),
        containerColor = ColorBgMain,
        title = { DialogTitleBar(label, onDismiss) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text("Название", fontSize = 12.sp) },
                singleLine = true,
                shape = RoundedCornerShape(2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("rename_field"),
            )
        },
        confirmButton = {
            DialogButton(text = "СОХРАНИТЬ", color = ColorBrandFill, enabled = value.isNotBlank(), testTag = "rename_confirm_btn") {
                onConfirm(value.trim())
            }
        },
        dismissButton = {
            DialogButton(text = "ОТМЕНА", color = ColorTextBody, testTag = "rename_cancel_btn", onClick = onDismiss)
        },
    )
}

@Composable
fun MaterialDeleteDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    softActionText: String? = null,
    onSoftAction: (() -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(2.dp),
        containerColor = ColorBgMain,
        title = { Text(title, style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp, color = ColorTextTitle)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(message, style = TextStyle(fontSize = 13.sp, color = ColorTextBody))
                if (softActionText != null && onSoftAction != null) {
                    DialogButton(
                        text = softActionText,
                        color = ColorBrandFill,
                        testTag = "delete_soft_btn",
                        onClick = onSoftAction,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            DialogButton(text = "УДАЛИТЬ", color = ColorDangerFill, testTag = "delete_confirm_btn", onClick = onConfirm)
        },
        dismissButton = {
            DialogButton(text = "ОТМЕНА", color = ColorTextBody, testTag = "delete_cancel_btn", onClick = onDismiss)
        },
    )
}

@Composable
private fun DialogActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    testTag: String,
    onClick: () -> Unit,
) {
    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Tokens.ButtonHeight)
            .bouncyClickable(onClick = onClick)
            .testTag(testTag),
        color = ColorSurfaceVariantLight,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = ColorBrandBlue, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = ColorTextTitle))
                Text(
                    text = subtitle,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(fontSize = 11.sp, color = ColorTextMuted),
                )
            }
        }
    }
}

@Composable
private fun DialogButton(
    text: String,
    color: Color,
    testTag: String,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(2.dp),
        color = Color.Transparent,
        modifier = modifier
            .heightIn(min = 44.dp)
            .bouncyClickable(enabled = enabled, onClick = onClick)
            .testTag(testTag),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 14.dp)) {
            Text(
                text = text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = if (enabled) color else ColorTextMuted.copy(alpha = 0.5f),
                ),
            )
        }
    }
}

@Composable
fun BrandActionButton(
    icon: ImageVector,
    label: String,
    testTag: String,
    filled: Boolean = true,
    onClick: () -> Unit,
) {

    com.politex.ui.components.BrandActionButton(
        icon = icon,
        label = label,
        testTag = testTag,
        filled = filled,
        onClick = onClick,
    )
}
