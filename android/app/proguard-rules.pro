# kotlinx.serialization, Retrofit, OkHttp, Coil and Navigation ship their own consumer rules.

# Keep the serializers of @Serializable classes (API models and navigation routes) resolvable by reflection-free lookup.
-keepclassmembers @kotlinx.serialization.Serializable class cz.gameshelf.app.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
