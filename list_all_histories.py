import os
import glob
import time

history_dirs = [
    "/home/abuhaneefayn/.config/Code/User/History",
    "/home/abuhaneefayn/.config/Antigravity IDE/User/History",
    "/home/abuhaneefayn/.config/Antigravity/User/History",
    "/home/abuhaneefayn/.config/Kiro/User/History"
]

all_repo_backups = []
all_api_backups = []

for hdir in history_dirs:
    if not os.path.exists(hdir):
        continue
    kt_files = glob.glob(os.path.join(hdir, "**/*.kt"), recursive=True)
    for p in kt_files:
        try:
            with open(p, "r", encoding="utf-8", errors="ignore") as f:
                content = f.read()
                if "class SchoolRepository" in content:
                    st = os.stat(p)
                    mtime_str = time.strftime('%Y-%m-%d %H:%M:%S', time.localtime(st.st_mtime))
                    all_repo_backups.append((st.st_mtime, p, st.st_size, mtime_str))
                elif "interface SchoolApiService" in content:
                    st = os.stat(p)
                    mtime_str = time.strftime('%Y-%m-%d %H:%M:%S', time.localtime(st.st_mtime))
                    all_api_backups.append((st.st_mtime, p, st.st_size, mtime_str))
        except Exception as e:
            pass

all_repo_backups.sort()
print(f"Total SchoolRepository backups found: {len(all_repo_backups)}")
for idx, (mtime, p, size, mtime_str) in enumerate(all_repo_backups):
    print(f"[{idx}] Path: {p} | Size: {size} bytes | Modified: {mtime_str}")

all_api_backups.sort()
print(f"\nTotal SchoolApiService backups found: {len(all_api_backups)}")
for idx, (mtime, p, size, mtime_str) in enumerate(all_api_backups):
    print(f"[{idx}] Path: {p} | Size: {size} bytes | Modified: {mtime_str}")
