package com.politex.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import coil.compose.AsyncImage
import com.politex.data.local.MpkDatabase
import com.politex.data.local.entity.MaterialItemEntity
import com.politex.data.local.entity.MaterialKind
import com.politex.data.model.GroupInfo
import com.politex.ui.components.AppCard
import com.politex.ui.components.AppDivider
import com.politex.ui.components.EmptyState
import com.politex.ui.components.SearchEmptyState
import com.politex.ui.components.BrandActionButton
import com.politex.ui.screens.materials.ExternalMaterialIntake
import com.politex.ui.screens.materials.MaterialCard
import com.politex.ui.screens.materials.MaterialDeleteDialog
import com.politex.ui.screens.materials.MaterialDocTextViewer
import com.politex.ui.screens.materials.MaterialImageViewer
import com.politex.ui.screens.materials.MaterialImportTypeDialog
import com.politex.ui.screens.materials.MaterialPdfViewer
import com.politex.ui.screens.materials.MaterialWebViewer
import com.politex.ui.screens.materials.docxRouteWeb
import com.politex.ui.screens.materials.pdfRouteWeb
import com.politex.ui.screens.materials.webViewEngineInfo
import com.politex.ui.screens.materials.MaterialRenameDialog
import com.politex.ui.screens.materials.MaterialSubjectDialog
import com.politex.ui.screens.materials.MaterialTextDialog
import com.politex.ui.screens.materials.ViewerMode
import com.politex.ui.screens.materials.materialIconFor
import com.politex.ui.screens.materials.materialIconTint
import com.politex.ui.theme.ColorBgMain
import com.politex.ui.theme.ColorBorderLight
import com.politex.ui.theme.ColorBrandBlue
import com.politex.ui.theme.ColorBrandFill
import com.politex.ui.theme.ColorDangerFill
import com.politex.ui.theme.ColorDividerLight
import com.politex.ui.theme.ColorError
import com.politex.ui.theme.ColorSurfaceHighlight
import com.politex.ui.theme.ColorTextBody
import com.politex.ui.theme.ColorTextMuted
import com.politex.ui.theme.ColorTextTitle
import com.politex.ui.theme.ColorTopBar
import com.politex.ui.theme.Tokens
import com.politex.ui.util.bouncyClickable
import com.politex.util.FuzzySearch
import com.politex.util.MaterialImporter
import com.politex.util.MpkCurriculum
import com.politex.util.PendingMaterial
import com.politex.util.SubjectRenameStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val MATERIAL_STORAGE_NOTE = "Материалы хранятся только на этом телефоне"

private const val RESTORED_SUBJECT = "Восстановленные"

enum class MaterialListUi { LOADING, EMPTY, CONTENT }

fun materialListUi(
    loaded: Boolean,
    itemCount: Int,
): MaterialListUi =
    when {
        !loaded -> MaterialListUi.LOADING
        itemCount == 0 -> MaterialListUi.EMPTY
        else -> MaterialListUi.CONTENT
    }

private fun escapeSegment(s: String): String = s.replace("%", "%25").replace("|", "%7C")

private fun unescapeSegment(s: String): String = s.replace("%7C", "|").replace("%25", "%")

internal sealed interface MaterialRoute {

    data class SubjectScreen(
        val subjectName: String,
    ) : MaterialRoute {
        override fun toString() = "s:" + escapeSegment(subjectName)
    }

    object ArchiveScreen : MaterialRoute {
        override fun toString() = "a"
    }

    data class Viewer(
        val itemId: Long,
    ) : MaterialRoute {
        override fun toString() = "i:$itemId"
    }

    companion object {
        fun decode(raw: String): MaterialRoute? =
            when (raw.firstOrNull()) {
                'a' -> if (raw == "a") ArchiveScreen else null
                'i' -> raw.substringAfter(':', "").toLongOrNull()?.let { Viewer(it) }
                's' -> raw.substringAfter(':', "").takeIf { it.isNotEmpty() }?.let { SubjectScreen(unescapeSegment(it)) }
                else -> null
            }
    }
}

