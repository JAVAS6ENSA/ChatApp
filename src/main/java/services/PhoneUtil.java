package services;

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
