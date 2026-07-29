package androidx.webkit;

/** Reports AndroidX-only WebView features as unavailable on Fire OS 5. */
public class WebViewFeature {
  public static boolean isFeatureSupported(String feature) {
    return false;
  }
}
