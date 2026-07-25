package com.mycompany.webappcamerakidcheck.repository;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.mycompany.webappcamerakidcheck.config.FirebaseConfig;
import com.mycompany.webappcamerakidcheck.model.SchoolClass;
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

public class ClassRepository {
    private final Gson gson = new Gson();

    public List<SchoolClass> getAllClassesRest() {
        List<SchoolClass> classes = new ArrayList<>();
        try {
            String urlString = FirebaseConfig.getDatabaseUrl() + "/classes.json";
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
                if (body != null && !body.equals("null") && !body.trim().isEmpty()) {
                    java.lang.reflect.Type type = new TypeToken<Map<String, SchoolClass>>(){}.getType();
                    Map<String, SchoolClass> map = gson.fromJson(body, type);
                    if (map != null) {
                        map.forEach((key, cls) -> {
                            if (cls != null) {
                                if (cls.getName() == null || cls.getName().isEmpty()) cls.setName(key);
                                classes.add(cls);
                            }
                        });
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("ClassRepository GET failed: " + e.getMessage());
        }
        return classes;
    }

    public boolean saveClassRest(SchoolClass schoolClass) {
        try {
            String urlString = FirebaseConfig.getDatabaseUrl() + "/classes/" + 
                               java.net.URLEncoder.encode(schoolClass.getName(), "UTF-8").replace("+", "%20") + ".json";
            URL url = URI.create(urlString).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("PUT");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setDoOutput(true);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            String json = gson.toJson(schoolClass);
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = json.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }
            return conn.getResponseCode() == 200;
        } catch (Exception e) {
            System.err.println("ClassRepository PUT failed: " + e.getMessage());
            return false;
        }
    }

    public boolean deleteClassRest(String className) {
        try {
            String urlString = FirebaseConfig.getDatabaseUrl() + "/classes/" + 
                               java.net.URLEncoder.encode(className, "UTF-8").replace("+", "%20") + ".json";
            URL url = URI.create(urlString).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("DELETE");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            return conn.getResponseCode() == 200;
        } catch (Exception e) {
            System.err.println("ClassRepository DELETE failed: " + e.getMessage());
            return false;
        }
    }

    public void saveClassAdminSdkAsync(SchoolClass schoolClass) throws Exception {
        DatabaseReference ref = FirebaseConfig.getDatabase().getReference("classes");
        ref.child(schoolClass.getName()).setValueAsync(schoolClass).get(); // blocking to catch errors
    }

    public void deleteClassAdminSdkAsync(String className) throws Exception {
        DatabaseReference ref = FirebaseConfig.getDatabase().getReference("classes");
        ref.child(className).removeValueAsync().get();
    }
}
