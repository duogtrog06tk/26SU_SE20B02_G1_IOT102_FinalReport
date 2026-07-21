// ============================================================================
// KHỐI 0: THƯ VIỆN HỆ THỐNG, KHAI BÁO NGUYÊN MẪU VÀ SƠ ĐỒ CHÂN PHẦN CỨNG
// ============================================================================
#include "esp_camera.h"
#include <WiFi.h>
#include <WiFiClientSecure.h>
#include <HTTPClient.h>
#include <Wire.h>
#include <LiquidCrystal_I2C.h>
#include <ArduinoJson.h>
#include <WebServer.h>
#include <DNSServer.h>

// --- KHAI BÁO NGUYÊN MẪU HÀM (FUNCTION PROTOTYPES) ---
void initCamera();
void alertBuzzer(int duration, int count);
void showMessage(const char* line1, const char* line2);
bool getAiServerIP();
void sendBootStatus(bool update_lcd = true);
void handleCaptureRegister();
void handleLEDControl();
void handleReboot();
void updateApConfigFromFirebase();
void handleWifiPortal();
void handleSaveWifi();
bool isCaptivePortal();
void captureAndRecognize();
void handleStream();

// --- CẤU HÌNH LIÊN KẾT ĐÁM MÂY (FIREBASE TĨNH) ---
const char* firebase_host = "kidcheckdb-default-rtdb.asia-southeast1.firebasedatabase.app";

// Biến toàn cục lưu trữ địa chỉ URL động của AI-Server sau khi đọc từ Firebase
String ai_server_base_url = "";

// Biến cờ bảo vệ tài nguyên phần cứng camera tránh xung đột giữa Live Stream & Nhận diện PIR
volatile bool is_recognizing = false;
String ap_ssid = "KidCheck-Setup-Wifi";
String ap_password = "admin123";  // Mật khẩu mặc định ban đầu (tối thiểu 8 ký tự)

// --- ĐỊNH NGHĨA SƠ ĐỒ CHÂN PHẦN CỨNG (HARDWARE MAP) ---
#define PIR_PIN 12     // Chân OUT của cảm biến chuyển động PIR AM312
#define LED_PIN 2      // Chân điều khiển LED đỏ trợ sáng/báo trạng thái
#define BUZZER_PIN 13  // Chân điều khiển còi chíp Buzzer 3V
#define I2C_SDA 14     // Chân dữ liệu SDA của màn hình LCD 1602
#define I2C_SCL 15     // Chân xung nhịp SCL của màn hình LCD 1602

