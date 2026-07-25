package com.mycompany.webappcamerakidcheck.controller;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import com.google.gson.Gson;
import com.mycompany.webappcamerakidcheck.model.Account;
import com.mycompany.webappcamerakidcheck.service.AccountService;

@WebServlet(name = "AccountController", urlPatterns = {"/api/login", "/api/accounts", "/api/me"})
public class AccountController extends HttpServlet {

    private final Gson gson = new Gson();
    private final AccountService accountService = new AccountService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        setCorsHeaders(response);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        
        String path = request.getServletPath();
        
        if ("/api/me".equals(path)) {
            HttpSession session = request.getSession(false);
            if (session != null && session.getAttribute("user_session") != null) {
                Account acc = (Account) session.getAttribute("user_session");
                try (PrintWriter out = response.getWriter()) {
                    out.print(gson.toJson(acc));
                }
            } else {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                try (PrintWriter out = response.getWriter()) {
                    out.print("{\"error\":\"Not logged in\"}");
                }
            }
            return;
        }
        
        String key = request.getParameter("key");
        if (key != null && !key.isEmpty()) {
            Map<String, Account> accounts = accountService.getAllAccounts();
            Account acc = accounts.get(key);
            try (PrintWriter out = response.getWriter()) {
                out.print(acc != null ? gson.toJson(acc) : "null");
            }
            return;
        }
        
        Map<String, Account> allAccounts = accountService.getAllAccounts();
        try (PrintWriter out = response.getWriter()) {
            out.print(gson.toJson(allAccounts));
        }
    }

    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        setCorsHeaders(response);
        response.setStatus(HttpServletResponse.SC_OK);
    }

    private void setCorsHeaders(HttpServletResponse response) {
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type");
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        setCorsHeaders(response);
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

                Account matchedAccount = accountService.login(loginReq.getEmail(), loginReq.getPassword());

                if (matchedAccount != null) {
                    request.getSession().setAttribute("user_session", matchedAccount);
                    response.setStatus(HttpServletResponse.SC_OK);
                    out.print(gson.toJson(matchedAccount));
                } else {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    out.print("{\"error\":\"Tài khoản hoặc mật khẩu không chính xác\"}");
                }
            } else if ("/api/accounts".equals(path)) {
                Account newAcc = gson.fromJson(request.getReader(), Account.class);
                if (newAcc != null && newAcc.getEmail() != null) {
                    String key = newAcc.getEmail().replaceAll("[^a-zA-Z0-9]", "_");
                    boolean saved = accountService.saveAccount(key, newAcc);
                    if (saved) {
                        response.setStatus(HttpServletResponse.SC_OK);
                        out.print(gson.toJson(newAcc));
                    } else {
                        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                        out.print("{\"error\":\"Không thể lưu tài khoản\"}");
                    }
                } else {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                }
            }
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        }
    }
}
