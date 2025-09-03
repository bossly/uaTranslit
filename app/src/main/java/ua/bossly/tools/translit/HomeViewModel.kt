package ua.bossly.tools.translit

import android.content.Context
import androidx.lifecycle.ViewModel

class HomeViewModel : ViewModel() {
    fun types(context: Context): Array<TransformType> {
        return TransformTypes.types(context = context)
    }
    
    fun transliterate(text: String, transformType: TransformType): String {
        return WordTransformation.transform(text, transformType)
    }
}
