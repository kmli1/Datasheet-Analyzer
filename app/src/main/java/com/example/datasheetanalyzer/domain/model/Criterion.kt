package com.example.datasheetanalyzer.domain.model

enum class Criterion(val label: String) {
    SUPPLY_VOLTAGE("Supply Voltage"),
    PINOUT("Pinout"),
    MAXIMUM_CURRENT("Maximum Current"),
    OPERATING_TEMPERATURE("Operating Temperature"),
    POWER_DISSIPATION("Power Dissipation"),
    ELECTRICAL_CHARACTERISTICS("Electrical Characteristics"),
    FEATURES("Features"),
    APPLICATIONS("Applications"),
    SUPPLY_CURRENT("Supply Current"),
    OUTPUT_CURRENT("Output Current"),
    INPUT_VOLTAGE("Input Voltage"),
    OUTPUT_VOLTAGE("Output Voltage"),
    STORAGE_TEMPERATURE("Storage Temperature"),
    FREQUENCY("Frequency"),
    BANDWIDTH("Bandwidth"),
    GAIN("Gain"),
    INPUT_OFFSET_VOLTAGE("Input Offset Voltage"),
    PACKAGE("Package"),
    ABSOLUTE_MAXIMUMS("Absolute Maximums"),
    RECOMMENDED_OPERATING_CONDITIONS("Recommended Operating Conditions")
}
