package com.mycompany.webappcamerakidcheck.service;

import com.mycompany.webappcamerakidcheck.model.Student;
import com.mycompany.webappcamerakidcheck.repository.StudentRepository;
import com.mycompany.webappcamerakidcheck.service.AccountService;

import java.util.List;
import java.util.Optional;

public class StudentService {
    private final StudentRepository studentRepository;
    private final AccountService accountService;
    private static volatile boolean useRestFallback = true; // Still keeping fallback flag if needed

    public StudentService() {
        this.studentRepository = new StudentRepository();
        this.accountService = new AccountService();
    }

    public List<Student> getAllStudents() {
        return studentRepository.getAllStudentsRest();
    }

    public boolean addStudent(Student student) {
        // Validation check
        List<Student> currentStudents = studentRepository.getAllStudentsRest();
        boolean exists = currentStudents.stream().anyMatch(s -> s.getId().equalsIgnoreCase(student.getId()));
        if (exists) {
            throw new IllegalArgumentException("Mã học sinh đã tồn tại!");
        }

        boolean saved = false;
        if (useRestFallback) {
            saved = studentRepository.saveStudentRest(student);
        } else {
            try {
                studentRepository.saveStudentAdminSdkAsync(student);
                saved = true;
            } catch (Exception e) {
                System.err.println("Admin SDK error on save: " + e.getMessage());
                useRestFallback = true;
                saved = studentRepository.saveStudentRest(student);
            }
        }

        if (saved) {
            createOrUpdateParentAccount(student);
        }
        return saved;
    }

    public boolean updateStudent(Student student) {
        boolean updated = false;
        if (useRestFallback) {
            updated = studentRepository.saveStudentRest(student);
        } else {
            try {
                studentRepository.saveStudentAdminSdkAsync(student);
                updated = true;
            } catch (Exception e) {
                System.err.println("Admin SDK error on update: " + e.getMessage());
                useRestFallback = true;
                updated = studentRepository.saveStudentRest(student);
            }
        }

        if (updated) {
            createOrUpdateParentAccount(student);
        }
        return updated;
    }

    public boolean deleteStudent(String studentId) {
        boolean deleted = false;
        if (useRestFallback) {
            deleted = studentRepository.deleteStudentRest(studentId);
        } else {
            try {
                studentRepository.deleteStudentAdminSdkAsync(studentId);
                deleted = true;
            } catch (Exception e) {
                System.err.println("Admin SDK error on delete: " + e.getMessage());
                useRestFallback = true;
                deleted = studentRepository.deleteStudentRest(studentId);
            }
        }

        if (deleted) {
            studentRepository.deleteAttendanceLogsRest(studentId);
        }
        return deleted;
    }

    public void resetAllAttendance() {
        List<Student> students = studentRepository.getAllStudentsRest();
        for (Student s : students) {
            s.setStatus("Chưa điểm danh");
            s.setTime("");
            s.setPickupStatus("Chưa đến");
            
            if (useRestFallback) {
                studentRepository.saveStudentRest(s);
            } else {
                try {
                    studentRepository.saveStudentAdminSdkAsync(s);
                } catch (Exception e) {
                    studentRepository.saveStudentRest(s);
                }
            }
        }
    }

    private void createOrUpdateParentAccount(Student student) {
        if (student.getParentEmail() != null && !student.getParentEmail().trim().isEmpty()) {
            String email = student.getParentEmail().trim();
            String parentName = student.getParentName() != null ? student.getParentName() : "Phụ huynh bé " + student.getName();
            
            com.mycompany.webappcamerakidcheck.model.Account existingAcc = accountService.getAccountByEmail(email);
            String newLinkedId = student.getId();
            
            if (existingAcc != null && "parent".equals(existingAcc.getRole())) {
                String currentLinked = existingAcc.getLinkedStudentId();
                if (currentLinked == null || currentLinked.trim().isEmpty()) {
                    currentLinked = newLinkedId;
                } else {
                    java.util.List<String> ids = new java.util.ArrayList<>(java.util.Arrays.asList(currentLinked.split(",")));
                    if (!ids.contains(newLinkedId)) {
                        ids.add(newLinkedId);
                        currentLinked = String.join(",", ids);
                    }
                }
                existingAcc.setLinkedStudentId(currentLinked);
                existingAcc.setName(parentName);
                String key = email.replaceAll("[^a-zA-Z0-9]", "_");
                accountService.saveAccount(key, existingAcc);
            } else {
                com.mycompany.webappcamerakidcheck.model.Account parentAcc = 
                    new com.mycompany.webappcamerakidcheck.model.Account(email, "123456", "parent", parentName, newLinkedId);
                String key = email.replaceAll("[^a-zA-Z0-9]", "_");
                accountService.saveAccount(key, parentAcc);
            }
        }
    }
}
