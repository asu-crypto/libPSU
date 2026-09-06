//
// LBL26 cwPSU BFV helpers for Batched Ciphertext Shuffle (NDSS 2026).
//
#include "edu_alibaba_mpc4j_s2pc_pso_psu_lbl26_Lbl26FheNativeUtils.h"
#include "../serialize.h"
#include "../utils.h"
#include "seal/seal.h"

using namespace seal;
using namespace std;

static Plaintext slots_to_plaintext(const EncryptionParameters &parms, const jlong *slots, jint active_slots) {
    SEALContext context(parms);
    BatchEncoder encoder(context);
    vector<uint64_t> vec(encoder.slot_count(), 0ULL);
    uint32_t n = std::min(static_cast<uint32_t>(active_slots), static_cast<uint32_t>(encoder.slot_count()));
    for (uint32_t i = 0; i < n; i++) {
        vec[i] = static_cast<uint64_t>(slots[i]);
    }
    Plaintext pt;
    encoder.encode(vec, pt);
    return pt;
}

static jlongArray plaintext_to_slots(JNIEnv *env, const EncryptionParameters &parms, const Plaintext &pt) {
    SEALContext context(parms);
    BatchEncoder encoder(context);
    vector<uint64_t> vec(encoder.slot_count());
    encoder.decode(pt, vec);
    jlongArray out = env->NewLongArray(static_cast<jsize>(vec.size()));
    vector<jlong> fill(vec.size());
    for (size_t i = 0; i < vec.size(); i++) {
        fill[i] = static_cast<jlong>(vec[i]);
    }
    env->SetLongArrayRegion(out, 0, static_cast<jsize>(vec.size()), fill.data());
    return out;
}

JNIEXPORT jbyteArray JNICALL Java_edu_alibaba_mpc4j_s2pc_upso_upsu_lbl26_Lbl26FheNativeUtils_encryptSlots(
    JNIEnv *env, jclass, jbyteArray parms_bytes, jbyteArray sk_bytes, jlongArray slot_values, jint active_slots) {
    EncryptionParameters parms = deserialize_encryption_parms(env, parms_bytes);
    SEALContext context(parms);
    SecretKey secret_key = deserialize_secret_key(env, sk_bytes, context);
    jlong *slots_ptr = env->GetLongArrayElements(slot_values, nullptr);
    Plaintext pt = slots_to_plaintext(parms, slots_ptr, active_slots);
    env->ReleaseLongArrayElements(slot_values, slots_ptr, JNI_ABORT);
    Encryptor encryptor(context, secret_key);
    Serializable<Ciphertext> ct = encryptor.encrypt_symmetric(pt);
    return serialize_ciphertext(env, ct);
}

JNIEXPORT jbyteArray JNICALL Java_edu_alibaba_mpc4j_s2pc_upso_upsu_lbl26_Lbl26FheNativeUtils_encryptSlotsPublic(
    JNIEnv *env, jclass, jbyteArray parms_bytes, jbyteArray pk_bytes, jlongArray slot_values, jint active_slots) {
    EncryptionParameters parms = deserialize_encryption_parms(env, parms_bytes);
    SEALContext context(parms);
    PublicKey public_key = deserialize_public_key(env, pk_bytes, context);
    jlong *slots_ptr = env->GetLongArrayElements(slot_values, nullptr);
    Plaintext pt = slots_to_plaintext(parms, slots_ptr, active_slots);
    env->ReleaseLongArrayElements(slot_values, slots_ptr, JNI_ABORT);
    Encryptor encryptor(context, public_key);
    Ciphertext ct;
    encryptor.encrypt(pt, ct);
    return serialize_ciphertext(env, ct);
}

JNIEXPORT jlongArray JNICALL Java_edu_alibaba_mpc4j_s2pc_upso_upsu_lbl26_Lbl26FheNativeUtils_decryptSlots(
    JNIEnv *env, jclass, jbyteArray parms_bytes, jbyteArray sk_bytes, jbyteArray ciphertext_bytes) {
    EncryptionParameters parms = deserialize_encryption_parms(env, parms_bytes);
    SEALContext context(parms);
    SecretKey secret_key = deserialize_secret_key(env, sk_bytes, context);
    Ciphertext ct = deserialize_ciphertext(env, ciphertext_bytes, context);
    Decryptor decryptor(context, secret_key);
    Plaintext pt;
    decryptor.decrypt(ct, pt);
    return plaintext_to_slots(env, parms, pt);
}

