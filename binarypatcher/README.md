# Binary Patches

The binary patches are applied on a file-by-file basis, so not the entire Jar file is targeted, instead the
decompressed entries *in* the jar file are.

Patch files themselves use the same format as [xdelta](https://en.wikipedia.org/wiki/Xdelta), using the
[javaxdelta](https://mvnrepository.com/artifact/com.nothome/javaxdelta) library, which sadly has been
unmaintained for over 10 years.

## Patch Bundle File Format

We use LZMA2 compression to compress the overall patch bundle.

The uncompressed data is laid out as follows, using **big endian** byte order, and starts with the
following file header:

| ID                  | Data Type      | Description                                                                               |
|---------------------|----------------|-------------------------------------------------------------------------------------------|
| bundle_signature    | 16 raw bytes   | File signature. `NFPATCHBUNDLE001` in ASCII.                                              |
| bundle_entries      | 32-bit signed  | Number of entries in the bundle. Negative values are not allowed, but 0 is.               |
| bundle_target_dists | 8-bit unsigned | Bitfield that indicates the [target distributions](#target-distributions) of this bundle. |

The header is followed by the following structure for each patch, as many times as indicated by the bundle header.

### Entry Format

| ID                  | Data Type               | Description                                                                                                                                |
|---------------------|-------------------------|--------------------------------------------------------------------------------------------------------------------------------------------|
| entry_flags         | 8-bit unsigned          | [Flags for the patch](#patch-flags), unknown bits that are set must be ignored.                                                            |
| entry_target        | [string](#strings)      | The [relative path](#relative-paths) of the file targeted by this patch.                                                                   |
| entry_base_checksum | 32-bit unsigned integer | Only present if patch type is "Modify". Denotes the CRC-32 checksum of the base file being patched.                                        |
| entry_data_length   | 32-bit unsigned integer | The length of the following patch data. For entries of type "Remove File", this must be 0.                                                 |
| entry_data          | bytes                   | The patch data. For patch type "Create", this is the raw contents of the new file. For the "Modify" types, it is a patch in xdelta format. |

### Entry Flags

| Bitmask | Description                                                                        |
|---------|------------------------------------------------------------------------------------|
| 0x07    | Bitfield defining the [target distribution](#target-distributions) for this patch. |
| 0x18    | Indicates the type of patch. `00`=Create, `01`=Modify, `10`=Remove                 |

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

### Target Distributions

Patches can be built against three different types of base jars:

- The client jar, processed by the PROCESS_JAR installertools task.
- The server jar, processed by the PROCESS_JAR installertools task.
- The merged client and server jars ("joined"), processed by the PROCESS_JAR installertools task.

The header of a bundle as well as each patch in it define the base jars it applies to.
It's possible for a bundle to declare it was built against a jar while not containing any patches for it (if it was not
modified),
while an patch must not declare a distribution that was not defined in the bundle header.

| Bitmask | Description                                     |
|---------|-------------------------------------------------|
| 0x01    | The bundle contains patches for the client jar. |
| 0x02    | The bundle contains patches for the server jar. |
| 0x04    | The bundle contains patches for the joined jar. |
