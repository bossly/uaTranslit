package ua.bossly.tools.translit

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
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
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat.startActivity
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
fun Spanned.toAnnotatedString(): AnnotatedString = buildAnnotatedString {
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

            is ForegroundColorSpan -> addStyle(
                SpanStyle(color = Color(span.foregroundColor)),
                start,
                end
            )
        }
    }
}

class MainActivity : ComponentActivity() {
    companion object {
        private const val TAG = "MainActivity"
        const val EXTRA_INITIAL_TEXT = "initial_text"
        const val EXTRA_FEATURE = "feature"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize Database and Repository
        val database = AppDatabase.getDatabase(applicationContext)
        val repository = TransliterationRepository(database.transliterationDao())

        // ViewModel Factory
        class HomeViewModelFactory(private val repository: TransliterationRepository) :
            ViewModelProvider.Factory {
            @Suppress("UNCHECKCT_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
                    return HomeViewModel(repository) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class")
            }
        }

        val viewModel: HomeViewModel =
            ViewModelProvider(this, HomeViewModelFactory(repository))[HomeViewModel::class.java]

        // Handle App Actions and deep links
        val initialData = handleIntent(intent)

        setContent {
            UaTranslitTheme {
                val context = LocalContext.current
                LaunchedEffect(Unit) {
                    viewModel.uiEvent.collect { event ->
                        val message = when (event) {
                            is HomeViewModel.UiEvent.SaveSuccess -> context.getString(R.string.saved_success)
                            is HomeViewModel.UiEvent.AlreadyExists -> context.getString(R.string.already_exists)
                        }
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    }
                }
                var currentScreen by remember { mutableStateOf("home") }
                var activeText by remember { mutableStateOf(initialData.first) }

                if (currentScreen == "home") {
                    HomeView(
                        viewModel = viewModel,
                        onNavigateToHistory = { currentScreen = "history" },
                        initialText = activeText,
                        initialFeature = initialData.second
                    )
                } else {
                    HistoryView(
                        viewModel = viewModel,
                        onBack = { currentScreen = "home" },
                        onReRun = { text, _ ->
                            activeText = text
                            currentScreen = "home"
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let {
            setIntent(it)
            // Handle new intent data if needed
            val intentData = handleIntent(it)
            Log.d(TAG, "New intent received with text: ${intentData.first}, feature: ${intentData.second}")
        }
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
    initialFeature: String
) {
    val context = LocalContext.current
    val types = remember(context) { viewModel.types(context).toList() }
    HomeContent(
        types = types,
        initialText = initialText,
        initialFeature = initialFeature,
        onNavigateToHistory = onNavigateToHistory,
        onSaveToHistory = { input, output, type -> viewModel.saveToHistory(input, output, type) },
        onShare = { text -> share(text, context) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeContent(
    types: List<TransformType>,
    initialText: String,
    initialFeature: String,
    onNavigateToHistory: () -> Unit,
    onSaveToHistory: (String, String, TransformType) -> Unit,
    onShare: (String) -> Unit
) {
    var inputText by remember { mutableStateOf(initialText) }
    var outputText by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    var selectedItem by remember(types) { mutableStateOf(types.first()) }

    LaunchedEffect(initialText) {
        inputText = initialText
        outputText = WordTransformation.transform(initialText, selectedItem)
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
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    title = { Text(text = stringResource(id = R.string.app_name)) },
                    actions = {
                        IconButton(onClick = { onShare(outputText) }) {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = "Share",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                        IconButton(onClick = onNavigateToHistory) {
                            Icon(
                                Icons.Default.History,
                                contentDescription = "History",
                                tint = MaterialTheme.colorScheme.onPrimary
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
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = stringResource(id = R.string.type_select),
                    style = TextStyle(fontWeight = FontWeight.Bold),
                    modifier = Modifier.fillMaxWidth()
                )

                Box(modifier = Modifier.padding(vertical = 8.dp)) {
                    Text(
                        text = selectedItem.name,
                        color = MaterialTheme.colorScheme.onSecondary,
                        modifier = Modifier
                            .clickable { expanded = true }
                            .background(
                                MaterialTheme.colorScheme.secondary,
                                RoundedCornerShape(4.dp)
                            )
                            .padding(16.dp)
                            .fillMaxWidth()
                            .testTag("selector")
                    )
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        types.forEach { item ->
                            DropdownMenuItem(text = { Text(text = item.name) }, onClick = {
                                selectedItem = item
                                expanded = false
                            })
                        }
                    }
                }

                OutlinedTextField(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focusState ->
                            if (!focusState.isFocused && inputText.isNotEmpty()) {
                                onSaveToHistory(inputText, outputText, selectedItem)
                            }
                        },
                    value = inputText,
                    onValueChange = {
                        inputText = it
                        outputText = WordTransformation.transform(it, selectedItem)
                    },
                    label = { Text(text = stringResource(id = R.string.input_cyrillic)) },
                    maxLines = Int.MAX_VALUE
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = outputText,
                    onValueChange = {
                    },
                    label = { Text(text = stringResource(id = R.string.input_latin)) },
                    readOnly = true,
                    maxLines = Int.MAX_VALUE,
                    trailingIcon = {
                        IconButton(onClick = {
                            if (inputText.isNotEmpty()) {
                                onSaveToHistory(inputText, outputText, selectedItem)
                            }
                        }) {
                            Icon(Icons.Default.Star, contentDescription = "Save to history")
                        }
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = HtmlCompat.fromHtml(
                        SpannableStringBuilder(selectedItem.tip).toString(),
                        HtmlCompat.FROM_HTML_MODE_COMPACT
                    ).toAnnotatedString(),
                    style = TextStyle(color = MaterialTheme.colorScheme.primary)
                )
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
    val history by viewModel.history.collectAsState(initial = emptyList())

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
    history: List<TransliterationHistory>,
    onBack: () -> Unit,
    onReRun: (String, String) -> Unit,
    onClearHistory: () -> Unit,
    onDeleteItem: (Long) -> Unit
) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                title = { Text(text = stringResource(R.string.history_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { onClearHistory() }) {
                        Icon(
                            Icons.Default.CleaningServices,
                            contentDescription = "Clear all",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        if (history.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(text = stringResource(R.string.no_history_found))
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(history, key = { it.id }) { item ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 4.dp)
                            ) {
                                Text(
                                    text = item.outputText,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.secondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = item.inputText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = {
                                        val clipboard =
                                            context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip =
                                            ClipData.newPlainText("translit", item.outputText)
                                        clipboard.setPrimaryClip(clip)
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        Icons.Default.ContentCopy,
                                        contentDescription = "Copy",
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        onReRun(
                                            item.inputText,
                                            item.transformType
                                        )
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = "Re-run",
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                IconButton(
                                    onClick = { onDeleteItem(item.id) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Delete",
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.onSurface
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
    startActivity(context, shareIntent, null)
}

@Preview(showBackground = true, locale = "uk")
@Composable
fun PreviewHomeView() {
    UaTranslitTheme {
        val context = LocalContext.current
        val types = remember(context) { TransformTypes.types(context).toList() }
        HomeContent(
            types = types,
            initialText = "Привіт",
            initialFeature = "",
            onNavigateToHistory = {},
            onSaveToHistory = { _, _, _ -> },
            onShare = {}
        )
    }
}

@Preview(showBackground = true, locale = "uk")
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