package com.example.detectmangodisease.dto

class SettingsDTO {
    enum class TypeModel{
        LOCAL, SERVER
    }
    enum class TypeMonitored {
        WIFI, FOUR_G, THREE_G, MODE_PLAIN
    }

    lateinit var modelUsed: TypeModel
    lateinit var monitored: TypeMonitored
}