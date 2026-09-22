package edu.alibaba.mpc4j.s2pc.aby.pcg.sowoprf;

import com.google.common.base.Preconditions;
import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.Party;
import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.rpc.desc.PtoDesc;
import edu.alibaba.mpc4j.common.rpc.pto.AbstractTwoPartyPto;
import edu.alibaba.mpc4j.common.tool.MathPreconditions;
import edu.alibaba.mpc4j.common.tool.bitmatrix.dense.DenseBitMatrix;
import edu.alibaba.mpc4j.common.tool.galoisfield.Z3ByteField;
import edu.alibaba.mpc4j.common.tool.utils.BlockUtils;
import edu.alibaba.mpc4j.s2pc.aby.pcg.sowoprf.securejoin.HaoWan26SecureJoinParams;

import java.util.Arrays;

/**
 * abstract (F3, F2)-sowOPRF receiver.
 *
 * @author Weiran Liu
 * @date 2024/6/6
 */
public abstract class AbstractF32SowOprfReceiver extends AbstractTwoPartyPto implements F32SowOprfReceiver {
    /**
     * Z3 field
     */
    protected final Z3ByteField z3Field;
    /**
     * (F3, F2)-wPRF
     */
    protected final F32Wprf f32Wprf;
    /**
     * matrix A
     */
    protected F32WprfMatrix matrixA;
    /**
     * matrix B
     */
    protected DenseBitMatrix matrixB;
    /**
     * max batch size
     */
    protected int expectBatchSize;
    /**
     * inputs
     */
    protected byte[][] inputs;
    /**
     * batch size
     */
    protected int batchSize;

    protected AbstractF32SowOprfReceiver(PtoDesc ptoDesc, Rpc receiverRpc, Party senderParty, F32SowOprfConfig config) {
        super(ptoDesc, receiverRpc, senderParty, config);
        z3Field = new Z3ByteField();
        f32Wprf = createF32Wprf(z3Field, config);
        matrixA = f32Wprf.getMatrixA();
        matrixB = f32Wprf.getMatrixB();
    }

    private static F32Wprf createF32Wprf(Z3ByteField z3Field, F32SowOprfConfig config) {
        if (config.getPublicParamsType() == F32WprfPublicParamsType.HAO_WAN_SECURE_JOIN) {
            return HaoWan26SecureJoinParams.createF32Wprf(z3Field, config.getMatrixType());
        }
        byte[] seedA = BlockUtils.zeroBlock();
        byte[] seedB = BlockUtils.zeroBlock();
        Arrays.fill(seedB, (byte) 0xFF);
        return new F32Wprf(z3Field, seedA, seedB, config.getMatrixType());
    }

    protected void setInitInput(int expectBatchSize) {
        MathPreconditions.checkPositive("expectBatchSize", expectBatchSize);
        this.expectBatchSize = expectBatchSize;
        initState();
    }

    protected void setInitInput() {
        expectBatchSize = -1;
        initState();
    }

    protected void setPtoInput(byte[][] inputs) throws MpcAbortException {
        checkInitialized();
        MathPreconditions.checkPositive("batchSize", inputs.length);
        batchSize = inputs.length;
        this.inputs = Arrays.stream(inputs)
            .peek(input -> {
                MathPreconditions.checkEqual("n", "input.length", F32Wprf.getInputLength(), input.length);
                for (byte b : input) {
                    Preconditions.checkArgument(z3Field.validateElement(b));
                }
            })
            .toArray(byte[][]::new);
    }
}
