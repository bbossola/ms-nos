package com.workshare.msnos.core.serializers;

import com.workshare.msnos.core.*;
import com.workshare.msnos.core.cloud.JoinSynchronizer;
import com.workshare.msnos.core.cloud.Multicaster;
import com.workshare.msnos.core.payloads.FltPayload;
import com.workshare.msnos.core.payloads.Presence;
import com.workshare.msnos.core.payloads.QnePayload;
import com.workshare.msnos.core.security.Signer;
import com.workshare.msnos.soup.time.SystemTime;
import com.workshare.msnos.usvc.api.RestApi;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class WireJsonSerializerTest {

    private static final UUID AGENT_UUID = UUID.randomUUID();

    private static final UUID CLOUD_UUID = UUID.randomUUID();
    private static final Long CLOUD_INSTANCE_ID = 1274L;

    private Cloud cloud;
    private Cloud cloudWithId;
    private LocalAgent agent;
    private WireJsonSerializer sz = new WireJsonSerializer();
    
    @Before
    public void before() throws Exception {
        cloud = new Cloud(CLOUD_UUID, "1231", new Signer(), new HashSet<Gateway>(Arrays.asList(new NoopGateway())), mock(JoinSynchronizer.class), mock(Multicaster.class), mock(ScheduledExecutorService.class),null);
        cloudWithId = new Cloud(CLOUD_UUID, "1231", new Signer(), new HashSet<Gateway>(Arrays.asList(new NoopGateway())), mock(JoinSynchronizer.class), mock(Multicaster.class), mock(ScheduledExecutorService.class),CLOUD_INSTANCE_ID);

        agent = new LocalAgent(AGENT_UUID);
        agent.join(cloud);
    }

    @Test
    public void shouldSerializeCloud() throws Exception {
        String expected = "\"CLD:" + toShortString(CLOUD_UUID)+"\"";
        String current = sz.toText(cloud);

        assertEquals(expected, current);
    }


    @Test
    public void shouldSerializeCloudWithInstanceId() throws Exception {
        String expected = "\"CLD:" + toShortString(CLOUD_UUID) + ":"+Long.toString(CLOUD_INSTANCE_ID,32)+"\"";
        String current = sz.toText(cloudWithId);

        assertEquals(expected, current);
    }

    @Test
    public void shouldDeserializeCloudObject() throws Exception {
        Cloud expected = cloud;

        final String text = "\"CLD:" + toShortString(CLOUD_UUID) + "\"";
        Cloud current = sz.fromText(text, Cloud.class);

        assertEquals(expected.getIden(), current.getIden());
        assertEquals(expected.getInstanceID(), current.getInstanceID());
    }

    @Test
    public void shouldDeserializeCloudWithIstanceIdObject() throws Exception {
        Cloud expected = cloudWithId;

        final String text = "\"CLD:" + toShortString(CLOUD_UUID) + ":"+Long.toString(CLOUD_INSTANCE_ID,32)+"\"";
        Cloud current = sz.fromText(text, Cloud.class);

        assertEquals(expected.getIden(), current.getIden());
        assertEquals(expected.getInstanceID(), current.getInstanceID());

    }

    @Test
    public void shouldSerializeAgentObject() throws Exception {
        String expected = "\"AGT:" + toShortString(AGENT_UUID) + "\"";
        String current = sz.toText(agent);

        assertEquals(expected, current);
    }

    @Test
    public void shouldDeserializeAgentObject() throws Exception {
        LocalAgent current = sz.fromText("\"AGT:" + toShortString(AGENT_UUID) + "\"", LocalAgent.class);

        assertEquals(agent.getIden(), current.getIden());
    }

    @Test
    public void shouldBeAbleToEncodeAndDecodeMessage() throws Exception {
        Message source = new MessageBuilder(Message.Type.PRS, agent, cloud).sequence(12).with(new Presence(true)).make();

        byte[] data = sz.toBytes(source);
        Message decoded = sz.fromBytes(data, Message.class);

        assertEquals(source, decoded);
    }

    @Test
    public void shouldBeAbleToEncodeAndDecodeQNE() throws Exception {
        Message source = new MessageBuilder(Message.Type.QNE, agent, cloud).sequence(12).with(new QnePayload("test", new RestApi("test", "/test", 7070))).make();

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
        String expected = "\"" + toShortString(uuid).replace("-", "") + "\"";
        String current = sz.toText(uuid);

        assertEquals(expected, current);
    }

    @Test
    public void shouldDeserializeUUIDObject() throws Exception {
        UUID expected = UUID.randomUUID();
        String text = "\"" + toShortString(expected).replace("-", "") + "\"";
        UUID current = sz.fromText(text, UUID.class);

        assertEquals(expected, current);
    }

    @Test
    public void shouldBeAbleToEncodeAndDecodeSignedMessage() throws Exception {
        final String sig = "this-is-a-signature";
        final String rnd = "random";
        Message source = new MessageBuilder(Message.Type.QNE, agent, cloud).sequence(12).signed(sig, rnd).make();

        byte[] data = sz.toBytes(source);
        Message decoded = sz.fromBytes(data, Message.class);

        assertEquals(sig, decoded.getSig());
        assertEquals(rnd, decoded.getRnd());
    }

    @Test
    public void shouldNotSerializeUUIDIfSequenceNumber() throws Exception {
        Message source = new MessageBuilder(MessageBuilder.Mode.RELAXED, Message.Type.QNE, agent.getIden(), cloud.getIden()).with(UUID.randomUUID()).sequence(23).make();

        byte[] data = sz.toBytes(source);
        Message decoded = sz.fromBytes(data, Message.class);

        assertEquals(getUUID(agent.getIden(), 23), decoded.getUuid());
    }

    @Test
    public void shouldCorrectlySerializeThenDeserializeUUID() throws Exception {
        fakeSystemTime(12345678901221L);

        Cloud mockCloud = mock(Cloud.class);
        when(mockCloud.getIden()).thenReturn(new Iden(Iden.Type.CLD, UUID.randomUUID()));
        when(mockCloud.generateNextMessageUUID()).thenReturn(new UUID(123, SystemTime.asMillis()));

        Message source = new MessageBuilder(Message.Type.QNE, mockCloud, mockCloud).make();

        byte[] data = sz.toBytes(source);
        Message decoded = sz.fromBytes(data, Message.class);

        assertEquals(SystemTime.asMillis(), decoded.getUuid().getLeastSignificantBits());
    }

    @Test
    public void shouldCorrectlyDeserializeFLTMessage() throws Exception {
        Message source = new MessageBuilder(MessageBuilder.Mode.RELAXED, Message.Type.FLT, cloud.getIden(), cloud.getIden()).with(new FltPayload(agent.getIden())).with(UUID.randomUUID()).sequence(23).make();

        byte[] data = sz.toBytes(source);
        Message decoded = sz.fromBytes(data, Message.class);

        assertEquals(source.getData(), decoded.getData());
    }

    @Test
    public void shouldSerializeBooleanCompact() throws Exception {
        assertEquals("1", sz.toText(Boolean.TRUE));
        assertEquals("0", sz.toText(Boolean.FALSE));
    }

    @Test
    public void shouldDeserializeBooleanCompact() throws Exception {
        assertEquals(Boolean.TRUE, sz.fromText("1", Boolean.class));
        assertEquals(Boolean.FALSE, sz.fromText("0", Boolean.class));
    }

    private void fakeSystemTime(final long time) {
        SystemTime.setTimeSource(new SystemTime.TimeSource() {
            @Override
            public long millis() {
                return time;
            }
        });
    }

    private UUID getUUID(Iden iden, long sequenceNumber) {
        return new UUID(iden.getUUID().getMostSignificantBits(), sequenceNumber);
    }
    
    private String toShortString(UUID uuid) {
        return uuid.toString().replaceAll("-", "");
    }

}
