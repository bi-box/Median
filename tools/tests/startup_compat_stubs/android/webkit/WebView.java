package android.webkit;
import android.content.pm.PackageInfo;
public class WebView {
    public static volatile int loads;
    public static volatile RuntimeException loadFailure;
    public static volatile ClassLoader loader = WebView.class.getClassLoader();
    public static ClassLoader getWebViewClassLoader() {
        loads++;
        if (loadFailure != null) throw loadFailure;
        return loader;
    }
    public static PackageInfo getCurrentWebViewPackage() { return null; }
}
