package ua.bossly.tools.translit

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.ShareActionProvider
import androidx.core.view.MenuItemCompat
import androidx.core.widget.addTextChangedListener
import ua.bossly.tools.translit.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity(), AdapterView.OnItemSelectedListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var types: Array<TransformType>
    private lateinit var transliterationType: TransformType

    private var shareProviderIntent: Intent? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

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
        val item = menu?.findItem(R.id.shareButton)
        val shareProvider = MenuItemCompat.getActionProvider(item) as ShareActionProvider

        Intent(Intent.ACTION_SEND).run {
            type = "text/plain"
            shareProviderIntent = this
            shareProvider.setShareIntent(this)
        }

        return super.onCreateOptionsMenu(menu)
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

        actionBar?.setDisplayShowTitleEnabled(false)
        actionBar?.setDisplayUseLogoEnabled(true)
    }

    private fun transliterate() {
        val text = binding.inputField.text.toString()
        val converted = WordTransformation.transform(text, transliterationType)
        binding.outputField.setText(converted)
        shareProviderIntent?.putExtra(Intent.EXTRA_TEXT, converted)
        binding.countText.text = "${converted.length}"
    }
}