package com.mycompany.webappcamerakidcheck.controller;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.google.gson.Gson;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;
import com.mycompany.webappcamerakidcheck.config.FirebaseConfig;
import com.mycompany.webappcamerakidcheck.model.AttendanceLog;

@WebServlet(name = "LogController", urlPatterns = {"/api/logs"})
public class LogController extends HttpServlet {

    private static final List<AttendanceLog> localBackupList = new ArrayList<>();
    private final Gson gson = new Gson();

    private static final String FIREBASE_DB_URL = "https://kidcheckdb-default-rtdb.asia-southeast1.firebasedatabase.app/";

    @Override
    public void init() throws ServletException {
        try {
            // FIX #1: AI Server ghi điểm danh vào nhánh "attendance/{date}/{student_id}",
            // không phải "logs/". Listener này đọc toàn bộ nhánh "attendance/",
            // duyệt qua từng ngày rồi từng học sinh để ghép vào danh sách log.
            DatabaseReference ref = FirebaseConfig.getDatabase().getReference("attendance");
            ref.addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {
                    if (dataSnapshot.exists()) {
                        localBackupList.clear();
                        // Cấp 1: ngày (VD: "2026-07-13")
                        for (DataSnapshot dateSnapshot : dataSnapshot.getChildren()) {
                            // Cấp 2: student_id (VD: "HS001_NguyenAn")
                            for (DataSnapshot studentSnapshot : dateSnapshot.getChildren()) {
                                try {
                                    String studentName = studentSnapshot.child("student_name").getValue(String.class);
                                    String timestamp   = studentSnapshot.child("timestamp").getValue(String.class);
                                    String status      = studentSnapshot.child("status").getValue(String.class);
                                    String photo       = studentSnapshot.child("photo").getValue(String.class);
                                    if (photo == null) photo = studentSnapshot.child("captured_image").getValue(String.class);
                                    if (photo == null) photo = studentSnapshot.child("captured_photo").getValue(String.class);
                                    String studentId   = studentSnapshot.getKey();

                                    AttendanceLog log = new AttendanceLog();
                                    log.setId(studentId);
                                    log.setName(studentName != null ? studentName : studentId);
                                    log.setStatus(status != null ? status : "present");
                                    // Ghép ngày + giờ thành chuỗi đầy đủ cho UI
                                    String dateStr = dateSnapshot.getKey();
                                    log.setTime(dateStr + " " + (timestamp != null ? timestamp : "--:--"));
                                    log.setClassName("");
                                    log.setPhoto(photo != null ? photo : "");
                                    log.setParentName("");
                                    log.setParentPhone("");
                                    log.setPickupStatus("");

                                    if (studentId != null) {
                                        localBackupList.add(log);
                                    }
                                } catch (Exception ex) {
                                    System.err.println("Lỗi parse attendance record: " + ex.getMessage());
                                }
                            }
                        }
                    }
                }

                @Override
                public void onCancelled(DatabaseError databaseError) {
                    System.err.println("Firebase Attendance sync cancelled: " + databaseError.getMessage());
                    syncLogsRest();
                }
            });
        } catch (Exception e) {
            System.err.println("Không thể thiết lập lắng nghe Attendance trên Firebase: " + e.getMessage());
            syncLogsRest();
        }
    }

    private void syncLogsRest() {
        try {
            String urlString = FIREBASE_DB_URL + "attendance.json";
            java.net.URL url = java.net.URI.create(urlString).toURL();
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            int code = conn.getResponseCode();
            if (code == 200) {
                java.io.InputStream is = conn.getInputStream();
                java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(is, java.nio.charset.StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }

                String body = sb.toString();
                if (body != null && !body.equals("null") && !body.isEmpty()) {
                    com.google.gson.JsonObject root = gson.fromJson(body, com.google.gson.JsonObject.class);
                    if (root != null) {
                        localBackupList.clear();
                        for (String dateKey : root.keySet()) {
                            com.google.gson.JsonElement dateElem = root.get(dateKey);
                            if (dateElem.isJsonObject()) {
                                com.google.gson.JsonObject dateObj = dateElem.getAsJsonObject();
                                for (String studentId : dateObj.keySet()) {
                                    com.google.gson.JsonElement stElem = dateObj.get(studentId);
                                    if (stElem.isJsonObject()) {
                                        com.google.gson.JsonObject stObj = stElem.getAsJsonObject();
                                        String studentName = stObj.has("student_name") ? stObj.get("student_name").getAsString() : studentId;
                                        String timestamp = stObj.has("timestamp") ? stObj.get("timestamp").getAsString() : "--:--";
                                        String status = stObj.has("status") ? stObj.get("status").getAsString() : "present";
                                        String photo = stObj.has("photo") ? stObj.get("photo").getAsString() : (stObj.has("captured_image") ? stObj.get("captured_image").getAsString() : (stObj.has("captured_photo") ? stObj.get("captured_photo").getAsString() : ""));

                                        AttendanceLog log = new AttendanceLog();
                                        log.setId(studentId);
                                        log.setName(studentName);
                                        log.setStatus(status);
                                        log.setTime(dateKey + " " + timestamp);
                                        log.setClassName("");
                                        log.setPhoto(photo);
                                        log.setParentName("");
                                        log.setParentPhone("");
                                        log.setPickupStatus("");
                                        localBackupList.add(log);
                                    }
                                }
                            }
                        }
                        System.out.println("REST LogController đồng bộ attendance thành công: " + localBackupList.size() + " lượt.");
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("REST LogController sync failed: " + e.getMessage());
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        syncLogsRest();

        String exportParam = request.getParameter("export");
        if ("true".equalsIgnoreCase(exportParam)) {
            response.setContentType("text/csv; charset=UTF-8");
            response.setCharacterEncoding("UTF-8");
            response.setHeader("Content-Disposition", "attachment; filename=\"BaoCaoChuyenCan.csv\"");
            
            try (PrintWriter out = response.getWriter()) {
                // Ghi UTF-8 BOM cho Excel
                out.write('\uFEFF');
                
                // Tiêu đề cột
                out.println("Mã học sinh,Họ và Tên,Lớp học,Trạng thái,Thời gian,Phụ huynh,Số điện thoại");
                
                for (AttendanceLog log : localBackupList) {
                    out.println(String.format("%s,%s,%s,%s,%s,%s,%s",
                        escapeCsv(log.getId()),
                        escapeCsv(log.getName()),
                        escapeCsv(log.getClassName()),
                        escapeCsv(log.getStatus()),
                        escapeCsv(log.getTime()),
                        escapeCsv(log.getParentName()),
                        escapeCsv(log.getParentPhone())
                    ));
                }
                out.flush();
            }
            return;
        }

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        try (PrintWriter out = response.getWriter()) {
            out.print(gson.toJson(localBackupList));
            out.flush();
        }
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        try {
            AttendanceLog log = gson.fromJson(request.getReader(), AttendanceLog.class);
            if (log != null) {
                try {
                    DatabaseReference ref = FirebaseConfig.getDatabase().getReference("logs");
                    // Tạo ID ngẫu nhiên cho mỗi log
                    ref.push().setValueAsync(log);
                } catch (Exception e) {
                    localBackupList.add(log);
                }
                
                response.setStatus(HttpServletResponse.SC_CREATED);
                try (PrintWriter out = response.getWriter()) {
                    out.print(gson.toJson(log));
                }
            }
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        }
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String studentId = request.getParameter("id");
        if (studentId != null && !studentId.trim().isEmpty()) {
            String targetId = studentId.trim();
            localBackupList.removeIf(log -> log.getId() != null && 
                (log.getId().equals(targetId) || log.getId().startsWith(targetId + "_")));
            
            try {
                // Xóa khỏi Firebase Database tất cả các nhánh con khớp với targetId bằng Admin SDK
                String dateStr = new java.text.SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date());
                DatabaseReference ref = FirebaseConfig.getDatabase().getReference("attendance/" + dateStr);
                ref.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            for (DataSnapshot child : snapshot.getChildren()) {
                                String key = child.getKey();
                                if (key != null && (key.equals(targetId) || key.startsWith(targetId + "_") || key.contains(targetId))) {
                                    child.getRef().removeValueAsync();
                                }
                            }
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {}
                });
            } catch (Exception e) {
                System.err.println("Lỗi xóa Firebase log bằng Admin SDK: " + e.getMessage());
            }

            // Đồng thời dùng REST API xóa trực tiếp để đảm bảo thành công kể cả khi Admin SDK lỗi
            try {
                String dateStr = new java.text.SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date());
                
                // Fetch danh sách key trong ngày hôm đó
                String attUrl = FIREBASE_DB_URL + "attendance/" + dateStr + ".json?shallow=true";
                java.net.URL url = new java.net.URL(attUrl);
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                if (conn.getResponseCode() == 200) {
                    java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(conn.getInputStream(), "UTF-8"));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                    reader.close();
                    
                    String body = sb.toString();
                    if (body != null && !body.equals("null") && !body.isEmpty()) {
                        java.util.regex.Pattern keyPattern = java.util.regex.Pattern.compile("\"([^\"]+)\"\\s*:");
                        java.util.regex.Matcher matcher = keyPattern.matcher(body);
                        while (matcher.find()) {
                            String key = matcher.group(1);
                            if (key != null && (key.equals(targetId) || key.startsWith(targetId + "_") || key.contains(targetId))) {
                                String delUrlStr = FIREBASE_DB_URL + "attendance/" + dateStr + "/" + key + ".json";
                                java.net.HttpURLConnection delConn = (java.net.HttpURLConnection) new java.net.URL(delUrlStr).openConnection();
                                delConn.setRequestMethod("DELETE");
                                delConn.getResponseCode();
                            }
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("Lỗi xóa Firebase log bằng REST: " + e.getMessage());
            }

            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType("application/json");
            try (PrintWriter out = response.getWriter()) {
                out.print("{\"success\":true,\"message\":\"Deleted log successfully\"}");
            }
        } else {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        }
    }
}