JNIEXPORT jbyteArray JNICALL Java_edu_alibaba_mpc4j_s2pc_upso_upsu_lbl26_Lbl26FheNativeUtils_subCiphertexts(
    JNIEnv *env, jclass, jbyteArray parms_bytes, jbyteArray ct1_bytes, jbyteArray ct2_bytes) {
    EncryptionParameters parms = deserialize_encryption_parms(env, parms_bytes);
    SEALContext context(parms);
    Evaluator evaluator(context);
    Ciphertext ct1 = deserialize_ciphertext(env, ct1_bytes, context);
    Ciphertext ct2 = deserialize_ciphertext(env, ct2_bytes, context);
    evaluator.sub_inplace(ct1, ct2);
    while (ct1.parms_id() != context.last_parms_id()) {
        evaluator.mod_switch_to_next_inplace(ct1);
    }
    return serialize_ciphertext(env, ct1);
}

JNIEXPORT jbyteArray JNICALL Java_edu_alibaba_mpc4j_s2pc_upso_upsu_lbl26_Lbl26FheNativeUtils_multiplyPlain(
    JNIEnv *env, jclass, jbyteArray parms_bytes, jbyteArray relin_keys_bytes, jbyteArray ct_bytes,
    jlongArray plain_slots, jint active_slots) {
    EncryptionParameters parms = deserialize_encryption_parms(env, parms_bytes);
    SEALContext context(parms);
    Evaluator evaluator(context);
    Ciphertext ct = deserialize_ciphertext(env, ct_bytes, context);
    jlong *slots_ptr = env->GetLongArrayElements(plain_slots, nullptr);
    BatchEncoder encoder(context);
    vector<uint64_t> vec(encoder.slot_count(), 0ULL);
    uint32_t n = std::min(static_cast<uint32_t>(active_slots), static_cast<uint32_t>(encoder.slot_count()));
    for (uint32_t i = 0; i < n; i++) {
        vec[i] = static_cast<uint64_t>(slots_ptr[i]);
    }
    env->ReleaseLongArrayElements(plain_slots, slots_ptr, JNI_ABORT);
    Plaintext pt;
    encoder.encode(vec, pt);
    evaluator.multiply_plain_inplace(ct, pt);
    while (ct.parms_id() != context.last_parms_id()) {
        evaluator.mod_switch_to_next_inplace(ct);
    }
    return serialize_ciphertext(env, ct);
}

// Fused `ct * mul_slots + add_slots` (slot-wise). Used by the new bit-share BCS
// output stage: each output ciphertext is built from a single ciphertext
// (encrypt(LSB(S_B)) under pk_R) plus two slot-vector plaintexts derived from
// the sender's local plaintext element bytes and DOSN share LSBs.
JNIEXPORT jbyteArray JNICALL Java_edu_alibaba_mpc4j_s2pc_upso_upsu_lbl26_Lbl26FheNativeUtils_multiplyPlainAddPlain(
    JNIEnv *env, jclass, jbyteArray parms_bytes, jbyteArray ct_bytes,
    jlongArray mul_slots, jlongArray add_slots, jint active_slots) {
    EncryptionParameters parms = deserialize_encryption_parms(env, parms_bytes);
    SEALContext context(parms);
    Evaluator evaluator(context);
    BatchEncoder encoder(context);
    Ciphertext ct = deserialize_ciphertext(env, ct_bytes, context);
    size_t slot_count = encoder.slot_count();
    uint32_t n = std::min(static_cast<uint32_t>(active_slots), static_cast<uint32_t>(slot_count));
    vector<uint64_t> mul_vec(slot_count, 0ULL);
    vector<uint64_t> add_vec(slot_count, 0ULL);
    jlong *mul_ptr = env->GetLongArrayElements(mul_slots, nullptr);
    jlong *add_ptr = env->GetLongArrayElements(add_slots, nullptr);
    for (uint32_t i = 0; i < n; i++) {
        mul_vec[i] = static_cast<uint64_t>(mul_ptr[i]);
        add_vec[i] = static_cast<uint64_t>(add_ptr[i]);
    }
    env->ReleaseLongArrayElements(mul_slots, mul_ptr, JNI_ABORT);
    env->ReleaseLongArrayElements(add_slots, add_ptr, JNI_ABORT);
    Plaintext pt_mul;
    encoder.encode(mul_vec, pt_mul);
    Plaintext pt_add;
    encoder.encode(add_vec, pt_add);
    evaluator.multiply_plain_inplace(ct, pt_mul);
    evaluator.add_plain_inplace(ct, pt_add);
    while (ct.parms_id() != context.last_parms_id()) {
        evaluator.mod_switch_to_next_inplace(ct);
    }
    return serialize_ciphertext(env, ct);
}

