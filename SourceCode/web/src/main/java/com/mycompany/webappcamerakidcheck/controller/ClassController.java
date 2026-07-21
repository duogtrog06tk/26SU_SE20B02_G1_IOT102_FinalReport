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

    @Override
    public void init() throws ServletException {
        if (localBackupList.isEmpty()) {
            localBackupList.add(new SchoolClass("Mầm 1", "Trẻ từ 3 đến 4 tuổi"));
            localBackupList.add(new SchoolClass("Chồi 2", "Trẻ từ 4 đến 5 tuổi"));
            localBackupList.add(new SchoolClass("Lá 3", "Trẻ từ 5 đến 6 tuổi"));
        }

        try {
            DatabaseReference ref = FirebaseConfig.getDatabase().getReference("classes");
            ref.addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {
                    if (dataSnapshot.exists()) {
                        localBackupList.clear();
                        for (DataSnapshot postSnapshot : dataSnapshot.getChildren()) {
                            SchoolClass schoolClass = postSnapshot.getValue(SchoolClass.class);
                            if (schoolClass != null) {
                                localBackupList.add(schoolClass);
                            }
                        }
                    } else {
                        // Khởi chạy dữ liệu mẫu lên Firebase
                        for (SchoolClass c : localBackupList) {
                            ref.child(c.getName()).setValueAsync(c);
                        }
                    }
                }

                @Override
                public void onCancelled(DatabaseError databaseError) {
                    System.err.println("Firebase Class sync cancelled: " + databaseError.getMessage());
                }
            });
        } catch (Exception e) {
            System.err.println("Không thể thiết lập lắng nghe Class trên Firebase: " + e.getMessage());
        }
    }

    private static final String FIREBASE_DB_URL = "https://kidcheckdb-default-rtdb.asia-southeast1.firebasedatabase.app/";

    private void syncClassesRest() {
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
                java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(is, java.nio.charset.StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }

                String body = sb.toString();
                if (body != null && !body.equals("null") && !body.isEmpty()) {
                    java.lang.reflect.Type type = new com.google.gson.reflect.TypeToken<java.util.Map<String, SchoolClass>>(){}.getType();
                    java.util.Map<String, SchoolClass> map = gson.fromJson(body, type);
                    if (map != null && !map.isEmpty()) {
                        localBackupList.clear();
                        localBackupList.addAll(map.values());
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("REST Class sync failed: " + e.getMessage());
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        syncClassesRest();
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
                
                try {
                    DatabaseReference ref = FirebaseConfig.getDatabase().getReference("classes");
                    ref.child(newClass.getName()).setValueAsync(newClass);
                } catch (Exception e) {
                    localBackupList.add(newClass);
                }

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
            try {
                DatabaseReference ref = FirebaseConfig.getDatabase().getReference("classes");
                ref.child(className).removeValueAsync();
            } catch (Exception e) {
                localBackupList.removeIf(c -> c.getName().equalsIgnoreCase(className));
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
                for (int i = 0; i < localBackupList.size(); i++) {
                    if (localBackupList.get(i).getName().equalsIgnoreCase(updatedClass.getName())) {
                        localBackupList.set(i, updatedClass);
                        break;
                    }
                }
                
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
