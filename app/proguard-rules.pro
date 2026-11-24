# Add project specific ProGuard rules here.

# Argon2 (contraseñas): la librería nativa busca estas clases por nombre.
-keep class com.lambdapioneer.argon2kt.** { *; }
# TensorFlow Lite (detector de objetos y clasificador de monedas).
-keep class org.tensorflow.lite.** { *; }
-dontwarn org.tensorflow.lite.**

# SmolVLM: la librería nativa busca nativeInit, nativeDescribe y nativeFree por nombre.
-keep class com.cecapi.app.feature.modulo7_entorno.SmolVlmLocal { *; }
-dontwarn com.cecapi.app.feature.modulo7_entorno.SmolVlmLocal
