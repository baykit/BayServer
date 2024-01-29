package yokohama.baykit.bayserver.agent.multiplexer;

import yokohama.baykit.bayserver.*;
import yokohama.baykit.bayserver.agent.GrandAgent;
import yokohama.baykit.bayserver.agent.NextSocketAction;
import yokohama.baykit.bayserver.agent.TimerHandler;
import yokohama.baykit.bayserver.rudder.AsynchronousFileChannelRudder;
import yokohama.baykit.bayserver.rudder.AsynchronousSocketChannelRudder;
import yokohama.baykit.bayserver.rudder.ChannelRudder;
import yokohama.baykit.bayserver.common.DataListener;
import yokohama.baykit.bayserver.common.Multiplexer;
import yokohama.baykit.bayserver.rudder.Rudder;
import yokohama.baykit.bayserver.docker.Port;
import yokohama.baykit.bayserver.util.DataConsumeListener;
import yokohama.baykit.bayserver.util.Pair;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

/**
 * The purpose of JobMultiplexer is handling sockets, pipes, or files by thread/fiber/goroutine.
 */
public class PigeonMultiplexer extends MultiplexerBase implements TimerHandler, Multiplexer {

    private final boolean anchorable;
    private CommandReceiver commandReceiver;


    public PigeonMultiplexer(GrandAgent agent, boolean anchorable) {
        super(agent);

        this.anchorable = anchorable;
        agent.addTimerHandler(this);
    }

    ////////////////////////////////////////////
    // Implements Multiplexer
    ////////////////////////////////////////////

    @Override
    public void start() {
        BayLog.info(BayMessage.get(Symbol.MSG_RUNNING_GRAND_AGENT, this));
        for(Rudder rd: BayServer.anchorablePortMap.keySet()) {
            reqAccept(rd);
        }
    }

    public void reqConnect(Rudder rd, SocketAddress addr) throws IOException {
        throw new IOException("Connect not supported");
    }


    public void reqRead(Rudder rd) {
        if(rd == null)
            throw new NullPointerException();

        RudderState state = findRudderState(rd);
        if(state == null)
            return;

        boolean needRead = false;
        synchronized (state.reading) {
            if (!state.reading[0]) {
                needRead = true;
                state.reading[0] = true;
            }
        }

        if(needRead) {
            if(state.rudder instanceof AsynchronousFileChannelRudder)
                nextFileRead(state);
            else
                nextNetworkRead(state);
        }


        state.access();
    }

    public synchronized void reqWrite(Rudder rd, ByteBuffer buf, InetSocketAddress adr, Object tag, DataConsumeListener listener)
        throws IOException {
        if(rd == null)
            throw new NullPointerException();

        RudderState state = findRudderState(rd);
        BayLog.debug("%s reqWrite chState=%s len=%d", agent, state, buf.remaining());
        if(state == null || state.closed) {
            throw new IOException("Invalid rudder");
        }
        WriteUnit unt = new WriteUnit(buf, adr, tag, listener);
        synchronized (state.writeQueue) {
            state.writeQueue.add(unt);
        }
        state.access();

        boolean needWrite = false;
        synchronized (state.writing) {
            if (!state.writing[0]) {
                needWrite = true;
                state.writing[0] = true;
            }
        }

        if(needWrite) {
            if(state.rudder instanceof AsynchronousFileChannelRudder)
                nextFileWrite(state);
            else
                nextNetworkWrite(state);
        }

        state.access();
    }

    @Override
    public void reqEnd(Rudder rd) {
        RudderState state = findRudderState(rd);
        if(state == null)
            return;

        state.end();
        state.access();
    }

    @Override
    public void reqClose(Rudder rd) {
        if(rd == null)
            throw new NullPointerException();

        BayLog.debug("%s askToClose rd=%s", agent, rd);
        RudderState state = findRudderState(rd);
        if (state == null) {
            BayLog.debug("%s Rudder state not found: rd=%s", agent, rd);
            return;
        }

        try {
            rd.close();
        }
        catch(IOException e) {
            BayLog.error(e);
        }

        state.access();
    }

    public void runCommandReceiver(Pipe.SourceChannel readCh, Pipe.SinkChannel writeCh) {
        commandReceiver = new CommandReceiver(agent, readCh, writeCh);
    }

    public void shutdown() {
        commandReceiver.end();
        closeAll();
    }

    ////////////////////////////////////////////
    // Implements TimerHandler
    ////////////////////////////////////////////
    @Override
    public void onTimer() {
        closeTimeoutSockets();
    }


