package com.mycompany.webappcamerakidcheck.repository;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.mycompany.webappcamerakidcheck.config.FirebaseConfig;
import com.mycompany.webappcamerakidcheck.model.Account;
import com.google.firebase.database.DatabaseReference;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class AccountRepository {
    private final Gson gson = new Gson();

    public Map<String, Account> getAllAccountsRest() {
        Map<String, Account> accounts = new HashMap<>();
        try {
            String urlString = FirebaseConfig.getDatabaseUrl() + "/accounts.json";
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
                    java.lang.reflect.Type type = new TypeToken<Map<String, Account>>(){}.getType();
                    Map<String, Account> map = gson.fromJson(body, type);
                    if (map != null) {
                        accounts.putAll(map);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("AccountRepository GET failed: " + e.getMessage());
        }
        return accounts;
    }

    public boolean saveAccountRest(String key, Account account) {
        try {
            String urlString = FirebaseConfig.getDatabaseUrl() + "/accounts/" + key + ".json";
            URL url = URI.create(urlString).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("PUT");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setDoOutput(true);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            String json = gson.toJson(account);
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = json.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }
            return conn.getResponseCode() == 200;
        } catch (Exception e) {
            System.err.println("AccountRepository PUT failed: " + e.getMessage());
            return false;
        }
    }

    public void saveAccountAdminSdkAsync(String key, Account account) throws Exception {
        DatabaseReference ref = FirebaseConfig.getDatabase().getReference("accounts").child(key);
        ref.setValueAsync(account).get(); // blocking to catch errors
    }
}
