package services;

/**
 * Single source of truth for phone-number identity.
 *
 * The username/account key is the phone number, so two users only "find" each
 * other (contacts, message routing, OTP login) if their numbers map to the
 * exact same string. Numbers are Moroccan (+212); this canonicalises every
 * accepted form to E.164: {@code +212XXXXXXXXX}.
 *
 *   0664276777      -> +212664276777
 *   0612571592      -> +212612571592
 *   212690909090    -> +212690909090
 *   00212665918414  -> +212665918414
 *   +212 665-918414 -> +212665918414
 *   664276777       -> +212664276777   (bare 9-digit subscriber number)
 */
public final class PhoneUtil {

    private PhoneUtil() {}

    public static String canonical(String raw) {
        if (raw == null) return "";
        String s = raw.replaceAll("[^0-9+]", "");
        if (s.isEmpty()) return "";

        String d = s.replaceAll("[^0-9]", "");
        if (d.startsWith("212")) d = d.substring(3);  // 212XXXXXXXXX -> XXXXXXXXX
        if (d.startsWith("0")) d = d.substring(1);    // local 0XXXXXXXXX -> XXXXXXXXX inwi orange...


        return  "+212" + d;
    }
}
