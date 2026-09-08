# Hao–Wan secure-join AltMod public parameters

- HaoWan ePSU-from-ssPMT setup.sh pins: https://github.com/Th0masAndy/secure-join @ `4a23526f4b3a8432f7fb12d54b9865e95faedcf4`
- Vector dump tree: https://github.com/ladnir/secure-join @ `1e1dddf250a0bd23dd9fc15e88480a58da2cb2a0` (identical Prf subtree)
- Generator: `tools/secure-join-altmod-kat/dump_altmod_basis.cpp`
- A basis from `AltModPrf::mACode` (matches eval; not a fresh F3AccPermCode)
- Byte order: little-endian `cryptoTools::block::data()` bytes

## SHA-256 digests

```
73cf15e90e21affabfe544b7c3972e41a6bc0e8b4afd2235aea75614a2b7092a  a_basis_images.txt
a900a7b2a253025e1134265748454a4ed5d4abb6b3e250d5fa8c9c639b6df6b9  b_basis_images.txt
5444a9c5b59b9a652ef42e2c2b3cad960f57f52ca306a314dd71328c7acad85a  b_parity_rows.txt
84d82aeff6e675c695fcad599128c667c0a19725a2d195b18890ddbf4803a170  f_fixed_key.txt
e1b8d9694abd00178e94cb4d7ef8b9edd929c9721110d45557b0b276884c28f7  g_basis_images.txt
93760daafc4b0a494ec9815c7ebd7dad955dacae08e722cf8d15fded7650c729  g_representative.txt
```
