import datetime
import logging
import os
import re
import sys
import time
import xml.etree.ElementTree as ET
from concurrent.futures import ThreadPoolExecutor, as_completed
from contextlib import contextmanager

import pandas as pd
from github import Github, GithubException

try:
    from tqdm import tqdm
except ImportError:
    tqdm = lambda x, **kwargs: x  # fallback if tqdm is not installed

# --- CONFIGURATION ---
GITHUB_TOKEN = ''  # Set to your GitHub token

if GITHUB_TOKEN == '':
    raise ValueError("GITHUB_TOKEN must be set.")

INPUT_CSV = 'dataset_java.csv'
OUTPUT_CSV = 'dataset_java_extended.csv'
PROGRESS_CSV = 'dataset_checkpoint.csv'
LOGFILE = 'dataset_processing.log'
BATCH_SIZE = 100  # Save progress every N repos
MAX_PROJECTS = 1000  # Set to None for no limit, or any integer for testing
MAX_WORKERS = 10   # Number of parallel threads

# --- LOGGING CONFIGURATION ---
logger = logging.getLogger("dataset_logger")
logger.setLevel(logging.DEBUG)

# File handler (detailed logs)
fh = logging.FileHandler(LOGFILE)
fh.setLevel(logging.DEBUG)
fh_formatter = logging.Formatter('[%(asctime)s] %(levelname)s: %(message)s', datefmt='%Y-%m-%d %H:%M:%S')
fh.setFormatter(fh_formatter)
logger.addHandler(fh)

# Console handler (minimal output)
ch = logging.StreamHandler()
ch.setLevel(logging.WARNING)  # Only show warnings and errors in the terminal
ch_formatter = logging.Formatter('%(levelname)s: %(message)s')
ch.setFormatter(ch_formatter)
logger.addHandler(ch)

# --- SUPPRESS STDOUT CONTEXT MANAGER ---
@contextmanager
def suppress_stdout():
    with open(os.devnull, 'w') as devnull:
        old_stdout = sys.stdout
        sys.stdout = devnull
        try:
            yield
        finally:
            sys.stdout = old_stdout

# --- MAVEN PARSING HELPERS ---
def get_file_content(repo, path):
    try:
        file_content = repo.get_contents(path)
        return file_content.decoded_content.decode()
    except Exception:
        return None

def get_namespace(element):
    m = re.match(r'\{.*\}', element.tag)
    return m.group(0) if m else ''

def parse_maven_properties(root, ns):
    properties = {}
    for prop in root.findall(f'.//{ns}properties'):
        for child in prop:
            tag = child.tag.replace(ns, '')
            properties[tag] = child.text
    return properties

def resolve_property(value, properties):
    if value and value.startswith('${') and value.endswith('}'):
        prop_name = value[2:-1]
        return properties.get(prop_name, value)
    return value

def extract_from_properties(root, ns, properties):
    for key in ['maven.compiler.release', 'maven.compiler.source', 'java.version']:
        for prop in root.findall(f'.//{ns}properties/{ns}{key}'):
            val = resolve_property(prop.text, properties)
            if val:
                return val
    return None

def extract_from_plugins(plugins_elem, ns, properties):
    for plugin in plugins_elem.findall(f'{ns}plugin'):
        aid = plugin.find(f'{ns}artifactId')
        if aid is not None and aid.text == 'maven-compiler-plugin':
            config = plugin.find(f'{ns}configuration')
            if config is not None:
                for tag in ['release', 'source', 'target']:
                    elem = config.find(f'{ns}{tag}')
                    if elem is not None and elem.text:
                        return resolve_property(elem.text, properties)
    return None

def extract_from_build(root, ns, properties):
    build = root.find(f'.//{ns}build')
    if build is not None:
        plugins = build.find(f'{ns}plugins')
        if plugins is not None:
            val = extract_from_plugins(plugins, ns, properties)
            if val:
                return val
        pm = build.find(f'{ns}pluginManagement')
        if pm is not None:
            pm_plugins = pm.find(f'{ns}plugins')
            if pm_plugins is not None:
                val = extract_from_plugins(pm_plugins, ns, properties)
                if val:
                    return val
    return None