// Khởi tạo các đối tượng điều khiển ngoại vi
LiquidCrystal_I2C lcd(0x27, 16, 2);
const byte DNS_PORT = 53;  // Cổng mạng phục vụ phân giải tên miền Captive Portal
DNSServer dnsServer;
WebServer server(80);  // Web Server nội bộ ở cổng 80 đón lệnh điều khiển từ AI Gateway

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
// KHỐI 1: KHỞI TẠO MODULE CAMERA VÀ CÁC TIỆN ÍCH NGOẠI VI
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

  // Cấu hình độ phân giải FRAMESIZE_QVGA (320x240) tối ưu hóa RAM & băng thông truyền tải theo yêu cầu
  if (psramFound()) {
    config.frame_size = FRAMESIZE_QVGA;
    config.jpeg_quality = 10;
    config.fb_count = 2;
  } else {
    config.frame_size = FRAMESIZE_QVGA;
    config.jpeg_quality = 10;
    config.fb_count = 1;
  }

  esp_err_t err = esp_camera_init(&config);
  if (err != ESP_OK) {
    lcd.clear();
    lcd.print("Cam Init Failed");
    while (1);
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
    WiFiClientSecure client;
    client.setInsecure(); // Bỏ qua xác thực SSL
    HTTPClient http;
    String url = "https://" + String(firebase_host) + "/System/AI_Server_IP.json";
    http.begin(client, url);
    http.setTimeout(3000);

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

void sendBootStatus(bool update_lcd) {
  if (ai_server_base_url == "") return;

  if (update_lcd) {
    showMessage("Check AI Server...", "Connecting...");
  }

  WiFiClient client;
  HTTPClient http;
  String target_url = ai_server_base_url + "/device-boot";
  http.begin(client, target_url);
  http.setTimeout(2000);
  http.addHeader("Content-Type", "application/json");

  StaticJsonDocument<256> doc;
  doc["device_id"] = "KidCheck_ESP32_01";
  doc["status"] = "ONLINE";
  doc["ip"] = WiFi.localIP().toString();
  doc["ssid"] = WiFi.SSID();
  doc["rssi"] = WiFi.RSSI();

  String requestBody;
  serializeJson(doc, requestBody);
  int httpResponseCode = http.POST(requestBody);
  
  if (httpResponseCode > 0) {
    Serial.println("[AI SERVER] Ket noi AI-Server THANH CONG: " + ai_server_base_url);
    if (update_lcd) {
      // Dòng 1: Check AI Server... | Dòng 2: AI Server OK!
      showMessage("Check AI Server...", "AI Server OK!");
      alertBuzzer(150, 1); // 1 tiếng bíp ngắn (tít)
      delay(3000); // Giữ thông báo đúng 3 giây
      showMessage("KidCheck System", "Ready..."); // Tự động chuyển ngay sang Ready...
    }
  } else {
    Serial.println("[HTTP] Gui status den AI-Server THAT BAI (Response Code: " + String(httpResponseCode) + "). Vui long kiem tra IP / AI Server.");
    if (update_lcd) {
      // Dòng 1: AI Server Fail! | Dòng 2: Check IP / App (Giữ nguyên màn hình Fail để người dùng kiểm tra)
      showMessage("AI Server Fail!", "Check IP / App");
      alertBuzzer(500, 2); // Còi báo lỗi 2 tiếng bíp dài
    }
    ai_server_base_url = "";
  }
  
  http.end();
}

// ============================================================================
// KHỐI 3: TIẾP NHẬN VÀ XỬ LÝ LỆNH ĐIỀU KHIỂN TỪ AI SERVER GATEWAY
// ============================================================================
void handleCaptureRegister() {
  server.sendHeader("Access-Control-Allow-Origin", "*");
  
  if (!server.hasArg("student_id")) {
    server.send(400, "text/plain", "Missing student_id");
    return;
  }
  String student_id = server.arg("student_id");
  showMessage("Chup dang ky...", student_id.c_str());

  WiFiClient client = server.client();
  String boundary = "KidCheckBoundary7MA4YWxk";
  
  // Gửi tiêu đề phản hồi Multipart nhị phân trực tiếp về AI Server
  client.print("HTTP/1.1 200 OK\r\n");
  client.print("Content-Type: multipart/form-data; boundary=" + boundary + "\r\n");
  client.print("Access-Control-Allow-Origin: *\r\n");
  client.print("Connection: close\r\n\r\n");

  int successCount = 0;
  for (int i = 1; i <= 3; i++) {
    showMessage("Giu nguyen mat", ("Chup tam: " + String(i) + "/3").c_str());
    digitalWrite(LED_PIN, HIGH);
    alertBuzzer(150, 1);
    
    // Mỗi tấm chụp cách nhau 3 giây để đổi góc mặt
    delay(3000);

    camera_fb_t* fb = esp_camera_fb_get();
    if (!fb) {
      digitalWrite(LED_PIN, LOW);
      Serial.println("[CAM] Chup anh that bai o tam: " + String(i));
      continue;
    }

    // Đóng gói part dữ liệu ảnh
    client.print("--" + boundary + "\r\n");
    client.print("Content-Disposition: form-data; name=\"file\"; filename=\"" + String(i) + ".jpg\"\r\n");
    client.print("Content-Type: image/jpeg\r\n");
    client.print("Content-Length: " + String(fb->len) + "\r\n\r\n");
    
    // Đẩy luồng dữ liệu nhị phân của ảnh qua HTTP stream
    client.write(fb->buf, fb->len);
    client.print("\r\n");

    esp_camera_fb_return(fb);
    digitalWrite(LED_PIN, LOW);
    successCount++;
    
    delay(1000);
  }
  
  // Gửi biên kết thúc multipart
  client.print("--" + boundary + "--\r\n");
  
  showMessage("KidCheck System", "San sang...");
}

void handleLEDControl() {
  server.sendHeader("Access-Control-Allow-Origin", "*");
  if (server.hasArg("state")) {
    String state = server.arg("state");
    if (state == "1") {
      digitalWrite(LED_PIN, HIGH);
      server.send(200, "text/plain", "LED ON SUCCESS");
    } else {
      digitalWrite(LED_PIN, LOW);
      server.send(200, "text/plain", "LED OFF SUCCESS");
    }
  } else {
    server.send(400, "text/plain", "Missing state parameter");
  }
}

void handleReboot() {
  server.sendHeader("Access-Control-Allow-Origin", "*");
  server.send(200, "text/plain", "Mach dang khoi dong lai...");
  delay(1000);
  ESP.restart();
}

void updateApConfigFromFirebase() {
  if (WiFi.status() == WL_CONNECTED) {
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
          Serial.println("[AP] Cap nhat mat khau softAP moi: " + ap_password);
          WiFi.softAP(ap_ssid.c_str(), ap_password.c_str());
        }
      }
    }
    http.end();
  }
}

