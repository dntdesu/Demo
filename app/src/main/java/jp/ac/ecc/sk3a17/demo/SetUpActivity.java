package jp.ac.ecc.sk3a17.demo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.app.ProgressDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.squareup.picasso.Picasso;
import com.theartofdev.edmodo.cropper.CropImage;
import com.theartofdev.edmodo.cropper.CropImageView;

import java.util.HashMap;

import de.hdodenhof.circleimageview.CircleImageView;

public class SetUpActivity extends AppCompatActivity {

    private Button btn_save;
    private EditText userName, fullName;
    private CircleImageView profileImage;
    private FirebaseAuth mAuth;
    private DatabaseReference userRef;
    private StorageReference userProfileImageRef; //to save profile image to storage
    private String currentUserId;
    private ProgressDialog loadingBar;
    final static int galleryPick = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_set_up);

        btn_save = findViewById(R.id.setup_save);
        userName = findViewById(R.id.setup_username);
        fullName = findViewById(R.id.setup_full_name);
        profileImage = findViewById(R.id.setup_profile_image);
        loadingBar = new ProgressDialog(this);

        mAuth = FirebaseAuth.getInstance();
        //get current useID through mAuth
        currentUserId = mAuth.getCurrentUser().getUid();
        //create a ref named userId before
        userRef = FirebaseDatabase.getInstance().getReference().child("Users").child(currentUserId);

        //create a folder to save file
        userProfileImageRef = FirebaseStorage.getInstance().getReference().child("Profile Images");

        //Save button clicked
        btn_save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SaveFirstTimeLogInInfo();
            }
        });

        //Image clicked
        profileImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //access to local gallery
                Intent galleryIntent = new Intent();
                galleryIntent.setAction(Intent.ACTION_GET_CONTENT);
                galleryIntent.setType("image/*");
                startActivityForResult(galleryIntent, galleryPick);
            }
        });

        //display avatar in Circle Image
        userRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists() && dataSnapshot.hasChild("Profile Images")){
                    String image = dataSnapshot.child("Profile Images").getValue().toString();
                    //use picasso to display profile image
                    Picasso.get().load(image).into(profileImage);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        //check if image in local gallery is selected
        if (requestCode == galleryPick && resultCode == RESULT_OK && data!=null){
            Uri imageUri = data.getData();
            //start CropImage Activity
            CropImage.activity(imageUri)
                    .setGuidelines(CropImageView.Guidelines.ON)
                    .setAspectRatio(1,1)
                    .start(this);
        }
        //when crop button is clicked, this will be called
        if (requestCode == CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE){
            CropImage.ActivityResult result = CropImage.getActivityResult(data);
            loadingBar.setTitle("Updating");
            loadingBar.setMessage("Please wait a moment");
            loadingBar.show();
            loadingBar.setCanceledOnTouchOutside(false);
            if (resultCode == RESULT_OK){
                Uri resultUri = result.getUri();

                //save image to storage, type of file is jpg
                final StorageReference filePath = userProfileImageRef.child(currentUserId + ".jpg");
                filePath.putFile(resultUri).addOnCompleteListener(new OnCompleteListener<UploadTask.TaskSnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<UploadTask.TaskSnapshot> task) {
                        if (task.isSuccessful()){
                            loadingBar.dismiss();
                            //save image link to realtime database
                            filePath.getDownloadUrl().addOnSuccessListener(new OnSuccessListener<Uri>() {
                                @Override
                                public void onSuccess(Uri uri) {
                                    final String downloadUrl = uri.toString();
                                    userRef.child("Profile Images").setValue(downloadUrl)
                                            .addOnCompleteListener(new OnCompleteListener<Void>() {
                                                @Override
                                                public void onComplete(@NonNull Task<Void> task) {
                                                    if (task.isSuccessful()) {

                                                    } else {
                                                        String message = task.getException().getMessage();
                                                        Toast.makeText(SetUpActivity.this, "Error Occurred..." + message, Toast.LENGTH_SHORT).show();
                                                        loadingBar.dismiss();
                                                    }
                                                }
                                            });
                                }
                            });
                        }
                    }
                });
            }
        }
    }

    private void SaveFirstTimeLogInInfo() {
        //check if has any field is empty
        String mapUserName = userName.getText().toString();
        String mapFullName = fullName.getText().toString();
        if (TextUtils.isEmpty(mapFullName) || TextUtils.isEmpty(mapUserName)) {
            Toast.makeText(this, "Please fill all the fields", Toast.LENGTH_SHORT).show();
        } else {

            loadingBar.setTitle("Updating");
            loadingBar.setMessage("Please wait a moment");
            loadingBar.show();
            loadingBar.setCanceledOnTouchOutside(true);

            HashMap userMap = new HashMap();
            userMap.put("User Name", mapUserName);
            userMap.put("Full Name", mapFullName);
            userMap.put("Gender", "none");

            userRef.updateChildren(userMap).addOnCompleteListener(new OnCompleteListener() {
                @Override
                public void onComplete(@NonNull Task task) {
                    if (task.isSuccessful()){
                        Toast.makeText(SetUpActivity.this, "Saved", Toast.LENGTH_SHORT).show();
                        loadingBar.dismiss();
                        SendToMain();
                    }else{
                        Toast.makeText(SetUpActivity.this, "Oops..Something went wrong", Toast.LENGTH_SHORT).show();
                        loadingBar.dismiss();
                    }
                }
            });
        }

    }

    private void SendToMain(){
        Intent mainIntent = new Intent(SetUpActivity.this, MainActivity.class);
        mainIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        mainIntent.putExtra("ID", currentUserId);
        startActivity(mainIntent);
        finish();
    }
}
