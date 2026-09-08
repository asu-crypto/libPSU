# Hao–Wan secure-join AltMod public parameters

- Reference: https://github.com/ladnir/secure-join
- Commit: `1e1dddf250a0bd23dd9fc15e88480a58da2cb2a0`
- Generator: `tools/secure-join-altmod-kat/dump_altmod_basis.cpp`
- Byte order: little-endian `cryptoTools::block::data()` bytes

## SHA-256 digests

```
3667924b509aff93a3054ccc50638d955f471946e5ec1bd6b4b62c8092a40268  a_basis_images.txt
a900a7b2a253025e1134265748454a4ed5d4abb6b3e250d5fa8c9c639b6df6b9  b_basis_images.txt
5444a9c5b59b9a652ef42e2c2b3cad960f57f52ca306a314dd71328c7acad85a  b_parity_rows.txt
84d82aeff6e675c695fcad599128c667c0a19725a2d195b18890ddbf4803a170  f_fixed_key.txt
e1b8d9694abd00178e94cb4d7ef8b9edd929c9721110d45557b0b276884c28f7  g_basis_images.txt
93760daafc4b0a494ec9815c7ebd7dad955dacae08e722cf8d15fded7650c729  g_representative.txt
```

## Dimensions

| Object | Meaning |
|--------|---------|
| G | 128-bit → 512-bit systematic F2 expansion (`x \|\| G0(x)\|\|G1(x)\|\|G2(x)`) |
| A | F3AccPermCode 512→256 (`p=8`, seed `CCBlock`) basis images |
| B | Systematic 256→128; parity half in `b_parity_rows.txt` |
| F | Test-only fixed key KATs (`f_fixed_key.txt`); not for production keys |
