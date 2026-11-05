package com.acme.shop;

import java.sql.*;
import java.util.logging.Logger;

/**
 * Sample code for demonstrating the AI Code Review Agent.
 *
 * WARNING: this file contains deliberate defects. It exists so that the review features
 * always have something real to find during a demonstration. Do not copy any of it.
 */
public class LoginService {

    // Deliberate defect: credential hard-coded in source.
    private static final String DB_PASSWORD = "sup3rs3cr3t-admin-2024";
    private static final String DB_URL = "jdbc:mysql://prod-db.acme.internal:3306/shop";

    private Connection connection;

    public LoginService() {
        try {
            connection = DriverManager.getConnection(DB_URL, "root", DB_PASSWORD);
        } catch (SQLException e) {
            // Deliberate defect: exception swallowed, object left half-constructed.
        }
    }

    /**
     * Checks a user's credentials.
     */
    public boolean authenticate(String username, String password) {
        // Deliberate defect: SQL built by concatenation, open to injection.
        String query = "SELECT id, role FROM users WHERE username = '" + username
                + "' AND password = '" + password + "'";
        try {
            Statement statement = connection.createStatement();
            ResultSet results = statement.executeQuery(query);
            // Deliberate defect: statement and result set never closed.
            return results.next();
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Deliberate defect: method name starts with a capital, violating Java convention.
     */
    public String GetUserRole(String username) {
        String role = null;
        try {
            Statement statement = connection.createStatement();
            ResultSet results = statement.executeQuery(
                    "SELECT role FROM users WHERE username = '" + username + "'");
            if (results.next()) role = results.getString("role");
        } catch (SQLException e) {
            // Deliberate defect: silent failure, caller cannot distinguish "no role" from "error".
        }
        // Deliberate defect: dereferenced without a null check by most callers.
        return role.trim();
    }

    /**
     * Deliberate defect: average with no guard against an empty list.
     */
    public int averageSessionLength(int totalMinutes, int sessionCount) {
        return totalMinutes / sessionCount;
    }

    public void logAccess(String username) {
        Logger log = Logger.getLogger("access");
        // Deliberate defect: logs a secret.
        log.info("User " + username + " connected using password " + DB_PASSWORD);
    }
}
