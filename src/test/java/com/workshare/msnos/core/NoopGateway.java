package com.workshare.msnos.core;

import java.io.IOException;

import com.workshare.msnos.core.Message.Status;
import com.workshare.msnos.core.protocols.ip.BaseEndpoint;
import com.workshare.msnos.core.protocols.ip.Endpoints;

public class NoopGateway implements Gateway {

    @Override
    public String name() {
        return "NOOP";
    }

    @Override
	public void addListener(Cloud cloud, Listener listener) {
	}

	@Override
	public Endpoints endpoints() {
		return BaseEndpoint.create();
	}

	@Override
	public Receipt send(Cloud cloud, Message message, Identifiable to) throws IOException {
		return new SingleReceipt(this, Status.FAILED, message);
	}

    @Override
    public void close() throws IOException {
    }

}