    ////////////////////////////////////////////
    // Private methods
    ////////////////////////////////////////////

    private void nextAction(RudderState st, NextSocketAction act, boolean reading) {
        switch(act) {
            case Continue:
                if(reading)
                    reqRead(st.rudder);
                break;

            case Read:
                reqRead(st.rudder);
                break;

            case Write:
                if(reading)
                    cancelRead(st);
                break;

            case Close:
                if(reading)
                    cancelRead(st);
                closeRudder(st);
                break;

            case Suspend:
                if(reading)
                    cancelRead(st);
                break;
        }
        st.access();
    }

    private void cancelRead(RudderState st) {
        synchronized (st.reading) {
            BayLog.debug("%s Reading off %s", agent, st.rudder);
            st.reading[0] = false;
        }
    }


    public void closeTimeoutSockets() {
        if(rudders.isEmpty())
            return;

        ArrayList<RudderState> closeList = new ArrayList<>();;
        synchronized (rudders) {
            long now = System.currentTimeMillis();
            for (RudderState st : rudders.values()) {
                if(st.transporter.checkTimeout(st, (int)(now - st.lastAccessTime) / 1000)) {
                    BayLog.debug("%s timeout: rd=%s", agent, st.rudder);
                    closeList.add(st);
                }
            }
        }
        for (RudderState c : closeList) {
            closeRudder(c);
        }
    }

    private void reqAccept(Rudder rd) {
        BayLog.debug("%s reqAccept isShutdown=%b", agent, agent.aborted);
        if (agent.aborted) {
            return;
        }

        Port p = BayServer.anchorablePortMap.get(rd);

        try {
            AsynchronousSocketChannel ch = null;
            ((AsynchronousServerSocketChannel) ChannelRudder.getChannel(rd)).accept(
                    rd,
                    new CompletionHandler<AsynchronousSocketChannel, Rudder>() {
                        @Override
                        public void completed(AsynchronousSocketChannel clientCh, Rudder serverRd) {

                            AsynchronousSocketChannelRudder clientRd = new AsynchronousSocketChannelRudder(clientCh);

                            try {
                                p.checkAdmitted(clientRd);
                            } catch (HttpException e) {
                                BayLog.error(e);
                                try {
                                    ch.close();
                                } catch (IOException ex) {
                                }
                                return;
                            }

                            try {
                                DataListener lis = p.newDataListener(agent.agentId, clientRd);
                                Transporter tp = p.newTransporter(agent.agentId, clientRd);
                                RudderState st = new RudderState(clientRd, lis, tp);
                                agent.netMultiplexer.addState(clientRd, st);
                                agent.netMultiplexer.reqRead(clientRd);
                            } catch (IOException e) {
                                BayLog.error(e);
                                if (ch != null) {
                                    try {
                                        ch.close();
                                    } catch (IOException ex) {
                                        BayLog.error(ex);
                                    }
                                }
                            }

                            reqAccept(serverRd);
                        }


                        @Override
                        public void failed(Throwable e, Rudder serverRd) {
                            BayLog.error(e);
                        }
                    });
        }
        catch(AcceptPendingException e) {
            // Another thread already accepting
        }

    }

    private void nextFileRead(RudderState state) {
        AsynchronousFileChannel ch = (AsynchronousFileChannel)ChannelRudder.getChannel(state.rudder);
        state.readBuf.clear();
        ch.read(
                state.readBuf,
                state.bytesRead,
                state.rudder,
                new ReadCompletionHandler());
    }

    private void nextFileWrite(RudderState state) {
        WriteUnit unit = state.writeQueue.get(0);
        BayLog.debug("%s Try to write: pkt=%s buflen=%d closed=%b", this, unit.tag, unit.buf.limit(), state.closed);
        //BayLog.debug("Data: %s", new String(unit.buf.array(), unit.buf.position(), unit.buf.limit() - unit.buf.position()));

        if(state.closed && unit.buf.limit() > 0) {
            AsynchronousFileChannel ch = (AsynchronousFileChannel)ChannelRudder.getChannel(state.rudder);
            state.readBuf.clear();
            ch.write(
                    unit.buf,
                    state.bytesWrote,
                    new Pair<>(state.rudder, unit),
                    new WriteCompletionHandler());

        }
        else {
            new WriteCompletionHandler().completed(unit.buf.limit(), new Pair<>(state.rudder, unit));
        }
    }

