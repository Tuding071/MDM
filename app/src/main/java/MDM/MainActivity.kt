package MDM

import android.Manifest
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.concurrent.ConcurrentHashMap

class MainActivity : AppCompatActivity() {
    
    private lateinit var downloadManager: DownloadManager
    private lateinit var urlInput: EditText
    private lateinit var addButton: Button
    private lateinit var downloadsContainer: LinearLayout
    private lateinit var scrollView: ScrollView
    
    private val downloadIds = ConcurrentHashMap<Long, DownloadItem>()
    private val STORAGE_PERMISSION_CODE = 1001
    
    private val onComplete = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            downloadIds.remove(id)?.let { item ->
                updateDownloadStatus(item, "Completed", 100)
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createUI()
        checkPermissions()
        initializeDownloadManager()
    }
    
    private fun createUI() {
        // Main layout
        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F5F5F5"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }

        // Input section
        val inputLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
            }
            gravity = Gravity.CENTER_VERTICAL
        }

        // URL input field
        urlInput = EditText(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                setMargins(0, 0, dpToPx(8), 0)
            }
            hint = "Enter video or file URL"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12))
            setBackgroundResource(android.R.drawable.edit_text)
        }

        // Add button
        addButton = Button(this).apply {
            text = "Add Download"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12))
            setBackgroundColor(Color.parseColor("#2196F3"))
            setTextColor(Color.WHITE)
            setOnClickListener { startDownload() }
        }

        inputLayout.addView(urlInput)
        inputLayout.addView(addButton)

        // Downloads title
        val downloadsTitle = TextView(this).apply {
            text = "Downloads"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8))
            }
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#333333"))
        }

        // Scroll view for downloads
        scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            ).apply {
                setMargins(dpToPx(16), 0, dpToPx(16), dpToPx(16))
            }
        }

        // Downloads container
        downloadsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        scrollView.addView(downloadsContainer)

        // Add all views to main layout
        mainLayout.addView(inputLayout)
        mainLayout.addView(downloadsTitle)
        mainLayout.addView(scrollView)

        setContentView(mainLayout)
    }
    
    private fun createDownloadItemView(fileName: String): DownloadItem {
        // Main item layout
        val itemLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, dpToPx(12))
            }
            setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
            setBackgroundColor(Color.WHITE)
            elevation = dpToPx(2).toFloat()
        }

        // File name
        val fileNameView = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, dpToPx(8))
            }
            text = fileName
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(Color.parseColor("#333333"))
            isSingleLine = true
        }

        // Progress bar
        val progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, dpToPx(8))
            }
            max = 100
            progress = 0
            progressDrawable = ContextCompat.getDrawable(this@MainActivity, android.R.drawable.progress_horizontal)
        }

        // Status text
        val statusView = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            text = "0% - Downloading..."
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(Color.parseColor("#666666"))
        }

        // Add views to item layout
        itemLayout.addView(fileNameView)
        itemLayout.addView(progressBar)
        itemLayout.addView(statusView)

        // Add to downloads container
        downloadsContainer.addView(itemLayout)

        return DownloadItem(itemLayout, fileNameView, progressBar, statusView)
    }
    
    private fun checkPermissions() {
        val permissions = arrayOf(
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE
        )
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (permissions.any { 
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED 
            }) {
                ActivityCompat.requestPermissions(this, permissions, STORAGE_PERMISSION_CODE)
            }
        }
    }
    
    private fun initializeDownloadManager() {
        downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        registerReceiver(onComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
    }
    
    private fun startDownload() {
        val url = urlInput.text.toString().trim()
        if (url.isEmpty()) {
            showToast("Please enter a URL")
            return
        }
        
        if (!isValidUrl(url)) {
            showToast("Please enter a valid URL")
            return
        }
        
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(getFileNameFromUrl(url))
            .setDescription("Downloading...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(
                Environment.DIRECTORY_DOWNLOADS,
                getFileNameFromUrl(url)
            )
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
        
        val downloadId = downloadManager.enqueue(request)
        val downloadItem = createDownloadItemView(getFileNameFromUrl(url))
        downloadIds[downloadId] = downloadItem
        
        urlInput.text.clear()
        showToast("Download started")
        
        startProgressUpdates(downloadId, downloadItem)
    }
    
    private fun startProgressUpdates(downloadId: Long, downloadItem: DownloadItem) {
        Thread {
            var downloading = true
            while (downloading) {
                val query = DownloadManager.Query().setFilterById(downloadId)
                val cursor = downloadManager.query(query)
                
                if (cursor.moveToFirst()) {
                    val status = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS))
                    val bytesDownloaded = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    val bytesTotal = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                    
                    runOnUiThread {
                        when (status) {
                            DownloadManager.STATUS_SUCCESSFUL -> {
                                updateDownloadStatus(downloadItem, "Completed", 100)
                                downloading = false
                            }
                            DownloadManager.STATUS_FAILED -> {
                                updateDownloadStatus(downloadItem, "Failed", 0)
                                downloading = false
                            }
                            DownloadManager.STATUS_PAUSED -> {
                                val progress = if (bytesTotal > 0) {
                                    (bytesDownloaded * 100L / bytesTotal).toInt()
                                } else 0
                                updateDownloadStatus(downloadItem, "Paused", progress)
                            }
                            else -> {
                                val progress = if (bytesTotal > 0) {
                                    (bytesDownloaded * 100L / bytesTotal).toInt()
                                } else 0
                                updateDownloadStatus(downloadItem, "Downloading", progress)
                            }
                        }
                    }
                } else {
                    downloading = false
                }
                cursor.close()
                
                Thread.sleep(1000)
            }
        }.start()
    }
    
    private fun updateDownloadStatus(item: DownloadItem, status: String, progress: Int) {
        item.progressBar.progress = progress
        item.statusView.text = "$progress% - $status"
        
        // Update status color based on state
        when (status) {
            "Completed" -> item.statusView.setTextColor(Color.parseColor("#4CAF50"))
            "Failed" -> item.statusView.setTextColor(Color.parseColor("#F44336"))
            "Paused" -> item.statusView.setTextColor(Color.parseColor("#FF9800"))
            else -> item.statusView.setTextColor(Color.parseColor("#666666"))
        }
    }
    
    private fun isValidUrl(url: String): Boolean {
        return url.startsWith("http://") || url.startsWith("https://")
    }
    
    private fun getFileNameFromUrl(url: String): String {
        return try {
            Uri.parse(url).lastPathSegment ?: "download_${System.currentTimeMillis()}"
        } catch (e: Exception) {
            "download_${System.currentTimeMillis()}"
        }
    }
    
    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
    
    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            resources.displayMetrics
        ).toInt()
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == STORAGE_PERMISSION_CODE) {
            if (grantResults.any { it != PackageManager.PERMISSION_GRANTED }) {
                showToast("Storage permission required for downloads")
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(onComplete)
    }
    
    data class DownloadItem(
        val layout: LinearLayout,
        val fileNameView: TextView,
        val progressBar: ProgressBar,
        val statusView: TextView
    )
}
