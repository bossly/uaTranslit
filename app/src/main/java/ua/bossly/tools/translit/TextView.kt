package ua.bossly.tools.translit

import android.content.Context
import android.os.Build
import android.text.Html
import android.text.Spanned
import android.util.AttributeSet
import android.util.Log
import androidx.appcompat.widget.AppCompatTextView

/**
 * Created on 08.09.2020.
 * Copyright by oleg
 */
class TextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatTextView(context, attrs, defStyleAttr) {

    init {
        context.theme.obtainStyledAttributes(
            attrs,
            R.styleable.TextView, 0, 0
        ).apply {

            try {
                getString(R.styleable.TextView_html)?.let {
                    text = htmlSpan(it)
                }
            } finally {
                recycle()
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun htmlSpan(html: String): Spanned? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT)
        } else {
            Html.fromHtml(html)
        }
    }

    override fun performClick(): Boolean {
        Log.d("TextView", "Clicked")
        return super.performClick()
    }
}
