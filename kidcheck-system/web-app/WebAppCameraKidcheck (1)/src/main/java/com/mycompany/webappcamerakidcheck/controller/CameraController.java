package com.mycompany.webappcamerakidcheck.controller;

import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Optional;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.google.gson.Gson;
import com.google.firebase.database.DatabaseReference;
import com.mycompany.webappcamerakidcheck.config.FirebaseConfig;
import com.mycompany.webappcamerakidcheck.model.Student;
import com.mycompany.webappcamerakidcheck.model.AttendanceLog;

@WebServlet(name = "CameraController", urlPatterns = {"/api/camera"})
public class CameraController extends HttpServlet {
    private final Gson gson = new Gson();

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        try {
            // Nhận dữ liệu từ camera thật gửi lên
            // Body: { "id": "HS001", "photo": "http://ip/photo.jpg" }
            CameraPayload payload = gson.fromJson(request.getReader(), CameraPayload.class);
            if (payload != null && payload.getId() != null) {
                DatabaseReference db = FirebaseConfig.getDatabase().getReference();
                String timeStr = new SimpleDateFormat("HH:mm").format(new Date());

                // Tìm kiếm học sinh trong danh sách đã đồng bộ từ Firebase
                Optional<Student> matchedStudent = StudentController.localBackupList.stream()
                        .filter(s -> s.getId().equalsIgnoreCase(payload.getId()))
                        .findFirst();

                if (matchedStudent.isPresent()) {
                    // 1. NẾU ĐÃ CÓ HỌC SINH ĐĂNG KÝ -> TIẾN HÀNH ĐIỂM DANH
                    Student student = matchedStudent.get();
                    
                    // Xác định trạng thái muộn dựa trên cấu hình (mặc định 08:15)
                    String status = "Có mặt";
                    String[] timeParts = timeStr.split(":");
                    int hour = Integer.parseInt(timeParts[0]);
                    int min = Integer.parseInt(timeParts[1]);
                    if (hour > 8 || (hour == 8 && min >= 15)) {
                        status = "Đi trễ";
                    }

                    // Cập nhật trạng thái điểm danh cho học sinh
                    student.setStatus(status);
                    student.setTime(timeStr);
                    student.setPickupStatus("Đang ở lớp");
                    if (payload.getPhoto() != null && !payload.getPhoto().isEmpty()) {
                        student.setPhoto(payload.getPhoto());
                    }

                    // Đồng bộ lên Firebase
                    db.child("students").child(student.getId()).setValueAsync(student);

                    // Tạo lịch sử điểm danh (Log)
                    AttendanceLog log = new AttendanceLog(
                        student.getId(),
                        student.getName(),
                        student.getClassName(),
                        student.getPhoto(),
                        status.equals("Đi trễ") ? "Bé đến muộn" : "Bé đến lớp",
                        timeStr,
                        student.getParentName(),
                        student.getParentPhone(),
                        "Đang ở lớp"
                    );
                    db.child("logs").push().setValueAsync(log);

                    response.setStatus(HttpServletResponse.SC_OK);
                    try (PrintWriter out = response.getWriter()) {
                        out.print(gson.toJson(student));
                    }
                } else {
                    // 2. NẾU CHƯA ĐĂNG KÝ -> BÁO CẢNH BÁO "KHÔNG HIỂN THỊ" / NGƯỜI LẠ
                    String strangerPhoto = (payload.getPhoto() != null && !payload.getPhoto().isEmpty()) 
                        ? payload.getPhoto() 
                        : "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=150&auto=format&fit=crop"; // ảnh đại diện ẩn danh
                    
                    // Tạo một Log Cảnh báo người lạ lên Firebase để giao diện Web nhận biết tức thời
                    AttendanceLog warningLog = new AttendanceLog(
                        "UNKNOWN",
                        "Người lạ / Chưa đăng ký",
                        "-",
                        strangerPhoto,
                        "Cảnh báo: Người lạ",
                        timeStr,
                        "Không xác định",
                        "Không xác định",
                        "Không được vào"
                    );
                    db.child("logs").push().setValueAsync(warningLog);

                    response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                    try (PrintWriter out = response.getWriter()) {
                        out.print("{\"error\":\"Học sinh chưa được đăng ký khuôn mặt!\"}");
                    }
                }
            } else {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                try (PrintWriter out = response.getWriter()) {
                    out.print("{\"error\":\"Invalid payload\"}");
                }
            }
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            try (PrintWriter out = response.getWriter()) {
                out.print("{\"error\":\"" + e.getMessage() + "\"}");
            }
        }
    }

    private static class CameraPayload {
        private String id;
        private String photo;

        public String getId() { return id; }
        public String getPhoto() { return photo; }
    }
}
