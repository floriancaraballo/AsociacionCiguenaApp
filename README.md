# AsociacionCiguena

Proyecto Android desarrollado con Kotlin y Jetpack Compose.

## Requisitos

- Android Studio estable
- JDK 17
- Acceso al proyecto correspondiente de Firebase

## Configurar otro ordenador

1. Clonar el repositorio y abrirlo con Android Studio.
2. Dejar que Android Studio cree `local.properties` con la ruta local del SDK.
3. Copiar `MAPS_API_KEY` y `PLACES_API_KEY` desde `local.properties.example` y
   sustituir los valores de ejemplo por claves autorizadas para desarrollo.
4. Descargar `google-services.json` desde Firebase Console y guardarlo en
   `app/google-services.json`.
5. Sincronizar Gradle y compilar:

```powershell
.\gradlew.bat :app:assembleDebug
```

La compilacion debug no necesita el keystore de produccion.

## Firma de produccion

El keystore y sus contrasenas no deben almacenarse en Git. Para generar una
version release:

1. Transferir el archivo `.jks` mediante un gestor de secretos o almacenamiento
   privado.
2. Crear `app/keystore.properties` usando
   `app/keystore.properties.example` como plantilla.
3. Mantener ambos archivos fuera del repositorio.

El keystore original debe conservarse con copia de seguridad. Sin ese archivo
no se podran publicar actualizaciones de la misma aplicacion firmada.

## Archivos locales

No se versionan:

- `local.properties`
- `app/google-services.json`
- `app/keystore.properties`
- `*.jks`
- `.idea/`, `.gradle/` y directorios `build/`
- APK y metadatos generados en `app/release/`
