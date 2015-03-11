package com.joedownie.cleanimage;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.location.Address;
import android.location.Geocoder;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v7.app.ActionBarActivity;
import android.text.Html;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;

import it.sephiroth.android.library.exif2.ExifInterface;
import it.sephiroth.android.library.exif2.ExifTag;
import it.sephiroth.android.library.exif2.IfdId;


public class CleanImageActivity extends ActionBarActivity {

    static final String LOG_TAG = "CleanImageActivity";
    static final int REQUEST_FILE = 1;
    static final int SHARE_FILE = 2;
    private Bitmap bitmap;
    private ImageView imageView;
    private TextView exifText;
    private ExifInterface exif  = new ExifInterface();
    private Uri mUri;
    private int mTagsCount = 0;
    private Context context;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_clean_image);

        exifText = (TextView) findViewById(R.id.exifView);
        imageView = (ImageView) findViewById(R.id.imageView);
        context = this;

        exifText.setText(getResources().getString(R.string.how_to));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_clean_image, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.about) {
            Intent intent = new Intent(this, AboutActivity.class);
            startActivity(intent);
        }

        return super.onOptionsItemSelected(item);
    }

    public void importOnClick(View v){
        Intent intent = new Intent( Intent.ACTION_GET_CONTENT );
        intent.setType( "image/*" );
        Intent chooser = Intent.createChooser(intent, "Choose picture");
        startActivityForResult(chooser, REQUEST_FILE);
    }

    public void shareOnClick(View v){
        if(bitmap != null){
            Intent intent = new Intent();
            intent.setAction(Intent.ACTION_SEND);
            intent.putExtra(Intent.EXTRA_STREAM, getImageUri(context, bitmap));
            intent.setType("image/jpeg");
            Intent chooser = Intent.createChooser(intent, "Share clean image with...");
            startActivityForResult(chooser, SHARE_FILE);
        }
    }

    @Override
    protected void onActivityResult( int requestCode, int resultCode, Intent data ) {
        if(resultCode == RESULT_OK && requestCode == REQUEST_FILE) {
            try{
                mUri = data.getData();
                ContentResolver cr = getContentResolver();
                InputStream is = cr.openInputStream(mUri);
                processInputStream(is);
                setImageView();
            } catch(FileNotFoundException e){
                // do nothing
            } catch(IOException e){
                // do nothing
            }
        }

        Log.i(LOG_TAG, requestCode + " " +  resultCode + " " + RESULT_OK );

        if(requestCode == SHARE_FILE){
            mUri = null;
            imageView.setImageDrawable(null);
            bitmap = null;
            exifText.setText("");
        }
    }

    private void setImageView(){

        if(exif.hasThumbnail())
            imageView.setImageBitmap(rotateBitmap(exif.getThumbnailBitmap()));
        else {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = false;
            try{
                Bitmap b = BitmapFactory.decodeStream(context.getContentResolver().openInputStream(mUri), null, options);
                imageView.setImageBitmap(b);
            } catch(FileNotFoundException e) {
                // do nothing
            }
        }

        try{
            setBitmap();
        } catch(FileNotFoundException e){
            // do nothing
        }
    }

    private void setBitmap() throws FileNotFoundException {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeStream(context.getContentResolver().openInputStream(mUri), null, options);

        float width = options.outWidth;
        float height = options.outHeight;
        final int MAX = getMaxTextureSize();

        float wRatio = width/height;
        float hRatio = height/width;

        boolean flag = false;
        while(width > MAX || height > MAX){
            width -= wRatio;
            height -= hRatio;
            flag = true;
        }

        if(flag)
            options.inSampleSize = 2;
        options.inJustDecodeBounds = false;
        bitmap = BitmapFactory.decodeStream(context.getContentResolver().openInputStream(mUri), null, options);
        try{
            if(exif.getTagIntValue(ExifInterface.TAG_ORIENTATION) != 0) // TODO rotate accordingly
                bitmap = rotateBitmap(bitmap);
        } catch(NullPointerException e) {
            e.printStackTrace();
        }
    }

    private Bitmap rotateBitmap(Bitmap b) {
        Matrix matrix = new Matrix();
        matrix.setRotate(90);

        try{
            return Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), matrix, true);
        } catch(OutOfMemoryError e) {
            // do nothing
        } catch(NullPointerException e) {
            return b;
        }

        return b;
    }

    private int getMaxTextureSize() {
        // Safe minimum default size
        final int IMAGE_MAX_BITMAP_DIMENSION = 2048;

        // Get EGL Display
        EGL10 egl = (EGL10) EGLContext.getEGL();
        EGLDisplay display = egl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);

        // Initialise
        int[] version = new int[2];
        egl.eglInitialize(display, version);

        // Query total number of configurations
        int[] totalConfigurations = new int[1];
        egl.eglGetConfigs(display, null, 0, totalConfigurations);

        // Query actual list configurations
        EGLConfig[] configurationsList = new EGLConfig[totalConfigurations[0]];
        egl.eglGetConfigs(display, configurationsList, totalConfigurations[0], totalConfigurations);

        int[] textureSize = new int[1];
        int maximumTextureSize = 0;

        // Iterate through all the configurations to located the maximum texture size
        for (int i = 0; i < totalConfigurations[0]; i++) {
            // Only need to check for width since opengl textures are always squared
            egl.eglGetConfigAttrib(display, configurationsList[i], EGL10.EGL_MAX_PBUFFER_WIDTH, textureSize);

            // Keep track of the maximum texture size
            if (maximumTextureSize < textureSize[0])
                maximumTextureSize = textureSize[0];
        }

        // Release
        egl.eglTerminate(display);

        // Return largest texture size found, or default
        return Math.max(maximumTextureSize, IMAGE_MAX_BITMAP_DIMENSION);
    }

    public Uri getImageUri(Context inContext, Bitmap inImage) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        inImage.compress(Bitmap.CompressFormat.JPEG, 100, bytes);
        String path = MediaStore.Images.Media.insertImage(inContext.getContentResolver(), inImage, "", null);
        return Uri.parse(path);
    }

    private void processInputStream( InputStream stream ) {

        List<ExifTag> all_tags = null;

        if( null != stream ) {
            long t1 = System.currentTimeMillis();
            try {
                exif.readExif( stream, ExifInterface.Options.OPTION_ALL );
            } catch( IOException e ) {
                exif = null;
            }
            long t2 = System.currentTimeMillis();

            if(null != exif) {
                all_tags = exif.getAllTags();
            }
        }

        exifText.setText( "" );

        if( null != exif ) {
            mTagsCount = 0;
            StringBuilder string = new StringBuilder();

            exifText.setText( "<h2>JPEG Info<h2><br/>" );

            if( exif.getQualityGuess() > 0 ) {
                string.append( "<b>JPEG quality:</b> " + exif.getQualityGuess() + "<br>" );
            }

            int[] imagesize = exif.getImageSize();
            if( imagesize[0] > 0 && imagesize[1] > 0 ) {
                string.append( "<b>Image Size: </b>" + imagesize[0] + "x" + imagesize[1] + "<br>" );
            }

            string.append( createStringFromIfFound( exif, ExifInterface.TAG_IMAGE_WIDTH, "TAG_IMAGE_WIDTH", all_tags ) );
            string.append( createStringFromIfFound( exif, ExifInterface.TAG_IMAGE_LENGTH, "TAG_IMAGE_LENGTH", all_tags ) );
            string.append( createStringFromIfFound( exif, ExifInterface.TAG_BITS_PER_SAMPLE, "TAG_BITS_PER_SAMPLE", all_tags ) );
            string.append( createStringFromIfFound( exif, ExifInterface.TAG_COMPRESSION, "TAG_COMPRESSION", all_tags ) );
            string.append( createStringFromIfFound( exif, ExifInterface.TAG_PHOTOMETRIC_INTERPRETATION, "TAG_PHOTOMETRIC_INTERPRETATION", all_tags ) );
            string.append( createStringFromIfFound( exif, ExifInterface.TAG_IMAGE_DESCRIPTION, "TAG_IMAGE_DESCRIPTION", all_tags ) );
            string.append( createStringFromIfFound( exif, ExifInterface.TAG_MAKE, "TAG_MAKE", all_tags ) );
            string.append( createStringFromIfFound( exif, ExifInterface.TAG_MODEL, "TAG_MODEL", all_tags ) );
            string.append( createStringFromIfFound( exif, ExifInterface.TAG_STRIP_OFFSETS, "TAG_STRIP_OFFSETS", all_tags ) );
            string.append( createStringFromIfFound( exif, ExifInterface.TAG_ORIENTATION, "TAG_ORIENTATION", all_tags ) );
            string.append( createStringFromIfFound( exif, ExifInterface.TAG_SAMPLES_PER_PIXEL, "TAG_SAMPLES_PER_PIXEL", all_tags ) );
            string.append( createStringFromIfFound( exif, ExifInterface.TAG_ROWS_PER_STRIP, "TAG_ROWS_PER_STRIP", all_tags ) );
            string.append( createStringFromIfFound( exif, ExifInterface.TAG_STRIP_BYTE_COUNTS, "TAG_STRIP_BYTE_COUNTS", all_tags ) );
            string.append( createStringFromIfFound( exif, ExifInterface.TAG_X_RESOLUTION, "TAG_X_RESOLUTION", all_tags ) );
            string.append( createStringFromIfFound( exif, ExifInterface.TAG_Y_RESOLUTION, "TAG_Y_RESOLUTION", all_tags ) );
            string.append( createStringFromIfFound( exif, ExifInterface.TAG_PLANAR_CONFIGURATION, "TAG_PLANAR_CONFIGURATION", all_tags ) );
            string.append( createStringFromIfFound( exif, ExifInterface.TAG_RESOLUTION_UNIT, "TAG_RESOLUTION_UNIT", all_tags ) );
            string.append( createStringFromIfFound( exif, ExifInterface.TAG_TRANSFER_FUNCTION, "TAG_TRANSFER_FUNCTION", all_tags ) );
            string.append( createStringFromIfFound( exif, ExifInterface.TAG_SOFTWARE, "TAG_SOFTWARE", all_tags ) );
            string.append( createStringFromIfFound( exif, ExifInterface.TAG_DATE_TIME, "TAG_DATE_TIME", all_tags ) );
            string.append( createStringFromIfFound( exif, ExifInterface.TAG_ARTIST, "TAG_ARTIST", all_tags ) );
            string.append( createStringFromIfFound( exif, ExifInterface.TAG_WHITE_POINT, "TAG_WHITE_POINT", all_tags ) );
            string.append( createStringFromIfFound( exif, ExifInterface.TAG_PRIMARY_CHROMATICITIES, "TAG_PRIMARY_CHROMATICITIES", all_tags ) );
            string.append( createStringFromIfFound( exif, ExifInterface.TAG_Y_CB_CR_COEFFICIENTS, "TAG_Y_CB_CR_COEFFICIENTS", all_tags ) );
            string.append( createStringFromIfFound( exif, ExifInterface.TAG_Y_CB_CR_SUB_SAMPLING, "TAG_Y_CB_CR_SUB_SAMPLING", all_tags ) );
            string.append( createStringFromIfFound( exif, ExifInterface.TAG_Y_CB_CR_POSITIONING, "TAG_Y_CB_CR_POSITIONING", all_tags ) );
            string.append( createStringFromIfFound( exif, ExifInterface.TAG_REFERENCE_BLACK_WHITE, "TAG_REFERENCE_BLACK_WHITE", all_tags ) );
            string.append( createStringFromIfFound( exif, ExifInterface.TAG_COPYRIGHT, "TAG_COPYRIGHT", all_tags ) );
            string.append( createStringFromIfFound( exif, ExifInterface.TAG_EXIF_IFD, "TAG_EXIF_IFD", all_tags ) );
            string.append( createStringFromIfFound( exif, ExifInterface.TAG_GPS_IFD, "TAG_GPS_IFD", all_tags ) );
            string.append( createStringFromIfFound( exif, ExifInterface.TAG_JPEG_INTERCHANGE_FORMAT, "TAG_JPEG_INTERCHANGE_FORMAT", all_tags ) );
            string.append( createStringFromIfFound( exif, ExifInterface.TAG_JPEG_INTERCHANGE_FORMAT_LENGTH, "TAG_JPEG_INTERCHANGE_FORMAT_LENGTH", all_tags ) );
            string.append( createStringFromIfFound( exif, ExifInterface.TAG_EXPOSURE_TIME, "TAG_EXPOSURE_TIME", all_tags ) );
            string.append( createStringFromIfFound( exif, ExifInterface.TAG_F_NUMBER, "TAG_F_NUMBER", all_tags ) );
            string.append( createStringFromIfFound( exif, ExifInterface.TAG_EXPOSURE_PROGRAM, "TAG_EXPOSURE_PROGRAM", all_tags ) );
            string.append( createStringFromIfFound( exif, ExifInterface.TAG_SPECTRAL_SENSITIVITY, "TAG_SPECTRAL_SENSITIVITY", all_tags ) );
            string.append( createStringFromIfFound( exif, ExifInterface.TAG_ISO_SPEED_RATINGS, "TAG_ISO_SPEED_RATINGS", all_tags ) );
            string.append( createStringFromIfFound( exif, ExifInterface.TAG_OECF, "TAG_OECF", all_tags ) );
            string.append( createStringFromIfFound( exif, ExifInterface.TAG_EXIF_VERSION, "TAG_EXIF_VERSION", all_tags ) );
            string.append( createStringFromIfFound( exif, ExifInterface.TAG_DATE_TIME_ORIGINAL, "TAG_DATE_TIME_ORIGINAL", all_tags ) );
            string.append( createStringFromIfFound( exif, ExifInterface.TAG_DATE_TIME_DIGITIZED, "TAG_DATE_TIME_DIGITIZED", all_tags ) );
            string.append( createStringFromIfFound( exif, ExifInterface.TAG_COMPONENTS_CONFIGURATION, "TAG_COMPONENTS_CONFIGURATION", all_tags ) );
            string.append( createStringFromIfFound( exif, ExifInterface.TAG_COMPRESSED_BITS_PER_PIXEL, "TAG_COMPRESSED_BITS_PER_PIXEL", all_tags ) );
            string.append( createStringFromIfFound( exif, ExifInterface.TAG_SHUTTER_SPEED_VALUE, "TAG_SHUTTER_SPEED_VALUE", all_tags ) );
            string.append( createStringFromIfFound( exif, ExifInterface.TAG_APERTURE_VALUE, "TAG_APERTURE_VALUE", all_tags ) );
            string.append( createStringFromIfFound( exif, ExifInterface.TAG_BRIGHTNESS_VALUE, "TAG_BRIGHTNESS_VALUE", all_tags ) );
            string.append( createStringFromIfFound( exif, ExifInterface.TAG_EXPOSURE_BIAS_VALUE, "TAG_EXPOSURE_BIAS_VALUE", all_tags ) );
            string.append( createStringFromIfFound( exif, ExifInterface.TAG_MAX_APERTURE_VALUE, "TAG_MAX_APERTURE_VALUE", all_tags ) );
            string.append( createStringFromIfFound( exif, ExifInterface.TAG_SUBJECT_DISTANCE, "TAG_SUBJECT_DISTANCE", all_tags ) );
            string.append( createStringFromIfFound( exif, ExifInterface.TAG_METERING_MODE, "TAG_METERING_MODE", all_tags ) );
            string.append( createStringFromIfFound( exif, ExifInterface.TAG_LIGHT_SOURCE, "TAG_LIGHT_SOURCE", all_tags ) );
            string.append( createStringFromIfFound( exif, ExifInterface.TAG_FLASH, "TAG_FLASH", all_tags ) );
            string.append( createStringFromIfFound( exif, ExifInterface.TAG_FOCAL_LENGTH, "TAG_FOCAL_LENGTH", all_tags ) );
            string.append( createStringFromIfFound( exif, ExifInterface.TAG_SUBJECT_AREA, "TAG_SUBJECT_AREA", all_tags ) );
            string.append( createStringFromIfFound( exif, ExifInterface.TAG_SUB_SEC_TIME, "TAG_SUB_SEC_TIME", all_tags ) );
            string.append( createStringFromIfFound( exif, ExifInterface.TAG_SUB_SEC_TIME_ORIGINAL, "TAG_SUB_SEC_TIME_ORIGINAL", all_tags ) );
            string.append( createStringFromIfFound( exif, ExifInterface.TAG_SUB_SEC_TIME_DIGITIZED, "TAG_SUB_SEC_TIME_DIGITIZED", all_tags ) );
            string.append( createStringFromIfFound( exif, ExifInterface.TAG_FLASHPIX_VERSION, "TAG_FLASHPIX_VERSION", all_tags ) );
            string.append( createStringFromIfFound( exif, ExifInterface.TAG_COLOR_SPACE, "TAG_COLOR_SPACE", all_tags ) );
            string.append( createStringFromIfFound( exif, ExifInterface.TAG_PIXEL_X_DIMENSION, "TAG_PIXEL_X_DIMENSION", all_tags ) );
            string.append( createStringFromIfFound( exif, ExifInterface.TAG_PIXEL_Y_DIMENSION, "TAG_PIXEL_Y_DIMENSION", all_tags ) );
            string.append( createStringFromIfFound( exif, ExifInterface.TAG_RELATED_SOUND_FILE, "TAG_RELATED_SOUND_FILE", all_tags ) );
            string.append( createStringFromIfFound( exif, ExifInterface.TAG_INTEROPERABILITY_IFD, "TAG_INTEROPERABILITY_IFD", all_tags ) );
            string.append( createStringFromIfFound( exif, ExifInterface.TAG_FLASH_ENERGY, "TAG_FLASH_ENERGY", all_tags ) );
            string.append( createStringFromIfFound( exif, ExifInterface.TAG_SPATIAL_FREQUENCY_RESPONSE, "TAG_SPATIAL_FREQUENCY_RESPONSE", all_tags ) );
            string.append( createStringFromIfFound( exif, ExifInterface.TAG_FOCAL_PLANE_X_RESOLUTION, "TAG_FOCAL_PLANE_X_RESOLUTION", all_tags ) );
            string.append( createStringFromIfFound( exif, ExifInterface.TAG_FOCAL_PLANE_Y_RESOLUTION, "TAG_FOCAL_PLANE_Y_RESOLUTION", all_tags ) );
            string.append( createStringFromIfFound( exif, ExifInterface.TAG_FOCAL_PLANE_RESOLUTION_UNIT, "TAG_FOCAL_PLANE_RESOLUTION_UNIT", all_tags ) );
            string.append( createStringFromIfFound( exif, ExifInterface.TAG_SUBJECT_LOCATION, "TAG_SUBJECT_LOCATION", all_tags ) );
            string.append( createStringFromIfFound( exif, ExifInterface.TAG_EXPOSURE_INDEX, "TAG_EXPOSURE_INDEX", all_tags ) );
            string.append( createStringFromIfFound( exif, ExifInterface.TAG_SENSING_METHOD, "TAG_SENSING_METHOD", all_tags ) );
            string.append( createStringFromIfFound( exif, ExifInterface.TAG_FILE_SOURCE, "TAG_FILE_SOURCE", all_tags ) );
            string.append( createStringFromIfFound( exif, ExifInterface.TAG_SCENE_TYPE, "TAG_SCENE_TYPE", all_tags ) );
            string.append( createStringFromIfFound( exif, ExifInterface.TAG_CFA_PATTERN, "TAG_CFA_PATTERN", all_tags ) );
            string.append( createStringFromIfFound( exif, ExifInterface.TAG_CUSTOM_RENDERED, "TAG_CUSTOM_RENDERED", all_tags ) );
            string.append( createStringFromIfFound( exif, ExifInterface.TAG_EXPOSURE_MODE, "TAG_EXPOSURE_MODE", all_tags ) );
            string.append( createStringFromIfFound( exif, ExifInterface.TAG_WHITE_BALANCE, "TAG_WHITE_BALANCE", all_tags ) );
            string.append( createStringFromIfFound( exif, ExifInterface.TAG_DIGITAL_ZOOM_RATIO, "TAG_DIGITAL_ZOOM_RATIO", all_tags ) );
            string.append( createStringFromIfFound( exif, ExifInterface.TAG_FOCAL_LENGTH_IN_35_MM_FILE, "TAG_FOCAL_LENGTH_IN_35_MM_FILE", all_tags ) );
            string.append( createStringFromIfFound( exif, ExifInterface.TAG_SCENE_CAPTURE_TYPE, "TAG_SCENE_CAPTURE_TYPE", all_tags ) );
            string.append( createStringFromIfFound( exif, ExifInterface.TAG_GAIN_CONTROL, "TAG_GAIN_CONTROL", all_tags ) );
            string.append( createStringFromIfFound( exif, ExifInterface.TAG_CONTRAST, "TAG_CONTRAST", all_tags ) );
            string.append( createStringFromIfFound( exif, ExifInterface.TAG_SATURATION, "TAG_SATURATION", all_tags ) );
            string.append( createStringFromIfFound( exif, ExifInterface.TAG_SHARPNESS, "TAG_SHARPNESS", all_tags ) );
            string.append( createStringFromIfFound( exif, ExifInterface.TAG_DEVICE_SETTING_DESCRIPTION, "TAG_DEVICE_SETTING_DESCRIPTION", all_tags ) );
            string.append( createStringFromIfFound( exif, ExifInterface.TAG_SUBJECT_DISTANCE_RANGE, "TAG_SUBJECT_DISTANCE_RANGE", all_tags ) );
            string.append( createStringFromIfFound( exif, ExifInterface.TAG_IMAGE_UNIQUE_ID, "TAG_IMAGE_UNIQUE_ID", all_tags ) );
            string.append( createStringFromIfFound( exif, ExifInterface.TAG_GPS_VERSION_ID, "TAG_GPS_VERSION_ID", all_tags ) );
            string.append( createStringFromIfFound( exif, ExifInterface.TAG_GPS_LATITUDE_REF, "TAG_GPS_LATITUDE_REF", all_tags ) );
            string.append( createStringFromIfFound( exif, ExifInterface.TAG_GPS_LATITUDE, "TAG_GPS_LATITUDE", all_tags ) );
            string.append( createStringFromIfFound( exif, ExifInterface.TAG_GPS_LONGITUDE_REF, "TAG_GPS_LONGITUDE_REF", all_tags ) );
            string.append( createStringFromIfFound( exif, ExifInterface.TAG_GPS_LONGITUDE, "TAG_GPS_LONGITUDE", all_tags ) );
            string.append( createStringFromIfFound( exif, ExifInterface.TAG_GPS_ALTITUDE_REF, "TAG_GPS_ALTITUDE_REF", all_tags ) );
            string.append( createStringFromIfFound( exif, ExifInterface.TAG_GPS_ALTITUDE, "TAG_GPS_ALTITUDE", all_tags ) );
            string.append( createStringFromIfFound( exif, ExifInterface.TAG_GPS_TIME_STAMP, "TAG_GPS_TIME_STAMP", all_tags ) );
            string.append( createStringFromIfFound( exif, ExifInterface.TAG_GPS_SATTELLITES, "TAG_GPS_SATTELLITES", all_tags ) );
            string.append( createStringFromIfFound( exif, ExifInterface.TAG_GPS_STATUS, "TAG_GPS_STATUS", all_tags ) );
            string.append( createStringFromIfFound( exif, ExifInterface.TAG_GPS_MEASURE_MODE, "TAG_GPS_MEASURE_MODE", all_tags ) );
            string.append( createStringFromIfFound( exif, ExifInterface.TAG_GPS_DOP, "TAG_GPS_DOP", all_tags ) );
            string.append( createStringFromIfFound( exif, ExifInterface.TAG_GPS_SPEED_REF, "TAG_GPS_SPEED_REF", all_tags ) );
            string.append( createStringFromIfFound( exif, ExifInterface.TAG_GPS_SPEED, "TAG_GPS_SPEED", all_tags ) );
            string.append( createStringFromIfFound( exif, ExifInterface.TAG_GPS_TRACK_REF, "TAG_GPS_TRACK_REF", all_tags ) );
            string.append( createStringFromIfFound( exif, ExifInterface.TAG_GPS_TRACK, "TAG_GPS_TRACK", all_tags ) );
            string.append( createStringFromIfFound( exif, ExifInterface.TAG_GPS_IMG_DIRECTION_REF, "TAG_GPS_IMG_DIRECTION_REF", all_tags ) );
            string.append( createStringFromIfFound( exif, ExifInterface.TAG_GPS_IMG_DIRECTION, "TAG_GPS_IMG_DIRECTION", all_tags ) );
            string.append( createStringFromIfFound( exif, ExifInterface.TAG_GPS_MAP_DATUM, "TAG_GPS_MAP_DATUM", all_tags ) );
            string.append( createStringFromIfFound( exif, ExifInterface.TAG_GPS_DEST_LATITUDE_REF, "TAG_GPS_DEST_LATITUDE_REF", all_tags ) );
            string.append( createStringFromIfFound( exif, ExifInterface.TAG_GPS_DEST_LATITUDE, "TAG_GPS_DEST_LATITUDE", all_tags ) );
            string.append( createStringFromIfFound( exif, ExifInterface.TAG_GPS_DEST_LONGITUDE_REF, "TAG_GPS_DEST_LONGITUDE_REF", all_tags ) );
            string.append( createStringFromIfFound( exif, ExifInterface.TAG_GPS_DEST_LONGITUDE, "TAG_GPS_DEST_LONGITUDE", all_tags ) );
            string.append( createStringFromIfFound( exif, ExifInterface.TAG_GPS_DEST_BEARING_REF, "TAG_GPS_DEST_BEARING_REF", all_tags ) );
            string.append( createStringFromIfFound( exif, ExifInterface.TAG_GPS_DEST_BEARING, "TAG_GPS_DEST_BEARING", all_tags ) );
            string.append( createStringFromIfFound( exif, ExifInterface.TAG_GPS_DEST_DISTANCE_REF, "TAG_GPS_DEST_DISTANCE_REF", all_tags ) );
            string.append( createStringFromIfFound( exif, ExifInterface.TAG_GPS_DEST_DISTANCE, "TAG_GPS_DEST_DISTANCE", all_tags ) );
            string.append( createStringFromIfFound( exif, ExifInterface.TAG_GPS_PROCESSING_METHOD, "TAG_GPS_PROCESSING_METHOD", all_tags ) );
            string.append( createStringFromIfFound( exif, ExifInterface.TAG_GPS_AREA_INFORMATION, "TAG_GPS_AREA_INFORMATION", all_tags ) );
            string.append( createStringFromIfFound( exif, ExifInterface.TAG_GPS_DATE_STAMP, "TAG_GPS_DATE_STAMP", all_tags ) );
            string.append( createStringFromIfFound( exif, ExifInterface.TAG_GPS_DIFFERENTIAL, "TAG_GPS_DIFFERENTIAL", all_tags ) );
            string.append( createStringFromIfFound( exif, ExifInterface.TAG_INTEROPERABILITY_INDEX, "TAG_INTEROPERABILITY_INDEX", all_tags ) );
            string.append( createStringFromIfFound( exif, ExifInterface.TAG_LENS_MAKE, "TAG_LENS_MAKE", all_tags ) );
            string.append( createStringFromIfFound( exif, ExifInterface.TAG_LENS_MODEL, "TAG_LENS_MODEL", all_tags ) );
            string.append( createStringFromIfFound( exif, ExifInterface.TAG_LENS_SPECS, "TAG_LENS_SPECS", all_tags ) );
            string.append( createStringFromIfFound( exif, ExifInterface.TAG_SENSITIVITY_TYPE, "TAG_SENSITIVITY_TYPE", all_tags ) );

            List<ExifTag> tags = exif.getTagsForTagId( exif.getTrueTagKey( ExifInterface.TAG_ORIENTATION ) );

            string.append( "<br>--------------<br>" );
            string.append( "<b>Total tags:</b> " + mTagsCount + "<br>" );
            string.append( "<b>Has Thumbnail:</b> " + exif.hasThumbnail() + "<br>" );


            ExifTag tag = exif.getTag( ExifInterface.TAG_EXIF_VERSION );
            if( null != tag ) {
                string.append( "<b>Exif version: </b> " + tag.getValueAsString() + "<br>" );
            }

            String latitude = exif.getLatitude();
            String longitude = exif.getLongitude();

            if( null != latitude && null != longitude ) {
                string.append( "<b>Latitude: </b> " + latitude + "<br>" );
                string.append( "<b>Longitude: </b> " + longitude + "<br>" );
            }

            Integer val = exif.getTagIntValue( ExifInterface.TAG_ORIENTATION );
            int orientation = 0;
            if( null != val ) {
                orientation = ExifInterface.getRotationForOrientationValue( val.shortValue() );
            }
            string.append( "<b>Orientation: </b> " + orientation + "<br>" );


            double aperture = exif.getApertureSize();
            if( aperture > 0 ) {
                string.append( "<b>Aperture Size: </b> " + String.format( "%.2f", aperture ) + "<br>" );
            }

            ExifTag shutterSpeed = exif.getTag( ExifInterface.TAG_SHUTTER_SPEED_VALUE );
            if( null != shutterSpeed ) {
                double speed = shutterSpeed.getValueAsRational( 0 ).toDouble();
                Log.d( LOG_TAG, "speed: " + speed );

                NumberFormat decimalFormatter = DecimalFormat.getNumberInstance();
                decimalFormatter.setMaximumFractionDigits( 1 );
                String speedString = "1/" + decimalFormatter.format( Math.pow( 2, speed ) ) + "s";
                string.append( "<b>Shutter Speed: </b> " + speedString + "<br>" );
            }

            String lensModel = exif.getLensModelDescription();
            if( null != lensModel ) {
                string.append( "<b>Lens Specifications: </b> " + lensModel + "<br>" );
            }

            short process = exif.getJpegProcess();
            string.append( "<b>JPEG Process: </b> " + process + "<br>" );
            exifText.setText( Html.fromHtml(string.toString()) );

            double[] latlon = exif.getLatLongAsDoubles();
            if( null != latlon ) {
                GetGeoLocationTask task = new GetGeoLocationTask();
                task.execute( latlon[0], latlon[1] );
            }
        }
    }

    private String createStringFromIfFound( ExifInterface exif, int key, String label, final List<ExifTag> all_tags ) {
        String exifString = "";
        ExifTag tag = exif.getTag( key );
        if( null != tag ) {

            all_tags.remove( tag );

            int ifid = tag.getIfd();
            String ifdid_str = "";

            switch( ifid ) {
                case IfdId.TYPE_IFD_0:
                    ifdid_str = "ifd0";
                    break;

                case IfdId.TYPE_IFD_1:
                    ifdid_str = "ifd1";
                    break;

                case IfdId.TYPE_IFD_EXIF:
                    ifdid_str = "exif";
                    break;

                case IfdId.TYPE_IFD_GPS:
                    ifdid_str = "gps";
                    break;

                case IfdId.TYPE_IFD_INTEROPERABILITY:
                    ifdid_str = "interop";
                    break;
            }

            mTagsCount++;
            exifString += "<b>" + label + "(" + ifdid_str + "): </b>";

            if( key == ExifInterface.TAG_DATE_TIME || key == ExifInterface.TAG_DATE_TIME_DIGITIZED || key == ExifInterface.TAG_DATE_TIME_ORIGINAL ) {
                Date date = ExifInterface.getDateTime( tag.getValueAsString(), TimeZone.getDefault() );
                if( null != date ) {
                    exifString += java.text.DateFormat.getDateTimeInstance().format( date );
                }
                else {
                    Log.e( LOG_TAG, "failed to format the date" );
                }
            } else {
                exifString += tag.forceGetValueAsString();
            }
            exifString += "<br>";
        }
        else {
			Log.w( LOG_TAG, "'" + label + "' not found" );
        }
        return exifString;
    }

    private class GetGeoLocationTask extends AsyncTask<Double, Void, Address> {

        @Override
        protected Address doInBackground( Double... params ) {

            double lat = params[0];
            double lon = params[1];

            Log.d( LOG_TAG, "lat: " + lat + ", lon: " + lon );

            List<Address> result = null;

            try {
                if( Geocoder.isPresent() ) {
                    Geocoder geo = new Geocoder( CleanImageActivity.this );
                    result = geo.getFromLocation( lat, lon, 1 );
                }
            } catch( Exception e ) {
                e.printStackTrace();
                return null;
            }

            Log.d( LOG_TAG, "result: " + result );

            if( null != result && result.size() > 0 ) {
                return result.get( 0 );
            }

            return null;
        }

        @Override
        protected void onPostExecute( Address result ) {
            super.onPostExecute( result );

            if( isCancelled() || isFinishing() ) return;

            if( null != result ) {

                StringBuilder finalString = new StringBuilder();

                if( null != result.getThoroughfare() ) {
                    finalString.append( result.getThoroughfare() );

                    if( null != result.getSubThoroughfare() ) {
                        finalString.append( " " + result.getSubThoroughfare() );
                    }

                    finalString.append( "\n" );
                }

                if( null != result.getPostalCode() ) {
                    finalString.append( result.getPostalCode() );

                    if( null != result.getLocality() ) {
                        finalString.append( " - " + result.getLocality() + "\n" );
                    }
                }
                else {
                    if( null != result.getLocality() ) {
                        finalString.append( result.getLocality() + "\n" );
                    }
                }

                if( null != result.getCountryName() ) {
                    finalString.append( result.getCountryName() );
                }
                else if( null != result.getCountryCode() ) {
                    finalString.append( result.getCountryCode() );
                }

                if( finalString.length() > 0 ) {
                    finalString.append( "\n" );
                    exifText.append( "\nAddress:\n" );
                    exifText.append( finalString );
                }
            }
        }
    }

}
