# ==========================================
# FIRESTORE - CRÍTICO
# ==========================================

# Mantener todas las clases de modelo/DTO
-keep class com.asociacionciguena.app.domain.model.** { *; }
-keep class com.asociacionciguena.app.data.dto.** { *; }

# Firestore necesita constructores vacíos y getters/setters
-keepclassmembers class com.asociacionciguena.app.domain.model.** {
    <init>();
    public <fields>;
    public <methods>;
}

-keepclassmembers class com.asociacionciguena.app.data.dto.** {
    <init>();
    public <fields>;
    public <methods>;
}

# Firebase Firestore
-keep class com.google.firebase.firestore.** { *; }
-keep class com.google.firebase.Timestamp { *; }
-keepclassmembers class * {
    @com.google.firebase.firestore.PropertyName <methods>;
    @com.google.firebase.firestore.ServerTimestamp <methods>;
}

# Firestore Document conversion
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses

# Firebase general
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# ==========================================
# KOTLIN DATA CLASSES
# ==========================================

-keepclassmembers class kotlin.Metadata {
    public <methods>;
}

# Mantener data classes
-keep @kotlin.Metadata class * { *; }

# ==========================================
# KOTLINX DATETIME
# ==========================================

-keep class kotlinx.datetime.** { *; }
-keepclassmembers class kotlinx.datetime.** {
    <init>(...);
    public <fields>;
    public <methods>;
}
-dontwarn kotlinx.datetime.**

# ==========================================
# HILT / DAGGER
# ==========================================

-keep class dagger.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

-keepclasseswithmembers class * {
    @dagger.* <methods>;
}

-keepclasseswithmembers class * {
    @javax.inject.* <methods>;
}

-keep @dagger.hilt.android.lifecycle.HiltViewModel class * {
    <init>(...);
}

# ==========================================
# COROUTINES
# ==========================================

-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

-dontwarn kotlinx.coroutines.**

# ==========================================
# VIEWMODELS
# ==========================================

-keep class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

-keep class * extends androidx.lifecycle.AndroidViewModel {
    <init>(...);
}

# ==========================================
# COMPOSE
# ==========================================

-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# ==========================================
# COIL (imágenes)
# ==========================================

-keep class coil.** { *; }
-dontwarn coil.**

# ==========================================
# RETROFIT / OKHTTP (si usas)
# ==========================================

-dontwarn okhttp3.**
-dontwarn okio.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# ==========================================
# SERIALIZACIÓN
# ==========================================

-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# ==========================================
# R8 FULL MODE
# ==========================================

-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile