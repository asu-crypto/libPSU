# Using libPSU as a Java library

Use **`edu.alibaba:libpsu`** and **`edu.alibaba.libpsu.LibPsu`** for application code.
This facade covers balanced PSU, two-sided PSU, offline/online PSU, PSI, UPSU,
and BA12. The `mpc4j-psu/` directory contains the implementation of this library.

## Maven dependency

Build and install from the repository root with JDK 17:

```bash
./scripts/mvn-jdk17.sh -pl :libpsu -am install -DskipTests
```

Then add the single application dependency:

```xml
<dependency>
    <groupId>edu.alibaba</groupId>
    <artifactId>libpsu</artifactId>
    <version>1.1.5</version>
</dependency>
```

The dependency includes the factory, protocol implementations, and shared Java
layers transitively. Native libraries must still be built and made available as
described in the [root README](../../README.md#requirements).

Projects that manage versions centrally may also import the existing BOM:

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

## Protocol selection

Use the facade for metadata, config construction, and runtime party construction:

```java
import edu.alibaba.libpsu.LibPsu;
import edu.alibaba.libpsu.api.ProtocolFunctionality;
import edu.alibaba.libpsu.api.ProtocolInfo;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuConfig;

ProtocolInfo info = LibPsu.findProtocol("AC:KRTW19", ProtocolFunctionality.PSU)
    .orElseThrow();
PsuConfig config = LibPsu.createPsuConfig("AC:KRTW19");
```

`LibPsu.protocols()` lists registered PSU, UPSU, and BA12 benchmark metadata;
`protocols(functionality)` filters that inventory by family. Use the functionality
when looking up names shared by PSU and UPSU. Legacy PSI remains available through
`createPsiConfig` and has no separate metadata entries. `ProtocolInfo.isDefaultEnabled()`
identifies stable, benchmarked entries; experimental variants can still be selected explicitly.

| Family | Config methods | Runtime methods |
|--------|----------------|-----------------|
| Balanced PSU | `createPsuConfig` | `createServer`, `createClient` |
| Two-sided PSU | `createPsuConfig`, `createDefaultTwoSidedConfig` | `createTwoSidedServer`, `createTwoSidedClient` |
| Offline/online PSU | `createOoPsuConfig` | `createOoPsuServer`, `createOoPsuClient` |
| PSI | `createPsiConfig` | `createPsiServer`, `createPsiClient` |
| UPSU | `createUpsuConfig` | `createUpsuSender`, `createUpsuReceiver` |
| BA12 | `createBa12Config(properties, elementByteLength)` | `createBa12Party` |

PSU, PSI, and UPSU config methods accept a protocol name, a name plus `Properties`,
or the existing properties alone. The properties-only overloads read
`psu_pto_name`, `psi_pto_name`, or `upsu_pto_name` respectively. Offline/online PSU
also accepts the existing PSU properties. BA12 exposes `readBa12Operation` and
`readBa12ProtocolName` for its operation and variant selection.

For an existing RPC connection, construct the parties with
`LibPsu.createServer(serverRpc, clientParty, config)` and
`LibPsu.createClient(clientRpc, serverParty, config)`. Initialize and run them
using the returned interfaces. Use `usesTwoSidedPublicFactory(config)` to route
protocols that require the two-sided interfaces, and `capabilitiesOf(config)`
to inspect disclosure and execution capabilities.

The returned RPC, config, party, and output types keep their established
`edu.alibaba.mpc4j` package names. BA12 returns secret-shared set-operation output;
its interface differs from ordinary PSU union output.

## Inputs and results

`PsuInput`, `PsuElement`, and `PsuResult` provide immutable application value
types with defensive copying. They do not replace protocol method parameters:
the returned runtime interfaces retain their existing input and result types.
For example, `PsuInput.toByteBufferSet()` converts normalized input to a set of
byte buffers. These value types support empty or singleton sets, while individual
protocols may impose stricter limits through `ProtocolInfo.getMinimumInputSetSize()`.

## Compatibility and protocol extensions

Existing `mpc4j-psu-*` Maven coordinates and protocol package names remain
available. `PsuLibrary` is retained as a deprecated compatibility entry point;
new callers should use `LibPsu`.

The `libpsu-api`, `libpsu-core`, `libpsu-spi`, and `libpsu-factory` modules remain
internal extension layers. Protocol authors may depend on them directly, but
application consumers only need `libpsu`.

## Adding a protocol

Follow [ADD_NEW_PROTOCOL.md](ADD_NEW_PROTOCOL.md). New protocol modules should register metadata first, export a descriptor through the factory layer, and keep reusable primitives in `libpsu-core` or `plugins/*` instead of copying helpers into the protocol package.

## Test profiles

Run the public facade tests with the fast profile:

```bash
./scripts/mvn-jdk17.sh -P libpsu-fast -pl :libpsu -am \
    -Dtest=LibPsuTest -Dsurefire.failIfNoSpecifiedTests=false test
```

See [TESTING.md](TESTING.md) for protocol contract checks and native execution tests.
