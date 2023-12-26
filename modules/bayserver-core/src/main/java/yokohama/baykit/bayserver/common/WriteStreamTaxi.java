package yokohama.baykit.bayserver.common;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.taxi.Taxi;
import yokohama.baykit.bayserver.taxi.TaxiRunner;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;

public class WriteStreamTaxi extends Taxi implements Valve {


    OutputStream out;
    boolean chValid;
    protected ArrayList<ByteBuffer> writeQueue = new ArrayList<>();
    int agentId;

    public WriteStreamTaxi(){

    }

    public void init(int agtId, OutputStream out) throws IOException {
        this.agentId = agtId;
        this.out = out;
        this.chValid = true;
    }

    /////////////////////////////////////////
    // Implements Valve
    /////////////////////////////////////////

    @Override
    public void openValve() {
        nextRun();
    }

    /////////////////////////////////////////
    // Implements Taxi
    /////////////////////////////////////////
    @Override
    protected void depart() {
        try {
            while(true) {
                ByteBuffer buf;
                synchronized (writeQueue) {
                    if(writeQueue.isEmpty())
                        break;
                    buf = writeQueue.remove(0);
                }
                out.write(buf.array(), 0, buf.limit());

                boolean empty;
                synchronized (writeQueue) {
                    empty = writeQueue.isEmpty();
                }

                if (!empty)
                    nextRun();
            }
        }
        catch(Throwable e) {
            BayLog.error(e);
        }
    }

    @Override
    protected void onTimer() {

    }

    public void post(byte[] data, int ofs, int len) {
        synchronized (writeQueue) {
            boolean empty = writeQueue.isEmpty();
            writeQueue.add(ByteBuffer.wrap(data, ofs, len));
            if(empty)
                openValve();
        }
    }

    private void nextRun() {
        TaxiRunner.post(agentId, this);
    }
}
