package com.mycompany.webappcamerakidcheck.controller;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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
import com.mycompany.webappcamerakidcheck.model.SupportRequest;

/**
 * Controller xử lý Yêu cầu Hỗ trợ từ phụ huynh / khách.
 * - GET  /api/support         → Trả về danh sách tất cả yêu cầu (JSON)
 * - POST /api/support         → Tạo yêu cầu mới (JSON body)
 * - PUT  /api/support?id=xxx  → Cập nhật trạng thái yêu cầu
 */
@WebServlet(name = "SupportController", urlPatterns = {"/api/support"})
public class SupportController extends HttpServlet {

    private static final List<SupportRequest> requestList = new ArrayList<>();
    private final Gson gson = new Gson();

    @Override
    public void init() throws ServletException {
        try {
            DatabaseReference ref = FirebaseConfig.getDatabase().getReference("support_requests");
            ref.addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {
                    if (dataSnapshot.exists()) {
                        requestList.clear();
                        for (DataSnapshot snap : dataSnapshot.getChildren()) {
                            SupportRequest req = snap.getValue(SupportRequest.class);
                            if (req != null) {
                                requestList.add(req);
                            }
                        }
                    }
                }

                @Override
                public void onCancelled(DatabaseError databaseError) {
                    System.err.println("Firebase Support sync cancelled: " + databaseError.getMessage());
                }
            });
        } catch (Exception e) {
            System.err.println("Không thể thiết lập lắng nghe Support Requests trên Firebase: " + e.getMessage());
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        try (PrintWriter out = response.getWriter()) {
            out.print(gson.toJson(requestList));
            out.flush();
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        try {
            SupportRequest req = gson.fromJson(request.getReader(), SupportRequest.class);
            if (req != null) {
                // Gán ID tự động
                req.setId("SR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
                req.setStatus("new");

                try {
                    DatabaseReference ref = FirebaseConfig.getDatabase().getReference("support_requests");
                    ref.push().setValueAsync(req);
                } catch (Exception e) {
                    // Fallback: lưu vào bộ nhớ tạm nếu Firebase không khả dụng
                    requestList.add(req);
                }

                response.setStatus(HttpServletResponse.SC_CREATED);
                try (PrintWriter out = response.getWriter()) {
                    out.print(gson.toJson(req));
                }
            }
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            try (PrintWriter out = response.getWriter()) {
                out.print("{\"error\":\"" + e.getMessage() + "\"}");
            }
        }
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        String targetId = request.getParameter("id");
        String newStatus = request.getParameter("status");
        if (targetId == null || newStatus == null) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        for (SupportRequest req : requestList) {
            if (targetId.equals(req.getId())) {
                req.setStatus(newStatus);
                try {
                    // Cập nhật trên Firebase
                    DatabaseReference ref = FirebaseConfig.getDatabase().getReference("support_requests");
                    ref.orderByChild("id").equalTo(targetId).addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(DataSnapshot dataSnapshot) {
                            for (DataSnapshot snap : dataSnapshot.getChildren()) {
                                snap.getRef().child("status").setValueAsync(newStatus);
                            }
                        }
                        @Override
                        public void onCancelled(DatabaseError databaseError) {}
                    });
                } catch (Exception ignored) {}
                break;
            }
        }
        response.setStatus(HttpServletResponse.SC_OK);
        try (PrintWriter out = response.getWriter()) {
            out.print("{\"success\":true}");
        }
    }
}
