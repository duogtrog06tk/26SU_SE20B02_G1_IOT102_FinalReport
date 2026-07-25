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

// --- KHAI BÁO NGUYÊN MẪU HÀM (FUNCTION PROTOTYPES) ---
void initCamera();
void alertBuzzer(int duration, int count);
void showMessage(const char* line1, const char* line2);
bool getAiServerIP();
void sendBootStatus(bool update_lcd = true);
void handleCaptureRegister();
void handleLEDControl();
void handleReboot();
void captureAndRecognize();
void handleStream();

// --- CẤU HÌNH LIÊN KẾT ĐÁM MÂY (FIREBASE TĨNH) ---
const char* firebase_host = "kidcheckdb-default-rtdb.asia-southeast1.firebasedatabase.app";

// Biến toàn cục lưu trữ địa chỉ URL động của AI-Server sau khi đọc từ Firebase
String ai_server_base_url = "";

// Biến cờ bảo vệ tài nguyên phần cứng camera tránh xung đột giữa Live Stream & Nhận diện PIR
volatile bool is_recognizing = false;
const char* wifi_ssid = "duongtrong";
const char* wifi_password = "12345678";

// --- ĐỊNH NGHĨA SƠ ĐỒ CHÂN PHẦN CỨNG (HARDWARE MAP) ---
#define PIR_PIN 12     // Chân OUT của cảm biến chuyển động PIR AM312
#define LED_PIN 2      // Chân điều khiển LED đỏ trợ sáng/báo trạng thái
#define BUZZER_PIN 13  // Chân điều khiển còi chíp Buzzer 3V
#define I2C_SDA 14     // Chân dữ liệu SDA của màn hình LCD 1602
#define I2C_SCL 15     // Chân xung nhịp SCL của màn hình LCD 1602

// Khởi tạo các đối tượng điều khiển ngoại vi
LiquidCrystal_I2C lcd(0x27, 16, 2);
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
    if (fb) esp_camera_fb_return(fb); // Xả ảnh cũ trong buffer
    fb = esp_camera_fb_get(); // Lấy ảnh mới hiện tại
    
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

// Các hàm liên quan đến AP và Captive Portal đã được xóa để nạp cứng WiFi

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
                StaticJsonDocument<512> doc;
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

    // Cấu hình mạng Station (WIFI_STA) hardcode
    WiFi.mode(WIFI_STA);
    WiFi.disconnect();
    delay(100);

    Serial.println("[WiFi] Dang ket noi WiFi: " + String(wifi_ssid));
    showMessage("WiFi Connect...", wifi_ssid);
    WiFi.begin(wifi_ssid, wifi_password);

    int wifiRetry = 0;
    while (WiFi.status() != WL_CONNECTED) {
        delay(500);
        wifiRetry++;
        Serial.print(".");
        // Reset mạch nếu không kết nối được sau 1 phút (120 * 500ms = 60s)
        if (wifiRetry > 120) {
            Serial.println("\n[WiFi] Khong the ket noi WiFi. Khoi dong lai mach...");
            ESP.restart();
        }
    }
    Serial.println();

    showMessage("WiFi OK!", WiFi.localIP().toString().c_str());
    Serial.println("[WiFi] Ket noi thanh cong: " + WiFi.localIP().toString());
    delay(1500);
    
    int retry = 0;
    while (!getAiServerIP() && retry < 5) { delay(1000); retry++; }
    if (ai_server_base_url != "") {
        sendBootStatus(true);
    }

    // Đăng ký cổng Web Server nội bộ
    server.on("/capture-reg", HTTP_GET, handleCaptureRegister);
    server.on("/led", HTTP_GET, handleLEDControl);
    server.on("/reboot", HTTP_GET, handleReboot);
    server.on("/stream", HTTP_GET, handleStream);
    
    server.onNotFound([]() {
        server.send(404, "text/plain", "API Not Found");
    });

    server.begin();
    Serial.println("[HTTP] Local Web Server dang chay o cong 80.");

    alertBuzzer(500, 1); // Còi bíp báo hiệu hoàn tất setup
}

void loop() {
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
