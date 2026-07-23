# KidCheck-IOT102

## 🚀 HƯỚNG DẪN KHỞI CHẠY DỰ ÁN KHI DEMO

Hệ thống điểm danh tự động KidCheck gồm 3 thành phần chính hoạt động phối hợp: **Java Web App**, **Python AI Server**, và **Thiết bị phần cứng ESP32-CAM**. Hãy khởi chạy các thành phần theo trình tự sau:

### Bước 1: Khởi chạy Python AI Server (Cổng 5000)
AI Server đóng vai trò Gateway xử lý nhận diện khuôn mặt và điều phối lệnh chụp ảnh.

**🔥 Cách nhanh nhất (Dành cho Windows):**
Chỉ cần nhấp đúp chuột vào tệp **`run_ai_server.bat`** nằm trong thư mục `SourceCode/ai-server/`. Hệ thống sẽ tự động bật môi trường ảo và khởi chạy máy chủ!

**Cách chạy thủ công (nếu không dùng file .bat):**
1. Mở Terminal / CMD / PowerShell, di chuyển vào thư mục dự án AI Server:
   ```bash
   cd SourceCode/ai-server
   ```
2. Kích hoạt môi trường ảo Python (venv) (Hoặc tự tạo bằng lệnh `python -m venv venv` nếu chưa có):
   * Trên Windows (Command Prompt):
     ```cmd
     venv\Scripts\activate
     ```
   * Trên Windows (PowerShell):
     ```powershell
     .\venv\Scripts\Activate.ps1
     ```
   * Trên Linux / macOS:
     ```bash
     source venv/bin/activate
     ```
3. Cài đặt các thư viện (chỉ làm lần đầu):
   ```bash
   pip install -r requirements.txt
   ```
4. Khởi chạy máy chủ Flask:
   ```bash
   python app.py
   ```
   *Máy chủ sẽ tự động quét địa chỉ IP LAN cục bộ của máy tính bạn (ví dụ: `192.168.1.8`) và ghi đè đăng ký lên Firebase Realtime Database tại khóa `/System/AI_Server_IP` để ESP32-CAM tự động biết và kết nối.*

### Bước 2: Khởi chạy Java Web App (Cổng 8080)
Web App cung cấp bảng điều khiển (Dashboard) quản lý lớp học, học sinh, điểm danh và điều khiển camera.
1. Mở dự án Java trong thư mục `SourceCode/web/` bằng IDE (IntelliJ IDEA, Eclipse, NetBeans) hoặc Visual Studio Code.
2. Build dự án và chạy ứng dụng trên máy chủ local Tomcat hoặc qua lệnh Maven:
   ```bash
   mvn clean spring-boot:run
   ```
3. Truy cập vào trang quản lý bằng trình duyệt tại địa chỉ:
   ```url
   http://localhost:8080
   ```

### Bước 3: Khởi chạy và vận hành ESP32-CAM
Mạch camera thực hiện chụp ảnh khi phát hiện chuyển động (PIR) hoặc nhận lệnh chụp đăng ký từ AI Server.
1. Mở file [KidCheck_Firmware.ino](file:///d:/Officefiles/Ki_4/IOT102/KidCheck-IOT102/kidcheck-system/firmware/KidCheck_Firmware/KidCheck_Firmware.ino) bằng **Arduino IDE**.
2. Chọn đúng Board (`AI Thinker ESP32-CAM`) và cổng COM, sau đó nhấn **Upload** để biên dịch và nạp code xuống mạch.
3. **Cấu hình WiFi lần đầu cho mạch:**
   - Mạch sẽ phát ra một điểm truy cập WiFi riêng có tên: **`KidCheck-Setup-Wifi`** (Mật khẩu: **`admin123`**).
   - Sử dụng điện thoại hoặc máy tính kết nối vào WiFi này. Trình duyệt sẽ tự động nhảy ra giao diện cấu hình (Captive Portal).
   - Chọn mạng WiFi gia đình bạn và nhập mật khẩu của mạng đó, sau đó nhấn kết nối.
   - Khi kết nối thành công, màn hình LCD trên mạch sẽ báo `WiFi OK!`, mạch tự động gửi IP của nó lên Firebase và tải IP của AI Server để sẵn sàng hoạt động.

---

# Code cơ bản


    Sửa code → add → commit → push

-Khởi tạo (initialize) một project thành Git repository:

    git init 

-Thêm toàn bộ file vào staging:

    git add . 

-Thêm file muốn thêm:

    git add <Tên File>

-Lưu trạng thái hiện tại với comment để miêu tả cho sự thay đổi:

    git commit -m "comment"

-code để link với repo trên github, link vào mới push được nha ae:

    git remote add origin https://github.com/duongtrong06tk-rgb/KidCheck-IOT102.git

-Đẩy code lên GitHub:

    git push -u origin main

-Lấy code mới từ GitHub:

    git pull origin main

-Kiểm tra trạng thái xem file nào đã sửa, file nào chưa commit:

    git status

-Xem lịch sử commit (Nhấn phím "q" để thoát log):

    git log
