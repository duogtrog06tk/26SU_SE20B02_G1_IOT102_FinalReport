"""
face_service.py
Module xử lý nhận diện khuôn mặt:
  1. Load toàn bộ ảnh trong known_faces/ và encode thành vector đặc trưng
  2. So sánh ảnh mới gửi lên với các vector đã có
  3. Trả về tên học sinh khớp nhất (hoặc None nếu không nhận ra ai)

Tại sao tách riêng file này: app.py chỉ lo nhận request, còn toàn bộ
logic "AI" nằm ở đây — dễ test riêng, dễ thay đổi thuật toán sau này
mà không đụng vào phần Flask.
"""

import os
import logging
import cv2
import face_recognition
import numpy as np
from config import KNOWN_FACES_DIR, FACE_MATCH_THRESHOLD

logger = logging.getLogger("face_service")


class FaceService:
    def __init__(self):
        # known_encodings: list các vector 128 chiều, mỗi vector đại diện 1 ảnh khuôn mặt
        # known_names: list tên tương ứng theo CÙNG thứ tự với known_encodings
        # Hai list này luôn đi cùng nhau theo index — encoding[i] thuộc về name[i]
        self.known_encodings = []
        self.known_names = []
        self.load_known_faces()

    def load_known_faces(self):
        """
        Quét toàn bộ thư mục known_faces/, mỗi thư mục con là 1 học sinh.
        Cấu trúc mong đợi:
            known_faces/
                HS001_NguyenAn/
                    1.jpg
                    2.jpg
                HS002_TranBinh/
                    1.jpg

        Input: không có (đọc trực tiếp từ ổ đĩa)
        Output: không trả về gì, nhưng cập nhật self.known_encodings và self.known_names

        Nếu lỗi: nếu thư mục known_faces/ rỗng hoặc không tồn tại, hệ thống
        vẫn chạy được nhưng sẽ không nhận ra ai cả — luôn trả "Unknown".
        """
        self.known_encodings = []
        self.known_names = []

        if not os.path.isdir(KNOWN_FACES_DIR):
            logger.warning(f"Không tìm thấy thư mục {KNOWN_FACES_DIR} — chưa có học sinh nào đăng ký")
            return

        student_folders = os.listdir(KNOWN_FACES_DIR)
        for student_id in student_folders:
            student_path = os.path.join(KNOWN_FACES_DIR, student_id)
            if not os.path.isdir(student_path):
                continue

            for filename in os.listdir(student_path):
                if not filename.lower().endswith((".jpg", ".jpeg", ".png")):
                    continue

                image_path = os.path.join(student_path, filename)
                try:
                    image = face_recognition.load_image_file(image_path)
                    # Một ảnh có thể chứa nhiều khuôn mặt — lấy khuôn mặt đầu tiên tìm được
                    encodings = face_recognition.face_encodings(image)

                    if len(encodings) == 0:
                        logger.warning(f"Không tìm thấy khuôn mặt nào trong ảnh {image_path} — bỏ qua")
                        continue

                    self.known_encodings.append(encodings[0])
                    self.known_names.append(student_id)
                    logger.info(f"Đã encode: {student_id} từ ảnh {filename}")

                except Exception as e:
                    logger.error(f"Lỗi khi encode ảnh {image_path}: {e}")

        logger.info(f"Tổng cộng đã load {len(self.known_names)} ảnh khuôn mặt từ {len(set(self.known_names))} học sinh")

    def recognize(self, image_array):
        """
        So sánh 1 ảnh mới (đã đọc thành numpy array) với toàn bộ known_encodings.

        Input:
            image_array: ảnh dạng numpy array (đọc bằng face_recognition.load_image_file
                         hoặc OpenCV rồi convert BGR -> RGB)

        Output: dict gồm:
            {
                "recognized": True/False,
                "student_id": "HS001_NguyenAn" hoặc None,
                "confidence": 0.42 (khoảng cách, càng nhỏ càng chắc chắn) hoặc None
            }

        Nếu lỗi: nếu ảnh gửi lên không có khuôn mặt nào (học sinh đứng lệch góc,
        ảnh mờ...), trả về recognized=False, không raise exception để API không bị crash.
        """
        face_locations = face_recognition.face_locations(image_array)

        if len(face_locations) == 0:
            logger.info("Không phát hiện khuôn mặt nào trong ảnh gửi lên")
            return {"recognized": False, "student_id": None, "confidence": None}

        # Chỉ xử lý khuôn mặt đầu tiên tìm được trong ảnh
        unknown_encoding = face_recognition.face_encodings(image_array, face_locations)[0]

        if len(self.known_encodings) == 0:
            logger.warning("known_encodings rỗng — chưa có học sinh nào đăng ký")
            return {"recognized": False, "student_id": None, "confidence": None}

        # face_distance trả về khoảng cách giữa ảnh mới và TỪNG ảnh đã biết
        # Khoảng cách nhỏ = giống nhau nhiều
        distances = face_recognition.face_distance(self.known_encodings, unknown_encoding)
        best_match_index = int(np.argmin(distances))
        best_distance = float(distances[best_match_index])

        if best_distance <= FACE_MATCH_THRESHOLD:
            matched_student_id = self.known_names[best_match_index]
            logger.info(f"Nhận ra: {matched_student_id} (khoảng cách: {best_distance:.3f})")
            return {
                "recognized": True,
                "student_id": matched_student_id,
                "confidence": round(best_distance, 3)
            }
        else:
            logger.info(f"Không khớp ai — khoảng cách gần nhất: {best_distance:.3f} (ngưỡng: {FACE_MATCH_THRESHOLD})")
            return {"recognized": False, "student_id": None, "confidence": round(best_distance, 3)}

    def save_new_face(self, student_id, image_array):
        """
        Lưu 1 ảnh đăng ký mới vào known_faces/{student_id}/ và encode ngay.
        Dùng cho luồng "Đăng ký từ xa qua ESP32-CAM" — khác với recognize() ở trên
        vì đây là THÊM dữ liệu mới, không phải so sánh.

        Input:
            student_id: mã định danh học sinh, dùng làm tên thư mục
                        (ví dụ "HS010_VuGiang" — không dấu, không khoảng trắng)
            image_array: ảnh dạng numpy array RGB (giống recognize())
        - student_id: mã học sinh
        - image_array: ảnh (numpy array RGB)
        
        Output: dictionary kết quả
        """
        # 1. Xóa các encodings cũ của học sinh này (nếu có) để tránh nhận diện sai khi cập nhật ảnh mới
        self.remove_face_encodings(student_id)

        face_locations = face_recognition.face_locations(image_array)

        if len(face_locations) == 0:
            logger.warning(f"Ảnh đăng ký cho {student_id} không có khuôn mặt rõ — không lưu")
            return {"success": False, "error": "Không phát hiện khuôn mặt trong ảnh, vui lòng chụp lại"}

        student_dir = os.path.join(KNOWN_FACES_DIR, student_id)
        os.makedirs(student_dir, exist_ok=True)

        existing_photos = [f for f in os.listdir(student_dir) if f.lower().endswith((".jpg", ".jpeg", ".png"))]
        next_index = len(existing_photos) + 1
        photo_path = os.path.join(student_dir, f"{next_index}.jpg")

        # face_recognition dùng ảnh RGB, nhưng lưu file cần đổi lại BGR cho OpenCV ghi đúng màu
        image_bgr = cv2.cvtColor(image_array, cv2.COLOR_RGB2BGR)
        cv2.imwrite(photo_path, image_bgr)
        logger.info(f"Đã lưu ảnh đăng ký: {photo_path}")

        # Encode ngay ảnh vừa lưu, thêm vào danh sách known_encodings hiện tại
        # — không cần load lại TOÀN BỘ known_faces/, chỉ thêm 1 ảnh mới này thôi
        new_encoding = face_recognition.face_encodings(image_array, face_locations)[0]
        self.known_encodings.append(new_encoding)
        self.known_names.append(student_id)

        return {"success": True, "photo_count": next_index}
    def remove_face_encodings(self, student_id):
        indices_to_remove = [i for i, name in enumerate(self.known_names) if name == student_id]
        for i in sorted(indices_to_remove, reverse=True):
            del self.known_names[i]
            del self.known_encodings[i]
        if indices_to_remove:
            logger.info(f'Da xoa {len(indices_to_remove)} encodings cu cua {student_id} trong RAM.')
