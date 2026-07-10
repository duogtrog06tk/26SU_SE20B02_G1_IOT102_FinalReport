package com.mycompany.webappcamerakidcheck.model;

public class SchoolClass {
    private String name;
    private String desc;
    private String startTime;
    private String lateTime;

    public SchoolClass() {}

    public SchoolClass(String name, String desc) {
        this.name = name;
        this.desc = desc;
        this.startTime = "07:00";
        this.lateTime = "08:15";
    }

    public SchoolClass(String name, String desc, String startTime, String lateTime) {
        this.name = name;
        this.desc = desc;
        this.startTime = startTime;
        this.lateTime = lateTime;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDesc() { return desc; }
    public void setDesc(String desc) { this.desc = desc; }

    public String getStartTime() { return startTime; }
    public void setStartTime(String startTime) { this.startTime = startTime; }

    public String getLateTime() { return lateTime; }
    public void setLateTime(String lateTime) { this.lateTime = lateTime; }
}
