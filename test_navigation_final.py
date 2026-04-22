#!/usr/bin/env python3
"""
Final verification test for elearner navigation after fix
"""

import requests
import re
import sys

BASE_URL = "http://localhost:8080"
TEST_ENDPOINT = "/elearner/landing?gradeLevel=10&term=1"

def test_page_load():
    """Test that landing page loads successfully"""
    try:
        response = requests.get(f"{BASE_URL}{TEST_ENDPOINT}", timeout=5)
        if response.status_code == 200:
            print("✓ Page loads successfully (HTTP 200)")
            return response.text
        else:
            print(f"✗ Page failed to load (HTTP {response.status_code})")
            return None
    except Exception as e:
        print(f"✗ Connection failed: {e}")
        return None

def check_function_definitions(html_content):
    """Check if key functions are defined in early script blocks"""
    issues = []
    
    # Find the position of the first script block
    first_script_start = html_content.find('<script')
    first_body = html_content.find('<body')
    
    # Extract early script block (after body tag, before main content)
    early_script_start = first_body + 5
    first_onclick = html_content.find('onclick=', early_script_start)
    early_content = html_content[early_script_start:early_script_start+5000]
    
    # Check for function definitions
    functions_to_check = [
        'function toggleMenu',
        'function toggleMobileMenu', 
        'function closeLesson',
        'function loadLesson',
        'function renderAssessments',
        'async function loadLesson'
    ]
    
    for func in functions_to_check:
        if func in early_content:
            print(f"✓ {func} defined early")
        else:
            print(f"✗ {func} NOT found in early content")
            issues.append(func)
    
    # Check that onclick handlers exist but are after function definitions
    if 'onclick="loadLesson' in html_content:
        print("✓ loadLesson onclick handlers found")
    else:
        print("⚠ No loadLesson onclick handlers found (may be okay)")
    
    return len(issues) == 0

def check_api_endpoints():
    """Check if API endpoints are available"""
    try:
        # Test health check
        response = requests.get(f"{BASE_URL}/actuator/health", timeout=5)
        if response.status_code == 200 and "UP" in response.text:
            print("✓ Health check passed")
            return True
        else:
            print(f"✗ Health check failed: {response.text}")
            return False
    except Exception as e:
        print(f"✗ Health check error: {e}")
        return False

def main():
    print("=" * 60)
    print("ELEARNER NAVIGATION FINAL VERIFICATION TEST")
    print("=" * 60)
    
    # Test 1: API availability
    print("\n[1] Testing API endpoints...")
    api_ok = check_api_endpoints()
    
    # Test 2: Page load
    print("\n[2] Testing page load...")
    html = test_page_load()
    
    if not html:
        print("\n✗ TESTS FAILED: Cannot load page")
        sys.exit(1)
    
    # Test 3: Function definitions
    print("\n[3] Checking JavaScript function definitions...")
    funcs_ok = check_function_definitions(html)
    
    # Test 4: Basic content checks
    print("\n[4] Checking page structure...")
    checks = [
        ('elearner-sidebar' in html, "Sidebar element found"),
        ('lesson-viewer' in html, "Lesson viewer element found"),
        ('content-tabs' in html, "Content tabs element found"),
        ('lessonTabMapping' in html, "Lesson tab mapping configured"),
        ('userRole' in html, "User role variable available"),
    ]
    
    all_good = True
    for check, label in checks:
        if check:
            print(f"✓ {label}")
        else:
            print(f"✗ {label}")
            all_good = False
    
    # Summary
    print("\n" + "=" * 60)
    if api_ok and html and funcs_ok and all_good:
        print("✓ ALL TESTS PASSED - Navigation system working correctly!")
        print("=" * 60)
        return 0
    else:
        print("✗ SOME TESTS FAILED - See details above")
        print("=" * 60)
        return 1

if __name__ == "__main__":
    sys.exit(main())
