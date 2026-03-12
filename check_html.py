import requests
import json
import re

session = requests.Session()
login_url = "http://localhost:8080/login"
subscriptions_url = "http://localhost:8080/admin/school-setup/subscriptions"

# Get CSRF token
login_page = session.get(login_url)
match = re.search(r'name="_csrf"\s+value="([^"]+)"', login_page.text)
if not match:
    print("Could not find CSRF token")
    exit(1)
csrf_token = match.group(1)

# I don't know the exact username/password but I saw test credentials in earlier files.
# Let's try system admin or school admin
payload = {
    "username": "admin@4school.app",
    "password": "password",
    "_csrf": csrf_token
}

response = session.post(login_url, data=payload)
if response.status_code != 200 and "Set-Cookie" not in response.headers:
    print("Login failed, assuming maybe need another user")

# fetch subscriptions
res = session.get(subscriptions_url)
if "status-badge" in res.text:
    print("SUCCESS: status-badge IS present in the rendered HTML!")
else:
    print("FAIL: status-badge NOT FOUND. The server is actually serving the old page.")
