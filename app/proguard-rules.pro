# Preserve Room DB entities and DAOs
-keep class ua.bossly.tools.translit.data.** { *; }
-keepclassmembers class ua.bossly.tools.translit.data.** { *; }

# Preserve AppFunctions data models & entry points
-keep class ua.bossly.tools.translit.appfunctions.** { *; }

# Preserve Kotlin CSV parser classes
-keep class com.github.doyaaaaaken.kotlincsv.** { *; }

# Preserve line number information for crash logs
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile