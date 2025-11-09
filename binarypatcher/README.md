# Binary Patches

The binary patches are applied on a file-by-file basis, so not the entire Jar file is targeted, instead the
decompressed entries *in* the jar file are.

Patch files themselves use the same format as [xdelta](https://en.wikipedia.org/wiki/Xdelta), using the
[javaxdelta](https://mvnrepository.com/artifact/com.nothome/javaxdelta) library, which sadly has been
unmaintained for over 10 years.

## Patch Bundle File Format

We use LZMA2 compression to compress the overall patch bundle.

The uncompressed data is laid out as follows, using **little endian** byte order, and starts with the
following file header:

| ID               | Data Type       | Description                                                                       |
|------------------|-----------------|-----------------------------------------------------------------------------------|
| bundle_signature | 16 raw bytes    | File signature. `NFPATCHBUNDLE001` in ASCII.                                      |
| bundle_timestamp | 64-bit unsigned | Timestamp of the files creation in milliseconds since the UTC epoch.              |
| bundle_entries   | 32-bit signed   | Number of entries in the bundle. Negative values are not allowed, but 0 is.       |
| bundle_flags     | 64-bit unsigned | [Flags for the bundle](#bundle-flags), unknown bits that are set must be ignored. |

The header is followed by the following structure for each entry, as many times as indicated by the bundle header.

### Entry Format

| ID                  | Data Type               | Description                                                                                                                                                        |
|---------------------|-------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| entry_flags         | 8-bit unsigned          | [Flags for the entry](#entry-flags), unknown bits that are set must be ignored.                                                                                    |
| entry_target        | [string](#strings)      | The [relative path](#relative-paths) of the file targeted by this entry.                                                                                           |
| entry_new_name      | [string](#strings)      | This field is only present if the entry type is "Modify and Rename", and denotes the new [relative path](#relative-paths) of the file after patching.              |
| entry_base_checksum | 32-bit unsigned integer | Only present if entry type is "Modify" or "Modify and Rename". Denotes the CRC-32 checksum of the base file being patched.                                         |
| entry_data_length   | 32-bit unsigned integer | The length of the following patch data. For entries of type "Remove File", this must be 0.                                                                         |
| entry_data          | bytes                   | The patch data. For entry type "Create", this is the raw contents of the new file. For the "Modify" and "Modify and Rename" types, it is a patch in xdelta format. |

### Bundle Flags

| Bitmask            | Description                                                       |
|--------------------|-------------------------------------------------------------------|
| 0x0000000000000001 | The bundle contains patches for the [client jar](#distributions). |
| 0x0000000000000002 | The bundle contains patches for the [server jar](#distributions). |
| 0x0000000000000004 | The bundle contains patches for the [joined jar](#distributions). |

### Entry Flags

| Bitmask | Description                                                                                |
|---------|--------------------------------------------------------------------------------------------|
| 0x01    | This patch applies to the [client jar](#distributions).                                    |
| 0x02    | This patch applies to the [server jar](#distributions).                                    |
| 0x04    | This patch applies to the [joined jar](#distributions).                                    |
| 0x18    | Indicates the type of entry. `00`=Create, `01`=Modify, `10`=Modify and Rename, `11`=Remove |

## Strings

Strings are prefixed by their length encoded as a 16-bit unsigned integer, followed by the string content,
encoded as 7-bit ASCII. The most significant bit is always unset, allowing the strings to be read as UTF-8/ISO-8859-1
safely.

Only characters from the range 0x20 to 0x7E (inclusive) are allowed.

## Relative Paths

Paths referred to by entries are expressed as follows:

- Path segments are separated by a single `/`
- Path segments are not empty
- Path segments must not be equal to `.` or `..`
- Paths only ever refer to files, never to folders
- Paths are case-sensitive

## Distributions

Patches can be built against three different types of base jars:

- The client jar, processed by the PROCESS_JAR installertools task.
- The server jar, processed by the PROCESS_JAR installertools task.
- The merged client and server jars, processed by the PROCESS_JAR installertools task.

Patches may apply to one or several of these distributions. We express this by using a bitmask.
