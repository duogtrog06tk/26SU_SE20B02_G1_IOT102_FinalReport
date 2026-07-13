// ============================================================================
// KHỐI 0: THƯ VIỆN HỆ THỐNG, CẤU HÌNH CLOUD VÀ ĐỊNH NGHĨA CHÂN NGOẠI VI
// ============================================================================
#include "esp_camera.h"
#include <WiFi.h>
#include <WiFiClientSecure.h>  // FIX #4: Cần thiết để gọi HTTPS đến Firebase không bị lỗi SSL
#include <HTTPClient.h>
#include <Wire.h>
#include <LiquidCrystal_I2C.h>
#include <ArduinoJson.h>
#include <WebServer.h>
#include <DNSServer.h>  // Hỗ trợ đánh chặn Captive Portal để tự bật trang cấu hình

// --- KHAI BÁO NGUYÊN MẪU HÀM (FIX LỖI NOT DECLARED) ---
void updateApConfigFromFirebase();
void captureAndRecognize();
bool isCaptivePortal();
void handleWifiPortal();
void handleSaveWifi();
bool sendImageToRegister();
void handleCaptureRegister();
void handleLEDControl();
void handleReboot();
bool getAiServerIP();
void sendBootStatus();

// --- CẤU HÌNH LIÊN KẾT ĐÁM MÂY (FIREBASE TĨNH) ---
// Đã loại bỏ https:// để tránh lỗi ghép chuỗi HTTPClient REST API
const char* firebase_host = "kidcheck-attendance-c5504-default-rtdb.asia-southeast1.firebasedatabase.app";

// Biến toàn cục lưu trữ địa chỉ URL động của AI-Server sau khi đọc từ Firebase
String ai_server_base_url = "";

// Biến lưu trữ cấu hình mạng AP nội bộ của ESP32-CAM
String ap_ssid = "KidCheck-Setup-Wifi";
String ap_password = "kidcheckadmin";  // Mật khẩu mặc định ban đầu (tối thiểu 8 ký tự)

// --- ĐỊNH NGHĨA SƠ ĐỒ CHÂN PHẦN CỨNG (HARDWARE MAP) ---
#define PIR_PIN 12     // Chân OUT của cảm biến chuyển động PIR AM312
#define LED_PIN 2      // Chân điều khiển LED đỏ báo hiệu trạng thái
#define BUZZER_PIN 13  // Chân điều khiển còi chíp Buzzer 3V
#define I2C_SDA 14     // Chân dữ liệu SDA của màn hình LCD 1602
#define I2C_SCL 15     // Chân xung nhịp SCL của màn hình LCD 1602

// Khởi tạo đối tượng quản lý thiết bị ngoại vi
LiquidCrystal_I2C lcd(0x27, 16, 2);
const byte DNS_PORT = 53;  // Cổng mạng phục vụ phân giải tên miền Captive Portal
DNSServer dnsServer;
WebServer server(80);  // Web Server nội bộ ở cổng 80 đón lệnh điều khiển

// --- CẤU HÌNH CHÂN CAMERA (ESP32-CAM AI-THINKER) ---
#define PWDN_GPIO_NUM 32
#define RESET_GPIO_NUM -1
#define XCLK_GPIO_NUM 0
#define SIOD_GPIO_NUM 26
#define SIOC_GPIO_NUM 27

#define Y9_GPIO_NUM 35
#define Y8_GPIO_NUM 34
#define Y7_GPIO_NUM 39
#define Y6_GPIO_NUM 36
#define Y5_GPIO_NUM 21
#define Y4_GPIO_NUM 19
#define Y3_GPIO_NUM 18
#define Y2_GPIO_NUM 5
#define VSYNC_GPIO_NUM 25
#define HREF_GPIO_NUM 23
#define PCLK_GPIO_NUM 22

