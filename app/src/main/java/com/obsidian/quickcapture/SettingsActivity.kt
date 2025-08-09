package com.obsidian.quickcapture

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * SettingsActivity for configuring file paths
 * Allows users to customize the Obsidian documents path and scratchpad file location
 */
class SettingsActivity : AppCompatActivity() {
    
    private lateinit var editDocumentsPath: EditText
    private lateinit var editScratchpadPath: EditText
    private lateinit var buttonCancel: Button
    private lateinit var buttonSave: Button
    
    companion object {
        const val PREFS_NAME = "ObsQuickCapPrefs"
        const val PREF_DOCUMENTS_PATH = "documentsPath"
        const val PREF_SCRATCHPAD_PATH = "scratchpadPath"
        
        // Default values
        const val DEFAULT_DOCUMENTS_PATH = "Robsidian"
        const val DEFAULT_SCRATCHPAD_PATH = "Transient/Scratchpad.md"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        
        initializeViews()
        loadCurrentSettings()
        setupButtonListeners()
    }
    
    private fun initializeViews() {
        editDocumentsPath = findViewById(R.id.editDocumentsPath)
        editScratchpadPath = findViewById(R.id.editScratchpadPath)
        buttonCancel = findViewById(R.id.buttonCancel)
        buttonSave = findViewById(R.id.buttonSave)
    }
    
    private fun loadCurrentSettings() {
        val sharedPrefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        
        val documentsPath = sharedPrefs.getString(PREF_DOCUMENTS_PATH, DEFAULT_DOCUMENTS_PATH)
        val scratchpadPath = sharedPrefs.getString(PREF_SCRATCHPAD_PATH, DEFAULT_SCRATCHPAD_PATH)
        
        editDocumentsPath.setText(documentsPath)
        editScratchpadPath.setText(scratchpadPath)
    }
    
    private fun setupButtonListeners() {
        buttonCancel.setOnClickListener {
            finish()
        }
        
        buttonSave.setOnClickListener {
            saveSettings()
        }
    }
    
    private fun saveSettings() {
        val documentsPath = editDocumentsPath.text.toString().trim()
        val scratchpadPath = editScratchpadPath.text.toString().trim()
        
        // Validate inputs
        if (documentsPath.isEmpty()) {
            Toast.makeText(this, "Documents path cannot be empty", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (scratchpadPath.isEmpty()) {
            Toast.makeText(this, "Scratchpad path cannot be empty", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Save to SharedPreferences
        val sharedPrefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        with(sharedPrefs.edit()) {
            putString(PREF_DOCUMENTS_PATH, documentsPath)
            putString(PREF_SCRATCHPAD_PATH, scratchpadPath)
            apply()
        }
        
        Toast.makeText(this, "Settings saved successfully", Toast.LENGTH_SHORT).show()
        finish()
    }
}