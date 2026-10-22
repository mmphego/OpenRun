# Development signing

Gradle creates the standard Android debug identity in your local Android user directory. No keystore is distributed with the source. Separate machines and ephemeral CI runs can produce different debug certificates, so their APKs may not update each other.

Export an encrypted OpenRun backup before uninstalling an older build signed with a different certificate. Uninstalling deletes local app data. Do not use debug certificates for production releases. See [release signing](../docs/RELEASING.md) for the stable release identity.
