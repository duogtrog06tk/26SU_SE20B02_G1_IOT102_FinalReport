package com.mycompany.webappcamerakidcheck.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.database.FirebaseDatabase;
import java.io.InputStream;

public class FirebaseConfig {
    private static boolean initialized = false;

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
                    .setDatabaseUrl("https://kidcheck-attendance-c5504-default-rtdb.asia-southeast1.firebasedatabase.app") // Đã cập nhật đúng database của bạn
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
