package bloo.ad.addbloo

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import bloo.ad.addbloo.db.Blocked
import bloo.ad.addbloo.db.UrlDao
import kotlinx.coroutines.*
import java.util.*

class UrlViewModel : ViewModel() {

    lateinit var all : LiveData<List<Blocked>>

    fun init(dao: UrlDao) {
        all = dao.getAll()
    }
}