// ============================================================================
// KHỐI 1: KHỞI TẠO MODULE CAMERA VÀ CÁC TIỆN ÍCH PHẢN HỒI NGOẠI VI
// ============================================================================
void initCamera() {
  camera_config_t config;
  config.ledc_channel = LEDC_CHANNEL_0;
  config.ledc_timer = LEDC_TIMER_0;
  config.pin_d0 = Y2_GPIO_NUM;
  config.pin_d1 = Y3_GPIO_NUM;
  config.pin_d2 = Y4_GPIO_NUM;
  config.pin_d3 = Y5_GPIO_NUM;
  config.pin_d4 = Y6_GPIO_NUM;
  config.pin_d5 = Y7_GPIO_NUM;
  config.pin_d6 = Y8_GPIO_NUM;
  config.pin_d7 = Y9_GPIO_NUM;
  config.pin_xclk = XCLK_GPIO_NUM;
  config.pin_pclk = PCLK_GPIO_NUM;
  config.pin_vsync = VSYNC_GPIO_NUM;
  config.pin_href = HREF_GPIO_NUM;
  config.pin_sscb_sda = SIOD_GPIO_NUM;
  config.pin_sscb_scl = SIOC_GPIO_NUM;
  config.pin_pwdn = PWDN_GPIO_NUM;
  config.pin_reset = RESET_GPIO_NUM;
  config.xclk_freq_hz = 20000000;
  config.pixel_format = PIXFORMAT_JPEG;

  if (psramFound()) {
    config.frame_size = FRAMESIZE_QVGA;  // Kích thước khung hình tối ưu cho AI: 320x240
    config.jpeg_quality = 10;            // Độ nén ảnh cân bằng để bảo vệ băng thông mạng
    config.fb_count = 2;
  } else {
    config.frame_size = FRAMESIZE_QVGA;
    config.jpeg_quality = 12;
    config.fb_count = 1;
  }

  esp_err_t err = esp_camera_init(&config);
  if (err != ESP_OK) {
    lcd.clear();
    lcd.print("Cam Init Failed");
    while (1)
      ;
  }
}

void alertBuzzer(int duration, int count) {
  for (int i = 0; i < count; i++) {
    digitalWrite(BUZZER_PIN, HIGH);
    delay(duration);
    digitalWrite(BUZZER_PIN, LOW);
    if (i < count - 1) delay(100);
  }
}

void showMessage(const char* line1, const char* line2) {
  lcd.clear();
  lcd.setCursor(0, 0);
  lcd.print(line1);
  lcd.setCursor(0, 1);
  lcd.print(line2);
}

// ============================================================================
// KHỐI 2: ĐỌC VÀ ĐỒNG BỘ DỮ LIỆU ĐỊA CHỈ IP QUA CLOUD FIREBASE
// ============================================================================
bool getAiServerIP() {
  if (WiFi.status() == WL_CONNECTED) {
    // FIX #4: Dùng WiFiClientSecure với setInsecure() để gọi HTTPS đến Firebase
    // Trên ESP32 core mới, http.begin(url) với HTTPS sẽ thất bại do lỗi bắt tay SSL
    WiFiClientSecure client;
    client.setInsecure();  // Bỏ qua xác thực chứng chỉ SSL (phù hợp cho LAN nội bộ)
    HTTPClient http;
    String url = "https://" + String(firebase_host) + "/System/AI_Server_IP.json";
    http.begin(client, url);

    int httpResponseCode = http.GET();
    if (httpResponseCode > 0) {
      String response = http.getString();
      StaticJsonDocument<200> doc;
      DeserializationError error = deserializeJson(doc, response);

      if (!error) {
        const char* server_url = doc["url"];
        ai_server_base_url = String(server_url);
        http.end();
        return true;
      }
    }
    http.end();
  }
  return false;
}

void sendBootStatus() {
  if (ai_server_base_url == "") return;

  HTTPClient http;
  String target_url = ai_server_base_url + "/device-boot";
  http.begin(target_url);
  http.addHeader("Content-Type", "application/json");

  StaticJsonDocument<200> doc;
  doc["device_id"] = "KidCheck_ESP32_01";
  doc["status"] = "ONLINE";
  doc["ip"] = WiFi.localIP().toString();

  String requestBody;
  serializeJson(doc, requestBody);
  http.POST(requestBody);
  http.end();
}