    private void nextNetworkRead(RudderState state) {
        AsynchronousSocketChannel ch = AsynchronousSocketChannelRudder.getAsynchronousSocketChannel(state.rudder);
        state.readBuf.clear();
        ch.read(
                state.readBuf,
                agent.timeoutSec,
                TimeUnit.MINUTES,
                state.rudder,
                new ReadCompletionHandler());
    }

    private void nextNetworkWrite(RudderState state) {
        WriteUnit unit = state.writeQueue.get(0);
        BayLog.debug("%s Try to write: pkt=%s buflen=%d closed=%b", this, unit.tag, unit.buf.limit(), state.closed);
        //BayLog.debug("Data: %s", new String(unit.buf.array(), unit.buf.position(), unit.buf.limit() - unit.buf.position()));

        if(state.closed && unit.buf.limit() > 0) {
            AsynchronousSocketChannel ch = AsynchronousSocketChannelRudder.getAsynchronousSocketChannel(state.rudder);
            state.readBuf.clear();
            ch.write(
                    unit.buf,
                    agent.timeoutSec,
                    TimeUnit.MINUTES,
                    new Pair<>(state.rudder, unit),
                    new WriteCompletionHandler());

        }
        else {
            new WriteCompletionHandler().completed(unit.buf.limit(), new Pair<>(state.rudder, unit));
        }
    }


    private class ReadCompletionHandler implements CompletionHandler<Integer, Rudder> {
        @Override
        public void completed(Integer n, Rudder rd) {
            RudderState st = findRudderState(rd);
            if (st == null || st.closing) {
                // channel is already closed
                BayLog.debug("%s Rudder is already closed: rd=%s", agent, rd);
                return;
            }

            cancelRead(st);
            st.bytesRead += n;

            NextSocketAction nextAct;
            try {
                if (n <= 0) {
                    nextAct = st.listener.notifyEof();
                }
                else {
                    st.readBuf.flip();
                    nextAct = st.listener.notifyRead(st.readBuf, null);
                }

            } catch (IOException e) {
                st.listener.notifyError(e);
                nextAct = NextSocketAction.Close;
            } catch (Throwable e) {
                st.listener.notifyError(e);
                agent.reqShutdown();
                nextAct = NextSocketAction.Close;
            }

            nextAction(st, nextAct, true);
        }

        @Override
        public void failed(Throwable e, Rudder rd) {
            RudderState st = findRudderState(rd);
            if (st == null || st.closing) {
                // channel is already closed
                BayLog.debug("%s Rudder is already closed: rd=%s", agent, rd);
                return;
            }

            st.listener.notifyError(e);
            if(!(e instanceof IOException))
                agent.reqShutdown();
            nextAction(st, NextSocketAction.Close, true);
        }

    }

    private class WriteCompletionHandler implements CompletionHandler<Integer, Pair<Rudder, WriteUnit>> {

        @Override
        public void completed(Integer n, Pair<Rudder, WriteUnit> pair) {
            Rudder rd = pair.a;
            WriteUnit unit = pair.b;
            RudderState st = findRudderState(rd);
            if (st == null || st.closing) {
                // channel is already closed
                BayLog.debug("%s Rudder is already closed: rd=%s", agent, rd);
                return;
            }

            BayLog.debug("wrote %d bytes", n);
            st.bytesWrote += n;

            try {
                if (n != unit.buf.limit()) {
                    throw new IOException("Could not write enough data");
                }
                synchronized (st.writeQueue) {
                    st.writeQueue.remove(0);
                }

                unit.done();

            } catch (IOException e) {
                st.listener.notifyError(e);
            }

            boolean writeMore = true;
            synchronized (st.writing) {
                if (st.writeQueue.isEmpty()) {
                    writeMore = false;
                    st.writing[0] = false;
                }
            }

            if(writeMore) {
                if(st.rudder instanceof AsynchronousFileChannelRudder)
                    nextFileWrite(st);
                else
                    nextNetworkWrite(st);
            }
        }

        @Override
        public void failed(Throwable e, Pair<Rudder, WriteUnit> pair) {
            Rudder rd = pair.a;
            RudderState st = findRudderState(rd);
            if (st == null || st.closing) {
                // channel is already closed
                BayLog.debug("%s Rudder is already closed: rd=%s", agent, rd);
                return;
            }

            st.listener.notifyError(e);
            if(!(e instanceof IOException))
                agent.reqShutdown();
            nextAction(st, NextSocketAction.Close, true);
        }

    }


}
