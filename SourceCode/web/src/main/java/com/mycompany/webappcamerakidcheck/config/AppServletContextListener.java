package com.mycompany.webappcamerakidcheck.config;

import com.mycompany.webappcamerakidcheck.service.StudentService;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@WebListener
public class AppServletContextListener implements ServletContextListener {

    private ScheduledExecutorService scheduler;
    private final StudentService studentService = new StudentService();

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        System.out.println("Web application starting... Initializing scheduler.");
        scheduler = Executors.newSingleThreadScheduledExecutor();

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay();
        long initialDelay = Duration.between(now, nextMidnight).getSeconds();
        
        System.out.println("Scheduled reset attendance task in " + initialDelay + " seconds.");

        scheduler.scheduleAtFixedRate(() -> {
            try {
                System.out.println("Bắt đầu reset điểm danh cho ngày mới...");
                studentService.resetAllAttendance();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, initialDelay, 24 * 60 * 60, TimeUnit.SECONDS);
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        System.out.println("Web application stopping... Shutting down scheduler.");
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }
}
