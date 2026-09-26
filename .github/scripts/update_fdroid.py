#!/usr/bin/env python3
"""
Automated script to update app metadata in the official F-Droid data repository
(https://gitlab.com/fdroid/fdroiddata) and submit a GitLab Merge Request.
"""

import argparse
import json
import os
import re
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Any, Dict, Optional, Tuple

UPSTREAM_PROJECT_ID = 2044884  # fdroid/fdroiddata
UPSTREAM_REPO_URL = "https://gitlab.com/fdroid/fdroiddata.git"
DEFAULT_APP_ID = "ua.bossly.tools.translit"


def log(message: str) -> None:
    print(f"[fdroid-update] {message}", flush=True)


def log_warn(message: str) -> None:
    print(f"::warning::[fdroid-update] {message}", flush=True)


def log_notice(message: str) -> None:
    print(f"::notice::[fdroid-update] {message}", flush=True)


def resolve_version_info(
    version_name_arg: Optional[str] = None,
    version_code_arg: Optional[str] = None,
    commit_arg: Optional[str] = None,
) -> Tuple[str, str, str]:
    """
    Resolve version name, version code, and commit/tag using:
    1. CLI args or environment variables
    2. APK output-metadata.json (if produced by build step)
    3. gradle.properties
    """
    version_name = version_name_arg or os.getenv("APP_VERSION_NAME")
    version_code = version_code_arg or os.getenv("APP_VERSION_CODE")
    commit_ref = (
        commit_arg
        or os.getenv("APP_COMMIT")
        or os.getenv("GITHUB_REF_NAME")
        or os.getenv("GITHUB_SHA")
    )

    # Check output-metadata.json if available
    metadata_file = Path("app/build/outputs/apk/release/output-metadata.json")
    if metadata_file.exists():
        try:
            with open(metadata_file, "r", encoding="utf-8") as f:
                meta = json.load(f)
            elements = meta.get("elements", [])
            if elements:
                el = elements[0]
                if not version_name and "versionName" in el:
                    version_name = str(el["versionName"])
                if not version_code and "versionCode" in el:
                    version_code = str(el["versionCode"])
                log(f"Loaded version info from {metadata_file}: versionName={version_name}, versionCode={version_code}")
        except Exception as e:
            log(f"Could not parse {metadata_file}: {e}")

    # Fallback to gradle.properties
    gradle_props = Path("gradle.properties")
    if gradle_props.exists() and (not version_name or not version_code):
        try:
            with open(gradle_props, "r", encoding="utf-8") as f:
                for line in f:
                    line = line.strip()
                    if line.startswith("appVersionName=") and not version_name:
                        version_name = line.split("=", 1)[1].strip()
                    elif line.startswith("appVersionCode=") and not version_code:
                        version_code = line.split("=", 1)[1].strip()
            if version_name or version_code:
                log(f"Loaded version info from gradle.properties: versionName={version_name}, versionCode={version_code}")
        except Exception as e:
            log(f"Could not parse gradle.properties: {e}")

    if not version_name:
        raise ValueError("Could not resolve version name (APP_VERSION_NAME).")
    if not version_code:
        raise ValueError("Could not resolve version code (APP_VERSION_CODE).")
    if not commit_ref:
        commit_ref = version_name

    return version_name, str(version_code), commit_ref


