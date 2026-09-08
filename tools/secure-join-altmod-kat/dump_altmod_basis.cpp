// Legacy standalone secureJoin frontend — NOT authoritative for HaoWan A/F vectors.
//
// Use tools/secure-join-altmod-kat/regenerate.sh instead. That instruments the
// original ePSU_fast/ePSU target (dump_altmod_kat.inc via SoOPPRF.cpp) so AltMod
// static initialization matches the Hao–Wan link order.
//
// This translation unit is intentionally not built by production or by
// regenerate.sh.
#error "Do not build dump_altmod_basis.cpp; use regenerate.sh + dump_altmod_kat.inc via ePSU_fast/ePSU"
