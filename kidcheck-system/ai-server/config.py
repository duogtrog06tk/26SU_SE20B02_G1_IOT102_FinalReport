"""
config.py
Cấu hình chung cho toàn bộ AI server.
Sửa các giá trị ở đây thay vì sửa rải rác trong nhiều file khác.
"""

import os

# Ngưỡng độ tin cậy để coi là "nhận ra đúng người"
# face_recognition trả về "khoảng cách" (distance), càng nhỏ càng giống.
# 0.6 là ngưỡng mặc định khuyến nghị bởi thư viện dlib.
FACE_MATCH_THRESHOLD = 0.6

# Giờ quy định điểm danh — sau giờ này tính là đi trễ
CHECK_IN_DEADLINE = "08:00:00"

# Thời gian cooldown (giây) — tránh ghi điểm danh trùng liên tục
# cho cùng 1 học sinh trong khoảng thời gian ngắn
COOLDOWN_SECONDS = 8

# Đường dẫn thư mục chứa ảnh khuôn mặt đã đăng ký
KNOWN_FACES_DIR = os.path.join(os.path.dirname(__file__), "known_faces")

# Đường dẫn file khóa Firebase (tải từ Firebase Console)
FIREBASE_CREDENTIALS_PATH = os.path.join(os.path.dirname(__file__), "serviceAccountKey.json")

# URL Firebase Realtime Database — lấy trong Firebase Console > Realtime Database
FIREBASE_DATABASE_URL = "https://kidcheckdb-default-rtdb.asia-southeast1.firebasedatabase.app/"