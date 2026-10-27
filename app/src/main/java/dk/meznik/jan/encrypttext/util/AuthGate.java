package dk.meznik.jan.encrypttext.util;

import android.content.Context;
import android.os.Build;
import android.security.keystore.KeyPermanentlyInvalidatedException;
import android.security.keystore.UserNotAuthenticatedException;

import androidx.annotation.NonNull;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.security.GeneralSecurityException;

import javax.crypto.Cipher;

import dk.meznik.jan.encrypttext.R;

import static androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG;
import static androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK;
import static androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL;

/**
 * Asks for a fingerprint (or face) or the screen lock PIN/pattern/password and then runs
 * a Keystore operation. Must be created in {@code onCreate()}.
 *
 * <p>Android 11+: the cipher is bound to this one authentication (CryptoObject).
 * Older versions don't support a CryptoObject together with the screen lock, so the key
 * stays unlocked for a few seconds after authentication and the cipher is created then.
 */
public class AuthGate {
    public interface CipherFactory {
        Cipher create() throws GeneralSecurityException;
    }

    public interface CipherAction {
        void run(Cipher cipher) throws GeneralSecurityException;
    }

    public interface Listener {
        /** The key is gone (screen lock removed, new fingerprint enrolled…): saved data is lost. */
        void onKeyInvalidated();

        void onAuthError(CharSequence message);
    }

    private static final boolean CRYPTO_WITH_CREDENTIAL = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R;

    private final BiometricPrompt prompt;
    private final Listener listener;
    private final CharSequence notAuthenticatedMessage;
    private CipherFactory pendingFactory;
    private CipherAction pendingAction;

    public AuthGate(FragmentActivity activity, CharSequence notAuthenticatedMessage, Listener listener) {
        this.listener = listener;
        this.notAuthenticatedMessage = notAuthenticatedMessage;
        prompt = new BiometricPrompt(activity, ContextCompat.getMainExecutor(activity),
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                        CipherFactory factory = pendingFactory;
                        CipherAction action = pendingAction;
                        pendingFactory = null;
                        pendingAction = null;
                        if (action == null) return;
                        BiometricPrompt.CryptoObject crypto = result.getCryptoObject();
                        execute(() -> action.run(crypto != null && crypto.getCipher() != null
                                ? crypto.getCipher() : factory.create()));
                    }

                    @Override
                    public void onAuthenticationError(int code, @NonNull CharSequence message) {
                        pendingFactory = null;
                        pendingAction = null;
                        if (code != BiometricPrompt.ERROR_USER_CANCELED
                                && code != BiometricPrompt.ERROR_NEGATIVE_BUTTON
                                && code != BiometricPrompt.ERROR_CANCELED) {
                            listener.onAuthError(message);
                        }
                    }
                });
    }

    private static int authenticators() {
        // BIOMETRIC_STRONG | DEVICE_CREDENTIAL isn't supported before Android 11.
        return CRYPTO_WITH_CREDENTIAL ? BIOMETRIC_STRONG | DEVICE_CREDENTIAL : BIOMETRIC_WEAK | DEVICE_CREDENTIAL;
    }

    /** False when there is no secure screen lock, so nothing can protect the key. */
    public static boolean isAvailable(Context context) {
        return BiometricManager.from(context).canAuthenticate(authenticators())
                == BiometricManager.BIOMETRIC_SUCCESS;
    }

    /** Explains that a screen lock is needed when there is none. */
    public static boolean checkAvailable(Context context) {
        if (isAvailable(context)) return true;
        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.no_lock_title)
                .setMessage(R.string.no_lock_message)
                .setPositiveButton(android.R.string.ok, null)
                .show();
        return false;
    }

    /** Wipes every vault (their key is gone) and tells the user. */
    public static void showKeyInvalidated(Context context) {
        PasswordVault.wipe(context);
        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.key_invalidated_title)
                .setMessage(R.string.key_invalidated_message)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    public void run(CharSequence title, CharSequence subtitle, CipherFactory factory, CipherAction action) {
        BiometricPrompt.PromptInfo info = new BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setAllowedAuthenticators(authenticators())
                .build();
        if (CRYPTO_WITH_CREDENTIAL) {
            Cipher cipher;
            try {
                cipher = factory.create();
            } catch (GeneralSecurityException e) {
                handle(e);
                return;
            }
            pendingFactory = factory;
            pendingAction = action;
            prompt.authenticate(info, new BiometricPrompt.CryptoObject(cipher));
        } else {
            pendingFactory = factory;
            pendingAction = action;
            prompt.authenticate(info);
        }
    }

    private interface Operation {
        void run() throws GeneralSecurityException;
    }

    private void execute(Operation operation) {
        try {
            operation.run();
        } catch (GeneralSecurityException e) {
            handle(e);
        }
    }

    private void handle(GeneralSecurityException e) {
        if (e instanceof KeyPermanentlyInvalidatedException) {
            listener.onKeyInvalidated();
        } else if (e instanceof UserNotAuthenticatedException) {
            // Weak biometrics (some face unlocks) don't unlock Keystore keys before Android 11.
            listener.onAuthError(notAuthenticatedMessage);
        } else {
            listener.onAuthError(e.getLocalizedMessage() != null ? e.getLocalizedMessage() : e.toString());
        }
    }
}