// Slot-batched folklore equality: for each bit i in [0, num_bits),
//   tmp_i = query_cipher[i] * (1 - 2 * db_bit_slots[i]) + db_bit_slots[i]   (mod p, slot-wise)
//
// Because db bits b are in {0, 1}, this evaluates to:
//   - if b = 0: query_cipher[i]            (so slot equals the query bit)
//   - if b = 1: 1 - query_cipher[i]
// which is exactly XOR(query_bit, db_bit) when query_bit is also 0/1. Summing across bits
// gives the Hamming distance between the slot-batched query codewords and assigned-bin codewords.
// Client decrypts and treats slot j == 0 as a match for query j.
//
// One plaintext multiplication per bit, no relinearization (depth 1), single mod-switch to the
// last parms_id keeps the response ciphertext small.
JNIEXPORT jbyteArray JNICALL Java_edu_alibaba_mpc4j_s2pc_upso_upsu_lbl26_Lbl26FheNativeUtils_batchedFolkloreReply(
    JNIEnv *env, jclass, jbyteArray parms_bytes, jobjectArray query_bit_ciphers,
    jobjectArray db_bit_slots, jint num_bits, jint active_slots) {
    EncryptionParameters parms = deserialize_encryption_parms(env, parms_bytes);
    SEALContext context(parms);
    Evaluator evaluator(context);
    BatchEncoder encoder(context);
    uint64_t plain_modulus = parms.plain_modulus().value();
    uint64_t neg_two_mod_p = plain_modulus - 2;
    size_t slot_count = encoder.slot_count();
    auto n_bits = static_cast<uint32_t>(num_bits);
    auto n_active = static_cast<uint32_t>(std::min<size_t>(static_cast<size_t>(active_slots), slot_count));
    if (n_bits == 0) {
        jclass exception = env->FindClass("java/lang/IllegalArgumentException");
        env->ThrowNew(exception, "batchedFolkloreReply: num_bits must be > 0");
        return nullptr;
    }
    Ciphertext accumulator;
    bool accumulator_initialized = false;
    for (uint32_t i = 0; i < n_bits; i++) {
        auto query_cipher_bytes = (jbyteArray) env->GetObjectArrayElement(query_bit_ciphers, i);
        Ciphertext query_cipher = deserialize_ciphertext(env, query_cipher_bytes, context);
        auto db_bit_row = (jlongArray) env->GetObjectArrayElement(db_bit_slots, i);
        jlong *row_ptr = env->GetLongArrayElements(db_bit_row, nullptr);
        vector<uint64_t> sub_vec(slot_count, 0ULL);
        vector<uint64_t> add_vec(slot_count, 0ULL);
        for (uint32_t j = 0; j < n_active; j++) {
            uint64_t bit = static_cast<uint64_t>(row_ptr[j]) & 1ULL;
            // 1 - 2*bit = (1) when bit=0, (p - 1) when bit=1
            sub_vec[j] = (1ULL + bit * neg_two_mod_p) % plain_modulus;
            add_vec[j] = bit;
        }
        env->ReleaseLongArrayElements(db_bit_row, row_ptr, JNI_ABORT);
        Plaintext pt_mul;
        encoder.encode(sub_vec, pt_mul);
        Plaintext pt_add;
        encoder.encode(add_vec, pt_add);
        evaluator.multiply_plain_inplace(query_cipher, pt_mul);
        evaluator.add_plain_inplace(query_cipher, pt_add);
        if (!accumulator_initialized) {
            accumulator = query_cipher;
            accumulator_initialized = true;
        } else {
            evaluator.add_inplace(accumulator, query_cipher);
        }
    }
    while (accumulator.parms_id() != context.last_parms_id()) {
        evaluator.mod_switch_to_next_inplace(accumulator);
    }
    return serialize_ciphertext(env, accumulator);
}