// ============================================================================
// KHỐI 3: TIẾP NHẬN VÀ XỬ LÝ LỆNH ĐIỀU KHIỂN TRỰC TIẾP TỪ WEB APP
// ============================================================================

// Khai báo nguyên mẫu hàm (Function Prototype) để sửa triệt để lỗi "not declared in this scope"
void updateApConfigFromFirebase();
void captureAndRecognize();
bool isCaptivePortal();
bool sendImageToRegister(camera_fb_t* fb, String student_id, int photoIndex) {
  if (ai_server_base_url == "") return false;

  HTTPClient http;
  http.begin(ai_server_base_url + "/register");

  String boundary = "KidCheckBoundary7MA4YWxk";
  http.addHeader("Content-Type", "multipart/form-data; boundary=" + boundary);

  String head = "--" + boundary + "\r\n"
              + "Content-Disposition: form-data; name=\"student_id\"\r\n\r\n"
              + student_id + "\r\n"
              + "--" + boundary + "\r\n"
              + "Content-Disposition: form-data; name=\"file\"; filename=\""
              + String(photoIndex) + ".jpg\"\r\n"
              + "Content-Type: image/jpeg\r\n\r\n";
  String tail = "\r\n--" + boundary + "--\r\n";

  size_t totalLen = head.length() + fb->len + tail.length();
  uint8_t* body = (uint8_t*)malloc(totalLen);
  if (!body) { http.end(); return false; }

  memcpy(body, head.c_str(), head.length());
  memcpy(body + head.length(), fb->buf, fb->len);
  memcpy(body + head.length() + fb->len, tail.c_str(), tail.length());

  int code = http.POST(body, totalLen);
  free(body);
  http.end();
  return code == 200;
}
// 3.1 Hàm xử lý lệnh chụp LIÊN TIẾP 3 TẤM phục vụ đăng ký học sinh mới từ Web App
void handleCaptureRegister() {
  // FIX #2c: Thêm CORS header để trình duyệt không bị chặn khi gọi từ Web App
  server.sendHeader("Access-Control-Allow-Origin", "*");
  if (!server.hasArg("student_id")) {
    server.send(400, "text/plain", "Thieu tham so student_id");
    return;
  }
  String student_id = server.arg("student_id");
  showMessage("Dang ky moi...", student_id.c_str());

  int successCount = 0;

  for (int i = 1; i <= 3; i++) {
    showMessage("Giu nguyen mat", ("Chup tam: " + String(i) + "/3").c_str());
    digitalWrite(LED_PIN, HIGH);
    alertBuzzer(150, 1);
    delay(3000);

    camera_fb_t* fb = esp_camera_fb_get();
    if (!fb) { digitalWrite(LED_PIN, LOW); continue; }

    if (sendImageToRegister(fb, student_id, i)) successCount++;

    esp_camera_fb_return(fb);
    digitalWrite(LED_PIN, LOW);
    delay(1000);
  }

  showMessage("KidCheck System", "San sang...");

  StaticJsonDocument<200> doc;
  doc["success"] = (successCount > 0);
  doc["photos_sent"] = successCount;
  doc["student_id"] = student_id;
  String resBody;
  serializeJson(doc, resBody);
  server.send(200, "application/json", resBody);
}

// 3.2 Hàm điều khiển trạng thái LED đỏ trợ sáng theo tham số URL (?state=1 hoặc ?state=0)
void handleLEDControl() {
  // FIX #2c: CORS header
  server.sendHeader("Access-Control-Allow-Origin", "*");
  if (server.hasArg("state")) {
    String state = server.arg("state");
    if (state == "1") {
      digitalWrite(LED_PIN, HIGH);  // Bật LED
      server.send(200, "text/plain", "LED ON SUCCESS");
    } else {
      digitalWrite(LED_PIN, LOW);  // Tắt LED
      server.send(200, "text/plain", "LED OFF SUCCESS");
    }
  } else {
    server.send(400, "text/plain", "Missing state parameter");
  }
}

