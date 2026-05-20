package services;

import dao.OtpDAO;

import java.security.SecureRandom;
import java.sql.Timestamp;


public class OtpService {

    public static final int TTL_MINUTES = 5;
    public static final int MAX_ATTEMPTS = 5;

    public enum Result { OK, NO_CODE, EXPIRED, MISMATCH, TOO_MANY_ATTEMPTS }

    private final OtpDAO otpDAO = new OtpDAO();
    private final SmsSender sms;
    private final SecureRandom rnd = new SecureRandom();

    public OtpService(SmsSender sms) {
        this.sms = sms;
    }


    public boolean devMode() {
        String v = System.getenv("OTP_DEV_MODE");
        return v != null && (v.equals("1") || v.equalsIgnoreCase("true")
                || v.equalsIgnoreCase("yes") || v.equalsIgnoreCase("on"));
    }


    public String currentCode(String phone) {
        OtpDAO.Otp o = otpDAO.get(phone);
        return o == null ? null : o.code;
    }


    public boolean sendCode(String phone) {
        String code = String.format("%06d", rnd.nextInt(1_000_000));
        if (!otpDAO.save(phone, code, TTL_MINUTES)) {
            System.err.println("[OTP] DB save FAILED for " + phone);
            return false;
        }
        System.out.println("[OTP] code stored for " + phone + " (valid "
                + TTL_MINUTES + " min); handing to SMS sender...");
        if (devMode()) {
            System.out.println("╔══════════════════════════════════════════════╗");
            System.out.println("║ [OTP DEV MODE] No SMS sent.                  ║");
            System.out.println("║ Phone : " + phone);
            System.out.println("║ Code  : " + code);
            System.out.println("╚══════════════════════════════════════════════╝");
            return true;
        }
        boolean sent = sms.send(phone, "Your ChatApp verification code is " + code
                + " (valid " + TTL_MINUTES + " min).");
        System.out.println("[OTP] SMS sender returned " + sent + " for " + phone);
        return sent;
    }

    public Result verify(String phone, String code) {
        OtpDAO.Otp otp = otpDAO.get(phone);
        if (otp == null) return Result.NO_CODE;

        if (otp.expiresAt.before(new Timestamp(System.currentTimeMillis()))) {
            otpDAO.delete(phone);
            return Result.EXPIRED;
        }
        if (otp.attempts >= MAX_ATTEMPTS) {
            otpDAO.delete(phone);
            return Result.TOO_MANY_ATTEMPTS;
        }
        if (!otp.code.equals(code == null ? "" : code.trim())) {
            otpDAO.incrementAttempts(phone);
            return Result.MISMATCH;
        }
        otpDAO.delete(phone); // single-use
        return Result.OK;
    }
}
