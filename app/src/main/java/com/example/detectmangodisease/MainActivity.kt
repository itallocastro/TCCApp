package com.example.detectmangodisease

import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.detectmangodisease.databinding.ActivityMainBinding
import com.example.detectmangodisease.databinding.CustomDialogBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var dialog: AlertDialog

    private lateinit var homeFragment: HomeFragment
    private lateinit var settingsFragment: SettingsFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        homeFragment = HomeFragment()
        settingsFragment = SettingsFragment()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.helpIcon.setOnClickListener{
            showDialog()
        }

        replaceFragment(homeFragment)

        binding.bottomNavigation.setOnItemSelectedListener {
            // TODO mudar para o fragment
            when(it.itemId) {
                R.id.item_home -> replaceFragment(homeFragment)
                R.id.item_settings -> replaceFragment(settingsFragment)
                else -> {
                    Toast.makeText(this, "Home", Toast.LENGTH_SHORT).show()
                }
            }
            true
        }

    }
    private fun showDialog() {
        val build = AlertDialog.Builder(this, R.style.Theme_CustomDialog);

        val dialogBinding: CustomDialogBinding = CustomDialogBinding.inflate(LayoutInflater.from(this))

        dialogBinding.buttonDialog.setOnClickListener {
            dialog.dismiss()
        }
        build.setView(dialogBinding.root)

        dialog = build.create()
        dialog.show()
    }

    private fun replaceFragment(fragment: Fragment) {
        val fragmentManager = supportFragmentManager
        val fragmentTransaction = fragmentManager.beginTransaction()

        fragmentTransaction.replace(R.id.frame_layout, fragment)
        fragmentTransaction.commit()
    }
}