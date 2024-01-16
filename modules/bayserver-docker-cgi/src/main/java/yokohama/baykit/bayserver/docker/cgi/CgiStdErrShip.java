package yokohama.baykit.bayserver.docker.cgi;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.agent.GrandAgent;
import yokohama.baykit.bayserver.agent.NextSocketAction;
import yokohama.baykit.bayserver.common.ReadOnlyShip;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;

public class CgiStdErrShip extends ReadOnlyShip  {

    CgiReqContentHandler handler;


    @Override
    public String toString() {
        return "agt#" + agentId + " err_sip#" + shipId + "/" + objectId;
    }


    /////////////////////////////////////
    // Initialize methods
    /////////////////////////////////////
    public void init(Channel ch, int agentId, CgiReqContentHandler handler) {
        super.init(ch, agentId, null);
        this.handler = handler;
    }

    /////////////////////////////////////
    // Implements Reusable
    /////////////////////////////////////

    public void reset() {
        super.reset();
        this.handler = null;
    }

    /////////////////////////////////////
    // Implements ReadOnlyShip
    /////////////////////////////////////
    @Override
    public NextSocketAction notifyRead(ByteBuffer buf) throws IOException {

        BayLog.debug("%s CGI StdErr: read %d bytes", this, buf.limit());
        String msg = new String(buf.array(), 0, buf.limit());
        if(msg.length() > 0)
            BayLog.error("CGI Stderr: %s", msg);

        handler.access();
        return NextSocketAction.Continue;
    }

    @Override
    public void notifyError(Throwable e) {
        BayLog.debug(e);
    }

    @Override
    public NextSocketAction notifyEof() {
        return NextSocketAction.Close;
    }

    @Override
    public void notifyClose() {
        handler.stdErrClosed();
    }

    @Override
    public final boolean checkTimeout(int durationSec) {
        return handler.timedOut();
    }


}
