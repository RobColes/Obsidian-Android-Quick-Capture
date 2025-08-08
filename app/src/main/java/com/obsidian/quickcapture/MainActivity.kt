package com.obsidian.quickcapture

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

/**
 * MainActivity for ObsQuickCapture
 * 
 * This activity provides a simple interface for quickly capturing text and saving it
 * to markdown files in the Obsidian vault structure. The app supports:
 * - Creating new timestamped files
 * - Appending to an existing scratchpad file
 * - Receiving shared text from other apps
 * 
 * The UI consists of a large text input area and three action buttons positioned
 * in the top half of the screen to avoid interference with the on-screen keyboard.
 */
class MainActivity : AppCompatActivity() {
    
    // UI components
    private lateinit var textInput: EditText
    private lateinit var buttonCancel: Button
    private lateinit var buttonNewFile: Button
    private lateinit var buttonAppend: Button
    
    // File paths for Obsidian vault structure - initialized in onCreate()
    private lateinit var appDocumentsPath: File
    private lateinit var transientPath: File
    private lateinit var scratchpadFile: File
    
    // Date formatter for timestamped filenames (24-hour format)
    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd HHmm", Locale.getDefault())
    
    /**
     * Request permission for file access (for Android versions that require it)
     * For Android 11+, we need special "All files access" permission
     */
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (!isGranted) {
            Toast.makeText(this, "Storage permission required for file operations", Toast.LENGTH_LONG).show()
        }
    }
    
    /**
     * Request "All files access" permission for Android 11+
     */
    private val requestAllFilesAccessLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Toast.makeText(this, "All files access permission required for file operations", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Initialize file paths using app-specific external storage
        initializeFilePaths()
        
        // Initialize UI components
        initializeViews()
        
        // Set up button click listeners
        setupButtonListeners()
        
        // Handle shared text from other apps
        handleIncomingIntent()
        
        // Request focus on text input to show keyboard immediately
        textInput.requestFocus()
        
        // Check and request permissions if needed (for older Android versions)
        checkStoragePermissions()
    }
    
    /**
     * Initialize file paths using public Documents directory (same as New File function)
     * Both New File and Append should use the same base directory structure
     */
    private fun initializeFilePaths() {
        // Use public Documents directory - same base path for both New File and Append operations
        val documentsPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        appDocumentsPath = File(documentsPath, "Robsidian")
        transientPath = File(appDocumentsPath, "Transient")
        scratchpadFile = File(transientPath, "Scratchpad.md")
    }
    
    /**
     * Initialize all UI components and set up the text input
     */
    private fun initializeViews() {
        textInput = findViewById(R.id.textInput)
        buttonCancel = findViewById(R.id.buttonCancel)
        buttonNewFile = findViewById(R.id.buttonNewFile)
        buttonAppend = findViewById(R.id.buttonAppend)
        
        // Configure text input for optimal text entry experience
        textInput.requestFocus()
        textInput.isCursorVisible = true
    }
    
    /**
     * Set up click listeners for all three action buttons
     */
    private fun setupButtonListeners() {
        // Cancel button - simply close the app without saving
        buttonCancel.setOnClickListener {
            finish()
        }
        
        // New File button - create a timestamped markdown file
        buttonNewFile.setOnClickListener {
            createNewFile()
        }
        
        // Append button - add text to the existing scratchpad file
        buttonAppend.setOnClickListener {
            appendToScratchpad()
        }
    }
    
    /**
     * Handle incoming intent when app is launched via share action
     * Pre-populate the text input with shared content
     */
    private fun handleIncomingIntent() {
        when (intent?.action) {
            Intent.ACTION_SEND -> {
                if (intent.type == "text/plain") {
                    val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
                    if (!sharedText.isNullOrEmpty()) {
                        textInput.setText(sharedText)
                        // Position cursor at the end of the shared text
                        textInput.setSelection(sharedText.length)
                    }
                }
            }
        }
    }
    
    /**
     * Check and request storage permissions for accessing public Documents directory
     * Different approaches for different Android versions
     */
    private fun checkStoragePermissions() {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                // Android 11+ requires "All files access" permission for public directories
                if (!Environment.isExternalStorageManager()) {
                    AlertDialog.Builder(this)
                        .setTitle("Permission Required")
                        .setMessage("This app needs \"All files access\" permission to save files to your Documents folder. You'll be taken to Settings to grant this permission.")
                        .setPositiveButton("Grant Permission") { _, _ ->
                            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                            intent.data = Uri.parse("package:$packageName")
                            requestAllFilesAccessLauncher.launch(intent)
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            }
            Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q -> {
                // Android 10 and below use traditional storage permissions
                if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) 
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    requestPermissionLauncher.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }
            // Android 11 (API 30) without MANAGE_EXTERNAL_STORAGE falls through to here
            // and may work with traditional permissions or require MediaStore API
        }
    }
    
    /**
     * Create a new markdown file with timestamp in filename
     * Format: yyyy-MM-dd HHmm.md (24-hour format)
     * Location: /Documents/Robsidian/
     */
    private fun createNewFile() {
        val inputText = textInput.text.toString()
        
        // Don't create empty files
        if (inputText.trim().isEmpty()) {
            Toast.makeText(this, "Please enter some text first", Toast.LENGTH_SHORT).show()
            return
        }
        
        try {
            // Ensure the Robsidian directory exists
            if (!appDocumentsPath.exists()) {
                if (!appDocumentsPath.mkdirs()) {
                    showError("Failed to create Robsidian directory")
                    return
                }
            }
            
            // Generate timestamped filename
            val timestamp = dateFormatter.format(Date())
            val fileName = "$timestamp.md"
            val newFile = File(appDocumentsPath, fileName)
            
            // Write the content to the file (plain markdown, no frontmatter)
            FileWriter(newFile).use { writer ->
                writer.write(inputText)
            }
            
            // Show success message and close app
            showSuccessAndClose("File saved: $fileName")
            
        } catch (e: Exception) {
            showError("Failed to create file: ${e.message}")
        }
    }
    
    /**
     * Append text content to the existing scratchpad file
     * Location: /Documents/Robsidian/Transient/Scratchpad.md
     * Creates the file and directory structure if they don't exist
     */
    private fun appendToScratchpad() {
        val inputText = textInput.text.toString()
        
        // Don't append empty text
        if (inputText.trim().isEmpty()) {
            Toast.makeText(this, "Please enter some text first", Toast.LENGTH_SHORT).show()
            return
        }
        
        try {
            // Ensure the directory structure exists
            if (!transientPath.exists()) {
                if (!transientPath.mkdirs()) {
                    showError("Failed to create Transient directory")
                    return
                }
            }
            
            // Create scratchpad file if it doesn't exist
            if (!scratchpadFile.exists()) {
                scratchpadFile.createNewFile()
            }
            
            // Append content to the file with a newline separator
            FileWriter(scratchpadFile, true).use { writer ->
                // Add newline before content if file is not empty
                if (scratchpadFile.length() > 0) {
                    writer.write("\n\n")
                }
                writer.write(inputText)
            }
            
            // Show success message and close app
            showSuccessAndClose("Text appended to Scratchpad.md")
            
        } catch (e: Exception) {
            showError("Failed to append to scratchpad: ${e.message}")
        }
    }
    
    /**
     * Display error message to user
     */
    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
    
    /**
     * Show success message, remind user to sync Obsidian, and close the app
     */
    private fun showSuccessAndClose(successMessage: String) {
        // Show success message
        Toast.makeText(this, successMessage, Toast.LENGTH_SHORT).show()
        
        // Brief delay, then show sync reminder and close
        textInput.postDelayed({
            Toast.makeText(this@MainActivity, 
                getString(R.string.toast_sync_reminder), 
                Toast.LENGTH_LONG).show()
            
            // Close app after showing the reminder
            finish()
        }, 1000) // 1 second delay
    }
    
}