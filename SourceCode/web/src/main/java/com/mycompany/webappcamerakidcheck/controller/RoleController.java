package com.mycompany.webappcamerakidcheck.controller;

import java.io.IOException;
import java.io.PrintWriter;
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
import com.mycompany.webappcamerakidcheck.model.RoleConfig;

@WebServlet(name = "RoleController", urlPatterns = {"/api/roles"})
public class RoleController extends HttpServlet {

    private static volatile RoleConfig roleConfig = new RoleConfig();
    private static volatile boolean useRestFallback = true; // Force REST fallback always
    private final Gson gson = new Gson();

    @Override
    public void init() throws ServletException {
        try {
            DatabaseReference ref = FirebaseConfig.getDatabase().getReference("role_configurations");
            ref.addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {
                    if (dataSnapshot.exists()) {
                        RoleConfig fbConfig = dataSnapshot.getValue(RoleConfig.class);
                        if (fbConfig != null) {
                            roleConfig = fbConfig;
                        }
                    } else {
                        ref.setValueAsync(roleConfig);
                    }
                }

                @Override
                public void onCancelled(DatabaseError databaseError) {
                    System.err.println("Firebase RoleConfig bị từ chối: " + databaseError.getMessage() + " → Chuyển sang REST.");
                    useRestFallback = true;
                    syncRolesRest();
                }
            });
        } catch (Exception e) {
            System.err.println("Không thể thiết lập lắng nghe RoleConfig: " + e.getMessage());
            useRestFallback = true;
            syncRolesRest();
        }
    }

    private void syncRolesRest() {
        try {
            String urlString = FirebaseConfig.getDatabaseUrl() + "/role_configurations.json";
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
                    RoleConfig fbConfig = gson.fromJson(body, RoleConfig.class);
                    if (fbConfig != null) {
                        roleConfig = fbConfig;
                        System.out.println("REST đồng bộ phân quyền thành công.");
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("REST role config sync failed: " + e.getMessage());
        }
    }

    private boolean saveRoleConfigRest(RoleConfig config) {
        try {
            String urlString = FirebaseConfig.getDatabaseUrl() + "/role_configurations.json";
            java.net.URL url = java.net.URI.create(urlString).toURL();
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("PUT");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setDoOutput(true);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            String json = gson.toJson(config);
            try (java.io.OutputStream os = conn.getOutputStream()) {
                byte[] input = json.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int code = conn.getResponseCode();
            return code == 200;
        } catch (Exception e) {
            System.err.println("REST save role config failed: " + e.getMessage());
            return false;
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        if (useRestFallback && (roleConfig.getTeacherClassAssignments() == null || roleConfig.getTeacherClassAssignments().isEmpty())) {
            syncRolesRest();
        }

        try (PrintWriter out = response.getWriter()) {
            out.print(gson.toJson(roleConfig));
            out.flush();
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        try {
            RoleConfig newConfig = gson.fromJson(request.getReader(), RoleConfig.class);
            if (newConfig != null) {
                boolean saved = false;
                if (useRestFallback) {
                    saved = saveRoleConfigRest(newConfig);
                } else {
                    try {
                        DatabaseReference ref = FirebaseConfig.getDatabase().getReference("role_configurations");
                        ref.setValueAsync(newConfig).get();
                        saved = true;
                    } catch (Exception e) {
                        System.err.println("Admin SDK error on save roles: " + e.getMessage());
                        useRestFallback = true;
                        saved = saveRoleConfigRest(newConfig);
                    }
                }

                if (saved) {
                    roleConfig = newConfig;
                    response.setStatus(HttpServletResponse.SC_OK);
                    try (PrintWriter out = response.getWriter()) {
                        out.print(gson.toJson(newConfig));
                    }
                } else {
                    response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                }
            }
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        }
    }
}
