import os
import re

def get_kotlin_files(root_dir):
    files = []
    for dirpath, _, filenames in os.walk(root_dir):
        for f in filenames:
            if f.endswith('.kt'):
                files.append(os.path.join(dirpath, f))
    return files

def get_html_files(root_dir):
    files = []
    for dirpath, _, filenames in os.walk(root_dir):
        for f in filenames:
            if f.endswith('.html'):
                files.append(os.path.join(dirpath, f))
    return files

def get_js_files(root_dir):
    files = []
    for dirpath, _, filenames in os.walk(root_dir):
        for f in filenames:
            if f.endswith('.js'):
                files.append(os.path.join(dirpath, f))
    return files

def extract_backend_routes(files):
    routes = {} 
    
    # Regex for class level RequestMapping
    class_mapping_re = re.compile(r'@RequestMapping\s*\(\s*(?:value\s*=\s*)?"([^"]+)"')
    
    # Regex for method level mappings
    method_mapping_re = re.compile(r'@(Get|Post|Put|Delete|Patch|Request)Mapping\s*(?:\(\s*(?:value\s*=\s*)?"([^"]+)"|\(\s*"([^"]+)")?')
    
    for file_path in files:
        with open(file_path, 'r') as f:
            lines = f.readlines()
            # Filter out lines that start with // (ignoring whitespace)
            content = "".join([line for line in lines if not line.strip().startswith("//")])
            
        class_prefix = ""
        class_match = class_mapping_re.search(content)
        class_span = (-1, -1)
        if class_match:
            class_prefix = class_match.group(1)
            if class_prefix.endswith('/'):
                class_prefix = class_prefix[:-1]
            class_span = class_match.span()
        
        for match in method_mapping_re.finditer(content):
            if match.span() == class_span:
                continue
                
            path1 = match.group(2)
            path2 = match.group(3)
            path = path1 if path1 else path2
            
            if path is None:
                path = ""
            
            full_path = class_prefix + path
            if not full_path.startswith('/'):
                full_path = '/' + full_path
            
            if len(full_path) > 1 and full_path.endswith('/'):
                full_path = full_path[:-1]
                
            normalized_path = re.sub(r'\{[^}]+\}', '{var}', full_path)
            routes[normalized_path] = file_path
            
    return routes

def extract_frontend_calls(files):
    calls = set()
    
    # 1. Attributes (Thymeleaf & HTMX)
    attr_re = re.compile(r'\b(?:th:)?(?:href|action|src|hx-get|hx-post|hx-put|hx-delete|hx-patch)\s*=\s*"([^"]+)"')
    
    # 2. Redirects in Kotlin
    redirect_re = re.compile(r'redirect:([^\s"]+)')
    
    # 3. JS Strings (Quotes and Backticks)
    quote_re = re.compile(r'["\'](/[a-zA-Z0-9_/-]+(?:\{[^}]+\})?)["\']')
    backtick_re = re.compile(r'`(/[^{}`]+(?:\$\{[^}]+\}[^{}`]*)*)`')
    
    for file_path in files:
        with open(file_path, 'r') as f:
            content = f.read()
            
        # HTML Processing
        if file_path.endswith('.html'):
            for match in attr_re.finditer(content):
                val = match.group(1).strip()
                if val.startswith('@{'):
                    if val.endswith('}'):
                        val = val[2:-1]
                if '(' in val:
                    val = val.split('(')[0]
                
                if not val.startswith('/'): continue
                if len(val) > 1 and val.endswith('/'): val = val[:-1]
                
                val = re.sub(r'\$\{[^}]+\}', '{var}', val) 
                val = re.sub(r'__\$\{([^}]+)\}_?_', '{var}', val)
                val = re.sub(r'\{[^}]+\}', '{var}', val)
                
                calls.add(val)

            # Also check JS strings in HTML
            for match in quote_re.finditer(content):
                val = match.group(1)
                if len(val) > 1 and val.endswith('/'): val = val[:-1]
                val = re.sub(r'\{[^}]+\}', '{var}', val)
                calls.add(val)
            for match in backtick_re.finditer(content):
                val = match.group(1)
                if len(val) > 1 and val.endswith('/'): val = val[:-1]
                val = re.sub(r'\$\{[^}]+\}', '{var}', val)
                calls.add(val)

        # JS Processing
        if file_path.endswith('.js'):
            for match in quote_re.finditer(content):
                val = match.group(1)
                if len(val) > 1 and val.endswith('/'): val = val[:-1]
                val = re.sub(r'\{[^}]+\}', '{var}', val)
                calls.add(val)
            for match in backtick_re.finditer(content):
                val = match.group(1)
                if len(val) > 1 and val.endswith('/'): val = val[:-1]
                val = re.sub(r'\$\{[^}]+\}', '{var}', val)
                calls.add(val)

        # Kotlin Processing
        if file_path.endswith('.kt'):
             for match in redirect_re.finditer(content):
                val = match.group(1)
                if not val.startswith('/'): continue
                if len(val) > 1 and val.endswith('/'): val = val[:-1]
                
                val = re.sub(r'\$\{[^}]+\}', '{var}', val)
                val = re.sub(r'\{[^}]+\}', '{var}', val)
                
                calls.add(val)

    return calls

def main():
    base_dir = '/home/abuhaneefayn/Desktop/4school'
    kotlin_dir = os.path.join(base_dir, 'src/main/kotlin')
    templates_dir = os.path.join(base_dir, 'src/main/resources/templates')
    static_js_dir = os.path.join(base_dir, 'src/main/resources/static/js')
    
    kt_files = get_kotlin_files(kotlin_dir)
    html_files = get_html_files(templates_dir)
    js_files = get_js_files(static_js_dir)
    
    backend_routes_map = extract_backend_routes(kt_files)
    frontend_calls = extract_frontend_calls(html_files + kt_files + js_files)
    
    unused = []
    for route, fpath in backend_routes_map.items():
        if route not in frontend_calls:
            route_regex = "^" + route.replace("{var}", "[^/]+") + "$"
            matched = False
            for call in frontend_calls:
                if re.match(route_regex, call):
                    matched = True
                    break
            
            if not matched:
                unused.append((route, fpath))
            
    print(f"Found {len(backend_routes_map)} backend routes.")
    print(f"Found {len(frontend_calls)} frontend/redirect calls.")
    print(f"Potential unused routes ({len(unused)}):")
    
    for r, f in sorted(unused):
        print(f"{r}  [{os.path.basename(f)}]")

if __name__ == '__main__':
    main()