// 3.3 Hàm nhận lệnh khởi động lại phần cứng thiết bị từ xa
void handleReboot() {
  // FIX #2c: CORS header
  server.sendHeader("Access-Control-Allow-Origin", "*");
  server.send(200, "text/plain", "Mach dang khoi dong lai...");
  delay(1000);
  ESP.restart();  // Lệnh reset cứng hệ thống của chip ESP32
}

// 3.4 Hàm đọc mật khẩu AP mới từ Firebase về để cập nhật (Đồng bộ từ Web App)
void updateApConfigFromFirebase() {
  if (WiFi.status() == WL_CONNECTED) {
    // FIX #4: Dùng WiFiClientSecure với setInsecure() để tránh lỗi SSL Handshake
    WiFiClientSecure client;
    client.setInsecure();
    HTTPClient http;
    String url = "https://" + String(firebase_host) + "/System/Device_Config.json";
    http.begin(client, url);

    int httpResponseCode = http.GET();
    if (httpResponseCode > 0) {
      String response = http.getString();
      StaticJsonDocument<200> doc;
      deserializeJson(doc, response);

      if (doc.containsKey("ap_password")) {
        String new_pass = doc["ap_password"].as<String>();
        if (new_pass != ap_password && new_pass.length() >= 8) {
          ap_password = new_pass;
          Serial.println("[AP] Phat hien mat khau AP moi tu Webapp: " + ap_password);
          WiFi.softAP(ap_ssid.c_str(), ap_password.c_str());
          Serial.println("[AP] Da cap nhat mat khau KidCheck-Setup-Wifi!");
        }
      }
    }
    http.end();
  }
}

// 3.5 Giao Diện Cấu Hình WiFi Đẹp Mắt (Responsive mượt mà cho Điện thoại & Máy tính)
void handleWifiPortal() {
  if (isCaptivePortal()) return;

  String html = "<!DOCTYPE html><html lang='vi'><head><meta charset='UTF-8'>";
  html += "<meta name='viewport' content='width=device-width, initial-scale=1.0'>";
  html += "<title>KidCheck IoT - Cấu Hình</title>";
  html += "<style>";
  html += "* { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; }";
  html += "body { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); min-height: 100vh; display: flex; align-items: center; justify-content: center; padding: 20px; }";
  html += ".container { background: rgba(255, 255, 255, 0.95); width: 100%; max-width: 420px; padding: 35px 25px; border-radius: 20px; box-shadow: 0 15px 35px rgba(0, 0, 0, 0.2); backdrop-filter: blur(10px); }";
  html += ".logo { font-size: 28px; font-weight: 800; color: #4a5568; margin-bottom: 5px; text-transform: uppercase; letter-spacing: 1px; }";
  html += ".logo span { color: #667eea; }";
  html += ".subtitle { color: #718096; font-size: 14px; margin-bottom: 30px; line-height: 1.5; }";
  html += ".input-group { margin-bottom: 20px; text-align: left; }";
  html += "label { display: block; font-size: 13px; font-weight: 600; color: #4a5568; margin-bottom: 6px; }";
  html += "input { width: 100%; padding: 14px 16px; border: 2px solid #e2e8f0; border-radius: 10px; font-size: 15px; transition: all 0.3s ease; color: #2d3748; }";
  html += "input:focus { outline: none; border-color: #667eea; box-shadow: 0 0 0 3px rgba(102, 126, 234, 0.15); }";
  html += "button { width: 100%; padding: 15px; background: linear-gradient(90deg, #667eea 0%, #764ba2 100%); color: white; border: none; border-radius: 10px; font-size: 16px; font-weight: 700; cursor: pointer; box-shadow: 0 4px 12px rgba(102, 126, 234, 0.3); transition: all 0.2s ease; margin-top: 10px; }";
  html += "button:hover { transform: translateY(-1px); box-shadow: 0 6px 15px rgba(102, 126, 234, 0.4); }";
  html += "button:active { transform: translateY(1px); }";
  html += "</style></head>";
  html += "<body><div class='container'>";
  html += "<div class='logo'>Kid<span>Check</span></div>";
  html += "<div class='subtitle'>Hệ thống điểm danh tự động IoT mầm non<br><span style='font-size:12px; font-weight:bold;'>[ TRANG CẤU HÌNH LẦN ĐẦU ]</span></div>";
  html += "<form action='/save-wifi' method='POST'>";
  html += "<div class='input-group'><label>Tên mạng mạng WiFi (SSID)</label><input type='text' name='ssid' placeholder='Nhập tên WiFi lớp/trường' required></div>";
  html += "<div class='input-group'><label>Mật khẩu WiFi</label><input type='password' name='pass' placeholder='Nhập mật khẩu WiFi'></div>";
  html += "<button type='submit'>KẾT NỐI & ĐỒNG BỘ MẠNG</button>";
  html += "</form></div></body></html>";

  server.send(200, "text/html", html);
}

