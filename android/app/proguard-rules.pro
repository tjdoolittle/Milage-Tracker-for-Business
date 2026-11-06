# Keep file names and line numbers in crash stack traces (Play deobfuscates the rest
# with the mapping file bundled in the AAB).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
