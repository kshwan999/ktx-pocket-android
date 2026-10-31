"""Bundle the same app/core sources for Swift Playground on iPad. No external packages."""
import argparse
from pathlib import Path
import shutil
import zipfile

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--output", type=Path, default=Path("ios/build/KTX-Pocket-iPad.swiftpm"))
arguments = parser.parse_args()
ios = Path(__file__).resolve().parents[1]
destination = arguments.output.resolve()
if destination.suffix != ".swiftpm" or destination.exists():
    raise SystemExit("Choose a new output directory ending in .swiftpm; existing files are never removed.")
destination.mkdir(parents=True)
shutil.copyfile(ios / "PlaygroundPackage.swift", destination / "Package.swift")
shutil.copyfile(ios / "PlaygroundInfo.plist", destination / "AppInfo.plist")
for source, module in ((ios / "KTXPocket", "AppModule"), (ios / "Core/Sources/KTXPocketCore", "KTXPocketCore")):
    target = destination / "Sources" / module
    target.mkdir(parents=True)
    for path in sorted(source.glob("*.swift")): shutil.copyfile(path, target / path.name)
archive_path = destination.with_suffix(".zip")
with zipfile.ZipFile(archive_path, "x", compression=zipfile.ZIP_DEFLATED) as archive:
    for path in sorted(destination.rglob("*")):
        if path.is_file(): archive.write(path, path.relative_to(destination.parent))
print(f"Created {destination.name} and {archive_path.name}")
