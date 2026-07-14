package com.mycompany.webappcamerakidcheck.model;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

public class RoleConfig {
    private Map<String, List<String>> teacherClassAssignments = new HashMap<>();
    private Map<String, String> parentStudentLinks = new HashMap<>();
    private Map<String, String> parentNames = new HashMap<>();
    private List<Map<String, String>> parentMessages = new ArrayList<>();

    public RoleConfig() {}

    public Map<String, List<String>> getTeacherClassAssignments() { return teacherClassAssignments; }
    public void setTeacherClassAssignments(Map<String, List<String>> teacherClassAssignments) { this.teacherClassAssignments = teacherClassAssignments; }

    public Map<String, String> getParentStudentLinks() { return parentStudentLinks; }
    public void setParentStudentLinks(Map<String, String> parentStudentLinks) { this.parentStudentLinks = parentStudentLinks; }

    public Map<String, String> getParentNames() { return parentNames; }
    public void setParentNames(Map<String, String> parentNames) { this.parentNames = parentNames; }

    public List<Map<String, String>> getParentMessages() { return parentMessages; }
    public void setParentMessages(List<Map<String, String>> parentMessages) { this.parentMessages = parentMessages; }
}
