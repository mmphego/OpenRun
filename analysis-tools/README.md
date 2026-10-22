# Offline APK inspection

Use REA on a host with its documented prerequisites:

```sh
REA_JADX_MCP_JAR=/absolute/path/jadx-headless-mcp-0.7.1-all.jar \
  npx rea-agents@6.5.0 inspect-android-package '/absolute/path/Runna v8.57.0.apk'
```

This sandbox's procps ownership check prevented the REA workflow from launching. The direct fallback used these wrappers:

```sh
java -cp /absolute/path/jadx-headless-mcp-0.7.1-all.jar Inspect.java '/absolute/path/Runna v8.57.0.apk'
python3 extract_metadata.py '/absolute/path/Runna v8.57.0.apk' > apk-evidence.json
```

The Python metadata wrapper requires the separately installed `hermes-dec==0.1.8`; it is not part of the app. REA, JADX and hermes-dec are analysis dependencies, not app dependencies. The input APK is not included. Verify upstream downloads and follow REA's Android guide for the pinned JAR and JDK prerequisites.
