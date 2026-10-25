# Development signing

Gradle creates the standard Android debug identity in your local Android user directory. No keystore is distributed with the source. Separate machines and ephemeral CI runs can produce different debug certificates, so their APKs may not update each other.

Do not ask users to uninstall or clear app data as an update workaround. Keep their installed app intact and recover its original signing identity. Local backups are ZIP files, not automatically encrypted, and do not replace upgrade compatibility. Do not use debug certificates for production releases. See [release signing](../docs/RELEASING.md) for the stable release identity.

Debug artifacts are only for testing. GitHub Releases require the persistent distribution key and pinned certificate described in docs/RELEASING.md. They never publish random runner debug keys.
