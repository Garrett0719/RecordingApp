package comp5216.sydney.edu.au.recordingapp;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaPlayer;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.Toast;
import android.widget.VideoView;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    public String photoFileName = "photo.jpg";
    public String videoFileName = "video.mp4";

    public File file;


    private static final int REQUEST_TAKE_PHOTO = 1;
    private static final int REQUEST_RECORD_VIDEO = 2;

    MarshmallowPermission marshmallowPermission = new MarshmallowPermission(this);

    GridView gridView;

    //Store the path to all media files
    private List<String> mediaUri= new ArrayList<String>();

    //Create two HashMap to store the image file data and video file data that are not synchronized
    private static HashMap<String,Uri> imageUri= new HashMap<String,Uri>();
    private static HashMap<String,Uri> videoUri= new HashMap<String,Uri>();

    //Preparing for the use of the firebase storage
    FirebaseStorage storage = FirebaseStorage.getInstance();
    StorageReference storageRef = storage.getReference();

    Button makeMediaButton;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        gridView = (GridView) findViewById(R.id.GridView);
        makeMediaButton = (Button)findViewById(R.id.makeMedia);

        //take photo
        makeMediaButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!marshmallowPermission.checkPermissionForCamera()
                        || !marshmallowPermission.checkPermissionForExternalStorage()) {
                    marshmallowPermission.requestPermissionForCamera();
                } else {
                    Intent photoIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

                    if (photoIntent.resolveActivity(getPackageManager()) != null) {

                        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss",
                                Locale.getDefault()).format(new Date());
                        photoFileName = "IMG_" + timeStamp + ".jpg";
                        Uri file_uri = getFileUri(photoFileName, 1);
                        photoIntent.putExtra(MediaStore.EXTRA_OUTPUT, file_uri);
                        startActivityForResult(photoIntent, REQUEST_TAKE_PHOTO);
                    }
                }
            }
        });

        //record video
        makeMediaButton.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                if (!marshmallowPermission.checkPermissionForCamera()
                        || !marshmallowPermission.checkPermissionForExternalStorage()) {
                    marshmallowPermission.requestPermissionForCamera();
                } else {
                    Intent recordIntent = new Intent (MediaStore.ACTION_VIDEO_CAPTURE);

                    String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss",
                            Locale.getDefault()).format (new Date());
                    videoFileName = "VIDEO_" + timeStamp + ".mp4";

                    Uri file_uri = getFileUri(videoFileName, 2);

                    recordIntent.putExtra(MediaStore.EXTRA_OUTPUT, file_uri);
                    startActivityForResult(recordIntent, REQUEST_RECORD_VIDEO);
                }
                return false;
            }
        });

        //Get the default root storage path
        String storePath = Environment.getExternalStorageDirectory() + "/";

        //Search for media files under this path
        getMediaFiles(storePath);

        if(mediaUri.size() < 1){
            return;
        }

        gridView.setAdapter(new GridViewAdapter(mediaUri,MainActivity.this));

        //Create a background thread to be ready to synchronise unsynchronised data
        new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    //Determine if there is a network connection
                    if (checkNetworkConnected(MainActivity.this) && checkWifiConnected(MainActivity.this)) {

                        //Determine if there is unsynchronised data
                        if (videoUri.size() >= 1 || imageUri.size() >= 1) {
                            backupData();
                            try{
                                // Reducing the CPU load by sleeping threads after synchronizing data
                                Thread.sleep(600000);
                            } catch (Exception e){
                                e.printStackTrace();
                            }
                        } else{
                            try{
                                //No unsynchronized data threads hibernate
                                Thread.sleep(600000);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    } else {
                        // No network, then the thread sleeps
                        try{
                            Thread.sleep(600000);
                        } catch (Exception e){
                            e.printStackTrace();
                        }
                    }
                }
            }
        }).start();
    }

    //Create an adapter for the GridView
    class GridViewAdapter extends BaseAdapter {

        Context theContext;

        //Storage file path for media files
        List<String> mediaUri;

        public GridViewAdapter(List<String> mediaMap, Context context) {
            this.mediaUri = mediaMap;
            this.theContext = context;
        }


        @Override
        public int getCount() {
            return mediaUri.size();
        }

        @Override
        public Object getItem(int position) {
            return position;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View covertView, ViewGroup parent) {

            //init GridView_item layout
            LayoutInflater layoutInflater = (LayoutInflater) theContext.getSystemService((Context.LAYOUT_INFLATER_SERVICE));
            covertView = layoutInflater.inflate(R.layout.girdview_item, null);
            ImageView imageView = covertView.findViewById(R.id.imageView);
            VideoView videoView = covertView.findViewById(R.id.videoView);

            //Determine if it is a picture
            if (mediaUri.get(position).contains("jpg")) {

                //Get image thumbnails
                Bitmap bitmap = BitmapFactory.decodeFile(mediaUri.get(position));

                //Make thumbnails appear on ImageView
                imageView.setImageBitmap(bitmap);
            } else {

                videoView.setVideoURI(Uri.parse(mediaUri.get(position)));
                videoView.requestFocus();
                videoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                    @Override
                    public void onPrepared(MediaPlayer mediaPlayer) {

                        //Get the thumbnail of the first second of the video
                        mediaPlayer.seekTo(10000);
                        videoView.start();

                    }
                });


            }
            return covertView;
            }

        }

    private static boolean isMediaFile(String path){
        //Search only media files with file types jpg and mp4
        if(path.contains("jpg") || path.contains("mp4")){
            return true;
        }
        return false;
    }

    private void getMediaFiles(String url){
        File file = new File(url);
        //store all the files into an array
        File[] files = file.listFiles();
        try{
            //Iterate through all files
            for(File f : files) {
                if(f.isDirectory()) {
                    getMediaFiles(f.getAbsolutePath());
                } else {
                    if(isMediaFile(f.getPath())) {
                        mediaUri.add(f.getPath());
                    }
                }
            }
        } catch (Exception e){
            e.printStackTrace();
        }

    }

    public Uri getFileUri (String fileName, int type) {
        Uri fileUri = null;
        try{
            String typestr = null;
            if(type == 1){
               typestr = "Pictures";
            }
            else if(type == 2) {
                typestr = "Movies";
            } else{
                System.out.println("typestr is error");
            }
                File mediaStorageDir = new File (Environment.getExternalStorageDirectory().toString()
                        + File.separator + typestr);

                if (!mediaStorageDir.exists()) {
                    mediaStorageDir.mkdirs();
                }

                file = new File(mediaStorageDir, fileName);

                // Determine the SDK version and use fileprovider if it is greater than 24
                if(Build.VERSION.SDK_INT >= 24) {
                    fileUri = FileProvider.getUriForFile(
                            this.getApplicationContext(),
                            "comp5216.sydney.edu.au.recordingapp.fileProvider",
                            file);
                } else{
                    fileUri = Uri.fromFile(mediaStorageDir);
                }
                return fileUri;
            } catch (Exception ex) {
            Log.d("get uri has error", ex.getStackTrace().toString());
        }
        return fileUri;
    }



    //Take photos
    public void onClickTakePhoto(View v) {
        if (!marshmallowPermission.checkPermissionForCamera()
                || !marshmallowPermission.checkPermissionForExternalStorage()) {
            marshmallowPermission.requestPermissionForCamera();
        } else {
            Intent photoIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

            if (photoIntent.resolveActivity(getPackageManager()) != null) {

                String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss",
                        Locale.getDefault()).format(new Date());
                photoFileName = "IMG_" + timeStamp + ".jpg";
                Uri file_uri = getFileUri(photoFileName, 1);
                photoIntent.putExtra(MediaStore.EXTRA_OUTPUT, file_uri);
                startActivityForResult(photoIntent, REQUEST_TAKE_PHOTO);
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        //Get the image file just taken
        if(requestCode == REQUEST_TAKE_PHOTO){
            Uri fileUri = null;
            File mediaStorageDir = new File (Environment.getExternalStorageDirectory().toString()
                    + File.separator + "Picture");
            if(Build.VERSION.SDK_INT >= 24) {
                fileUri = FileProvider.getUriForFile(
                        this.getApplicationContext(),
                        "comp5216.sydney.edu.au.recordingapp.fileProvider",
                        file);
            } else{
                fileUri = Uri.fromFile(mediaStorageDir);
            }
            //Determine if the network is connected
                if(checkNetworkConnected(MainActivity.this) && checkWifiConnected(MainActivity.this)){
                    //If connected, then upload file
                    uploadImageFile(file.getName(), fileUri);
                } else{
                    //If there is no network connection,
                    // then the file data will be stored in the HashMap first and wait for upload
                    imageUri.put(file.getName(),fileUri);
                }
        }
        else if(requestCode == REQUEST_RECORD_VIDEO){
            Uri fileUri = null;

            //Get the video file just taken
            File mediaStorageDir = new File (Environment.getExternalStorageDirectory().toString()
                    + File.separator + "Movies");
            if(Build.VERSION.SDK_INT >= 24) {
                fileUri = FileProvider.getUriForFile(
                        this.getApplicationContext(),
                        "comp5216.sydney.edu.au.recordingapp.fileProvider",
                        file);
            } else{

                fileUri = Uri.fromFile(mediaStorageDir);
            }
            //Determine if the network is connected
                if(checkNetworkConnected(MainActivity.this) && checkWifiConnected(MainActivity.this)){
                    //If connected, then upload file
                    upLoadVideoFile(file.getName(), fileUri);
                }
                else{
                    //If there is no network connection,
                    // then the file data will be stored in the HashMap first and wait for upload
                    videoUri.put(file.getName(),fileUri);
                }
        }
            finish();
            Intent intent = new Intent(this, MainActivity.class);
            startActivity(intent);

    }

    //Uploading video files to firebase
    private void upLoadVideoFile(String name, Uri fileUri) {
        StorageReference videoRef = storageRef.child("video/" + name);
        videoRef.putFile(fileUri).addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
            @Override
            public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                videoRef.getDownloadUrl().addOnSuccessListener(new OnSuccessListener<Uri>() {
                    @Override
                    public void onSuccess(Uri uri) {
                        Log.d("tag","Success upload" + uri.toString());
                    }
                });
                Toast.makeText(MainActivity.this,"Video Is Uploaded",Toast.LENGTH_SHORT).show();
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Toast.makeText(MainActivity.this,"upload video fail",Toast.LENGTH_SHORT).show();
            }
        });
    }

    // a method to determine if the network is connected
    public boolean checkWifiConnected(Context theContext) {
        if (theContext != null) {
            ConnectivityManager connectivityManager = (ConnectivityManager) theContext
                    .getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo wiFiNetworkInfo = connectivityManager
                    .getNetworkInfo(ConnectivityManager.TYPE_WIFI);
            if (wiFiNetworkInfo != null) {
                return wiFiNetworkInfo.isAvailable();
            }
        }
        return false;
    }

    //a method to determine if the wifi is connected
    public boolean checkNetworkConnected(Context theContext) {
        if (theContext != null) {
            ConnectivityManager connectivityManager = (ConnectivityManager) theContext
                    .getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
            if (networkInfo != null) {
                return networkInfo.isAvailable();
            }
        }
        return false;
    }

    //Uploading image files to firebase
    private void uploadImageFile(String fileName, Uri fileUri) {
        StorageReference imageRef = storageRef.child("images/" + fileName);
        imageRef.putFile(fileUri).addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
            @Override
            public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                imageRef.getDownloadUrl().addOnSuccessListener(new OnSuccessListener<Uri>() {
                    @Override
                    public void onSuccess(Uri uri) {
                        Log.d("tag","Success upload" + uri.toString());
                    }
                });
                Toast.makeText(MainActivity.this,"Image Is Uploaded",Toast.LENGTH_SHORT).show();
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Toast.makeText(MainActivity.this,"upload image fail",Toast.LENGTH_SHORT).show();
            }
        });
    }

    //Button function for manual data synchronization
    public void onBackupDataClick (View v){

            AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
            builder.setTitle(("Backup Data"))
                    .setMessage("You are not connected to WiFi. Would you like to proceed ?")
                    .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            //Upload data with or without internet connection
                            backupData();
                        }
                    })
                    .setNegativeButton("No", new DialogInterface.OnClickListener() {
                        //Wait for a network link before uploading data
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            if (checkNetworkConnected(MainActivity.this) && checkWifiConnected(MainActivity.this)) {
                                backupData();
                            } else {
                                finish();
                                Intent intent = new Intent(getApplicationContext(), MainActivity.class);
                                startActivity(intent);
                            }
                        }
                    });
            builder.create().show();
        }

    // Method of synchronizing data
    private void backupData (){
        //Detects the presence of unsynchronized image data
        if(imageUri.size() >= 1) {
            //Iterating through a HashMap of images
            for (String imageName : imageUri.keySet()) {
                //Upload data
                uploadImageFile(imageName, imageUri.get(imageName));
            }
            //Clear the synchronized data
            imageUri.clear();
        }
        //Detects the presence of unsynchronized image data
        if(videoUri.size() >= 1) {
            //Iterating through a HashMap of video
            for (String videoName : videoUri.keySet()) {
                //Upload data
                upLoadVideoFile(videoName, videoUri.get(videoName));
            }
            //Clear the synchronized data
            videoUri.clear();
        }
    }
}