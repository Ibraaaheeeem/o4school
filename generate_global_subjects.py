
core_subjects = {
    "English Language",
    "General Mathematics",
    "Mathematics",
    "Civic Education",
    "Civic Education (Senior)",
    "Civic Education (Basic)",
    "Biology",
    "Chemistry",
    "Physics",
    "Economics",
    "Basic Science",
    "Social Studies",
    "Basic Technology",
    "Computer & IT",
    "Information Technology (IT)",
    "Agricultural Science",
    "Agriculture",
    "Basic Science and Technology"
}

def get_sql_values(subject_list, min_grade, max_grade):
    values = []
    for subject in subject_list:
        escaped_subject = subject.replace("'", "''")
        is_core = "TRUE" if subject in core_subjects else "FALSE"
        values.append(f"('{escaped_subject}', {min_grade}, {max_grade}, {is_core}, NOW(), NOW())")
    return values

print("-- Create table global_subjects if not exists")
print("CREATE TABLE IF NOT EXISTS global_subjects (")
print("    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),")
print("    name VARCHAR(255) NOT NULL UNIQUE,")
print("    code VARCHAR(255) UNIQUE,")
print("    min_grade_level INTEGER NOT NULL DEFAULT 1,")
print("    max_grade_level INTEGER NOT NULL DEFAULT 12,")
print("    category VARCHAR(255),")
print("    is_core BOOLEAN NOT NULL DEFAULT FALSE,")
print("    is_active BOOLEAN NOT NULL DEFAULT TRUE,")
print("    created_at TIMESTAMP NOT NULL DEFAULT NOW(),")
print("    updated_at TIMESTAMP NOT NULL DEFAULT NOW()")
print(");")
print("")

subjects = [
    "Upholstery",
    "Visual Art",
    "Welding And Fabrication",
    "Woodwork",
    "Yoruba Language",
    "Leather Goods",
    "Literature-in-English",
    "Machine Woodworking",
    "Marketing",
    "Metal Work",
    "Mining",
    "Music",
    "Office Practice",
    "Painting And Decoration",
    "Photography",
    "Physical Education",
    "Physics",
    "Plumbing And Pipe Fitting",
    "Printing Craft Practice",
    "Radio Television and Electrical Work",
    "Radio Television And Repairs",
    "Salesmanship",
    "Store Keeping",
    "Store Management",
    "Technical Drawings",
    "Textile trade",
    "Tie And Dye Craft",
    "Tourism",
    "Cosmetology",
    "Data Processing",
    "Dyeing And Bleaching",
    "Economics",
    "Electrical Installation And Maintenance Work",
    "English Language",
    "Financial Accounting",
    "Fisheries",
    "Foods & Nutrition",
    "French Language",
    "Furniture Making",
    "Further Mathematics",
    "Garment Making",
    "General Mathematics",
    "Geography",
    "Government",
    "GSM Maintenance And Repairs",
    "Health Education",
    "History",
    "Home Management",
    "Igbo Language",
    "Insurance",
    "Islamic Studies",
    "Keyboarding",
    "Agricultural Science",
    "Air Conditioning And Refrigeration",
    "Animal Husbandary",
    "Arabic",
    "Auto body repair And Spray painting",
    "Auto Electrical Works",
    "Auto Mechanical Works",
    "Auto Mechanics",
    "Automobile Parts Merchandising",
    "Autopart Merchandizing",
    "Basic Electricity",
    "Basic Electronics",
    "Biology",
    "Block laying, Brick laying & Concrete Works",
    "Book Keeping",
    "Building Construction",
    "Carpentary And Joinery",
    "Catering and Craft Practice",
    "Chemistry",
    "Christian Religious Studies",
    "Civic Education (Senior)",
    "Clothing & Textiles",
    "Commerce",
    "Computer & IT"
]

print("-- Insert subjects")
print("INSERT INTO global_subjects (name, min_grade_level, max_grade_level, is_core, created_at, updated_at) VALUES")
print(",\n".join(get_sql_values(subjects, 10, 12)))
print("ON CONFLICT (name) DO UPDATE SET min_grade_level = LEAST(global_subjects.min_grade_level, EXCLUDED.min_grade_level), max_grade_level = GREATEST(global_subjects.max_grade_level, EXCLUDED.max_grade_level), is_core = EXCLUDED.is_core;")
print("")


jss_subjects = [
    "Agriculture",
    "Arabic",
    "Basic Science",
    "Basic Technology",
    "Business Studies",
    "Christian Religious Studies",
    "Civic Education (Basic)",
    "Cultural And Creative Arts",
    "English Language",
    "Entrepreneurship",
    "French Language",
    "General Mathematics",
    "Hausa Language",
    "Home Economics",
    "Igbo Language",
    "Information Technology (IT)",
    "Islamic Studies",
    "Physical & Health Education",
    "Security Education",
    "Social Studies",
    "Yoruba Language"
]