private class MaterialActions(
    val onOpen: (MaterialItemEntity) -> Unit,
    val onRename: (MaterialItemEntity) -> Unit,

    val onSetArchived: (MaterialItemEntity, Boolean) -> Unit,
    val onDelete: (MaterialItemEntity) -> Unit,

    val onReorder: (List<MaterialItemEntity>, MaterialItemEntity, Int) -> Unit,

    val sizeOf: (MaterialItemEntity) -> Long,
)

@Composable
fun MaterialBaseScreen(
    groupInfo: GroupInfo,
    modifier: Modifier = Modifier,
    onExternalMaterialConsumed: () -> Unit = {},
) {
    val context = LocalContext.current
    val dao = remember { MpkDatabase.getInstance(context).materialDao() }
    val scope = rememberCoroutineScope()

    val itemsOrNull by dao.getAllItems().collectAsState(initial = null as List<MaterialItemEntity>?)
    var lastItems by remember { mutableStateOf(emptyList<MaterialItemEntity>()) }

    LaunchedEffect(itemsOrNull) { itemsOrNull?.let { lastItems = it } }
    val items = itemsOrNull ?: lastItems
    val loaded = itemsOrNull != null

    val sizesState = remember { mutableStateOf<Map<Long, Long>>(emptyMap()) }
    LaunchedEffect(items) {
        sizesState.value =
            withContext(Dispatchers.IO) {
                items.associate { it.id to MaterialImporter.storedSize(it) }
            }
    }

    var query by rememberSaveable { mutableStateOf("") }
    val stackState = rememberSaveable { mutableStateOf("") }
    val stack =
        remember(stackState.value) {
            if (stackState.value.isEmpty()) emptyList() else stackState.value.split('|').mapNotNull(MaterialRoute.Companion::decode)
        }
    val push: (MaterialRoute) -> Unit = { route ->
        query = ""
        stackState.value = if (stackState.value.isEmpty()) route.toString() else stackState.value + "|" + route
    }
    val pop: () -> Unit = { stackState.value = stackState.value.substringBeforeLast('|', "") }

    val curriculumSubjects =
        remember(groupInfo.specialtyCode, groupInfo.course) {
            MpkCurriculum.getSubjects(groupInfo.specialtyCode, groupInfo.course)
        }

    var showImportType by remember { mutableStateOf(false) }
    var showTextInput by remember { mutableStateOf(false) }
    var showSubject by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<PendingMaterial?>(null) }
    var pendingFromExternal by remember { mutableStateOf(false) }
    var renamingItem by remember { mutableStateOf<MaterialItemEntity?>(null) }
    var deletingItem by remember { mutableStateOf<MaterialItemEntity?>(null) }
    var importing by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    var preparedMaterial by remember { mutableStateOf<PendingMaterial?>(null) }
    var banner by remember { mutableStateOf<Banner?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val externalConsumed by rememberUpdatedState(onExternalMaterialConsumed)

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            snapshotFlow { ExternalMaterialIntake.active }
                .filterNotNull()
                .collect { material ->
                    if (preparedMaterial === material) return@collect
                    externalConsumed()
                    ExternalMaterialIntake.consumeWarning()?.let { banner = Banner(it, isError = false) }
                    checking = true
                    try {
                        if (MaterialImporter.exceedsSizeLimit(context, material)) {
                            banner = Banner(MaterialImporter.TOO_LARGE_MESSAGE, isError = true)
                            ExternalMaterialIntake.finish(material)
                            preparedMaterial = material
                        } else {
                            val existing =
                                runCatching { MaterialImporter.findExistingByContent(context, dao, material) }
                                    .onFailure { if (it is CancellationException) throw it }
                                    .getOrNull()
                            if (existing != null) {
                                if (!existing.archived && existing.subjectName.isNotBlank()) {
                                    push(MaterialRoute.SubjectScreen(existing.subjectName))
                                }
                                push(MaterialRoute.Viewer(existing.id))
                                ExternalMaterialIntake.finish(material)
                            } else {
                                pending = material
                                pendingFromExternal = true
                                showSubject = true
                            }
                            preparedMaterial = material
                        }
                    } finally {
                        checking = false
                    }
                }
        }
    }

    fun showOk(text: String) {
        banner = Banner(text, isError = false)
    }

    fun showErr(text: String) {
        banner = Banner(text, isError = true)
    }

    val filePicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            val mime = context.contentResolver.getType(uri) ?: ""
            if (mime.startsWith("image/")) {
                showErr("Картинки загружайте через «Набор изображений»: так лекция из 12 фото останется одним материалом")
                return@rememberLauncherForActivityResult
            }
            pending = PendingMaterial.SingleFile(uri)
            pendingFromExternal = false
            showSubject = true
        }
    val imagesPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            if (uris.isNullOrEmpty()) return@rememberLauncherForActivityResult
            pending = PendingMaterial.Images(uris)
            pendingFromExternal = false
            showSubject = true
        }

    fun importInto(
        subject: String,
        material: PendingMaterial,
        openAfter: Boolean = false,
    ) {
        importing = true
        scope.launch {
            val outcome = runCatching { MaterialImporter.importMaterial(context, dao, subject, material) }
            importing = false
            outcome
                .onSuccess { id ->
                    ExternalMaterialIntake.finish(material)
                    showOk("Материал добавлен в «${SubjectRenameStore.displayName(context, subject)}»")
                    if (openAfter) {
                        push(MaterialRoute.SubjectScreen(subject))
                        push(MaterialRoute.Viewer(id))
                    }
                }.onFailure {
                    showErr("Не удалось импортировать: ${it.message ?: it.javaClass.simpleName}")
                    if (material === ExternalMaterialIntake.active) {
                        pending = material
                        pendingFromExternal = true
                        showSubject = true
                    }
                }
        }
    }

    fun deleteItem(item: MaterialItemEntity) {
        scope.launch {

            runCatching {
                MaterialImporter.deleteStoredFiles(item)
                dao.deleteItemWithBookmarks(item.id)
            }.onFailure { showErr("Не удалось удалить: ${it.message ?: it.javaClass.simpleName}") }
        }
    }

    fun setArchived(
        item: MaterialItemEntity,
        archived: Boolean,
    ) {
        scope.launch {
            if (archived) {
                dao.setArchived(item.id, true, dao.nextArchiveSortOrder())
                showOk("Убрано в Архив")
            } else {

                val subject = item.subjectName.ifBlank { RESTORED_SUBJECT }
                dao.updateItem(item.copy(subjectName = subject, archived = false, sortOrder = dao.nextItemSortOrder(subject)))
                showOk("Возвращено в «${SubjectRenameStore.displayName(context, subject)}»")
            }
        }
    }

    val actions =
        remember {
            MaterialActions(
                onOpen = { item -> push(MaterialRoute.Viewer(item.id)) },
                onRename = { item -> renamingItem = item },
                onSetArchived = { item, archived -> setArchived(item, archived) },
                onDelete = { item -> deletingItem = item },
                onReorder = { list, item, delta ->
                    val index = list.indexOfFirst { it.id == item.id }
                    val neighbour = list.getOrNull(index + delta)
                    if (neighbour != null) scope.launch { dao.swapSortOrder(item.id, neighbour.id) }
                },
                sizeOf = { item -> sizesState.value[item.id] ?: 0L },
            )
        }

    BackHandler(enabled = stack.isNotEmpty()) { pop() }

    val currentRoute = stack.lastOrNull()
    val viewerItem = (currentRoute as? MaterialRoute.Viewer)?.let { route -> items.find { it.id == route.itemId } }

    Box(modifier = modifier.fillMaxSize().background(ColorBgMain)) {
        Column(modifier = Modifier.fillMaxSize()) {
            val viewer = currentRoute as? MaterialRoute.Viewer
            if (viewer != null && viewerItem != null) {

                DisposableEffect(viewerItem.id) {
                    ViewerMode.active = true
                    onDispose { ViewerMode.active = false }
                }

                when (viewerItem.kindEnum) {
                    MaterialKind.IMAGES ->
                        MaterialImageViewer(
                            item = viewerItem,
                            dao = dao,
                            onBack = pop,
                            onDelete = { actions.onDelete(viewerItem) },
                            modifier = Modifier.fillMaxSize(),
                        )

                    MaterialKind.FILE -> {

                        val context = LocalContext.current
                        val engine = remember { webViewEngineInfo(context) }
                        val fileExists = remember(viewerItem.uriPath) { File(viewerItem.uriPath).exists() }
                        var useFallback by remember(viewerItem.id) { mutableStateOf(false) }
                        val useWeb = !useFallback && when (viewerItem.ext) {
                            "pdf" -> pdfRouteWeb(fileExists, viewerItem.pageCount, engine.first, engine.second)
                            "docx" -> docxRouteWeb(viewerItem.textEdited, fileExists, engine.first, engine.second)
                            else -> false
                        }
                        when {
                            useWeb ->
                                MaterialWebViewer(
                                    item = viewerItem,
                                    dao = dao,
                                    onBack = pop,
                                    onFallbackNative = { useFallback = true },
                                    modifier = Modifier.fillMaxSize(),
                                )

                            viewerItem.ext == "pdf" ->
                                MaterialPdfViewer(item = viewerItem, dao = dao, onBack = pop, modifier = Modifier.fillMaxSize())

                            else ->
                                MaterialDocTextViewer(item = viewerItem, dao = dao, onBack = pop, modifier = Modifier.fillMaxSize())
                        }
                    }

                    MaterialKind.TEXT ->
                        MaterialDocTextViewer(item = viewerItem, dao = dao, onBack = pop, modifier = Modifier.fillMaxSize())
                }
            } else if (viewer != null) {

                LaunchedEffect(viewer.itemId) {
                    if (dao.getItemById(viewer.itemId) == null) pop()
                }
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = ColorBrandBlue, modifier = Modifier.testTag("material_viewer_loading"))
                }
            } else {
                val subjectRoute = currentRoute as? MaterialRoute.SubjectScreen
                val archiveRoute = currentRoute as? MaterialRoute.ArchiveScreen

                if (subjectRoute == null && archiveRoute == null) {
                    MaterialHeader(
                        query = query,
                        onQueryChange = { query = it },
                        showSearch = true,
                        onAdd = { showImportType = true },
                    )
                }

                val subjectItems =
                    remember(items, subjectRoute) {
                        subjectRoute?.let { route -> items.filter { !it.archived && it.subjectName == route.subjectName } }
                    }
                val archivedItems = remember(items, archiveRoute) { if (archiveRoute != null) items.filter { it.archived } else null }

                when {
                    subjectRoute != null && subjectItems != null ->
                        MaterialSubjectContent(
                            subjectName = subjectRoute.subjectName,
                            isArchive = false,
                            items = subjectItems,
                            loaded = loaded,
                            actions = actions,
                            onBack = pop,
                            onAdd = { showImportType = true },
                            modifier = Modifier.weight(1f),
                        )

                    archiveRoute != null && archivedItems != null ->
                        MaterialSubjectContent(
                            subjectName = null,
                            isArchive = true,
                            items = archivedItems,
                            loaded = loaded,
                            actions = actions,
                            onBack = pop,
                            onAdd = { showImportType = true },
                            modifier = Modifier.weight(1f),
                        )

                    else ->
                        if (query.isBlank()) {
                            MaterialRootList(
                                items = items,
                                loaded = loaded,
                                onOpenSubject = { subject -> push(MaterialRoute.SubjectScreen(subject)) },
                                onOpenArchive = { push(MaterialRoute.ArchiveScreen) },
                                modifier = Modifier.weight(1f),
                            )
                        } else {
                            MaterialSearchContent(
                                query = query,
                                items = items,
                                actions = actions,
                                onClear = { query = "" },
                                modifier = Modifier.weight(1f),
                            )
                        }
                }
            }
        }

        if (checking || importing) {
            Box(
                modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.25f))
                    .testTag("material_intake_progress"),
                contentAlignment = Alignment.Center,
            ) {
                AppCard(
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(16.dp),
                    ) {
                        CircularProgressIndicator(color = ColorBrandBlue, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = if (checking) "Проверяем материал…" else "Копируем в хранилище…",
                            style = TextStyle(fontSize = 13.sp, color = ColorTextBody),
                        )
                    }
                }
            }
        }

        banner?.let { current ->
            MessageBanner(
                banner = current,
                onClose = { banner = null },
                modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(12.dp),
            )
        }
    }

    if (showImportType) {
        MaterialImportTypeDialog(
            onDismiss = { showImportType = false },
            onPickFile = {
                showImportType = false
                runCatching { filePicker.launch(arrayOf("*/*")) }
                    .onFailure { showErr("Не удалось открыть выбор файлов") }
            },
            onPickImages = {
                showImportType = false
                runCatching { imagesPicker.launch(arrayOf("image/*")) }
                    .onFailure { showErr("Не удалось открыть выбор изображений") }
            },
            onPickText = {
                showImportType = false
                showTextInput = true
            },
        )
    }

    if (showTextInput) {
        MaterialTextDialog(
            onDismiss = { showTextInput = false },
            onConfirm = { text ->
                showTextInput = false
                pending = PendingMaterial.Text(text)
                pendingFromExternal = false
                showSubject = true
            },
        )
    }

    if (showSubject) {
        val material = pending
        val fromExternal = pendingFromExternal
        MaterialSubjectDialog(
            curriculumSubjects = curriculumSubjects,
            usedSubjects = remember(items) {
                items.filter { !it.archived && it.subjectName.isNotBlank() }.map { it.subjectName }.distinct()
            },
            onDismiss = {
                showSubject = false
                pending = null
                pendingFromExternal = false
                if (material != null) ExternalMaterialIntake.finish(material)
            },
            onConfirm = { subject ->
                showSubject = false
                pending = null
                pendingFromExternal = false
                if (material != null) importInto(subject, material, openAfter = fromExternal)
            },
        )
    }

    renamingItem?.let { item ->
        MaterialRenameDialog(
            initial = item.title,
            label = "Переименовать материал",
            onDismiss = { renamingItem = null },
            onConfirm = { newTitle ->
                renamingItem = null
                scope.launch { dao.renameItem(item.id, newTitle) }
            },
        )
    }

    deletingItem?.let { item ->
        MaterialDeleteDialog(
            title = "Удалить материал?",

            message = "«${item.title}» уберётся из приложения. Исходный файл на устройстве останется.",
            softActionText = if (item.archived) "ВЕРНУТЬ ИЗ АРХИВА" else "УБРАТЬ В АРХИВ",
            onSoftAction = {
                deletingItem = null
                setArchived(item, !item.archived)
            },
            onDismiss = { deletingItem = null },
            onConfirm = {
                deletingItem = null
                deleteItem(item)
            },
        )
    }
}

