import json
import glob
import re

transcripts = glob.glob("/home/abuhaneefayn/.gemini/antigravity-ide/brain/*/.system_generated/logs/transcript.jsonl")
print(f"Scanning {len(transcripts)} transcripts for SchoolApiService.kt...")

views = []
line_re = re.compile(r"^(\d+):\s?(.*)$")

for t_path in transcripts:
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
                        if "SchoolApiService.kt" in target:
                            code = args.get("CodeContent", "")
                            if code:
                                code_lines = code.splitlines()
                                views.append({
                                    "time": step.get("created_at", ""),
                                    "path": t_path,
                                    "type": "write",
                                    "start": 1,
                                    "end": len(code_lines),
                                    "lines": {i+1: l for i, l in enumerate(code_lines)}
                                })
                
                # Check view_file responses
                if step.get("type") == "VIEW_FILE" and "SchoolApiService.kt" in step.get("content", ""):
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
                            "type": "view",
                            "start": start_l,
                            "end": end_l,
                            "lines": lines_dict
                        })
    except Exception as e:
        pass

print(f"Collected {len(views)} views/writes of SchoolApiService.kt")
views.sort(key=lambda x: x["time"])

for idx, v in enumerate(views):
    conv_id = v["path"].split("/")[-4]
    print(f"[{idx}] Time: {v['time']} | Conv: {conv_id} | Type: {v['type']} | Range: {v['start']}-{v['end']} | Lines count: {len(v['lines'])}")

# Merge lines for each unique conversation
convs = []
for v in views:
    c = v["path"].split("/")[-4]
    if c not in convs:
        convs.append(c)

for c in convs:
    c_views = [v for v in views if c in v["path"]]
    merged_lines = {}
    for v in c_views:
        merged_lines.update(v["lines"])
    max_l = max(merged_lines.keys()) if merged_lines else 0
    print(f"Conv: {c} | Merged {len(merged_lines)} lines out of {max_l} total lines.")
    
    # Write reconstruction to temp file
    recon_path = f"/home/abuhaneefayn/Desktop/4school/reconstructed_api_{c}.kt"
    with open(recon_path, "w", encoding="utf-8") as f:
        for i in range(1, max_l + 1):
            f.write(merged_lines.get(i, f"// MISSING LINE {i}") + "\n")
    print(f"Reconstruction written to {recon_path}")
