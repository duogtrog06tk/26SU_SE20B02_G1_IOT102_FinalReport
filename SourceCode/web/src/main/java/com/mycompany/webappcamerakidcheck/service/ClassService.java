package com.mycompany.webappcamerakidcheck.service;

import com.mycompany.webappcamerakidcheck.model.SchoolClass;
import com.mycompany.webappcamerakidcheck.repository.ClassRepository;

import java.util.List;

public class ClassService {
    private final ClassRepository classRepository;
    private static volatile boolean useRestFallback = true; // Fallback to REST

    public ClassService() {
        this.classRepository = new ClassRepository();
    }

    public List<SchoolClass> getAllClasses() {
        List<SchoolClass> classes = classRepository.getAllClassesRest();
        ensureDefaultClasses(classes);
        return classes;
    }

    private void ensureDefaultClasses(List<SchoolClass> classes) {
        if (classes.isEmpty()) {
            SchoolClass c1 = new SchoolClass("Mầm 1", "Trẻ từ 3 đến 4 tuổi", "07:30", "08:30", "16:30");
            SchoolClass c2 = new SchoolClass("Chồi 2", "Trẻ từ 4 đến 5 tuổi", "07:30", "08:30", "16:30");
            SchoolClass c3 = new SchoolClass("Lá 3", "Trẻ từ 5 đến 6 tuổi", "07:30", "08:30", "16:30");
            
            saveClass(c1);
            saveClass(c2);
            saveClass(c3);
            
            classes.add(c1);
            classes.add(c2);
            classes.add(c3);
        }
    }

    public boolean addClass(SchoolClass schoolClass) {
        List<SchoolClass> currentClasses = classRepository.getAllClassesRest();
        boolean exists = currentClasses.stream().anyMatch(c -> c.getName().equalsIgnoreCase(schoolClass.getName()));
        if (exists) {
            throw new IllegalArgumentException("Lớp học đã tồn tại!");
        }
        return saveClass(schoolClass);
    }

    public boolean updateClass(SchoolClass schoolClass) {
        return saveClass(schoolClass);
    }

    private boolean saveClass(SchoolClass schoolClass) {
        boolean saved = false;
        if (useRestFallback) {
            saved = classRepository.saveClassRest(schoolClass);
        } else {
            try {
                classRepository.saveClassAdminSdkAsync(schoolClass);
                saved = true;
            } catch (Exception e) {
                System.err.println("Admin SDK error on save class: " + e.getMessage());
                useRestFallback = true;
                saved = classRepository.saveClassRest(schoolClass);
            }
        }
        return saved;
    }

    public boolean deleteClass(String className) {
        boolean deleted = false;
        if (useRestFallback) {
            deleted = classRepository.deleteClassRest(className);
        } else {
            try {
                classRepository.deleteClassAdminSdkAsync(className);
                deleted = true;
            } catch (Exception e) {
                System.err.println("Admin SDK error on delete class: " + e.getMessage());
                useRestFallback = true;
                deleted = classRepository.deleteClassRest(className);
            }
        }
        return deleted;
    }
}
