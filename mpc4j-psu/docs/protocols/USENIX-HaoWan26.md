# USENIX:HaoWan26

Hao–Wan ePSU-fast (Figure 17): F32 shared-output OPRF + OKVS + PEQT + ssOTd.

## At a glance

| | |
|--|--|
| **Protocol ID** | `USENIX:HaoWan26` |
| **Functionality** | Balanced ePSU (one-sided client union) |
| **Security** | Semi-honest |
| **Output** | Client learns union; server finishes |
| **Factory** | `PsuFactory` |
| **Driver** | `PsuMain` |
| **Config key** | `psu_pto_name = USENIX:HaoWan26` |
| **OT / cost** | INCLUDED_INIT (F32 SOW + CotFactory in ssOTd) |
| **Init order** | `server.init(maxServer, maxClient)`; `client.init(maxClient, maxServer)` |

## Pipeline (Figure 17)

1. Paper client/R = F32 SOW **sender** (key holder); paper server/S = F32 SOW **receiver** (query holder).
2. Shares satisfy `[t]_0 XOR [t]_1 = F(x)` (no clear T-share wire).
3. Client OKVS stores truncated `F(y)`; server compares `[t]_0 XOR Decode(OKVS,x)` to `[t]_1`.
4. ssOTd reveals a server item only when reconstructed membership is zero.
5. Finish ACK/confirm barrier is mandatory.

## Secure-join AltMod parameters

HaoWan `ePSU-from-ssPMT` `setup.sh` pins `Th0masAndy/secure-join` @ `4a23526f4b3a8432f7fb12d54b9865e95faedcf4`.
Vectors under `haowan26/secure-join-1e1dddf/` were dumped from `ladnir/secure-join` @ `1e1dddf` (identical `Prf` subtree). Matrix **A** is taken from `AltModPrf::mACode` (the static instance used by `eval`).

**The secure-join profile matches the pinned reference for G, A, B, complete fixed-key `F(k,x)` evaluation, LE byte ordering, and truncation prefix (`memcpy` of the first `ceil(ell/8)` LE bytes) under the committed KAT suite.**

Protocol OKVS/PEQT store those same `ell` bits after a GF(2)-linear LE→MPC4J fixed-reduce repack (last-byte mask as in HaoWan PEQT, then byte reverse) so DOKVS accepts `isFixedReduceByteArray`. That repack is not a second cryptographic truncation.

Also:

* all-zero 128-bit elements are valid and supported (`G(0)=0`, `F(k,0)=0`);
* G expansion and SOW A/B must share one `F32WprfPublicParamsType` (mixed profiles rejected);
* `MPC4J_NATIVE` remains a separate non-byte-compatible profile.

Honest union tests do **not** certify malicious security.

## Implementation

| | |
|--|--|
| Path | `mpc4j-psu/protocols/balanced/haowan2026/` + `mpc4j-s2pc-opf/.../haowan26/` |
| Maven artifact | `mpc4j-psu-protocol-haowan2026` |
| Classes | `HaoWan2026PsuConfig, HaoWan2026PsuServer, HaoWan2026PsuClient` |

## See also

- [OT_BASE_COST_AUDIT.md](../OT_BASE_COST_AUDIT.md)
- `tools/secure-join-altmod-kat/README.md`
- Resource MANIFEST: `mpc4j-s2pc-opf/src/main/resources/haowan26/secure-join-1e1dddf/MANIFEST.md`
