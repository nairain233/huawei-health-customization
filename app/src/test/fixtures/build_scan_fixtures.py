"""生成改名、额外歧义和缺根字段的 DEX，仅写入忽略的 build 目录。"""
from pathlib import Path
import argparse
import os
import subprocess

ROOT = Path(__file__).resolve().parents[4]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--jdk", default=os.environ.get("JAVA_HOME"), required=not os.environ.get("JAVA_HOME"))
parser.add_argument("--d8", required=True, help="Android SDK build-tools 中的 lib/d8.jar")
args = parser.parse_args()
JDK = Path(args.jdk) / "bin"
D8 = Path(args.d8)
suffix = ".exe" if os.name == "nt" else ""
template = (ROOT / "app/src/test/fixtures/structural/ColumnLayoutAdapter.java.in").read_text(encoding="utf-8")
for variant, holder, field, method in [("first", "e", "cn", "x"), ("renamed", "z", "zz", "p"),
                                       ("ambiguous", "z", "zz", "p"), ("missing-root", "z", "zz", "p")]:
    output = ROOT / "build/scan-structure" / variant
    src, classes, dex = (output / name for name in ["sources", "classes", "dex"])
    for directory in [src, classes, dex]:
        directory.mkdir(parents=True, exist_ok=True)
    extra = "public void another(HOLDER h, int i) { BIND(h, i); content(h, i); System.out.println(\"setQuickEntryLayout content is null.\"); HOLDER.take(h).touch(); }" if variant == "ambiguous" else ""
    code = template.replace("EXTRA", extra).replace("HOLDER", holder).replace("ROOT", field).replace("BIND", method)
    if variant == "missing-root":
        code = code.replace(f"return holder.{field};", "return null;")
    files = {
        "com/huawei/health/marketing/views/ColumnLayoutAdapter.java": code,
        "android/widget/RelativeLayout.java": "package android.widget; public class RelativeLayout { public void touch() {} }",
        "com/huawei/health/marketing/datatype/SingleEntryContent.java": "package com.huawei.health.marketing.datatype; public class SingleEntryContent {}",
    }
    for name, body in files.items():
        target = src / name
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(body, encoding="utf-8")
    subprocess.run([str(JDK / f"javac{suffix}"), "-encoding", "UTF-8", "--release", "8", "-Xlint:-options", "-d", str(classes),
                    *[str(src / name) for name in files]], check=True)
    subprocess.run([str(JDK / f"java{suffix}"), "-cp", str(D8), "com.android.tools.r8.D8", "--min-api", "28",
                    "--output", str(dex), *[str(file) for file in sorted(classes.rglob("*.class"))]], check=True)
    print(f"{variant}: {dex / 'classes.dex'}")
