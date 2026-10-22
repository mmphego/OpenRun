# Repository publication settings

The source was recovered without `.git`, so there are no verifiable commits/remotes to preserve. A new initial source-import commit records this explicitly. Never overwrite an existing unrelated repository or force-push.

Preferred target: `mmphego/OpenRun`, public, default branch `main`.

Description: **Native Android running and walking companion with offline training, GPS recording and a locally cached multi-provider race catalogue.**

Topics: `android`, `java`, `running`, `walking`, `fitness`, `gps`, `offline-first`, `room`, `race-calendar`.

Enable Issues, private vulnerability reporting and Actions. Disable unused wiki/projects. Require CI before merging when repository rules are available. Review first-time fork workflow runs without granting secrets. Select the project licence after provenance review. Configure release signing as described in RELEASING.md.

After creating an empty repository with authenticated GitHub CLI:

```bash
gh repo create mmphego/OpenRun --public --source=. --remote=origin --push
```

If it already exists, inspect its files/history before adding a remote; do not run the command blindly. Set description/topics with `gh repo edit`, inspect the first CI run with `gh run list`/`gh run view`, then create the scoped starter issues from `docs/STARTER-ISSUES.md`. No remote publication success is implied by this file.
