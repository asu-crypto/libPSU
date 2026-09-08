# Secure-join / ePSU_fast AltMod KAT generator

## Authoritative source

Vectors must be dumped from the original HaoWan **`ePSU_fast/ePSU`** executable:

* https://github.com/CryptMatrix/ePSU-from-ssPMT @ `255bf1e4055128dc3eeeae43d934b4d068cabe67`
* `ePSU_fast/setup.sh` secure-join pin: `Th0masAndy/secure-join@4a23526f4b3a8432f7fb12d54b9865e95faedcf4`
* Nested: `libOTe@d0e499206d1d4d16c6b4ca6c0e712490e0632f80`, `cryptoTools@0cf6986873e2b83966d5110398dca99172d63c20`
* **volePSI is not pinned by upstream `setup.sh`** — record the observed commit in provenance.

A standalone `secureJoin` frontend (including `ladnir/secure-join@1e1dddf`) is **not** authoritative: `AltModPrf::mACode` is sensitive to cross-TU dynamic initialization of `oc::CCBlock`.

## Regenerate (two clean trees)

From the libPSU repository root:

```bash
tools/secure-join-altmod-kat/regenerate.sh --keep-work \
  --out tools/secure-join-altmod-kat/out
```

The script:

1. Clones ePSU-from-ssPMT twice into independent workdirs
2. Copies `dump_altmod_kat.inc` into `ePSU_fast/include/` and instruments `SoOPPRF.cpp` + `test_ePSU.cpp`
3. Builds thirdparty + the original `ePSU` target (`ePSU_LINK_LIBS` unchanged)
4. Runs `HAOWAN_ALT_MOD_KAT_OUT=… ./ePSU` (no network)
5. Classifies `AltModPrf::mACode` against explicit ZERO and CC seeds (`A_SEED_CLASS=…`)
6. Requires byte-identical outputs between the two clean dumps

Then copy `out/canonical/*` into:

```text
mpc4j-*/src/*/resources/haowan26/secure-join-4a23526-epsu-fast/
```

and refresh `SHA256SUMS` / `MANIFEST.md`.

## Verify committed resources

```bash
tools/secure-join-altmod-kat/verify.sh
sha256sum -c tools/secure-join-altmod-kat/SHA256SUMS
```

Production Java must not build or execute this C++ tooling.