print("-- Insert JSS subjects (Grades 7-9)")
print("INSERT INTO global_subjects (name, min_grade_level, max_grade_level, is_core, created_at, updated_at) VALUES")
print(",\n".join(get_sql_values(jss_subjects, 7, 9)))
print("ON CONFLICT (name) DO UPDATE SET min_grade_level = LEAST(global_subjects.min_grade_level, EXCLUDED.min_grade_level), max_grade_level = GREATEST(global_subjects.max_grade_level, EXCLUDED.max_grade_level), is_core = EXCLUDED.is_core;")
print("")


primary_lower_subjects = [
    "English Language",
    "Arabic",
    "Basic Science",
    "Basic Technology",
    "Christian Religious Studies",
    "Civic Education (Basic)",
    "Cultural And Creative Arts",
    "General Mathematics",
    "Hausa Language",
    "Igbo Language",
    "Information Technology (IT)",
    "Islamic Studies",
    "Physical & Health Education",
    "Security Education",
    "Social Studies",
    "Yoruba Language"
]
primary_lower_subjects = sorted(list(set(primary_lower_subjects)))

print("-- Insert Primary Lower subjects (Grades 1-3)")
print("INSERT INTO global_subjects (name, min_grade_level, max_grade_level, is_core, created_at, updated_at) VALUES")
print(",\n".join(get_sql_values(primary_lower_subjects, 1, 3)))
print("ON CONFLICT (name) DO UPDATE SET min_grade_level = LEAST(global_subjects.min_grade_level, EXCLUDED.min_grade_level), max_grade_level = GREATEST(global_subjects.max_grade_level, EXCLUDED.max_grade_level), is_core = EXCLUDED.is_core;")
print("")


primary_upper_subjects = [
    "Yoruba Language",
    "Agriculture",
    "Arabic",
    "Basic Science",
    "Basic Technology",
    "Christian Religious Studies",
    "Civic Education (Basic)",
    "Cultural And Creative Arts",
    "English Language",
    "Entrepreneurship",
    "French Language",
    "General Mathematics",
    "Hausa Language",
    "Home Economics",
    "Igbo Language",
    "Information Technology (IT)",
    "Islamic Studies",
    "Physical & Health Education",
    "Security Education",
    "Social Studies",
    "Yoruba Language"
]
primary_upper_subjects = sorted(list(set(primary_upper_subjects)))

print("-- Insert Primary Upper subjects (Grades 4-6)")
print("INSERT INTO global_subjects (name, min_grade_level, max_grade_level, is_core, created_at, updated_at) VALUES")
print(",\n".join(get_sql_values(primary_upper_subjects, 4, 6)))
print("ON CONFLICT (name) DO UPDATE SET min_grade_level = LEAST(global_subjects.min_grade_level, EXCLUDED.min_grade_level), max_grade_level = GREATEST(global_subjects.max_grade_level, EXCLUDED.max_grade_level), is_core = EXCLUDED.is_core;")
print("")


preschool_subjects = [
    "Health Habits",
    "Handwriting",
    "Literacy",
    "Numeracy",
    "Rest and Relaxation",
    "Pre-Science",
    "Social Habits"
]
preschool_subjects = sorted(list(set(preschool_subjects)))

print("-- Insert Pre-School subjects (Grades -3 to -2)")
print("INSERT INTO global_subjects (name, min_grade_level, max_grade_level, is_core, created_at, updated_at) VALUES")
print(",\n".join(get_sql_values(preschool_subjects, -3, -2)))
print("ON CONFLICT (name) DO UPDATE SET min_grade_level = LEAST(global_subjects.min_grade_level, EXCLUDED.min_grade_level), max_grade_level = GREATEST(global_subjects.max_grade_level, EXCLUDED.max_grade_level), is_core = EXCLUDED.is_core;")
print("")


nursery_subjects = [
    "Literacy (Letter Work)",
    "Literacy (Language Domain)",
    "Numeracy",
    "Basic Science and Technology",
    "Health Habits",
    "Social Habits",
    "Civic Education",
    "Physical and Health Education",
    "Creativity",
    "Personal Development",
    "Songs and Rhymes",
    "Handwriting"
]
nursery_subjects = sorted(list(set(nursery_subjects)))

print("-- Insert Nursery subjects (Grades -1 to 0)")
print("INSERT INTO global_subjects (name, min_grade_level, max_grade_level, is_core, created_at, updated_at) VALUES")
print(",\n".join(get_sql_values(nursery_subjects, -1, 0)))
print("ON CONFLICT (name) DO UPDATE SET min_grade_level = LEAST(global_subjects.min_grade_level, EXCLUDED.min_grade_level), max_grade_level = GREATEST(global_subjects.max_grade_level, EXCLUDED.max_grade_level), is_core = EXCLUDED.is_core;")

