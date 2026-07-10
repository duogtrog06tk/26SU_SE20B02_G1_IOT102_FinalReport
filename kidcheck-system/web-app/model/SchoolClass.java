package com.mycompany.webappcamerakidcheck.model;

public class SchoolClass {
    private String name;
    private String desc;
    private String startTime;
    private String lateTime;
    private int maxCapacity;

    public SchoolClass() {}

    public SchoolClass(String name, String desc) {
        this.name = name;
        this.desc = desc;
        this.startTime = "07:00";
        this.lateTime = "08:15";
        this.maxCapacity = 25;
    }

    public SchoolClass(String name, String desc, String startTime, String lateTime) {
        this.name = name;
        this.desc = desc;
        this.startTime = startTime;
        this.lateTime = lateTime;
        this.maxCapacity = 25;
    }

    public SchoolClass(String name, String desc, String startTime, String lateTime, int maxCapacity) {
        this.name = name;
        this.desc = desc;
        this.startTime = startTime;
        this.lateTime = lateTime;
        this.maxCapacity = maxCapacity;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDesc() { return desc; }
    public void setDesc(String desc) { this.desc = desc; }

    public String getStartTime() { return startTime; }
    public void setStartTime(String startTime) { this.startTime = startTime; }

    public String getLateTime() { return lateTime; }
    public void setLateTime(String lateTime) { this.lateTime = lateTime; }

    public int getMaxCapacity() { return maxCapacity; }
    public void setMaxCapacity(int maxCapacity) { this.maxCapacity = maxCapacity; }
}
