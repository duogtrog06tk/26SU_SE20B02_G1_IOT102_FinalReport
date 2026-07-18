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
import com.mycompany.webappcamerakidcheck.model.Student;

@WebServlet(name = "StudentController", urlPatterns = { "/api/students" })
public class StudentController extends HttpServlet {

    private static final List<Student> localBackupList = new ArrayList<>();
    private static volatile boolean useRestFallback = true; // Force REST fallback always
    private final Gson gson = new Gson();

    private static final String FIREBASE_DB_URL = "https://kidcheckdb-default-rtdb.asia-southeast1.firebasedatabase.app/";

    @Override
    public void init() throws ServletException {
        try {
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
                        System.out.println("Firebase Admin SDK đồng bộ thành công: " + localBackupList.size() + " bé.");
                    } else {
                        localBackupList.clear();
                    }
                }

                @Override
                public void onCancelled(DatabaseError databaseError) {
                    // Admin SDK bị Unauthorized → chuyển sang REST vĩnh viễn
                    System.err.println("Firebase Admin SDK bị từ chối: " + databaseError.getMessage() + " → Chuyển sang REST Fallback.");
                    useRestFallback = true;
                    syncStudentsRest();
                }
            });
        } catch (Exception e) {
            System.err.println("Không thể thiết lập lắng nghe Firebase: " + e.getMessage());
            useRestFallback = true;
            syncStudentsRest();
        }
    }

    private void syncStudentsRest() {
        try {
            String urlString = FIREBASE_DB_URL + "students.json";
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
                    java.lang.reflect.Type type = new com.google.gson.reflect.TypeToken<java.util.Map<String, Student>>(){}.getType();
                    java.util.Map<String, Student> map = gson.fromJson(body, type);
                    if (map != null) {
                        localBackupList.clear();
                        localBackupList.addAll(map.values());
                        System.out.println("REST đồng bộ danh sách học sinh thành công: " + localBackupList.size() + " bé.");
                    }
                } else {
                    System.out.println("REST: Chưa có học sinh nào trong Firebase.");
                }
            }
        } catch (Exception e) {
            System.err.println("REST sync failed: " + e.getMessage());
        }
    }

    private boolean saveStudentRest(Student student) {
        try {
            String urlString = FIREBASE_DB_URL + "students/" + student.getId() + ".json";
            java.net.URL url = java.net.URI.create(urlString).toURL();
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("PUT");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setDoOutput(true);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            String json = gson.toJson(student);
            try (java.io.OutputStream os = conn.getOutputStream()) {
                byte[] input = json.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int code = conn.getResponseCode();
            System.out.println("REST saveStudent " + student.getId() + " → HTTP " + code);
            return code == 200;
        } catch (Exception e) {
            System.err.println("REST save student failed: " + e.getMessage());
            return false;
        }
    }

    private boolean deleteStudentRest(String studentId) {
        try {
            String urlString = FIREBASE_DB_URL + "students/" + studentId + ".json";
            java.net.URL url = java.net.URI.create(urlString).toURL();
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("DELETE");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            int code = conn.getResponseCode();
            System.out.println("REST deleteStudent " + studentId + " → HTTP " + code);
            return code == 200;
        } catch (Exception e) {
            System.err.println("REST delete student failed: " + e.getMessage());
            return false;
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        if (localBackupList.isEmpty()) {
            syncStudentsRest();
        }

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
                if (localBackupList.isEmpty()) {
                    syncStudentsRest();
                }
                boolean exists = localBackupList.stream().anyMatch(s -> s.getId().equalsIgnoreCase(newStudent.getId()));
                if (exists) {
                    response.setStatus(HttpServletResponse.SC_CONFLICT);
                    try (PrintWriter out = response.getWriter()) {
                        out.print("{\"error\":\"Mã học sinh đã tồn tại!\"}");
                    }
                    return;
                }

                // Luôn dùng REST nếu Admin SDK bị Unauthorized
                boolean saved = false;
                if (useRestFallback) {
                    saved = saveStudentRest(newStudent);
                } else {
                    try {
                        DatabaseReference ref = FirebaseConfig.getDatabase().getReference("students");
                        ref.child(newStudent.getId()).setValueAsync(newStudent).get(); // BLOCKING call to catch Unauthorized
                        saved = true;
                    } catch (Exception e) {
                        System.err.println("Admin SDK error on save: " + e.getMessage());
                        useRestFallback = true;
                        saved = saveStudentRest(newStudent);
                    }
                }

                if (saved) {
                    localBackupList.add(newStudent);
                    response.setStatus(HttpServletResponse.SC_CREATED);
                    try (PrintWriter out = response.getWriter()) {
                        out.print(gson.toJson(newStudent));
                    }
                } else {
                    response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                    try (PrintWriter out = response.getWriter()) {
                        out.print("{\"error\":\"Không thể lưu lên Firebase!\"}");
                    }
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
                boolean updated = false;
                if (useRestFallback) {
                    updated = saveStudentRest(updateData);
                } else {
                    try {
                        DatabaseReference ref = FirebaseConfig.getDatabase().getReference("students");
                        ref.child(updateData.getId()).setValueAsync(updateData).get();
                        updated = true;
                    } catch (Exception e) {
                        System.err.println("Admin SDK error on update: " + e.getMessage());
                        useRestFallback = true;
                        updated = saveStudentRest(updateData);
                    }
                }

                if (updated) {
                    localBackupList.removeIf(s -> s.getId().equalsIgnoreCase(updateData.getId()));
                    localBackupList.add(updateData);

                    response.setStatus(HttpServletResponse.SC_OK);
                    try (PrintWriter out = response.getWriter()) {
                        out.print(gson.toJson(updateData));
                    }
                } else {
                    response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
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
            boolean deleted = false;
            if (useRestFallback) {
                deleted = deleteStudentRest(studentId);
            } else {
                try {
                    DatabaseReference ref = FirebaseConfig.getDatabase().getReference("students");
                    ref.child(studentId).removeValueAsync().get();
                    deleted = true;
                } catch (Exception e) {
                    System.err.println("Admin SDK error on delete: " + e.getMessage());
                    useRestFallback = true;
                    deleted = deleteStudentRest(studentId);
                }
            }

            if (deleted) {
                localBackupList.removeIf(s -> s.getId().equalsIgnoreCase(studentId));
                try (PrintWriter out = response.getWriter()) {
                    out.print("{\"success\":true}");
                }
            } else {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            }
            return;
        }
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
    }
}
