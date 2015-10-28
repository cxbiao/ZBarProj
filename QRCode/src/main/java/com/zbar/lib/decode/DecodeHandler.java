package com.zbar.lib.decode;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import com.zbar.lib.CaptureActivity;
import com.zbar.lib.R;
import com.zbar.lib.bitmap.PlanarYUVLuminanceSource;

import java.io.ByteArrayOutputStream;

/**
 * Zbar解码
 */
public final class DecodeHandler extends Handler {

	private final CaptureActivity activity;
	private DecodeUtils mDecodeUtils = null;



	DecodeHandler(CaptureActivity activity) {
		this.activity = activity;
		mDecodeUtils = new DecodeUtils();
	}

	@Override
	public void handleMessage(Message message) {
		switch (message.what) {
		case R.id.decode:
			decode((byte[]) message.obj, message.arg1, message.arg2);
			break;
		case R.id.quit:
			Looper.myLooper().quit();
			break;
		}
	}

	private void decode(byte[] data, int width, int height) {
		// 这里需要将获取的data翻转一下，因为相机默认拿的的横屏的数据
		byte[] rotatedData = new byte[data.length];
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++)
				rotatedData[x * height + height - y - 1] = data[x + y * width];
		}
		int tmp = width;// Here we are swapping, that's the difference to #11
		width = height;
		height = tmp;

		Rect cropRect = activity.getCropRect();
		String result = mDecodeUtils.decodeWithZbar(rotatedData, width, height, cropRect);

		if (result != null && activity.getHandler()!=null) {
			Message message = Message.obtain(activity.getHandler(), R.id.decode_succeeded, result);
			Bundle bundle = new Bundle();
			PlanarYUVLuminanceSource source = new PlanarYUVLuminanceSource(rotatedData, width, height,
					cropRect.left, cropRect.top,
					cropRect.width(), cropRect.height(), false);

			bundleThumbnail(source, bundle);
			message.setData(bundle);
			message.sendToTarget();

		} else {
			if (null != activity.getHandler()) {
				activity.getHandler().sendEmptyMessage(R.id.decode_failed);
			}
		}
	}


	private void bundleThumbnail(PlanarYUVLuminanceSource source, Bundle bundle) {
		int[] pixels = source.renderThumbnail();
		int width = source.getThumbnailWidth();
		int height = source.getThumbnailHeight();
		Bitmap bitmap = Bitmap.createBitmap(pixels, 0, width, width, height, Bitmap.Config.RGB_565);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		bitmap.compress(Bitmap.CompressFormat.JPEG, 50, out);
		bundle.putByteArray(DecodeThread.BARCODE_BITMAP, out.toByteArray());
	}

}
