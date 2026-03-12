import os
import sys
import json
import uuid
import re
from datetime import datetime
from pathlib import Path

try:
    import psycopg2
    import requests
except ImportError:
    print("Please install requirements: pip install psycopg2-binary requests")
    sys.exit(1)

# Default configuration
DB_HOST = os.environ.get("DB_HOST", "localhost")
DB_PORT = os.environ.get("DB_PORT", "5432")
DB_NAME = os.environ.get("DB_NAME", "myschool")
DB_USER = os.environ.get("DB_USER", "abuhaneefayn")
DB_PASS = os.environ.get("DB_PASSWORD", "pass")

API_VERSION = os.environ.get("WHATSAPP_API_VERSION", "v21.0")

def load_properties(filepath):
    """Read a Java .properties file into a dict."""
    props = {}
    with open(filepath, 'r') as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith('#'):
                continue
            if '=' in line:
                key, value = line.split('=', 1)
                props[key.strip()] = value.strip()
    return props

def load_whatsapp_config():
    """Load WhatsApp config from env vars first, then application-dev.properties."""
    script_dir = Path(__file__).resolve().parent
    props_path = script_dir / "webapp" / "src" / "main" / "resources" / "application-dev.properties"
    
    props = {}
    if props_path.exists():
        props = load_properties(props_path)
    
    # Environment variables take precedence (same as Spring Boot)
    access_token = os.environ.get("WHATSAPP_ACCESS_TOKEN") or props.get("whatsapp.meta.access-token", "")
    business_account_id = os.environ.get("WHATSAPP_BUSINESS_ACCOUNT_ID") or props.get("whatsapp.meta.business-account-id", "")
    api_version = os.environ.get("WHATSAPP_API_VERSION") or props.get("whatsapp.meta.api-version", API_VERSION)
    
    if not access_token or not business_account_id:
        print("WhatsApp credentials not found in env vars or application-dev.properties")
        return None
    
    source = "environment variable" if os.environ.get("WHATSAPP_ACCESS_TOKEN") else "application-dev.properties"
    print(f"  Token source: {source}")
    
    return {
        "access_token": access_token,
        "business_account_id": business_account_id,
        "api_version": api_version
    }

def get_db_connection():
    try:
        conn = psycopg2.connect(
            host=DB_HOST,
            port=DB_PORT,
            dbname=DB_NAME,
            user=DB_USER,
            password=DB_PASS
        )
        return conn
    except Exception as e:
        print(f"Failed to connect to database: {e}")
        sys.exit(1)

def extract_parameter_count(components):
    parameter_count = 0
    unique_placeholders = set()
    
    for comp in components:
        text = comp.get("text", "")
        # Regex to find {{param}}
        matches = re.findall(r'\{\{([a-zA-Z0-9_]+)\}\}', text)
        for match in matches:
            unique_placeholders.add(match)
            
            # Find max digit in the placeholder (e.g., {{1}}, {{2}})
            digits = ''.join([c for c in match if c.isdigit()])
            if digits:
                max_param = int(digits)
                if max_param > parameter_count:
                    parameter_count = max_param
                    
    # If no numbered placeholders were found but we have unique named ones, use the set size
    if parameter_count == 0 and unique_placeholders:
        parameter_count = len(unique_placeholders)
        
    return parameter_count

def fetch_templates(access_token, business_account_id, api_version=API_VERSION):
    url = f"https://graph.facebook.com/{api_version}/{business_account_id}/message_templates"
    headers = {
        "Authorization": f"Bearer {access_token}",
        "Content-Type": "application/json"
    }
    
    print(f"Fetching templates from Meta API...")
    try:
        response = requests.get(url, headers=headers)
        response.raise_for_status()
        data = response.json()
        return data.get("data", [])
    except Exception as e:
        print(f"Error fetching templates: {e}")
        if 'response' in locals() and response.text:
            print(f"Response details: {response.text}")
        sys.exit(1)

def update_database(templates, school_id):
    conn = get_db_connection()
    cursor = conn.cursor()
    
    success_count = 0
    failed_count = 0
    
    for meta_template in templates:
        meta_id = meta_template.get("id")
        name = meta_template.get("name")
        language = meta_template.get("language", "en_US")
        category = meta_template.get("category", "UTILITY")
        status = meta_template.get("status", "PENDING")
        
        components = meta_template.get("components", [])
        components_json = json.dumps(components)
        
        parameter_count = extract_parameter_count(components)
        now = datetime.now()
        
        try:
            # Check if template exists
            cursor.execute("SELECT id FROM whatsapp_templates WHERE template_id = %s", (meta_id,))
            existing = cursor.fetchone()
            
            if existing:
                # Update existing
                query = """
                    UPDATE whatsapp_templates 
                    SET template_name = %s, language = %s, category = %s, status = %s, 
                        parameter_count = %s, components_json = %s, last_synced_at = %s, updated_at = %s
                    WHERE template_id = %s
                """
                cursor.execute(query, (
                    name, language, category, status, parameter_count, components_json, now, now, meta_id
                ))
                print(f"Updated template: {name} ({meta_id})")
            else:
                # Insert new
                new_id = str(uuid.uuid4())
                query = """
                    INSERT INTO whatsapp_templates (
                        id, created_at, updated_at, is_active, school_id, 
                        template_id, template_name, language, category, 
                        parameter_count, components_json, status, last_synced_at, 
                        is_for_broadcast, target_role
                    ) VALUES (
                        %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s
                    )
                """
                cursor.execute(query, (
                    new_id, now, now, True, school_id, meta_id, name, 
                    language, category, parameter_count, components_json, 
                    status, now, False, "GENERAL"
                ))
                print(f"Inserted new template: {name} ({meta_id})")
                
            conn.commit()
            success_count += 1
            
        except Exception as e:
            conn.rollback()
            print(f"Error processing template {name} ({meta_id}): {e}")
            failed_count += 1
            
    cursor.close()
    conn.close()
    
    print("\n--- Summary ---")
    print(f"Total templates fetched: {len(templates)}")
    print(f"Successfully processed: {success_count}")
    print(f"Failed to process: {failed_count}")

def main():
    print("=== WhatsApp Template Sync Script ===")
    
    # Load credentials from application-dev.properties
    config = load_whatsapp_config()
    if not config:
        print("Could not load WhatsApp config. Exiting.")
        sys.exit(1)
    
    access_token = config["access_token"]
    business_account_id = config["business_account_id"]
    api_version = config["api_version"]
    
    print(f"Loaded config from application-dev.properties:")
    print(f"  Business Account ID: {business_account_id}")
    print(f"  API Version: {api_version}")
    print(f"  Token length: {len(access_token)} chars")
    
    school_id_str = input("Enter School ID (UUID for the templates): ").strip()
    if not school_id_str:
        print("School ID is required")
        sys.exit(1)
        
    # Validate UUID
    try:
        school_id = str(uuid.UUID(school_id_str))
    except ValueError:
        print("Invalid School ID format. Must be a valid UUID.")
        sys.exit(1)
        
    # Fetch and sync
    templates = fetch_templates(access_token, business_account_id, api_version)
    if templates:
        update_database(templates, school_id)
    else:
        print("No templates found.")

if __name__ == "__main__":
    main()
