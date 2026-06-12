# IronPulse R8 rules.
#
# Gson (de)serializes the model classes by reflection — R8 must neither strip
# nor rename their fields, or every saved JSON file silently loads empty.
-keep class com.ironpulse.model.** { *; }

# Gson uses generic type information stored in class signatures.
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod, InnerClasses

# TypeToken subclasses capture generics at runtime.
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# Gson internals reflect on java.lang.reflect.Type hierarchies.
-dontwarn sun.misc.Unsafe
