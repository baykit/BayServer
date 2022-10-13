package baykit.bayserver.docker.http.h2.command;

import baykit.bayserver.agent.NextSocketAction;
import baykit.bayserver.protocol.PacketPartAccessor;
import baykit.bayserver.docker.http.h2.*;

import java.io.IOException;

/**
 * Preface is dummy command and packet
 * 
 *   packet is not in frame format but raw data: "PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n"
 */
public class CmdPreface extends H2Command {

    public static final byte[] prefaceBytes = "PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n".getBytes();

    public CmdPreface(int streamId, H2Flags flags) {
        super(H2Type.Preface, streamId, flags);
    }
    public String protocol;

    @Override
    public void unpack(H2Packet pkt) throws IOException {
        PacketPartAccessor acc = pkt.newDataAccessor();
        byte[] prefaceData = new byte[24];
        acc.getBytes(prefaceData);
        protocol = new String(prefaceData, 6, 8);
    }

    @Override
    public void pack(H2Packet pkt) throws IOException {
        PacketPartAccessor acc = pkt.newH2DataAccessor();
        acc.putBytes(prefaceBytes);
    }

    @Override
    public NextSocketAction handle(H2CommandHandler handler) throws IOException {
        return handler.handlePreface(this);
    }
}
