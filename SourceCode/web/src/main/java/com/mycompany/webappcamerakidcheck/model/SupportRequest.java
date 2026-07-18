package com.mycompany.webappcamerakidcheck.model;

/**
 * Model đại diện cho một Yêu cầu Hỗ trợ từ phụ huynh / khách
 * gửi qua biểu mẫu Liên hệ ở trang Landing Page.
 */
public class SupportRequest {
    private String id;
    private String name;
    private String phone;
    private String role;
    private String message;
    private String time;
    private String status; // "new", "read", "resolved"

    public SupportRequest() {}

    public SupportRequest(String id, String name, String phone, String role, String message, String time, String status) {
        this.id = id;
        this.name = name;
        this.phone = phone;
        this.role = role;
        this.message = message;
        this.time = time;
        this.status = status;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getTime() { return time; }
    public void setTime(String time) { this.time = time; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
