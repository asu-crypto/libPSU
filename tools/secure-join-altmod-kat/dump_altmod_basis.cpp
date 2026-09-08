// Dump AltMod G/A/B basis images from pinned ladnir/secure-join @ 1e1dddf.
// Avoids AltModPrf.h (pulls CorGenerator); mirrors AltModPrf.cpp static init.
#include "secure-join/Prf/AltModPrf.h"
#include "secure-join/Prf/F2LinearCode.h"
#include "secure-join/Prf/F3LinearCode.h"
#include "cryptoTools/Crypto/PRNG.h"
#include "cryptoTools/Common/Matrix.h"
#include "cryptoTools/Common/block.h"
#include <array>
#include <fstream>
#include <iomanip>
#include <iostream>
#include <vector>
#include <cstring>

using namespace secJoin;
using oc::block;
using oc::u64;
using oc::u8;

static constexpr u64 KeySize = 512;
static constexpr u64 MidSize = 256;
static constexpr u64 OutSize = 128;

static void writeHex(std::ostream& o, const u8* p, size_t n) {
  for (size_t i = 0; i < n; ++i)
    o << std::hex << std::setw(2) << std::setfill('0') << (unsigned)p[i];
}
static void writeBlock(std::ostream& o, const block& b) {
  writeHex(o, b.data(), 16);
}
static void writeMeta(std::ostream& o, const char* name) {
  o << "# name=" << name << "\n";
  o << "# reference=https://github.com/ladnir/secure-join\n";
  o << "# commit=1e1dddf250a0bd23dd9fc15e88480a58da2cb2a0\n";
  o << "# generator=tools/secure-join-altmod-kat/dump_altmod_basis.cpp\n";
  o << "# byte_order=little-endian block bytes as cryptoTools::block::data()\n";
}

int main(int argc, char** argv) {
  std::string outDir = argc > 1 ? argv[1] : ".";

  // G codes (AltModPrf.cpp makeAltModWPrfGCode)
  std::array<F2LinearCode, 3> gCode;
  {
    oc::Matrix<u8> g(128, sizeof(block));
    oc::PRNG prng(block(45245327478243784ull, 28874799389237822ull));
    for (u64 i = 0; i < 3; ++i) {
      prng.get(g.data(), g.size());
      gCode[i].init(g);
    }
  }

  // A code
  F3AccPermCode aCode;
  aCode.init(KeySize, MidSize); // p=max(7,ceil(log2(256)))=8, seed=CCBlock

  // B parity half + code
  std::array<block, 128> mB;
  std::memset(mB.data(), 0, sizeof(mB));
  {
    oc::PRNG prng(block(234532451234512134ull, 214512345123455437ull));
    for (u64 i = 0; i < mB.size(); ++i)
      mB[i] = prng.get();
  }
  F2LinearCode bCode;
  {
    oc::Matrix<u8> g(128, sizeof(block)), gt(128, sizeof(block));
    for (u64 i = 0; i < 128; ++i)
      osuCrypto::copyBytes(g[i], mB[i]);
    oc::transpose(g, gt);
    bCode.init(gt);
  }

  // ---- G basis ----
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
      for (u64 j = 0; j < 3; ++j)
        gCode[j].encode(x, X[j + 1]);
      o << std::dec << i << " ";
      for (auto& b : X) writeBlock(o, b);
      o << "\n";
    }
  }
  {
    std::ofstream o(outDir + "/g_representative.txt");
    writeMeta(o, "G_rep");
    struct Item { const char* name; block x; } xs[] = {
      {"zero", block(0,0)},
      {"one", block(0,1)},
      {"all_ones", block(~0ull,~0ull)},
      {"alternating", block(0xAAAAAAAAAAAAAAAAULL, 0x5555555555555555ULL)}
    };
    for (auto& it : xs) {
      std::array<block, 4> X;
      X[0] = it.x;
      for (u64 j = 0; j < 3; ++j) gCode[j].encode(it.x, X[j+1]);
      o << it.name << " ";
      for (auto& b : X) writeBlock(o, b);
      o << "\n";
    }
  }

  // ---- A basis ----
  {
    std::ofstream o(outDir + "/a_basis_images.txt");
    writeMeta(o, "A_basis");
    o << "# dims: 512 F3 inputs x 256 F3 outputs\n";
    o << "# format: index then 256 bytes in {0,1,2}\n";
    for (u64 i = 0; i < KeySize; ++i) {
      std::vector<u8> in(KeySize, 0), out(MidSize, 0);
      in[i] = 1;
      aCode.encode(span<u8>(in), span<u8>(out));
      o << std::dec << i << " ";
      writeHex(o, out.data(), out.size());
      o << "\n";
    }
  }

  // ---- B basis via systematic definition ----
  {
    std::ofstream o(outDir + "/b_basis_images.txt");
    writeMeta(o, "B_basis");
    o << "# dims: 256 binary inputs x 128 output bits\n";
    o << "# B*v = v[0..127] XOR mBCode.encode(v[128..255])\n";
    for (u64 i = 0; i < MidSize; ++i) {
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
        bCode.encode(tt, tt);
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
    o << "# 128 rows x 128 bits: mB[i] (parity half)\n";
    for (u64 i = 0; i < 128; ++i) {
      o << std::dec << i << " ";
      writeBlock(o, mB[i]);
      o << "\n";
    }
  }

  // ---- Plain F(k,x) via AltModPrf::eval with fixed test-only key ----
  {
    std::ofstream o(outDir + "/f_fixed_key.txt");
    writeMeta(o, "F_kat");
    AltModPrf::KeyType key{};
    for (u64 i = 0; i < key.size(); ++i)
      key[i] = block(i + 1, i + 2);
    AltModPrf F(key);
    F.mInputExpansionMode = AltModPrfExpansionMode::Linear;
    struct Item { const char* name; block x; } xs[] = {
      {"zero", block(0,0)},
      {"one", block(0,1)},
      {"x10", block(1,0)},
      {"all_ones", block(~0ull,~0ull)},
      {"pattern", block(0x0123456789abcdefULL, 0xfedcba9876543210ULL)}
    };
    for (auto& it : xs) {
      block y = F.eval(it.x);
      o << it.name << " x="; writeBlock(o, it.x);
      o << " y="; writeBlock(o, y); o << std::endl;
    }
  }

  std::cerr << "Wrote AltMod basis images to " << outDir << "\n";
  return 0;
}
