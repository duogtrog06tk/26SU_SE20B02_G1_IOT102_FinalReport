"""
firebase_service.py
Module ghi dữ liệu điểm danh lên Firebase Realtime Database.

Tại sao tách riêng file này: nếu sau này nhóm đổi sang Firestore hoặc
database khác, chỉ cần sửa file này — app.py và face_service.py không
cần biết Firebase hoạt động ra sao.
"""

import logging
import time
from datetime import datetime
import firebase_admin
from firebase_admin import credentials, db
from config import FIREBASE_CREDENTIALS_PATH, FIREBASE_DATABASE_URL, CHECK_IN_DEADLINE, COOLDOWN_SECONDS

logger = logging.getLogger("firebase_service")


class FirebaseService:
    def __init__(self):
        # _last_checkin_time: lưu lần điểm danh gần nhất của mỗi student_id
        # dùng để chống ghi trùng liên tục (cooldown) — xem hàm should_skip_duplicate()
        self._last_checkin_time = {}
        self._init_firebase()

    def _init_firebase(self):
        """
        Khởi tạo kết nối Firebase 1 lần duy nhất khi server start.
        Input: không có — đọc file serviceAccountKey.json từ config.py
        Output: không trả về, nhưng nếu lỗi sẽ raise exception ngay khi server
                khởi động (cố ý — để biết ngay nếu thiếu file key, không để
                lỗi xuất hiện âm thầm lúc đang chạy).
        """
        try:
            cred = credentials.Certificate(FIREBASE_CREDENTIALS_PATH)
            firebase_admin.initialize_app(cred, {"databaseURL": FIREBASE_DATABASE_URL})
            logger.info("Kết nối Firebase thành công")
        except FileNotFoundError:
            logger.error(
                f"Không tìm thấy file {FIREBASE_CREDENTIALS_PATH}. "
                "Tải file này từ Firebase Console > Project Settings > Service Accounts."
            )
            raise

    def register_server_ip(self, ip, port=5000):
        """Ghi địa chỉ IP:port của AI-server lên Firebase để ESP32 tự động đọc."""
        url = f"http://{ip}:{port}"
        ref = db.reference("System/AI_Server_IP")
        ref.set({"url": url})
        logger.info(f"Đã đăng ký AI-Server IP lên Firebase: {url}")
        return url

    def should_skip_duplicate(self, student_id):
        """
        Kiểm tra xem học sinh này có vừa được điểm danh trong vài giây gần đây không.
        Tránh trường hợp học sinh đứng trước camera 2-3 giây, hệ thống ghi
        điểm danh 5-6 lần liên tiếp lên Firebase.

        Input: student_id (str)
        Output: True nếu nên bỏ qua (vừa ghi gần đây), False nếu nên ghi tiếp
        """
        now = time.time()
        last_time = self._last_checkin_time.get(student_id)

        if last_time is not None and (now - last_time) < COOLDOWN_SECONDS:
            logger.info(f"Bỏ qua ghi trùng cho {student_id} — vừa điểm danh {now - last_time:.1f}s trước")
            return True

        self._last_checkin_time[student_id] = now
        return False

    def write_attendance(self, student_id, image_url=None):
        """
        Ghi 1 lượt điểm danh vào Firebase.

        Cấu trúc dữ liệu trong Firebase:
            attendance/
              2026-06-23/
                HS001_NguyenAn/
                  student_name: "HS001_NguyenAn"
                  timestamp: "08:12:34"
                  status: "late"
                  image_url: "..."

        Input:
            student_id: mã định danh học sinh (tên thư mục trong known_faces/)
            image_url: link ảnh chụp lúc điểm danh (có thể None nếu chưa upload ảnh)

        Output: dict dữ liệu vừa ghi — để app.py trả lại cho ESP32-CAM biết kết quả

        Nếu lỗi: nếu mất kết nối Firebase, raise exception để app.py
        bắt được và trả lỗi rõ ràng về ESP32-CAM, không âm thầm bỏ qua.
        """
        now = datetime.now()
        today_str = now.strftime("%Y-%m-%d")
        time_str = now.strftime("%H:%M:%S")

        status = "late" if time_str > CHECK_IN_DEADLINE else "present"

        record = {
            "student_name": student_id,
            "timestamp": time_str,
            "status": status,
            "image_url": image_url
        }

        ref = db.reference(f"attendance/{today_str}/{student_id}")
        ref.set(record)

        logger.info(f"Đã ghi Firebase: {student_id} | {time_str} | {status}")
        return record
