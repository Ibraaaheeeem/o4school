import java.sql.DriverManager

val url = "jdbc:postgresql://localhost:5432/4s_db"
val user = "postgres" 
val pass = "postgres"

DriverManager.getConnection(url, user, pass).use { conn ->
    conn.createStatement().use { stmt ->
        val rs = stmt.executeQuery("SELECT u.id as user_id, u.full_name, u.role, u.is_active as user_active, ur.id as role_id, ur.school_id, ur.role_id as ur_role_id, ur.is_active as role_active FROM users u LEFT JOIN user_school_roles ur ON u.id = ur.user_id WHERE u.phone_number LIKE '%2348133336390%';")
        while (rs.next()) {
            println("User: ${rs.getString("full_name")} (${rs.getString("user_id")})")
            println("  - Global Active: ${rs.getBoolean("user_active")}")
            println("  - Global Role: ${rs.getString("role")}")
            println("  - School Role ID: ${rs.getString("role_id")}")
            println("  - School ID: ${rs.getString("school_id")}")
            println("  - Role Active: ${rs.getBoolean("role_active")}")
        }
    }
}
