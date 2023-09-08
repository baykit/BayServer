package baykit.bayserver.watercraft;

import baykit.bayserver.Sink;
import baykit.bayserver.agent.NextSocketAction;
import baykit.bayserver.agent.transporter.DataListener;
import baykit.bayserver.protocol.ProtocolException;
import baykit.bayserver.util.Counter;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

/**
 * Boat wraps output stream
 */
public abstract class Boat implements DataListener {

    public static final int INVALID_BOAT_ID = 0;
    static Counter oidCounter = new Counter();
    static Counter idCounter = new Counter();

    public final int objectId;
    public int boatId;

    protected Boat() {
        this.objectId = oidCounter.next();
        this.boatId = INVALID_BOAT_ID;
    }

    public void init() {
        this.boatId = idCounter.next();
    }

    @Override
    public NextSocketAction notifyConnect() throws IOException {
        throw new Sink();
    }

    @Override
    public NextSocketAction notifyRead(ByteBuffer buf, InetSocketAddress adr) throws IOException {
        throw new Sink();
    }

    @Override
    public NextSocketAction notifyEof() throws IOException {
        throw new Sink();
    }

    @Override
    public NextSocketAction notifyHandshakeDone(String protocol) throws IOException {
        throw new Sink();
    }

    @Override
    public boolean notifyProtocolError(ProtocolException e) throws IOException {
        throw new Sink();
    }

    @Override
    public boolean checkTimeout(int durationSec) {
        throw new Sink();
    }
}
