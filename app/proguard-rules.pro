# Add project specific ProGuard rules here.

# Argon2 (contraseñas): la librería nativa busca estas clases por nombre.
-keep class com.lambdapioneer.argon2kt.** { *; }
# TensorFlow Lite (detector de objetos).
-keep class org.tensorflow.lite.** { *; }
-dontwarn org.tensorflow.lite.**
