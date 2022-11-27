package com.example.bookappkotlin.activities

import android.Manifest
import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.Intent
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.LayoutInflater
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.example.bookappkotlin.Constants
import com.example.bookappkotlin.MyApplication
import com.example.bookappkotlin.R
import com.example.bookappkotlin.adapters.AdapterComment
import com.example.bookappkotlin.databinding.ActivityPdfDetailBinding
import com.example.bookappkotlin.databinding.DialogCommentAddBinding
import com.example.bookappkotlin.models.ModelComment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage
import java.io.FileOutputStream
import java.lang.Exception
import java.security.cert.Extension
import kotlin.math.log

class PdfDetailActivity : AppCompatActivity() {
    //view binding
    private lateinit var binding: ActivityPdfDetailBinding

    private companion object{
        const val TAG= "BOOK_DETAILS_TAG"
    }

    //book id, get from intent
    private var bookId = ""
    //get from firebase
    private var bookTitle = ""
    private var bookUrl = ""

    //will hold a boolean value false/true to indicate either is in current user's favorite list or not
    private var isInmyFavorite = false

    private lateinit var firebaseAuth: FirebaseAuth

    private lateinit var progressDialog: ProgressDialog
//    arraylist to hold comments
    private lateinit var commentArrayList: ArrayList<ModelComment>
//    adapter to be set to recyclerview
    private lateinit var adapterComment: AdapterComment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPdfDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        //get book id from intent
        bookId = intent.getStringExtra("bookId")!!

        //init progress bar
        progressDialog = ProgressDialog(this)
        progressDialog.setTitle("Please wait...")
        progressDialog.setCanceledOnTouchOutside(false)

        //init firebase auth
        firebaseAuth = FirebaseAuth.getInstance()
        if (firebaseAuth.currentUser!=null){
            //người dùng đăng nhập, kiểm tra nếu sách trong yêu thích hoặc không
            checkIsFavorite()
        }

        //increment book view count, whenever this page starts
        MyApplication.incrementBookViewCount(bookId)

        loadBookDetails()
        showComments()

        //handle back button click , go back
        binding.backBtn.setOnClickListener {
            onBackPressed()
        }

        //handle click, open pdf view activity, //lets create and activity for pdf view
        binding.readBookBtn.setOnClickListener {
            val intent = Intent(this, PdfViewActivity::class.java)
            intent.putExtra("bookId",bookId)
            startActivity(intent)
        }
        //handle click, download book/pdf
        binding.downloadBookBtn.setOnClickListener {
            if(ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED ){
                Log.d(TAG, "onCreate: STORAGE PERMISSION is already granted")
                downloadBook()
            }
            else{
                Log.d(TAG, "onCreate: STORAGE PERMISSION was not granted, LET request it")
                requestStoragePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }

        //handle click, add/remove favorite
        binding.favoriteBtn.setOnClickListener {
            // Có thể thêm vào yêu thích nếu đã đăng nhập
            //1) Kiểm tra user đã đăng nhập hay chưa
            if (firebaseAuth.currentUser==null){
                //chưa đăng nhập, không thể thêm vào yêu thích
                Toast.makeText(this, "You're not logged in", Toast.LENGTH_SHORT).show()
            }
            else{
                //tài khoản đăng nhập, có thể thêm vào yêu thích
                if (isInmyFavorite){
                    //đã có trong yêu thích, xóa
                    MyApplication.removeFromFavorite(this,bookId)
                }
                else{
                    //không có trong yêu thích, thêm
                    addToFavorite()

                }

            }
        }
        //handle click, show add comment dialog
        binding.addCommentBtn.setOnClickListener {
            if(firebaseAuth.currentUser==null){
                Toast.makeText(this, "You're not logged in", Toast.LENGTH_SHORT).show()
            }
            else{
                addCommentDialog()
            }
        }
    }

    private fun showComments() {
//        init arraylist
        commentArrayList = ArrayList()
//        db path to load comments
        val ref = FirebaseDatabase.getInstance().getReference("Books")
        ref.child(bookId).child("Comments")
            .addValueEventListener(object :ValueEventListener{
                override fun onDataChange(snapshot: DataSnapshot) {
//                    clear list
                    commentArrayList.clear()
                    for (ds in snapshot.children){
//                        get data s model
                        val model = ds.getValue(ModelComment::class.java)
//                        add to list
                        commentArrayList.add(model!!)
                    }
//                    setup adapter
                    adapterComment = AdapterComment(this@PdfDetailActivity,commentArrayList)
//                    set adapter to recyclerview
                    binding.commentRv.adapter = adapterComment                      
                }

                override fun onCancelled(error: DatabaseError) {

                }
            })
    }