def update_metadata_content(
    content: str,
    version_name: str,
    version_code: str,
    commit_ref: str,
) -> Tuple[str, bool]:
    """
    Updates the YAML metadata string for F-Droid:
    - Appends a new build block under Builds: if not already present
    - Updates CurrentVersion and CurrentVersionCode
    Returns (new_content, was_modified)
    """
    # Check if this versionCode already exists under Builds
    vc_pattern = rf"^\s*-\s*versionName:\s*.*\n\s*versionCode:\s*{re.escape(version_code)}\b"
    already_has_build = bool(re.search(vc_pattern, content, re.MULTILINE))

    new_content = content

    if not already_has_build:
        # Build block to insert
        new_build = (
            f"  - versionName: {version_name}\n"
            f"    versionCode: {version_code}\n"
            f"    commit: {commit_ref}\n"
            f"    subdir: app\n"
            f"    gradle:\n"
            f"      - yes\n"
        )

        # Locate where Builds: list ends.
        # Top-level keys that typically appear after Builds:
        # AutoUpdateMode, UpdateCheckMode, CurrentVersion, CurrentVersionCode, ArchivePolicy, etc.
        pattern = r"(\n(?:AutoUpdateMode|UpdateCheckMode|CurrentVersion|CurrentVersionCode|ArchivePolicy):)"
        match = re.search(pattern, new_content)
        if match:
            idx = match.start(1)
            # Ensure a preceding newline before inserting
            new_content = new_content[:idx].rstrip() + "\n\n" + new_build + "\n" + new_content[idx:].lstrip("\n")
        else:
            # If no following section found, simply append at the end of Builds or end of file
            new_content = new_content.rstrip() + "\n\n" + new_build

    # Update CurrentVersion
    if re.search(r"^CurrentVersion:\s*.*$", new_content, re.MULTILINE):
        new_content = re.sub(
            r"^CurrentVersion:\s*.*$",
            f"CurrentVersion: {version_name}",
            new_content,
            flags=re.MULTILINE,
        )
    else:
        new_content = new_content.rstrip() + f"\nCurrentVersion: {version_name}\n"

    # Update CurrentVersionCode
    if re.search(r"^CurrentVersionCode:\s*.*$", new_content, re.MULTILINE):
        new_content = re.sub(
            r"^CurrentVersionCode:\s*.*$",
            f"CurrentVersionCode: {version_code}",
            new_content,
            flags=re.MULTILINE,
        )
    else:
        new_content = new_content.rstrip() + f"\nCurrentVersionCode: {version_code}\n"

    was_modified = new_content != content
    return new_content, was_modified


class GitLabClient:
    def __init__(self, token: str):
        self.token = token
        self.base_url = "https://gitlab.com/api/v4"

    def request(
        self,
        method: str,
        path: str,
        data: Optional[Dict[str, Any]] = None,
    ) -> Dict[str, Any]:
        url = f"{self.base_url}{path}"
        headers = {
            "PRIVATE-TOKEN": self.token,
            "User-Agent": "uaTranslit-fdroid-updater",
        }
        body = None
        if data is not None:
            body = json.dumps(data).encode("utf-8")
            headers["Content-Type"] = "application/json"

        req = urllib.request.Request(url, data=body, headers=headers, method=method)
        try:
            with urllib.request.urlopen(req) as resp:
                resp_body = resp.read().decode("utf-8")
                return json.loads(resp_body) if resp_body else {}
        except urllib.error.HTTPError as e:
            err_msg = e.read().decode("utf-8", errors="replace")
            raise RuntimeError(f"GitLab API {method} {path} returned HTTP {e.code}: {err_msg}") from e

    def get_user(self) -> Dict[str, Any]:
        return self.request("GET", "/user")

    def get_project(self, project_path_or_id: Any) -> Optional[Dict[str, Any]]:
        quoted = urllib.parse.quote(str(project_path_or_id), safe="")
        try:
            return self.request("GET", f"/projects/{quoted}")
        except RuntimeError as e:
            if "HTTP 404" in str(e):
                return None
            raise

    def fork_project(self, upstream_id: int) -> Dict[str, Any]:
        return self.request("POST", f"/projects/{upstream_id}/fork")

    def find_open_mr(self, upstream_id: int, source_branch: str) -> Optional[Dict[str, Any]]:
        quoted_branch = urllib.parse.quote(source_branch, safe="")
        mrs = self.request("GET", f"/projects/{upstream_id}/merge_requests?source_branch={quoted_branch}&state=opened")
        if isinstance(mrs, list) and len(mrs) > 0:
            return mrs[0]
        return None

    def create_mr(
        self,
        fork_project_id: int,
        source_branch: str,
        target_project_id: int,
        title: str,
        description: str,
    ) -> Dict[str, Any]:
        data = {
            "source_branch": source_branch,
            "target_branch": "master",
            "target_project_id": target_project_id,
            "title": title,
            "description": description,
            "remove_source_branch": True,
        }
        return self.request("POST", f"/projects/{fork_project_id}/merge_requests", data)


