# ReelVoice Proguard Rules
-keepattributes *Annotation*
-keepclassmembers class * {
    @org.jetbrains.annotations.* <methods>;
}