void handleWifiPortal() {
  if (isCaptivePortal()) return;

  int n = WiFi.scanNetworks();
  String wifiOptions = "";
  for (int i = 0; i < n; ++i) {
    String ssid = WiFi.SSID(i);
    int rssi = WiFi.RSSI(i);
    String encType = (WiFi.encryptionType(i) == WIFI_AUTH_OPEN) ? "Mở" : "Bảo mật";
    if (ssid.length() > 0 && wifiOptions.indexOf("value='" + ssid + "'") == -1) {
      wifiOptions += "<option value='" + ssid + "'>" + ssid + " (" + String(rssi) + " dBm, " + encType + ")</option>";
    }
  }
  WiFi.scanDelete();

  String html = "<!DOCTYPE html><html lang='vi'><head><meta charset='UTF-8'>";
  html += "<meta name='viewport' content='width=device-width, initial-scale=1.0'>";
  html += "<title>KidCheck IoT - Cấu Hình</title>";
  html += "<link rel='stylesheet' href='https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css'>";
  html += "<style>";
  html += "@import url('https://fonts.googleapis.com/css2?family=Nunito:wght@400;600;700;800;900&display=swap');";
  html += "* { box-sizing: border-box; margin: 0; padding: 0; font-family: 'Nunito', sans-serif; }";
  html += "body { background: linear-gradient(135deg, #6C5CE7 0%, #8E2DE2 100%); min-height: 100vh; display: flex; align-items: center; justify-content: center; padding: 20px; }";
  html += ".container { background: #FFFFFF; width: 100%; max-width: 420px; padding: 40px 30px; border-radius: 24px; box-shadow: 0 20px 40px rgba(0, 0, 0, 0.25); text-align: center; border: 3px solid rgba(255, 255, 255, 0.1); }";
  html += ".logo { font-size: 32px; font-weight: 900; color: #2D3748; margin-bottom: 8px; text-transform: uppercase; letter-spacing: 1px; display: inline-flex; align-items: center; gap: 8px; }";
  html += ".logo i { color: #FF6B81; }";
  html += ".logo span { color: #FF6B81; }";
  html += ".subtitle { color: #718096; font-size: 15px; margin-bottom: 30px; line-height: 1.6; }";
  html += ".subtitle strong { color: #6C5CE7; display: block; font-size: 12px; margin-top: 5px; text-transform: uppercase; letter-spacing: 1px; }";
  html += ".input-group { margin-bottom: 22px; text-align: left; }";
  html += "label { display: block; font-size: 14px; font-weight: 700; color: #2D3748; margin-bottom: 8px; }";
  html += ".input-wrapper { position: relative; }";
  html += ".input-wrapper i { position: absolute; left: 16px; top: 50%; transform: translateY(-50%); color: #A0AEC0; font-size: 16px; z-index: 10; }";
  html += "input, select { width: 100%; padding: 14px 16px 14px 46px; border: 2px solid #E8EEF5; border-radius: 16px; font-size: 15px; font-weight: 600; transition: all 0.3s ease; color: #2D3748; background: #F8FAFC; -webkit-appearance: none; -moz-appearance: none; appearance: none; }";
  html += "input:focus, select:focus { outline: none; border-color: #FF6B81; box-shadow: 0 0 0 4px rgba(255, 107, 129, 0.15); background: #FFFFFF; }";
  html += "button { width: 100%; padding: 16px; background: linear-gradient(90deg, #FF6B81 0%, #E04D62 100%); color: white; border: none; border-radius: 16px; font-size: 16px; font-weight: 800; cursor: pointer; box-shadow: 0 8px 20px rgba(255, 107, 129, 0.3); transition: all 0.3s ease; margin-top: 15px; display: inline-flex; align-items: center; justify-content: center; gap: 8px; }";
  html += "button:hover { transform: translateY(-2px); box-shadow: 0 12px 25px rgba(255, 107, 129, 0.45); }";
  html += "button:active { transform: translateY(1px); }";
  html += "</style></head>";
  html += "<body><div class='container'>";
  html += "<div class='logo'><i class='fa-solid fa-camera-retro'></i>Kid<span>Check</span></div>";
  html += "<div class='subtitle'>Hệ thống điểm danh tự động IoT mầm non<strong>[ Cấu hình thiết lập WiFi ban đầu ]</strong></div>";
  html += "<form action='/save-wifi' method='POST'>";
  html += "<div class='input-group'><label>Chọn mạng WiFi (SSID)</label><div class='input-wrapper'><i class='fa-solid fa-wifi'></i><select name='ssid' id='ssid_select' onchange='checkCustomWifi(this)' required><option value='' disabled selected>-- Chọn WiFi của bạn --</option>" + wifiOptions + "<option value='custom'>-- Nhập WiFi khác (Mạng ẩn) --</option></select></div></div>";
  html += "<div id='custom-wifi-wrapper' style='display:none;' class='input-group'><label>Nhập tên WiFi thủ công</label><div class='input-wrapper'><i class='fa-solid fa-keyboard'></i><input type='text' id='ssid_custom' placeholder='Nhập chính xác tên WiFi ẩn'></div></div>";
  html += "<div class='input-group'><label>Mật khẩu WiFi</label><div class='input-wrapper'><i class='fa-solid fa-key'></i><input type='password' name='pass' placeholder='Nhập mật khẩu WiFi'></div></div>";
  html += "<button type='submit'><i class='fa-solid fa-paper-plane'></i> KẾT NỐI & ĐỒNG BỘ THIẾT BỊ</button>";
  html += "</form></div>";
  html += "<script>";
  html += "function checkCustomWifi(select) {";
  html += "  var wrapper = document.getElementById('custom-wifi-wrapper');";
  html += "  var customInput = document.getElementById('ssid_custom');";
  html += "  if (select.value === 'custom') {";
  html += "    wrapper.style.display = 'block';";
  html += "    customInput.setAttribute('name', 'ssid');";
  html += "    customInput.setAttribute('required', 'true');";
  html += "    select.removeAttribute('name');";
  html += "  } else {";
  html += "    wrapper.style.display = 'none';";
  html += "    customInput.removeAttribute('name');";
  html += "    customInput.removeAttribute('required');";
  html += "    select.setAttribute('name', 'ssid');";
  html += "  }";
  html += "}";
  html += "</script></body></html>";

  server.send(200, "text/html", html);
}