def ensure_gitlab_fork(client: GitLabClient, username: str) -> Dict[str, Any]:
    fork_path = f"{username}/fdroiddata"
    fork = client.get_project(fork_path)
    if fork:
        log(f"Found existing fork: {fork_path} (ID: {fork['id']})")
        return fork

    log(f"Fork {fork_path} not found. Forking fdroid/fdroiddata (ID: {UPSTREAM_PROJECT_ID})...")
    fork = client.fork_project(UPSTREAM_PROJECT_ID)

    # Wait for fork import to complete
    for _ in range(30):
        time.sleep(3)
        check = client.get_project(fork_path)
        if check and check.get("import_status") in ("finished", "none", ""):
            log(f"Fork created successfully: {fork_path} (ID: {check['id']})")
            return check

    return fork


def run_cmd(cmd: list[str], cwd: Optional[Path] = None, check: bool = True) -> subprocess.CompletedProcess:
    return subprocess.run(
        cmd,
        cwd=cwd,
        check=check,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
    )


def append_step_summary(mr_url: str, version_name: str, version_code: str) -> None:
    summary_path = os.getenv("GITHUB_STEP_SUMMARY")
    if not summary_path:
        return
    try:
        with open(summary_path, "a", encoding="utf-8") as f:
            f.write(
                "\n### 🤖 F-Droid Metadata Update\n\n"
                f"- **App Version**: `{version_name}` (code `{version_code}`)\n"
                f"- **GitLab Merge Request**: [{mr_url}]({mr_url})\n\n"
            )
    except Exception as e:
        log(f"Failed to write to $GITHUB_STEP_SUMMARY: {e}")