private data class Banner(
    val text: String,
    val isError: Boolean,
)

@Composable
private fun MessageBanner(
    banner: Banner,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {

    LaunchedEffect(banner) {
        delay(4000)
        onClose()
    }
    Surface(
        shape = RoundedCornerShape(2.dp),
        border = BorderStroke(1.dp, if (banner.isError) ColorError else ColorBorderLight),
        color = if (banner.isError) ColorError.copy(alpha = 0.08f) else ColorSurfaceHighlight,
        modifier =
        modifier
            .fillMaxWidth()
            .testTag(if (banner.isError) "material_error_banner" else "material_info_banner"),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 12.dp, end = 2.dp, top = 4.dp, bottom = 4.dp),
        ) {
            Text(
                text = banner.text,
                modifier = Modifier.weight(1f).padding(vertical = 8.dp),
                style = TextStyle(fontSize = 12.sp, color = if (banner.isError) ColorError else ColorTextBody),
            )
            IconButton(onClick = onClose, modifier = Modifier.size(44.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Скрыть", tint = ColorTextMuted, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun MaterialHeader(
    query: String,
    onQueryChange: (String) -> Unit,
    showSearch: Boolean,
    onAdd: () -> Unit,
) {
    Row(
        modifier =
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {

        if (showSearch) {
            com.politex.ui.components.SearchBar(
                value = query,
                onValueChange = onQueryChange,
                placeholder = "Поиск материалов",
                testTag = "material_search_field",
                modifier = Modifier.weight(1f),
            )
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }
        BrandActionButton(
            icon = Icons.Default.Add,
            label = "ДОБАВИТЬ",
            testTag = "material_add_btn",
            onClick = onAdd,
        )
    }
}

@Composable
private fun MaterialRootList(
    items: List<MaterialItemEntity>,
    loaded: Boolean,
    onOpenSubject: (String) -> Unit,
    onOpenArchive: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val active = remember(items) { items.filter { !it.archived && it.subjectName.isNotBlank() } }
    val subjects = remember(active) { active.groupBy { it.subjectName }.keys.sorted() }
    val archiveCount = remember(items) { items.count { it.archived } }

    when (materialListUi(loaded, active.size + archiveCount)) {
        MaterialListUi.LOADING -> {
            MaterialLoadingPlaceholder(modifier)
            return
        }
        MaterialListUi.EMPTY -> {
            EmptyState(
                title = "Материалов пока нет",
                hint =
                "Нажмите «ДОБАВИТЬ» и выберите файл, набор изображений или текст. " +
                    "Материал ляжет в предмет — потом его можно переименовать, " +
                    "перенести в Архив и читать прямо в приложении.",
                note = MATERIAL_STORAGE_NOTE,
                hintTestTag = "material_empty_hint",
                modifier = modifier,
            )
            return
        }
        MaterialListUi.CONTENT -> Unit
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth().testTag("material_root_list"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(count = subjects.size, key = { "subject:${subjects[it]}" }) { index ->
            val subject = subjects[index]
            SubjectRow(
                subjectName = subject,
                count = active.count { it.subjectName == subject },
                onOpen = { onOpenSubject(subject) },
            )
        }

        if (archiveCount > 0) {
            item(key = "archive") {
                MaterialCard(testTag = "material_archive_section", onClick = onOpenArchive) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Icon(
                            imageVector = Icons.Default.Inventory2,
                            contentDescription = null,
                            tint = ColorTopBar,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Архив",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = ColorTextTitle),
                            )
                            Text(
                                text = "$archiveCount ${deferredWord(archiveCount)}",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = TextStyle(fontSize = 11.sp, color = ColorTextMuted),
                            )
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = ColorTextMuted, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SubjectRow(
    subjectName: String,
    count: Int,
    onOpen: () -> Unit,
) {
    val context = LocalContext.current
    val renameRevision = SubjectRenameStore.revision.intValue
    val displayName = remember(subjectName, renameRevision) { SubjectRenameStore.displayName(context, subjectName) }
    MaterialCard(testTag = "material_subject_$subjectName", onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = null,
                tint = ColorBrandBlue,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = displayName,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
                style = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = ColorTextTitle),
            )
            Text(
                text = "$count",
                style = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = ColorTextMuted),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = ColorTextMuted, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun MaterialSubjectContent(
    subjectName: String?,
    isArchive: Boolean,
    items: List<MaterialItemEntity>,
    loaded: Boolean,
    actions: MaterialActions,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val renameRevision = SubjectRenameStore.revision.intValue
    val displaySubject =
        remember(subjectName, renameRevision) {
            subjectName?.let { SubjectRenameStore.displayName(context, it) }
        }
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(Tokens.HitTarget).testTag(if (isArchive) "archive_back_btn" else "subject_back_btn")) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = ColorTextTitle)
            }
            if (isArchive) {
                Icon(
                    imageVector = Icons.Default.Inventory2,
                    contentDescription = null,
                    tint = ColorTopBar,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displaySubject ?: "Архив",
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 15.sp, color = ColorTextTitle),
                )
                Text(
                    text =
                    if (isArchive) {
                        "${items.size} ${deferredWord(items.size)}"
                    } else {
                        "${items.size} ${pluralWord(items.size, "материал", "материала", "материалов")}"
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(fontSize = 11.sp, color = ColorTextMuted),
                )
            }

            Surface(
                shape = RoundedCornerShape(2.dp),
                border = BorderStroke(1.dp, ColorBrandBlue),
                color = ColorBrandFill,
                modifier =
                Modifier
                    .padding(end = 12.dp)
                    .size(Tokens.IconHit)
                    .bouncyClickable(onClick = onAdd)
                    .testTag("material_add_btn"),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Добавить",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }

        AppDivider(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp))

        when (materialListUi(loaded, items.size)) {
            MaterialListUi.LOADING ->
                MaterialLoadingPlaceholder(Modifier.weight(1f))

            MaterialListUi.EMPTY ->
                EmptyState(
                    title = if (isArchive) "В Архиве пусто" else "Материалов пока нет",
                    hint =
                    if (isArchive) {
                        "Сюда переносятся материалы кнопкой-архивом: не удаляются, а убираются с глаз."
                    } else {
                        "Нажмите «ДОБАВИТЬ» на главном экране материалов и выберите этот предмет."
                    },
                    note = MATERIAL_STORAGE_NOTE,
                    hintTestTag = "material_empty_hint",
                    modifier = Modifier.weight(1f),
                )

            MaterialListUi.CONTENT ->
                LazyColumn(
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .testTag(if (isArchive) "material_archive_list" else "material_subject_list"),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(count = items.size, key = { items[it].id }) { index ->
                        MaterialItemRow(
                            item = items[index],
                            actions = actions,
                            isArchiveRow = isArchive,
                            canMoveUp = index > 0,
                            canMoveDown = index < items.size - 1,
                            siblings = items,
                        )
                    }
                }
        }
    }
}

@Composable
private fun MaterialLoadingPlaceholder(modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxWidth().testTag("material_loading_placeholder"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(count = 4) {
            Box(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .height(62.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(ColorSurfaceHighlight),
            )
        }
    }
}

@Composable
private fun MaterialSearchContent(
    query: String,
    items: List<MaterialItemEntity>,
    actions: MaterialActions,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = remember(items) { items.filter { !it.archived } }

    val hits =
        remember(scope, query) {
            val q = query.trim()
            if (q.isEmpty()) return@remember emptyList()
            scope
                .mapNotNull { item ->
                    val score = FuzzySearch.score(item.title + "\n" + item.textContent, q)
                    if (score > 0) item to score else null
                }.sortedWith(compareByDescending<Pair<MaterialItemEntity, Int>> { it.second }.thenBy { it.first.title })
        }

    val queryWords = remember(query) { FuzzySearch.queryWordCount(query) }

    if (hits.isEmpty()) {

        SearchEmptyState(
            query = query,
            hint = "Архив в поиске не участвует",
            onReset = onClear,
            resetLabel = "Очистить",
            resetTestTag = "material_search_clear",
            resetFilled = false,
            hintTestTag = "material_empty_hint",
            modifier = modifier,
        )
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth().testTag("material_search_list"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(count = hits.size, key = { hits[it].first.id }) { index ->
            val (item, score) = hits[index]
            MaterialItemRow(
                item = item,
                actions = actions,
                isArchiveRow = false,
                showReorder = false,
                matchCount = score to queryWords,
            )
        }
    }
}

@Composable
private fun MaterialItemRow(
    item: MaterialItemEntity,
    actions: MaterialActions,
    isArchiveRow: Boolean = false,
    showReorder: Boolean = true,
    canMoveUp: Boolean = true,
    canMoveDown: Boolean = true,
    siblings: List<MaterialItemEntity> = emptyList(),
    matchCount: Pair<Int, Int>? = null,
) {
    MaterialCard(testTag = "material_item_${item.id}", onClick = { actions.onOpen(item) }) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier =
                    Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(ColorSurfaceHighlight),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = materialIconFor(item),
                        contentDescription = null,
                        tint = materialIconTint(item),
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = ColorTextTitle),
                    )
                    Text(
                        text = materialMeta(item, actions.sizeOf(item)),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(fontSize = 11.sp, color = ColorTextMuted),
                    )
                }
                if (matchCount != null) {
                    Surface(
                        shape = RoundedCornerShape(2.dp),
                        color = ColorBrandBlue.copy(alpha = 0.10f),
                        border = BorderStroke(1.dp, ColorBrandBlue.copy(alpha = 0.4f)),
                        modifier = Modifier.testTag("material_match_${item.id}"),
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)) {
                            Text(
                                text = "${matchCount.first}/${matchCount.second}",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 10.sp, color = ColorBrandBlue),
                            )
                        }
                    }
                }
            }

            val paths = item.imagePaths
            if (paths.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                    items(count = minOf(paths.size, 8)) { index ->
                        AsyncImage(
                            model = File(paths[index]),
                            contentDescription = "Превью ${index + 1}",
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier =
                            Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(ColorSurfaceHighlight)
                                .testTag("material_thumb_${item.id}_$index"),
                        )
                    }
                    if (paths.size > 8) {
                        item {
                            Box(
                                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(2.dp)).background(ColorSurfaceHighlight),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("+${paths.size - 8}", style = TextStyle(fontSize = 11.sp, color = ColorTextMuted))
                            }
                        }
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            ) {
                if (showReorder) {

                    RowIconButton(
                        icon = Icons.Default.KeyboardArrowUp,
                        description = "Выше",
                        enabled = canMoveUp,
                        testTag = "material_up_${item.id}",
                    ) { actions.onReorder(siblings, item, -1) }
                    RowIconButton(
                        icon = Icons.Default.KeyboardArrowDown,
                        description = "Ниже",
                        enabled = canMoveDown,
                        testTag = "material_down_${item.id}",
                    ) { actions.onReorder(siblings, item, 1) }
                    Spacer(modifier = Modifier.width(4.dp))
                }
                RowIconButton(
                    icon = Icons.Default.Edit,
                    description = "Переименовать",
                    enabled = true,
                    testTag = "material_rename_${item.id}",
                ) { actions.onRename(item) }
                if (isArchiveRow) {

                    RowIconButton(
                        icon = Icons.Default.Unarchive,
                        description = "Вернуть из Архива",
                        enabled = true,
                        testTag = "material_restore_${item.id}",
                    ) { actions.onSetArchived(item, false) }
                } else {
                    RowIconButton(
                        icon = Icons.Default.Inventory2,
                        description = "Убрать в Архив",
                        enabled = true,
                        testTag = "material_archive_${item.id}",
                    ) { actions.onSetArchived(item, true) }
                }
                RowIconButton(
                    icon = Icons.Default.Delete,
                    description = "Удалить",
                    enabled = true,
                    danger = true,
                    testTag = "material_delete_${item.id}",
                ) { actions.onDelete(item) }
            }
        }
    }
}

