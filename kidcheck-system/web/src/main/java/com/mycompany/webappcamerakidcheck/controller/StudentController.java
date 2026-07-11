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

    public static final List<Student> localBackupList = new ArrayList<>();
    private final Gson gson = new Gson();

    @Override
    public void init() throws ServletException {
        try {
            // Đăng ký đồng bộ dữ liệu thời gian thực từ Firebase sang local list
            DatabaseReference ref = FirebaseConfig.getDatabase().getReference("students");
            ref.addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {
                    localBackupList.clear();
                    if (dataSnapshot.exists()) {
                        for (DataSnapshot postSnapshot : dataSnapshot.getChildren()) {
                            Student student = postSnapshot.getValue(Student.class);
                            if (student != null) {
                                localBackupList.add(student);
                            }
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
