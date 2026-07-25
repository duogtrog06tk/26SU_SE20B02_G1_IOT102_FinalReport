package com.mycompany.webappcamerakidcheck.service;

import com.mycompany.webappcamerakidcheck.model.Account;
import com.mycompany.webappcamerakidcheck.repository.AccountRepository;

import java.util.Map;

public class AccountService {
    private final AccountRepository accountRepository;
    private static volatile boolean useRestFallback = true; // Fallback to REST

    public AccountService() {
        this.accountRepository = new AccountRepository();
    }

    public Map<String, Account> getAllAccounts() {
        Map<String, Account> accounts = accountRepository.getAllAccountsRest();
        ensureDefaultAccounts(accounts);
        return accounts;
    }

    private void ensureDefaultAccounts(Map<String, Account> accounts) {
        boolean hasAdmin = accounts.containsKey("admin");
        boolean hasTeacher = accounts.containsKey("teacher");

        if (!hasAdmin) {
            Account admin = new Account("admin@kidcheck.com", "admin123", "admin", "Hiệu Trưởng KidCheck", null);
            saveAccount("admin", admin);
            accounts.put("admin", admin);
        }
        if (!hasTeacher) {
            Account teacher = new Account("teacher@kidcheck.com", "teacher123", "teacher", "Cô giáo phụ trách", null);
            saveAccount("teacher", teacher);
            accounts.put("teacher", teacher);
        }
    }

    public boolean saveAccount(String key, Account account) {
        boolean saved = false;
        if (useRestFallback) {
            saved = accountRepository.saveAccountRest(key, account);
        } else {
            try {
                accountRepository.saveAccountAdminSdkAsync(key, account);
                saved = true;
            } catch (Exception e) {
                System.err.println("Admin SDK error on save account: " + e.getMessage());
                useRestFallback = true;
                saved = accountRepository.saveAccountRest(key, account);
            }
        }
        return saved;
    }

    public Account getAccountByEmail(String email) {
        if (email == null) return null;
        Map<String, Account> accounts = getAllAccounts();
        String lowerEmail = email.trim().toLowerCase();
        
        for (Account acc : accounts.values()) {
            if (acc.getEmail() != null && acc.getEmail().trim().toLowerCase().equals(lowerEmail)) {
                return acc;
            }
        }
        return null;
    }

    public Account login(String inputAccount, String inputPass) {
        if (inputAccount == null || inputPass == null) return null;
        
        Map<String, Account> accounts = getAllAccounts(); // This also ensures default accounts exist
        String lowerInputAcc = inputAccount.trim().toLowerCase();
        String trimmedPass = inputPass.trim();

        for (Map.Entry<String, Account> entry : accounts.entrySet()) {
            Account acc = entry.getValue();
            if ((lowerInputAcc.equals(entry.getKey().toLowerCase()) || 
                 (acc.getEmail() != null && acc.getEmail().toLowerCase().equals(lowerInputAcc))) &&
                trimmedPass.equals(acc.getPassword())) {
                return acc;
            }
        }
        return null;
    }
}
