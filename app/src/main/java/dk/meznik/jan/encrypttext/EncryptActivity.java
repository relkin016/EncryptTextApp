package dk.meznik.jan.encrypttext;

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.text.Editable;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.NestedScrollView;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.List;

import dk.meznik.jan.encrypttext.util.AuthGate;
import dk.meznik.jan.encrypttext.util.Encryption;
import dk.meznik.jan.encrypttext.util.PasswordVault;

public class EncryptActivity extends AppCompatActivity implements AuthGate.Listener {
    private static final String STATE_RESULT = "result";
    private static final String STATE_RESULT_PLAIN = "result_plain";

    private View root;
    private NestedScrollView scroll;
    private TextInputLayout passwordLayout;
    private TextInputEditText passwordText;
    private TextInputLayout inputLayout;
    private TextInputEditText inputText;
    private MaterialButton fillButton;
    private MaterialCardView resultCard;
    private TextView resultLabel;
    private TextView resultText;
    private MaterialButton replaceButton;

    private PasswordVault vault;
    private AuthGate authGate;
    private String result;
    // Decrypted text is marked as sensitive in the clipboard (hidden in the clipboard preview).
    private boolean resultIsPlain;
    private boolean canReplace;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_encrypt);

        root = findViewById(R.id.root);
        scroll = findViewById(R.id.scroll);
        passwordLayout = findViewById(R.id.passwordLayout);
        passwordText = findViewById(R.id.editTextPassword);
        inputLayout = findViewById(R.id.inputLayout);
        inputText = findViewById(R.id.editText1);
        fillButton = findViewById(R.id.buttonFill);
        resultCard = findViewById(R.id.resultCard);
        resultLabel = findViewById(R.id.resultLabel);
        resultText = findViewById(R.id.editText2);
        replaceButton = findViewById(R.id.buttonReplace);

        applyWindowInsets();

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_saved_passwords) {
                manageSavedPasswords();
                return true;
            } else if (item.getItemId() == R.id.action_about) {
                showAbout();
                return true;
            }
            return false;
        });

        vault = new PasswordVault(this);
        authGate = new AuthGate(this, getString(R.string.error_auth_weak), this);

        findViewById(R.id.buttonEncrypt).setOnClickListener(v -> process(true));
        findViewById(R.id.buttonDecrypt).setOnClickListener(v -> process(false));
        findViewById(R.id.buttonSave).setOnClickListener(v -> savePassword());
        fillButton.setOnClickListener(v -> fillPassword());
        findViewById(R.id.buttonCopy).setOnClickListener(v -> copyResult());
        findViewById(R.id.buttonShare).setOnClickListener(v -> shareResult());
        findViewById(R.id.buttonSwap).setOnClickListener(v -> useResultAsInput());
        replaceButton.setOnClickListener(v -> exitWithResult(result));

        // Set the input text depending on how the app was started
        Intent intent = getIntent();
        CharSequence text = null;
        if (Intent.ACTION_SEND.equals(intent.getAction())) {
            text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
        } else if (Intent.ACTION_PROCESS_TEXT.equals(intent.getAction())) {
            text = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT);
            canReplace = !intent.getBooleanExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, false);
        }

        if (savedInstanceState != null) {
            showResult(savedInstanceState.getString(STATE_RESULT),
                    savedInstanceState.getBoolean(STATE_RESULT_PLAIN), false);
        } else if (text != null) {
            inputText.setText(text);
            passwordText.requestFocus();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        fillButton.setEnabled(!vault.isEmpty());
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_RESULT, result);
        outState.putBoolean(STATE_RESULT_PLAIN, resultIsPlain);
    }

    private void applyWindowInsets() {
        AppBarLayout appBar = findViewById(R.id.appBar);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            Insets ime = windowInsets.getInsets(WindowInsetsCompat.Type.ime());
            appBar.setPadding(bars.left, bars.top, bars.right, 0);
            scroll.setPadding(bars.left, 0, bars.right, Math.max(bars.bottom, ime.bottom));
            return WindowInsetsCompat.CONSUMED;
        });
    }

    private static String text(TextView view) {
        Editable editable = view.getEditableText();
        return editable != null ? editable.toString() : view.getText().toString();
    }

    private void process(boolean encrypt) {
        String input = text(inputText);
        if (input.trim().isEmpty()) {
            inputLayout.setError(getString(R.string.error_empty_text));
            return;
        }
        inputLayout.setError(null);
        String password = text(passwordText);
        try {
            String output = encrypt ? Encryption.encrypt(password, input)
                    : Encryption.decrypt(password, input.trim());
            hideKeyboard();
            showResult(output, !encrypt, true);
        } catch (Exception ex) {
            if (encrypt) {
                snackbar(getString(R.string.error_encrypt, ex.getLocalizedMessage()));
            } else {
                inputLayout.setError(getString(R.string.error_decrypt));
            }
        }
    }

    private void showResult(String output, boolean plain, boolean scrollTo) {
        result = output;
        resultIsPlain = plain;
        if (output == null) {
            resultCard.setVisibility(View.GONE);
            return;
        }
        resultLabel.setText(plain ? R.string.result_decrypted : R.string.result_encrypted);
        resultText.setText(output);
        replaceButton.setVisibility(canReplace ? View.VISIBLE : View.GONE);
        resultCard.setVisibility(View.VISIBLE);
        if (scrollTo) {
            scroll.post(() -> scroll.smoothScrollTo(0, resultCard.getBottom()));
        }
    }

    private void hideKeyboard() {
        View focus = getCurrentFocus();
        if (focus != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(focus.getWindowToken(), 0);
            focus.clearFocus();
        }
    }

    private void snackbar(CharSequence message) {
        Snackbar.make(root, message, Snackbar.LENGTH_LONG).show();
    }

    public void exitWithResult(String data) {
        Intent result = new Intent();
        result.putExtra(Intent.EXTRA_PROCESS_TEXT, data);
        setResult(RESULT_OK, result);
        finish();
    }

    private void copyResult() {
        ClipboardManager clipboardManager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clipData = ClipData.newPlainText(getString(R.string.app_name), result);
        if (resultIsPlain && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            PersistableBundle extras = new PersistableBundle();
            extras.putBoolean(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                    ? ClipDescription.EXTRA_IS_SENSITIVE : "android.content.extra.IS_SENSITIVE", true);
            clipData.getDescription().setExtras(extras);
        }
        clipboardManager.setPrimaryClip(clipData);
        // Android 13+ shows its own confirmation
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show();
        }
    }

    private void shareResult() {
        Intent send = new Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, result);
        Intent chooser = Intent.createChooser(send, null);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            chooser.putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS,
                    new ComponentName[]{new ComponentName(this, EncryptActivity.class)});
        }
        startActivity(chooser);
    }

    private void useResultAsInput() {
        inputText.setText(result);
        inputLayout.setError(null);
        showResult(null, false, false);
        scroll.smoothScrollTo(0, 0);
    }

    // ---- Saved passwords ----

    private boolean checkLockScreen() {
        if (AuthGate.isAvailable(this)) return true;
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.no_lock_title)
                .setMessage(R.string.no_lock_message)
                .setPositiveButton(android.R.string.ok, null)
                .show();
        return false;
    }

    private void savePassword() {
        String password = text(passwordText);
        if (password.isEmpty()) {
            passwordLayout.setError(getString(R.string.error_empty_password));
            return;
        }
        passwordLayout.setError(null);
        if (!checkLockScreen()) return;

        View view = getLayoutInflater().inflate(R.layout.dialog_password_name, null);
        TextInputEditText nameText = view.findViewById(R.id.name);
        String defaultName = getString(R.string.default_password_name, vault.list().size() + 1);
        nameText.setText(defaultName);
        nameText.selectAll();
        nameText.requestFocus();

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.save_password_title)
                .setMessage(R.string.save_password_message)
                .setView(view)
                .setPositiveButton(R.string.save, (dialog, which) -> {
                    String name = text(nameText).trim();
                    String label = name.isEmpty() ? defaultName : name;
                    authGate.run(getString(R.string.auth_save_title), label, vault::encryptCipher, cipher -> {
                        vault.add(label, password, cipher);
                        fillButton.setEnabled(true);
                        snackbar(getString(R.string.password_saved, label));
                    });
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void fillPassword() {
        List<PasswordVault.Entry> entries = vault.list();
        if (entries.isEmpty()) return;
        if (!checkLockScreen()) return;
        if (entries.size() == 1) {
            revealPassword(entries.get(0));
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.choose_password)
                .setItems(names(entries), (dialog, which) -> revealPassword(entries.get(which)))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void revealPassword(PasswordVault.Entry entry) {
        authGate.run(getString(R.string.auth_fill_title), entry.name, () -> vault.decryptCipher(entry), cipher -> {
            String password = vault.reveal(entry, cipher);
            passwordText.setText(password);
            passwordText.setSelection(password.length());
            passwordLayout.setError(null);
        });
    }

    private void manageSavedPasswords() {
        List<PasswordVault.Entry> entries = vault.list();
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.saved_passwords)
                .setPositiveButton(R.string.close, null);
        if (entries.isEmpty()) {
            builder.setMessage(R.string.no_saved_passwords);
        } else {
            builder.setItems(names(entries), (dialog, which) -> confirmDelete(entries.get(which).name))
                    .setNeutralButton(R.string.delete_all, (dialog, which) -> confirmDelete(null));
        }
        builder.show();
    }

    /** name == null deletes everything. */
    private void confirmDelete(String name) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(name == null ? getString(R.string.delete_all_title)
                        : getString(R.string.delete_password_title, name))
                .setMessage(R.string.delete_password_message)
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    if (name == null) vault.clear();
                    else vault.remove(name);
                    fillButton.setEnabled(!vault.isEmpty());
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static String[] names(List<PasswordVault.Entry> entries) {
        String[] names = new String[entries.size()];
        for (int i = 0; i < names.length; i++) names[i] = entries.get(i).name;
        return names;
    }

    @Override
    public void onKeyInvalidated() {
        vault.clear();
        fillButton.setEnabled(false);
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.key_invalidated_title)
                .setMessage(R.string.key_invalidated_message)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    @Override
    public void onAuthError(CharSequence message) {
        snackbar(message);
    }

    private void showAbout() {
        String version;
        try {
            version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            version = "?";
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.about_title, getString(R.string.app_name), version))
                .setMessage(R.string.about_message)
                .setPositiveButton(R.string.close, null)
                .show();
    }
}