// 3.6 Hàm xử lý lưu dữ liệu WiFi do người dùng nhập từ Portal + Trang thông báo thành công
void handleSaveWifi() {
  if (server.hasArg("ssid") && server.hasArg("pass")) {
    String req_ssid = server.arg("ssid");
    String req_pass = server.arg("pass");

    String msg = "<!DOCTYPE html><html lang='vi'><head><meta charset='UTF-8'>";
    msg += "<meta name='viewport' content='width=device-width, initial-scale=1.0'>";
    msg += "<title>Cấu Hình Thành Công</title>";
    msg += "<style>";
    msg += "* { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif; }";
    msg += "body { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); min-height: 100vh; display: flex; align-items: center; justify-content: center; padding: 20px; }";
    msg += ".card { background: white; width: 100%; max-width: 400px; padding: 40px 30px; border-radius: 20px; box-shadow: 0 15px 35px rgba(0, 0, 0, 0.2); text-align: center; }";
    msg += ".icon { width: 70px; height: 70px; background: #e6fffa; color: #319795; border-radius: 50%; display: flex; align-items: center; justify-content: center; font-size: 35px; margin: 0 auto 25px auto; font-weight: bold; }";
    msg += "h2 { color: #2d3748; font-size: 22px; font-weight: 700; margin-bottom: 15px; }";
    msg += "p { color: #718096; font-size: 15px; line-height: 1.6; margin-bottom: 25px; }";
    msg += ".loader { border: 3px solid #f3f3f3; border-top: 3px solid #667eea; border-radius: 50%; width: 24px; height: 24px; animation: spin 1s linear infinite; margin: 0 auto; }";
    msg += "@keyframes spin { 0% { transform: rotate(0deg); } 100% { transform: rotate(360deg); } }";
    msg += "</style></head>";
    msg += "<body><div class='card'>";
    msg += "<div class='icon'>✓</div>";
    msg += "<h2>Đã Nhận Dữ Liệu!</h2>";
    msg += "<p>Hệ thống IoT đang tiến hành kết nối vào mạng mạng WiFi mới.<br><strong style='color:#4a5568;'>Vui lòng kiểm tra màn hình LCD trên thiết bị!</strong></p>";
    msg += "<div class='loader'></div>";
    msg += "</div></body></html>";

    server.send(200, "text/html", msg);
    delay(2000);

    // Tiến hành hiển thị tiến trình kết nối lên LCD và bắt đầu kết nối mạng
    showMessage("Ket noi mang", req_ssid.c_str());
    WiFi.begin(req_ssid.c_str(), req_pass.c_str());

    int counter = 0;
    while (WiFi.status() != WL_CONNECTED && counter < 20) {
      delay(500);
      counter++;
    }

    if (WiFi.status() == WL_CONNECTED) {
      showMessage("Ket noi OK!", WiFi.localIP().toString().c_str());
      delay(2000);

      // Kết nối mạng mạng thành công -> Lên Cloud kéo IP đầu não của AI-Server về máy
      showMessage("Firebase Fetch", "Load Server IP");
      int retry = 0;
      while (!getAiServerIP() && retry < 5) {
        delay(1000);
        retry++;
      }

      // Đồng bộ đẩy IP động của ESP32 lên Firebase thông qua Server
      if (ai_server_base_url != "") {
        showMessage("Syncing Server", "Updating Device IP");
        sendBootStatus();
        delay(1000);
      }
    } else {
      showMessage("Ket noi loi!", "Try again");
      delay(2000);
      showMessage("KidCheck System", "San sang AP...");
    }
  } else {
    server.send(400, "text/plain", "Bad Request: Missing SSID or Password");
  }

}  

