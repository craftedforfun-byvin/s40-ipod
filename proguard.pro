# ProGuard is used here purely as a bytecode downgrader + J2ME preverifier.
# -microedition writes the CLDC StackMap attribute that the phone's VM checks,
# and -target 1.2 rewrites the class file version to 46 so a CLDC 1.1 VM will
# load it at all. We deliberately do not shrink or obfuscate: the app is small
# and a mangled stack trace on a feature phone is no fun to debug.

-microedition
-target 1.2

-dontshrink
-dontoptimize
-dontobfuscate
-dontnote
-dontwarn

-keep public class ipod.IPodMIDlet {
    public <init>();
    protected void startApp();
    protected void pauseApp();
    protected void destroyApp(boolean);
}

-keep class ipod.** { *; }
