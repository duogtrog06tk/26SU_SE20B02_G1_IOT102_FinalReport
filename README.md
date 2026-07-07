# KidCheck-IOT102

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
