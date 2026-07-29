package androidx.webkit;

import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/**
 * Platform-WebView fallback used on Fire OS 5.
 *
 * <p>The legacy URL callback inherited from {@link WebViewClient} remains
 * active. AndroidX-only error adaptation is optional and therefore a no-op.
 */
public class WebViewClientCompat extends WebViewClient {
  public void onReceivedError(
      WebView view, WebResourceRequest request, WebResourceErrorCompat error) {}
}