void handleSaveWifi() {
  if (server.hasArg("ssid") && server.hasArg("pass")) {
    String req_ssid = server.arg("ssid");
    String req_pass = server.arg("pass");

    String msg = "<!DOCTYPE html><html lang='vi'><head><meta charset='UTF-8'>";
    msg += "<meta name='viewport' content='width=device-width, initial-scale=1.0'>";
    msg += "<title>Cấu Hình Thành Công</title>";
    msg += "<link rel='stylesheet' href='https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css'>";
    msg += "<style>";
    msg += "@import url('https://fonts.googleapis.com/css2?family=Nunito:wght@400;600;700;800;900&display=swap');";
    msg += "* { box-sizing: border-box; margin: 0; padding: 0; font-family: 'Nunito', sans-serif; }";
    msg += "body { background: linear-gradient(135deg, #6C5CE7 0%, #8E2DE2 100%); min-height: 100vh; display: flex; align-items: center; justify-content: center; padding: 20px; }";
    msg += ".card { background: #FFFFFF; width: 100%; max-width: 400px; padding: 45px 35px; border-radius: 24px; box-shadow: 0 20px 40px rgba(0, 0, 0, 0.25); text-align: center; }";
    msg += ".icon { width: 80px; height: 80px; background: #EBFDF5; color: #2EC4B6; border-radius: 50%; display: flex; align-items: center; justify-content: center; font-size: 40px; margin: 0 auto 25px auto; box-shadow: 0 8px 20px rgba(46, 196, 182, 0.2); }";
    msg += "h2 { color: #2D3748; font-size: 24px; font-weight: 900; margin-bottom: 15px; }";
    msg += "p { color: #718096; font-size: 15px; line-height: 1.6; margin-bottom: 30px; }";
    msg += "p strong { color: #FF6B81; }";
    msg += ".loader { border: 4px solid #F3F3F3; border-top: 4px solid #6C5CE7; border-radius: 50%; width: 32px; height: 32px; animation: spin 1s linear infinite; margin: 0 auto; }";
    msg += "@keyframes spin { 0% { transform: rotate(0deg); } 100% { transform: rotate(360deg); } }";
    msg += "</style></head>";
    msg += "<body><div class='card'>";
    msg += "<div class='icon'><i class='fa-solid fa-circle-check'></i></div>";
    msg += "<h2>Đã Nhận Cấu Hình!</h2>";
    msg += "<p>Mạch KidCheck đang cố gắng kết nối vào mạng WiFi mới.<br><strong>Vui lòng kiểm tra màn hình LCD trên thiết bị!</strong></p>";
    msg += "<div class='loader'></div>";
    msg += "</div></body></html>";

    server.send(200, "text/html", msg);
    delay(2000);

    showMessage("WiFi Connect...", req_ssid.c_str());
    WiFi.begin(req_ssid.c_str(), req_pass.c_str());

    int counter = 0;
    while (WiFi.status() != WL_CONNECTED && counter < 20) {
      delay(500);
      counter++;
    }

    if (WiFi.status() == WL_CONNECTED) {
      showMessage("WiFi Connected!", WiFi.localIP().toString().c_str());
      delay(2000);

      showMessage("Firebase Fetch", "Load Server IP");
      int retry = 0;
      while (!getAiServerIP() && retry < 5) {
        delay(1000);
        retry++;
      }

      if (ai_server_base_url != "") {
        showMessage("Syncing Server", "Updating Device IP");
        sendBootStatus();
        delay(1000);
      }
    } else {
      showMessage("Connection Fail", "Try again");
      delay(2000);
      showMessage("KidCheck System", "AP Ready...");
    }
  } else {
    server.send(400, "text/plain", "Bad Request: Missing SSID or Password");
  }
}

