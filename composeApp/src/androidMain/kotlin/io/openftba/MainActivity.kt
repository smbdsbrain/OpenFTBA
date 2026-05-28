package io.openftba

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import io.openftba.data.AndroidRideRepository
import io.openftba.data.AndroidSettingsStore
import io.openftba.ui.App
import io.openftba.ui.share.AndroidShareContext
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var repo: AndroidRideRepository

    // SAF folder picker: grant persistent read access to the OpenTracks export folder.
    private val pickFolder = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            repo.updateSettings(repo.state.value.settings.copy(watchFolder = uri.toString()))
        }
    }

    // SAF folder picker for SRTM elevation tiles. Needs WRITE too — downloaded tiles are saved here.
    private val pickDemFolder = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            repo.updateSettings(repo.state.value.settings.copy(demFolder = uri.toString()))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AndroidShareContext.context = applicationContext
        val store = AndroidSettingsStore(applicationContext)
        repo = AndroidRideRepository(applicationContext, store)
        setContent {
            App(
                repo,
                onPickFolder = { pickFolder.launch(null) },
                onPickDemFolder = { pickDemFolder.launch(null) },
            )
        }
        lifecycleScope.launch { repo.rescan() }
    }
}
