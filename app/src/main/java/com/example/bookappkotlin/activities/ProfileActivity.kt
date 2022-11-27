package com.example.bookappkotlin.activities

import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast
import com.bumptech.glide.Glide
import com.example.bookappkotlin.MyApplication
import com.example.bookappkotlin.R
import com.example.bookappkotlin.adapters.AdapterPdfFavorite
import com.example.bookappkotlin.databinding.ActivityProfileBinding
import com.example.bookappkotlin.models.ModelPdf
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.lang.Exception

class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding

    private lateinit var firebaseAuth: FirebaseAuth
//    Firebase get current user
    private lateinit var firebaseUser: FirebaseUser

//    arraylist to hold books
    private lateinit var bookArrayList: ArrayList<ModelPdf>
    private lateinit var adapterPdfFavorite: AdapterPdfFavorite
//    progress dialog
    private lateinit var progressDialog: ProgressDialog

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)
//        reset to default values
        binding.accountTypeTv.text = "N/A"
        binding.memberDateTv.text = "N/A"
        binding.favoritesLabelTv.text = "N/A"
        binding.accountStatusTv.text = "N/A"

        firebaseAuth = FirebaseAuth.getInstance()
        firebaseUser = firebaseAuth.currentUser!!

//        init/setup progress dialog
        progressDialog = ProgressDialog(this)
        progressDialog.setTitle("Please wait")
        progressDialog.setCanceledOnTouchOutside(false)

        loadUserInfo()
        loadFavoriteBooks()
        //handle click, go back
        binding.backBtn.setOnClickListener {
            onBackPressed()
        }
        //handle click, open edit profile
        binding.profileEditBtn.setOnClickListener {
            startActivity(Intent(this,ProfileEditActivity::class.java))
        }
//        handle click, verify user if not
        binding.accountStatusTv.setOnClickListener {
            if (firebaseUser.isEmailVerified){
//                User is verity
                Toast.makeText(this, "Already verified...", Toast.LENGTH_SHORT).show()
            }
            else{
//                User isn't verified, show confirmation dialog before verification
                emailVerificationDialog()
            }
        }

    }

    private fun emailVerificationDialog() {
//        show confirmation dialog
        val builder = AlertDialog.Builder(this)
        builder.setTitle("verify Email")
            .setMessage("Are you sure you want to send email verification instructions to your email ${firebaseUser.email}")
            .setPositiveButton("SEND"){d,e->
                sendEmailVerification()
            }
            .setNegativeButton("CANCEL"){d,e->
                d.dismiss()
            }
            .show()
    }

    private fun sendEmailVerification() {
//        show progress dialog
        progressDialog.setMessage("Sending email verification instructions to email ${firebaseUser.email}")
        progressDialog.show()
//        send instructions
        firebaseUser.sendEmailVerification()
            .addOnSuccessListener {
//                successfully sent
                Toast.makeText(this, "Instructions sent! check your email ${firebaseUser.email}", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e->
//                failed to send
                progressDialog.dismiss()
                Toast.makeText(this, "Failed to send due to ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadUserInfo() {
//        check if user is verified or not, changes may effect after re login when you verify email
        if (firebaseUser.isEmailVerified){
            binding.accountStatusTv.text= "Verified"
        }
        else{
            binding.accountStatusTv.text = "Not Verified"
        }

        //csld tham chiếu tải thông tin người dùng
        val ref = FirebaseDatabase.getInstance().getReference("Users")
        ref.child(firebaseAuth.uid!!)
            .addValueEventListener(object : ValueEventListener{
                override fun onDataChange(snapshot: DataSnapshot) {
                    //lấy thông tin người dùng
                    val email = "${snapshot.child("email").value}"
                    val name = "${snapshot.child("name").value}"
                    val profileImage = "${snapshot.child("profileImage").value}"
                    val timestamp = "${snapshot.child("timestamp").value}"
                    val uid = "${snapshot.child("uid").value}"
                    val userType = "${snapshot.child("userType").value}"

                    // đổi định dạng thời gian
                    val formattedDate = MyApplication.formatTimeStamp(timestamp.toLong())
                    //thiết lập dữ liệu
                    binding.nameTv.text = name
                    binding.emailTv.text = email
                    binding.memberDateTv.text = formattedDate
                    binding.accountTypeTv.text = userType
                    //thiết lập avatar
                    try {
                        Glide.with(this@ProfileActivity)
                            .load(profileImage)
                            .placeholder(R.drawable.ic_person_gray)
                            .into(binding.profileIv)
                    }
                    catch (e: Exception){

                    }

                }

                override fun onCancelled(error: DatabaseError) {

                }
            })
    }

    private fun loadFavoriteBooks(){
//        init arraylist
        bookArrayList = ArrayList()

        val ref = FirebaseDatabase.getInstance().getReference("Users")
        ref.child(firebaseAuth.uid!!).child("Favorites")
            .addValueEventListener(object : ValueEventListener{
                override fun onDataChange(snapshot: DataSnapshot) {
//                    clear arraylist,before starting adding data
                    bookArrayList.clear()
                    for (ds in snapshot.children){
//                        get only id of the books, rest of the info we have loaded in adapter class
                        val bookId = "${ds.child("bookId").value}"

//                        set to model
                        val modelPdf = ModelPdf()
                        modelPdf.id = bookId

//                        add model to list
                        bookArrayList.add(modelPdf)
                    }
//                    set number of favorite books
                    binding.favoriteBookCountTv.text = "${bookArrayList.size}"
//                    setup adapter
                    adapterPdfFavorite = AdapterPdfFavorite(this@ProfileActivity, bookArrayList)
//                    set adapter to recyclerview
                    binding.favoriteRv.adapter = adapterPdfFavorite
                }

                override fun onCancelled(error: DatabaseError) {

                }
            })
    }
}