package com.mycompany.webappcamerakidcheck.controller;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.google.gson.Gson;
import com.mycompany.webappcamerakidcheck.model.SchoolClass;
import com.mycompany.webappcamerakidcheck.service.ClassService;

@WebServlet(name = "ClassController", urlPatterns = {"/api/classes"})
public class ClassController extends HttpServlet {

    private final Gson gson = new Gson();
    private final ClassService classService = new ClassService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        setCorsHeaders(response);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        
        List<SchoolClass> classes = classService.getAllClasses();
        try (PrintWriter out = response.getWriter()) {
            out.print(gson.toJson(classes));
            out.flush();
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
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        try {
            SchoolClass newClass = gson.fromJson(request.getReader(), SchoolClass.class);
            if (newClass != null && newClass.getName() != null) {
                try {
                    boolean saved = classService.addClass(newClass);
                    if (saved) {
                        response.setStatus(HttpServletResponse.SC_CREATED);
                        try (PrintWriter out = response.getWriter()) {
                            out.print(gson.toJson(newClass));
                        }
                    } else {
                        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                        try (PrintWriter out = response.getWriter()) {
                            out.print("{\"error\":\"Không thể lưu lớp học\"}");
                        }
                    }
                } catch (IllegalArgumentException e) {
                    response.setStatus(HttpServletResponse.SC_CONFLICT);
                    try (PrintWriter out = response.getWriter()) {
                        out.print("{\"error\":\"" + e.getMessage() + "\"}");
                    }
                }
            } else {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            }
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        }
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        setCorsHeaders(response);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        String className = request.getParameter("name");
        if (className != null && !className.isEmpty()) {
            boolean deleted = classService.deleteClass(className);
            if (deleted) {
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

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        setCorsHeaders(response);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        try {
            SchoolClass updatedClass = gson.fromJson(request.getReader(), SchoolClass.class);
            if (updatedClass != null && updatedClass.getName() != null) {
                boolean updated = classService.updateClass(updatedClass);
                if (updated) {
                    response.setStatus(HttpServletResponse.SC_OK);
                    try (PrintWriter out = response.getWriter()) {
                        out.print(gson.toJson(updatedClass));
                    }
                } else {
                    response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                }
            } else {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            }
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        }
    }
}
