package com.mycompany.webappcamerakidcheck.controller;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import com.mycompany.webappcamerakidcheck.model.Student;

@WebServlet(name = "StudentController", urlPatterns = {"/api/students"})
public class StudentController extends HttpServlet {

    private static final List<Student> localBackupList = new ArrayList<>();
    private final Gson gson = new Gson();

    @Override
    public void init() throws ServletException {
        // Tải dữ liệu mặc định ban đầu nếu Firebase trống
        if (localBackupList.isEmpty()) {
            localBackupList.add(new Student("HS001", "Nguyễn Gia Bảo", "Mầm 1", "Chưa điểm danh", "https://images.unsplash.com/photo-1503919545889-aef636e10ad4?w=150&auto=format&fit=crop", "", "Nguyễn Gia Hưng", "0901234567", "Dị ứng với đậu phộng", "Chưa đến"));
            localBackupList.add(new Student("HS002", "Lê Mỹ An", "Mầm 1", "Chưa điểm danh", "https://images.unsplash.com/photo-1519085360753-af0119f7cbe7?w=150&auto=format&fit=crop", "", "Lê Anh Tuấn", "0912345678", "Bình thường", "Chưa đến"));
            localBackupList.add(new Student("HS003", "Trần Minh Khang", "Chồi 2", "Chưa điểm danh", "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=150&auto=format&fit=crop", "", "Phan Thanh Mai", "0987654321", "Cận thị nhẹ", "Chưa đến"));
            localBackupList.add(new Student("HS004", "Phạm Vy Anh", "Chồi 2", "Chưa điểm danh", "https://images.unsplash.com/photo-1544005313-94ddf0286df2?w=150&auto=format&fit=crop", "", "Phạm Hồng Phước", "0976543210", "Dị ứng hải sản", "Chưa đến"));
            localBackupList.add(new Student("HS005", "Hoàng Đức Nam", "Lá 3", "Chưa điểm danh", "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=150&auto=format&fit=crop", "", "Hoàng Văn Khánh", "0965432109", "Bình thường", "Chưa đến"));
        }
        
        try {
            // Đăng ký đồng bộ dữ liệu thời gian thực từ Firebase sang local list
            DatabaseReference ref = FirebaseConfig.getDatabase().getReference("students");
            ref.addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {
                    if (dataSnapshot.exists()) {
                        localBackupList.clear();
                        for (DataSnapshot postSnapshot : dataSnapshot.getChildren()) {
                            Student student = postSnapshot.getValue(Student.class);
                            if (student != null) {
                                localBackupList.add(student);
                            }
                        }
                    } else {
                        // Nếu Firebase trống, đẩy dữ liệu mẫu lên Firebase
                        for (Student s : localBackupList) {
                            ref.child(s.getId()).setValueAsync(s);
                        }
                    }
                }

                @Override
                public void onCancelled(DatabaseError databaseError) {
                    System.err.println("Firebase Student sync cancelled: " + databaseError.getMessage());
                }
            });
        } catch (Exception e) {
            System.err.println("Không thể thiết lập lắng nghe Firebase: " + e.getMessage());
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        try (PrintWriter out = response.getWriter()) {
            out.print(gson.toJson(localBackupList));
            out.flush();
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        try {
            Student newStudent = gson.fromJson(request.getReader(), Student.class);
            if (newStudent != null && newStudent.getId() != null) {
                boolean exists = localBackupList.stream().anyMatch(s -> s.getId().equalsIgnoreCase(newStudent.getId()));
                if (exists) {
                    response.setStatus(HttpServletResponse.SC_CONFLICT);
                    try (PrintWriter out = response.getWriter()) {
                        out.print("{\"error\":\"Mã học sinh đã tồn tại!\"}");
                    }
                    return;
                }
                
                // Lưu lên Firebase
                try {
                    DatabaseReference ref = FirebaseConfig.getDatabase().getReference("students");
                    ref.child(newStudent.getId()).setValueAsync(newStudent);
                } catch (Exception e) {
                    localBackupList.add(newStudent); // Dự phòng nếu ko có kết nối mạng
                }
                
                response.setStatus(HttpServletResponse.SC_CREATED);
                try (PrintWriter out = response.getWriter()) {
                    out.print(gson.toJson(newStudent));
                }
            }
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        }
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        try {
            Student updateData = gson.fromJson(request.getReader(), Student.class);
            if (updateData != null && updateData.getId() != null) {
                // Đẩy cập nhật lên Firebase
                try {
                    DatabaseReference ref = FirebaseConfig.getDatabase().getReference("students");
                    ref.child(updateData.getId()).setValueAsync(updateData);
                } catch (Exception e) {
                    // Update local backup
                    for (Student s : localBackupList) {
                        if (s.getId().equalsIgnoreCase(updateData.getId())) {
                            s.setName(updateData.getName());
                            s.setClassName(updateData.getClassName());
                            s.setPhoto(updateData.getPhoto());
                            s.setStatus(updateData.getStatus());
                            s.setTime(updateData.getTime());
                            s.setParentName(updateData.getParentName());
                            s.setParentPhone(updateData.getParentPhone());
                            s.setHealthNote(updateData.getHealthNote());
                            s.setPickupStatus(updateData.getPickupStatus());
                        }
                    }
                }
                
                response.setStatus(HttpServletResponse.SC_OK);
                try (PrintWriter out = response.getWriter()) {
                    out.print(gson.toJson(updateData));
                }
            }
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        }
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        String studentId = request.getParameter("id");
        if (studentId != null) {
            try {
                DatabaseReference ref = FirebaseConfig.getDatabase().getReference("students");
                ref.child(studentId).removeValueAsync();
            } catch (Exception e) {
                localBackupList.removeIf(s -> s.getId().equalsIgnoreCase(studentId));
            }
            
            try (PrintWriter out = response.getWriter()) {
                out.print("{\"success\":true}");
            }
            return;
        }
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
    }
}
