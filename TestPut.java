import java.net.*;
import java.io.*;
public class TestPut {
    public static void main(String[] args) throws Exception {
        String urlString = "https://kidcheckdb-default-rtdb.asia-southeast1.firebasedatabase.app/students/HS999.json";
        URL url = URI.create(urlString).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("PUT");
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        conn.setDoOutput(true);
        String json = "{\"id\":\"HS999\",\"name\":\"Test Name\"}";
        try (OutputStream os = conn.getOutputStream()) {
            byte[] input = json.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }
        int code = conn.getResponseCode();
        System.out.println("HTTP Code: " + code);
    }
}
