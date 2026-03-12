import java.sql.DriverManager
import java.util.UUID

val url = "jdbc:postgresql://localhost:5432/4s_db"
val user = "postgres" 
val pass = "postgres"

val phone = "2348133336390"

DriverManager.getConnection(url, user, pass).use { conn ->
    conn.createStatement().use { stmt ->
        println("--- DIAGNOSTIC FOR PHONE: $phone ---")
        
        // Find User
        val userRs = stmt.executeQuery("SELECT id, full_name, email FROM users WHERE phone_number LIKE '%$phone%'")
        val userIds = mutableListOf<UUID>()
        while (userRs.next()) {
            val uid = UUID.fromString(userRs.getString("id"))
            userIds.add(uid)
            println("User: ${userRs.getString("full_name")} ($uid) - ${userRs.getString("email")}")
        }
        
        for (uid in userIds) {
            println("\nChecking schools for User $uid:")
            // Find Parents
            val parentRs = stmt.executeQuery("SELECT p.id as parent_id, p.school_id, s.name as school_name FROM parents p JOIN schools s ON p.school_id = s.id WHERE p.user_id = '$uid'")
            while (parentRs.next()) {
                val pid = UUID.fromString(parentRs.getString("parent_id"))
                val sid = UUID.fromString(parentRs.getString("school_id"))
                println("  - School: ${parentRs.getString("school_name")} ($sid)")
                println("    - Parent ID: $pid")
                
                // Find Students
                val studentRs = conn.createStatement().executeQuery("""
                    SELECT s.id as student_id, u.full_name as student_name 
                    FROM parent_student_relationships ps 
                    JOIN students s ON ps.student_id = s.id 
                    JOIN users u ON s.user_id = u.id 
                    WHERE ps.parent_id = '$pid'
                """)
                val studentIds = mutableListOf<UUID>()
                while (studentRs.next()) {
                    val stid = UUID.fromString(studentRs.getString("student_id"))
                    studentIds.add(stid)
                    println("      - Student: ${studentRs.getString("student_name")} ($stid)")
                }
                
                if (studentIds.isNotEmpty()) {
                    val stIdsStr = studentIds.joinToString("','", "'", "'")
                    // Sum Invoices
                    val invRs = conn.createStatement().executeQuery("""
                        SELECT status, SUM(total_amount) as total 
                        FROM invoices 
                        WHERE student_id IN ($stIdsStr) AND is_active = true 
                        GROUP BY status
                    """)
                    println("      - Invoices (Active):")
                    while (invRs.next()) {
                        println("        - ${invRs.getString("status")}: ${invRs.getLong("total")} kobo")
                    }
                }
                
                // Sum Settlements
                val setRs = conn.createStatement().executeQuery("""
                    SELECT status, SUM(amount) as total 
                    FROM settlements 
                    WHERE (paystack_wallet_id IN (SELECT id FROM paystack_parent_wallets WHERE parent_id = '$pid')
                       OR squad_wallet_id IN (SELECT id FROM squad_parent_wallets WHERE parent_id = '$pid')
                       OR (payer_email = (SELECT email FROM users WHERE id = '$uid') AND school_id = '$sid'))
                    GROUP BY status
                """)
                println("      - Settlements:")
                while (setRs.next()) {
                    println("        - ${setRs.getString("status")}: ${setRs.getBigDecimal("total")} Naira")
                }
            }
        }
    }
}
