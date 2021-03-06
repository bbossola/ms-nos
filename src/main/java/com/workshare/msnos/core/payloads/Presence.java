package com.workshare.msnos.core.payloads;

import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.workshare.msnos.core.Agent;
import com.workshare.msnos.core.Cloud;
import com.workshare.msnos.core.Iden;
import com.workshare.msnos.core.LocalAgent;
import com.workshare.msnos.core.Message;
import com.workshare.msnos.core.Message.Payload;
import com.workshare.msnos.core.MsnosException;
import com.workshare.msnos.core.RemoteAgent;
import com.workshare.msnos.core.protocols.ip.Endpoint;
import com.workshare.msnos.core.protocols.ip.HttpEndpoint;
import com.workshare.msnos.soup.json.Json;

public class Presence implements Message.Payload {

    private static Logger log = LoggerFactory.getLogger(Presence.class);

    private final boolean present;
    private final Set<Endpoint> endpoints;

    public Presence(boolean present, Set<Endpoint> endpoints) {
        this.present = present;
        this.endpoints = endpoints;
        log.trace(present ? "Presence message created: {}" : "Absence message created: {}", this);
    }

    public Presence(boolean present, Agent agent) throws MsnosException {
        this(present, present ? agent.getEndpoints() : new HashSet<Endpoint>());
    }

    public boolean isPresent() {
        return present;
    }

    public Set<Endpoint> getEndpoints() {
        return endpoints;
    }

    @Override
    public String toString() {
        return Json.toJsonString(this);
    }

    @Override
    public Message.Payload[] split() {
        Set<Endpoint> netOne = new HashSet<Endpoint>();
        Set<Endpoint> netTwo = new HashSet<Endpoint>();

        int i = 0;
        for (Endpoint ep : endpoints) {
            if (i++ % 2 == 0)
                netOne.add(ep);
            else
                netTwo.add(ep);
        }

        return new Payload[]{
                new Presence(present, netOne),
                new Presence(present, netTwo)
        };
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + endpoints.hashCode();
        result = prime * result + (present ? 1231 : 1237);
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        try {
            Presence other = (Presence) obj;
            return endpoints.equals(other.endpoints) && present == other.present;
        } catch (Exception any) {
            return false;
        }
    }

    @Override
    public boolean process(Message message, Cloud.Internal internal) {
        Iden from = message.getFrom();

        if (isPresent()) {
            RemoteAgent agent = new RemoteAgent(from.getUUID(), internal.cloud(), extractEndpoints(from));
            log.debug("Discovered new agent from network: {}", agent.toString());
            internal.remoteAgents().add(agent);
        } else {
            log.debug("Agent from network leaving: {}", from);
            internal.remoteAgents().remove(from);
        }

        return true;
    }

    // FIXME this method is NOT tested - patch - need to be refactored and tested please
    private Set<Endpoint> extractEndpoints(Iden from) {
        final Set<Endpoint> all = getEndpoints();
        final Set<Endpoint> res = new HashSet<Endpoint>();
        for (Endpoint endpoint  : all) {
            if (endpoint instanceof HttpEndpoint) {
                HttpEndpoint http = (HttpEndpoint)endpoint;
                endpoint = http.withTarget(from);
            }
            res.add(endpoint);
        }

        return res;
    }

    public static Presence on(LocalAgent agent) {
        return new Presence(true, agent.getEndpoints());
    }

}
