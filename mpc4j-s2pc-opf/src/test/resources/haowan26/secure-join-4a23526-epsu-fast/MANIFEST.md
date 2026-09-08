# Hao–Wan secure-join AltMod public parameters (authoritative ePSU_fast dump)

- Primary reference: https://github.com/CryptMatrix/ePSU-from-ssPMT @ `255bf1e4055128dc3eeeae43d934b4d068cabe67`
- Dump target: `ePSU_fast/ePSU` with original `ePSU_LINK_LIBS` (`visa::volePSI`, `oc::libOTe`, `oc::cryptoTools`, OpenMP, `libsecureJoin.a`, pthread)
- Secure-join pin (from `ePSU_fast/setup.sh`): https://github.com/Th0masAndy/secure-join @ `4a23526f4b3a8432f7fb12d54b9865e95faedcf4`
- Nested pins recorded by that secure-join revision: `libOTe@d0e499206d1d4d16c6b4ca6c0e712490e0632f80`, `cryptoTools@0cf6986873e2b83966d5110398dca99172d63c20`
- Secondary observation only: `ladnir/secure-join@1e1dddf250a0bd23dd9fc15e88480a58da2cb2a0` has an identical `Prf/` tree
- **volePSI is unpinned by upstream `setup.sh`.** Observed during reproduction: see `PROVENANCE.txt` / `volepsi.UNPINNED.txt`
- Generator: `tools/secure-join-altmod-kat/dump_altmod_kat.inc` included into `SoOPPRF.cpp` (reference-only patch)
- A source: `AltModPrf::mACode`
- Observed classification: **`A_SEED_CLASS=CC`** (matches explicit `block(0xcccccccccccccccc,0xcccccccccccccccc)` seed for all 512 bases; not the zero-seed candidate)
- Byte order: little-endian `cryptoTools::block::data()` bytes

## SHA-256 digests (full files as committed)

```
f73d0d2afb43d545e005f424da480352a756e048ab1e36ba9ff399139d7e63fb  a_basis_images.txt
0b653445cc53a6bc25361c560aed3f72986b781fdc3bc4661dd514eb19ff4253  b_basis_images.txt
fb49b2e5670ce9a9407c84ac4efb74db0e8b39ae1bf4c4ae20ed1c5a48281caf  b_parity_rows.txt
cba98ecd3474b8fa4e2bbebe9544ae2d838ee0867d55e6d40b2424bc7781f806  f_fixed_key.txt
b2353c85012a7828b24f193ccc4e03e1ce13607b514d453c5773753fc3d0b327  g_basis_images.txt
3ab586b7d6b10c94da33a5dd1eaac0a4a678d03097457d9f2fe6316142b28622  g_representative.txt
```

The 512 A data rows match the historical CC full-file digest
`3667924b509aff93a3054ccc50638d955f471946e5ec1bd6b4b62c8092a40268`
(prior resource headers differed; current full-file digest is listed above).

## Fixed-key F(k,x) (CC)

```
zero      00000000000000000000000000000000
one       b00fb2e6f96bcddb418635a1c9e49bbb
x10       20b8010b9482bb781cf2f5664444b22f
all_ones  c9862a7c61b2e7672ea8a82fe7f94733
pattern   e9589976145e1275d1bab4217b8e4551
```

Regenerate with `tools/secure-join-altmod-kat/regenerate.sh` (two clean trees) and `verify.sh`.