private fun materialMeta(
    item: MaterialItemEntity,
    sizeBytes: Long,
): String {
    val size = MaterialImporter.humanSize(sizeBytes)
    return when (item.kindEnum) {
        MaterialKind.TEXT -> "Текст • ${item.textContent.length} симв."
        MaterialKind.IMAGES -> "Набор: ${item.imagePaths.size} изобр." + if (size != "0 Б") " • $size" else ""
        MaterialKind.FILE ->
            buildString {
                append(item.ext.uppercase().ifBlank { "ФАЙЛ" })
                if (size != "0 Б") append(" • $size")
                if (item.pageCount > 0) append(" • ${item.pageCount} стр.")
            }
    }
}

@Composable
private fun RowIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    enabled: Boolean,
    testTag: String,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(2.dp),
        color = Color.Transparent,
        modifier =
        Modifier
            .size(44.dp)
            .bouncyClickable(enabled = enabled, onClick = onClick)
            .testTag(testTag),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = description,
                tint =
                when {
                    !enabled -> ColorTextMuted.copy(alpha = 0.35f)
                    danger -> ColorDangerFill
                    else -> ColorBrandBlue
                },
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

private fun pluralWord(
    n: Int,
    one: String,
    few: String,
    many: String,
): String =
    when {
        n % 10 == 1 && n % 100 != 11 -> one
        n % 10 in 2..4 && n % 100 !in 12..14 -> few
        else -> many
    }

private fun deferredWord(n: Int): String = pluralWord(n, "отложенный", "отложенных", "отложенных")
