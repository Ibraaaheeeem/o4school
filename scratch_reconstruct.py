import json
import glob
import re

transcripts = glob.glob("/home/abuhaneefayn/.gemini/antigravity-ide/brain/*/.system_generated/logs/transcript.jsonl")
print(f"Scanning {len(transcripts)} transcripts...")

# We will collect segments: (timestamp, transcript_path, start_line, end_line, lines_dict)
views = []

# Regex to find line patterns like "123: original_line_content"
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
                        if "SchoolRepository.kt" in target:
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
                if step.get("type") == "VIEW_FILE" and "SchoolRepository.kt" in step.get("content", ""):
                    content = step.get("content", "")
                    # Extract showing range
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

print(f"Collected {len(views)} views/writes of SchoolRepository.kt")

# Group by conversation directory to look at chronological state
# Let's find the latest state of the file
# We sort views by time
views.sort(key=lambda x: x["time"])

# Let's print out what ranges we have for each conversation
for idx, v in enumerate(views):
    conv_id = v["path"].split("/")[-4]
    print(f"[{idx}] Time: {v['time']} | Conv: {conv_id} | Type: {v['type']} | Range: {v['start']}-{v['end']} | Lines count: {len(v['lines'])}")

# Let's try to reconstruct the most recent version of the file (before it was emptied).
# Our current conv_id is 4637fb6a-44df-47cf-be5a-b80bc8edb890.
# Let's see if we have views from this current conversation that cover the entire file (e.g. lines 1 to 1160).
# Let's try to merge all views of the current conversation (4637fb6a-44df-47cf-be5a-b80bc8edb890) or the one immediately prior if needed.
current_conv = "4637fb6a-44df-47cf-be5a-b80bc8edb890"
current_conv_views = [v for v in views if current_conv in v["path"]]

print(f"\nViews in current conversation ({current_conv}):")
for idx, v in enumerate(current_conv_views):
    print(f"  Range: {v['start']}-{v['end']} | Lines count: {len(v['lines'])}")

# Let's also look at the views in the conversation that had 908 lines (e.g., 7f3724fb-481c-454b-8a3d-b8bcea9720fc or similar)
# Actually, let's build a unified dictionary of line number -> line content.
# We will iterate through views chronologically, overlaying lines.
# But wait, lines might shift if modifications were made.
# Let's see if we can reconstruct the file from the current conversation's views.
# Wait! In the current conversation, did we have a view of the beginning of the file?
# Let's check if we have any view in the current conversation that starts at line 1.
# If not, let's look at the conversation just before current conversation (which probably had the exact same file before we started this turn).
# What was the conversation right before current one?
# Let's find the unique conversations in views, sorted by their last view time.
convs = []
for v in views:
    c = v["path"].split("/")[-4]
    if c not in convs:
        convs.append(c)

print("\nConversations in chronological order of last modification:")
for c in convs:
    c_views = [v for v in views if c in v["path"]]
    last_time = c_views[-1]["time"]
    max_line = max(v["end"] for v in c_views)
    print(f"  Conv: {c} | Last Time: {last_time} | Max Line: {max_line}")

# Let's write a file containing the reconstruction from the latest conversation before current, or the current conversation.
# Let's merge the lines from the second to last conversation:
if len(convs) >= 2:
    prev_conv = convs[-2]
    print(f"\nMerging lines from previous conversation: {prev_conv}")
    prev_views = [v for v in views if prev_conv in v["path"]]
    merged_lines = {}
    for v in prev_views:
        merged_lines.update(v["lines"])
    
    max_l = max(merged_lines.keys()) if merged_lines else 0
    print(f"Merged {len(merged_lines)} lines out of {max_l} total lines.")
    
    # Write the reconstruction to a temp file
    recon_path = "/home/abuhaneefayn/Desktop/4school/reconstructed_prev.kt"
    with open(recon_path, "w", encoding="utf-8") as f:
        for i in range(1, max_l + 1):
            f.write(merged_lines.get(i, f"// MISSING LINE {i}\n") + "\n")
    print(f"Reconstruction written to {recon_path}")

# Let's also merge lines from the current conversation
print(f"\nMerging lines from current conversation: {current_conv}")
merged_current = {}
for v in current_conv_views:
    merged_current.update(v["lines"])

max_curr_l = max(merged_current.keys()) if merged_current else 0
print(f"Merged {len(merged_current)} lines out of {max_curr_l} total lines.")
recon_curr_path = "/home/abuhaneefayn/Desktop/4school/reconstructed_curr.kt"
with open(recon_curr_path, "w", encoding="utf-8") as f:
    for i in range(1, max_curr_l + 1):
        recon_line = merged_current.get(i)
        if recon_line is None:
            f.write(f"// MISSING LINE {i}\n")
        else:
            f.write(recon_line + "\n")
print(f"Reconstruction of current written to {recon_curr_path}")
