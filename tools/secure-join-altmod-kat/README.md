# Secure-join AltMod KAT generator

Pinned reference: [ladnir/secure-join](https://github.com/ladnir/secure-join) commit
`1e1dddf250a0bd23dd9fc15e88480a58da2cb2a0`.

## Regenerate

```bash
git clone https://github.com/ladnir/secure-join.git /tmp/secure-join
cd /tmp/secure-join && git checkout 1e1dddf250a0bd23dd9fc15e88480a58da2cb2a0
python3 build.py --par=$(nproc)

# Copy dump_altmod_basis.cpp into frontend/ and add CMake target (see dump_altmod_basis.cpp header),
# or run the copy maintained under this directory against the built tree:
cp dump_altmod_basis.cpp /tmp/secure-join/frontend/
# ensure frontend/CMakeLists.txt has: add_executable(dump_altmod_basis dump_altmod_basis.cpp)
#                                    target_link_libraries(dump_altmod_basis secureJoin)
cmake -S /tmp/secure-join -B /tmp/secure-join/out/build/linux
cmake --build /tmp/secure-join/out/build/linux --target dump_altmod_basis

OUT=../../mpc4j-s2pc-opf/src/main/resources/haowan26/secure-join-1e1dddf
mkdir -p "$OUT"
/tmp/secure-join/out/build/linux/frontend/dump_altmod_basis "$OUT"
sha256sum "$OUT"/*.txt
```

Production Java must not build or execute this C++ tool.
