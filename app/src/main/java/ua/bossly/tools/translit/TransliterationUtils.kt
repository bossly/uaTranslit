package ua.bossly.tools.translit

import com.github.doyaaaaaken.kotlincsv.dsl.csvReader

/**
 * Created on 05.09.2020.
 * Copyright by oleg
 */
enum class TransliterationType {
    PASSPORT_2010, GEOGRAPHIC_1996, AMERICAN_1965
}

object TransliterationUtils {
    const val csvData: String = "" +
            "а,б,в,г,ґ,д,е,є,ж,з,и,і,ї,й,к,л,м,н,о,п,р,с,т,у,ф,х,ц,ч,ш,щ,ю,я,ь\n" +
            "a,b,v,h,g,d,e,ie,zh,z,y,i,i,i,k,l,m,n,o,p,r,s,t,u,f,kh,ts,ch,sh,shch,іu,ia,\n" + // Password
            "a,b,v,h,g,d,e,ie,zh,z,y,i,i,i,k,l,m,n,o,p,r,s,t,u,f,kh,ts,ch,sh,sch,іu,ia,\n" + // Geographic
            "a,b,v,h,g,d,e,ye,zh,z,y,i,yi,y,k,l,m,n,o,p,r,s,t,u,f,kh,ts,ch,sh,shch,yu,ya,'\n"  // American

    private val rows: List<List<String>> = csvReader().readAll(csvData)

    fun convert(text: String): String {
        var converted = ""

        text.toLowerCase().forEach {
            val columns = rows[0]
            for (column in columns.indices) {
                if (rows[0][column] == it.toString()) {
                    converted += rows[1][column]
                    break
                }
            }
        }

        return converted
    }
}