// Reference-only AltMod KAT dump intended to be #included into ePSU_fast/src/SoOPPRF.cpp
// so the dump shares the original ePSU link topology (visa::volePSI, oc::libOTe,
// oc::cryptoTools, OpenMP, libsecureJoin.a). Do not compile into Java production.
#pragma once

#include "secure-join/Prf/AltModPrf.h"
#include "secure-join/Prf/F2LinearCode.h"
#include "secure-join/Prf/F3LinearCode.h"
#include "cryptoTools/Common/block.h"
#include "cryptoTools/Common/Matrix.h"

#include <array>
#include <cstring>
#include <fstream>
#include <iomanip>
#include <iostream>
#include <string>
#include <vector>

using namespace secJoin;
using oc::block;
using oc::u64;
using oc::u8;

namespace haowan_altmod_kat {
namespace {

void writeHex(std::ostream& o, const u8* p, size_t n) {
  for (size_t i = 0; i < n; ++i) {
    o << std::hex << std::setw(2) << std::setfill('0') << (unsigned)p[i];
  }
}

void writeBlock(std::ostream& o, const block& b) {
  writeHex(o, b.data(), 16);
}

void writeMeta(std::ostream& o, const char* name) {
  o << "# name=" << name << "\n";
  o << "# primary_reference=https://github.com/CryptMatrix/ePSU-from-ssPMT\n";
  o << "# epsu_commit=255bf1e4055128dc3eeeae43d934b4d068cabe67\n";
  o << "# secure_join=Th0masAndy/secure-join@4a23526f4b3a8432f7fb12d54b9865e95faedcf4\n";
  o << "# dump_path=ePSU_fast/ePSU (original ePSU_LINK_LIBS)\n";
  o << "# a_source=AltModPrf::mACode\n";
  o << "# generator=tools/secure-join-altmod-kat/dump_altmod_kat.inc\n";
  o << "# byte_order=little-endian block bytes as cryptoTools::block::data()\n";
}

void encodeABasis(const F3AccPermCode& code, u64 idx, std::vector<u8>& out) {
  std::vector<u8> in(AltModPrf::KeySize, 0);
  in[idx] = 1;
  out.assign(AltModPrf::MidSize, 0);
  code.encode(span<u8>(in), span<u8>(out));
}

bool sameBytes(const std::vector<u8>& a, const std::vector<u8>& b) {
  return a.size() == b.size() && std::memcmp(a.data(), b.data(), a.size()) == 0;
}

}  // namespace

// Declared for test_ePSU.cpp early-exit dump mode.
int dumpAltModKat(const char* outDirC) {
  const std::string outDir = outDirC ? outDirC : ".";

  F3AccPermCode zeroA;
  zeroA.init(AltModPrf::KeySize, AltModPrf::MidSize, 8, block(0, 0));
  F3AccPermCode ccA;
  ccA.init(
      AltModPrf::KeySize,
      AltModPrf::MidSize,
      8,
      block(0xccccccccccccccccULL, 0xccccccccccccccccULL)
  );

  const F3AccPermCode& mA = AltModPrf::mACode;
  bool matchesZero = true;
  bool matchesCc = true;
  for (u64 i = 0; i < AltModPrf::KeySize; ++i) {
    std::vector<u8> obs, zOut, cOut;
    encodeABasis(mA, i, obs);
    encodeABasis(zeroA, i, zOut);
    encodeABasis(ccA, i, cOut);
    const bool eqZ = sameBytes(obs, zOut);
    const bool eqC = sameBytes(obs, cOut);
    if (!eqZ && !eqC) {
      std::cerr << "A_SEED_CLASS=UNKNOWN first_diff_basis=" << i << "\n";
      return 2;
    }
    matchesZero = matchesZero && eqZ;
    matchesCc = matchesCc && eqC;
  }

  std::string seedClass;
  if (matchesZero && !matchesCc) {
    seedClass = "ZERO";
  } else if (matchesCc && !matchesZero) {
    seedClass = "CC";
  } else if (matchesZero && matchesCc) {
    // Degenerate: zero-seed and CC-seed codes identical (unexpected).
    seedClass = "ZERO";
    std::cerr << "warning: ZERO and CC A codes are identical for all 512 bases\n";
  } else {
    std::cerr << "A_SEED_CLASS=MIXED\n";
    return 3;
  }
  {
    std::ofstream o(outDir + "/A_SEED_CLASS.txt");
    o << "A_SEED_CLASS=" << seedClass << "\n";
  }
  std::cerr << "A_SEED_CLASS=" << seedClass << "\n";

  // ---- G basis via AltModPrf::mGCode (systematic x || G0||G1||G2) ----
  {
    std::ofstream o(outDir + "/g_basis_images.txt");
    writeMeta(o, "G_basis");
    o << "# dims: 128 inputs x 512 output bits\n";
    o << "# format: index || 4x16-byte blocks (systematic x || G0(x)||G1(x)||G2(x))\n";
    for (u64 i = 0; i < 128; ++i) {
      u8 bytes[16] = {};
      bytes[i / 8] = (u8)(1u << (i % 8));
      block x;
      std::memcpy(x.data(), bytes, 16);
      std::array<block, 4> X;
      X[0] = x;
      for (u64 j = 0; j < 3; ++j) {
        AltModPrf::mGCode[j].encode(x, X[j + 1]);
      }
      o << std::dec << i << " ";
      for (auto& b : X) {
        writeBlock(o, b);
      }
      o << "\n";
    }
  }
  {
    std::ofstream o(outDir + "/g_representative.txt");
    writeMeta(o, "G_rep");
    struct Item {
      const char* name;
      block x;
    } xs[] = {
        {"zero", block(0, 0)},
        {"one", block(0, 1)},
        {"all_ones", block(~0ull, ~0ull)},
        {"alternating", block(0xAAAAAAAAAAAAAAAAULL, 0x5555555555555555ULL)}};
    for (auto& it : xs) {
      std::array<block, 4> X;
      X[0] = it.x;
      for (u64 j = 0; j < 3; ++j) {
        AltModPrf::mGCode[j].encode(it.x, X[j + 1]);
      }
      o << it.name << " ";
      for (auto& b : X) {
        writeBlock(o, b);
      }
      o << "\n";
    }
  }

  // ---- A basis from AltModPrf::mACode ----
  {
    std::ofstream o(outDir + "/a_basis_images.txt");
    writeMeta(o, "A_basis");
    o << "# dims: 512 F3 inputs x 256 F3 outputs\n";
    o << "# format: index then 256 bytes in {0,1,2}\n";
    o << "# A_SEED_CLASS=" << seedClass << "\n";
    for (u64 i = 0; i < AltModPrf::KeySize; ++i) {
      std::vector<u8> out;
      encodeABasis(mA, i, out);
      o << std::dec << i << " ";
      writeHex(o, out.data(), out.size());
      o << "\n";
    }
  }

  // ---- B basis via systematic definition using AltModPrf::mB / mBCode ----
  {
    std::ofstream o(outDir + "/b_basis_images.txt");
    writeMeta(o, "B_basis");
    o << "# dims: 256 binary inputs x 128 output bits\n";
    o << "# B*v = v[0..127] XOR mBCode.encode(v[128..255])\n";
    for (u64 i = 0; i < AltModPrf::MidSize; ++i) {
      block yy = oc::ZeroBlock, tt = oc::ZeroBlock;
      if (i < 128) {
        u8 bytes[16] = {};
        bytes[i / 8] = (u8)(1u << (i % 8));
        std::memcpy(yy.data(), bytes, 16);
      } else {
        u64 j = i - 128;
        u8 bytes[16] = {};
        bytes[j / 8] = (u8)(1u << (j % 8));
        std::memcpy(tt.data(), bytes, 16);
        AltModPrf::mBCode.encode(tt, tt);
      }
      block y = yy ^ tt;
      o << std::dec << i << " ";
      writeBlock(o, y);
      o << "\n";
    }
  }
  {
    std::ofstream o(outDir + "/b_parity_rows.txt");
    writeMeta(o, "B_parity");
    o << "# 128 rows x 128 bits: AltModPrf::mB[i]\n";
    for (u64 i = 0; i < 128; ++i) {
      o << std::dec << i << " ";
      writeBlock(o, AltModPrf::mB[i]);
      o << "\n";
    }
  }

  // ---- Complete F(k,x) via AltModPrf::eval ----
  {
    std::ofstream o(outDir + "/f_fixed_key.txt");
    writeMeta(o, "F_kat");
    o << "# A_SEED_CLASS=" << seedClass << "\n";
    AltModPrf::KeyType key{};
    for (u64 i = 0; i < key.size(); ++i) {
      key[i] = block(i + 1, i + 2);
    }
    AltModPrf F(key);
    F.mInputExpansionMode = AltModPrfExpansionMode::Linear;
    struct Item {
      const char* name;
      block x;
    } xs[] = {
        {"zero", block(0, 0)},
        {"one", block(0, 1)},
        {"x10", block(1, 0)},
        {"all_ones", block(~0ull, ~0ull)},
        {"pattern", block(0x0123456789abcdefULL, 0xfedcba9876543210ULL)}};
    for (auto& it : xs) {
      block y = F.eval(it.x);
      o << it.name << " x=";
      writeBlock(o, it.x);
      o << " y=";
      writeBlock(o, y);
      o << std::endl;
    }
  }

  std::cerr << "Wrote AltMod KAT vectors to " << outDir << "\n";
  return 0;
}

}  // namespace haowan_altmod_kat

// Global linkage for test_ePSU.cpp (using-declaration alone does not emit a
// distinct global definition for the linker).
int dumpAltModKat(const char* outDirC) {
  return haowan_altmod_kat::dumpAltModKat(outDirC);
}
