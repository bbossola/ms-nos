package com.workshare.msnos.core.serializers;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.HashSet;
import java.util.UUID;

import org.junit.Before;
import org.junit.Test;

import com.workshare.msnos.core.Agent;
import com.workshare.msnos.core.Cloud;
import com.workshare.msnos.core.Gateway;
import com.workshare.msnos.core.Iden;
import com.workshare.msnos.core.Message;
import com.workshare.msnos.core.NoopGateway;
import com.workshare.msnos.core.Version;
import com.workshare.msnos.core.payloads.Presence;

public class WireJsonSerializerTest {

    private static final UUID CLOUD_UUID = UUID.randomUUID();
    private static final UUID AGENT_UUID = UUID.randomUUID();

    private static final Iden CLOUD_IDEN = new Iden(Iden.Type.CLD, CLOUD_UUID);
    private static final Iden AGENT_IDEN = new Iden(Iden.Type.AGT, AGENT_UUID);

    private WireJsonSerializer sz = new WireJsonSerializer();
    private Cloud cloud;
    private Agent agent;

    @Before
    public void before() throws Exception {
        cloud = new Cloud(CLOUD_UUID, new HashSet<Gateway>(Arrays.asList(new NoopGateway())));

        agent = new Agent(AGENT_UUID);
        agent.join(cloud);
    }

    @Test
    public void shouldSerializeCloudObject() throws Exception {

        String expected = "\"CLD:" + toShortString(CLOUD_UUID) + "\"";
        String current = sz.toText(cloud);

        assertEquals(expected, current);
    }

    private String toShortString(UUID uuid) {
        return uuid.toString().replaceAll("-", "");
    }

    @Test
    public void shouldDeserializeCloudObject() throws Exception {
        Cloud expected = cloud;
        Cloud current = sz.fromText("\"CLD:" + toShortString(CLOUD_UUID) + "\"", Cloud.class);

        assertEquals(expected.getIden(), current.getIden());
    }

    @Test
    public void shouldSerializeAgentObject() throws Exception {
        String expected = "\"AGT:" + toShortString(AGENT_UUID) + "\"";
        String current = sz.toText(agent);

        assertEquals(expected, current);
    }

    @Test
    public void shouldDeserializeAgentObject() throws Exception {
        Agent current = sz.fromText("\"AGT:" + toShortString(AGENT_UUID) + "\"", Agent.class);

        assertEquals(agent.getIden(), current.getIden());
    }

    @Test
    public void shouldBeAbleToEncodeAndDecodeMessage() throws Exception {
        Message source = new Message(Message.Type.PRS, AGENT_IDEN, CLOUD_IDEN, 2, false, new Presence(true));

        byte[] data = sz.toBytes(source);
        Message decoded = sz.fromBytes(data, Message.class);

        assertEquals(source, decoded);
    }

    @Test
    public void shouldSerializeVersionObject() throws Exception {
        String expected = "\"1.0\"";
        String current = sz.toText(Version.V1_0);

        assertEquals(expected, current);
    }

    @Test
    public void shouldSerializeUUIDObject() throws Exception {
        UUID uuid = UUID.randomUUID();
        String expected = "\""+toShortString(uuid).replace("-",  "")+"\"";
        String current = sz.toText(uuid);

        assertEquals(expected, current);
    }

    @Test
    public void shouldDeserializeUUIDObject() throws Exception {
        UUID expected = UUID.randomUUID();
        String text = "\""+toShortString(expected).replace("-",  "")+"\"";
        UUID current = sz.fromText(text, UUID.class);

        assertEquals(expected, current);
    }
}
