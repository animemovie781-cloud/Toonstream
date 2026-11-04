package com.toonstream.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.toonstream.app.databinding.ActivityMainBinding
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        binding.bottomNavigation.setupWithNavController(navController)

        navController.addOnDestinationChangedListener { _, destination, _ ->
            when (destination.id) {
                R.id.navigation_home,
                R.id.navigation_movies,
                R.id.navigation_series,
                R.id.navigation_my_list -> binding.bottomNavigation.visibility = android.view.View.VISIBLE
                else -> binding.bottomNavigation.visibility = android.view.View.GONE
            }
        }
    }
}
