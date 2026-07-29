package androidx.webkit;

/** Minimal source-compatible type for the optional AndroidX WebKit callback. */
public abstract class WebResourceErrorCompat {
  public abstract int getErrorCode();

  public abstract CharSequence getDescription();
}
