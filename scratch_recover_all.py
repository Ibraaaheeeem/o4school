import os
import glob
import time

history_dirs = [
    "/home/abuhaneefayn/.config/Code/User/History",
    "/home/abuhaneefayn/.config/Antigravity IDE/User/History",
    "/home/abuhaneefayn/.config/Antigravity/User/History",
    "/home/abuhaneefayn/.config/Kiro/User/History"
]

repos = []
apis = []

for hdir in history_dirs:
    if not os.path.exists(hdir):
        continue
    print("Scanning:", hdir)
    kt_files = glob.glob(os.path.join(hdir, "**/*.kt"), recursive=True)
    for p in kt_files:
        try:
            with open(p, "r", encoding="utf-8", errors="ignore") as f:
                first_line = f.readline()
                if "package com.haneef.school.data.repository" in first_line:
                    st = os.stat(p)
                    mtime_str = time.strftime('%Y-%m-%d %H:%M:%S', time.localtime(st.st_mtime))
                    repos.append((st.st_mtime, p, st.st_size, mtime_str))
                elif "package com.haneef.school.data.api" in first_line:
                    st = os.stat(p)
                    mtime_str = time.strftime('%Y-%m-%d %H:%M:%S', time.localtime(st.st_mtime))
                    apis.append((st.st_mtime, p, st.st_size, mtime_str))
        except Exception as e:
            pass

repos.sort()
print(f"\nFound {len(repos)} repository history entries.")
for mtime, p, size, mtime_str in repos[-5:]:
    print(f"File: {p} | Size: {size} bytes | Modified: {mtime_str}")

apis.sort()
print(f"\nFound {len(apis)} api service history entries.")
for mtime, p, size, mtime_str in apis[-5:]:
    print(f"File: {p} | Size: {size} bytes | Modified: {mtime_str}")

# Restore the absolutely LATEST repository
if repos:
    # Sort by mtime descending
    latest_repo = sorted(repos, key=lambda x: x[0], reverse=True)[0]
    print(f"\nRestoring repository to original from {latest_repo[1]} (Size: {latest_repo[2]} bytes, Modified: {latest_repo[3]})...")
    dest_repo = "/home/abuhaneefayn/Desktop/4school/4school/app/src/main/java/com/haneef/school/data/repository/SchoolRepository.kt"
    os.makedirs(os.path.dirname(dest_repo), exist_ok=True)
    with open(latest_repo[1], "r", encoding="utf-8") as src, open(dest_repo, "w", encoding="utf-8") as dest:
        dest.write(src.read())
    print("Repository restored successfully.")

# Restore the absolutely LATEST api service
if apis:
    # Sort by mtime descending
    latest_api = sorted(apis, key=lambda x: x[0], reverse=True)[0]
    print(f"\nRestoring API Service to original from {latest_api[1]} (Size: {latest_api[2]} bytes, Modified: {latest_api[3]})...")
    dest_api = "/home/abuhaneefayn/Desktop/4school/4school/app/src/main/java/com/haneef/school/data/api/SchoolApiService.kt"
    os.makedirs(os.path.dirname(dest_api), exist_ok=True)
    with open(latest_api[1], "r", encoding="utf-8") as src, open(dest_api, "w", encoding="utf-8") as dest:
        dest.write(src.read())
    print("API Service restored successfully.")
