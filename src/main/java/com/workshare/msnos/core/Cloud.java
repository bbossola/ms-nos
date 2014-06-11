package com.workshare.msnos.core;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.workshare.msnos.core.JoinSynchronizer.Status;
import com.workshare.msnos.core.payloads.Presence;
import com.workshare.msnos.soup.json.Json;
import com.workshare.msnos.soup.time.SystemTime;

public class Cloud implements Identifiable {

    private static final long AGENT_TIMEOUT = Long.getLong("msnos.core.agents.timeout.millis", 60000L);
    private static final long AGENT_RETRIES = Long.getLong("msnos.core.agents.retries.num", 3);

    private static Logger log = LoggerFactory.getLogger(Cloud.class);
    private static Logger proto = LoggerFactory.getLogger("protocol");

    public static interface Listener {
        public void onMessage(Message message);
    }

    public static class Multicaster extends com.workshare.msnos.soup.threading.Multicaster<Listener, Message> {
        public Multicaster() {
            super();
        }

        public Multicaster(Executor executor) {
            super(executor);
        }

        @Override
        protected void dispatch(Listener listener, Message message) {
            listener.onMessage(message);
        }
    }

    private final Iden iden;
    private final Map<Iden, LocalAgent> localAgents;
    private final Map<Iden, RemoteAgent> remoteAgents;

    transient private final Set<Gateway> gates;
    transient private final Multicaster caster;
    transient private final ScheduledExecutorService scheduler;
    transient private final JoinSynchronizer synchronizer;

    public Cloud(UUID uuid) throws IOException {
        this(uuid, Gateways.all(), new JoinSynchronizer());
    }

    public Cloud(UUID uuid, Set<Gateway> gates, JoinSynchronizer synchronizer) throws IOException {
        this(uuid, gates, synchronizer, new Multicaster(), Executors.newSingleThreadScheduledExecutor());
    }

