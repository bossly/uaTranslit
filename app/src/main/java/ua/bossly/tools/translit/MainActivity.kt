package ua.bossly.tools.translit

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
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat.startActivity
import androidx.core.text.HtmlCompat
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
        
        // Handle App Actions and deep links
        val initialData = handleIntent(intent)
        
        setContent {
            UaTranslitTheme {
                HomeView(
                    initialText = initialData.first,
                    initialFeature = initialData.second
                )
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
    viewModel: HomeViewModel = HomeViewModel(),
    initialText: String = "",
    initialFeature: String = ""
) {
    val context = LocalContext.current
    var inputText by remember { mutableStateOf(initialText) }
    var outputText by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    var selectedItem by remember { mutableStateOf(viewModel.types(context).first()) }

    // Process initial text if provided
    if (initialText.isNotEmpty() && inputText.isEmpty()) {
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
                    title = {
                        Text(text = stringResource(id = R.string.app_name))
                    },
                    actions = {
                        IconButton(onClick = {
                            share(outputText, context)
                        }) {
                            Icon(
                                Icons.Default.Share, contentDescription = "Share",
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

                Box {
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
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        viewModel.types(context).forEach { item ->
                            DropdownMenuItem(text = { Text(text = item.name) }, onClick = {
                                selectedItem = item
                                expanded = false
                            })
                        }
                    }
                }
                OutlinedTextField(
                    value = inputText,
                    onValueChange = {
                        inputText = it
                        outputText =
                            WordTransformation.transform(it, selectedItem)
                    }, // Implement logic here
                    label = { Text(text = stringResource(id = R.string.input_cyrillic)) },
                    maxLines = Int.MAX_VALUE,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input")
                )
                Row(modifier = Modifier.fillMaxWidth()) {
                    Box {
                        OutlinedTextField(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("output"),
                            value = outputText,
                            onValueChange = {}, // Implement logic here
                            label = { Text(text = stringResource(id = R.string.input_latin)) },
                            maxLines = Int.MAX_VALUE,
                            readOnly = true
                        )
                        Text(
                            outputText.length.toString(),
                            Modifier
                                .align(Alignment.BottomEnd)
                                .padding(6.dp)
                        )
                    }
                }
                Text(
                    text = HtmlCompat.fromHtml(
                        SpannableStringBuilder(selectedItem.tip).toString(),
                        HtmlCompat.FROM_HTML_MODE_COMPACT
                    ).toAnnotatedString(),
                    style = TextStyle(color = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.padding(8.dp)
                )
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

@Preview
@Composable
fun PreviewHomeView() {
    UaTranslitTheme {
        HomeView()
    }
}