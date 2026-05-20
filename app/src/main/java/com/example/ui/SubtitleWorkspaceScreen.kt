package com.example.ui

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.SrtParser
import com.example.data.SubtitleProject
import com.example.data.SubtitleTrack
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubtitleWorkspaceScreen(viewModel: SubtitleViewModel) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // ViewModel States
    val projects by viewModel.projects.collectAsState(initial = emptyList())
    val selectedProject by viewModel.selectedProject.collectAsState()
    val tracks = viewModel.getFilteredTracks()
    val activeTrackId by viewModel.activeTrackId.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val filterMode by viewModel.filterMode.collectAsState()
    val apiKeyOverride by viewModel.apiKeyOverride.collectAsState()
    val isProcessingGemini by viewModel.isProcessingGemini.collectAsState()
    val activeTaskTrackId by viewModel.activeTaskTrackId.collectAsState()
    val findQuery by viewModel.findQuery.collectAsState()
    val replaceQuery by viewModel.replaceQuery.collectAsState()
    val actionFeedback by viewModel.actionFeedback.collectAsState()

    // Dialog & Navigation UI States
    var showProjectDrawer by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var importName by remember { mutableStateOf("") }
    var importSrtText by remember { mutableStateOf("") }
    var showPresetMenu by remember { mutableStateOf(false) }
    var showReplacementFeedback by remember { mutableStateOf(false) }
    var showApiKeySettings by remember { mutableStateOf(false) }
    var localApiKeyText by remember { mutableStateOf(apiKeyOverride) }

    // Synchronize local states
    LaunchedEffect(apiKeyOverride) {
        localApiKeyText = apiKeyOverride
    }

    // AutoScroll listener when project index focus moves
    LaunchedEffect(key1 = "autoscroll") {
        viewModel.scrollRequest.collectLatest { index ->
            if (index in tracks.indices) {
                listState.animateScrollToItem(index)
            }
        }
    }

    // Auto dismiss status action feedbacks
    LaunchedEffect(key1 = actionFeedback) {
        if (actionFeedback != null) {
            showReplacementFeedback = true
            kotlinx.coroutines.delay(4000)
            showReplacementFeedback = false
            viewModel.clearFeedback()
        }
    }

    // SAF Document picker for .srt files
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            try {
                context.contentResolver.openInputStream(it)?.use { stream ->
                    val srtText = stream.bufferedReader().readText()
                    var fileName = "imported.srt"
                    context.contentResolver.query(it, null, null, null, null)?.use { cursor ->
                        val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (idx != -1 && cursor.moveToFirst()) {
                            fileName = cursor.getString(idx)
                        }
                    }
                    val projName = fileName.substringBeforeLast(".srt").replace("_", " ").replace("-", " ")
                    viewModel.importProject(projName, fileName, srtText)
                    Toast.makeText(context, "Loaded $fileName successfully!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to parse selected SRT file.", Toast.LENGTH_LONG).show()
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Translate,
                            contentDescription = "App Logo",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Column {
                            Text(
                                text = selectedProject?.name ?: "Sorani Translator",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                )
                            )
                            selectedProject?.let {
                                Text(
                                    text = "Source: ${it.sourceFileName}",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                )
                            }
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showApiKeySettings = !showApiKeySettings },
                        modifier = Modifier.testTag("open_api_key_settings")
                    ) {
                        Icon(
                            imageVector = Icons.Default.VpnKey,
                            contentDescription = "API Keys Control",
                            tint = if (apiKeyOverride.isNotBlank() || com.example.BuildConfig.GEMINI_API_KEY.isNotBlank() && com.example.BuildConfig.GEMINI_API_KEY != "MY_GEMINI_API_KEY") {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            }
                        )
                    }
                    IconButton(
                        onClick = { showProjectDrawer = true },
                        modifier = Modifier.testTag("open_projects_drawer")
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = "Projects")
                    }
                    IconButton(
                        onClick = { showPresetMenu = true },
                        modifier = Modifier.testTag("export_menu_button")
                    ) {
                        Icon(Icons.Default.Download, contentDescription = "Export")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Expanded Optional API Key input section
            AnimatedVisibility(
                visible = showApiKeySettings,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "🔑 Custom Gemini API Key configuration",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        )
                        Text(
                            text = "By default, the workstation uses the API Key configured securely inside the AI Studio Secrets panel. You can also specify an override key below:",
                            style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.secondary)
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = localApiKeyText,
                                onValueChange = {
                                    localApiKeyText = it
                                    viewModel.setApiKeyOverride(it)
                                },
                                placeholder = { Text("AI Studio Gemini API Key override...", style = MaterialTheme.typography.bodyMedium) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                shape = RoundedCornerShape(6.dp),
                                textStyle = MaterialTheme.typography.bodyMedium,
                                trailingIcon = {
                                    if (localApiKeyText.isNotBlank()) {
                                        IconButton(onClick = {
                                            localApiKeyText = ""
                                            viewModel.setApiKeyOverride("")
                                        }) {
                                            Icon(Icons.Default.Clear, contentDescription = "Clear API Key")
                                        }
                                    }
                                }
                            )
                            Button(
                                onClick = { showApiKeySettings = false },
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text("Done")
                            }
                        }
                    }
                }
            }

            // Top control panel
            SearchBarSection(
                searchQuery = searchQuery,
                onSearchChange = { viewModel.setSearchQuery(it) },
                findQuery = findQuery,
                onFindChange = { viewModel.setFindQuery(it) },
                replaceQuery = replaceQuery,
                onReplaceChange = { viewModel.setReplaceQuery(it) },
                onReplaceNext = { viewModel.performReplaceNext() },
                onReplaceAll = { viewModel.performReplaceAll() },
                onAddTrack = { viewModel.addNewTrack() },
                onCleanUp = { viewModel.performMasterCleanUp() },
                filterMode = filterMode,
                onFilterModeChange = { viewModel.setFilterMode(it) },
                isProcessingGemini = isProcessingGemini,
                onTranslateAllUntranslated = { viewModel.translateAllUntranslated() },
                onRefineAllTranslated = { viewModel.refineAllTranslated() }
            )

            // Main central workspace of subtitle scrollable elements
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                SubtitleListGrid(
                    tracks = tracks,
                    activeTrackId = activeTrackId,
                    listState = listState,
                    activeTaskTrackId = activeTaskTrackId,
                    onTrackChange = { id, orig, trans -> viewModel.updateTrackContent(id, orig, trans) },
                    onTrackSelect = { viewModel.selectTrack(it) },
                    onTrackDelete = { viewModel.deleteTrack(it) },
                    onTranslateTrack = { viewModel.translateTrack(it) },
                    onRefineTrack = { viewModel.refineTrack(it) },
                    modifier = Modifier.fillMaxSize()
                )

                // Feedback Banner Layer over text
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 8.dp)
                ) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = showReplacementFeedback,
                        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        ),
                        shape = RoundedCornerShape(6.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (isProcessingGemini) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = "Info feedback",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Text(
                                    text = actionFeedback ?: "",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                    maxLines = 2
                                )
                            }
                            IconButton(
                                onClick = { showReplacementFeedback = false },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Dismiss", modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

    // Projects switcher sheet modal dialog
    if (showProjectDrawer) {
        AlertDialog(
            onDismissRequest = { showProjectDrawer = false },
            title = {
                Text(
                    text = "Project Workspace List",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                ) {
                    Divider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(8.dp))

                    if (projects.isEmpty()) {
                        Text(
                            text = "No stored projects. Ingest subtitles below.",
                            style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.secondary)
                        )
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            itemsIndexed(projects) { _, proj ->
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (selectedProject?.id == proj.id) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                        }
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.selectProject(proj.id)
                                            showProjectDrawer = false
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = proj.name,
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                            )
                                            Text(
                                                text = "Source track: ${proj.sourceFileName}",
                                                style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.secondary)
                                            )
                                        }
                                        if (projects.size > 1) {
                                            IconButton(
                                                onClick = {
                                                    coroutineScope.launch {
                                                        viewModel.deleteProject(proj.id)
                                                    }
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Delete,
                                                    contentDescription = "Delete",
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = {
                            importName = ""
                            importSrtText = ""
                            showImportDialog = true
                            showProjectDrawer = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "New", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Paste SRT", style = MaterialTheme.typography.labelSmall)
                    }

                    OutlinedButton(
                        onClick = { filePickerLauncher.launch("text/*") },
                        modifier = Modifier.weight(1.1f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Icon(Icons.Default.UploadFile, contentDescription = "Import Pick", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Pick .srt file", style = MaterialTheme.typography.labelSmall)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showProjectDrawer = false }) {
                    Text("Close", style = MaterialTheme.typography.labelSmall)
                }
            }
        )
    }

    // Modal dialogue to load custom SRT code
    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text("Paste Subtitle Script (SRT)", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = importName,
                        onValueChange = { importName = it },
                        label = { Text("Workspace Project Name", style = MaterialTheme.typography.labelSmall) },
                        placeholder = { Text("e.g. Kurdish Culinary Guide", style = MaterialTheme.typography.bodyMedium) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(6.dp),
                        textStyle = MaterialTheme.typography.bodyMedium
                    )

                    OutlinedTextField(
                        value = importSrtText,
                        onValueChange = { importSrtText = it },
                        label = { Text("Raw SRT Format Data", style = MaterialTheme.typography.labelSmall) },
                        placeholder = {
                            Text(
                                "1\n00:00:01,000 --> 00:00:03,000\nWhat is your name?\n",
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        textStyle = MaterialTheme.typography.labelSmall,
                        shape = RoundedCornerShape(6.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val nameInput = if (importName.isNotBlank()) importName else "Custom Workshop Project"
                        val srtBody = if (importSrtText.isNotBlank()) importSrtText else """
                            1
                            00:00:01,000 --> 00:00:04,000
                            [Modern Dialogue Section]
                            Welcome to Kurdish translation workstation!
                        """.trimIndent()
                        
                        viewModel.importProject(nameInput, "${nameInput.lowercase().replace(" ", "_")}.srt", srtBody)
                        showImportDialog = false
                    },
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text("Parse & Ingest", style = MaterialTheme.typography.labelSmall)
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) {
                    Text("Cancel", style = MaterialTheme.typography.labelSmall)
                }
            }
        )
    }

    // Modern dropdown modal for export
    if (showPresetMenu) {
        AlertDialog(
            onDismissRequest = { showPresetMenu = false },
            title = { Text("Export & Share Subtitles", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "The system will export your subtitles format, properly appending the Kurdish localization tag (.ku) and re-ordering indices.",
                        style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.secondary)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "FILE TARGET NAME:",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            val proj = selectedProject
                            val cleanName = proj?.sourceFileName?.substringBeforeLast(".")?.replace(Regex("\\.(en|EN|fr|FR|ku|KU|de|de)$"), "") ?: "untitled"
                            Text(
                                text = "$cleanName.ku.srt",
                                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = {
                            viewModel.commitImmediateChanges()
                            val (fName, content) = viewModel.prepareExportContent(useKurdish = true)
                            val success = saveSrtFileToDownloadsFolder(context, fName, content)
                            if (success) {
                                Toast.makeText(context, "Saved file to Downloads: $fName", Toast.LENGTH_LONG).show()
                            } else {
                                triggerGenericShareIntent(context, fName, content)
                            }
                            showPresetMenu = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Share ku", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Export Kurdish Sorani (.ku.srt)", style = MaterialTheme.typography.labelSmall)
                    }

                    OutlinedButton(
                        onClick = {
                            viewModel.commitImmediateChanges()
                            val (fName, content) = viewModel.prepareExportContent(useKurdish = false)
                            triggerGenericShareIntent(context, fName.replace(".ku.srt", ".original.srt"), content)
                            showPresetMenu = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text("Export original track (.srt)", style = MaterialTheme.typography.labelSmall)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showPresetMenu = false },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Cancel", style = MaterialTheme.typography.labelSmall)
                }
            }
        )
    }
}

@Composable
fun SearchBarSection(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    findQuery: String,
    onFindChange: (String) -> Unit,
    replaceQuery: String,
    onReplaceChange: (String) -> Unit,
    onReplaceNext: () -> Unit,
    onReplaceAll: () -> Unit,
    onAddTrack: () -> Unit,
    onCleanUp: () -> Unit,
    filterMode: FilterMode,
    onFilterModeChange: (FilterMode) -> Unit,
    isProcessingGemini: Boolean,
    onTranslateAllUntranslated: () -> Unit,
    onRefineAllTranslated: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Row 1: Search inputs + Add slot + Clean up actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchChange,
                    leadingIcon = {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = "Search",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchChange("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear search", modifier = Modifier.size(14.dp))
                            }
                        }
                    },
                    placeholder = { Text("Filter keywords...", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("search_input_comma"),
                    singleLine = true,
                    shape = RoundedCornerShape(6.dp),
                    textStyle = MaterialTheme.typography.bodyMedium,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                )

                Button(
                    onClick = onCleanUp,
                    modifier = Modifier
                        .height(56.dp)
                        .testTag("eraser_clean_up_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Brush,
                        contentDescription = "Clean Up SDH",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Clean SDH", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onAddTrack,
                    modifier = Modifier
                        .height(56.dp)
                        .testTag("add_dialogue_slot_button"),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Track", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text("Add Slot", style = MaterialTheme.typography.labelSmall)
                }
            }

            // Interactive translation mode filters & Batch operations Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Translation Filter Choices
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = filterMode == FilterMode.ALL,
                        onClick = { onFilterModeChange(FilterMode.ALL) },
                        label = { Text("All", style = MaterialTheme.typography.labelSmall) }
                    )
                    FilterChip(
                        selected = filterMode == FilterMode.UNTRANSLATED,
                        onClick = { onFilterModeChange(FilterMode.UNTRANSLATED) },
                        label = { Text("Untranslated", style = MaterialTheme.typography.labelSmall) }
                    )
                    FilterChip(
                        selected = filterMode == FilterMode.TRANSLATED,
                        onClick = { onFilterModeChange(FilterMode.TRANSLATED) },
                        label = { Text("Translated", style = MaterialTheme.typography.labelSmall) }
                    )
                }

                // Batch AI Operations
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onTranslateAllUntranslated,
                        enabled = !isProcessingGemini,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = "AI Translate all", modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Translate All", style = MaterialTheme.typography.labelSmall)
                    }

                    OutlinedButton(
                        onClick = onRefineAllTranslated,
                        enabled = !isProcessingGemini,
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = "AI Refine all", modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Refine All", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // Row 2: Find & Replace Workflow
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = findQuery,
                    onValueChange = onFindChange,
                    placeholder = { Text("Find terms...", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary) },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("find_input"),
                    singleLine = true,
                    shape = RoundedCornerShape(6.dp),
                    textStyle = MaterialTheme.typography.bodyMedium,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    )
                )

                OutlinedTextField(
                    value = replaceQuery,
                    onValueChange = onReplaceChange,
                    placeholder = { Text("Replace text with...", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary) },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("replace_input"),
                    singleLine = true,
                    shape = RoundedCornerShape(6.dp),
                    textStyle = MaterialTheme.typography.bodyMedium,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    )
                )

                Button(
                    onClick = onReplaceNext,
                    modifier = Modifier
                        .height(40.dp)
                        .testTag("replace_next_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary
                    ),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp)
                ) {
                    Text("Next", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onReplaceAll,
                    modifier = Modifier
                        .height(40.dp)
                        .testTag("replace_all_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp)
                ) {
                    Text("All", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun SubtitleListGrid(
    tracks: List<SubtitleTrack>,
    activeTrackId: Int?,
    listState: androidx.compose.foundation.lazy.LazyListState,
    activeTaskTrackId: Int?,
    onTrackChange: (Int, String, String) -> Unit,
    onTrackSelect: (Int) -> Unit,
    onTrackDelete: (Int) -> Unit,
    onTranslateTrack: (Int) -> Unit,
    onRefineTrack: (Int) -> Unit,
    modifier: Modifier
) {
    if (tracks.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(8.dp)
                )
                .border(
                    BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    shape = RoundedCornerShape(8.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ClosedCaptionDisabled,
                    contentDescription = "Empty",
                    tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f),
                    modifier = Modifier.size(40.dp)
                )
                Text(
                    text = "No subtitle tracks matched filtering.",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.secondary
                    )
                )
            }
        }
    } else {
        LazyColumn(
            state = listState,
            modifier = modifier
                .fillMaxWidth()
                .testTag("subtitle_list"),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            itemsIndexed(tracks, key = { _, track -> track.id }) { _, track ->
                val isActive = track.id == activeTrackId
                val isRunningTask = track.id == activeTaskTrackId
                SubtitleCard(
                    track = track,
                    isActive = isActive,
                    isRunningTask = isRunningTask,
                    onTextChange = { orig, trans -> onTrackChange(track.id, orig, trans) },
                    onClick = { onTrackSelect(track.id) },
                    onDelete = { onTrackDelete(track.id) },
                    onTranslate = { onTranslateTrack(track.id) },
                    onRefine = { onRefineTrack(track.id) }
                )
            }
        }
    }
}