// 3.7 Hàm kiểm tra và ép trình duyệt chuyển hướng về trang cấu hình lần đầu (Captive Portal)
bool isCaptivePortal() {
  if (!server.hostHeader().equalsIgnoreCase(WiFi.softAPIP().toString())) {
    Serial.println("[DNS] Phat hien thiet bi kiem tra mang. Dang ep hien trang Portal...");

    // Trả về mã HTTP Code 302 Redirect ép thiết bị di động bật Pop-up Portal lên ngay lập tức
    server.sendHeader("Location", String("http://") + WiFi.softAPIP().toString(), true);
    server.send(302, "text/plain", "");
    return true;
  }
  return false;
}

// ============================================================================
// KHỐI 4: VÒNG LẶP ĐIỂM DANH TỰ ĐỘNG (PIR KÍCH HOẠT)
// ============================================================================

void captureAndRecognize() {
    // Nếu chưa lấy được địa chỉ IP động của AI-Server từ Cloud Firebase thì không thực thi
    if (ai_server_base_url == "") {
        showMessage("Loi he thong!", "IP Server: Null");
        return;
    }

    // 4.1 Phản hồi giao diện trực quan và Bật LED đỏ trợ sáng/báo hiệu bắt đầu chụp
    showMessage("Chup anh...", "Gui đen AI-Server");
    digitalWrite(LED_PIN, HIGH); 

    // 4.2 Triệu hồi phần cứng để lấy khung hình dữ liệu hình ảnh từ bộ nhớ đệm Camera
    camera_fb_t * fb = esp_camera_fb_get();
    if (!fb) {
        showMessage("Loi Camera!", "Thu lai sau");
        digitalWrite(LED_PIN, LOW);
        return;
    }

    // 4.3 Thiết lập kết nối HTTP POST truyền mảng dữ liệu nhị phân (Binary Stream) sang AI-Server
    if (WiFi.status() == WL_CONNECTED) {
        HTTPClient http;
        String target_url = ai_server_base_url + "/recognize"; // Đường dẫn động đồng bộ từ Cloud
        http.begin(target_url); 
        http.addHeader("Content-Type", "image/jpeg");

        // Đẩy khối ảnh đi trực tiếp qua mạng vô tuyến LAN
        int httpResponseCode = http.POST(fb->buf, fb->len);

        if (httpResponseCode > 0) {
            String response = http.getString();
            
            // Phân tích cú pháp chuỗi JSON trả về từ AI-Server để đưa ra phản hồi ngoại vi tương ứng
            StaticJsonDocument<200> doc;
            DeserializationError error = deserializeJson(doc, response);

            if (!error) {
                bool success = doc["success"];
                const char* name = doc["name"];

                // 4.4 Thực thi phản hồi thiết bị dựa trên kết quả AI phân tích xử lý
                if (success) {
                    // Trường hợp 1: Khớp dữ liệu khuôn mặt bé -> Điểm danh thành công
                    showMessage("Xin chao Be!", name);
                    alertBuzzer(200, 1); // Còi chíp kêu 1 tiếng bíp ngắn thành công
                    delay(3000);         // Giữ tên học sinh hiển thị trên LCD trong 3 giây để nhìn thấy
                } else {
                    // Trường hợp 2: Không trích xuất được khuôn mặt hoặc khuôn mặt không hợp lệ
                    showMessage("Nhan dien loi!", "Khong hop le");
                    alertBuzzer(100, 3); // Còi chíp kêu 3 tiếng bíp ngắn liên tục để cảnh báo
                    delay(2000);
                }
            }
        } else {
            showMessage("Loi ket noi!", "AI Server Down");
            alertBuzzer(500, 2); // Kêu 2 tiếng dài thông báo mất liên lạc với máy tính Python
            delay(2000);
        }
        http.end();
    } else {
        showMessage("Mat WiFi!", "Dang ket noi lai");
    }

    // 4.5 Giải phóng hoàn toàn bộ đệm ảnh cũ, tắt LED và trả thiết bị về trạng thái trực chiến ban đầu
    esp_camera_fb_return(fb);
    digitalWrite(LED_PIN, LOW); 
    showMessage("KidCheck System", "San sang...");
}

