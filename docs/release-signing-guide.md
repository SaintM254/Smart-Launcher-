# Release signing and GitHub Actions guide

This guide explains how to make the existing `v*` release workflow build and publish the signed Smart Launcher APK. **Do not push the `v1.0` tag until the signing secrets are configured**: pushing a matching tag starts the release workflow automatically.

## 1. Keep the signing identity safe

The release key and Gradle properties are kept outside Git:

- `~/.config/smart-launcher/SmartLauncher-release.p12`
- `~/.config/smart-launcher/signing.properties`

Keep both files together, private, and backed up to at least two encrypted offline locations. Android requires the same package ID and signing key for in-place upgrades. Never commit, upload as a release asset, or paste either file or any secret value into an issue, chat, or public terminal log.

On the machine that holds the files, you can reapply the intended permissions with:

```sh
chmod 700 "$HOME/.config/smart-launcher"
chmod 600 "$HOME/.config/smart-launcher/SmartLauncher-release.p12" \
  "$HOME/.config/smart-launcher/signing.properties"
```

## 2. Grant access to repository Actions secrets

The release workflow needs four **repository Actions secrets**. The GitHub connection used for this session currently returns HTTP 403 when listing repository secrets, so the agent cannot configure them until GitHub is reconnected with permission to manage Actions secrets.

In Arena, reconnect GitHub and grant the Smart Launcher repository Actions-secrets management permission. Alternatively, an administrator of `SaintM254/Smart-Launcher-` can add the secrets in GitHub directly:

1. Open the repository's **Settings → Secrets and variables → Actions**.
2. Under **Repository secrets**, choose **New repository secret**.
3. Add the four names in the table below. Do not use environment secrets; the workflow reads repository-level secrets.

| Secret name | Value |
| --- | --- |
| `SMART_LAUNCHER_KEYSTORE_BASE64` | Base64 encoding of `SmartLauncher-release.p12` as one uninterrupted line. |
| `SMART_LAUNCHER_STORE_PASSWORD` | `storePassword` from the local `signing.properties`. |
| `SMART_LAUNCHER_KEY_ALIAS` | `keyAlias` from that file (currently `smart-launcher`). |
| `SMART_LAUNCHER_KEY_PASSWORD` | `keyPassword` from that file. |

### Preferred setup from a Linux/macOS terminal

The GitHub CLI sends the values directly to GitHub without printing them. First make sure `gh` is authenticated to the correct account and has permission to manage this repository's secrets:

```sh
gh auth status
gh secret list --repo SaintM254/Smart-Launcher-
```

`gh secret list` shows secret **names only**, never their values. Then run this Bash snippet on the machine containing the signing files. It reads the passwords from the protected properties file and streams each value to `gh secret set`; it does not put passwords in shell history or print them. Do not enable shell tracing (`set -x`).

```bash
set -euo pipefail
repo='SaintM254/Smart-Launcher-'
config="$HOME/.config/smart-launcher/signing.properties"

read_property() {
  awk -v key="$1" '
    index($0, "=") > 0 && substr($0, 1, index($0, "=") - 1) == key {
      print substr($0, index($0, "=") + 1)
      exit
    }
  ' "$config"
}

test -s "$config"
store_file="$(read_property storeFile)"
test -s "$store_file"

# GNU/Linux base64: -w0 prevents line wrapping.
base64 -w0 "$store_file" | gh secret set SMART_LAUNCHER_KEYSTORE_BASE64 --repo "$repo"
read_property storePassword | gh secret set SMART_LAUNCHER_STORE_PASSWORD --repo "$repo"
read_property keyAlias | gh secret set SMART_LAUNCHER_KEY_ALIAS --repo "$repo"
read_property keyPassword | gh secret set SMART_LAUNCHER_KEY_PASSWORD --repo "$repo"

# Confirm names only; GitHub never returns secret values.
gh secret list --repo "$repo"
```

On macOS, replace the base64 line with:

```sh
base64 "$store_file" | tr -d '\n' | gh secret set SMART_LAUNCHER_KEYSTORE_BASE64 --repo "$repo"
```

If `gh secret list` or `gh secret set` returns HTTP 403, stop there. Reconnect GitHub in Arena with the repository-secrets permission, or use the GitHub Settings page above while signed in as a repository administrator. Do not work around the error by committing the key or putting it in workflow YAML.

## 3. Check the workflow before tagging

The Android CI workflow must be green on the exact commit you intend to release. It runs lint, unit tests, and a debug build. Check the Actions page or use:

```sh
gh run list --repo SaintM254/Smart-Launcher- --branch arena/40479970-smart-launcher
```

The release workflow at `.github/workflows/release.yml` runs on tags matching `v*`. It checks that all four secrets are present, reconstructs the PKCS#12 file in the runner's temporary directory, runs lint and tests, and builds the minified/resource-shrunk release APK. It then creates a GitHub Release with the release notes and attaches `SmartLauncher-v1.0.apk`.

The workflow uses GitHub's automatic `GITHUB_TOKEN` for publishing; **do not create a fifth secret for it**.

## 4. Publish `v1.0`

After the secrets are in place and the intended branch commit has passing CI, create the tag at that commit. This does not require merging the branch:

```sh
git checkout arena/40479970-smart-launcher
git pull --ff-only origin arena/40479970-smart-launcher
git status --short
git tag -a v1.0 -m "Smart Launcher v1.0"
git push origin v1.0
```

Only run the tag commands once the working tree is clean and the secret names are visible in `gh secret list`. Pushing `v1.0` starts the signed-release workflow; when it succeeds, the workflow creates the `v1.0` GitHub Release automatically. Watch the run in **Actions → Android release**. If the run fails, fix the reported cause before publishing; do not attach an unsigned APK as a substitute.

## 5. Download and verify the APK

Download `SmartLauncher-v1.0.apk` from the resulting GitHub Release. If Android SDK Build Tools 36 are installed locally, verify its APK signature with:

```sh
sdk_root="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
test -n "$sdk_root" || { echo "Set ANDROID_HOME or ANDROID_SDK_ROOT first." >&2; exit 1; }
"$sdk_root/build-tools/36.0.0/apksigner" verify --verbose SmartLauncher-v1.0.apk
```

Keep the original PKCS#12 file, its passwords, and a secure backup for every future update. The application ID is `com.saintm.smartlauncher`; preserving both it and the same signing identity is what allows Android to install future releases over this one.