bool isCaptivePortal() {
  if (!server.hostHeader().equalsIgnoreCase(WiFi.softAPIP().toString())) {
    Serial.println("[DNS] Captive Portal redirected...");
    server.sendHeader("Location", String("http://") + WiFi.softAPIP().toString() + "/", true);
    server.send(302, "text/html", "<!DOCTYPE html><html><head><meta http-equiv='refresh' content='0;url=http://192.168.4.1/'><script>location.href='http://192.168.4.1/';</script></head><body>Redirecting to Captive Portal...</body></html>");
    return true;
  }
  return false;
}

// ============================================================================
// KHỐI 4: VÒNG LẶP ĐIỂM DANH TỰ ĐỘNG (PIR CHÂN IO12 KÍCH HOẠT TRONG 5 GIÂY)
// ============================================================================
void captureAndRecognize() {
    is_recognizing = true;
    if (ai_server_base_url == "") {
        getAiServerIP();
    }
    
    if (ai_server_base_url == "") {
        showMessage("System Error!", "Server IP: Null");
        delay(1500);
        is_recognizing = false;
        return;
    }

    digitalWrite(LED_PIN, HIGH); 
    showMessage("Motion Detected!", "Scanning Face 5s");

    unsigned long start_time = millis();
    bool recognized = false;

    // Vòng lặp 5 giây chụp và nhận diện liên tục cho đến khi thành công hoặc hết 5s
    while (millis() - start_time < 5000) {
        server.handleClient();

        camera_fb_t * fb = esp_camera_fb_get();
        if (!fb) {
            delay(100);
            continue;
        }

        if (WiFi.status() == WL_CONNECTED) {
            HTTPClient http;
            String target_url = ai_server_base_url + "/recognize";
            http.begin(target_url); 
            http.addHeader("Content-Type", "image/jpeg");
            http.setTimeout(2500);

            // Gửi POST Binary ảnh lên AI Server
            int httpResponseCode = http.POST(fb->buf, fb->len);

            if (httpResponseCode > 0) {
                String response = http.getString();
                StaticJsonDocument<250> doc;
                DeserializationError error = deserializeJson(doc, response);

                if (!error) {
                    bool success = doc["success"];
                    const char* name = doc["name"];

                    if (success && name != NULL) {
                        // Line 1: Recognized... | Line 2: HS001 OK!
                        showMessage("Recognized...", name);
                        alertBuzzer(200, 1);
                        recognized = true;
                        http.end();
                        esp_camera_fb_return(fb);
                        delay(2500); // Tạm dừng để học sinh đi qua
                        break;
                    }
                }
            } else {
                Serial.println("[HTTP] Image upload error: " + String(httpResponseCode));
            }
            http.end();
        } else {
            showMessage("WiFi Disconnected", "Reconnecting...");
            esp_camera_fb_return(fb);
            delay(500);
            break;
        }

        esp_camera_fb_return(fb);
        delay(200); // Khoảng nghỉ nhỏ giữa các lần chụp trong cửa sổ 5s
    }

    digitalWrite(LED_PIN, LOW); 

    if (!recognized) {
        // Hết 5s không nhận diện được: đổi chữ LCD, còi bíp kêu 3 tiếng ngắn
        showMessage("Recognition Fail", "Unknown/Invalid");
        alertBuzzer(100, 3);
        delay(1500);
    }

    showMessage("KidCheck System", "Ready...");
    is_recognizing = false;
}

