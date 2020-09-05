package ua.bossly.tools.translit

import android.content.Context
import java.io.InputStream

/**
 * Created on 18.11.2020.
 * Copyright by oleg
 */
class TransformType(stream: InputStream) : FileTransliteration(stream) {
    var name: String = rows[0][1]
    var tip: String = rows[0][2]
}

object TransformTypes {
    fun types(context: Context): Array<TransformType> {
        return arrayOf(
            TransformType(
                context.resources.openRawResource(R.raw.passport_2010)
            ),
            TransformType(
                context.resources.openRawResource(R.raw.geographic_1996)
            ),
            TransformType(
                context.resources.openRawResource(R.raw.american_1965)
            ),
            TransformType(
                context.resources.openRawResource(R.raw.manifest)
            ),
        )
    }
}
