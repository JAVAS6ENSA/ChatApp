package Messaging;

import java.io.*;
import java.net.Socket;

public class MessageReceiver implements Runnable {

    private BufferedReader in;
    private MessageListener listener;
    private boolean running = true;

    public MessageReceiver(Socket socket, MessageListener listener) throws IOException {
        this.in = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), "UTF-8"));
        this.listener = listener;
    }

    @Override
    public void run() {
        try {
            String line;
            while (running && (line = in.readLine()) != null) {
                handleMessage(line);
            }
        } catch (IOException e) {
            if (running) {
                listener.onError("Connection perdue: " + e.getMessage());
            }
        }
    }

    private void handleMessage(String raw) {
        String[] parts = raw.split("\\|", 4);
        if (parts.length < 2) return;

        String type = parts[0];

        switch (type) {
            case "PRIVATE":
                // PRIVATE|senderId|receiverId|content
                if (parts.length == 4) {
                    int senderId   = Integer.parseInt(parts[1]);
                    int receiverId = Integer.parseInt(parts[2]);
                    String content = parts[3];
                    listener.onPrivateMessage(senderId, receiverId, content);
                }
                break;

            case "GROUP":
                // GROUP|senderId|groupId|content
                if (parts.length == 4) {
                    int senderId = Integer.parseInt(parts[1]);
                    int groupId  = Integer.parseInt(parts[2]);
                    String content = parts[3];
                    listener.onGroupMessage(senderId, groupId, content);
                }
                break;

            case "LOGIN_OK":
                // LOGIN_OK|userId
                listener.onLoginSuccess(Integer.parseInt(parts[1]));
                break;

            case "LOGIN_FAIL":
                listener.onLoginFail();
                break;

            case "LOGOUT":
                // LOGOUT|userId
                listener.onUserDisconnected(Integer.parseInt(parts[1]));
                break;

            default:
                listener.onRawMessage(raw);
                break;
        }
    }

    public void stop() {
        running = false;
        try { in.close(); } catch (IOException ignored) {}
    }

    // ── Interface lil Frontend ────────────────────────────────────────────────
    public interface MessageListener {
        void onPrivateMessage(int senderId, int receiverId, String content);
        void onGroupMessage(int senderId, int groupId, String content);
        void onLoginSuccess(int userId);
        void onLoginFail();
        void onUserDisconnected(int userId);
        void onError(String error);
        void onRawMessage(String raw);
    }
}