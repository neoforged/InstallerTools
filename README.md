# Installer Tools for NeoForge

This repository contains various tools related to the installation of [NeoForge](https://github.com/neoforged/NeoForge).

The tools contained here are referenced by the installer profile created during the NeoForge build and then run
by [LegacyInstaller](https://github.com/neoforged/LegacyInstaller), or sometimes by third-party Minecraft launchers
directly.

## Overview

### Tools in the main artifact

| Tool                  | Description                                                                                                                               |
|-----------------------|-------------------------------------------------------------------------------------------------------------------------------------------|
| `MCP_DATA`            | Extracts data from a NeoForm archive.                                                                                                     |
| `CREATE_DIR`          | Creates a directory.                                                                                                                      |
| `CREATE_PARENTS`      | Given a path to a file, creates all missing parent directories.                                                                           |
| `SRG_TO_MCP`          | Applies a CSV based MCP mapping file to a JAR file that has already been remapped to SRG names before.                                    |
| `EXTRACT_INHERITANCE` | Collect information about the inheritance hierarchy of classes in a given Jar file in a JSON file.                                        |
| `CHAIN_MAPPINGS`      | Combines two mapping files that map from namespaces `A` to `B` and `B` to `C` into a new mapping file that maps from `A` to `C` directly. |
| `MERGE_MAPPINGS`      | Merges SRG parameter name mappings into Mojangs official mapping file.                                                                    |
| `DOWNLOAD_MOJMAPS`    | Downlods Mojangs official mappings file.                                                                                                  |
| `EXTRACT_FILES`       | Extracts files from Zip or Jar files.                                                                                                     |                                                                                                    
| `BUNDLER_EXTRACT`     | Used to extract the nested Minecraft server jar introduced in Minecraft 1.18.                                                             |

### Tools in separate artifacts

| Tool          | Description                                                                                                                                              |
|---------------|----------------------------------------------------------------------------------------------------------------------------------------------------------|
| binarypatcher | A wrapper around [javaxdelta](https://javaxdelta.sourceforge.net/) to apply our GDIFF patches to Minecraft class files.                                  |
| cli-utils     | Shared classes used by the other tools. In particular this includes the progress reporting code used by tools to report their progress to the installer. |
| jarsplitter   | Takes Minecraft jars and splits them into a separate jar files for classes and everything else.                                                          |
| problems-api  | Shared API used by various tools throughout our ecosystem to encode reports about warnings and errors as JSON files.                                     |
| zipinject     | Injects static content into ZIP files.                                                                                                                   |

## Java Version

All tools in this project are compatible with Java 8, since that is the minimum version supported by the installer.

## Download

End-users do not have to use these tools directly, they're automatically downloaded during the installation.
While some individual tools may be useful beyond the scope of the NeoForged project, there is no stable API to use,
and no continued support guarantees are made.

For developers, the tools are available
on [Maven Central](https://central.sonatype.com/namespace/net.neoforged.installertools),
and from [our Maven](https://maven.neoforged.net/).

You can find the latest version on the [project listing](https://projects.neoforged.net/neoforged/installertools).

## License

This project is licensed under the [LGPL v2.1](./LICENSE).
