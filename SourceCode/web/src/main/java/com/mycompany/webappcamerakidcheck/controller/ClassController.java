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
import com.mycompany.webappcamerakidcheck.model.SchoolClass;

@WebServlet(name = "ClassController", urlPatterns = {"/api/classes"})
public class ClassController extends HttpServlet {

    private static final List<SchoolClass> localBackupList = new ArrayList<>();
    private final Gson gson = new Gson();
    private static final String FIREBASE_DB_URL = "https://kidcheckdb-default-rtdb.asia-southeast1.firebasedatabase.app/";

    @Override
    public void init() throws ServletException {
        // Bước 1: Khởi tạo danh sách lớp mẫu vào bộ nhớ local trước (fallback tức thì)
        if (localBackupList.isEmpty()) {
            localBackupList.add(new SchoolClass("Mầm 1", "Trẻ từ 3 đến 4 tuổi", "07:30", "08:30", "16:30"));
            localBackupList.add(new SchoolClass("Chồi 2", "Trẻ từ 4 đến 5 tuổi", "07:30", "08:30", "16:30"));
            localBackupList.add(new SchoolClass("Lá 3", "Trẻ từ 5 đến 6 tuổi", "07:30", "08:30", "16:30"));
        }

        // Bước 2: Thử đồng bộ với Firebase qua REST trước (nhanh và đáng tin cậy hơn trong init)
        try {
            boolean hasFirebaseData = syncClassesRestWithReturn();
            if (!hasFirebaseData) {
                // Firebase chưa có dữ liệu → Push toàn bộ localBackupList lên Firebase bằng REST
                System.out.println("[ClassController] Firebase classes trống → Đang khởi tạo dữ liệu mẫu lên Firebase...");
                for (SchoolClass c : new java.util.ArrayList<>(localBackupList)) {
                    boolean ok = saveClassRest(c);
                    System.out.println("[ClassController] Push class '" + c.getName() + "' → " + (ok ? "✅ Thành công" : "❌ Thất bại"));
                }
            } else {
                System.out.println("[ClassController] Đã tải " + localBackupList.size() + " lớp từ Firebase REST.");
            }
        } catch (Exception e) {
            System.err.println("[ClassController] REST init sync failed, giữ dữ liệu local: " + e.getMessage());
        }

        // Bước 3: Đăng ký Firebase Admin SDK listener để cập nhật realtime về sau
        try {
            DatabaseReference ref = FirebaseConfig.getDatabase().getReference("classes");
            ref.addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {
                    if (dataSnapshot.exists() && dataSnapshot.getChildrenCount() > 0) {
                        // Firebase có dữ liệu → cập nhật localBackupList
                        java.util.List<SchoolClass> freshList = new java.util.ArrayList<>();
                        for (DataSnapshot postSnapshot : dataSnapshot.getChildren()) {
                            SchoolClass schoolClass = postSnapshot.getValue(SchoolClass.class);
                            if (schoolClass != null) {
                                // Bảo đảm key từ Firebase được gán vào name nếu thiếu
                                if (schoolClass.getName() == null || schoolClass.getName().isEmpty()) {
                                    schoolClass.setName(postSnapshot.getKey());
                                }
                                freshList.add(schoolClass);
                            }
                        }
                        if (!freshList.isEmpty()) {
                            localBackupList.clear();
                            localBackupList.addAll(freshList);
                            System.out.println("[ClassController] Realtime sync: " + freshList.size() + " lớp từ Firebase Admin SDK.");
                        }
                    } else {
                        // Firebase trống → dùng thread riêng để push (tránh block Firebase callback thread)
                        System.out.println("[ClassController] Firebase classes trống, đang push dữ liệu mẫu lên (background)...");
                        final java.util.List<SchoolClass> toUpload = new java.util.ArrayList<>(localBackupList);
                        new Thread(() -> {
                            for (SchoolClass c : toUpload) {
                                boolean ok = saveClassRest(c);
                                System.out.println("[ClassController] BG Push '" + c.getName() + "' → " + (ok ? "✅" : "❌"));
                                // Cũng push qua Admin SDK
                                ref.child(c.getName()).setValueAsync(c);
                            }
                        }, "firebase-class-bootstrap").start();
                    }
                }

                @Override
                public void onCancelled(DatabaseError databaseError) {
                    System.err.println("[ClassController] Firebase Admin SDK cancelled: " + databaseError.getMessage());
                }
            });
        } catch (Exception e) {
            System.err.println("[ClassController] Không thể thiết lập Admin SDK listener: " + e.getMessage());
        }
    }

    private boolean saveClassRest(SchoolClass schoolClass) {
        try {
            String urlString = FIREBASE_DB_URL + "classes/" + java.net.URLEncoder.encode(schoolClass.getName(), "UTF-8").replace("+", "%20") + ".json";
            java.net.URL url = java.net.URI.create(urlString).toURL();
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("PUT");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setDoOutput(true);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            String json = gson.toJson(schoolClass);
            try (java.io.OutputStream os = conn.getOutputStream()) {
                byte[] input = json.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int code = conn.getResponseCode();
            System.out.println("REST saveClass " + schoolClass.getName() + " → HTTP " + code);
            return code == 200;
        } catch (Exception e) {
            System.err.println("REST save class failed: " + e.getMessage());
            return false;
        }
    }

    private boolean deleteClassRest(String className) {
        try {
            String urlString = FIREBASE_DB_URL + "classes/" + java.net.URLEncoder.encode(className, "UTF-8").replace("+", "%20") + ".json";
            java.net.URL url = java.net.URI.create(urlString).toURL();
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("DELETE");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            int code = conn.getResponseCode();
            System.out.println("REST deleteClass " + className + " → HTTP " + code);
            return code == 200;
        } catch (Exception e) {
            System.err.println("REST delete class failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Đồng bộ danh sách lớp từ Firebase REST API.
     * @return true nếu Firebase có dữ liệu và đã cập nhật localBackupList;
     *         false nếu Firebase trống/null hoặc lỗi mạng.
     */
    private boolean syncClassesRestWithReturn() {
        try {
            String urlString = FIREBASE_DB_URL + "classes.json";
            java.net.URL url = java.net.URI.create(urlString).toURL();
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            int code = conn.getResponseCode();
            if (code == 200) {
                java.io.InputStream is = conn.getInputStream();
                java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(is, java.nio.charset.StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);

                String body = sb.toString().trim();
                if (!body.isEmpty() && !body.equals("null")) {
                    java.lang.reflect.Type type = new com.google.gson.reflect.TypeToken<java.util.Map<String, SchoolClass>>(){}.getType();
                    java.util.Map<String, SchoolClass> map = gson.fromJson(body, type);
                    if (map != null && !map.isEmpty()) {
                        // Gán name từ key Firebase nếu bị thiếu (Firebase lưu theo key)
                        java.util.List<SchoolClass> fetched = new java.util.ArrayList<>();
                        map.forEach((key, cls) -> {
                            if (cls != null) {
                                if (cls.getName() == null || cls.getName().isEmpty()) cls.setName(key);
                                fetched.add(cls);
                            }
                        });
                        localBackupList.clear();
                        localBackupList.addAll(fetched);
                        return true; // Firebase có dữ liệu
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[ClassController] REST GET classes failed: " + e.getMessage());
        }
        return false; // Firebase trống hoặc lỗi
    }

    // Giữ lại tên cũ để tương thích (wrapper)
    private void syncClassesRest() {
        syncClassesRestWithReturn();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        // Chỉ cập nhật localBackupList khi Firebase thực sự có dữ liệu
        // (tránh xóa data đã có khi Firebase tạm thời trả về null)
        syncClassesRestWithReturn();
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
            SchoolClass newClass = gson.fromJson(request.getReader(), SchoolClass.class);
            if (newClass != null && newClass.getName() != null) {
                boolean exists = localBackupList.stream().anyMatch(c -> c.getName().equalsIgnoreCase(newClass.getName()));
                if (exists) {
                    response.setStatus(HttpServletResponse.SC_CONFLICT);
                    try (PrintWriter out = response.getWriter()) {
                        out.print("{\"error\":\"Lớp học đã tồn tại!\"}");
                    }
                    return;
                }
                
                saveClassRest(newClass);
                try {
                    DatabaseReference ref = FirebaseConfig.getDatabase().getReference("classes");
                    ref.child(newClass.getName()).setValueAsync(newClass);
                } catch (Exception e) {
                    System.err.println("Admin SDK save class error: " + e.getMessage());
                }

                localBackupList.add(newClass);

                response.setStatus(HttpServletResponse.SC_CREATED);
                try (PrintWriter out = response.getWriter()) {
                    out.print(gson.toJson(newClass));
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
        String className = request.getParameter("name");
        if (className != null) {
            localBackupList.removeIf(c -> c.getName().equalsIgnoreCase(className));
            deleteClassRest(className);
            try {
                DatabaseReference ref = FirebaseConfig.getDatabase().getReference("classes");
                ref.child(className).removeValueAsync();
            } catch (Exception e) {
                System.err.println("Admin SDK delete class error: " + e.getMessage());
            }
            try (PrintWriter out = response.getWriter()) {
                out.print("{\"success\":true}");
            }
            return;
        }
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        try {
            SchoolClass updatedClass = gson.fromJson(request.getReader(), SchoolClass.class);
            if (updatedClass != null && updatedClass.getName() != null) {
                // Cập nhật trong local list
                boolean found = false;
                for (int i = 0; i < localBackupList.size(); i++) {
                    if (localBackupList.get(i).getName().equalsIgnoreCase(updatedClass.getName())) {
                        localBackupList.set(i, updatedClass);
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    localBackupList.add(updatedClass);
                }
                
                saveClassRest(updatedClass);
                try {
                    DatabaseReference ref = FirebaseConfig.getDatabase().getReference("classes");
                    ref.child(updatedClass.getName()).setValueAsync(updatedClass);
                } catch (Exception e) {
                    // Firebase offline
                }

                response.setStatus(HttpServletResponse.SC_OK);
                try (PrintWriter out = response.getWriter()) {
                    out.print(gson.toJson(updatedClass));
                }
            }
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        }
    }
}
