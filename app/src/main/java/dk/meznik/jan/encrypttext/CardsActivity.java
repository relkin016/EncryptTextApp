package dk.meznik.jan.encrypttext;

import android.os.Bundle;
import android.text.Editable;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.NestedScrollView;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.json.JSONException;

import java.util.List;

import dk.meznik.jan.encrypttext.util.AuthGate;
import dk.meznik.jan.encrypttext.util.Card;
import dk.meznik.jan.encrypttext.util.Clipboard;
import dk.meznik.jan.encrypttext.util.PasswordVault;

/** Bank cards / accounts kept in {@link PasswordVault#cards}; each field can be copied. */
public class CardsActivity extends AppCompatActivity implements AuthGate.Listener {
    private static final String SECRET_MASK = "••••";

    private View root;
    private LinearLayout list;
    private TextView empty;

    private PasswordVault vault;
    private AuthGate authGate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        // No screenshots or recents thumbnail while card details may be on screen.
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        setContentView(R.layout.activity_cards);

        root = findViewById(R.id.root);
        list = findViewById(R.id.list);
        empty = findViewById(R.id.empty);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

        ExtendedFloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(v -> {
            if (AuthGate.checkAvailable(this)) editCard(null, new Card());
        });

        AppBarLayout appBar = findViewById(R.id.appBar);
        NestedScrollView scroll = findViewById(R.id.scroll);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            appBar.setPadding(bars.left, bars.top, bars.right, 0);
            scroll.setPadding(bars.left, 0, bars.right, bars.bottom);
            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) fab.getLayoutParams();
            int margin = getResources().getDimensionPixelSize(R.dimen.fab_margin);
            lp.setMargins(margin, margin, margin + bars.right, margin + bars.bottom);
            fab.setLayoutParams(lp);
            return WindowInsetsCompat.CONSUMED;
        });

        vault = PasswordVault.cards(this);
        authGate = new AuthGate(this, getString(R.string.error_auth_weak), this);
        refresh();
    }

    private void refresh() {
        list.removeAllViews();
        List<PasswordVault.Entry> entries = vault.list();
        empty.setVisibility(entries.isEmpty() ? View.VISIBLE : View.GONE);
        for (PasswordVault.Entry entry : entries) {
            View item = getLayoutInflater().inflate(R.layout.item_card, list, false);
            ((TextView) item.findViewById(R.id.name)).setText(entry.name);
            item.setOnClickListener(v -> openCard(entry));
            list.addView(item);
        }
    }

    private void snackbar(CharSequence message) {
        Snackbar.make(root, message, Snackbar.LENGTH_LONG).show();
    }

    private static String text(TextView view) {
        Editable editable = view.getEditableText();
        return editable != null ? editable.toString().trim() : view.getText().toString().trim();
    }

    private void openCard(PasswordVault.Entry entry) {
        if (!AuthGate.checkAvailable(this)) return;
        authGate.run(getString(R.string.auth_open_card_title), entry.name, () -> vault.decryptCipher(entry), cipher -> {
            Card card;
            try {
                card = Card.fromJson(vault.reveal(entry, cipher));
            } catch (JSONException e) {
                snackbar(getString(R.string.error_card_read));
                return;
            }
            showCard(entry.name, card);
        });
    }

    private void showCard(String name, Card card) {
        View view = getLayoutInflater().inflate(R.layout.dialog_card_view, null);
        LinearLayout fields = view.findViewById(R.id.fields);
        addField(fields, R.string.card_number, Card.groupNumber(card.number), card.number, false);
        addField(fields, R.string.card_holder, card.holder, card.holder, false);
        addField(fields, R.string.card_expiry, card.expiry, card.expiry, false);
        addField(fields, R.string.card_cvv, card.cvv, card.cvv, true);
        addField(fields, R.string.card_pin, card.pin, card.pin, true);
        addField(fields, R.string.card_account, card.account, card.account.replace(" ", ""), false);

        new MaterialAlertDialogBuilder(this)
                .setTitle(name)
                .setView(view)
                .setPositiveButton(R.string.close, null)
                .setNeutralButton(R.string.edit, (dialog, which) -> editCard(name, card))
                .setNegativeButton(R.string.delete, (dialog, which) -> confirmDelete(name))
                .show();
    }

    /** Secret values are masked until "show" is tapped; copying works either way. */
    private void addField(LinearLayout fields, int label, String shown, String copied, boolean secret) {
        if (copied.isEmpty()) return;
        View row = getLayoutInflater().inflate(R.layout.item_card_field, fields, false);
        ((TextView) row.findViewById(R.id.label)).setText(label);
        TextView value = row.findViewById(R.id.value);
        value.setText(secret ? SECRET_MASK : shown);
        if (secret) {
            MaterialButton reveal = row.findViewById(R.id.reveal);
            reveal.setVisibility(View.VISIBLE);
            reveal.setOnClickListener(v -> {
                value.setText(shown);
                reveal.setVisibility(View.GONE);
            });
        }
        row.findViewById(R.id.copy).setOnClickListener(v ->
                Clipboard.copy(this, getString(label), copied, true));
        fields.addView(row);
    }

    /** oldName == null adds a new card. */
    private void editCard(String oldName, Card card) {
        View view = getLayoutInflater().inflate(R.layout.dialog_card_edit, null);
        TextInputEditText nameText = view.findViewById(R.id.name);
        TextInputEditText numberText = view.findViewById(R.id.number);
        TextInputEditText holderText = view.findViewById(R.id.holder);
        TextInputEditText expiryText = view.findViewById(R.id.expiry);
        TextInputEditText cvvText = view.findViewById(R.id.cvv);
        TextInputEditText pinText = view.findViewById(R.id.pin);
        TextInputEditText accountText = view.findViewById(R.id.account);
        TextInputLayout numberLayout = view.findViewById(R.id.numberLayout);
        TextInputLayout expiryLayout = view.findViewById(R.id.expiryLayout);
        TextInputLayout cvvLayout = view.findViewById(R.id.cvvLayout);
        TextInputLayout pinLayout = view.findViewById(R.id.pinLayout);
        TextInputLayout accountLayout = view.findViewById(R.id.accountLayout);

        nameText.setText(oldName);
        numberText.setText(Card.groupNumber(card.number));
        holderText.setText(card.holder);
        expiryText.setText(card.expiry);
        cvvText.setText(card.cvv);
        pinText.setText(card.pin);
        accountText.setText(card.account);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(oldName == null ? getString(R.string.new_card) : oldName)
                .setView(view)
                .setPositiveButton(R.string.save, null)
                .setNegativeButton(android.R.string.cancel, null)
                .show();
        // Set after show() so that a validation error keeps the dialog open.
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            Card edited = new Card();
            edited.number = Card.digits(text(numberText));
            edited.holder = text(holderText);
            edited.cvv = text(cvvText);
            edited.pin = text(pinText);
            edited.account = text(accountText);
            String expiry = text(expiryText);

            boolean ok = true;
            numberLayout.setError(null);
            expiryLayout.setError(null);
            cvvLayout.setError(null);
            pinLayout.setError(null);
            accountLayout.setError(null);
            if (edited.number.isEmpty() && edited.account.isEmpty()) {
                numberLayout.setError(getString(R.string.error_card_empty));
                accountLayout.setError(getString(R.string.error_card_empty));
                ok = false;
            } else if (!edited.number.isEmpty() && !Card.isValidNumber(edited.number)) {
                numberLayout.setError(getString(R.string.error_card_number));
                ok = false;
            }
            if (!expiry.isEmpty()) {
                edited.expiry = Card.normalizeExpiry(expiry);
                if (edited.expiry == null) {
                    expiryLayout.setError(getString(R.string.error_card_expiry));
                    ok = false;
                }
            }
            if (!edited.cvv.isEmpty() && !edited.cvv.matches("\\d{3,4}")) {
                cvvLayout.setError(getString(R.string.error_card_cvv));
                ok = false;
            }
            if (!edited.pin.isEmpty() && !edited.pin.matches("\\d{4,12}")) {
                pinLayout.setError(getString(R.string.error_card_pin));
                ok = false;
            }
            if (!ok) return;

            String name = text(nameText);
            if (name.isEmpty()) {
                name = edited.number.length() >= 4
                        ? getString(R.string.default_card_name_number, edited.number.substring(edited.number.length() - 4))
                        : getString(R.string.default_card_name, vault.list().size() + 1);
            }
            String label = name;
            // The dialog stays open if authentication is cancelled, so nothing typed is lost.
            authGate.run(getString(R.string.auth_save_card_title), label, vault::encryptCipher, cipher -> {
                vault.replace(oldName, label, edited.toJson(), cipher);
                dialog.dismiss();
                refresh();
                snackbar(getString(R.string.card_saved, label));
            });
        });
    }

    private void confirmDelete(String name) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.delete_password_title, name))
                .setMessage(R.string.delete_card_message)
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    vault.remove(name);
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    @Override
    public void onKeyInvalidated() {
        AuthGate.showKeyInvalidated(this);
        refresh();
    }

    @Override
    public void onAuthError(CharSequence message) {
        snackbar(message);
    }
}
