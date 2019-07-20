package bloo.ad.addbloo

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.LinearLayoutManager
import bloo.ad.addbloo.db.Blocked
import bloo.ad.addbloo.db.Db
import bloo.ad.addbloo.db.UrlDao
import kotlinx.android.synthetic.main.activity_filter.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch


class FilterActivity : AppCompatActivity() {

    private lateinit var model : UrlViewModel
    private lateinit var adapter: UrlListAdapter
    private lateinit var dao: UrlDao

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_filter)

        dao = Db.instance(applicationContext).blockedUrlsDao()
        model = ViewModelProviders.of(this).get(UrlViewModel::class.java)
        model.init(dao)

        adapter = UrlListAdapter(model.all.value?.toMutableList() ?: mutableListOf()) { url, isChecked ->
            GlobalScope.launch(Dispatchers.IO) {
                dao.update(Blocked(url, isChecked))
            }
        }

        urls.layoutManager = LinearLayoutManager(this)
        urls.adapter = adapter

        val allObserver = Observer<List<Blocked>> { data ->
            adapter.items = data.sortedBy { it.host }.toMutableList()
            adapter.notifyDataSetChanged()
        }

        model.all.observe(this, allObserver)

        searchButton.setOnClickListener {
            val filteredData = model.all.value?.filter { it.host.contains(search.text, true) } ?: listOf()
            adapter.items = filteredData.toMutableList()
            adapter.notifyDataSetChanged()
        }

        clear.setOnClickListener {
            search.setText("")
            searchButton.callOnClick()
        }
    }
}
