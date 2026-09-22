# Testing libPSU protocols

The test reactor checks the public `LibPsu` facade and the protocol inventory behind it:

* **Unit:** metadata completeness, uniqueness, and the explicit all-protocol inventory.
* **Integration:** registry-to-config wiring for every PSU, UPSU, and legacy PSI type.
* **Configuration coverage:** every protocol must ship a parseable `2^4` benchmark
  configuration; PSU configs are constructed through the same registry used by the CLI.
* **Privacy contracts:** output disclosure metadata must agree with one-sided/two-sided factory routing,
  leakage baselines must be explicitly labelled, and returned union data must be immutable and
  defensively copied.
* **Public facade:** `LibPsuTest` ensures the single `edu.alibaba:libpsu` entry point exposes the
  same complete protocol inventory as the internal registry.

Run from the repository root using JDK 17. Test the public facade with:

```bash
./scripts/mvn-jdk17.sh -P libpsu-fast -pl :libpsu -am \
  -Dtest=LibPsuTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Run the protocol contract suite with:

```bash
./scripts/mvn-jdk17.sh -pl :mpc4j-psu-tests -am \
  -Dtest='AllProtocol*Test' -Dsurefire.failIfNoSpecifiedTests=false test
```

These checks cover metadata, construction, configuration coverage, and output
immutability. They do not execute every cryptographic protocol or establish a
cryptographic privacy proof.

The protocol-specific cryptographic execution tests remain in `tests/src/test/java` and each
protocol module. Native FHE execution additionally requires the `libpsu-native-fhe` profile.

When adding a protocol, add its metadata and a `fair_bench_2p4.conf`. The inventory unit test is
intentionally explicit so a new protocol cannot silently enter the library without a review of
the protocol contracts and public facade coverage.
