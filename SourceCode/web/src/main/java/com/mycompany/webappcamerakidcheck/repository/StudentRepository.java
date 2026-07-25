package com.mycompany.webappcamerakidcheck.repository;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.mycompany.webappcamerakidcheck.config.FirebaseConfig;
import com.mycompany.webappcamerakidcheck.model.Student;
import com.google.firebase.database.DatabaseReference;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StudentRepository {
    private final Gson gson = new Gson();

    public List<Student> getAllStudentsRest() {
        List<Student> students = new ArrayList<>();
        try {
            String urlString = FirebaseConfig.getDatabaseUrl() + "/students.json";
            URL url = URI.create(urlString).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            if (conn.getResponseCode() == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                
                String body = sb.toString();
                if (body != null && !body.equals("null") && !body.isEmpty()) {
                    java.lang.reflect.Type type = new TypeToken<Map<String, Student>>(){}.getType();
                    Map<String, Student> map = gson.fromJson(body, type);
                    if (map != null) {
                        students.addAll(map.values());
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("StudentRepository GET failed: " + e.getMessage());
        }
        return students;
    }

    public boolean saveStudentRest(Student student) {
        try {
            String urlString = FirebaseConfig.getDatabaseUrl() + "/students/" + student.getId() + ".json";
            URL url = URI.create(urlString).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("PUT");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setDoOutput(true);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            String json = gson.toJson(student);
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = json.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }
            return conn.getResponseCode() == 200;
        } catch (Exception e) {
            System.err.println("StudentRepository PUT failed: " + e.getMessage());
            return false;
        }
    }

    public boolean deleteStudentRest(String studentId) {
        try {
            String urlString = FirebaseConfig.getDatabaseUrl() + "/students/" + studentId + ".json";
            URL url = URI.create(urlString).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("DELETE");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            return conn.getResponseCode() == 200;
        } catch (Exception e) {
            System.err.println("StudentRepository DELETE failed: " + e.getMessage());
            return false;
        }
    }

    public void deleteAttendanceLogsRest(String studentId) {
        try {
            String attUrl = FirebaseConfig.getDatabaseUrl() + "/attendance.json?shallow=true";
            URL url = URI.create(attUrl).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            if (conn.getResponseCode() != 200) return;

            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            reader.close();

            String body = sb.toString();
            if (body == null || body.equals("null") || body.isEmpty()) return;

            Pattern datePattern = Pattern.compile("\"(\\d{4}-\\d{2}-\\d{2})\"");
            Matcher matcher = datePattern.matcher(body);

            while (matcher.find()) {
                String dateKey = matcher.group(1);
                String deleteUrl = FirebaseConfig.getDatabaseUrl() + "/attendance/" + dateKey + "/" + studentId + ".json";
                try {
                    URL delUrl = URI.create(deleteUrl).toURL();
                    HttpURLConnection delConn = (HttpURLConnection) delUrl.openConnection();
                    delConn.setRequestMethod("DELETE");
                    delConn.setConnectTimeout(3000);
                    delConn.setReadTimeout(3000);
                    delConn.getResponseCode();
                } catch (Exception e) {
                    System.err.println("Lỗi xóa log ngày " + dateKey + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("Lỗi xóa attendance logs: " + e.getMessage());
        }
    }

    public void saveStudentAdminSdkAsync(Student student) throws Exception {
        DatabaseReference ref = FirebaseConfig.getDatabase().getReference("students");
        ref.child(student.getId()).setValueAsync(student).get(); // blocking to catch errors
    }

    public void deleteStudentAdminSdkAsync(String studentId) throws Exception {
        DatabaseReference ref = FirebaseConfig.getDatabase().getReference("students");
        ref.child(studentId).removeValueAsync().get();
    }
}
