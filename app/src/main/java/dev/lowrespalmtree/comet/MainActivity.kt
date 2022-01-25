package dev.lowrespalmtree.comet

import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import dev.lowrespalmtree.comet.databinding.ActivityMainBinding
import kotlinx.coroutines.ExperimentalCoroutinesApi

@ExperimentalCoroutinesApi
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var nhf: NavHostFragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Database.init(applicationContext)  // TODO move to App Startup?

        nhf = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment?
        nhf?.also { binding.drawerNavigation.setupWithNavController(it.navController) }
    }

    /** Navigate to the PageViewFragment; this will automatically use the home URL if any. */
    fun goHome(@Suppress("unused_parameter") item: MenuItem) {
        val bundle = bundleOf()
        Preferences.getHomeUrl(this)?.let { bundle.putString("url", it) }
        nhf?.navController?.navigate(R.id.action_global_pageFragment, bundle)
        binding.drawerLayout.closeDrawers()
    }
}