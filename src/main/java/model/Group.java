package model;

import java.util.ArrayList;
import java.util.List;

public class Group {

    private int          id;
    private String       name;
    private int          createdBy;
    private List<Integer> memberIds;

    // Constructeur nouveau groupe
    public Group(String name, int createdBy) {
        this.name      = name;
        this.createdBy = createdBy;
        this.memberIds = new ArrayList<>();
    }

    // Constructeur complet (lecture BDD)
    public Group(int id, String name, int createdBy) {
        this.id        = id;
        this.name      = name;
        this.createdBy = createdBy;
        this.memberIds = new ArrayList<>();
    }

    // Getters
    public int           getId()        { return id; }
    public String        getName()      { return name; }
    public int           getCreatedBy() { return createdBy; }
    public List<Integer> getMemberIds() { return memberIds; }

    // Setters
    public void setId(int id)          { this.id   = id; }
    public void setName(String name)   { this.name = name; }

    // Gestion membres
    public void addMember(int userId) {
        if (!memberIds.contains(userId)) memberIds.add(userId);
    }
    public void removeMember(int userId) { memberIds.remove(Integer.valueOf(userId)); }
    public boolean isMember(int userId)  { return memberIds.contains(userId); }
    public int getMemberCount()          { return memberIds.size(); }

    @Override
    public String toString() {
        return "Group{id=" + id + ", name='" + name +
                "', createdBy=" + createdBy +
                ", members=" + memberIds + "}";
    }
}