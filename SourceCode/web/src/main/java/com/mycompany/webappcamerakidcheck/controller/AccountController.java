package com.mycompany.webappcamerakidcheck.controller;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;
import com.mycompany.webappcamerakidcheck.config.FirebaseConfig;
import com.mycompany.webappcamerakidcheck.model.Account;

@WebServlet(name = "AccountController", urlPatterns = {"/api/login", "/api/accounts"}, loadOnStartup = 1)
public class AccountController extends HttpServlet {

    private final Gson gson = new Gson();
    private static final String FIREBASE_DB_URL = "https://kidcheckdb-default-rtdb.asia-southeast1.firebasedatabase.app/";
    private static Map<String, Account> localAccounts = new HashMap<>();
    private static boolean useRestFallback = false;

    @Override
    public void init() throws ServletException {
        try {
            DatabaseReference ref = FirebaseConfig.getDatabase().getReference("accounts");
            ref.addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {
                    if (dataSnapshot.exists()) {
                        localAccounts.clear();
                        for (DataSnapshot postSnapshot : dataSnapshot.getChildren()) {
                            try {
                                Account acc = postSnapshot.getValue(Account.class);
                                if (acc != null) {
                                    localAccounts.put(postSnapshot.getKey(), acc);
                                }
                            } catch (Exception e) {}
                        }
                    } else {
                        localAccounts.clear();
                    }

                    // Tự động kiểm tra và khởi tạo tài khoản admin/teacher trực tiếp trên Firebase nếu chưa có
                    boolean hasAdmin = dataSnapshot.exists() && dataSnapshot.hasChild("admin");
                    boolean hasTeacher = dataSnapshot.exists() && dataSnapshot.hasChild("teacher");

                    if (!hasAdmin) {
                        Account admin = new Account("admin@kidcheck.com", "admin123", "admin", "Hiệu Trưởng KidCheck", null);
                        ref.child("admin").setValueAsync(admin);
                        localAccounts.put("admin", admin);
                    }
                    if (!hasTeacher) {
                        Account teacher = new Account("teacher@kidcheck.com", "teacher123", "teacher", "Cô giáo phụ trách", null);
                        ref.child("teacher").setValueAsync(teacher);
                        localAccounts.put("teacher", teacher);
                    }
                }

                @Override
                public void onCancelled(DatabaseError databaseError) {
                    useRestFallback = true;
                    syncAccountsRest();
                }
            });
        } catch (Exception e) {
            useRestFallback = true;
            syncAccountsRest();
        }
    }

    public static void syncAccountsRestStatic() {
        try {
            String urlString = FIREBASE_DB_URL + "accounts.json";
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");

            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                String inputLine;
                StringBuilder sb = new StringBuilder();
                while ((inputLine = in.readLine()) != null) {
                    sb.append(inputLine);
                }
                in.close();

                String body = sb.toString();
                boolean restHasAdmin = false;
                boolean restHasTeacher = false;

                if (body != null && !body.equals("null") && !body.isEmpty()) {
                    java.lang.reflect.Type type = new TypeToken<Map<String, Account>>(){}.getType();
                    Map<String, Account> map = new Gson().fromJson(body, type);
                    if (map != null) {
                        localAccounts.clear();
                        localAccounts.putAll(map);
                        if (map.containsKey("admin")) restHasAdmin = true;
                        if (map.containsKey("teacher")) restHasTeacher = true;
                    }
                } else {
                    localAccounts.clear();
                }

                if (!restHasAdmin) {
                    Account admin = new Account("admin@kidcheck.com", "admin123", "admin", "Hiệu Trưởng KidCheck", null);
                    saveAccountRest("admin", admin);
                }
                if (!restHasTeacher) {
                    Account teacher = new Account("teacher@kidcheck.com", "teacher123", "teacher", "Cô giáo phụ trách", null);
                    saveAccountRest("teacher", teacher);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void syncAccountsRest() {
        syncAccountsRestStatic();
    }
    
    public static void saveAccountRest(String key, Account account) {
        try {
            String urlString = FIREBASE_DB_URL + "accounts/" + key + ".json";
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("PUT");
            conn.setRequestProperty("Content-Type", "application/json; utf-8");
            conn.setDoOutput(true);

            String json = new Gson().toJson(account);
            try (java.io.OutputStream os = conn.getOutputStream()) {
                byte[] input = json.getBytes("utf-8");
                os.write(input, 0, input.length);
            }
            conn.getResponseCode();
            localAccounts.put(key, account);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static Account getAccountByEmail(String email) {
        if (useRestFallback) syncAccountsRestStatic();
        if (email == null) return null;
        String lowerEmail = email.trim().toLowerCase();
        for (Account acc : localAccounts.values()) {
            if (acc.getEmail() != null && acc.getEmail().trim().toLowerCase().equals(lowerEmail)) {
                return acc;
            }
        }
        return null;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        
        if (useRestFallback) syncAccountsRestStatic();
        
        // Nếu có param ?key= thì trả về 1 account cụ thể
        String key = request.getParameter("key");
        if (key != null && !key.isEmpty()) {
            Account acc = localAccounts.get(key);
            try (PrintWriter out = response.getWriter()) {
                out.print(acc != null ? gson.toJson(acc) : "null");
            }
            return;
        }
        
        // Không có param -> trả về toàn bộ accounts (dưới dạng Map)
        try (PrintWriter out = response.getWriter()) {
            out.print(gson.toJson(localAccounts));
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String path = request.getServletPath();
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try (PrintWriter out = response.getWriter()) {
            if ("/api/login".equals(path)) {
                Account loginReq = gson.fromJson(request.getReader(), Account.class);
                if (loginReq == null || loginReq.getEmail() == null || loginReq.getPassword() == null) {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    out.print("{\"error\":\"Thiếu thông tin đăng nhập\"}");
                    return;
                }

                // CƠ CHẾ BẢO ĐẢM TUYỆT ĐỐI CÓ TÀI KHOẢN KHI ĐĂNG NHẬP
                if (!localAccounts.containsKey("admin")) {
                    Account admin = new Account("admin@kidcheck.com", "admin123", "admin", "Hiệu Trưởng KidCheck", null);
                    localAccounts.put("admin", admin);
                    saveAccountRest("admin", admin);
                }
                if (!localAccounts.containsKey("teacher")) {
                    Account teacher = new Account("teacher@kidcheck.com", "teacher123", "teacher", "Cô giáo phụ trách", null);
                    localAccounts.put("teacher", teacher);
                    saveAccountRest("teacher", teacher);
                }

                String inputAccount = loginReq.getEmail().trim().toLowerCase();
                String inputPass = loginReq.getPassword().trim();

                Account matchedAccount = null;
                for (Map.Entry<String, Account> entry : localAccounts.entrySet()) {
                    Account acc = entry.getValue();
                    // Cho phép đăng nhập bằng username (như 'admin') HOẶC email HOẶC SĐT (cùng lưu trong email)
                    if ((inputAccount.equals(entry.getKey().toLowerCase()) || 
                         (acc.getEmail() != null && acc.getEmail().toLowerCase().equals(inputAccount))) &&
                        inputPass.equals(acc.getPassword())) {
                        matchedAccount = acc;
                        break;
                    }
                }

                if (matchedAccount != null) {
                    response.setStatus(HttpServletResponse.SC_OK);
                    out.print(gson.toJson(matchedAccount));
                } else {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    out.print("{\"error\":\"Tài khoản hoặc mật khẩu không chính xác\"}");
                }
            } else if ("/api/accounts".equals(path)) {
                // Tạo tài khoản mới (dành cho tự động đăng ký phụ huynh)
                Account newAcc = gson.fromJson(request.getReader(), Account.class);
                if (newAcc != null && newAcc.getEmail() != null) {
                    String key = newAcc.getEmail().replaceAll("[^a-zA-Z0-9]", "_");
                    if (!useRestFallback) {
                        try {
                            DatabaseReference ref = FirebaseConfig.getDatabase().getReference("accounts").child(key);
                            ref.setValueAsync(newAcc);
                            localAccounts.put(key, newAcc);
                        } catch (Exception e) {
                            saveAccountRest(key, newAcc);
                        }
                    } else {
                        saveAccountRest(key, newAcc);
                    }
                    response.setStatus(HttpServletResponse.SC_OK);
                    out.print(gson.toJson(newAcc));
                }
            }
        }
    }
}
