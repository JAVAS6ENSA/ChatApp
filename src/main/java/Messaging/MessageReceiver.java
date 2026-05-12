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
                if (parts.length == 4) {
                    int senderId   = Integer.parseInt(parts[1]);
                    int receiverId = Integer.parseInt(parts[2]);
                    String content = parts[3];
                    listener.onPrivateMessage(senderId, receiverId, content);
                }
                break;

            case "GROUP_MSG":
                if (parts.length == 4) {
                    int groupId = Integer.parseInt(parts[1]);
                    String sender = parts[2];
                    String content = parts[3];
                    listener.onGroupMessage(groupId, sender, content);
                }
                break;

            case "GROUP_CREATED":
                if (parts.length >= 3) {
                    int gid = Integer.parseInt(parts[1]);
                    String name = parts[2];
                    listener.onRawMessage("GROUP_CREATED|" + gid + "|" + name);
                }
                break;

            case "GROUP_LIST":
                if (parts.length >= 2) {
                    listener.onRawMessage(raw);
                }
                break;

            case "GROUP_HISTORY":
                if (parts.length == 4) {
                    listener.onRawMessage(raw);
                }
                break;
            case "GROUP_AUDIO":
            case "GROUP_FILE":
                listener.onRawMessage(raw);
                break;

            case "GROUP_HISTORY_END":
                if (parts.length >= 2) {
                    listener.onRawMessage(raw);
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

    public interface MessageListener {
        void onPrivateMessage(int senderId, int receiverId, String content);
        void onGroupMessage(int groupId, String sender, String content);
        void onLoginSuccess(int userId);
        void onLoginFail();
        void onUserDisconnected(int userId);
        void onError(String error);
        void onRawMessage(String raw);
    }
}