    private var comment = ""
    private fun addCommentDialog() {
//        inflater/ bind view for dialog_comment_add.xml
        val commentAddBinding =DialogCommentAddBinding.inflate(LayoutInflater.from(this))

//        setup alert dialog
        val builder = AlertDialog.Builder(this,R.style.CustomDialog)
        builder.setView(commentAddBinding.root)

//        create and show alert dialog
        val alertDialog = builder.create()
        alertDialog.show()
//        handle click, dismiss dialog
        commentAddBinding.backBtn.setOnClickListener {
            alertDialog.dismiss()
        }
//        handle click, add comment
        commentAddBinding.submitBtn.setOnClickListener { 
//            get data
            comment = commentAddBinding.commentEt.text.toString().trim()
//            validate Data
            if (comment.isEmpty()){
                Toast.makeText(this, "Enter comment...", Toast.LENGTH_SHORT).show()
            }
            else{
                alertDialog.dismiss()
                addComment()
            }
        }
    }

    private fun addComment() {
//        show progress
        progressDialog.setMessage("Adding Comment")
        progressDialog.show()
//        timestamp for comment id, comment timestamp etc.
        val timestamp = "${System.currentTimeMillis()}"
//        setup data to add in db for comment
        val hashMap = HashMap<String,Any>()
        hashMap["id"] = "$timestamp"
        hashMap["bookId"] = "$bookId"
        hashMap["timestamp"] = "$timestamp"
        hashMap["comment"] = "$comment"
        hashMap["uid"] = "${firebaseAuth.uid}"
//        Db path to add data into it
//        Books > bookId > Comments > commentId > commentData
        val ref = FirebaseDatabase.getInstance().getReference("Books")
        ref.child(bookId).child("Comments").child(timestamp)
            .setValue(hashMap)
            .addOnSuccessListener { 
                progressDialog.dismiss()
                Toast.makeText(this, "Comment added...", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e->
                progressDialog.dismiss()
                Toast.makeText(this, "Failed to add comment due to ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private val requestStoragePermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()){isGranted: Boolean->
        //lets check if granted or not
        if (isGranted){
            Log.d(TAG, "requestStoragePermissionLauncher: STORAGE PERMISSION is granted")
            downloadBook()
        }
        else{
            Log.d(TAG, "requestStoragePermissionLauncher: STORAGE PERMISSION is denied")
            Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    private fun downloadBook(){
        Log.d(TAG, "downloadBook: Downloading Book")

        progressDialog.setMessage("Downloading Book")
        progressDialog.show()

        //downloads book from firebase storage using url
        val storageReference = FirebaseStorage.getInstance().getReferenceFromUrl(bookUrl)
        storageReference.getBytes(Constants.MAX_BYTES_PDF)
            .addOnSuccessListener {bytes ->
                Log.d(TAG, "downloadBook: Book downloaded...")
                saveDownloadsFolder(bytes)

            }
            .addOnFailureListener { e->
                progressDialog.dismiss()
                Log.d(TAG, "downloadBook: Failed to download book due to ${e.message}")
                Toast.makeText(this, "Failed to download book due to ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun saveDownloadsFolder(bytes: ByteArray) {
        Log.d(TAG, "saveDownloadsFolder: saving downloaded book")

        val nameWithExtension = "$bookTitle.pdf"

        try {
            val downloadsFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            downloadsFolder.mkdirs() //create folder if not exitst
            val filePath = downloadsFolder.path + "/" + nameWithExtension

            val out = FileOutputStream(filePath)
            out.write(bytes)
            out.close()

            Toast.makeText(this, "Saved to Downloads Folder", Toast.LENGTH_SHORT).show()
            Log.d(TAG, "saveDownloadsFolder: Saved to Downloads Folder")
            progressDialog.dismiss()
            incrementDownloadCount()

        }
        catch (e:Exception){
            progressDialog.dismiss()
            Log.d(TAG, "saveDownloadsFolder: Failed to save due to ${e.message}")
            Toast.makeText(this, "Failed to save due to ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun incrementDownloadCount() {
        //increment downloads count to firebase

        Log.d(TAG, "incrementDownloadCount: ")

        //step 1: get previous downloads count
        val ref = FirebaseDatabase.getInstance().getReference("Books")
        ref.child(bookId)
            .addListenerForSingleValueEvent(object : ValueEventListener{
                override fun onDataChange(snapshot: DataSnapshot) {
                    //get downloads count
                    var downloadsCount = "${snapshot.child("downloadsCount").value}"
                    Log.d(TAG, "onDataChange: Current Downloads Count: $downloadsCount")

                    if (downloadsCount== ""|| downloadsCount == "null"){
                        downloadsCount = "0"
                    }

                    //convert to long and increment 1
                    val newDownloadsCount: Long = downloadsCount.toLong() + 1
                    Log.d(TAG, "onDataChange: New Downloads Count: $newDownloadsCount")

                    //setup data to update to db
                    val hashMap: HashMap<String,Any> = HashMap()
                    hashMap["downloadsCount"] = newDownloadsCount
                    //step 2: Update new incremented downloads count to db
                    val dbRef = FirebaseDatabase.getInstance().getReference("Books")
                    dbRef.child(bookId)
                        .updateChildren(hashMap)
                        .addOnSuccessListener {
                            Log.d(TAG, "onDataChange: Downloads count incremented")
                        }
                        .addOnFailureListener { e->
                            Log.d(TAG, "onDataChange: Failed to increment due to ${e.message}")
                        }
                }

                override fun onCancelled(error: DatabaseError) {

                }
            })
    }

    private fun loadBookDetails() {
        //Books> bookId > Details
        val ref = FirebaseDatabase.getInstance().getReference("Books")
        ref.child(bookId)
            .addListenerForSingleValueEvent(object : ValueEventListener{
                override fun onDataChange(snapshot: DataSnapshot) {
                    //get data
                    val categoryId = "${snapshot.child("categoryId").value}"
                    val description = "${snapshot.child("description").value}"
                    val downloadsCount = "${snapshot.child("downloadsCount").value}"
                    val timestamp = "${snapshot.child("timestamp").value}"
                    bookTitle = "${snapshot.child("title").value}"
                    val uid = "${snapshot.child("uid").value}"
                    bookUrl = "${snapshot.child("url").value}"
                    val viewsCount = "${snapshot.child("viewsCount").value}"

                    //format date
                    val date = MyApplication.formatTimeStamp(timestamp.toLong())

                    //load pdf category
                    MyApplication.loadCategory(categoryId, binding.categoryTv)

                    //load pdf thumbnail, pages count
                    MyApplication.loadPdfFromUrlSinglePage(
                        "$bookUrl",
                        "$bookTitle",
                        binding.pdfView,
                        binding.progressBar,
                        binding.pagesTv
                    )
                    //load pdf size
                    MyApplication.loadPdfSize("$bookUrl", "$bookTitle", binding.sizeTv)

                    //set data
                    binding.titleTv.text = bookTitle
                    binding.descriptionTv.text = description
                    binding.viewsTv.text = viewsCount
                    binding.downloadsTv.text = downloadsCount
                    binding.dateTv.text = date
                }

                override fun onCancelled(error: DatabaseError) {

                }
            })
    }

    private fun checkIsFavorite(){
        Log.d(TAG, "checkIsFavorite: Check if book is in fav or not")

        val ref = FirebaseDatabase.getInstance().getReference("Users")
        ref.child(firebaseAuth.uid!!).child("Favorites").child(bookId)
            .addValueEventListener(object : ValueEventListener{
                override fun onDataChange(snapshot: DataSnapshot) {
                    isInmyFavorite = snapshot.exists()
                    if (isInmyFavorite){
                        //có trong yêu thích
                        Log.d(TAG, "onDataChange: available in favorite")
                        //đặt biểu tượng trên cùng có thể vẽ được
                        binding.favoriteBtn.setCompoundDrawablesRelativeWithIntrinsicBounds(0,
                            R.drawable.ic_favorite_filled_white,0,0)
                        binding.favoriteBtn.text = "Remove Favorite"
                    }
                    else{
                        //không có trong yêu thích
                        Log.d(TAG, "onDataChange: not available in favorite")
                        //đặt biểu tượng trên cùng có thể vẽ được
                        binding.favoriteBtn.setCompoundDrawablesRelativeWithIntrinsicBounds(0,
                            R.drawable.ic_favorite_border_white,0,0)
                        binding.favoriteBtn.text = "Add Favorite"
                    }
                }

                override fun onCancelled(error: DatabaseError) {

                }
            })
    }

    private fun addToFavorite(){
        val timestamp = System.currentTimeMillis()

        //thiết lập dữ liệu thêm vào db
        val hashMap = HashMap<String,Any>()
        hashMap["bookId"] = bookId
        hashMap["timestamp"] = timestamp

        //lưu vào db
        val ref = FirebaseDatabase.getInstance().getReference("Users")
        ref.child(firebaseAuth.uid!!).child("Favorites").child(bookId)
            .setValue(hashMap)
            .addOnSuccessListener {
                //thêm vào yêu thích
                Log.d(TAG, "addToFavorite: Added to fav")
                Toast.makeText(this, "Added to favorite", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e->
                //lỗi khi thêm vào yêu thích
                Log.d(TAG, "addToFavorite: Failed to add to fav due to ${e.message}")
                Toast.makeText(this, "Failed to add to fav due to ${e.message}", Toast.LENGTH_SHORT).show()
            }

    }
}