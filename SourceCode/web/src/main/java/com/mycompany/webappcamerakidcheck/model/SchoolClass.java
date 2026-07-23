package com.mycompany.webappcamerakidcheck.model;
import com.google.firebase.database.IgnoreExtraProperties;

@IgnoreExtraProperties
public class SchoolClass {
    private String name;
    private String desc;
    private String startTime;
    private String lateTime;
    private String pickupTime;
    private int maxCapacity;

    public SchoolClass() {
        this.startTime = "07:30";
        this.lateTime = "08:30";
        this.pickupTime = "16:30";
        this.maxCapacity = 25;
    }

    public SchoolClass(String name, String desc) {
        this.name = name;
        this.desc = desc;
        this.startTime = "07:30";
        this.lateTime = "08:30";
        this.pickupTime = "16:30";
        this.maxCapacity = 25;
    }

    public SchoolClass(String name, String desc, String startTime, String lateTime, String pickupTime) {
        this.name = name;
        this.desc = desc;
        this.startTime = (startTime != null && !startTime.isEmpty()) ? startTime : "07:30";
        this.lateTime = (lateTime != null && !lateTime.isEmpty()) ? lateTime : "08:30";
        this.pickupTime = (pickupTime != null && !pickupTime.isEmpty()) ? pickupTime : "16:30";
        this.maxCapacity = 25;
    }

    public SchoolClass(String name, String desc, String startTime, String lateTime, String pickupTime, int maxCapacity) {
        this.name = name;
        this.desc = desc;
        this.startTime = (startTime != null && !startTime.isEmpty()) ? startTime : "07:30";
        this.lateTime = (lateTime != null && !lateTime.isEmpty()) ? lateTime : "08:30";
        this.pickupTime = (pickupTime != null && !pickupTime.isEmpty()) ? pickupTime : "16:30";
        this.maxCapacity = maxCapacity > 0 ? maxCapacity : 25;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDesc() { return desc; }
    public void setDesc(String desc) { this.desc = desc; }

    public String getStartTime() { return (startTime != null && !startTime.isEmpty()) ? startTime : "07:30"; }
    public void setStartTime(String startTime) { this.startTime = startTime; }

    public String getLateTime() { return (lateTime != null && !lateTime.isEmpty()) ? lateTime : "08:30"; }
    public void setLateTime(String lateTime) { this.lateTime = lateTime; }

    public String getPickupTime() { return (pickupTime != null && !pickupTime.isEmpty()) ? pickupTime : "16:30"; }
    public void setPickupTime(String pickupTime) { this.pickupTime = pickupTime; }

    public int getMaxCapacity() { return maxCapacity > 0 ? maxCapacity : 25; }
    public void setMaxCapacity(int maxCapacity) { this.maxCapacity = maxCapacity; }
}

