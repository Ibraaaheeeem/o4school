use school_backend::db::Database;
use sqlx::Row;

mod common;

#[tokio::test]
async fn inspect_roles_and_constraints() {
    let database_url = std::env::var("DATABASE_URL").unwrap_or_else(|_| "postgres://postgres:password@localhost:5432/myschool".to_string());
    let db = Database::new(&database_url).await.expect("Failed to connect to DB");

    // List roles
    let rows = sqlx::query("SELECT id, name FROM roles ORDER BY name")
        .fetch_all(db.pool())
        .await
        .expect("Failed to query roles");

    println!("Roles:");
    for r in rows {
        let id: uuid::Uuid = r.try_get("id").expect("role id");
        let name: String = r.try_get("name").expect("role name");
        println!("- {} | {}", id, name);
    }

    // Check specific constraint
    let fk_name = "fkpxsug0huupvf2p9pi6vocjmt4";
    let c_row = sqlx::query(
        "SELECT conname, conrelid::regclass::text AS table_from, pg_get_constraintdef(c.oid) AS definition FROM pg_constraint c WHERE conname = $1",
    )
    .bind(fk_name)
    .fetch_optional(db.pool())
    .await
    .expect("Failed to query constraint");

    if let Some(row) = c_row {
        use sqlx::Row;
        let conname: String = row.try_get("conname").unwrap_or_default();
        let table_from: String = row.try_get("table_from").unwrap_or_default();
        let definition: String = row.try_get("definition").unwrap_or_default();
        println!("Constraint lookup for {}: {} | {} | {}", fk_name, conname, table_from, definition);
    } else {
        println!("Constraint {} not found", fk_name);
    }

    // List constraints on user_school_roles
    let cs = sqlx::query(
        "SELECT conname, pg_get_constraintdef(c.oid) AS definition FROM pg_constraint c WHERE conrelid = 'user_school_roles'::regclass"
    )
    .fetch_all(db.pool())
    .await
    .expect("Failed to query constraints");

    println!("Constraints on user_school_roles:");
    for cc in cs {
        let conname: String = cc.try_get("conname").expect("constraint name");
        let definition: String = cc.try_get("definition").unwrap_or_default();
        println!("- {} : {}", conname, definition);
    }

    // Role usage counts
    let counts = sqlx::query("SELECT role_id, COUNT(*) as cnt FROM user_school_roles GROUP BY role_id ORDER BY cnt DESC")
        .fetch_all(db.pool())
        .await
        .expect("Failed to query role usage");

    println!("Role usage in user_school_roles:");
    for rc in counts {
        let role_id: uuid::Uuid = rc.try_get("role_id").expect("role id");
        let cnt: i64 = rc.try_get("cnt").expect("count");
        println!("- {} => {}", role_id, cnt);
    }
}
