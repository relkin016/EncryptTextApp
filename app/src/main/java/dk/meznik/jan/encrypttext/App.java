package dk.meznik.jan.encrypttext;

import android.app.Application;

import com.google.android.material.color.DynamicColors;

public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // Android 12+: цвета из обоев (Material You)
        DynamicColors.applyToActivitiesIfAvailable(this);
    }
}