def main() -> int:
    parser = argparse.ArgumentParser(description="Update F-Droid metadata and create GitLab MR")
    parser.add_argument("--version-name", help="App version name (e.g. 2.0.0)")
    parser.add_argument("--version-code", help="App version code (e.g. 43)")
    parser.add_argument("--commit", help="Commit hash or tag name")
    parser.add_argument("--app-id", default=DEFAULT_APP_ID, help="App package ID")
    parser.add_argument("--token", help="GitLab Personal Access Token")
    parser.add_argument("--metadata-file", help="Path to local metadata YAML file (for testing)")
    parser.add_argument("--dry-run", action="store_true", help="Perform local update without pushing or creating MR")
    args = parser.parse_args()

    token = args.token or os.getenv("GITLAB_TOKEN")
    if not token and not args.dry_run:
        log_warn(
            "GITLAB_TOKEN secret is not set. Skipping F-Droid Merge Request creation. "
            "To enable automated F-Droid updates, create a GitLab Personal Access Token with 'api' scope "
            "and add it to your GitHub Repository Secrets as GITLAB_TOKEN."
        )
        return 0

    try:
        version_name, version_code, commit_ref = resolve_version_info(
            version_name_arg=args.version_name,
            version_code_arg=args.version_code,
            commit_arg=args.commit,
        )
    except Exception as e:
        log(f"Error resolving version: {e}")
        return 1

    log(f"Target version: {version_name} (code: {version_code}), commit ref: {commit_ref}")

    if args.dry_run:
        log("Running in --dry-run mode...")
        if args.metadata_file and Path(args.metadata_file).exists():
            log(f"Reading metadata from local file: {args.metadata_file}")
            with open(args.metadata_file, "r", encoding="utf-8") as f:
                content = f.read()
        else:
            raw_url = f"https://gitlab.com/fdroid/fdroiddata/-/raw/master/metadata/{args.app_id}.yml"
            try:
                with urllib.request.urlopen(raw_url) as resp:
                    content = resp.read().decode("utf-8")
            except Exception as e:
                log(f"Failed to fetch current metadata from {raw_url}: {e}")
                return 1

        updated_content, modified = update_metadata_content(content, version_name, version_code, commit_ref)
        log(f"Metadata modified: {modified}")
        print("\n--- [DRY-RUN] Resulting metadata (last 25 lines) ---")
        print("\n".join(updated_content.splitlines()[-25:]))
        print("--- [DRY-RUN] End ---")
        return 0

    client = GitLabClient(token)
    try:
        user_info = client.get_user()
        gitlab_username = user_info["username"]
        log(f"Authenticated as GitLab user: {gitlab_username}")
    except Exception as e:
        log(f"Authentication failed: {e}")
        return 1

    # Ensure user has a fork of fdroid/fdroiddata
    try:
        fork_info = ensure_gitlab_fork(client, gitlab_username)
        fork_project_id = fork_info["id"]
    except Exception as e:
        log(f"Failed to ensure GitLab fork: {e}")
        return 1

    branch_name = f"update-{args.app_id}-{version_name}"

    # Check if an open MR already exists
    existing_mr = client.find_open_mr(UPSTREAM_PROJECT_ID, branch_name)
    if existing_mr:
        mr_url = existing_mr["web_url"]
        log_notice(f"An open Merge Request already exists: {mr_url}")
        append_step_summary(mr_url, version_name, version_code)
        return 0

    # Work in a temporary directory
    with tempfile.TemporaryDirectory() as tmp_dir:
        repo_dir = Path(tmp_dir) / "fdroiddata"
        log("Shallow cloning upstream fdroid/fdroiddata (depth 1)...")
        run_cmd(["git", "clone", "--depth", "1", UPSTREAM_REPO_URL, str(repo_dir)])

        # Configure git
        run_cmd(["git", "config", "user.name", "github-actions[bot]"], cwd=repo_dir)
        run_cmd(["git", "config", "user.email", "github-actions[bot]@users.noreply.github.com"], cwd=repo_dir)

        # Checkout update branch
        run_cmd(["git", "checkout", "-b", branch_name], cwd=repo_dir)

        # Update metadata file
        metadata_file = repo_dir / "metadata" / f"{args.app_id}.yml"
        if not metadata_file.exists():
            log(f"Error: metadata file not found at {metadata_file}")
            return 1

        with open(metadata_file, "r", encoding="utf-8") as f:
            content = f.read()

        new_content, modified = update_metadata_content(content, version_name, version_code, commit_ref)
        if not modified:
            log("No changes detected in metadata. File is already up to date.")
            return 0

        with open(metadata_file, "w", encoding="utf-8") as f:
            f.write(new_content)

        commit_title = f"Update {args.app_id} to {version_name} ({version_code})"
        run_cmd(["git", "commit", "-am", commit_title], cwd=repo_dir)

        # Push to the user's fork
        fork_push_url = f"https://oauth2:{token}@gitlab.com/{gitlab_username}/fdroiddata.git"
        log(f"Pushing branch {branch_name} to fork {gitlab_username}/fdroiddata...")
        run_cmd(["git", "push", fork_push_url, branch_name, "--force"], cwd=repo_dir)

    # Create Merge Request
    mr_title = f"Update {args.app_id} to {version_name} ({version_code})"
    mr_description = (
        f"Automated update for `{args.app_id}` (`{version_name}`, versionCode `{version_code}`).\n\n"
        f"- **Upstream Repository**: https://github.com/bossly/uaTranslit\n"
        f"- **Release ref**: `{commit_ref}`\n\n"
        f"*Created by GitHub Actions release workflow.*"
    )
    log(f"Opening Merge Request from {gitlab_username}:{branch_name} to fdroid/fdroiddata:master...")
    try:
        mr = client.create_mr(
            fork_project_id=fork_project_id,
            source_branch=branch_name,
            target_project_id=UPSTREAM_PROJECT_ID,
            title=mr_title,
            description=mr_description,
        )
        mr_url = mr.get("web_url", "")
        log_notice(f"Merge Request created successfully: {mr_url}")
        append_step_summary(mr_url, version_name, version_code)
    except Exception as e:
        log(f"Failed to create Merge Request: {e}")
        return 1

    return 0


if __name__ == "__main__":
    sys.exit(main())
