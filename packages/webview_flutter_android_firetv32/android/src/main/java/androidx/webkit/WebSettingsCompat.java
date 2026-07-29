package androidx.webkit;

import android.webkit.WebSettings;

/** Fire OS 5 fallback for optional WebKit settings. */
public final class WebSettingsCompat {
  private WebSettingsCompat() {}

  public static void setPaymentRequestEnabled(WebSettings settings, boolean enabled) {
    // Payment Request is not available in the Fire OS 5 WebView.
  }
}
