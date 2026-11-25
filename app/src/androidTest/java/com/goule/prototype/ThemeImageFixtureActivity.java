package com.goule.prototype;

import android.app.Activity;
import android.os.Bundle;

/** Explicitly starts the disposable image-provider APK before the client imports fixtures. */
public final class ThemeImageFixtureActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        finish();
    }
}
