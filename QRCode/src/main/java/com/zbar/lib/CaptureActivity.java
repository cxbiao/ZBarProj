package com.zbar.lib;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.graphics.Rect;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Vibrator;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.SurfaceHolder;
import android.view.SurfaceHolder.Callback;
import android.view.SurfaceView;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.view.animation.TranslateAnimation;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.zbar.lib.camera.CameraManager;
import com.zbar.lib.decode.CaptureActivityHandler;
import com.zbar.lib.decode.DecodeUtils;
import com.zbar.lib.decode.InactivityTimer;

import java.io.IOException;

/**
 * 二维码扫描界面
 */
public class CaptureActivity extends Activity implements Callback,View.OnClickListener {

	private static final long VIBRATE_DURATION = 200L;
	private CaptureActivityHandler handler;
	private boolean hasSurface;
	private InactivityTimer inactivityTimer;
	private MediaPlayer mediaPlayer;
	private boolean playBeep;
	private static final float BEEP_VOLUME = 0.50f;
	private boolean vibrate;
	private RelativeLayout mContainer = null;
	private RelativeLayout mCropLayout = null;
	private Rect cropRect;
	private boolean isOpen = false; // 控制开关灯
	private TextView openlights;
	private static final int REQUEST_CODE = 100;
	private ProgressDialog mProgress;
	private static final int PARSE_BARCODE_SUC = 300;
	private static final int PARSE_BARCODE_FAIL = 301;


	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.activity_qr_scan);
		// 初始化 CameraManager
		CameraManager.init(getApplication());
		hasSurface = false;
		inactivityTimer = new InactivityTimer(this);

		mContainer = (RelativeLayout) findViewById(R.id.capture_containter);
		mCropLayout = (RelativeLayout) findViewById(R.id.capture_crop_layout);

		ImageView mQrLineView = (ImageView) findViewById(R.id.capture_scan_line);
		TranslateAnimation mAnimation = new TranslateAnimation(TranslateAnimation.ABSOLUTE, 0f, TranslateAnimation.ABSOLUTE, 0f,
				TranslateAnimation.RELATIVE_TO_PARENT, 0f, TranslateAnimation.RELATIVE_TO_PARENT, 0.9f);
		mAnimation.setDuration(1500);
		mAnimation.setRepeatCount(-1);
		//mAnimation.setRepeatMode(Animation.REVERSE);
		mAnimation.setInterpolator(new LinearInterpolator());
		mQrLineView.setAnimation(mAnimation);

		openlights= (TextView) findViewById(R.id.tv_lights);
		openlights.setOnClickListener(this);
		findViewById(R.id.tv_album).setOnClickListener(this);
		findViewById(R.id.tv_back_qrcode).setOnClickListener(this);
	}



	@SuppressWarnings("deprecation")
	@Override
	protected void onResume() {
		super.onResume();
		SurfaceView surfaceView = (SurfaceView) findViewById(R.id.capture_preview);
		SurfaceHolder surfaceHolder = surfaceView.getHolder();
		if (hasSurface) {
			initCamera(surfaceHolder);
		} else {
			surfaceHolder.addCallback(this);
			surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
		}
		playBeep = true;
		AudioManager audioService = (AudioManager) getSystemService(AUDIO_SERVICE);
		if (audioService.getRingerMode() != AudioManager.RINGER_MODE_NORMAL) {
			playBeep = false;
		}
		initBeepSound();
		vibrate = true;

	}

	@Override
	protected void onPause() {
		super.onPause();
		if (handler != null) {
			handler.quitSynchronously();
			handler = null;
		}
		CameraManager.get().closeDriver();
	}

	@Override
	protected void onDestroy() {
		inactivityTimer.shutdown();
		super.onDestroy();
	}

	public void handleDecode(String result,Bundle bundle) {
		inactivityTimer.onActivity();
		playBeepSoundAndVibrate();
		Toast.makeText(getApplicationContext(), result, Toast.LENGTH_SHORT).show();

		setQrResult(result);

		// 连续扫描，不发送此消息扫描一次结束后就不能再次扫描
		// handler.sendEmptyMessage(R.id.restart_preview);
	}


	private void setQrResult(String result){
		Intent intent=new Intent();
		intent.putExtra("result", result);
		setResult(RESULT_OK, intent);
		finish();
	}

	private void initCamera(SurfaceHolder surfaceHolder) {
		try {
			CameraManager.get().openDriver(surfaceHolder);

			Point point = CameraManager.get().getCameraResolution();
			int width = point.y;
			int height = point.x;

			int x = mCropLayout.getLeft() * width / mContainer.getWidth();
			int y = mCropLayout.getTop() * height / mContainer.getHeight();

			int cropWidth = mCropLayout.getWidth() * width / mContainer.getWidth();
			int cropHeight = mCropLayout.getHeight() * height / mContainer.getHeight();

			setCropRect(new Rect(x,y,x+cropWidth,y+cropHeight));


		} catch (Exception e) {
			return;
		}
		if (handler == null) {
			handler = new CaptureActivityHandler(CaptureActivity.this);
		}
	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

	}

	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		if (!hasSurface) {
			hasSurface = true;
			initCamera(holder);
		}
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		hasSurface = false;

	}

	public Handler getHandler() {
		return handler;
	}

	private void initBeepSound() {
		if (playBeep && mediaPlayer == null) {
			setVolumeControlStream(AudioManager.STREAM_MUSIC);
			mediaPlayer = new MediaPlayer();
			mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
			mediaPlayer.setOnCompletionListener(beepListener);

			AssetFileDescriptor file = getResources().openRawResourceFd(R.raw.beep);
			try {
				mediaPlayer.setDataSource(file.getFileDescriptor(), file.getStartOffset(), file.getLength());
				file.close();
				mediaPlayer.setVolume(BEEP_VOLUME, BEEP_VOLUME);
				mediaPlayer.prepare();
			} catch (IOException e) {
				mediaPlayer = null;
			}
		}
	}

	public Rect getCropRect() {
		return cropRect;
	}

	public void setCropRect(Rect cropRect) {
		this.cropRect = cropRect;
	}



	private void playBeepSoundAndVibrate() {
		if (playBeep && mediaPlayer != null) {
			mediaPlayer.start();
		}
		if (vibrate) {
			Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
			vibrator.vibrate(VIBRATE_DURATION);
		}
	}

	private final OnCompletionListener beepListener = new OnCompletionListener() {
		public void onCompletion(MediaPlayer mediaPlayer) {
			mediaPlayer.seekTo(0);
		}
	};

	@Override
	public void onClick(View v) {
		switch (v.getId()) {
			case R.id.tv_back_qrcode:
				finish();
				break;
			case R.id.tv_lights:
				// 开灯
				if (isOpen) {
					CameraManager.get().offLight();
					openlights.setText("开灯");
					openlights.setSelected(false);
					isOpen = false;
				} else { // 关灯
					CameraManager.get().openLight();
					openlights.setText("关灯");
					openlights.setSelected(true);
					isOpen = true;
				}
				break;
			case R.id.tv_album:
				// 打开手机中的相册
				Intent intent = new Intent(Intent.ACTION_GET_CONTENT/*Intent.ACTION_PICK*/, null);
				intent.setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*");
				startActivityForResult(intent, REQUEST_CODE);
				break;
		}
	}


	private Handler mHandler=new Handler(){
		@Override
		public void handleMessage(Message msg) {
			mProgress.dismiss();
			switch (msg.what){
				case PARSE_BARCODE_SUC:
					Toast.makeText(getBaseContext(),(String)msg.obj,Toast.LENGTH_SHORT).show();
					setQrResult((String)msg.obj);
					break;
				case PARSE_BARCODE_FAIL:
					Toast.makeText(getBaseContext(),(String)msg.obj,Toast.LENGTH_SHORT).show();
					break;
			}

		}
	};

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (resultCode == RESULT_OK) {
			switch (requestCode) {
				case REQUEST_CODE:
					mProgress = new ProgressDialog(CaptureActivity.this);
					mProgress.setMessage("正在扫描,请稍等片刻...");
					mProgress.setCancelable(false);
					mProgress.show();

					// 获取选中图片的路径
					Cursor cursor = getContentResolver().query(data.getData(), new String[] { MediaStore.Images.Media.DATA }, null, null, null);
					if (cursor.moveToFirst()) {
						final String photo_path = cursor.getString(cursor.getColumnIndex(MediaStore.Images.Media.DATA));
						cursor.close();
						new Thread(new Runnable() {
							@Override
							public void run() {
								Bitmap bitmap=loadImage(CaptureActivity.this, photo_path);
								DecodeUtils decodeUtils=new DecodeUtils();
								String result=decodeUtils.decodeWithZbar(bitmap);
								if(!TextUtils.isEmpty(result)){
									mHandler.obtainMessage(PARSE_BARCODE_SUC,result).sendToTarget();
								}else{
									mHandler.obtainMessage(PARSE_BARCODE_FAIL,"扫描图片失败!").sendToTarget();
								}

							}
						}).start();

					}

					break;

			}
		}
	}


	public static int calculateInSampleSize(BitmapFactory.Options options,
											int reqWidth, int reqHeight) {
		// 源图片的宽度
		int width = options.outWidth;
		int height = options.outHeight;
		int inSampleSize = 1;

		if (width > reqWidth || height > reqHeight) {
			// 计算出实际宽度和目标宽度的比率
			int widthRatio = Math.round((float) width / (float) reqWidth);
			int heightRatio = Math.round((float) width / (float) reqWidth);
			inSampleSize = Math.max(widthRatio, heightRatio);
		}
		return inSampleSize;
	}

	public static Bitmap loadImage(Context context,String pathName) {
		// 第一次解析将inJustDecodeBounds设置为true，来获取图片大小
		final BitmapFactory.Options options = new BitmapFactory.Options();
		options.inJustDecodeBounds = true;
		BitmapFactory.decodeFile(pathName, options);

		int reqWidth=context.getResources().getDisplayMetrics().widthPixels;
		int reqHeight=context.getResources().getDisplayMetrics().heightPixels;
		// 调用上面定义的方法计算inSampleSize值
		options.inSampleSize = calculateInSampleSize(options, reqWidth,
				reqHeight);
		// 使用获取到的inSampleSize值再次解析图片
		options.inJustDecodeBounds = false;
		Bitmap bitmap = BitmapFactory.decodeFile(pathName, options);

		return bitmap;
	}
}