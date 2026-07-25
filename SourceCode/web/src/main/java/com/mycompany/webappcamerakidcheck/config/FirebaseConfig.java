package com.mycompany.webappcamerakidcheck.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.database.FirebaseDatabase;
import java.io.InputStream;

public class FirebaseConfig {
    private static boolean initialized = false;
    private static final String DEFAULT_DB_URL = "https://kidcheckdb-default-rtdb.asia-southeast1.firebasedatabase.app";

    public static String getDatabaseUrl() {
        String envUrl = System.getenv("FIREBASE_DB_URL");
        if (envUrl != null && !envUrl.isEmpty()) {
            // Remove trailing slash if exists for consistency
            if (envUrl.endsWith("/")) {
                return envUrl.substring(0, envUrl.length() - 1);
            }
            return envUrl;
        }
        return DEFAULT_DB_URL;
    }

    public static synchronized void initialize() {
        if (initialized) {
            return;
        }
        try {
            // Đọc file credential từ classpath (nằm trong thư mục resources)
            InputStream serviceAccount = FirebaseConfig.class
                    .getClassLoader()
                    .getResourceAsStream("serviceAccountKey.json");

            if (serviceAccount == null) {
                System.err.println("Firebase Config Warning: Không tìm thấy file serviceAccountKey.json trong classpath resources. Hãy đặt file này vào thư mục src/main/resources/");
                return;
            }

            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .setDatabaseUrl(getDatabaseUrl())
                    .build();

            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseApp.initializeApp(options);
            }
            initialized = true;
            System.out.println("Firebase Admin SDK khởi tạo thành công!");
        } catch (Exception e) {
            System.err.println("Lỗi khởi tạo Firebase SDK: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static FirebaseDatabase getDatabase() {
        initialize();
        return FirebaseDatabase.getInstance();
    }
}
