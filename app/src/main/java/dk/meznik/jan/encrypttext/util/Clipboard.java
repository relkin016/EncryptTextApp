package dk.meznik.jan.encrypttext.util;

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Build;
import android.os.PersistableBundle;
import android.widget.Toast;

import dk.meznik.jan.encrypttext.R;

public final class Clipboard {
    private Clipboard() {
    }

    /** Sensitive text is hidden in the clipboard preview and keyboard suggestions. */
    public static void copy(Context context, CharSequence label, String text, boolean sensitive) {
        ClipboardManager clipboardManager = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clipData = ClipData.newPlainText(label, text);
        if (sensitive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            PersistableBundle extras = new PersistableBundle();
            extras.putBoolean(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                    ? ClipDescription.EXTRA_IS_SENSITIVE : "android.content.extra.IS_SENSITIVE", true);
            clipData.getDescription().setExtras(extras);
        }
        clipboardManager.setPrimaryClip(clipData);
        // Android 13+ shows its own confirmation
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(context, R.string.copied, Toast.LENGTH_SHORT).show();
        }
    }
}
