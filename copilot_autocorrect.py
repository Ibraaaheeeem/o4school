import os

# Folder containing your code
CODE_FOLDER = "./core/src/main/kotlin/com/haneef/_school/service"
PROMPT_COMMENT = """
# COPILOT TASK: Review this file for improvements, refactor where needed, and suggest fixes.
# Please include:
# - Cleaner code
# - Null safety
# - Security and performance fixes
# - Add missing error handling
"""

def prepend_prompt(file_path):
    with open(file_path, "r+") as f:
        content = f.read()
        f.seek(0, 0)
        f.write(PROMPT_COMMENT.rstrip("\r\n") + "\n\n" + content)

def walk_folder(folder):
    for root, _, files in os.walk(folder):
        for file in files:
            if file.endswith((".py", ".js", ".ts", ".go")):  # adjust extensions
                prepend_prompt(os.path.join(root, file))

if __name__ == "__main__":
    walk_folder(CODE_FOLDER)
    print("Prompt inserted into all files.")