// ============================================================================
// KHỐI 5: HÀM CẤU HÌNH BAN ĐẦU (SETUP) VÀ VÒNG LẶP NỀN (LOOP)
// ============================================================================

// 5.1 Hàm thiết lập cấu hình ban đầu cho toàn bộ hệ thống phần cứng và mạng mạng
void setup() {
    // Kích hoạt cổng giao tiếp Serial để giám sát hệ thống trên máy tính máy tính
    Serial.begin(115200);
    Serial.println("\n[SYSTEM] Khoi dong he thong KidCheck IoT...");

    // Cấu hình trạng thái logic ban đầu cho các chân GPIO ngoại vi
    pinMode(PIR_PIN, INPUT);
    pinMode(LED_PIN, OUTPUT);
    pinMode(BUZZER_PIN, OUTPUT);
    digitalWrite(LED_PIN, LOW);
    digitalWrite(BUZZER_PIN, LOW);

    // Hành động Chớp LED đỏ 1 lần báo hiệu chip đã nhận nguồn điện ổn định
    digitalWrite(LED_PIN, HIGH);
    delay(400);
    digitalWrite(LED_PIN, LOW);

    // KHỞI TẠO CAMERA TRƯỚC (Bắt buộc để tránh lỗi treo bus hoặc sập nguồn)
    Serial.println("[CAMERA] Dang setup cam...");
    initCamera();
    Serial.println("[CAMERA] Setup camera THANH CONG!");

    // KHỞI TẠO MÀN HÌNH LCD SAU KHI CAMERA ĐÃ ỔN ĐỊNH
    Wire.begin(I2C_SDA, I2C_SCL);
    lcd.init();
    lcd.backlight();
    showMessage("KidCheck IoT", "Dang khoi dong...");
    delay(1000);

    // CẤU HÌNH CHẾ ĐỘ MẠNG SONG SONG (DUAL MODE: AP + STA)
    WiFi.mode(WIFI_AP_STA);
    
    // Khởi tạo trạm phát WiFi cấu hình cấu trúc có mật khẩu bảo mật bảo mật
    WiFi.softAP(ap_ssid.c_str(), ap_password.c_str());
    Serial.println("[AP] Mang phat song: " + ap_ssid + " | Mk: " + ap_password);
    Serial.print("[AP] Dia chi IP truy cap Portal: ");
    Serial.println(WiFi.softAPIP());

    // FIX #5: Gọi WiFi.begin() không tham số để ESP32 đọc credentials đã lưu
    // trong bộ nhớ Flash (NVS) và tự kết nối lại mạng WiFi cũ sau khi khởi động lại
    // Điều này tránh phải cấu hình WiFi lại từ đầu mỗi lần mất điện
    Serial.println("[WiFi] Dang thu ket noi lai WiFi da luu...");
    WiFi.begin();  // FIX #5: Không truyền tham số = dùng credentials đã lưu trong NVS

    // Chờ tối đa 8 giây (16 x 500ms) để thiết bị kết nối lại WiFi đã lưu
    int wifiRetry = 0;
    while (WiFi.status() != WL_CONNECTED && wifiRetry < 16) {
        delay(500);
        wifiRetry++;
        Serial.print(".");
    }
    Serial.println();

    if (WiFi.status() == WL_CONNECTED) {
        // Kết nối thành công từ NVS — lấy IP AI Server và báo cáo trạng thái ngay
        showMessage("WiFi OK!", WiFi.localIP().toString().c_str());
        Serial.println("[WiFi] Ket noi lai thanh cong: " + WiFi.localIP().toString());
        delay(1500);
        int retry = 0;
        while (!getAiServerIP() && retry < 5) { delay(1000); retry++; }
        if (ai_server_base_url != "") {
            sendBootStatus();
        }
    } else {
        // Không có WiFi đã lưu hoặc mạng không khả dụng — tiếp tục chế độ AP Portal
        Serial.println("[WiFi] Khong tim thay WiFi da luu. Vao che do AP Portal.");
        showMessage("Mo Cong Cau Hinh", "Ssid:Cam-Setup");
    }

    // Kích hoạt DNS Server điều hướng toàn bộ truy cập kiểm tra về IP nội bộ (Captive Portal)
    dnsServer.start(DNS_PORT, "*", WiFi.softAPIP());

    // CẤU HÌNH CÁC ĐƯỜNG DẪN API CHO WEB SERVER NỘI BỘ (ĐÓN LỆNH ĐIỀU KHIỂN)
    server.on("/", HTTP_GET, handleWifiPortal);                 // Trang cấu hình Portal mặc định
    server.on("/save-wifi", HTTP_POST, handleSaveWifi);         // Xử lý lưu mạng WiFi từ Portal
    server.on("/capture-reg", HTTP_GET, handleCaptureRegister); // Lệnh chụp 3 tấm đăng ký học sinh
    server.on("/led", HTTP_GET, handleLEDControl);               // Lệnh điều khiển bật tắt LED đỏ
    server.on("/reboot", HTTP_GET, handleReboot);               // Lệnh khởi động lại từ xa
    
    // Đăng ký bộ gác cổng Captive Portal cho các đường dẫn không tồn tại
    server.onNotFound([]() {
        if (!isCaptivePortal()) {
            server.send(404, "text/plain", "Not Found");
        }
    });

    server.begin(); // Bật máy chủ Web nội bộ lên
    Serial.println("[HTTP] Local Web Server dang chay o cong 80.");

    // Thông báo giao diện LCD sẵn sàng cho giai đoạn cấu cấu hình
    alertBuzzer(500, 1); // Bíp 1 tiếng dài hoàn tất setup ban đầu
}

