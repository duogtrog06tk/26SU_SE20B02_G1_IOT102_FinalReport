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
                                    String studentId   = studentSnapshot.getKey();

                                    AttendanceLog log = new AttendanceLog();
                                    log.setId(studentId);
                                    log.setName(studentName != null ? studentName : studentId);
                                    log.setStatus(status != null ? status : "present");
                                    // Ghép ngày + giờ thành chuỗi đầy đủ cho UI
                                    String dateStr = dateSnapshot.getKey();
                                    log.setTime(dateStr + " " + (timestamp != null ? timestamp : "--:--"));
                                    log.setClassName("");
                                    log.setPhoto("");
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
                }
            });
        } catch (Exception e) {
            System.err.println("Không thể thiết lập lắng nghe Attendance trên Firebase: " + e.getMessage());
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
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
}
