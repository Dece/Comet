package dev.lowrespalmtree.comet

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import dev.lowrespalmtree.comet.databinding.ActivityMainBinding
import kotlinx.coroutines.ExperimentalCoroutinesApi

@ExperimentalCoroutinesApi
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var navHost: NavHostFragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Database.init(applicationContext)  // TODO move to App Startup?

        navHost = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment?
        navHost?.also { binding.drawerNavigation.setupWithNavController(it.navController) }
    }

    fun goHome() {
        navHost?.navController?.navigate(R.id.action_global_pageViewFragment)
        binding.drawerLayout.closeDrawers()
    }

    fun openHistory() {
        binding.drawerLayout.closeDrawers()
        // TODO
    }

    fun openSettings() {
        navHost?.navController?.navigate(R.id.action_global_settingsFragment)
        binding.drawerLayout.closeDrawers()
    }
}