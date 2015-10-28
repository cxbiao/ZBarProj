
package com.zbar.lib.decode;

import android.graphics.Bitmap;
import android.graphics.Rect;

import net.sourceforge.zbar.Config;
import net.sourceforge.zbar.Image;
import net.sourceforge.zbar.ImageScanner;
import net.sourceforge.zbar.Symbol;
import net.sourceforge.zbar.SymbolSet;



public class DecodeUtils {


    private ImageScanner mImageScanner;

    static {
        System.loadLibrary("iconv");
    }

    public DecodeUtils() {
        mImageScanner = new ImageScanner();
        mImageScanner.setConfig(0, Config.X_DENSITY, 3);
        mImageScanner.setConfig(0, Config.Y_DENSITY, 3);
    }

    public String decodeWithZbar(byte[] data, int width, int height, Rect crop) {
        Image barcode = new Image(width, height, "Y800");
        barcode.setData(data);
        if (null != crop) {
            barcode.setCrop(crop.left, crop.top, crop.width(), crop.height());
        }

        int result = mImageScanner.scanImage(barcode);
        String resultStr = null;

        if (result != 0) {
            SymbolSet syms = mImageScanner.getResults();
            for (Symbol sym : syms) {
                resultStr = sym.getData();
            }
        }

        return resultStr;
    }



    public String decodeWithZbar(Bitmap bitmap) {

        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        Image barcode = new Image(width, height, "Y800");

        int size = width * height;
        int[] pixels = new int[size];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
        byte[] pixelsData = new byte[size];
        for (int i = 0; i < size; i++) {
            pixelsData[i] = (byte) pixels[i];
        }

        barcode.setData(pixelsData);

        int result = mImageScanner.scanImage(barcode);
        String resultStr = null;

        if (result != 0) {
            SymbolSet syms = mImageScanner.getResults();
            for (Symbol sym : syms) {
                resultStr = sym.getData();
            }
        }

        return resultStr;
    }


}
