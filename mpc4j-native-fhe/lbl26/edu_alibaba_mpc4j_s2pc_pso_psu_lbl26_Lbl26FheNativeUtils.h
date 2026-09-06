//
// LBL26 cwPSU BFV helpers for Batched Ciphertext Shuffle (NDSS 2026).
//
#include <jni.h>

#ifndef MPC4J_LBL26_FHE_NATIVE_UTILS_H
#define MPC4J_LBL26_FHE_NATIVE_UTILS_H

#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT jbyteArray JNICALL Java_edu_alibaba_mpc4j_s2pc_upso_upsu_lbl26_Lbl26FheNativeUtils_encryptSlots(
    JNIEnv *env, jclass, jbyteArray parms_bytes, jbyteArray sk_bytes, jlongArray slot_values, jint active_slots);

JNIEXPORT jbyteArray JNICALL Java_edu_alibaba_mpc4j_s2pc_upso_upsu_lbl26_Lbl26FheNativeUtils_encryptSlotsPublic(
    JNIEnv *env, jclass, jbyteArray parms_bytes, jbyteArray pk_bytes, jlongArray slot_values, jint active_slots);

JNIEXPORT jlongArray JNICALL Java_edu_alibaba_mpc4j_s2pc_upso_upsu_lbl26_Lbl26FheNativeUtils_decryptSlots(
    JNIEnv *env, jclass, jbyteArray parms_bytes, jbyteArray sk_bytes, jbyteArray ciphertext_bytes);

JNIEXPORT jbyteArray JNICALL Java_edu_alibaba_mpc4j_s2pc_upso_upsu_lbl26_Lbl26FheNativeUtils_subCiphertexts(
    JNIEnv *env, jclass, jbyteArray parms_bytes, jbyteArray ct1_bytes, jbyteArray ct2_bytes);

JNIEXPORT jbyteArray JNICALL Java_edu_alibaba_mpc4j_s2pc_upso_upsu_lbl26_Lbl26FheNativeUtils_multiplyPlain(
    JNIEnv *env, jclass, jbyteArray parms_bytes, jbyteArray relin_keys_bytes, jbyteArray ct_bytes,
    jlongArray plain_slots, jint active_slots);

JNIEXPORT jbyteArray JNICALL Java_edu_alibaba_mpc4j_s2pc_upso_upsu_lbl26_Lbl26FheNativeUtils_multiplyPlainAddPlain(
    JNIEnv *env, jclass, jbyteArray parms_bytes, jbyteArray ct_bytes,
    jlongArray mul_slots, jlongArray add_slots, jint active_slots);

JNIEXPORT jbyteArray JNICALL Java_edu_alibaba_mpc4j_s2pc_upso_upsu_lbl26_Lbl26FheNativeUtils_batchedFolkloreReply(
    JNIEnv *env, jclass, jbyteArray parms_bytes, jobjectArray query_bit_ciphers,
    jobjectArray db_bit_slots, jint num_bits, jint active_slots);

#ifdef __cplusplus
}
#endif

#endif
