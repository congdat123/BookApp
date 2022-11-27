package com.example.bookappkotlin

import android.app.Application
import android.app.ProgressDialog
import android.content.Context
import android.text.format.DateFormat
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import com.example.bookappkotlin.activities.PdfDetailActivity
import com.github.barteksc.pdfviewer.PDFView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage
import java.util.*
import kotlin.collections.HashMap

class MyApplication : Application() {

    override fun onCreate() {
        super.onCreate()
    }

    companion object{
        //created a static method to convert timestamp to proper date format, so we can use it everywhere in project, no need to rewrite again
        fun formatTimeStamp(timestamp: Long):String{
            val cal = Calendar.getInstance(Locale.ENGLISH)
            cal.timeInMillis = timestamp
            //format dd/MM/yyyy
            return DateFormat.format("dd/MM/yyyy", cal).toString()
        }

        //function to get pdf size
        fun loadPdfSize(pdfUrl:String,pdfTitle: String, sizeTv:TextView){
            val TAG = "PDF_SIZE_TAG"
        //using url we can get file and its metadata from firebase storage
            val ref = FirebaseStorage.getInstance().getReferenceFromUrl(pdfUrl)
            ref.metadata
                .addOnSuccessListener {storageMetaData ->
                    Log.d(TAG, "loadPdfSize: got metadata")
                    val bytes = storageMetaData.sizeBytes.toDouble()
                    Log.d(TAG, "loadPdfSize: Size Bytes $bytes")

                    //convert bytes to KB/MB
                    val kb = bytes/1024
                    val mb= kb/1024
                    if (mb>=1){
                        sizeTv.text = "${String.format("%.2f", mb)} MB"
                    }
                    else if (kb>=1){
                        sizeTv.text = "${String.format("%.2f", kb)} KB"
                    }
                    else{
                        sizeTv.text = "${String.format("%.2f", bytes)} byte"
                    }
                }
                .addOnFailureListener { e->
                    //failed to get metadata
                    Log.d(TAG, "loadPdfSize: Failed to get metadata due to ${e.message}")
                }
        }

        /*instead of making new function loadPdfPageCount() to just load pages count it would be more good to use some existing function to do that
        * i.e. loadPdfFromUrlSinglePage
        * We will and another parameter of type TextView e.g. pagesTv
        * Whenever we call that function
        * 1) if we require page numbers we will pass pagesTv (TextView)
        * 2) if we don't require page number we will pass null
        * And in function if pagesTv (TextView) parameter is not null we will set the page number count*/
        fun loadPdfFromUrlSinglePage(
            pdfUrl: String,
            pdfTitle: String,
            pdfView: PDFView,
            progressBar: ProgressBar,
            pagesTv: TextView?
        ){
            val TAG = "PDF_THUMBNAIL_TAG"
            //using url we can get file and its metadata from firebase storages
            val ref = FirebaseStorage.getInstance().getReferenceFromUrl(pdfUrl)
            ref.getBytes(Constants.MAX_BYTES_PDF)
                .addOnSuccessListener {bytes ->
                    Log.d(TAG, "loadPdfSize: Size Bytes $bytes")

                    //SET TO PDFVIEW
                    pdfView.fromBytes(bytes)
                        .pages(0)//show first page only
                        .spacing(0)
                        .swipeHorizontal(false)
                        .enableSwipe(false)
                        .onError{t->
                            progressBar.visibility = View.INVISIBLE
                            Log.d(TAG, "loadPdfFromUrlSinglePage: ${t.message}")
                        }
                        .onPageError { page, t ->
                            progressBar.visibility = View.INVISIBLE
                            Log.d(TAG, "loadPdfFromUrlSinglePage: ${t.message}")
                        }
                        .onLoad { nbPages->
                            Log.d(TAG, "loadPdfFromUrlSinglePage: Pages: $nbPages")
                            //pdf loaded,we can set page count, pdf thumbnail
                            progressBar.visibility= View.INVISIBLE

                            //if pagesTv param is not null then set page numbers
                            if(pagesTv != null){
                                pagesTv.text = "$nbPages"
                            }
                        }
                        .load()
                }
                .addOnFailureListener { e->
                    //failed to get metadata
                    Log.d(TAG, "loadPdfSize: Failed to get metadata due to ${e.message}")
                }
        }

        fun loadCategory(categoryId: String,categoryTv: TextView){
            //load category using category id from firebase
            val ref = FirebaseDatabase.getInstance().getReference("Categories")
            ref.child(categoryId)
                .addListenerForSingleValueEvent(object : ValueEventListener{
                    override fun onDataChange(snapshot: DataSnapshot) {
                        //get category
                        val category = "${snapshot.child("category").value}"
                        //set category
                        categoryTv.text=category
                    }

                    override fun onCancelled(error: DatabaseError) {

                    }
                })
        }

        fun deleteBook(context: Context, bookId: String, bookUrl: String, bookTitle: String){
            //param details
            //1) context, used when require e.g. Progress dialog, toast
            //2)bookId, to delete book from db
            //3) bookUrl, delete book from firebase storage
            //4) bookTitle, show in dialog etc

            val TAG = "DELETE_BOOK_TAG"

            Log.d(TAG, "deleteBook: deleting...")

            //progress dialog
            val progressDialog = ProgressDialog(context)
            progressDialog.setTitle("Please wait...")
            progressDialog.setMessage("Deleting $bookTitle")
            progressDialog.setCanceledOnTouchOutside(false)
            progressDialog.show()

            Log.d(TAG, "deleteBook: Deleting from storage...")
            val storageReference = FirebaseStorage.getInstance().getReferenceFromUrl(bookUrl)
            storageReference.delete()
                .addOnSuccessListener {
                    Log.d(TAG, "deleteBook: Deleted from storage")
                    Log.d(TAG, "deleteBook: Deleting from db now...")

                    val ref = FirebaseDatabase.getInstance().getReference("Books")
                    ref.child(bookId)
                        .removeValue()
                        .addOnSuccessListener {
                            progressDialog.dismiss()
                            Toast.makeText(context, "Successfully delete...", Toast.LENGTH_SHORT).show()
                            Log.d(TAG, "deleteBook: Deleted from db too...")
                        }
                        .addOnFailureListener {e->
                            progressDialog.dismiss()
                            Log.d(TAG, "deleteBook: Failed to delete from db due to ${e.message}")
                            Toast.makeText(context, "Failed to delete due to ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                }
                .addOnFailureListener {e->
                    progressDialog.dismiss()
                    Log.d(TAG, "deleteBook: Failed to delete from storage due to ${e.message}")
                    Toast.makeText(context, "Failed to delete due to ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }

        fun incrementBookViewCount(bookId: String){
            //1) Get current book views count
            var ref = FirebaseDatabase.getInstance().getReference("Books")
            ref.child(bookId)
                .addListenerForSingleValueEvent(object : ValueEventListener{
                    override fun onDataChange(snapshot: DataSnapshot) {
                        //get views count
                        var viewsCount = "${snapshot.child("viewsCount").value}"

                        if(viewsCount==""|| viewsCount=="null"){
                            viewsCount = "0";
                        }

                        //2) Increment views count
                        val newViewsCount = viewsCount.toLong() + 1

                        //setup data to upload in db
                        val hashMap = HashMap<String,Any>()
                        hashMap["viewsCount"] = newViewsCount

                        //set to db
                        val dbRef = FirebaseDatabase.getInstance().getReference("Books")
                        dbRef.child(bookId)
                            .updateChildren(hashMap)
                    }

                    override fun onCancelled(error: DatabaseError) {

                    }
                })
        }
        public fun removeFromFavorite(context: Context,bookId: String){
            val TAG  = "REMOVE_FAV_PDF"
            Log.d(TAG, "removeFromFavorite: Removing from fav")
            val firebaseAuth = FirebaseAuth.getInstance()

            //dữ liệu tham chiếu
            val ref = FirebaseDatabase.getInstance().getReference("Users")
            ref.child(firebaseAuth.uid!!).child("Favorites").child(bookId)
                .removeValue()
                .addOnSuccessListener {
                    Log.d(TAG, "removeFromFavorite: Removed from fav")
                    Toast.makeText(context, "Removed from favorite", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener {e->
                    Log.d(TAG, "removeFromFavorite: Failed to remove from fav due to ${e.message}")
                    Toast.makeText(context, "Failed to remove from fav due to ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

}