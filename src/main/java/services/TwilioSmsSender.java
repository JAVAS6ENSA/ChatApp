package services;

import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;

//sms sender a la methode send
public class TwilioSmsSender implements SmsSender {

    private final String accountSid = trim(System.getenv("TWILIO_ACCOUNT_SID"));
    private final String authToken  = trim(System.getenv("TWILIO_AUTH_TOKEN"));
    private final String fromNumber = trim(System.getenv("TWILIO_FROM_NUMBER"));

    private static String trim(String s) { return s == null ? null : s.trim(); }
    private final boolean configured;

    public TwilioSmsSender() {
        this.configured = accountSid != null && !accountSid.isBlank()
                       && authToken  != null && !authToken.isBlank()
                       && fromNumber != null && !fromNumber.isBlank();
        if (configured) {
            Twilio.init(accountSid, authToken);
            System.out.println("[Twilio] Configured. Sending from " + fromNumber);
        } else {
            System.err.println("[Twilio] Credentials not set (TWILIO_ACCOUNT_SID / "
                    + "TWILIO_AUTH_TOKEN / TWILIO_FROM_NUMBER). "
                    + "SMS will be printed to the console instead of sent.");
        }
    }

    @Override
    public boolean send(String toPhone, String message) {
        if (!configured) {
            System.out.println("[SMS->" + toPhone + "] " + message);
            return true;
        }
        try {
            Message.creator(new PhoneNumber(toPhone),
                            new PhoneNumber(fromNumber),
                            message)
                   .create();
            return true;
        } catch (Exception e) {
            System.err.println("[Twilio] send failed: " + e.getMessage());
            return false;
        }
    }
}
