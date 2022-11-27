package com.example.bookappkotlin.filters

import android.widget.Filter
import com.example.bookappkotlin.adapters.AdapterPdfUser
import com.example.bookappkotlin.models.ModelPdf

class FilterPdfUser: Filter {
    var filterList: ArrayList<ModelPdf>
    var adapterPdfUser: AdapterPdfUser

    constructor(filterList: ArrayList<ModelPdf>, adapterPdfUser: AdapterPdfUser) : super() {
        this.filterList = filterList
        this.adapterPdfUser = adapterPdfUser
    }

    override fun performFiltering(constraint: CharSequence?): FilterResults {
        //value to search
        var constraint: CharSequence? = constraint
        var results = FilterResults()
        //value to be searched should not be null and not empty
        if (constraint != null && constraint.isNotEmpty()){
            //not null or empty

            //change to update case, or lower case to remove case sensitivity
            constraint = constraint.toString().uppercase()
            val filteredModels = ArrayList<ModelPdf>()
            for (i in filterList.indices){
                //validate if match
                if(filterList[i].title.uppercase().contains(constraint)){
                    //searched value matched with title, add to list
                    filteredModels.add(filterList[i])
                }
            }
            //return filtered list and size
            results.count = filteredModels.size
            results.values = filteredModels
        }
        else{
            //either it is null or is empty
            //return original list and size
            results.count = filterList.size
            results.values = filterList
        }
        return results
    }

    override fun publishResults(constraint: CharSequence, results: FilterResults?) {
        //apply filter changes
        adapterPdfUser.pdfArrayList = results!!.values as ArrayList<ModelPdf>

        adapterPdfUser.notifyDataSetChanged()
    }
}