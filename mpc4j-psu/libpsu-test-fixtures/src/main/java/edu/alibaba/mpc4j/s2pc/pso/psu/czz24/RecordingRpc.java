package edu.alibaba.mpc4j.s2pc.pso.psu.czz24;

import edu.alibaba.mpc4j.common.rpc.Party;
import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.rpc.utils.DataPacket;
import edu.alibaba.mpc4j.common.rpc.utils.DataPacketHeader;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Rpc decorator that records outbound payloads for interop wire dumps.
 */
public final class RecordingRpc implements Rpc {
    static final class CapturedPacket {
        final String partyName;
        final int ptoId;
        final int stepId;
        final List<byte[]> payload;

        CapturedPacket(String partyName, DataPacketHeader header, List<byte[]> payload) {
            this.partyName = partyName;
            this.ptoId = header.getPtoId();
            this.stepId = header.getStepId();
            this.payload = payload.stream()
                .map(bytes -> Arrays.copyOf(bytes, bytes.length))
                .collect(Collectors.toList());
        }
    }

    private final Rpc delegate;
    private final List<CapturedPacket> sentPackets = new ArrayList<>();

    RecordingRpc(Rpc delegate) {
        this.delegate = delegate;
    }

    List<CapturedPacket> getSentPackets() {
        return Collections.unmodifiableList(sentPackets);
    }

    void clearCaptures() {
        sentPackets.clear();
    }

    @Override
    public Party ownParty() {
        return delegate.ownParty();
    }

    @Override
    public Set<Party> getPartySet() {
        return delegate.getPartySet();
    }

    @Override
    public Party getParty(int partyId) {
        return delegate.getParty(partyId);
    }

    @Override
    public void connect() {
        delegate.connect();
    }

    @Override
    public void send(DataPacket dataPacket) {
        sentPackets.add(new CapturedPacket(
            delegate.ownParty().getPartyName(),
            dataPacket.getHeader(),
            dataPacket.getPayload()
        ));
        delegate.send(dataPacket);
    }

    @Override
    public DataPacket receive(DataPacketHeader header) {
        return delegate.receive(header);
    }

    @Override
    public DataPacket receiveAny(int ptoId) {
        return delegate.receiveAny(ptoId);
    }

    @Override
    public long getPayloadByteLength() {
        return delegate.getPayloadByteLength();
    }

    @Override
    public long getSendByteLength() {
        return delegate.getSendByteLength();
    }

    @Override
    public long getSendDataPacketNum() {
        return delegate.getSendDataPacketNum();
    }

    @Override
    public void synchronize() {
        delegate.synchronize();
    }

    @Override
    public void reset() {
        delegate.reset();
    }

    @Override
    public void disconnect() {
        delegate.disconnect();
    }
}