def extract_from_profiles(root, ns, properties):
    for profile in root.findall(f'.//{ns}profile'):
        prop_elem = profile.find(f'{ns}properties')
        if prop_elem is not None:
            for key in ['maven.compiler.release', 'maven.compiler.source', 'java.version']:
                elem = prop_elem.find(f'{ns}{key}')
                if elem is not None and elem.text:
                    val = resolve_property(elem.text, properties)
                    if val:
                        return val
        build = profile.find(f'{ns}build')
        if build is not None:
            plugins = build.find(f'{ns}plugins')
            if plugins is not None:
                val = extract_from_plugins(plugins, ns, properties)
                if val:
                    return val
            pm = build.find(f'{ns}pluginManagement')
            if pm is not None:
                pm_plugins = pm.find(f'{ns}plugins')
                if pm_plugins is not None:
                    val = extract_from_plugins(pm_plugins, ns, properties)
                    if val:
                        return val
    return None

def extract_java_version_maven(pom_content):
    try:
        root = ET.fromstring(pom_content)
        ns = get_namespace(root)
        properties = parse_maven_properties(root, ns)
        val = extract_from_properties(root, ns, properties)
        if val:
            return val
        val = extract_from_build(root, ns, properties)
        if val:
            return val
        val = extract_from_profiles(root, ns, properties)
        if val:
            return val
        return None
    except Exception:
        return None

def extract_junit_version_maven(pom_content):
    try:
        root = ET.fromstring(pom_content)
        ns = get_namespace(root)
        properties = parse_maven_properties(root, ns)
        for dep in root.findall(f'.//{ns}dependency'):
            gid = dep.find(f'{ns}groupId')
            aid = dep.find(f'{ns}artifactId')
            ver = dep.find(f'{ns}version')
            if gid is not None and aid is not None:
                if 'junit' in gid.text and 'junit' in aid.text:
                    if ver is not None:
                        return resolve_property(ver.text, properties)
        return None
    except Exception:
        return None

def detect_build_tool(repo):
    try:
        files = [f.name for f in repo.get_contents('')]
        if 'pom.xml' in files:
            return 'Maven'
        elif 'build.gradle' in files or 'build.gradle.kts' in files:
            return 'Gradle'
        elif 'build.xml' in files:
            return 'Ant'
        elif 'build.sbt' in files:
            return 'SBT'
        else:
            return 'Unknown'
    except Exception:
        return 'Unknown'

def check_rate_limit(github_instance):
    rate_limit = github_instance.get_rate_limit().core
    if rate_limit.remaining < 10:
        reset_timestamp = rate_limit.reset.timestamp()  # UTC timestamp when limit resets
        now = time.time()
        sleep_time = reset_timestamp - now + 5  # Add 5 seconds buffer
        sleep_time = max(0, sleep_time)
        reset_time_str = datetime.datetime.fromtimestamp(reset_timestamp).strftime('%Y-%m-%d %I:%M:%S %p')
        logger.warning(f"Rate limit reached. Sleeping until reset at {reset_time_str} ({int(sleep_time)} seconds).")
        time.sleep(sleep_time)

def repo_has_dir(repo, dir_path):
    try:
        contents = repo.get_contents(dir_path)
        return isinstance(contents, list)
    except Exception:
        return False