    public Cloud(UUID uuid, Set<Gateway> gates, JoinSynchronizer synchronizer, Multicaster multicaster, ScheduledExecutorService executor) {
        this.iden = new Iden(Iden.Type.CLD, uuid);
        this.localAgents = new ConcurrentHashMap<Iden, LocalAgent>();
        this.remoteAgents = new ConcurrentHashMap<Iden, RemoteAgent>();
        this.caster = multicaster;
        this.gates = gates;
        this.scheduler = executor;
        this.synchronizer = synchronizer;

        for (Gateway gate : gates) {
            gate.addListener(new Gateway.Listener() {
                @Override
                public void onMessage(Message message) {
                    process(message);
                }
            });
        }

        final long period = AGENT_TIMEOUT / 2;
        log.debug("Probing agent every {} milliseconds", period);
        scheduler.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                probeQuietAgents();
            }
        }, period, period, TimeUnit.MILLISECONDS);
    }

    public Iden getIden() {
        return iden;
    }

    public Collection<RemoteAgent> getRemoteAgents() {
        return Collections.unmodifiableCollection(remoteAgents.values());
    }

    public Collection<LocalAgent> getLocalAgents() {
        return Collections.unmodifiableCollection(localAgents.values());
    }

    public Set<Gateway> getGateways() {
        return Collections.unmodifiableSet(gates);
    }

    private void probeQuietAgents() {
        log.trace("Probing quite agents...");
        for (RemoteAgent agent : getRemoteAgents()) {
            if (agent.getAccessTime() < SystemTime.asMillis() - AGENT_TIMEOUT) {
                log.debug("- sending ping to {}", agent.toString());
                try {
                    send(Messages.ping(this, agent));
                } catch (IOException e) {
                    log.debug("Unexpected exception pinging agent " + agent, e);
                }
            }
            if (agent.getAccessTime() < SystemTime.asMillis() - (AGENT_TIMEOUT * AGENT_RETRIES)) {
                log.debug("- remote agent removed due to inactivity: {}", agent);
                removeFaultyAgent(agent);
            }
        }
        log.trace("Done!");
    }

    private void removeFaultyAgent(RemoteAgent agent) {
        RemoteAgent result = remoteAgents.remove(agent.getIden());
        if (result != null)
            caster.dispatch(Messages.fault(this, agent));
    }

    public Receipt send(Message message) throws IOException {
        proto.info("TX: {} {} {} {}", message.getType(), message.getFrom(), message.getTo(), message.getData());

        MultiGatewayReceipt res = new MultiGatewayReceipt(message);
        for (Gateway gate : gates) {
            res.add(gate.send(message));
        }

        return res;
    }

    void onJoin(LocalAgent agent) throws IOException {
        log.debug("Local agent joined: {}", agent);
        localAgents.put(agent.getIden(), agent);

        final Status status = synchronizer.start(agent);
        try {
            send(Messages.presence(agent, this));
            send(Messages.discovery(agent, this));
            synchronizer.wait(status);
        } finally {
            synchronizer.remove(status);
        }
    }

    void onLeave(LocalAgent agent) throws IOException {
        send(Messages.absence(agent, this));
        log.debug("Local agent left: {}", agent);
        localAgents.remove(agent.getIden());
    }

    public Listener addListener(com.workshare.msnos.core.Cloud.Listener listener) {
        return caster.addListener(listener);
    }

    public void removeListener(com.workshare.msnos.core.Cloud.Listener listener) {
        log.debug("Removing listener: {}", listener);
        caster.removeListener(listener);
    }

    private void process(Message message) {

        synchronizer.process(message);

        if (isProcessable(message)) {
            proto.info("RX: {} {} {} {}", message.getType(), message.getFrom(), message.getTo(), message.getData());

            if (isPresence(message))
                processPresence(message);
            else if (isAbsence(message))
                processAbsence(message);
            else if (isPong(message))
                processPong(message);
            else
                caster.dispatch(message);
        } else {
            proto.debug("NN: {} {} {} {}", message.getType(), message.getFrom(), message.getTo(), message.getData());
        }

        touchSourceAgent(message);
    }

    private boolean isProcessable(Message message) {
        if (isComingFromALocalAgent(message)) {

            log.debug("Skipped message sent from a local agent: {}", message);
            return false;
        }

        if (isAddressedToARemoteAgent(message)) {
            log.debug("Skipped message addressed to a remote agent: {}", message);
            return false;
        }

        if (!isAddressedToTheLocalCloud(message)) {
            log.debug("Skipped message addressed to another cloud: {}", message);
            return false;
        }

        return true;
    }

    private boolean isAddressedToARemoteAgent(Message message) {
        return remoteAgents.containsKey(message.getTo());
    }

    private boolean isComingFromALocalAgent(Message message) {
        return localAgents.containsKey(message.getFrom());
    }

    private boolean isAddressedToTheLocalCloud(Message message) {
        final Iden to = message.getTo();
        return to.equals(iden) || localAgents.containsKey(to);
    }

    private void processPresence(Message message) {
        Iden from = message.getFrom();
        RemoteAgent agent = new RemoteAgent(from.getUUID(), this, ((Presence) message.getData()).getNetworks());
        log.debug("Discovered new agent from network: {}", agent.toString());
        remoteAgents.put(agent.getIden(), agent);
    }

    private void processAbsence(Message message) {
        Iden from = message.getFrom();
        log.debug("Agent from network leaving: {}", from);
        remoteAgents.remove(from);
    }

    private void processPong(Message message) {
        if (!remoteAgents.containsKey(message.getFrom()))
            try {
                send(Messages.discovery(this, message.getFrom()));
            } catch (IOException e) {
                log.error("Unexpected exception sending message " + message, e);
            }
    }

    @Override
    public String toString() {
        return Json.toJsonString(this);
    }

    private boolean isAbsence(Message message) {
        return message.getType() == Message.Type.PRS && !((Presence) message.getData()).isPresent();
    }

    private boolean isPresence(Message message) {
        return message.getType() == Message.Type.PRS && ((Presence) message.getData()).isPresent();
    }

    private boolean isPong(Message message) {
        return message.getType() == Message.Type.PON;
    }

    private void touchSourceAgent(Message message) {
        if (remoteAgents.containsKey(message.getFrom())) {
            RemoteAgent agent = remoteAgents.get(message.getFrom());
            agent.touch();
        }
    }

}
