import json
import glob
import re
import os

transcripts = glob.glob("/home/abuhaneefayn/.gemini/antigravity-ide/brain/*/.system_generated/logs/transcript.jsonl")
print(f"Scanning {len(transcripts)} transcripts...")

line_re = re.compile(r"^(\d+):\s?(.*)$")

for filename in ["SchoolRepository.kt", "SchoolApiService.kt"]:
    print(f"\n--- Reconstructing {filename} ---")
    views = []
    
    for t_path in transcripts:
        conv_id = t_path.split("/")[-4]
        try:
            with open(t_path, "r", encoding="utf-8") as f:
                for line in f:
                    if not line.strip():
                        continue
                    step = json.loads(line)
                    
                    # Check write_to_file
                    for call in step.get("tool_calls", []):
                        if call.get("name") == "write_to_file":
                            args = call.get("args", {})
                            if isinstance(args, str):
                                try:
                                    args = json.loads(args)
                                except:
                                    pass
                            target = args.get("TargetFile", "")
                            if filename in target:
                                code = args.get("CodeContent", "")
                                if code:
                                    code_lines = code.splitlines()
                                    views.append({
                                        "time": step.get("created_at", ""),
                                        "path": t_path,
                                        "conv": conv_id,
                                        "type": "write",
                                        "start": 1,
                                        "end": len(code_lines),
                                        "lines": {i+1: l for i, l in enumerate(code_lines)}
                                    })
                    
                    # Check view_file responses
                    if step.get("type") == "VIEW_FILE" and filename in step.get("content", ""):
                        content = step.get("content", "")
                        show_match = re.search(r"Showing lines (\d+) to (\d+)", content)
                        if show_match:
                            start_l = int(show_match.group(1))
                            end_l = int(show_match.group(2))
                            lines_dict = {}
                            for cline in content.splitlines():
                                m = line_re.match(cline.strip())
                                if m:
                                    lnum = int(m.group(1))
                                    lcontent = m.group(2)
                                    lines_dict[lnum] = lcontent
                            views.append({
                                "time": step.get("created_at", ""),
                                "path": t_path,
                                "conv": conv_id,
                                "type": "view",
                                "start": start_l,
                                "end": end_l,
                                "lines": lines_dict
                            })
        except Exception as e:
            pass
            
    print(f"Collected {len(views)} views/writes of {filename}")
    
    # Let's group by conv and merge lines for each conversation
    convs_merged = {}
    for v in views:
        c = v["conv"]
        if c not in convs_merged:
            convs_merged[c] = {}
        convs_merged[c].update(v["lines"])
        
    # Find the conversation that has the most lines merged (the most complete reconstruction)
    best_conv = None
    best_lines = {}
    best_count = 0
    
    for c, lines in convs_merged.items():
        # count how many lines we have that are not empty and don't look like missing line markers
        valid_lines = {k: v for k, v in lines.items() if v and not v.startswith("// MISSING")}
        print(f"  Conv: {c} | Total lines: {max(lines.keys()) if lines else 0} | Valid lines: {len(valid_lines)}")
        if len(valid_lines) > best_count:
            best_count = len(valid_lines)
            best_conv = c
            best_lines = lines
            
    if best_lines:
        max_l = max(best_lines.keys())
        print(f"-> Restoring from Conv {best_conv} with {best_count} valid lines up to line {max_l}")
        
        # Write to final destination
        if filename == "SchoolRepository.kt":
            dest = "/home/abuhaneefayn/Desktop/4school/4school/app/src/main/java/com/haneef/school/data/repository/SchoolRepository.kt"
        else:
            dest = "/home/abuhaneefayn/Desktop/4school/4school/app/src/main/java/com/haneef/school/data/api/SchoolApiService.kt"
            
        with open(dest, "w", encoding="utf-8") as f:
            for i in range(1, max_l + 1):
                f.write(best_lines.get(i, f"// MISSING LINE {i}") + "\n")
        print(f"Successfully wrote {dest}")
    else:
        print("-> No reconstruction possible")
