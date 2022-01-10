package dev.lowrespalmtree.comet

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.ExperimentalCoroutinesApi


@ExperimentalCoroutinesApi
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Database.init(applicationContext)  // TODO move to App Startup?

        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .add(R.id.main_content, PageViewFragment().apply { arguments = intent.extras })
                .commit()
        }
    }

    override fun onBackPressed() {
        // TODO pass to PageViewFragment
        super.onBackPressed()
    }

    companion object {
        const val TAG = "MainActivity"
    }
}