package com.example.detectmangodisease.dto

class SettingsDTO {
    enum class TypeModel{
        LOCAL {override fun toString(): String { return "Local" }},
        SERVER {
            override fun toString(): String {
                return "Servidor"
            }
        }
    }
    enum class TypeMonitored {
        WIFI {
            override fun toString(): String {
                return "WiFi"
            }
        },
        FOUR_G {
            override fun toString(): String {
                return "4G"
            }
        },
        THREE_G {
            override fun toString(): String {
                return "3G"
            }
        },
        MODE_PLAIN {
            override fun toString(): String {
                return "Modo Avião"
            }
        }
    }

    lateinit var modelUsed: TypeModel
    lateinit var monitored: TypeMonitored
}