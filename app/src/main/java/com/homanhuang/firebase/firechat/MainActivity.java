/**
 * Copyright Google Inc. All Rights Reserved.
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.homanhuang.firebase.firechat;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.DataSetObserver;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.OpenableColumns;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.webkit.MimeTypeMap;
import android.widget.AbsListView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.firebase.ui.auth.AuthUI;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageMetadata;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.homanhuang.firebase.firechat.R;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    /** Google DRIVE_OPEN Intent action. */
    private static final String ACTION_DRIVE_OPEN = "com.google.android.apps.drive.DRIVE_OPEN";
    /** Google Drive file ID key. */
    private static final String EXTRA_FILE_ID = "resourceId";
    /** Google Drive file ID. */
    private String mFileId;
    GoogleSignInClient mGoogleSignInClient;

    private GoogleSignInClient buildGoogleSignInClient() {
        GoogleSignInOptions signInOptions =
                new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestScopes(Drive.SCOPE_FILE)
                        .build();
        return GoogleSignIn.getClient(this, signInOptions);
    }

    private String reqCode2String(int rc) {
        String s = "";
        switch(rc) {
            case RC_PHOTO_PICKER:
                s = "RC_PHOTO_PICKER";
                break;
            case RC_SIGN_IN:
                s = "RC_SIGN_IN";
                break;
            default:
                s = "Code Not Found!";
                break;
        }
        return s;
    }

    // For Photo Picker and storage
    private static final int RC_PHOTO_PICKER = 2;
    private static final String IMAGE_PATH = "chat_images";
    private FirebaseStorage mFirebaseStorage;
    private StorageReference mImageStorageReference;

    // For Sign-in
    private static final int RC_SIGN_IN = 123;

    // Choose authentication providers
    List<AuthUI.IdpConfig> providers = Arrays.asList(
            new AuthUI.IdpConfig.Builder(AuthUI.EMAIL_PROVIDER).build(),
            //new AuthUI.IdpConfig.Builder(AuthUI.PHONE_VERIFICATION_PROVIDER).build(),
            new AuthUI.IdpConfig.Builder(AuthUI.GOOGLE_PROVIDER).build());
            //new AuthUI.IdpConfig.Builder(AuthUI.FACEBOOK_PROVIDER).build(),
            //new AuthUI.IdpConfig.Builder(AuthUI.TWITTER_PROVIDER).build());

    /* Log tag and shortcut */
    final static String TAG = "MYLOG Main";
    public static void ltag(String message) {
        Log.i(TAG, message);
    }
    /* Toast shortcut */
    public static void msg(Context context, String message) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show();
    }

    public static final String ANONYMOUS = "anonymous";
    public static final int DEFAULT_MSG_LENGTH_LIMIT = 1000;

    private ListView mMessageListView;
    private MessageAdapter mMessageAdapter;
    private ProgressBar mProgressBar;
    private ImageButton mPhotoPickerButton;
    private EditText mMessageEditText;
    private Button mSendButton;
    private Button mCancelButton;

    private String mUsername;

    //Two database variables:
    private FirebaseDatabase mFirebaseDatabase; //entry point
    private DatabaseReference mMsgDatabaseReference; //portion of message
    private ChildEventListener mChildEventListener;

    public void hideKeyboard(Context context, EditText mEditText) {
    	InputMethodManager keyboard = (InputMethodManager)getSystemService(context.INPUT_METHOD_SERVICE);
    	// hide keyboard after input
    	keyboard.hideSoftInputFromWindow(mEditText.getWindowToken(), 0);
    }

    //Authentication
    private FirebaseAuth mFirebaseAuth;
    private FirebaseAuth.AuthStateListener mAuthStateListener;

    static final int REQUEST_ID_MULTIPLE_PERMISSIONS = 200; // any code you want.
    public void requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.INTERNET) == PackageManager.PERMISSION_GRANTED &&
                    checkSelfPermission(Manifest.permission.GET_ACCOUNTS) == PackageManager.PERMISSION_GRANTED ) {
                ltag("Permission is granted");
            } else {
                ltag("Permission is revoked");
                ActivityCompat.requestPermissions(this, new String[]{
                                Manifest.permission.INTERNET,
                                Manifest.permission.GET_ACCOUNTS},
                        REQUEST_ID_MULTIPLE_PERMISSIONS);
            }
        }
    }


    //Fix scroll in wrong spot
    public abstract class OnScrollPositionChangedListener implements AbsListView.OnScrollListener{
        int pos;
        public  int viewHeight = 0;
        public  int listHeight = 0;

        @Override
        public void onScroll(AbsListView v, int i, int vi, int n) {
            try {
                if(viewHeight==0) {
                    viewHeight = v.getChildAt(0).getHeight();
                    listHeight = v.getHeight();

                }
                pos = viewHeight * i;

            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                onScrollPositionChanged(pos);
            }
        }
        @Override
        public void onScrollStateChanged(AbsListView absListView, int i) {}
        public abstract void onScrollPositionChanged(int scrollYPosition);
    }

    /*
        onCreate
     */
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        requestPermissions();
        mGoogleSignInClient = buildGoogleSignInClient();

        mUsername = ANONYMOUS;

        //database entry
        mFirebaseDatabase = FirebaseDatabase.getInstance();
        //authentication
        mFirebaseAuth = FirebaseAuth.getInstance();
        //reference
        mMsgDatabaseReference = mFirebaseDatabase.getReference().child("messages");

        // Initialize references to views
        mProgressBar = (ProgressBar) findViewById(R.id.progressBar);
        mMessageListView = (ListView) findViewById(R.id.messageListView);
        mPhotoPickerButton = (ImageButton) findViewById(R.id.photoPickerButton);
        mMessageEditText = (EditText) findViewById(R.id.messageEditText);
        mSendButton = (Button) findViewById(R.id.sendButton);
        mCancelButton = (Button) findViewById(R.id.cancelButton);

        // Initialize message ListView and its adapter
        List<FriendlyMessage> friendlyMessages = new ArrayList<>();
        mMessageAdapter = new MessageAdapter(this, R.layout.item_message, friendlyMessages);
        mMessageListView.setAdapter(mMessageAdapter);
        mMessageListView.getFirstVisiblePosition();
        // fix scroll
        OnScrollPositionChangedListener onScrollPositionChangedListener = new OnScrollPositionChangedListener() {
            @Override
            public void onScroll(AbsListView v, int i, int vi, int n) {
                super.onScroll(v, i, vi, n);
                //Do your onScroll stuff
            }

            @Override
            public void onScrollPositionChanged(int scrollYPosition) {
                //Enjoy having access to the amount the ListView has scrolled
               // seekBar.setProgress(scrollYPosition);
            }
        };


        // Initialize progress bar
        mProgressBar.setVisibility(ProgressBar.INVISIBLE);

        // Firebase Storage and Reference
        mFirebaseStorage = FirebaseStorage.getInstance();
        // Connect to your cloud image folder
        mImageStorageReference = mFirebaseStorage.getReference().child(IMAGE_PATH);
        // ImagePickerButton shows an image picker to upload a image for a message
        mPhotoPickerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // TODO: Fire an intent to show an image picker
                ltag("Image button pressed.");

                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("image/jpeg");
                intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
                startActivityForResult(Intent.createChooser(
                        intent, "Complete action using"), RC_PHOTO_PICKER);
            }
        });

        // Enable Send button when there's text to send
        mMessageEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                if (charSequence.toString().trim().length() > 0) {
                    mSendButton.setEnabled(true);
                } else {
                    mSendButton.setEnabled(false);
                }
            }

            @Override
            public void afterTextChanged(Editable editable) {
            }
        });
        mMessageEditText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(DEFAULT_MSG_LENGTH_LIMIT)});

        // Send button sends a message and clears the EditText
        mSendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                /*
                Message object has three instance variables:
                    A String for the text of the message
                    A String for the user’s name,
                    A String for the URL of the photo if it’s a photo message. (null for text)
                 */
                FriendlyMessage friendlyMessage = new FriendlyMessage(
                        mMessageEditText.getText().toString(),
                        mUsername,
                        null);

                //insert message to database by push
                mMsgDatabaseReference.push().setValue(friendlyMessage);

                // Clear input box
                mMessageEditText.setText("");
                hideKeyboard(getApplicationContext(), mMessageEditText);
            }
        });
        this.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

        mCancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Clear input box
                mMessageEditText.setText("");
                hideKeyboard(getApplicationContext(), mMessageEditText);
                scrollMyListViewToBottom(size);
            }
        });

        mAuthStateListener = new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                //check user
                FirebaseUser user = firebaseAuth.getCurrentUser();
                if (user != null) {
                    //signed in
                    ltag("user: "+user.getDisplayName()+", signed in");

                    onSignInInitialize(user.getDisplayName());

                } else {
                    //signed out and bring in Firebase UI
                    onSignOutCleanup();

                    // Create and launch sign-in intent
                    startActivityForResult(
                            AuthUI.getInstance()
                                    .createSignInIntentBuilder()
                                    .setAvailableProviders(providers)
                                    .build(),
                            RC_SIGN_IN);
                }
            }
        };

    }

    @Override
    protected void onResume() {
        super.onResume();
        mFirebaseAuth.addAuthStateListener(mAuthStateListener);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mFirebaseAuth.removeAuthStateListener(mAuthStateListener);
        mMessageAdapter.clear();
        detachDbReadListener();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.sign_out_menu:
                //sign out
                AuthUI.getInstance().signOut(this);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /*
        Message Reader
     */
    int size=0;
    private void attachDbReadListener() {
        ltag("Child Listener: Read messages");

        mChildEventListener = new ChildEventListener() {
            @Override
            public void onChildAdded(DataSnapshot dataSnapshot, String s) {
                FriendlyMessage friendlyMessage = dataSnapshot.getValue(FriendlyMessage.class);
                mMessageAdapter.add(friendlyMessage);
                size = mMessageAdapter.getCount();
                ltag("size: "+size);
                scrollMyListViewToBottom(size);
            }

            @Override
            public void onChildChanged(DataSnapshot dataSnapshot, String s) {

            }

            @Override
            public void onChildRemoved(DataSnapshot dataSnapshot) {
                size--;
            }

            @Override
            public void onChildMoved(DataSnapshot dataSnapshot, String s) {

            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        };
        mMsgDatabaseReference.addChildEventListener(mChildEventListener);

    }


    private void onSignInInitialize(String username) {
        mUsername = username;
        attachDbReadListener();
        ltag("Sing in count: "+size);
    }

    private void detachDbReadListener() {
        if (mChildEventListener != null) {
            mMsgDatabaseReference.removeEventListener(mChildEventListener);
        }
    }

    private void onSignOutCleanup() {
        mUsername = ANONYMOUS;
        mMessageAdapter.clear();
        detachDbReadListener();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        String resultCodeString = "";
        switch(resultCode) {
            case RESULT_CANCELED:
                resultCodeString = "RESULT_CANCELED";
                break;
            case RESULT_OK :
                resultCodeString = "RESULT_OK";
                break;
            default:
                resultCodeString = "Code Not Found!";
                break;
        }
        ltag("request code: "+reqCode2String(requestCode)+" , result code: "+resultCodeString);

        if (requestCode == RC_SIGN_IN) {
            if (resultCode == RESULT_OK) {
                msg(getApplicationContext(), "User signed in!");
            } else if (requestCode == RESULT_CANCELED) {
                msg(getApplicationContext(), "Sign in canceled.");
                finish();
            }
        } else if (requestCode == RC_PHOTO_PICKER && resultCode == RESULT_OK) {

            Uri seletedImageUri = data.getData();
            String filename = "";
            String mimeType = "";

            // Google drive method
            if (seletedImageUri.toString().contains("com.google.android.apps.docs.storage.legacy")) {
                mimeType = getContentResolver().getType(seletedImageUri);
                Cursor imageCursor =
                        getContentResolver().query(seletedImageUri, null, null, null, null);
                int nameIndex = imageCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                imageCursor.moveToFirst();
                filename = imageCursor.getString(nameIndex);
            } else { //other resource
                filename = seletedImageUri.getLastPathSegment();
                MimeTypeMap mime = MimeTypeMap.getSingleton();
                mimeType = mime.getExtensionFromMimeType(getContentResolver().getType(seletedImageUri));
            }

            // reference of store file <== filename
            String uploadTo = IMAGE_PATH+"/"+filename;
            ltag("upload to: "+uploadTo);

            StorageReference photoRef = mImageStorageReference.child(uploadTo);
            StorageMetadata photoMetadata = new StorageMetadata.Builder()
                    .setContentType(mimeType)
                    .build();

            // upload file to Firebase Storage
            UploadTask uploadTask;
            uploadTask = photoRef.putFile(seletedImageUri, photoMetadata);
            // fail to upload
            uploadTask.addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception e) {
                    ltag(e.getMessage().toString());
                }
            });
            // success to upload
            uploadTask.addOnSuccessListener(this, new OnSuccessListener<UploadTask.TaskSnapshot>() {
                @Override
                public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                    Uri downloadUrl = taskSnapshot.getDownloadUrl();

                    ltag("Download url: "+downloadUrl.getPath().toString());

                    // update the message list, so the textbody is null, username and photoUrl
                    FriendlyMessage friendlyMessage = new FriendlyMessage(
                            null, //textbody
                            mUsername, //username
                            downloadUrl.toString() //photoUrl
                    );
                    mMsgDatabaseReference.push().setValue(friendlyMessage);
                }
            });
        }
    }

    private void scrollMyListViewToBottom(final int position) {
        final Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                mMessageListView.smoothScrollToPosition(position);
            }}, 100);
    }

}