// 5.2 Vòng lặp kiểm soát và vận hành hệ thống thời gian thực thời gian thực
void loop() {
    // Liên tục xử lý các gói tin DNS yêu cầu chuyển hướng Portal
    dnsServer.processNextRequest();
    
    // Liên tục lắng nghe và thực thi các lệnh từ Webapp/AI-Server truyền xuống
    server.handleClient();

    // Điều kiện điểm danh: Chỉ hoạt động khi thiết bị đã kết nối thành công mạng nhà (STA)
    if (WiFi.status() == WL_CONNECTED) {
        
        // Quản lý biến thời gian: Cứ mỗi 15 giây tự động lên Firebase kiểm tra xem mật khẩu AP có bị đổi không
        static unsigned long last_sync_time = 0;
        if (millis() - last_sync_time > 15000) {
            updateApConfigFromFirebase();
            last_sync_time = millis();
        }

        // LUỒNG CHÍNH: Đọc tín hiệu cảm biến PIR chân IO12 phát hiện bé đến trường
        if (digitalRead(PIR_PIN) == HIGH) {
            captureAndRecognize(); // Thực thi chu trình chụp ảnh ảnh gửi nhận diện
            
            // Cơ chế khử rung (Anti-bounce) chờ bé bước đi hẳn mới quét tiếp, tránh trùng lặp log
            while(digitalRead(PIR_PIN) == HIGH) {
                server.handleClient(); // Vẫn giữ mạng phản hồi lệnh điều khiển trong lúc chờ
                delay(200);
            }
        }
    }
    
    delay(10); // Khoảng nghỉ tĩnh 10ms bảo vệ CPU chip không bị quá tải nhiệt
}
