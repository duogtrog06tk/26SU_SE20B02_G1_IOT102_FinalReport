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
        if (localBackupList.isEmpty()) {
            String todayStr = new java.text.SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date());
            localBackupList.add(new AttendanceLog("HS001", "Nguyễn Gia Bảo", "Mầm 1", "https://images.unsplash.com/photo-1503919545889-aef636e10ad4?w=150&auto=format&fit=crop", "Bé đến lớp", todayStr + " 07:15", "Nguyễn Gia Hưng", "0901234567", "Đang ở lớp"));
            localBackupList.add(new AttendanceLog("HS002", "Lê Mỹ An", "Mầm 1", "https://images.unsplash.com/photo-1519085360753-af0119f7cbe7?w=150&auto=format&fit=crop", "Bé đến lớp", todayStr + " 07:22", "Lê Anh Tuấn", "0912345678", "Đang ở lớp"));
            localBackupList.add(new AttendanceLog("HS003", "Trần Minh Khang", "Chồi 2", "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=150&auto=format&fit=crop", "Bé đến muộn", todayStr + " 08:20", "Phan Thanh Mai", "0987654321", "Đang ở lớp"));
            localBackupList.add(new AttendanceLog("HS004", "Phạm Vy Anh", "Chồi 2", "https://images.unsplash.com/photo-1544005313-94ddf0286df2?w=150&auto=format&fit=crop", "Bé đến lớp", todayStr + " 07:30", "Phạm Hồng Phước", "0976543210", "Đang ở lớp"));
            localBackupList.add(new AttendanceLog("HS004", "Phạm Vy Anh", "Chồi 2", "https://images.unsplash.com/photo-1544005313-94ddf0286df2?w=150&auto=format&fit=crop", "Đã đón về", todayStr + " 16:15", "Phạm Hồng Phước", "0976543210", "Đã đón về"));
            localBackupList.add(new AttendanceLog("HS005", "Hoàng Đức Nam", "Lá 3", "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=150&auto=format&fit=crop", "Bé đến muộn", todayStr + " 08:35", "Hoàng Văn Khánh", "0965432109", "Đang ở lớp"));
            localBackupList.add(new AttendanceLog("HS006", "Hoàng Mỹ Linh", "Lá 3", "https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=150&auto=format&fit=crop", "Bé đến lớp", todayStr + " 07:45", "Hoàng Kim Liên", "0965432111", "Đang ở lớp"));
        }
        try {
            DatabaseReference ref = FirebaseConfig.getDatabase().getReference("logs");
            ref.addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {
                    if (dataSnapshot.exists()) {
                        localBackupList.clear();
                        for (DataSnapshot postSnapshot : dataSnapshot.getChildren()) {
                            AttendanceLog log = postSnapshot.getValue(AttendanceLog.class);
                            if (log != null) {
                                localBackupList.add(log);
                            }
                        }
                    } else {
                        // Khởi chạy dữ liệu mẫu lên Firebase
                        for (AttendanceLog log : localBackupList) {
                            ref.child(log.getId() + "_" + System.currentTimeMillis()).setValueAsync(log);
                        }
                    }
                }

                @Override
                public void onCancelled(DatabaseError databaseError) {
                    System.err.println("Firebase Log sync cancelled: " + databaseError.getMessage());
                }
            });
        } catch (Exception e) {
            System.err.println("Không thể thiết lập lắng nghe Logs trên Firebase: " + e.getMessage());
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
