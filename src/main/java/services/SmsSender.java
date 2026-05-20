package services;
public interface SmsSender {
    boolean send(String toPhone, String message);
}
