package com.spotify.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;

import java.util.*;

@Entity
@Table(name = "tracks")
@NoArgsConstructor
public class Track {
    @Id
    private String id;

    private String name;
    private Date addedAt;

    @ManyToMany(mappedBy = "trackList", cascade = CascadeType.PERSIST, fetch = FetchType.EAGER)
    private Set<User> userList = new HashSet<>();

    public Track(String id, String name, Date addedAt) {
        this.id = id;
        this.name = name;
        this.addedAt = addedAt;
    }


    public Set<User> getUserList() {
        return userList;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Date getAddedAt() {
        return addedAt;
    }

    public void setAddedAt(Date addedAt) {
        this.addedAt = addedAt;
    }

    public void setUserList(Set<User> userList) {
        this.userList = userList;
    }
}
