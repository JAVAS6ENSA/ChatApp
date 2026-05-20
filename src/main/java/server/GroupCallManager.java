package server;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;


public class GroupCallManager {

    public static class Participant {
        public final String username;
        public String ip;
        public String lanIp;
        public int audioPort;
        public int videoPort;
        public final long joinedAt = System.currentTimeMillis();

        public Participant(String username, String ip, int audioPort, int videoPort) {
            this(username, ip, null, audioPort, videoPort);
        }

        public Participant(String username, String ip, String lanIp,
                           int audioPort, int videoPort) {
            this.username = username;
            this.ip = ip;
            this.lanIp = lanIp;
            this.audioPort = audioPort;
            this.videoPort = videoPort;
        }

        public String ipFor(String recipientPublicIp) {
            if (lanIp != null && !lanIp.isBlank()
                    && ip != null && ip.equals(recipientPublicIp)) {
                return lanIp;
            }
            return ip;
        }
    }

    public static class Meeting {
        public final int groupId;
        public final String type;
        public final String host;
        public final int callRowId;
        public final long startedAtMs = System.currentTimeMillis();
        public final Map<String, Participant> participants = new ConcurrentHashMap<>();

        public Meeting(int groupId, String type, String host, int callRowId) {
            this.groupId = groupId;
            this.type = type;
            this.host = host;
            this.callRowId = callRowId;
        }
    }

    private final Map<Integer, Meeting> meetings = new ConcurrentHashMap<>();

    public Meeting getMeeting(int groupId) {
        return meetings.get(groupId);
    }

    public Meeting createMeeting(int groupId, String type, String host, int callRowId) {
        Meeting m = new Meeting(groupId, type, host, callRowId);
        Meeting prev = meetings.putIfAbsent(groupId, m);
        return prev != null ? prev : m;
    }

    public void endMeeting(int groupId) {
        meetings.remove(groupId);
    }

    public Collection<Meeting> getActiveMeetings() {
        return meetings.values();
    }
}