// ============================================================================
// KHỐI 5: HÀM CẤU HÌNH BAN ĐẦU (SETUP) VÀ VÒNG LẶP ĐA NHIỆM (LOOP)
// ============================================================================
void setup() {
    Serial.begin(115200);
    Serial.println("\n[SYSTEM] Khoi dong he thong KidCheck IoT...");

    // Cấu hình chân ngoại vi (PIR chân 12, LED chân 2, Buzzer chân 13)
    pinMode(PIR_PIN, INPUT);
    pinMode(LED_PIN, OUTPUT);
    pinMode(BUZZER_PIN, OUTPUT);
    digitalWrite(LED_PIN, LOW);
    digitalWrite(BUZZER_PIN, LOW);

    digitalWrite(LED_PIN, HIGH);
    delay(400);
    digitalWrite(LED_PIN, LOW);

    // Khởi tạo camera trước
    Serial.println("[CAMERA] Dang setup cam...");
    initCamera();
    Serial.println("[CAMERA] Setup camera THANH CONG!");

    // Khởi tạo màn hình LCD (I2C SDA chân 14, SCL chân 15)
    Wire.begin(I2C_SDA, I2C_SCL);
    lcd.init();
    lcd.backlight();
    showMessage("KidCheck IoT", "Booting system...");
    delay(1000);

    // Cấu hình chế độ mạng song song (WIFI_AP_STA)
    WiFi.mode(WIFI_AP_STA);
    IPAddress apIP(192, 168, 4, 1);
    WiFi.softAPConfig(apIP, apIP, IPAddress(255, 255, 255, 0));
    WiFi.softAP(ap_ssid.c_str(), ap_password.c_str());
    Serial.println("[AP] Mang phat song: " + ap_ssid + " | Mk: " + ap_password);
    Serial.print("[AP] Dia chi IP AP: ");
    Serial.println(WiFi.softAPIP());

    Serial.println("[WiFi] Dang thu ket noi lai WiFi da luu...");
    WiFi.begin();

    int wifiRetry = 0;
    while (WiFi.status() != WL_CONNECTED && wifiRetry < 16) {
        delay(500);
        wifiRetry++;
        Serial.print(".");
    }
    Serial.println();

    if (WiFi.status() == WL_CONNECTED) {
        showMessage("WiFi OK!", WiFi.localIP().toString().c_str());
        Serial.println("[WiFi] Ket noi thanh cong: " + WiFi.localIP().toString());
        delay(1500);
        int retry = 0;
        while (!getAiServerIP() && retry < 5) { delay(1000); retry++; }
        if (ai_server_base_url != "") {
            sendBootStatus(true);
        }
    } else {
        Serial.println("[WiFi] Khong tim thay WiFi. Dang cho cau hinh AP Portal.");
        showMessage("Setup AP Portal", "SSID:KidCheck-AP");
    }

    // Khởi động DNS Server cổng 53 phục vụ chuyển hướng Captive Portal
    dnsServer.start(DNS_PORT, "*", WiFi.softAPIP());

    // Đăng ký cổng Web Server nội bộ
    server.on("/", HTTP_GET, handleWifiPortal);
    server.on("/save-wifi", HTTP_POST, handleSaveWifi);
    server.on("/capture-reg", HTTP_GET, handleCaptureRegister);
    server.on("/led", HTTP_GET, handleLEDControl);
    server.on("/reboot", HTTP_GET, handleReboot);
    server.on("/stream", HTTP_GET, handleStream);
    
    // Bẫy chuyển hướng Captive Portal tự động cho Windows/Android/iOS
    server.on("/generate_204", HTTP_GET, handleWifiPortal);
    server.on("/connecttest.txt", HTTP_GET, handleWifiPortal);
    server.on("/redirect", HTTP_GET, handleWifiPortal);
    server.on("/hotspot-detect.html", HTTP_GET, handleWifiPortal);
    server.on("/canonical.html", HTTP_GET, handleWifiPortal);
    
    server.onNotFound([]() {
        if (!isCaptivePortal()) {
            handleWifiPortal();
        }
    });

    server.begin();
    Serial.println("[HTTP] Local Web Server dang chay o cong 80.");

    alertBuzzer(500, 1); // Còi bíp báo hiệu hoàn tất setup
}

