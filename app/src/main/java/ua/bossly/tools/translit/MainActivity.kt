package ua.bossly.tools.translit

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.ReportDrawnWhen
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import ua.bossly.tools.translit.ui.components.TranslitTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.text.HtmlCompat
import androidx.lifecycle.ViewModelProvider
import ua.bossly.tools.translit.data.AppDatabase
import ua.bossly.tools.translit.data.TransliterationHistory
import ua.bossly.tools.translit.data.TransliterationRepository
import ua.bossly.tools.translit.ui.theme.UaTranslitTheme

/**
 * Converts a [Spanned] into an [AnnotatedString] trying to keep as much formatting as possible.
 *
 * Currently supports `bold`, `italic`, `underline` and `color`.
 */
fun Spanned.toAnnotatedString(linkColor: Color = Color.Unspecified): AnnotatedString = buildAnnotatedString {
    val spanned = this@toAnnotatedString
    append(spanned.toString())
    getSpans(0, spanned.length, Any::class.java).forEach { span ->
        val start = getSpanStart(span)
        val end = getSpanEnd(span)
        when (span) {
            is StyleSpan -> when (span.style) {
                Typeface.BOLD -> addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, end)
                Typeface.ITALIC -> addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, end)
                Typeface.BOLD_ITALIC -> addStyle(
                    SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic),
                    start,
                    end
                )
            }

            is UnderlineSpan -> addStyle(
                SpanStyle(textDecoration = TextDecoration.Underline),
                start,
                end
            )

            is ForegroundColorSpan -> {
                val color = Color(span.foregroundColor)
                // Filter out default black/near-black spans from HtmlCompat so Compose MaterialTheme text color applies cleanly in Light & Dark modes
                if (color != Color.Black && span.foregroundColor != 0xFF000000.toInt()) {
                    addStyle(SpanStyle(color = color), start, end)
                }
            }
        }
    }
}


class MainActivity : ComponentActivity() {
    companion object {
        private const val TAG = "MainActivity"

        /** When true (e.g. instrumented UI tests), skip save-result Toasts for clean screenshots. */
        const val EXTRA_SUPPRESS_SAVE_FEEDBACK = "suppress_save_feedback"
    }

