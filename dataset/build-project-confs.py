import csv
import os

CSV_FILE = 'dataset_java_extended_filtered.csv'
OUTPUT_DIR = '../project-configs/evaluation/'
LOG_DIR = '../logs/'
MAX_ROWS = None  # Set to an integer to limit rows, or None for all

def generate_project_confs(csv_file, output_dir, log_dir, max_rows=None):
    # Create output directory if it doesn't exist
    os.makedirs(output_dir, exist_ok=True)

    with open(csv_file, newline='', encoding='utf-8') as csvfile:
        reader = csv.DictReader(csvfile)
        for idx, row in enumerate(reader, start=1):
            if max_rows is not None and idx > max_rows:
                break

            conf_filename = f'project-{idx}.conf'
            conf_path = os.path.join(output_dir, conf_filename)
            log_filename = f'project-{idx}.txt'
            log_path = os.path.join(log_dir, log_filename)

            if os.path.exists(conf_path):
                print(f"Skipped: {conf_path} already exists.")
                continue

            if os.path.exists(log_path):
                print(f"Skipped: {conf_path} because log file {log_path} already exists.")
                continue

            repo = row['repository']
            root_path = f'https://github.com/{repo}.git'
            conf_content = f'''teralizer {{
  project {{
    root-path = "{root_path}"
  }}
}}
'''

            with open(conf_path, 'w', encoding='utf-8') as conf_file:
                conf_file.write(conf_content)
            print(f"Created: {conf_path}")

if __name__ == "__main__":
    generate_project_confs(CSV_FILE, OUTPUT_DIR, LOG_DIR, MAX_ROWS)
