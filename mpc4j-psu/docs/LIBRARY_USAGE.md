# Using libPSU as a Java library

This reactor is being migrated from benchmark-oriented MPC4J modules into a reusable PSU library. The supported boundary for new application code is:

- `libpsu-api`: stable metadata and immutable caller-facing value types.
- `libpsu-core`: shared validation, set-element normalization, and reusable helpers.
- `libpsu-spi`: protocol interfaces and provider descriptors, with legacy MPC4J package names kept as compatibility shims.
- `libpsu-factory`: descriptor lookup, config parsing, and compatibility runtime construction.

## Maven dependency management

Import the BOM, then depend on the library modules and protocol modules you need:

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>edu.alibaba</groupId>
            <artifactId>mpc4j-psu-bom</artifactId>
            <version>1.1.5</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

Typical balanced-PSU consumers need `libpsu-api`, `libpsu-factory`, and one or more `mpc4j-psu-protocol-*` modules. Existing RPC execution still uses the compatibility interfaces in `edu.alibaba.mpc4j.s2pc.pso.psu`.

## Protocol selection

Use `ProtocolMetadataRegistry` when presenting or filtering available protocols, and `ProtocolRegistry` when constructing runtime configs:

```java
ProtocolInfo info = ProtocolMetadataRegistry.findByName("EUROCRYPT:PuGaoTri26").orElseThrow();
PsuConfig config = ProtocolRegistry.createPsuConfig("EUROCRYPT:PuGaoTri26", new Properties());
```

`ProtocolInfo.isDefaultEnabled()` excludes experimental or disabled protocols from default selection. Experimental variants can still be selected explicitly by name.

## Inputs and results

Use `PsuInput`, `PsuElement`, and `PsuResult` at application boundaries. They defensively copy byte arrays, deduplicate by raw byte content, and support empty or singleton sets at the library API level. Individual protocols may declare stricter limits through `ProtocolInfo.getMinimumInputSetSize()`.

## Adding a protocol

Follow [ADD_NEW_PROTOCOL.md](ADD_NEW_PROTOCOL.md). New protocol modules should register metadata first, export a descriptor through the factory layer, and keep reusable primitives in `libpsu-core` or `plugins/*` instead of copying helpers into the protocol package.

## Test profiles

Use the fast library gate while developing API, core, SPI, or factory code:

```bash
mvn -P libpsu-fast -pl mpc4j-psu/libpsu-api,mpc4j-psu/libpsu-core,mpc4j-psu/libpsu-spi,mpc4j-psu/libpsu-factory -am test
```

Use `libpsu-integration` for the broader protocol test reactor and `libpsu-bench` for explicit benchmark-style tests.
