package com.mycompany.webappcamerakidcheck.model;
import com.google.firebase.database.IgnoreExtraProperties;

@IgnoreExtraProperties
public class Student {
    private String id;
    private String name;
    private String className;
    private String status;
    private String photo;
    private String time;
    private String parentName;
    private String parentPhone;
    private String healthNote;
    private String pickupStatus; // "Chưa đến", "Đang ở lớp", "Đã đón về"
    private String parentEmail;

    public Student() {}

    public Student(String id, String name, String className, String status, String photo, String time) {
        this.id = id;
        this.name = name;
        this.className = className;
        this.status = status;
        this.photo = photo;
        this.time = time;
        this.parentName = "Chưa cập nhật";
        this.parentPhone = "Chưa cập nhật";
        this.healthNote = "Bình thường";
        this.pickupStatus = "Chưa đến";
    }

    public Student(String id, String name, String className, String status, String photo, String time, 
                   String parentName, String parentPhone, String healthNote, String pickupStatus) {
        this.id = id;
        this.name = name;
        this.className = className;
        this.status = status;
        this.photo = photo;
        this.time = time;
        this.parentName = parentName;
        this.parentPhone = parentPhone;
        this.healthNote = healthNote;
        this.pickupStatus = pickupStatus;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getClassName() { return className; }
    public void setClassName(String className) { this.className = className; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getPhoto() { return photo; }
    public void setPhoto(String photo) { this.photo = photo; }

    public String getTime() { return time; }
    public void setTime(String time) { this.time = time; }

    public String getParentName() { return parentName; }
    public void setParentName(String parentName) { this.parentName = parentName; }

    public String getParentPhone() { return parentPhone; }
    public void setParentPhone(String parentPhone) { this.parentPhone = parentPhone; }

    public String getHealthNote() { return healthNote; }
    public void setHealthNote(String healthNote) { this.healthNote = healthNote; }

    public String getPickupStatus() { return pickupStatus; }
    public void setPickupStatus(String pickupStatus) { this.pickupStatus = pickupStatus; }

    public String getParentEmail() { return parentEmail; }
    public void setParentEmail(String parentEmail) { this.parentEmail = parentEmail; }
}
