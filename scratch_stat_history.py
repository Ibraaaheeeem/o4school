import os
import glob
import time

repo_paths = [
    "/home/abuhaneefayn/.config/Kiro/User/History/15c82b77/K3jq.kt",
    "/home/abuhaneefayn/.config/Kiro/User/History/-19dc721f/bch3.kt",
    "/home/abuhaneefayn/.config/Kiro/User/History/-19dc721f/bdmR.kt",
    "/home/abuhaneefayn/.config/Kiro/User/History/-19dc721f/m168.kt",
    "/home/abuhaneefayn/.config/Kiro/User/History/-19dc721f/Md1c.kt",
    "/home/abuhaneefayn/.config/Kiro/User/History/-19dc721f/vUYE.kt",
    "/home/abuhaneefayn/.config/Kiro/User/History/-19dc721f/WzOZ.kt",
    "/home/abuhaneefayn/.config/Kiro/User/History/60d64495/PUAf.kt",
    "/home/abuhaneefayn/.config/Kiro/User/History/74f641bd/45bw.kt",
    "/home/abuhaneefayn/.config/Kiro/User/History/74f641bd/awPm.kt",
    "/home/abuhaneefayn/.config/Kiro/User/History/74f641bd/gRFg.kt",
    "/home/abuhaneefayn/.config/Kiro/User/History/74f641bd/HKx3.kt",
    "/home/abuhaneefayn/.config/Kiro/User/History/74f641bd/l3Rc.kt",
    "/home/abuhaneefayn/.config/Kiro/User/History/74f641bd/N21m.kt",
    "/home/abuhaneefayn/.config/Kiro/User/History/74f641bd/oKnZ.kt",
    "/home/abuhaneefayn/.config/Kiro/User/History/74f641bd/Q4HL.kt",
    "/home/abuhaneefayn/.config/Kiro/User/History/74f641bd/xyYi.kt",
    "/home/abuhaneefayn/.config/Kiro/User/History/-ec2f62/PNVC.kt"
]

api_paths = [
    "/home/abuhaneefayn/.config/Kiro/User/History/126fb886/CqP2.kt",
    "/home/abuhaneefayn/.config/Kiro/User/History/126fb886/q7RN.kt",
    "/home/abuhaneefayn/.config/Kiro/User/History/126fb886/z1eT.kt",
    "/home/abuhaneefayn/.config/Kiro/User/History/5a7da6be/wUsn.kt",
    "/home/abuhaneefayn/.config/Kiro/User/History/-6babd582/28Df.kt",
    "/home/abuhaneefayn/.config/Kiro/User/History/-6babd582/LNn1.kt",
    "/home/abuhaneefayn/.config/Kiro/User/History/-6babd582/W57i.kt",
    "/home/abuhaneefayn/.config/Kiro/User/History/725e1d3/QPmL.kt",
    "/home/abuhaneefayn/.config/Kiro/User/History/74e979f2/E5Aj.kt"
]

print("--- Repository Files ---")
repo_details = []
for p in repo_paths:
    if os.path.exists(p):
        st = os.stat(p)
        mtime_str = time.strftime('%Y-%m-%d %H:%M:%S', time.localtime(st.st_mtime))
        repo_details.append((st.st_mtime, p, st.st_size, mtime_str))

repo_details.sort()
for mtime, p, size, mtime_str in repo_details:
    print(f"File: {p} | Size: {size} bytes | Modified: {mtime_str}")

print("\n--- API Service Files ---")
api_details = []
for p in api_paths:
    if os.path.exists(p):
        st = os.stat(p)
        mtime_str = time.strftime('%Y-%m-%d %H:%M:%S', time.localtime(st.st_mtime))
        api_details.append((st.st_mtime, p, st.st_size, mtime_str))

api_details.sort()
for mtime, p, size, mtime_str in api_details:
    print(f"File: {p} | Size: {size} bytes | Modified: {mtime_str}")
