package dk.meznik.jan.encrypttext.util;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;

/** Bank card / account details. Stored as JSON inside {@link PasswordVault#cards}. */
public class Card {
    public String number = "";
    public String holder = "";
    /** MM/YY */
    public String expiry = "";
    public String cvv = "";
    public String pin = "";
    /** IBAN or account number, as typed. */
    public String account = "";

    public String toJson() {
        try {
            return new JSONObject()
                    .put("number", number)
                    .put("holder", holder)
                    .put("expiry", expiry)
                    .put("cvv", cvv)
                    .put("pin", pin)
                    .put("account", account)
                    .toString();
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    public static Card fromJson(String json) throws JSONException {
        JSONObject o = new JSONObject(json);
        Card card = new Card();
        card.number = o.optString("number");
        card.holder = o.optString("holder");
        card.expiry = o.optString("expiry");
        card.cvv = o.optString("cvv");
        card.pin = o.optString("pin");
        card.account = o.optString("account");
        return card;
    }

    public static String digits(String s) {
        return s.replaceAll("[^0-9]", "");
    }

    /** "4111111111111111" → "4111 1111 1111 1111" */
    public static String groupNumber(String number) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < number.length(); i++) {
            if (i > 0 && i % 4 == 0) sb.append(' ');
            sb.append(number.charAt(i));
        }
        return sb.toString();
    }

    public static boolean isValidNumber(String number) {
        if (number.length() < 12 || number.length() > 19) return false;
        int sum = 0;
        boolean dbl = false;
        for (int i = number.length() - 1; i >= 0; i--) {
            int d = number.charAt(i) - '0';
            if (dbl) {
                d *= 2;
                if (d > 9) d -= 9;
            }
            sum += d;
            dbl = !dbl;
        }
        return sum % 10 == 0;
    }

    /** Accepts MMYY, MM/YY, MM/YYYY, M/YY…; returns MM/YY or null. */
    public static String normalizeExpiry(String s) {
        String[] parts = s.trim().split("[/.\\-\\s]+");
        String month;
        String year;
        if (parts.length == 2) {
            month = parts[0];
            year = parts[1];
        } else if (parts.length == 1 && (parts[0].length() == 4 || parts[0].length() == 6)) {
            month = parts[0].substring(0, 2);
            year = parts[0].substring(2);
        } else {
            return null;
        }
        if (!month.matches("\\d{1,2}") || !year.matches("\\d{2}|\\d{4}")) return null;
        int m = Integer.parseInt(month);
        if (m < 1 || m > 12) return null;
        return String.format(Locale.ROOT, "%02d/%s", m, year.substring(year.length() - 2));
    }
}
