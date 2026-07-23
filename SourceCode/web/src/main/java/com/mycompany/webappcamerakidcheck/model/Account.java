package com.mycompany.webappcamerakidcheck.model;

import com.google.firebase.database.IgnoreExtraProperties;

@IgnoreExtraProperties
public class Account {
    private String email;
    private String password;
    private String role; // "admin", "teacher", "parent"
    private String name;
    private String linkedStudentId; // For parent only

    public Account() {}

    public Account(String email, String password, String role, String name, String linkedStudentId) {
        this.email = email;
        this.password = password;
        this.role = role;
        this.name = name;
        this.linkedStudentId = linkedStudentId;
    }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getLinkedStudentId() { return linkedStudentId; }
    public void setLinkedStudentId(String linkedStudentId) { this.linkedStudentId = linkedStudentId; }
}
