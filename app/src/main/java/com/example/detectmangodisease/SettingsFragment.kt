package com.example.detectmangodisease

import android.content.Context
import android.content.SharedPreferences
import android.opengl.Visibility
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import com.example.detectmangodisease.databinding.FragmentSettingsBinding
import com.example.detectmangodisease.dto.SettingsDTO

class SettingsFragment : Fragment() {
    
    private lateinit var binding: FragmentSettingsBinding

    private lateinit var settings: SettingsDTO

    private lateinit var sharedPref: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = SettingsDTO()
        sharedPref = requireActivity().getSharedPreferences(getString(R.string.settings_file), Context.MODE_PRIVATE)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding = FragmentSettingsBinding.bind(view)

        binding.radioGroupModel.setOnCheckedChangeListener { group, checkedId ->
            when(checkedId) {
                binding.radioGroupModelServer.id -> setModel(SettingsDTO.TypeModel.SERVER)
                binding.radioGroupModelLocal.id -> setModel(SettingsDTO.TypeModel.LOCAL)
                else -> {
                    setModel(SettingsDTO.TypeModel.LOCAL)
                }
            }
        }
        binding.radioGroupMonitored.setOnCheckedChangeListener { group, checkedId ->
            when(checkedId) {
                binding.radioGroupMonitoredWifi.id -> {settings.monitored = SettingsDTO.TypeMonitored.WIFI}
                binding.radioGroupMonitoredFourG.id -> {settings.monitored = SettingsDTO.TypeMonitored.FOUR_G}
                binding.radioGroupMonitoredThreeG.id -> {settings.monitored = SettingsDTO.TypeMonitored.THREE_G}
                binding.radioGroupMonitoredPlainMode.id -> {settings.monitored = SettingsDTO.TypeMonitored.MODE_PLAIN}
                else -> {
                    settings.monitored = SettingsDTO.TypeMonitored.MODE_PLAIN
                }
            }
        }

        binding.radioGroupModel.check(binding.radioGroupModelLocal.id)
        setModel(SettingsDTO.TypeModel.LOCAL)

        changeRadioGroupMonitored(binding.radioGroupMonitoredPlainMode.id, SettingsDTO.TypeMonitored.MODE_PLAIN)
    }

    private fun changeRadioGroupMonitored(radioButtonIndex: Int, typeMonitored: SettingsDTO.TypeMonitored) {
        binding.radioGroupMonitored.check(radioButtonIndex)

        settings.monitored = typeMonitored
    }

    private fun enableRadioButtonPlainMode(enabled: Boolean) {
        for (i in 0 until binding.radioGroupMonitored.childCount) {
            if(binding.radioGroupMonitored.getChildAt(i).id == binding.radioGroupMonitoredPlainMode.id) {
                binding.radioGroupMonitoredPlainMode.isEnabled = enabled

                if(!enabled && binding.radioGroupMonitoredPlainMode.isChecked) {
                    binding.radioGroupMonitoredPlainMode.isChecked = false

                    changeRadioGroupMonitored(binding.radioGroupMonitoredWifi.id, SettingsDTO.TypeMonitored.WIFI)
                }
            } else {
                binding.radioGroupMonitored.getChildAt(i).isEnabled = !enabled

                if(enabled && (binding.radioGroupMonitored.getChildAt(i) as RadioButton).isChecked) {
                    changeRadioGroupMonitored(binding.radioGroupMonitoredPlainMode.id, SettingsDTO.TypeMonitored.MODE_PLAIN)
                }
            }
        }
    }
    private fun setModel(typeModel: SettingsDTO.TypeModel) {
        settings.modelUsed = typeModel
        if(typeModel == SettingsDTO.TypeModel.SERVER) {
            enableRadioButtonPlainMode(false)
        } else {
            enableRadioButtonPlainMode(true)
        }
    }

    private fun saveSettings() {
        if(sharedPref != null) {
            with(sharedPref.edit()) {
                putString("modelUsed", settings.modelUsed.name)
                putString("monitored", settings.monitored.name)
                apply()
            }
        }
    }
    override fun onDestroyView() {
        saveSettings()
        super.onDestroyView()
    }
}