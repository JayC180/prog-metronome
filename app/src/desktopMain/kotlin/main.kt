import androidx.compose.runtime.remember
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.unit.dp
import com.jayc180.rhythmengine.audio.AudioEngineDesktop
import com.jayc180.rhythmengine.builder.TrackBuilder
import com.jayc180.rhythmengine.ui.App
import com.jayc180.rhythmengine.ui.AppViewModel

fun main() = application {
    val vm = remember {
        AppViewModel(
            builder = TrackBuilder(initialBpm = 120.0),
            audio   = AudioEngineDesktop(),
        )
    }

    Window(
        onCloseRequest = {
            vm.builder.stop()
            vm.audio.stopAll()
            vm.audio.close()
            exitApplication()
        },
        title = "Rhythm Engine",
        state = rememberWindowState(width = 900.dp, height = 680.dp),
    ) {
        App(vm = vm)
    }
}