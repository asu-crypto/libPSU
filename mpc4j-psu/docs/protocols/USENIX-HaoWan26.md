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

The `HAO_WAN_SECURE_JOIN` Java profile matches G, A, B and the complete fixed-key `F(k,x)` bytes observed from the original `ePSU-from-ssPMT@255bf1e` `ePSU_fast` executable, linked with `secure-join@4a23526` and the dependency/toolchain/link configuration recorded in the resource manifest.

The reference A initialization is sensitive to cross-translation-unit dynamic initialization. This compatibility statement is tied to the recorded `ePSU_fast` link configuration and is not a claim that every standalone `secureJoin` executable produces identical A/F bytes.

Observed on the recorded link path: **`A_SEED_CLASS=CC`**.

Also:

* all-zero 128-bit elements are valid and supported (`G(0)=0`, `F(k,0)=0`);
* G expansion and SOW A/B must share one `F32WprfPublicParamsType` (mixed profiles rejected);
* `MPC4J_NATIVE` remains a separate non-byte-compatible profile;
* protocol OKVS/PEQT uses the documented LE→fixed-reduce repack of the same `ell` bits (not raw C++ OKVS bytes);
* `ladnir/secure-join@1e1dddf` may be mentioned only as a later commit with an identical `Prf/` subtree.

Honest union tests do **not** certify malicious security. HaoWan26 is implemented under its stated semi-honest model.

## Implementation

| | |
|--|--|
| Path | `mpc4j-psu/protocols/balanced/haowan2026/` + `mpc4j-s2pc-opf/.../haowan26/` |
| Maven artifact | `mpc4j-psu-protocol-haowan2026` |
| Classes | `HaoWan2026PsuConfig, HaoWan2026PsuServer, HaoWan2026PsuClient` |

## See also

- [OT_BASE_COST_AUDIT.md](../OT_BASE_COST_AUDIT.md)
- `tools/secure-join-altmod-kat/README.md`
- Resource MANIFEST: `mpc4j-s2pc-aby/src/main/resources/haowan26/secure-join-4a23526-epsu-fast/MANIFEST.md`