void loop() {
    dnsServer.processNextRequest();
    server.handleClient();

    // Heartbeat định kỳ 30 giây gửi trạng thái ngầm (KHÔNG báo còi, KHÔNG đổi LCD)
    static unsigned long last_heartbeat_time = 0;
    if (millis() - last_heartbeat_time > 30000) {
        if (ai_server_base_url != "") {
            sendBootStatus(false); // Gửi ngầm: update_lcd = false
        }
        last_heartbeat_time = millis();
    }

    // Điểm danh thời gian thực qua cảm biến PIR chân IO12 (Hoạt động cả khi Online lẫn Standalone AP Offline)
    if (digitalRead(PIR_PIN) == HIGH) {
        captureAndRecognize();
        while (digitalRead(PIR_PIN) == HIGH) {
            dnsServer.processNextRequest();
            server.handleClient();
            delay(200);
        }
    }
    delay(10);
}

#define PART_BOUNDARY "123456789000000000000987654321"
static const char* _STREAM_CONTENT_TYPE = "multipart/x-mixed-replace; boundary=" PART_BOUNDARY;
static const char* _STREAM_BOUNDARY = "\r\n--" PART_BOUNDARY "\r\n";
static const char* _STREAM_PART = "Content-Type: image/jpeg\r\nContent-Length: %u\r\n\r\n";

void handleStream() {
  server.sendHeader("Access-Control-Allow-Origin", "*");
  
  if (is_recognizing) {
    server.send(503, "text/plain", "Camera busy recognizing");
    return;
  }

  camera_fb_t * fb = esp_camera_fb_get();
  if (!fb) {
    server.send(500, "text/plain", "Camera capture error");
    return;
  }

  server.setContentLength(fb->len);
  server.send(200, "image/jpeg", "");
  WiFiClient client = server.client();
  client.write(fb->buf, fb->len);
  esp_camera_fb_return(fb);
}
