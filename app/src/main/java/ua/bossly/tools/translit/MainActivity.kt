package ua.bossly.tools.translit

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import ua.bossly.tools.translit.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity(), AdapterView.OnItemSelectedListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var types: Array<TransformType>
    private lateinit var transliterationType: TransformType

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        setupViews()

        when (intent.action) {
            Intent.ACTION_SEND -> {
                intent.getStringExtra(Intent.EXTRA_TEXT)?.let { text ->
                    binding.inputField.setText(text)
                }
            }
        }
    }

    override fun onNothingSelected(adapterView: AdapterView<*>?) {}

    override fun onItemSelected(adapterView: AdapterView<*>?, view: View?, position: Int, itemId: Long) {
        transliterationType = types[position]
        binding.tipText.htmlSpan(transliterationType.tip)
        transliterate()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.copyButton -> copyToClipboard()
            R.id.shareButton -> openShareSheet()
        }

        return super.onOptionsItemSelected(item)
    }

    private fun copyToClipboard() {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val text = binding.inputField.text.toString()
        val converted = WordTransformation.transform(text, transliterationType)
        val clip = ClipData.newPlainText(getString(R.string.app_name), converted)
        clipboard.setPrimaryClip(clip)

        Toast.makeText(this, R.string.clipboard_copied, Toast.LENGTH_SHORT).show()
    }

    private fun openShareSheet() {
        val sendIntent: Intent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, binding.outputField.text.toString())
            type = "text/plain"
        }

        val shareIntent = Intent.createChooser(sendIntent, null)
        startActivity(shareIntent)
    }

    private fun setupViews() {
        binding.inputField.addTextChangedListener {
            transliterate()
        }

        types = TransformTypes.types(this)
        transliterationType = types.first()
        val arItems = types.map { a -> a.name }
        val spinnerAdapter: ArrayAdapter<Any?> = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item, android.R.id.text1, arItems
        )
        binding.selector.adapter = spinnerAdapter
        binding.selector.onItemSelectedListener = this
    }

    private fun transliterate() {
        val text = binding.inputField.text.toString()
        val converted = WordTransformation.transform(text, transliterationType)
        binding.outputField.setText(converted)
        binding.countText.text = "${converted.length}"
    }
}