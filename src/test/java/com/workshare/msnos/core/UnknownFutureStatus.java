package com.workshare.msnos.core;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.workshare.msnos.core.Message.Status;

public class UnknownFutureStatus implements Future<Status> {

	@Override
	public boolean cancel(boolean arg0) {
		return true;
	}

	@Override
	public Status get() throws InterruptedException, ExecutionException {
		return Status.UNKNOWN;
	}

	@Override
	public Status get(long arg0, TimeUnit arg1)
			throws InterruptedException, ExecutionException,
			TimeoutException {
		return Status.UNKNOWN;
	}

	@Override
	public boolean isCancelled() {
		return false;
	}

	@Override
	public boolean isDone() {
		return false;
	}
}