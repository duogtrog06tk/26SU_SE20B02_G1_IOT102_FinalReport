package com.mycompany.webappcamerakidcheck.model;

public class AttendanceLog {
    private String id;
    private String name;
    private String className;
    private String photo;
    private String status;
    private String time;
    private String parentName;
    private String parentPhone;
    private String pickupStatus;

    public AttendanceLog() {}

    public AttendanceLog(String id, String name, String className, String photo, String status, String time) {
        this.id = id;
        this.name = name;
        this.className = className;
        this.photo = photo;
        this.status = status;
        this.time = time;
        this.parentName = "Chưa cập nhật";
        this.parentPhone = "Chưa cập nhật";
        this.pickupStatus = "Chưa xác định";
    }

    public AttendanceLog(String id, String name, String className, String photo, String status, String time,
                         String parentName, String parentPhone, String pickupStatus) {
        this.id = id;
        this.name = name;
        this.className = className;
        this.photo = photo;
        this.status = status;
        this.time = time;
        this.parentName = parentName;
        this.parentPhone = parentPhone;
        this.pickupStatus = pickupStatus;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getClassName() { return className; }
    public void setClassName(String className) { this.className = className; }

    public String getPhoto() { return photo; }
    public void setPhoto(String photo) { this.photo = photo; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getTime() { return time; }
    public void setTime(String time) { this.time = time; }

    public String getParentName() { return parentName; }
    public void setParentName(String parentName) { this.parentName = parentName; }

    public String getParentPhone() { return parentPhone; }
    public void setParentPhone(String parentPhone) { this.parentPhone = parentPhone; }

    public String getPickupStatus() { return pickupStatus; }
    public void setPickupStatus(String pickupStatus) { this.pickupStatus = pickupStatus; }
}
