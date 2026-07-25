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
import com.mycompany.webappcamerakidcheck.model.Student;
import com.mycompany.webappcamerakidcheck.service.StudentService;

@WebServlet(name = "StudentController", urlPatterns = { "/api/students", "/api/reset-attendance" })
public class StudentController extends HttpServlet {

    private final Gson gson = new Gson();
    private final StudentService studentService = new StudentService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        setCorsHeaders(response);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        List<Student> students = studentService.getAllStudents();
        try (PrintWriter out = response.getWriter()) {
            out.print(gson.toJson(students));
            out.flush();
        }
    }

    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        setCorsHeaders(response);
        response.setStatus(HttpServletResponse.SC_OK);
    }

    private void setCorsHeaders(HttpServletResponse response) {
        // FIXME: Update this in production to match your exact frontend domain instead of '*'
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
        
        String path = request.getRequestURI();
        if (path.endsWith("/reset-attendance")) {
            studentService.resetAllAttendance();
            response.setStatus(HttpServletResponse.SC_OK);
            try (PrintWriter out = response.getWriter()) {
                out.print("{\"success\":true, \"message\":\"Đã reset điểm danh cho ngày mới\"}");
            }
            return;
        }

        try {
            Student newStudent = gson.fromJson(request.getReader(), Student.class);
            if (newStudent != null && newStudent.getId() != null) {
                try {
                    boolean saved = studentService.addStudent(newStudent);
                    if (saved) {
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
    protected void doPut(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        setCorsHeaders(response);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        
        try {
            Student updateData = gson.fromJson(request.getReader(), Student.class);
            if (updateData != null && updateData.getId() != null) {
                boolean updated = studentService.updateStudent(updateData);
                if (updated) {
                    response.setStatus(HttpServletResponse.SC_OK);
                    try (PrintWriter out = response.getWriter()) {
                        out.print(gson.toJson(updateData));
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

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        setCorsHeaders(response);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        
        String studentId = request.getParameter("id");
        if (studentId != null && !studentId.isEmpty()) {
            boolean deleted = studentService.deleteStudent(studentId);
            if (deleted) {
                try (PrintWriter out = response.getWriter()) {
                    out.print("{\"success\":true}");
                }
            } else {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            }
            return;
        }
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
    }
}
