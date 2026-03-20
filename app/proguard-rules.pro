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
# COMPOSE - BASE
# ==========================================

-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# ==========================================
# COIL (imágenes)
# ==========================================

-keep class coil.** { *; }
-dontwarn coil.**

# ==========================================
# RETROFIT / OKHTTP
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
# LIBRERÍAS EXTERNAS - IGNORE WARNINGS
# ==========================================

# ───────── JACKSON ─────────
-dontwarn com.fasterxml.jackson.**
-dontwarn com.fasterxml.jackson.annotation.**
-dontwarn com.fasterxml.jackson.core.**
-dontwarn com.fasterxml.jackson.databind.**

# ───────── ITEXT PDF ─────────
-dontwarn com.itextpdf.**
-dontwarn com.itextpdf.commons.utils.JsonUtil
-dontwarn com.itextpdf.barcodes.**
-dontwarn com.itextpdf.io.image.AwtImageDataFactory
-dontwarn com.itextpdf.kernel.pdf.xobject.PdfImageXObject

# ───────── JAVA AWT/IMAGEIO (no existen en Android) ─────────
-dontwarn java.awt.**
-dontwarn java.awt.image.**
-dontwarn javax.imageio.**
-dontwarn java.lang.reflect.AnnotatedType

# ───────── OKHTTP LEGACY ─────────
-dontwarn com.squareup.okhttp.**
-dontwarn io.grpc.okhttp.**

# ───────── GUAVA/REFLECTION ─────────
-dontwarn com.google.common.reflect.**
-dontwarn com.google.common.util.concurrent.**

# ───────── REGLA FINAL: Ignorar cualquier otra clase faltante ─────────
-dontwarn **

# ==========================================
# COMPOSE - TEXT INPUT (CRÍTICO PARA AbstractTextEvent)
# ==========================================

# Mantener clases generales de texto y UI
-keep class androidx.compose.ui.text.** { *; }
-keep class androidx.compose.foundation.text.** { *; }
-keep class androidx.compose.ui.input.** { *; }
-keep class androidx.compose.ui.node.** { *; }
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.compose.ui.layout.** { *; }

# Mantener clases específicas de input de texto
-keep class androidx.compose.ui.text.input.** { *; }
-keep class androidx.compose.foundation.text.input.** { *; }

# ───────── AbstractTextEvent y relacionados (EVITAR ERROR EN RUNTIME) ─────────
-keep class androidx.compose.ui.text.input.TextFieldValue { *; }
-keep class androidx.compose.ui.text.input.ImeAction { *; }
-keep class androidx.compose.ui.text.input.KeyboardOptions { *; }
-keep class androidx.compose.ui.text.input.KeyboardType { *; }
-keep class androidx.compose.ui.text.input.VisualTransformation { *; }
-keep class androidx.compose.ui.text.input.TextFieldState { *; }
-keepnames class * extends androidx.compose.ui.text.input.AbstractTextEvent
-keepnames class androidx.compose.ui.text.input.AbstractTextEvent
-keep class androidx.compose.foundation.text.CoreTextFieldKt { *; }
-keep class androidx.compose.foundation.text.BasicTextFieldKt { *; }
-keep class androidx.compose.foundation.text.TextFieldScrollLayoutModifierKt { *; }
-keep class androidx.compose.ui.text.input.ImeOptions { *; }
-keep class androidx.compose.ui.text.input.OffsetMapping { *; }
-keep class androidx.compose.ui.text.input.TransformedText { *; }

# Mantener annotations y atributos de Compose
-keepattributes *Annotation*, InnerClasses, EnclosingMethod, Signature
-keep class androidx.compose.** { *; }

# Evitar ofuscación de métodos @Composable
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
    @androidx.compose.ui.Modifier <fields>;
}