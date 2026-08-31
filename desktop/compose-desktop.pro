# Room discovers the generated database implementation by its unmodified class name.
-keep class com.sysadmindoc.billminder4pc.data.BillDatabase_Impl { *; }

# The release smoke entry point exercises the optimized database runtime before MSI creation.
-keep class com.sysadmindoc.billminder4pc.data.ReleasePackageSmokeKt {
    public static void main(java.lang.String[]);
}

# The bundled SQLite library binds these JVM methods by exact native names and signatures.
-keep class androidx.sqlite.driver.bundled.** { *; }