    private val database by lazy { AppDatabase.getDatabase(applicationContext) }
    private val repository by lazy { TransliterationRepository(database.transliterationDao()) }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val viewModel: HomeViewModel by viewModels {
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(HomeViewModel::class.java))
                        return HomeViewModel(repository) as T
                    throw IllegalArgumentException("Unknown ViewModel class")
                }
            }
        }

        // Handle App Actions and deep links
        val initialData = handleIntent(intent)

        // Immediately trigger background CSV rule pre-warming in parallel with Compose setContent initialization
        viewModel.loadTypesAsync(applicationContext, initialData.second)

        setContent {
            UaTranslitTheme {
                val context = LocalContext.current
                val appContext = context.applicationContext
                LaunchedEffect(Unit) {
                    val activity = context as ComponentActivity
                    viewModel.uiEvent.collect { event ->
                        if (activity.intent.getBooleanExtra(EXTRA_SUPPRESS_SAVE_FEEDBACK, false)) {
                            return@collect
                        }
                        val message = when (event) {
                            is HomeViewModel.UiEvent.SaveSuccess -> appContext.getString(R.string.saved_success)
                            is HomeViewModel.UiEvent.AlreadyExists -> appContext.getString(R.string.already_exists)
                        }
                        Toast.makeText(appContext, message, Toast.LENGTH_SHORT).show()
                    }
                }
                var currentScreen by rememberSaveable { mutableStateOf("home") }
                var activeText by rememberSaveable { mutableStateOf(initialData.first) }
                var activeTypeName by rememberSaveable { mutableStateOf("") }

                BackHandler(enabled = currentScreen != "home") {
                    currentScreen = "home"
                }

                if (currentScreen == "home") {
                    HomeView(
                        viewModel = viewModel,
                        onNavigateToHistory = { currentScreen = "history" },
                        initialText = activeText,
                        initialTypeName = activeTypeName,
                        initialFeature = initialData.second,
                        onTextChange = { activeText = it },
                        onTypeChange = { activeTypeName = it.name }
                    )
                } else {
                    HistoryView(
                        viewModel = viewModel,
                        onBack = { currentScreen = "home" },
                        onReRun = { text, typeName ->
                            activeText = text
                            activeTypeName = typeName
                            currentScreen = "home"
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Handle new intent data if needed
        val intentData = handleIntent(intent)
        Log.d(
            TAG,
            "New intent received with text: ${intentData.first}, feature: ${intentData.second}"
        )
    }

    private fun handleIntent(intent: Intent): Pair<String, String> {
        var initialText = ""
        var feature = ""

        when (intent.action) {
            Intent.ACTION_SEND -> {
                // Handle shared text
                if (intent.type == "text/plain") {
                    initialText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
                    Log.d(TAG, "Received shared text: $initialText")
                }
            }
            Intent.ACTION_VIEW -> {
                // Handle deep links from App Actions
                val data: Uri? = intent.data
                data?.let { uri ->
                    Log.d(TAG, "Received deep link: $uri")
                    when (uri.host) {
                        "open" -> {
                            feature = uri.getQueryParameter("feature") ?: ""
                            Log.d(TAG, "Open feature: $feature")
                        }
                        "transliterate" -> {
                            initialText = uri.getQueryParameter("text") ?: ""
                            feature = "transliterate"
                            Log.d(TAG, "Transliterate text: $initialText")
                        }
                        else -> {
                            Log.d(TAG, "Unknown deep link host: ${uri.host}")
                        }
                    }
                }
            }
            else -> {
                Log.d(TAG, "Unhandled intent action: ${intent.action}")
            }
        }

        return Pair(initialText, feature)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeView(
    viewModel: HomeViewModel,
    onNavigateToHistory: () -> Unit,
    initialText: String,
    initialTypeName: String = "",
    initialFeature: String,
    onTextChange: (String) -> Unit = {},
    onTypeChange: (TransformType) -> Unit = {}
) {
    val context = LocalContext.current
    LaunchedEffect(context, initialTypeName) {
        viewModel.loadTypesAsync(context, initialTypeName)
    }
    LaunchedEffect(initialText) {
        if (initialText.isNotEmpty()) {
            viewModel.updateInput(initialText)
        }
    }
    val types by viewModel.typesState.collectAsState()
    val outputText by viewModel.outputText.collectAsState()

    ReportDrawnWhen { types.isNotEmpty() }
    HomeContent(
        types = types,
        initialText = initialText,
        initialTypeName = initialTypeName,
        initialFeature = initialFeature,
        outputText = outputText,
        onNavigateToHistory = onNavigateToHistory,
        onSaveToHistory = { input, output, type -> viewModel.saveToHistory(input, output, type) },
        onShare = { text -> share(text, context) },
        onTextChange = { text ->
            viewModel.updateInput(text)
            onTextChange(text)
        },
        onTypeChange = { type ->
            viewModel.updateSelectedType(type)
            onTypeChange(type)
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeContent(
    types: List<TransformType>,
    initialText: String,
    initialTypeName: String = "",
    initialFeature: String,
    outputText: String,
    onNavigateToHistory: () -> Unit,
    onSaveToHistory: (String, String, TransformType) -> Unit,
    onShare: (String) -> Unit,
    onTextChange: (String) -> Unit = {},
    onTypeChange: (TransformType) -> Unit = {}
) {
    val context = LocalContext.current
    val clipboardCopiedMessage = stringResource(R.string.clipboard_copied)
    var inputText by rememberSaveable { mutableStateOf(initialText) }
    var expanded by remember { mutableStateOf(false) }
    var selectedItem by remember(types, initialTypeName) {
        mutableStateOf(
            if (types.isNotEmpty()) {
                types.find { it.name == initialTypeName } ?: types.first()
            } else null
        )
    }

    LaunchedEffect(types, initialTypeName) {
        if (types.isNotEmpty()) {
            val item = if (initialTypeName.isNotEmpty()) {
                types.find { it.name == initialTypeName } ?: types.first()
            } else {
                selectedItem ?: types.first()
            }
            selectedItem = item
            onTypeChange(item)
        }
    }

    LaunchedEffect(initialText) {
        if (inputText != initialText) {
            inputText = initialText
            onTextChange(initialText)
        }
    }

    // Log initial feature for debugging App Actions
    if (initialFeature.isNotEmpty()) {
        Log.d("HomeView", "Initial feature requested: $initialFeature")
    }

    Surface(color = MaterialTheme.colorScheme.background) {
        Scaffold(
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                    title = {
                        Text(
                            text = stringResource(id = R.string.app_name),
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.semantics { heading() }
                        )
                    },
                    actions = {
                        IconButton(onClick = { onShare(outputText) }) {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = stringResource(R.string.menu_share)
                            )
                        }
                        IconButton(onClick = onNavigateToHistory) {
                            Icon(
                                Icons.Default.History,
                                contentDescription = stringResource(R.string.cd_open_history)
                            )
                        }
                    }
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(all = 16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextField(
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                            .testTag("selector")
                            .semantics {
                                role = Role.DropdownList
                            },
                        readOnly = true,
                        value = selectedItem?.name ?: stringResource(id = R.string.type_select),
                        onValueChange = {},
                        label = { Text(text = stringResource(id = R.string.type_select)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        shape = RoundedCornerShape(16.dp),
                        colors = ExposedDropdownMenuDefaults.textFieldColors(
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent
                        )
                    )
                    if (expanded) {
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            types.forEach { item ->
                                DropdownMenuItem(
                                    text = { Text(text = item.name) },
                                    onClick = {
                                        selectedItem = item
                                        onTypeChange(item)
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                TranslitTextField(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input"),
                    value = inputText,
                    onValueChange = {
                        inputText = it
                        onTextChange(it)
                    },
                    label = stringResource(id = R.string.input_cyrillic),
                    trailingIcon = {
                        if (inputText.isNotEmpty()) {
                            IconButton(onClick = {
                                inputText = ""
                                onTextChange("")
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = stringResource(R.string.cd_clear_input)
                                )
                            }
                        }
                    }
                )

                TranslitTextField(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("output"),
                    value = outputText,
                    onValueChange = {},
                    label = stringResource(id = R.string.input_latin),
                    readOnly = true,
                    trailingIcon = {
                        Row {
                            if (outputText.isNotEmpty()) {
                                IconButton(onClick = {
                                    val clipboard =
                                        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("translit", outputText)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(
                                        context,
                                        clipboardCopiedMessage,
                                        Toast.LENGTH_SHORT
                                    ).show()

                                }) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = stringResource(R.string.cd_copy_output)
                                    )
                                }
                            }
                            IconButton(onClick = {
                                if (inputText.isNotEmpty() && selectedItem != null) {
                                    onSaveToHistory(inputText, outputText, selectedItem!!)
                                }
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = stringResource(R.string.save_to_history)
                                )
                            }
                        }
                    }
                )

                if (!selectedItem?.tip.isNullOrEmpty()) {
                    val primaryColor = MaterialTheme.colorScheme.primary
                    val annotatedTip = remember(selectedItem?.tip, primaryColor) {
                        HtmlCompat.fromHtml(
                            selectedItem!!.tip,
                            HtmlCompat.FROM_HTML_MODE_COMPACT
                        ).toAnnotatedString(linkColor = primaryColor)
                    }

                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = stringResource(R.string.cd_info_tip),
                                tint = primaryColor,
                                modifier = Modifier.padding(end = 12.dp)
                            )
                            Text(
                                text = annotatedTip,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryView(
    viewModel: HomeViewModel,
    onBack: () -> Unit,
    onReRun: (String, String) -> Unit
) {
    val history by viewModel.history.collectAsState()

    HistoryContent(
        history = history,
        onBack = onBack,
        onReRun = onReRun,
        onClearHistory = { viewModel.clearHistory() },
        onDeleteItem = { id -> viewModel.deleteFromHistory(id) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryContent(
    history: List<TransliterationHistory>?,
    onBack: () -> Unit,
    onReRun: (String, String) -> Unit,
    onClearHistory: () -> Unit,
    onDeleteItem: (Long) -> Unit
) {
    val context = LocalContext.current
    val clipboardCopiedMessage = stringResource(R.string.clipboard_copied)
    var showClearDialog by remember { mutableStateOf(false) }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(text = stringResource(R.string.confirm_clear_history_title)) },
            text = { Text(text = stringResource(R.string.confirm_clear_history_message)) },
            confirmButton = {
                TextButton(onClick = {
                    onClearHistory()
                    showClearDialog = false
                }) {
                    Text(text = stringResource(R.string.confirm_clear_history_positive))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(text = stringResource(R.string.confirm_clear_history_negative))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                title = {
                    Text(
                        text = stringResource(R.string.history_title),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.semantics { heading() }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_navigate_up)
                        )
                    }
                },
                actions = {
                    if (!history.isNullOrEmpty()) {
                        IconButton(onClick = { showClearDialog = true }) {
                            Icon(
                                Icons.Default.CleaningServices,
                                contentDescription = stringResource(R.string.cd_clear_history_all)
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        if (history == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
        } else if (history.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp)
                    .testTag("history_empty"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = null,
                    modifier = Modifier
                        .size(64.dp)
                        .padding(bottom = 16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Text(
                    text = stringResource(R.string.no_history_found),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .testTag("history_list"),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(history, key = { it.id }) { item ->
                    OutlinedCard(
                        onClick = {
                            onReRun(item.inputText, item.transformType)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 8.dp),
                                horizontalAlignment = Alignment.Start
                            ) {
                                Text(
                                    text = item.outputText,
                                    modifier = Modifier.fillMaxWidth(),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Start,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = item.inputText,
                                    modifier = Modifier.fillMaxWidth(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Start,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (item.transformType.isNotEmpty()) {
                                    Text(
                                        text = item.transformType,
                                        modifier = Modifier.fillMaxWidth(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        textAlign = TextAlign.Start,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = {
                                        val clipboard =
                                            context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip =
                                            ClipData.newPlainText("translit", item.outputText)
                                        clipboard.setPrimaryClip(clip)
                                        Toast.makeText(
                                            context,
                                            clipboardCopiedMessage,
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                ) {
                                    Icon(
                                        Icons.Default.ContentCopy,
                                        contentDescription = stringResource(R.string.menu_copy),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(
                                    onClick = { onDeleteItem(item.id) }
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = stringResource(R.string.cd_history_delete),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun share(text: String, context: Context) {
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        putExtra(Intent.EXTRA_TEXT, text)
        type = "text/plain"
    }
    val shareIntent = Intent.createChooser(sendIntent, null)
    context.startActivity(shareIntent)
}

@Preview(name = "Light Mode", showBackground = true, locale = "uk")
@Preview(name = "Dark Mode", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, locale = "uk")
@Composable
fun PreviewHomeView() {
    UaTranslitTheme {
        val context = LocalContext.current
        val types = remember(context) { TransformTypes.types(context).toList() }
        HomeContent(
            types = types,
            initialText = "Привіт",
            initialFeature = "",
            outputText = if (types.isNotEmpty()) WordTransformation.transform("Привіт", types.first()) else "",
            onNavigateToHistory = {},
            onSaveToHistory = { _, _, _ -> },
            onShare = {}
        )
    }
}

@Preview(name = "Light Mode", showBackground = true, locale = "uk")
@Preview(name = "Dark Mode", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, locale = "uk")
@Composable
fun PreviewHistoryView() {
    UaTranslitTheme {
        HistoryContent(
            history = listOf(
                TransliterationHistory(1, "Привіт", "Hryvit", "Standard"),
                TransliterationHistory(2, "Світ", "Svit", "Standard")
            ),
            onBack = {},
            onReRun = { _, _ -> },
            onClearHistory = {},
            onDeleteItem = {}
        )
    }
}