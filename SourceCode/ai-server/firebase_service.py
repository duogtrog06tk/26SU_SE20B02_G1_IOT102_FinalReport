"""
firebase_service.py
Module ghi dữ liệu điểm danh lên Firebase Realtime Database.

Tại sao tách riêng file này: nếu sau này nhóm đổi sang Firestore hoặc
database khác, chỉ cần sửa file này — app.py và face_service.py không
cần biết Firebase hoạt động ra sao.
"""

import logging
import time
import requests
import email_service
from datetime import datetime
import firebase_admin
from firebase_admin import credentials, db
from config import FIREBASE_CREDENTIALS_PATH, FIREBASE_DATABASE_URL, CHECK_IN_DEADLINE, PICKUP_START_TIME, COOLDOWN_SECONDS

logger = logging.getLogger("firebase_service")


class FirebaseService:
    def __init__(self):
        # Caches for refactored attendance logic
        self._classes_cache = {}
        self._students_cache = {}
        self._today_attendance = {} # format: {"student_id": {"in": bool, "out": bool}}
        self._current_date = None
        
        self._last_checkin_time = {}
        self.use_rest_fallback = False
        self.database_url = FIREBASE_DATABASE_URL
        if not self.database_url.endswith("/"):
            self.database_url += "/"
        self._init_firebase()

    def _init_firebase(self):
        """
        Khởi tạo kết nối Firebase 1 lần duy nhất khi server start.
        Nếu lỗi xác thực (do IAM propagation hoặc key lỗi), tự động kích hoạt chế độ REST Fallback.
        """
        try:
            cred = credentials.Certificate(FIREBASE_CREDENTIALS_PATH)
            firebase_admin.initialize_app(cred, {"databaseURL": self.database_url})
            logger.info("Kết nối Firebase Admin SDK thành công")
            
            # Lắng nghe thay đổi của bảng classes để xóa cache tự động
            try:
                db.reference("classes").listen(self._on_classes_change)
                logger.info("Đã đăng ký lắng nghe realtime (Realtime Listener) cho cấu hình lớp học.")
            except Exception as listen_e:
                logger.warning(f"Không thể đăng ký listen classes: {listen_e}")

        except FileNotFoundError:
            logger.warning(
                f"Không tìm thấy file {FIREBASE_CREDENTIALS_PATH}. "
                "Sẽ sử dụng chế độ REST API Fallback."
            )
            self.use_rest_fallback = True
        except Exception as e:
            logger.warning(
                f"Không thể khởi tạo Firebase Admin SDK ({e}). "
                "Sẽ sử dụng chế độ REST API Fallback."
            )
            self.use_rest_fallback = True

    def _on_classes_change(self, event):
        """
        Callback được gọi tự động (Realtime) khi có bất kỳ thay đổi nào 
        về khung giờ học trên Firebase (do WebApp cập nhật).
        """
        logger.info("Phát hiện thay đổi cấu hình khung giờ lớp học từ Firebase! Đang xóa Cache...")
        self._classes_cache = {}
        # Xóa luôn cache học sinh để đảm bảo an toàn nếu ai đó đổi lớp
        self._students_cache = {}

    def register_server_ip(self, ip, port=5000):
        """Ghi địa chỉ IP:port của AI-server lên Firebase để ESP32 tự động đọc."""
        url = f"http://{ip}:{port}"
        if self.use_rest_fallback:
            try:
                import requests
                req_url = f"{self.database_url}System/AI_Server_IP.json"
                r = requests.put(req_url, json={"url": url}, timeout=5)
                if r.status_code == 200:
                    logger.info(f"REST Fallback: Đã đăng ký AI-Server IP lên Firebase: {url}")
                else:
                    logger.error(f"REST Fallback Error {r.status_code}: {r.text}")
            except Exception as e:
                logger.error(f"Lỗi khi REST đăng ký IP: {e}")
        else:
            try:
                ref = db.reference("System/AI_Server_IP")
                ref.set({"url": url})
                logger.info(f"Đã đăng ký AI-Server IP lên Firebase: {url}")
            except Exception as e:
                logger.warning(f"Admin SDK đăng ký IP thất bại ({e}). Tự động chuyển sang REST...")
                self.use_rest_fallback = True
                self.register_server_ip(ip, port)
        return url

    def save_device_status(self, device_id, ip, status="ONLINE", ssid="unknown", rssi=0):
        """Lưu IP, Wi-Fi SSID, RSSI và trạng thái của ESP32-CAM lên Firebase."""
        data = {
            "device_id": device_id,
            "ip": ip,
            "status": status,
            "ssid": ssid,
            "rssi": rssi,
            "last_seen": datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        }
        if self.use_rest_fallback:
            try:
                import requests
                req_url = f"{self.database_url}System/ESP32_CAM_Status.json"
                r = requests.put(req_url, json=data, timeout=5)
                if r.status_code == 200:
                    logger.info(f"REST Fallback: Đã lưu trạng thái ESP32-CAM: {device_id} | SSID: {ssid}")
                else:
                    logger.error(f"REST Fallback Error {r.status_code}: {r.text}")
            except Exception as e:
                logger.error(f"Lỗi khi REST lưu trạng thái: {e}")
        else:
            try:
                ref = db.reference("System/ESP32_CAM_Status")
                ref.set(data)
                logger.info(f"Đã lưu trạng thái ESP32-CAM lên Firebase: {device_id} | {ip} | {status} | SSID: {ssid} ({rssi} dBm)")
            except Exception as e:
                logger.warning(f"Admin SDK lưu trạng thái thất bại ({e}). Tự động chuyển sang REST...")
                self.use_rest_fallback = True
                self.save_device_status(device_id, ip, status, ssid, rssi)

    def sync_student_photos(self, known_faces_dir):
        """Tải, giải mã và đồng bộ tất cả ảnh học sinh từ Firebase về thư mục known_faces/ cục bộ."""
        import os
        import base64
        import shutil
        import requests

        logger.info("Đang bắt đầu đồng bộ ảnh học sinh từ Firebase...")
        students = {}
        if self.use_rest_fallback:
            try:
                req_url = f"{self.database_url}students.json"
                r = requests.get(req_url, timeout=5)
                if r.status_code == 200:
                    data = r.json()
                    # Nếu data là None (Firebase trống), gán students = {}
                    students = data if data is not None else {}
                else:
                    logger.error(f"REST Fallback Error {r.status_code}: {r.text}")
                    logger.error("Không thể lấy danh sách học sinh. Hủy đồng bộ để tránh xóa nhầm ảnh cục bộ.")
                    return
            except Exception as e:
                logger.error(f"Lỗi khi REST đồng bộ ảnh: {e}")
                return
        else:
            try:
                ref = db.reference("students")
                students = ref.get() or {}
            except Exception as e:
                logger.warning(f"Admin SDK đồng bộ thất bại ({e}). Tự động chuyển sang REST...")
                self.use_rest_fallback = True
                self.sync_student_photos(known_faces_dir)
                return

        try:
            if not os.path.exists(known_faces_dir):
                os.makedirs(known_faces_dir)

            # Lấy IP cục bộ để tự cập nhật lại link ảnh chính thức (tránh circular import với app.py)
            import socket
            s_temp = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
            try:
                s_temp.connect(("8.8.8.8", 80))
                local_ip = s_temp.getsockname()[0]
            except Exception:
                local_ip = "127.0.0.1"
            finally:
                s_temp.close()

            # 1. Đổi tên thư mục tạm (ví dụ TEMP_HS sang mã số thực sự của bé)
            for student_key, student_data in students.items():
                if not student_data:
                    continue
                student_id = student_data.get("id")
                photo = student_data.get("photo")
                if not student_id or not photo:
                    continue

                if "/faces/" in photo:
                    source_id = photo.split("/faces/")[-1].split("?")[0]
                    if source_id != student_id:
                        source_dir = os.path.join(known_faces_dir, source_id)
                        student_dir = os.path.join(known_faces_dir, student_id)
                        if os.path.exists(source_dir) and not os.path.exists(student_dir):
                            logger.info(f"Đang đổi tên thư mục ảnh từ {source_id} sang {student_id}")
                            try:
                                shutil.move(source_dir, student_dir)
                                new_photo_url = f"http://{local_ip}:5000/faces/{student_id}"
                                
                                if self.use_rest_fallback:
                                    req_url = f"{self.database_url}students/{student_id}.json"
                                    requests.patch(req_url, json={"photo": new_photo_url}, timeout=5)
                                else:
                                    db.reference(f"students/{student_id}").update({
                                        "photo": new_photo_url
                                    })
                                student_data["photo"] = new_photo_url
                            except Exception as move_err:
                                logger.error(f"Lỗi đổi tên thư mục: {move_err}")

            # 2. Tải và đồng bộ ảnh mới/chưa có về máy
            for student_key, student_data in students.items():
                if not student_data:
                    continue
                student_id = student_data.get("id")
                photo = student_data.get("photo")
                if not student_id or not photo:
                    continue

                student_dir = os.path.join(known_faces_dir, student_id)
                os.makedirs(student_dir, exist_ok=True)

                target_file = os.path.join(student_dir, f"{student_id}.jpg")
                
                if os.path.exists(target_file) or len(os.listdir(student_dir)) > 0:
                    continue

                try:
                    file_data = None
                    if photo.startswith("data:image"):
                        if "," in photo:
                            _, encoded = photo.split(",", 1)
                        else:
                            encoded = photo
                        file_data = base64.b64decode(encoded)
                    elif photo.startswith("http"):
                        r = requests.get(photo, timeout=5)
                        if r.status_code == 200:
                            file_data = r.content

                    if file_data:
                        with open(target_file, "wb") as f:
                            f.write(file_data)
                        logger.info(f"Đã đồng bộ ảnh cho học sinh {student_id}")
                except Exception as e:
                    logger.error(f"Lỗi tải/giải mã ảnh cho {student_id}: {e}")

            # 3. Xóa các thư mục cục bộ của học sinh đã bị xóa khỏi Firebase
            # Chỉ xóa khi node trên Firebase có field 'id' đầy đủ (tránh race condition khi Java chưa kịp ghi xong)
            valid_firebase_ids = set()
            for k, v in students.items():
                if isinstance(v, dict) and v.get("id"):
                    valid_firebase_ids.add(v.get("id"))
                elif isinstance(v, dict):
                    # Node tồn tại nhưng chưa có id — có thể là AI Server vừa PATCH photo, chưa có Java lưu
                    # Giữ lại key này để không xóa nhầm
                    valid_firebase_ids.add(k)

            if os.path.exists(known_faces_dir):
                local_folders = os.listdir(known_faces_dir)
                for folder in local_folders:
                    folder_path = os.path.join(known_faces_dir, folder)
                    if os.path.isdir(folder_path):
                        if folder == "TEMP_HS":
                            continue
                        # Chỉ xóa nếu thư mục không có trong Firebase và không có trong cả key lần id
                        if folder not in students and folder not in valid_firebase_ids:
                            logger.info(f"Học sinh {folder} đã bị xóa trên Firebase. Đang xóa thư mục cục bộ...")
                            try:
                                try:
                                    from app import face_service
                                    face_service.remove_face_encodings(folder)
                                except Exception as inner_e:
                                    logger.error(f"Lỗi xóa RAM encodings: {inner_e}")
                                shutil.rmtree(folder_path)
                            except Exception as delete_err:
                                logger.error(f"Lỗi xóa thư mục cục bộ của {folder}: {delete_err}")
        except Exception as e:
            logger.error(f"Lỗi đồng bộ danh sách học sinh từ Firebase: {e}")

    def get_student_class_config(self, student_id):
        """Lấy thông tin học sinh và khung giờ của lớp từ cache (TTL 5 phút) hoặc Firebase."""
        now_ts = time.time()
        
        # Kiểm tra cache học sinh (hết hạn sau 5 phút)
        if student_id not in self._students_cache or (now_ts - self._students_cache[student_id].get("ts", 0)) > 300:
            try:
                if self.use_rest_fallback:
                    import requests
                    r = requests.get(f"{self.database_url}students/{student_id}.json", timeout=3)
                    if r.status_code == 200 and r.json():
                        self._students_cache[student_id] = {"data": r.json(), "ts": now_ts}
                else:
                    st_data = db.reference(f"students/{student_id}").get()
                    if st_data:
                        self._students_cache[student_id] = {"data": st_data, "ts": now_ts}
            except Exception as e:
                logger.warning(f"Lỗi lấy thông tin học sinh {student_id}: {e}")

        student = self._students_cache.get(student_id, {}).get("data", {})
        class_name = student.get("className", "")
        
        # Kiểm tra cache lớp học (hết hạn sau 5 phút)
        if class_name and (class_name not in self._classes_cache or (now_ts - self._classes_cache[class_name].get("ts", 0)) > 300):
            try:
                if self.use_rest_fallback:
                    import requests
                    r = requests.get(f"{self.database_url}classes/{class_name}.json", timeout=3)
                    if r.status_code == 200 and r.json():
                        self._classes_cache[class_name] = {"data": r.json(), "ts": now_ts}
                else:
                    cls_data = db.reference(f"classes/{class_name}").get()
                    if cls_data:
                        self._classes_cache[class_name] = {"data": cls_data, "ts": now_ts}
            except Exception as e:
                logger.warning(f"Lỗi lấy thông tin lớp {class_name}: {e}")

        class_info = self._classes_cache.get(class_name, {}).get("data", {})
        return student, class_info

    def should_skip_duplicate(self, student_id):
        """
        Kiểm tra nếu học sinh nên bị bỏ qua ghi nhận diện (chống spam Web/Firebase).
        Quy tắc:
        - Buổi sáng (trước pickupTime): Chỉ ghi 1 lần.
        - Buổi chiều (sau pickupTime): Chỉ ghi 1 lần VÀ phải có điểm danh sáng.
        """
        now = datetime.now()
        today_str = now.strftime("%Y-%m-%d")
        time_str = now.strftime("%H:%M:%S")

        # Reset cache nếu qua ngày mới
        if self._current_date != today_str:
            self._today_attendance = {}
            self._current_date = today_str

        student, class_info = self.get_student_class_config(student_id)
        pickup_time = class_info.get("pickupTime", PICKUP_START_TIME)

        # Lấy trạng thái điểm danh trong ngày từ Cache hoặc tải lại từ Firebase nếu chưa có
        if student_id not in self._today_attendance:
            in_status = False
            out_status = False
            try:
                if self.use_rest_fallback:
                    import requests
                    r_in = requests.get(f"{self.database_url}attendance/{today_str}/{student_id}_in.json", timeout=2)
                    if r_in.status_code == 200 and r_in.json(): in_status = True
                    r_out = requests.get(f"{self.database_url}attendance/{today_str}/{student_id}_out.json", timeout=2)
                    if r_out.status_code == 200 and r_out.json(): out_status = True
                else:
                    if db.reference(f"attendance/{today_str}/{student_id}_in").get(): in_status = True
                    if db.reference(f"attendance/{today_str}/{student_id}_out").get(): out_status = True
            except Exception:
                pass
            self._today_attendance[student_id] = {"in": in_status, "out": out_status}

        att = self._today_attendance[student_id]

        if time_str < pickup_time:
            # Nhận diện buổi sáng
            if att["in"]:
                return True # Đã điểm danh sáng rồi -> Bỏ qua web, chỉ kêu bíp
            return False
        else:
            # Nhận diện buổi chiều
            if not att["in"]:
                return True # Sáng vắng mặt -> Bỏ qua web chiều luôn
            if att["out"]:
                return True # Đã đón buổi chiều rồi -> Bỏ qua web
            return False

    def write_attendance(self, student_id, image_url=None):
        now = datetime.now()
        today_str = now.strftime("%Y-%m-%d")
        time_str = now.strftime("%H:%M:%S")

        student, class_info = self.get_student_class_config(student_id)
        student_name = student.get("name") or student_id
        parent_email = student.get("parentEmail")
        
        pickup_time = class_info.get("pickupTime", PICKUP_START_TIME)
        checkin_deadline = class_info.get("lateTime", CHECK_IN_DEADLINE)

        is_pickup = False
        if time_str > pickup_time:
            status = "pickup"
            is_pickup = True
            node_suffix = "_out"
        else:
            status = "late" if time_str > checkin_deadline else "present"
            node_suffix = "_in"

        # Update cache IMMEDIATELY so concurrent/immediate next recognitions skip writing
        if student_id not in self._today_attendance:
            self._today_attendance[student_id] = {"in": False, "out": False}
        if is_pickup:
            self._today_attendance[student_id]["out"] = True
        else:
            self._today_attendance[student_id]["in"] = True

        existing_log = None
        try:
            if self.use_rest_fallback:
                import requests
                r_att = requests.get(f"{self.database_url}attendance/{today_str}/{student_id}{node_suffix}.json", timeout=3)
                if r_att.status_code == 200 and r_att.json():
                    existing_log = r_att.json()
            else:
                existing_log = db.reference(f"attendance/{today_str}/{student_id}{node_suffix}").get()
        except Exception as e:
            logger.warning(f"Không thể lấy attendance hiện tại cho {student_id}: {e}")

        if existing_log and isinstance(existing_log, dict):
            # Nếu đã có điểm danh, giữ nguyên timestamp và status
            time_str = existing_log.get("timestamp", time_str)
            status = existing_log.get("status", status)

        record = {
            "id": student_id,
            "student_name": student_name,
            "timestamp": time_str,
            "status": status,
            "photo": image_url or "",
            "captured_photo": image_url or "",
            "image_url": image_url or ""
        }

        display_status = "Đón về" if is_pickup else ("Có mặt" if status == "present" else "Đi trễ")
        latest_record = {
            "id": student_id,
            "name": student_name,
            "status": display_status,
            "time": f"{today_str} {time_str}",
            "photo": image_url or ""
        }

        if self.use_rest_fallback:
            try:
                import requests
                req_url = f"{self.database_url}attendance/{today_str}/{student_id}{node_suffix}.json"
                r = requests.put(req_url, json=record, timeout=5)
                
                # Cập nhật thêm trạng thái vào bảng students để WebApp hiển thị lên Dashboard
                student_url = f"{self.database_url}students/{student_id}.json"
                patch_payload = {"status": display_status, "time": time_str}
                if image_url:
                    patch_payload["capturedPhoto"] = image_url
                requests.patch(student_url, json=patch_payload, timeout=5)
                
                # Ghi nhận diện mới nhất vào System/Latest_Recognition để kích hoạt Welcome Panel ngay trên WebApp
                latest_url = f"{self.database_url}System/Latest_Recognition.json"
                requests.put(latest_url, json=latest_record, timeout=5)

                if r.status_code == 200:
                    logger.info(f"REST Fallback: Đã ghi điểm danh: {student_id} ({student_name}) | {time_str} | {status}")
                    
                    # Kích hoạt gửi Email nếu có Parent Email
                    if parent_email:
                        email_service.send_attendance_email_async(
                            parent_email=parent_email,
                            student_name=student_name,
                            time_str=time_str,
                            base64_image=image_url,
                            is_late=(status == "late"),
                            is_pickup=is_pickup
                        )
                else:
                    logger.error(f"REST Fallback Error {r.status_code}: {r.text}")
            except Exception as e:
                logger.error(f"Lỗi khi REST ghi điểm danh: {e}")
        else:
            try:
                ref = db.reference(f"attendance/{today_str}/{student_id}{node_suffix}")
                ref.set(record)
                
                # Cập nhật thêm trạng thái vào bảng students
                student_ref = db.reference(f"students/{student_id}")
                update_payload = {"status": display_status, "time": time_str}
                if image_url:
                    update_payload["capturedPhoto"] = image_url
                student_ref.update(update_payload)
                
                # Ghi nhận diện mới nhất vào System/Latest_Recognition
                db.reference("System/Latest_Recognition").set(latest_record)

                logger.info(f"Đã ghi Firebase: {student_id} ({student_name}) | {time_str} | {status}")
                
                # Kích hoạt gửi Email nếu có Parent Email
                if parent_email:
                    email_service.send_attendance_email_async(
                        parent_email=parent_email,
                        student_name=student_name,
                        time_str=time_str,
                        base64_image=image_url,
                        is_late=(status == "late"),
                        is_pickup=is_pickup
                    )

            except Exception as e:
                logger.warning(f"Admin SDK ghi điểm danh thất bại ({e}). Tự động chuyển sang REST...")
                self.use_rest_fallback = True
                self.write_attendance(student_id, image_url)
        return record
