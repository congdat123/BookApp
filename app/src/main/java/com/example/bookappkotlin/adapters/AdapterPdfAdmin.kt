package com.example.bookappkotlin.adapters

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Filter
import android.widget.Filterable
import androidx.recyclerview.widget.RecyclerView
import com.example.bookappkotlin.filters.FilterPdfAdmin
import com.example.bookappkotlin.models.ModelPdf
import com.example.bookappkotlin.MyApplication
import com.example.bookappkotlin.activities.PdfDetailActivity
import com.example.bookappkotlin.activities.PdfEditActivity
import com.example.bookappkotlin.databinding.RowPdfAdminBinding

class AdapterPdfAdmin :RecyclerView.Adapter<AdapterPdfAdmin.HolderPdfAdmin>, Filterable{

    //context
    private var context: Context
    //arraylist to hold pdfs
    public var pdfArrayList: ArrayList<ModelPdf>
    private val filterList: ArrayList<ModelPdf>
    //viewBinding
    private lateinit var binding: RowPdfAdminBinding

    //filter object, just make it private, will work
    private var filter : FilterPdfAdmin?= null

    //constructor
    constructor(context: Context, pdfArrayList: ArrayList<ModelPdf>) : super() {
        this.context = context
        this.pdfArrayList = pdfArrayList
        this.filterList = pdfArrayList
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HolderPdfAdmin {
        //bind/inflate layout row_pdf_admin.xml
        binding = RowPdfAdminBinding.inflate(LayoutInflater.from(context),parent,false)

        return HolderPdfAdmin(binding.root)

    }

    override fun onBindViewHolder(holder: HolderPdfAdmin, position: Int) {
        /*--Get data, Set data, Handle click etc---*/

        //get data
        val model = pdfArrayList[position]
        val pdfId = model.id
        val categoryId = model.categoryId
        val title = model.title
        val description = model.description
        val pdfUrl = model.url
        val timestamp = model.timestamp
        //convert timestamp to dd/MM/yyyy format
        val formattedDate = MyApplication.formatTimeStamp(timestamp)
        //let create an Application class that will contain the functions that will be used multiple places in app

        //set data
        holder.titleTv.text = title
        holder.descriptionTv.text = description
        holder.dateTv.text = formattedDate

        //load further details like category, pdf from url, pdf size

        //load category id
        MyApplication.loadCategory(categoryId, holder.categoryTv)

        //we don't need page number here, pas null for page number || load pdf thumbnail
        MyApplication.loadPdfFromUrlSinglePage(
            pdfUrl,
            title,
            holder.pdfView,
            holder.progressBar,
            null
        )

        //load pdf size
        MyApplication.loadPdfSize(pdfUrl, title, holder.sizeTv)

        //handle click , show dialog with option 1) Edit Book, 2) Delete Book
        holder.moreBtn.setOnClickListener {
            moreOptionsDialog(model,holder)
        }

        //handle item click, open PdfDetailActivity activity, lets create it first
        holder.itemView.setOnClickListener {
            //intent with book id
            val intent = Intent(context, PdfDetailActivity::class.java)
            intent.putExtra("bookId", pdfId)//will be used to load book details
            context.startActivity(intent)
        }

    }

    private fun moreOptionsDialog(model: ModelPdf, holder: HolderPdfAdmin) {
        //get id, url, title of book
        val bookId = model.id
        val bookUrl = model.url
        val bookTitle = model.title

        //options to show in dialog
        val options = arrayOf("Edit", "Delete")

        //alert dialog
        val builder = AlertDialog.Builder(context)
        builder.setTitle("Choose Option")
            .setItems(options){dialog, position ->
                //handle item click
                if(position==0){
                    //Edit is clicked
                    val intent = Intent(context, PdfEditActivity::class.java)
                    intent.putExtra("bookId",bookId)//passed bookId, will be used to edit the book
                    context.startActivity(intent)

                }
                else if (position == 1){
                    //Delete is clicked

                    //show confirmation dialog first if you need...
                    MyApplication.deleteBook(context, bookId, bookUrl, bookTitle)
                }
            }
            .show()
    }

    override fun getItemCount(): Int {
        return pdfArrayList.size //items count
    }


    override fun getFilter(): Filter {
        if (filter==null){
            filter = FilterPdfAdmin(filterList,this)
        }
        return filter as FilterPdfAdmin
    }

    /*View Holder class for row_pdf_admin.xml*/
    inner class HolderPdfAdmin(itemView:View):RecyclerView.ViewHolder(itemView){
        //UI Views of row_pdf_admin.xml
        val pdfView = binding.pdfView
        val progressBar = binding.progressBar
        val titleTv = binding.titleTv
        val descriptionTv = binding.descriptionTv
        val categoryTv = binding.categoryTv
        val sizeTv = binding.sizeTv
        val dateTv = binding.dateTv
        val moreBtn = binding.moreBtn
    }
}