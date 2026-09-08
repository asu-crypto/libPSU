# Secure-join AltMod KAT generator

HaoWan `ePSU-from-ssPMT` `setup.sh` pins [Th0masAndy/secure-join](https://github.com/Th0masAndy/secure-join) at
`4a23526f4b3a8432f7fb12d54b9865e95faedcf4`.

Vectors were dumped from [ladnir/secure-join](https://github.com/ladnir/secure-join) commit
`1e1dddf250a0bd23dd9fc15e88480a58da2cb2a0`, which has an identical `Prf/` subtree.

**Important:** matrix `A` must be taken from `AltModPrf::mACode` (the static instance used by
`AltModPrf::eval`). Constructing a fresh `F3AccPermCode` with `CCBlock` does **not** match eval
under this translation unit’s static initialization order.

## Regenerate

```bash
git clone https://github.com/ladnir/secure-join.git /tmp/secure-join
cd /tmp/secure-join && git checkout 1e1dddf250a0bd23dd9fc15e88480a58da2cb2a0
python3 build.py --par=$(nproc)

cp dump_altmod_basis.cpp /tmp/secure-join/frontend/   # or this tools copy
# ensure frontend/CMakeLists.txt links dump_altmod_basis → secureJoin
cmake --build /tmp/secure-join/out/build/linux --target dump_altmod_basis

OUT=../../mpc4j-s2pc-opf/src/main/resources/haowan26/secure-join-1e1dddf
mkdir -p "$OUT"
/tmp/secure-join/out/build/linux/frontend/dump_altmod_basis "$OUT"
sha256sum "$OUT"/*.txt
```

Production Java must not build or execute this C++ tool.
