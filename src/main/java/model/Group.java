package model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Group {

    private int          id;
    private String       name;
    private int          createdBy;
    private final List<Integer> memberIds;
    private final Set<Integer>  adminIds;

    // Newly-created group (id not yet known).
    public Group(String name, int createdBy) {
        this.name      = name;
        this.createdBy = createdBy;
        this.memberIds = new ArrayList<>();
        this.adminIds  = new HashSet<>();
    }

    // Loaded from DB.
    public Group(int id, String name, int createdBy) {
        this.id        = id;
        this.name      = name;
        this.createdBy = createdBy;
        this.memberIds = new ArrayList<>();
        this.adminIds  = new HashSet<>();
    }

    public int           getId()        { return id; }
    public String        getName()      { return name; }
    public int           getCreatedBy() { return createdBy; }
    public List<Integer> getMemberIds() { return memberIds; }
    public Set<Integer>  getAdminIds()  { return adminIds; }

    public void setId(int id)          { this.id   = id; }
    public void setName(String name)   { this.name = name; }

    public void addMember(int userId) {
        if (!memberIds.contains(userId)) memberIds.add(userId);
    }
    public void removeMember(int userId) {
        memberIds.remove(Integer.valueOf(userId));
        adminIds.remove(userId);
    }
    public boolean isMember(int userId)  { return memberIds.contains(userId); }
    public boolean isAdmin(int userId)   { return adminIds.contains(userId); }
    public int getMemberCount()          { return memberIds.size(); }

    public void promote(int userId) {
        if (memberIds.contains(userId)) adminIds.add(userId);
    }
    public void demote(int userId) { adminIds.remove(userId); }

    @Override
    public String toString() {
        return "Group{id=" + id + ", name='" + name +
                "', createdBy=" + createdBy +
                ", members=" + memberIds +
                ", admins=" + adminIds + "}";
    }
}
