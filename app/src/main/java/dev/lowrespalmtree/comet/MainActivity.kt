package dev.lowrespalmtree.comet

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.navigation.NavigationView
import kotlinx.coroutines.ExperimentalCoroutinesApi

@ExperimentalCoroutinesApi
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Database.init(applicationContext)  // TODO move to App Startup?

        supportFragmentManager.findFragmentById(R.id.nav_host_fragment)?.also { navHost ->
            findViewById<NavigationView>(R.id.drawer_navigation)?.apply {
                setupWithNavController((navHost as NavHostFragment).navController)
            }
        }
    }
}