# --- MAIN PROCESSING FUNCTION ---
def process_repo(row):
    repo_fullname = row['repository']
    g = Github(GITHUB_TOKEN)

    is_repo_available = True
    build_tool = 'Unknown'
    pom = None
    junit_version = None
    java_version = None
    has_src_main = None
    has_src_test = None

    repo_size = None
    stargazers = None
    forks = None
    open_issues = None
    watchers = None
    created_at = None
    updated_at = None
    pushed_at = None
    is_archived = None
    is_disabled = None

    logger.info(f"Processing {repo_fullname} ...")
    repo = None

    try:
        check_rate_limit(g)
        with suppress_stdout():
            try:
                repo = g.get_repo(repo_fullname)
            except GithubException as ge:
                if ge.status == 404:
                    logger.warning(f'Repository not found (404): {repo_fullname}')
                    is_repo_available = False
                else:
                    logger.error(f'GitHub error for {repo_fullname}: {ge}')
                    is_repo_available = False
            except Exception as e:
                logger.error(f'General error for {repo_fullname}: {e}')
                is_repo_available = False

        if is_repo_available and repo is not None:
            repo_size = repo.size
            stargazers = repo.stargazers_count
            forks = repo.forks_count
            open_issues = repo.open_issues_count
            watchers = repo.watchers_count
            created_at = repo.created_at
            updated_at = repo.updated_at
            pushed_at = repo.pushed_at
            is_archived = repo.archived
            is_disabled = repo.disabled

            build_tool = detect_build_tool(repo)
            if build_tool == 'Maven':
                pom = get_file_content(repo, 'pom.xml')
            if build_tool == 'Maven' and pom:
                java_version = extract_java_version_maven(pom)
                junit_version = extract_junit_version_maven(pom)
                has_src_main = repo_has_dir(repo, "src/main")
                has_src_test = repo_has_dir(repo, "src/test")
            logger.info(
                f"Done: {repo_fullname} | Build tool: {build_tool} | Java: {java_version} | JUnit: {junit_version} | "
                f"src/main: {has_src_main} | src/test: {has_src_test} | size: {repo_size} | "
                f"stargazers: {stargazers} | forks: {forks} | open_issues: {open_issues} | watchers: {watchers} | "
                f"created_at: {created_at} | updated_at: {updated_at} | pushed_at: {pushed_at} | "
                f"is_archived: {is_archived} | is_disabled: {is_disabled}"
            )

    except Exception as e:
        logger.error(f'Unexpected error for {repo_fullname}: {e}')
        is_repo_available = False

    result_row = row.to_dict()
    result_row.update({
        'is_repo_available': is_repo_available,
        'build_tool': build_tool,
        'java_version': java_version,
        'junit_version': junit_version,
        'has_src_main': has_src_main,
        'has_src_test': has_src_test,

        'repo_size': repo_size,
        'stargazers': stargazers,
        'forks': forks,
        'open_issues': open_issues,
        'watchers': watchers,
        'created_at': created_at,
        'updated_at': updated_at,
        'pushed_at': pushed_at,
        'is_archived': is_archived,
        'is_disabled': is_disabled,
    })
    return result_row

# --- MAIN SCRIPT ---
def main():
    df = pd.read_csv(INPUT_CSV)
    if os.path.exists(PROGRESS_CSV):
        progress_df = pd.read_csv(PROGRESS_CSV)
        processed_repos = set(progress_df['repository'])
        logger.info(f"Resuming from checkpoint. {len(processed_repos)} repositories already processed.")
        results = progress_df.to_dict(orient='records')
    else:
        processed_repos = set()
        results = []

    rows_to_process = [row for idx, row in df.iterrows() if row['repository'] not in processed_repos]
    if MAX_PROJECTS is not None:
        rows_to_process = rows_to_process[:MAX_PROJECTS]

    logger.info(f"Starting processing of {len(rows_to_process)} repositories with {MAX_WORKERS} threads...")

    # Minimal terminal output: just show progress bar and warnings/errors
    with ThreadPoolExecutor(max_workers=MAX_WORKERS) as executor:
        futures = {executor.submit(process_repo, row): row for row in rows_to_process}
        for i, future in enumerate(tqdm(as_completed(futures), total=len(futures))):
            result_row = future.result()
            results.append(result_row)
            if (i + 1) % BATCH_SIZE == 0:
                progress_df = pd.DataFrame(results)
                progress_df.to_csv(PROGRESS_CSV, index=False)
                logger.info(f"Checkpoint saved at {i+1} repositories.")

    final_df = pd.DataFrame(results)
    final_df.to_csv(OUTPUT_CSV, index=False)
    logger.info(f'All done! Extended CSV written to {OUTPUT_CSV}')

if __name__ == '__main__':
    main()
