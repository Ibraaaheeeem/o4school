import json
import glob
import os

print("Starting search for SchoolRepository.kt...")

transcripts = glob.glob("/home/abuhaneefayn/.gemini/antigravity-ide/brain/*/.system_generated/logs/transcript.jsonl")
print(f"Found {len(transcripts)} transcript files.")

for t_path in transcripts:
    try:
        with open(t_path, "r", encoding="utf-8") as f:
            for line in f:
                if not line.strip():
                    continue
                step = json.loads(line)
                
                # Check for write_to_file calls
                tool_calls = step.get("tool_calls", [])
                for call in tool_calls:
                    if call.get("name") == "write_to_file":
                        args = call.get("args", {})
                        # Sometimes args is a string representing JSON, let's parse it if so
                        if isinstance(args, str):
                            try:
                                args = json.loads(args)
                            except:
                                pass
                        target = args.get("TargetFile", "")
                        if "SchoolRepository.kt" in target:
                            print(f"FOUND write_to_file in {t_path}:")
                            print("CodeContent preview:")
                            print(args.get("CodeContent", "")[:1000])
                            print("-" * 50)
                            
                # Check for view_file responses
                if step.get("type") == "VIEW_FILE" and "SchoolRepository.kt" in step.get("content", ""):
                    content = step.get("content", "")
                    if "Showing lines 1 to" in content or "Total Lines:" in content:
                        print(f"FOUND view_file in {t_path}:")
                        print(content[:1000])
                        print("-" * 50)
    except Exception as e:
        print(f"Error reading {t_path}: {e}")

print("Search completed.")