@Composable
fun SubtitleCard(
    track: SubtitleTrack,
    isActive: Boolean,
    isRunningTask: Boolean,
    onTextChange: (String, String) -> Unit,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onTranslate: () -> Unit,
    onRefine: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("subtitle_track_card_${track.indexNumber}"),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive || isRunningTask) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
            }
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (isRunningTask) {
                MaterialTheme.colorScheme.primary
            } else if (isActive) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            }
        ),
        shape = RoundedCornerShape(8.dp),
        elevation = if (isActive || isRunningTask) CardDefaults.cardElevation(defaultElevation = 2.dp) else CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            if (isActive || isRunningTask) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .background(if (isRunningTask) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary)
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Headline
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .background(
                                    color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "#${track.indexNumber}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.secondary
                                )
                            )
                        }
                        
                        // Human-readable SRT timeline duration
                        val startSec = track.startMs / 1000f
                        val endSec = track.endMs / 1000f
                        Text(
                            text = String.format("%.1fs ➔ %.1fs", startSec, endSec),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.secondary,
                                fontFamily = FontFamily.Monospace
                            )
                        )

                        if (track.translatedText.isBlank()) {
                            Box(
                                modifier = Modifier
                                    .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = "Missing translation",
                                    style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.error, fontSize = 9.sp)
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .border(1.dp, Color(0xFF10B981).copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = "Sorani complete",
                                    style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF10B981), fontSize = 9.sp)
                                )
                            }
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Track",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                // Interactive Inputs
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Original Statement
                    OutlinedTextField(
                        value = track.originalText,
                        onValueChange = { onTextChange(it, track.translatedText) },
                        label = { Text("Original Script / Dialog", style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodyMedium,
                        shape = RoundedCornerShape(6.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                        )
                    )

                    // Kurdish Translate field (Arabic RTL Alignment)
                    OutlinedTextField(
                        value = track.translatedText,
                        onValueChange = { onTextChange(track.originalText, it) },
                        label = { Text("Kurdish Sorani (سۆرانی)", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)) },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = TextStyle(
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 16.sp,
                            textDirection = TextDirection.Rtl,
                            textAlign = TextAlign.Right,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        placeholder = {
                            Text(
                                "بنووسە یان داوای وەرگێڕانی زیرەک بکە لێرە...",
                                style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.secondary),
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Right
                            )
                        },
                        shape = RoundedCornerShape(6.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                        )
                    )
                }

                // Card actions bar (Translate single, Refine single, loader indicator)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = onTranslate,
                            enabled = !isRunningTask,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = RoundedCornerShape(4.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Icon(Icons.Default.Translate, contentDescription = "AI translates row", modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Auto Translate", style = MaterialTheme.typography.labelSmall)
                        }

                        OutlinedButton(
                            onClick = onRefine,
                            enabled = !isRunningTask && track.translatedText.isNotBlank(),
                            shape = RoundedCornerShape(4.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = "AI refines row", modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Refine Sorani", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    if (isRunningTask) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 1.5.dp
                            )
                            Text(
                                "Gemini translating...",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                            )
                        }
                    }
                }
            }
        }
    }
}

// MediaStore File Ingestion / Downloader Helper (Requires ZERO permissions on Q+)
fun saveSrtFileToDownloadsFolder(context: Context, fileName: String, content: String): Boolean {
    return try {
        val details = android.content.ContentValues().apply {
            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "text/plain")
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
            }
        }
        val collection = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI
        } else {
            @Suppress("DEPRECATION")
            android.provider.MediaStore.Files.getContentUri("external")
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(collection, details) ?: return false
        resolver.openOutputStream(uri)?.use { stream ->
            stream.write(content.toByteArray())
        }
        true
    } catch (e: Exception) {
        false
    }
}

// Share Intent fallback logic
fun triggerGenericShareIntent(context: Context, fileName: String, content: String) {
    try {
        val tempFile = File(context.cacheDir, fileName)
        tempFile.writeText(content)
        
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            tempFile
        )
        
        val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(android.content.Intent.createChooser(shareIntent, "Save Subtitle File"))
    } catch (e: Exception) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("Subtitle SRT", content)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "$fileName content copied to Clipboard!", Toast.LENGTH_LONG).show()
    }
}
