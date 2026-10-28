"""Compile sur un serveur de build distant (SSH), puis rapatrie les APK et les résultats des tests.

Usage : python scripts/remote.py <arguments gradle...>
Configuration dans remote.properties (ignoré par git), par exemple :
    host=192.168.1.10
    user=builder
    dir=kairo-build
    java_home=~/jdk17
    gradle_home=~/gradle-8.7
    android_home=~/android-sdk
Mot de passe SSH : variable d'environnement KAIRO_SSH_PW (jamais dans le dépôt).
"""
import io
import os
import subprocess
import sys
import tarfile

import paramiko


def load_config(root: str) -> dict:
    path = os.path.join(root, "remote.properties")
    if not os.path.isfile(path):
        sys.exit("remote.properties manquant (voir l'en-tête de scripts/remote.py)")
    conf = {}
    with open(path, encoding="utf-8") as fh:
        for line in fh:
            line = line.strip()
            if line and not line.startswith("#") and "=" in line:
                key, value = line.split("=", 1)
                conf[key.strip()] = value.strip().replace("~", "$HOME", 1)
    return conf


OUTPUTS = [
    "app/build/outputs/apk/debug/app-debug.apk",
    "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk",
    "app/build/outputs/apk/release/app-release.apk",
]
REPORTS = "app/build/test-results"


def repo_root() -> str:
    return subprocess.check_output(["git", "rev-parse", "--show-toplevel"], text=True).strip()


def make_tar(root: str) -> bytes:
    files = subprocess.check_output(
        ["git", "ls-files", "--cached", "--others", "--exclude-standard"], cwd=root, text=True
    ).splitlines()
    # Secrets locaux ignorés par git mais nécessaires au build (clé Pixabay, clé de signature).
    for secret in ("secrets.properties", "keystore/tvhome.jks"):
        if os.path.isfile(os.path.join(root, secret)):
            files.append(secret)
    buf = io.BytesIO()
    with tarfile.open(fileobj=buf, mode="w:gz") as tar:
        for f in files:
            path = os.path.join(root, f)
            if os.path.isfile(path):
                info = tar.gettarinfo(path, arcname=f)
                if f == "gradlew":
                    info.mode = 0o755
                with open(path, "rb") as fh:
                    tar.addfile(info, fh)
    return buf.getvalue()


def run(client: paramiko.SSHClient, cmd: str) -> int:
    chan = client.get_transport().open_session()
    chan.set_combine_stderr(True)
    chan.exec_command(cmd)
    while True:
        data = chan.recv(4096)
        if not data:
            break
        sys.stdout.write(data.decode(errors="replace"))
        sys.stdout.flush()
    return chan.recv_exit_status()


def main() -> int:
    password = os.environ.get("KAIRO_SSH_PW")
    if not password:
        print("KAIRO_SSH_PW manquant", file=sys.stderr)
        return 2
    root = repo_root()
    conf = load_config(root)
    REMOTE_DIR = conf.get("dir", "kairo-build")
    ENV = (
        f"export JAVA_HOME={conf['java_home']} ANDROID_HOME={conf['android_home']} "
        f"GRADLE_USER_HOME=$HOME/{REMOTE_DIR}/.gradle-home "
        f"PATH={conf['java_home']}/bin:{conf['gradle_home']}/bin:$PATH"
    )
    client = paramiko.SSHClient()
    client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    client.connect(conf["host"], username=conf["user"], password=password, timeout=20,
                   allow_agent=False, look_for_keys=False)
    sftp = client.open_sftp()
    run(client, f"mkdir -p ~/{REMOTE_DIR}")
    with sftp.open(f"{REMOTE_DIR}/src.tar.gz", "wb") as fh:
        fh.write(make_tar(root))
    run(client, f"cd ~/{REMOTE_DIR} && tar xzf src.tar.gz && rm src.tar.gz")

    gradle_args = " ".join(sys.argv[1:]) or "--version"
    code = run(client, f"{ENV}; cd ~/{REMOTE_DIR} && "
                       f"(test -x ./gradlew && ./gradlew --console=plain {gradle_args} || "
                       f"gradle --console=plain {gradle_args})")

    # Le serveur est partagé : les secrets n'y restent pas après le build.
    run(client, f"cd ~/{REMOTE_DIR} && rm -f secrets.properties keystore/tvhome.jks")

    out_dir = os.path.join(root, "build-remote")
    os.makedirs(out_dir, exist_ok=True)
    for rel in OUTPUTS:
        try:
            sftp.stat(f"{REMOTE_DIR}/{rel}")
        except OSError:
            continue
        sftp.get(f"{REMOTE_DIR}/{rel}", os.path.join(out_dir, os.path.basename(rel)))
    run(client, f"cd ~/{REMOTE_DIR} && for f in {REPORTS}/*/TEST-*.xml; do "
                f"[ -e \"$f\" ] && grep -ho '<testsuite name=\"[^\"]*\" tests=\"[0-9]*\" skipped=\"[0-9]*\" "
                f"failures=\"[0-9]*\" errors=\"[0-9]*\"' \"$f\"; done; true")
    run(client, f"cd ~/{REMOTE_DIR} && test -d {REPORTS} && tar czf ~/{REMOTE_DIR}/reports.tgz {REPORTS} || true")
    try:
        sftp.get(f"{REMOTE_DIR}/reports.tgz", os.path.join(out_dir, "test-results.tgz"))
    except OSError:
        pass
    client.close()
    return code


if __name__ == "__main__":
    sys.exit(main())
