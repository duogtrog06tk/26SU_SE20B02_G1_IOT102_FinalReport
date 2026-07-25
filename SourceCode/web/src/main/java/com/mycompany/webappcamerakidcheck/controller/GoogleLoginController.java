package com.mycompany.webappcamerakidcheck.controller;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mycompany.webappcamerakidcheck.model.Account;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@WebServlet("/api/google-login")
public class GoogleLoginController extends HttpServlet {

    private static final String CLIENT_ID = System.getenv("GOOGLE_CLIENT_ID") != null ? System.getenv("GOOGLE_CLIENT_ID") : "93530020887-v6tfsdr94ao2v8a20fsfedkjmsdofalk.apps.googleusercontent.com";
    private static final String CLIENT_SECRET = System.getenv("GOOGLE_CLIENT_SECRET") != null ? System.getenv("GOOGLE_CLIENT_SECRET") : "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";
    
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final Gson gson = new Gson();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String code = request.getParameter("code");

        String serverName = request.getServerName();
        int serverPort = request.getServerPort();
        String contextPath = request.getContextPath();
        String redirectUri = request.getScheme() + "://" + serverName + ":" + serverPort + contextPath + "/api/google-login";

        // Bước 1: Redirect sang Google để lấy code
        if (code == null || code.trim().isEmpty()) {
            String authorizationUrl = "https://accounts.google.com/o/oauth2/auth?"
                    + "scope=" + URLEncoder.encode("email profile", StandardCharsets.UTF_8)
                    + "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8)
                    + "&response_type=code"
                    + "&client_id=" + CLIENT_ID
                    + "&approval_prompt=force";
            response.sendRedirect(authorizationUrl);
            return;
        }

        try {
            // Bước 2: Đổi code lấy Access Token
            String tokenUrl = "https://oauth2.googleapis.com/token";
            String requestBody = "code=" + URLEncoder.encode(code, StandardCharsets.UTF_8) +
                                 "&client_id=" + URLEncoder.encode(CLIENT_ID, StandardCharsets.UTF_8) +
                                 "&client_secret=" + URLEncoder.encode(CLIENT_SECRET, StandardCharsets.UTF_8) +
                                 "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8) +
                                 "&grant_type=authorization_code";

            HttpRequest tokenRequest = HttpRequest.newBuilder()
                    .uri(URI.create(tokenUrl))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> tokenResponse = httpClient.send(tokenRequest, HttpResponse.BodyHandlers.ofString());

            if (tokenResponse.statusCode() != 200) {
                System.err.println("Google OAuth token exchange failed: " + tokenResponse.body());
                response.sendRedirect(contextPath + "/index.html?google_error=token_exchange_error");
                return;
            }

            JsonObject jsonToken = gson.fromJson(tokenResponse.body(), JsonObject.class);
            String accessToken = jsonToken.get("access_token").getAsString();

            // Bước 3: Lấy thông tin User Profile
            String userInfoUrl = "https://www.googleapis.com/oauth2/v3/userinfo";
            HttpRequest profileRequest = HttpRequest.newBuilder()
                    .uri(URI.create(userInfoUrl))
                    .header("Authorization", "Bearer " + accessToken)
                    .GET()
                    .build();

            HttpResponse<String> profileResponse = httpClient.send(profileRequest, HttpResponse.BodyHandlers.ofString());

            if (profileResponse.statusCode() != 200) {
                System.err.println("Google Profile info request failed: " + profileResponse.body());
                response.sendRedirect(contextPath + "/index.html?google_error=profile_retrieval_error");
                return;
            }

            JsonObject profileJson = gson.fromJson(profileResponse.body(), JsonObject.class);
            String email = profileJson.has("email") ? profileJson.get("email").getAsString() : "";
            
            if (email == null || email.isEmpty()) {
                response.sendRedirect(contextPath + "/index.html?google_error=no_email");
                return;
            }

            com.mycompany.webappcamerakidcheck.service.AccountService accountService = new com.mycompany.webappcamerakidcheck.service.AccountService();
            Account acc = accountService.getAccountByEmail(email);

            if (acc == null) {
                // Theo yêu cầu, nếu chưa có tài khoản trên Firebase thì sẽ báo lỗi không cho truy cập
                response.sendRedirect(contextPath + "/index.html?google_error=not_registered");
                return;
            }

            // Bước 5: Đăng nhập thành công, tạo dữ liệu session an toàn
            request.getSession().setAttribute("user_session", acc);
            
            // Redirect về trang chủ với tham số google_auth=session_set (để JS gọi API /api/me)
            response.sendRedirect(contextPath + "/index.html?google_auth=session_set");

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Google login interrupted: " + e.getMessage());
            response.sendRedirect(contextPath + "/index.html?google_error=interrupted");
        } catch (Exception e) {
            System.err.println("Google login exception: " + e.getMessage());
            response.sendRedirect(contextPath + "/index.html?google_error=exception");
        }
    }
}
