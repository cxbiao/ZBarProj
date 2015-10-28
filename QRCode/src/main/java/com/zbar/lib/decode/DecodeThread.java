package com.zbar.lib.decode;

import android.os.Handler;
import android.os.Looper;

import com.zbar.lib.CaptureActivity;

import java.util.concurrent.CountDownLatch;

/**
 * 描述: 解码线程
 */
public final class DecodeThread extends Thread {

	CaptureActivity activity;
	private Handler handler;
	private final CountDownLatch handlerInitLatch;
	public static final String BARCODE_BITMAP = "BARCODE_BITMAP";

	DecodeThread(CaptureActivity activity) {
		this.activity = activity;
		handlerInitLatch = new CountDownLatch(1);
	}

	Handler getHandler() {
		try {
			handlerInitLatch.await();
		} catch (InterruptedException ie) {
			// continue?
		}
		return handler;
	}

	@Override
	public void run() {
		Looper.prepare();
		handler = new DecodeHandler(activity);
		handlerInitLatch.countDown();
		Looper.loop();
	}

}
