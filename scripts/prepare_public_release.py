"""Prepare a clean public source snapshot; never copy private development history.

Run from the repository root: python scripts/prepare_public_release.py <new-output-dir>
The output directory must not already exist. This script does not publish anything.
"""
from pathlib import Path
import shutil
import sys

ROOT = Path(__file__).resolve().parents[1]
FILES = (
    '.gitignore', 'README.md', 'BUILD.md', 'LICENSE', 'THIRD_PARTY_NOTICES.md',
    'build.gradle.kts', 'settings.gradle.kts', 'gradle.properties', 'gradlew', 'gradlew.bat',
    'water.properties.example', 'keystore.properties.example', 'app/build.gradle.kts',
    'app/proguard-rules.pro', 'docs/RELEASE_NOTES.md', 'docs/SCREENSHOTS.md',
    'scripts/patch_pip_install.py', 'scripts/prepare_public_release.py',
)
DIRECTORIES = ('gradle', 'app/src/main', 'app/src/demo', 'app/src/test', 'vendor', 'docs/screenshots')

def prepare(target):
    if target.exists():
        raise SystemExit('Output already exists; choose a new directory.')
    target.mkdir(parents=True)
    for name in FILES:
        src = ROOT / name
        dst = target / name
        dst.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(src, dst)
    for name in DIRECTORIES:
        shutil.copytree(ROOT / name, target / name,
            ignore=shutil.ignore_patterns('__pycache__', '*.pyc', '*.log', 'build'))
    forbidden = {'water.properties', 'keystore.properties', 'local.properties', 'memory.md', '.git'}
    for p in target.rglob('*'):
        if p.is_file() and (p.name in forbidden or p.suffix in {'.jks', '.keystore', '.apk', '.pyc', '.log'}):
            raise SystemExit('Forbidden file: ' + str(p.relative_to(target)))
    print('Prepared', sum(p.is_file() for p in target.rglob('*')), 'files at', target)

if __name__ == '__main__':
    prepare(Path(sys.argv[1]).resolve())
