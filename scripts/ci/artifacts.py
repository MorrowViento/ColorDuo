#!/usr/bin/env python3
"""Verify built APKs and prepare ColorDuo download/publication artifacts."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[2]
DEVELOPMENT_CERT = "b4e3a12d03957e441c3ccf2e4e2be55de0db35fc5eb9aa8ac49a0a9d7298c41f"
VERSION_PATTERN = re.compile(r"\d+\.\d+\.\d+(?:-[0-9A-Za-z]+(?:[.-][0-9A-Za-z]+)*)?")


def validate_version(version, code):
    if not VERSION_PATTERN.fullmatch(version):
        raise ValueError("Version must be major.minor.patch with an optional prerelease suffix")
    if not re.fullmatch(r"[1-9][0-9]*", str(code)) or int(code) > 2_100_000_000:
        raise ValueError("versionCode must be an integer from 1 to 2100000000")
    return int(code)


def release_notes(version, changelog=None):
    text = changelog if changelog is not None else (ROOT / "docs/CHANGELOG.md").read_text()
    match = re.search(rf"^## {re.escape(version)}[ \t]*\n(.*?)(?=^## |\Z)", text, re.M | re.S)
    if not match or not match[1].strip():
        raise ValueError(f"Missing nonempty changelog entry for {version}")
    return match[1].strip() + "\n"


def github_prefix(owner):
    # Xposed-Modules-Repo/submission/src/bot.ts: normalizeUsername/checkPages.
    return "io.github." + owner.lower().replace("-", "_") + "."


def module_metadata(apk):
    with zipfile.ZipFile(apk) as archive:
        prop = archive.read("META-INF/xposed/module.prop").decode()
        entries = archive.read("META-INF/xposed/java_init.list").decode().strip()
        scope = archive.read("META-INF/xposed/scope.list").decode().strip()
    values = dict(re.findall(r"^([^#=\s]+)\s*=\s*(.*?)\s*$", prop, re.M))
    for field in ("minApiVersion", "targetApiVersion"):
        if not values.get(field, "").isdigit() or int(values[field]) <= 0:
            raise ValueError(f"Missing or invalid Xposed {field}")
    if int(values["targetApiVersion"]) < int(values["minApiVersion"]):
        raise ValueError("Xposed target API is below the minimum API")
    if not entries or not all(re.fullmatch(r"[\w$]+(?:\.[\w$]+)+", x) for x in entries.splitlines()):
        raise ValueError("Missing or invalid Xposed Java entry point")
    if scope != "com.android.systemui":
        raise ValueError("Unexpected module scope")
    return {"properties": values, "javaEntries": entries.splitlines(), "scope": scope}


def verify_apk(apk, variant, metadata, sdk):
    tools = sdk / "build-tools/37.0.0"
    badging = subprocess.check_output([str(tools / "aapt2"), "dump", "badging", str(apk)], text=True)
    package = re.search(r"^package: (.+)$", badging, re.M)
    if not package:
        raise ValueError("APK has no package metadata")
    attrs = dict(re.findall(r"(\w+)='([^']*)'", package[1]))
    expected = {"name": metadata["applicationId"], "versionName": metadata["versionName"],
                "versionCode": str(metadata["versionCode"])}
    if any(attrs.get(key) != value for key, value in expected.items()):
        raise ValueError("APK identity/version differs from Gradle output metadata")
    signature = subprocess.check_output(
        [str(tools / "apksigner"), "verify", "--print-certs", str(apk)], text=True)
    certs = set(re.findall(r"certificate SHA-256 digest: ([0-9a-fA-F]{64})", signature))
    if len(certs) != 1:
        raise ValueError("Expected one verified APK signing certificate")
    cert = next(iter(certs)).lower()
    if variant == "release":
        if "application-debuggable" in badging:
            raise ValueError("Release APK must not be debuggable")
        if cert == DEVELOPMENT_CERT:
            raise ValueError("Release APK uses the publicly tracked development signing key")
    return cert, module_metadata(apk)


def package_apk(args):
    directory = ROOT / "app/build/outputs/apk" / args.variant
    output = json.loads((directory / "output-metadata.json").read_text())
    if len(output["elements"]) != 1:
        raise ValueError("Expected one universal APK")
    item = output["elements"][0]
    version, code = item["versionName"], item["versionCode"]
    validate_version(version, code)
    if args.version is not None and (args.version != version or args.version_code != str(code)):
        raise ValueError("Built APK does not match the requested release version")
    apk = directory / item["outputFile"]
    metadata = {"applicationId": output["applicationId"], "versionName": version, "versionCode": code}
    cert, xposed = verify_apk(apk, args.variant, metadata, args.sdk)
    commit = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip()
    owner = os.environ.get("GITHUB_REPOSITORY", "chorusfruit-233/ColorDuo").split("/")[0]
    prefix = github_prefix(owner)
    notes = release_notes(version) if args.variant == "release" else None
    out = args.output
    if out.exists() and any(out.iterdir()):
        raise ValueError("Artifact output directory must be empty")
    out.mkdir(parents=True, exist_ok=True)
    suffix = f"-debug-{commit[:7]}" if args.variant == "debug" else ""
    target = out / f"ColorDuo-{version}{suffix}.apk"
    shutil.copy2(apk, target)
    digest = hashlib.sha256(target.read_bytes()).hexdigest()
    (out / "SHA256SUMS").write_text(f"{digest}  {target.name}\n")
    info = {**metadata, "variant": args.variant, "commit": commit, "apkSha256": digest,
            "signerSha256": cert, "xposed": xposed,
            "submission": {"expectedPrefix": prefix,
                           "namespaceMatches": metadata["applicationId"].lower().startswith(prefix)}}
    (out / "build-info.json").write_text(json.dumps(info, ensure_ascii=False, indent=2) + "\n")
    if notes is not None:
        (out / "CHANGELOG.md").write_text(notes)
        shutil.copy2(ROOT / "module-README.md", out / "README.md")
        shutil.copy2(ROOT / "module-SUMMARY", out / "SUMMARY")
    artifact_name = f"ColorDuo-{version}-{args.variant}-{commit[:7]}"
    if os.environ.get("GITHUB_OUTPUT"):
        with open(os.environ["GITHUB_OUTPUT"], "a") as stream:
            stream.write(f"artifact_name={artifact_name}\n")
    if os.environ.get("GITHUB_STEP_SUMMARY"):
        with open(os.environ["GITHUB_STEP_SUMMARY"], "a") as stream:
            stream.write(f"APK: `{target.name}`\n\nVersion: `{version}` / `{code}`\n\n")
            stream.write(f"Submission namespace matches `{prefix}*`: **{info['submission']['namespaceMatches']}**\n")
    print(json.dumps(info, ensure_ascii=False, indent=2))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    plan = commands.add_parser("plan", help="Validate release inputs before using signing credentials")
    plan.add_argument("--version", required=True)
    plan.add_argument("--version-code", required=True)
    package = commands.add_parser("package", help="Verify and collect an existing Gradle APK")
    package.add_argument("--variant", choices=("debug", "release"), required=True)
    package.add_argument("--version")
    package.add_argument("--version-code")
    package.add_argument("--output", type=Path, default=ROOT / "dist")
    package.add_argument("--sdk", type=Path, default=os.environ.get("ANDROID_HOME"))
    args = parser.parse_args()
    if args.command == "plan":
        validate_version(args.version, args.version_code)
        release_notes(args.version)
        print(f"Release inputs valid: {args.version} / {args.version_code}")
    else:
        if args.sdk is None:
            parser.error("Set ANDROID_HOME or pass --sdk")
        if args.variant == "release" and (args.version is None or args.version_code is None):
            parser.error("Release packaging requires --version and --version-code")
        package_apk(args)


if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError, KeyError, zipfile.BadZipFile) as error:
        sys.exit(str(error))
    except subprocess.CalledProcessError as error:
        sys.exit(f"{error.cmd[0]} failed with exit code {error.returncode}")
