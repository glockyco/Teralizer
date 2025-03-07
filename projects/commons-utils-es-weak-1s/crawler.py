import csv
import os
import requests
from pathlib import Path
import sys

def normalize_path(file_path):
    file_path = Path(file_path)
    try:
        src_index = file_path.parts.index("src")
        return Path(*file_path.parts[src_index:])
    except ValueError:
        print(f"Skipping: 'src' not found in {file_path}")
        return None

def get_test_variants(file_path, url):
    test_file_path = Path(str(file_path).replace("main", "test"))
    if test_file_path.suffix == ".java":
        test_file_path = test_file_path.with_stem(test_file_path.stem + "Test")
    
    test_url = url.replace("main", "test")
    if test_url.endswith(".java"):
        test_url = test_url.replace(".java", "Test.java")
    
    return test_file_path, test_url

def download_file(file_path, url):
    if not file_path:
        return
    
    file_path.parent.mkdir(parents=True, exist_ok=True)
    
    try:
        response = requests.get(url, stream=True)
        response.raise_for_status()  # Raise an error for bad responses
        
        with open(file_path, 'wb') as f:
            for chunk in response.iter_content(chunk_size=8192):
                f.write(chunk)
        
        print(f"Downloaded: {file_path}")
    except requests.RequestException as e:
        print(f"Failed to download {url}: {e}")

def download_files_from_csv(csv_file):
    with open(csv_file, newline='', encoding='utf-8') as file:
        reader = csv.reader(file, delimiter=';')
        for row in reader:
            if len(row) != 2:
                print(f"Skipping invalid row: {row}")
                continue
            
            file_path, url = row
            normalized_path = normalize_path(file_path)
            if not normalized_path:
                continue
            
            download_file(normalized_path, url)
            
            test_file_path, test_url = get_test_variants(normalized_path, url)
            download_file(test_file_path, test_url)

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: python script.py <csv_file_path>")
        sys.exit(1)
    
    csv_file = sys.argv[1]
    download_files_from_csv(csv_file)
