package com.mycompany.webappcamerakidcheck.model;

public class Settings {
    private String startTime = "07:00";
    private String lateTime = "08:15";
    private String aiEndpoint = "http://192.168.1.50:5000/api/recognize";

    public Settings() {}

    public Settings(String startTime, String lateTime, String aiEndpoint) {
        this.startTime = startTime;
        this.lateTime = lateTime;
        this.aiEndpoint = aiEndpoint;
    }

    public String getStartTime() { return startTime; }
    public void setStartTime(String startTime) { this.startTime = startTime; }

    public String getLateTime() { return lateTime; }
    public void setLateTime(String lateTime) { this.lateTime = lateTime; }

    public String getAiEndpoint() { return aiEndpoint; }
    public void setAiEndpoint(String aiEndpoint) { this.aiEndpoint = aiEndpoint; }
}
