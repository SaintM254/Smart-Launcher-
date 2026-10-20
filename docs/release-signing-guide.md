# GitHub permission guide for the Smart Launcher release

## You do not need to code or send me a password

The Android app and its tests are passing. The release key is already stored safely outside the repository. I cannot complete the signed release because GitHub is denying this session permission to manage repository Actions secrets (HTTP 403).

**Do not send me a personal access token, password, key file, or anything described as full-access.** You do not need to make a token or type any secret values. I already have the release key; I need the GitHub connection to allow this one repository's Actions secrets to be managed.

## Check the GitHub app access — click by click

1. Open [GitHub's Installed GitHub Apps page](https://github.com/settings/installations) and sign in to the account that owns or administers `SaintM254/Smart-Launcher-`.
2. Look for the GitHub app used by Arena (it may be named **Arena**). If you see it, click **Configure**.
3. Check its repository access. Make sure **SaintM254/Smart-Launcher-** is included. If the page offers **All repositories** or **Only select repositories**, selecting just **Smart-Launcher-** is enough; do not grant access to everything.
4. Look for a permission named **Secrets** under repository permissions. The access needed is **Read and write**.
5. If GitHub shows a **Review request** or **Review permissions** button for the Secrets permission, open it and approve that request for this repository.

### If there is no Arena app, or no Secrets permission

Stop there—this is not something you did wrong. If the app is not listed, or GitHub does not offer a **Secrets — Read and write** permission to approve, there is no switch you can turn on in the repository settings. The Arena GitHub app would have to request that permission first. **Do not create a personal access token as a workaround.**

If you reach this point, just reply with one of these descriptions:

- “I found Arena and Secrets says Read and write.”
- “I found Arena, but there is no Secrets permission.”
- “I do not see Arena on the Installed GitHub Apps page.”

A screenshot of that permissions page is also okay, as long as it does not show any passwords or tokens. I can use that information to determine the next safe step. You do not need to copy any secret value.

## What happens after access is available

Once GitHub allows this session to manage the Smart Launcher repository's Actions secrets, I will:

1. Save the existing release signing values directly to GitHub without displaying them in chat.
2. Run and verify the signed release build.
3. Create the `v1.0` tag on the verified `arena/40479970-smart-launcher` branch commit and publish the GitHub Release with `SmartLauncher-v1.0.apk`.

You do not need to merge the branch, run terminal commands, create a key, or upload files. Until the Secrets permission is available, I will not create a tag or publish an unsigned APK.
